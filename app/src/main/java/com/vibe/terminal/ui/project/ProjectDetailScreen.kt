package com.vibe.terminal.ui.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.terminal.data.conversation.ConversationParser
import com.vibe.terminal.domain.model.ConversationSession
import com.vibe.terminal.domain.model.ConversationTopic
import com.vibe.terminal.domain.model.MatchType
import com.vibe.terminal.domain.model.ProjectRoadmap
import com.vibe.terminal.domain.model.RelatedSession
import com.vibe.terminal.domain.model.RoadmapGroup
import com.vibe.terminal.domain.model.RoadmapSection
import com.vibe.terminal.domain.model.RoadmapTask
import com.vibe.terminal.domain.model.TaskSource
import com.vibe.terminal.domain.model.TaskStatus
import com.vibe.terminal.ui.theme.StatusConnected
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class TopicViewMode {
    BY_TIME,     // 按时间间隔分组（20分钟）
    BY_SESSION,  // 按会话断点分组（parentUuid）
    ROADMAP      // 项目路线图
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToTerminal: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val smartParsingEnabled by viewModel.smartParsingEnabled.collectAsState()
    var viewMode by remember { mutableStateOf(TopicViewMode.BY_SESSION) }
    var isTerminalPreviewCollapsed by remember { mutableStateOf(false) }

    // Load roadmap when switching to ROADMAP view
    LaunchedEffect(viewMode) {
        if (viewMode == TopicViewMode.ROADMAP && uiState.roadmap == null && !uiState.isRoadmapLoading) {
            viewModel.loadRoadmap()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.project?.name ?: "Project",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        uiState.machine?.let { machine ->
                            Text(
                                text = machine.displayAddress(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshHistory() },
                        enabled = !uiState.isLoading && !uiState.isRefreshing
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToTerminal,
                containerColor = StatusConnected
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Open Terminal",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 项目信息卡片
            ProjectInfoCard(
                projectName = uiState.project?.name ?: "",
                workingDirectory = uiState.project?.workingDirectory ?: "",
                zellijSession = uiState.project?.zellijSession ?: "",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 终端预览 - 可折叠
            if (uiState.sessions.isNotEmpty()) {
                com.vibe.terminal.ui.conversation.TerminalPreview(
                    sessions = uiState.sessions,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    maxLines = 6,
                    assistantType = uiState.project?.assistantType ?: com.vibe.terminal.domain.model.AssistantType.CLAUDE_CODE,
                    isCollapsed = isTerminalPreviewCollapsed,
                    onToggleCollapse = { isTerminalPreviewCollapsed = !isTerminalPreviewCollapsed }
                )
            }

            HorizontalDivider()

            // 视图切换
            ViewModeSelector(
                currentMode = viewMode,
                onModeChange = { viewMode = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 内容区域
            val loadingProgress = uiState.loadingProgress
            when {
                uiState.isLoading && uiState.sessions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            if (loadingProgress != null) {
                                Text(
                                    text = "Loading ${loadingProgress.current}/${loadingProgress.total}...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (loadingProgress.currentFileName.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = loadingProgress.currentFileName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    text = "Loading conversation history...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                uiState.sessions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No conversation history",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    Column {
                        // 如果正在加载更多，显示进度条
                        if (uiState.isLoading && loadingProgress != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Loading ${loadingProgress.current}/${loadingProgress.total}..." +
                                                if (loadingProgress.failedCount > 0) " (${loadingProgress.failedCount} failed)" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (loadingProgress.failedCount > 0)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        // 根据模式生成主题列表
                        // 使用 sessions.size 作为额外的 key 确保列表更新时重新计算
                        if (viewMode == TopicViewMode.ROADMAP) {
                            RoadmapView(
                                roadmap = uiState.roadmap,
                                sessions = uiState.sessions,
                                isLoading = uiState.isRoadmapLoading,
                                error = uiState.roadmapError,
                                onRetry = { viewModel.loadRoadmap() },
                                onToggleCompletion = { task -> viewModel.toggleTaskCompletion(task) },
                                onDeleteTask = { task -> viewModel.deleteTask(task) },
                                smartParsingEnabled = smartParsingEnabled,
                                onSmartParsingToggle = { viewModel.setSmartParsingEnabled(it) },
                                onScrollStart = { isTerminalPreviewCollapsed = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val topics = remember(uiState.sessions, uiState.sessions.size, viewMode) {
                                when (viewMode) {
                                    TopicViewMode.BY_TIME -> ConversationParser.groupByTimeGap(uiState.sessions)
                                    TopicViewMode.BY_SESSION -> ConversationParser.groupBySessionBreaks(uiState.sessions)
                                    TopicViewMode.ROADMAP -> emptyList() // Handled above
                                }
                            }

                            TopicListView(
                                topics = topics,
                                viewMode = viewMode,
                                onScrollStart = { isTerminalPreviewCollapsed = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectInfoCard(
    projectName: String,
    workingDirectory: String,
    zellijSession: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = StatusConnected,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = workingDirectory,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Session: $zellijSession",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeSelector(
    currentMode: TopicViewMode,
    onModeChange: (TopicViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentMode == TopicViewMode.BY_SESSION,
            onClick = { onModeChange(TopicViewMode.BY_SESSION) },
            label = { Text("会话") },
            leadingIcon = {
                Icon(
                    Icons.Default.Topic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        FilterChip(
            selected = currentMode == TopicViewMode.BY_TIME,
            onClick = { onModeChange(TopicViewMode.BY_TIME) },
            label = { Text("时间") },
            leadingIcon = {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        FilterChip(
            selected = currentMode == TopicViewMode.ROADMAP,
            onClick = { onModeChange(TopicViewMode.ROADMAP) },
            label = { Text("路线图") },
            leadingIcon = {
                Icon(
                    Icons.Default.TaskAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
    }
}

@Composable
private fun TopicListView(
    topics: List<ConversationTopic>,
    viewMode: TopicViewMode,
    onScrollStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    val listState = rememberLazyListState()

    // 检测滚动开始并自动折叠终端预览
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            onScrollStart()
        }
    }

    Column(modifier = modifier) {
        // 统计信息
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when (viewMode) {
                    TopicViewMode.BY_SESSION -> "${topics.size} 个会话段"
                    TopicViewMode.BY_TIME -> "${topics.size} 个时间段 (20分钟间隔)"
                    TopicViewMode.ROADMAP -> "" // Not displayed in this view
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(topics, key = { it.id }) { topic ->
                ExpandableTopicCard(
                    topic = topic,
                    dateFormatter = dateFormatter
                )
            }
        }
    }
}

@Composable
private fun ExpandableTopicCard(
    topic: ConversationTopic,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 主题头部
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Topic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 时间范围
                        Text(
                            text = "${dateFormatter.format(topic.startTime)} ~ ${dateFormatter.format(topic.endTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    // 统计信息
                    Text(
                        text = "${topic.userMessageCount} 条请求 · ${topic.assistantMessageCount} 条回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 展开显示该主题下的所有对话
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // 显示主题内的所有 segments
                    topic.segments.forEachIndexed { index, segment ->
                        SegmentCard(
                            index = index + 1,
                            segment = segment,
                            dateFormatter = dateFormatter
                        )
                        if (index < topic.segments.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentCard(
    index: Int,
    segment: com.vibe.terminal.domain.model.ConversationSegment,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }

    // 判断消息类型
    val isSystemContinuation = segment.userMessage.startsWith("This session is being continued")
    val isEmptyUserMessage = segment.userMessage.isBlank()
    val isAutoGenerated = isSystemContinuation || isEmptyUserMessage

    // 生成预览文本
    val previewText = when {
        isSystemContinuation -> "[会话自动延续]"
        isEmptyUserMessage -> getAutoGeneratedPreview(segment)
        else -> segment.userMessagePreview
    }

    // 预览颜色
    val previewColor = if (isAutoGenerated) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // 序号背景色
    val indexColor = if (isAutoGenerated) {
        Color(0xFF9C27B0) // 紫色表示系统生成
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDetails = !showDetails },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // 序号
                Surface(
                    color = indexColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isAutoGenerated) "⚡" else "$index",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = previewColor,
                        fontWeight = if (isAutoGenerated) FontWeight.Medium else FontWeight.Normal,
                        maxLines = if (showDetails) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = dateFormatter.format(segment.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        if (isAutoGenerated) {
                            FeatureChip("Auto", Color(0xFF9C27B0))
                        }
                        if (segment.hasThinking) {
                            FeatureChip("Thinking", Color(0xFF9C27B0))
                        }
                        if (segment.hasToolUse) {
                            FeatureChip("Tools", Color(0xFF2196F3))
                        }
                        if (segment.hasCodeChange) {
                            FeatureChip("Code", Color(0xFF4CAF50))
                        }
                    }
                }
            }

            // 展开显示详细内容
            AnimatedVisibility(
                visible = showDetails,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // 用户/系统消息部分 - 系统消息可展开收起
                    if (!isEmptyUserMessage) {
                        if (isSystemContinuation && segment.userMessage.length > 200) {
                            // 系统消息太长，使用可展开组件
                            ExpandableContentBlock(
                                title = "📋 System",
                                content = segment.userMessage,
                                color = Color(0xFF9C27B0),
                                previewLength = 150
                            )
                        } else {
                            // 普通用户消息，直接显示
                            Text(
                                text = if (isSystemContinuation) "System:" else "User:",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSystemContinuation) Color(0xFF9C27B0) else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = segment.userMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 助手回复详情
                    if (segment.assistantMessages.isNotEmpty()) {
                        Text(
                            text = "Assistant: ${segment.assistantMessages.size} response(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        segment.assistantMessages.forEach { msg ->
                            AssistantMessageDetail(msg)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 为自动生成的消息生成预览文本
 */
private fun getAutoGeneratedPreview(segment: com.vibe.terminal.domain.model.ConversationSegment): String {
    val toolNames = segment.assistantMessages
        .flatMap { it.contentBlocks }
        .filterIsInstance<com.vibe.terminal.domain.model.ContentBlock.ToolUse>()
        .map { it.toolName }
        .distinct()

    val hasThinking = segment.assistantMessages
        .flatMap { it.contentBlocks }
        .any { it is com.vibe.terminal.domain.model.ContentBlock.Thinking }

    return when {
        toolNames.isNotEmpty() -> "[工具执行: ${toolNames.joinToString(", ")}]"
        hasThinking -> "[思考中...]"
        else -> "[系统自动响应]"
    }
}

/**
 * 显示助手消息的详细内容
 */
@Composable
private fun AssistantMessageDetail(msg: com.vibe.terminal.domain.model.AssistantMessage) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        msg.contentBlocks.forEach { block ->
            when (block) {
                is com.vibe.terminal.domain.model.ContentBlock.Thinking -> {
                    // Thinking 块 - 完整显示，不收起
                    Surface(
                        color = Color(0xFF9C27B0).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "💭 Thinking",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF9C27B0),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = block.thinking,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                is com.vibe.terminal.domain.model.ContentBlock.ToolUse -> {
                    val toolColor = when (block.toolName) {
                        "Edit", "Write" -> Color(0xFF4CAF50)
                        "Bash" -> Color(0xFFFF9800)
                        "Read", "Glob", "Grep" -> Color(0xFF2196F3)
                        else -> Color(0xFF607D8B)
                    }
                    val inputPreview = getToolInputPreview(block.toolName, block.input)
                    // Tool Use - 短内容直接显示
                    Surface(
                        color = toolColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "🔧 ${block.toolName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = toolColor,
                                fontWeight = FontWeight.Bold
                            )
                            if (inputPreview.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = inputPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                is com.vibe.terminal.domain.model.ContentBlock.Text -> {
                    if (block.text.isNotBlank()) {
                        // Text 块 - 长文本可收起
                        ExpandableContentBlock(
                            title = "💬 Text",
                            content = block.text,
                            color = MaterialTheme.colorScheme.primary,
                            previewLength = 300,
                            showTitle = false
                        )
                    }
                }
                is com.vibe.terminal.domain.model.ContentBlock.ToolResult -> {
                    if (block.content.isNotBlank()) {
                        // Tool Result - 可收起
                        ExpandableContentBlock(
                            title = "📋 Result",
                            content = block.content,
                            color = Color(0xFF607D8B),
                            previewLength = 100
                        )
                    }
                }
            }
        }
    }
}

/**
 * 可展开/收起的内容块
 */
@Composable
private fun ExpandableContentBlock(
    title: String,
    content: String,
    color: Color,
    previewLength: Int = 150,
    showTitle: Boolean = true,
    alwaysShowFull: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    val isLongContent = content.length > previewLength && !alwaysShowFull

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(
                if (isLongContent) Modifier.clickable { expanded = !expanded }
                else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 标题行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showTitle) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isLongContent) {
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        color = color.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (expanded) "收起" else "展开",
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                fontSize = 9.sp
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 内容
            if (content.isNotBlank()) {
                if (showTitle) {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                AnimatedVisibility(
                    visible = true,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Text(
                        text = if (expanded || alwaysShowFull || !isLongContent) {
                            content
                        } else {
                            content.take(previewLength) + "..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = if (expanded || alwaysShowFull) Int.MAX_VALUE else 5
                    )
                }
            }
        }
    }
}

/**
 * 获取工具输入的预览文本
 */
private fun getToolInputPreview(toolName: String, input: Map<String, Any?>): String {
    return when (toolName) {
        "Read" -> input["file_path"]?.toString() ?: ""
        "Edit" -> input["file_path"]?.toString() ?: ""
        "Write" -> input["file_path"]?.toString() ?: ""
        "Bash" -> input["command"]?.toString()?.take(100) ?: ""
        "Glob" -> input["pattern"]?.toString() ?: ""
        "Grep" -> input["pattern"]?.toString() ?: ""
        "WebSearch" -> input["query"]?.toString() ?: ""
        "WebFetch" -> input["url"]?.toString() ?: ""
        else -> ""
    }
}

@Composable
private fun FeatureChip(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            fontSize = 9.sp
        )
    }
}

// ==================== Roadmap View Components ====================

@Composable
private fun RoadmapView(
    roadmap: ProjectRoadmap?,
    sessions: List<ConversationSession>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onToggleCompletion: (RoadmapTask) -> Unit,
    onDeleteTask: (RoadmapTask) -> Unit,
    smartParsingEnabled: Boolean,
    onSmartParsingToggle: (Boolean) -> Unit,
    onScrollStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when {
        isLoading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在加载路线图...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        error != null -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        onClick = onRetry,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "重试",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        roadmap == null || roadmap.groups.isEmpty() -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.TaskAlt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无路线图数据",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "请在项目中添加 CLAUDE.md 文件\n或使用 TodoWrite 工具创建任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        else -> {
            RoadmapContent(
                roadmap = roadmap,
                sessions = sessions,
                onToggleCompletion = onToggleCompletion,
                onDeleteTask = onDeleteTask,
                smartParsingEnabled = smartParsingEnabled,
                onSmartParsingToggle = onSmartParsingToggle,
                onScrollStart = onScrollStart,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun RoadmapContent(
    roadmap: ProjectRoadmap,
    sessions: List<ConversationSession>,
    onToggleCompletion: (RoadmapTask) -> Unit,
    onDeleteTask: (RoadmapTask) -> Unit,
    smartParsingEnabled: Boolean,
    onSmartParsingToggle: (Boolean) -> Unit,
    onScrollStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    val listState = rememberLazyListState()

    // 检测滚动开始并自动折叠终端预览
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            onScrollStart()
        }
    }

    Column(modifier = modifier) {
        // Progress summary
        RoadmapProgressHeader(
            roadmap = roadmap,
            smartParsingEnabled = smartParsingEnabled,
            onSmartParsingToggle = onSmartParsingToggle
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(roadmap.groups, key = { it.id }) { group ->
                RoadmapGroupCard(
                    group = group,
                    sessions = sessions,
                    dateFormatter = dateFormatter,
                    onToggleCompletion = onToggleCompletion,
                    onDeleteTask = onDeleteTask,
                    smartParsingEnabled = smartParsingEnabled
                )
            }
        }
    }
}

@Composable
private fun RoadmapProgressHeader(
    roadmap: ProjectRoadmap,
    smartParsingEnabled: Boolean,
    onSmartParsingToggle: (Boolean) -> Unit
) {
    val statusCounts = roadmap.getStatusCounts()
    val totalTasks = roadmap.getAllTasks().size
    val completedTasks = statusCounts[TaskStatus.COMPLETED] ?: 0
    val inProgressTasks = statusCounts[TaskStatus.IN_PROGRESS] ?: 0
    val pendingTasks = statusCounts[TaskStatus.PENDING] ?: 0
    val progressPercent = roadmap.getProgressPercentage()

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "项目进度",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${progressPercent.toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(progressPercent / 100f)
                            .height(8.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = StatusConnected
                    ) {}
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Status counts and smart parsing toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status counts
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatusCount("已完成", completedTasks, StatusConnected)
                    StatusCount("进行中", inProgressTasks, Color(0xFFFF9800))
                    StatusCount("待处理", pendingTasks, MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Smart parsing toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "智能解析",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = smartParsingEnabled,
                        onCheckedChange = onSmartParsingToggle,
                        modifier = Modifier.height(20.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusCount(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(8.dp),
            shape = RoundedCornerShape(4.dp),
            color = color
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label: $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoadmapGroupCard(
    group: RoadmapGroup,
    sessions: List<ConversationSession>,
    dateFormatter: DateTimeFormatter,
    onToggleCompletion: (RoadmapTask) -> Unit,
    onDeleteTask: (RoadmapTask) -> Unit,
    smartParsingEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    // 总任务数包含直接任务和 sections 中的任务
    val totalTaskCount = group.tasks.size + group.sections.sumOf { it.tasks.size }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sourceIcon = when (group.source) {
                    TaskSource.CLAUDE_MD -> "📄"
                    TaskSource.OPENCODE_MD -> "📋"
                    TaskSource.ROADMAP_FILE -> "🗺️"
                    TaskSource.TODO_WRITE -> "✏️"
                    TaskSource.CONVERSATION -> "💬"
                    null -> "📁"
                }
                Text(
                    text = sourceIcon,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (group.description.isNotBlank()) {
                        Text(
                            text = group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "$totalTaskCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tasks list (包括直接任务和 sections)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    // 直接任务（无 section 的任务）
                    group.tasks.forEachIndexed { index, task ->
                        RoadmapTaskItem(
                            task = task,
                            sessions = sessions,
                            dateFormatter = dateFormatter,
                            onToggleCompletion = onToggleCompletion,
                            onDeleteTask = onDeleteTask,
                            smartParsingEnabled = smartParsingEnabled
                        )
                        if (index < group.tasks.size - 1 || group.sections.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Sections（如 1.1, 1.2 等子分组）
                    group.sections.forEachIndexed { sectionIndex, section ->
                        RoadmapSectionCard(
                            section = section,
                            sessions = sessions,
                            dateFormatter = dateFormatter,
                            onToggleCompletion = onToggleCompletion,
                            onDeleteTask = onDeleteTask,
                            smartParsingEnabled = smartParsingEnabled
                        )
                        if (sectionIndex < group.sections.size - 1) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section 子分组卡片（如 1.1 项目初始化）
 */
@Composable
private fun RoadmapSectionCard(
    section: RoadmapSection,
    sessions: List<ConversationSession>,
    dateFormatter: DateTimeFormatter,
    onToggleCompletion: (RoadmapTask) -> Unit,
    onDeleteTask: (RoadmapTask) -> Unit,
    smartParsingEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Section number badge
                if (section.number.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = section.number,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = section.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${section.tasks.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Section tasks
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    section.tasks.forEachIndexed { index, task ->
                        RoadmapTaskItem(
                            task = task,
                            sessions = sessions,
                            dateFormatter = dateFormatter,
                            onToggleCompletion = onToggleCompletion,
                            onDeleteTask = onDeleteTask,
                            smartParsingEnabled = smartParsingEnabled
                        )
                        if (index < section.tasks.size - 1) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoadmapTaskItem(
    task: RoadmapTask,
    sessions: List<ConversationSession>,
    dateFormatter: DateTimeFormatter,
    onToggleCompletion: (RoadmapTask) -> Unit,
    onDeleteTask: (RoadmapTask) -> Unit,
    smartParsingEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showRelatedSessions by remember { mutableStateOf(false) }
    var selectedRelatedSession by remember { mutableStateOf<RelatedSession?>(null) }

    // 使用有效状态（本地完成优先）
    val effectiveStatus = task.getEffectiveStatus()
    val statusColor = when (effectiveStatus) {
        TaskStatus.COMPLETED -> StatusConnected
        TaskStatus.IN_PROGRESS -> Color(0xFFFF9800)
        TaskStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        TaskStatus.BLOCKED -> MaterialTheme.colorScheme.error
    }

    val statusIcon = when (effectiveStatus) {
        TaskStatus.COMPLETED -> "✓"
        TaskStatus.IN_PROGRESS -> "◐"
        TaskStatus.PENDING -> "○"
        TaskStatus.BLOCKED -> "✕"
    }

    // 只有当智能解析开启且有关联会话时才可点击
    val hasRelatedSessions = smartParsingEnabled && task.relatedSessions.isNotEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = hasRelatedSessions) {
                showRelatedSessions = !showRelatedSessions
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Completion checkbox (clickable status indicator)
                Surface(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onToggleCompletion(task) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (task.isLocallyCompleted) {
                        StatusConnected.copy(alpha = 0.2f)
                    } else {
                        statusColor.copy(alpha = 0.2f)
                    },
                    border = if (task.isLocallyCompleted) {
                        androidx.compose.foundation.BorderStroke(2.dp, StatusConnected)
                    } else {
                        null
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (task.isLocallyCompleted) "✓" else statusIcon,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (task.isLocallyCompleted) StatusConnected else statusColor,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (effectiveStatus == TaskStatus.IN_PROGRESS) FontWeight.Bold else FontWeight.Normal,
                        color = if (effectiveStatus == TaskStatus.COMPLETED) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (task.isLocallyCompleted) {
                            TextDecoration.LineThrough
                        } else {
                            null
                        }
                    )

                    if (task.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Metadata row
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.createdAt != null) {
                            Text(
                                text = dateFormatter.format(task.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                        if (smartParsingEnabled && task.relatedSessions.isNotEmpty()) {
                            FeatureChip(
                                text = "${task.relatedSessions.size} 关联",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        task.tags.take(2).forEach { tag ->
                            FeatureChip(text = tag, color = Color(0xFF607D8B))
                        }
                    }
                }

                // Delete button
                IconButton(
                    onClick = { onDeleteTask(task) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "删除任务",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Related sessions (expandable) - only show when smart parsing is enabled
            AnimatedVisibility(
                visible = smartParsingEnabled && showRelatedSessions && task.relatedSessions.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "关联对话:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    task.relatedSessions.take(3).forEach { relatedSession ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { selectedRelatedSession = relatedSession },
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = relatedSession.matchedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = dateFormatter.format(relatedSession.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp
                                    )
                                    Text(
                                        text = "点击查看详情 →",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Related session detail dialog - shows only the specific matched segment
    selectedRelatedSession?.let { relatedSession ->
        val session = sessions.find { it.sessionId == relatedSession.sessionId }
        // Find the specific segment that matches the timestamp
        val matchedSegment = session?.segments?.find { segment ->
            segment.timestamp == relatedSession.timestamp ||
            segment.userMessage.contains(relatedSession.matchedText.take(50)) ||
            segment.assistantMessages.any { msg ->
                msg.contentBlocks.any { block ->
                    when (block) {
                        is com.vibe.terminal.domain.model.ContentBlock.Text ->
                            block.text.contains(relatedSession.matchedText.take(50))
                        is com.vibe.terminal.domain.model.ContentBlock.ToolUse ->
                            block.toolName.contains(relatedSession.matchedText.take(20))
                        else -> false
                    }
                }
            }
        }

        RelatedSessionDetailDialog(
            relatedSession = relatedSession,
            segment = matchedSegment,
            dateFormatter = dateFormatter,
            onDismiss = { selectedRelatedSession = null }
        )
    }
}

/**
 * 关联对话详情对话框
 * 只显示那一条具体的关联内容，而不是整个会话
 */
@Composable
private fun RelatedSessionDetailDialog(
    relatedSession: RelatedSession,
    segment: com.vibe.terminal.domain.model.ConversationSegment?,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Match type badge
                    val matchTypeText = when (relatedSession.matchType) {
                        MatchType.EXACT -> "精确匹配"
                        MatchType.KEYWORD -> "关键词"
                        MatchType.TODO_WRITE -> "TodoWrite"
                        MatchType.SEMANTIC -> "语义相关"
                    }
                    val matchTypeColor = when (relatedSession.matchType) {
                        MatchType.EXACT -> StatusConnected
                        MatchType.KEYWORD -> Color(0xFF2196F3)
                        MatchType.TODO_WRITE -> Color(0xFFFF9800)
                        MatchType.SEMANTIC -> Color(0xFF9C27B0)
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = matchTypeColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = matchTypeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = matchTypeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "关联详情",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateFormatter.format(relatedSession.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (relatedSession.sessionSlug != null) {
                    Text(
                        text = relatedSession.sessionSlug,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 匹配的文本
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "匹配内容",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = relatedSession.matchedText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // 如果找到了具体的 segment，显示详细内容
                if (segment != null) {
                    HorizontalDivider()

                    // 用户消息
                    if (segment.userMessage.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "U",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "用户消息",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = segment.userMessage,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // 助手回复
                    if (segment.assistantMessages.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "A",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "助手回复",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                segment.assistantMessages.forEach { msg ->
                                    msg.contentBlocks.forEach { block ->
                                        when (block) {
                                            is com.vibe.terminal.domain.model.ContentBlock.Text -> {
                                                if (block.text.isNotBlank()) {
                                                    Text(
                                                        text = block.text,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            is com.vibe.terminal.domain.model.ContentBlock.ToolUse -> {
                                                Surface(
                                                    color = Color(0xFF2196F3).copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "🔧 ${block.toolName}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF2196F3),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        val preview = getToolInputPreview(block.toolName, block.input)
                                                        if (preview.isNotBlank()) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = preview,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            is com.vibe.terminal.domain.model.ContentBlock.Thinking -> {
                                                Surface(
                                                    color = Color(0xFF9C27B0).copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "💭 ${block.thinking.take(200)}${if (block.thinking.length > 200) "..." else ""}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(8.dp),
                                                        fontSize = 10.sp,
                                                        color = Color(0xFF9C27B0)
                                                    )
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 特征标签
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        if (segment.hasThinking) {
                            FeatureChip("Thinking", Color(0xFF9C27B0))
                        }
                        if (segment.hasToolUse) {
                            FeatureChip("Tools", Color(0xFF2196F3))
                        }
                        if (segment.hasCodeChange) {
                            FeatureChip("Code", Color(0xFF4CAF50))
                        }
                    }
                } else {
                    // 没有找到具体 segment 时的提示
                    Text(
                        text = "无法找到对应的对话段落详情",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 会话详情对话框
 * 显示完整的会话信息，包括所有对话段落
 */
@Composable
private fun SessionDetailDialog(
    session: ConversationSession,
    dateFormatter: DateTimeFormatter,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = session.slug ?: "对话详情",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${dateFormatter.format(session.startTime)} ~ ${dateFormatter.format(session.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 统计信息
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${session.totalUserMessages}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "用户消息",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${session.totalAssistantMessages}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "助手回复",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${session.segments.size}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = "对话段落",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                // 对话段落列表
                items(session.segments) { segment ->
                    SessionSegmentItem(
                        segment = segment,
                        dateFormatter = dateFormatter
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 对话段落项
 */
@Composable
private fun SessionSegmentItem(
    segment: com.vibe.terminal.domain.model.ConversationSegment,
    dateFormatter: DateTimeFormatter
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 用户消息预览
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "U",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = segment.userMessagePreview.ifBlank { "[系统消息]" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = dateFormatter.format(segment.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                        if (segment.hasThinking) {
                            FeatureChip("Thinking", Color(0xFF9C27B0))
                        }
                        if (segment.hasToolUse) {
                            FeatureChip("Tools", Color(0xFF2196F3))
                        }
                        if (segment.hasCodeChange) {
                            FeatureChip("Code", Color(0xFF4CAF50))
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 展开显示详细内容
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // 完整用户消息
                    if (segment.userMessage.isNotBlank()) {
                        Text(
                            text = "用户消息:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = segment.userMessage,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp),
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 助手回复摘要
                    if (segment.assistantMessages.isNotEmpty()) {
                        Text(
                            text = "助手回复: ${segment.assistantMessages.size} 条",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        segment.assistantMessages.forEach { msg ->
                            msg.contentBlocks.forEach { block ->
                                when (block) {
                                    is com.vibe.terminal.domain.model.ContentBlock.Text -> {
                                        if (block.text.isNotBlank()) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = block.text.take(500) + if (block.text.length > 500) "..." else "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(8.dp),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                    is com.vibe.terminal.domain.model.ContentBlock.ToolUse -> {
                                        Surface(
                                            color = Color(0xFF2196F3).copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "🔧 ${block.toolName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF2196F3),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                val preview = getToolInputPreview(block.toolName, block.input)
                                                if (preview.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = preview,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    is com.vibe.terminal.domain.model.ContentBlock.Thinking -> {
                                        Surface(
                                            color = Color(0xFF9C27B0).copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "💭 ${block.thinking.take(200)}${if (block.thinking.length > 200) "..." else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(8.dp),
                                                fontSize = 10.sp,
                                                color = Color(0xFF9C27B0)
                                            )
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
