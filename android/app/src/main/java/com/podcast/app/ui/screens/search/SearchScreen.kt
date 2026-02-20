package com.podcast.app.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.podcast.app.api.claude.AISearchService
import com.podcast.app.data.local.entities.DownloadStatus
import com.podcast.app.ui.Screen
import com.podcast.app.ui.components.EmptyState
import com.podcast.app.ui.components.LoadingState
import com.podcast.app.ui.components.NetworkDisabledBanner
import com.podcast.app.ui.components.PodcastCard
import com.podcast.app.util.TestTags
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val showRssDialog by viewModel.showRssDialog.collectAsState()
    val rssUrl by viewModel.rssUrl.collectAsState()
    val rssSubscriptionSuccess by viewModel.rssSubscriptionSuccess.collectAsState()
    val showSubscribeConfirmation by viewModel.showSubscribeConfirmation.collectAsState()
    val selectedPodcast by viewModel.selectedPodcast.collectAsState()

    // AI Search state (GH#30, GH#35, GH#36)
    val showAiSearch by viewModel.showAiSearch.collectAsState()
    val aiQuery by viewModel.aiQuery.collectAsState()
    val aiSearchResults by viewModel.aiSearchResults.collectAsState()
    val aiEpisodeResults by viewModel.aiEpisodeResults.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val aiError by viewModel.aiError.collectAsState()
    val aiExplanation by viewModel.aiExplanation.collectAsState()
    val aiSearchType by viewModel.aiSearchType.collectAsState()  // GH#36: Search type for conditional display
    val isAiAvailable = viewModel.isAiAvailable

    // GH#35: Determine if AI search has active results
    val hasAiResults = aiSearchResults.isNotEmpty() || aiEpisodeResults.isNotEmpty()

    // GH#38: Episode download states for AI search results
    val episodeDownloadStates by viewModel.episodeDownloadStates.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Keyboard control for dismissing after search
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Handle AI search errors
    LaunchedEffect(aiError) {
        aiError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearAiError()
        }
    }

    // Navigate to episodes screen after successful RSS subscription
    LaunchedEffect(rssSubscriptionSuccess) {
        rssSubscriptionSuccess?.let { podcast ->
            navController.navigate(Screen.Episodes.createRoute(podcast.id))
            viewModel.clearRssSubscriptionSuccess()
        }
    }

    // RSS Feed Dialog
    if (showRssDialog) {
        RssFeedDialog(
            rssUrl = rssUrl,
            onUrlChange = viewModel::updateRssUrl,
            onDismiss = viewModel::hideRssDialog,
            onConfirm = viewModel::subscribeFromRss,
            isLoading = isLoading
        )
    }

    // Subscription Confirmation Dialog
    if (showSubscribeConfirmation && selectedPodcast != null) {
        SubscribeConfirmationDialog(
            podcast = selectedPodcast!!,
            onConfirm = {
                viewModel.confirmSubscription()
                navController.navigate(Screen.Episodes.createRoute(selectedPodcast!!.id))
            },
            onDismiss = viewModel::hideSubscribeConfirmation
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Podcasts") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // MCP Explorer button (GH#41)
                    IconButton(
                        onClick = { navController.navigate(Screen.McpWidget.route) },
                        modifier = Modifier.testTag("mcp_explorer_button")
                    ) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = "MCP Explorer"
                        )
                    }
                    // AI Search button (GH#30) - always visible and clickable
                    // AI search UI appears on toggle; actual search checks API availability
                    IconButton(
                        onClick = { viewModel.toggleAiSearch() },
                        modifier = Modifier.testTag(TestTags.AI_SEARCH_BUTTON)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Search",
                            tint = when {
                                !isAiAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                showAiSearch -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(
                        onClick = { viewModel.showRssDialog() },
                        modifier = Modifier.testTag(TestTags.RSS_FEED_BUTTON)
                    ) {
                        Icon(
                            Icons.Default.RssFeed,
                            contentDescription = "Add RSS feed"
                        )
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

            // AI Search expandable field (GH#30, GH#35)
            AnimatedVisibility(visible = showAiSearch) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = aiQuery,
                            onValueChange = { viewModel.updateAiQuery(it) },
                            placeholder = { Text("Ask AI: e.g., 'podcasts about machine learning'") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        viewModel.performAiSearch()
                                    },
                                    enabled = aiQuery.isNotBlank() && !isAiLoading,
                                    modifier = Modifier.testTag(TestTags.AI_SEARCH_SUBMIT)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Search",
                                        tint = if (aiQuery.isNotBlank() && !isAiLoading)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                viewModel.performAiSearch()
                            }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag(TestTags.AI_SEARCH_INPUT)
                        )
                    }

                    // GH#35: Show AI explanation in a Card with icon
                    aiExplanation?.let { explanation ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .testTag(TestTags.AI_SEARCH_NL_RESPONSE),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = explanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // GH#35: Clear button when AI has results
                    if (hasAiResults) {
                        OutlinedButton(
                            onClick = { viewModel.clearAiSearchResults() },
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .testTag(TestTags.AI_SEARCH_CLEAR)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear AI Results")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.updateQuery(it) },
                placeholder = { Text("Search podcasts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    // Dismiss keyboard when user presses search (search auto-triggers on text change)
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("search_input")
            )

            when {
                // AI Search loading state (GH#30)
                isAiLoading -> {
                    LoadingState(
                        message = "AI is searching...",
                        modifier = Modifier.testTag(TestTags.AI_SEARCH_LOADING)
                    )
                }

                // GH#35, GH#36: AI Search results with search-type-conditional display
                // - bytitle: Podcast feeds primary, episodes secondary
                // - byperson/byterm: Episode tiles primary, podcasts secondary
                showAiSearch && hasAiResults -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(TestTags.AI_SEARCH_RESULTS)
                    ) {
                        when (aiSearchType) {
                            // bytitle: Show podcast feeds as PRIMARY, recent episodes as SECONDARY
                            "bytitle" -> {
                                // PRIMARY: Matching Podcasts (vertical list)
                                if (aiSearchResults.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Matching Podcasts",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.testTag(TestTags.AI_SEARCH_PODCASTS)
                                        )
                                    }

                                    items(aiSearchResults, key = { it.podcastIndexId }) { podcast ->
                                        PodcastCard(
                                            podcast = podcast,
                                            onClick = {
                                                navController.navigate(Screen.PodcastFeed.createRoute(podcast.podcastIndexId))
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // SECONDARY: Recent Episodes (limited to 3)
                                if (aiEpisodeResults.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Recent Episodes",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .testTag(TestTags.AI_SEARCH_EPISODES)
                                        )
                                    }

                                    items(aiEpisodeResults.take(3), key = { it.id }) { episode ->
                                        val downloadState = episodeDownloadStates[episode.id]?.status.toAIEpisodeDownloadState()
                                        AIEpisodeCard(
                                            episode = episode,
                                            onPlayClick = {
                                                // GH#38: Play episode via shared playback state
                                                viewModel.playAiSearchEpisode(episode)
                                                navController.navigate(Screen.Player.route)
                                            },
                                            onDownloadClick = { viewModel.onAiEpisodeDownloadClick(episode) },
                                            downloadState = downloadState
                                        )
                                    }
                                }
                            }

                            // byperson/byterm: Show episodes as PRIMARY, podcasts as SECONDARY
                            "byperson", "byterm" -> {
                                // PRIMARY: Episode tiles (full list)
                                if (aiEpisodeResults.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = if (aiSearchType == "byperson") "Episodes Featuring This Person"
                                                   else "Relevant Episodes",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.testTag(TestTags.AI_SEARCH_EPISODES)
                                        )
                                    }

                                    items(aiEpisodeResults, key = { it.id }) { episode ->
                                        val downloadState = episodeDownloadStates[episode.id]?.status.toAIEpisodeDownloadState()
                                        AIEpisodeCard(
                                            episode = episode,
                                            onPlayClick = {
                                                // GH#38: Play episode via shared playback state
                                                viewModel.playAiSearchEpisode(episode)
                                                navController.navigate(Screen.Player.route)
                                            },
                                            onDownloadClick = { viewModel.onAiEpisodeDownloadClick(episode) },
                                            downloadState = downloadState
                                        )
                                    }
                                }

                                // SECONDARY: Related Podcasts (horizontal scroll)
                                if (aiSearchResults.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Related Podcasts",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .testTag(TestTags.AI_SEARCH_PODCASTS)
                                        )
                                    }

                                    item {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            items(aiSearchResults, key = { it.podcastIndexId }) { podcast ->
                                                PodcastCard(
                                                    podcast = podcast,
                                                    onClick = {
                                                        navController.navigate(Screen.PodcastFeed.createRoute(podcast.podcastIndexId))
                                                    },
                                                    modifier = Modifier.width(150.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Fallback: Show both sections as before (legacy behavior)
                            else -> {
                                if (aiEpisodeResults.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Relevant Episodes",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.testTag(TestTags.AI_SEARCH_EPISODES)
                                        )
                                    }

                                    items(aiEpisodeResults, key = { it.id }) { episode ->
                                        val downloadState = episodeDownloadStates[episode.id]?.status.toAIEpisodeDownloadState()
                                        AIEpisodeCard(
                                            episode = episode,
                                            onPlayClick = {
                                                // GH#38: Play episode via shared playback state
                                                viewModel.playAiSearchEpisode(episode)
                                                navController.navigate(Screen.Player.route)
                                            },
                                            onDownloadClick = { viewModel.onAiEpisodeDownloadClick(episode) },
                                            downloadState = downloadState
                                        )
                                    }
                                }

                                if (aiSearchResults.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Related Podcasts",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier
                                                .padding(top = if (aiEpisodeResults.isNotEmpty()) 8.dp else 0.dp)
                                                .testTag(TestTags.AI_SEARCH_PODCASTS)
                                        )
                                    }

                                    item {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            items(aiSearchResults, key = { it.podcastIndexId }) { podcast ->
                                                PodcastCard(
                                                    podcast = podcast,
                                                    onClick = {
                                                        navController.navigate(Screen.PodcastFeed.createRoute(podcast.podcastIndexId))
                                                    },
                                                    modifier = Modifier.width(150.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

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
                                    // GH#32: Navigate to feed screen to browse before subscribing
                                    navController.navigate(Screen.PodcastFeed.createRoute(podcast.podcastIndexId))
                                }
                            )
                        }
                    }
                }

                // GH#35: Hide trending when AI search is active with a query
                trendingPodcasts.isNotEmpty() && !(showAiSearch && aiQuery.isNotBlank()) -> {
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
                                    // GH#32: Navigate to feed screen to browse before subscribing
                                    navController.navigate(Screen.PodcastFeed.createRoute(podcast.podcastIndexId))
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

/**
 * Dialog for adding a podcast via RSS feed URL.
 */
@Composable
private fun RssFeedDialog(
    rssUrl: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                "Add RSS Feed",
                modifier = Modifier.testTag(TestTags.RSS_DIALOG_TITLE)
            )
        },
        text = {
            Column {
                Text(
                    "Enter the RSS feed URL of the podcast you want to add.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = rssUrl,
                    onValueChange = onUrlChange,
                    placeholder = { Text("https://example.com/feed.xml") },
                    singleLine = true,
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.RSS_URL_INPUT)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isLoading && rssUrl.isNotBlank(),
                modifier = Modifier.testTag(TestTags.RSS_SUBSCRIBE_BUTTON)
            ) {
                Text(if (isLoading) "Adding..." else "Subscribe")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
                modifier = Modifier.testTag(TestTags.RSS_CANCEL_BUTTON)
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag(TestTags.RSS_DIALOG)
    )
}

/**
 * Confirmation dialog shown before subscribing to a podcast.
 */
@Composable
private fun SubscribeConfirmationDialog(
    podcast: com.podcast.app.data.local.entities.Podcast,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Subscribe to Podcast?",
                modifier = Modifier.testTag(TestTags.SUBSCRIBE_CONFIRMATION_TITLE)
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Podcast artwork
                podcast.imageUrl?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = podcast.title,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .testTag(TestTags.SUBSCRIBE_CONFIRMATION_IMAGE),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Podcast name
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag(TestTags.SUBSCRIBE_CONFIRMATION_NAME)
                )

                // Podcast description preview
                podcast.description?.let { desc ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = desc.take(150) + if (desc.length > 150) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(TestTags.SUBSCRIBE_CONFIRMATION_DESCRIPTION)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TestTags.SUBSCRIBE_CONFIRM_BUTTON)
            ) {
                Text("Subscribe")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.SUBSCRIBE_CANCEL_BUTTON)
            ) {
                Text("Cancel")
            }
        },
        modifier = Modifier.testTag(TestTags.SUBSCRIBE_CONFIRMATION_DIALOG)
    )
}

/**
 * GH#35: Card component for displaying AI search episode results.
 * Shows episode title, podcast name, duration, and play/download actions.
 *
 * podcast-test-search-download: Added test tags for download button states.
 */
@Composable
private fun AIEpisodeCard(
    episode: AISearchService.AISearchEpisode,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    downloadState: AIEpisodeDownloadState = AIEpisodeDownloadState.NOT_DOWNLOADED,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TestTags.AI_EPISODE_TILE),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode/Podcast image
            val imageUrl = episode.imageUrl ?: episode.podcastImageUrl
            imageUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Episode details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = episode.podcastTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    episode.publishedAt?.let { timestamp ->
                        Text(
                            text = formatEpisodeDate(timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    episode.audioDuration?.let { duration ->
                        Text(
                            text = " - ${formatEpisodeDuration(duration)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action buttons with download state handling
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier.testTag(TestTags.AI_EPISODE_PLAY_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Download button with state-dependent display
            when (downloadState) {
                AIEpisodeDownloadState.DOWNLOADING -> {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(TestTags.AI_EPISODE_DOWNLOADING),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .testTag(TestTags.AI_EPISODE_DOWNLOAD_PROGRESS),
                            strokeWidth = 2.dp
                        )
                    }
                }
                AIEpisodeDownloadState.DOWNLOADED -> {
                    IconButton(
                        onClick = { /* Already downloaded - could show options */ },
                        modifier = Modifier.testTag(TestTags.AI_EPISODE_DOWNLOAD_COMPLETE)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                AIEpisodeDownloadState.NOT_DOWNLOADED -> {
                    IconButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.testTag(TestTags.AI_EPISODE_DOWNLOAD_BUTTON)
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
    }
}

/**
 * Download state for AI episode cards.
 */
enum class AIEpisodeDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED
}

/**
 * GH#38: Convert DownloadStatus to AIEpisodeDownloadState for UI display.
 */
private fun DownloadStatus?.toAIEpisodeDownloadState(): AIEpisodeDownloadState {
    return when (this) {
        DownloadStatus.COMPLETED -> AIEpisodeDownloadState.DOWNLOADED
        DownloadStatus.IN_PROGRESS, DownloadStatus.PENDING -> AIEpisodeDownloadState.DOWNLOADING
        DownloadStatus.FAILED, DownloadStatus.CANCELLED, null -> AIEpisodeDownloadState.NOT_DOWNLOADED
    }
}

/**
 * Format episode timestamp for display.
 */
private fun formatEpisodeDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Format episode duration in seconds to human-readable string.
 */
private fun formatEpisodeDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
