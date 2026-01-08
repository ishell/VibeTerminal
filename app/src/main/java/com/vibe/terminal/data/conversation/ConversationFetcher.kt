package com.vibe.terminal.data.conversation

import com.vibe.terminal.data.ssh.SshConfig
import com.vibe.terminal.data.ssh.SshKeyLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从远程服务器获取 Claude Code 对话历史
 * 支持本地缓存以提升性能
 */
@Singleton
class ConversationFetcher @Inject constructor(
    private val cache: ConversationCache
) {

    /**
     * 获取项目的对话历史文件列表（包含修改时间）
     * @param config SSH配置
     * @param projectPath 项目路径 (如 /home/user/myproject)
     * @return 对话文件列表 (sessionId -> 文件路径)
     */
    suspend fun listConversationFiles(
        config: SshConfig,
        projectPath: String
    ): Result<List<ConversationFileInfo>> = withContext(Dispatchers.IO) {
        var client: SSHClient? = null
        var tempKeyFile: File? = null

        try {
            client = SSHClient(DefaultConfig()).apply {
                addHostKeyVerifier(PromiscuousVerifier())
                connect(config.host, config.port)

                when (val auth = config.authMethod) {
                    is SshConfig.AuthMethod.Password -> {
                        authPassword(config.username, auth.password)
                    }
                    is SshConfig.AuthMethod.PublicKey -> {
                        val keyResult = SshKeyLoader.loadKey(auth.privateKey, auth.passphrase)
                        tempKeyFile = keyResult.tempFile
                        authPublickey(config.username, keyResult.keyProvider)
                    }
                }
            }

            // 构建 Claude Code 对话目录路径
            val encodedPath = encodeProjectPath(projectPath)
            val claudeDir = "~/.claude/projects/$encodedPath"

            // 使用 stat 获取文件列表和修改时间
            // 格式: 文件路径|修改时间戳
            val session = client.startSession()
            val command = session.exec(
                "for f in $claudeDir/*.jsonl; do [ -f \"\$f\" ] && stat --format='%n|%Y' \"\$f\" 2>/dev/null; done | sort -t'|' -k2 -rn"
            )

            val output = ByteArrayOutputStream()
            command.inputStream.copyTo(output)
            command.join()
            session.close()

            val files = output.toString(Charsets.UTF_8)
                .lines()
                .filter { it.isNotBlank() && it.contains("|") }
                .mapNotNull { line ->
                    val parts = line.split("|")
                    if (parts.size == 2) {
                        val filePath = parts[0]
                        val modTime = parts[1].toLongOrNull() ?: 0L
                        val fileName = filePath.substringAfterLast("/")
                        val sessionId = fileName.removeSuffix(".jsonl")
                        ConversationFileInfo(
                            sessionId = sessionId,
                            filePath = filePath,
                            projectPath = projectPath,
                            modificationTime = modTime
                        )
                    } else null
                }

            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                client?.disconnect()
            } catch (_: Exception) { }
            tempKeyFile?.delete()
        }
    }

    /**
     * 获取单个对话文件内容（优先使用缓存）
     */
    suspend fun fetchConversationFile(
        config: SshConfig,
        fileInfo: ConversationFileInfo
    ): Result<String> = withContext(Dispatchers.IO) {
        // 检查缓存是否有效
        if (cache.isCacheValid(fileInfo.sessionId, fileInfo.projectPath, fileInfo.modificationTime)) {
            val cachedContent = cache.getCachedContent(fileInfo.sessionId, fileInfo.projectPath)
            if (cachedContent != null) {
                return@withContext Result.success(cachedContent)
            }
        }

        // 缓存无效，从远程下载
        var client: SSHClient? = null
        var tempKeyFile: File? = null

        try {
            client = SSHClient(DefaultConfig()).apply {
                addHostKeyVerifier(PromiscuousVerifier())
                connect(config.host, config.port)

                when (val auth = config.authMethod) {
                    is SshConfig.AuthMethod.Password -> {
                        authPassword(config.username, auth.password)
                    }
                    is SshConfig.AuthMethod.PublicKey -> {
                        val keyResult = SshKeyLoader.loadKey(auth.privateKey, auth.passphrase)
                        tempKeyFile = keyResult.tempFile
                        authPublickey(config.username, keyResult.keyProvider)
                    }
                }
            }

            // 读取文件内容
            val session = client.startSession()
            val command = session.exec("cat '${fileInfo.filePath}'")

            val output = ByteArrayOutputStream()
            command.inputStream.copyTo(output)
            command.join()
            session.close()

            val content = output.toString(Charsets.UTF_8)

            // 保存到缓存
            cache.saveToCache(
                sessionId = fileInfo.sessionId,
                projectPath = fileInfo.projectPath,
                content = content,
                remoteModTime = fileInfo.modificationTime
            )

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                client?.disconnect()
            } catch (_: Exception) { }
            tempKeyFile?.delete()
        }
    }

    /**
     * 获取并解析对话历史
     */
    suspend fun fetchAndParseConversation(
        config: SshConfig,
        fileInfo: ConversationFileInfo
    ): Result<com.vibe.terminal.domain.model.ConversationSession> = withContext(Dispatchers.IO) {
        fetchConversationFile(config, fileInfo).mapCatching { content ->
            ConversationParser.parseJsonl(
                jsonlContent = content,
                sessionId = fileInfo.sessionId,
                projectPath = fileInfo.projectPath
            )
        }
    }

    /**
     * 编码项目路径 (Claude Code 使用的格式)
     * 例如: /home/jay/work/myproject -> -home-jay-work-myproject
     */
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
    val modificationTime: Long = 0L
)
