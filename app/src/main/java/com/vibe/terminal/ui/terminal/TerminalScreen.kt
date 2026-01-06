package com.vibe.terminal.ui.terminal

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.terminal.data.ssh.SshConnectionState
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
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = StatusError,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Connection failed",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = (connectionState as SshConnectionState.Error).message,
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
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

                    // Hidden input field for keyboard
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
                            .size(1.dp),
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        cursorBrush = SolidColor(Color.Transparent),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
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
