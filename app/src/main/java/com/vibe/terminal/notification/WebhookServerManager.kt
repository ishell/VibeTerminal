package com.vibe.terminal.notification

import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Webhook 服务器管理器
 *
 * 管理本地 HTTP 服务器的启动/停止，并提供服务器状态
 */
@Singleton
class WebhookServerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WebhookServerManager"
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverAddress = MutableStateFlow<String?>(null)
    val serverAddress: StateFlow<String?> = _serverAddress.asStateFlow()

    /**
     * 启动 Webhook 服务器
     */
    fun startServer() {
        val intent = Intent(context, NotificationWebhookService::class.java).apply {
            action = NotificationWebhookService.ACTION_START
        }
        context.startService(intent)
        _isRunning.value = true
        updateServerAddress()
        Log.i(TAG, "Webhook server started")
    }

    /**
     * 停止 Webhook 服务器
     */
    fun stopServer() {
        val intent = Intent(context, NotificationWebhookService::class.java).apply {
            action = NotificationWebhookService.ACTION_STOP
        }
        context.startService(intent)
        _isRunning.value = false
        _serverAddress.value = null
        Log.i(TAG, "Webhook server stopped")
    }

    /**
     * 获取 Tailscale IP 地址（用于服务器配置）
     */
    fun getTailscaleIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { iface ->
                iface.inetAddresses.toList()
            }?.filterIsInstance<Inet4Address>()
            ?.filter { addr ->
                // Tailscale 地址通常是 100.x.x.x
                val ip = addr.hostAddress ?: ""
                ip.startsWith("100.") && !addr.isLoopbackAddress
            }?.firstOrNull()?.hostAddress
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Tailscale IP", e)
            null
        }
    }

    /**
     * 获取所有可用的 IP 地址
     */
    fun getAllIpAddresses(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.flatMap { iface ->
                iface.inetAddresses.toList()
            }?.filterIsInstance<Inet4Address>()
            ?.filter { addr -> !addr.isLoopbackAddress }
            ?.mapNotNull { it.hostAddress }
            ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP addresses", e)
            emptyList()
        }
    }

    private fun updateServerAddress() {
        val tailscaleIp = getTailscaleIp()
        val port = NotificationWebhookService.DEFAULT_PORT
        _serverAddress.value = if (tailscaleIp != null) {
            "http://$tailscaleIp:$port"
        } else {
            // Fallback to any available IP
            getAllIpAddresses().firstOrNull()?.let { ip ->
                "http://$ip:$port"
            }
        }
    }

    /**
     * 生成服务器端 Hook 配置脚本
     */
    fun generateHookScript(): String {
        val serverUrl = _serverAddress.value ?: "http://<phone-tailscale-ip>:${NotificationWebhookService.DEFAULT_PORT}"

        return """
#!/bin/bash
# Claude Code Notification Hook Script
# Place this in ~/.claude/hooks/ and configure in settings.json

WEBHOOK_URL="$serverUrl/notify"

# Function to send notification
send_notification() {
    local event="${'$'}1"
    local project="${'$'}2"
    local message="${'$'}3"

    curl -s -X POST "${'$'}WEBHOOK_URL" \
        -H "Content-Type: application/json" \
        -d "{\"event\": \"${'$'}event\", \"project\": \"${'$'}project\", \"message\": \"${'$'}message\"}" \
        --connect-timeout 2 \
        --max-time 5 \
        || true  # Don't fail if notification fails
}

# Read hook event from stdin (Claude Code passes context as JSON)
read -r input

# Extract event type from args or default to stop
event="${'$'}{1:-stop}"
project="${'$'}(echo "${'$'}input" | grep -o '"cwd":"[^"]*"' | cut -d'"' -f4 | xargs basename 2>/dev/null || echo "unknown")"

case "${'$'}event" in
    stop)
        send_notification "stop" "${'$'}project" "Task completed"
        ;;
    input|permission)
        send_notification "input" "${'$'}project" "Input needed"
        ;;
    *)
        send_notification "${'$'}event" "${'$'}project" ""
        ;;
esac
""".trimIndent()
    }

    /**
     * 生成 Claude Code settings.json 配置
     */
    fun generateSettingsJson(): String {
        val serverUrl = _serverAddress.value ?: "http://<phone-tailscale-ip>:${NotificationWebhookService.DEFAULT_PORT}"

        return """
{
  "hooks": {
    "Stop": [{
      "hooks": [{
        "type": "command",
        "command": "curl -s -X POST '$serverUrl/notify' -H 'Content-Type: application/json' -d '{\"event\":\"stop\",\"project\":\"'$(basename \"${'$'}PWD\")'\"}'",
        "timeout": 5
      }]
    }],
    "Notification": [{
      "matcher": "permission_prompt|idle_prompt",
      "hooks": [{
        "type": "command",
        "command": "curl -s -X POST '$serverUrl/notify' -H 'Content-Type: application/json' -d '{\"event\":\"input\",\"project\":\"'$(basename \"${'$'}PWD\")'\"}'",
        "timeout": 5
      }]
    }]
  }
}
""".trimIndent()
    }
}
