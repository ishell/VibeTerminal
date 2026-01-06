package com.vibe.terminal.data.ssh

/**
 * SSH连接配置
 */
data class SshConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: AuthMethod
) {
    sealed class AuthMethod {
        data class Password(val password: String) : AuthMethod()
        data class PublicKey(
            val privateKey: String,
            val passphrase: String? = null
        ) : AuthMethod()
    }
}
