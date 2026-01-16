# Podcast 2.0 App - Implementation Code Examples & MCP Server

## Part 1: MCP Server Implementation (Python)

### Installation & Setup

```bash
# Install dependencies
pip install mcp fastmcp anthropic

# Directory structure
podcast-app-mcp/
├── server.py                 # Main MCP server
├── android_bridge.py         # IPC communication
├── requirements.txt
└── config.json              # App configuration
```

### MCP Server Implementation

```python
# server.py - Complete MCP Server

import json
import subprocess
import asyncio
from typing import Any, Optional
from mcp.server.fastmcp import FastMCP
from mcp.server import types
from dataclasses import dataclass
import socket
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("PodcastMCP")

# Initialize FastMCP server
mcp = FastMCP(
    name="podcast-controller",
    instructions="""
    You are a voice assistant for a podcast app running on GrapheneOS.
    When a user asks to play a podcast, search for content, or control playback,
    use the available tools to interact with the app.
    
    Be concise in your responses - the user may be driving and needs safety-focused feedback.
    Always confirm what you're doing and provide status updates.
    """
)

@dataclass
class PlaybackStatus:
    is_playing: bool
    current_podcast: Optional[str]
    current_episode: Optional[str]
    position_seconds: int
    duration_seconds: int
    playback_speed: float

class AndroidAppBridge:
    """
    Bridge to Android app via:
    - Socket communication (preferred)
    - ADB shell broadcast (fallback for development)
    """
    
    def __init__(self, socket_path: str = "/tmp/podcast_app.sock"):
        self.socket_path = socket_path
        self.use_adb = False
    
    async def call_action(self, action: str, params: dict = {}) -> dict:
        """Execute action in Android app"""
        try:
            return await self._call_via_socket(action, params)
        except Exception as e:
            logger.warning(f"Socket failed, falling back to ADB: {e}")
            return await self._call_via_adb(action, params)
    
    async def _call_via_socket(self, action: str, params: dict) -> dict:
        """Use local socket for IPC (production)"""
        loop = asyncio.get_event_loop()
        
        # Create request
        request = {
            "action": action,
            "params": params
        }
        
        # Send via socket
        reader, writer = await asyncio.open_unix_connection(self.socket_path)
        writer.write(json.dumps(request).encode() + b'\n')
        await writer.drain()
        
        # Read response
        response_data = await reader.read(4096)
        writer.close()
        await writer.wait_closed()
        
        response = json.loads(response_data.decode())
        return response
    
    async def _call_via_adb(self, action: str, params: dict) -> dict:
        """Use ADB broadcast for development/debugging"""
        cmd = [
            "adb", "shell", "am", "broadcast",
            "-a", f"com.podcast.app.action.{action}"
        ]
        
        # Add parameters
        for key, value in params.items():
            if isinstance(value, str):
                cmd.extend(["--es", key, value])
            elif isinstance(value, int):
                cmd.extend(["--ei", key, str(value)])
            elif isinstance(value, float):
                cmd.extend(["--ef", key, str(value)])
        
        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=5)
            if result.returncode == 0:
                return {"status": "success", "action": action}
            else:
                return {"status": "error", "action": action, "error": result.stderr}
        except subprocess.TimeoutExpired:
            return {"status": "error", "action": action, "error": "timeout"}

# Global bridge instance
bridge = AndroidAppBridge()

# ============================================================================
# MCP TOOLS - These are what Claude/Ollama can call
# ============================================================================

@mcp.tool()
async def play_episode(podcast_id: str, episode_id: str, start_position: int = 0) -> dict:
    """
    Start playing a specific episode.
    
    Args:
        podcast_id: The Podcast Index ID of the podcast
        episode_id: The Podcast Index ID of the episode
        start_position: Start playback at this timestamp (seconds)
    
    Returns:
        Status of playback start
    """
    result = await bridge.call_action("playEpisode", {
        "podcastId": podcast_id,
        "episodeId": episode_id,
        "startPosition": start_position
    })
    
    if result.get("status") == "success":
        return {
            "status": "playing",
            "podcast_id": podcast_id,
            "episode_id": episode_id,
            "message": "Now playing episode"
        }
    else:
        return {
            "status": "error",
            "message": f"Failed to play: {result.get('error', 'unknown error')}"
        }

@mcp.tool()
async def pause() -> dict:
    """Pause current playback"""
    result = await bridge.call_action("pausePlayback")
    return {
        "status": "paused",
        "message": "Podcast paused"
    }

@mcp.tool()
async def resume() -> dict:
    """Resume current playback"""
    result = await bridge.call_action("resumePlayback")
    return {
        "status": "playing",
        "message": "Podcast resumed"
    }

@mcp.tool()
async def skip_forward(seconds: int = 15) -> dict:
    """Skip forward N seconds"""
    result = await bridge.call_action("skipForward", {"seconds": seconds})
    return {
        "status": "skipped",
        "seconds": seconds,
        "message": f"Skipped forward {seconds} seconds"
    }

@mcp.tool()
async def skip_backward(seconds: int = 15) -> dict:
    """Skip backward N seconds"""
    result = await bridge.call_action("skipBackward", {"seconds": seconds})
    return {
        "status": "skipped",
        "seconds": -seconds,
        "message": f"Skipped backward {seconds} seconds"
    }

@mcp.tool()
async def set_playback_speed(speed: float = 1.0) -> dict:
    """Change playback speed (1.0 = normal, 1.5 = 1.5x, 2.0 = 2x)"""
    valid_speeds = [0.75, 1.0, 1.25, 1.5, 1.75, 2.0]
    if speed not in valid_speeds:
        speed = min(valid_speeds, key=lambda x: abs(x - speed))
    
    result = await bridge.call_action("setPlaybackSpeed", {"speed": speed})
    return {
        "status": "speed_changed",
        "speed": speed,
        "message": f"Playback speed set to {speed}x"
    }

@mcp.tool()
async def search_podcasts(query: str, limit: int = 10) -> list[dict]:
    """
    Search for podcasts by term (queries Podcast Index API)
    
    Args:
        query: Search term (e.g., "technology", "AI", "fitness")
        limit: Maximum results to return
    
    Returns:
        List of matching podcasts with metadata
    """
    result = await bridge.call_action("searchPodcasts", {
        "query": query,
        "limit": limit
    })
    
    if result.get("status") == "success":
        podcasts = result.get("podcasts", [])
        return [
            {
                "id": p.get("id"),
                "title": p.get("title"),
                "description": p.get("description", ""),
                "episode_count": p.get("episodeCount", 0),
                "image_url": p.get("image", ""),
                "categories": p.get("categories", {})
            }
            for p in podcasts
        ]
    else:
        return []

@mcp.tool()
async def add_podcast_to_library(podcast_id: str) -> dict:
    """Subscribe to a podcast"""
    result = await bridge.call_action("addPodcast", {"podcastId": podcast_id})
    return {
        "status": "subscribed",
        "podcast_id": podcast_id,
        "message": "Podcast added to library"
    }

@mcp.tool()
async def remove_podcast_from_library(podcast_id: str) -> dict:
    """Unsubscribe from a podcast"""
    result = await bridge.call_action("removePodcast", {"podcastId": podcast_id})
    return {
        "status": "unsubscribed",
        "podcast_id": podcast_id,
        "message": "Podcast removed from library"
    }

@mcp.tool()
async def get_subscribed_podcasts() -> list[dict]:
    """Get user's current podcast subscriptions"""
    result = await bridge.call_action("getSubscribedPodcasts")
    
    if result.get("status") == "success":
        podcasts = result.get("podcasts", [])
        return [
            {
                "id": p.get("id"),
                "title": p.get("title"),
                "unplayed_count": p.get("unplayedCount", 0),
                "image_url": p.get("image", "")
            }
            for p in podcasts
        ]
    else:
        return []

@mcp.tool()
async def get_playback_status() -> dict:
    """Get current playback status"""
    result = await bridge.call_action("getPlaybackStatus")
    
    if result.get("status") == "success":
        return {
            "is_playing": result.get("isPlaying", False),
            "podcast_title": result.get("podcastTitle", ""),
            "episode_title": result.get("episodeTitle", ""),
            "position_seconds": result.get("positionSeconds", 0),
            "duration_seconds": result.get("durationSeconds", 0),
            "playback_speed": result.get("playbackSpeed", 1.0),
            "timestamp": result.get("timestamp", 0)
        }
    else:
        return {"error": "Unable to get playback status"}

@mcp.tool()
async def get_next_unplayed_episode(podcast_id: Optional[str] = None) -> Optional[dict]:
    """
    Get the next unplayed episode
    
    If podcast_id is provided, get next episode for that podcast.
    Otherwise, get next unplayed from all subscriptions.
    """
    params = {}
    if podcast_id:
        params["podcastId"] = podcast_id
    
    result = await bridge.call_action("getNextUnplayedEpisode", params)
    
    if result.get("status") == "success":
        ep = result.get("episode", {})
        return {
            "id": ep.get("id"),
            "podcast_id": ep.get("podcastId"),
            "podcast_title": ep.get("podcastTitle"),
            "episode_title": ep.get("title"),
            "description": ep.get("description", ""),
            "duration": ep.get("duration", 0),
            "published_at": ep.get("publishedAt", 0)
        }
    else:
        return None

@mcp.tool()
async def get_episode_transcript(episode_id: str) -> Optional[str]:
    """
    Get transcript for an episode (Podcast 2.0 support)
    
    Returns the transcript text if available, or None if not available
    """
    result = await bridge.call_action("getTranscript", {"episodeId": episode_id})
    
    if result.get("status") == "success":
        return result.get("transcript", "")
    else:
        return None

@mcp.tool()
async def get_episode_chapters(episode_id: str) -> list[dict]:
    """Get chapters for an episode (Podcast 2.0)"""
    result = await bridge.call_action("getChapters", {"episodeId": episode_id})
    
    if result.get("status") == "success":
        chapters = result.get("chapters", [])
        return [
            {
                "start_time": c.get("startTime", 0),
                "title": c.get("title", ""),
                "url": c.get("href", ""),
                "image": c.get("image", "")
            }
            for c in chapters
        ]
    else:
        return []

@mcp.tool()
async def mark_episode_as_played(episode_id: str) -> dict:
    """Mark an episode as completed/played"""
    result = await bridge.call_action("markAsPlayed", {"episodeId": episode_id})
    return {
        "status": "marked_played",
        "episode_id": episode_id,
        "message": "Episode marked as played"
    }

@mcp.tool()
async def list_playback_queue() -> list[dict]:
    """Get current playback queue"""
    result = await bridge.call_action("getPlaybackQueue")
    
    if result.get("status") == "success":
        queue = result.get("queue", [])
        return [
            {
                "position": i,
                "episode_id": ep.get("id"),
                "podcast_title": ep.get("podcastTitle"),
                "episode_title": ep.get("title"),
                "duration": ep.get("duration", 0)
            }
            for i, ep in enumerate(queue)
        ]
    else:
        return []

@mcp.tool()
async def clear_playback_queue() -> dict:
    """Clear the playback queue"""
    result = await bridge.call_action("clearQueue")
    return {
        "status": "cleared",
        "message": "Playback queue cleared"
    }

# ============================================================================
# MCP RESOURCES - Read-only state that Claude/Ollama can access
# ============================================================================

@mcp.resource(
    uri="podcast://library",
    name="User's Podcast Library",
    description="Current list of subscribed podcasts"
)
async def get_library() -> str:
    """Get user's podcast library"""
    podcasts = await get_subscribed_podcasts()
    return json.dumps(podcasts, indent=2)

@mcp.resource(
    uri="podcast://playback-status",
    name="Current Playback Status",
    description="Real-time playback information"
)
async def get_status() -> str:
    """Get current playback status"""
    status = await get_playback_status()
    return json.dumps(status, indent=2)

@mcp.resource(
    uri="podcast://queue",
    name="Playback Queue",
    description="Episodes queued for playback"
)
async def get_queue() -> str:
    """Get playback queue"""
    queue = await list_playback_queue()
    return json.dumps(queue, indent=2)

# ============================================================================
# MCP PROMPTS - System instructions for better LLM behavior
# ============================================================================

@mcp.prompt(
    name="driving_mode",
    description="Response mode optimized for safe driving - concise, non-distracting",
    arguments=[
        {"name": "current_action", "description": "What the user is asking the app to do"}
    ]
)
async def driving_prompt(current_action: str) -> str:
    """Return a prompt that instructs Claude to give concise, driving-safe responses"""
    return f"""
    The user is using the podcast app while driving. Keep your response concise, clear, and non-distracting.
    
    User's request: {current_action}
    
    Guidelines:
    - Maximum 1 sentence response
    - Confirm what you're doing
    - Do not provide detailed information that requires reading
    - Use natural language, not technical jargon
    - Example good response: "Playing the latest episode of Tech News Daily"
    - Example bad response: "Initiating playback of episode ID 123456 from podcast 789012..."
    """

@mcp.prompt(
    name="recommend_next",
    description="Generate podcast recommendations based on listening history",
    arguments=[
        {"name": "last_podcasts", "description": "User's recently played podcasts"}
    ]
)
async def recommend_prompt(last_podcasts: str) -> str:
    """Prompt Claude to recommend next podcast based on history"""
    return f"""
    The user has recently listened to: {last_podcasts}
    
    Based on these listening habits, suggest what they might want to listen to next.
    Consider:
    - Related topics and genres
    - New episodes from similar podcasts
    - Cross-genre recommendations they might enjoy
    
    Keep recommendations brief and actionable.
    """

# ============================================================================
# Server Startup
# ============================================================================

if __name__ == "__main__":
    import uvicorn
    
    # Run MCP server
    mcp.run(transport="stdio")

```

## Part 2: Android Bridge (Kotlin)

```kotlin
// AndroidManifest.xml - Broadcast Receiver Registration

<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.podcast.app">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <application
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.PodcastApp">

        <!-- MCP Broadcast Receivers -->
        <receiver
            android:name=".mcp.MCPActionReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="com.podcast.app.action.playEpisode" />
                <action android:name="com.podcast.app.action.pausePlayback" />
                <action android:name="com.podcast.app.action.resumePlayback" />
                <action android:name="com.podcast.app.action.skipForward" />
                <action android:name="com.podcast.app.action.skipBackward" />
                <action android:name="com.podcast.app.action.setPlaybackSpeed" />
                <action android:name="com.podcast.app.action.searchPodcasts" />
                <action android:name="com.podcast.app.action.addPodcast" />
                <action android:name="com.podcast.app.action.removePodcast" />
                <action android:name="com.podcast.app.action.getSubscribedPodcasts" />
                <action android:name="com.podcast.app.action.getPlaybackStatus" />
                <action android:name="com.podcast.app.action.getNextUnplayedEpisode" />
                <action android:name="com.podcast.app.action.getTranscript" />
                <action android:name="com.podcast.app.action.getChapters" />
                <action android:name="com.podcast.app.action.markAsPlayed" />
                <action android:name="com.podcast.app.action.getPlaybackQueue" />
                <action android:name="com.podcast.app.action.clearQueue" />
            </intent-filter>
        </receiver>

        <service
            android:name=".service.MCPSocketService"
            android:exported="false" />

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

```kotlin
// MCPActionReceiver.kt - Broadcast Receiver for MCP Commands

package com.podcast.app.mcp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.podcast.app.PodcastApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import android.util.Log

class MCPActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("MCPActionReceiver", "Received action: ${intent.action}")
        
        val appContext = (context.applicationContext as PodcastApp)
        val playbackController = appContext.playbackController
        val podcastRepository = appContext.podcastRepository
        
        when (intent.action) {
            "com.podcast.app.action.playEpisode" -> {
                val podcastId = intent.getStringExtra("podcastId") ?: return
                val episodeId = intent.getStringExtra("episodeId") ?: return
                val startPos = intent.getIntExtra("startPosition", 0)
                
                GlobalScope.launch(Dispatchers.Main) {
                    playbackController.playEpisode(podcastId, episodeId, startPos)
                }
            }
            
            "com.podcast.app.action.pausePlayback" -> {
                playbackController.pause()
            }
            
            "com.podcast.app.action.resumePlayback" -> {
                playbackController.resume()
            }
            
            "com.podcast.app.action.skipForward" -> {
                val seconds = intent.getIntExtra("seconds", 15)
                playbackController.skipForward(seconds)
            }
            
            "com.podcast.app.action.skipBackward" -> {
                val seconds = intent.getIntExtra("seconds", 15)
                playbackController.skipBackward(seconds)
            }
            
            "com.podcast.app.action.setPlaybackSpeed" -> {
                val speed = intent.getFloatExtra("speed", 1.0f)
                playbackController.setPlaybackSpeed(speed)
            }
            
            "com.podcast.app.action.markAsPlayed" -> {
                val episodeId = intent.getStringExtra("episodeId") ?: return
                
                GlobalScope.launch(Dispatchers.IO) {
                    podcastRepository.markEpisodeAsPlayed(episodeId)
                }
            }
        }
    }
}
```

```kotlin
// MCPSocketService.kt - Local Socket Server for Structured IPC

package com.podcast.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.podcast.app.PodcastApp
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class MCPSocketService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var serverSocket: ServerSocket? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            startSocketServer()
        }
        return START_STICKY
    }
    
    private suspend fun startSocketServer() = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(0) // Use any available port
            val port = serverSocket!!.localPort
            Log.d("MCPSocketService", "Socket server listening on port $port")
            
            while (true) {
                val clientSocket = serverSocket!!.accept()
                scope.launch {
                    handleClientConnection(clientSocket)
                }
            }
        } catch (e: Exception) {
            Log.e("MCPSocketService", "Socket server error", e)
        }
    }
    
    private suspend fun handleClientConnection(socket: Socket) = withContext(Dispatchers.IO) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            val writer = PrintWriter(socket.outputStream, true)
            
            val app = application as PodcastApp
            
            // Read request
            val requestLine = reader.readLine() ?: return@withContext
            val request = Json.decodeFromString<MCPRequest>(requestLine)
            
            // Execute action
            val response = when (request.action) {
                "playEpisode" -> {
                    val podcastId = request.params["podcastId"] as? String
                    val episodeId = request.params["episodeId"] as? String
                    if (podcastId != null && episodeId != null) {
                        app.playbackController.playEpisode(podcastId, episodeId, 0)
                        MCPResponse(status = "success", action = request.action)
                    } else {
                        MCPResponse(status = "error", action = request.action, error = "Missing parameters")
                    }
                }
                
                "getPlaybackStatus" -> {
                    val status = app.playbackController.getStatus()
                    MCPResponse(status = "success", action = request.action, data = status)
                }
                
                else -> MCPResponse(status = "error", action = request.action, error = "Unknown action")
            }
            
            // Send response
            writer.println(Json.encodeToString(MCPResponse.serializer(), response))
            socket.close()
        } catch (e: Exception) {
            Log.e("MCPSocketService", "Client handler error", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        scope.cancel()
        serverSocket?.close()
        super.onDestroy()
    }
}

// Data classes for IPC
@kotlinx.serialization.Serializable
data class MCPRequest(
    val action: String,
    val params: Map<String, Any> = emptyMap()
)

@kotlinx.serialization.Serializable
data class MCPResponse(
    val status: String,
    val action: String,
    val error: String? = null,
    val data: Any? = null
)
```

## Part 3: Connecting Claude/Ollama to MCP Server

### Using with Claude (Desktop)

```json
{
  "mcpServers": {
    "podcast": {
      "command": "python",
      "args": ["/path/to/podcast-app-mcp/server.py"],
      "description": "Podcast app control via MCP"
    }
  }
}
```

Place in: `~/.config/Claude/claude_desktop_config.json`

### Using with Ollama

```bash
# Start Ollama with MCP server
ollama serve &

# Run podcast MCP server
python /path/to/server.py &

# Connect via CLI or API
# The server will be available at stdio transport
```

### Example Conversations

```
User: "Play the latest episode of Reply All"
→ LLM calls search_podcasts("Reply All")
→ LLM calls get_next_unplayed_episode(podcast_id)
→ LLM calls play_episode(podcast_id, episode_id)
→ LLM responds: "Playing the latest episode of Reply All"

User: "What am I currently listening to?"
→ LLM calls get_playback_status()
→ LLM responds with current episode, position, duration

User: "Skip forward 30 seconds"
→ LLM calls skip_forward(30)
→ LLM responds: "Skipped ahead 30 seconds"

User: "Show me the transcript"
→ LLM calls get_playback_status() to find current episode
→ LLM calls get_episode_transcript(episode_id)
→ LLM displays transcript
```

---

## Requirements.txt

```
mcp>=0.8.0
fastmcp>=0.5.0
anthropic>=0.12.0
pydantic>=2.0.0
python-socketio>=5.0.0
aiohttp>=3.8.0
```

---

## Running the Complete System

```bash
# 1. Start Android app on GrapheneOS device
# (APK built from Kotlin source)

# 2. Forward socket from device to development machine (if needed)
adb forward tcp:50000 localabstract:podcast_app

# 3. Start MCP server
cd podcast-app-mcp
python server.py

# 4. Start Claude Desktop or Ollama
# Both will auto-connect to MCP server via config

# 5. Begin voice commands
# "Play the latest Lex Friedman episode"
# "What podcast am I listening to?"
# "Skip back 1 minute"
```

---

## Testing MCP Server Locally

```python
# test_mcp.py

import asyncio
import json
from server import (
    play_episode, pause, resume, skip_forward,
    search_podcasts, get_playback_status,
    add_podcast_to_library, get_subscribed_podcasts
)

async def test_search():
    """Test podcast search"""
    results = await search_podcasts("technology", limit=5)
    print("Search results:", json.dumps(results, indent=2))

async def test_playback():
    """Test playback control"""
    status = await get_playback_status()
    print("Status:", status)
    
    if not status.get("is_playing"):
        # Play first available episode
        podcasts = await get_subscribed_podcasts()
        if podcasts:
            result = await play_episode(
                podcasts[0]["id"],
                "episode_123"
            )
            print("Play result:", result)

async def main():
    await test_search()
    await test_playback()

if __name__ == "__main__":
    asyncio.run(main())
```

Run: `python test_mcp.py`

---

