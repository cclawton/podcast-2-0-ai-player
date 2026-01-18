package com.podcast.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.util.TestTags
import com.podcast.app.util.TextUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpisodeItem(
    episode: Episode,
    progress: Float = 0f,
    isDownloaded: Boolean = false,
    downloadStatus: DownloadStatus? = null,
    downloadProgress: Float = 0f,
    fallbackImageUrl: String? = null,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use episode image, or fall back to podcast/feed image
    // Handle both null and empty string cases
    val displayImageUrl = episode.imageUrl?.takeIf { it.isNotBlank() } ?: fallbackImageUrl

    // For backwards compatibility: derive status from isDownloaded if downloadStatus not provided
    val effectiveStatus = downloadStatus ?: if (isDownloaded) DownloadStatus.COMPLETED else null
    val animatedDownloadProgress by animateFloatAsState(
        targetValue = downloadProgress,
        label = "download_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                displayImageUrl?.let { imageUrl ->
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        episode.publishedAt?.let { timestamp ->
                            Text(
                                text = formatDate(timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        episode.audioDuration?.let { duration ->
                            Text(
                                text = " • ${formatDuration(duration)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(onClick = onPlayClick) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                onInfoClick?.let { infoClick ->
                    IconButton(
                        onClick = infoClick,
                        modifier = Modifier.testTag(TestTags.EPISODE_INFO_BUTTON)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Episode info",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDownloadClick) {
                    DownloadStatusIcon(status = effectiveStatus)
                }
            }

            // Show download progress bar when downloading
            if (effectiveStatus == DownloadStatus.IN_PROGRESS || effectiveStatus == DownloadStatus.PENDING) {
                LinearProgressIndicator(
                    progress = { if (effectiveStatus == DownloadStatus.IN_PROGRESS) animatedDownloadProgress else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            // Show playback progress bar when partially played
            if (progress > 0f && progress < 1f && effectiveStatus != DownloadStatus.IN_PROGRESS) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            TextUtils.stripHtml(episode.description)?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    // Timestamps are stored in milliseconds; no conversion needed
    return sdf.format(Date(timestamp))
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

@Composable
private fun DownloadStatusIcon(status: DownloadStatus?) {
    when (status) {
        DownloadStatus.PENDING -> {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        DownloadStatus.IN_PROGRESS -> {
            Icon(
                imageVector = Icons.Default.Downloading,
                contentDescription = "Downloading",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DownloadStatus.COMPLETED -> {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete download",
                tint = MaterialTheme.colorScheme.error
            )
        }
        DownloadStatus.FAILED -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Download failed - tap to retry",
                tint = MaterialTheme.colorScheme.error
            )
        }
        DownloadStatus.CANCELLED, null -> {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
