"""
PodcastIndex API Client with Full Logging

This module provides a PodcastIndex API client that logs all requests and responses
for debugging the AI query tool end-to-end.
"""

import hashlib
import json
import os
import re
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional, List

import httpx
from rich.console import Console
from rich.panel import Panel
from rich.syntax import Syntax
from rich.table import Table


# Constants matching Android PodcastIndexApi.kt
BASE_URL = "https://api.podcastindex.org/api/1.0"
USER_AGENT = "PodcastApp/1.0"
DEFAULT_MAX_RESULTS = 20


console = Console()


@dataclass
class PodcastResult:
    """A podcast from PodcastIndex search results."""
    id: int
    title: str
    author: str
    description: str
    episode_count: int
    image_url: Optional[str]
    feed_url: str
    language: Optional[str]

    @classmethod
    def from_api(cls, data: dict) -> "PodcastResult":
        return cls(
            id=data.get("id", 0),
            title=data.get("title", "Unknown"),
            author=data.get("author", "Unknown"),
            description=data.get("description", "")[:200] if data.get("description") else "",
            episode_count=data.get("episodeCount", 0),
            image_url=data.get("artwork") or data.get("image"),
            feed_url=data.get("url", ""),
            language=data.get("language"),
        )


@dataclass
class RequestLog:
    """Stores details of a PodcastIndex API request."""
    timestamp: float
    url: str
    headers: dict
    method: str = "GET"

    def to_dict(self) -> dict:
        # Redact sensitive headers
        safe_headers = {}
        for k, v in self.headers.items():
            if k.lower() in ("authorization", "x-auth-key"):
                safe_headers[k] = v[:8] + "...REDACTED" if len(v) > 8 else "REDACTED"
            else:
                safe_headers[k] = v
        return {
            "timestamp": self.timestamp,
            "url": self.url,
            "headers": safe_headers,
            "method": self.method,
        }


@dataclass
class ResponseLog:
    """Stores details of a PodcastIndex API response."""
    timestamp: float
    status_code: int
    headers: dict
    body: dict
    elapsed_ms: float

    def to_dict(self) -> dict:
        return {
            "timestamp": self.timestamp,
            "status_code": self.status_code,
            "body": self.body,
            "elapsed_ms": self.elapsed_ms,
        }


@dataclass
class SearchResult:
    """Result of a PodcastIndex search."""
    search_type: str
    query: str
    podcasts: List[PodcastResult]
    total_count: int
    request: RequestLog
    response: ResponseLog
    error: Optional[str] = None

    @property
    def success(self) -> bool:
        return self.error is None and len(self.podcasts) >= 0


class PodcastIndexClient:
    """
    PodcastIndex API client with verbose logging for debugging.

    Authentication uses SHA-1 hash of (apiKey + apiSecret + timestamp).

    Usage:
        client = PodcastIndexClient(api_key="...", api_secret="...")
        result = client.search("byperson", "David Deutsch")

        # Or auto-load from gradle.properties:
        client = PodcastIndexClient.from_gradle_properties()
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        api_secret: Optional[str] = None,
        verbose: bool = True,
        timeout: float = 30.0,
    ):
        """
        Initialize the PodcastIndex client.

        Args:
            api_key: PodcastIndex API key
            api_secret: PodcastIndex API secret
            verbose: If True, prints detailed logs to console
            timeout: Request timeout in seconds
        """
        self.api_key = api_key or os.environ.get("PODCASTINDEX_API_KEY")
        self.api_secret = api_secret or os.environ.get("PODCASTINDEX_API_SECRET")

        if not self.api_key or not self.api_secret:
            raise ValueError(
                "API credentials required. Provide api_key/api_secret or set "
                "PODCASTINDEX_API_KEY/PODCASTINDEX_API_SECRET env vars, "
                "or use PodcastIndexClient.from_gradle_properties()"
            )

        self.verbose = verbose
        self.timeout = timeout

        self._last_request: Optional[RequestLog] = None
        self._last_response: Optional[ResponseLog] = None
        self._history: List[SearchResult] = []

        self._client = httpx.Client(timeout=timeout)

    @classmethod
    def from_gradle_properties(
        cls,
        gradle_path: Optional[str] = None,
        verbose: bool = True,
    ) -> "PodcastIndexClient":
        """
        Create client by reading credentials from gradle.properties.

        Args:
            gradle_path: Path to gradle.properties. Auto-detected if not provided.
            verbose: If True, prints detailed logs to console.

        Returns:
            PodcastIndexClient instance
        """
        if gradle_path is None:
            # Try to find gradle.properties relative to this file
            current_dir = Path(__file__).parent
            possible_paths = [
                current_dir / "../../../../android/gradle.properties",
                Path("/workspaces/podcast-2-0-ai-player/android/gradle.properties"),
            ]
            for path in possible_paths:
                if path.exists():
                    gradle_path = str(path.resolve())
                    break

        if not gradle_path or not Path(gradle_path).exists():
            raise FileNotFoundError(
                f"gradle.properties not found. Tried: {possible_paths}"
            )

        if verbose:
            console.print(f"[dim]Loading credentials from: {gradle_path}[/dim]")

        api_key = None
        api_secret = None

        with open(gradle_path, "r") as f:
            for line in f:
                line = line.strip()
                if line.startswith("PODCAST_INDEX_API_KEY="):
                    api_key = line.split("=", 1)[1]
                elif line.startswith("PODCAST_INDEX_API_SECRET="):
                    api_secret = line.split("=", 1)[1]

        if not api_key or not api_secret:
            raise ValueError(
                "PODCAST_INDEX_API_KEY or PODCAST_INDEX_API_SECRET not found in gradle.properties"
            )

        return cls(api_key=api_key, api_secret=api_secret, verbose=verbose)

    def _generate_auth_hash(self, timestamp: str) -> str:
        """
        Generate SHA-1 auth hash matching Android PodcastIndexAuthInterceptor.

        Hash = SHA1(apiKey + apiSecret + timestamp)
        """
        input_string = f"{self.api_key}{self.api_secret}{timestamp}"
        hash_bytes = hashlib.sha1(input_string.encode("utf-8")).digest()
        return hash_bytes.hex()

    def _build_headers(self) -> dict:
        """Build authenticated headers for API request."""
        timestamp = str(int(time.time()))
        auth_hash = self._generate_auth_hash(timestamp)

        return {
            "X-Auth-Key": self.api_key,
            "X-Auth-Date": timestamp,
            "Authorization": auth_hash,
            "User-Agent": USER_AGENT,
        }

    def search(
        self,
        search_type: str,
        query: str,
        max_results: int = DEFAULT_MAX_RESULTS,
    ) -> SearchResult:
        """
        Search PodcastIndex API.

        Args:
            search_type: One of "byperson", "bytitle", "byterm"
            query: Search query string
            max_results: Maximum results to return

        Returns:
            SearchResult with podcasts and logged request/response
        """
        if self.verbose:
            self._print_header(f"PodcastIndex Search: {search_type}")

        # Build endpoint URL
        endpoint_map = {
            "byperson": "search/byperson",
            "bytitle": "search/bytitle",
            "byterm": "search/byterm",
        }
        endpoint = endpoint_map.get(search_type, "search/byterm")
        url = f"{BASE_URL}/{endpoint}"

        # Build headers
        headers = self._build_headers()

        # Build params
        params = {"q": query, "max": max_results}
        if search_type == "byterm":
            params["clean"] = "true"

        # Log request
        full_url = f"{url}?{'&'.join(f'{k}={v}' for k, v in params.items())}"
        self._last_request = RequestLog(
            timestamp=time.time(),
            url=full_url,
            headers=headers,
        )

        if self.verbose:
            self._print_request(search_type, query, full_url, headers)

        # Send request
        start_time = time.time()
        error = None
        response_body = {}
        status_code = 0
        response_headers = {}
        podcasts = []
        total_count = 0

        try:
            response = self._client.get(url, headers=headers, params=params)
            status_code = response.status_code
            response_headers = dict(response.headers)
            response_body = response.json()

            if status_code == 200:
                feeds = response_body.get("feeds", [])
                total_count = response_body.get("count", len(feeds))
                podcasts = [PodcastResult.from_api(feed) for feed in feeds]
            else:
                error = f"API error: {status_code} - {response_body.get('description', 'Unknown error')}"

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
            self._print_response(response_body, status_code, elapsed_ms, error)
            if podcasts:
                self._print_results(podcasts)

        # Create result
        result = SearchResult(
            search_type=search_type,
            query=query,
            podcasts=podcasts,
            total_count=total_count,
            request=self._last_request,
            response=self._last_response,
            error=error,
        )

        self._history.append(result)
        return result

    def get_last_request(self) -> Optional[dict]:
        """Get the last request as a dictionary (with redacted secrets)."""
        return self._last_request.to_dict() if self._last_request else None

    def get_last_response(self) -> Optional[dict]:
        """Get the last response as a dictionary."""
        return self._last_response.to_dict() if self._last_response else None

    def _print_header(self, title: str) -> None:
        """Print a section header."""
        console.print()
        console.print("=" * 70, style="bold green")
        console.print(f"  {title}", style="bold white")
        console.print("=" * 70, style="bold green")
        console.print()

    def _print_request(self, search_type: str, query: str, url: str, headers: dict) -> None:
        """Print request details."""
        console.print(f"[bold yellow]SEARCH TYPE:[/bold yellow] {search_type}")
        console.print(f"[bold yellow]QUERY:[/bold yellow] {query}")
        console.print()

        console.print("[bold yellow]REQUEST TO PODCASTINDEX:[/bold yellow]")

        # Show URL
        console.print(f"  [cyan]URL:[/cyan] {url}")

        # Show headers (redacted)
        console.print(f"  [cyan]Headers:[/cyan]")
        for k, v in headers.items():
            if k.lower() in ("authorization", "x-auth-key"):
                display_v = v[:8] + "...REDACTED" if len(v) > 8 else "REDACTED"
            else:
                display_v = v
            console.print(f"    {k}: {display_v}")
        console.print()

    def _print_response(
        self, body: dict, status_code: int, elapsed_ms: float, error: Optional[str]
    ) -> None:
        """Print response details."""
        status_style = "green" if status_code == 200 else "red"
        console.print(
            f"[bold {status_style}]RESPONSE:[/bold {status_style}] "
            f"Status {status_code} ({elapsed_ms:.0f}ms)"
        )

        if error:
            console.print(f"[bold red]ERROR:[/bold red] {error}")
            return

        # Show abbreviated response
        display_body = {
            "status": body.get("status"),
            "count": body.get("count"),
            "description": body.get("description"),
            "feeds": f"[{len(body.get('feeds', []))} podcasts]",
        }

        syntax = Syntax(
            json.dumps(display_body, indent=2),
            "json",
            theme="monokai",
            line_numbers=False,
        )
        console.print(Panel(syntax, title="Response Summary", border_style=status_style))
        console.print()

    def _print_results(self, podcasts: List[PodcastResult]) -> None:
        """Print podcast results in a table."""
        table = Table(
            title=f"Search Results ({len(podcasts)} podcasts)",
            show_header=True,
            header_style="bold magenta",
        )
        table.add_column("#", style="dim", width=3)
        table.add_column("Title", style="cyan", max_width=35)
        table.add_column("Author", style="yellow", max_width=20)
        table.add_column("Episodes", style="green", justify="right", width=8)
        table.add_column("Language", style="dim", width=5)

        for i, podcast in enumerate(podcasts[:10], 1):  # Show top 10
            table.add_row(
                str(i),
                podcast.title[:35] if len(podcast.title) > 35 else podcast.title,
                podcast.author[:20] if len(podcast.author) > 20 else podcast.author,
                str(podcast.episode_count),
                podcast.language or "-",
            )

        console.print(table)
        console.print()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self._client.close()


def main():
    """CLI entry point for testing PodcastIndex API."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Test PodcastIndex API searches",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python -m tests.ai_query.podcastindex_client --type byperson --query "David Deutsch"
  python -m tests.ai_query.podcastindex_client --type bytitle --query "Joe Rogan"
  python -m tests.ai_query.podcastindex_client --type byterm --query "quantum computing"
        """,
    )
    parser.add_argument("--type", "-t", required=True, choices=["byperson", "bytitle", "byterm"])
    parser.add_argument("--query", "-q", required=True, help="Search query")
    parser.add_argument("--max", "-m", type=int, default=20, help="Max results")
    parser.add_argument("--quiet", action="store_true", help="Minimal output")

    args = parser.parse_args()

    try:
        with PodcastIndexClient.from_gradle_properties(verbose=not args.quiet) as client:
            result = client.search(args.type, args.query, args.max)

            if args.quiet:
                if result.success:
                    print(json.dumps({
                        "search_type": result.search_type,
                        "query": result.query,
                        "count": result.total_count,
                        "podcasts": [
                            {"id": p.id, "title": p.title, "author": p.author}
                            for p in result.podcasts[:5]
                        ],
                    }, indent=2))
                else:
                    print(json.dumps({"error": result.error}))
                    exit(1)
    except (ValueError, FileNotFoundError) as e:
        console.print(f"[bold red]Error:[/bold red] {e}")
        exit(1)


if __name__ == "__main__":
    main()
