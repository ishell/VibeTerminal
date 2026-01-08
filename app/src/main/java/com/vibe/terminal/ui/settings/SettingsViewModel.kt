package com.vibe.terminal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.data.preferences.UserPreferences
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.repository.MachineRepository
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
    private val userPreferences: UserPreferences
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        machineRepository.getAllMachines(),
        userPreferences.screenTimeout,
        userPreferences.language
    ) { machines, screenTimeout, language ->
        SettingsUiState(
            machines = machines,
            screenTimeoutMinutes = screenTimeout,
            language = language,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoading = true)
    )

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
}

data class SettingsUiState(
    val machines: List<Machine> = emptyList(),
    val screenTimeoutMinutes: Int = 0,
    val language: String = UserPreferences.LANGUAGE_SYSTEM,
    val isLoading: Boolean = false
)
