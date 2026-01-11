package com.vibe.terminal.data.ssh

import android.util.Log
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.CharArrayReader
import java.io.File
import java.io.StringReader
import java.security.SecureRandom

private const val TAG = "SshKeyLoader"

/**
 * SSH密钥加载结果
 */
data class SshKeyLoadResult(
    val keyProvider: KeyProvider,
    val tempFile: File? = null
) {
    /**
     * 安全清理临时文件
     * 使用多次覆盖以防止数据恢复
     */
    fun cleanup() {
        tempFile?.let { file ->
            try {
                if (file.exists()) {
                    secureDeleteFile(file)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to securely delete temp file: ${e.message}")
                // 备用删除
                try {
                    file.delete()
                } catch (_: Exception) { }
            }
        }
    }
}

/**
 * 安全文件删除工具
 */
private fun secureDeleteFile(file: File) {
    if (!file.exists()) return

    val length = file.length()
    if (length > 0) {
        val random = SecureRandom()

        // 三次覆盖：零 -> 一 -> 随机
        file.outputStream().use { out ->
            // Pass 1: 全零
            val zeros = ByteArray(length.toInt()) { 0 }
            out.write(zeros)
            out.flush()
        }

        file.outputStream().use { out ->
            // Pass 2: 全一
            val ones = ByteArray(length.toInt()) { 0xFF.toByte() }
            out.write(ones)
            out.flush()
        }

        file.outputStream().use { out ->
            // Pass 3: 随机数据
            val randomBytes = ByteArray(length.toInt())
            random.nextBytes(randomBytes)
            out.write(randomBytes)
            out.flush()
        }
    }

    // 删除文件
    file.delete()
}

/**
 * SSH密钥加载器
 *
 * 安全改进：
 * - 使用 CharArray 处理密码短语（可清零）
 * - 安全删除临时文件（多次覆盖）
 * - 最小化敏感数据在内存中的存留时间
 */
object SshKeyLoader {

    /**
     * 加载SSH密钥
     *
     * @param privateKey 私钥内容
     * @param passphrase 密码短语（可选）
     * @return 密钥加载结果，包含 KeyProvider 和可能的临时文件
     */
    fun loadKey(privateKey: String, passphrase: String?): SshKeyLoadResult {
        // 转换为 CharArray 以便后续清零
        val privateKeyChars = privateKey.toCharArray()
        val passphraseChars = passphrase?.toCharArray()

        return try {
            loadKeySecure(privateKeyChars, passphraseChars)
        } finally {
            // 清零敏感数据
            secureWipe(passphraseChars)
            // 注意：privateKeyChars 不清零，因为 sshj 可能还在使用
            // 调用方应在连接完成后调用 cleanup()
        }
    }

    /**
     * 使用 CharArray 加载密钥（更安全）
     */
    fun loadKeySecure(privateKey: CharArray, passphrase: CharArray?): SshKeyLoadResult {
        Log.d(TAG, "Loading SSH key, length=${privateKey.size}, hasPassphrase=${passphrase != null}")

        val passwordFinder: PasswordFinder? = passphrase?.let {
            PasswordUtils.createOneOff(it.copyOf())  // 复制一份，原始数据由调用方清零
        }

        val keyType = detectKeyType(privateKey)
        Log.d(TAG, "Detected key type: $keyType")

        return when (keyType) {
            KeyType.OPENSSH_V1 -> loadOpenSshV1Key(privateKey, passwordFinder)
            KeyType.PEM_RSA, KeyType.PEM_DSA, KeyType.PEM_EC -> loadPemKey(privateKey, passwordFinder)
            KeyType.PUTTY -> loadPuttyKey(privateKey, passwordFinder)
            KeyType.UNKNOWN -> loadUnknownKey(privateKey, passwordFinder)
        }
    }

    private enum class KeyType {
        OPENSSH_V1, PEM_RSA, PEM_DSA, PEM_EC, PUTTY, UNKNOWN
    }

    private fun detectKeyType(privateKey: CharArray): KeyType {
        val content = String(privateKey)
        return when {
            content.contains("BEGIN OPENSSH PRIVATE KEY") -> KeyType.OPENSSH_V1
            content.contains("BEGIN RSA PRIVATE KEY") -> KeyType.PEM_RSA
            content.contains("BEGIN DSA PRIVATE KEY") -> KeyType.PEM_DSA
            content.contains("BEGIN EC PRIVATE KEY") -> KeyType.PEM_EC
            content.contains("PuTTY-User-Key-File") -> KeyType.PUTTY
            else -> KeyType.UNKNOWN
        }
    }

    private fun loadOpenSshV1Key(privateKey: CharArray, passwordFinder: PasswordFinder?): SshKeyLoadResult {
        // OpenSSH v1 格式需要临时文件
        val keyFile = createSecureTempFile()

        try {
            // 写入密钥内容
            keyFile.writer(Charsets.UTF_8).use { writer ->
                writer.write(privateKey)
            }

            // 设置文件权限：仅所有者可读
            keyFile.setReadable(false, false)
            keyFile.setReadable(true, true)
            keyFile.setWritable(false, false)
            keyFile.setWritable(true, true)

            val keyProvider = OpenSSHKeyV1KeyFile().apply {
                init(keyFile, passwordFinder)
            }

            // 验证密钥可解析
            try {
                val privateKeyObj = keyProvider.private
                val publicKeyObj = keyProvider.public
                Log.d(TAG, "Key parsed successfully: private=${privateKeyObj?.algorithm}, public=${publicKeyObj?.algorithm}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse OpenSSH key: ${e.javaClass.simpleName}")
                secureDeleteFile(keyFile)
                throw RuntimeException("Failed to parse OpenSSH private key: ${e.message}", e)
            }

            return SshKeyLoadResult(keyProvider = keyProvider, tempFile = keyFile)
        } catch (e: Exception) {
            secureDeleteFile(keyFile)
            throw e
        }
    }

    private fun loadPemKey(privateKey: CharArray, passwordFinder: PasswordFinder?): SshKeyLoadResult {
        val keyProvider = PKCS8KeyFile().apply {
            init(CharArrayReader(privateKey), passwordFinder)
        }

        try {
            val privateKeyObj = keyProvider.private
            Log.d(TAG, "PEM key parsed successfully: ${privateKeyObj?.algorithm}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse PEM key: ${e.javaClass.simpleName}")
            throw RuntimeException("Failed to parse PEM private key: ${e.message}", e)
        }

        return SshKeyLoadResult(keyProvider = keyProvider)
    }

    private fun loadPuttyKey(privateKey: CharArray, passwordFinder: PasswordFinder?): SshKeyLoadResult {
        val keyProvider = PuTTYKeyFile().apply {
            init(CharArrayReader(privateKey), passwordFinder)
        }
        return SshKeyLoadResult(keyProvider = keyProvider)
    }

    private fun loadUnknownKey(privateKey: CharArray, passwordFinder: PasswordFinder?): SshKeyLoadResult {
        val keyFile = createSecureTempFile()

        try {
            keyFile.writer(Charsets.UTF_8).use { writer ->
                writer.write(privateKey)
            }
            keyFile.setReadable(false, false)
            keyFile.setReadable(true, true)

            val keyProvider = OpenSSHKeyFile().apply {
                init(keyFile, passwordFinder)
            }

            try {
                keyProvider.private
                Log.d(TAG, "Unknown format key parsed as OpenSSH")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse unknown key format: ${e.javaClass.simpleName}")
                secureDeleteFile(keyFile)
                throw RuntimeException("Failed to parse private key: ${e.message}", e)
            }

            return SshKeyLoadResult(keyProvider = keyProvider, tempFile = keyFile)
        } catch (e: Exception) {
            secureDeleteFile(keyFile)
            throw e
        }
    }

    private fun createSecureTempFile(): File {
        val file = File.createTempFile("ssh_key_", ".tmp")
        // 设置初始权限
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
        return file
    }

    /**
     * 安全清零 CharArray
     */
    fun secureWipe(data: CharArray?) {
        data?.fill('\u0000')
    }

    /**
     * 安全清零 ByteArray
     */
    fun secureWipe(data: ByteArray?) {
        data?.fill(0)
    }
}
