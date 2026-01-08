# VibeTerminal

**随时随地，氛围编程。**

[English](README.md)

VibeTerminal 是一款专为 [Zellij](https://zellij.dev/) 和 [Claude Code](https://claude.com/claude-code) 用户设计的 Android SSH 终端应用。结合手机的语音输入功能，你可以真正实现"说话写代码"——这就是极致的 "Vibe Coding" 体验。

---

## 为什么选择 VibeTerminal？

如果你已经在使用 Zellij 作为终端复用器，用 Claude Code 作为 AI 编程助手，你一定知道这个组合有多强大。但如果你能随时随地使用它呢？

- **等咖啡的时候？** 让 Claude 重构那个函数。
- **在公交车上？** 审查并批准 Claude 刚创建的 PR。
- **遛狗的时候？** 用语音口述一个新功能需求。

VibeTerminal 将你的手机连接到开发服务器，让你通过自然的语音输入与 Claude Code 交互。无需打字。

## 功能特性

### 无缝 Zellij 集成

VibeTerminal 专为 Zellij 用户打造。触摸手势直接映射到 Zellij 快捷键：

| 手势 | 功能 | Zellij 快捷键 |
|------|------|---------------|
| 单指左右滑动 | 切换左右面板 | `Ctrl+P` → `h/l` |
| 单指上下滑动 | 切换上下面板 | `Ctrl+P` → `k/j` |
| 双击 | 切换全屏 | `Ctrl+P` → `f` |
| 双指捏合 | 进入/退出全屏 | `Ctrl+P` → `f` |
| 双指左右滑动 | 切换标签页 | `Alt+h/l` |
| 三指点击 | 切换浮动面板 | `Ctrl+P` → `w` |

### 语音驱动开发

手机的语音键盘是你的秘密武器。不用在小键盘上费劲打字，直接说话：

> "Claude，给用户注册表单添加输入验证"

Claude Code 听到你的指令，理解你的意图，然后编写代码。你只需审查、批准，然后继续你的生活。

### 完整终端模拟器

- 支持 xterm-256color
- Nerd Font 图标支持（Iosevka Nerd Font）
- 中日韩字符支持
- 备用屏幕模式下的鼠标滚动
- 快捷键栏：Esc、Tab、Ctrl 和方向键

### 项目管理

- 保存多个 SSH 连接
- 按项目组织，自定义 Zellij 会话名称
- 自动附加到现有 Zellij 会话
- 安全的凭据存储

## 截图

<!-- TODO: 添加截图 -->

## 快速开始

1. **配置服务器**：安装 Zellij 和 Claude Code
2. **添加机器**：在 VibeTerminal 中输入 SSH 凭据
3. **创建项目**：指定你的 Zellij 会话名称
4. **连接**：开始 Vibe Coding！

## 系统要求

- Android 8.0 (API 26) 或更高版本
- 可 SSH 访问的服务器，需安装：
  - [Zellij](https://zellij.dev/)
  - [Claude Code](https://claude.com/claude-code)

### 推荐：使用 Tailscale

为了获得最佳体验，我们强烈推荐使用 [Tailscale](https://tailscale.com/) 连接你的手机和开发服务器。Tailscale 提供：

- **安全连接** — 端到端加密的 WireGuard 隧道
- **无需端口转发** — 可穿透 NAT 和防火墙
- **稳定 IP** — 服务器地址始终不变
- **低延迟** — 尽可能建立点对点直连

使用 Tailscale，你可以从任何地方安全地访问开发服务器，无需将 SSH 暴露在公网。

## 许可证

MIT License
