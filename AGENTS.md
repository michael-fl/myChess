# AGENTS.md

This file provides guidance to Codex (and other coding agents) when working with
code in this repository.

**All project guidance lives in [`CLAUDE.md`](CLAUDE.md)** — build & run, the
REPL command surface, the architecture overview, and the project-specific
conventions. The guidance is the same for every agent, so read `CLAUDE.md` and
follow it in full.

Two tool-name substitutions apply when you read `CLAUDE.md` as a Codex agent:

- Wherever it addresses "Claude Code (claude.ai/code)", read "Codex".
- Wherever it references the global rules at `~/.claude/CLAUDE.md`, read your own
  global configuration (`~/.Codex/AGENTS.md`).

The project guidance is kept in the single file `CLAUDE.md` on purpose: two full
copies drift apart (this file's slow-test list had already gone stale), so there
is one source of truth instead.
