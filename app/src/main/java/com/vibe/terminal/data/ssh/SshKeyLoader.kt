package com.vibe.terminal.data.ssh

import android.util.Log
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.File
import java.io.StringReader

private const val TAG = "SshKeyLoader"

/**
 * SSH密钥加载结果
 */
data class SshKeyLoadResult(
    val keyProvider: KeyProvider,
    val tempFile: File? = null
) {
    fun cleanup() {
        tempFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) { }
        }
    }
}

/**
 * SSH密钥加载器
 */
object SshKeyLoader {

    /**
     * 加载SSH密钥
     */
    fun loadKey(privateKey: String, passphrase: String?): SshKeyLoadResult {
        Log.d(TAG, "Loading SSH key, length=${privateKey.length}, hasPassphrase=${passphrase != null}")

        val passwordFinder: PasswordFinder? = passphrase?.let {
            PasswordUtils.createOneOff(it.toCharArray())
        }

        val keyType = when {
            privateKey.contains("BEGIN OPENSSH PRIVATE KEY") -> "OpenSSH"
            privateKey.contains("BEGIN RSA PRIVATE KEY") -> "RSA-PEM"
            privateKey.contains("BEGIN DSA PRIVATE KEY") -> "DSA-PEM"
            privateKey.contains("BEGIN EC PRIVATE KEY") -> "EC-PEM"
            privateKey.contains("PuTTY-User-Key-File") -> "PuTTY"
            else -> "Unknown"
        }
        Log.d(TAG, "Detected key type: $keyType")

        return when {
            privateKey.contains("BEGIN OPENSSH PRIVATE KEY") -> {
                // 新版OpenSSH格式 (OpenSSH 7.8+)
                val keyFile = File.createTempFile("ssh_key_", ".tmp")
                Log.d(TAG, "Created temp file: ${keyFile.absolutePath}")

                keyFile.writeText(privateKey)
                keyFile.setReadable(false, false)
                keyFile.setReadable(true, true)

                val keyProvider = OpenSSHKeyV1KeyFile().apply {
                    init(keyFile, passwordFinder)
                }

                // 立即尝试解析密钥，捕获任何错误
                try {
                    val privateKeyObj = keyProvider.private
                    val publicKeyObj = keyProvider.public
                    Log.d(TAG, "Key parsed successfully: private=${privateKeyObj?.algorithm}, public=${publicKeyObj?.algorithm}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse OpenSSH key: ${e.javaClass.simpleName}: ${e.message}", e)
                    keyFile.delete()
                    throw RuntimeException("Failed to parse OpenSSH private key: ${e.message}", e)
                }

                SshKeyLoadResult(keyProvider = keyProvider, tempFile = keyFile)
            }
            privateKey.contains("BEGIN RSA PRIVATE KEY") ||
            privateKey.contains("BEGIN DSA PRIVATE KEY") ||
            privateKey.contains("BEGIN EC PRIVATE KEY") -> {
                val keyProvider = PKCS8KeyFile().apply {
                    init(StringReader(privateKey), passwordFinder)
                }

                try {
                    val privateKeyObj = keyProvider.private
                    Log.d(TAG, "PEM key parsed successfully: ${privateKeyObj?.algorithm}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse PEM key: ${e.message}", e)
                    throw RuntimeException("Failed to parse PEM private key: ${e.message}", e)
                }

                SshKeyLoadResult(keyProvider = keyProvider)
            }
            privateKey.contains("PuTTY-User-Key-File") -> {
                val keyProvider = PuTTYKeyFile().apply {
                    init(StringReader(privateKey), passwordFinder)
                }
                SshKeyLoadResult(keyProvider = keyProvider)
            }
            else -> {
                val keyFile = File.createTempFile("ssh_key_", ".tmp")
                keyFile.writeText(privateKey)
                keyFile.setReadable(false, false)
                keyFile.setReadable(true, true)

                val keyProvider = OpenSSHKeyFile().apply {
                    init(keyFile, passwordFinder)
                }

                try {
                    keyProvider.private
                    Log.d(TAG, "Unknown format key parsed as OpenSSH")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse unknown key format: ${e.message}", e)
                    keyFile.delete()
                    throw RuntimeException("Failed to parse private key: ${e.message}", e)
                }

                SshKeyLoadResult(keyProvider = keyProvider, tempFile = keyFile)
            }
        }
    }
}
