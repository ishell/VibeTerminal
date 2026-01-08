# VibeTerminal

**Vibe Coding Anywhere, Anytime.**

[中文文档](README_zh.md)

VibeTerminal is an Android SSH terminal app designed specifically for developers who love [Zellij](https://zellij.dev/) and [Claude Code](https://claude.com/claude-code). Combined with your phone's voice input, you can literally code by talking — the ultimate "vibe coding" experience.

---

## Why VibeTerminal?

If you're already using Zellij as your terminal multiplexer and Claude Code as your AI coding assistant, you know how powerful this combo is. But what if you could access it from anywhere?

- **Waiting for coffee?** Ask Claude to refactor that function.
- **On the bus?** Review and approve the PR Claude just created.
- **Walking the dog?** Voice-dictate a new feature request.

VibeTerminal bridges your mobile device to your development server, letting you interact with Claude Code through natural voice input. No typing required.

## Features

### Seamless Zellij Integration

VibeTerminal is built with Zellij users in mind. Touch gestures map directly to Zellij keybindings:

| Gesture | Action | Zellij Keybinding |
|---------|--------|-------------------|
| Single-finger swipe left/right | Focus left/right pane | `Ctrl+P` → `h/l` |
| Single-finger swipe up/down | Focus up/down pane | `Ctrl+P` → `k/j` |
| Double tap | Toggle fullscreen | `Ctrl+P` → `f` |
| Pinch in/out | Enter/exit fullscreen | `Ctrl+P` → `f` |
| Two-finger swipe left/right | Switch tabs | `Alt+h/l` |
| Three-finger tap | Toggle floating panes | `Ctrl+P` → `w` |

### Voice-Driven Development

Your phone's voice keyboard is the secret weapon. Instead of struggling with a tiny keyboard, just talk:

> "Hey Claude, add input validation to the user registration form"

Claude Code hears you, understands you, and writes the code. You review, approve, and move on with your day.

### Full Terminal Emulator

- xterm-256color support
- Nerd Font icons (Iosevka Nerd Font)
- CJK character support
- Mouse scroll in alternate screen mode
- Quick keyboard bar for Esc, Tab, Ctrl, and arrow keys

### Project Management

- Save multiple SSH connections
- Organize by projects with custom Zellij session names
- Auto-attach to existing Zellij sessions
- Secure credential storage

## Screenshots

<!-- TODO: Add screenshots -->

## Getting Started

1. **Set up your server** with Zellij and Claude Code installed
2. **Add a machine** in VibeTerminal with your SSH credentials
3. **Create a project** and specify your Zellij session name
4. **Connect** and start vibe coding!

## Requirements

- Android 8.0 (API 26) or higher
- SSH access to a server with:
  - [Zellij](https://zellij.dev/) installed
  - [Claude Code](https://claude.com/claude-code) installed and configured

### Recommended: Tailscale

For the best experience, we highly recommend using [Tailscale](https://tailscale.com/) to connect your phone and development server. Tailscale provides:

- **Secure connection** — End-to-end encrypted WireGuard tunnel
- **No port forwarding** — Works behind NAT and firewalls
- **Stable IP** — Your server always has the same address
- **Low latency** — Direct peer-to-peer connection when possible

With Tailscale, you can securely access your dev server from anywhere without exposing SSH to the public internet.

## License

MIT License
