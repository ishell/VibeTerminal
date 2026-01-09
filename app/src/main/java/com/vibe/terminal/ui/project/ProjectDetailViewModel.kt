package com.vibe.terminal.ui.project

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.data.conversation.ConversationFetcher
import com.vibe.terminal.data.ssh.HostKeyManager
import com.vibe.terminal.data.ssh.SshConfig
import com.vibe.terminal.domain.model.ConversationSession
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.model.Project
import com.vibe.terminal.domain.repository.MachineRepository
import com.vibe.terminal.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val machineRepository: MachineRepository,
    private val conversationFetcher: ConversationFetcher,
    private val hostKeyManager: HostKeyManager
) : ViewModel() {

    private val projectId: String = savedStateHandle["projectId"] ?: ""

    private val _uiState = MutableStateFlow(ProjectDetailUiState())
    val uiState: StateFlow<ProjectDetailUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            val projects = projectRepository.getAllProjects().first()
            val project = projects.find { it.id == projectId }

            if (project != null) {
                val machines = machineRepository.getAllMachines().first()
                val machine = machines.find { it.id == project.machineId }

                _uiState.update {
                    it.copy(
                        project = project,
                        machine = machine
                    )
                }

                // 项目加载完成后自动加载对话历史
                if (machine != null) {
                    loadConversationHistoryInternal(project, machine)
                }
            }
        }
    }

    fun loadConversationHistory() {
        val project = _uiState.value.project ?: return
        val machine = _uiState.value.machine ?: return
        loadConversationHistoryInternal(project, machine)
    }

    private fun loadConversationHistoryInternal(project: Project, machine: Machine) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, loadingProgress = null) }

            val sshConfig = createSshConfig(machine)

            val filesResult = conversationFetcher.listConversationFiles(
                config = sshConfig,
                projectPath = project.workingDirectory
            )

            filesResult.fold(
                onSuccess = { files ->
                    if (files.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                sessions = emptyList(),
                                lastUpdated = System.currentTimeMillis(),
                                loadingProgress = null
                            )
                        }
                        return@fold
                    }

                    val filesToLoad = files.take(10)
                    val total = filesToLoad.size
                    val loadedSessions = mutableListOf<ConversationSession>()
                    var failedCount = 0

                    // 边加载边显示
                    filesToLoad.forEachIndexed { index, fileInfo ->
                        // 更新进度
                        _uiState.update {
                            it.copy(
                                loadingProgress = LoadingProgress(
                                    current = index + 1,
                                    total = total,
                                    currentFileName = fileInfo.sessionId,
                                    failedCount = failedCount
                                )
                            )
                        }

                        val sessionResult = conversationFetcher.fetchAndParseConversation(
                            config = sshConfig,
                            fileInfo = fileInfo,
                            projectId = project.id
                        )
                        sessionResult.fold(
                            onSuccess = { session ->
                                loadedSessions.add(session)
                                // 每加载完一个就更新 UI（边加载边显示）
                                // 创建新的列表实例确保 Compose 能检测到变化
                                val sortedSessions = loadedSessions.toList().sortedByDescending { s -> s.startTime }
                                _uiState.update {
                                    it.copy(
                                        sessions = sortedSessions,
                                        lastUpdated = System.currentTimeMillis()
                                    )
                                }
                            },
                            onFailure = {
                                failedCount++
                                // 更新失败计数
                                _uiState.update {
                                    it.copy(
                                        loadingProgress = it.loadingProgress?.copy(failedCount = failedCount)
                                    )
                                }
                            }
                        )
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadingProgress = null
                        )
                    }

                    // 启动自动刷新
                    startAutoRefresh()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load conversation history",
                            loadingProgress = null
                        )
                    }
                }
            )
        }
    }

    fun refreshHistory() {
        val project = _uiState.value.project ?: return
        val machine = _uiState.value.machine ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            val sshConfig = createSshConfig(machine)

            val filesResult = conversationFetcher.listConversationFiles(
                config = sshConfig,
                projectPath = project.workingDirectory
            )

            filesResult.fold(
                onSuccess = { files ->
                    val sessions = mutableListOf<ConversationSession>()
                    for (fileInfo in files.take(10)) {
                        val sessionResult = conversationFetcher.fetchAndParseConversation(
                            config = sshConfig,
                            fileInfo = fileInfo,
                            projectId = project.id
                        )
                        sessionResult.onSuccess { session ->
                            sessions.add(session)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            sessions = sessions.sortedByDescending { s -> s.startTime },
                            lastUpdated = System.currentTimeMillis()
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            )
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(10_000) // 10秒刷新一次
                refreshHistory()
            }
        }
    }

    private suspend fun createSshConfig(machine: Machine): SshConfig {
        val fingerprint = hostKeyManager.getStoredFingerprint(machine.host, machine.port)
        return SshConfig(
            host = machine.host,
            port = machine.port,
            username = machine.username,
            authMethod = when (machine.authType) {
                Machine.AuthType.PASSWORD -> SshConfig.AuthMethod.Password(machine.password ?: "")
                Machine.AuthType.SSH_KEY -> SshConfig.AuthMethod.PublicKey(
                    privateKey = machine.privateKey ?: "",
                    passphrase = machine.passphrase
                )
            },
            trustedFingerprint = fingerprint
        )
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}

data class ProjectDetailUiState(
    val project: Project? = null,
    val machine: Machine? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val sessions: List<ConversationSession> = emptyList(),
    val error: String? = null,
    val lastUpdated: Long = 0L,
    // 加载进度
    val loadingProgress: LoadingProgress? = null
)

/**
 * 加载进度信息
 */
data class LoadingProgress(
    val current: Int,           // 当前正在加载第几个
    val total: Int,             // 总共多少个文件
    val currentFileName: String = "",  // 当前正在加载的文件名
    val failedCount: Int = 0    // 失败的文件数量
)
