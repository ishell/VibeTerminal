package com.vibe.terminal.ui.terminal

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ScreenRotation
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.terminal.data.preferences.UserPreferences
import com.vibe.terminal.data.ssh.SshConnectionState
import com.vibe.terminal.data.ssh.SshErrorInfo
import com.vibe.terminal.terminal.renderer.TerminalColorScheme
import com.vibe.terminal.terminal.renderer.TerminalMinimap
import com.vibe.terminal.terminal.renderer.TerminalRenderer
import com.vibe.terminal.terminal.renderer.getTerminalFontFamily
import com.vibe.terminal.ui.terminal.keyboard.PathStyleKeyboard
import com.vibe.terminal.ui.terminal.keyboard.TermiusStyleKeyboard
import com.vibe.terminal.ui.theme.StatusConnected
import com.vibe.terminal.ui.theme.StatusConnecting
import com.vibe.terminal.ui.theme.StatusDisconnected
import com.vibe.terminal.ui.theme.StatusError
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val screenTimeoutMinutes by viewModel.screenTimeoutMinutes.collectAsState()
    val terminalFont by viewModel.terminalFont.collectAsState()
    val terminalFontSize by viewModel.terminalFontSize.collectAsState()
    val keyboardStyle by viewModel.keyboardStyle.collectAsState()

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var inputText by remember { mutableStateOf("") }
    var isLandscapeMode by rememberSaveable { mutableStateOf(false) }

    // Get activity for window flags
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Keep screen on based on user preference
    DisposableEffect(screenTimeoutMinutes, connectionState) {
        val window = activity?.window

        when {
            // System default - don't modify window flags
            screenTimeoutMinutes == 0 -> {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // Always on (-1) or custom timeout when connected
            connectionState is SshConnectionState.Connected -> {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // Not connected - follow system default
            else -> {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        onDispose {
            // Clear flag when leaving the screen
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Handle custom timeout (turn off keep screen on after specified minutes)
    LaunchedEffect(screenTimeoutMinutes, connectionState) {
        if (screenTimeoutMinutes > 0 && connectionState is SshConnectionState.Connected) {
            // Wait for the specified timeout
            delay(screenTimeoutMinutes * 60 * 1000L)
            // After timeout, clear the flag
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Note: Don't disconnect here on dispose!
    // The ViewModel's onCleared() will handle disconnection when actually leaving the screen.
    // DisposableEffect(Unit) would disconnect on rotation which is wrong since SshClient is singleton.

    // Manage screen orientation for landscape mode
    DisposableEffect(isLandscapeMode) {
        if (isLandscapeMode) {
            // Switch to landscape
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            // Restore to portrait/unspecified
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        onDispose {
            // Restore orientation when leaving the screen
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Hide keyboard when entering landscape mode
    // Clear focus first, then hide keyboard with a delay to ensure it works
    LaunchedEffect(isLandscapeMode) {
        if (isLandscapeMode) {
            // Clear focus to prevent keyboard from showing
            focusManager.clearFocus()
            // Small delay to ensure orientation change is processed
            kotlinx.coroutines.delay(100)
            // Hide keyboard
            keyboardController?.hide()
            // Additional delay and hide again to be sure
            kotlinx.coroutines.delay(200)
            keyboardController?.hide()
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
                actions = {
                    // Rotation toggle button
                    IconButton(onClick = { isLandscapeMode = !isLandscapeMode }) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            contentDescription = if (isLandscapeMode) "Exit landscape" else "Enter landscape",
                            tint = if (isLandscapeMode) StatusConnected else Color.White
                        )
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
                    // Use Box to allow PathStyleKeyboard to float over everything
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Terminal view with minimap
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                // Terminal renderer
                                TerminalRenderer(
                                    emulator = viewModel.emulator,
                                    colorScheme = TerminalColorScheme.Dark,
                                    fontSize = terminalFontSize.toFloat(),
                                    fontFamily = getTerminalFontFamily(terminalFont),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    onTap = {
                                        focusRequester.requestFocus()
                                        // Only show keyboard in portrait mode
                                        if (!isLandscapeMode) {
                                            keyboardController?.show()
                                        }
                                    },
                                    onDoubleTap = {
                                        // Double tap to toggle fullscreen panel
                                        viewModel.togglePanelFullscreen()
                                    },
                                    onPinchIn = {
                                        // Pinch in (fingers together) -> zoom in to single panel
                                        viewModel.enterPanelFullscreen()
                                    },
                                    onPinchOut = {
                                        // Pinch out (fingers apart) -> zoom out to show all panels
                                        viewModel.exitPanelFullscreen()
                                    },
                                    onSwipeLeft = {
                                        // Swipe left -> next panel
                                        viewModel.focusNextPanel()
                                    },
                                    onSwipeRight = {
                                        // Swipe right -> previous panel
                                        viewModel.focusPreviousPanel()
                                    },
                                    // Vertical swipe is now used for scrolling within panel (viewing history)
                                    // Panel up/down navigation removed to avoid conflict with scroll gesture
                                    onTwoFingerSwipeLeft = {
                                        // Two-finger swipe left -> next tab
                                        viewModel.goToNextTab()
                                    },
                                    onTwoFingerSwipeRight = {
                                        // Two-finger swipe right -> previous tab
                                        viewModel.goToPreviousTab()
                                    },
                                    onThreeFingerTap = {
                                        // Three-finger tap -> toggle floating panes
                                        viewModel.toggleFloatingPanes()
                                    },
                                    onSendInput = viewModel::sendInput,
                                    onSizeChanged = { cols, rows ->
                                        // Resize terminal when canvas size changes
                                        viewModel.resize(cols, rows)
                                    }
                                )

                                // Minimap (VS Code style) - only show in landscape mode
                                // In portrait mode, terminal needs full width for better Zellij compatibility
                                if (isLandscapeMode) {
                                    TerminalMinimap(
                                        emulator = viewModel.emulator,
                                        colorScheme = TerminalColorScheme.Dark,
                                        width = 80
                                    )
                                }
                            }

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

                            // Termius-style keyboard bar (always visible at bottom)
                            if (keyboardStyle in listOf(
                                    UserPreferences.KEYBOARD_STYLE_TERMIUS,
                                    UserPreferences.KEYBOARD_STYLE_BOTH
                                )) {
                                TermiusStyleKeyboard(
                                    onKey = viewModel::sendKey,
                                    onCtrlKey = viewModel::sendCtrlKey,
                                    onSendInput = viewModel::sendInput
                                )
                            }
                        }

                        // Path-style floating keyboard (available in all orientations)
                        if (keyboardStyle in listOf(
                                UserPreferences.KEYBOARD_STYLE_PATH,
                                UserPreferences.KEYBOARD_STYLE_BOTH
                            )) {
                            PathStyleKeyboard(
                                onKey = viewModel::sendKey,
                                onCtrlKey = viewModel::sendCtrlKey,
                                onSendInput = viewModel::sendInput,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
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
