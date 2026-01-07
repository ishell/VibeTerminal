package com.vibe.terminal.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.model.Project
import com.vibe.terminal.domain.repository.MachineRepository
import com.vibe.terminal.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.StringReader
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val machineRepository: MachineRepository
) : ViewModel() {

    private val _projects = projectRepository.getAllProjects()
    private val _machines = machineRepository.getAllMachines()

    val uiState: StateFlow<HomeUiState> = combine(_projects, _machines) { projects, machines ->
        HomeUiState(
            projects = projects,
            machines = machines,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    private val _dialogState = MutableStateFlow(NewProjectDialogState())
    val dialogState: StateFlow<NewProjectDialogState> = _dialogState.asStateFlow()

    private val _showNewProjectDialog = MutableStateFlow(false)
    val showNewProjectDialog: StateFlow<Boolean> = _showNewProjectDialog.asStateFlow()

    fun showNewProjectDialog() {
        _dialogState.value = NewProjectDialogState()
        _showNewProjectDialog.value = true
    }

    fun hideNewProjectDialog() {
        _showNewProjectDialog.value = false
        _dialogState.value = NewProjectDialogState()
    }

    fun fetchZellijSessions(machine: Machine) {
        viewModelScope.launch {
            _dialogState.update { it.copy(isLoadingSessions = true, sessionError = null) }

            val result = withContext(Dispatchers.IO) {
                var client: SSHClient? = null
                try {
                    client = SSHClient(DefaultConfig()).apply {
                        addHostKeyVerifier(PromiscuousVerifier())
                        connect(machine.host, machine.port)

                        when (machine.authType) {
                            Machine.AuthType.PASSWORD -> {
                                authPassword(machine.username, machine.password ?: "")
                            }
                            Machine.AuthType.SSH_KEY -> {
                                val keyProvider = loadKeyProvider(
                                    machine.privateKey ?: "",
                                    machine.passphrase
                                )
                                authPublickey(machine.username, keyProvider)
                            }
                        }
                    }

                    // Execute zellij list-sessions
                    val session = client.startSession()
                    val command = session.exec("zellij list-sessions -n 2>/dev/null || zellij list-sessions 2>/dev/null || echo ''")
                    command.join(10, TimeUnit.SECONDS)

                    val output = command.inputStream.bufferedReader().readText()
                    session.close()

                    // Parse session names (one per line, filter empty lines)
                    // Strip ANSI escape codes and clean up output
                    val ansiRegex = Regex("\u001B\\[[0-9;]*[a-zA-Z]")
                    val sessions = output.lines()
                        .map { line ->
                            // Remove ANSI escape codes
                            ansiRegex.replace(line, "").trim()
                        }
                        .filter { it.isNotBlank() && !it.contains("No active sessions") }
                        .map { line ->
                            // zellij list-sessions format: "session_name [Created ... ago]" or just "session_name"
                            // Also handle "(current)" suffix
                            line.split(" ").firstOrNull()?.trim()?.removeSuffix("(current)") ?: line
                        }
                        .filter { it.isNotBlank() && !it.startsWith("[") }
                        .distinct()

                    Result.success(sessions)
                } catch (e: Exception) {
                    Result.failure(e)
                } finally {
                    try {
                        client?.disconnect()
                    } catch (_: Exception) {
                        // Ignore disconnect errors
                    }
                }
            }

            result.fold(
                onSuccess = { sessions ->
                    _dialogState.update {
                        it.copy(
                            zellijSessions = sessions,
                            isLoadingSessions = false,
                            sessionError = null
                        )
                    }
                },
                onFailure = { error ->
                    _dialogState.update {
                        it.copy(
                            isLoadingSessions = false,
                            sessionError = error.message ?: "Failed to fetch sessions"
                        )
                    }
                }
            )
        }
    }

    fun createProject(name: String, machineId: String, zellijSession: String) {
        viewModelScope.launch {
            val project = Project(
                id = UUID.randomUUID().toString(),
                name = name,
                machineId = machineId,
                zellijSession = zellijSession
            )
            projectRepository.saveProject(project)
            hideNewProjectDialog()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }

    private fun loadKeyProvider(privateKey: String, passphrase: String?): KeyProvider {
        val passwordFinder: PasswordFinder? = passphrase?.let {
            PasswordUtils.createOneOff(it.toCharArray())
        }

        return when {
            privateKey.contains("BEGIN OPENSSH PRIVATE KEY") -> {
                val tempFile = java.io.File.createTempFile("ssh_key_", ".tmp")
                try {
                    tempFile.writeText(privateKey)
                    tempFile.setReadable(false, false)
                    tempFile.setReadable(true, true)
                    OpenSSHKeyFile().apply {
                        init(tempFile, passwordFinder)
                    }
                } finally {
                    tempFile.delete()
                }
            }
            privateKey.contains("BEGIN RSA PRIVATE KEY") ||
            privateKey.contains("BEGIN DSA PRIVATE KEY") ||
            privateKey.contains("BEGIN EC PRIVATE KEY") -> {
                PKCS8KeyFile().apply {
                    init(StringReader(privateKey), passwordFinder)
                }
            }
            privateKey.contains("PuTTY-User-Key-File") -> {
                PuTTYKeyFile().apply {
                    init(StringReader(privateKey), passwordFinder)
                }
            }
            else -> {
                val tempFile = java.io.File.createTempFile("ssh_key_", ".tmp")
                try {
                    tempFile.writeText(privateKey)
                    tempFile.setReadable(false, false)
                    tempFile.setReadable(true, true)
                    OpenSSHKeyFile().apply {
                        init(tempFile, passwordFinder)
                    }
                } finally {
                    tempFile.delete()
                }
            }
        }
    }
}

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val machines: List<Machine> = emptyList(),
    val isLoading: Boolean = false
)

data class NewProjectDialogState(
    val zellijSessions: List<String> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val sessionError: String? = null
)
