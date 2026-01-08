package com.vibe.terminal.data.conversation

import com.vibe.terminal.data.ssh.SshConfig
import com.vibe.terminal.data.ssh.SshConnectionPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从远程服务器获取 Claude Code 对话历史
 * 支持数据库缓存和SSH连接复用以提升性能
 */
@Singleton
class ConversationFetcher @Inject constructor(
    private val cache: ConversationCache,
    private val dbCache: ConversationDbCache,
    private val connectionPool: SshConnectionPool
) {

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
     * 获取并解析对话历史（优先使用数据库缓存）
     */
    suspend fun fetchAndParseConversation(
        config: SshConfig,
        fileInfo: ConversationFileInfo,
        projectId: String = ""
    ): Result<com.vibe.terminal.domain.model.ConversationSession> = withContext(Dispatchers.IO) {
        try {
            // 1. 检查数据库缓存是否有效
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

            // 2. 检查文件缓存
            val content = if (cache.isCacheValid(fileInfo.sessionId, fileInfo.projectPath, fileInfo.modificationTime)) {
                cache.getCachedContent(fileInfo.sessionId, fileInfo.projectPath)
            } else {
                null
            }

            // 3. 如果没有缓存，从远程下载
            val jsonlContent = content ?: run {
                val downloaded = connectionPool.executeCommand(
                    config,
                    "cat '${fileInfo.filePath}'"
                ).getOrThrow()

                // 保存到文件缓存
                cache.saveToCache(
                    sessionId = fileInfo.sessionId,
                    projectPath = fileInfo.projectPath,
                    content = downloaded,
                    remoteModTime = fileInfo.modificationTime
                )
                downloaded
            }

            // 4. 解析
            val session = ConversationParser.parseJsonl(
                jsonlContent = jsonlContent,
                sessionId = fileInfo.sessionId,
                projectPath = fileInfo.projectPath
            )

            // 5. 保存到数据库缓存
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
     * 获取单个对话文件内容（保留向后兼容）
     */
    suspend fun fetchConversationFile(
        config: SshConfig,
        fileInfo: ConversationFileInfo
    ): Result<String> = withContext(Dispatchers.IO) {
        if (cache.isCacheValid(fileInfo.sessionId, fileInfo.projectPath, fileInfo.modificationTime)) {
            val cachedContent = cache.getCachedContent(fileInfo.sessionId, fileInfo.projectPath)
            if (cachedContent != null) {
                return@withContext Result.success(cachedContent)
            }
        }

        try {
            val content = connectionPool.executeCommand(
                config,
                "cat '${fileInfo.filePath}'"
            ).getOrThrow()

            cache.saveToCache(
                sessionId = fileInfo.sessionId,
                projectPath = fileInfo.projectPath,
                content = content,
                remoteModTime = fileInfo.modificationTime
            )

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
