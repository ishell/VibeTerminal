# VibeTerminal 安全审计报告

**审计日期：** 2026年1月11日
**审计范围：** 源代码分析和配置审查
**整体安全评级：** B+ (良好，有改进空间)

---

## 执行摘要

VibeTerminal 展现了**扎实的安全基础**，对敏感 SSH 凭证采用多层保护。应用实现了现代 Android 安全最佳实践，包括数据库加密、Android Keystore 集成和安全密钥管理。然而，存在几个**中等和低风险问题**需要在生产部署前关注。

**整体风险评估：中等**（大多数关键系统保护良好，但存在一些边缘情况）

---

## 漏洞汇总表

| # | 类别 | 发现 | 严重程度 | 状态 |
|---|------|------|----------|------|
| 1 | 凭证存储 | 未实现单独凭证加密 | 高 | ⚠ 需要处理 |
| 2 | 密钥管理 | SSH 密钥字符串处理不安全 | 中 | ⚠ 需要处理 |
| 3 | Webhook 安全 | Webhook 端点无认证 | 中 | ⚠ 需要处理 |
| 4 | Webhook 安全 | 无速率限制或 DoS 防护 | 中 | ⚠ 需要处理 |
| 5 | 日志 | 密钥类型检测字符串在内存中 | 低 | ℹ 次要 |
| 6 | 数据库 | SQLCipher 正确实现 | - | ✓ 安全 |
| 7 | SSH | 主机密钥验证已实现 | - | ✓ 安全 |
| 8 | 备份 | 数据正确排除在备份外 | - | ✓ 安全 |
| 9 | Keystore | 数据库密钥受保护 | - | ✓ 安全 |
| 10 | 权限 | 仅请求适当权限 | - | ✓ 安全 |
| 11 | ProGuard | Release 构建启用 R8 混淆 | - | ✓ 安全 |
| 12 | 组件 | 导出组件正确控制 | - | ✓ 安全 |

---

## 详细发现

### 1. 凭证存储 - 优秀（有改进空间）

#### 1.1 SQLCipher 数据库加密 ✓ 强
**严重程度：** 信息
**状态：** 正确实现

- 使用 SQLCipher 4.5.7 和 AES-256 加密数据库
- 加密密钥通过 `DatabaseKeyManager` 管理
- 密钥安全存储在 Android Keystore（兼容设备上有硬件支持）

```kotlin
// 使用 SecureRandom 生成 256 位随机密钥
val newKey = ByteArray(32) // 32 字节 = 256 位
secureRandom.nextBytes(key)

// 存储：通过 EncryptedSharedPreferences 使用 AES-256-GCM 加密
EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
```

#### 1.2 机器配置存储 ⚠ 需关注
**严重程度：** 高
**位置：** `MachineEntity.kt`

**风险：**
- 私钥作为明文字符串存储在数据库中（虽然数据库本身已加密）
- SSH 密码短语存储在数据库中
- 除数据库级加密外，没有单独的凭证加密层
- 如果数据库加密密钥被泄露，所有凭证都会暴露

**建议：**
- 使用 Android Keystore 实现单独凭证加密
- 将私钥单独存储在 Keystore 而非数据库
- 使用 `EncryptedSharedPreferences` 存储单独的机器密码

#### 1.3 备份排除 ✓ 优秀
**严重程度：** 信息
**状态：** 正确配置

`backup_rules.xml` 和 `data_extraction_rules.xml` 正确排除所有敏感数据：
```xml
<!-- 排除所有内容 - SSH 凭证绝不能被备份 -->
<exclude domain="database" path="." />
<exclude domain="sharedpref" path="." />
<exclude domain="file" path="." />
```

---

### 2. SSH 安全 - 强

#### 2.1 主机密钥验证 ✓ 优秀
**严重程度：** 信息

实现了 TOFU（首次使用时信任）模型：
- 主机密钥指纹使用 SHA-256 存储在 `known_hosts` 文件中
- 防止中间人攻击
- 密钥变更时警告用户

```kotlin
// SHA-256 指纹计算
val digest = MessageDigest.getInstance("SHA-256")
val hash = digest.digest(publicKey.encoded)
return "SHA256:" + Base64.encodeToString(hash, Base64.NO_WRAP or Base64.NO_PADDING)
```

#### 2.2 SSH 密钥加载 ⚠ 中等风险
**严重程度：** 中
**位置：** `SshKeyLoader.kt`

**风险：**
1. 私钥作为 String 在内存中传递（建议使用 CharArray）
2. 临时密钥文件单次覆盖删除，无法抵抗取证恢复
3. JVM 堆中的 String 对象可能在内存中持续存在

**已有缓解措施：**
- 使用后清理临时文件
- 文件权限正确设置（仅所有者读写）
- 删除前用零覆盖内容

**建议：**
- 将私钥 String 转换为 CharArray 进行安全处理
- 实现多次覆盖（DoD 5220.22-M 标准）
- 考虑将私钥存储在 Android Keystore 中

---

### 3. Webhook 安全 ⚠ 需加固

**位置：** `NotificationWebhookService.kt`

**风险：**
1. **Webhook 端点无认证：**
   ```kotlin
   if (method == "POST" && path == "/notify") {
       handleNotification(body)  // 无认证检查！
   }
   ```

2. **使用正则解析 JSON（不安全）：**
   - 简单正则可能遗漏边缘情况
   - 对格式错误的 JSON 不够健壮

3. **端口硬编码并暴露：**
   - 默认监听 8765 端口
   - 对本地网络攻击开放

**已有缓解措施：**
- 默认禁用：`DEFAULT_WEBHOOK_SERVER_ENABLED = false`
- 配合 Tailscale（私有网络）使用
- 每请求 5 秒超时

**建议：**
1. 添加认证令牌机制
2. 实现速率限制
3. 添加负载大小限制
4. 改进 JSON 验证

---

### 4. 其他安全观察

#### 4.1 无 SQL 注入 ✓
所有数据库查询使用参数化 Room 查询：
```kotlin
@Query("SELECT * FROM machines WHERE id = :id")
suspend fun getMachineById(id: String): MachineEntity?
```

#### 4.2 无命令注入 ✓
应用不执行 shell 命令，SSH 通过 sshj 库处理。

#### 4.3 日志安全 ✓
- 实际私钥内容未记录
- Debug 日志在 Release 构建中通过 ProGuard 移除
- 敏感数据（密码、密码短语）未在任何地方记录

#### 4.4 无硬编码秘密 ✓
全面扫描未发现硬编码密码、API 密钥或秘密。

#### 4.5 组件导出控制 ✓
- 仅启动器 Activity 导出（启动应用必需）
- 所有服务未导出（仅内部访问）
- 未发现深链接或自定义 Intent scheme

---

## 正面安全方面

1. ✓ **SQLCipher 数据库加密** - 与 Android Keystore 配合的优秀实现
2. ✓ **主机密钥验证** - TOFU 模型防止中间人攻击
3. ✓ **无命令注入** - 通过库处理 SSH，非 shell
4. ✓ **无 SQL 注入** - 所有查询参数化
5. ✓ **备份排除** - 凭证受保护免于云备份
6. ✓ **代码混淆** - Release 构建启用 R8
7. ✓ **组件隔离** - 服务正确未导出
8. ✓ **禁用明文** - 不允许 HTTP
9. ✓ **无硬编码秘密** - 所有凭证来源适当
10. ✓ **现代依赖** - 最新安全库

---

## 优先建议

### 高优先级（尽快处理）
1. **使用 Android Keystore 实现单独凭证加密**
   - 将私钥存储在 Keystore 而非数据库
   - 使用 EncryptedSharedPreferences 存储机器密码

2. **改进 SSH 密钥处理**
   - 将 String 转换为 CharArray 处理敏感数据
   - 实现多次安全删除

### 中优先级（主要版本发布前处理）
3. **加固 Webhook 服务器**
   - 添加认证令牌机制
   - 实现速率限制
   - 添加负载大小限制

### 低优先级（未来改进）
4. **添加安全测试**
   - 实现凭证处理的单元测试
   - 添加 SSH 连接流程的集成测试

---

## 结论

**VibeTerminal 展现了适合 SSH 终端应用的扎实安全基础。** SQLCipher 与 Android Keystore 保护的实现、正确的 SSH 主机密钥验证以及无注入漏洞表明了安全意识的开发方法。

应用目前**适合个人/专业使用**，但在推荐用于高度敏感环境（金融服务、政府等）之前应解决上述中优先级问题。

**整体安全评级：B+（良好，有改进空间）**
