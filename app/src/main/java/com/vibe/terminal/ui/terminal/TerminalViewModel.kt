package com.vibe.terminal.ui.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.data.preferences.UserPreferences
import com.vibe.terminal.data.ssh.HostKeyManager
import com.vibe.terminal.data.ssh.HostKeyStatus
import com.vibe.terminal.data.ssh.SshClient
import com.vibe.terminal.data.ssh.SshConfig
import com.vibe.terminal.data.ssh.SshConnectResult
import com.vibe.terminal.data.ssh.SshConnectionState
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.model.Project
import com.vibe.terminal.domain.repository.MachineRepository
import com.vibe.terminal.domain.repository.ProjectRepository
import com.vibe.terminal.terminal.emulator.TerminalEmulator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val machineRepository: MachineRepository,
    private val sshClient: SshClient,
    private val hostKeyManager: HostKeyManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    // Zellij panel mode state
    private val _isPanelFullscreen = MutableStateFlow(false)
    val isPanelFullscreen: StateFlow<Boolean> = _isPanelFullscreen.asStateFlow()

    val connectionState: StateFlow<SshConnectionState> = sshClient.connectionState

    /**
     * Screen timeout setting in minutes
     * 0 = System default
     * -1 = Always on
     * Other = Custom timeout in minutes
     */
    val screenTimeoutMinutes: StateFlow<Int> = userPreferences.screenTimeout
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences.DEFAULT_SCREEN_TIMEOUT
        )

    /**
     * Terminal font setting
     */
    val terminalFont: StateFlow<String> = userPreferences.terminalFont
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences.DEFAULT_TERMINAL_FONT
        )

    val emulator = TerminalEmulator(columns = 80, rows = 24)

    private var outputStream: OutputStream? = null
    private var readJob: Job? = null

    init {
        loadProjectAndConnect()
    }

    private fun loadProjectAndConnect() {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            if (project == null) {
                _uiState.update { it.copy(error = "Project not found") }
                return@launch
            }

            val machine = machineRepository.getMachineById(project.machineId)
            if (machine == null) {
                _uiState.update { it.copy(error = "Machine not found") }
                return@launch
            }

            _uiState.update {
                it.copy(
                    project = project,
                    machine = machine
                )
            }

            connect(machine, project)
        }
    }

    private suspend fun connect(machine: Machine, project: Project) {
        val fingerprint = hostKeyManager.getStoredFingerprint(machine.host, machine.port)
        val config = SshConfig(
            host = machine.host,
            port = machine.port,
            username = machine.username,
            authMethod = when (machine.authType) {
                Machine.AuthType.PASSWORD -> SshConfig.AuthMethod.Password(machine.password ?: "")
                Machine.AuthType.SSH_KEY -> SshConfig.AuthMethod.PublicKey(
                    machine.privateKey ?: "",
                    machine.passphrase
                )
            },
            trustedFingerprint = fingerprint
        )

        when (val result = sshClient.connect(config)) {
            is SshConnectResult.Success -> {
                startPtySession(project)
                projectRepository.updateLastConnected(project.id)
            }
            is SshConnectResult.HostKeyVerificationRequired -> {
                _uiState.update {
                    it.copy(hostKeyStatus = result.status, pendingConfig = config)
                }
            }
            is SshConnectResult.Failed -> {
                _uiState.update { it.copy(error = result.error.message) }
            }
        }
    }

    /**
     * Accept host key and continue connection
     */
    fun acceptHostKeyAndConnect() {
        val state = _uiState.value
        val hostKeyStatus = state.hostKeyStatus ?: return
        val config = state.pendingConfig ?: return

        viewModelScope.launch {
            // Save the host key
            sshClient.acceptHostKey(config.host, config.port, "")

            // Clear host key dialog
            _uiState.update { it.copy(hostKeyStatus = null, pendingConfig = null) }

            // Retry connection (host key is now saved)
            val project = state.project ?: return@launch
            connect(state.machine!!, project)
        }
    }

    /**
     * Reject host key and cancel connection
     */
    fun rejectHostKey() {
        _uiState.update {
            it.copy(
                hostKeyStatus = null,
                pendingConfig = null,
                error = "Connection cancelled: host key rejected"
            )
        }
    }

    private suspend fun startPtySession(project: Project) {
        sshClient.startPtySession(
            termType = "xterm-256color",
            cols = emulator.columns,
            rows = emulator.rows
        ).onSuccess { ptySession ->
            outputStream = ptySession.outputStream

            // 启动输出读取
            readJob = viewModelScope.launch(Dispatchers.IO) {
                readOutput(ptySession.inputStream)
            }

            // 发送 zellij attach 命令
            sendCommand(project.getZellijAttachCommand())

        }.onFailure { error ->
            _uiState.update { it.copy(error = error.message) }
        }
    }

    private suspend fun readOutput(inputStream: InputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                val data = buffer.copyOf(bytesRead)
                withContext(Dispatchers.Main) {
                    emulator.processInput(data)
                }
            }
        } catch (e: Exception) {
            // Connection closed
        }
    }

    fun sendInput(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(text.toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun sendKey(keyCode: Int) {
        val sequence = when (keyCode) {
            KEY_ENTER -> "\r"
            KEY_BACKSPACE -> "\u007f"
            KEY_TAB -> "\t"
            KEY_ESCAPE -> "\u001b"
            KEY_UP -> "\u001b[A"
            KEY_DOWN -> "\u001b[B"
            KEY_RIGHT -> "\u001b[C"
            KEY_LEFT -> "\u001b[D"
            KEY_HOME -> "\u001b[H"
            KEY_END -> "\u001b[F"
            KEY_PAGE_UP -> "\u001b[5~"
            KEY_PAGE_DOWN -> "\u001b[6~"
            KEY_DELETE -> "\u001b[3~"
            else -> return
        }
        sendInput(sequence)
    }

    fun sendCtrlKey(char: Char) {
        val code = char.uppercaseChar().code - 64
        if (code in 1..26) {
            sendInput(code.toChar().toString())
        }
    }

    private fun sendCommand(command: String) {
        sendInput("$command\r")
    }

    fun resize(cols: Int, rows: Int) {
        emulator.resize(cols, rows)
        sshClient.resizePty(cols, rows)
    }

    fun disconnect() {
        viewModelScope.launch {
            readJob?.cancel()
            outputStream = null
            sshClient.disconnect()
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            // 先断开现有连接
            readJob?.cancel()
            outputStream = null
            sshClient.disconnect()

            // 重新连接
            val project = _uiState.value.project
            val machine = _uiState.value.machine
            if (project != null && machine != null) {
                connect(machine, project)
            }
        }
    }

    // ========== Zellij Panel Controls ==========

    /**
     * Toggle fullscreen mode for current panel
     * Used for pinch-to-zoom and double-tap gestures
     * Sends Ctrl+P, f (zellij pane mode -> fullscreen toggle)
     */
    fun togglePanelFullscreen() {
        sendZellijKeybinding("f")
        _isPanelFullscreen.value = !_isPanelFullscreen.value
    }

    /**
     * Enter fullscreen mode (zoom in to single panel)
     */
    fun enterPanelFullscreen() {
        if (!_isPanelFullscreen.value) {
            sendZellijKeybinding("f")
            _isPanelFullscreen.value = true
        }
    }

    /**
     * Exit fullscreen mode (zoom out to show all panels)
     */
    fun exitPanelFullscreen() {
        if (_isPanelFullscreen.value) {
            sendZellijKeybinding("f")
            _isPanelFullscreen.value = false
        }
    }

    /**
     * Focus next panel (for swipe left gesture)
     * Sends Ctrl+P, l (zellij pane mode -> focus right/next pane)
     */
    fun focusNextPanel() {
        sendZellijKeybinding("l")
    }

    /**
     * Focus previous panel (for swipe right gesture)
     * Sends Ctrl+P, h (zellij pane mode -> focus left/previous pane)
     */
    fun focusPreviousPanel() {
        sendZellijKeybinding("h")
    }

    /**
     * Focus panel above (for swipe up gesture)
     * Sends Ctrl+P, k (zellij pane mode -> focus up)
     */
    fun focusPanelUp() {
        sendZellijKeybinding("k")
    }

    /**
     * Focus panel below (for swipe down gesture)
     * Sends Ctrl+P, j (zellij pane mode -> focus down)
     */
    fun focusPanelDown() {
        sendZellijKeybinding("j")
    }

    /**
     * Go to next tab (for two-finger swipe left gesture)
     * Sends Alt+l (zellij default keybinding for next tab)
     */
    fun goToNextTab() {
        sendAltKey('l')
    }

    /**
     * Go to previous tab (for two-finger swipe right gesture)
     * Sends Alt+h (zellij default keybinding for previous tab)
     */
    fun goToPreviousTab() {
        sendAltKey('h')
    }

    /**
     * Toggle floating panes (for three-finger tap)
     * Sends Ctrl+P, w (zellij pane mode -> toggle floating)
     */
    fun toggleFloatingPanes() {
        sendZellijKeybinding("w")
    }

    /**
     * Send Alt+key combination for zellij shortcuts
     */
    private fun sendAltKey(key: Char) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Alt+key is sent as ESC followed by the key
                outputStream?.write("\u001b${key}".toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    /**
     * Send zellij keybinding sequence
     * First sends Ctrl+P to enter pane mode, then the action key, then Escape to exit pane mode
     */
    private fun sendZellijKeybinding(actionKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ctrl+P = \x10 (ASCII 16) to enter pane mode
                // Then send the action key
                // Then send Escape to exit pane mode
                outputStream?.write("\u0010".toByteArray())  // Ctrl+P
                outputStream?.flush()
                // Small delay to ensure pane mode is activated
                kotlinx.coroutines.delay(50)
                outputStream?.write(actionKey.toByteArray())  // Action key (h, l, j, k, f, w, etc.)
                outputStream?.flush()
                // Small delay then exit pane mode
                kotlinx.coroutines.delay(50)
                outputStream?.write("\u001b".toByteArray())  // Escape to exit pane mode
                outputStream?.flush()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    companion object {
        const val KEY_ENTER = 1
        const val KEY_BACKSPACE = 2
        const val KEY_TAB = 3
        const val KEY_ESCAPE = 4
        const val KEY_UP = 5
        const val KEY_DOWN = 6
        const val KEY_RIGHT = 7
        const val KEY_LEFT = 8
        const val KEY_HOME = 9
        const val KEY_END = 10
        const val KEY_PAGE_UP = 11
        const val KEY_PAGE_DOWN = 12
        const val KEY_DELETE = 13
    }
}

data class TerminalUiState(
    val project: Project? = null,
    val machine: Machine? = null,
    val error: String? = null,
    val hostKeyStatus: HostKeyStatus? = null,
    val pendingConfig: SshConfig? = null
)
