"""
Claude API Client with Full Logging

This module provides a Claude API client that logs all requests and responses
for debugging the AI query tool.
"""

import json
import os
import time
from dataclasses import dataclass, field
from typing import Optional

import httpx
from rich.console import Console
from rich.panel import Panel
from rich.syntax import Syntax
from rich.table import Table

from .query_interpreter import get_system_prompt, parse_claude_response, QueryInterpretation


# Constants matching Android AISearchService.kt
API_URL = "https://api.anthropic.com/v1/messages"
ANTHROPIC_VERSION = "2023-06-01"
MODEL = "claude-haiku-4-5-20251001"
MAX_TOKENS = 256


console = Console()


@dataclass
class RequestLog:
    """Stores details of a Claude API request."""
    timestamp: float
    url: str
    headers: dict
    body: dict

    def to_dict(self) -> dict:
        return {
            "timestamp": self.timestamp,
            "url": self.url,
            "headers": {k: v for k, v in self.headers.items() if k != "x-api-key"},
            "body": self.body,
        }


@dataclass
class ResponseLog:
    """Stores details of a Claude API response."""
    timestamp: float
    status_code: int
    headers: dict
    body: dict
    elapsed_ms: float

    def to_dict(self) -> dict:
        return {
            "timestamp": self.timestamp,
            "status_code": self.status_code,
            "headers": dict(self.headers),
            "body": self.body,
            "elapsed_ms": self.elapsed_ms,
        }


@dataclass
class QueryResult:
    """Result of a query interpretation request."""
    query: str
    interpretation: Optional[QueryInterpretation]
    request: RequestLog
    response: ResponseLog
    error: Optional[str] = None

    @property
    def success(self) -> bool:
        return self.interpretation is not None and self.error is None


class ClaudeQueryClient:
    """
    Claude API client with verbose logging for debugging AI queries.

    Usage:
        client = ClaudeQueryClient(api_key="sk-ant-...", verbose=True)
        result = client.interpret_query("podcasts about AI")

        # Access logged data
        print(client.get_last_request())
        print(client.get_last_response())
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        verbose: bool = True,
        model: str = MODEL,
        timeout: float = 30.0,
    ):
        """
        Initialize the Claude query client.

        Args:
            api_key: Anthropic API key. If not provided, reads from ANTHROPIC_API_KEY env var.
            verbose: If True, prints detailed logs to console.
            model: Claude model ID to use.
            timeout: Request timeout in seconds.
        """
        self.api_key = api_key or os.environ.get("ANTHROPIC_API_KEY")
        if not self.api_key:
            raise ValueError(
                "API key required. Provide api_key parameter or set ANTHROPIC_API_KEY env var."
            )

        self.verbose = verbose
        self.model = model
        self.timeout = timeout
        self.system_prompt = get_system_prompt()

        self._last_request: Optional[RequestLog] = None
        self._last_response: Optional[ResponseLog] = None
        self._history: list[QueryResult] = []

        self._client = httpx.Client(timeout=timeout)

    def interpret_query(self, query: str) -> QueryResult:
        """
        Send a query to Claude for interpretation.

        Args:
            query: Natural language podcast search query.

        Returns:
            QueryResult with interpretation and logged request/response data.
        """
        if self.verbose:
            self._print_header(f"Testing Query: {query}")

        # Build request
        headers = {
            "x-api-key": self.api_key,
            "anthropic-version": ANTHROPIC_VERSION,
            "content-type": "application/json",
        }

        body = {
            "model": self.model,
            "max_tokens": MAX_TOKENS,
            "system": self.system_prompt,
            "messages": [{"role": "user", "content": query}],
        }

        # Log request
        self._last_request = RequestLog(
            timestamp=time.time(),
            url=API_URL,
            headers=headers,
            body=body,
        )

        if self.verbose:
            self._print_system_prompt()
            self._print_request(query, body)

        # Send request
        start_time = time.time()
        error = None
        response_body = {}
        status_code = 0
        response_headers = {}

        try:
            response = self._client.post(API_URL, headers=headers, json=body)
            status_code = response.status_code
            response_headers = dict(response.headers)
            response_body = response.json()
        except httpx.TimeoutException:
            error = "Request timed out"
        except httpx.RequestError as e:
            error = f"Request failed: {e}"
        except json.JSONDecodeError:
            error = "Failed to parse response JSON"

        elapsed_ms = (time.time() - start_time) * 1000

        # Log response
        self._last_response = ResponseLog(
            timestamp=time.time(),
            status_code=status_code,
            headers=response_headers,
            body=response_body,
            elapsed_ms=elapsed_ms,
        )

        if self.verbose:
            self._print_response(response_body, status_code, elapsed_ms)

        # Parse interpretation
        interpretation = None
        if not error and status_code == 200:
            interpretation = parse_claude_response(json.dumps(response_body))
            if interpretation is None:
                error = "Failed to parse Claude response"
        elif not error:
            error = f"API error: {status_code} - {response_body.get('error', {}).get('message', 'Unknown')}"

        if self.verbose:
            self._print_interpretation(interpretation, error)

        # Create result
        result = QueryResult(
            query=query,
            interpretation=interpretation,
            request=self._last_request,
            response=self._last_response,
            error=error,
        )

        self._history.append(result)
        return result

    def get_last_request(self) -> Optional[dict]:
        """Get the last request as a dictionary."""
        return self._last_request.to_dict() if self._last_request else None

    def get_last_response(self) -> Optional[dict]:
        """Get the last response as a dictionary."""
        return self._last_response.to_dict() if self._last_response else None

    def get_history(self) -> list[QueryResult]:
        """Get all query results from this session."""
        return self._history.copy()

    def clear_history(self) -> None:
        """Clear the query history."""
        self._history.clear()

    def _print_header(self, title: str) -> None:
        """Print a section header."""
        console.print()
        console.print("=" * 70, style="bold blue")
        console.print(f"  {title}", style="bold white")
        console.print("=" * 70, style="bold blue")
        console.print()

    def _print_system_prompt(self) -> None:
        """Print the system prompt."""
        console.print("[bold cyan]SYSTEM PROMPT SENT TO CLAUDE:[/bold cyan]")
        console.print(Panel(
            self.system_prompt,
            title="System Prompt",
            border_style="cyan",
            padding=(1, 2),
        ))
        console.print()

    def _print_request(self, query: str, body: dict) -> None:
        """Print request details."""
        console.print(f"[bold yellow]USER QUERY:[/bold yellow] {query}")
        console.print()

        console.print("[bold yellow]REQUEST TO CLAUDE API:[/bold yellow]")
        # Redact system prompt for brevity in request display
        display_body = body.copy()
        display_body["system"] = "[SYSTEM PROMPT - see above]"
        syntax = Syntax(
            json.dumps(display_body, indent=2),
            "json",
            theme="monokai",
            line_numbers=False,
        )
        console.print(Panel(syntax, title=f"POST {API_URL}", border_style="yellow"))
        console.print()

    def _print_response(self, body: dict, status_code: int, elapsed_ms: float) -> None:
        """Print response details."""
        status_style = "green" if status_code == 200 else "red"
        console.print(
            f"[bold {status_style}]RESPONSE FROM CLAUDE:[/bold {status_style}] "
            f"Status {status_code} ({elapsed_ms:.0f}ms)"
        )

        syntax = Syntax(
            json.dumps(body, indent=2),
            "json",
            theme="monokai",
            line_numbers=False,
        )
        console.print(Panel(syntax, title="Response Body", border_style=status_style))
        console.print()

    def _print_interpretation(
        self, interpretation: Optional[QueryInterpretation], error: Optional[str]
    ) -> None:
        """Print parsed interpretation."""
        if error:
            console.print(f"[bold red]ERROR:[/bold red] {error}")
            return

        if interpretation:
            table = Table(title="Parsed Interpretation", show_header=True, header_style="bold magenta")
            table.add_column("Field", style="cyan")
            table.add_column("Value", style="white")

            table.add_row("search_type", interpretation.search_type)
            table.add_row("query", interpretation.query)
            table.add_row("explanation", interpretation.explanation)

            console.print(table)
            console.print()
            console.print("[bold green]SUCCESS[/bold green]")
        else:
            console.print("[bold red]FAILED TO PARSE INTERPRETATION[/bold red]")

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self._client.close()


def main():
    """CLI entry point for testing queries."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Test AI query interpretation with Claude",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python -m tests.ai_query.claude_client --query "podcasts about AI"
  python -m tests.ai_query.claude_client --query "joe rogan recent episodes" --quiet
  python -m tests.ai_query.claude_client --query "david deutsch" --model claude-sonnet-4-20250514
        """,
    )
    parser.add_argument("--query", "-q", required=True, help="Query to test")
    parser.add_argument("--quiet", action="store_true", help="Minimal output")
    parser.add_argument("--model", "-m", default=MODEL, help=f"Model to use (default: {MODEL})")
    parser.add_argument("--api-key", "-k", help="API key (or set ANTHROPIC_API_KEY)")

    args = parser.parse_args()

    try:
        with ClaudeQueryClient(
            api_key=args.api_key,
            verbose=not args.quiet,
            model=args.model,
        ) as client:
            result = client.interpret_query(args.query)

            if args.quiet:
                if result.success:
                    print(json.dumps({
                        "search_type": result.interpretation.search_type,
                        "query": result.interpretation.query,
                        "explanation": result.interpretation.explanation,
                    }))
                else:
                    print(json.dumps({"error": result.error}))
                    exit(1)
    except ValueError as e:
        console.print(f"[bold red]Error:[/bold red] {e}")
        exit(1)


if __name__ == "__main__":
    main()
