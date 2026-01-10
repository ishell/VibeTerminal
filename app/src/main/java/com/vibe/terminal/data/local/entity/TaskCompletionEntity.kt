package com.vibe.terminal.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地任务完成状态实体
 * 用于存储用户在 App 中标记的任务完成状态
 * 与远程 roadmap 文件独立，本地状态优先
 */
@Entity(
    tableName = "task_completions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("taskHash")]
)
data class TaskCompletionEntity(
    @PrimaryKey
    val id: String,                    // UUID
    val projectId: String,             // 关联的项目 ID
    val taskHash: String,              // SHA256(taskContent) 用于匹配任务
    val taskContent: String,           // 任务内容（用于显示和相似度匹配）
    val isCompleted: Boolean = false,  // 是否完成
    val completedAt: Long? = null,     // 完成时间
    val isDeleted: Boolean = false,    // 是否已删除（从列表中隐藏）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
