package com.vibe.terminal.domain.model

import java.time.Instant

/**
 * 任务状态
 */
enum class TaskStatus {
    PENDING,      // 待处理
    IN_PROGRESS,  // 进行中
    COMPLETED,    // 已完成
    BLOCKED       // 被阻塞
}

/**
 * 任务来源
 */
enum class TaskSource {
    CLAUDE_MD,        // 从 CLAUDE.md 提取
    OPENCODE_MD,      // 从 opencode.md 提取
    ROADMAP_FILE,     // 从 roadmap.md / ROADMAP.md 等文件提取
    TODO_WRITE,       // 从 TodoWrite 工具调用提取
    CONVERSATION      // 从对话内容推断
}

/**
 * Roadmap 任务项
 */
data class RoadmapTask(
    val id: String,
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val source: TaskSource,
    val priority: Int = 0,  // 0 = normal, 1 = high, -1 = low
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val relatedSessions: List<RelatedSession> = emptyList(),  // 关联的对话会话
    val parentId: String? = null,  // 父任务 ID（用于子任务）
    val tags: List<String> = emptyList(),
    // 本地完成状态（用户在 App 中标记）
    val isLocallyCompleted: Boolean = false,
    val localCompletedAt: Instant? = null
) {
    /**
     * 计算任务内容的 hash，用于本地状态匹配
     */
    fun computeHash(): String {
        val content = title.trim().lowercase()
        return content.hashCode().toString(16)
    }

    /**
     * 获取有效状态（本地完成状态优先）
     */
    fun getEffectiveStatus(): TaskStatus {
        return if (isLocallyCompleted) TaskStatus.COMPLETED else status
    }
}

/**
 * 关联的对话会话
 */
data class RelatedSession(
    val sessionId: String,
    val sessionSlug: String?,
    val matchType: MatchType,
    val matchedText: String,  // 匹配的文本片段
    val timestamp: Instant
)

/**
 * 匹配类型
 */
enum class MatchType {
    EXACT,       // 精确匹配任务名称
    KEYWORD,     // 关键词匹配
    TODO_WRITE,  // TodoWrite 工具调用
    SEMANTIC     // 语义相关（未来可用 AI 实现）
}

/**
 * Roadmap 分组（用于按来源或状态分组显示）
 * 对应 ROADMAP.md 中的 ## Phase X: Title
 */
data class RoadmapGroup(
    val id: String,
    val title: String,
    val description: String = "",
    val tasks: List<RoadmapTask>,  // 直接任务（无 section 的情况）
    val sections: List<RoadmapSection> = emptyList(),  // 子分组（如 1.1, 1.2）
    val source: TaskSource? = null,
    val order: Int = 0
) {
    /**
     * 获取所有任务（包括 sections 中的任务）
     */
    fun getAllTasks(): List<RoadmapTask> {
        return tasks + sections.flatMap { it.tasks }
    }
}

/**
 * Roadmap 子分组（对应 ### 1.1 Title）
 */
data class RoadmapSection(
    val id: String,
    val title: String,
    val number: String = "",  // 如 "1.1", "2.3"
    val tasks: List<RoadmapTask>,
    val order: Int = 0
)

/**
 * 项目 Roadmap
 */
data class ProjectRoadmap(
    val projectId: String,
    val projectPath: String,
    val groups: List<RoadmapGroup>,
    val lastUpdated: Instant,
    val claudeMdContent: String? = null,   // 原始 CLAUDE.md 内容
    val opencodeMdContent: String? = null  // 原始 opencode.md 内容
) {
    /**
     * 获取所有任务（包括 sections 中的任务）
     */
    fun getAllTasks(): List<RoadmapTask> = groups.flatMap { it.getAllTasks() }

    /**
     * 按状态统计（使用有效状态，本地完成优先）
     */
    fun getStatusCounts(): Map<TaskStatus, Int> {
        return getAllTasks().groupBy { it.getEffectiveStatus() }.mapValues { it.value.size }
    }

    /**
     * 获取进度百分比（使用有效状态，本地完成优先）
     */
    fun getProgressPercentage(): Float {
        val allTasks = getAllTasks()
        if (allTasks.isEmpty()) return 0f
        val completed = allTasks.count { it.getEffectiveStatus() == TaskStatus.COMPLETED }
        return (completed.toFloat() / allTasks.size) * 100
    }
}

/**
 * CLAUDE.md 解析结果
 */
data class ClaudeMdInfo(
    val projectDescription: String = "",
    val roadmapItems: List<RoadmapTask> = emptyList(),
    val todoItems: List<RoadmapTask> = emptyList(),
    val rawContent: String = ""
)
