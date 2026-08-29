# myChess

Yet another chess engine — just for fun.

A small, self-contained chess engine written from scratch in Java 25. It speaks
both an interactive REPL and the **UCI protocol** (so it plugs into Cute Chess,
Arena, or a lichess bot bridge), supports **standard chess and Chess960**, and
plays with a [tapered evaluation](docs/tapered-evaluation.md) built on
[PeSTO's piece-square tables](#credits-and-third-party-material)
behind an iterative-deepening alpha-beta search with a
[transposition table](docs/search.md#7-search-optimizations), null-move pruning, and
[quiescence search](docs/search.md#64-quiescence-search).

---

## Quick start

**Prerequisites:** JDK 25 (set `JAVA_HOME` if your system default is older), Maven 3.9 or newer.

```bash
mvn compile                             # compile
mvn exec:java                           # start the interactive REPL
mvn exec:java -Dexec.arguments="uci"    # start in UCI mode instead
mvn test                                # full test suite (~1,170 tests, ~6 min)
mvn test -DexcludedGroups=slow          # fast tests only (~20 s)
mvn package                             # build the jar + copy runtime deps into target/dependency
```

`mvn exec:java` launches the engine's **REPL** (*read-eval-print loop*) — an interactive text prompt on stdin/stdout. It prints the starting board and a `>` prompt, waits for a move or command, executes it, prints the resulting board, and loops. Type moves in long algebraic (`e2-e4`) or standard short algebraic (`Nf3`, `O-O`, `exd5`) — or commands. Sample session:

```
8|♜ ♞ ♝ ♛ ♚ ♝ ♞ ♜
7|♟ ♟ ♟ ♟ ♟ ♟ ♟ ♟
6|. . . . . . . .
5|. . . . . . . .
4|. . . . . . . .
3|. . . . . . . .
2|♙ ♙ ♙ ♙ ♙ ♙ ♙ ♙
1|♖ ♘ ♗ ♕ ♔ ♗ ♘ ♖
  ---------------
  a b c d e f g h
Moves: 0, halfMoveClock: 0, castling: KQkq
Turn: white
>e2-e4               # user plays a move
>go                  # engine plays one move for the side to move
>tip                 # engine suggests a move but does not apply it
>auto                # engine plays both sides until the game ends
>fen                 # print the current position as FEN
>revert              # undo the last move
>new                 # reset to the starting position
>quit                # exit
```

A few useful pointers for first-time use:

- The engine's per-move time budget defaults to 30 s ([`EngineConfig`](src/main/java/org/michaelfl/mychess/EngineConfig.java)). The search is time-bounded iterative deepening with no fixed depth cap in normal play, so `go` and `auto` will spend that long on each move — be patient.
- `import [[ <moves> ]]` and `import <pgn>` reload a game from notation; `export` prints the current game in the same long-algebraic form that `import [[...]]` accepts; `pgn` prints it as PGN move text; `fen` prints (and, from the REPL, can re-load) a FEN.
- The opening book at `db/openings.db` ships **empty**; the engine works without it. See [Opening Database](docs/opening-database.md#93-import-pipeline) to populate it from PGN files.
- The full REPL reference is in [§ 10.4 REPL commands](docs/notation.md#104-repl-commands).

### Running as a UCI engine

Passing `uci` as the first CLI argument switches [`MyChessMain`](src/main/java/org/michaelfl/mychess/MyChessMain.java) from the REPL to the [`UciHandler`](src/main/java/org/michaelfl/mychess/UciHandler.java), which speaks a practical subset of UCI (`uci`, `isready`, `ucinewgame`, `setoption`, `position`, `go`, `stop`, `quit`) plus the `UCI_Chess960` option for Fischer-random play. During development the quickest path is `mvn exec:java -Dexec.arguments="uci"` (shown in the Quick start above).

For use in a GUI or a bot bridge, run the packaged jar standalone — `mvn package` puts the runtime dependencies under `target/dependency/` (adjust the version in the jar name to match `pom.xml`):

```bash
java -cp "target/my-chess-4.4.1.jar:target/dependency/*" \
     org.michaelfl.mychess.MyChessMain uci
```

This is how the engine is driven by [cutechess-cli for strength testing](docs/elo-testing.md) and by the [lichess bot bridge](docs/myChess-on-lichess.md).

---

## Table of Contents

In this file:

1. [Introduction](#1-introduction)
   1. [What is myChess?](#11-what-is-mychess)
   2. [Scope and status](#12-scope-and-status)
   3. [Repository layout](#13-repository-layout)
2. [Architecture Overview](#2-architecture-overview)
   1. [High-level structure](#21-high-level-structure)
   2. [Package boundaries and dependencies](#22-package-boundaries-and-dependencies)
   3. [Game, engine, and search separation](#23-game-engine-and-search-separation)
   4. [Concurrency and async move calculation](#24-concurrency-and-async-move-calculation)
   5. [Entry points: REPL and UCI](#25-entry-points-repl-and-uci)

Also in this file, at the end: [Credits and third-party material](#credits-and-third-party-material).

In separate files under [`docs/`](docs/):

3. [Core Data Types](docs/data-types.md) — board, piece, move, status encoding; Zobrist hashing; move-list and notation types.
4. [Move Generation](docs/move-generation.md) — pseudo-legal generation per piece, castling (standard + Chess960), en passant, king-capture detection, ordering hook.
5. [Evaluation Function](docs/evaluation.md) — material, piece-square tables, mobility, threats, castling, opening, double pawns, checks, composition.
   - [Tapered evaluation](docs/tapered-evaluation.md) — phase-interpolated midgame/endgame tables, the PeSTO-derived piece-square tables (see [credits](#credits-and-third-party-material)), and the bishop-pair term.
   - [King safety](docs/king-safety.md) — build plan for the one large evaluation term still missing, with the three shelved attempts and why each measured negative.
6. [Search Algorithm](docs/search.md#6-search-algorithm) — iterative-deepening negamax alpha-beta, PV table, quiescence, time management, mate scoring.
7. [Search Optimizations](docs/search.md#7-search-optimizations) — transposition table, null-move pruning, SEE-ordered captures, best-known-move ordering, killers, material-only shortcut, make/undo, packed moves, opening book, full sorting policy.
8. [Game Lifecycle and Result Detection](docs/game-lifecycle.md)
9. [Opening Database](docs/opening-database.md)
10. [Notation and I/O](docs/notation.md)
11. [Testing](docs/testing.md) — suite structure and conventions, notable cases, and the route from a lost lichess game to a pinned regression test.
12. [Chess960 support](docs/Chess960-project.md) — start-position generation, Shredder-FEN, castling slots, UCI wiring.
13. [Roadmap: Improving Playing Strength](docs/roadmap.md) — concrete next steps with effort/Elo estimates ([backlog](docs/roadmap-backlog.md), [completed items](docs/roadmap-done.md)).
14. [Known issues](docs/known-issues.md) — open bugs and ongoing investigations.
15. [Measuring playing strength with cutechess](docs/elo-testing.md) — the cutechess-cli match setup, SPRT parameters, opening-book choices, and how to read the output. See also the [version history](docs/version-history.md) and the [absolute-Elo measurement notes](docs/myChess-ELO-measurement.md).
16. [Bench history](docs/bench-history.md) — the `bench` node signature per release (depth 8 for the whole series, depth 9 from 4.3.4 on), what each jump attributes to, and why the node count is an equivalence oracle rather than a strength metric.
17. [STS history](docs/sts-history.md) — the Strategic Test Suite score per theme and release, what the number does and does not say, and why a run is only worth doing when the `bench` signature moved.
18. [Running myChess on lichess](docs/myChess-on-lichess.md) — setting the engine up as a UCI bot behind the lichess-bot bridge.

---

## 1. Introduction

### 1.1 What is myChess?

myChess is a small, self-contained chess engine written from scratch in Java. It is a personal hobby project — the original tag line is *"yet another chess engine, just for fun"* — and the codebase reflects that: clarity over micro-optimization, no third-party engine framework, and no neural-network evaluation. The one piece of borrowed material in the engine itself is the set of piece-square tables, adopted from PeSTO in v4.4.0; the test tree additionally carries the Strategic Test Suite as measurement data. Both are credited under [Credits and third-party material](#credits-and-third-party-material). It has grown well past a toy, though: it now plays a competitive-hobby-level game, is playable in any UCI GUI, and supports Chess960.

The engine plays a full game of chess against itself or against a human opponent. It implements the complete rules of chess (including castling, en passant, pawn promotion, the fifty-move rule, and threefold repetition), generates moves with a [hand-written generator](docs/move-generation.md) over a [12×12 mailbox board](docs/data-types.md#31-board-representation-1212-mailbox), [evaluates positions](docs/evaluation.md) with a [tapered](docs/tapered-evaluation.md) material + positional + mobility weighted sum, and [searches](docs/search.md) via iterative-deepening alpha-beta with a [transposition table](docs/search.md#7-search-optimizations), null-move pruning, [quiescence search](docs/search.md#64-quiescence-search), [killer-move heuristics](docs/search.md#72-killer-moves), SEE-ordered captures, and a small opening book backed by [MapDB](https://mapdb.org/).

Two interfaces are provided: a line-oriented **REPL** on stdin/stdout (moves in algebraic notation or commands such as `go`, `revert`, `auto`, `fen`, `pgn`, `import`), and a **UCI** front-end so the engine can be driven by any standard chess GUI or by a lichess bot bridge. Both are launched from [`MyChessMain`](src/main/java/org/michaelfl/mychess/MyChessMain.java); the REPL is implemented in [`CommandHandler`](src/main/java/org/michaelfl/mychess/CommandHandler.java) and the UCI front-end in [`UciHandler`](src/main/java/org/michaelfl/mychess/UciHandler.java).

The project is also a study object for the supporting techniques typical of a classical chess engine: bordered mailbox board representation, [packed-int move encoding](docs/data-types.md#33-move-encoding-packed-int), [Zobrist hashing](docs/data-types.md#38-zobrist-hashing-and-positionencoding), a Zobrist-keyed [transposition table](docs/search.md#7-search-optimizations), [principal-variation move ordering](docs/search.md#71-best-known-move-pv-ordering), alpha-beta pruning with null-move pruning, [static exchange evaluation](src/main/java/org/michaelfl/mychess/StaticExchangeEvaluation.java) for capture ordering, and quiescence search. Each of those is documented in detail in the corresponding chapter under [`docs/`](docs/).

### 1.2 Scope and status

**What the engine does:**

- Full standard chess rules — all piece movements, [castling](docs/move-generation.md#43-castling-legality) (with full legality check including squares-under-attack), [en passant](docs/move-generation.md#44-en-passant), pawn promotion (queen, rook, bishop, knight). See [Move Generation](docs/move-generation.md).
- **Chess960 (Fischer random)** — all 960 start positions, Shredder-FEN import/export, and Chess960 castling. Enabled via the `UCI_Chess960` UCI option. See [Chess960 support](docs/Chess960-project.md).
- Endgame and draw detection — checkmate, stalemate, fifty-move rule, and threefold repetition (via Zobrist hash comparison against the status stack). An insufficient-material check is implemented on `Board` but [not wired into game-result detection](docs/game-lifecycle.md#85-insufficient-material). See [Game Lifecycle and Result Detection](docs/game-lifecycle.md).
- Iterative-deepening alpha-beta search in negamax form with a Zobrist-keyed [transposition table](docs/search.md#7-search-optimizations), [null-move pruning](docs/search.md#7-search-optimizations), a [principal-variation table](docs/search.md#63-principal-variation-table), [killer-move ordering](docs/search.md#72-killer-moves), [SEE](src/main/java/org/michaelfl/mychess/StaticExchangeEvaluation.java)-ordered captures, [quiescence search](docs/search.md#64-quiescence-search) (itself TT-backed) for capture sequences, and a [configurable per-move time budget](docs/search.md#65-time-management-and-cancellation). See [Search Algorithm](docs/search.md#6-search-algorithm) and [Search Optimizations](docs/search.md#7-search-optimizations).
- A [tapered evaluation](docs/tapered-evaluation.md) with separate midgame/endgame piece-square tables (Texel-tuned), tapered material, a bishop-pair bonus, mobility, and several positional terms. See [Evaluation Function](docs/evaluation.md).
- [Opening book](docs/search.md#77-opening-book-lookup) lookup from a MapDB-backed database keyed by Zobrist position string. The book ships empty; it is built offline by [`OpeningDBImporter`](src/main/java/org/michaelfl/mychess/openingdb/OpeningDBImporter.java) from a directory of PGN files. See [Opening Database](docs/opening-database.md).
- I/O in standard formats — [FEN](docs/notation.md#101-fen) import/export (including Shredder-FEN for Chess960) and [PGN](docs/notation.md#102-pgn) import (of recorded games for replay or opening-book ingestion) and export (the `pgn` command, via [`PGNConverter`](src/main/java/org/michaelfl/mychess/PGNConverter.java)). See [Notation and I/O](docs/notation.md).
- Two front-ends — the interactive **REPL** and a **UCI** protocol handler — plus self-play (`auto`), single-move calculation (`go`), tip suggestion (`tip`), a `bench` fixed-workload node count, and full move history with `revert` undo.

**What the engine deliberately does not do (yet):**

- **No parallel search** — the search runs on a single dedicated worker thread ([`ChessEngine`](src/main/java/org/michaelfl/mychess/engines/ChessEngine.java) submits to `Executors.newSingleThreadExecutor()`).
- **No neural-network evaluation** (NNUE or otherwise) — evaluation is a hand-written, Texel-tuned weighted sum.
- **No endgame tablebases**.
- **No pondering** — the engine does not think on the opponent's time.
- **No persistent learning** — only the static opening book is read; the engine does not write back game outcomes.

The git history shows that earlier branches contained two alternative search engines, "engine V1" and "engine V2", both removed in earlier commits. The codebase now contains exactly one engine, [`MyChessEngine`](src/main/java/org/michaelfl/mychess/engines/MyChessEngine.java), delegating to [`PositionSearch`](src/main/java/org/michaelfl/mychess/engines/PositionSearch.java).

**Playing strength:** **v4.4.1 measures 1928 ± 21 CCRL Blitz**, from a direct [absolute-Elo anchor bracket](docs/myChess-ELO-measurement.md#the-v441-re-anchor--measured-2026-08-17) run on 2026-08-17 — 2000 games against five externally rated engines (TSCP, Zeta Dva, Princhess, BBC, plus a free Kojiro cross-check) at TC 40/120, combined with Ordo. That places it around rank 700 of the 2918 engines on the CCRL Blitz list, i.e. the upper half. Two deviations from CCRL's own conditions are worth knowing: no endgame tablebases (CCRL uses 4-6 piece, which makes our anchors slightly weaker than their rating and the number err high) and TC 40/120 instead of CCRL's 2'+1". Between re-anchors, progress is tracked by self-play SPRT and fixed-N matches under [cutechess-cli](docs/elo-testing.md); the per-release deltas are in the [version history](docs/version-history.md), and the chain of those deltas had predicted ~1915 before this measurement said 1928 — they transfer to external opponents better than expected. Strong club-player level, far from competitive engines, which is by design.

**Build & runtime requirements:**

- Java 25 (the build targets `maven.compiler.release=25`).
- Maven 3.9 or newer.
- JUnit Jupiter 5.11 for tests.
- MapDB 3.0.8 as the only runtime dependency.

The test suite contains roughly 1,170 tests (a handful skipped) and serves as the executable specification for move generation, encoding, search correctness, evaluation, Chess960, and notation parsing. Long-running tests are tagged `@Tag("slow")` and can be skipped with `-DexcludedGroups=slow`.

### 1.3 Repository layout

```
myChess/
├── CLAUDE.md                 # Guidance for AI coding agents (architecture cheat sheet)
├── README.md                 # This document
├── pom.xml                   # Maven build (Java 25, JUnit 5, MapDB)
├── docs/                     # Detailed per-area documentation (see the ToC above)
├── db/                       # Created at runtime — MapDB opening book (git-ignored)
├── src/
│   ├── main/java/org/michaelfl/mychess/
│   │   ├── MyChessMain.java          # Entry point: dispatches to REPL or UCI
│   │   ├── CommandHandler.java       # REPL command dispatch
│   │   ├── UciHandler.java           # UCI protocol front-end
│   │   ├── UciMoveParser.java        # UCI long-algebraic move parsing
│   │   ├── Log.java                  # Output mode switch (REPL vs UCI)
│   │   ├── Bench.java                # Fixed-workload node-count benchmark
│   │   ├── Game.java                 # One game: board + two engines + status engine
│   │   ├── GameConfig.java           # Per-game engine selection and config
│   │   ├── GameStatus.java           # Immutable snapshot: turn, castling, hash, …
│   │   ├── Board.java                # 12×12 mailbox, make/undo, Zobrist updates
│   │   ├── CastlingSlot.java         # Castling rook/king slots (standard + Chess960)
│   │   ├── Chess960StartPositions.java  # Enumerates all 960 start setups
│   │   ├── Move.java                 # Packed-int move accessor + type constants
│   │   ├── MoveFlag.java             # Move-type flag constants
│   │   ├── Moves.java                # Result of move generation (int[] wrapper)
│   │   ├── MovesArray.java           # Mutable packed-int move array
│   │   ├── MoveDescription.java      # Symbolic (algebraic-notation) move
│   │   ├── MoveGenerator.java        # Pseudo-legal move generation per piece
│   │   ├── MoveSorter.java           # Strategy: how to order generated moves
│   │   ├── SortableMovesBucket.java  # Insertion-sort bucket with weight keys
│   │   ├── StaticExchangeEvaluation.java  # SEE for capture ordering / pruning
│   │   ├── KillerMoves.java          # Per-depth killer-move table (2 slots)
│   │   ├── TranspositionTable.java   # Zobrist-keyed transposition table
│   │   ├── WeightingFunction.java    # Static (tapered) evaluation
│   │   ├── PieceSquareTables.java    # Per-piece midgame/endgame positional tables
│   │   ├── QuiescenceSearch.java     # Capture-only extension past max depth
│   │   ├── PositionEncoding.java     # Compact position serialization
│   │   ├── RandomNumbers.java        # Precomputed Zobrist table
│   │   ├── BitOps.java               # Pack/unpack 4 bytes ↔ int
│   │   ├── IntArray.java             # Manually-grown int[] with O(1) push/pop
│   │   ├── ChessUtil.java            # Field ↔ (col,row), notation helpers
│   │   ├── Fen.java                  # FEN import/export (incl. Shredder-FEN)
│   │   ├── Pgn.java                  # PGN tokenizer/parser
│   │   ├── PGNImporter.java          # File-level PGN import
│   │   ├── PGNConverter.java         # Game → PGN move-text export
│   │   ├── SimpleNotationImporter.java  # Long-algebraic move parser
│   │   ├── GameImporter.java         # Replays a list of MoveDescriptions
│   │   ├── MyChessEnv.java           # Per-process environment (opening DB)
│   │   ├── EngineConfig.java         # Search depth, time, TT, rule toggles
│   │   ├── Statistics.java           # Search node counters (for logging)
│   │   ├── Assert.java               # Lazy-message assertion helper
│   │   ├── IllegalMoveException.java
│   │   ├── IllegalChessPositionException.java
│   │   ├── engines/
│   │   │   ├── ChessEngine.java          # Abstract engine: async API, executor
│   │   │   ├── MyChessEngine.java        # Concrete engine, delegates to PositionSearch
│   │   │   ├── PositionSearch.java       # Iterative-deepening negamax alpha-beta
│   │   │   ├── MoveSorterImpl.java       # Ordering: PV → TT → killers → SEE captures
│   │   │   ├── EngineTuning.java         # Search tuning knobs (time, NMP, skip-iter)
│   │   │   ├── IterationInfo.java        # Per-iteration bookkeeping
│   │   │   ├── IterationTimings.java     # Per-depth moving-average iteration times
│   │   │   ├── SearchNodeContext.java    # Per-node search inputs
│   │   │   ├── SearchNodeResult.java     # Per-node search outputs
│   │   │   ├── BookMissThrottle.java     # Rate-limits opening-book miss logging
│   │   │   └── NextMoveTask.java         # Async result handle, cancellation
│   │   └── openingdb/
│   │       ├── OpeningDB.java            # MapDB BTreeMap wrapper
│   │       ├── DBValue.java              # Binary record: (count, [move,total,win,loss]*)
│   │       └── OpeningDBImporter.java    # PGN → DB ingest pipeline
│   ├── main/resources/
│   │   ├── version.properties       # Filtered to the pom version at build time
│   │   ├── chess960_fens.csv        # Precomputed Chess960 start FENs
│   │   └── bench                    # Fixed position set for the `bench` command
│   └── test/java/org/michaelfl/mychess/  # ~1,170 tests across ~70 test classes
└── target/                   # Maven output (git-ignored)
```

**Detailed documentation by area:**

| Source area | Documentation |
|---|---|
| `Board`, `Move`, `GameStatus`, `Moves`, `IntArray`, `BitOps`, `RandomNumbers`, `PositionEncoding`, `MoveDescription` | [Core Data Types](docs/data-types.md) |
| `MoveGenerator`, `MoveSorter`, `SortableMovesBucket`, `KillerMoves`, `StaticExchangeEvaluation`, `CastlingSlot`, `Chess960StartPositions` | [Move Generation](docs/move-generation.md) + [Move sorting policy](docs/search.md#78-move-sorting-sortablemovesbucket) + [Chess960](docs/Chess960-project.md) |
| `WeightingFunction`, `PieceSquareTables` | [Evaluation Function](docs/evaluation.md) + [Tapered evaluation](docs/tapered-evaluation.md) |
| `engines/PositionSearch`, `engines/ChessEngine`, `engines/MyChessEngine`, `engines/MoveSorterImpl`, `engines/NextMoveTask`, `TranspositionTable`, `QuiescenceSearch` | [Search Algorithm](docs/search.md#6-search-algorithm) + [Search Optimizations](docs/search.md#7-search-optimizations) |
| `Game`, `GameConfig` (result detection, draw rules) | [Game Lifecycle and Result Detection](docs/game-lifecycle.md) |
| `openingdb/OpeningDB`, `openingdb/DBValue`, `openingdb/OpeningDBImporter` | [Opening Database](docs/opening-database.md) |
| `Fen`, `Pgn`, `PGNImporter`, `PGNConverter`, `SimpleNotationImporter`, `GameImporter`, `CommandHandler`, `UciHandler`, `UciMoveParser` | [Notation and I/O](docs/notation.md) |
| `src/test/…` | [Testing](docs/testing.md) |
| Playing-strength measurement | [Elo testing](docs/elo-testing.md), [version history](docs/version-history.md), [absolute-Elo notes](docs/myChess-ELO-measurement.md) |
| Planned strength improvements | [Roadmap](docs/roadmap.md), [backlog](docs/roadmap-backlog.md), [done](docs/roadmap-done.md) |

**Key conventions:**

- All production code lives under a single Java package, `org.michaelfl.mychess`, plus two sub-packages: `engines` for search and `openingdb` for the opening book. The dependency direction is strictly one-way (`engines` and `openingdb` depend on the root package, never the reverse).
- Tests sit in the matching test-source tree with one `*Test.java` per production class (plus data-driven suites for the Texel-tuning adapters).
- The MapDB opening book lives at `db/openings.db`. The directory is created on first run and git-ignored; the engine works without a populated book — it simply skips the book-lookup step.

## 2. Architecture Overview

### 2.1 High-level structure

myChess is organized as five layers, stacked from the entry point downward:

```
┌──────────────────────────────────────────────────────────────────┐
│  MyChessMain  ─ entry point; dispatches to REPL or UCI           │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│  CommandHandler (REPL)   /   UciHandler (UCI)                    │
│  ─ parse input lines, drive one Game                             │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│  Game             ─ one game; owns the Board and three engines   │
│  ├─ Board                  (rules-level state + status stack)    │
│  ├─ engineWhite : ChessEngine                                    │
│  ├─ engineBlack : ChessEngine                                    │
│  └─ statusEngine: MyChessEngine(depth=2)  ─ for result detection │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│  ChessEngine (abstract)                                          │
│  ├─ async API (nextMoveAsync → NextMoveTask)                     │
│  ├─ single-thread executor                                       │
│  └─ short-circuits: game-over / 50-move / 3-fold / opening book  │
│                                                                  │
│  MyChessEngine (concrete) ─ delegates to PositionSearch          │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│  PositionSearch   ─ iterative-deepening negamax alpha-beta       │
│  ├─ MoveGenerator        (pseudo-legal generation per piece)     │
│  ├─ MoveSorterImpl       (PV → TT → killers → SEE captures)      │
│  ├─ TranspositionTable   (Zobrist-keyed; shared via EngineConfig)│
│  ├─ KillerMoves          (per-depth, 2 slots)                    │
│  ├─ QuiescenceSearch     (capture extensions past max depth)     │
│  ├─ WeightingFunction    (tapered static evaluation)             │
│  └─ Statistics           (node counters for logging)             │
└──────────────────────────────────────────────────────────────────┘
```

The split has two non-obvious properties:

1. **`Game` owns the engines, not the other way around.** A `Game` instantiates one engine per color plus a third *status engine* used only to decide whether the position after a move is checkmate, stalemate, or ongoing. The user-facing engines never make a move on the board; they only return a candidate `MoveAndWeight`. The actual mutation of the `Board` happens in `Game.makeMove(...)`, after the engine has returned.

2. **The search has no knowledge of the REPL/UCI front-end or even of `Game`.** `PositionSearch` operates on a `Board` copy and a freshly built `MoveGenerator`. It receives a `NextMoveTask` for cancellation polling and a `ChessEngine` for the configuration (including the shared transposition table) and the `Random` instance, but it does not call back into `Game`. This is what lets the same search class be reused inside the depth-2 status engine without recursion or shared state.

Each box in the diagram has its own deep-dive chapter: [`Board` / `Move` / `GameStatus`](docs/data-types.md), [`MoveGenerator`](docs/move-generation.md), [`WeightingFunction`](docs/evaluation.md), [`PositionSearch` / `QuiescenceSearch` / `KillerMoves` / `TranspositionTable`](docs/search.md).

### 2.2 Package boundaries and dependencies

There are exactly three packages:

| Package | Responsibility | May depend on |
|---|---|---|
| `org.michaelfl.mychess` | Rules, board, moves, evaluation, notation, REPL, UCI | (root) |
| `org.michaelfl.mychess.engines` | Search algorithm and async engine API | root |
| `org.michaelfl.mychess.openingdb` | MapDB-backed opening book | root |

The dependency direction is strictly one-way: `engines` and `openingdb` import from the root package; the root package never imports from either sub-package, with the single exception that `Game` and `Board` refer to `engines.ChessEngine` and `engines.ChessEngine.MoveAndWeight` because the engine is fundamentally part of how a game progresses.

Inside the root package, the natural sub-layers are visible from the import graph rather than from a directory structure:

- **Pure data / encoding** — `BitOps`, `IntArray`, `ChessUtil`, `RandomNumbers`. No dependencies on other myChess classes.
- **Move and position primitives** — `Move`, `MoveFlag`, `Moves`, `MovesArray`, `GameStatus`, `MoveDescription`, `CastlingSlot`. Depend on the encoding layer.
- **Board** — `Board`, `PositionEncoding`, `Fen`, `Chess960StartPositions`. Depend on primitives.
- **Generation, sorting, evaluation** — `MoveGenerator`, `MoveSorter`, `SortableMovesBucket`, `StaticExchangeEvaluation`, `KillerMoves`, `TranspositionTable`, `WeightingFunction`, `PieceSquareTables`, `QuiescenceSearch`. Depend on board.
- **Notation and import** — `Pgn`, `PGNImporter`, `PGNConverter`, `SimpleNotationImporter`, `GameImporter`, `UciMoveParser`.
- **Orchestration** — `Game`, `GameConfig`, `EngineConfig`, `MyChessEnv`, `Statistics`, `Assert`, `Log`, `Bench`.
- **Front-ends** — `CommandHandler` (REPL), `UciHandler` (UCI), `MyChessMain`. Top of the chain; nothing depends on these.

### 2.3 Game, engine, and search separation

The three roles — *rules*, *engine*, *search* — are kept deliberately separate.

**`Game`** is the rules-level façade. It owns:

- a single `Board` (mutated in place via `makeMove` / `revertMove`);
- two user-facing `ChessEngine`s (`engineWhite`, `engineBlack`), one per color;
- one internal `statusEngine` — always a `MyChessEngine` with `EngineConfig.Builder().maxDepth(2).silent(true)`, used by `calculateGameResult()` to decide after every move whether the side to move still has a legal reply.

`Game` exposes a small, rules-oriented API: `makeMove(MoveDescription)`, `makeMove(MoveAndWeight)`, `revertMove()`, `getResult()`, `getTurn()`, `exportFEN()`, `exportMoves()`. After every successful `makeMove`, `Game` immediately runs `statusEngine.calculateNextMove(...)`. If the status engine reports no legal move at depth ≥ 1, the result transitions from `ONGOING` to `CHECKMATE` or `STALEMATE`.

`Game` is also responsible for **rollback safety**: if `calculateAndSetGameResult()` throws or if post-move verification fails, `revertMove()` is called and the original exception is rethrown. The board's status stack always reflects only successfully applied moves.

**`ChessEngine`** (abstract) is the asynchronous engine API. It does three things:

1. Owns the single-thread `ExecutorService` on which the search runs.
2. Short-circuits trivial cases *before* delegating to the search: game already over, 50-move rule armed, threefold repetition, or a usable opening-book candidate.
3. Holds engine-level state that persists across calls: the `EngineConfig` (including the shared transposition table) and the `Random` instance used for opening-book sampling.

The abstract method `calculateNextMoveSub(NextMoveTask)` is the extension point. The only concrete implementation, [`MyChessEngine`](src/main/java/org/michaelfl/mychess/engines/MyChessEngine.java), delegates straight to `PositionSearch.calculateNextMove(...)`.

**`PositionSearch`** is the pure search. It takes a `ChessEngine` (for config, the shared transposition table, and the shared `Random`), a `NextMoveTask` (for cancellation polling), and the `Game` (only to read the current board and turn). It constructs its own working `Board` copy via `game.getBoard().copy()`, its own `KillerMoves` table, and its own `MoveGenerator` bound to that table — none of those persist across move calculations (the transposition table does, so entries survive across moves within a game). Its single public entry point is `calculateNextMove(...)`, which runs iterative deepening until the time budget is consumed or the configured `maxDepth` is reached, then returns the best `MoveAndWeight` it found. Full details are in [Search Algorithm](docs/search.md#6-search-algorithm) and [Search Optimizations](docs/search.md#7-search-optimizations).

The reason the three roles are split this way: it lets the same search class be used both as the user-facing engine *and* as the depth-2 status engine inside `Game`, without recursion and without one search interfering with another's state.

**`GameConfig`** is the wiring point. By default it constructs a `Game` with `MyChessEngine` on both sides and a single shared `EngineConfig`, but its constructor accepts two engine classes and two configs so that asymmetric games (different depths, different time budgets, eventually different engines) can be set up programmatically.

### 2.4 Concurrency and async move calculation

myChess uses **one worker thread per engine**, plus the calling thread for everything else. There is no parallel search and no work-stealing.

The async API lives in `ChessEngine`:

```
ChessEngine
├─ ExecutorService executor = Executors.newSingleThreadExecutor()
├─ NextMoveTask nextMoveAsync(MyChessEnv env)
│     ├─ creates NextMoveTask
│     ├─ executor.submit(() -> calculateNextMove(task))
│     └─ stores the Future on the task, returns task
└─ shutdown() — calls executor.shutdownNow()
```

`NextMoveTask` is a small handle around three things:

- a `Future<MoveAndWeight>` set after submission;
- a `volatile boolean isCanceled` flag, polled cooperatively by the search;
- the `MyChessEnv` (the per-process environment, currently just the `OpeningDB` reference).

Callers retrieve the result with `task.getResult(timeout, TimeUnit)` which forwards to `Future.get(...)`. The REPL's `auto` command uses a one-hour timeout — large enough never to fire in practice, but small enough that an unbounded hang is impossible.

**Cancellation is cooperative on two layers:**

1. `NextMoveTask.cancel()` sets the volatile flag *and* calls `resultFuture.cancel(false)`. The interrupt itself does little inside `PositionSearch` (the search does no blocking I/O), so the flag is the load-bearing mechanism. UCI `stop` cancels through the same path.
2. `PositionSearch.alphaBetaSearchI(...)` checks `task.isCanceled()` once per node, after move generation. If true, it throws a `CancellationException`, which unwinds back to the executor.

**Time management is independent of cancellation.** `PositionSearch` records `timeout = System.currentTimeMillis() + millisPerMove` at construction time and checks it inside `isTimeout()` — but only every 10,000 visited nodes, to avoid hitting `currentTimeMillis()` on every leaf. When the deadline is exceeded mid-iteration, the in-flight depth's result is discarded and the best result from the previous completed iteration is returned. This is what makes iterative deepening safe under a hard time budget: there is always a complete previous-depth answer to fall back on. On top of that, a [skip-hopeless-iteration heuristic](docs/search.md#651-skip-hopeless-iteration-heuristic) tracks a per-depth moving average of past iteration times and avoids starting a deepening iteration in the first place when it likely won't complete. See [Time management and cancellation](docs/search.md#65-time-management-and-cancellation) for the polling details and trade-offs.

**The status engine is a synchronous user of the same machinery.** `Game.calculateGameResult()` calls `statusEngine.calculateNextMove(new NextMoveTask())` directly, not through `nextMoveAsync`. It executes on the calling thread, with the timeout of the status engine's own `EngineConfig`, but depth-capped at 2 so it returns within milliseconds in practice.

**Engine shutdown** is initiated by `Game.shutdown()`, which calls `shutdown()` on both user-facing engines. The REPL's quit/exit/q handler and the UCI `quit` handler both call `game.shutdown()`. MapDB is closed via the try-with-resources block in `MyChessMain`.

### 2.5 Entry points: REPL and UCI

The entry point [`MyChessMain.main(...)`](src/main/java/org/michaelfl/mychess/MyChessMain.java) chooses the front-end from the CLI arguments: with `uci` as the first argument it starts the UCI handler, otherwise the interactive REPL.

```java
static void main(String[] args) {
    if (args.length > 0 && "uci".equalsIgnoreCase(args[0])) {
        runUci();
        return;
    }
    runRepl();
}
```

Both front-ends open the opening book with try-with-resources so MapDB is guaranteed to flush and unlock the file even on crash; in UCI mode the book is optional and a missing `db/openings.db` is tolerated.

**REPL.** [`CommandHandler`](src/main/java/org/michaelfl/mychess/CommandHandler.java) implements a classical Chain-of-Responsibility dispatch:

- A package-private abstract inner class `Command` defines two methods: `canHandle(String)` and `handle(String) throws Exception`.
- Each REPL verb is one nested final subclass: `QuitCommand`, `NewGameCommand`, `AutoGameCommand`, `ImportCommand`, `PrintCommand`, `BoardCommand`, `ExportCommand`, `RevertCommand`, `TipCommand`, `LastCommand`, `DeepWeightCommand`, `WeightCommand`, `GoCommand`, `MovesCommand`, `FenCommand`, `HashCommand`, `OpeningDBCommand`, …
- A list of those instances is iterated on each input line; the first whose `canHandle(line)` returns true gets the line; if none matches, the line is parsed as an algebraic-notation move and applied via `Game.makeMove(MoveDescription)`.

The interactive flow for the most common commands:

| Input | Resulting flow |
|---|---|
| `e2-e4` (or any algebraic move) | `SimpleNotationImporter` → `MoveDescription` → `Game.makeMove` → `Board.makeMove` → `calculateAndSetGameResult` → print board |
| `go` / `g` | `Game.getEngine().nextMoveAsync(env)` → block on `getResult(1, HOURS)` → `Game.makeMove(MoveAndWeight)` → print board |
| `auto` | Repeat `go` for both sides until `game.getResult() != ONGOING`, with a hard 1000-move safety bound |
| `revert` / `r` | `Game.revertMove()` → pops one entry from the board's `GameStatus` stack, resets the result to `ONGOING` |
| `fen` / `export` | `Game.exportFEN()` / `Game.exportMoves()` printed verbatim |
| `pgn` | `PGNConverter.toPGN(game.exportMoves())` — current game as PGN move text |
| `import <pgn>` / `imp <pgn>` | `PGNImporter` → list of `MoveDescription` → replay on a fresh `Game` |
| `tip` | Like `go` but does not apply the move; prints the suggestion and the principal variation |
| `o…` | Look up the current Zobrist position string in the opening DB and print all known moves with their win/draw/loss statistics |

Adding a new REPL command is a two-step change: define a new nested `Command` subclass inside `CommandHandler` and append it to the command list assembled in the constructor.

**UCI.** [`UciHandler`](src/main/java/org/michaelfl/mychess/UciHandler.java) reads UCI commands line by line and drives the same `Game`/`ChessEngine` stack. It implements the handshake (`uci` → `id`/`option`/`uciok`, `isready` → `readyok`), `ucinewgame`, `position [startpos|fen …] moves …` (moves parsed by [`UciMoveParser`](src/main/java/org/michaelfl/mychess/UciMoveParser.java)), `go` (with time-control fields), `stop`, and `quit`, plus the `UCI_Chess960` option that switches the board and move generator into Fischer-random mode. Output is routed through [`Log`](src/main/java/org/michaelfl/mychess/Log.java) in UCI mode so diagnostics never corrupt the protocol stream. See [Running myChess on lichess](docs/myChess-on-lichess.md) for a deployment example.

---

## Credits and third-party material

myChess is written from scratch, and the search, board representation, move
generation, and evaluation architecture are original work. Three things in the tree
are not, and are credited here.

**Piece-square tables — PeSTO, by Ronald Friederich.** Since **v4.4.0** the twelve
tapered piece-square tables in
[`PieceSquareTables`](src/main/java/org/michaelfl/mychess/PieceSquareTables.java)
are derived from [PeSTO's evaluation
function](https://www.chessprogramming.org/PeSTO%27s_Evaluation_Function), created
by Ronald Friederich and first tested in his engine
[RofChade](https://www.chessprogramming.org/RofChade). PeSTO itself supersedes
Tomasz Michniewski's [Simplified Evaluation
Function](https://www.chessprogramming.org/Simplified_Evaluation_Function), and the
code form published on the wiki is based on Pawel Koziol's adaptation for TSCP.

The values were mirror-symmetrized (each square averaged with its file-mirrored
counterpart) and scaled ×2 onto myChess's evaluation scale. That is a mechanical
transformation, not a re-tune — the numbers remain Friederich's. Swapping them in
while keeping every other myChess evaluation term measured **+32.6 ± 12.4 Elo**
over v4.3.4 across 2 000 games; see [roadmap § 12.7.5](docs/roadmap.md#1275-pesto-piece-square-tables--done-326-elo-v440)
for the measurement and for why a *pure* PeSTO evaluation measured ~0 while this
hybrid did not. myChess's own Texel tuner and the tables it produced remain in the
tree and are still usable.

**License:** no explicit license or terms of use accompany the tables at the source
(checked 2026-08-11). The Chess Programming Wiki page that publishes them is
licensed [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) by its
contributors. They are credited here because attribution is owed either way.

**Strategic Test Suite — by Dann Corbit and Swaminathan Natarajan.** Since **v4.4.2**
the tree carries the [Strategic Test
Suite](https://www.chessprogramming.org/Strategic_Test_Suite) at
[`src/test/resources/sts/`](src/test/resources/sts/), a curated collection of
positions grouped into 15 strategic themes (undermining, open files, knight
outposts, king activity, …). It is used by
[`Sts`](src/test/java/org/michaelfl/mychess/Sts.java) to score the evaluation per
theme and so name the weakest component instead of guessing at it; see
[`docs/sts-history.md`](docs/sts-history.md).

The positions and their theme grouping are Corbit's and Natarajan's. The file used
here is the **LAN v6** form redistributed by
[`fsmosca/STS-Rating`](https://github.com/fsmosca/STS-Rating), which annotates each
position with up to ten candidate moves and a point value per move, produced with
Stockfish 15 at 60 s per position and `multipv 10`. That form keeps only positions
where the best move leads the second by at least 10 centipawns, which is why it
holds **1188** of the original 1500. Nothing was modified — myChess reads the file
as published.

**License:** the redistributed file is MIT-licensed (© 2019 fsmosca); the notice
travels with it at
[`src/test/resources/sts/LICENSE-STS-Rating.txt`](src/test/resources/sts/LICENSE-STS-Rating.txt).
No explicit license accompanies the suite itself at its origin (checked
2026-08-18); it is credited here because attribution is owed either way. The file
lives in the test resources, so it is not packaged into any distributed artifact.

**Opening book source data.** The MapDB opening book is built from
[KingBase](https://www.kingbase-chess.net/) PGN archives by `OpeningDBImporter`. The
generated `db/` directory is git-ignored, so no third-party game data is
redistributed in this repository.

**Test data.** Evaluation tuning used the Zurichess `quiet-labeled` position set,
and the `bench` suite includes Stockfish's standard benchmark positions; both are
noted where they are used.
