package com.vibe.terminal.data.conversation

import android.util.Base64
import android.util.Log
import com.vibe.terminal.data.ssh.SshConfig
import com.vibe.terminal.data.ssh.SshConnectionPool
import com.vibe.terminal.domain.model.AssistantType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从远程服务器获取对话历史
 * 支持 Claude Code 和 OpenCode 两种格式
 * 支持增量同步、压缩传输和智能刷新以优化移动网络体验
 */
@Singleton
class ConversationFetcher @Inject constructor(
    private val cache: ConversationCache,
    private val dbCache: ConversationDbCache,
    private val connectionPool: SshConnectionPool
) {

    companion object {
        private const val TAG = "ConversationFetcher"

        // 压缩传输的最小文件大小阈值（小于此值不压缩）
        private const val COMPRESSION_THRESHOLD = 1024L // 1KB

        // 超时配置
        private const val BASE_TIMEOUT_SECONDS = 30L      // 基础超时
        private const val TIMEOUT_PER_MB_SECONDS = 10L    // 每 MB 额外增加的超时时间
        private const val MAX_TIMEOUT_SECONDS = 300L      // 最大超时 5 分钟

        // 重试配置
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 1000L
    }

    /**
     * 获取项目的对话历史文件列表（包含修改时间和文件大小）
     * 根据 assistantType 选择不同的存储路径
     */
    suspend fun listConversationFiles(
        config: SshConfig,
        projectPath: String,
        assistantType: AssistantType = AssistantType.CLAUDE_CODE
    ): Result<List<ConversationFileInfo>> = withContext(Dispatchers.IO) {
        try {
            when (assistantType) {
                AssistantType.CLAUDE_CODE -> listClaudeCodeFiles(config, projectPath)
                AssistantType.OPENCODE -> listOpenCodeFiles(config, projectPath)
                AssistantType.CODEX -> listCodexFiles(config)
                AssistantType.BOTH -> listAllFiles(config, projectPath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 列出所有支持的 AI 助手对话文件 (Claude Code + OpenCode + Codex)
     */
    private suspend fun listAllFiles(
        config: SshConfig,
        projectPath: String
    ): Result<List<ConversationFileInfo>> {
        val claudeFiles = listClaudeCodeFiles(config, projectPath).getOrElse { emptyList() }
        val openCodeFiles = listOpenCodeFiles(config, projectPath).getOrElse { emptyList() }
        val codexFiles = listCodexFiles(config).getOrElse { emptyList() }

        // 合并并按修改时间排序
        val allFiles = (claudeFiles + openCodeFiles + codexFiles)
            .sortedByDescending { it.modificationTime }
            .take(20) // 限制总数

        return Result.success(allFiles)
    }

    /**
     * 列出 Claude Code 对话文件
     */
    private suspend fun listClaudeCodeFiles(
        config: SshConfig,
        projectPath: String
    ): Result<List<ConversationFileInfo>> {
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
                        fileSize = fileSize,
                        assistantType = AssistantType.CLAUDE_CODE
                    )
                } else null
            }

        return Result.success(files)
    }

    /**
     * 列出 OpenCode 会话文件
     */
    private suspend fun listOpenCodeFiles(
        config: SshConfig,
        projectPath: String
    ): Result<List<ConversationFileInfo>> {
        // OpenCode 存储在 ~/.local/share/opencode/storage/session/<project-hash>/
        // 会话文件格式: ses_xxx.json
        val encodedPath = encodeProjectPath(projectPath)
        val openCodeDir = "~/.local/share/opencode/storage/session"

        // 先找到项目目录（通过 projectID hash）
        // OpenCode 使用项目路径的 hash 作为目录名
        val findProjectDirCmd = """
            for d in $openCodeDir/*/; do
                [ -d "${'$'}d" ] && echo "${'$'}d"
            done
        """.trimIndent()

        val projectDirs = connectionPool.executeCommand(config, findProjectDirCmd).getOrElse { "" }
            .lines()
            .filter { it.isNotBlank() }

        val files = mutableListOf<ConversationFileInfo>()

        // 遍历每个项目目录查找会话文件
        for (projectDir in projectDirs.take(3)) { // 限制搜索范围
            val listCmd = "for f in ${projectDir}ses_*.json; do [ -f \"\$f\" ] && stat --format='%n|%Y|%s' \"\$f\" 2>/dev/null; done | sort -t'|' -k2 -rn | head -10"
            val output = connectionPool.executeCommand(config, listCmd, timeoutSeconds = 10).getOrElse { "" }

            output.lines()
                .filter { it.isNotBlank() && it.contains("|") }
                .mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size >= 2) {
                        val filePath = parts[0]
                        val modTime = parts[1].toLongOrNull() ?: 0L
                        val fileSize = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                        val fileName = filePath.substringAfterLast("/")
                        val sessionId = fileName.removeSuffix(".json")
                        ConversationFileInfo(
                            sessionId = sessionId,
                            filePath = filePath,
                            projectPath = projectPath,
                            modificationTime = modTime,
                            fileSize = fileSize,
                            assistantType = AssistantType.OPENCODE
                        )
                    } else null
                }
                .also { files.addAll(it) }
        }

        return Result.success(files.sortedByDescending { it.modificationTime }.take(10))
    }

    /**
     * 列出 Codex CLI 会话文件
     * Codex 存储在 ~/.codex/sessions/YYYY/MM/DD/rollout-*.jsonl
     */
    private suspend fun listCodexFiles(
        config: SshConfig
    ): Result<List<ConversationFileInfo>> {
        val codexSessionsDir = "~/.codex/sessions"

        // 递归查找最近的 .jsonl 文件
        val command = """
            find $codexSessionsDir -name 'rollout-*.jsonl' -type f 2>/dev/null | while read f; do
                stat --format='%n|%Y|%s' "${'$'}f" 2>/dev/null
            done | sort -t'|' -k2 -rn | head -15
        """.trimIndent()

        val output = connectionPool.executeCommand(config, command, timeoutSeconds = 15).getOrElse { "" }

        val files = output.lines()
            .filter { it.isNotBlank() && it.contains("|") }
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 2) {
                    val filePath = parts[0]
                    val modTime = parts[1].toLongOrNull() ?: 0L
                    val fileSize = parts.getOrNull(2)?.toLongOrNull() ?: 0L
                    val fileName = filePath.substringAfterLast("/")
                    val sessionId = fileName.removeSuffix(".jsonl")

                    // 从路径提取 project path (使用目录结构)
                    // ~/.codex/sessions/2025/01/10/rollout-xxx.jsonl
                    val pathParts = filePath.split("/")
                    val projectPath = if (pathParts.size >= 4) {
                        // 取 sessions 之后的日期目录
                        pathParts.takeLast(4).dropLast(1).joinToString("/")
                    } else {
                        "codex"
                    }

                    ConversationFileInfo(
                        sessionId = sessionId,
                        filePath = filePath,
                        projectPath = projectPath,
                        modificationTime = modTime,
                        fileSize = fileSize,
                        assistantType = AssistantType.CODEX
                    )
                } else null
            }

        return Result.success(files)
    }

    /**
     * 获取并解析对话历史（支持增量同步）
     */
    suspend fun fetchAndParseConversation(
        config: SshConfig,
        fileInfo: ConversationFileInfo,
        projectId: String = ""
    ): Result<com.vibe.terminal.domain.model.ConversationSession> = withContext(Dispatchers.IO) {
        try {
            // 根据文件的 assistantType 解析，而不是项目的
            when (fileInfo.assistantType) {
                AssistantType.CLAUDE_CODE -> fetchAndParseClaudeCode(config, fileInfo, projectId)
                AssistantType.OPENCODE -> fetchAndParseOpenCode(config, fileInfo, projectId)
                AssistantType.CODEX -> fetchAndParseCodex(config, fileInfo, projectId)
                AssistantType.BOTH -> {
                    // BOTH 不会出现在单个文件上
                    // 这里默认按 Claude Code 处理
                    fetchAndParseClaudeCode(config, fileInfo, projectId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch session ${fileInfo.sessionId}: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 获取并解析 Claude Code 对话
     */
    private suspend fun fetchAndParseClaudeCode(
        config: SshConfig,
        fileInfo: ConversationFileInfo,
        projectId: String
    ): Result<com.vibe.terminal.domain.model.ConversationSession> {
        // 1. 检查数据库缓存是否完全有效
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
                    Log.d(TAG, "Using cached session: ${fileInfo.sessionId}")
                    return Result.success(cached)
                }
            }
        }

        // 2. 尝试增量同步（带重试）
        val jsonlContent = fetchContentWithRetry(config, fileInfo)

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

        return Result.success(session)
    }

    /**
     * 获取并解析 OpenCode 对话
     */
    private suspend fun fetchAndParseOpenCode(
        config: SshConfig,
        fileInfo: ConversationFileInfo,
        projectId: String
    ): Result<com.vibe.terminal.domain.model.ConversationSession> {
        // 1. 获取会话 JSON
        val sessionJson = connectionPool.executeCommand(config, "cat '${fileInfo.filePath}'", 30)
            .getOrThrow()

        // 2. 获取该会话的消息列表
        val messageDir = "~/.local/share/opencode/storage/message/${fileInfo.sessionId}"
        val messagesListCmd = "ls -1 $messageDir/*.json 2>/dev/null | sort -V"
        val messageFiles = connectionPool.executeCommand(config, messagesListCmd, 10)
            .getOrElse { "" }
            .lines()
            .filter { it.isNotBlank() }

        // 3. 读取所有消息 JSON
        val messagesJsonBuilder = StringBuilder("[")
        var firstMessage = true
        for (msgFile in messageFiles.take(100)) { // 限制消息数量
            val msgJson = connectionPool.executeCommand(config, "cat '$msgFile'", 10)
                .getOrNull() ?: continue
            if (!firstMessage) messagesJsonBuilder.append(",")
            messagesJsonBuilder.append(msgJson)
            firstMessage = false
        }
        messagesJsonBuilder.append("]")
        val messagesJson = messagesJsonBuilder.toString()

        // 4. 获取消息部件（简化处理，只获取文本部件）
        val partsJsonMap = mutableMapOf<String, List<String>>()

        // 遍历每个消息获取其部件
        for (msgFile in messageFiles.take(50)) {
            val msgId = msgFile.substringAfterLast("/").removeSuffix(".json")
            val partDir = "~/.local/share/opencode/storage/part/$msgId"

            val partFiles = connectionPool.executeCommand(config, "ls -1 $partDir/*.json 2>/dev/null", 5)
                .getOrElse { "" }
                .lines()
                .filter { it.isNotBlank() }

            val parts = partFiles.take(20).mapNotNull { partFile ->
                connectionPool.executeCommand(config, "cat '$partFile'", 5).getOrNull()
            }

            if (parts.isNotEmpty()) {
                partsJsonMap[msgId] = parts
            }
        }

        // 5. 解析
        val session = OpenCodeParser.parseSession(
            sessionJson = sessionJson,
            messagesJson = messagesJson,
            partsJsonMap = partsJsonMap,
            projectPath = fileInfo.projectPath
        )

        return Result.success(session)
    }

    /**
     * 获取并解析 Codex CLI 对话
     */
    private suspend fun fetchAndParseCodex(
        config: SshConfig,
        fileInfo: ConversationFileInfo,
        projectId: String
    ): Result<com.vibe.terminal.domain.model.ConversationSession> {
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
                    Log.d(TAG, "Using cached Codex session: ${fileInfo.sessionId}")
                    return Result.success(cached)
                }
            }
        }

        // 2. 获取 JSONL 内容（带重试）
        val jsonlContent = fetchContentWithRetry(config, fileInfo)

        // 3. 使用 CodexParser 解析
        val session = CodexParser.parseJsonl(
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

        return Result.success(session)
    }

    /**
     * 带重试的内容获取
     */
    private suspend fun fetchContentWithRetry(
        config: SshConfig,
        fileInfo: ConversationFileInfo
    ): String {
        var lastException: Exception? = null

        repeat(MAX_RETRIES + 1) { attempt ->
            try {
                if (attempt > 0) {
                    Log.d(TAG, "Retry attempt $attempt for ${fileInfo.sessionId}")
                    delay(RETRY_DELAY_MS * attempt)
                }
                return fetchContentWithIncrementalSync(config, fileInfo)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Attempt ${attempt + 1} failed for ${fileInfo.sessionId}: ${e.message}")
            }
        }

        throw lastException ?: Exception("Failed to fetch content after retries")
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
     * 根据文件大小计算超时时间
     */
    private fun calculateTimeout(fileSize: Long): Long {
        val fileSizeMB = fileSize / (1024 * 1024)
        val timeout = BASE_TIMEOUT_SECONDS + (fileSizeMB * TIMEOUT_PER_MB_SECONDS)
        return timeout.coerceAtMost(MAX_TIMEOUT_SECONDS)
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
        val timeout = calculateTimeout(incrementalSize)

        Log.d(TAG, "Fetching incremental: ${fileInfo.sessionId}, offset=$offset, size=$incrementalSize, timeout=${timeout}s")

        // 获取增量内容（带压缩）
        val incrementalContent = if (incrementalSize > COMPRESSION_THRESHOLD) {
            fetchCompressedIncremental(config, fileInfo.filePath, offset, timeout)
        } else {
            fetchRawIncremental(config, fileInfo.filePath, offset, timeout)
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
        val timeout = calculateTimeout(fileInfo.fileSize)

        Log.d(TAG, "Fetching full: ${fileInfo.sessionId}, size=${fileInfo.fileSize}, timeout=${timeout}s")

        // 根据文件大小决定是否压缩
        val content = if (fileInfo.fileSize > COMPRESSION_THRESHOLD) {
            fetchCompressedFull(config, fileInfo.filePath, timeout)
        } else {
            fetchRawFull(config, fileInfo.filePath, timeout)
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
        offset: Long,
        timeoutSeconds: Long
    ): String {
        // tail -c +N 从第 N 个字节开始读取（1-indexed）
        val command = "tail -c +${offset + 1} '$filePath' | gzip -c | base64 -w 0"
        val base64Compressed = connectionPool.executeCommand(config, command, timeoutSeconds).getOrThrow()
        return decompressBase64Gzip(base64Compressed)
    }

    /**
     * 原始方式获取增量内容
     */
    private suspend fun fetchRawIncremental(
        config: SshConfig,
        filePath: String,
        offset: Long,
        timeoutSeconds: Long
    ): String {
        val command = "tail -c +${offset + 1} '$filePath'"
        return connectionPool.executeCommand(config, command, timeoutSeconds).getOrThrow()
    }

    /**
     * 压缩方式获取全量内容
     */
    private suspend fun fetchCompressedFull(
        config: SshConfig,
        filePath: String,
        timeoutSeconds: Long
    ): String {
        val command = "gzip -c '$filePath' | base64 -w 0"
        val base64Compressed = connectionPool.executeCommand(config, command, timeoutSeconds).getOrThrow()
        return decompressBase64Gzip(base64Compressed)
    }

    /**
     * 原始方式获取全量内容
     */
    private suspend fun fetchRawFull(
        config: SshConfig,
        filePath: String,
        timeoutSeconds: Long
    ): String {
        return connectionPool.executeCommand(config, "cat '$filePath'", timeoutSeconds).getOrThrow()
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
            val content = fetchContentWithRetry(config, fileInfo)
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
    val fileSize: Long = 0L,
    val assistantType: AssistantType = AssistantType.CLAUDE_CODE
)
