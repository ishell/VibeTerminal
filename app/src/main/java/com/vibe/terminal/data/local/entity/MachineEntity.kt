package com.vibe.terminal.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SSH认证类型
 */
enum class AuthType {
    PASSWORD,
    SSH_KEY
}

/**
 * 开发机器配置实体
 */
@Entity(tableName = "machines")
data class MachineEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType,
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
