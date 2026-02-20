package com.podcast.app.ui.screens.mcpwidget

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.podcast.app.util.TestTags
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.podcast.app.mcp.widget.McpWidgetViewModel
import com.podcast.app.mcp.widget.McpWidgetWebView
import kotlinx.coroutines.flow.collectLatest

/**
 * Screen that hosts the MCP widget WebView.
 *
 * Provides a UI for exploring podcasts and episodes via the MCP protocol,
 * with actions to play episodes and subscribe to podcasts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpWidgetScreen(
    navController: NavController,
    viewModel: McpWidgetViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val isLoading by viewModel.isLoading.collectAsState()

    var webView by remember { mutableStateOf<McpWidgetWebView?>(null) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Handle widget events
    LaunchedEffect(webView) {
        webView?.let { wv ->
            viewModel.widgetEvents.collectLatest { event ->
                when (event) {
                    is McpWidgetViewModel.WidgetEvent.PushResult -> {
                        wv.pushToolResult(event.json)
                    }
                    is McpWidgetViewModel.WidgetEvent.ShowError -> {
                        wv.pushError(event.message)
                    }
                    McpWidgetViewModel.WidgetEvent.ShowLoading -> {
                        wv.setLoading(true)
                    }
                    McpWidgetViewModel.WidgetEvent.HideLoading -> {
                        wv.setLoading(false)
                    }
                }
            }
        }
    }

    // Handle UI events
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                is McpWidgetViewModel.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is McpWidgetViewModel.UiEvent.NavigateToSearch -> {
                    showSearchDialog = true
                }
                McpWidgetViewModel.UiEvent.NavigateBack -> {
                    navController.popBackStack()
                }
            }
        }
    }

    // Search dialog
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Podcasts") },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search term") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.searchPodcasts(searchQuery)
                            showSearchDialog = false
                        }
                    }
                ) {
                    Text("Search")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.MCP_WIDGET_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("MCP Explorer") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = { viewModel.loadInitialData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Loading indicator
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TestTags.MCP_WIDGET_LOADING)
                )
            }

            // WebView
            Box(modifier = Modifier.fillMaxSize().testTag(TestTags.MCP_WIDGET_WEBVIEW)) {
                AndroidView(
                    factory = { ctx ->
                        McpWidgetWebView(ctx).apply {
                            webView = this

                            eventListener = object : McpWidgetWebView.WidgetEventListener {
                                override fun onToolCallRequest(toolName: String, argsJson: String) {
                                    viewModel.onToolCallRequest(toolName, argsJson)
                                }

                                override fun onEpisodePlay(episodeId: Long, title: String) {
                                    viewModel.onEpisodePlay(episodeId, title)
                                }

                                override fun onPodcastSubscribe(podcastId: Long, title: String) {
                                    viewModel.onPodcastSubscribe(podcastId, title)
                                }

                                override fun onSearchRequest() {
                                    viewModel.onSearchRequest()
                                }

                                override fun onWidgetReady() {
                                    viewModel.loadInitialData()
                                }
                            }

                            loadWidget()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { /* WebView state is managed internally */ }
                )
            }
        }
    }

    // Cleanup WebView on dispose
    DisposableEffect(Unit) {
        onDispose {
            webView?.release()
        }
    }
}
