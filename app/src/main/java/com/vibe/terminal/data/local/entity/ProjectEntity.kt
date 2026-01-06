package com.vibe.terminal.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 项目/会话实体
 */
@Entity(
    tableName = "projects",
    foreignKeys = [
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("machineId")]
)
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val machineId: String,
    val zellijSession: String,
    val workingDirectory: String = "~",
    val lastConnected: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
