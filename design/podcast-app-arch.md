# Open-Source Podcast 2.0 App with MCP Integration
## Comprehensive Architecture & Implementation Guide

---

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [System Architecture](#system-architecture)
3. [Component Specifications](#component-specifications)
4. [API Documentation](#api-documentation)
5. [Database Schema](#database-schema)
6. [MCP Layer Design](#mcp-layer-design)
7. [Technology Stack](#technology-stack)
8. [Implementation Roadmap](#implementation-roadmap)

---

## Executive Summary

This document provides a complete blueprint for building a **lean, Podcast 2.0 compliant native Android app** with:

- ✅ **Podcast Index API integration** for discovery and metadata
- ✅ **Model Context Protocol (MCP) layer** for natural language control (voice/text while driving)
- ✅ **GrapheneOS-optimized** (privacy-first, minimal permissions)
- ✅ **Offline-first architecture** with SQLite local storage
- ✅ **Small LLM default** (Ollama/MLKit on-device) + optional external LLM (Claude via MCP)
- ✅ **Podcast 2.0 tag support** (transcripts, value-for-value, chapters, etc.)

**Core Philosophy**: Simple to build, powerful via natural language agents.

---

## System Architecture

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    PODCAST 2.0 APP (Android)                    │
│                      [GrapheneOS Optimized]                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                ┌─────────────┼─────────────┐
                │             │             │
        ┌───────▼────────┐   │   ┌─────────▼────────┐
        │   UI Layer     │   │   │  MCP Handler     │
        │  (Jetpack      │   │   │  (Command Parse) │
        │   Compose)     │   │   └──────────┬───────┘
        └───────┬────────┘   │              │
                │            │              │
        ┌───────▼────────────┴──────────────▼─────────┐
        │     Core App Logic & State Management       │
        │  ┌────────────────────────────────────────┐ │
        │  │  ▪ PodcastRepository                   │ │
        │  │  ▪ EpisodeService                      │ │
        │  │  ▪ DownloadManager                     │ │
        │  │  ▪ PlaybackController                  │ │
        │  │  ▪ MetadataProcessor (P2.0 tags)       │ │
        │  └────────────────────────────────────────┘ │
        └───────┬──────────────────────────────────────┘
                │
        ┌───────┴────────────────────┬──────────────────┐
        │                            │                  │
    ┌───▼────────┐    ┌────────────┴───────┐    ┌──────▼─────┐
    │   SQLite   │    │  Podcast Index API │    │  LLM Layer │
    │  Database  │    │  (REST)            │    │  (Optional)│
    │  (Local)   │    │                    │    │            │
    │            │    │  ┌──────────────┐  │    │ ┌────────┐ │
    │ ┌────────┐ │    │  │ Search       │  │    │ │Ollama  │ │
    │ │Podcasts│ │    │  │ Metadata     │  │    │ │(Local) │ │
    │ │Episodes│ │    │  │ Recent       │  │    │ │        │ │
    │ │Progress│ │    │  └──────────────┘  │    │ └────────┘ │
    │ │Playback│ │    │                    │    │            │
    │ │Metadata│ │    └────────────────────┘    │ ┌────────┐ │
    │ └────────┘ │                              │ │Claude  │ │
    │            │                              │ │(via MCP)│
    │            │                              │ └────────┘ │
    └────────────┘                              └────────────┘

```

### Data Flow for Natural Language Control

```
User Voice/Text (Driving Mode)
        │
        ▼
┌──────────────────────────┐
│  MCP Input Handler       │
│  (Parse intent)          │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────────────────┐
│  LLM Processing (On-device/Cloud)    │
│                                      │
│  Ollama (default)  OR  Claude (MCP) │
└──────────┬───────────────────────────┘
           │
           ▼
┌──────────────────────────────────────┐
│  MCP Tool Execution Layer            │
│  ┌────────────────────────────────┐  │
│  │ play_episode(podcast_id, ep_id)│  │
│  │ pause()                         │  │
│  │ skip_forward(seconds)           │  │
│  │ search_podcasts(query)          │  │
│  │ get_subscribed_shows()          │  │
│  │ add_podcast_to_library()        │  │
│  │ get_playback_status()           │  │
│  │ set_playback_speed(speed)       │  │
│  │ get_episode_transcript()        │  │
│  │ mark_episode_as_played()        │  │
│  │ get_next_unplayed_episode()     │  │
│  └────────────────────────────────┘  │
└──────────┬───────────────────────────┘
           │
           ▼
┌──────────────────────────┐
│  App State Update        │
│  & Backend Execution     │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐
│  Audio Output            │
│  (spoken confirmation)   │
└──────────────────────────┘

```

---

## Component Specifications

### 1. UI Layer (Jetpack Compose)

```kotlin
// Main screens
├── PodcastFeedScreen          // Discovery via Podcast Index API
├── LibraryScreen              // User's subscriptions
├── PlayerScreen               // Full-screen player
├── SettingsScreen             // App config & LLM prefs
├── SearchScreen               // Podcast search
├── EpisodeDetailsScreen        // P2.0 metadata (chapters, transcript, etc.)
└── PlaybackQueueScreen        // Current queue

// Key UI Components
├── PlayerControls             // Play/pause, skip, speed
├── MicrophoneButton            // Voice input for MCP commands
├── TranscriptViewer           // Renders P2.0 transcript tag
├── ChapterNavigation          // Jumpable chapters
└── ProgressIndicator          // Download/playback progress
```

### 2. Repository & Data Layer

```kotlin
// PodcastRepository (abstraction over API + local DB)
├── getSubscribedPodcasts()    // Local cache
├── searchPodcasts(query)      // API → cache
├── getPodcast(id)             // Local + API
├── addPodcast(podcast)        // Local storage
├── removePodcast(id)          // Delete from DB

// EpisodeRepository
├── getEpisodes(podcastId)     // Fetch from cache
├── getPlaybackProgress(episodeId)  // Playback position
├── updatePlaybackProgress()        // Save progress
├── markAsPlayed(episodeId)         // Toggle played state
└── getEpisodeMetadata(episodeId)   // P2.0 tags (chapters, transcript, etc.)

// DownloadManager
├── enqueueDownload(episode)        // Add to queue
├── downloadEpisode(episode)        // Stream to disk
├── getDownloadStatus(episodeId)    // % complete
└── deleteDownload(episodeId)       // Free storage
```

### 3. Podcast Index API Integration Layer

```kotlin
// PodcastIndexClient (REST API wrapper)
interface PodcastIndexClient {
    // Search
    suspend fun searchByTerm(query: String): SearchResponse
    suspend fun searchByPerson(person: String): SearchResponse
    suspend fun getRecentEpisodes(maxResults: Int): List<Episode>
    
    // Podcast metadata
    suspend fun getPodcastById(id: Int): PodcastMetadata
    suspend fun getPodcastByFeedUrl(url: String): PodcastMetadata
    suspend fun getPodcastsByCategory(category: String): List<PodcastMetadata>
    
    // Episode details
    suspend fun getEpisodeById(id: Int): EpisodeDetail
    suspend fun getEpisodesByFeedId(feedId: Int, page: Int): PaginatedEpisodes
    suspend fun getRandomEpisodes(): List<EpisodeDetail>
}

// API Authentication (required for rate limiting)
// ├── API_KEY
// └── API_SECRET
//     (obtained from https://podcastindex.org/api)
```

### 4. MCP Layer (Natural Language Control)

```python
# MCPServer: Podcast Control Interface
├── Tools (Claude/Ollama can call these)
│   ├── play_episode(podcast_id: str, episode_id: str) -> PlayResult
│   ├── pause() -> PauseResult
│   ├── resume() -> ResumeResult
│   ├── skip_forward(seconds: int) -> SkipResult
│   ├── skip_backward(seconds: int) -> SkipResult
│   ├── set_playback_speed(speed: float) -> SpeedResult
│   ├── search_podcasts(query: str) -> List[PodcastResult]
│   ├── add_podcast_to_library(podcast_id: str) -> AddResult
│   ├── get_subscribed_podcasts() -> List[PodcastInfo]
│   ├── get_playback_status() -> PlaybackStatus
│   ├── get_next_unplayed_episode(podcast_id: str) -> EpisodeInfo
│   ├── get_episode_transcript(episode_id: str) -> TranscriptText
│   ├── mark_episode_as_played(episode_id: str) -> MarkResult
│   └── list_current_queue() -> List[QueuedEpisode]
│
├── Resources (read-only state)
│   ├── /podcasts/subscribed           # Current library
│   ├── /episodes/playback_position    # Current playback state
│   ├── /podcasts/{id}/episodes        # All episodes for podcast
│   └── /app/playback_status           # Real-time status
│
└── Prompts (system instructions for Claude/Ollama)
    ├── driving_mode_prompt            # Concise responses for safety
    ├── episode_recommendation_prompt  # Suggest based on history
    └── search_refinement_prompt       # Help narrow queries
```

---

## API Documentation

### Podcast Index REST API Integration

#### Authentication
```
Headers:
  X-Auth-Date: {timestamp}
  X-Auth-Signature: {SHA-1(key + secret + timestamp)}
  User-Agent: PodcastApp/1.0
```

#### Key Endpoints (used by this app)

**1. Search Podcasts**
```http
GET /search/byterm?q={query}&max=20&clean=true
Accept: application/json

Response:
{
  "status": "true",
  "feeds": [
    {
      "id": 12345,
      "title": "Podcast Title",
      "url": "https://example.com/feed.xml",
      "link": "https://example.com",
      "description": "...",
      "image": "https://...",
      "artwork": "https://...",
      "episodeCount": 150,
      "itunesId": 12345,
      "episodesDays": 45,
      "newestItemPublishTime": 1704067200,
      "language": "en",
      "categories": {
        "1": "Technology",
        "2": "Business"
      },
      "locked": false,
      "explicit": false
    }
  ]
}
```

**2. Get Podcast Metadata**
```http
GET /podcasts/byid?id={podcast_id}

Response:
{
  "status": "true",
  "podcast": {
    "id": 12345,
    "feedUrl": "https://example.com/feed.xml",
    "title": "...",
    "image": "...",
    "description": "...",
    "episodeCount": 150,
    "latestEpisodeDate": 1704067200,
    "lastUpdateTime": 1704000000,
    "locked": false,
    "explicit": false,
    "link": "https://example.com",
    "podcastGuid": "123e4567-e89b-12d3-a456-426614174000",
    "chatGuid": "123e4567-e89b-12d3-a456-426614174001",
    "email": "contact@example.com",
    "language": "en-us",
    "categories": { "1": "Technology" },
    "funding": [
      {
        "url": "https://patreon.com/...",
        "title": "Support us on Patreon"
      }
    ],
    "value": {
      "type": "lightning",
      "method": "keysend",
      "suggested": "0.00000015"
    }
  }
}
```

**3. Get Episodes for Podcast**
```http
GET /episodes/byfeedid?id={podcast_id}&max=50&page={page}

Response:
{
  "status": "true",
  "episodes": [
    {
      "id": 987654,
      "title": "Episode Title",
      "description": "...",
      "pubDate": 1704067200,
      "enclosureUrl": "https://cdn.example.com/episode.mp3",
      "enclosureType": "audio/mpeg",
      "enclosureLength": 54000000,
      "duration": 3600,
      "explicit": false,
      "episodeGuid": "episode-guid-string",
      "feedItunesId": 12345,
      "feedImage": "https://...",
      "feedId": 12345,
      "image": "https://...",
      "link": "https://example.com/episode",
      "chapters": {
        "version": "1.2",
        "chapters": [
          {
            "startTime": 0,
            "title": "Intro",
            "href": "https://example.com#intro",
            "image": "https://..."
          }
        ]
      },
      "transcripts": [
        {
          "url": "https://cdn.example.com/transcript.vtt",
          "type": "application/x-subrip",
          "language": "en",
          "rel": "captions"
        }
      ]
    }
  ]
}
```

**4. Get Recent Episodes**
```http
GET /episodes/recent?max=20&sort=datePublished

Response:
{
  "status": "true",
  "episodes": [ ... ]
}
```

### MCP Server Tools API

#### Tool Schema (Python/TypeScript)

```python
# Tool: play_episode
Tool(
    name="play_episode",
    description="Start playing a specific episode",
    inputSchema={
        "type": "object",
        "properties": {
            "podcast_id": {
                "type": "string",
                "description": "The unique ID of the podcast (from Podcast Index)"
            },
            "episode_id": {
                "type": "string",
                "description": "The unique ID of the episode to play"
            },
            "start_position": {
                "type": "integer",
                "description": "Start playback at this timestamp (seconds)",
                "default": 0
            }
        },
        "required": ["podcast_id", "episode_id"]
    }
)
# Returns: {"status": "playing", "title": "...", "duration": 3600}

# Tool: search_podcasts
Tool(
    name="search_podcasts",
    description="Search for podcasts by term",
    inputSchema={
        "type": "object",
        "properties": {
            "query": {
                "type": "string",
                "description": "Search term (e.g., 'technology', 'AI', 'fitness')"
            },
            "limit": {
                "type": "integer",
                "description": "Max results to return",
                "default": 10
            }
        },
        "required": ["query"]
    }
)
# Returns: List[{id, title, description, episode_count, image}]

# Tool: get_playback_status
Tool(
    name="get_playback_status",
    description="Get current playback status",
    inputSchema={"type": "object", "properties": {}},
)
# Returns: {
#   "is_playing": bool,
#   "current_episode": {...},
#   "position": 1234,  # seconds
#   "duration": 3600,
#   "speed": 1.0,
#   "podcast_title": "..."
# }
```

---

## Database Schema

### SQLite Schema (Room ORM)

```sql
-- Podcasts Table
CREATE TABLE podcasts (
  id INTEGER PRIMARY KEY,
  podcast_index_id INTEGER UNIQUE NOT NULL,
  title TEXT NOT NULL,
  feed_url TEXT NOT NULL UNIQUE,
  image_url TEXT,
  description TEXT,
  language TEXT DEFAULT 'en',
  explicit INTEGER DEFAULT 0,
  category TEXT,
  website_url TEXT,
  podcast_guid TEXT UNIQUE,
  last_synced_at INTEGER,  -- Unix timestamp
  is_subscribed INTEGER DEFAULT 1,
  custom_name TEXT,        -- User can rename
  added_at INTEGER,        -- When user subscribed
  created_at INTEGER,
  updated_at INTEGER
);

-- Episodes Table
CREATE TABLE episodes (
  id INTEGER PRIMARY KEY,
  episode_index_id INTEGER UNIQUE NOT NULL,
  podcast_id INTEGER NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  audio_url TEXT NOT NULL,
  audio_duration INTEGER,  -- seconds
  published_at INTEGER,    -- Unix timestamp
  episode_guid TEXT UNIQUE,
  explicit INTEGER DEFAULT 0,
  link TEXT,
  image_url TEXT,
  transcript_url TEXT,
  transcript_type TEXT,    -- application/x-subrip, text/vtt, etc.
  transcript_cached TEXT,  -- Cached transcript for offline
  chapters_json TEXT,      -- JSON array of chapter objects
  chapters_timestamp_start INTEGER,
  created_at INTEGER,
  updated_at INTEGER,
  
  FOREIGN KEY (podcast_id) REFERENCES podcasts(id) ON DELETE CASCADE
);

-- Playback Progress Table
CREATE TABLE playback_progress (
  id INTEGER PRIMARY KEY,
  episode_id INTEGER NOT NULL UNIQUE,
  position_seconds INTEGER DEFAULT 0,
  duration_seconds INTEGER,
  is_completed INTEGER DEFAULT 0,
  completed_at INTEGER,
  last_played_at INTEGER,  -- Most recent playback
  playback_speed REAL DEFAULT 1.0,
  
  FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE
);

-- Downloads Table (offline episodes)
CREATE TABLE downloads (
  id INTEGER PRIMARY KEY,
  episode_id INTEGER NOT NULL UNIQUE,
  file_path TEXT NOT NULL,
  file_size INTEGER,  -- bytes
  downloaded_at INTEGER,
  status TEXT DEFAULT 'completed',  -- pending, in_progress, completed, failed
  error_message TEXT,
  
  FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE CASCADE
);

-- Search History (for MCP context)
CREATE TABLE search_history (
  id INTEGER PRIMARY KEY,
  query TEXT NOT NULL,
  search_type TEXT,  -- podcast, episode
  searched_at INTEGER,
  result_count INTEGER
);

-- Podcast Index Sync Metadata
CREATE TABLE sync_metadata (
  id INTEGER PRIMARY KEY,
  last_sync_time INTEGER,
  total_podcasts_synced INTEGER,
  api_rate_limit_remaining INTEGER,
  api_rate_limit_reset INTEGER
);

-- Indexes for performance
CREATE INDEX idx_episodes_podcast ON episodes(podcast_id);
CREATE INDEX idx_episodes_published ON episodes(published_at DESC);
CREATE INDEX idx_playback_completed ON playback_progress(is_completed);
CREATE INDEX idx_playback_last_played ON playback_progress(last_played_at DESC);
CREATE INDEX idx_podcasts_subscribed ON podcasts(is_subscribed);
```

### Room DAO Definitions (Kotlin)

```kotlin
// PodcastDao
@Dao
interface PodcastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: Podcast)
    
    @Query("SELECT * FROM podcasts WHERE is_subscribed = 1 ORDER BY added_at DESC")
    fun getSubscribedPodcasts(): Flow<List<Podcast>>
    
    @Query("SELECT * FROM podcasts WHERE podcast_index_id = :id")
    suspend fun getPodcastByIndexId(id: Int): Podcast?
    
    @Query("SELECT COUNT(*) FROM podcasts WHERE is_subscribed = 1")
    fun getSubscriptionCount(): Flow<Int>
    
    @Delete
    suspend fun removePodcast(podcast: Podcast)
}

// EpisodeDao
@Dao
interface EpisodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: Episode)
    
    @Query("""
        SELECT * FROM episodes 
        WHERE podcast_id = :podcastId 
        ORDER BY published_at DESC
        LIMIT :limit
    """)
    fun getEpisodesByPodcast(podcastId: Int, limit: Int = 50): Flow<List<Episode>>
    
    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getEpisodeById(episodeId: Int): Episode?
    
    @Query("SELECT * FROM episodes WHERE episode_index_id = :indexId")
    suspend fun getEpisodeByIndexId(indexId: Int): Episode?
    
    @Update
    suspend fun updateEpisode(episode: Episode)
}

// PlaybackProgressDao
@Dao
interface PlaybackProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateProgress(progress: PlaybackProgress)
    
    @Query("SELECT * FROM playback_progress WHERE episode_id = :episodeId")
    fun getProgress(episodeId: Int): Flow<PlaybackProgress?>
    
    @Query("""
        SELECT * FROM playback_progress 
        WHERE is_completed = 0 
        ORDER BY last_played_at DESC
        LIMIT 1
    """)
    suspend fun getLastPlayedUncompletedEpisode(): PlaybackProgress?
}

// DownloadDao
@Dao
interface DownloadDao {
    @Insert
    suspend fun insertDownload(download: Download)
    
    @Query("SELECT * FROM downloads WHERE episode_id = :episodeId")
    suspend fun getDownload(episodeId: Int): Download?
    
    @Query("SELECT * FROM downloads WHERE status = 'completed'")
    fun getDownloadedEpisodes(): Flow<List<Download>>
    
    @Delete
    suspend fun removeDownload(download: Download)
}
```

---

## MCP Layer Design

### Architecture: MCP Server Implementation

```python
# Location: /app/mcp_server.py

from mcp.server import Server, types
from mcp.server.stdio import stdio_server
import json
import subprocess
from dataclasses import dataclass
from typing import Any, Optional

class PodcastMCPServer:
    """
    MCP Server that bridges Claude/Ollama with the Android Podcast app.
    
    Communication:
    - Claude/Ollama → MCP Server (stdio) → Android app (local service)
    - Android app state → MCP Resources
    - MCP Tools ← App capabilities
    """
    
    def __init__(self):
        self.server = Server("podcast-control")
        self._register_tools()
        self._register_resources()
        self._register_prompts()
    
    def _register_tools(self):
        """Register MCP tools for podcast control"""
        
        @self.server.call_tool()
        async def play_episode(args: dict) -> str:
            """Start playing an episode"""
            podcast_id = args["podcast_id"]
            episode_id = args["episode_id"]
            start_pos = args.get("start_position", 0)
            
            result = self._call_app_action("playEpisode", {
                "podcastId": podcast_id,
                "episodeId": episode_id,
                "startPosition": start_pos
            })
            return json.dumps(result)
        
        @self.server.call_tool()
        async def pause() -> str:
            """Pause playback"""
            result = self._call_app_action("pausePlayback", {})
            return json.dumps(result)
        
        @self.server.call_tool()
        async def skip_forward(args: dict) -> str:
            """Skip forward N seconds"""
            seconds = args.get("seconds", 15)
            result = self._call_app_action("skipForward", {"seconds": seconds})
            return json.dumps(result)
        
        @self.server.call_tool()
        async def search_podcasts(args: dict) -> str:
            """Search for podcasts"""
            query = args["query"]
            limit = args.get("limit", 10)
            
            result = self._call_app_action("searchPodcasts", {
                "query": query,
                "limit": limit
            })
            return json.dumps(result)
        
        @self.server.call_tool()
        async def get_playback_status() -> str:
            """Get current playback status"""
            result = self._call_app_action("getPlaybackStatus", {})
            return json.dumps(result)
        
        @self.server.call_tool()
        async def add_podcast_to_library(args: dict) -> str:
            """Subscribe to a podcast"""
            podcast_id = args["podcast_id"]
            result = self._call_app_action("addPodcast", {
                "podcastId": podcast_id
            })
            return json.dumps(result)
        
        @self.server.call_tool()
        async def get_subscribed_podcasts() -> str:
            """Get user's podcast library"""
            result = self._call_app_action("getSubscribedPodcasts", {})
            return json.dumps(result)
        
        @self.server.call_tool()
        async def get_next_unplayed_episode(args: dict) -> str:
            """Find next unplayed episode for podcast"""
            podcast_id = args.get("podcast_id")
            result = self._call_app_action("getNextUnplayedEpisode", {
                "podcastId": podcast_id
            })
            return json.dumps(result)
        
        @self.server.call_tool()
        async def get_episode_transcript(args: dict) -> str:
            """Get transcript for episode (P2.0 support)"""
            episode_id = args["episode_id"]
            result = self._call_app_action("getTranscript", {
                "episodeId": episode_id
            })
            return json.dumps(result)
        
        @self.server.call_tool()
        async def set_playback_speed(args: dict) -> str:
            """Change playback speed"""
            speed = args.get("speed", 1.0)
            result = self._call_app_action("setPlaybackSpeed", {
                "speed": speed
            })
            return json.dumps(result)
        
        @self.server.call_tool()
        async def mark_episode_as_played(args: dict) -> str:
            """Mark episode as completed"""
            episode_id = args["episode_id"]
            result = self._call_app_action("markAsPlayed", {
                "episodeId": episode_id
            })
            return json.dumps(result)
    
    def _register_resources(self):
        """Register MCP resources (read-only state)"""
        
        @self.server.resource(
            uri="podcast://subscribed",
            name="Subscribed Podcasts",
            mimeType="application/json"
        )
        async def get_subscribed() -> str:
            """Get list of subscribed podcasts"""
            result = self._call_app_action("getSubscribedPodcasts", {})
            return json.dumps(result)
        
        @self.server.resource(
            uri="podcast://playback-status",
            name="Current Playback Status",
            mimeType="application/json"
        )
        async def get_status() -> str:
            """Get real-time playback status"""
            result = self._call_app_action("getPlaybackStatus", {})
            return json.dumps(result)
        
        @self.server.resource(
            uri="podcast://queue",
            name="Playback Queue",
            mimeType="application/json"
        )
        async def get_queue() -> str:
            """Get current queue"""
            result = self._call_app_action("getPlaybackQueue", {})
            return json.dumps(result)
    
    def _register_prompts(self):
        """Register MCP prompts (system instructions)"""
        
        # Driving mode: concise, safety-focused responses
        self.server.prompt(
            name="driving_mode",
            description="Response mode for safe driving - concise, no distractions",
            arguments=[
                types.PromptArgument(
                    name="context",
                    description="Current driving context"
                )
            ]
        )
        
        # Episode discovery: personalized recommendations
        self.server.prompt(
            name="recommend_episodes",
            description="Recommend episodes based on listening history",
            arguments=[
                types.PromptArgument(
                    name="genres",
                    description="Preferred genres"
                )
            ]
        )
    
    def _call_app_action(self, action: str, params: dict) -> dict:
        """
        Call Android app via ADB or local socket.
        
        In production: Use Android Service IPC or local socket.
        """
        try:
            # Example: adb shell am broadcast -a com.podcast.app.ACTION_PLAY_EPISODE
            # --es podcastId "123" --es episodeId "456"
            
            cmd = ["adb", "shell", "am", "broadcast"]
            cmd.append(f"-a com.podcast.app.action.{action}")
            
            for key, value in params.items():
                cmd.extend([f"--es {key}", str(value)])
            
            subprocess.run(cmd, check=True)
            return {"status": "success", "action": action}
        except Exception as e:
            return {"status": "error", "action": action, "error": str(e)}
```

### MCP → Android IPC Bridge

```kotlin
// Location: /app/src/main/java/com/podcast/app/mcp/MCPBridge.kt

/**
 * Bridges MCP commands to Android app via:
 * 1. Broadcast receivers (simple actions)
 * 2. Local socket service (complex operations)
 * 3. AIDL Service (for production)
 */

class MCPActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.podcast.app.action.playEpisode" -> {
                val podcastId = intent.getStringExtra("podcastId") ?: return
                val episodeId = intent.getStringExtra("episodeId") ?: return
                val startPos = intent.getIntExtra("startPosition", 0)
                
                val appContext = context.applicationContext as PodcastApp
                appContext.playbackController.playEpisode(podcastId, episodeId, startPos)
            }
            "com.podcast.app.action.pausePlayback" -> {
                val appContext = context.applicationContext as PodcastApp
                appContext.playbackController.pause()
            }
            "com.podcast.app.action.skipForward" -> {
                val seconds = intent.getIntExtra("seconds", 15)
                val appContext = context.applicationContext as PodcastApp
                appContext.playbackController.skipForward(seconds)
            }
            "com.podcast.app.action.setPlaybackSpeed" -> {
                val speed = intent.getFloatExtra("speed", 1.0f)
                val appContext = context.applicationContext as PodcastApp
                appContext.playbackController.setPlaybackSpeed(speed)
            }
        }
    }
}

// Register in AndroidManifest.xml:
/*
<receiver
    android:name=".mcp.MCPActionReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.podcast.app.action.playEpisode" />
        <action android:name="com.podcast.app.action.pausePlayback" />
        <action android:name="com.podcast.app.action.skipForward" />
        <action android:name="com.podcast.app.action.setPlaybackSpeed" />
        <action android:name="com.podcast.app.action.getPlaybackStatus" />
        <action android:name="com.podcast.app.action.searchPodcasts" />
    </intent-filter>
</receiver>
*/
```

---

## Technology Stack

| Layer | Technology | Why |
|-------|-----------|-----|
| **UI** | Jetpack Compose | Modern, efficient Android UI |
| **Architecture** | MVVM + Flow | Reactive, testable |
| **Local DB** | Room + SQLite | Fast, reliable local storage |
| **HTTP Client** | Retrofit 2 + OkHttp | Robust API calls with caching |
| **Serialization** | Kotlinx Serialization | Lightweight, fast JSON parsing |
| **Dependency Injection** | Hilt | Standard for Android apps |
| **Media Playback** | ExoPlayer | Industry standard, P2.0 support |
| **Background Tasks** | WorkManager | Reliable scheduling (sync, downloads) |
| **Voice Input** | Android Speech Recognition API | Native, no external deps |
| **MCP Server** | Python + FastMCP | Lightweight, easy to extend |
| **LLM (Local)** | Ollama or MLKit | On-device, no data leakage |
| **LLM (Cloud)** | Claude API | Optional via MCP |
| **Download Manager** | OkHttp + coroutines | Efficient, resumable downloads |
| **Audio Processing** | ExoPlayer's Audio Processor | Speed adjustment, silence skip |

---

## Implementation Roadmap

### Phase 1: MVP (2-3 weeks)
- [ ] Android project setup (Gradle, Hilt, Room)
- [ ] Basic UI (Compose screens: Library, Search, Player)
- [ ] Podcast Index API integration
- [ ] Local SQLite database
- [ ] Basic playback (ExoPlayer)
- [ ] OPML import/export

**Deliverable**: Simple podcast app that plays episodes from Podcast Index

### Phase 2: MCP Layer (1-2 weeks)
- [ ] Python MCP server scaffold
- [ ] Tool registrations (play, pause, search, status)
- [ ] Android IPC bridge (broadcast receivers)
- [ ] Voice input handler
- [ ] Integration testing

**Deliverable**: Control app via `"play the latest episode of X"` commands

### Phase 3: Podcast 2.0 Support (1 week)
- [ ] Parse P2.0 XML tags (chapters, transcripts, funding)
- [ ] Transcript display (VTT/WebVTT parsing)
- [ ] Chapter navigation UI
- [ ] Value-for-value button display

**Deliverable**: Full Podcast 2.0 compliance

### Phase 4: LLM Integration (1 week)
- [ ] Ollama on-device LLM (optional)
- [ ] Claude API integration via MCP
- [ ] System prompts (driving mode, recommendations)
- [ ] Context injection (user's library, history)

**Deliverable**: "Smart" natural language control via Claude

### Phase 5: Polish & Optimization (1 week)
- [ ] Offline support (cached transcripts, queued episodes)
- [ ] GrapheneOS permission hardening
- [ ] Performance profiling
- [ ] Documentation & README

**Deliverable**: Production-ready app

---

## Key Design Decisions

### 1. **Single File Preference vs Modular**
- **Choice**: Modular (separate components)
- **Reason**: Easier to integrate with claude-flow build process; allows iterative component development

### 2. **Offline vs Online**
- **Choice**: Offline-first with sync
- **Reason**: Works on GrapheneOS (limited network access); syncs when available

### 3. **LLM Strategy**
- **Choice**: Ollama default + Claude optional
- **Reason**: Ollama runs locally (privacy); Claude available for advanced reasoning

### 4. **Database**
- **Choice**: SQLite (Room)
- **Reason**: No external dependencies; ACID compliance; offline-capable

### 5. **Audio Player**
- **Choice**: ExoPlayer
- **Reason**: Built-in P2.0 support; handles HLS/DASH; excellent playback control

---

## Security & Privacy (GrapheneOS)

✅ **No telemetry**: All data stored locally
✅ **No external calls** except Podcast Index (public, no auth)
✅ **Minimal permissions**: Microphone (voice), Media (playback), Internet (API)
✅ **Optional Google Play**: Not required; works standalone
✅ **Encrypted preferences**: SharedPreferences encrypted with EncryptedSharedPreferences

---

## Summary: What This Blueprint Provides

1. **API Specifications**: Exact Podcast Index API endpoints, request/response schemas
2. **Database Schema**: SQLite tables with indexes, Room DAOs
3. **Architecture Diagrams**: Data flow, component interaction, MCP bridge
4. **MCP Server Code**: Python implementation with tool/resource/prompt definitions
5. **Android IPC Bridge**: Kotlin broadcast receiver for MCP → app communication
6. **Tech Stack**: Production-ready technologies with justifications
7. **Roadmap**: 5-phase implementation (5-8 weeks)

This is ready to feed into claude-flow for automated code generation. Each phase is self-contained and testable.

---

## Next Steps for claude-flow Integration

```yaml
# claude-flow.yml example
phases:
  - phase: mvp
    description: "Basic podcast app"
    components:
      - name: "room_database"
        prompt: "Generate Room DAOs and entities from schema"
        input: "database-schema.md"
      
      - name: "podcast_index_client"
        prompt: "Generate Retrofit API client for Podcast Index"
        input: "api-documentation.md"
      
      - name: "compose_ui"
        prompt: "Generate Jetpack Compose screens (Library, Search, Player)"
        input: "component-specs.md"
      
      - name: "exoplayer_setup"
        prompt: "Configure ExoPlayer for podcast playback"
  
  - phase: mcp_layer
    description: "Natural language control"
    components:
      - name: "mcp_server"
        prompt: "Generate Python MCP server with podcast tools"
        input: "mcp-layer-design.md"
      
      - name: "android_bridge"
        prompt: "Generate Android broadcast receiver for IPC"
  
  - phase: podcast2_support
    description: "Podcast 2.0 tags"
    components:
      - name: "p2_parser"
        prompt: "Generate P2.0 XML parser for chapters, transcripts"
      
      - name: "transcript_ui"
        prompt: "Generate Compose UI for transcript display"
```

---

