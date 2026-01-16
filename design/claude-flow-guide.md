# Podcast 2.0 App - Integration Guide for Claude-Flow

## Overview

This document provides a complete blueprint for feeding this architecture into a claude-flow automated code generation process. All components are self-contained, testable, and ready for parallel development.

---

## Directory Structure for Claude-Flow

```
podcast-app-src/
├── docs/
│   ├── ARCHITECTURE.md           # System overview
│   ├── API_SPEC.md               # Podcast Index API
│   ├── DATABASE_SCHEMA.md         # SQLite schema
│   ├── MCP_DESIGN.md              # MCP layer spec
│   └── COMPONENT_SPEC.md          # UI/Logic components
│
├── android/
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/podcast/app/
│   │   │   │   ├── ui/
│   │   │   │   │   ├── screens/
│   │   │   │   │   │   ├── PodcastFeedScreen.kt
│   │   │   │   │   │   ├── LibraryScreen.kt
│   │   │   │   │   │   ├── PlayerScreen.kt
│   │   │   │   │   │   └── SearchScreen.kt
│   │   │   │   │   └── composables/
│   │   │   │   │       ├── PlayerControls.kt
│   │   │   │   │       ├── EpisodeCard.kt
│   │   │   │   │       └── TranscriptViewer.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── db/
│   │   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── PodcastDao.kt
│   │   │   │   │   │   │   ├── EpisodeDao.kt
│   │   │   │   │   │   │   └── PlaybackProgressDao.kt
│   │   │   │   │   │   └── entity/
│   │   │   │   │   │       ├── Podcast.kt
│   │   │   │   │   │       └── Episode.kt
│   │   │   │   │   ├── api/
│   │   │   │   │   │   └── PodcastIndexClient.kt
│   │   │   │   │   └── repository/
│   │   │   │   │       ├── PodcastRepository.kt
│   │   │   │   │       └── EpisodeRepository.kt
│   │   │   │   ├── domain/
│   │   │   │   │   └── usecase/
│   │   │   │   │       ├── SearchPodcastsUseCase.kt
│   │   │   │   │       └── GetPlaybackStatusUseCase.kt
│   │   │   │   ├── playback/
│   │   │   │   │   ├── PlaybackController.kt
│   │   │   │   │   └── DownloadManager.kt
│   │   │   │   ├── mcp/
│   │   │   │   │   ├── MCPActionReceiver.kt
│   │   │   │   │   └── MCPSocketService.kt
│   │   │   │   └── PodcastApp.kt (Application class)
│   │   │   └── res/
│   │   ├── build.gradle.kts
│   │   └── AndroidManifest.xml
│   └── gradle/
│       └── libs.versions.toml
│
├── mcp-server/
│   ├── server.py                 # FastMCP server implementation
│   ├── android_bridge.py         # IPC communication layer
│   ├── models.py                 # Pydantic models
│   ├── requirements.txt
│   ├── config.yaml               # Server configuration
│   └── tests/
│       └── test_mcp.py           # Unit tests
│
└── README.md                      # Quick start guide
```

---

## Claude-Flow Generation Workflow

### Phase 1: Database & Data Models

**Prompt 1.1: Generate Room Entities**
```
File: docs/DATABASE_SCHEMA.md
Task: Generate Kotlin data classes for Room ORM using these specifications:
- podcasts table → Podcast.kt entity with fields: podcast_index_id, title, feed_url, image_url, etc.
- episodes table → Episode.kt entity with: episode_index_id, podcast_id (FK), title, audio_url, transcript_url, chapters_json
- playback_progress table → PlaybackProgress.kt
- Include all @Entity, @PrimaryKey, @ForeignKey annotations
- Add @ColumnInfo for custom column names
- Add serialization support (kotlinx.serialization)
Output: android/app/src/main/java/com/podcast/app/data/db/entity/
```

**Prompt 1.2: Generate Room DAOs**
```
File: docs/DATABASE_SCHEMA.md + Kotlin entities
Task: Generate Data Access Objects for Room:
- PodcastDao.kt with methods: insertPodcast(), getSubscribedPodcasts(), getPodcastByIndexId(), removePodcast()
- EpisodeDao.kt with: insertEpisode(), getEpisodesByPodcast(), getEpisodeById(), updateEpisode()
- PlaybackProgressDao.kt with: updateProgress(), getProgress(), getLastPlayedUncompletedEpisode()
- Use Flow<> for reactive queries
- Include proper @Insert, @Update, @Query annotations
- Add indexes for performance queries
Output: android/app/src/main/java/com/podcast/app/data/db/dao/
```

**Prompt 1.3: Generate AppDatabase**
```
File: docs/DATABASE_SCHEMA.md + generated entities + DAOs
Task: Generate Room Database class:
- AppDatabase.kt with @Database annotation
- Include all 5 entities (Podcast, Episode, PlaybackProgress, Download, SearchHistory)
- Create abstract methods returning DAOs
- Set version = 1
- Include migration path for future versions
- Create database builder with proper error handling
Output: android/app/src/main/java/com/podcast/app/data/db/AppDatabase.kt
```

### Phase 2: API Integration

**Prompt 2.1: Generate Podcast Index API Client**
```
File: docs/API_SPEC.md
Task: Generate Retrofit API client for Podcast Index:
- PodcastIndexClient.kt interface with methods:
  * searchByTerm(query: String, max: Int): SearchResponse
  * getPodcastById(id: Int): PodcastMetadata
  * getEpisodesByFeedId(feedId: Int, page: Int): PaginatedEpisodes
  * getRecentEpisodes(maxResults: Int): List<Episode>
- Create response data classes matching API schema
- Add authentication interceptor (API_KEY + API_SECRET)
- Include rate limiting headers handling
- Add OkHttp logging interceptor for development
Output: android/app/src/main/java/com/podcast/app/data/api/
```

**Prompt 2.2: Generate API Response Models**
```
File: docs/API_SPEC.md
Task: Generate kotlinx.serialization data classes for Podcast Index API responses:
- SearchResponse
- PodcastMetadata (with funding, categories, etc.)
- EpisodeDetail (with chapters, transcripts, duration)
- PaginatedEpisodes
- Add @Serializable, @SerialName annotations for JSON mapping
Output: android/app/src/main/java/com/podcast/app/data/api/model/
```

### Phase 3: Repository Pattern

**Prompt 3.1: Generate Repositories**
```
File: docs/COMPONENT_SPEC.md + DAOs + API Client
Task: Generate repository classes that abstract API + local DB:
- PodcastRepository.kt with:
  * getSubscribedPodcasts(): Flow<List<Podcast>> (from local cache)
  * searchPodcasts(query: String): List<Podcast> (API → cache → return)
  * addPodcast(podcast: Podcast) (save locally)
  * removePodcast(id: Int) (delete from DB)
- EpisodeRepository.kt with:
  * getEpisodes(podcastId: Int): Flow<List<Episode>>
  * getPlaybackProgress(episodeId: Int): Flow<PlaybackProgress>
  * updatePlaybackProgress()
- Use Hilt @Singleton, @Provides
Output: android/app/src/main/java/com/podcast/app/data/repository/
```

### Phase 4: ViewModels & State Management

**Prompt 4.1: Generate ViewModels**
```
File: Component specs + repositories
Task: Generate Jetpack ViewModel classes:
- PodcastLibraryViewModel.kt
  * subscribed podcasts: StateFlow<List<Podcast>>
  * loadSubscribedPodcasts()
  * removePodcast(id: Int)
- EpisodeListViewModel.kt
  * episodes: StateFlow<List<Episode>>
  * loadEpisodes(podcastId: Int)
- PlayerViewModel.kt
  * playbackStatus: StateFlow<PlaybackStatus>
  * play(episodeId: Int)
  * pause()
  * setSpeed(speed: Float)
- SearchViewModel.kt
  * searchResults: StateFlow<List<Podcast>>
  * search(query: String)
- Use Hilt for dependency injection
- Include proper error handling
Output: android/app/src/main/java/com/podcast/app/ui/viewmodel/
```

### Phase 5: Jetpack Compose UI

**Prompt 5.1: Generate Compose Screens**
```
File: Component specs + ViewModels
Task: Generate Jetpack Compose screens:
- LibraryScreen.kt: Display subscribed podcasts in a grid, swipe to unsubscribe
- SearchScreen.kt: Search bar + results, tap to add to library
- EpisodeListScreen.kt: Episodes for a podcast, sorted by publish date descending
- PlayerScreen.kt: Now playing, seek bar, speed control, chapters
- SettingsScreen.kt: App preferences, LLM settings, cache management
Use:
- Compose Foundation, Material3
- State hoisting with ViewModels
- Lazy grids/lists for performance
- Proper theme support (light/dark)
Output: android/app/src/main/java/com/podcast/app/ui/screens/
```

**Prompt 5.2: Generate Compose Components**
```
File: Component specs
Task: Generate reusable Compose components:
- PodcastCard.kt: Shows podcast title, image, unplayed count
- EpisodeCard.kt: Episode title, published date, duration
- PlayerControls.kt: Play/pause, skip, speed buttons
- TranscriptViewer.kt: Searchable transcript display
- ChapterNavigation.kt: Jump to chapter
- ProgressIndicator.kt: Download progress bar
Use standard Compose patterns, preview composables
Output: android/app/src/main/java/com/podcast/app/ui/composable/
```

### Phase 6: Audio Playback

**Prompt 6.1: Generate PlaybackController**
```
Task: Generate ExoPlayer integration:
- PlaybackController.kt singleton:
  * init(context: Context) - Initialize ExoPlayer
  * playEpisode(episodeId: Int, startPosition: Long)
  * pause(), resume(), stop()
  * skipForward(seconds: Int), skipBackward(seconds: Int)
  * setPlaybackSpeed(speed: Float)
  * getPlaybackState(): StateFlow<PlaybackStatus>
  * savePlaybackPosition() on pause
- Handle local + remote audio (HTTP + local files)
- Implement media session controls
Output: android/app/src/main/java/com/podcast/app/playback/
```

**Prompt 6.2: Generate DownloadManager**
```
Task: Generate offline episode download manager:
- DownloadManager.kt:
  * enqueueDownload(episode: Episode)
  * downloadEpisode(episode: Episode): Flow<DownloadProgress>
  * getDownloadStatus(episodeId: Int): Flow<Int> // % complete
  * deleteDownload(episodeId: Int)
  * getDownloadedEpisodes(): Flow<List<Episode>>
- Use OkHttp for resumable downloads
- Store in app cache directory
- Update DB with download status
Output: android/app/src/main/java/com/podcast/app/playback/
```

### Phase 7: Podcast 2.0 Support

**Prompt 7.1: Generate P2.0 Metadata Parser**
```
Task: Generate Podcast 2.0 XML tag parser:
- P2MetadataParser.kt:
  * parseChapters(chaptersJson: String): List<Chapter>
  * parseTranscript(transcriptUrl: String): Transcript
  * parseFunding(fundingJson: String): List<Funding>
  * parseValueForValue(valueJson: String): ValueBlock
- Handle VTT, WebVTT, JSON transcript formats
- Create Chapter, Transcript data classes
Output: android/app/src/main/java/com/podcast/app/data/p2/
```

**Prompt 7.2: Generate Transcript UI**
```
Task: Generate transcript display Compose component:
- TranscriptViewer.kt:
  * Displays transcript with speaker names
  * Tappable timestamps jump to playback position
  * Searchable (highlight matches)
  * Dark/light theme support
- ChapterNavigation.kt for chapters
Output: android/app/src/main/java/com/podcast/app/ui/composable/
```

### Phase 8: MCP Layer

**Prompt 8.1: Generate MCP Server**
```
File: docs/MCP_DESIGN.md
Task: Generate Python FastMCP server:
- server.py with:
  * 15+ tools for podcast control (play, pause, search, etc.)
  * 3+ resources (library, playback-status, queue)
  * 3+ prompts (driving_mode, recommendations)
  * AndroidAppBridge class for IPC
- Use async/await throughout
- Proper error handling
- Logging for debugging
Output: mcp-server/server.py
```

**Prompt 8.2: Generate Android MCP Bridge**
```
Task: Generate Android IPC receiver:
- MCPActionReceiver.kt:
  * BroadcastReceiver for MCP actions
  * Routes to PlaybackController
  * Return results via broadcast or socket
- MCPSocketService.kt:
  * Listen on local socket
  * Handle JSON request/response
Output: android/app/src/main/java/com/podcast/app/mcp/
```

### Phase 9: Build & Configuration

**Prompt 9.1: Generate Gradle Configuration**
```
Task: Generate build.gradle.kts:
- Dependencies: ExoPlayer, Retrofit2, Room, Jetpack Compose, Hilt, OkHttp
- Version catalog (libs.versions.toml)
- BuildTypes (debug with logcat, release optimized)
- Compose compiler version
Output: android/build.gradle.kts + libs.versions.toml
```

**Prompt 9.2: Generate AndroidManifest**
```
Task: Generate AndroidManifest.xml:
- Permissions: INTERNET, RECORD_AUDIO, READ/WRITE_EXTERNAL_STORAGE
- Broadcast receiver registration for MCP
- Service for socket IPC
- Content provider for OPML import/export
- Minimum API 28 (Pie), target 35
Output: android/app/src/main/AndroidManifest.xml
```

---

## Testing Strategy

### Prompt 10.1: Generate Unit Tests

```
Task: Generate unit tests for each component:
- PodcastRepositoryTest.kt: Mock API, test caching
- EpisodeRepositoryTest.kt: Test playback progress tracking
- PlaybackControllerTest.kt: Test state transitions
- DownloadManagerTest.kt: Test download resumption
- P2MetadataParserTest.kt: Test XML parsing
Use: JUnit4, Mockk, turbine (Flow testing)
Output: android/app/src/test/
```

### Prompt 10.2: Generate Integration Tests

```
Task: Generate Android integration tests:
- DatabaseTest.kt: Room database queries
- PodcastIndexClientTest.kt: API mocking
- UIScreenTest.kt: Compose preview snapshots
Use: JUnit4, Robolectric, Compose test harness
Output: android/app/src/androidTest/
```

---

## Parallel Execution Plan

You can request multiple components in parallel (not sequential):

```
Parallel Group 1 (Database):
├─ Generate Podcast entity
├─ Generate Episode entity
├─ Generate PodcastDao
├─ Generate EpisodeDao
└─ Generate AppDatabase

Parallel Group 2 (API):
├─ Generate PodcastIndexClient
└─ Generate API response models

Parallel Group 3 (UI):
├─ Generate LibraryViewModel
├─ Generate PlayerViewModel
├─ Generate SearchViewModel
├─ Generate all Screens (parallel)
└─ Generate all Components (parallel)

Parallel Group 4 (Audio):
├─ Generate PlaybackController
└─ Generate DownloadManager

Parallel Group 5 (MCP):
├─ Generate Python MCP server
└─ Generate Android MCP bridge
```

---

## Example Claude-Flow Command

```yaml
# claude-flow-podcast.yaml

project:
  name: "podcast-2-0-app"
  description: "Open-source Podcast 2.0 app with MCP"

phases:
  - phase: foundation
    parallel: true
    tasks:
      - name: "room-entities"
        prompt_file: "prompts/room_entities.txt"
        input: "docs/DATABASE_SCHEMA.md"
        output: "android/app/src/main/java/com/podcast/app/data/db/entity/"
      
      - name: "room-daos"
        prompt_file: "prompts/room_daos.txt"
        input: "docs/DATABASE_SCHEMA.md"
        depends_on: ["room-entities"]
        output: "android/app/src/main/java/com/podcast/app/data/db/dao/"
      
      - name: "podcast-index-client"
        prompt_file: "prompts/api_client.txt"
        input: "docs/API_SPEC.md"
        output: "android/app/src/main/java/com/podcast/app/data/api/"

  - phase: viewmodels
    parallel: true
    tasks:
      - name: "library-viewmodel"
        prompt_file: "prompts/library_vm.txt"
        depends_on: ["room-daos"]
        output: "android/app/src/main/java/com/podcast/app/ui/viewmodel/"
      
      - name: "player-viewmodel"
        prompt_file: "prompts/player_vm.txt"
        depends_on: ["room-daos"]
        output: "android/app/src/main/java/com/podcast/app/ui/viewmodel/"
      
      - name: "search-viewmodel"
        prompt_file: "prompts/search_vm.txt"
        depends_on: ["podcast-index-client"]
        output: "android/app/src/main/java/com/podcast/app/ui/viewmodel/"

  - phase: ui
    parallel: true
    tasks:
      - name: "screens"
        prompt_file: "prompts/compose_screens.txt"
        input: ["docs/COMPONENT_SPEC.md", "generated/viewmodels/"]
        output: "android/app/src/main/java/com/podcast/app/ui/screens/"
      
      - name: "components"
        prompt_file: "prompts/compose_components.txt"
        input: "docs/COMPONENT_SPEC.md"
        output: "android/app/src/main/java/com/podcast/app/ui/composable/"

  - phase: audio
    parallel: true
    tasks:
      - name: "playback-controller"
        prompt_file: "prompts/playback_controller.txt"
        depends_on: ["room-daos"]
        output: "android/app/src/main/java/com/podcast/app/playback/"
      
      - name: "download-manager"
        prompt_file: "prompts/download_manager.txt"
        depends_on: ["room-daos"]
        output: "android/app/src/main/java/com/podcast/app/playback/"

  - phase: mcp
    parallel: true
    tasks:
      - name: "mcp-server"
        prompt_file: "prompts/mcp_server.txt"
        input: "docs/MCP_DESIGN.md"
        output: "mcp-server/server.py"
      
      - name: "android-mcp-bridge"
        prompt_file: "prompts/android_mcp_bridge.txt"
        input: "docs/MCP_DESIGN.md"
        depends_on: ["playback-controller"]
        output: "android/app/src/main/java/com/podcast/app/mcp/"

  - phase: config
    tasks:
      - name: "gradle-config"
        prompt_file: "prompts/gradle.txt"
        output: "android/"
      
      - name: "manifest"
        prompt_file: "prompts/manifest.txt"
        output: "android/app/src/main/"

  - phase: tests
    parallel: true
    tasks:
      - name: "unit-tests"
        prompt_file: "prompts/unit_tests.txt"
        input: ["docs/", "generated/"]
        output: "android/app/src/test/"
      
      - name: "integration-tests"
        prompt_file: "prompts/integration_tests.txt"
        input: ["docs/", "generated/"]
        output: "android/app/src/androidTest/"
```

---

## Key Success Metrics

✅ **All components generated in <2 hours**
✅ **No manual merging needed** (distinct files, clean separation)
✅ **Tests passing** (unit + integration)
✅ **App launches** without crashes
✅ **Podcast Index API integration** working
✅ **MCP tools callable** from Claude/Ollama
✅ **GrapheneOS compatible** (minimal permissions)

---

## What This Delivers

1. **Architecture Blueprint**: Complete system design with diagrams
2. **API Specifications**: Exact Podcast Index endpoint contracts
3. **Database Schema**: SQLite design with Room integration
4. **Component Specifications**: Each UI screen and business logic component
5. **MCP Layer Design**: Tools, resources, prompts for natural language control
6. **Code Generation Prompts**: Ready to feed into claude-flow
7. **Testing Strategy**: Unit + integration test coverage
8. **Build Configuration**: Gradle + dependencies
9. **Documentation**: README, guides, architecture diagrams

This is ready for immediate use with claude-flow or manual development. Each phase is self-contained and can be built in parallel.

