package com.podcast.app.ui.screens.episodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.ui.Screen
import com.podcast.app.ui.components.EmptyState
import com.podcast.app.ui.components.EpisodeItem
import com.podcast.app.ui.components.MiniPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodesScreen(
    podcastId: Long?,
    navController: NavController,
    viewModel: EpisodesViewModel = hiltViewModel()
) {
    val podcast by viewModel.podcast.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentEpisode by viewModel.currentEpisode.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(podcast?.title ?: "Episodes") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Unsubscribe") },
                                onClick = {
                                    viewModel.unsubscribe()
                                    showMenu = false
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            MiniPlayer(
                episode = currentEpisode,
                playbackState = playbackState,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onSkipNextClick = { viewModel.skipNext() },
                onClick = { navController.navigate(Screen.Player.route) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("episodes_screen")
        ) {
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (episodes.isEmpty()) {
                EmptyState(
                    title = "No episodes",
                    message = "Check for new episodes",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("episodes_list")
                ) {
                    // Podcast header
                    item {
                        PodcastHeader(
                            imageUrl = podcast?.imageUrl,
                            title = podcast?.title ?: "",
                            author = podcast?.author,
                            description = podcast?.description,
                            episodeCount = episodes.size
                        )
                    }

                    items(episodes, key = { it.id }) { episode ->
                        val download = downloads[episode.id]
                        val episodeProgress = progress[episode.id]
                        val progressPercent = episodeProgress?.let { prog ->
                            val duration = prog.durationSeconds ?: 0
                            if (duration > 0) {
                                prog.positionSeconds.toFloat() / duration
                            } else 0f
                        } ?: 0f

                        EpisodeItem(
                            episode = episode,
                            progress = progressPercent,
                            isDownloaded = download?.status == DownloadStatus.COMPLETED,
                            onPlayClick = { viewModel.playEpisode(episode.id) },
                            onDownloadClick = { viewModel.downloadEpisode(episode) },
                            onClick = { viewModel.playEpisode(episode.id) },
                            modifier = Modifier.testTag("episode_item")
                        )
                    }
                }
            }
        }
    }

    // Auto-refresh on first load
    LaunchedEffect(podcastId) {
        viewModel.refresh()
    }
}

@Composable
private fun PodcastHeader(
    imageUrl: String?,
    title: String,
    author: String?,
    description: String?,
    episodeCount: Int
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$episodeCount episodes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    description?.let {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
}
