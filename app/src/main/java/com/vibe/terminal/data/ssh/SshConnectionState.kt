package com.vibe.terminal.data.ssh

/**
 * SSH连接状态
 */
sealed class SshConnectionState {
    data object Disconnected : SshConnectionState()
    data object Connecting : SshConnectionState()
    data object Connected : SshConnectionState()
    data class Error(
        val errorInfo: SshErrorInfo,
        val cause: Throwable? = null
    ) : SshConnectionState() {
        // 便捷属性
        val message: String get() = errorInfo.title
    }
}
