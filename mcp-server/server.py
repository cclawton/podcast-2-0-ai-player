#!/usr/bin/env python3
"""
Podcast 2.0 MCP Server

A Model Context Protocol server for controlling the Podcast 2.0 Android app.
Provides tools for playback control, library management, and episode discovery.

Usage:
    python server.py

Or via MCP stdio:
    python -m mcp.server.stdio server:app
"""

import asyncio
import json
import socket
import hashlib
import hmac
import time
from dataclasses import dataclass
from typing import Optional
from mcp.server import Server
from mcp.types import Tool, TextContent
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Create the MCP server
app = Server("podcast-control")

# Android bridge configuration
ANDROID_SOCKET_NAME = "podcast_app_mcp"
SESSION_KEY: Optional[str] = None


@dataclass
class AndroidBridge:
    """Bridge for communicating with the Android app via socket or ADB."""

    socket_name: str = ANDROID_SOCKET_NAME
    use_adb_fallback: bool = True

    async def send_command(self, action: str, params: dict) -> dict:
        """Send a command to the Android app."""
        request_id = f"{int(time.time() * 1000)}"

        request = {
            "id": request_id,
            "action": action,
            "params": params,
            "timestamp": int(time.time() * 1000)
        }

        # Add auth token if session key is available
        if SESSION_KEY:
            auth_data = f"{request_id}{action}{request['timestamp']}"
            request["authToken"] = self._generate_hmac(auth_data)

        try:
            return await self._send_via_socket(request)
        except Exception as e:
            logger.warning(f"Socket connection failed: {e}")
            if self.use_adb_fallback:
                return await self._send_via_adb(action, params)
            raise

    async def _send_via_socket(self, request: dict) -> dict:
        """Send command via abstract namespace socket."""
        # Note: On Android, this would use LocalSocket
        # For development/testing, we use a regular socket
        reader, writer = await asyncio.open_unix_connection(
            f"\0{self.socket_name}"
        )

        try:
            writer.write(json.dumps(request).encode() + b"\n")
            await writer.drain()

            response_data = await reader.readline()
            return json.loads(response_data.decode())
        finally:
            writer.close()
            await writer.wait_closed()

    async def _send_via_adb(self, action: str, params: dict) -> dict:
        """Fallback: Send command via ADB broadcast."""
        import subprocess

        cmd = [
            "adb", "shell", "am", "broadcast",
            "-a", "com.podcast.app.action.MCP_COMMAND",
            "--es", "action", action
        ]

        for key, value in params.items():
            cmd.extend(["--es", key, str(value)])

        try:
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
            if result.returncode == 0:
                return {"status": "success", "action": action}
            return {"status": "error", "error": result.stderr}
        except Exception as e:
            return {"status": "error", "error": str(e)}

    def _generate_hmac(self, data: str) -> str:
        """Generate HMAC for authentication."""
        if not SESSION_KEY:
            return ""
        return hmac.new(
            SESSION_KEY.encode(),
            data.encode(),
            hashlib.sha256
        ).hexdigest()


# Global bridge instance
bridge = AndroidBridge()


# =============================================================================
# MCP TOOLS - Playback Control
# =============================================================================

@app.call_tool()
async def play_episode(arguments: dict) -> list[TextContent]:
    """Start playing a specific episode."""
    episode_id = arguments.get("episode_id")
    start_position = arguments.get("start_position", 0)

    if not episode_id:
        return [TextContent(type="text", text='{"error": "episode_id is required"}')]

    result = await bridge.send_command("playEpisode", {
        "episodeId": str(episode_id),
        "startPosition": str(start_position)
    })

    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def pause(arguments: dict) -> list[TextContent]:
    """Pause the current playback."""
    result = await bridge.send_command("pausePlayback", {})
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def resume(arguments: dict) -> list[TextContent]:
    """Resume playback."""
    result = await bridge.send_command("resumePlayback", {})
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def skip_forward(arguments: dict) -> list[TextContent]:
    """Skip forward by specified seconds (default 15)."""
    seconds = arguments.get("seconds", 15)
    result = await bridge.send_command("skipForward", {"seconds": str(seconds)})
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def skip_backward(arguments: dict) -> list[TextContent]:
    """Skip backward by specified seconds (default 15)."""
    seconds = arguments.get("seconds", 15)
    result = await bridge.send_command("skipBackward", {"seconds": str(seconds)})
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def set_playback_speed(arguments: dict) -> list[TextContent]:
    """Set playback speed (0.5 to 3.0)."""
    speed = arguments.get("speed", 1.0)

    # Validate speed
    valid_speeds = {0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0}
    if speed not in valid_speeds:
        return [TextContent(
            type="text",
            text=f'{{"error": "Invalid speed. Valid speeds: {sorted(valid_speeds)}"}}'
        )]

    result = await bridge.send_command("setPlaybackSpeed", {"speed": str(speed)})
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def get_playback_status(arguments: dict) -> list[TextContent]:
    """Get current playback status."""
    result = await bridge.send_command("getPlaybackStatus", {})
    return [TextContent(type="text", text=json.dumps(result))]


# =============================================================================
# MCP TOOLS - Library Management
# =============================================================================

@app.call_tool()
async def search_podcasts(arguments: dict) -> list[TextContent]:
    """Search for podcasts by term."""
    query = arguments.get("query")
    limit = arguments.get("limit", 10)

    if not query:
        return [TextContent(type="text", text='{"error": "query is required"}')]

    result = await bridge.send_command("searchPodcasts", {
        "query": query,
        "limit": str(limit)
    })
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def get_subscribed_podcasts(arguments: dict) -> list[TextContent]:
    """Get the user's subscribed podcasts."""
    result = await bridge.send_command("getSubscribedPodcasts", {})
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def add_to_library(arguments: dict) -> list[TextContent]:
    """Subscribe to a podcast."""
    podcast_id = arguments.get("podcast_id")

    if not podcast_id:
        return [TextContent(type="text", text='{"error": "podcast_id is required"}')]

    result = await bridge.send_command("addPodcast", {"podcastId": str(podcast_id)})
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def remove_from_library(arguments: dict) -> list[TextContent]:
    """Unsubscribe from a podcast."""
    podcast_id = arguments.get("podcast_id")

    if not podcast_id:
        return [TextContent(type="text", text='{"error": "podcast_id is required"}')]

    result = await bridge.send_command("removePodcast", {"podcastId": str(podcast_id)})
    return [TextContent(type="text", text=json.dumps(result))]


# =============================================================================
# MCP TOOLS - Episode Discovery
# =============================================================================

@app.call_tool()
async def get_next_episode(arguments: dict) -> list[TextContent]:
    """Get the next unplayed episode for a podcast."""
    podcast_id = arguments.get("podcast_id")

    if not podcast_id:
        return [TextContent(type="text", text='{"error": "podcast_id is required"}')]

    result = await bridge.send_command("getNextUnplayedEpisode", {
        "podcastId": str(podcast_id)
    })
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def mark_as_played(arguments: dict) -> list[TextContent]:
    """Mark an episode as played."""
    episode_id = arguments.get("episode_id")

    if not episode_id:
        return [TextContent(type="text", text='{"error": "episode_id is required"}')]

    result = await bridge.send_command("markAsPlayed", {"episodeId": str(episode_id)})
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def get_transcript(arguments: dict) -> list[TextContent]:
    """Get the transcript for an episode (Podcast 2.0 feature)."""
    episode_id = arguments.get("episode_id")

    if not episode_id:
        return [TextContent(type="text", text='{"error": "episode_id is required"}')]

    result = await bridge.send_command("getTranscript", {"episodeId": str(episode_id)})
    return [TextContent(type="text", text=json.dumps(result))]


@app.call_tool()
async def get_chapters(arguments: dict) -> list[TextContent]:
    """Get chapters for an episode (Podcast 2.0 feature)."""
    episode_id = arguments.get("episode_id")

    if not episode_id:
        return [TextContent(type="text", text='{"error": "episode_id is required"}')]

    result = await bridge.send_command("getChapters", {"episodeId": str(episode_id)})
    return [TextContent(type="text", text=json.dumps(result))]


# =============================================================================
# MCP RESOURCES
# =============================================================================

@app.resource(uri="podcast://subscribed")
async def get_subscribed_resource() -> str:
    """Resource: Current subscribed podcasts."""
    result = await bridge.send_command("getSubscribedPodcasts", {})
    return json.dumps(result)


@app.resource(uri="podcast://playback-status")
async def get_playback_status_resource() -> str:
    """Resource: Current playback status."""
    result = await bridge.send_command("getPlaybackStatus", {})
    return json.dumps(result)


@app.resource(uri="podcast://queue")
async def get_queue_resource() -> str:
    """Resource: Current playback queue."""
    result = await bridge.send_command("getPlaybackQueue", {})
    return json.dumps(result)


# =============================================================================
# MCP PROMPTS
# =============================================================================

@app.prompt(name="driving_mode")
async def driving_mode_prompt(context: str = "") -> str:
    """Prompt for safe, concise responses while driving."""
    return f"""You are controlling a podcast app for someone who is driving.
Keep responses VERY short and focused. Use simple confirmations like "Playing now" or "Paused".
Do not provide long explanations or options while the user is driving.

Current context: {context}

Available quick commands:
- Play/pause the current episode
- Skip forward/backward
- Play next unplayed episode
- Change playback speed

Always prioritize safety. If unsure, ask the user to confirm when safe to do so."""


@app.prompt(name="recommend_episodes")
async def recommend_episodes_prompt(genres: str = "") -> str:
    """Prompt for episode recommendations."""
    return f"""Based on the user's listening history and preferences, recommend episodes.

Preferred genres: {genres}

Consider:
1. Episodes from subscribed podcasts they haven't finished
2. New episodes from favorite shows
3. Popular episodes in their preferred categories
4. Episodes similar to recently played content

Provide 3-5 recommendations with brief descriptions."""


# =============================================================================
# TOOL REGISTRATION
# =============================================================================

# Register all tools with their schemas
app.add_tool(Tool(
    name="play_episode",
    description="Start playing a specific episode",
    inputSchema={
        "type": "object",
        "properties": {
            "episode_id": {"type": "string", "description": "The episode ID to play"},
            "start_position": {"type": "integer", "description": "Start position in seconds", "default": 0}
        },
        "required": ["episode_id"]
    }
))

app.add_tool(Tool(
    name="pause",
    description="Pause current playback",
    inputSchema={"type": "object", "properties": {}}
))

app.add_tool(Tool(
    name="resume",
    description="Resume playback",
    inputSchema={"type": "object", "properties": {}}
))

app.add_tool(Tool(
    name="skip_forward",
    description="Skip forward by specified seconds",
    inputSchema={
        "type": "object",
        "properties": {
            "seconds": {"type": "integer", "description": "Seconds to skip", "default": 15}
        }
    }
))

app.add_tool(Tool(
    name="skip_backward",
    description="Skip backward by specified seconds",
    inputSchema={
        "type": "object",
        "properties": {
            "seconds": {"type": "integer", "description": "Seconds to skip", "default": 15}
        }
    }
))

app.add_tool(Tool(
    name="set_playback_speed",
    description="Set playback speed (0.5 to 3.0)",
    inputSchema={
        "type": "object",
        "properties": {
            "speed": {"type": "number", "description": "Playback speed (0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0)"}
        },
        "required": ["speed"]
    }
))

app.add_tool(Tool(
    name="get_playback_status",
    description="Get current playback status",
    inputSchema={"type": "object", "properties": {}}
))

app.add_tool(Tool(
    name="search_podcasts",
    description="Search for podcasts by term",
    inputSchema={
        "type": "object",
        "properties": {
            "query": {"type": "string", "description": "Search query"},
            "limit": {"type": "integer", "description": "Max results", "default": 10}
        },
        "required": ["query"]
    }
))

app.add_tool(Tool(
    name="get_subscribed_podcasts",
    description="Get the user's subscribed podcasts",
    inputSchema={"type": "object", "properties": {}}
))

app.add_tool(Tool(
    name="add_to_library",
    description="Subscribe to a podcast",
    inputSchema={
        "type": "object",
        "properties": {
            "podcast_id": {"type": "string", "description": "Podcast ID to subscribe to"}
        },
        "required": ["podcast_id"]
    }
))

app.add_tool(Tool(
    name="remove_from_library",
    description="Unsubscribe from a podcast",
    inputSchema={
        "type": "object",
        "properties": {
            "podcast_id": {"type": "string", "description": "Podcast ID to unsubscribe from"}
        },
        "required": ["podcast_id"]
    }
))

app.add_tool(Tool(
    name="get_next_episode",
    description="Get the next unplayed episode for a podcast",
    inputSchema={
        "type": "object",
        "properties": {
            "podcast_id": {"type": "string", "description": "Podcast ID"}
        },
        "required": ["podcast_id"]
    }
))

app.add_tool(Tool(
    name="mark_as_played",
    description="Mark an episode as played",
    inputSchema={
        "type": "object",
        "properties": {
            "episode_id": {"type": "string", "description": "Episode ID"}
        },
        "required": ["episode_id"]
    }
))

app.add_tool(Tool(
    name="get_transcript",
    description="Get the transcript for an episode (Podcast 2.0)",
    inputSchema={
        "type": "object",
        "properties": {
            "episode_id": {"type": "string", "description": "Episode ID"}
        },
        "required": ["episode_id"]
    }
))

app.add_tool(Tool(
    name="get_chapters",
    description="Get chapters for an episode (Podcast 2.0)",
    inputSchema={
        "type": "object",
        "properties": {
            "episode_id": {"type": "string", "description": "Episode ID"}
        },
        "required": ["episode_id"]
    }
))


if __name__ == "__main__":
    import mcp.server.stdio
    mcp.server.stdio.run(app)
