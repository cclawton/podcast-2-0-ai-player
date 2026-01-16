package com.podcast.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.podcast.app.ui.Screen
import com.podcast.app.ui.components.EmptyState
import com.podcast.app.ui.components.EpisodeItem
import com.podcast.app.ui.components.MiniPlayer
import com.podcast.app.ui.components.NetworkDisabledBanner
import com.podcast.app.ui.components.PodcastCard

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

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (podcasts.isEmpty()) {
                    EmptyState(
                        title = "No podcasts yet",
                        message = "Search and subscribe to podcasts to build your library",
                        modifier = Modifier.testTag("library_empty")
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Subscribed podcasts grid
                        Text(
                            text = "Subscriptions",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 150.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).testTag("library_list")
                        ) {
                            items(podcasts, key = { it.id }) { podcast ->
                                PodcastCard(
                                    podcast = podcast,
                                    onClick = {
                                        navController.navigate(Screen.Episodes.createRoute(podcast.id))
                                    },
                                    modifier = Modifier.testTag("podcast_item")
                                )
                            }
                        }

                        // Recent episodes
                        if (recentEpisodes.isNotEmpty()) {
                            Text(
                                text = "Recent Episodes",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(recentEpisodes.take(10), key = { it.id }) { episode ->
                                    Box(modifier = Modifier.fillMaxWidth(0.85f)) {
                                        EpisodeItem(
                                            episode = episode,
                                            onPlayClick = { viewModel.playEpisode(episode.id) },
                                            onDownloadClick = { /* TODO: Download */ },
                                            onClick = { viewModel.playEpisode(episode.id) }
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
}
