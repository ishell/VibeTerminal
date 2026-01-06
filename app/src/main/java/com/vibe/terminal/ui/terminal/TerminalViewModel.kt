package com.vibe.terminal.ui.terminal

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.data.ssh.SshClient
import com.vibe.terminal.data.ssh.SshConfig
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val sshClient: SshClient
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    val connectionState: StateFlow<SshConnectionState> = sshClient.connectionState

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
            }
        )

        sshClient.connect(config).onSuccess {
            startPtySession(project)
            projectRepository.updateLastConnected(project.id)
        }.onFailure { error ->
            _uiState.update { it.copy(error = error.message) }
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
    val error: String? = null
)
