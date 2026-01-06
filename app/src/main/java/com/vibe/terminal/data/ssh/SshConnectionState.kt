package com.vibe.terminal.data.ssh

/**
 * SSH连接状态
 */
sealed class SshConnectionState {
    data object Disconnected : SshConnectionState()
    data object Connecting : SshConnectionState()
    data object Connected : SshConnectionState()
    data class Error(val message: String, val cause: Throwable? = null) : SshConnectionState()
}
