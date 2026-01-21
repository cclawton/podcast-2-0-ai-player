#!/usr/bin/env python3
"""
AI Query Test Runner - End-to-End Pipeline Testing

Test the full AI query pipeline:
1. Send query to Claude → Get interpretation
2. Send interpreted query to PodcastIndex API → Get podcast results

Usage:
    # Just Claude interpretation
    python run_ai_query_test.py --query "podcasts with david deutsch"

    # Full pipeline with PodcastIndex search
    python run_ai_query_test.py --query "podcasts with david deutsch" --with-search

    # Run all fixtures
    python run_ai_query_test.py --run-fixtures

    # Run all fixtures with PodcastIndex search
    python run_ai_query_test.py --run-fixtures --with-search --quiet
"""

import sys
import os
import json

# Add the mcp-server directory to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from tests.ai_query.claude_client import ClaudeQueryClient
from tests.ai_query.podcastindex_client import PodcastIndexClient
from tests.ai_query.fixtures import TEST_QUERIES
from rich.console import Console
from rich.table import Table
from rich.panel import Panel

console = Console()


def run_single_query(query: str, verbose: bool = True, with_search: bool = False):
    """Test a single query through the pipeline."""
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        console.print("[bold red]Error:[/bold red] ANTHROPIC_API_KEY not set")
        sys.exit(1)

    # Step 1: Claude interpretation
    console.print()
    console.print("=" * 70, style="bold blue")
    console.print("  STEP 1: CLAUDE INTERPRETATION", style="bold white")
    console.print("=" * 70, style="bold blue")

    with ClaudeQueryClient(api_key=api_key, verbose=verbose) as claude_client:
        claude_result = claude_client.interpret_query(query)

    if not claude_result.success:
        console.print(f"[bold red]Claude interpretation failed:[/bold red] {claude_result.error}")
        return None

    # Step 2: PodcastIndex search (if requested)
    if with_search and claude_result.interpretation:
        console.print()
        console.print("=" * 70, style="bold green")
        console.print("  STEP 2: PODCASTINDEX SEARCH", style="bold white")
        console.print("=" * 70, style="bold green")

        try:
            with PodcastIndexClient.from_gradle_properties(verbose=verbose) as pi_client:
                search_result = pi_client.search(
                    search_type=claude_result.interpretation.search_type,
                    query=claude_result.interpretation.query,
                )

                # Print summary
                _print_summary(query, claude_result, search_result)
                return (claude_result, search_result)

        except (ValueError, FileNotFoundError) as e:
            console.print(f"[bold red]PodcastIndex error:[/bold red] {e}")
            return (claude_result, None)
    else:
        # Just Claude interpretation
        _print_summary(query, claude_result, None)
        return (claude_result, None)


def _print_summary(query: str, claude_result, search_result):
    """Print pipeline summary."""
    console.print()
    console.print("=" * 70, style="bold magenta")
    console.print("  PIPELINE SUMMARY", style="bold white")
    console.print("=" * 70, style="bold magenta")
    console.print()

    # Claude interpretation
    if claude_result.success:
        interp = claude_result.interpretation
        console.print(f"[bold green]✓[/bold green] Claude interpreted query:")
        console.print(f"  [cyan]Input:[/cyan] \"{query}\"")
        console.print(f"  [cyan]Search Type:[/cyan] {interp.search_type}")
        console.print(f"  [cyan]Extracted Query:[/cyan] \"{interp.query}\"")
        console.print(f"  [cyan]Explanation:[/cyan] {interp.explanation}")
    else:
        console.print(f"[bold red]✗[/bold red] Claude interpretation failed: {claude_result.error}")

    # PodcastIndex search
    if search_result:
        console.print()
        if search_result.success:
            console.print(f"[bold green]✓[/bold green] PodcastIndex returned {search_result.total_count} podcasts")
            if search_result.podcasts:
                console.print(f"  [cyan]Top results:[/cyan]")
                for i, p in enumerate(search_result.podcasts[:3], 1):
                    console.print(f"    {i}. {p.title} by {p.author} ({p.episode_count} eps)")
        else:
            console.print(f"[bold red]✗[/bold red] PodcastIndex search failed: {search_result.error}")

    console.print()


def run_fixtures(verbose: bool = True, with_search: bool = False):
    """Run all fixture tests."""
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        console.print("[bold red]Error:[/bold red] ANTHROPIC_API_KEY not set")
        sys.exit(1)

    # Initialize clients
    claude_client = ClaudeQueryClient(api_key=api_key, verbose=verbose)

    pi_client = None
    if with_search:
        try:
            pi_client = PodcastIndexClient.from_gradle_properties(verbose=verbose)
        except (ValueError, FileNotFoundError) as e:
            console.print(f"[bold yellow]Warning:[/bold yellow] PodcastIndex unavailable: {e}")
            console.print("[dim]Continuing with Claude-only tests...[/dim]")
            with_search = False

    results = []

    for test_case in TEST_QUERIES:
        if verbose:
            console.print()
            console.print(f"[bold]Testing:[/bold] {test_case.query}")
            console.print("-" * 50)

        # Claude interpretation
        claude_result = claude_client.interpret_query(test_case.query)

        # PodcastIndex search
        search_result = None
        if with_search and pi_client and claude_result.success:
            search_result = pi_client.search(
                search_type=claude_result.interpretation.search_type,
                query=claude_result.interpretation.query,
            )

        results.append((test_case, claude_result, search_result))

    # Summary table
    console.print("\n")
    console.print("=" * 80, style="bold blue")
    console.print("  TEST SUMMARY", style="bold white")
    console.print("=" * 80, style="bold blue")

    table = Table(show_header=True, header_style="bold magenta")
    table.add_column("Query", style="cyan", max_width=30)
    table.add_column("Expected", style="yellow", max_width=20)
    table.add_column("Got", style="white", max_width=20)
    table.add_column("Claude", style="green", width=8)
    if with_search:
        table.add_column("PI Results", style="blue", width=10)

    passed = 0
    failed = 0

    for test_case, claude_result, search_result in results:
        if claude_result.success:
            interp = claude_result.interpretation
            type_match = interp.search_type == test_case.expected_type
            query_match = interp.query.lower() == test_case.expected_query.lower()

            if type_match and query_match:
                claude_status = "[green]✓ PASS[/green]"
                passed += 1
            else:
                claude_status = "[yellow]~ CLOSE[/yellow]"
                failed += 1

            expected = f"{test_case.expected_type}: {test_case.expected_query}"
            got = f"{interp.search_type}: {interp.query}"
        else:
            claude_status = f"[red]✗ FAIL[/red]"
            failed += 1
            expected = f"{test_case.expected_type}: {test_case.expected_query}"
            got = f"Error: {claude_result.error}"

        row = [
            test_case.query[:30],
            expected[:20],
            got[:20],
            claude_status,
        ]

        if with_search:
            if search_result and search_result.success:
                row.append(f"[green]{search_result.total_count}[/green]")
            elif search_result:
                row.append(f"[red]ERR[/red]")
            else:
                row.append("-")

        table.add_row(*row)

    console.print(table)
    console.print(f"\n[bold]Results:[/bold] {passed} passed, {failed} failed out of {len(results)}")

    # Cleanup
    if pi_client:
        pi_client._client.close()


def main():
    import argparse

    parser = argparse.ArgumentParser(
        description="AI Query Test Runner - End-to-End Pipeline",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Test single query (Claude only)
  python run_ai_query_test.py --query "podcasts with david deutsch"

  # Test single query with PodcastIndex search
  python run_ai_query_test.py --query "podcasts with david deutsch" --with-search

  # Run all fixtures (Claude only)
  python run_ai_query_test.py --run-fixtures

  # Run all fixtures with PodcastIndex search
  python run_ai_query_test.py --run-fixtures --with-search

  # Quiet mode (summary only)
  python run_ai_query_test.py --run-fixtures --with-search --quiet

Environment Variables:
  ANTHROPIC_API_KEY        - Required for Claude API
  PODCASTINDEX_API_KEY     - Optional (falls back to gradle.properties)
  PODCASTINDEX_API_SECRET  - Optional (falls back to gradle.properties)
        """,
    )
    parser.add_argument("--query", "-q", help="Single query to test")
    parser.add_argument("--run-fixtures", action="store_true", help="Run all fixture tests")
    parser.add_argument("--with-search", "-s", action="store_true",
                        help="Also run PodcastIndex search with Claude's interpretation")
    parser.add_argument("--quiet", action="store_true",
                        help="Minimal output (no verbose API logs)")

    args = parser.parse_args()

    if args.query:
        run_single_query(args.query, verbose=not args.quiet, with_search=args.with_search)
    elif args.run_fixtures:
        run_fixtures(verbose=not args.quiet, with_search=args.with_search)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
