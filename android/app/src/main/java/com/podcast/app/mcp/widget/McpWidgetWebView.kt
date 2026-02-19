package com.podcast.app.mcp.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Custom WebView for hosting the MCP widget UI.
 *
 * Provides a JavaScript bridge for bidirectional communication between
 * the Android app and the embedded MCP widget HTML/JS.
 *
 * Security considerations:
 * - JavaScript is enabled (required for widget functionality)
 * - Only loads local assets (file:///android_asset/)
 * - All tool arguments are validated before execution
 * - JavascriptInterface methods are restricted and validated
 */
class McpWidgetWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "McpWidgetWebView"
        private const val WIDGET_URL = "file:///android_asset/mcp-widget/mcp-app.html"
        private const val BRIDGE_NAME = "AndroidBridge"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Listener for events from the widget.
     */
    interface WidgetEventListener {
        /**
         * Called when the widget requests a tool to be executed.
         * @param toolName The MCP tool name (e.g., "search_byterm", "episodes_byfeedid")
         * @param argsJson JSON string containing tool arguments
         */
        fun onToolCallRequest(toolName: String, argsJson: String)

        /**
         * Called when the user taps play on an episode.
         * @param episodeId The episode ID to play
         * @param title The episode title (for display)
         */
        fun onEpisodePlay(episodeId: Long, title: String)

        /**
         * Called when the user taps subscribe on a podcast.
         * @param podcastId The Podcast Index ID
         * @param title The podcast title (for display)
         */
        fun onPodcastSubscribe(podcastId: Long, title: String)

        /**
         * Called when the user requests a search action.
         */
        fun onSearchRequest()

        /**
         * Called when the widget has finished loading and is ready.
         */
        fun onWidgetReady()
    }

    var eventListener: WidgetEventListener? = null

    init {
        setupWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Configure WebView settings
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = false

            // Performance optimizations
            cacheMode = WebSettings.LOAD_NO_CACHE

            // Security: Disable features we don't need
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false

            // Mobile optimizations
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
        }

        // Add JavaScript interface
        addJavascriptInterface(AndroidBridgeInterface(), BRIDGE_NAME)

        // Configure WebView client
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Widget loaded: $url")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // Only allow loading our local asset
                return url?.startsWith("file:///android_asset/") != true
            }
        }

        // Set background color to match widget theme
        setBackgroundColor(android.graphics.Color.parseColor("#1a1a2e"))
    }

    /**
     * Load the MCP widget HTML.
     */
    fun loadWidget() {
        loadUrl(WIDGET_URL)
    }

    /**
     * Push a tool result to the widget for display.
     *
     * @param resultJson JSON string containing the tool result in MCP format:
     *   { "content": [{ "type": "text", "text": "{...}" }] }
     *   or direct data format:
     *   { "feeds": [...], "action": "search_podcasts" }
     */
    fun pushToolResult(resultJson: String) {
        // Escape for JavaScript
        val escaped = resultJson
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        scope.launch {
            evaluateJavascript("window.pushToolResult('$escaped');", null)
        }
    }

    /**
     * Push podcast search results to the widget.
     */
    fun pushPodcastResults(podcasts: List<PodcastResultItem>) {
        val data = mapOf(
            "action" to "search_podcasts",
            "feeds" to podcasts.map { it.toMap() }
        )
        pushToolResult(json.encodeToString(data))
    }

    /**
     * Push episode results to the widget.
     */
    fun pushEpisodeResults(episodes: List<EpisodeResultItem>) {
        val data = mapOf(
            "action" to "episodes_byfeedid",
            "items" to episodes.map { it.toMap() }
        )
        pushToolResult(json.encodeToString(data))
    }

    /**
     * Show an error message in the widget.
     */
    fun pushError(message: String) {
        val data = mapOf(
            "error" to true,
            "message" to message
        )
        pushToolResult(json.encodeToString(data))
    }

    /**
     * Show or hide the loading overlay in the widget.
     */
    fun setLoading(loading: Boolean) {
        val js = if (loading) {
            "document.getElementById('loading-overlay').classList.add('visible');"
        } else {
            "document.getElementById('loading-overlay').classList.remove('visible');"
        }
        scope.launch {
            evaluateJavascript(js, null)
        }
    }

    /**
     * Release resources when the view is detached.
     */
    fun release() {
        scope.cancel()
        removeJavascriptInterface(BRIDGE_NAME)
        stopLoading()
        destroy()
    }

    /**
     * JavaScript interface exposed to the widget.
     *
     * Security: All methods validate inputs before passing to the listener.
     */
    private inner class AndroidBridgeInterface {

        @JavascriptInterface
        fun onToolCallRequest(toolName: String, argsJson: String) {
            // Validate tool name (alphanumeric and underscore only)
            if (!toolName.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))) {
                Log.w(TAG, "Invalid tool name: $toolName")
                return
            }

            // Validate JSON format
            if (argsJson.isNotEmpty()) {
                try {
                    Json.parseToJsonElement(argsJson)
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid tool arguments JSON: ${e.message}")
                    return
                }
            }

            Log.d(TAG, "Tool call request: $toolName")
            scope.launch {
                eventListener?.onToolCallRequest(toolName, argsJson)
            }
        }

        @JavascriptInterface
        fun onEpisodePlay(episodeId: String, title: String) {
            val id = episodeId.toLongOrNull()
            if (id == null || id <= 0) {
                Log.w(TAG, "Invalid episode ID: $episodeId")
                return
            }

            // Sanitize title
            val safeTitle = title.take(500).replace(Regex("[<>]"), "")

            Log.d(TAG, "Episode play request: $id - $safeTitle")
            scope.launch {
                eventListener?.onEpisodePlay(id, safeTitle)
            }
        }

        @JavascriptInterface
        fun onPodcastSubscribe(podcastId: String, title: String) {
            val id = podcastId.toLongOrNull()
            if (id == null || id <= 0) {
                Log.w(TAG, "Invalid podcast ID: $podcastId")
                return
            }

            // Sanitize title
            val safeTitle = title.take(500).replace(Regex("[<>]"), "")

            Log.d(TAG, "Podcast subscribe request: $id - $safeTitle")
            scope.launch {
                eventListener?.onPodcastSubscribe(id, safeTitle)
            }
        }

        @JavascriptInterface
        fun onSearchRequest() {
            Log.d(TAG, "Search request")
            scope.launch {
                eventListener?.onSearchRequest()
            }
        }

        @JavascriptInterface
        fun onWidgetReady() {
            Log.d(TAG, "Widget ready")
            scope.launch {
                eventListener?.onWidgetReady()
            }
        }
    }
}

/**
 * Data class for podcast results to push to the widget.
 */
data class PodcastResultItem(
    val id: Long,
    val title: String,
    val author: String? = null,
    val artwork: String? = null,
    val episodeCount: Int = 0,
    val language: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "author" to author,
        "artwork" to artwork,
        "episodeCount" to episodeCount,
        "language" to language
    )
}

/**
 * Data class for episode results to push to the widget.
 */
data class EpisodeResultItem(
    val id: Long,
    val title: String,
    val feedTitle: String? = null,
    val image: String? = null,
    val datePublished: Long = 0,
    val duration: Int = 0
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
        "feedTitle" to feedTitle,
        "image" to image,
        "datePublished" to datePublished,
        "duration" to duration
    )
}
