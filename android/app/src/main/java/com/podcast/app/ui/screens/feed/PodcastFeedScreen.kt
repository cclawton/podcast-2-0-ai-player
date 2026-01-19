package com.podcast.app.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.podcast.app.data.local.entities.Episode
import com.podcast.app.data.local.entities.Podcast
import com.podcast.app.playback.PlaybackState
import com.podcast.app.ui.Screen
import com.podcast.app.util.TestTags
import java.text.SimpleDateFormat
import java.util.*

/**
 * PodcastFeedScreen (GH#32)
 *
 * Displays podcast information and episode list for unsubscribed podcasts.
 * Allows browsing episodes before subscribing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastFeedScreen(
    navController: NavController,
    viewModel: PodcastFeedViewModel = hiltViewModel()
) {
    val podcast by viewModel.podcast.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isSubscribing by viewModel.isSubscribing.collectAsState()
    val subscriptionSuccess by viewModel.subscriptionSuccess.collectAsState()
    val currentEpisode by viewModel.currentEpisode.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Handle subscription success - navigate to episodes screen
    LaunchedEffect(subscriptionSuccess) {
        subscriptionSuccess?.let { podcastId ->
            navController.navigate(Screen.Episodes.createRoute(podcastId)) {
                popUpTo(Screen.Search.route) { inclusive = false }
            }
            viewModel.clearSubscriptionSuccess()
        }
    }

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.PODCAST_FEED_SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = podcast?.title ?: "Loading...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (podcast?.isSubscribed == false) {
                        Button(
                            onClick = { viewModel.subscribe() },
                            enabled = !isSubscribing,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag(TestTags.PODCAST_FEED_SUBSCRIBE_BUTTON)
                        ) {
                            if (isSubscribing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Subscribe")
                            }
                        }
                    } else if (podcast?.isSubscribed == true) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Subscribed") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when {
            isLoading && podcast == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .testTag(TestTags.PODCAST_FEED_EPISODES),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Podcast Header
                    item {
                        podcast?.let { pod ->
                            PodcastHeader(podcast = pod)
                        }
                    }

                    // Episodes section header
                    item {
                        Text(
                            text = "Episodes (${episodes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Episode list
                    items(episodes, key = { it.episodeIndexId }) { episode ->
                        FeedEpisodeItem(
                            episode = episode,
                            isPlaying = currentEpisode?.episodeIndexId == episode.episodeIndexId &&
                                    playbackState == PlaybackState.PLAYING,
                            isSubscribed = podcast?.isSubscribed == true,
                            onPlayClick = { viewModel.playEpisode(episode) },
                            onDownloadClick = { viewModel.downloadEpisode(episode) }
                        )
                    }

                    // Loading indicator for episodes
                    if (isLoading && episodes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    // Empty state
                    if (!isLoading && episodes.isEmpty() && podcast != null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No episodes available",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastHeader(podcast: Podcast) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Podcast artwork
            AsyncImage(
                model = podcast.imageUrl,
                contentDescription = podcast.title,
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            // Podcast info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(TestTags.PODCAST_FEED_TITLE)
                )

                podcast.author?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (podcast.explicit) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Explicit", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    Text(
                        text = "${podcast.episodeCount} episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Description
        podcast.description?.let { description ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(TestTags.PODCAST_FEED_DESCRIPTION)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun FeedEpisodeItem(
    episode: Episode,
    isPlaying: Boolean,
    isSubscribed: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag(TestTags.PODCAST_FEED_EPISODE_ITEM),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlayClick)
                .padding(12.dp)
        ) {
            // Episode title
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Episode metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date
                    episode.publishedAt?.let { timestamp ->
                        Text(
                            text = dateFormat.format(Date(timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Duration
                    episode.audioDuration?.let { duration ->
                        if (duration > 0) {
                            val minutes = duration / 60
                            val hours = minutes / 60
                            val displayMinutes = minutes % 60
                            Text(
                                text = if (hours > 0) "${hours}h ${displayMinutes}m" else "${minutes}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Play button
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Download button (only if subscribed)
                    if (isSubscribed) {
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag(TestTags.PODCAST_FEED_DOWNLOAD_BUTTON)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Description preview
            episode.description?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description.replace(Regex("<[^>]*>"), ""), // Strip HTML
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
