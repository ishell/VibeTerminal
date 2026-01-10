package com.vibe.terminal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.terminal.R
import com.vibe.terminal.data.preferences.UserPreferences
import com.vibe.terminal.domain.model.Machine
import com.vibe.terminal.ui.theme.StatusConnected

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToMachineEdit: (String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val webhookServerRunning by viewModel.webhookServerRunning.collectAsState()
    val webhookServerAddress by viewModel.webhookServerAddress.collectAsState()
    var showScreenTimeoutDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showKeyboardStyleDialog by remember { mutableStateOf(false) }
    var showWebhookConfigDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigateToMachineEdit(null) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_machine_desc))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.machines),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (uiState.machines.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.no_machines),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(uiState.machines, key = { it.id }) { machine ->
                    MachineCard(
                        machine = machine,
                        onEdit = { onNavigateToMachineEdit(machine.id) },
                        onDelete = { viewModel.deleteMachine(machine.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.appearance),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Language selection
            item {
                val currentLanguageLabel = when (uiState.language) {
                    UserPreferences.LANGUAGE_EN -> stringResource(R.string.language_en)
                    UserPreferences.LANGUAGE_ZH -> stringResource(R.string.language_zh)
                    else -> stringResource(R.string.language_system)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.language),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = currentLanguageLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Font selection
            item {
                val currentFontLabel = when (uiState.terminalFont) {
                    UserPreferences.FONT_JETBRAINS_MONO -> stringResource(R.string.font_jetbrains_mono)
                    UserPreferences.FONT_SYSTEM_MONO -> stringResource(R.string.font_system_mono)
                    else -> stringResource(R.string.font_iosevka)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFontDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FontDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.terminal_font),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = currentFontLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.theme),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.dark_default),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Font size selection
            item {
                val currentFontSizeLabel = when (uiState.terminalFontSize) {
                    UserPreferences.FONT_SIZE_COMPACT -> stringResource(R.string.font_size_compact)
                    UserPreferences.FONT_SIZE_BALANCED -> stringResource(R.string.font_size_balanced)
                    UserPreferences.FONT_SIZE_DEFAULT -> stringResource(R.string.font_size_default)
                    UserPreferences.FONT_SIZE_COMFORT -> stringResource(R.string.font_size_comfort)
                    UserPreferences.FONT_SIZE_LARGE -> stringResource(R.string.font_size_large)
                    else -> "${uiState.terminalFontSize}sp"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showFontSizeDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FontDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.terminal_font_size),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = currentFontSizeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.color_scheme),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Dark+",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Terminal Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.terminal),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                val currentTimeoutLabel = UserPreferences.SCREEN_TIMEOUT_OPTIONS
                    .find { it.first == uiState.screenTimeoutMinutes }?.second
                    ?: stringResource(R.string.system_default)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showScreenTimeoutDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenLockPortrait,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.screen_timeout),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = currentTimeoutLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Virtual keyboard style
            item {
                val currentKeyboardLabel = when (uiState.keyboardStyle) {
                    UserPreferences.KEYBOARD_STYLE_NONE -> stringResource(R.string.keyboard_style_none)
                    UserPreferences.KEYBOARD_STYLE_PATH -> stringResource(R.string.keyboard_style_path)
                    UserPreferences.KEYBOARD_STYLE_TERMIUS -> stringResource(R.string.keyboard_style_termius)
                    UserPreferences.KEYBOARD_STYLE_BOTH -> stringResource(R.string.keyboard_style_both)
                    else -> stringResource(R.string.keyboard_style_termius)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showKeyboardStyleDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.keyboard_style),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = currentKeyboardLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Notification toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.notifications),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.notifications_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.notificationsEnabled,
                            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                        )
                    }
                }
            }

            // Webhook Server toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Webhook,
                                contentDescription = null,
                                tint = if (webhookServerRunning) StatusConnected else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.webhook_server),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (webhookServerRunning) {
                                        webhookServerAddress ?: stringResource(R.string.webhook_server_running)
                                    } else {
                                        stringResource(R.string.webhook_server_desc)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (webhookServerRunning) StatusConnected else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.webhookServerEnabled,
                                onCheckedChange = { viewModel.setWebhookServerEnabled(it) }
                            )
                        }

                        // Show config button when server is running
                        if (webhookServerRunning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { showWebhookConfigDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.view_server_config))
                            }
                        }
                    }
                }
            }
        }
    }

    // Screen Timeout Dialog
    if (showScreenTimeoutDialog) {
        ScreenTimeoutDialog(
            currentValue = uiState.screenTimeoutMinutes,
            onDismiss = { showScreenTimeoutDialog = false },
            onSelect = { minutes ->
                viewModel.setScreenTimeout(minutes)
                showScreenTimeoutDialog = false
            }
        )
    }

    // Language Dialog
    if (showLanguageDialog) {
        LanguageDialog(
            currentValue = uiState.language,
            onDismiss = { showLanguageDialog = false },
            onSelect = { language ->
                viewModel.setLanguage(language)
                showLanguageDialog = false
            }
        )
    }

    // Font Dialog
    if (showFontDialog) {
        FontDialog(
            currentValue = uiState.terminalFont,
            onDismiss = { showFontDialog = false },
            onSelect = { font ->
                viewModel.setTerminalFont(font)
                showFontDialog = false
            }
        )
    }

    // Keyboard Style Dialog
    if (showKeyboardStyleDialog) {
        KeyboardStyleDialog(
            currentValue = uiState.keyboardStyle,
            onDismiss = { showKeyboardStyleDialog = false },
            onSelect = { style ->
                viewModel.setKeyboardStyle(style)
                showKeyboardStyleDialog = false
            }
        )
    }

    // Font Size Dialog
    if (showFontSizeDialog) {
        FontSizeDialog(
            currentValue = uiState.terminalFontSize,
            onDismiss = { showFontSizeDialog = false },
            onSelect = { size ->
                viewModel.setTerminalFontSize(size)
                showFontSizeDialog = false
            }
        )
    }

    // Webhook Config Dialog
    if (showWebhookConfigDialog) {
        WebhookConfigDialog(
            serverAddress = webhookServerAddress ?: "",
            settingsJson = viewModel.getSettingsJson(),
            onDismiss = { showWebhookConfigDialog = false }
        )
    }
}

@Composable
private fun MachineCard(
    machine: Machine,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = StatusConnected,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = machine.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = machine.displayAddress(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.auth_label, machine.authType.name.lowercase().replaceFirstChar { it.uppercase() }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit_desc)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_desc),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ScreenTimeoutDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screen_timeout)) },
        text = {
            Column {
                UserPreferences.SCREEN_TIMEOUT_OPTIONS.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == minutes,
                            onClick = { onSelect(minutes) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (currentValue == minutes) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LanguageDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        UserPreferences.LANGUAGE_SYSTEM to stringResource(R.string.language_system),
        UserPreferences.LANGUAGE_EN to stringResource(R.string.language_en),
        UserPreferences.LANGUAGE_ZH to stringResource(R.string.language_zh)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == value,
                            onClick = { onSelect(value) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (currentValue == value) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun FontDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    data class FontOption(
        val value: String,
        val label: String,
        val description: String
    )

    val options = listOf(
        FontOption(
            UserPreferences.FONT_IOSEVKA,
            stringResource(R.string.font_iosevka),
            stringResource(R.string.font_iosevka_desc)
        ),
        FontOption(
            UserPreferences.FONT_JETBRAINS_MONO,
            stringResource(R.string.font_jetbrains_mono),
            stringResource(R.string.font_jetbrains_mono_desc)
        ),
        FontOption(
            UserPreferences.FONT_SYSTEM_MONO,
            stringResource(R.string.font_system_mono),
            stringResource(R.string.font_system_mono_desc)
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.terminal_font)) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == option.value,
                            onClick = { onSelect(option.value) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currentValue == option.value) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun KeyboardStyleDialog(
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    data class KeyboardOption(
        val value: String,
        val label: String,
        val description: String
    )

    val options = listOf(
        KeyboardOption(
            UserPreferences.KEYBOARD_STYLE_NONE,
            stringResource(R.string.keyboard_style_none),
            stringResource(R.string.keyboard_style_none_desc)
        ),
        KeyboardOption(
            UserPreferences.KEYBOARD_STYLE_PATH,
            stringResource(R.string.keyboard_style_path),
            stringResource(R.string.keyboard_style_path_desc)
        ),
        KeyboardOption(
            UserPreferences.KEYBOARD_STYLE_TERMIUS,
            stringResource(R.string.keyboard_style_termius),
            stringResource(R.string.keyboard_style_termius_desc)
        ),
        KeyboardOption(
            UserPreferences.KEYBOARD_STYLE_BOTH,
            stringResource(R.string.keyboard_style_both),
            stringResource(R.string.keyboard_style_both_desc)
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_style)) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == option.value,
                            onClick = { onSelect(option.value) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currentValue == option.value) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun FontSizeDialog(
    currentValue: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    data class FontSizeOption(
        val value: Int,
        val label: String,
        val description: String
    )

    val options = listOf(
        FontSizeOption(
            UserPreferences.FONT_SIZE_COMPACT,
            stringResource(R.string.font_size_compact),
            stringResource(R.string.font_size_compact_desc)
        ),
        FontSizeOption(
            UserPreferences.FONT_SIZE_BALANCED,
            stringResource(R.string.font_size_balanced),
            stringResource(R.string.font_size_balanced_desc)
        ),
        FontSizeOption(
            UserPreferences.FONT_SIZE_DEFAULT,
            stringResource(R.string.font_size_default),
            stringResource(R.string.font_size_default_desc)
        ),
        FontSizeOption(
            UserPreferences.FONT_SIZE_COMFORT,
            stringResource(R.string.font_size_comfort),
            stringResource(R.string.font_size_comfort_desc)
        ),
        FontSizeOption(
            UserPreferences.FONT_SIZE_LARGE,
            stringResource(R.string.font_size_large),
            stringResource(R.string.font_size_large_desc)
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.terminal_font_size)) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.value) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentValue == option.value,
                            onClick = { onSelect(option.value) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currentValue == option.value) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun WebhookConfigDialog(
    serverAddress: String,
    settingsJson: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.webhook_config_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Server address
                Text(
                    text = stringResource(R.string.webhook_address_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = serverAddress,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Settings.json config
                Text(
                    text = stringResource(R.string.webhook_settings_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.webhook_settings_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.height(200.dp)
                ) {
                    Text(
                        text = settingsJson,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
