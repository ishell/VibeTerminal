package com.vibe.terminal.data.conversation

import com.vibe.terminal.domain.model.AssistantMessage
import com.vibe.terminal.domain.model.ContentBlock
import com.vibe.terminal.domain.model.ConversationSegment
import com.vibe.terminal.domain.model.ConversationSession
import com.vibe.terminal.domain.model.ConversationTopic
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.Duration

/**
 * Claude Code 对话 JSONL 解析器
 */
object ConversationParser {

    // 时间间隔阈值：超过此间隔认为是新主题（20分钟）
    private val TIME_GAP_THRESHOLD = Duration.ofMinutes(20)

    /**
     * 解析 JSONL 内容为对话会话
     */
    fun parseJsonl(jsonlContent: String, sessionId: String, projectPath: String): ConversationSession {
        val lines = jsonlContent.lines().filter { it.isNotBlank() }
        val segments = mutableListOf<ConversationSegment>()
        val allUuids = mutableSetOf<String>()
        val rootSegmentIds = mutableSetOf<String>()  // parentUuid 断点的 segment IDs

        // 第一遍：收集所有 UUID
        for (line in lines) {
            try {
                val json = JSONObject(line)
                val uuid = json.optString("uuid", "")
                if (uuid.isNotBlank()) {
                    allUuids.add(uuid)
                }
            } catch (e: Exception) {
                continue
            }
        }

        var currentUserMessage: UserMessageData? = null
        var currentAssistantMessages = mutableListOf<AssistantMessage>()
        var startTime: Instant? = null
        var endTime: Instant? = null
        var totalUserMessages = 0
        var totalAssistantMessages = 0
        var latestSlug: String? = null

        for (line in lines) {
            try {
                val json = JSONObject(line)
                val type = json.optString("type", "")
                val timestamp = parseTimestamp(json.optString("timestamp", ""))
                val uuid = json.optString("uuid", "")
                val parentUuid = json.optString("parentUuid", "")

                // 提取 slug (对话主题)
                val slug = json.optString("slug", "").takeIf { it.isNotBlank() }
                if (slug != null) {
                    latestSlug = slug
                }

                if (startTime == null && timestamp != null) {
                    startTime = timestamp
                }
                if (timestamp != null) {
                    endTime = timestamp
                }

                when (type) {
                    "user" -> {
                        // 保存上一个段落
                        currentUserMessage?.let { userMsg ->
                            segments.add(createSegment(userMsg, currentAssistantMessages))
                        }

                        // 检查是否是根节点（parentUuid 不在列表中）
                        val isRoot = parentUuid.isBlank() || parentUuid !in allUuids

                        // 开始新段落
                        val message = json.optJSONObject("message")
                        val content = extractUserMessageContent(message)

                        currentUserMessage = UserMessageData(
                            id = uuid,
                            content = content,
                            timestamp = timestamp ?: Instant.now(),
                            isRootMessage = isRoot
                        )

                        if (isRoot) {
                            rootSegmentIds.add(uuid)
                        }

                        currentAssistantMessages = mutableListOf()
                        totalUserMessages++
                    }
                    "assistant" -> {
                        val message = json.optJSONObject("message")
                        val contentArray = message?.optJSONArray("content")

                        if (contentArray != null) {
                            val assistantMsg = parseAssistantMessage(uuid, contentArray, timestamp)
                            currentAssistantMessages.add(assistantMsg)
                            totalAssistantMessages++
                        }
                    }
                }
            } catch (e: Exception) {
                continue
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
            slug = latestSlug
        )
    }

    /**
     * 按 parentUuid 断点分组（会话恢复点）
     */
    fun groupBySessionBreaks(sessions: List<ConversationSession>): List<ConversationTopic> {
        val allSegments = sessions.flatMap { it.segments }.sortedBy { it.timestamp }
        if (allSegments.isEmpty()) return emptyList()

        // 重新解析以获取 root 信息
        // 由于我们现在没有存储 isRoot 信息，需要重新实现
        // 简化方案：基于 segments 列表，检查 parentUuid 不存在的情况
        // 但由于 segment 没有存储 parentUuid，我们使用另一种方式：
        // 检查 "This session is being continued" 文本或大时间跳跃

        val topics = mutableListOf<ConversationTopic>()
        var currentTopicSegments = mutableListOf<ConversationSegment>()

        for (segment in allSegments) {
            val isNewTopic = currentTopicSegments.isEmpty() ||
                isSessionContinuation(segment.userMessage) ||
                isLargeTimeGap(currentTopicSegments.lastOrNull()?.timestamp, segment.timestamp, Duration.ofHours(2))

            if (isNewTopic && currentTopicSegments.isNotEmpty()) {
                // 保存当前主题
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

    /**
     * 按时间间隔分组（默认20分钟）
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

    private fun isSessionContinuation(message: String): Boolean {
        return message.startsWith("This session is being continued")
    }

    private fun isLargeTimeGap(lastTime: Instant?, currentTime: Instant, threshold: Duration): Boolean {
        if (lastTime == null) return false
        return Duration.between(lastTime, currentTime) > threshold
    }

    private fun createTopic(segments: List<ConversationSegment>): ConversationTopic {
        val firstSegment = segments.first()
        val lastSegment = segments.last()

        // 找到第一条非 "session continuation" 的消息作为标题
        val titleSegment = segments.firstOrNull {
            !isSessionContinuation(it.userMessage) && it.userMessage.length > 10
        } ?: firstSegment

        val title = titleSegment.userMessagePreview.let {
            if (it.length > 60) it.take(60) + "..." else it
        }

        return ConversationTopic(
            id = firstSegment.id,
            title = title,
            segments = segments,
            startTime = firstSegment.timestamp,
            endTime = lastSegment.timestamp,
            userMessageCount = segments.size,
            assistantMessageCount = segments.sumOf { it.assistantMessages.size }
        )
    }

    private fun extractUserMessageContent(message: JSONObject?): String {
        if (message == null) return ""

        val content = message.opt("content")
        return when (content) {
            is String -> content
            is JSONArray -> {
                // 遍历数组找到 text 类型的内容
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val item = content.optJSONObject(i)
                    if (item?.optString("type") == "text") {
                        sb.append(item.optString("text", ""))
                    }
                }
                sb.toString()
            }
            else -> ""
        }
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
                block.toolName in listOf("Edit", "Write", "Bash")
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

    private fun parseAssistantMessage(
        uuid: String,
        contentArray: JSONArray,
        timestamp: Instant?
    ): AssistantMessage {
        val blocks = mutableListOf<ContentBlock>()

        for (i in 0 until contentArray.length()) {
            val item = contentArray.get(i)

            when (item) {
                is JSONObject -> {
                    val blockType = item.optString("type", "")
                    when (blockType) {
                        "text" -> {
                            blocks.add(ContentBlock.Text(item.optString("text", "")))
                        }
                        "thinking" -> {
                            blocks.add(ContentBlock.Thinking(item.optString("thinking", "")))
                        }
                        "tool_use" -> {
                            val toolName = item.optString("name", "")
                            val input = jsonObjectToMap(item.optJSONObject("input"))
                            blocks.add(ContentBlock.ToolUse(toolName, input))
                        }
                        "tool_result" -> {
                            val toolUseId = item.optString("tool_use_id", "")
                            val content = when (val c = item.opt("content")) {
                                is String -> c
                                is JSONArray -> {
                                    val sb = StringBuilder()
                                    for (j in 0 until c.length()) {
                                        val obj = c.optJSONObject(j)
                                        if (obj?.optString("type") == "text") {
                                            sb.append(obj.optString("text", ""))
                                        }
                                    }
                                    sb.toString()
                                }
                                else -> ""
                            }
                            blocks.add(ContentBlock.ToolResult(toolUseId, content))
                        }
                    }
                }
                is String -> {
                    blocks.add(ContentBlock.Text(item))
                }
            }
        }

        return AssistantMessage(
            id = uuid,
            contentBlocks = blocks,
            timestamp = timestamp ?: Instant.now()
        )
    }

    private fun parseTimestamp(timestampStr: String): Instant? {
        return try {
            Instant.parse(timestampStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun jsonObjectToMap(jsonObject: JSONObject?): Map<String, Any?> {
        if (jsonObject == null) return emptyMap()

        val map = mutableMapOf<String, Any?>()
        jsonObject.keys().forEach { key ->
            map[key] = jsonObject.opt(key)
        }
        return map
    }

    private data class UserMessageData(
        val id: String,
        val content: String,
        val timestamp: Instant,
        val isRootMessage: Boolean = false
    )
}
