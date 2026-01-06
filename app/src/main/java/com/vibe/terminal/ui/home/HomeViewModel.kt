package com.vibe.terminal.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.model.Project
import com.vibe.terminal.domain.repository.MachineRepository
import com.vibe.terminal.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val machineRepository: MachineRepository
) : ViewModel() {

    private val _projects = projectRepository.getAllProjects()
    private val _machines = machineRepository.getAllMachines()

    val uiState: StateFlow<HomeUiState> = combine(_projects, _machines) { projects, machines ->
        HomeUiState(
            projects = projects,
            machines = machines,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    private val _showNewProjectDialog = MutableStateFlow(false)
    val showNewProjectDialog: StateFlow<Boolean> = _showNewProjectDialog.asStateFlow()

    fun showNewProjectDialog() {
        _showNewProjectDialog.value = true
    }

    fun hideNewProjectDialog() {
        _showNewProjectDialog.value = false
    }

    fun createProject(name: String, machineId: String, zellijSession: String) {
        viewModelScope.launch {
            val project = Project(
                id = UUID.randomUUID().toString(),
                name = name,
                machineId = machineId,
                zellijSession = zellijSession
            )
            projectRepository.saveProject(project)
            hideNewProjectDialog()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
        }
    }
}

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val machines: List<Machine> = emptyList(),
    val isLoading: Boolean = false
)
