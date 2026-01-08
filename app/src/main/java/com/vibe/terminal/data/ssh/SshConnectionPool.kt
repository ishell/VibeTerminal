package com.vibe.terminal.data.ssh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.PublicKey
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSH连接池，复用SSH连接以提升性能
 *
 * 功能：
 * - 按 host:port:username 缓存连接
 * - 空闲超时自动断开（默认60秒）
 * - 线程安全
 * - 自动重连
 * - 安全的主机密钥验证
 */
@Singleton
class SshConnectionPool @Inject constructor(
    private val hostKeyManager: HostKeyManager
) {

    companion object {
        private const val IDLE_TIMEOUT_MS = 60_000L  // 60秒空闲后断开
        private const val COMMAND_TIMEOUT_SECONDS = 30L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // 连接缓存 (key -> PooledConnection)
    private val connections = mutableMapOf<String, PooledConnection>()

    // 空闲检测任务
    private val idleCheckJobs = mutableMapOf<String, Job>()

    /**
     * 生成连接的唯一key
     */
    private fun getConnectionKey(config: SshConfig): String {
        return "${config.host}:${config.port}:${config.username}"
    }

    /**
     * 获取或创建连接
     *
     * @param config SSH配置（必须包含 trustedFingerprint）
     * @return 连接结果
     */
    suspend fun getConnection(config: SshConfig): Result<PooledConnection> = mutex.withLock {
        val key = getConnectionKey(config)

        // 检查现有连接
        val existing = connections[key]
        if (existing != null && existing.isConnected()) {
            existing.updateLastUsed()
            return Result.success(existing)
        }

        // 清理旧连接
        existing?.disconnect()
        connections.remove(key)

        // 创建新连接
        return withContext(Dispatchers.IO) {
            try {
                // 验证必须有可信指纹
                val fingerprint = config.trustedFingerprint
                    ?: return@withContext Result.failure(
                        SecurityException("连接池连接必须提供已验证的主机密钥指纹")
                    )

                val client = SSHClient(DefaultConfig()).apply {
                    addHostKeyVerifier(
                        hostKeyManager.createKnownHostsVerifier(
                            config.host,
                            config.port,
                            fingerprint
                        )
                    )
                    connect(config.host, config.port)

                    when (val auth = config.authMethod) {
                        is SshConfig.AuthMethod.Password -> {
                            authPassword(config.username, auth.password)
                        }
                        is SshConfig.AuthMethod.PublicKey -> {
                            val keyResult = SshKeyLoader.loadKey(auth.privateKey, auth.passphrase)
                            try {
                                authPublickey(config.username, keyResult.keyProvider)
                            } finally {
                                keyResult.cleanup()
                            }
                        }
                    }
                }

                val pooledConnection = PooledConnection(key, client, config)
                connections[key] = pooledConnection

                // 启动空闲检测
                startIdleCheck(key)

                Result.success(pooledConnection)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 验证主机密钥（用于首次连接）
     *
     * @return 主机密钥状态
     */
    suspend fun verifyHostKey(
        host: String,
        port: Int
    ): SshConnectResult = withContext(Dispatchers.IO) {
        var tempClient: SSHClient? = null
        try {
            val collectingVerifier = hostKeyManager.createCollectingVerifier()

            tempClient = SSHClient(DefaultConfig()).apply {
                addHostKeyVerifier(collectingVerifier)
                connect(host, port)
            }

            val publicKey = collectingVerifier.collectedKey
                ?: return@withContext SshConnectResult.Failed(
                    SecurityException("无法获取服务器公钥")
                )

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

    /**
     * 接受并保存主机密钥
     */
    suspend fun acceptHostKey(host: String, port: Int) = withContext(Dispatchers.IO) {
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

    /**
     * 执行SSH命令（自动获取连接）
     */
    suspend fun executeCommand(
        config: SshConfig,
        command: String,
        timeoutSeconds: Long = COMMAND_TIMEOUT_SECONDS
    ): Result<String> {
        return getConnection(config).fold(
            onSuccess = { conn ->
                try {
                    Result.success(conn.executeCommand(command, timeoutSeconds))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * 启动空闲检测
     */
    private fun startIdleCheck(key: String) {
        idleCheckJobs[key]?.cancel()
        idleCheckJobs[key] = scope.launch {
            while (true) {
                delay(IDLE_TIMEOUT_MS / 2)

                mutex.withLock {
                    val conn = connections[key]
                    if (conn == null) {
                        idleCheckJobs[key]?.cancel()
                        idleCheckJobs.remove(key)
                        return@launch
                    }

                    if (conn.isIdle(IDLE_TIMEOUT_MS)) {
                        conn.disconnect()
                        connections.remove(key)
                        idleCheckJobs[key]?.cancel()
                        idleCheckJobs.remove(key)
                        return@launch
                    }
                }
            }
        }
    }

    /**
     * 关闭指定连接
     */
    suspend fun closeConnection(config: SshConfig) = mutex.withLock {
        val key = getConnectionKey(config)
        connections[key]?.disconnect()
        connections.remove(key)
        idleCheckJobs[key]?.cancel()
        idleCheckJobs.remove(key)
    }

    /**
     * 关闭所有连接
     */
    suspend fun closeAll() = mutex.withLock {
        connections.values.forEach { it.disconnect() }
        connections.clear()
        idleCheckJobs.values.forEach { it.cancel() }
        idleCheckJobs.clear()
    }

    /**
     * 获取当前活跃连接数
     */
    suspend fun getActiveConnectionCount(): Int = mutex.withLock {
        connections.count { it.value.isConnected() }
    }
}

/**
 * 池化的SSH连接
 */
class PooledConnection(
    val key: String,
    private val client: SSHClient,
    private val config: SshConfig
) {
    @Volatile
    private var lastUsedTime = System.currentTimeMillis()

    private val sessionMutex = Mutex()

    fun updateLastUsed() {
        lastUsedTime = System.currentTimeMillis()
    }

    fun isIdle(timeoutMs: Long): Boolean {
        return System.currentTimeMillis() - lastUsedTime > timeoutMs
    }

    fun isConnected(): Boolean {
        return try {
            client.isConnected && client.isAuthenticated
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 执行命令
     */
    suspend fun executeCommand(
        command: String,
        timeoutSeconds: Long = 30
    ): String = sessionMutex.withLock {
        withContext(Dispatchers.IO) {
            updateLastUsed()

            val session = client.startSession()
            try {
                val cmd = session.exec(command)
                val output = ByteArrayOutputStream()
                cmd.inputStream.copyTo(output)
                cmd.join(timeoutSeconds, TimeUnit.SECONDS)
                output.toString(Charsets.UTF_8)
            } finally {
                try {
                    session.close()
                } catch (_: Exception) { }
                updateLastUsed()
            }
        }
    }

    /**
     * 获取原始 Session（用于需要流式处理的场景）
     */
    suspend fun startSession(): Session = sessionMutex.withLock {
        updateLastUsed()
        client.startSession()
    }

    fun disconnect() {
        try {
            client.disconnect()
        } catch (_: Exception) { }
    }
}
