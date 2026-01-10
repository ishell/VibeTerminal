package com.vibe.terminal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vibe.terminal.data.local.entity.TaskCompletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskCompletionDao {

    /**
     * 获取项目的所有任务完成状态
     */
    @Query("SELECT * FROM task_completions WHERE projectId = :projectId")
    fun getCompletionsByProject(projectId: String): Flow<List<TaskCompletionEntity>>

    /**
     * 获取项目的所有任务完成状态（非 Flow）
     */
    @Query("SELECT * FROM task_completions WHERE projectId = :projectId")
    suspend fun getCompletionsByProjectOnce(projectId: String): List<TaskCompletionEntity>

    /**
     * 通过 taskHash 查询完成状态
     */
    @Query("SELECT * FROM task_completions WHERE projectId = :projectId AND taskHash = :taskHash LIMIT 1")
    suspend fun getCompletionByHash(projectId: String, taskHash: String): TaskCompletionEntity?

    /**
     * 插入或更新任务完成状态
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: TaskCompletionEntity)

    /**
     * 批量插入任务完成状态
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletions(completions: List<TaskCompletionEntity>)

    /**
     * 更新任务完成状态
     */
    @Query("""
        UPDATE task_completions
        SET isCompleted = :isCompleted,
            completedAt = :completedAt,
            updatedAt = :updatedAt
        WHERE projectId = :projectId AND taskHash = :taskHash
    """)
    suspend fun updateCompletionStatus(
        projectId: String,
        taskHash: String,
        isCompleted: Boolean,
        completedAt: Long?,
        updatedAt: Long = System.currentTimeMillis()
    )

    /**
     * 删除项目的所有任务完成状态
     */
    @Query("DELETE FROM task_completions WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)

    /**
     * 删除特定任务的完成状态
     */
    @Query("DELETE FROM task_completions WHERE projectId = :projectId AND taskHash = :taskHash")
    suspend fun deleteByHash(projectId: String, taskHash: String)

    /**
     * 获取已删除的任务（非 Flow）
     */
    @Query("SELECT * FROM task_completions WHERE projectId = :projectId AND isDeleted = 1")
    suspend fun getDeletedTasksByProjectOnce(projectId: String): List<TaskCompletionEntity>

    /**
     * 标记任务为已删除
     */
    @Query("""
        UPDATE task_completions
        SET isDeleted = 1,
            updatedAt = :updatedAt
        WHERE projectId = :projectId AND taskHash = :taskHash
    """)
    suspend fun markAsDeleted(
        projectId: String,
        taskHash: String,
        updatedAt: Long = System.currentTimeMillis()
    )
}
