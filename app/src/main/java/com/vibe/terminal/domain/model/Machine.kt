package com.vibe.terminal.domain.model

import java.util.UUID

/**
 * 开发机器领域模型
 */
data class Machine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType,
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null
) {
    enum class AuthType {
        PASSWORD,
        SSH_KEY
    }

    /**
     * 获取显示用的连接地址
     */
    fun displayAddress(): String {
        return if (port == 22) {
            "$username@$host"
        } else {
            "$username@$host:$port"
        }
    }
}
