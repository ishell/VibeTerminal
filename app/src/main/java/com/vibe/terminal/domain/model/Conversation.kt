package com.vibe.terminal.domain.model

import java.time.Instant

/**
 * Claude Code 对话段落（按用户请求分段）
 */
data class ConversationSegment(
    val id: String,
    val userMessage: String,           // 用户请求内容
    val userMessagePreview: String,    // 用户请求预览（截取前100字符）
    val assistantMessages: List<AssistantMessage>,  // 助手回复列表
    val timestamp: Instant,
    val hasThinking: Boolean = false,  // 是否包含思考过程
    val hasToolUse: Boolean = false,   // 是否使用了工具
    val hasCodeChange: Boolean = false // 是否有代码修改
)

/**
 * 助手消息
 */
data class AssistantMessage(
    val id: String,
    val contentBlocks: List<ContentBlock>,
    val timestamp: Instant
)

/**
 * 内容块类型
 */
sealed class ContentBlock {
    data class Text(val text: String) : ContentBlock()
    data class Thinking(val thinking: String) : ContentBlock()
    data class ToolUse(
        val toolName: String,
        val input: Map<String, Any?>,
        val result: String? = null
    ) : ContentBlock()
    data class ToolResult(
        val toolUseId: String,
        val content: String
    ) : ContentBlock()
}

/**
 * 对话会话（包含多个段落）
 */
data class ConversationSession(
    val sessionId: String,
    val projectPath: String,
    val segments: List<ConversationSegment>,
    val startTime: Instant,
    val endTime: Instant,
    val totalUserMessages: Int,
    val totalAssistantMessages: Int,
    val slug: String? = null  // 对话主题/标题 (如 "elegant-watching-cherny")
)

/**
 * 项目对话历史
 */
data class ProjectConversationHistory(
    val projectId: String,
    val projectPath: String,
    val sessions: List<ConversationSession>,
    val lastUpdated: Instant
)

/**
 * 对话主题（按 parentUuid 断点或时间间隔分组的多个 segment）
 */
data class ConversationTopic(
    val id: String,
    val title: String,                    // 主题标题（第一条用户消息的摘要）
    val segments: List<ConversationSegment>,
    val startTime: Instant,
    val endTime: Instant,
    val userMessageCount: Int,
    val assistantMessageCount: Int
)
