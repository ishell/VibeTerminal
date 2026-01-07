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
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSH客户端封装
 *
 * 负责SSH连接管理和PTY会话创建
 */
@Singleton
class SshClient @Inject constructor() {

    private var sshClient: SSHClient? = null
    private var sessionChannel: SessionChannel? = null
    private var shell: Session.Shell? = null
    private var tempKeyFile: File? = null

    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

    /**
     * 连接到SSH服务器
     */
    suspend fun connect(config: SshConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = SshConnectionState.Connecting

            // 清理之前的临时文件
            cleanupTempKeyFile()

            val client = SSHClient(DefaultConfig()).apply {
                addHostKeyVerifier(PromiscuousVerifier())
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
            Result.success(Unit)
        } catch (e: Exception) {
            cleanupTempKeyFile()
            val errorInfo = SshErrorAnalyzer.analyze(e)
            _connectionState.value = SshConnectionState.Error(errorInfo, e)
            Result.failure(e)
        }
    }

    private fun cleanupTempKeyFile() {
        tempKeyFile?.let { file ->
            try {
                if (file.exists()) {
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

data class PtySession(
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val errorStream: InputStream,
    val onResize: (cols: Int, rows: Int) -> Unit
)
