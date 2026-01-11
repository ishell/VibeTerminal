package com.vibe.terminal.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * 通知 Webhook 服务
 *
 * 在本地启动一个 HTTP 服务器监听来自服务器的 Claude Code 通知
 * 通过 Tailscale 网络，服务器可以直接向手机发送通知
 *
 * 安全特性：
 * - 认证令牌机制：每个请求必须携带有效的认证令牌
 * - 速率限制：防止 DoS 攻击
 * - 请求大小限制：防止内存耗尽攻击
 * - IP 记录：记录请求来源便于审计
 */
@AndroidEntryPoint
class NotificationWebhookService : Service() {

    companion object {
        private const val TAG = "NotificationWebhook"
        const val DEFAULT_PORT = 8765
        const val ACTION_START = "com.vibe.terminal.notification.START"
        const val ACTION_STOP = "com.vibe.terminal.notification.STOP"

        // 安全配置
        private const val MAX_BODY_SIZE = 4096  // 4KB 最大请求体
        private const val RATE_LIMIT_WINDOW_MS = 60_000L  // 1 分钟窗口
        private const val RATE_LIMIT_MAX_REQUESTS = 30  // 每窗口最大请求数
        private const val AUTH_TOKEN_LENGTH = 32  // 256 位令牌

        // 令牌存储
        private const val PREFS_NAME = "webhook_security"
        private const val KEY_AUTH_TOKEN = "auth_token"

        /**
         * 获取或生成认证令牌
         */
        fun getOrCreateAuthToken(context: Context): String {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            var token = prefs.getString(KEY_AUTH_TOKEN, null)
            if (token == null) {
                token = generateSecureToken()
                prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
            }
            return token
        }

        /**
         * 重新生成认证令牌
         */
        fun regenerateAuthToken(context: Context): String {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val token = generateSecureToken()
            prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
            return token
        }

        private fun generateSecureToken(): String {
            val random = SecureRandom()
            val bytes = ByteArray(AUTH_TOKEN_LENGTH)
            random.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    @Inject
    lateinit var notificationService: ClaudeNotificationService

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var isRunning = false
    private var authToken: String? = null

    // 速率限制：IP -> 请求时间戳列表
    private val rateLimitMap = ConcurrentHashMap<String, MutableList<Long>>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 加载认证令牌
        authToken = getOrCreateAuthToken(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_STICKY
    }

    private fun startServer() {
        if (isRunning) return

        serverJob = serviceScope.launch {
            try {
                serverSocket = ServerSocket(DEFAULT_PORT)
                isRunning = true
                Log.i(TAG, "Webhook server started on port $DEFAULT_PORT")

                while (isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch { handleClient(clientSocket) }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val clientIp = socket.inetAddress?.hostAddress ?: "unknown"

        try {
            socket.soTimeout = 5000 // 5 second timeout

            // 速率限制检查
            if (!checkRateLimit(clientIp)) {
                Log.w(TAG, "Rate limit exceeded for $clientIp")
                sendErrorResponse(socket, 429, "Too Many Requests")
                return
            }

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            // Read HTTP request
            val requestLine = reader.readLine() ?: return
            val headers = mutableMapOf<String, String>()
            var contentLength = 0

            // Read headers
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                val colonIndex = line.indexOf(':')
                if (colonIndex > 0) {
                    val key = line.substring(0, colonIndex).trim().lowercase()
                    val value = line.substring(colonIndex + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
                line = reader.readLine()
            }

            // 请求体大小检查
            if (contentLength > MAX_BODY_SIZE) {
                Log.w(TAG, "Request body too large from $clientIp: $contentLength bytes")
                sendResponse(writer, 413, "Payload Too Large")
                return
            }

            // Read body
            val body = if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                reader.read(buffer, 0, contentLength)
                String(buffer)
            } else ""

            // Parse request
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: ""
            val path = parts.getOrNull(1) ?: ""

            Log.d(TAG, "Request from $clientIp: $method $path")

            // 处理健康检查（无需认证）
            if (method == "GET" && path == "/health") {
                sendResponse(writer, 200, "VibeTerminal Webhook Server Running")
                return
            }

            // 认证检查
            val providedToken = headers["x-auth-token"] ?: extractJsonValue(body, "auth_token")
            if (!validateAuthToken(providedToken)) {
                Log.w(TAG, "Authentication failed from $clientIp")
                sendResponse(writer, 401, "Unauthorized")
                return
            }

            // Handle notification endpoint
            if (method == "POST" && path == "/notify") {
                handleNotification(body)
                sendResponse(writer, 200, "OK")
            } else {
                sendResponse(writer, 404, "Not Found")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling client from $clientIp", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * 检查速率限制
     * @return true 如果请求允许，false 如果超过限制
     */
    private fun checkRateLimit(clientIp: String): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - RATE_LIMIT_WINDOW_MS

        val requests = rateLimitMap.computeIfAbsent(clientIp) { mutableListOf() }

        synchronized(requests) {
            // 清理过期的请求记录
            requests.removeAll { it < windowStart }

            // 检查是否超过限制
            if (requests.size >= RATE_LIMIT_MAX_REQUESTS) {
                return false
            }

            // 记录当前请求
            requests.add(now)
        }

        // 定期清理空的 IP 记录
        if (rateLimitMap.size > 100) {
            rateLimitMap.entries.removeIf { it.value.isEmpty() }
        }

        return true
    }

    /**
     * 验证认证令牌
     */
    private fun validateAuthToken(token: String?): Boolean {
        if (token == null || token.isEmpty()) return false
        if (authToken == null) return false

        // 使用常量时间比较防止时序攻击
        return constantTimeEquals(token, authToken!!)
    }

    /**
     * 常量时间字符串比较（防止时序攻击）
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    private fun handleNotification(body: String) {
        try {
            // Parse simple JSON format: {"event": "stop|input", "project": "name", "message": "..."}
            val event = extractJsonValue(body, "event")
            val project = extractJsonValue(body, "project")
            val message = extractJsonValue(body, "message")

            Log.d(TAG, "Notification: event=$event, project=$project")

            when (event) {
                "stop", "completed", "done" -> {
                    notificationService.notifyTaskCompleted(project)
                }
                "input", "permission", "waiting" -> {
                    notificationService.notifyInputNeeded(project)
                }
                "idle" -> {
                    notificationService.notifyIdle(project)
                }
                else -> {
                    // Unknown event, treat as general notification
                    if (event != null) {
                        notificationService.notifyTaskCompleted(project)
                    }
                    // 空事件不处理，可能是恶意请求
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notification", e)
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        // Simple JSON value extraction (no external library needed)
        // 添加输入验证
        if (json.length > MAX_BODY_SIZE) return null
        val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    private fun sendResponse(writer: PrintWriter, statusCode: Int, body: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            401 -> "Unauthorized"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            429 -> "Too Many Requests"
            else -> "Error"
        }

        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: text/plain\r\n")
        writer.print("Content-Length: ${body.length}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(body)
        writer.flush()
    }

    private fun sendErrorResponse(socket: Socket, statusCode: Int, body: String) {
        try {
            val writer = PrintWriter(socket.getOutputStream(), true)
            sendResponse(writer, statusCode, body)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send error response", e)
        }
    }

    private fun stopServer() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        rateLimitMap.clear()
        Log.i(TAG, "Webhook server stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        serviceScope.cancel()
    }
}
