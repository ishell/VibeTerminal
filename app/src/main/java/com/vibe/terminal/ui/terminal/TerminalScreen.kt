package com.vibe.terminal.ui.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.terminal.data.ssh.SshConnectionState
import com.vibe.terminal.data.ssh.SshErrorInfo
import com.vibe.terminal.terminal.renderer.TerminalColorScheme
import com.vibe.terminal.terminal.renderer.TerminalRenderer
import com.vibe.terminal.ui.theme.StatusConnected
import com.vibe.terminal.ui.theme.StatusConnecting
import com.vibe.terminal.ui.theme.StatusDisconnected
import com.vibe.terminal.ui.theme.StatusError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnect()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.project?.name ?: "Terminal",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = when (connectionState) {
                                            is SshConnectionState.Connected -> StatusConnected
                                            is SshConnectionState.Connecting -> StatusConnecting
                                            is SshConnectionState.Error -> StatusError
                                            is SshConnectionState.Disconnected -> StatusDisconnected
                                        },
                                        shape = MaterialTheme.shapes.small
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (connectionState) {
                                    is SshConnectionState.Connected -> "Connected"
                                    is SshConnectionState.Connecting -> "Connecting..."
                                    is SshConnectionState.Error -> "Error"
                                    is SshConnectionState.Disconnected -> "Disconnected"
                                },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            when (connectionState) {
                is SshConnectionState.Connecting -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Connecting to ${uiState.machine?.displayAddress()}...",
                                color = Color.White
                            )
                        }
                    }
                }

                is SshConnectionState.Error -> {
                    val errorState = connectionState as SshConnectionState.Error
                    ConnectionErrorView(
                        errorInfo = errorState.errorInfo,
                        onRetry = { viewModel.reconnect() },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }

                is SshConnectionState.Connected -> {
                    // Terminal view
                    TerminalRenderer(
                        emulator = viewModel.emulator,
                        colorScheme = TerminalColorScheme.Dark,
                        fontSize = 12f,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onTap = {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    )

                    // Hidden input field for keyboard (supports IME for Chinese, etc.)
                    BasicTextField(
                        value = inputText,
                        onValueChange = { newText ->
                            if (newText.length > inputText.length) {
                                val newChars = newText.substring(inputText.length)
                                viewModel.sendInput(newChars)
                            }
                            inputText = ""
                        },
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .size(1.dp)
                            .onKeyEvent { keyEvent ->
                                when (keyEvent.key) {
                                    Key.Backspace -> {
                                        viewModel.sendKey(TerminalViewModel.KEY_BACKSPACE)
                                        true
                                    }
                                    Key.Enter -> {
                                        viewModel.sendKey(TerminalViewModel.KEY_ENTER)
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        viewModel.sendKey(TerminalViewModel.KEY_UP)
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        viewModel.sendKey(TerminalViewModel.KEY_DOWN)
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        viewModel.sendKey(TerminalViewModel.KEY_LEFT)
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        viewModel.sendKey(TerminalViewModel.KEY_RIGHT)
                                        true
                                    }
                                    Key.Tab -> {
                                        viewModel.sendKey(TerminalViewModel.KEY_TAB)
                                        true
                                    }
                                    Key.Escape -> {
                                        viewModel.sendKey(TerminalViewModel.KEY_ESCAPE)
                                        true
                                    }
                                    else -> false
                                }
                            },
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        cursorBrush = SolidColor(Color.Transparent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )

                    // Quick keyboard bar
                    QuickKeyboardBar(
                        onKey = viewModel::sendKey,
                        onCtrlKey = viewModel::sendCtrlKey
                    )
                }

                is SshConnectionState.Disconnected -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Disconnected",
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }

    // Request focus when connected
    LaunchedEffect(connectionState) {
        if (connectionState is SshConnectionState.Connected) {
            focusRequester.requestFocus()
        }
    }
}

/**
 * 连接错误显示组件
 */
@Composable
private fun ConnectionErrorView(
    errorInfo: SshErrorInfo,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDetails by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(Color(0xFF1E1E1E))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // 错误图标
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = StatusError,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 错误标题
        Text(
            text = errorInfo.title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 错误描述
        Text(
            text = errorInfo.description,
            color = Color.Gray,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 建议卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2D2D2D)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "可能的解决方案",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                errorInfo.suggestions.forEachIndexed { index, suggestion ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "${index + 1}.",
                            color = Color(0xFF4EC9B0),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(20.dp)
                        )
                        Text(
                            text = suggestion,
                            color = Color(0xFFCCCCCC),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 详情展开按钮
        TextButton(
            onClick = { showDetails = !showDetails }
        ) {
            Text(
                text = if (showDetails) "隐藏详情" else "查看详情",
                color = Color(0xFF569CD6)
            )
            Icon(
                imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color(0xFF569CD6)
            )
        }

        // 详情内容
        AnimatedVisibility(
            visible = showDetails,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF252526)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "技术详情",
                        color = Color(0xFF808080),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorInfo.technicalDetails,
                        color = Color(0xFFCE9178),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E))
                            .padding(8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 重试按钮
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("重试连接")
        }
    }
}

@Composable
private fun QuickKeyboardBar(
    onKey: (Int) -> Unit,
    onCtrlKey: (Char) -> Unit
) {
    Surface(
        color = Color(0xFF2D2D2D),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            KeyButton("Esc") { onKey(TerminalViewModel.KEY_ESCAPE) }
            KeyButton("Tab") { onKey(TerminalViewModel.KEY_TAB) }
            KeyButton("Ctrl") { /* Toggle ctrl mode */ }

            IconButton(onClick = { onKey(TerminalViewModel.KEY_UP) }) {
                Icon(Icons.Default.KeyboardArrowUp, "Up", tint = Color.White)
            }
            IconButton(onClick = { onKey(TerminalViewModel.KEY_DOWN) }) {
                Icon(Icons.Default.KeyboardArrowDown, "Down", tint = Color.White)
            }
            IconButton(onClick = { onKey(TerminalViewModel.KEY_LEFT) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", tint = Color.White)
            }
            IconButton(onClick = { onKey(TerminalViewModel.KEY_RIGHT) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", tint = Color.White)
            }
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp)
    ) {
        Text(text, fontSize = 12.sp)
    }
}
