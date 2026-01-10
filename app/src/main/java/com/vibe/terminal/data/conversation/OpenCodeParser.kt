package com.vibe.terminal.data.conversation

import com.vibe.terminal.domain.model.AssistantMessage
import com.vibe.terminal.domain.model.ContentBlock
import com.vibe.terminal.domain.model.ConversationSegment
import com.vibe.terminal.domain.model.ConversationSession
import com.vibe.terminal.domain.model.ConversationTopic
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * OpenCode 对话解析器
 *
 * OpenCode 存储结构:
 * ~/.local/share/opencode/storage/
 * ├── session/<project>/ses_xxx.json    # 会话元数据
 * ├── message/ses_xxx/msg_xxx.json      # 消息
 * └── part/msg_xxx/part_xxx.json        # 消息部件
 */
object OpenCodeParser {

    // 时间间隔阈值：超过此间隔认为是新主题（20分钟）
    private val TIME_GAP_THRESHOLD = Duration.ofMinutes(20)

    /**
     * 解析 OpenCode 会话 JSON
     *
     * @param sessionJson 会话 JSON 内容
     * @param messagesJson 消息列表 JSON 内容 (array)
     * @param partsJsonMap 消息 ID -> 部件 JSON 列表
     */
    fun parseSession(
        sessionJson: String,
        messagesJson: String,
        partsJsonMap: Map<String, List<String>>,
        projectPath: String
    ): ConversationSession {
        val sessionObj = JSONObject(sessionJson)
        val sessionId = sessionObj.optString("id", "")
        val title = sessionObj.optString("title", "")

        val messagesArray = try {
            JSONArray(messagesJson)
        } catch (e: Exception) {
            JSONArray()
        }

        val segments = mutableListOf<ConversationSegment>()
        var startTime: Instant? = null
        var endTime: Instant? = null
        var totalUserMessages = 0
        var totalAssistantMessages = 0

        var currentUserMessage: UserMessageData? = null
        var currentAssistantMessages = mutableListOf<AssistantMessage>()

        for (i in 0 until messagesArray.length()) {
            val messageObj = messagesArray.optJSONObject(i) ?: continue
            val messageId = messageObj.optString("id", "")
            val role = messageObj.optString("role", "")
            val timestamp = parseTimestamp(messageObj.optString("createdAt", ""))

            if (startTime == null && timestamp != null) {
                startTime = timestamp
            }
            if (timestamp != null) {
                endTime = timestamp
            }

            // 获取该消息的部件
            val partsJsonList = partsJsonMap[messageId] ?: emptyList()
            val contentBlocks = parseMessageParts(partsJsonList)

            when (role) {
                "user" -> {
                    // 保存上一个段落
                    currentUserMessage?.let { userMsg ->
                        segments.add(createSegment(userMsg, currentAssistantMessages))
                    }

                    // 从部件中提取用户消息文本
                    val userText = contentBlocks.filterIsInstance<ContentBlock.Text>()
                        .joinToString("\n") { it.text }

                    currentUserMessage = UserMessageData(
                        id = messageId,
                        content = userText,
                        timestamp = timestamp ?: Instant.now(),
                        isRootMessage = false
                    )
                    currentAssistantMessages = mutableListOf()
                    totalUserMessages++
                }
                "assistant" -> {
                    val assistantMsg = AssistantMessage(
                        id = messageId,
                        contentBlocks = contentBlocks,
                        timestamp = timestamp ?: Instant.now()
                    )
                    currentAssistantMessages.add(assistantMsg)
                    totalAssistantMessages++
                }
            }
        }

        // 保存最后一个段落
        currentUserMessage?.let { userMsg ->
            segments.add(createSegment(userMsg, currentAssistantMessages))
        }

        return ConversationSession(
            sessionId = sessionId,
            projectPath = projectPath,
            segments = segments,
            startTime = startTime ?: Instant.now(),
            endTime = endTime ?: Instant.now(),
            totalUserMessages = totalUserMessages,
            totalAssistantMessages = totalAssistantMessages,
            slug = title.takeIf { it.isNotBlank() }
        )
    }

    /**
     * 解析消息部件列表
     */
    private fun parseMessageParts(partsJsonList: List<String>): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()

        for (partJson in partsJsonList) {
            try {
                val partObj = JSONObject(partJson)
                val partType = partObj.optString("type", "")

                when (partType) {
                    "text" -> {
                        val text = partObj.optString("text", "")
                        if (text.isNotBlank()) {
                            blocks.add(ContentBlock.Text(text))
                        }
                    }
                    "reasoning", "thinking" -> {
                        val thinking = partObj.optString("text", "")
                            .ifBlank { partObj.optString("reasoning", "") }
                        if (thinking.isNotBlank()) {
                            blocks.add(ContentBlock.Thinking(thinking))
                        }
                    }
                    "tool-invocation", "tool_use" -> {
                        val toolName = partObj.optString("toolName", "")
                            .ifBlank { partObj.optString("name", "") }
                        val input = parseToolInput(partObj)
                        val result = partObj.optString("result", "").takeIf { it.isNotBlank() }
                        blocks.add(ContentBlock.ToolUse(toolName, input, result))
                    }
                    "tool-result", "tool_result" -> {
                        val toolUseId = partObj.optString("toolInvocationId", "")
                            .ifBlank { partObj.optString("tool_use_id", "") }
                        val content = partObj.optString("result", "")
                            .ifBlank { partObj.optString("content", "") }
                        blocks.add(ContentBlock.ToolResult(toolUseId, content))
                    }
                    "file" -> {
                        val filePath = partObj.optString("path", "")
                        blocks.add(ContentBlock.Text("[File: $filePath]"))
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }

        return blocks
    }

    /**
     * 解析工具输入参数
     */
    private fun parseToolInput(partObj: JSONObject): Map<String, Any?> {
        val input = partObj.optJSONObject("input")
            ?: partObj.optJSONObject("args")
            ?: return emptyMap()

        val map = mutableMapOf<String, Any?>()
        input.keys().forEach { key ->
            map[key] = input.opt(key)
        }
        return map
    }

    /**
     * 按时间间隔分组
     */
    fun groupByTimeGap(
        sessions: List<ConversationSession>,
        gapThreshold: Duration = TIME_GAP_THRESHOLD
    ): List<ConversationTopic> {
        val allSegments = sessions.flatMap { it.segments }.sortedBy { it.timestamp }
        if (allSegments.isEmpty()) return emptyList()

        val topics = mutableListOf<ConversationTopic>()
        var currentTopicSegments = mutableListOf<ConversationSegment>()

        for (segment in allSegments) {
            val isNewTopic = currentTopicSegments.isEmpty() ||
                isLargeTimeGap(currentTopicSegments.lastOrNull()?.timestamp, segment.timestamp, gapThreshold)

            if (isNewTopic && currentTopicSegments.isNotEmpty()) {
                topics.add(createTopic(currentTopicSegments))
                currentTopicSegments = mutableListOf()
            }

            currentTopicSegments.add(segment)
        }

        // 保存最后一个主题
        if (currentTopicSegments.isNotEmpty()) {
            topics.add(createTopic(currentTopicSegments))
        }

        return topics.sortedByDescending { it.startTime }
    }

    private fun isLargeTimeGap(lastTime: Instant?, currentTime: Instant, threshold: Duration): Boolean {
        if (lastTime == null) return false
        return Duration.between(lastTime, currentTime) > threshold
    }

    private fun createTopic(segments: List<ConversationSegment>): ConversationTopic {
        val firstSegment = segments.first()
        val lastSegment = segments.last()

        val titleSegment = segments.firstOrNull {
            it.userMessage.isNotBlank() && it.userMessage.length > 5
        }

        val title = when {
            titleSegment != null -> {
                val preview = titleSegment.userMessagePreview
                if (preview.length > 60) preview.take(60) + "..." else preview
            }
            else -> {
                val toolNames = segments
                    .flatMap { it.assistantMessages }
                    .flatMap { it.contentBlocks }
                    .filterIsInstance<ContentBlock.ToolUse>()
                    .map { it.toolName }
                    .distinct()
                    .take(3)

                if (toolNames.isNotEmpty()) {
                    "[Tool: ${toolNames.joinToString(", ")}]"
                } else {
                    "[Session]"
                }
            }
        }

        return ConversationTopic(
            id = UUID.randomUUID().toString(),
            title = title,
            segments = segments,
            startTime = firstSegment.timestamp,
            endTime = lastSegment.timestamp,
            userMessageCount = segments.count { it.userMessage.isNotBlank() },
            assistantMessageCount = segments.sumOf { it.assistantMessages.size }
        )
    }

    private fun createSegment(
        userMessage: UserMessageData,
        assistantMessages: List<AssistantMessage>
    ): ConversationSegment {
        val preview = userMessage.content.take(100).let {
            if (userMessage.content.length > 100) "$it..." else it
        }.replace("\n", " ")

        val hasThinking = assistantMessages.any { msg ->
            msg.contentBlocks.any { it is ContentBlock.Thinking }
        }
        val hasToolUse = assistantMessages.any { msg ->
            msg.contentBlocks.any { it is ContentBlock.ToolUse }
        }
        val hasCodeChange = assistantMessages.any { msg ->
            msg.contentBlocks.any { block ->
                block is ContentBlock.ToolUse &&
                block.toolName.lowercase() in listOf("edit", "write", "bash", "shell", "file")
            }
        }

        return ConversationSegment(
            id = userMessage.id,
            userMessage = userMessage.content,
            userMessagePreview = preview,
            assistantMessages = assistantMessages,
            timestamp = userMessage.timestamp,
            hasThinking = hasThinking,
            hasToolUse = hasToolUse,
            hasCodeChange = hasCodeChange
        )
    }

    private fun parseTimestamp(timestampStr: String): Instant? {
        if (timestampStr.isBlank()) return null
        return try {
            // OpenCode 使用 ISO 格式或 Unix 毫秒时间戳
            if (timestampStr.contains("T") || timestampStr.contains("-")) {
                Instant.parse(timestampStr)
            } else {
                Instant.ofEpochMilli(timestampStr.toLong())
            }
        } catch (e: Exception) {
            null
        }
    }

    private data class UserMessageData(
        val id: String,
        val content: String,
        val timestamp: Instant,
        val isRootMessage: Boolean = false
    )
}
