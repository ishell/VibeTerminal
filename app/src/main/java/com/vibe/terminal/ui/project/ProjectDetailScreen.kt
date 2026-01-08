package com.vibe.terminal.ui.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Topic
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.terminal.data.conversation.ConversationParser
import com.vibe.terminal.domain.model.ConversationSession
import com.vibe.terminal.domain.model.ConversationTopic
import com.vibe.terminal.ui.theme.StatusConnected
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class TopicViewMode {
    BY_TIME,     // 按时间间隔分组（20分钟）
    BY_SESSION   // 按会话断点分组（parentUuid）
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToTerminal: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var viewMode by remember { mutableStateOf(TopicViewMode.BY_SESSION) }

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

            HorizontalDivider()

            // 视图切换
            ViewModeSelector(
                currentMode = viewMode,
                onModeChange = { viewMode = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 内容区域
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading conversation history...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    // 根据模式生成主题列表
                    val topics = remember(uiState.sessions, viewMode) {
                        when (viewMode) {
                            TopicViewMode.BY_TIME -> ConversationParser.groupByTimeGap(uiState.sessions)
                            TopicViewMode.BY_SESSION -> ConversationParser.groupBySessionBreaks(uiState.sessions)
                        }
                    }

                    TopicListView(
                        topics = topics,
                        viewMode = viewMode,
                        modifier = Modifier.fillMaxSize()
                    )
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
    }
}

@Composable
private fun TopicListView(
    topics: List<ConversationTopic>,
    viewMode: TopicViewMode,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

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
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
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
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = segment.userMessagePreview,
                        style = MaterialTheme.typography.bodySmall,
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

                    // 完整用户消息
                    Text(
                        text = "User:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
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

                    // 助手回复摘要
                    if (segment.assistantMessages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Assistant: ${segment.assistantMessages.size} response(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        segment.assistantMessages.take(3).forEach { msg ->
                            msg.contentBlocks
                                .filterIsInstance<com.vibe.terminal.domain.model.ContentBlock.Text>()
                                .firstOrNull { it.text.isNotBlank() }
                                ?.let { textBlock ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = textBlock.text.take(300) + if (textBlock.text.length > 300) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(8.dp),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                        }
                    }
                }
            }
        }
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
