package com.podcast.app.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.podcast.app.ui.Screen
import com.podcast.app.ui.components.EmptyState
import com.podcast.app.ui.components.LoadingState
import com.podcast.app.ui.components.NetworkDisabledBanner
import com.podcast.app.ui.components.PodcastCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val trendingPodcasts by viewModel.trendingPodcasts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val canSearch by viewModel.canSearch.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Podcasts") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("search_screen")
        ) {
            if (!canSearch) {
                NetworkDisabledBanner()
            }

            SearchBar(
                query = query,
                onQueryChange = { viewModel.updateQuery(it) },
                onSearch = { },
                active = false,
                onActiveChange = { },
                placeholder = { Text("Search podcasts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("search_input")
            ) { }

            when {
                isLoading -> {
                    LoadingState(message = "Searching...", modifier = Modifier.testTag("search_loading"))
                }

                query.isNotEmpty() && searchResults.isEmpty() -> {
                    EmptyState(
                        title = "No results",
                        message = "Try a different search term",
                        modifier = Modifier.testTag("search_empty")
                    )
                }

                query.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().testTag("search_results")
                    ) {
                        items(searchResults, key = { it.podcastIndexId }) { podcast ->
                            PodcastCard(
                                podcast = podcast,
                                onClick = {
                                    viewModel.subscribeToPodcast(podcast.podcastIndexId)
                                    navController.navigate(Screen.Episodes.createRoute(podcast.id))
                                }
                            )
                        }
                    }
                }

                trendingPodcasts.isNotEmpty() -> {
                    Text(
                        text = "Trending Podcasts",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(trendingPodcasts, key = { it.podcastIndexId }) { podcast ->
                            PodcastCard(
                                podcast = podcast,
                                onClick = {
                                    viewModel.subscribeToPodcast(podcast.podcastIndexId)
                                }
                            )
                        }
                    }
                }

                else -> {
                    EmptyState(
                        title = "Discover podcasts",
                        message = "Search for your favorite shows or browse trending podcasts"
                    )
                }
            }
        }
    }
}
