package com.vibe.terminal.data.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.SessionChannel
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSH客户端封装
 *
 * 负责SSH连接管理和PTY会话创建
 * 实现了安全的主机密钥验证
 */
@Singleton
class SshClient @Inject constructor(
    private val hostKeyManager: HostKeyManager
) {

    private var sshClient: SSHClient? = null
    private var sessionChannel: SessionChannel? = null
    private var shell: Session.Shell? = null
    private var tempKeyFile: File? = null

    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    /**
     * 连接到SSH服务器（带主机密钥验证）
     *
     * @param config SSH配置
     * @return 连接结果，可能需要用户确认主机密钥
     */
    suspend fun connect(config: SshConfig): SshConnectResult = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = SshConnectionState.Connecting

            // 清理之前的临时文件
            cleanupTempKeyFile()

            // 如果没有提供已信任的指纹，需要先检查主机密钥
            if (config.trustedFingerprint == null) {
                val verifyResult = verifyHostKey(config)
                if (verifyResult !is SshConnectResult.Success) {
                    _connectionState.value = SshConnectionState.Disconnected
                    return@withContext verifyResult
                }
            }

            // 使用已验证的指纹进行连接
            val client = SSHClient(DefaultConfig()).apply {
                val verifier = if (config.trustedFingerprint != null) {
                    hostKeyManager.createKnownHostsVerifier(
                        config.host,
                        config.port,
                        config.trustedFingerprint
                    )
                } else {
                    // 已经验证过，创建一个接受当前连接的验证器
                    AcceptAllVerifier()
                }
                addHostKeyVerifier(verifier)
                connect(config.host, config.port)

                when (val auth = config.authMethod) {
                    is SshConfig.AuthMethod.Password -> {
                        authPassword(config.username, auth.password)
                    }
                    is SshConfig.AuthMethod.PublicKey -> {
                        val keyResult = SshKeyLoader.loadKey(auth.privateKey, auth.passphrase)
                        tempKeyFile = keyResult.tempFile
                        authPublickey(config.username, keyResult.keyProvider)
                        // 认证成功后清理临时文件
                        cleanupTempKeyFile()
                    }
                }
            }

            sshClient = client
            _connectionState.value = SshConnectionState.Connected
            SshConnectResult.Success
        } catch (e: Exception) {
            cleanupTempKeyFile()
            val errorInfo = SshErrorAnalyzer.analyze(e)
            _connectionState.value = SshConnectionState.Error(errorInfo, e)
            SshConnectResult.Failed(e)
        }
    }

    /**
     * 验证主机密钥
     */
    private suspend fun verifyHostKey(config: SshConfig): SshConnectResult {
        var tempClient: SSHClient? = null
        try {
            val collectingVerifier = hostKeyManager.createCollectingVerifier()

            tempClient = SSHClient(DefaultConfig()).apply {
                addHostKeyVerifier(collectingVerifier)
                connect(config.host, config.port)
            }

            val publicKey = collectingVerifier.collectedKey
                ?: return SshConnectResult.Failed(SecurityException("无法获取服务器公钥"))

            // 检查主机密钥状态
            val status = hostKeyManager.checkHostKey(config.host, config.port, publicKey)

            return when (status) {
                is HostKeyStatus.Verified -> SshConnectResult.Success
                is HostKeyStatus.Unknown,
                is HostKeyStatus.Changed -> SshConnectResult.HostKeyVerificationRequired(status)
            }
        } finally {
            try {
                tempClient?.disconnect()
            } catch (_: Exception) { }
        }
    }

    /**
     * 接受主机密钥并保存
     */
    suspend fun acceptHostKey(
        host: String,
        port: Int,
        fingerprint: String
    ) {
        // 创建临时连接获取公钥
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

    private fun cleanupTempKeyFile() {
        tempKeyFile?.let { file ->
            try {
                if (file.exists()) {
                    // 覆写文件内容后删除
                    file.writeBytes(ByteArray(file.length().toInt()) { 0 })
                    file.delete()
                }
            } catch (_: Exception) { }
        }
        tempKeyFile = null
    }

    /**
     * 启动PTY会话
     */
    suspend fun startPtySession(
        termType: String = "xterm-256color",
        cols: Int = 80,
        rows: Int = 24
    ): Result<PtySession> = withContext(Dispatchers.IO) {
        try {
            val client = sshClient ?: return@withContext Result.failure(
                IllegalStateException("Not connected")
            )

            val newSession = client.startSession() as SessionChannel
            newSession.allocatePTY(termType, cols, rows, 0, 0, emptyMap())
            val newShell = newSession.startShell()

            sessionChannel = newSession
            shell = newShell

            val ptySession = PtySession(
                inputStream = newShell.inputStream,
                outputStream = newShell.outputStream,
                errorStream = newShell.errorStream,
                onResize = { newCols, newRows ->
                    newSession.changeWindowDimensions(newCols, newRows, 0, 0)
                }
            )

            Result.success(ptySession)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resizePty(cols: Int, rows: Int) {
        sessionChannel?.changeWindowDimensions(cols, rows, 0, 0)
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            shell?.close()
            sessionChannel?.close()
            sshClient?.disconnect()
        } catch (_: Exception) { }
        finally {
            shell = null
            sessionChannel = null
            sshClient = null
            cleanupTempKeyFile()
            _connectionState.value = SshConnectionState.Disconnected
        }
    }

    fun isConnected(): Boolean = sshClient?.isConnected == true
}

/**
 * 临时验证器 - 仅在已验证主机密钥后使用
 */
private class AcceptAllVerifier : HostKeyVerifier {
    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean = true
    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
}

data class PtySession(
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val errorStream: InputStream,
    val onResize: (cols: Int, rows: Int) -> Unit
)
