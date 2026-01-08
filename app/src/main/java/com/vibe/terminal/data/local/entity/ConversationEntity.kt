package com.vibe.terminal.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 对话文件缓存元数据
 */
@Entity(
    tableName = "conversation_files",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ConversationFileEntity(
    @PrimaryKey
    val id: String,                    // sessionId
    val projectId: String,
    val filePath: String,
    val fileSize: Long,
    val lastModified: Long,            // 文件最后修改时间
    val cachedAt: Long = System.currentTimeMillis()
)

/**
 * 对话会话缓存
 */
@Entity(
    tableName = "conversation_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ConversationFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fileId")]
)
data class ConversationSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val fileId: String,
    val projectPath: String,
    val slug: String?,
    val startTime: Long,
    val endTime: Long,
    val totalUserMessages: Int,
    val totalAssistantMessages: Int
)

/**
 * 对话段落缓存
 */
@Entity(
    tableName = "conversation_segments",
    foreignKeys = [
        ForeignKey(
            entity = ConversationSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("timestamp")]
)
data class ConversationSegmentEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val userMessage: String,
    val userMessagePreview: String,
    val timestamp: Long,
    val hasThinking: Boolean,
    val hasToolUse: Boolean,
    val hasCodeChange: Boolean,
    val assistantMessagesJson: String   // 序列化的 JSON，避免多表关联
)
