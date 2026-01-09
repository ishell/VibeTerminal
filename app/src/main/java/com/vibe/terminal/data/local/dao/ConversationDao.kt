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

    // ========== 增量同步相关 ==========

    /**
     * 获取文件的上次读取偏移量
     */
    @Query("SELECT lastOffset FROM conversation_files WHERE id = :sessionId")
    suspend fun getLastOffset(sessionId: String): Long?

    /**
     * 更新文件的读取偏移量
     */
    @Query("UPDATE conversation_files SET lastOffset = :offset, fileSize = :fileSize, lastModified = :lastModified WHERE id = :sessionId")
    suspend fun updateOffset(sessionId: String, offset: Long, fileSize: Long, lastModified: Long)

    /**
     * 检查文件是否有更新（基于文件大小）
     * 返回需要增量读取的起始偏移量，如果不需要更新返回 null
     */
    @Query("""
        SELECT lastOffset FROM conversation_files
        WHERE id = :sessionId
        AND projectId = :projectId
        AND fileSize < :newFileSize
    """)
    suspend fun getOffsetIfNeedsUpdate(
        projectId: String,
        sessionId: String,
        newFileSize: Long
    ): Long?

    /**
     * 检查文件是否已缓存（任意版本）
     */
    @Query("SELECT EXISTS(SELECT 1 FROM conversation_files WHERE id = :sessionId AND projectId = :projectId)")
    suspend fun hasCache(projectId: String, sessionId: String): Boolean
}
