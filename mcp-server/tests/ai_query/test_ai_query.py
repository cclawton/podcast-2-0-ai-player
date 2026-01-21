"""
AI Query Test Suite

Comprehensive tests for the AI query interpretation tool.
Run with: pytest tests/ai_query/test_ai_query.py -v
"""

import json
import os
import pytest
from typing import Optional
from unittest.mock import Mock, patch

from rich.console import Console
from rich.table import Table

from .query_interpreter import (
    sanitize_query,
    parse_claude_response,
    get_system_prompt,
    QueryInterpretation,
    validate_search_type,
    normalize_search_type,
)
from .fixtures import (
    TEST_QUERIES,
    INVALID_QUERIES,
    SANITIZATION_TEST_CASES,
    MOCK_RESPONSES,
    get_mock_response,
)


console = Console()


# ============================================================================
# Unit Tests - Query Sanitization
# ============================================================================

class TestSanitization:
    """Tests for query sanitization logic."""

    def test_normal_query(self):
        """Normal queries should pass through with trimming."""
        assert sanitize_query("  hello world  ") == "hello world"

    def test_empty_query(self):
        """Empty queries should return None."""
        assert sanitize_query("") is None
        assert sanitize_query("   ") is None

    def test_too_long_query(self):
        """Queries over 500 chars should return None."""
        long_query = "a" * 501
        assert sanitize_query(long_query) is None

        # Exactly 500 should work
        assert sanitize_query("a" * 500) == "a" * 500

    def test_dangerous_characters_removed(self):
        """Dangerous characters should be stripped."""
        assert sanitize_query("hello<world>") == "helloworld"
        assert sanitize_query("test;query") == "testquery"
        assert sanitize_query("search & find") == "search  find"
        assert sanitize_query("pipe|test") == "pipetest"

    def test_only_dangerous_characters(self):
        """Query with only dangerous chars should return None."""
        assert sanitize_query("<>;&|") is None

    @pytest.mark.parametrize("input_query,expected", SANITIZATION_TEST_CASES)
    def test_sanitization_cases(self, input_query: str, expected: str):
        """Test all sanitization cases from fixtures."""
        result = sanitize_query(input_query)
        assert result == expected


# ============================================================================
# Unit Tests - Response Parsing
# ============================================================================

class TestResponseParsing:
    """Tests for Claude response parsing logic."""

    def test_parse_success_byperson(self):
        """Parse a successful byperson response."""
        response = json.dumps(get_mock_response("success_byperson"))
        result = parse_claude_response(response)

        assert result is not None
        assert result.search_type == "byperson"
        assert result.query == "David Deutsch"

    def test_parse_success_bytitle(self):
        """Parse a successful bytitle response."""
        response = json.dumps(get_mock_response("success_bytitle"))
        result = parse_claude_response(response)

        assert result is not None
        assert result.search_type == "bytitle"
        assert result.query == "Joe Rogan"

    def test_parse_success_byterm(self):
        """Parse a successful byterm response."""
        response = json.dumps(get_mock_response("success_byterm"))
        result = parse_claude_response(response)

        assert result is not None
        assert result.search_type == "byterm"
        assert result.query == "quantum computing"

    def test_parse_with_markdown_code_blocks(self):
        """Parse response wrapped in markdown code blocks."""
        response = json.dumps(get_mock_response("success_with_markdown"))
        result = parse_claude_response(response)

        assert result is not None
        assert result.search_type == "byperson"
        assert result.query == "Test Person"

    def test_parse_malformed_json(self):
        """Malformed JSON should return None."""
        response = json.dumps(get_mock_response("malformed_json"))
        result = parse_claude_response(response)

        assert result is None

    def test_parse_empty_query(self):
        """Empty query field should return None."""
        response = json.dumps(get_mock_response("empty_query"))
        result = parse_claude_response(response)

        assert result is None

    def test_parse_empty_content(self):
        """Empty content array should return None."""
        response = json.dumps(get_mock_response("empty_content"))
        result = parse_claude_response(response)

        assert result is None

    def test_parse_invalid_json(self):
        """Invalid JSON string should return None."""
        result = parse_claude_response("not json at all")
        assert result is None

    def test_parse_json_with_extra_text(self):
        """JSON embedded in extra text should still parse."""
        response = json.dumps({
            "content": [{
                "type": "text",
                "text": 'Here is the result: {"search_type": "byterm", "query": "test", "explanation": "test"} Hope this helps!'
            }]
        })
        result = parse_claude_response(response)

        assert result is not None
        assert result.query == "test"


# ============================================================================
# Unit Tests - Search Type Validation
# ============================================================================

class TestSearchTypeValidation:
    """Tests for search type validation."""

    def test_valid_types(self):
        """Valid search types should pass."""
        assert validate_search_type("byperson") is True
        assert validate_search_type("bytitle") is True
        assert validate_search_type("byterm") is True

    def test_invalid_types(self):
        """Invalid search types should fail."""
        assert validate_search_type("invalid") is False
        assert validate_search_type("") is False
        assert validate_search_type("BYPERSON") is False  # Case sensitive

    def test_normalize_valid(self):
        """Normalization should lowercase and validate."""
        assert normalize_search_type("BYPERSON") == "byperson"
        assert normalize_search_type("ByTitle") == "bytitle"
        assert normalize_search_type("  byterm  ") == "byterm"

    def test_normalize_invalid(self):
        """Invalid types should normalize to byterm."""
        assert normalize_search_type("invalid") == "byterm"
        assert normalize_search_type("") == "byterm"


# ============================================================================
# Unit Tests - System Prompt
# ============================================================================

class TestSystemPrompt:
    """Tests for the system prompt."""

    def test_prompt_contains_search_types(self):
        """System prompt should document all search types."""
        prompt = get_system_prompt()

        assert "byperson" in prompt
        assert "bytitle" in prompt
        assert "byterm" in prompt

    def test_prompt_contains_examples(self):
        """System prompt should contain examples."""
        prompt = get_system_prompt()

        assert "joe rogans recent guests" in prompt.lower()
        assert "david deutsch" in prompt.lower()
        assert "quantum computing" in prompt.lower()

    def test_prompt_contains_rules(self):
        """System prompt should contain critical rules."""
        prompt = get_system_prompt()

        assert "CRITICAL RULES" in prompt
        assert "filler words" in prompt.lower()


# ============================================================================
# Integration Tests - Live API (requires ANTHROPIC_API_KEY)
# ============================================================================

@pytest.mark.skipif(
    not os.environ.get("ANTHROPIC_API_KEY"),
    reason="ANTHROPIC_API_KEY not set"
)
class TestLiveAPI:
    """
    Integration tests that call the real Claude API.

    These tests require ANTHROPIC_API_KEY to be set.
    Run with: pytest tests/ai_query/test_ai_query.py -v -k TestLiveAPI
    """

    @pytest.fixture
    def client(self):
        """Create a Claude client for testing."""
        from .claude_client import ClaudeQueryClient
        return ClaudeQueryClient(verbose=False)

    def test_basic_person_query(self, client):
        """Test a basic person search query."""
        result = client.interpret_query("podcasts with david deutsch")

        assert result.success
        assert result.interpretation.search_type == "byperson"
        assert "deutsch" in result.interpretation.query.lower()

    def test_basic_title_query(self, client):
        """Test a basic title search query."""
        result = client.interpret_query("find the joe rogan podcast")

        assert result.success
        assert result.interpretation.search_type == "bytitle"
        assert "rogan" in result.interpretation.query.lower()

    def test_basic_term_query(self, client):
        """Test a basic term search query."""
        result = client.interpret_query("podcasts about quantum computing")

        assert result.success
        assert result.interpretation.search_type == "byterm"
        assert "quantum" in result.interpretation.query.lower()

    @pytest.mark.parametrize("test_case", TEST_QUERIES[:5])  # Test first 5 fixtures
    def test_fixture_queries(self, client, test_case):
        """Test queries from fixtures against live API."""
        result = client.interpret_query(test_case.query)

        assert result.success, f"Query failed: {result.error}"
        assert result.interpretation.search_type == test_case.expected_type, \
            f"Expected {test_case.expected_type}, got {result.interpretation.search_type}"


# ============================================================================
# CLI Runner
# ============================================================================

def run_interactive_tests(queries: Optional[list[str]] = None, verbose: bool = True):
    """
    Run interactive tests with rich output.

    Args:
        queries: List of queries to test. If None, uses TEST_QUERIES fixtures.
        verbose: If True, shows detailed Claude API output.
    """
    from .claude_client import ClaudeQueryClient

    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        console.print("[bold red]Error:[/bold red] ANTHROPIC_API_KEY not set")
        console.print("Set it with: export ANTHROPIC_API_KEY=sk-ant-...")
        return

    client = ClaudeQueryClient(api_key=api_key, verbose=verbose)

    test_queries = queries or [tq.query for tq in TEST_QUERIES]
    results = []

    for query in test_queries:
        result = client.interpret_query(query)
        results.append(result)

    # Summary table
    console.print("\n")
    console.print("=" * 70, style="bold blue")
    console.print("  TEST SUMMARY", style="bold white")
    console.print("=" * 70, style="bold blue")

    table = Table(show_header=True, header_style="bold magenta")
    table.add_column("Query", style="cyan", max_width=30)
    table.add_column("Type", style="yellow")
    table.add_column("Extracted Query", style="white")
    table.add_column("Status", style="green")

    passed = 0
    failed = 0

    for result in results:
        if result.success:
            status = "[green]PASS[/green]"
            passed += 1
            table.add_row(
                result.query[:30] + "..." if len(result.query) > 30 else result.query,
                result.interpretation.search_type,
                result.interpretation.query,
                status,
            )
        else:
            status = f"[red]FAIL: {result.error}[/red]"
            failed += 1
            table.add_row(
                result.query[:30] + "..." if len(result.query) > 30 else result.query,
                "-",
                "-",
                status,
            )

    console.print(table)
    console.print(f"\n[bold]Results:[/bold] {passed} passed, {failed} failed")


def main():
    """CLI entry point."""
    import argparse

    parser = argparse.ArgumentParser(
        description="AI Query Test Suite",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Run unit tests
  pytest tests/ai_query/test_ai_query.py -v

  # Run live API tests (requires ANTHROPIC_API_KEY)
  pytest tests/ai_query/test_ai_query.py -v -k TestLiveAPI

  # Run interactive tests with all fixtures
  python -m tests.ai_query.test_ai_query --run-fixtures

  # Test a single query
  python -m tests.ai_query.test_ai_query --query "podcasts about AI"

  # Test multiple queries
  python -m tests.ai_query.test_ai_query --query "joe rogan" --query "david deutsch"
        """,
    )
    parser.add_argument("--query", "-q", action="append", help="Query to test (can be repeated)")
    parser.add_argument("--run-fixtures", action="store_true", help="Run all fixture tests")
    parser.add_argument("--quiet", action="store_true", help="Minimal output")
    parser.add_argument("--unit-only", action="store_true", help="Run only unit tests (no API)")

    args = parser.parse_args()

    if args.unit_only:
        # Run unit tests via pytest
        import sys
        sys.exit(pytest.main([__file__, "-v", "-k", "not TestLiveAPI"]))

    if args.query:
        run_interactive_tests(queries=args.query, verbose=not args.quiet)
    elif args.run_fixtures:
        run_interactive_tests(verbose=not args.quiet)
    else:
        # Default: run unit tests
        import sys
        sys.exit(pytest.main([__file__, "-v"]))


if __name__ == "__main__":
    main()
