package com.podcast.app.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.podcast.app.api.claude.LLMTestResult
import com.podcast.app.privacy.OperationalMode
import com.podcast.app.privacy.PrivacyPreset
import com.podcast.app.sync.SyncInterval
import com.podcast.app.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val syncSettings by viewModel.syncSettings.collectAsState()
    val operationalMode by viewModel.operationalMode.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    // Claude API state
    val claudeApiKey by viewModel.claudeApiKey.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    val connectionTestMessage by viewModel.connectionTestMessage.collectAsState()

    // LLM Test state (GH#31)
    val llmTestResult by viewModel.llmTestResult.collectAsState()
    val isRunningLlmTest by viewModel.isRunningLlmTest.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar when sync message changes
    LaunchedEffect(syncMessage) {
        syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    // Refresh permission state when returning to this screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("settings_screen")
        ) {
            // Status card
            StatusCard(operationalMode = operationalMode)

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy presets
            SectionHeader("Privacy Presets")
            PrivacyPreset.entries.forEach { preset ->
                PresetItem(
                    preset = preset,
                    isSelected = isPresetSelected(settings, preset),
                    onClick = { viewModel.applyPreset(preset) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Network settings
            SectionHeader("Network")
            SettingSwitch(
                title = "Enable Network",
                description = "Master toggle for all network operations",
                checked = settings.networkEnabled,
                onCheckedChange = { viewModel.updateNetworkEnabled(it) }
            )

            if (settings.networkEnabled) {
                SettingSwitch(
                    title = "Podcast Search",
                    description = "Search and discover new podcasts",
                    checked = settings.allowPodcastSearch,
                    onCheckedChange = { viewModel.updatePodcastSearch(it) }
                )
                SettingSwitch(
                    title = "Feed Updates",
                    description = "Check for new episodes",
                    checked = settings.allowFeedUpdates,
                    onCheckedChange = { viewModel.updateFeedUpdates(it) }
                )
                SettingSwitch(
                    title = "Audio Streaming",
                    description = "Stream without downloading",
                    checked = settings.allowAudioStreaming,
                    onCheckedChange = { viewModel.updateAudioStreaming(it) }
                )
                SettingSwitch(
                    title = "Image Loading",
                    description = "Load podcast artwork",
                    checked = settings.allowImageLoading,
                    onCheckedChange = { viewModel.updateImageLoading(it) }
                )
                SettingSwitch(
                    title = "Background Sync",
                    description = "Update feeds while app is closed",
                    checked = settings.allowBackgroundSync,
                    onCheckedChange = { viewModel.updateBackgroundSync(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI settings
            SectionHeader("AI Features")
            SettingSwitch(
                title = "Local AI (Ollama)",
                description = "Use Ollama via Termux for voice commands",
                checked = settings.allowOllamaIntegration,
                onCheckedChange = { viewModel.updateOllamaIntegration(it) }
            )
            SettingSwitch(
                title = "Claude API",
                description = "Use cloud AI for recommendations",
                checked = settings.allowClaudeApi,
                onCheckedChange = { viewModel.updateClaudeApi(it) }
            )

            // Claude API configuration when enabled
            if (settings.allowClaudeApi) {
                ClaudeApiConfiguration(
                    apiKey = claudeApiKey,
                    onApiKeyChange = { viewModel.updateClaudeApiKey(it) },
                    onClearApiKey = { viewModel.clearClaudeApiKey() },
                    onTestConnection = { viewModel.testClaudeConnection() },
                    isTestingConnection = isTestingConnection,
                    connectionTestResult = connectionTestResult,
                    connectionTestMessage = connectionTestMessage,
                    // GH#31: Enhanced LLM test
                    onTestWithQueries = { viewModel.testClaudeWithQueries() },
                    isRunningLlmTest = isRunningLlmTest,
                    llmTestResult = llmTestResult
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download settings
            SectionHeader("Downloads")
            NavigationItem(
                title = "Download Manager",
                description = "View and manage downloaded episodes",
                icon = Icons.Filled.Download,
                onClick = { navController.navigate(Screen.Downloads.route) },
                testTag = "download_manager_item"
            )
            SettingSwitch(
                title = "Wi-Fi Only Downloads",
                description = "Only auto-download on Wi-Fi",
                checked = settings.autoDownloadOnWifiOnly,
                onCheckedChange = { viewModel.updateAutoDownloadOnWifiOnly(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Storage settings
            SectionHeader("Storage")
            SettingSwitch(
                title = "Auto-delete old episodes",
                description = "Remove downloaded episodes after retention period",
                checked = settings.autoDeleteEnabled,
                onCheckedChange = { viewModel.updateAutoDeleteEnabled(it) }
            )

            if (settings.autoDeleteEnabled) {
                RetentionPeriodSelector(
                    currentDays = settings.downloadRetentionDays,
                    onDaysChanged = { viewModel.updateRetentionDays(it) }
                )

                SettingSwitch(
                    title = "Only delete played episodes",
                    description = "Preserve unplayed downloads",
                    checked = settings.autoDeleteOnlyPlayed,
                    onCheckedChange = { viewModel.updateAutoDeleteOnlyPlayed(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Feed Sync settings
            SectionHeader("Feed Sync")

            // Only show sync settings if background sync is enabled in privacy settings
            if (settings.allowBackgroundSync) {
                SyncIntervalSelector(
                    currentInterval = syncSettings.syncInterval,
                    onIntervalChanged = { viewModel.updateSyncInterval(it) }
                )

                SettingSwitch(
                    title = "Wi-Fi Only Sync",
                    description = "Only sync feeds on Wi-Fi",
                    checked = syncSettings.wifiOnly,
                    onCheckedChange = { viewModel.updateSyncWifiOnly(it) }
                )

                SettingSwitch(
                    title = "New Episode Notifications",
                    description = "Show notification when new episodes found",
                    checked = syncSettings.notifyNewEpisodes,
                    onCheckedChange = { viewModel.updateSyncNotifications(it) }
                )

                // Sync status card
                SyncStatusCard(
                    lastSyncTimestamp = syncSettings.lastSyncTimestamp,
                    lastSyncError = syncSettings.lastSyncError,
                    lastSyncNewEpisodes = syncSettings.lastSyncNewEpisodes,
                    isSyncing = isSyncing,
                    onSyncNow = { viewModel.syncNow() },
                    formatTime = { viewModel.formatLastSyncTime(it) }
                )
            } else {
                Text(
                    text = "Enable 'Background Sync' in Network settings to configure automatic feed updates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Data storage
            SectionHeader("Data Storage")
            SettingSwitch(
                title = "Store Search History",
                description = "Keep local search history",
                checked = settings.storeSearchHistory,
                onCheckedChange = { viewModel.updateStoreSearchHistory(it) }
            )
            SettingSwitch(
                title = "Store Playback History",
                description = "Track listening progress",
                checked = settings.storePlaybackHistory,
                onCheckedChange = { viewModel.updateStorePlaybackHistory(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Permissions - Only show RUNTIME permissions that users can change
            SectionHeader("Permissions")
            PermissionItem(
                title = "Microphone",
                granted = permissionState.hasRecordAudio,
                description = "For voice commands"
            )
            // POST_NOTIFICATIONS is a runtime permission on Android 13+ (API 33+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionItem(
                    title = "Notifications",
                    granted = permissionState.hasPostNotifications,
                    description = "For playback controls and updates"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Troubleshooting
            SectionHeader("Troubleshooting")
            NavigationItem(
                title = "Diagnostics",
                description = "View logs, test API, troubleshoot issues",
                icon = Icons.Filled.BugReport,
                onClick = { navController.navigate(Screen.Diagnostics.route) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusCard(operationalMode: OperationalMode) {
    val (title, description, color) = when (operationalMode) {
        OperationalMode.OFFLINE_NO_PERMISSION -> Triple(
            "Offline (No Permission)",
            "Internet permission not granted. App works fully offline.",
            MaterialTheme.colorScheme.secondaryContainer
        )
        OperationalMode.OFFLINE_NO_NETWORK -> Triple(
            "Offline (No Network)",
            "No network connection available.",
            MaterialTheme.colorScheme.errorContainer
        )
        OperationalMode.OFFLINE_USER_CHOICE -> Triple(
            "Offline (User Choice)",
            "Network disabled in settings. Maximum privacy mode.",
            MaterialTheme.colorScheme.primaryContainer
        )
        OperationalMode.ONLINE_LIMITED -> Triple(
            "Online (Limited)",
            "Some network features enabled.",
            MaterialTheme.colorScheme.tertiaryContainer
        )
        OperationalMode.ONLINE_FULL -> Triple(
            "Online (Full)",
            "All network features enabled.",
            MaterialTheme.colorScheme.primaryContainer
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun PresetItem(
    preset: PrivacyPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    granted: Boolean,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = if (granted) "Granted" else "Not Granted",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun NavigationItem(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun isPresetSelected(settings: com.podcast.app.privacy.PrivacySettings, preset: PrivacyPreset): Boolean {
    return when (preset) {
        PrivacyPreset.MAXIMUM_PRIVACY -> !settings.networkEnabled
        PrivacyPreset.BALANCED -> settings.networkEnabled && !settings.allowBackgroundSync && !settings.allowClaudeApi
        PrivacyPreset.FULL_FEATURES -> settings.networkEnabled && settings.allowBackgroundSync
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncIntervalSelector(
    currentInterval: SyncInterval,
    onIntervalChanged: (SyncInterval) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sync Interval",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "How often to check for new episodes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(0.8f)
        ) {
            OutlinedTextField(
                value = currentInterval.displayName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SyncInterval.entries.forEach { interval ->
                    DropdownMenuItem(
                        text = { Text(interval.displayName) },
                        onClick = {
                            onIntervalChanged(interval)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(
    lastSyncTimestamp: Long,
    lastSyncError: String?,
    lastSyncNewEpisodes: Int,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    formatTime: (Long) -> String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (lastSyncError != null) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync Status",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (lastSyncTimestamp > 0) {
                            "Last synced: ${formatTime(lastSyncTimestamp)}"
                        } else {
                            "Never synced"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (lastSyncError != null) {
                        Text(
                            text = "Error: $lastSyncError",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (lastSyncNewEpisodes > 0) {
                        Text(
                            text = "Found $lastSyncNewEpisodes new episode${if (lastSyncNewEpisodes > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 8.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Button(
                        onClick = onSyncNow,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Sync Now")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionPeriodSelector(
    currentDays: Int,
    onDaysChanged: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val retentionOptions = listOf(
        7 to "1 week",
        14 to "2 weeks",
        30 to "1 month",
        60 to "2 months",
        90 to "3 months"
    )

    val currentLabel = retentionOptions.find { it.first == currentDays }?.second
        ?: "$currentDays days"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Delete after",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Remove episodes older than this",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(0.6f)
        ) {
            OutlinedTextField(
                value = currentLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                retentionOptions.forEach { (days, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onDaysChanged(days)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaudeApiConfiguration(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: () -> Unit,
    isTestingConnection: Boolean,
    connectionTestResult: Boolean?,
    connectionTestMessage: String?,
    // GH#31: Enhanced LLM test parameters
    onTestWithQueries: () -> Unit = {},
    isRunningLlmTest: Boolean = false,
    llmTestResult: LLMTestResult? = null
) {
    var showApiKey by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("claude_api_config"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "API Key Configuration",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                placeholder = { Text("sk-ant-...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("claude_api_key_input"),
                singleLine = true,
                visualTransformation = if (showApiKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                            )
                        }
                        if (apiKey.isNotBlank()) {
                            IconButton(onClick = onClearApiKey) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear API key"
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTestConnection,
                    enabled = apiKey.isNotBlank() && !isTestingConnection,
                    modifier = Modifier.testTag("test_connection_button")
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                    }
                    Text(if (isTestingConnection) "Testing..." else "Test Connection")
                }

                // Show result
                if (connectionTestResult != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (connectionTestResult) {
                                Icons.Filled.Check
                            } else {
                                Icons.Filled.Close
                            },
                            contentDescription = null,
                            tint = if (connectionTestResult) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text(
                            text = connectionTestMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionTestResult) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your API key is stored securely using Android Keystore encryption.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // GH#31: Enhanced LLM test with natural language queries
            if (connectionTestResult == true) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "LLM Test",
                    style = MaterialTheme.typography.titleSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Test the AI with simple questions to verify it's working.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onTestWithQueries,
                    enabled = !isRunningLlmTest && !isTestingConnection,
                    modifier = Modifier.testTag("test_llm_button")
                ) {
                    if (isRunningLlmTest) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text("Testing AI...")
                    } else {
                        Text("Test AI Responses")
                    }
                }

                // Display LLM test results
                llmTestResult?.let { result ->
                    if (result.queryResponses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("llm_test_results"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.padding(start = 4.dp))
                                    Text(
                                        text = "AI Responses Verified",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                result.queryResponses.forEach { qr ->
                                    Text(
                                        text = "Q: ${qr.query.replace(" Answer in one sentence.", "")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "A: ${qr.response}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
