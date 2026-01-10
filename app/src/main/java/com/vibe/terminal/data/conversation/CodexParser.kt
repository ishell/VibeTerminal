package com.vibe.terminal.data.conversation

import android.util.Log
import com.vibe.terminal.domain.model.AssistantMessage
import com.vibe.terminal.domain.model.ContentBlock
import com.vibe.terminal.domain.model.ConversationSegment
import com.vibe.terminal.domain.model.ConversationSession
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * OpenAI Codex CLI 对话 JSONL 解析器
 *
 * Codex CLI 存储对话在 ~/.codex/sessions/YYYY/MM/DD/rollout-*.jsonl
 * 格式与 Claude Code 类似，使用 line-delimited JSON
 */
object CodexParser {

    private const val TAG = "CodexParser"

    /**
     * 解析 Codex JSONL 内容为对话会话
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
        var sessionSlug: String? = null
        var workingDirectory: String? = null

        for (line in lines) {
            try {
                val json = JSONObject(line)
                val type = json.optString("type", "")
                val role = json.optString("role", "")  // Some formats use role instead of type
                val timestamp = parseTimestamp(json)
                val id = json.optString("id", json.optString("uuid", System.nanoTime().toString()))

                // Extract environment context for working directory
                val envContext = json.optJSONObject("environment_context")
                if (envContext != null) {
                    workingDirectory = envContext.optString("cwd", envContext.optString("working_directory", ""))
                }

                // Extract session summary info
                if (type == "session_summary") {
                    val summarySessionId = json.optString("session_id", "")
                    if (summarySessionId.isNotBlank()) {
                        sessionSlug = summarySessionId
                    }
                    continue
                }

                if (startTime == null && timestamp != null) {
                    startTime = timestamp
                }
                if (timestamp != null) {
                    endTime = timestamp
                }

                // Handle different event types
                when {
                    type == "user" || role == "user" -> {
                        // Save previous segment
                        currentUserMessage?.let { userMsg ->
                            segments.add(createSegment(userMsg, currentAssistantMessages))
                        }

                        val content = extractMessageContent(json)
                        currentUserMessage = UserMessageData(
                            id = id,
                            content = content,
                            timestamp = timestamp ?: Instant.now()
                        )
                        currentAssistantMessages = mutableListOf()
                        totalUserMessages++
                    }
                    type == "assistant" || role == "assistant" -> {
                        val assistantMsg = parseAssistantMessage(id, json, timestamp)
                        if (assistantMsg != null) {
                            currentAssistantMessages.add(assistantMsg)
                            totalAssistantMessages++
                        }
                    }
                    type == "message" -> {
                        // Generic message type - check role inside
                        val msgRole = json.optString("role", "")
                        val content = extractMessageContent(json)

                        when (msgRole) {
                            "user" -> {
                                currentUserMessage?.let { userMsg ->
                                    segments.add(createSegment(userMsg, currentAssistantMessages))
                                }
                                currentUserMessage = UserMessageData(
                                    id = id,
                                    content = content,
                                    timestamp = timestamp ?: Instant.now()
                                )
                                currentAssistantMessages = mutableListOf()
                                totalUserMessages++
                            }
                            "assistant" -> {
                                val assistantMsg = parseAssistantMessage(id, json, timestamp)
                                if (assistantMsg != null) {
                                    currentAssistantMessages.add(assistantMsg)
                                    totalAssistantMessages++
                                }
                            }
                        }
                    }
                    type == "tool_call" || type == "function_call" -> {
                        // Tool/function call event
                        val toolBlock = parseToolCall(json)
                        if (toolBlock != null && currentAssistantMessages.isNotEmpty()) {
                            // Append to last assistant message
                            val lastMsg = currentAssistantMessages.last()
                            val updatedBlocks = lastMsg.contentBlocks + toolBlock
                            currentAssistantMessages[currentAssistantMessages.lastIndex] =
                                lastMsg.copy(contentBlocks = updatedBlocks)
                        } else if (toolBlock != null) {
                            // Create new assistant message with tool call
                            currentAssistantMessages.add(
                                AssistantMessage(
                                    id = id,
                                    contentBlocks = listOf(toolBlock),
                                    timestamp = timestamp ?: Instant.now()
                                )
                            )
                            totalAssistantMessages++
                        }
                    }
                    type == "tool_result" || type == "function_result" -> {
                        // Tool result event
                        val toolResultBlock = parseToolResult(json)
                        if (toolResultBlock != null && currentAssistantMessages.isNotEmpty()) {
                            val lastMsg = currentAssistantMessages.last()
                            val updatedBlocks = lastMsg.contentBlocks + toolResultBlock
                            currentAssistantMessages[currentAssistantMessages.lastIndex] =
                                lastMsg.copy(contentBlocks = updatedBlocks)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse line: ${e.message}")
                continue
            }
        }

        // Save last segment
        currentUserMessage?.let { userMsg ->
            segments.add(createSegment(userMsg, currentAssistantMessages))
        }

        // If no segments found, try alternative parsing
        if (segments.isEmpty() && lines.isNotEmpty()) {
            val fallbackSession = tryFallbackParsing(lines, sessionId, projectPath)
            if (fallbackSession != null) {
                return fallbackSession
            }
        }

        return ConversationSession(
            sessionId = sessionId,
            projectPath = workingDirectory ?: projectPath,
            segments = segments,
            startTime = startTime ?: Instant.now(),
            endTime = endTime ?: Instant.now(),
            totalUserMessages = totalUserMessages,
            totalAssistantMessages = totalAssistantMessages,
            slug = sessionSlug
        )
    }

    /**
     * Fallback parsing for unknown formats
     */
    private fun tryFallbackParsing(
        lines: List<String>,
        sessionId: String,
        projectPath: String
    ): ConversationSession? {
        val segments = mutableListOf<ConversationSegment>()
        var startTime: Instant? = null
        var endTime: Instant? = null

        for (line in lines) {
            try {
                val json = JSONObject(line)

                // Try to extract any text content
                val content = json.optString("content", "")
                    .ifBlank { json.optString("text", "") }
                    .ifBlank { json.optString("message", "") }

                val timestamp = parseTimestamp(json)

                if (startTime == null && timestamp != null) {
                    startTime = timestamp
                }
                if (timestamp != null) {
                    endTime = timestamp
                }

                if (content.isNotBlank()) {
                    val id = json.optString("id", System.nanoTime().toString())
                    segments.add(
                        ConversationSegment(
                            id = id,
                            userMessage = content,
                            userMessagePreview = content.take(100),
                            assistantMessages = emptyList(),
                            timestamp = timestamp ?: Instant.now(),
                            hasThinking = false,
                            hasToolUse = false,
                            hasCodeChange = false
                        )
                    )
                }
            } catch (e: Exception) {
                continue
            }
        }

        return if (segments.isNotEmpty()) {
            ConversationSession(
                sessionId = sessionId,
                projectPath = projectPath,
                segments = segments,
                startTime = startTime ?: Instant.now(),
                endTime = endTime ?: Instant.now(),
                totalUserMessages = segments.size,
                totalAssistantMessages = 0,
                slug = null
            )
        } else null
    }

    private fun extractMessageContent(json: JSONObject): String {
        // Try different content locations
        val message = json.optJSONObject("message")
        if (message != null) {
            val content = message.opt("content")
            return extractContentFromValue(content)
        }

        val content = json.opt("content")
        return extractContentFromValue(content)
    }

    private fun extractContentFromValue(content: Any?): String {
        return when (content) {
            is String -> content
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    when (val item = content.get(i)) {
                        is String -> sb.append(item)
                        is JSONObject -> {
                            val type = item.optString("type", "")
                            if (type == "text" || type.isBlank()) {
                                sb.append(item.optString("text", ""))
                            }
                        }
                    }
                }
                sb.toString()
            }
            else -> ""
        }
    }

    private fun parseAssistantMessage(
        id: String,
        json: JSONObject,
        timestamp: Instant?
    ): AssistantMessage? {
        val blocks = mutableListOf<ContentBlock>()

        // Extract content
        val message = json.optJSONObject("message")
        val contentSource = message?.optJSONArray("content") ?: json.optJSONArray("content")

        if (contentSource != null) {
            for (i in 0 until contentSource.length()) {
                when (val item = contentSource.get(i)) {
                    is JSONObject -> {
                        val blockType = item.optString("type", "")
                        when (blockType) {
                            "text" -> {
                                blocks.add(ContentBlock.Text(item.optString("text", "")))
                            }
                            "thinking", "reasoning" -> {
                                val thinking = item.optString("thinking", "")
                                    .ifBlank { item.optString("content", "") }
                                blocks.add(ContentBlock.Thinking(thinking))
                            }
                            "tool_use", "function_call", "tool_call" -> {
                                val toolName = item.optString("name", item.optString("function", ""))
                                val input = jsonObjectToMap(
                                    item.optJSONObject("input")
                                        ?: item.optJSONObject("arguments")
                                        ?: item.optJSONObject("parameters")
                                )
                                blocks.add(ContentBlock.ToolUse(toolName, input))
                            }
                            "tool_result", "function_result" -> {
                                val toolUseId = item.optString("tool_use_id", item.optString("call_id", ""))
                                val resultContent = extractContentFromValue(item.opt("content"))
                                blocks.add(ContentBlock.ToolResult(toolUseId, resultContent))
                            }
                        }
                    }
                    is String -> {
                        blocks.add(ContentBlock.Text(item))
                    }
                }
            }
        } else {
            // Try simple content string
            val simpleContent = extractMessageContent(json)
            if (simpleContent.isNotBlank()) {
                blocks.add(ContentBlock.Text(simpleContent))
            }
        }

        return if (blocks.isNotEmpty()) {
            AssistantMessage(
                id = id,
                contentBlocks = blocks,
                timestamp = timestamp ?: Instant.now()
            )
        } else null
    }

    private fun parseToolCall(json: JSONObject): ContentBlock.ToolUse? {
        val name = json.optString("name", json.optString("function", ""))
        if (name.isBlank()) return null

        val input = jsonObjectToMap(
            json.optJSONObject("input")
                ?: json.optJSONObject("arguments")
                ?: json.optJSONObject("parameters")
        )
        return ContentBlock.ToolUse(name, input)
    }

    private fun parseToolResult(json: JSONObject): ContentBlock.ToolResult? {
        val toolUseId = json.optString("tool_use_id", json.optString("call_id", ""))
        val content = extractContentFromValue(json.opt("content"))
            .ifBlank { json.optString("result", "") }
            .ifBlank { json.optString("output", "") }

        return if (content.isNotBlank()) {
            ContentBlock.ToolResult(toolUseId, content)
        } else null
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
                block.toolName.lowercase() in listOf("edit", "write", "bash", "shell", "exec", "run")
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

    private fun parseTimestamp(json: JSONObject): Instant? {
        // Try various timestamp fields
        val timestampStr = json.optString("timestamp", "")
            .ifBlank { json.optString("created_at", "") }
            .ifBlank { json.optString("time", "") }
            .ifBlank { json.optString("ts", "") }

        if (timestampStr.isNotBlank()) {
            return try {
                Instant.parse(timestampStr)
            } catch (e: Exception) {
                // Try parsing as epoch millis/seconds
                val num = timestampStr.toLongOrNull()
                if (num != null) {
                    if (num > 1_000_000_000_000L) {
                        Instant.ofEpochMilli(num)
                    } else {
                        Instant.ofEpochSecond(num)
                    }
                } else null
            }
        }

        // Try numeric timestamp
        val epochMs = json.optLong("timestamp_ms", 0L)
        if (epochMs > 0) {
            return Instant.ofEpochMilli(epochMs)
        }

        val epochSec = json.optLong("timestamp", 0L)
        if (epochSec > 1_000_000_000L) {
            return Instant.ofEpochSecond(epochSec)
        }

        return null
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
