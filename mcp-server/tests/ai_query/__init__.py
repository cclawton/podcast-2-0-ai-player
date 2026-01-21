"""
AI Query Test Tool for Podcast 2.0 AI Player

This package provides Python testing tools for the AI query functionality,
allowing you to:
- See exact prompts sent to Claude
- Inspect raw responses
- Test query interpretation logic
- Execute PodcastIndex searches with interpreted queries
- Debug the full pipeline without rebuilding the Android app
"""

from .claude_client import ClaudeQueryClient
from .podcastindex_client import PodcastIndexClient, PodcastResult, SearchResult
from .query_interpreter import (
    sanitize_query,
    parse_claude_response,
    get_system_prompt,
    QueryInterpretation,
)
from .fixtures import TEST_QUERIES, MOCK_RESPONSES

__all__ = [
    # Claude
    "ClaudeQueryClient",
    # PodcastIndex
    "PodcastIndexClient",
    "PodcastResult",
    "SearchResult",
    # Query interpretation
    "sanitize_query",
    "parse_claude_response",
    "get_system_prompt",
    "QueryInterpretation",
    # Fixtures
    "TEST_QUERIES",
    "MOCK_RESPONSES",
]
