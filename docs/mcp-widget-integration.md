# MCP UI Widget Integration Guide

## Overview

This document provides a comprehensive guide for integrating the PodcastIndex MCP UI widget from [cclawton/podcastindex-mcp-server](https://github.com/cclawton/podcastindex-mcp-server) into the Podcast 2.0 AI Player Android app. The widget provides a rich, interactive interface for podcast discovery that can be embedded via WebView with native Android integration.

## Source Repository Analysis

**Repository:** https://github.com/cclawton/podcastindex-mcp-server

### Architecture Summary

The podcastindex-mcp-server provides:
1. **MCP Server** (TypeScript) - 25+ tools across 9 modules for Podcast Index API access
2. **UI Widget** (HTML/TypeScript) - Interactive podcast/episode explorer with mobile bridge
3. **Type Definitions** - Complete TypeScript interfaces for Podcast Index data

---

## 1. MCP Tools Catalog (25+ Tools Across 9 Modules)

### 1.1 Search Tools (4 tools)

| Tool | Description | Parameters | Returns |
|------|-------------|------------|---------|
| `podcast_search_byterm` | Search podcasts by keyword/topic across title, author, description | `q` (required), `max`, `val`, `clean`, `fulltext` | `{count, feeds[]}` |
| `podcast_search_bytitle` | Title-specific search (more precise) | `q` (required), `max`, `clean`, `fulltext` | `{count, feeds[]}` |
| `podcast_search_byperson` | Find episodes featuring a specific person | `q` (required), `max`, `fulltext` | `{count, items[]}` |
| `podcast_search_music` | Search music-tagged podcasts | `q` (required), `max`, `clean`, `fulltext` | `{count, feeds[]}` |

### 1.2 Podcast Tools (6 tools)

| Tool | Description | Parameters | Returns |
|------|-------------|------------|---------|
| `podcast_get_byfeedid` | Get podcast by PodcastIndex feed ID | `id` (required) | `{feed, query}` |
| `podcast_get_byfeedurl` | Get podcast by feed URL | `url` (required) | `{feed, query}` |
| `podcast_get_byitunesid` | Get podcast by iTunes/Apple Podcasts ID | `id` (required) | `{feed, query}` |
| `podcast_get_byguid` | Get podcast by GUID | `guid` (required) | `{feed, query}` |
| `podcast_get_trending` | Get trending podcasts | `max`, `since`, `lang`, `cat`, `notcat` | `{count, feeds[]}` |
| `podcast_get_dead` | Get dead/inactive feeds | none | `{count, feeds[]}` |

### 1.3 Episode Tools (6 tools)

| Tool | Description | Parameters | Returns |
|------|-------------|------------|---------|
| `episode_get_byfeedid` | Get episodes for podcast by feed ID | `id` (required), `since`, `max`, `fulltext` | `{count, items[]}` |
| `episode_get_byfeedurl` | Get episodes for podcast by feed URL | `url` (required), `since`, `max`, `fulltext` | `{count, items[]}` |
| `episode_get_byid` | Get specific episode by ID | `id` (required), `fulltext` | `{episode}` |
| `episode_get_byguid` | Get episode by GUID (needs feedid/url/podcastguid) | `guid` (required), `feedid/feedurl/podcastguid`, `fulltext` | `{episode}` |
| `episode_get_live` | Get currently live podcast episodes | `max` | `{count, items[]}` |
| `episode_get_random` | Get random episodes | `max`, `lang`, `cat`, `notcat`, `fulltext` | `{count, items[]}` |

### 1.4 Recent Content Tools (3 tools)

| Tool | Description | Parameters | Returns |
|------|-------------|------------|---------|
| `recent_get_episodes` | Recently published episodes | `max`, `excludeString`, `before`, `fulltext` | `{count, items[]}` |
| `recent_get_feeds` | Recently updated feeds | `max`, `since`, `lang`, `cat`, `notcat` | `{count, feeds[]}` |
| `recent_get_newfeeds` | Newly added feeds | `max`, `since`, `desc` | `{count, feeds[]}` |

### 1.5 Value4Value Tools (3 tools, no auth required)

| Tool | Description | Parameters | Returns |
|------|-------------|------------|---------|
| `value_get_byfeedid` | Get V4V payment info by feed ID | `id` (required) | `{value}` |
| `value_get_byfeedurl` | Get V4V payment info by feed URL | `url` (required) | `{value}` |
| `value_get_bypodcastguid` | Get V4V payment info by GUID | `guid` (required) | `{value}` |

### 1.6 Stats & Categories (2 tools)

| Tool | Description | Parameters | Returns |
|------|-------------|------------|---------|
| `stats_get_current` | Get PodcastIndex database statistics | none | `{stats}` |
| `categories_list` | List all podcast categories | none | `{count, categories[]}` |

### 1.7 Hub Notifications (1 tool, no auth required)

| Tool | Description | Parameters | Returns |
|------|-------------|------------|---------|
| `hub_pubnotify` | Notify hub of feed update | `id` or `url` (one required) | `{status, description}` |

---

## 2. UI Widget Structure

### 2.1 File Structure

```
podcastindex-mcp-server/
├── mcp-app.html          # Main widget HTML/CSS
├── src/
│   ├── mcp-app.ts        # Widget logic (dual MCP/mobile mode)
│   ├── types.ts          # TypeScript interfaces
│   ├── index.ts          # MCP server entry point
│   └── tools/
│       ├── index.ts      # Tool aggregation + UI_TOOL_NAMES
│       ├── search.ts     # 4 search tools
│       ├── podcasts.ts   # 6 podcast tools
│       ├── episodes.ts   # 6 episode tools
│       ├── recent.ts     # 3 recent tools
│       ├── value.ts      # 3 V4V tools
│       ├── stats.ts      # 1 stats tool
│       ├── categories.ts # 1 categories tool
│       └── hub.ts        # 1 hub tool
```

### 2.2 Widget DOM Structure

```html
<div class="container">
  <!-- Header -->
  <div class="header">results from podcastindex.org</div>

  <!-- Error Banner (hidden by default) -->
  <div class="error-banner"></div>

  <!-- Tab Bar -->
  <div class="tabs">
    <button class="tab active" data-tab="search">Podcasts</button>
    <button class="tab" data-tab="episodes">Episodes</button>
  </div>

  <!-- Search Tab -->
  <div class="tab-container active" data-tab="search">
    <div class="toolbar">
      <input class="search-input" placeholder="Filter results..." />
      <span class="count-label"></span>
    </div>
    <table>
      <thead class="sortable"></thead>
      <tbody></tbody>
    </table>
    <div class="pagination">...</div>
  </div>

  <!-- Episodes Tab -->
  <div class="tab-container" data-tab="episodes">
    <!-- Same structure as search tab -->
  </div>

  <!-- Loading Overlay -->
  <div class="loading"><div class="spinner"></div></div>
</div>
```

### 2.3 Widget Features

- **Dual-Mode Operation**: Works with MCP host OR standalone mobile mode
- **Tab Navigation**: Podcasts (search results) / Episodes views
- **Sortable Columns**: Click headers to sort by any column
- **Client-Side Filtering**: Real-time filter with 300ms debounce
- **Pagination**: 25 rows per page with navigation
- **Thumbnail Support**: Podcast/episode artwork display
- **Clickable Podcast Titles**: Click to fetch episodes for that podcast

---

## 3. Mobile Bridge Pattern

### 3.1 Three Global Functions

The widget exposes three `window` globals for mobile app integration:

#### `window._mcpWidgetReady` (boolean)
```typescript
// Set to true when widget is fully initialized
// Mobile app should poll this before sending data
window._mcpWidgetReady = true;
```

#### `window.pushToolResult(json: string)` (input)
```typescript
// Mobile app calls this to push MCP tool results into the widget
// Bypasses MCP host entirely
window.pushToolResult = (jsonString: string) => {
  const result: ToolResult = JSON.parse(jsonString);
  // result = { content: [{ type: "text", text: "..." }] }
  handleToolResult(JSON.parse(result.content[0].text));
};
```

#### `window.onToolCallRequest(json: string)` (output)
```typescript
// Widget calls this when user triggers an action (e.g., click podcast to get episodes)
// Only used in standalone mode (no MCP host)
// Native code should implement this
window.onToolCallRequest = (jsonString: string) => {
  // jsonString = { "name": "episode_get_byfeedid", "arguments": { "id": 12345 } }
  // Native code executes tool and calls pushToolResult() with response
};
```

### 3.2 Mode Detection Flow

```typescript
// On widget load:
async function init() {
  try {
    // Try to connect to MCP host (1500ms timeout)
    await app.connect();
    // If successful: MCP mode
  } catch {
    // Fallback: standalone mode
    // Rely on pushToolResult/onToolCallRequest
  }
  window._mcpWidgetReady = true;
}
```

### 3.3 Tool Execution Flow

```typescript
async function callTool(name: string, args: object): Promise<any> {
  if (mcpConnected) {
    // MCP mode: call through MCP host
    const result = await app.callServerTool(name, args);
    return parseToolResult(result);
  } else {
    // Standalone mode: delegate to native
    window.onToolCallRequest(JSON.stringify({ name, arguments: args }));
    return null; // Native will call pushToolResult() async
  }
}
```

---

## 4. Data Types

### 4.1 Podcast Interface

```typescript
interface Podcast {
  // Basic info
  id: number;                   // PodcastIndex feed ID
  title: string;
  url: string;                  // RSS feed URL
  description: string;
  author: string;

  // Media
  image: string;                // Original image URL
  artwork: string;              // High-quality artwork URL
  language: string;

  // Tracking
  lastUpdateTime: number;       // Unix timestamp
  lastCrawlTime: number;
  crawlErrors: number;
  parseErrors: number;

  // Engagement
  trendScore?: number;
  episodeCount: number;
  explicit: boolean;

  // Additional
  link?: string;                // Website URL
  itunesId?: number;
  podcastGuid?: string;
  categories?: Record<string, string>;
  funding?: Array<{ url: string; title: string }>;
  value?: Value;
}
```

### 4.2 Episode Interface

```typescript
interface Episode {
  // Content
  id: number;                   // PodcastIndex episode ID
  feedId: number;               // Parent podcast ID
  title: string;
  description: string;
  guid: string;
  link?: string;

  // Publishing
  datePublished: number;        // Unix timestamp (seconds)
  dateCrawled?: number;

  // Audio
  enclosureUrl: string;         // Audio file URL
  enclosureType: string;        // MIME type (audio/mpeg)
  enclosureLength: number;      // File size in bytes
  duration: number;             // Duration in seconds

  // Metadata
  season?: number;
  episode?: number;
  episodeType?: string;         // "full" | "trailer" | "bonus"
  explicit: boolean;

  // Podcast 2.0
  image?: string;
  chaptersUrl?: string;
  transcriptUrl?: string;
  persons?: Person[];
  soundbites?: Soundbite[];
  value?: Value;
  socialInteract?: SocialInteract[];
}
```

### 4.3 Supporting Types

```typescript
interface Soundbite {
  startTime: number;
  duration: number;
  title?: string;
}

interface Person {
  name: string;
  role?: string;
  group?: string;
  img?: string;
  href?: string;
}

interface Value {
  model: ValueModel;
  destinations: ValueDestination[];
}

interface ValueModel {
  type: string;       // "lightning"
  method: string;     // "keysend"
  suggested?: string; // "0.00000015"
}

interface ValueDestination {
  name: string;
  address: string;
  type: string;
  split: number;
}

interface Category {
  id: number;
  name: string;
}

interface Stats {
  feedCountTotal: number;
  episodeCountTotal: number;
  feedsWithNewEpisodes3days: number;
  feedsWithNewEpisodes10days: number;
  feedsWithNewEpisodes30days: number;
  feedsWithNewEpisodes90days: number;
  feedsWithValue: number;
}
```

---

## 5. Android WebView Implementation

### 5.1 WebView Setup

```kotlin
// McpWidgetView.kt
class McpWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    private lateinit var bridge: McpWidgetBridge

    init {
        setupWebView()
    }

    private fun setupWebView() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false  // Security: no file access
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // Add JavaScript interface
        bridge = McpWidgetBridge(context, this)
        addJavascriptInterface(bridge, "AndroidBridge")

        // Load widget
        loadUrl("file:///android_asset/mcp-widget/mcp-app.html")
    }

    fun pushToolResult(result: String) {
        post {
            evaluateJavascript("window.pushToolResult('${escapeForJs(result)}')", null)
        }
    }

    fun isWidgetReady(): Boolean = bridge.widgetReady

    private fun escapeForJs(s: String): String {
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
    }
}
```

### 5.2 JavaScript Interface

```kotlin
// McpWidgetBridge.kt
class McpWidgetBridge(
    private val context: Context,
    private val webView: McpWidgetView
) {
    var widgetReady: Boolean = false
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val podcastIndexClient: PodcastIndexClient by lazy {
        (context.applicationContext as PodcastApp).podcastIndexClient
    }

    @JavascriptInterface
    fun setWidgetReady(ready: Boolean) {
        widgetReady = ready
    }

    @JavascriptInterface
    fun onToolCallRequest(json: String) {
        scope.launch {
            val request = Json.decodeFromString<ToolCallRequest>(json)
            val result = executeToolCall(request)
            webView.pushToolResult(Json.encodeToString(result))
        }
    }

    private suspend fun executeToolCall(request: ToolCallRequest): ToolResult {
        return withContext(Dispatchers.IO) {
            when (request.name) {
                "podcast_search_byterm" -> {
                    val q = request.arguments["q"] as String
                    val max = (request.arguments["max"] as? Number)?.toInt() ?: 10
                    val response = podcastIndexClient.searchByTerm(q, max)
                    ToolResult(content = listOf(
                        TextContent(type = "text", text = Json.encodeToString(response))
                    ))
                }
                "episode_get_byfeedid" -> {
                    val id = (request.arguments["id"] as Number).toInt()
                    val max = (request.arguments["max"] as? Number)?.toInt() ?: 25
                    val response = podcastIndexClient.getEpisodesByFeedId(id, max)
                    ToolResult(content = listOf(
                        TextContent(type = "text", text = Json.encodeToString(response))
                    ))
                }
                // ... implement all 25+ tools
                else -> {
                    ToolResult(content = listOf(
                        TextContent(type = "text", text = """{"error":true,"message":"Unknown tool: ${request.name}"}""")
                    ))
                }
            }
        }
    }
}

@Serializable
data class ToolCallRequest(
    val name: String,
    val arguments: Map<String, JsonElement>
)

@Serializable
data class ToolResult(
    val content: List<TextContent>
)

@Serializable
data class TextContent(
    val type: String,
    val text: String
)
```

### 5.3 Widget Asset Bundling

```kotlin
// In build.gradle.kts
android {
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}
```

**Asset Structure:**
```
app/src/main/assets/mcp-widget/
├── mcp-app.html          # Copy from source repo
├── mcp-app.js            # Compiled from mcp-app.ts
└── mcp-app.css           # Extracted from HTML or inline
```

---

## 6. Security Considerations

### 6.1 WebView Security

```kotlin
// CRITICAL: Apply these security settings
settings.apply {
    // Enable JavaScript (required for widget)
    javaScriptEnabled = true

    // Disable file access (prevent local file exfiltration)
    allowFileAccess = false
    allowFileAccessFromFileURLs = false
    allowUniversalAccessFromFileURLs = false
    allowContentAccess = false

    // Block mixed content
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

    // Disable geolocation
    setGeolocationEnabled(false)
}
```

### 6.2 JavaScript Interface Security

```kotlin
// CRITICAL: Validate all input from JavaScript
@JavascriptInterface
fun onToolCallRequest(json: String) {
    // Validate JSON structure
    val request = try {
        Json.decodeFromString<ToolCallRequest>(json)
    } catch (e: Exception) {
        Log.e("McpBridge", "Invalid tool request JSON")
        return
    }

    // Validate tool name (whitelist)
    val allowedTools = setOf(
        "podcast_search_byterm",
        "podcast_search_bytitle",
        "episode_get_byfeedid",
        // ... all allowed tools
    )
    if (request.name !in allowedTools) {
        Log.w("McpBridge", "Blocked unknown tool: ${request.name}")
        return
    }

    // Validate arguments
    if (!validateArguments(request.name, request.arguments)) {
        Log.w("McpBridge", "Invalid arguments for ${request.name}")
        return
    }

    // Execute
    scope.launch { executeToolCall(request) }
}

private fun validateArguments(tool: String, args: Map<String, JsonElement>): Boolean {
    return when (tool) {
        "podcast_search_byterm" -> {
            val q = args["q"]?.jsonPrimitive?.contentOrNull
            q != null && q.length in 1..200 && !q.contains(Regex("[<>\"']"))
        }
        "episode_get_byfeedid" -> {
            val id = args["id"]?.jsonPrimitive?.intOrNull
            id != null && id > 0
        }
        // ... validate other tools
        else -> false
    }
}
```

### 6.3 Content Security

```kotlin
// Sanitize data before displaying in widget
private fun sanitizeForWidget(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
```

### 6.4 Network Security

- Widget loads from local assets only (no remote HTML)
- All API calls go through existing PodcastIndexClient with HTTPS
- No direct network access from WebView JavaScript

---

## 7. Integration Plan

### 7.1 Files to Sync from Source Repo

| Source File | Destination | Notes |
|-------------|-------------|-------|
| `mcp-app.html` | `assets/mcp-widget/` | May need style adjustments |
| `src/mcp-app.ts` | `assets/mcp-widget/` | Compile to JS, adapt bridge |
| `src/types.ts` | N/A | Reference for Kotlin data classes |

### 7.2 New Files to Create

```
android/app/src/main/
├── assets/
│   └── mcp-widget/
│       ├── mcp-app.html         # Widget HTML (copied)
│       ├── mcp-app.js           # Widget JS (compiled + modified)
│       └── styles.css           # Widget styles (optional split)
├── java/com/podcast/app/
│   └── mcp/
│       ├── widget/
│       │   ├── McpWidgetView.kt         # WebView wrapper
│       │   ├── McpWidgetBridge.kt       # JavaScript interface
│       │   └── McpWidgetScreen.kt       # Compose screen wrapper
│       └── tools/
│           └── ToolExecutor.kt          # Map widget tools to API
```

### 7.3 Implementation Steps

1. **Phase 1: Asset Setup**
   - Copy `mcp-app.html` to assets
   - Compile `mcp-app.ts` to JavaScript
   - Modify JS to call `AndroidBridge.onToolCallRequest()` instead of `window.onToolCallRequest()`
   - Add `AndroidBridge.setWidgetReady(true)` call when widget initializes

2. **Phase 2: WebView Component**
   - Create `McpWidgetView` with security settings
   - Implement `McpWidgetBridge` with `@JavascriptInterface` methods
   - Add input validation for all tool calls

3. **Phase 3: Tool Execution**
   - Create `ToolExecutor` class to map widget tools to existing `PodcastIndexClient`
   - Implement all 25+ tools using existing API methods
   - Add proper error handling and timeout management

4. **Phase 4: UI Integration**
   - Create `McpWidgetScreen` Compose wrapper
   - Add navigation to widget from Search screen
   - Handle episode selection to trigger playback

5. **Phase 5: Offline Support**
   - Cache tool results for offline access
   - Show cached data when network unavailable
   - Sync when network returns

### 7.4 Changes to Existing mcp-server/ Directory

The existing Python MCP server (`/workspaces/podcast-2-0-ai-player/mcp-server/`) focuses on:
- Playback control (play, pause, skip)
- Library management (subscribe, unsubscribe)
- Local app state (playback status, queue)

The new widget adds:
- Podcast/episode discovery (search, trending, recent)
- Rich UI for browsing results
- Podcast 2.0 features (V4V, chapters, transcripts)

**Recommended approach:** Keep both systems:
- Python MCP server: External control (Claude Desktop, voice assistants)
- Widget: In-app discovery with native integration

**Optional enhancements to Python server:**
- Add the 25+ discovery tools from podcastindex-mcp-server
- Share tool definitions via common schema file

---

## 8. Test Strategy

### 8.1 Unit Tests

```kotlin
// McpWidgetBridgeTest.kt
class McpWidgetBridgeTest {
    @Test
    fun `onToolCallRequest validates JSON structure`() {
        val bridge = McpWidgetBridge(mockContext, mockWebView)
        bridge.onToolCallRequest("{invalid json")
        // Verify no crash, no tool execution
    }

    @Test
    fun `onToolCallRequest blocks unknown tools`() {
        val bridge = McpWidgetBridge(mockContext, mockWebView)
        bridge.onToolCallRequest("""{"name":"dangerous_tool","arguments":{}}""")
        // Verify tool not executed
    }

    @Test
    fun `searchByTerm validates query length`() {
        val executor = ToolExecutor(mockClient)
        val result = executor.execute("podcast_search_byterm", mapOf("q" to ""))
        assertThat(result).containsError()
    }
}
```

### 8.2 Espresso Tests

```kotlin
// McpWidgetTest.kt (referenced in podcast-test-mcp-widget issue)
@HiltAndroidTest
class McpWidgetTest {
    @Test
    fun widgetLoadsSuccessfully() {
        // Navigate to widget
        // Wait for _mcpWidgetReady
        // Verify UI elements displayed
    }

    @Test
    fun searchResultsDisplayInWidget() {
        // Push mock search results via pushToolResult
        // Verify podcast cards appear
    }

    @Test
    fun episodeClickTriggersPlayback() {
        // Push episodes
        // Click episode row
        // Verify playback started
    }

    @Test
    fun widgetWorksOfflineWithCache() {
        // Pre-populate cache
        // Disable network
        // Load widget
        // Verify cached data displayed
    }
}
```

### 8.3 Integration Tests

```kotlin
// ToolExecutorIntegrationTest.kt
class ToolExecutorIntegrationTest {
    @Test
    fun `all 25 tools execute successfully`() {
        val tools = listOf(
            "podcast_search_byterm" to mapOf("q" to "technology"),
            "podcast_get_trending" to emptyMap(),
            // ... all tools
        )
        tools.forEach { (name, args) ->
            val result = executor.execute(name, args)
            assertThat(result).doesNotContainError()
        }
    }
}
```

---

## 9. Implementation Checklist

### Phase 1: Asset Setup
- [ ] Copy and adapt mcp-app.html
- [ ] Compile mcp-app.ts to mcp-app.js
- [ ] Modify JS bridge calls for Android
- [ ] Bundle assets in app

### Phase 2: WebView Component
- [ ] Create McpWidgetView with security settings
- [ ] Implement McpWidgetBridge @JavascriptInterface
- [ ] Add input validation for all methods
- [ ] Handle lifecycle (pause/resume WebView)

### Phase 3: Tool Execution
- [ ] Create ToolExecutor class
- [ ] Implement search tools (4)
- [ ] Implement podcast tools (6)
- [ ] Implement episode tools (6)
- [ ] Implement recent tools (3)
- [ ] Implement V4V tools (3)
- [ ] Implement stats/categories/hub tools (3)
- [ ] Add error handling and timeouts

### Phase 4: UI Integration
- [ ] Create McpWidgetScreen Compose wrapper
- [ ] Add navigation from Search screen
- [ ] Connect episode clicks to PlaybackController
- [ ] Handle podcast subscription from widget

### Phase 5: Testing
- [ ] Unit tests for McpWidgetBridge
- [ ] Unit tests for ToolExecutor
- [ ] Espresso tests for widget (McpWidgetTest)
- [ ] Integration tests for all tools

### Phase 6: Polish
- [ ] Offline caching
- [ ] Loading states
- [ ] Error states
- [ ] Dark mode support
- [ ] Accessibility

---

## 10. Related Issues

| Beads ID | Title | Relevance |
|----------|-------|-----------|
| `podcast-mcp-ui` | GH#41: Sync MCP tools with UI widget support | Main tracking issue |
| `podcast-test-mcp-widget` | Espresso tests for MCP UI widget | Test coverage |
| `podcast-00e` | Create PlaybackService for background playback | Widget episode playback |
| `podcast-alk` | GH#39: Single episode download from search | Widget episode actions |

---

## 11. References

- **Source Repository:** https://github.com/cclawton/podcastindex-mcp-server
- **Podcast Index API:** https://podcastindex-org.github.io/docs-api/
- **MCP Specification:** https://spec.modelcontextprotocol.io/
- **Android WebView Security:** https://developer.android.com/develop/ui/views/layout/webapps/security
