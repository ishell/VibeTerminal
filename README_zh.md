# VibeTerminal

**随时随地，氛围编程。**

[English](README.md)

VibeTerminal 是一款专为 [Zellij](https://zellij.dev/) + [Claude Code](https://claude.com/claude-code) 工作流设计的 Android 终端应用。它不仅是 SSH 客户端，更是一个帮你在手机上高效使用 Claude Code 的项目管理助手。

## 为什么做这个项目

Claude Code + Opus 4 彻底改变了写代码的方式——你可以在 99% 的时间里告别传统 IDE[<sup>1</sup>]。但这也带来了新的挑战：你需要认真阅读 Claude Code 输出的每一行内容，理解生成的代码、diff、说明，这和过去手工调试同样重要。

最近看到一些关于移动编程的文章[<sup>2</sup>][<sup>3</sup>][<sup>4</sup>]，启发了我做这个项目。如果你是那种出去旅游也要抱着电脑的人，我希望 VibeTerminal 能让你只带手机就够了。

目前这只是为我个人工作流定制的工具，欢迎提 issue 交流。

## 核心特性

- **项目管理** — 每个项目对应一个 Zellij Session，清晰隔离
- **会话历史** — 自动解析 Claude Code 的 JSON 文件，展示对话历史和项目进度
- **降低心智负担** — 专注于理解输出和测试验证，而非环境切换
- **断线恢复** — 基于 Zellij 的会话持久化，网络断开也不丢失工作状态

## 系统要求

- Android 8.0 (API 26) 或更高版本
- 可通过 SSH 访问的开发服务器，需安装：
  - [Zellij](https://zellij.dev/) — 终端复用器，保持会话不中断
  - [Claude Code](https://claude.com/claude-code) — AI 编程助手

## 快速开始

1. **配置服务器**：在开发机（Linux/Mac/WSL）上安装 Zellij 和 Claude Code
2. **添加机器**：在 VibeTerminal 中配置 SSH 连接
3. **创建项目**：指定 Zellij 会话名称
4. **开始编程**：连接并享受 Vibe Coding！

## 工作流程

VibeTerminal 的核心理念是「一个项目 = 一个 Zellij Session」：

```
┌─────────────────────────────────────────────────────────┐
│  手机 (VibeTerminal)                                     │
│    │                                                    │
│    │ SSH / Tailscale                                    │
│    ▼                                                    │
│  开发服务器                                              │
│    │                                                    │
│    └─► Zellij Session: "MyProject"                      │
│          ├─► Panel 1: Claude Code                       │
│          ├─► Panel 2: Neovim                            │
│          └─► Panel 3: 测试/构建                          │
└─────────────────────────────────────────────────────────┘
```

**典型工作循环：**

1. **创建工作台**：通过 `zellij a MyProject -c` 开启 session，作为项目专属工作台
2. **运行 Claude Code**：在 panel 中启动 Claude Code，开始与 AI 协作
3. **智能追踪**：App 自动探测 Claude Code 的 JSON 文件，展示对话历史
4. **迭代循环**：阅读输出 → 理解变更 → 测试验证 → 继续下一轮

<!-- TODO: 补充手机端使用截图 -->

## 推荐：使用 Tailscale

为了获得最佳的移动编程体验，强烈推荐使用 [Tailscale](https://tailscale.com/) 连接手机和开发服务器：

- **安全连接** — 端到端加密的 WireGuard 隧道
- **无需端口转发** — 可穿透 NAT 和防火墙
- **稳定 IP** — 服务器地址始终不变
- **低延迟** — 尽可能建立点对点直连

使用 Tailscale，你可以从任何地方安全地访问开发服务器，无需将 SSH 暴露在公网。

## 许可证

MIT License

## 参考文章

[1] [用 Claude Code，你要读的不是一点点信息，而是瀑布一样长的信息](https://mp.weixin.qq.com/s/k147AyWj01W-V7IpkV0g2A)

[2] [doom-coding](https://github.com/rberg27/doom-coding)

[3] [Claude Code On-The-Go](https://granda.org/en/2026/01/02/claude-code-on-the-go/)

[4] [xp 的 AI 工作流](https://blog.openacid.com/life/xp-vibe-coding/)

## Thanks

[anyrouter](https://anyrouter.top)
