package com.vibe.terminal.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.data.ssh.HostKeyManager
import com.vibe.terminal.data.ssh.HostKeyStatus
import com.vibe.terminal.data.ssh.SshConnectResult
import com.vibe.terminal.data.ssh.SshErrorAnalyzer
import com.vibe.terminal.data.ssh.SshErrorInfo
import com.vibe.terminal.data.ssh.SshKeyLoader
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
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MachineEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val machineRepository: MachineRepository,
    private val hostKeyManager: HostKeyManager
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

            val port = state.port.toIntOrNull() ?: 22

            // 第一步：验证主机密钥
            val verifyResult = withContext(Dispatchers.IO) {
                verifyHostKeyInternal(state.host, port)
            }

            when (verifyResult) {
                is SshConnectResult.HostKeyVerificationRequired -> {
                    // 需要用户确认主机密钥
                    _uiState.update {
                        it.copy(testStatus = TestConnectionStatus.HostKeyVerification(verifyResult.status))
                    }
                    return@launch
                }
                is SshConnectResult.Failed -> {
                    val errorInfo = SshErrorAnalyzer.analyze(verifyResult.error)
                    _uiState.update {
                        it.copy(testStatus = TestConnectionStatus.Failed(errorInfo))
                    }
                    return@launch
                }
                is SshConnectResult.Success -> {
                    // 继续认证测试
                }
            }

            // 第二步：测试认证
            val result = withContext(Dispatchers.IO) {
                testAuthenticationInternal(state)
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

    /**
     * 用户接受主机密钥后继续测试
     */
    fun acceptHostKeyAndContinue() {
        val state = _uiState.value
        val hostKeyStatus = (state.testStatus as? TestConnectionStatus.HostKeyVerification)?.status
            ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(testStatus = TestConnectionStatus.Testing) }

            val port = state.port.toIntOrNull() ?: 22

            // 保存主机密钥
            withContext(Dispatchers.IO) {
                saveHostKeyInternal(state.host, port)
            }

            // 继续测试认证
            val result = withContext(Dispatchers.IO) {
                testAuthenticationInternal(state)
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

    private suspend fun verifyHostKeyInternal(host: String, port: Int): SshConnectResult {
        var tempClient: SSHClient? = null
        return try {
            val collectingVerifier = hostKeyManager.createCollectingVerifier()

            tempClient = SSHClient(DefaultConfig()).apply {
                addHostKeyVerifier(collectingVerifier)
                connect(host, port)
            }

            val publicKey = collectingVerifier.collectedKey
                ?: return SshConnectResult.Failed(SecurityException("无法获取服务器公钥"))

            val status = hostKeyManager.checkHostKey(host, port, publicKey)

            when (status) {
                is HostKeyStatus.Verified -> SshConnectResult.Success
                is HostKeyStatus.Unknown,
                is HostKeyStatus.Changed -> SshConnectResult.HostKeyVerificationRequired(status)
            }
        } catch (e: Exception) {
            SshConnectResult.Failed(e)
        } finally {
            try {
                tempClient?.disconnect()
            } catch (_: Exception) { }
        }
    }

    private suspend fun saveHostKeyInternal(host: String, port: Int) {
        var tempClient: SSHClient? = null
        try {
            val collectingVerifier = hostKeyManager.createCollectingVerifier()

            tempClient = SSHClient(DefaultConfig()).apply {
                addHostKeyVerifier(collectingVerifier)
                connect(host, port)
            }

            val publicKey = collectingVerifier.collectedKey
            if (publicKey != null) {
                hostKeyManager.saveHostKey(host, port, publicKey)
            }
        } finally {
            try {
                tempClient?.disconnect()
            } catch (_: Exception) { }
        }
    }

    private suspend fun testAuthenticationInternal(state: MachineEditUiState): Result<Unit> {
        var client: SSHClient? = null
        var keyResult: com.vibe.terminal.data.ssh.SshKeyLoadResult? = null
        return try {
            val collectingVerifier = hostKeyManager.createCollectingVerifier()

            client = SSHClient(DefaultConfig()).apply {
                // 使用收集验证器（主机已验证）
                addHostKeyVerifier(collectingVerifier)
                connect(state.host, state.port.toIntOrNull() ?: 22)

                when (state.authType) {
                    Machine.AuthType.PASSWORD -> {
                        authPassword(state.username, state.password)
                    }
                    Machine.AuthType.SSH_KEY -> {
                        keyResult = SshKeyLoader.loadKey(
                            state.privateKey,
                            state.passphrase.ifBlank { null }
                        )
                        authPublickey(state.username, keyResult!!.keyProvider)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            keyResult?.cleanup()
            try { client?.disconnect() } catch (_: Exception) { }
        }
    }

    fun dismissTestResult() {
        _uiState.update { it.copy(testStatus = TestConnectionStatus.Idle) }
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
    data class HostKeyVerification(val status: HostKeyStatus) : TestConnectionStatus()
}
