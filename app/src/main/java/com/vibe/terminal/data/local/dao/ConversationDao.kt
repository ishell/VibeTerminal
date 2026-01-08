package com.vibe.terminal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.vibe.terminal.data.local.entity.ConversationFileEntity
import com.vibe.terminal.data.local.entity.ConversationSegmentEntity
import com.vibe.terminal.data.local.entity.ConversationSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    // ========== 文件缓存 ==========

    @Query("SELECT * FROM conversation_files WHERE projectId = :projectId")
    suspend fun getFilesByProject(projectId: String): List<ConversationFileEntity>

    @Query("SELECT * FROM conversation_files WHERE id = :fileId")
    suspend fun getFileById(fileId: String): ConversationFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ConversationFileEntity)

    @Query("DELETE FROM conversation_files WHERE id = :fileId")
    suspend fun deleteFile(fileId: String)

    @Query("DELETE FROM conversation_files WHERE projectId = :projectId")
    suspend fun deleteFilesByProject(projectId: String)

    // ========== 会话缓存 ==========

    @Query("SELECT * FROM conversation_sessions WHERE fileId = :fileId")
    suspend fun getSessionsByFile(fileId: String): List<ConversationSessionEntity>

    @Query("SELECT * FROM conversation_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: String): ConversationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ConversationSessionEntity)

    @Query("DELETE FROM conversation_sessions WHERE fileId = :fileId")
    suspend fun deleteSessionsByFile(fileId: String)

    // ========== 段落缓存 ==========

    @Query("SELECT * FROM conversation_segments WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSegmentsBySession(sessionId: String): List<ConversationSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<ConversationSegmentEntity>)

    @Query("DELETE FROM conversation_segments WHERE sessionId = :sessionId")
    suspend fun deleteSegmentsBySession(sessionId: String)

    // ========== 批量操作 ==========

    @Transaction
    suspend fun cacheSession(
        file: ConversationFileEntity,
        session: ConversationSessionEntity,
        segments: List<ConversationSegmentEntity>
    ) {
        insertFile(file)
        insertSession(session)
        insertSegments(segments)
    }

    @Transaction
    suspend fun updateSessionCache(
        file: ConversationFileEntity,
        session: ConversationSessionEntity,
        segments: List<ConversationSegmentEntity>
    ) {
        // 先删除旧数据
        deleteSegmentsBySession(session.sessionId)
        deleteSessionsByFile(file.id)
        // 插入新数据
        insertFile(file)
        insertSession(session)
        insertSegments(segments)
    }

    // ========== 查询缓存状态 ==========

    @Query("""
        SELECT cf.* FROM conversation_files cf
        WHERE cf.projectId = :projectId
        AND cf.id = :sessionId
        AND cf.fileSize = :fileSize
        AND cf.lastModified = :lastModified
    """)
    suspend fun findValidCache(
        projectId: String,
        sessionId: String,
        fileSize: Long,
        lastModified: Long
    ): ConversationFileEntity?
}
