#!/usr/bin/env python3
"""
Standalone runner for AI Query tests.

Usage:
    python run_ai_query_test.py --query "podcasts with david deutsch"
    python run_ai_query_test.py --run-fixtures
    python run_ai_query_test.py --run-fixtures --quiet
"""

import sys
import os

# Add the mcp-server directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from tests.ai_query.claude_client import ClaudeQueryClient
from tests.ai_query.fixtures import TEST_QUERIES
from rich.console import Console
from rich.table import Table

console = Console()


def run_single_query(query: str, verbose: bool = True):
    """Test a single query."""
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        console.print("[bold red]Error:[/bold red] ANTHROPIC_API_KEY not set")
        sys.exit(1)

    with ClaudeQueryClient(api_key=api_key, verbose=verbose) as client:
        result = client.interpret_query(query)
        return result


def run_fixtures(verbose: bool = True):
    """Run all fixture tests."""
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        console.print("[bold red]Error:[/bold red] ANTHROPIC_API_KEY not set")
        sys.exit(1)

    client = ClaudeQueryClient(api_key=api_key, verbose=verbose)
    results = []

    for test_case in TEST_QUERIES:
        result = client.interpret_query(test_case.query)
        results.append((test_case, result))

    # Summary table
    console.print("\n")
    console.print("=" * 70, style="bold blue")
    console.print("  TEST SUMMARY", style="bold white")
    console.print("=" * 70, style="bold blue")

    table = Table(show_header=True, header_style="bold magenta")
    table.add_column("Query", style="cyan", max_width=35)
    table.add_column("Expected", style="yellow")
    table.add_column("Got", style="white")
    table.add_column("Match", style="green")

    passed = 0
    failed = 0

    for test_case, result in results:
        if result.success:
            type_match = result.interpretation.search_type == test_case.expected_type
            query_match = result.interpretation.query.lower() == test_case.expected_query.lower()

            if type_match and query_match:
                status = "[green]✓ PASS[/green]"
                passed += 1
            else:
                status = "[yellow]~ PARTIAL[/yellow]"
                failed += 1

            table.add_row(
                test_case.query[:35],
                f"{test_case.expected_type}: {test_case.expected_query}",
                f"{result.interpretation.search_type}: {result.interpretation.query}",
                status,
            )
        else:
            status = f"[red]✗ FAIL: {result.error}[/red]"
            failed += 1
            table.add_row(
                test_case.query[:35],
                f"{test_case.expected_type}: {test_case.expected_query}",
                "-",
                status,
            )

    console.print(table)
    console.print(f"\n[bold]Results:[/bold] {passed} passed, {failed} failed")


def main():
    import argparse

    parser = argparse.ArgumentParser(description="AI Query Test Runner")
    parser.add_argument("--query", "-q", help="Single query to test")
    parser.add_argument("--run-fixtures", action="store_true", help="Run all fixture tests")
    parser.add_argument("--quiet", action="store_true", help="Minimal output (no verbose API logs)")

    args = parser.parse_args()

    if args.query:
        run_single_query(args.query, verbose=not args.quiet)
    elif args.run_fixtures:
        run_fixtures(verbose=not args.quiet)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
