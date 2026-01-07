package com.vibe.terminal.data.ssh

import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.keyprovider.PuTTYKeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.File
import java.io.StringReader

/**
 * SSH密钥加载结果
 *
 * @param keyProvider 密钥提供者
 * @param tempFile 临时文件（如果有的话），需要在使用完密钥后删除
 */
data class SshKeyLoadResult(
    val keyProvider: KeyProvider,
    val tempFile: File? = null
) {
    /**
     * 清理临时文件
     */
    fun cleanup() {
        tempFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {
                // 忽略删除错误
            }
        }
    }
}

/**
 * SSH密钥加载器
 *
 * 自动检测密钥格式并加载
 */
object SshKeyLoader {

    /**
     * 加载SSH密钥
     *
     * 注意：对于新版OpenSSH格式密钥，会创建临时文件，
     * 调用者需要在认证完成后调用 SshKeyLoadResult.cleanup() 来清理
     *
     * @param privateKey 私钥内容
     * @param passphrase 密钥密码（可选）
     * @return 密钥加载结果
     */
    fun loadKey(privateKey: String, passphrase: String?): SshKeyLoadResult {
        val passwordFinder: PasswordFinder? = passphrase?.let {
            PasswordUtils.createOneOff(it.toCharArray())
        }

        return when {
            privateKey.contains("BEGIN OPENSSH PRIVATE KEY") -> {
                // 新版OpenSSH格式需要通过临时文件加载
                // OpenSSHKeyFile是懒加载的，需要保留文件直到认证完成
                val keyFile = File.createTempFile("ssh_key_", ".tmp")
                keyFile.writeText(privateKey)
                keyFile.setReadable(false, false)
                keyFile.setReadable(true, true)
                SshKeyLoadResult(
                    keyProvider = OpenSSHKeyFile().apply { init(keyFile, passwordFinder) },
                    tempFile = keyFile
                )
            }
            privateKey.contains("BEGIN RSA PRIVATE KEY") ||
            privateKey.contains("BEGIN DSA PRIVATE KEY") ||
            privateKey.contains("BEGIN EC PRIVATE KEY") -> {
                // PEM format - 可以直接从字符串加载
                SshKeyLoadResult(
                    keyProvider = PKCS8KeyFile().apply {
                        init(StringReader(privateKey), passwordFinder)
                    }
                )
            }
            privateKey.contains("PuTTY-User-Key-File") -> {
                SshKeyLoadResult(
                    keyProvider = PuTTYKeyFile().apply {
                        init(StringReader(privateKey), passwordFinder)
                    }
                )
            }
            else -> {
                // 默认用临时文件方式，更兼容
                val keyFile = File.createTempFile("ssh_key_", ".tmp")
                keyFile.writeText(privateKey)
                keyFile.setReadable(false, false)
                keyFile.setReadable(true, true)
                SshKeyLoadResult(
                    keyProvider = OpenSSHKeyFile().apply { init(keyFile, passwordFinder) },
                    tempFile = keyFile
                )
            }
        }
    }

    /**
     * 使用密钥执行操作，自动处理临时文件清理
     */
    inline fun <T> withKey(
        privateKey: String,
        passphrase: String?,
        block: (KeyProvider) -> T
    ): T {
        val result = loadKey(privateKey, passphrase)
        return try {
            block(result.keyProvider)
        } finally {
            result.cleanup()
        }
    }
}
