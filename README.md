# Podcast 2.0 AI Player

> **⚠️ EXPERIMENTAL PROJECT - USE AT YOUR OWN RISK**
>
> This project is a **thought exercise and experiment** in using AI-powered development with [Claude](https://anthropic.com), [Claude-Flow](https://github.com/ruvnet/claude-flow) multi-agent swarms, and [Beads](https://github.com/steveyegge/beads) for micro-issue management to build a complete Android application.
>
> **This app is built almost entirely by AI.** Not all features listed below are fully implemented. This is an active experiment in AI-assisted software development, not a production-ready application.
>
> **Install and use at your own risk.**

---

A privacy-first, GrapheneOS-optimized podcast player with AI-powered natural language control via Model Context Protocol (MCP).

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Podcast 2.0](https://img.shields.io/badge/Podcast-2.0-orange.svg)](https://podcastindex.org)
[![AI Built](https://img.shields.io/badge/Built%20by-Claude%20AI-blueviolet.svg)](https://anthropic.com)
[![Experimental](https://img.shields.io/badge/Status-Experimental-red.svg)]()

## Features

<video src="screenshots/demo.mp4" autoplay loop muted playsinline width="100%"></video>

### Core Podcast Features
- **Podcast Index Integration** - Search, discover, and subscribe to podcasts
- **Offline-First Design** - Download episodes for offline listening
- **Background Playback** - Continue listening while using other apps
- **Playback Controls** - Variable speed (0.5x-3.0x), skip forward/backward
- **Queue Management** - Build and manage your listening queue

### Podcast 2.0 Support
- **Chapters** - Navigate episodes by chapter
- **Transcripts** - Read along with episode transcripts
- **Value-for-Value** - Support creators directly (Lightning Network)
- **Person Tags** - Discover episodes by guest/host

### AI-Powered Features
- **Natural Language Search** - "Find podcasts about quantum computing"
- **Voice Control** - Hands-free control while driving
- **Smart Recommendations** - AI-powered episode suggestions
- **Claude API Integration** - Optional cloud AI for advanced queries

### Privacy & Security
- **No Tracking** - Zero analytics, telemetry, or hidden data collection
- **Offline-First** - Works fully without network access
- **Encrypted Storage** - API keys stored with AES-256-GCM encryption
- **GrapheneOS Optimized** - Minimal permissions, maximum privacy
- **Open Source** - Full transparency, audit the code yourself

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    PODCAST 2.0 APP (Android)                │
│                    [GrapheneOS Optimized]                   │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
      ┌───────▼────────┐     │     ┌─────────▼────────┐
      │   UI Layer     │     │     │  MCP Handler     │
      │  (Jetpack      │     │     │  (Voice/Text)    │
      │   Compose)     │     │     └──────────────────┘
      └────────────────┘     │
                             │
      ┌──────────────────────▼──────────────────────┐
      │        Core App Logic (MVVM + Repository)   │
      │  • PodcastRepository  • PlaybackController  │
      │  • EpisodeService     • DownloadManager     │
      └─────────────────────────────────────────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
    ┌────▼─────┐    ┌───────▼────────┐    ┌─────▼──────┐
    │  Room    │    │ Podcast Index  │    │ LLM Layer  │
    │ Database │    │     API        │    │ (Optional) │
    └──────────┘    └────────────────┘    └────────────┘
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 1.9+ |
| UI Framework | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository Pattern |
| DI | Hilt |
| Database | Room (SQLite) |
| Networking | Retrofit + OkHttp |
| Media | Media3 ExoPlayer |
| Async | Kotlin Coroutines + Flow |
| Serialization | Kotlinx Serialization |
| MCP Server | Python FastMCP |

## Requirements

- **Android**: API 28+ (Android 9.0 Pie)
- **Target**: API 35 (Android 15)
- **Recommended**: GrapheneOS for maximum privacy

## Getting Started

### Prerequisites

1. Android Studio Hedgehog or newer
2. JDK 17+
3. Podcast Index API credentials (free at [podcastindex.org](https://podcastindex.org))

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/cclawton/podcast-2-0-ai-player.git
   cd podcast-2-0-ai-player
   ```

2. **Configure API credentials**

   Create `local.properties` in the project root:
   ```properties
   PODCAST_INDEX_API_KEY=your_api_key_here
   PODCAST_INDEX_API_SECRET=your_api_secret_here
   ```

   Or set environment variables:
   ```bash
   export PODCAST_INDEX_API_KEY=your_api_key_here
   export PODCAST_INDEX_API_SECRET=your_api_secret_here
   ```

3. **Build the app**
   ```bash
   cd android
   ./gradlew assembleDebug
   ```

4. **Install on device**
   ```bash
   ./gradlew installDebug
   ```

### Optional: Claude API Integration

For AI-powered features, add your Claude API key in the app's Settings screen. The key is encrypted using Android Keystore (AES-256-GCM).

## MCP Server

The app includes an MCP (Model Context Protocol) server for AI integration:

```bash
cd mcp-server
pip install -r requirements.txt
python server.py
```

### Available MCP Tools

| Tool | Description |
|------|-------------|
| `play_episode` | Start playing a specific episode |
| `pause` / `resume` | Control playback |
| `skip_forward` / `skip_backward` | Skip by seconds |
| `set_playback_speed` | Adjust speed (0.5x-3.0x) |
| `search_podcasts` | Search Podcast Index |
| `get_subscribed_podcasts` | List subscriptions |
| `add_to_library` / `remove_from_library` | Manage library |
| `get_transcript` | Get episode transcript |
| `get_chapters` | Get episode chapters |

## Project Structure

```
podcast-2-0-ai-player/
├── android/                    # Android app
│   └── app/
│       └── src/
│           ├── main/
│           │   └── java/com/podcast/app/
│           │       ├── api/            # API clients
│           │       ├── data/           # Room entities, DAOs
│           │       ├── di/             # Hilt modules
│           │       ├── mcp/            # MCP bridge
│           │       ├── playback/       # ExoPlayer integration
│           │       ├── privacy/        # Privacy management
│           │       └── ui/             # Compose screens
│           ├── test/                   # Unit tests
│           └── androidTest/            # Espresso tests
├── mcp-server/                 # Python MCP server
│   ├── server.py
│   └── requirements.txt
├── design/                     # Architecture docs
└── .beads/                     # Issue tracking
```

## Privacy Commitment

This app is built with privacy as a core principle:

- **No Analytics**: We don't track your listening habits
- **No Telemetry**: No data sent to third parties
- **Offline-First**: Full functionality without network
- **Local Storage**: Your data stays on your device
- **Optional Network**: Network permission can be revoked
- **Open Source**: Verify our claims yourself

## LLM Strategy (Three-Tier)

| Tier | Method | Network | Description |
|------|--------|---------|-------------|
| 1 | Pattern Matching | None | Regex-based command parsing (default) |
| 2 | Termux + Ollama | Local | On-device LLM via localhost |
| 3 | Claude API | Internet | Cloud AI (explicit opt-in) |

The app works fully with Tier 1 only - no LLM required for basic operation.

## Contributing

Contributions are welcome! Please read our contributing guidelines and submit PRs.

### Development Workflow

1. Check open issues: `bd list --ready`
2. Create a branch: `git checkout -b feature/your-feature`
3. Make changes and test
4. Submit a PR

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## AI Development Experiment

This project serves as an experiment in AI-assisted software development:

### Tools Used
- **[Claude](https://anthropic.com)** - Anthropic's AI assistant (Opus 4.5) for code generation
- **[Claude-Flow](https://github.com/ruvnet/claude-flow)** - Multi-agent swarm orchestration for parallel development
- **[Beads](https://github.com/steveyegge/beads)** - Micro-issue tracking for granular task management
- **Claude Code** - CLI tool for AI-powered development

### Development Approach
The entire codebase is generated and maintained by AI agents working in coordinated swarms:
- **Hierarchical topology** prevents agent drift
- **Specialized agents** (coder, tester, reviewer) work in parallel
- **Memory persistence** enables learning across sessions
- **Beads micro-issues** track granular implementation tasks

### What This Experiment Explores
1. Can AI agents build a complete, functional Android app?
2. How effective are multi-agent swarms for software development?
3. What are the limits of AI-generated code quality and security?
4. Can AI maintain architectural consistency across a large codebase?

### Current Status
This is a work in progress. Features are being implemented incrementally by AI agents. Check the [GitHub Issues](https://github.com/cclawton/podcast-2-0-ai-player/issues) for current development status.

## Acknowledgments

- [Podcast Index](https://podcastindex.org) - Open podcast directory
- [Anthropic](https://anthropic.com) - Claude AI
- [Claude-Flow](https://github.com/ruvnet/claude-flow) - Multi-agent orchestration
- [Beads](https://github.com/steveyegge/beads) - Issue tracking
- [ExoPlayer/Media3](https://developer.android.com/media/media3) - Media playback
- [GrapheneOS](https://grapheneos.org) - Privacy-focused Android

## Support

- Issues: [GitHub Issues](https://github.com/cclawton/podcast-2-0-ai-player/issues)
- Discussions: [GitHub Discussions](https://github.com/cclawton/podcast-2-0-ai-player/discussions)

---

**Built with privacy in mind. No tracking. No analytics. Just podcasts.**
