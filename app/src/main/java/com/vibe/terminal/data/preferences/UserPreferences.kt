package com.vibe.terminal.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * 用户设置存储
 */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SCREEN_TIMEOUT_KEY = intPreferencesKey("screen_timeout_minutes")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val TERMINAL_FONT_KEY = stringPreferencesKey("terminal_font")
        private val TERMINAL_FONT_SIZE_KEY = intPreferencesKey("terminal_font_size")
        private val KEYBOARD_STYLE_KEY = stringPreferencesKey("keyboard_style")
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val WEBHOOK_SERVER_ENABLED_KEY = booleanPreferencesKey("webhook_server_enabled")
        private val SMART_PARSING_ENABLED_KEY = booleanPreferencesKey("smart_parsing_enabled")

        // 屏幕常亮时间选项（分钟）
        val SCREEN_TIMEOUT_OPTIONS = listOf(
            0 to "System default",
            5 to "5 minutes",
            10 to "10 minutes",
            30 to "30 minutes",
            60 to "1 hour",
            -1 to "Always on"
        )

        // 语言选项
        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_EN = "en"
        const val LANGUAGE_ZH = "zh"

        val LANGUAGE_OPTIONS = listOf(
            LANGUAGE_SYSTEM,
            LANGUAGE_EN,
            LANGUAGE_ZH
        )

        // 终端字体选项
        const val FONT_IOSEVKA = "iosevka"
        const val FONT_JETBRAINS_MONO = "jetbrains_mono"
        const val FONT_SYSTEM_MONO = "system_mono"

        val FONT_OPTIONS = listOf(
            FONT_IOSEVKA,
            FONT_JETBRAINS_MONO,
            FONT_SYSTEM_MONO
        )

        // 终端字体大小选项 (sp)
        // 10sp: ~68列 (紧凑，适合需要更多列数的用户)
        // 11sp: ~62列 (平衡)
        // 12sp: ~57列 (默认，良好可读性)
        // 13sp: ~52列 (舒适)
        // 14sp: ~48列 (大字体，易读)
        const val FONT_SIZE_COMPACT = 10
        const val FONT_SIZE_BALANCED = 11
        const val FONT_SIZE_DEFAULT = 12
        const val FONT_SIZE_COMFORT = 13
        const val FONT_SIZE_LARGE = 14

        val FONT_SIZE_OPTIONS = listOf(
            FONT_SIZE_COMPACT,
            FONT_SIZE_BALANCED,
            FONT_SIZE_DEFAULT,
            FONT_SIZE_COMFORT,
            FONT_SIZE_LARGE
        )

        // 虚拟键盘风格选项
        const val KEYBOARD_STYLE_NONE = "none"
        const val KEYBOARD_STYLE_PATH = "path"
        const val KEYBOARD_STYLE_TERMIUS = "termius"
        const val KEYBOARD_STYLE_BOTH = "both"

        val KEYBOARD_STYLE_OPTIONS = listOf(
            KEYBOARD_STYLE_NONE,
            KEYBOARD_STYLE_PATH,
            KEYBOARD_STYLE_TERMIUS,
            KEYBOARD_STYLE_BOTH
        )

        const val DEFAULT_SCREEN_TIMEOUT = 0  // 默认跟随系统
        const val DEFAULT_LANGUAGE = LANGUAGE_SYSTEM  // 默认跟随系统
        const val DEFAULT_TERMINAL_FONT = FONT_IOSEVKA  // 默认 Iosevka Nerd Font
        const val DEFAULT_TERMINAL_FONT_SIZE = FONT_SIZE_DEFAULT  // 默认 12sp
        const val DEFAULT_KEYBOARD_STYLE = KEYBOARD_STYLE_TERMIUS  // 默认 Termius 风格
        const val DEFAULT_NOTIFICATIONS_ENABLED = true  // 默认开启通知
        const val DEFAULT_WEBHOOK_SERVER_ENABLED = false  // 默认关闭 webhook 服务器
        const val DEFAULT_SMART_PARSING_ENABLED = true  // 默认开启智能解析
    }

    /**
     * 获取屏幕常亮时间（分钟）
     * 0 = 跟随系统
     * -1 = 永远常亮
     * 其他 = 指定分钟数
     */
    val screenTimeout: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SCREEN_TIMEOUT_KEY] ?: DEFAULT_SCREEN_TIMEOUT
    }

    /**
     * 设置屏幕常亮时间
     */
    suspend fun setScreenTimeout(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[SCREEN_TIMEOUT_KEY] = minutes
        }
    }

    /**
     * 获取语言设置
     * "system" = 跟随系统
     * "en" = English
     * "zh" = 中文
     */
    val language: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: DEFAULT_LANGUAGE
    }

    /**
     * 设置语言
     */
    suspend fun setLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    /**
     * 获取终端字体设置
     */
    val terminalFont: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TERMINAL_FONT_KEY] ?: DEFAULT_TERMINAL_FONT
    }

    /**
     * 设置终端字体
     */
    suspend fun setTerminalFont(font: String) {
        context.dataStore.edit { preferences ->
            preferences[TERMINAL_FONT_KEY] = font
        }
    }

    /**
     * 获取终端字体大小设置 (sp)
     * 10 = 紧凑 (~68列)
     * 11 = 平衡 (~62列)
     * 12 = 默认 (~57列)
     * 13 = 舒适 (~52列)
     * 14 = 大字体 (~48列)
     */
    val terminalFontSize: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[TERMINAL_FONT_SIZE_KEY] ?: DEFAULT_TERMINAL_FONT_SIZE
    }

    /**
     * 设置终端字体大小
     */
    suspend fun setTerminalFontSize(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[TERMINAL_FONT_SIZE_KEY] = size
        }
    }

    /**
     * 获取虚拟键盘风格设置
     * "none" = 不显示虚拟键盘
     * "path" = Path 风格 (浮动按钮)
     * "termius" = Termius 风格 (底部工具栏)
     * "both" = 两种风格同时显示
     */
    val keyboardStyle: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEYBOARD_STYLE_KEY] ?: DEFAULT_KEYBOARD_STYLE
    }

    /**
     * 设置虚拟键盘风格
     */
    suspend fun setKeyboardStyle(style: String) {
        context.dataStore.edit { preferences ->
            preferences[KEYBOARD_STYLE_KEY] = style
        }
    }

    /**
     * 获取通知开关设置
     * true = 开启 Claude Code 完成/需要输入的通知
     * false = 关闭通知
     */
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED_KEY] ?: DEFAULT_NOTIFICATIONS_ENABLED
    }

    /**
     * 设置通知开关
     */
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    /**
     * 获取 Webhook 服务器开关设置
     * true = 启动 Webhook 服务器接收远程通知
     * false = 关闭 Webhook 服务器
     */
    val webhookServerEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[WEBHOOK_SERVER_ENABLED_KEY] ?: DEFAULT_WEBHOOK_SERVER_ENABLED
    }

    /**
     * 设置 Webhook 服务器开关
     */
    suspend fun setWebhookServerEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WEBHOOK_SERVER_ENABLED_KEY] = enabled
        }
    }

    /**
     * 获取智能解析开关设置
     * true = 开启智能解析，显示任务与对话的关联
     * false = 关闭智能解析，隐藏关联信息
     */
    val smartParsingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SMART_PARSING_ENABLED_KEY] ?: DEFAULT_SMART_PARSING_ENABLED
    }

    /**
     * 设置智能解析开关
     */
    suspend fun setSmartParsingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMART_PARSING_ENABLED_KEY] = enabled
        }
    }
}
