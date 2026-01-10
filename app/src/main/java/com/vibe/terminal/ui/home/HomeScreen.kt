package com.vibe.terminal.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.terminal.data.ssh.HostKeyStatus
import com.vibe.terminal.domain.model.AssistantType
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.domain.model.Project
import com.vibe.terminal.ui.theme.StatusConnected

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToProjectDetail: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
    onNavigateToMachineEdit: (String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val showNewProjectDialog by viewModel.showNewProjectDialog.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VibeTerminal") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (uiState.machines.isEmpty()) {
                        onNavigateToMachineEdit(null)
                    } else {
                        viewModel.showNewProjectDialog()
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Project")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.projects.isEmpty() && uiState.machines.isEmpty() -> {
                    EmptyState(
                        onAddMachine = { onNavigateToMachineEdit(null) }
                    )
                }
                uiState.projects.isEmpty() -> {
                    EmptyProjectState(
                        onAddProject = { viewModel.showNewProjectDialog() }
                    )
                }
                else -> {
                    ProjectList(
                        projects = uiState.projects,
                        machines = uiState.machines,
                        onProjectClick = onNavigateToProjectDetail,
                        onProjectTerminal = onNavigateToTerminal,
                        onProjectDelete = viewModel::deleteProject
                    )
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            machines = uiState.machines,
            dialogState = dialogState,
            onFetchSessions = viewModel::fetchZellijSessions,
            onAcceptHostKey = viewModel::acceptHostKeyAndFetchSessions,
            onRejectHostKey = viewModel::rejectHostKey,
            onDismiss = viewModel::hideNewProjectDialog,
            onCreate = viewModel::createProject
        )
    }
}

@Composable
private fun EmptyState(
    onAddMachine: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Computer,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No machines configured",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add a development machine to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddMachine) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Machine")
        }
    }
}

@Composable
private fun EmptyProjectState(
    onAddProject: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No projects yet",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Create a project to connect to your dev machine",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddProject) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Project")
        }
    }
}

@Composable
private fun ProjectList(
    projects: List<Project>,
    machines: List<Machine>,
    onProjectClick: (String) -> Unit,
    onProjectTerminal: (String) -> Unit,
    onProjectDelete: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(projects, key = { it.id }) { project ->
            val machine = machines.find { it.id == project.machineId }
            ProjectCard(
                project = project,
                machine = machine,
                onClick = { onProjectClick(project.id) },
                onTerminal = { onProjectTerminal(project.id) },
                onDelete = { onProjectDelete(project.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectCard(
    project: Project,
    machine: Machine?,
    onClick: () -> Unit,
    onTerminal: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        onClick = onClick  // 点击卡片进入项目详情
    ) {
        Column {
            // 主要内容行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = StatusConnected,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = machine?.displayAddress() ?: "Unknown machine",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Session: ${project.zellijSession}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 终端按钮
                IconButton(onClick = onTerminal) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = "Open Terminal",
                        tint = StatusConnected
                    )
                }

                // 删除按钮
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProjectDialog(
    machines: List<Machine>,
    dialogState: NewProjectDialogState,
    onFetchSessions: (Machine) -> Unit,
    onAcceptHostKey: () -> Unit,
    onRejectHostKey: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (name: String, machineId: String, zellijSession: String, workingDirectory: String, assistantType: AssistantType) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var zellijSession by remember { mutableStateOf("") }
    var workingDirectory by remember { mutableStateOf("~/VibeCode") }  // 默认目录
    var selectedMachine by remember { mutableStateOf(machines.firstOrNull()) }
    var machineExpanded by remember { mutableStateOf(false) }
    var sessionExpanded by remember { mutableStateOf(false) }

    // Show host key verification dialog if needed
    if (dialogState.hostKeyStatus != null) {
        HostKeyVerificationDialog(
            status = dialogState.hostKeyStatus,
            hostAddress = dialogState.pendingMachine?.let { "${it.host}:${it.port}" } ?: "",
            onAccept = onAcceptHostKey,
            onReject = onRejectHostKey
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Machine 选择
                ExposedDropdownMenuBox(
                    expanded = machineExpanded,
                    onExpandedChange = { machineExpanded = !machineExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedMachine?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Machine") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = machineExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = machineExpanded,
                        onDismissRequest = { machineExpanded = false }
                    ) {
                        machines.forEach { machine ->
                            DropdownMenuItem(
                                text = { Text(machine.name) },
                                onClick = {
                                    selectedMachine = machine
                                    machineExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Zellij Session with fetch button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = sessionExpanded && dialogState.zellijSessions.isNotEmpty(),
                        onExpandedChange = {
                            if (dialogState.zellijSessions.isNotEmpty()) {
                                sessionExpanded = !sessionExpanded
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = zellijSession,
                            onValueChange = { zellijSession = it },
                            label = { Text("Zellij Session") },
                            singleLine = true,
                            trailingIcon = {
                                if (dialogState.zellijSessions.isNotEmpty()) {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = sessionExpanded)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        if (dialogState.zellijSessions.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = sessionExpanded,
                                onDismissRequest = { sessionExpanded = false }
                            ) {
                                dialogState.zellijSessions.forEach { session ->
                                    val sessionCwd = dialogState.sessionWorkingDirs[session]
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(session)
                                                if (sessionCwd != null) {
                                                    Text(
                                                        text = sessionCwd,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            zellijSession = session
                                            // Auto-fill working directory if available, otherwise keep default
                                            if (!sessionCwd.isNullOrBlank()) {
                                                workingDirectory = sessionCwd
                                            }
                                            sessionExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { selectedMachine?.let { onFetchSessions(it) } },
                        enabled = selectedMachine != null && !dialogState.isLoadingSessions
                    ) {
                        if (dialogState.isLoadingSessions) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Fetch Sessions"
                            )
                        }
                    }
                }

                // Show session count or error
                if (dialogState.zellijSessions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${dialogState.zellijSessions.size} session(s) found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                dialogState.sessionError?.let { error ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Working Directory
                OutlinedTextField(
                    value = workingDirectory,
                    onValueChange = { workingDirectory = it },
                    label = { Text("Working Directory") },
                    placeholder = { Text("~/VibeCode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // AI Assistant 自动检测提示
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI Assistant will be auto-detected (Claude Code / OpenCode / Codex)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedMachine?.let {
                        // 使用 BOTH 自动检测两种格式
                        onCreate(name, it.id, zellijSession, workingDirectory, AssistantType.BOTH)
                    }
                },
                enabled = name.isNotBlank() && zellijSession.isNotBlank() && workingDirectory.isNotBlank() && selectedMachine != null
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Host Key Verification Dialog
 */
@Composable
private fun HostKeyVerificationDialog(
    status: HostKeyStatus,
    hostAddress: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val isChanged = status is HostKeyStatus.Changed

    AlertDialog(
        onDismissRequest = onReject,
        icon = {
            Icon(
                imageVector = if (isChanged) Icons.Default.Warning else Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (isChanged) MaterialTheme.colorScheme.error else Color(0xFF2196F3)
            )
        },
        title = {
            Text(
                text = if (isChanged) "主机密钥已更改" else "未知主机",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                if (isChanged) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "警告：此主机的密钥已更改！这可能表示存在安全风险（中间人攻击）。\n\n请确认此更改是预期的（如服务器重装）后再继续。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "首次连接到此主机，请验证以下指纹是否正确：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Host address
                Text(
                    text = "主机地址",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = hostAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Fingerprint info
                when (status) {
                    is HostKeyStatus.Unknown -> {
                        Text(
                            text = "算法",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = status.algorithm,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "指纹",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = status.fingerprint,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    is HostKeyStatus.Changed -> {
                        Text(
                            text = "原指纹",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = status.oldFingerprint,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "新指纹",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF4CAF50)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = status.newFingerprint,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                    is HostKeyStatus.Verified -> {
                        // Shouldn't happen in this dialog
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = if (isChanged) {
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    androidx.compose.material3.ButtonDefaults.buttonColors()
                }
            ) {
                Text(if (isChanged) "接受新密钥" else "信任此主机")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("拒绝")
            }
        }
    )
}
