package com.vibe.terminal.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.terminal.domain.model.Machine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineEditScreen(
    viewModel: MachineEditViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 读取文件内容
            try {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    viewModel.updatePrivateKey(content)
                }
            } catch (e: Exception) {
                // 处理错误
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.name.isEmpty()) "New Machine" else "Edit Machine") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("Name") },
                placeholder = { Text("My Dev Machine") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = uiState.host,
                    onValueChange = viewModel::updateHost,
                    label = { Text("Host") },
                    placeholder = { Text("192.168.1.100 or hostname.ts.net") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedTextField(
                    value = uiState.port,
                    onValueChange = viewModel::updatePort,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Authentication",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = uiState.authType == Machine.AuthType.PASSWORD,
                        onClick = { viewModel.updateAuthType(Machine.AuthType.PASSWORD) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = uiState.authType == Machine.AuthType.PASSWORD,
                    onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Password")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = uiState.authType == Machine.AuthType.SSH_KEY,
                        onClick = { viewModel.updateAuthType(Machine.AuthType.SSH_KEY) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = uiState.authType == Machine.AuthType.SSH_KEY,
                    onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("SSH Key")
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState.authType) {
                Machine.AuthType.PASSWORD -> {
                    PasswordField(
                        value = uiState.password,
                        onValueChange = viewModel::updatePassword,
                        label = "Password"
                    )
                }
                Machine.AuthType.SSH_KEY -> {
                    // 选择文件按钮
                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Key File")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 显示已加载的密钥状态或手动输入
                    OutlinedTextField(
                        value = uiState.privateKey,
                        onValueChange = viewModel::updatePrivateKey,
                        label = { Text("Private Key") },
                        placeholder = { Text("Select file or paste key here...") },
                        supportingText = {
                            if (uiState.privateKey.isNotBlank()) {
                                val lines = uiState.privateKey.lines().size
                                Text("$lines lines loaded")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        maxLines = 10
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PasswordField(
                        value = uiState.passphrase,
                        onValueChange = viewModel::updatePassphrase,
                        label = "Passphrase (optional)"
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Test Connection Result
            when (val testStatus = uiState.testStatus) {
                is TestConnectionStatus.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Connection successful!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                is TestConnectionStatus.Failed -> {
                    var showDetails by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    testStatus.errorInfo.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                testStatus.errorInfo.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (testStatus.errorInfo.suggestions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                testStatus.errorInfo.suggestions.forEach { suggestion ->
                                    Text(
                                        "• $suggestion",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            // Expandable technical details
                            if (testStatus.errorInfo.technicalDetails.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDetails = !showDetails },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Technical Details",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (showDetails) "Hide" else "Show",
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }

                                if (showDetails) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        testStatus.errorInfo.technicalDetails,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                else -> {}
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    enabled = uiState.isValid && uiState.testStatus !is TestConnectionStatus.Testing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (uiState.testStatus is TestConnectionStatus.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...")
                    } else {
                        Text("Test Connection")
                    }
                }

                Button(
                    onClick = { viewModel.save(onNavigateBack) },
                    enabled = uiState.isValid && !uiState.isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.isSaving) "Saving..." else "Save")
                }
            }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide" else "Show"
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
