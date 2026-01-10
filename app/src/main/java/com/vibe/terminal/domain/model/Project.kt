package com.vibe.terminal.domain.model

import java.util.UUID

/**
 * AI 编程助手类型
 */
enum class AssistantType {
    CLAUDE_CODE,  // Claude Code (Anthropic)
    OPENCODE,     // OpenCode (SST)
    CODEX,        // Codex CLI (OpenAI)
    BOTH          // Auto-detect all supported assistants
}

/**
 * 项目/会话领域模型
 */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val machineId: String,
    val zellijSession: String,
    val workingDirectory: String = "~",
    val assistantType: AssistantType = AssistantType.CLAUDE_CODE,
    val lastConnected: Long = 0
) {
    /**
     * 获取Zellij附加命令
     */
    fun getZellijAttachCommand(): String {
        return "zellij attach $zellijSession --create"
    }

    /**
     * 是否曾经连接过
     */
    fun hasConnected(): Boolean = lastConnected > 0

    /**
     * 获取对话历史存储路径
     */
    fun getConversationHistoryPath(): String {
        return when (assistantType) {
            AssistantType.CLAUDE_CODE -> "~/.claude/projects/${workingDirectory.replace("/", "-")}"
            AssistantType.OPENCODE -> "~/.local/share/opencode/storage"
            AssistantType.CODEX -> "~/.codex/sessions"
            AssistantType.BOTH -> "~/.claude/projects/${workingDirectory.replace("/", "-")}" // Primary path
        }
    }
}
