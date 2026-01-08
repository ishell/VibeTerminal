package com.vibe.terminal.data.conversation

import com.vibe.terminal.domain.model.AssistantMessage
import com.vibe.terminal.domain.model.ContentBlock
import com.vibe.terminal.domain.model.ConversationSegment
import com.vibe.terminal.domain.model.ConversationSession
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Claude Code 对话 JSONL 解析器
 */
object ConversationParser {

    /**
     * 解析 JSONL 内容为对话会话
     */
    fun parseJsonl(jsonlContent: String, sessionId: String, projectPath: String): ConversationSession {
        val lines = jsonlContent.lines().filter { it.isNotBlank() }
        val segments = mutableListOf<ConversationSegment>()

        var currentUserMessage: UserMessageData? = null
        var currentAssistantMessages = mutableListOf<AssistantMessage>()
        var startTime: Instant? = null
        var endTime: Instant? = null
        var totalUserMessages = 0
        var totalAssistantMessages = 0

        for (line in lines) {
            try {
                val json = JSONObject(line)
                val type = json.optString("type", "")
                val timestamp = parseTimestamp(json.optString("timestamp", ""))

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

                        // 开始新段落
                        val message = json.optJSONObject("message")
                        val content = message?.optString("content", "") ?: ""
                        val uuid = json.optString("uuid", "")

                        currentUserMessage = UserMessageData(
                            id = uuid,
                            content = content,
                            timestamp = timestamp ?: Instant.now()
                        )
                        currentAssistantMessages = mutableListOf()
                        totalUserMessages++
                    }
                    "assistant" -> {
                        val message = json.optJSONObject("message")
                        val contentArray = message?.optJSONArray("content")
                        val uuid = json.optString("uuid", "")

                        if (contentArray != null) {
                            val assistantMsg = parseAssistantMessage(uuid, contentArray, timestamp)
                            currentAssistantMessages.add(assistantMsg)
                            totalAssistantMessages++
                        }
                    }
                }
            } catch (e: Exception) {
                // 跳过解析失败的行
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
            totalAssistantMessages = totalAssistantMessages
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
        val timestamp: Instant
    )
}
