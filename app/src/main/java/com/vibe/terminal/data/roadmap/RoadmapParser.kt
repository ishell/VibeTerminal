package com.vibe.terminal.data.roadmap

import com.vibe.terminal.domain.model.ClaudeMdInfo
import com.vibe.terminal.domain.model.ContentBlock
import com.vibe.terminal.domain.model.ConversationSession
import com.vibe.terminal.domain.model.MatchType
import com.vibe.terminal.domain.model.ProjectRoadmap
import com.vibe.terminal.domain.model.RelatedSession
import com.vibe.terminal.domain.model.RoadmapGroup
import com.vibe.terminal.domain.model.RoadmapSection
import com.vibe.terminal.domain.model.RoadmapTask
import com.vibe.terminal.domain.model.TaskSource
import com.vibe.terminal.domain.model.TaskStatus
import java.time.Instant
import java.util.UUID

/**
 * Roadmap 解析器
 *
 * 从以下来源提取任务：
 * 1. CLAUDE.md / opencode.md 文件
 * 2. 对话中的 TodoWrite 工具调用
 * 3. 对话内容关键词匹配
 */
object RoadmapParser {

    /**
     * 解析 CLAUDE.md 内容
     */
    fun parseClaudeMd(content: String): ClaudeMdInfo {
        if (content.isBlank()) return ClaudeMdInfo()

        val lines = content.lines()
        val roadmapItems = mutableListOf<RoadmapTask>()
        val todoItems = mutableListOf<RoadmapTask>()
        var projectDescription = ""

        var currentSection = ""
        var descriptionBuilder = StringBuilder()
        var inDescriptionSection = false

        for (line in lines) {
            val trimmedLine = line.trim()

            // 检测标题
            when {
                trimmedLine.startsWith("# ") -> {
                    // 主标题，通常是项目名称
                    currentSection = "header"
                    inDescriptionSection = true
                }
                trimmedLine.lowercase().matches(Regex("^##\\s*(roadmap|计划|路线图|规划).*")) -> {
                    currentSection = "roadmap"
                    inDescriptionSection = false
                    projectDescription = descriptionBuilder.toString().trim()
                }
                trimmedLine.lowercase().matches(Regex("^##\\s*(todo|待办|任务|tasks?).*")) -> {
                    currentSection = "todo"
                    inDescriptionSection = false
                    projectDescription = descriptionBuilder.toString().trim()
                }
                trimmedLine.lowercase().matches(Regex("^##\\s*(completed|done|已完成).*")) -> {
                    currentSection = "completed"
                    inDescriptionSection = false
                }
                trimmedLine.startsWith("## ") -> {
                    currentSection = "other"
                    inDescriptionSection = false
                    if (projectDescription.isBlank()) {
                        projectDescription = descriptionBuilder.toString().trim()
                    }
                }
                inDescriptionSection && trimmedLine.isNotBlank() && !trimmedLine.startsWith("#") -> {
                    descriptionBuilder.appendLine(trimmedLine)
                }
            }

            // 解析任务项（支持多种格式）
            val taskMatch = parseTaskLine(trimmedLine, currentSection)
            if (taskMatch != null) {
                when (currentSection) {
                    "roadmap" -> roadmapItems.add(taskMatch.copy(source = TaskSource.CLAUDE_MD))
                    "todo" -> todoItems.add(taskMatch.copy(source = TaskSource.CLAUDE_MD))
                    "completed" -> {
                        val completedTask = taskMatch.copy(
                            status = TaskStatus.COMPLETED,
                            source = TaskSource.CLAUDE_MD
                        )
                        todoItems.add(completedTask)
                    }
                }
            }
        }

        if (projectDescription.isBlank()) {
            projectDescription = descriptionBuilder.toString().trim()
        }

        return ClaudeMdInfo(
            projectDescription = projectDescription,
            roadmapItems = roadmapItems,
            todoItems = todoItems,
            rawContent = content
        )
    }

    /**
     * 解析 Roadmap 文件（roadmap.md, ROADMAP.md, roadmap.txt 等）
     * 比 parseClaudeMd 更灵活，会尝试提取所有列表项
     */
    fun parseRoadmapFile(content: String): ClaudeMdInfo {
        if (content.isBlank()) return ClaudeMdInfo()

        val lines = content.lines()
        val roadmapItems = mutableListOf<RoadmapTask>()
        var projectDescription = ""
        var descriptionBuilder = StringBuilder()
        var inContent = false

        for (line in lines) {
            val trimmedLine = line.trim()

            // 跳过空行
            if (trimmedLine.isBlank()) continue

            // 检测标题（收集描述）
            when {
                trimmedLine.startsWith("# ") -> {
                    // 主标题后开始收集描述
                    inContent = true
                    if (projectDescription.isBlank()) {
                        projectDescription = descriptionBuilder.toString().trim()
                        descriptionBuilder = StringBuilder()
                    }
                }
                trimmedLine.startsWith("## ") || trimmedLine.startsWith("### ") -> {
                    // 子标题
                    if (projectDescription.isBlank()) {
                        projectDescription = descriptionBuilder.toString().trim()
                    }
                    inContent = true
                }
                !trimmedLine.startsWith("-") && !trimmedLine.startsWith("*") &&
                !trimmedLine.startsWith("[") && !trimmedLine.matches(Regex("^\\d+\\..*")) -> {
                    // 普通文本，可能是描述
                    if (inContent && projectDescription.isBlank()) {
                        descriptionBuilder.appendLine(trimmedLine)
                    }
                }
            }

            // 解析任务项
            val task = parseRoadmapTaskLine(trimmedLine)
            if (task != null) {
                roadmapItems.add(task)
            }
        }

        if (projectDescription.isBlank()) {
            projectDescription = descriptionBuilder.toString().trim()
        }

        return ClaudeMdInfo(
            projectDescription = projectDescription,
            roadmapItems = roadmapItems,
            todoItems = emptyList(),
            rawContent = content
        )
    }

    /**
     * 解析 ROADMAP.md 文件为层级结构
     * 支持 Phase/Section/Task 三级结构
     *
     * 格式示例:
     * ## Phase 1: 项目基础架构
     * ### 1.1 项目初始化
     * - [ ] Task 1
     * - [x] Task 2
     */
    fun parseRoadmapHierarchy(content: String): List<RoadmapGroup> {
        if (content.isBlank()) return emptyList()

        val lines = content.lines()
        val groups = mutableListOf<RoadmapGroup>()

        var currentGroup: MutableRoadmapGroup? = null
        var currentSection: MutableRoadmapSection? = null
        var inPhaseContent = false  // 是否已经进入 Phase 内容
        var groupOrder = 0
        var sectionOrder = 0

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank()) continue

            // 检测 Phase 标题 (## Phase X: Title 或 ## 阶段 X: Title)
            val phaseMatch = Regex("^##\\s*(Phase\\s*\\d+|阶段\\s*\\d+)[：:]?\\s*(.*)$", RegexOption.IGNORE_CASE)
                .find(trimmedLine)
            if (phaseMatch != null) {
                // 保存上一个 group
                currentGroup?.let { g ->
                    currentSection?.let { s ->
                        g.sections.add(s.toRoadmapSection())
                    }
                    groups.add(g.toRoadmapGroup())
                }

                val phaseName = phaseMatch.groupValues[1]
                val phaseTitle = phaseMatch.groupValues[2].ifBlank { phaseName }

                currentGroup = MutableRoadmapGroup(
                    id = "phase-${groupOrder}",
                    title = "$phaseName: $phaseTitle".trim().trimEnd(':'),
                    order = groupOrder++
                )
                currentSection = null
                sectionOrder = 0
                inPhaseContent = true
                continue
            }

            // 如果还没进入 Phase 内容，跳过（跳过 intro/metadata）
            if (!inPhaseContent) continue

            // 检测 Section 标题 (### X.Y Title)
            val sectionMatch = Regex("^###\\s*(\\d+\\.\\d+)\\s+(.+)$").find(trimmedLine)
            if (sectionMatch != null && currentGroup != null) {
                // 保存上一个 section
                currentSection?.let { s ->
                    currentGroup.sections.add(s.toRoadmapSection())
                }

                val sectionNumber = sectionMatch.groupValues[1]
                val sectionTitle = sectionMatch.groupValues[2]

                currentSection = MutableRoadmapSection(
                    id = "section-${currentGroup.id}-${sectionOrder}",
                    number = sectionNumber,
                    title = sectionTitle,
                    order = sectionOrder++
                )
                continue
            }

            // 跳过代码块
            if (trimmedLine.startsWith("```")) {
                continue
            }

            // 解析任务项
            val task = parseRoadmapTaskLine(trimmedLine)
            if (task != null) {
                if (currentSection != null) {
                    currentSection.tasks.add(task)
                } else if (currentGroup != null) {
                    currentGroup.tasks.add(task)
                }
            }
        }

        // 保存最后的 group
        currentGroup?.let { g ->
            currentSection?.let { s ->
                g.sections.add(s.toRoadmapSection())
            }
            groups.add(g.toRoadmapGroup())
        }

        return groups
    }

    /**
     * 可变的 RoadmapGroup 用于解析过程
     */
    private class MutableRoadmapGroup(
        val id: String,
        val title: String,
        val order: Int
    ) {
        val tasks = mutableListOf<RoadmapTask>()
        val sections = mutableListOf<RoadmapSection>()

        fun toRoadmapGroup() = RoadmapGroup(
            id = id,
            title = title,
            tasks = tasks.toList(),
            sections = sections.toList(),
            source = TaskSource.ROADMAP_FILE,
            order = order
        )
    }

    /**
     * 可变的 RoadmapSection 用于解析过程
     */
    private class MutableRoadmapSection(
        val id: String,
        val number: String,
        val title: String,
        val order: Int
    ) {
        val tasks = mutableListOf<RoadmapTask>()

        fun toRoadmapSection() = RoadmapSection(
            id = id,
            title = title,
            number = number,
            tasks = tasks.toList(),
            order = order
        )
    }

    /**
     * 解析 Roadmap 文件中的任务行
     * 比 parseTaskLine 更灵活，接受所有列表项
     */
    private fun parseRoadmapTaskLine(line: String): RoadmapTask? {
        val trimmed = line.trim()

        // Checkbox 格式: - [ ] Task 或 - [x] Task
        val checkboxRegex = Regex("^[-*]\\s*\\[([xX\\s])\\]\\s+(.+)$")
        val checkboxMatch = checkboxRegex.find(trimmed)
        if (checkboxMatch != null) {
            val isCompleted = checkboxMatch.groupValues[1].lowercase() == "x"
            val title = checkboxMatch.groupValues[2].trim()
            return RoadmapTask(
                id = UUID.randomUUID().toString(),
                title = title,
                status = if (isCompleted) TaskStatus.COMPLETED else TaskStatus.PENDING,
                source = TaskSource.ROADMAP_FILE
            )
        }

        // 列表格式: - Task 或 * Task 或 1. Task
        val listRegex = Regex("^[-*]\\s+(.+)$|^\\d+\\.\\s+(.+)$")
        val listMatch = listRegex.find(trimmed)
        if (listMatch != null) {
            val title = (listMatch.groupValues[1].ifBlank { listMatch.groupValues[2] }).trim()
            // 过滤掉太短的内容或纯标点
            if (title.length > 2 && !title.all { it.isWhitespace() || it in ".-*[]" }) {
                return RoadmapTask(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    status = TaskStatus.PENDING,
                    source = TaskSource.ROADMAP_FILE
                )
            }
        }

        return null
    }

    /**
     * 解析单行任务
     * 支持格式：
     * - [ ] Task name
     * - [x] Completed task
     * - [X] Completed task
     * - Task name (without checkbox)
     * * Task name
     * 1. Task name
     */
    private fun parseTaskLine(line: String, section: String): RoadmapTask? {
        val trimmed = line.trim()

        // Checkbox 格式: - [ ] Task 或 - [x] Task
        val checkboxRegex = Regex("^[-*]\\s*\\[([xX\\s])\\]\\s+(.+)$")
        val checkboxMatch = checkboxRegex.find(trimmed)
        if (checkboxMatch != null) {
            val isCompleted = checkboxMatch.groupValues[1].lowercase() == "x"
            val title = checkboxMatch.groupValues[2].trim()
            return RoadmapTask(
                id = UUID.randomUUID().toString(),
                title = title,
                status = if (isCompleted) TaskStatus.COMPLETED else TaskStatus.PENDING,
                source = TaskSource.CLAUDE_MD
            )
        }

        // 列表格式: - Task 或 * Task 或 1. Task（仅在 roadmap/todo 部分）
        if (section in listOf("roadmap", "todo", "completed")) {
            val listRegex = Regex("^[-*]\\s+(.+)$|^\\d+\\.\\s+(.+)$")
            val listMatch = listRegex.find(trimmed)
            if (listMatch != null) {
                val title = (listMatch.groupValues[1].ifBlank { listMatch.groupValues[2] }).trim()
                if (title.isNotBlank() && !title.startsWith("[")) {
                    return RoadmapTask(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        status = if (section == "completed") TaskStatus.COMPLETED else TaskStatus.PENDING,
                        source = TaskSource.CLAUDE_MD
                    )
                }
            }
        }

        return null
    }

    /**
     * 从对话中提取 TodoWrite 工具调用的任务
     */
    fun extractTodoWriteTasks(sessions: List<ConversationSession>): List<RoadmapTask> {
        val tasks = mutableListOf<RoadmapTask>()
        val seenTitles = mutableSetOf<String>()

        for (session in sessions) {
            for (segment in session.segments) {
                for (assistantMsg in segment.assistantMessages) {
                    for (block in assistantMsg.contentBlocks) {
                        if (block is ContentBlock.ToolUse && block.toolName.lowercase() == "todowrite") {
                            val todoItems = extractTodosFromToolUse(block, session, segment.timestamp)
                            for (item in todoItems) {
                                // 去重：相同标题的任务只保留最新的
                                if (item.title !in seenTitles) {
                                    seenTitles.add(item.title)
                                    tasks.add(item)
                                }
                            }
                        }
                    }
                }
            }
        }

        return tasks
    }

    /**
     * 从 TodoWrite 工具调用中提取任务
     */
    private fun extractTodosFromToolUse(
        toolUse: ContentBlock.ToolUse,
        session: ConversationSession,
        timestamp: Instant
    ): List<RoadmapTask> {
        val tasks = mutableListOf<RoadmapTask>()

        // TodoWrite 的 input 格式通常是 { "todos": [...] }
        val todosInput = toolUse.input["todos"]

        when (todosInput) {
            is List<*> -> {
                for (todo in todosInput) {
                    if (todo is Map<*, *>) {
                        val content = todo["content"]?.toString() ?: continue
                        val statusStr = todo["status"]?.toString() ?: "pending"

                        val status = when (statusStr.lowercase()) {
                            "completed" -> TaskStatus.COMPLETED
                            "in_progress" -> TaskStatus.IN_PROGRESS
                            else -> TaskStatus.PENDING
                        }

                        tasks.add(
                            RoadmapTask(
                                id = UUID.randomUUID().toString(),
                                title = content,
                                status = status,
                                source = TaskSource.TODO_WRITE,
                                createdAt = timestamp,
                                updatedAt = timestamp,
                                relatedSessions = listOf(
                                    RelatedSession(
                                        sessionId = session.sessionId,
                                        sessionSlug = session.slug,
                                        matchType = MatchType.TODO_WRITE,
                                        matchedText = content,
                                        timestamp = timestamp
                                    )
                                )
                            )
                        )
                    }
                }
            }
            is String -> {
                // 简单字符串格式
                tasks.add(
                    RoadmapTask(
                        id = UUID.randomUUID().toString(),
                        title = todosInput,
                        status = TaskStatus.PENDING,
                        source = TaskSource.TODO_WRITE,
                        createdAt = timestamp,
                        relatedSessions = listOf(
                            RelatedSession(
                                sessionId = session.sessionId,
                                sessionSlug = session.slug,
                                matchType = MatchType.TODO_WRITE,
                                matchedText = todosInput,
                                timestamp = timestamp
                            )
                        )
                    )
                )
            }
        }

        return tasks
    }

    /**
     * 将任务与对话会话关联（通过关键词匹配）
     */
    fun linkTasksToSessions(
        tasks: List<RoadmapTask>,
        sessions: List<ConversationSession>
    ): List<RoadmapTask> {
        return tasks.map { task ->
            val relatedSessions = findRelatedSessions(task, sessions)
            task.copy(
                relatedSessions = (task.relatedSessions + relatedSessions).distinctBy { it.sessionId }
            )
        }
    }

    /**
     * 查找与任务相关的会话
     */
    private fun findRelatedSessions(
        task: RoadmapTask,
        sessions: List<ConversationSession>
    ): List<RelatedSession> {
        val related = mutableListOf<RelatedSession>()

        // 提取任务标题中的关键词
        val keywords = extractKeywords(task.title)
        if (keywords.isEmpty()) return related

        for (session in sessions) {
            for (segment in session.segments) {
                // 检查用户消息
                val userMessageLower = segment.userMessage.lowercase()
                val matchedKeyword = keywords.find { it in userMessageLower }

                if (matchedKeyword != null) {
                    related.add(
                        RelatedSession(
                            sessionId = session.sessionId,
                            sessionSlug = session.slug,
                            matchType = MatchType.KEYWORD,
                            matchedText = segment.userMessagePreview,
                            timestamp = segment.timestamp
                        )
                    )
                    break // 每个 session 只添加一次
                }

                // 检查助手消息中的文本
                for (assistantMsg in segment.assistantMessages) {
                    for (block in assistantMsg.contentBlocks) {
                        if (block is ContentBlock.Text) {
                            val textLower = block.text.lowercase()
                            val textMatchedKeyword = keywords.find { it in textLower }
                            if (textMatchedKeyword != null) {
                                related.add(
                                    RelatedSession(
                                        sessionId = session.sessionId,
                                        sessionSlug = session.slug,
                                        matchType = MatchType.KEYWORD,
                                        matchedText = block.text.take(100),
                                        timestamp = segment.timestamp
                                    )
                                )
                                break
                            }
                        }
                    }
                }
            }
        }

        return related.distinctBy { it.sessionId }
    }

    /**
     * 从文本中提取关键词
     */
    private fun extractKeywords(text: String): List<String> {
        // 移除常见的停用词和标点
        val stopWords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "shall", "can", "need",
            "add", "create", "implement", "fix", "update", "remove", "delete",
            "实现", "添加", "修复", "更新", "删除", "创建"
        )

        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in stopWords }
            .take(5)
    }

    /**
     * 构建完整的项目 Roadmap
     */
    fun buildProjectRoadmap(
        projectId: String,
        projectPath: String,
        claudeMdContent: String?,
        opencodeMdContent: String?,
        roadmapFiles: Map<String, String> = emptyMap(),
        sessions: List<ConversationSession>
    ): ProjectRoadmap {
        val groups = mutableListOf<RoadmapGroup>()
        var orderIndex = 0

        // 1. 解析 CLAUDE.md
        if (!claudeMdContent.isNullOrBlank()) {
            val claudeMdInfo = parseClaudeMd(claudeMdContent)

            if (claudeMdInfo.roadmapItems.isNotEmpty()) {
                groups.add(
                    RoadmapGroup(
                        id = "claude-md-roadmap",
                        title = "Roadmap (CLAUDE.md)",
                        description = claudeMdInfo.projectDescription,
                        tasks = linkTasksToSessions(claudeMdInfo.roadmapItems, sessions),
                        source = TaskSource.CLAUDE_MD,
                        order = orderIndex++
                    )
                )
            }

            if (claudeMdInfo.todoItems.isNotEmpty()) {
                groups.add(
                    RoadmapGroup(
                        id = "claude-md-todo",
                        title = "TODO (CLAUDE.md)",
                        tasks = linkTasksToSessions(claudeMdInfo.todoItems, sessions),
                        source = TaskSource.CLAUDE_MD,
                        order = orderIndex++
                    )
                )
            }
        }

        // 2. 解析 opencode.md（如果有）
        if (!opencodeMdContent.isNullOrBlank()) {
            val opencodeMdInfo = parseClaudeMd(opencodeMdContent) // 格式相同

            if (opencodeMdInfo.roadmapItems.isNotEmpty()) {
                groups.add(
                    RoadmapGroup(
                        id = "opencode-md-roadmap",
                        title = "Roadmap (opencode.md)",
                        tasks = linkTasksToSessions(
                            opencodeMdInfo.roadmapItems.map { it.copy(source = TaskSource.OPENCODE_MD) },
                            sessions
                        ),
                        source = TaskSource.OPENCODE_MD,
                        order = orderIndex++
                    )
                )
            }
        }

        // 3. 解析 roadmap 相关文件（使用层级解析）
        for ((filePath, content) in roadmapFiles) {
            val fileName = filePath.substringAfterLast("/").lowercase()

            // 对于 ROADMAP.md 使用层级解析
            if (fileName == "roadmap.md") {
                val hierarchicalGroups = parseRoadmapHierarchy(content)
                if (hierarchicalGroups.isNotEmpty()) {
                    // 为每个 group 的任务添加 session 关联
                    for (group in hierarchicalGroups) {
                        val linkedTasks = linkTasksToSessions(group.tasks, sessions)
                        val linkedSections = group.sections.map { section ->
                            section.copy(tasks = linkTasksToSessions(section.tasks, sessions))
                        }
                        groups.add(
                            group.copy(
                                tasks = linkedTasks,
                                sections = linkedSections,
                                order = orderIndex++
                            )
                        )
                    }
                    continue
                }
            }

            // 其他文件使用简单解析
            val fileInfo = parseRoadmapFile(content)

            if (fileInfo.roadmapItems.isNotEmpty() || fileInfo.todoItems.isNotEmpty()) {
                val allTasks = (fileInfo.roadmapItems + fileInfo.todoItems).map {
                    it.copy(source = TaskSource.ROADMAP_FILE)
                }
                groups.add(
                    RoadmapGroup(
                        id = "roadmap-file-${filePath.hashCode()}",
                        title = filePath.substringAfterLast("/"),
                        description = fileInfo.projectDescription.take(200),
                        tasks = linkTasksToSessions(allTasks, sessions),
                        source = TaskSource.ROADMAP_FILE,
                        order = orderIndex++
                    )
                )
            }
        }

        // 4. 从对话中提取 TodoWrite 任务
        val todoWriteTasks = extractTodoWriteTasks(sessions)
        if (todoWriteTasks.isNotEmpty()) {
            // 按状态分组
            val pendingTasks = todoWriteTasks.filter { it.status != TaskStatus.COMPLETED }
            val completedTasks = todoWriteTasks.filter { it.status == TaskStatus.COMPLETED }

            if (pendingTasks.isNotEmpty()) {
                groups.add(
                    RoadmapGroup(
                        id = "conversation-tasks",
                        title = "Tasks (from conversations)",
                        tasks = linkTasksToSessions(pendingTasks, sessions),
                        source = TaskSource.TODO_WRITE,
                        order = orderIndex++
                    )
                )
            }

            if (completedTasks.isNotEmpty()) {
                groups.add(
                    RoadmapGroup(
                        id = "conversation-completed",
                        title = "Completed (from conversations)",
                        tasks = completedTasks,
                        source = TaskSource.TODO_WRITE,
                        order = orderIndex++
                    )
                )
            }
        }

        return ProjectRoadmap(
            projectId = projectId,
            projectPath = projectPath,
            groups = groups.sortedBy { it.order },
            lastUpdated = Instant.now(),
            claudeMdContent = claudeMdContent,
            opencodeMdContent = opencodeMdContent
        )
    }
}
