package com.vibe.terminal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.data.preferences.UserPreferences
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.repository.MachineRepository
import com.vibe.terminal.notification.WebhookServerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val machineRepository: MachineRepository,
    private val userPreferences: UserPreferences,
    private val webhookServerManager: WebhookServerManager
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        machineRepository.getAllMachines(),
        userPreferences.screenTimeout,
        userPreferences.language,
        userPreferences.terminalFont,
        userPreferences.terminalFontSize,
        userPreferences.keyboardStyle,
        userPreferences.notificationsEnabled,
        userPreferences.webhookServerEnabled
    ) { flows ->
        val machines = flows[0] as List<*>
        val screenTimeout = flows[1] as Int
        val language = flows[2] as String
        val terminalFont = flows[3] as String
        val terminalFontSize = flows[4] as Int
        val keyboardStyle = flows[5] as String
        val notificationsEnabled = flows[6] as Boolean
        val webhookServerEnabled = flows[7] as Boolean

        @Suppress("UNCHECKED_CAST")
        SettingsUiState(
            machines = machines as List<Machine>,
            screenTimeoutMinutes = screenTimeout,
            language = language,
            terminalFont = terminalFont,
            terminalFontSize = terminalFontSize,
            keyboardStyle = keyboardStyle,
            notificationsEnabled = notificationsEnabled,
            webhookServerEnabled = webhookServerEnabled,
            webhookServerAddress = webhookServerManager.serverAddress.value,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoading = true)
    )

    // Webhook server state
    val webhookServerAddress: StateFlow<String?> = webhookServerManager.serverAddress
    val webhookServerRunning: StateFlow<Boolean> = webhookServerManager.isRunning

    fun deleteMachine(machineId: String) {
        viewModelScope.launch {
            machineRepository.deleteMachine(machineId)
        }
    }

    fun setScreenTimeout(minutes: Int) {
        viewModelScope.launch {
            userPreferences.setScreenTimeout(minutes)
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch {
            userPreferences.setLanguage(language)
        }
    }

    fun setTerminalFont(font: String) {
        viewModelScope.launch {
            userPreferences.setTerminalFont(font)
        }
    }

    fun setKeyboardStyle(style: String) {
        viewModelScope.launch {
            userPreferences.setKeyboardStyle(style)
        }
    }

    fun setTerminalFontSize(size: Int) {
        viewModelScope.launch {
            userPreferences.setTerminalFontSize(size)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setNotificationsEnabled(enabled)
        }
    }

    fun setWebhookServerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setWebhookServerEnabled(enabled)
            if (enabled) {
                webhookServerManager.startServer()
            } else {
                webhookServerManager.stopServer()
            }
        }
    }

    fun getHookScript(): String = webhookServerManager.generateHookScript()

    fun getSettingsJson(): String = webhookServerManager.generateSettingsJson()
}

data class SettingsUiState(
    val machines: List<Machine> = emptyList(),
    val screenTimeoutMinutes: Int = 0,
    val language: String = UserPreferences.LANGUAGE_SYSTEM,
    val terminalFont: String = UserPreferences.DEFAULT_TERMINAL_FONT,
    val terminalFontSize: Int = UserPreferences.DEFAULT_TERMINAL_FONT_SIZE,
    val keyboardStyle: String = UserPreferences.DEFAULT_KEYBOARD_STYLE,
    val notificationsEnabled: Boolean = UserPreferences.DEFAULT_NOTIFICATIONS_ENABLED,
    val webhookServerEnabled: Boolean = UserPreferences.DEFAULT_WEBHOOK_SERVER_ENABLED,
    val webhookServerAddress: String? = null,
    val isLoading: Boolean = false
)
