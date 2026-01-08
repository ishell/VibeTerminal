package com.vibe.terminal.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.terminal.data.conversation.ConversationFetcher
import com.vibe.terminal.data.ssh.SshConfig
import com.vibe.terminal.data.ssh.SshConnectionPool
import com.vibe.terminal.domain.model.ConversationSession
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.model.Project
import com.vibe.terminal.domain.repository.MachineRepository
import com.vibe.terminal.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val machineRepository: MachineRepository,
    private val conversationFetcher: ConversationFetcher,
    private val sshConnectionPool: SshConnectionPool
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

    private val _dialogState = MutableStateFlow(NewProjectDialogState())
    val dialogState: StateFlow<NewProjectDialogState> = _dialogState.asStateFlow()

    private val _showNewProjectDialog = MutableStateFlow(false)
    val showNewProjectDialog: StateFlow<Boolean> = _showNewProjectDialog.asStateFlow()

    // 对话历史状态 (projectId -> ConversationHistoryState)
    private val _conversationStates = MutableStateFlow<Map<String, ConversationHistoryState>>(emptyMap())
    val conversationStates: StateFlow<Map<String, ConversationHistoryState>> = _conversationStates.asStateFlow()

    // 自动刷新 Jobs (projectId -> Job)
    private val autoRefreshJobs = mutableMapOf<String, Job>()

    fun showNewProjectDialog() {
        _dialogState.value = NewProjectDialogState()
        _showNewProjectDialog.value = true
    }

    fun hideNewProjectDialog() {
        _showNewProjectDialog.value = false
        _dialogState.value = NewProjectDialogState()
    }

    fun fetchZellijSessions(machine: Machine) {
        viewModelScope.launch {
            _dialogState.update { it.copy(isLoadingSessions = true, sessionError = null) }

            val sshConfig = SshConfig(
                host = machine.host,
                port = machine.port,
                username = machine.username,
                authMethod = when (machine.authType) {
                    Machine.AuthType.PASSWORD -> SshConfig.AuthMethod.Password(machine.password ?: "")
                    Machine.AuthType.SSH_KEY -> SshConfig.AuthMethod.PublicKey(
                        privateKey = machine.privateKey ?: "",
                        passphrase = machine.passphrase
                    )
                }
            )

            val result = withContext(Dispatchers.IO) {
                try {
                    // 使用连接池执行命令
                    val listOutput = sshConnectionPool.executeCommand(
                        sshConfig,
                        "zellij list-sessions -n 2>/dev/null || zellij list-sessions 2>/dev/null || echo ''"
                    ).getOrThrow()

                    // Parse session names
                    val ansiRegex = Regex("\u001B\\[[0-9;]*[a-zA-Z]")
                    val sessions = listOutput.lines()
                        .map { line -> ansiRegex.replace(line, "").trim() }
                        .filter { it.isNotBlank() && !it.contains("No active sessions") }
                        .map { line ->
                            line.split(" ").firstOrNull()?.trim()?.removeSuffix("(current)") ?: line
                        }
                        .filter { it.isNotBlank() && !it.startsWith("[") }
                        .distinct()

                    // Get working directory for each session
                    val sessionWorkingDirs = mutableMapOf<String, String>()
                    for (sessionName in sessions) {
                        try {
                            val cwdOutput = sshConnectionPool.executeCommand(
                                sshConfig,
                                "zellij -s '$sessionName' action dump-layout 2>/dev/null | grep -m1 'cwd \"' | sed 's/.*cwd \"\\([^\"]*\\)\".*/\\1/'",
                                timeoutSeconds = 5
                            ).getOrNull()

                            val cwd = cwdOutput?.trim() ?: ""
                            if (cwd.isNotBlank() && cwd.startsWith("/")) {
                                sessionWorkingDirs[sessionName] = cwd
                            }
                        } catch (_: Exception) {
                            // Ignore errors for individual sessions
                        }
                    }

                    Result.success(Pair(sessions, sessionWorkingDirs))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            result.fold(
                onSuccess = { (sessions, workingDirs) ->
                    _dialogState.update {
                        it.copy(
                            zellijSessions = sessions,
                            sessionWorkingDirs = workingDirs,
                            isLoadingSessions = false,
                            sessionError = null
                        )
                    }
                },
                onFailure = { error ->
                    _dialogState.update {
                        it.copy(
                            isLoadingSessions = false,
                            sessionError = error.message ?: "Failed to fetch sessions"
                        )
                    }
                }
            )
        }
    }

    fun createProject(name: String, machineId: String, zellijSession: String, workingDirectory: String) {
        viewModelScope.launch {
            val project = Project(
                id = UUID.randomUUID().toString(),
                name = name,
                machineId = machineId,
                zellijSession = zellijSession,
                workingDirectory = workingDirectory
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

    /**
     * 获取项目的对话历史
     */
    fun fetchConversationHistory(project: Project, isRefresh: Boolean = false) {
        val machine = uiState.value.machines.find { it.id == project.machineId } ?: return

        viewModelScope.launch {
            // 设置加载状态
            _conversationStates.update { states ->
                val existing = states[project.id]
                states + (project.id to (existing?.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh
                ) ?: ConversationHistoryState(isLoading = true)))
            }

            val sshConfig = SshConfig(
                host = machine.host,
                port = machine.port,
                username = machine.username,
                authMethod = when (machine.authType) {
                    Machine.AuthType.PASSWORD -> SshConfig.AuthMethod.Password(machine.password ?: "")
                    Machine.AuthType.SSH_KEY -> SshConfig.AuthMethod.PublicKey(
                        privateKey = machine.privateKey ?: "",
                        passphrase = machine.passphrase
                    )
                }
            )

            // 获取对话文件列表
            val filesResult = conversationFetcher.listConversationFiles(
                config = sshConfig,
                projectPath = project.workingDirectory
            )

            filesResult.fold(
                onSuccess = { files ->
                    if (files.isEmpty()) {
                        _conversationStates.update { states ->
                            states + (project.id to ConversationHistoryState(
                                isLoading = false,
                                isRefreshing = false,
                                sessions = emptyList(),
                                lastUpdated = System.currentTimeMillis()
                            ))
                        }
                        return@fold
                    }

                    // 获取最近的几个对话文件 (最多5个)
                    val sessions = mutableListOf<ConversationSession>()
                    for (fileInfo in files.take(5)) {
                        val sessionResult = conversationFetcher.fetchAndParseConversation(
                            config = sshConfig,
                            fileInfo = fileInfo
                        )
                        sessionResult.onSuccess { session ->
                            sessions.add(session)
                        }
                    }

                    _conversationStates.update { states ->
                        states + (project.id to ConversationHistoryState(
                            isLoading = false,
                            isRefreshing = false,
                            sessions = sessions.sortedByDescending { it.startTime },
                            lastUpdated = System.currentTimeMillis()
                        ))
                    }

                    // 首次获取成功后启动自动刷新
                    if (!isRefresh) {
                        startAutoRefresh(project)
                    }
                },
                onFailure = { error ->
                    _conversationStates.update { states ->
                        val existing = states[project.id]
                        states + (project.id to ConversationHistoryState(
                            isLoading = false,
                            isRefreshing = false,
                            sessions = existing?.sessions ?: emptyList(),
                            error = error.message ?: "Failed to fetch conversation history",
                            lastUpdated = existing?.lastUpdated ?: 0L
                        ))
                    }
                }
            )
        }
    }

    /**
     * 启动自动刷新
     */
    private fun startAutoRefresh(project: Project) {
        // 取消之前的刷新任务
        autoRefreshJobs[project.id]?.cancel()

        // 启动新的自动刷新任务 (每10秒刷新一次)
        autoRefreshJobs[project.id] = viewModelScope.launch {
            while (true) {
                delay(10_000) // 10秒
                // 检查是否还在显示历史
                if (_conversationStates.value.containsKey(project.id)) {
                    fetchConversationHistory(project, isRefresh = true)
                } else {
                    break
                }
            }
        }
    }

    /**
     * 手动刷新对话历史
     */
    fun refreshConversationHistory(project: Project) {
        fetchConversationHistory(project, isRefresh = true)
    }

    /**
     * 清除项目的对话历史状态
     */
    fun clearConversationHistory(projectId: String) {
        // 取消自动刷新
        autoRefreshJobs[projectId]?.cancel()
        autoRefreshJobs.remove(projectId)

        _conversationStates.update { states ->
            states - projectId
        }
    }
}

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val machines: List<Machine> = emptyList(),
    val isLoading: Boolean = false
)

data class NewProjectDialogState(
    val zellijSessions: List<String> = emptyList(),
    val sessionWorkingDirs: Map<String, String> = emptyMap(),  // session -> working directory
    val isLoadingSessions: Boolean = false,
    val sessionError: String? = null
)

data class ConversationHistoryState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val sessions: List<ConversationSession> = emptyList(),
    val error: String? = null,
    val lastUpdated: Long = 0L
)
