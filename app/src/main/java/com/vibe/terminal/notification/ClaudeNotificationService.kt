package com.vibe.terminal.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vibe.terminal.R
import com.vibe.terminal.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Claude Code 通知服务
 *
 * 负责发送 Android 系统通知，当 Claude Code：
 * - 完成任务时通知用户
 * - 需要用户输入时通知用户
 */
@Singleton
class ClaudeNotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val CHANNEL_ID_TASK = "claude_task_notifications"
        private const val CHANNEL_ID_INPUT = "claude_input_notifications"

        private const val NOTIFICATION_ID_TASK_COMPLETED = 1001
        private const val NOTIFICATION_ID_INPUT_NEEDED = 1002
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Task completion channel
            val taskChannel = NotificationChannel(
                CHANNEL_ID_TASK,
                "Task Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when Claude Code completes a task"
                enableVibration(true)
                setShowBadge(true)
            }

            // Input needed channel (higher priority)
            val inputChannel = NotificationChannel(
                CHANNEL_ID_INPUT,
                "Input Required",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when Claude Code needs your input"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(taskChannel)
            notificationManager.createNotificationChannel(inputChannel)
        }
    }

    /**
     * 发送任务完成通知
     */
    fun notifyTaskCompleted(projectName: String? = null) {
        val title = "Task Completed"
        val message = if (projectName != null) {
            "Claude has finished the task in $projectName"
        } else {
            "Claude has finished the task"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_TASK)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainActivityIntent())
            .build()

        notificationManager.notify(NOTIFICATION_ID_TASK_COMPLETED, notification)
    }

    /**
     * 发送需要输入通知
     */
    fun notifyInputNeeded(projectName: String? = null) {
        val title = "Input Required"
        val message = if (projectName != null) {
            "Claude needs your input in $projectName"
        } else {
            "Claude needs your input"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_INPUT)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(createMainActivityIntent())
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        notificationManager.notify(NOTIFICATION_ID_INPUT_NEEDED, notification)
    }

    /**
     * 发送空闲通知
     */
    fun notifyIdle(projectName: String? = null) {
        val title = "Claude is Idle"
        val message = if (projectName != null) {
            "Claude might be waiting for your response in $projectName"
        } else {
            "Claude might be waiting for your response"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_INPUT)
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createMainActivityIntent())
            .build()

        notificationManager.notify(NOTIFICATION_ID_INPUT_NEEDED, notification)
    }

    /**
     * 取消所有通知
     */
    fun cancelAll() {
        notificationManager.cancel(NOTIFICATION_ID_TASK_COMPLETED)
        notificationManager.cancel(NOTIFICATION_ID_INPUT_NEEDED)
    }

    /**
     * 取消任务完成通知
     */
    fun cancelTaskNotification() {
        notificationManager.cancel(NOTIFICATION_ID_TASK_COMPLETED)
    }

    /**
     * 取消输入需求通知
     */
    fun cancelInputNotification() {
        notificationManager.cancel(NOTIFICATION_ID_INPUT_NEEDED)
    }

    private fun createMainActivityIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
