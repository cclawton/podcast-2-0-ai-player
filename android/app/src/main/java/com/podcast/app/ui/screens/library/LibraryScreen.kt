package com.podcast.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.ui.Screen
import com.podcast.app.ui.components.EmptyState
import com.podcast.app.ui.components.EpisodeCard
import com.podcast.app.ui.components.MiniPlayer
import com.podcast.app.ui.components.NetworkDisabledBanner
import com.podcast.app.ui.components.PodcastThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val podcasts by viewModel.subscribedPodcasts.collectAsState()
    val recentEpisodes by viewModel.recentEpisodes.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val currentEpisode by viewModel.currentEpisode.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val canUseNetwork by viewModel.canUseNetwork.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val podcastImages by viewModel.podcastImages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.Search.route) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add podcast")
            }
        },
        bottomBar = {
            MiniPlayer(
                episode = currentEpisode,
                playbackState = playbackState,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onSkipNextClick = { viewModel.skipNext() },
                onClick = { navController.navigate(Screen.Player.route) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("library_screen")
        ) {
            if (!canUseNetwork) {
                NetworkDisabledBanner()
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (podcasts.isEmpty()) {
                    EmptyState(
                        title = "No podcasts yet",
                        message = "Search and subscribe to podcasts to build your library",
                        modifier = Modifier.testTag("library_empty")
                    )
                } else {
                    val configuration = LocalConfiguration.current
                    val screenWidth = configuration.screenWidthDp.dp
                    // Calculate thumbnail size to fit 4 per row with padding and spacing
                    val horizontalPadding = 16.dp * 2 // Left and right padding
                    val spacing = 8.dp * 3 // 3 gaps between 4 items
                    val thumbnailSize = (screenWidth - horizontalPadding - spacing) / 4

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Subscribed podcasts - 4-column grid with image-only thumbnails
                        Text(
                            text = "Subscriptions",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        // Non-lazy grid for subscriptions - allows vertical scroll of entire content
                        SubscriptionsGrid(
                            podcasts = podcasts,
                            thumbnailSize = thumbnailSize,
                            onPodcastClick = { podcast ->
                                navController.navigate(Screen.Episodes.createRoute(podcast.id))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag("library_list")
                        )

                        // Recent episodes - horizontal scroll with wider cards
                        if (recentEpisodes.isNotEmpty()) {
                            Text(
                                text = "Recent Episodes",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.heightIn(min = 160.dp)
                            ) {
                                items(recentEpisodes.take(10), key = { it.id }) { episode ->
                                    val download = downloads[episode.id]
                                    val downloadStatus = download?.status
                                    val downloadProgress = if (download != null && download.fileSize != null && download.fileSize > 0) {
                                        download.downloadedBytes.toFloat() / download.fileSize.toFloat()
                                    } else 0f

                                    EpisodeCard(
                                        episode = episode,
                                        downloadStatus = downloadStatus,
                                        downloadProgress = downloadProgress,
                                        fallbackImageUrl = podcastImages[episode.podcastId]?.takeIf { it.isNotBlank() },
                                        onPlayClick = { viewModel.playEpisode(episode.id) },
                                        onDownloadClick = { viewModel.downloadEpisode(episode) },
                                        onClick = { viewModel.playEpisode(episode.id) },
                                        modifier = Modifier.width(320.dp)
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

/**
 * A non-lazy grid for subscriptions that displays podcasts in a 4-column layout.
 * This allows the entire content to scroll vertically together.
 */
@Composable
private fun SubscriptionsGrid(
    podcasts: List<com.podcast.app.data.local.entities.Podcast>,
    thumbnailSize: androidx.compose.ui.unit.Dp,
    onPodcastClick: (com.podcast.app.data.local.entities.Podcast) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = 4
    val rows = (podcasts.size + columns - 1) / columns

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in 0 until rows) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < podcasts.size) {
                        PodcastThumbnail(
                            podcast = podcasts[index],
                            onClick = { onPodcastClick(podcasts[index]) },
                            modifier = Modifier.width(thumbnailSize)
                        )
                    } else {
                        // Empty space to maintain grid alignment
                        androidx.compose.foundation.layout.Spacer(
                            modifier = Modifier.width(thumbnailSize)
                        )
                    }
                }
            }
        }
    }
}
