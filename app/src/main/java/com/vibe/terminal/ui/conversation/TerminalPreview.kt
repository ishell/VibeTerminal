package com.vibe.terminal.ui.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibe.terminal.domain.model.AssistantType
import com.vibe.terminal.domain.model.ConversationSegment
import com.vibe.terminal.domain.model.ConversationSession

// 终端颜色主题
private val TerminalBackground = Color(0xFF1E1E1E)
private val TerminalGreen = Color(0xFF4EC9B0)
private val TerminalBlue = Color(0xFF569CD6)
private val TerminalYellow = Color(0xFFDCDCAA)
private val TerminalPurple = Color(0xFFC586C0)
private val TerminalGray = Color(0xFF6A9955)
private val TerminalWhite = Color(0xFFD4D4D4)
private val TerminalPrompt = Color(0xFF9CDCFE)

/**
 * 终端预览组件 - 以终端风格展示 Claude Code 历史记录
 * 支持折叠/展开功能
 */
@Composable
fun TerminalPreview(
    sessions: List<ConversationSession>,
    modifier: Modifier = Modifier,
    maxLines: Int = 12,
    assistantType: AssistantType = AssistantType.CLAUDE_CODE,
    isCollapsed: Boolean = false,
    onToggleCollapse: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = TerminalBackground
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 终端标题栏 - 可点击折叠
            TerminalTitleBar(
                assistantType = assistantType,
                isCollapsed = isCollapsed,
                onToggleCollapse = onToggleCollapse,
                sessionCount = sessions.flatMap { it.segments }.size
            )

            // 可折叠的内容区域
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    // 终端内容
                    if (sessions.isEmpty()) {
                        TerminalEmptyState()
                    } else {
                        TerminalContent(sessions, maxLines)
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalTitleBar(
    assistantType: AssistantType,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    sessionCount: Int
) {
    // 根据助手类型生成标题
    val titleText = when (assistantType) {
        AssistantType.CLAUDE_CODE -> "claude-code — history"
        AssistantType.OPENCODE -> "opencode — history"
        AssistantType.CODEX -> "codex — history"
        AssistantType.BOTH -> "ai assistant — history"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // 不显示点击波纹效果
                onClick = onToggleCollapse
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bash 提示符
        Text(
            text = "$",
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TerminalGreen
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = titleText,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalGray,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )

        // 折叠状态下显示会话数量
        if (isCollapsed && sessionCount > 0) {
            Text(
                text = "[$sessionCount]",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = TerminalGreen,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 折叠/展开提示文本
        Text(
            text = if (isCollapsed) "▶" else "▼",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = TerminalYellow.copy(alpha = 0.8f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TerminalEmptyState() {
    Column {
        TerminalLine(
            prompt = "~",
            command = "claude",
            output = null
        )
        Text(
            text = "No conversation history yet.",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TerminalGray,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun TerminalContent(
    sessions: List<ConversationSession>,
    maxLines: Int
) {
    val recentSegments = sessions
        .flatMap { it.segments }
        .sortedByDescending { it.timestamp }
        .take(maxLines)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        recentSegments.forEachIndexed { index, segment ->
            TerminalSegmentLine(
                segment = segment,
                index = recentSegments.size - index
            )
        }

        // 最后一行：等待输入的提示符
        Row {
            Text(
                text = "❯ ",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalGreen,
                fontWeight = FontWeight.Bold
            )
            // 闪烁光标效果（简化版本）
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .height(14.dp)
                    .background(TerminalWhite.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
private fun TerminalSegmentLine(
    segment: ConversationSegment,
    index: Int
) {
    Column {
        // 用户请求行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 提示符
            Text(
                text = "❯ ",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalGreen,
                fontWeight = FontWeight.Bold
            )

            // 用户请求（截取前60字符）
            val userRequest = segment.userMessagePreview
                .replace("\n", " ")
                .take(60)
                .let { if (segment.userMessagePreview.length > 60) "$it..." else it }

            Text(
                text = userRequest,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalWhite,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }

        // 响应摘要行
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 响应图标
            if (segment.hasThinking) {
                TerminalBadge(
                    icon = Icons.Default.Psychology,
                    text = "thinking",
                    color = TerminalPurple
                )
            }

            if (segment.hasToolUse) {
                val toolCount = segment.assistantMessages
                    .flatMap { it.contentBlocks }
                    .count { it is com.vibe.terminal.domain.model.ContentBlock.ToolUse }
                TerminalBadge(
                    icon = Icons.Default.Build,
                    text = "${toolCount}x tools",
                    color = TerminalBlue
                )
            }

            if (segment.hasCodeChange) {
                TerminalBadge(
                    icon = Icons.Default.Code,
                    text = "code",
                    color = TerminalGreen
                )
            }

            // 如果没有特殊标签，显示一个简单的响应指示
            if (!segment.hasThinking && !segment.hasToolUse && !segment.hasCodeChange) {
                Text(
                    text = "→ response",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TerminalGray
                )
            }
        }
    }
}

@Composable
private fun TerminalBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = color
        )
    }
}

@Composable
private fun TerminalLine(
    prompt: String,
    command: String,
    output: String?
) {
    Column {
        Row {
            Text(
                text = "$prompt ",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalPrompt
            )
            Text(
                text = "❯ ",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = command,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalYellow
            )
        }
        if (output != null) {
            Text(
                text = output,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = TerminalWhite,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

/**
 * 紧凑版终端预览 - 用于卡片中
 */
@Composable
fun TerminalPreviewCompact(
    sessions: List<ConversationSession>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp)),
        color = TerminalBackground
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // 简化的标题栏
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFF5F56))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFFBD2E))
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF27C93F))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 显示最近 4 条记录的摘要
            val recentSegments = sessions
                .flatMap { it.segments }
                .sortedByDescending { it.timestamp }
                .take(4)

            if (recentSegments.isEmpty()) {
                Text(
                    text = "❯ No history yet",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalGray
                )
            } else {
                recentSegments.forEach { segment ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "❯ ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalGreen
                        )
                        Text(
                            text = segment.userMessagePreview
                                .replace("\n", " ")
                                .take(35),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = TerminalWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // 小图标
                        if (segment.hasCodeChange) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = TerminalGreen,
                                modifier = Modifier.size(10.dp)
                            )
                        } else if (segment.hasToolUse) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = TerminalBlue,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 光标行
            Row {
                Text(
                    text = "❯ ",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TerminalGreen
                )
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(12.dp)
                        .background(TerminalWhite.copy(alpha = 0.6f))
                )
            }
        }
    }
}
