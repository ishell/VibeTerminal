package com.vibe.terminal.notification

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Claude Code 通知检测器
 *
 * 检测终端输出中的特定模式，判断 Claude Code 是否：
 * 1. 完成任务（返回到提示符）
 * 2. 需要用户输入（权限请求、问题等）
 */
class ClaudeNotificationDetector {

    companion object {
        private const val TAG = "ClaudeNotificationDetector"

        // Claude Code prompt patterns
        private val PROMPT_PATTERNS = listOf(
            "❯",           // Claude Code default prompt
            "claude>",     // Alternative prompt
            "> "           // Simple prompt (at start of line after newline)
        )

        // Permission/input request patterns
        private val PERMISSION_PATTERNS = listOf(
            "Allow",
            "Deny",
            "Yes/No",
            "y/n",
            "[Y/n]",
            "[y/N]",
            "Press Enter",
            "Continue?",
            "Proceed?",
            "approve",
            "permission"
        )

        // Task completion patterns (Claude finished working)
        private val COMPLETION_PATTERNS = listOf(
            "Task completed",
            "Done!",
            "Finished",
            "Successfully",
            "Complete"
        )

        // Thinking/working patterns (Claude is busy)
        private val WORKING_PATTERNS = listOf(
            "Thinking",
            "Working",
            "Processing",
            "Analyzing",
            "Reading",
            "Writing",
            "Searching"
        )

        // Debounce time to avoid duplicate notifications (ms)
        private const val NOTIFICATION_DEBOUNCE_MS = 3000L

        // Time threshold to detect "idle" state (ms)
        private const val IDLE_THRESHOLD_MS = 5000L
    }

    sealed class NotificationEvent {
        data object TaskCompleted : NotificationEvent()
        data object InputNeeded : NotificationEvent()
        data object Idle : NotificationEvent()
    }

    private val _notificationEvent = MutableStateFlow<NotificationEvent?>(null)
    val notificationEvent: StateFlow<NotificationEvent?> = _notificationEvent.asStateFlow()

    // State tracking
    private var lastOutputTime = 0L
    private var lastNotificationTime = 0L
    private var isClaudeWorking = false
    private var recentOutput = StringBuilder()
    private val outputBuffer = StringBuilder()

    // Track recent lines for context
    private val recentLines = mutableListOf<String>()
    private val maxRecentLines = 10

    /**
     * 处理终端输出数据
     * @param data 原始字节数据
     */
    fun processOutput(data: ByteArray) {
        val text = String(data, Charsets.UTF_8)
        processOutput(text)
    }

    /**
     * 处理终端输出文本
     */
    fun processOutput(text: String) {
        val currentTime = System.currentTimeMillis()
        lastOutputTime = currentTime

        // Append to buffer
        outputBuffer.append(text)

        // Process complete lines
        while (outputBuffer.contains("\n")) {
            val newlineIndex = outputBuffer.indexOf("\n")
            val line = outputBuffer.substring(0, newlineIndex)
            outputBuffer.delete(0, newlineIndex + 1)
            processLine(line, currentTime)
        }

        // Also check current buffer for patterns (for partial lines)
        checkPatternsInBuffer(outputBuffer.toString(), currentTime)
    }

    private fun processLine(line: String, currentTime: Long) {
        // Store recent lines for context
        recentLines.add(line)
        if (recentLines.size > maxRecentLines) {
            recentLines.removeAt(0)
        }

        // Check for working indicators
        if (WORKING_PATTERNS.any { line.contains(it, ignoreCase = true) }) {
            isClaudeWorking = true
            Log.d(TAG, "Claude is working: $line")
        }

        // Check for permission/input request
        if (PERMISSION_PATTERNS.any { line.contains(it, ignoreCase = true) }) {
            if (shouldNotify(currentTime)) {
                Log.d(TAG, "Input needed detected: $line")
                triggerNotification(NotificationEvent.InputNeeded)
            }
        }

        // Check for task completion
        if (COMPLETION_PATTERNS.any { line.contains(it, ignoreCase = true) }) {
            if (isClaudeWorking && shouldNotify(currentTime)) {
                Log.d(TAG, "Task completed detected: $line")
                triggerNotification(NotificationEvent.TaskCompleted)
                isClaudeWorking = false
            }
        }

        // Check for prompt (indicates Claude returned control)
        if (PROMPT_PATTERNS.any { pattern ->
            line.trim().startsWith(pattern) || line.trim().endsWith(pattern)
        }) {
            if (isClaudeWorking && shouldNotify(currentTime)) {
                Log.d(TAG, "Prompt detected (task likely complete): $line")
                triggerNotification(NotificationEvent.TaskCompleted)
                isClaudeWorking = false
            }
        }
    }

    private fun checkPatternsInBuffer(buffer: String, currentTime: Long) {
        // Quick check for urgent patterns in current buffer
        if (buffer.isEmpty()) return

        // Permission patterns are high priority
        if (PERMISSION_PATTERNS.any { buffer.contains(it, ignoreCase = true) }) {
            if (shouldNotify(currentTime)) {
                Log.d(TAG, "Input needed detected in buffer")
                triggerNotification(NotificationEvent.InputNeeded)
            }
        }
    }

    /**
     * 检查是否应该发送通知（防抖动）
     */
    private fun shouldNotify(currentTime: Long): Boolean {
        return currentTime - lastNotificationTime > NOTIFICATION_DEBOUNCE_MS
    }

    private fun triggerNotification(event: NotificationEvent) {
        lastNotificationTime = System.currentTimeMillis()
        _notificationEvent.value = event
    }

    /**
     * 清除通知事件
     */
    fun clearNotification() {
        _notificationEvent.value = null
    }

    /**
     * 检查是否空闲（长时间无输出）
     */
    fun checkIdleState(): Boolean {
        val currentTime = System.currentTimeMillis()
        val idleTime = currentTime - lastOutputTime

        if (idleTime > IDLE_THRESHOLD_MS && isClaudeWorking) {
            // Claude was working but now idle - might need input
            return true
        }
        return false
    }

    /**
     * 重置状态
     */
    fun reset() {
        isClaudeWorking = false
        recentLines.clear()
        outputBuffer.clear()
        _notificationEvent.value = null
    }

    /**
     * 手动标记 Claude 开始工作（用于用户提交 prompt 时）
     */
    fun markClaudeWorking() {
        isClaudeWorking = true
        lastOutputTime = System.currentTimeMillis()
    }
}
