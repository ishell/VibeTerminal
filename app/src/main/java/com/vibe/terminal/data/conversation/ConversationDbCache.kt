package com.vibe.terminal.data.conversation

import com.vibe.terminal.data.local.dao.ConversationDao
import com.vibe.terminal.data.local.entity.ConversationFileEntity
import com.vibe.terminal.data.local.entity.ConversationSegmentEntity
import com.vibe.terminal.data.local.entity.ConversationSessionEntity
import com.vibe.terminal.domain.model.AssistantMessage
import com.vibe.terminal.domain.model.ContentBlock
import com.vibe.terminal.domain.model.ConversationSegment
import com.vibe.terminal.domain.model.ConversationSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库缓存仓库 - 缓存解析后的对话数据
 */
@Singleton
class ConversationDbCache @Inject constructor(
    private val conversationDao: ConversationDao
) {

    /**
     * 检查是否有有效缓存
     */
    suspend fun hasValidCache(
        projectId: String,
        sessionId: String,
        fileSize: Long,
        lastModified: Long
    ): Boolean = withContext(Dispatchers.IO) {
        conversationDao.findValidCache(projectId, sessionId, fileSize, lastModified) != null
    }

    /**
     * 从缓存加载会话
     */
    suspend fun loadFromCache(sessionId: String): ConversationSession? = withContext(Dispatchers.IO) {
        val sessionEntity = conversationDao.getSessionById(sessionId) ?: return@withContext null
        val segmentEntities = conversationDao.getSegmentsBySession(sessionId)

        val segments = segmentEntities.map { entity ->
            ConversationSegment(
                id = entity.id,
                userMessage = entity.userMessage,
                userMessagePreview = entity.userMessagePreview,
                assistantMessages = deserializeAssistantMessages(entity.assistantMessagesJson),
                timestamp = Instant.ofEpochMilli(entity.timestamp),
                hasThinking = entity.hasThinking,
                hasToolUse = entity.hasToolUse,
                hasCodeChange = entity.hasCodeChange
            )
        }

        ConversationSession(
            sessionId = sessionEntity.sessionId,
            projectPath = sessionEntity.projectPath,
            segments = segments,
            startTime = Instant.ofEpochMilli(sessionEntity.startTime),
            endTime = Instant.ofEpochMilli(sessionEntity.endTime),
            totalUserMessages = sessionEntity.totalUserMessages,
            totalAssistantMessages = sessionEntity.totalAssistantMessages,
            slug = sessionEntity.slug
        )
    }

    /**
     * 保存会话到缓存
     */
    suspend fun saveToCache(
        projectId: String,
        filePath: String,
        fileSize: Long,
        lastModified: Long,
        session: ConversationSession
    ) = withContext(Dispatchers.IO) {
        val fileEntity = ConversationFileEntity(
            id = session.sessionId,
            projectId = projectId,
            filePath = filePath,
            fileSize = fileSize,
            lastModified = lastModified
        )

        val sessionEntity = ConversationSessionEntity(
            sessionId = session.sessionId,
            fileId = session.sessionId,
            projectPath = session.projectPath,
            slug = session.slug,
            startTime = session.startTime.toEpochMilli(),
            endTime = session.endTime.toEpochMilli(),
            totalUserMessages = session.totalUserMessages,
            totalAssistantMessages = session.totalAssistantMessages
        )

        val segmentEntities = session.segments.map { segment ->
            ConversationSegmentEntity(
                id = segment.id,
                sessionId = session.sessionId,
                userMessage = segment.userMessage,
                userMessagePreview = segment.userMessagePreview,
                timestamp = segment.timestamp.toEpochMilli(),
                hasThinking = segment.hasThinking,
                hasToolUse = segment.hasToolUse,
                hasCodeChange = segment.hasCodeChange,
                assistantMessagesJson = serializeAssistantMessages(segment.assistantMessages)
            )
        }

        conversationDao.updateSessionCache(fileEntity, sessionEntity, segmentEntities)
    }

    /**
     * 清除项目的所有缓存
     */
    suspend fun clearProjectCache(projectId: String) = withContext(Dispatchers.IO) {
        conversationDao.deleteFilesByProject(projectId)
    }

    // ========== 序列化/反序列化 ==========

    private fun serializeAssistantMessages(messages: List<AssistantMessage>): String {
        val jsonArray = JSONArray()
        for (msg in messages) {
            val msgObj = JSONObject()
            msgObj.put("id", msg.id)
            msgObj.put("timestamp", msg.timestamp.toEpochMilli())

            val blocksArray = JSONArray()
            for (block in msg.contentBlocks) {
                val blockObj = JSONObject()
                when (block) {
                    is ContentBlock.Text -> {
                        blockObj.put("type", "text")
                        blockObj.put("text", block.text)
                    }
                    is ContentBlock.Thinking -> {
                        blockObj.put("type", "thinking")
                        blockObj.put("thinking", block.thinking)
                    }
                    is ContentBlock.ToolUse -> {
                        blockObj.put("type", "tool_use")
                        blockObj.put("toolName", block.toolName)
                        // 简化：只保存工具名，不保存完整input
                    }
                    is ContentBlock.ToolResult -> {
                        blockObj.put("type", "tool_result")
                        blockObj.put("toolUseId", block.toolUseId)
                        blockObj.put("content", block.content.take(500)) // 限制长度
                    }
                }
                blocksArray.put(blockObj)
            }
            msgObj.put("blocks", blocksArray)
            jsonArray.put(msgObj)
        }
        return jsonArray.toString()
    }

    private fun deserializeAssistantMessages(json: String): List<AssistantMessage> {
        if (json.isBlank()) return emptyList()

        return try {
            val jsonArray = JSONArray(json)
            val messages = mutableListOf<AssistantMessage>()

            for (i in 0 until jsonArray.length()) {
                val msgObj = jsonArray.getJSONObject(i)
                val blocksArray = msgObj.getJSONArray("blocks")
                val blocks = mutableListOf<ContentBlock>()

                for (j in 0 until blocksArray.length()) {
                    val blockObj = blocksArray.getJSONObject(j)
                    val type = blockObj.getString("type")
                    when (type) {
                        "text" -> blocks.add(ContentBlock.Text(blockObj.optString("text", "")))
                        "thinking" -> blocks.add(ContentBlock.Thinking(blockObj.optString("thinking", "")))
                        "tool_use" -> blocks.add(ContentBlock.ToolUse(
                            toolName = blockObj.optString("toolName", ""),
                            input = emptyMap()
                        ))
                        "tool_result" -> blocks.add(ContentBlock.ToolResult(
                            toolUseId = blockObj.optString("toolUseId", ""),
                            content = blockObj.optString("content", "")
                        ))
                    }
                }

                messages.add(AssistantMessage(
                    id = msgObj.getString("id"),
                    contentBlocks = blocks,
                    timestamp = Instant.ofEpochMilli(msgObj.getLong("timestamp"))
                ))
            }
            messages
        } catch (e: Exception) {
            emptyList()
        }
    }
}
