package com.vibe.terminal.data.ssh

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SSH 主机密钥管理器
 *
 * 安全地存储和验证 SSH 服务器的主机密钥，防止中间人攻击
 */
@Singleton
class HostKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val knownHostsFile: File by lazy {
        File(context.filesDir, "known_hosts").also { file ->
            if (!file.exists()) {
                file.createNewFile()
                // 设置只有应用可读写
                file.setReadable(false, false)
                file.setReadable(true, true)
                file.setWritable(false, false)
                file.setWritable(true, true)
            }
        }
    }

    // 内存缓存
    private val knownHosts = mutableMapOf<String, HostKeyEntry>()
    private var loaded = false

    /**
     * 加载已知主机
     */
    suspend fun loadKnownHosts() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext

        synchronized(knownHosts) {
            if (loaded) return@withContext

            knownHosts.clear()
            if (knownHostsFile.exists()) {
                knownHostsFile.readLines().forEach { line ->
                    parseHostEntry(line)?.let { entry ->
                        knownHosts[entry.hostKey] = entry
                    }
                }
            }
            loaded = true
        }
    }

    /**
     * 解析主机条目
     * 格式: host:port algorithm fingerprint
     */
    private fun parseHostEntry(line: String): HostKeyEntry? {
        val parts = line.trim().split(" ")
        if (parts.size != 3) return null

        return HostKeyEntry(
            hostKey = parts[0],
            algorithm = parts[1],
            fingerprint = parts[2]
        )
    }

    /**
     * 获取主机的唯一标识
     */
    private fun getHostKey(host: String, port: Int): String {
        return if (port == 22) host else "[$host]:$port"
    }

    /**
     * 检查主机密钥状态
     */
    suspend fun checkHostKey(
        host: String,
        port: Int,
        publicKey: PublicKey
    ): HostKeyStatus = withContext(Dispatchers.IO) {
        loadKnownHosts()

        val hostKey = getHostKey(host, port)
        val fingerprint = calculateFingerprint(publicKey)
        val algorithm = KeyType.fromKey(publicKey)?.toString() ?: "UNKNOWN"

        synchronized(knownHosts) {
            val existing = knownHosts[hostKey]

            when {
                existing == null -> {
                    HostKeyStatus.Unknown(
                        host = host,
                        port = port,
                        fingerprint = fingerprint,
                        algorithm = algorithm
                    )
                }
                existing.fingerprint == fingerprint -> {
                    HostKeyStatus.Verified
                }
                else -> {
                    HostKeyStatus.Changed(
                        host = host,
                        port = port,
                        oldFingerprint = existing.fingerprint,
                        newFingerprint = fingerprint,
                        algorithm = algorithm
                    )
                }
            }
        }
    }

    /**
     * 保存主机密钥
     */
    suspend fun saveHostKey(
        host: String,
        port: Int,
        publicKey: PublicKey
    ) = withContext(Dispatchers.IO) {
        loadKnownHosts()

        val hostKey = getHostKey(host, port)
        val fingerprint = calculateFingerprint(publicKey)
        val algorithm = KeyType.fromKey(publicKey)?.toString() ?: "UNKNOWN"

        val entry = HostKeyEntry(hostKey, algorithm, fingerprint)

        synchronized(knownHosts) {
            knownHosts[hostKey] = entry
            saveToFile()
        }
    }

    /**
     * 获取已存储的主机密钥指纹
     *
     * @return 指纹字符串，如果不存在则返回 null
     */
    suspend fun getStoredFingerprint(host: String, port: Int): String? = withContext(Dispatchers.IO) {
        loadKnownHosts()

        val hostKey = getHostKey(host, port)
        synchronized(knownHosts) {
            knownHosts[hostKey]?.fingerprint
        }
    }

    /**
     * 删除主机密钥
     */
    suspend fun removeHostKey(host: String, port: Int) = withContext(Dispatchers.IO) {
        loadKnownHosts()

        val hostKey = getHostKey(host, port)

        synchronized(knownHosts) {
            knownHosts.remove(hostKey)
            saveToFile()
        }
    }

    /**
     * 保存到文件
     */
    private fun saveToFile() {
        val content = knownHosts.values.joinToString("\n") { entry ->
            "${entry.hostKey} ${entry.algorithm} ${entry.fingerprint}"
        }
        knownHostsFile.writeText(content)
    }

    /**
     * 计算公钥指纹 (SHA-256)
     */
    private fun calculateFingerprint(publicKey: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKey.encoded)
        return "SHA256:" + Base64.encodeToString(hash, Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * 创建一个收集密钥的验证器（用于首次连接）
     */
    fun createCollectingVerifier(): CollectingHostKeyVerifier {
        return CollectingHostKeyVerifier()
    }

    /**
     * 创建验证已知主机的验证器
     */
    fun createKnownHostsVerifier(
        host: String,
        port: Int,
        expectedFingerprint: String
    ): HostKeyVerifier {
        return object : HostKeyVerifier {
            override fun verify(hostname: String?, hostPort: Int, key: PublicKey?): Boolean {
                if (key == null) return false
                val fingerprint = calculateFingerprint(key)
                return fingerprint == expectedFingerprint
            }

            override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> {
                return emptyList()
            }
        }
    }
}

/**
 * 主机密钥条目
 */
data class HostKeyEntry(
    val hostKey: String,      // host 或 [host]:port
    val algorithm: String,    // 算法类型
    val fingerprint: String   // SHA256 指纹
)

/**
 * 主机密钥状态
 */
sealed class HostKeyStatus {
    /** 已验证，密钥匹配 */
    data object Verified : HostKeyStatus()

    /** 未知主机，需要用户确认 */
    data class Unknown(
        val host: String,
        val port: Int,
        val fingerprint: String,
        val algorithm: String
    ) : HostKeyStatus()

    /** 密钥已更改，可能是中间人攻击 */
    data class Changed(
        val host: String,
        val port: Int,
        val oldFingerprint: String,
        val newFingerprint: String,
        val algorithm: String
    ) : HostKeyStatus()
}

/**
 * 收集主机密钥的验证器
 *
 * 用于首次连接时获取服务器的公钥
 */
class CollectingHostKeyVerifier : HostKeyVerifier {
    var collectedKey: PublicKey? = null
        private set

    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
        collectedKey = key
        // 总是返回 true，让连接继续，但我们会在之后断开并验证
        return true
    }

    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> {
        return emptyList()
    }
}
