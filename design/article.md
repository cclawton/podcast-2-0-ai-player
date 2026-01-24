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

The hierarchical topology kept agents focused. A coordinator agent managed task distribution while specialized workers tackled their domains: one agent implemented Room database entities, another built the Podcast Index API client, a third created ExoPlayer playback controls. Anti-drift configuration prevented the chaos that can happen when multiple agents work on the same codebase.

## Micro-Issue Management: Beads for Granular Tracking

[Beads](https://github.com/steveyegge/beads), a micro-issue tracker, became essential for managing the granular tasks AI agents excel at. Each small fix—"Episode dates displaying incorrectly (year 580012)"—got its own tracked issue. This created a clear audit trail and prevented agents from losing context on what needed fixing.

Looking at my Beads history, I see the journey: database schema implementation, API client with secure HMAC authentication, ExoPlayer integration, download manager for offline listening, Espresso test suites, and finally the AI-powered natural language search using Claude Haiku.

## The Results: 50+ Commits, Real Features

The app now has working podcast search and subscription, background playback, episode downloads for offline listening, playback speed controls, and—most ambitiously—AI-powered natural language search. When I type "Find me podcasts about quantum computing," Claude interprets this, constructs the right Podcast Index API query, and returns relevant results.

Along the way, AI agents fixed real bugs: WorkManager foreground service types for Android 14+, notification rate limiting, HTML stripping from episode descriptions, and proper image fallback handling. These aren't toy problems—they're the same issues any production app faces.

## Lessons Learned

AI-assisted development isn't magic. It requires clear architecture upfront (thank you, Perplexity), structured task decomposition (thank you, Beads), and proper orchestration (thank you, Claude-Flow). The human still makes architectural decisions and reviews output. But the execution speed is remarkable.

This project is experimental and incomplete—background playback still has issues, and not all planned features are implemented. But as a proof of concept for AI-assisted development at scale, it's exceeded my expectations.

The code is open source. The architecture documents are included. If you want to see what AI-built software looks like today, clone the repo and judge for yourself.

---

*Built with [Claude](https://anthropic.com), [Claude-Flow](https://github.com/ruvnet/claude-flow), and [Beads](https://github.com/steveyegge/beads). No tracking. No analytics. Just podcasts—and an experiment in the future of software development.*
