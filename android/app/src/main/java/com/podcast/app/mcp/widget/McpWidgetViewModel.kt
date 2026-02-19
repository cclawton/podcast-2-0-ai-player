package com.podcast.app.mcp.widget

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the MCP Widget screen.
 *
 * Coordinates between the WebView widget and Android services.
 */
@HiltViewModel
class McpWidgetViewModel @Inject constructor(
    private val toolExecutor: McpToolExecutor
) : ViewModel() {

    companion object {
        private const val TAG = "McpWidgetViewModel"
    }

    /**
     * Sealed class representing events to send to the WebView.
     */
    sealed class WidgetEvent {
        data class PushResult(val json: String) : WidgetEvent()
        data class ShowError(val message: String) : WidgetEvent()
        data object ShowLoading : WidgetEvent()
        data object HideLoading : WidgetEvent()
    }

    /**
     * Sealed class representing UI events.
     */
    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        data class NavigateToSearch(val query: String? = null) : UiEvent()
        data object NavigateBack : UiEvent()
    }

    private val _widgetEvents = MutableSharedFlow<WidgetEvent>()
    val widgetEvents: SharedFlow<WidgetEvent> = _widgetEvents.asSharedFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Handle a tool call request from the widget.
     */
    fun onToolCallRequest(toolName: String, argsJson: String) {
        viewModelScope.launch {
            Log.d(TAG, "Tool call: $toolName with args: $argsJson")
            _isLoading.value = true
            _widgetEvents.emit(WidgetEvent.ShowLoading)

            try {
                val result = toolExecutor.execute(toolName, argsJson)
                val json = resultToJson(result)
                _widgetEvents.emit(WidgetEvent.PushResult(json))
            } catch (e: Exception) {
                Log.e(TAG, "Tool execution failed", e)
                _widgetEvents.emit(WidgetEvent.ShowError(e.message ?: "Unknown error"))
            } finally {
                _isLoading.value = false
                _widgetEvents.emit(WidgetEvent.HideLoading)
            }
        }
    }

    /**
     * Handle episode play request from the widget.
     */
    fun onEpisodePlay(episodeId: Long, title: String) {
        viewModelScope.launch {
            Log.d(TAG, "Play episode: $episodeId - $title")
            _isLoading.value = true

            try {
                val result = toolExecutor.playEpisode(episodeId)
                when (result) {
                    is McpToolExecutor.ToolResult.Success -> {
                        _uiEvents.emit(UiEvent.ShowSnackbar("Playing: $title"))
                    }
                    is McpToolExecutor.ToolResult.Error -> {
                        _uiEvents.emit(UiEvent.ShowSnackbar("Failed: ${result.message}"))
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Play failed", e)
                _uiEvents.emit(UiEvent.ShowSnackbar("Failed to play episode"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Handle podcast subscribe request from the widget.
     */
    fun onPodcastSubscribe(podcastId: Long, title: String) {
        viewModelScope.launch {
            Log.d(TAG, "Subscribe podcast: $podcastId - $title")
            _isLoading.value = true

            try {
                val result = toolExecutor.subscribeToPodcast(podcastId)
                when (result) {
                    is McpToolExecutor.ToolResult.Success -> {
                        _uiEvents.emit(UiEvent.ShowSnackbar(result.message))
                    }
                    is McpToolExecutor.ToolResult.Error -> {
                        _uiEvents.emit(UiEvent.ShowSnackbar("Failed: ${result.message}"))
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Subscribe failed", e)
                _uiEvents.emit(UiEvent.ShowSnackbar("Failed to subscribe"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Handle search request from the widget.
     */
    fun onSearchRequest() {
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.NavigateToSearch())
        }
    }

    /**
     * Load initial data for the widget.
     */
    fun loadInitialData() {
        viewModelScope.launch {
            Log.d(TAG, "Loading initial data")
            _isLoading.value = true

            try {
                // Load subscribed podcasts
                val result = toolExecutor.execute("get_subscribed", "")
                val json = resultToJson(result)
                _widgetEvents.emit(WidgetEvent.PushResult(json))
            } catch (e: Exception) {
                Log.e(TAG, "Initial load failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Search podcasts and push results to widget.
     */
    fun searchPodcasts(query: String) {
        viewModelScope.launch {
            Log.d(TAG, "Searching: $query")
            _isLoading.value = true
            _widgetEvents.emit(WidgetEvent.ShowLoading)

            try {
                val argsJson = """{"q": "$query", "max": 20}"""
                val result = toolExecutor.execute("search_byterm", argsJson)
                val json = resultToJson(result)
                _widgetEvents.emit(WidgetEvent.PushResult(json))
            } catch (e: Exception) {
                Log.e(TAG, "Search failed", e)
                _widgetEvents.emit(WidgetEvent.ShowError(e.message ?: "Search failed"))
            } finally {
                _isLoading.value = false
                _widgetEvents.emit(WidgetEvent.HideLoading)
            }
        }
    }

    /**
     * Convert a tool result to JSON for the widget.
     */
    private fun resultToJson(result: McpToolExecutor.ToolResult): String {
        return when (result) {
            is McpToolExecutor.ToolResult.Podcasts -> {
                val feeds = result.items.map { it.toMap() }
                """{"action":"search_podcasts","feeds":${feeds.toJsonArray()}}"""
            }
            is McpToolExecutor.ToolResult.Episodes -> {
                val items = result.items.map { it.toMap() }
                """{"action":"episodes_byfeedid","items":${items.toJsonArray()}}"""
            }
            is McpToolExecutor.ToolResult.Success -> {
                """{"success":true,"message":"${escapeJson(result.message)}"}"""
            }
            is McpToolExecutor.ToolResult.Error -> {
                """{"error":true,"message":"${escapeJson(result.message)}"}"""
            }
        }
    }

    private fun List<Map<String, Any?>>.toJsonArray(): String {
        return joinToString(",", "[", "]") { map ->
            map.entries.joinToString(",", "{", "}") { (k, v) ->
                when (v) {
                    null -> "\"$k\":null"
                    is String -> "\"$k\":\"${escapeJson(v)}\""
                    is Number -> "\"$k\":$v"
                    else -> "\"$k\":\"${escapeJson(v.toString())}\""
                }
            }
        }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
