package com.vibe.terminal.notification

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
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
import javax.inject.Inject

/**
 * 通知 Webhook 服务
 *
 * 在本地启动一个 HTTP 服务器监听来自服务器的 Claude Code 通知
 * 通过 Tailscale 网络，服务器可以直接向手机发送通知
 */
@AndroidEntryPoint
class NotificationWebhookService : Service() {

    companion object {
        private const val TAG = "NotificationWebhook"
        const val DEFAULT_PORT = 8765
        const val ACTION_START = "com.vibe.terminal.notification.START"
        const val ACTION_STOP = "com.vibe.terminal.notification.STOP"
    }

    @Inject
    lateinit var notificationService: ClaudeNotificationService

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

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
        try {
            socket.soTimeout = 5000 // 5 second timeout

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

            Log.d(TAG, "Request: $method $path, body: $body")

            // Handle notification endpoint
            if (method == "POST" && path == "/notify") {
                handleNotification(body)
                sendResponse(writer, 200, "OK")
            } else if (method == "GET" && path == "/health") {
                sendResponse(writer, 200, "VibeTerminal Webhook Server Running")
            } else {
                sendResponse(writer, 404, "Not Found")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun handleNotification(body: String) {
        try {
            // Parse simple JSON format: {"event": "stop|input", "project": "name", "message": "..."}
            val event = extractJsonValue(body, "event")
            val project = extractJsonValue(body, "project")
            val message = extractJsonValue(body, "message")

            Log.d(TAG, "Notification: event=$event, project=$project, message=$message")

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
                    notificationService.notifyTaskCompleted(project)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notification", e)
        }
    }

    private fun extractJsonValue(json: String, key: String): String? {
        // Simple JSON value extraction (no external library needed)
        val pattern = """"$key"\s*:\s*"([^"]*)"""".toRegex()
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    private fun sendResponse(writer: PrintWriter, statusCode: Int, body: String) {
        val statusText = when (statusCode) {
            200 -> "OK"
            404 -> "Not Found"
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

    private fun stopServer() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Log.i(TAG, "Webhook server stopped")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        serviceScope.cancel()
    }
}
