package com.vibe.terminal.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

        // 屏幕常亮时间选项（分钟）
        val SCREEN_TIMEOUT_OPTIONS = listOf(
            0 to "System default",
            5 to "5 minutes",
            10 to "10 minutes",
            30 to "30 minutes",
            60 to "1 hour",
            -1 to "Always on"
        )

        const val DEFAULT_SCREEN_TIMEOUT = 0  // 默认跟随系统
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
}
