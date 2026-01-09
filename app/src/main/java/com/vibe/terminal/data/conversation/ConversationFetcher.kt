package com.vibe.terminal.data.conversation

import android.util.Base64
import com.vibe.terminal.data.ssh.SshConfig
import com.vibe.terminal.data.ssh.SshConnectionPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从远程服务器获取 Claude Code 对话历史
 * 支持增量同步、压缩传输和智能刷新以优化移动网络体验
 */
@Singleton
class ConversationFetcher @Inject constructor(
    private val cache: ConversationCache,
    private val dbCache: ConversationDbCache,
    private val connectionPool: SshConnectionPool
) {

    companion object {
        // 压缩传输的最小文件大小阈值（小于此值不压缩）
        private const val COMPRESSION_THRESHOLD = 1024L // 1KB
    }

    /**
     * 获取项目的对话历史文件列表（包含修改时间和文件大小）
     */
    suspend fun listConversationFiles(
        config: SshConfig,
        projectPath: String
    ): Result<List<ConversationFileInfo>> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = encodeProjectPath(projectPath)
            val claudeDir = "~/.claude/projects/$encodedPath"

            // 获取文件列表，包含修改时间和大小
            val command = "for f in $claudeDir/*.jsonl; do [ -f \"\$f\" ] && stat --format='%n|%Y|%s' \"\$f\" 2>/dev/null; done | sort -t'|' -k2 -rn"
            val output = connectionPool.executeCommand(config, command).getOrThrow()

            val files = output
                .lines()
                .filter { it.isNotBlank() && it.contains("|") }
                .mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size >= 2) {
                        val filePath = parts[0]
                        val modTime = parts[1].toLongOrNull() ?: 0L
                        val fileSize = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                        val fileName = filePath.substringAfterLast("/")
                        val sessionId = fileName.removeSuffix(".jsonl")
                        ConversationFileInfo(
                            sessionId = sessionId,
                            filePath = filePath,
                            projectPath = projectPath,
                            modificationTime = modTime,
                            fileSize = fileSize
                        )
                    } else null
                }

            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取并解析对话历史（支持增量同步）
     *
     * 优化策略：
     * 1. 智能刷新：先检查文件大小是否变化
     * 2. 增量同步：只下载新增的内容
     * 3. 压缩传输：对传输内容进行 gzip 压缩
     */
    suspend fun fetchAndParseConversation(
        config: SshConfig,
        fileInfo: ConversationFileInfo,
        projectId: String = ""
    ): Result<com.vibe.terminal.domain.model.ConversationSession> = withContext(Dispatchers.IO) {
        try {
            // 1. 检查数据库缓存是否完全有效（文件大小和修改时间都匹配）
            if (projectId.isNotBlank() && fileInfo.fileSize > 0) {
                val hasValidDbCache = dbCache.hasValidCache(
                    projectId = projectId,
                    sessionId = fileInfo.sessionId,
                    fileSize = fileInfo.fileSize,
                    lastModified = fileInfo.modificationTime
                )

                if (hasValidDbCache) {
                    val cached = dbCache.loadFromCache(fileInfo.sessionId)
                    if (cached != null) {
                        return@withContext Result.success(cached)
                    }
                }
            }

            // 2. 尝试增量同步
            val jsonlContent = fetchContentWithIncrementalSync(config, fileInfo)

            // 3. 解析
            val session = ConversationParser.parseJsonl(
                jsonlContent = jsonlContent,
                sessionId = fileInfo.sessionId,
                projectPath = fileInfo.projectPath
            )

            // 4. 保存到数据库缓存
            if (projectId.isNotBlank()) {
                dbCache.saveToCache(
                    projectId = projectId,
                    filePath = fileInfo.filePath,
                    fileSize = fileInfo.fileSize,
                    lastModified = fileInfo.modificationTime,
                    session = session
                )
            }

            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 带增量同步和压缩的内容获取
     */
    private suspend fun fetchContentWithIncrementalSync(
        config: SshConfig,
        fileInfo: ConversationFileInfo
    ): String {
        // 检查本地文件缓存是否完全有效
        if (cache.isCacheValid(fileInfo.sessionId, fileInfo.projectPath, fileInfo.modificationTime)) {
            val cachedContent = cache.getCachedContent(fileInfo.sessionId, fileInfo.projectPath)
            if (cachedContent != null) {
                return cachedContent
            }
        }

        // 检查是否可以增量同步
        val incrementalOffset = cache.getIncrementalOffset(
            sessionId = fileInfo.sessionId,
            projectPath = fileInfo.projectPath,
            remoteFileSize = fileInfo.fileSize
        )

        return if (incrementalOffset != null && incrementalOffset > 0) {
            // 增量同步：只下载新增部分
            fetchIncrementalContent(config, fileInfo, incrementalOffset)
        } else {
            // 全量下载
            fetchFullContent(config, fileInfo)
        }
    }

    /**
     * 增量下载内容
     */
    private suspend fun fetchIncrementalContent(
        config: SshConfig,
        fileInfo: ConversationFileInfo,
        offset: Long
    ): String {
        val incrementalSize = fileInfo.fileSize - offset

        // 获取增量内容（带压缩）
        val incrementalContent = if (incrementalSize > COMPRESSION_THRESHOLD) {
            fetchCompressedIncremental(config, fileInfo.filePath, offset)
        } else {
            fetchRawIncremental(config, fileInfo.filePath, offset)
        }

        // 追加到本地缓存
        cache.appendToCache(
            sessionId = fileInfo.sessionId,
            projectPath = fileInfo.projectPath,
            incrementalContent = incrementalContent,
            newFileSize = fileInfo.fileSize,
            remoteModTime = fileInfo.modificationTime
        )

        // 返回完整内容
        return cache.getCachedContent(fileInfo.sessionId, fileInfo.projectPath) ?: incrementalContent
    }

    /**
     * 全量下载内容
     */
    private suspend fun fetchFullContent(
        config: SshConfig,
        fileInfo: ConversationFileInfo
    ): String {
        // 根据文件大小决定是否压缩
        val content = if (fileInfo.fileSize > COMPRESSION_THRESHOLD) {
            fetchCompressedFull(config, fileInfo.filePath)
        } else {
            fetchRawFull(config, fileInfo.filePath)
        }

        // 保存到缓存
        cache.saveToCache(
            sessionId = fileInfo.sessionId,
            projectPath = fileInfo.projectPath,
            content = content,
            remoteModTime = fileInfo.modificationTime
        )

        return content
    }

    /**
     * 压缩方式获取增量内容
     */
    private suspend fun fetchCompressedIncremental(
        config: SshConfig,
        filePath: String,
        offset: Long
    ): String {
        // tail -c +N 从第 N 个字节开始读取（1-indexed）
        val command = "tail -c +${offset + 1} '$filePath' | gzip -c | base64 -w 0"
        val base64Compressed = connectionPool.executeCommand(config, command).getOrThrow()
        return decompressBase64Gzip(base64Compressed)
    }

    /**
     * 原始方式获取增量内容
     */
    private suspend fun fetchRawIncremental(
        config: SshConfig,
        filePath: String,
        offset: Long
    ): String {
        val command = "tail -c +${offset + 1} '$filePath'"
        return connectionPool.executeCommand(config, command).getOrThrow()
    }

    /**
     * 压缩方式获取全量内容
     */
    private suspend fun fetchCompressedFull(
        config: SshConfig,
        filePath: String
    ): String {
        val command = "gzip -c '$filePath' | base64 -w 0"
        val base64Compressed = connectionPool.executeCommand(config, command).getOrThrow()
        return decompressBase64Gzip(base64Compressed)
    }

    /**
     * 原始方式获取全量内容
     */
    private suspend fun fetchRawFull(
        config: SshConfig,
        filePath: String
    ): String {
        return connectionPool.executeCommand(config, "cat '$filePath'").getOrThrow()
    }

    /**
     * 解压 Base64 编码的 Gzip 数据
     */
    private fun decompressBase64Gzip(base64Data: String): String {
        if (base64Data.isBlank()) return ""

        val compressed = Base64.decode(base64Data.trim(), Base64.DEFAULT)
        return GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() }
    }

    /**
     * 获取单个对话文件内容（保留向后兼容）
     */
    suspend fun fetchConversationFile(
        config: SshConfig,
        fileInfo: ConversationFileInfo
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val content = fetchContentWithIncrementalSync(config, fileInfo)
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun encodeProjectPath(path: String): String {
        return path.replace("/", "-")
    }
}

/**
 * 对话文件信息
 */
data class ConversationFileInfo(
    val sessionId: String,
    val filePath: String,
    val projectPath: String,
    val modificationTime: Long = 0L,
    val fileSize: Long = 0L
)
