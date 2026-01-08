package com.vibe.terminal.data.ssh

/**
 * SSH连接配置
 */
data class SshConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod,
    /** 已验证的主机密钥指纹（用于安全连接） */
    val trustedFingerprint: String? = null
) {
    sealed class AuthMethod {
        data class Password(val password: String) : AuthMethod()
        data class PublicKey(
            val privateKey: String,
            val passphrase: String? = null
        ) : AuthMethod()
    }
}

/**
 * SSH连接结果
 */
sealed class SshConnectResult {
    /** 连接成功 */
    data object Success : SshConnectResult()

    /** 需要主机密钥确认 */
    data class HostKeyVerificationRequired(
        val status: HostKeyStatus
    ) : SshConnectResult()

    /** 连接失败 */
    data class Failed(val error: Throwable) : SshConnectResult()
}
