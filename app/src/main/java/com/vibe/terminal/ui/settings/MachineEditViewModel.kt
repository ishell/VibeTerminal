package com.vibe.terminal.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.data.ssh.SshConfig
import com.vibe.terminal.data.ssh.SshErrorAnalyzer
import com.vibe.terminal.data.ssh.SshErrorInfo
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.repository.MachineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import javax.inject.Inject

@HiltViewModel
class MachineEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val machineRepository: MachineRepository
) : ViewModel() {

    private val machineId: String? = savedStateHandle["machineId"]
    private val isEditing = machineId != null

    private val _uiState = MutableStateFlow(MachineEditUiState())
    val uiState: StateFlow<MachineEditUiState> = _uiState.asStateFlow()

    init {
        if (isEditing && machineId != null) {
            loadMachine(machineId)
        }
    }

    private fun loadMachine(id: String) {
        viewModelScope.launch {
            machineRepository.getMachineById(id)?.let { machine ->
                _uiState.update {
                    it.copy(
                        name = machine.name,
                        host = machine.host,
                        port = machine.port.toString(),
                        username = machine.username,
                        authType = machine.authType,
                        password = machine.password ?: "",
                        privateKey = machine.privateKey ?: "",
                        passphrase = machine.passphrase ?: "",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateHost(host: String) {
        _uiState.update { it.copy(host = host) }
    }

    fun updatePort(port: String) {
        _uiState.update { it.copy(port = port) }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun updateAuthType(authType: Machine.AuthType) {
        _uiState.update { it.copy(authType = authType) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun updatePrivateKey(privateKey: String) {
        _uiState.update { it.copy(privateKey = privateKey) }
    }

    fun updatePassphrase(passphrase: String) {
        _uiState.update { it.copy(passphrase = passphrase) }
    }

    fun save(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (!state.isValid) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val machine = Machine(
                id = machineId ?: UUID.randomUUID().toString(),
                name = state.name,
                host = state.host,
                port = state.port.toIntOrNull() ?: 22,
                username = state.username,
                authType = state.authType,
                password = if (state.authType == Machine.AuthType.PASSWORD) state.password else null,
                privateKey = if (state.authType == Machine.AuthType.SSH_KEY) state.privateKey else null,
                passphrase = if (state.authType == Machine.AuthType.SSH_KEY) state.passphrase.ifBlank { null } else null
            )

            if (isEditing) {
                machineRepository.updateMachine(machine)
            } else {
                machineRepository.saveMachine(machine)
            }

            _uiState.update { it.copy(isSaving = false) }
            onSuccess()
        }
    }

    fun testConnection() {
        val state = _uiState.value

        if (!state.isValid) return

        viewModelScope.launch {
            _uiState.update { it.copy(testStatus = TestConnectionStatus.Testing) }

            val result = withContext(Dispatchers.IO) {
                var client: SSHClient? = null
                try {
                    client = SSHClient(DefaultConfig()).apply {
                        addHostKeyVerifier(PromiscuousVerifier())
                        connect(state.host, state.port.toIntOrNull() ?: 22)

                        when (state.authType) {
                            Machine.AuthType.PASSWORD -> {
                                authPassword(state.username, state.password)
                            }
                            Machine.AuthType.SSH_KEY -> {
                                val keyProvider = loadKeyProvider(state.privateKey, state.passphrase.ifBlank { null })
                                authPublickey(state.username, keyProvider)
                            }
                        }
                    }
                    Result.success(Unit)
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
                onSuccess = {
                    _uiState.update { it.copy(testStatus = TestConnectionStatus.Success) }
                },
                onFailure = { error ->
                    val errorInfo = SshErrorAnalyzer.analyze(error)
                    _uiState.update { it.copy(testStatus = TestConnectionStatus.Failed(errorInfo)) }
                }
            )
        }
    }

    fun dismissTestResult() {
        _uiState.update { it.copy(testStatus = TestConnectionStatus.Idle) }
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

data class MachineEditUiState(
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: Machine.AuthType = Machine.AuthType.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val testStatus: TestConnectionStatus = TestConnectionStatus.Idle
) {
    val isValid: Boolean
        get() = name.isNotBlank() &&
                host.isNotBlank() &&
                port.toIntOrNull() != null &&
                username.isNotBlank() &&
                when (authType) {
                    Machine.AuthType.PASSWORD -> password.isNotBlank()
                    Machine.AuthType.SSH_KEY -> privateKey.isNotBlank()
                }
}

sealed class TestConnectionStatus {
    data object Idle : TestConnectionStatus()
    data object Testing : TestConnectionStatus()
    data object Success : TestConnectionStatus()
    data class Failed(val errorInfo: SshErrorInfo) : TestConnectionStatus()
}
