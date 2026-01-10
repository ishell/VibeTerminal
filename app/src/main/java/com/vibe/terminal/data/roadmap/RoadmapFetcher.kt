package com.vibe.terminal.data.roadmap

import android.util.Log
import com.vibe.terminal.data.conversation.ConversationFetcher
import com.vibe.terminal.data.ssh.SshConfig
import com.vibe.terminal.data.ssh.SshConnectionPool
import com.vibe.terminal.domain.model.AssistantType
import com.vibe.terminal.domain.model.ConversationSession
import com.vibe.terminal.domain.model.ProjectRoadmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Roadmap 数据获取器
 *
 * 从远程服务器获取：
 * 1. CLAUDE.md 文件内容
 * 2. opencode.md 文件内容
 * 3. Roadmap 相关文件（roadmap.md, ROADMAP.md, docs/roadmap.md 等）
 * 4. 对话历史（复用 ConversationFetcher）
 */
@Singleton
class RoadmapFetcher @Inject constructor(
    private val connectionPool: SshConnectionPool,
    private val conversationFetcher: ConversationFetcher
) {
    companion object {
        private const val TAG = "RoadmapFetcher"

        // CLAUDE.md 可能的位置
        private val CLAUDE_MD_PATHS = listOf(
            "CLAUDE.md",
            ".claude/CLAUDE.md",
            "docs/CLAUDE.md",
            "doc/CLAUDE.md"
        )

        // OpenCode 配置文件可能的位置
        private val OPENCODE_MD_PATHS = listOf(
            "opencode.md",
            ".opencode/opencode.md",
            "OPENCODE.md"
        )

        // Roadmap 文件可能的位置（大小写不同、不同目录）
        private val ROADMAP_FILE_PATHS = listOf(
            // 根目录
            "roadmap.md",
            "ROADMAP.md",
            "Roadmap.md",
            "roadmap.txt",
            "ROADMAP.txt",
            "Roadmap.txt",
            // docs 目录
            "docs/roadmap.md",
            "docs/ROADMAP.md",
            "docs/Roadmap.md",
            "docs/roadmap.txt",
            "docs/ROADMAP.txt",
            // doc 目录
            "doc/roadmap.md",
            "doc/ROADMAP.md",
            "doc/Roadmap.md",
            "doc/roadmap.txt",
            "doc/ROADMAP.txt",
            // .github 目录
            ".github/ROADMAP.md",
            ".github/roadmap.md"
        )
    }

    /**
     * 获取项目的完整 Roadmap
     */
    suspend fun fetchProjectRoadmap(
        config: SshConfig,
        projectId: String,
        projectPath: String,
        assistantType: AssistantType,
        existingSessions: List<ConversationSession> = emptyList()
    ): Result<ProjectRoadmap> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取 CLAUDE.md 内容
            val claudeMdContent = fetchClaudeMd(config, projectPath)

            // 2. 获取 opencode.md 内容（如果使用 OpenCode 或 Both）
            val opencodeMdContent = if (assistantType in listOf(AssistantType.OPENCODE, AssistantType.BOTH)) {
                fetchOpencodeMd(config, projectPath)
            } else null

            // 3. 获取 Roadmap 相关文件
            val roadmapFiles = fetchRoadmapFiles(config, projectPath)

            // 4. 使用现有的会话数据，或者获取新的
            val sessions = existingSessions.ifEmpty {
                fetchConversationSessions(config, projectId, projectPath, assistantType)
            }

            // 5. 构建 Roadmap
            val roadmap = RoadmapParser.buildProjectRoadmap(
                projectId = projectId,
                projectPath = projectPath,
                claudeMdContent = claudeMdContent,
                opencodeMdContent = opencodeMdContent,
                roadmapFiles = roadmapFiles,
                sessions = sessions
            )

            Result.success(roadmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch roadmap for $projectPath: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 获取 CLAUDE.md 内容
     */
    private suspend fun fetchClaudeMd(config: SshConfig, projectPath: String): String? {
        for (relativePath in CLAUDE_MD_PATHS) {
            val fullPath = "$projectPath/$relativePath"
            try {
                val result = connectionPool.executeCommand(
                    config,
                    "cat '$fullPath' 2>/dev/null",
                    timeoutSeconds = 10
                )
                val content = result.getOrNull()
                if (!content.isNullOrBlank()) {
                    Log.d(TAG, "Found CLAUDE.md at: $fullPath")
                    return content
                }
            } catch (e: Exception) {
                // 继续尝试下一个路径
            }
        }

        Log.d(TAG, "No CLAUDE.md found in $projectPath")
        return null
    }

    /**
     * 获取 opencode.md 内容
     */
    private suspend fun fetchOpencodeMd(config: SshConfig, projectPath: String): String? {
        for (relativePath in OPENCODE_MD_PATHS) {
            val fullPath = "$projectPath/$relativePath"
            try {
                val result = connectionPool.executeCommand(
                    config,
                    "cat '$fullPath' 2>/dev/null",
                    timeoutSeconds = 10
                )
                val content = result.getOrNull()
                if (!content.isNullOrBlank()) {
                    Log.d(TAG, "Found opencode.md at: $fullPath")
                    return content
                }
            } catch (e: Exception) {
                // 继续尝试下一个路径
            }
        }

        Log.d(TAG, "No opencode.md found in $projectPath")
        return null
    }

    /**
     * 获取 Roadmap 相关文件
     * 返回一个 Map，key 是文件名，value 是文件内容
     */
    private suspend fun fetchRoadmapFiles(
        config: SshConfig,
        projectPath: String
    ): Map<String, String> {
        val files = mutableMapOf<String, String>()

        for (relativePath in ROADMAP_FILE_PATHS) {
            val fullPath = "$projectPath/$relativePath"
            try {
                val result = connectionPool.executeCommand(
                    config,
                    "cat '$fullPath' 2>/dev/null",
                    timeoutSeconds = 10
                )
                val content = result.getOrNull()
                if (!content.isNullOrBlank()) {
                    Log.d(TAG, "Found roadmap file at: $fullPath")
                    // 使用相对路径作为 key，便于显示
                    files[relativePath] = content
                }
            } catch (e: Exception) {
                // 继续尝试下一个路径
            }
        }

        if (files.isEmpty()) {
            Log.d(TAG, "No roadmap files found in $projectPath")
        } else {
            Log.d(TAG, "Found ${files.size} roadmap file(s) in $projectPath")
        }

        return files
    }

    /**
     * 获取对话会话列表
     */
    private suspend fun fetchConversationSessions(
        config: SshConfig,
        projectId: String,
        projectPath: String,
        assistantType: AssistantType
    ): List<ConversationSession> {
        val sessions = mutableListOf<ConversationSession>()

        // 获取对话文件列表
        val filesResult = conversationFetcher.listConversationFiles(
            config = config,
            projectPath = projectPath,
            assistantType = assistantType
        )

        val files = filesResult.getOrElse { return emptyList() }

        // 获取最近的几个对话文件（最多 10 个）
        for (fileInfo in files.take(10)) {
            val sessionResult = conversationFetcher.fetchAndParseConversation(
                config = config,
                fileInfo = fileInfo,
                projectId = projectId
            )
            sessionResult.onSuccess { session ->
                sessions.add(session)
            }
        }

        return sessions
    }

    /**
     * 仅获取 CLAUDE.md 内容（用于快速预览）
     */
    suspend fun fetchClaudeMdOnly(
        config: SshConfig,
        projectPath: String
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetchClaudeMd(config, projectPath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
