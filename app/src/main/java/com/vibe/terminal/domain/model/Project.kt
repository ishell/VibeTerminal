package com.vibe.terminal.domain.model

import java.util.UUID

/**
 * 项目/会话领域模型
 */
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val machineId: String,
    val zellijSession: String,
    val workingDirectory: String = "~",
    val lastConnected: Long = 0
) {
    /**
     * 获取Zellij附加命令
     */
    fun getZellijAttachCommand(): String {
        return "zellij attach $zellijSession --create"
    }

    /**
     * 是否曾经连接过
     */
    fun hasConnected(): Boolean = lastConnected > 0
}
