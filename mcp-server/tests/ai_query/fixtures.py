"""
Test Fixtures for AI Query Testing

Contains test queries, expected interpretations, and mock responses
for testing the AI query tool.
"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class TestQuery:
    """A test case for query interpretation."""
    query: str
    expected_type: str
    expected_query: str
    description: str

    def matches(self, actual_type: str, actual_query: str) -> bool:
        """Check if actual results match expected (case-insensitive query match)."""
        return (
            actual_type == self.expected_type and
            actual_query.lower() == self.expected_query.lower()
        )


# Test queries matching the examples in the system prompt
TEST_QUERIES = [
    # Person searches
    TestQuery(
        query="recent podcasts with david deutsch",
        expected_type="byperson",
        expected_query="David Deutsch",
        description="Person search with filler words",
    ),
    TestQuery(
        query="episodes with elon musk",
        expected_type="byperson",
        expected_query="Elon Musk",
        description="Person search - episodes with",
    ),
    TestQuery(
        query="interviews featuring naval ravikant",
        expected_type="byperson",
        expected_query="Naval Ravikant",
        description="Person search - interviews featuring",
    ),
    TestQuery(
        query="sam harris conversations",
        expected_type="byperson",
        expected_query="Sam Harris",
        description="Person search - conversations",
    ),

    # Title searches
    TestQuery(
        query="joe rogans recent guests",
        expected_type="bytitle",
        expected_query="Joe Rogan",
        description="Title search with possessive and filler",
    ),
    TestQuery(
        query="find the lex fridman podcast",
        expected_type="bytitle",
        expected_query="Lex Fridman",
        description="Title search - find the podcast",
    ),
    TestQuery(
        query="huberman lab episodes",
        expected_type="bytitle",
        expected_query="Huberman Lab",
        description="Title search - podcast name + episodes",
    ),
    TestQuery(
        query="show me the tim ferriss show",
        expected_type="bytitle",
        expected_query="Tim Ferriss",
        description="Title search - show me",
    ),

    # Term/topic searches
    TestQuery(
        query="podcasts about quantum computing",
        expected_type="byterm",
        expected_query="quantum computing",
        description="Topic search - about",
    ),
    TestQuery(
        query="artificial intelligence discussions",
        expected_type="byterm",
        expected_query="artificial intelligence",
        description="Topic search - discussions",
    ),
    TestQuery(
        query="cryptocurrency and blockchain",
        expected_type="byterm",
        expected_query="cryptocurrency blockchain",
        description="Topic search - multiple terms",
    ),
    TestQuery(
        query="mental health podcasts",
        expected_type="byterm",
        expected_query="mental health",
        description="Topic search - health topic",
    ),

    # Edge cases
    TestQuery(
        query="AI",
        expected_type="byterm",
        expected_query="AI",
        description="Single short term",
    ),
    TestQuery(
        query="true crime",
        expected_type="byterm",
        expected_query="true crime",
        description="Genre search",
    ),
]


# Queries that should fail sanitization
INVALID_QUERIES = [
    "",  # Empty
    "   ",  # Whitespace only
    "a" * 501,  # Too long
    "<script>alert(1)</script>",  # After sanitization becomes empty-ish
]


# Queries with special characters that should be sanitized
SANITIZATION_TEST_CASES = [
    ("hello<world>", "helloworld"),
    ("test;query", "testquery"),
    ("search & find", "search  find"),
    ("pipe|test", "pipetest"),
    ("  spaces  ", "spaces"),
    ("normal query", "normal query"),
]


# Mock Claude API responses for offline testing
MOCK_RESPONSES = {
    "success_byperson": {
        "content": [
            {
                "type": "text",
                "text": '{"search_type": "byperson", "query": "David Deutsch", "explanation": "Searching for podcast episodes featuring David Deutsch as a guest"}'
            }
        ],
        "id": "msg_mock_1",
        "model": "claude-haiku-4-5-20251001",
        "role": "assistant",
        "stop_reason": "end_turn",
        "type": "message",
        "usage": {"input_tokens": 100, "output_tokens": 50}
    },
    "success_bytitle": {
        "content": [
            {
                "type": "text",
                "text": '{"search_type": "bytitle", "query": "Joe Rogan", "explanation": "Searching for the Joe Rogan podcast"}'
            }
        ],
        "id": "msg_mock_2",
        "model": "claude-haiku-4-5-20251001",
        "role": "assistant",
        "stop_reason": "end_turn",
        "type": "message",
        "usage": {"input_tokens": 100, "output_tokens": 50}
    },
    "success_byterm": {
        "content": [
            {
                "type": "text",
                "text": '{"search_type": "byterm", "query": "quantum computing", "explanation": "Searching for podcasts about quantum computing"}'
            }
        ],
        "id": "msg_mock_3",
        "model": "claude-haiku-4-5-20251001",
        "role": "assistant",
        "stop_reason": "end_turn",
        "type": "message",
        "usage": {"input_tokens": 100, "output_tokens": 50}
    },
    "success_with_markdown": {
        "content": [
            {
                "type": "text",
                "text": '```json\n{"search_type": "byperson", "query": "Test Person", "explanation": "Test"}\n```'
            }
        ],
        "id": "msg_mock_4",
        "model": "claude-haiku-4-5-20251001",
        "role": "assistant",
        "stop_reason": "end_turn",
        "type": "message",
        "usage": {"input_tokens": 100, "output_tokens": 50}
    },
    "malformed_json": {
        "content": [
            {
                "type": "text",
                "text": '{"search_type": "byterm", "query": incomplete'
            }
        ],
        "id": "msg_mock_5",
        "model": "claude-haiku-4-5-20251001",
        "role": "assistant",
        "stop_reason": "end_turn",
        "type": "message",
        "usage": {"input_tokens": 100, "output_tokens": 50}
    },
    "empty_query": {
        "content": [
            {
                "type": "text",
                "text": '{"search_type": "byterm", "query": "", "explanation": "Empty"}'
            }
        ],
        "id": "msg_mock_6",
        "model": "claude-haiku-4-5-20251001",
        "role": "assistant",
        "stop_reason": "end_turn",
        "type": "message",
        "usage": {"input_tokens": 100, "output_tokens": 50}
    },
    "empty_content": {
        "content": [],
        "id": "msg_mock_7",
        "model": "claude-haiku-4-5-20251001",
        "role": "assistant",
        "stop_reason": "end_turn",
        "type": "message",
        "usage": {"input_tokens": 100, "output_tokens": 0}
    },
    "api_error": {
        "type": "error",
        "error": {
            "type": "invalid_request_error",
            "message": "Invalid API key"
        }
    },
}


def get_mock_response(key: str) -> dict:
    """Get a mock response by key."""
    return MOCK_RESPONSES.get(key, MOCK_RESPONSES["success_byterm"])
