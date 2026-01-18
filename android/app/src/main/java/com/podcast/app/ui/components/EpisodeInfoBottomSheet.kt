package com.podcast.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Explicit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.util.TestTags
import com.podcast.app.util.TextUtils
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom sheet displaying full episode information including:
 * - Full description/show notes
 * - Transcript availability
 * - Chapter markers
 * - Links mentioned
 * - Publication date and duration
 * - Podcast 2.0 tags (season, episode number, explicit)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeInfoBottomSheet(
    episode: Episode,
    podcast: Podcast? = null,
    podcastTitle: String? = null,
    fallbackImageUrl: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onChapterClick: ((startTimeSeconds: Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val displayImageUrl = episode.imageUrl?.takeIf { it.isNotBlank() } ?: fallbackImageUrl

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(TestTags.EPISODE_INFO_SHEET)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with image and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                displayImageUrl?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag(TestTags.EPISODE_INFO_TITLE)
                    )

                    podcastTitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Metadata section
            Text(
                text = "Episode Details",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Publication date
            episode.publishedAt?.let { timestamp ->
                MetadataRow(
                    icon = Icons.Default.CalendarToday,
                    label = "Published",
                    value = formatFullDate(timestamp),
                    testTag = TestTags.EPISODE_INFO_DATE
                )
            }

            // Duration
            episode.audioDuration?.let { duration ->
                MetadataRow(
                    icon = Icons.Default.AccessTime,
                    label = "Duration",
                    value = formatFullDuration(duration),
                    testTag = TestTags.EPISODE_INFO_DURATION
                )
            }

            // Season and Episode number
            if (episode.seasonNumber != null || episode.episodeNumber != null) {
                val episodeInfo = buildString {
                    episode.seasonNumber?.let { append("Season $it") }
                    if (episode.seasonNumber != null && episode.episodeNumber != null) {
                        append(", ")
                    }
                    episode.episodeNumber?.let { append("Episode $it") }
                }
                MetadataRow(
                    icon = Icons.Default.Numbers,
                    label = "Episode",
                    value = episodeInfo,
                    testTag = TestTags.EPISODE_INFO_EPISODE_NUMBER
                )
            }

            // Explicit indicator
            if (episode.explicit) {
                MetadataRow(
                    icon = Icons.Default.Explicit,
                    label = "Content",
                    value = "Explicit",
                    testTag = TestTags.EPISODE_INFO_EXPLICIT
                )
            }

            // Episode link
            episode.link?.takeIf { it.isNotBlank() }?.let { link ->
                MetadataRow(
                    icon = Icons.Default.Link,
                    label = "Episode Link",
                    value = link,
                    isLink = true,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                        context.startActivity(intent)
                    },
                    testTag = TestTags.EPISODE_INFO_LINK
                )
            }

            // Transcript availability
            episode.transcriptUrl?.takeIf { it.isNotBlank() }?.let { transcriptUrl ->
                MetadataRow(
                    icon = Icons.Default.Description,
                    label = "Transcript",
                    value = episode.transcriptType?.uppercase() ?: "Available",
                    isLink = true,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(transcriptUrl))
                        context.startActivity(intent)
                    },
                    testTag = TestTags.EPISODE_INFO_TRANSCRIPT
                )
            }

            // Chapter markers
            val chapters = parseChapters(episode.chaptersJson)
            if (chapters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Chapters (${chapters.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(TestTags.EPISODE_INFO_CHAPTERS_HEADER)
                )

                if (onChapterClick != null) {
                    Text(
                        text = "Tap to jump to chapter",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                chapters.forEachIndexed { index, chapter ->
                    ChapterItem(
                        index = index + 1,
                        title = chapter.title,
                        startTime = chapter.startTime,
                        isClickable = onChapterClick != null,
                        onClick = { onChapterClick?.invoke(chapter.startTime) },
                        modifier = Modifier.testTag("${TestTags.EPISODE_INFO_CHAPTER_ITEM}_$index")
                    )
                }
            }

            // Value4Value / Funding section
            podcast?.let { p ->
                if (p.fundingUrl != null || p.valueType != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Support This Podcast",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(TestTags.VALUE_INFO_CARD)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Funding link
                    p.fundingUrl?.let { fundingUrl ->
                        MetadataRow(
                            icon = Icons.Default.VolunteerActivism,
                            label = "Donate",
                            value = p.fundingMessage ?: "Support the show",
                            isLink = true,
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fundingUrl))
                                context.startActivity(intent)
                            }
                        )
                    }

                    // Value4Value (Lightning, etc.)
                    p.valueType?.let { valueType ->
                        MetadataRow(
                            icon = Icons.Default.AttachMoney,
                            label = "Value4Value",
                            value = when (valueType.lowercase()) {
                                "lightning" -> "Lightning Network"
                                else -> valueType
                            }
                        )

                        p.valueModel?.let { model ->
                            Text(
                                text = "Model: $model",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 32.dp)
                            )
                        }
                    }
                }
            }

            // Full description / Show notes
            TextUtils.stripHtml(episode.description)?.let { description ->
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Show Notes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag(TestTags.EPISODE_INFO_SHOW_NOTES_HEADER)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(TestTags.EPISODE_INFO_DESCRIPTION)
                )
            }

            // Bottom spacing for navigation gesture area
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MetadataRow(
    icon: ImageVector,
    label: String,
    value: String,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                if (onClick != null) mod.clickable(onClick = onClick) else mod
            }
            .padding(vertical = 6.dp)
            .let { mod ->
                testTag?.let { mod.testTag(it) } ?: mod
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChapterItem(
    index: Int,
    title: String,
    startTime: Int,
    isClickable: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { mod ->
                if (isClickable && onClick != null) {
                    mod.clickable(onClick = onClick)
                } else {
                    mod
                }
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isClickable) Icons.Default.PlayCircle else Icons.Default.List,
            contentDescription = if (isClickable) "Play from chapter" else null,
            modifier = Modifier.size(20.dp),
            tint = if (isClickable) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Text(
            text = formatChapterTime(startTime),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Data class representing a chapter marker
 */
private data class Chapter(
    val title: String,
    val startTime: Int // in seconds
)

/**
 * Parses chapters from JSON format.
 * Expected format: [{"title": "Chapter Title", "startTime": 120}, ...]
 */
private fun parseChapters(chaptersJson: String?): List<Chapter> {
    if (chaptersJson.isNullOrBlank()) return emptyList()

    return try {
        val jsonArray = JSONArray(chaptersJson)
        (0 until jsonArray.length()).mapNotNull { i ->
            val obj = jsonArray.getJSONObject(i)
            val title = obj.optString("title", "").takeIf { it.isNotBlank() }
                ?: obj.optString("name", "Chapter ${i + 1}")
            val startTime = obj.optInt("startTime", obj.optInt("start", 0))
            Chapter(title, startTime)
        }
    } catch (e: Exception) {
        emptyList()
    }
}

private fun formatFullDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}

private fun formatFullDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return when {
        hours > 0 -> String.format("%d hour%s %d min%s", hours, if (hours > 1) "s" else "", minutes, if (minutes != 1) "s" else "")
        minutes > 0 -> String.format("%d minute%s", minutes, if (minutes != 1) "s" else "")
        else -> String.format("%d second%s", secs, if (secs != 1) "s" else "")
    }
}

private fun formatChapterTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}
