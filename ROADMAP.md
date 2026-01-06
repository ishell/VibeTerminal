# VibeTerminal Roadmap

> Android 移动端 Claude Code 操作终端

## 项目愿景

在手机上通过 SSH 连接开发机器，以优秀的用户体验操作 Claude Code，实现随时随地高效编程。

## 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **架构**: MVVM + Clean Architecture
- **SSH库**: sshj
- **本地存储**: Room + DataStore
- **异步处理**: Kotlin Coroutines + Flow
- **依赖注入**: Hilt

---

## Phase 1: 项目基础架构

### 1.1 项目初始化
- [ ] Kotlin + Jetpack Compose 项目搭建
- [ ] Gradle 依赖配置 (sshj, Room, Hilt, Coroutines)
- [ ] 代码规范配置 (ktlint)

### 1.2 应用架构
```
app/
├── data/                    # 数据层
│   ├── local/              # Room 数据库
│   │   ├── entity/         # 数据实体
│   │   ├── dao/            # 数据访问对象
│   │   └── database/       # 数据库配置
│   ├── ssh/                # SSH 客户端
│   │   ├── SshClient.kt
│   │   ├── SshConfig.kt
│   │   └── PtySession.kt
│   └── repository/         # 数据仓库实现
│
├── domain/                  # 领域层
│   ├── model/              # 领域模型
│   ├── repository/         # 仓库接口
│   └── usecase/            # 业务用例
│
├── terminal/               # 终端模块
│   ├── emulator/           # 终端模拟核心
│   ├── parser/             # ANSI/VT100 解析
│   └── renderer/           # Compose 渲染
│
├── ui/                     # UI 层
│   ├── navigation/         # 导航配置
│   ├── home/               # 首页 - 项目列表
│   ├── terminal/           # 终端页
│   ├── settings/           # 设置页
│   ├── theme/              # 主题配置
│   └── components/         # 公共组件
│
├── keyboard/               # 定制键盘
│   ├── CustomKeyboardView.kt
│   ├── KeyMapper.kt
│   └── ZellijShortcuts.kt
│
├── service/                # 后台服务
│   └── SshConnectionService.kt
│
└── di/                     # 依赖注入模块
```

### 1.3 数据模型设计
```kotlin
// 开发机器配置
data class Machine(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType,  // PASSWORD / SSH_KEY
    val password: String?,
    val privateKeyPath: String?,
    val passphrase: String?
)

// 项目/会话
data class Project(
    val id: String,
    val name: String,
    val machineId: String,
    val zellijSession: String,
    val workingDirectory: String,
    val lastConnected: Long
)
```

---

## Phase 2: SSH 连接模块

### 2.1 SSH 客户端封装
- [ ] 基础连接管理 (connect/disconnect)
- [ ] 密码认证支持
- [ ] SSH Key 认证支持 (Ed25519, RSA, ECDSA)
- [ ] 连接状态管理 (StateFlow)
- [ ] 错误处理与重试机制

### 2.2 PTY 会话管理
- [ ] 伪终端 (PTY) 分配
- [ ] 终端尺寸协商 (SIGWINCH)
- [ ] 输入流处理 (stdin)
- [ ] 输出流处理 (stdout/stderr)
- [ ] Shell 环境变量配置

### 2.3 连接保活
- [ ] 心跳机制 (keepalive)
- [ ] 断线检测
- [ ] 自动重连策略
- [ ] 前台服务保持连接

---

## Phase 3: 终端模拟器

### 3.1 ANSI/VT100 解析器
- [ ] 基础控制字符 (BS, TAB, LF, CR)
- [ ] CSI 序列解析 (光标移动、清屏等)
- [ ] SGR 序列解析 (颜色、样式)
- [ ] OSC 序列解析 (标题设置等)
- [ ] 256色支持
- [ ] TrueColor (24-bit) 支持

### 3.2 终端缓冲区
- [ ] 屏幕缓冲区 (Screen Buffer)
- [ ] 滚动缓冲区 (Scrollback Buffer)
- [ ] 替代屏幕 (Alternate Screen)
- [ ] 光标状态管理
- [ ] 字符属性管理

### 3.3 Compose 渲染器
- [ ] Canvas 高性能文本绘制
- [ ] 等宽字体配置 (JetBrains Mono / Fira Code)
- [ ] 光标渲染与闪烁动画
- [ ] 选择区域高亮
- [ ] 滚动性能优化

### 3.4 输入处理
- [ ] 软键盘集成
- [ ] 特殊键映射 (Ctrl, Alt, Esc, 方向键, F1-F12)
- [ ] 组合键支持 (Ctrl+C, Ctrl+D 等)
- [ ] 输入法支持 (中文等)

---

## Phase 4: 基础 UI

### 4.1 首页 - 项目列表
- [ ] 项目卡片展示
- [ ] 机器连接状态指示 (在线/离线)
- [ ] 快速连接按钮
- [ ] 下拉刷新
- [ ] 空状态引导

### 4.2 设置页
- [ ] 机器管理 (增删改查)
- [ ] SSH 配置表单
- [ ] SSH Key 导入/生成
- [ ] 外观设置
  - [ ] 字体大小
  - [ ] 配色方案 (预设 + 自定义)
  - [ ] 主题 (亮色/暗色/跟随系统)

### 4.3 终端页
- [ ] 全屏终端视图
- [ ] 顶部状态栏
  - [ ] 连接状态
  - [ ] Session 名称
  - [ ] 当前目录
- [ ] 底部快捷键栏
- [ ] 手势操作
  - [ ] 双指缩放字体
  - [ ] 滑动滚动历史

---

## Phase 5: Zellij 集成

### 5.1 Session 管理
- [ ] 列出现有 sessions (`zellij ls`)
- [ ] 创建新 session (`zellij -s <name>`)
- [ ] 附加到 session (`zellij a <name>`)
- [ ] 分离 session (`zellij detach`)
- [ ] Session 信息本地持久化

### 5.2 Panel 操作
- [ ] 解析当前 layout
- [ ] Panel 切换命令
- [ ] 新建/关闭 Panel
- [ ] 分屏操作

### 5.3 Tab 栏 UI
- [ ] Panel 列表展示
- [ ] 当前 Panel 高亮
- [ ] 点击切换
- [ ] 长按预览

---

## Phase 6: 定制键盘

### 6.1 基础快捷键层
```
┌─────────────────────────────────────────────┐
│ [Esc] [Ctrl] [Alt] [Tab] [↑] [↓] [←] [→]   │
└─────────────────────────────────────────────┘
```
- [ ] 修饰键 (Ctrl, Alt, Shift, Meta)
- [ ] 方向键
- [ ] 功能键 (Esc, Tab, Enter, Backspace)
- [ ] 修饰键状态切换 (单击临时 / 双击锁定)

### 6.2 Zellij 快捷键层
```
长按 Ctrl 或特定手势激活:
┌─────────────────────────────────────────────┐
│ [新Panel] [关闭] [←] [→] [↑] [↓] [全屏]    │
└─────────────────────────────────────────────┘
```
- [ ] 新建 Panel (水平/垂直分屏)
- [ ] 关闭当前 Panel
- [ ] Panel 导航 (上下左右)
- [ ] 全屏切换
- [ ] Tab 切换

### 6.3 Claude Code 快捷键层
- [ ] 常用命令快捷按钮
  - [ ] `/clear` - 清除对话
  - [ ] `/compact` - 压缩上下文
  - [ ] `/help` - 帮助
- [ ] 快捷回复 (Y/n/A)

---

## Phase 7: 混合显示模式

### 7.1 单 Panel 全屏模式
- [ ] 当前活动 Panel 全屏显示
- [ ] 左右滑动切换 Panel
- [ ] 边缘指示器 (显示左右还有 Panel)

### 7.2 多 Panel 预览模式
- [ ] 双指捏合触发
- [ ] 缩略图网格布局
- [ ] 点击切换到目标 Panel
- [ ] 拖拽调整布局 (可选)

### 7.3 迷你预览条
- [ ] 底部显示其他 Panel 单行预览
- [ ] 点击快速切换
- [ ] 可配置显示/隐藏

---

## Phase 8: Claude Code 智能交互 (进阶)

### 8.1 输出解析器
- [ ] 终端输出流实时解析
- [ ] 确认请求检测
  - [ ] `[Y/n]` 模式
  - [ ] `Allow/Deny` 模式
  - [ ] 工具调用确认
- [ ] 内容类型识别
  - [ ] 文字说明
  - [ ] 代码块
  - [ ] Diff
  - [ ] 文件操作

### 8.2 智能通知
- [ ] 前台服务监控
- [ ] 确认请求通知推送
- [ ] 通知快捷操作 (Yes/No 按钮)
- [ ] 任务完成通知

### 8.3 内容美化展示
- [ ] 浮动信息卡片
- [ ] 代码块语法高亮
- [ ] Diff 红绿对比显示
- [ ] 文件操作卡片
- [ ] 点击展开详情

---

## Phase 9: 配置管理 (进阶)

### 9.1 远程配置读取
- [ ] 读取 `~/.claude/settings.json`
- [ ] 读取 `CLAUDE.md` / `CLAUDE.local.md`
- [ ] 读取 MCP servers 配置
- [ ] 读取 Skills 配置

### 9.2 可视化配置编辑
- [ ] 配置项 GUI 展示
- [ ] 表单式编辑
- [ ] 保存并同步到远程

### 9.3 快捷配置模板
- [ ] 常用配置预设
- [ ] 一键应用
- [ ] 配置导入/导出

---

## Phase 10: 增强功能 (远期)

### 10.1 效率提升
- [ ] 语音输入 (语音转文字给 Claude)
- [ ] 常用命令收藏
- [ ] 代码片段收藏
- [ ] 快捷指令模板

### 10.2 多机器管理
- [ ] 机器分组
- [ ] 批量操作
- [ ] 机器状态监控

### 10.3 协作功能
- [ ] 会话历史回放
- [ ] 终端截图/录屏
- [ ] 分享终端状态

### 10.4 离线支持
- [ ] 离线草稿编写
- [ ] Prompt 模板管理
- [ ] 上线后自动发送

---

## 里程碑

### MVP (Minimum Viable Product)
完成 Phase 1-4，实现:
- SSH 连接开发机器
- 现代化终端模拟
- 基础 UI 界面
- 能够运行 Claude Code

### v1.0
完成 Phase 5-7，实现:
- Zellij 深度集成
- 定制键盘
- 混合显示模式
- 流畅的移动端操作体验

### v2.0
完成 Phase 8-9，实现:
- Claude Code 智能交互
- 配置远程管理
- 通知系统

### v3.0
完成 Phase 10，实现:
- 全部增强功能
- 效率超越 PC 端

---

## 技术参考

### 依赖库
```kotlin
// SSH
implementation("com.hierynomus:sshj:0.38.0")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Hilt
implementation("com.google.dagger:hilt-android:2.50")
ksp("com.google.dagger:hilt-compiler:2.50")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.navigation:navigation-compose:2.7.7")

// DataStore
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
```

### 参考项目
- [Termux](https://github.com/termux/termux-app) - 终端模拟器实现
- [ConnectBot](https://github.com/connectbot/connectbot) - SSH 客户端
- [JuiceSSH](https://juicessh.com/) - 商业 SSH 客户端 (UI 参考)
