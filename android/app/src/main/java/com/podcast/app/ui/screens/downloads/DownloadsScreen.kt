package com.podcast.app.ui.screens.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.util.TestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    navController: NavController,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isClearing by viewModel.isClearing.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.completedDownloads.isNotEmpty() || uiState.activeDownloads.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            enabled = !isClearing
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear all downloads"
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag(TestTags.DOWNLOADS_SCREEN)
        ) {
            // Storage info card
            StorageInfoCard(
                totalSize = uiState.totalDownloadedSize,
                episodeCount = uiState.downloadedEpisodeCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Active downloads section
                if (uiState.activeDownloads.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Active Downloads (${uiState.activeDownloads.size})",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(uiState.activeDownloads, key = { it.download.episodeId }) { downloadWithEpisode ->
                        ActiveDownloadItem(
                            downloadWithEpisode = downloadWithEpisode,
                            onCancel = { viewModel.cancelDownload(downloadWithEpisode.download.episodeId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }

                // Completed downloads section
                if (uiState.completedDownloads.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Completed Downloads (${uiState.completedDownloads.size})",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(uiState.completedDownloads, key = { it.download.episodeId }) { downloadWithEpisode ->
                        CompletedDownloadItem(
                            downloadWithEpisode = downloadWithEpisode,
                            onDelete = { viewModel.deleteDownload(downloadWithEpisode.download.episodeId) },
                            onRetry = { viewModel.retryDownload(downloadWithEpisode.download.episodeId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }

                // Empty state
                if (uiState.activeDownloads.isEmpty() && uiState.completedDownloads.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No downloads",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Downloaded episodes will appear here",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Clear all confirmation dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear all downloads?") },
                text = {
                    Text("This will remove all downloaded episodes from your device. You can re-download them later.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllDownloads()
                            showClearDialog = false
                        }
                    ) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun StorageInfoCard(
    totalSize: Long,
    episodeCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
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
            Column {
                Text(
                    text = "Storage Used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatFileSize(totalSize),
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Episodes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = episodeCount.toString(),
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
private fun ActiveDownloadItem(
    downloadWithEpisode: DownloadWithEpisode,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val download = downloadWithEpisode.download
    val episode = downloadWithEpisode.episode
    val progress = if (download.fileSize != null && download.fileSize > 0) {
        download.downloadedBytes.toFloat() / download.fileSize.toFloat()
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "download_progress"
    )

    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode image
            episode?.imageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Episode info and progress
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode?.title ?: "Unknown Episode",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when (download.status) {
                            DownloadStatus.PENDING -> "Queued..."
                            DownloadStatus.IN_PROGRESS -> "${(animatedProgress * 100).toInt()}%"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${formatFileSize(download.downloadedBytes)} / ${formatFileSize(download.fileSize ?: 0)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Cancel button
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = "Cancel download",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CompletedDownloadItem(
    downloadWithEpisode: DownloadWithEpisode,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val download = downloadWithEpisode.download
    val episode = downloadWithEpisode.episode
    val isFailed = download.status == DownloadStatus.FAILED

    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isFailed) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Episode info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode?.title ?: "Unknown Episode",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isFailed) {
                        "Failed: ${download.errorMessage ?: "Unknown error"}"
                    } else {
                        formatFileSize(download.fileSize ?: download.downloadedBytes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action button
            if (isFailed) {
                IconButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry download",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete download",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
