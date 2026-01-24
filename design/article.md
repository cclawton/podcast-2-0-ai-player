# Building a Podcast App with AI: From Blueprint to Working Android App in Days

**How I used Claude, multi-agent swarms, and micro-issue tracking to build a privacy-first podcast player**

---

A week ago, I had an idea: build an open-source Podcast 2.0 player optimized for GrapheneOS with natural language voice control. Today, I have a working Android app with 50+ commits, 38 GitHub issues tracked, and features I couldn't have implemented alone in such a short time.

The secret? AI-assisted development taken to its logical extreme.

## The Design Phase: Perplexity as Architect

It started with a conversation in Perplexity. I described what I wanted: a privacy-first podcast app with MCP (Model Context Protocol) integration for voice commands while driving. Over several exchanges, Perplexity helped me architect the entire system—five architecture documents totaling 17,000+ words covering everything from database schemas to API specifications.

The output included complete SQLite schemas with proper indexing, 15 MCP tools for natural language control, a three-tier LLM strategy (offline pattern matching, local Ollama, optional Claude API), and a nine-phase implementation roadmap. This wasn't just documentation—it was a comprehensive blueprint ready for execution.

## The Build Phase: Claude-Flow Multi-Agent Swarms

With the architecture locked, I turned to [Claude-Flow](https://github.com/ruvnet/claude-flow) for implementation. Claude-Flow orchestrates multiple AI agents working in parallel—specialized coders, testers, and reviewers operating as a coordinated swarm.

The hierarchical topology kept agents focused. A coordinator agent managed task distribution while specialized workers tackled their domains: one agent implemented Room database entities, another built the [Podcast Index](https://podcastindex.org) API client, a third created ExoPlayer playback controls. Anti-drift configuration prevented the chaos that can happen when multiple agents work on the same codebase.

## Context Management: Beads for AI-Scale Issue Tracking

[Beads](https://github.com/steveyegge/beads), a micro-issue tracker, solved a critical problem: **context management for AI agents**. LLMs have finite context windows, and traditional issue trackers create bloated tickets that waste precious tokens. Beads takes the opposite approach—tiny, focused issues that AI agents can consume without context overflow.

Each Beads issue is a compact unit: a short title, minimal description, and labels. When an agent needs to fix "Episode dates displaying incorrectly (year 580012)," it gets exactly that context—nothing more. Beads also supports **automatic compacting**, condensing resolved issues into summaries so the active working set stays lean. This let me track 50+ granular tasks without overwhelming agent context windows.

## AI-Powered Search: The MCP Integration

The most ambitious feature is natural language search powered by my [PodcastIndex MCP Server](https://github.com/cclawton/podcastindex-mcp-server). Here's how it works:

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   User Query    │     │   Claude LLM    │     │   MCP Server    │     │  Podcast Index  │
│                 │────▶│                 │────▶│                 │────▶│      API        │
│ "Find podcasts  │     │  Interprets     │     │  Tool: search   │     │                 │
│  about AI"      │     │  intent         │     │  _podcasts()    │     │ podcastindex.org│
└─────────────────┘     └─────────────────┘     └─────────────────┘     └─────────────────┘
                                                        │
                                                        ▼
                              ┌──────────────────────────────────────────┐
                              │  Structured Results → Android App UI     │
                              └──────────────────────────────────────────┘
```

The [Podcast Index](https://podcastindex.org) provides an open, community-driven podcast directory—no Big Tech gatekeepers. My MCP server wraps their API with tools that Claude can invoke naturally.

## What's Working, What's Not

**Completed:** Podcast search and subscription, episode streaming, downloads for offline listening, playback speed controls (0.5x-3.0x), AI-powered natural language search, Claude API integration, encrypted credential storage.

**Outstanding issues still open:**
- **[GH#28](https://github.com/cclawton/podcast-2-0-ai-player/issues/28)**: Background playback stops after ~1 minute (critical)
- **[GH#38](https://github.com/cclawton/podcast-2-0-ai-player/issues/38)**: Single episode download from search results
- **[GH#22](https://github.com/cclawton/podcast-2-0-ai-player/issues/22)**: Android Auto support
- **[GH#8](https://github.com/cclawton/podcast-2-0-ai-player/issues/8)**: Full MCP server integration

## Try It Yourself

**Download APKs** from the [GitHub Releases](https://github.com/cclawton/podcast-2-0-ai-player/releases) page. The latest alpha (v0.1.0-alpha.34) includes all current features.

**Links:**
- [Source Code](https://github.com/cclawton/podcast-2-0-ai-player)
- [PodcastIndex MCP Server](https://github.com/cclawton/podcastindex-mcp-server)
- [Podcast Index API](https://podcastindex.org)
- [Claude-Flow](https://github.com/ruvnet/claude-flow)
- [Beads Issue Tracker](https://github.com/steveyegge/beads)

## Lessons Learned

AI-assisted development requires clear architecture upfront, structured task decomposition with context-aware tools like Beads, and proper orchestration via Claude-Flow. The human still makes architectural decisions and reviews output. But the execution speed is remarkable.

This project is experimental and incomplete—but as a proof of concept for AI-assisted development at scale, it's exceeded my expectations.

---

*Built with [Claude](https://anthropic.com), [Claude-Flow](https://github.com/ruvnet/claude-flow), [Beads](https://github.com/steveyegge/beads), and the [Podcast Index](https://podcastindex.org). No tracking. No analytics. Just podcasts.*
