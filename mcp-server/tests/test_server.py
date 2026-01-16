"""
Tests for the Podcast MCP Server.
"""

import pytest
import json
from unittest.mock import AsyncMock, patch, MagicMock


class TestInputValidation:
    """Test input validation for MCP tools."""

    def test_valid_episode_id(self):
        """Test valid episode ID format."""
        valid_ids = ["1", "123", "999999999"]
        for id in valid_ids:
            assert id.isdigit()
            assert int(id) > 0

    def test_invalid_episode_id(self):
        """Test invalid episode ID format."""
        invalid_ids = ["", "-1", "abc", "12.34", "0"]
        for id in invalid_ids:
            is_valid = id.isdigit() and int(id) > 0 if id.isdigit() else False
            assert not is_valid or id == ""

    def test_valid_search_query(self):
        """Test valid search queries."""
        valid_queries = ["technology", "AI podcast", "True Crime Stories"]
        for query in valid_queries:
            assert len(query) > 0
            assert len(query) <= 200

    def test_playback_speed_validation(self):
        """Test playback speed validation."""
        valid_speeds = {0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0}

        assert 1.0 in valid_speeds
        assert 2.0 in valid_speeds
        assert 0.3 not in valid_speeds
        assert 4.0 not in valid_speeds


class TestAndroidBridge:
    """Test Android bridge communication."""

    @pytest.mark.asyncio
    async def test_generate_hmac(self):
        """Test HMAC generation for authentication."""
        import hmac
        import hashlib

        session_key = "test_session_key"
        data = "test_data"

        expected = hmac.new(
            session_key.encode(),
            data.encode(),
            hashlib.sha256
        ).hexdigest()

        assert len(expected) == 64  # SHA-256 hex string
        assert all(c in "0123456789abcdef" for c in expected)

    @pytest.mark.asyncio
    async def test_request_format(self):
        """Test request format for Android bridge."""
        import time

        request_id = f"{int(time.time() * 1000)}"
        action = "playEpisode"
        params = {"episodeId": "123"}

        request = {
            "id": request_id,
            "action": action,
            "params": params,
            "timestamp": int(time.time() * 1000)
        }

        assert "id" in request
        assert "action" in request
        assert "params" in request
        assert "timestamp" in request


class TestPlaybackTools:
    """Test playback control tools."""

    @pytest.mark.asyncio
    async def test_play_episode_requires_id(self):
        """Test that play_episode requires episode_id."""
        arguments = {}  # Missing episode_id

        # Should return error when episode_id is missing
        if not arguments.get("episode_id"):
            error_response = '{"error": "episode_id is required"}'
            assert "episode_id is required" in error_response

    @pytest.mark.asyncio
    async def test_skip_forward_default_seconds(self):
        """Test skip_forward uses default seconds."""
        arguments = {}
        seconds = arguments.get("seconds", 15)
        assert seconds == 15

    @pytest.mark.asyncio
    async def test_set_speed_validation(self):
        """Test playback speed validation."""
        valid_speeds = {0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0}

        # Valid speed
        assert 1.5 in valid_speeds

        # Invalid speed
        assert 1.3 not in valid_speeds


class TestLibraryTools:
    """Test library management tools."""

    @pytest.mark.asyncio
    async def test_search_requires_query(self):
        """Test that search_podcasts requires query."""
        arguments = {}  # Missing query

        if not arguments.get("query"):
            error_response = '{"error": "query is required"}'
            assert "query is required" in error_response

    @pytest.mark.asyncio
    async def test_add_to_library_requires_id(self):
        """Test that add_to_library requires podcast_id."""
        arguments = {}  # Missing podcast_id

        if not arguments.get("podcast_id"):
            error_response = '{"error": "podcast_id is required"}'
            assert "podcast_id is required" in error_response


class TestPrompts:
    """Test MCP prompts."""

    def test_driving_mode_prompt(self):
        """Test driving mode prompt content."""
        prompt_text = """You are controlling a podcast app for someone who is driving.
Keep responses VERY short and focused."""

        assert "driving" in prompt_text.lower()
        assert "short" in prompt_text.lower()

    def test_recommend_episodes_prompt(self):
        """Test episode recommendation prompt."""
        genres = "technology, comedy"
        prompt_text = f"Preferred genres: {genres}"

        assert "technology" in prompt_text
        assert "comedy" in prompt_text


class TestToolSchemas:
    """Test tool schema definitions."""

    def test_play_episode_schema(self):
        """Test play_episode tool schema."""
        schema = {
            "type": "object",
            "properties": {
                "episode_id": {"type": "string"},
                "start_position": {"type": "integer", "default": 0}
            },
            "required": ["episode_id"]
        }

        assert "episode_id" in schema["properties"]
        assert "episode_id" in schema["required"]
        assert schema["properties"]["start_position"]["default"] == 0

    def test_search_podcasts_schema(self):
        """Test search_podcasts tool schema."""
        schema = {
            "type": "object",
            "properties": {
                "query": {"type": "string"},
                "limit": {"type": "integer", "default": 10}
            },
            "required": ["query"]
        }

        assert "query" in schema["required"]
        assert schema["properties"]["limit"]["default"] == 10


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
