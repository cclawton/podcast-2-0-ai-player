"""
Query Interpreter Logic

This module replicates the Kotlin AISearchService query interpretation logic
in Python for testing and debugging.
"""

import json
import re
from dataclasses import dataclass
from typing import Optional


# Constants matching Android AISearchService.kt
MAX_QUERY_LENGTH = 500


@dataclass
class QueryInterpretation:
    """
    Represents Claude's interpretation of a natural language query.

    Attributes:
        search_type: One of "byperson", "bytitle", or "byterm"
        query: The extracted search term
        explanation: Claude's explanation of the interpretation
    """
    search_type: str
    query: str
    explanation: str

    def to_dict(self) -> dict:
        return {
            "search_type": self.search_type,
            "query": self.query,
            "explanation": self.explanation,
        }

    @classmethod
    def from_dict(cls, data: dict) -> "QueryInterpretation":
        return cls(
            search_type=data.get("search_type", "byterm"),
            query=data.get("query", ""),
            explanation=data.get("explanation", ""),
        )


def get_system_prompt() -> str:
    """
    Returns the exact system prompt used in AISearchService.kt (lines 101-121).

    This prompt instructs Claude how to interpret natural language podcast queries
    and extract the optimal search parameters for the PodcastIndex API.
    """
    return """You are a podcast search assistant for the PodcastIndex API. Your job is to interpret natural language queries and extract optimal search parameters.

AVAILABLE SEARCH TYPES:
- "byperson": Search for podcasts featuring a specific person (guest, host, or author). Use when the query mentions a person's name.
- "bytitle": Search podcast titles. Use when looking for a specific podcast show.
- "byterm": General keyword search across all podcast metadata. Use for topics, subjects, or when unsure.

CRITICAL RULES:
1. Extract ONLY the key search term (person name, podcast title, or topic) - NOT the full query phrase
2. Remove filler words like "recent", "latest", "episodes", "podcasts", "featuring", "with", "about", "find", "show me"
3. For person searches, use just the person's name (e.g., "David Deutsch" not "recent podcasts with David Deutsch")
4. For podcast searches, use just the podcast name (e.g., "Joe Rogan" not "joe rogans recent guests")

EXAMPLES:
- "joe rogans recent guests" → {"search_type": "bytitle", "query": "Joe Rogan", "explanation": "Searching for the Joe Rogan podcast to find recent episodes and guests"}
- "recent podcasts with david deutsch" → {"search_type": "byperson", "query": "David Deutsch", "explanation": "Searching for podcast episodes featuring David Deutsch as a guest"}
- "podcasts about quantum computing" → {"search_type": "byterm", "query": "quantum computing", "explanation": "Searching for podcasts about quantum computing"}
- "find the lex fridman podcast" → {"search_type": "bytitle", "query": "Lex Fridman", "explanation": "Searching for the Lex Fridman podcast"}
- "episodes with elon musk" → {"search_type": "byperson", "query": "Elon Musk", "explanation": "Searching for podcast episodes featuring Elon Musk"}

Respond with ONLY a JSON object with fields: search_type, query, explanation"""


def sanitize_query(query: str) -> Optional[str]:
    """
    Sanitize a user query before sending to Claude.

    Replicates AISearchService.kt lines 94-98:
    - Trims whitespace
    - Validates length (max 500 chars)
    - Removes dangerous characters: < > ; & |

    Args:
        query: Raw user input

    Returns:
        Sanitized query string, or None if invalid
    """
    trimmed = query.strip()

    # Check for empty or too long
    if not trimmed or len(trimmed) > MAX_QUERY_LENGTH:
        return None

    # Remove dangerous characters
    sanitized = re.sub(r'[<>;&|]', '', trimmed).strip()

    # Return None if empty after sanitization
    return sanitized if sanitized else None


def parse_claude_response(response_body: str) -> Optional[QueryInterpretation]:
    """
    Parse Claude's JSON response into a QueryInterpretation.

    Replicates AISearchService.kt lines 139-163:
    - Extracts text from Claude's response content
    - Strips markdown code blocks if present
    - Finds JSON object boundaries
    - Parses and validates required fields

    Args:
        response_body: Raw JSON response body from Claude API

    Returns:
        QueryInterpretation if parsing succeeds, None otherwise
    """
    try:
        json_data = json.loads(response_body)

        content = json_data.get("content", [])
        if not content:
            return None

        text = content[0].get("text", "").strip()
        if not text:
            return None

        # Strip markdown code blocks if present
        text = text.removeprefix("```json").removeprefix("```")
        text = text.removesuffix("```")
        text = text.strip()

        # Find JSON object boundaries
        start_idx = text.find('{')
        end_idx = text.rfind('}')

        if start_idx >= 0 and end_idx > start_idx:
            text = text[start_idx:end_idx + 1]

        parsed = json.loads(text)

        search_type = parsed.get("search_type", "byterm")
        query = parsed.get("query", "")
        explanation = parsed.get("explanation", "")

        if query:
            return QueryInterpretation(
                search_type=search_type,
                query=query,
                explanation=explanation,
            )

        return None

    except (json.JSONDecodeError, KeyError, IndexError, TypeError):
        return None


def validate_search_type(search_type: str) -> bool:
    """
    Validate that a search type is one of the allowed values.

    Args:
        search_type: The search type to validate

    Returns:
        True if valid, False otherwise
    """
    return search_type in ("byperson", "bytitle", "byterm")


def normalize_search_type(search_type: str) -> str:
    """
    Normalize a search type to a valid value.

    Args:
        search_type: The search type to normalize

    Returns:
        Normalized search type (defaults to "byterm" if invalid)
    """
    normalized = search_type.lower().strip()
    return normalized if validate_search_type(normalized) else "byterm"
