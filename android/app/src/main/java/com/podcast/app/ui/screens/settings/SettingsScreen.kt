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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.podcast.app.privacy.OperationalMode
import com.podcast.app.privacy.PrivacyPreset
import com.podcast.app.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val operationalMode by viewModel.operationalMode.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()

    Scaffold(
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

            Spacer(modifier = Modifier.height(16.dp))

            // Download settings
            SectionHeader("Downloads")
            NavigationItem(
                title = "Download Manager",
                description = "View and manage downloaded episodes",
                icon = Icons.Filled.Download,
                onClick = { navController.navigate(Screen.Downloads.route) }
            )
            SettingSwitch(
                title = "Wi-Fi Only Downloads",
                description = "Only auto-download on Wi-Fi",
                checked = settings.autoDownloadOnWifiOnly,
                onCheckedChange = { viewModel.updateAutoDownloadOnWifiOnly(it) }
            )

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

            // Permissions
            SectionHeader("Permissions")
            PermissionItem(
                title = "Internet",
                granted = permissionState.hasInternet,
                description = "Optional - app works offline"
            )
            PermissionItem(
                title = "Microphone",
                granted = permissionState.hasRecordAudio,
                description = "For voice commands"
            )
            PermissionItem(
                title = "Foreground Service",
                granted = permissionState.hasForegroundService,
                description = "Background playback"
            )

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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
