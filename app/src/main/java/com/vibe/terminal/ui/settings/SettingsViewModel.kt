package com.vibe.terminal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.repository.MachineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = machineRepository.getAllMachines()
        .map { machines ->
            SettingsUiState(
                machines = machines,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(isLoading = true)
        )

    fun deleteMachine(machineId: String) {
        viewModelScope.launch {
            machineRepository.deleteMachine(machineId)
        }
    }
}

data class SettingsUiState(
    val machines: List<Machine> = emptyList(),
    val isLoading: Boolean = false
)
