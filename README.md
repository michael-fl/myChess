# myChess

Yet another chess engine — just for fun.

---

## Quick start

**Prerequisites:** JDK 25 (set `JAVA_HOME` if your system default is older), Maven 3.9 or newer.

```bash
mvn compile          # build
mvn exec:java        # start the REPL
mvn test             # run the test suite (296 tests, ~2-3 min)
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

- The engine's per-move time budget defaults to 30 s ([`EngineConfig`](src/main/java/org/michaelfl/mychess/EngineConfig.java)). `go` and `auto` will spend that long on each move at depth 8 — be patient.
- `import [[ <moves> ]]` and `import <pgn>` reload a game from notation; `export` prints the current game in the same long-algebraic form that `import [[...]]` accepts.
- The opening book at `db/openings.db` ships **empty**; the engine works without it. See [Opening Database](docs/opening-database.md#93-import-pipeline) to populate it from PGN files.
- The full REPL reference is in [§ 10.4 REPL commands](docs/notation.md#104-repl-commands).

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
   5. [REPL and main loop](#25-repl-and-main-loop)

In separate files under [`docs/`](docs/):

3. [Core Data Types](docs/data-types.md) — board, piece, move, status encoding; Zobrist hashing; move-list and notation types.
   1. [Board representation (12×12 mailbox)](docs/data-types.md#31-board-representation-1212-mailbox)
   2. [Piece encoding](docs/data-types.md#32-piece-encoding)
   3. [Move encoding (packed int)](docs/data-types.md#33-move-encoding-packed-int)
   4. [Move types](docs/data-types.md#34-move-types)
   5. [`GameStatus` and the status stack](docs/data-types.md#35-gamestatus-and-the-status-stack)
   6. [`Moves`, `MovesArray`, `IntArray`](docs/data-types.md#36-moves-movesarray-intarray)
   7. [`SortableMovesBucket`](docs/data-types.md#37-sortablemovesbucket)
   8. [Zobrist hashing and `PositionEncoding`](docs/data-types.md#38-zobrist-hashing-and-positionencoding)
   9. [`MoveDescription` (symbolic moves)](docs/data-types.md#39-movedescription-symbolic-moves)
   10. [`NextMoveTask` (async result handle)](docs/data-types.md#310-nextmovetask-async-result-handle)
4. [Move Generation](docs/move-generation.md) — pseudo-legal generation per piece, castling, en passant, king-capture detection, ordering hook.
   1. [Generator structure](docs/move-generation.md#41-generator-structure)
   2. [Per-piece movement](docs/move-generation.md#42-per-piece-movement)
   3. [Castling legality](docs/move-generation.md#43-castling-legality)
   4. [En passant](docs/move-generation.md#44-en-passant)
   5. [Pseudo-legal moves and king-capture detection](docs/move-generation.md#45-pseudo-legal-moves-and-king-capture-detection)
   6. [Move-ordering hook](docs/move-generation.md#46-move-ordering-hook)
5. [Evaluation Function](docs/evaluation.md) — material, piece-square tables, mobility, threats, castling, opening, double pawns, checks, composition.
   1. [Material weight](docs/evaluation.md#51-material-weight)
   2. [Piece-square tables](docs/evaluation.md#52-piece-square-tables)
   3. [Mobility](docs/evaluation.md#53-mobility)
   4. [Threat weight](docs/evaluation.md#54-threat-weight)
   5. [Castling state](docs/evaluation.md#55-castling-state)
   6. [Opening state](docs/evaluation.md#56-opening-state)
   7. [Double-pawn penalty](docs/evaluation.md#57-double-pawn-penalty)
   8. [Check count](docs/evaluation.md#58-check-count)
   9. [Composition formula](docs/evaluation.md#59-composition-formula)
6. [Search Algorithm](docs/search.md#6-search-algorithm) — iterative-deepening negamax alpha-beta, PV table, quiescence, time management, mate scoring.
   1. [Negamax / alpha-beta foundation](docs/search.md#61-negamax--alpha-beta-foundation)
   2. [Iterative deepening](docs/search.md#62-iterative-deepening)
   3. [Principal variation table](docs/search.md#63-principal-variation-table)
   4. [Quiescence search](docs/search.md#64-quiescence-search)
   5. [Time management and cancellation](docs/search.md#65-time-management-and-cancellation)
   6. [Checkmate and stalemate scoring](docs/search.md#66-checkmate-and-stalemate-scoring)
7. [Search Optimizations](docs/search.md#7-search-optimizations) — best-known-move ordering, killers, material-only shortcut, make/undo, packed moves, pruning stats, opening book, full sorting policy.
   1. [Best-known-move (PV) ordering](docs/search.md#71-best-known-move-pv-ordering)
   2. [Killer moves](docs/search.md#72-killer-moves)
   3. [Material-only evaluation shortcut](docs/search.md#73-material-only-evaluation-shortcut)
   4. [Make / undo on a single board](docs/search.md#74-make--undo-on-a-single-board)
   5. [Packed-int move representation](docs/search.md#75-packed-int-move-representation)
   6. [Beta cutoff and pruning statistics](docs/search.md#76-beta-cutoff-and-pruning-statistics)
   7. [Opening-book lookup](docs/search.md#77-opening-book-lookup)
   8. [Move sorting (`SortableMovesBucket`)](docs/search.md#78-move-sorting-sortablemovesbucket)
8. [Game Lifecycle and Result Detection](docs/game-lifecycle.md) *(stub)*
9. [Opening Database](docs/opening-database.md) *(stub)*
10. [Notation and I/O](docs/notation.md) *(stub)*
11. [Testing](docs/testing.md) *(stub)*

---

## 1. Introduction

### 1.1 What is myChess?

myChess is a small, self-contained chess engine written from scratch in Java. It is a personal hobby project — the original tag line is *"yet another chess engine, just for fun"* — and the codebase reflects that: clarity over micro-optimization, no third-party engine framework, and no attempt to compete with established engines on playing strength.

The engine plays a full game of chess against itself or against a human opponent. It implements the complete rules of chess (including castling, en passant, pawn promotion, the fifty-move rule, and threefold repetition), generates moves with a [hand-written generator](docs/move-generation.md) over a [12×12 mailbox board](docs/data-types.md#31-board-representation-1212-mailbox), [evaluates positions](docs/evaluation.md) with a material + positional + mobility weighted sum, and [searches](docs/search.md) via iterative-deepening alpha-beta with [quiescence search](docs/search.md#64-quiescence-search), [killer-move heuristics](docs/search.md#72-killer-moves), and a small opening book backed by [MapDB](https://mapdb.org/).

There is no GUI, no UCI/XBoard protocol implementation, and no network play. The only interface is a line-oriented REPL on stdin/stdout in which the user types moves in algebraic notation or commands such as `go`, `revert`, `auto`, `fen`, or `import`. The REPL is implemented in [`CommandHandler`](src/main/java/org/michaelfl/mychess/CommandHandler.java) and started by [`MyChessMain`](src/main/java/org/michaelfl/mychess/MyChessMain.java).

The project is also a study object for the supporting techniques typical of a classical chess engine: bordered mailbox board representation, [packed-int move encoding](docs/data-types.md#33-move-encoding-packed-int), [Zobrist hashing](docs/data-types.md#38-zobrist-hashing-and-positionencoding), [principal-variation move ordering](docs/search.md#71-best-known-move-pv-ordering), alpha-beta pruning, and quiescence search. Each of those is documented in detail in the corresponding chapter under [`docs/`](docs/).

### 1.2 Scope and status

**What the engine does:**

- Full standard chess rules — all piece movements, [castling](docs/move-generation.md#43-castling-legality) (with full legality check including squares-under-attack), [en passant](docs/move-generation.md#44-en-passant), pawn promotion (queen, rook, bishop, knight). See [Move Generation](docs/move-generation.md).
- Endgame and draw detection — checkmate, stalemate, fifty-move rule, and threefold repetition (via Zobrist hash comparison against the status stack). An insufficient-material check is implemented on `Board` but [not wired into game-result detection](docs/game-lifecycle.md#85-insufficient-material). See [Game Lifecycle and Result Detection](docs/game-lifecycle.md).
- Iterative-deepening alpha-beta search in negamax form with a [principal-variation table](docs/search.md#63-principal-variation-table), [killer-move ordering](docs/search.md#72-killer-moves), [quiescence search](docs/search.md#64-quiescence-search) for capture sequences, and a [configurable per-move time budget](docs/search.md#65-time-management-and-cancellation). See [Search Algorithm](docs/search.md#6-search-algorithm) and [Search Optimizations](docs/search.md#7-search-optimizations).
- [Opening book](docs/search.md#77-opening-book-lookup) lookup from a MapDB-backed database keyed by Zobrist position string. The book ships empty; it is built offline by [`OpeningDBImporter`](src/main/java/org/michaelfl/mychess/openingdb/OpeningDBImporter.java) from a directory of PGN files. See [Opening Database](docs/opening-database.md).
- I/O in standard formats — [FEN](docs/notation.md#101-fen) for position export (no import: `fen` is a one-way diagnostic) and [PGN](docs/notation.md#102-pgn) for import of recorded games for replay or opening-book ingestion (no export). See [Notation and I/O](docs/notation.md).
- Self-play (`auto`), single-move calculation (`go`), tip suggestion (`tip`), and full move history with `revert` undo.

**What the engine deliberately does not do (yet):**

- **No UCI / XBoard protocol** — the engine cannot be plugged into Arena, Cute Chess, or any other standard GUI. The only interface is the built-in REPL.
- **No transposition table** — positions reached by different move orders are searched independently. This is the single largest missing optimization compared with a competitive engine.
- **No parallel search** — the search runs on a single dedicated worker thread ([`ChessEngine`](src/main/java/org/michaelfl/mychess/engines/ChessEngine.java) submits to `Executors.newSingleThreadExecutor()`).
- **No neural-network evaluation** (NNUE or otherwise) — evaluation is a hand-written weighted sum.
- **No endgame tablebases**.
- **No pondering** — the engine does not think on the opponent's time.
- **No persistent learning** — only the static opening book is read; the engine does not write back game outcomes.

The git history shows that earlier branches contained two alternative search engines, "engine V1" and "engine V2", both removed in recent commits. The codebase now contains exactly one engine, [`MyChessEngine`](src/main/java/org/michaelfl/mychess/engines/MyChessEngine.java), delegating to [`PositionSearch`](src/main/java/org/michaelfl/mychess/engines/PositionSearch.java). A handful of fields on [`EngineConfig`](src/main/java/org/michaelfl/mychess/EngineConfig.java) marked *"Only used by engine V1"* are dead weight left behind by that cleanup.

**Build & runtime requirements:**

- Java 25 (the build targets `maven.compiler.release=25`).
- Maven 3.9 or newer.
- JUnit Jupiter 5.11 for tests.
- MapDB 3.0.8 as the only runtime dependency.

The test suite contains 296 passing tests (4 skipped) and serves as the executable specification for move generation, encoding, search correctness, and notation parsing.

### 1.3 Repository layout

```
myChess/
├── CLAUDE.md                 # Guidance for AI coding agents (architecture cheat sheet)
├── README.md                 # This document
├── pom.xml                   # Maven build (Java 25, JUnit 5, MapDB)
├── db/                       # Created at runtime — MapDB opening book (git-ignored)
├── src/
│   ├── main/java/org/michaelfl/mychess/
│   │   ├── MyChessMain.java          # Entry point: opens DB, starts REPL
│   │   ├── CommandHandler.java       # REPL command dispatch
│   │   ├── Game.java                 # One game: board + two engines + status engine
│   │   ├── GameConfig.java           # Per-game engine selection and config
│   │   ├── GameStatus.java           # Immutable snapshot: turn, castling, hash, …
│   │   ├── Board.java                # 12×12 mailbox, make/undo, Zobrist updates
│   │   ├── Move.java                 # Packed-int move accessor + type constants
│   │   ├── Moves.java                # Result of move generation (int[] wrapper)
│   │   ├── MovesArray.java           # Mutable packed-int move array
│   │   ├── MoveDescription.java      # Symbolic (algebraic-notation) move
│   │   ├── MoveGenerator.java        # Pseudo-legal move generation per piece
│   │   ├── MoveSorter.java           # Strategy: how to order generated moves
│   │   ├── SortableMovesBucket.java  # Insertion-sort bucket with weight keys
│   │   ├── KillerMoves.java          # Per-depth killer-move table (2 slots)
│   │   ├── WeightingFunction.java    # Static evaluation
│   │   ├── PieceSquareTables.java    # Per-piece per-square positional bonuses
│   │   ├── QuiescenceSearch.java     # Capture-only extension past max depth
│   │   ├── PositionEncoding.java     # Compact position serialization
│   │   ├── RandomNumbers.java        # Precomputed Zobrist table
│   │   ├── BitOps.java               # Pack/unpack 4 bytes ↔ int
│   │   ├── IntArray.java             # Manually-grown int[] with O(1) push/pop
│   │   ├── ChessUtil.java            # Field ↔ (col,row), notation helpers
│   │   ├── Fen.java                  # FEN import/export
│   │   ├── Pgn.java                  # PGN tokenizer/parser
│   │   ├── PGNImporter.java          # File-level PGN import
│   │   ├── SimpleNotationImporter.java  # Long-algebraic move parser
│   │   ├── GameImporter.java         # Replays a list of MoveDescriptions
│   │   ├── MovesCounter.java         # Perft-style position counter
│   │   ├── MyChessEnv.java           # Per-process environment (opening DB)
│   │   ├── EngineConfig.java         # Search depth, time, rule toggles
│   │   ├── Statistics.java           # Search node counters (for logging)
│   │   ├── Assert.java               # Lazy-message assertion helper
│   │   ├── IllegalMoveException.java
│   │   ├── IllegalChessPositionException.java
│   │   ├── engines/
│   │   │   ├── ChessEngine.java          # Abstract engine: async API, executor
│   │   │   ├── MyChessEngine.java        # Concrete engine, delegates to PositionSearch
│   │   │   ├── PositionSearch.java       # Iterative-deepening negamax alpha-beta
│   │   │   ├── MoveSorterImpl.java       # Ordering: PV → killers → captures
│   │   │   └── NextMoveTask.java         # Async result handle, cancellation
│   │   └── openingdb/
│   │       ├── OpeningDB.java            # MapDB BTreeMap wrapper
│   │       ├── DBValue.java              # Binary record: (count, [move,total,win,loss]*)
│   │       └── OpeningDBImporter.java    # PGN → DB ingest pipeline
│   └── test/java/org/michaelfl/mychess/  # 296 tests covering generator, search,
│                                          # FEN/PGN, evaluation, Zobrist, …
└── target/                   # Maven output (git-ignored)
```

**Detailed documentation by area:**

| Source area | Documentation |
|---|---|
| `Board`, `Move`, `GameStatus`, `Moves`, `IntArray`, `BitOps`, `RandomNumbers`, `PositionEncoding`, `MoveDescription` | [Core Data Types](docs/data-types.md) |
| `MoveGenerator`, `MoveSorter`, `SortableMovesBucket`, `KillerMoves` | [Move Generation](docs/move-generation.md) + [Move sorting policy](docs/search.md#78-move-sorting-sortablemovesbucket) |
| `WeightingFunction`, `PieceSquareTables` | [Evaluation Function](docs/evaluation.md) |
| `engines/PositionSearch`, `engines/ChessEngine`, `engines/MyChessEngine`, `engines/MoveSorterImpl`, `engines/NextMoveTask`, `QuiescenceSearch` | [Search Algorithm](docs/search.md#6-search-algorithm) + [Search Optimizations](docs/search.md#7-search-optimizations) |
| `Game`, `GameConfig` (result detection, draw rules) | [Game Lifecycle and Result Detection](docs/game-lifecycle.md) |
| `openingdb/OpeningDB`, `openingdb/DBValue`, `openingdb/OpeningDBImporter` | [Opening Database](docs/opening-database.md) |
| `Fen`, `Pgn`, `PGNImporter`, `SimpleNotationImporter`, `GameImporter`, `CommandHandler` | [Notation and I/O](docs/notation.md) |
| `src/test/…` | [Testing](docs/testing.md) |

**Key conventions:**

- All production code lives under a single Java package, `org.michaelfl.mychess`, plus two sub-packages: `engines` for search and `openingdb` for the opening book. The dependency direction is strictly one-way (`engines` and `openingdb` depend on the root package, never the reverse).
- Tests sit in the matching test-source tree with one `*Test.java` per production class.
- The MapDB opening book lives at `db/openings.db`. The directory is created on first run and git-ignored; the engine works without a populated book — it simply skips the book-lookup step.

## 2. Architecture Overview

### 2.1 High-level structure

myChess is organized as five layers, stacked from the entry point downward:

```
┌──────────────────────────────────────────────────────────────────┐
│  MyChessMain  ─ entry point, owns the OpeningDB lifecycle        │
└────────────────────────────┬─────────────────────────────────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│  CommandHandler   ─ REPL: parses lines, dispatches to commands   │
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
│  ├─ MoveSorterImpl       (PV → killers → captures ordering)      │
│  ├─ KillerMoves          (per-depth, 2 slots)                    │
│  ├─ QuiescenceSearch     (capture extensions past max depth)     │
│  ├─ WeightingFunction    (static evaluation, ~9 components)      │
│  └─ Statistics           (node counters for logging)             │
└──────────────────────────────────────────────────────────────────┘
```

The split has two non-obvious properties:

1. **`Game` owns the engines, not the other way around.** A `Game` instantiates one engine per color plus a third *status engine* used only to decide whether the position after a move is checkmate, stalemate, or ongoing. The user-facing engines never make a move on the board; they only return a candidate `MoveAndWeight`. The actual mutation of the `Board` happens in `Game.makeMove(...)`, after the engine has returned.

2. **The search has no knowledge of the REPL or even of `Game`.** `PositionSearch` operates on a `Board` copy and a freshly built `MoveGenerator`. It receives a `NextMoveTask` for cancellation polling and a `ChessEngine` for the configuration and the `Random` instance, but it does not call back into `Game`. This is what lets the same search class be reused inside the depth-2 status engine without recursion or shared state.

Each box in the diagram has its own deep-dive chapter: [`Board` / `Move` / `GameStatus`](docs/data-types.md), [`MoveGenerator`](docs/move-generation.md), [`WeightingFunction`](docs/evaluation.md), [`PositionSearch` / `QuiescenceSearch` / `KillerMoves`](docs/search.md).

### 2.2 Package boundaries and dependencies

There are exactly three packages:

| Package | Responsibility | May depend on |
|---|---|---|
| `org.michaelfl.mychess` | Rules, board, moves, evaluation, notation, REPL | (root) |
| `org.michaelfl.mychess.engines` | Search algorithm and async engine API | root |
| `org.michaelfl.mychess.openingdb` | MapDB-backed opening book | root |

The dependency direction is strictly one-way: `engines` and `openingdb` import from the root package; the root package never imports from either sub-package, with the single exception that `Game` and `Board` refer to `engines.ChessEngine` and `engines.ChessEngine.MoveAndWeight` because the engine is fundamentally part of how a game progresses.

Inside the root package, the natural sub-layers are visible from the import graph rather than from a directory structure:

- **Pure data / encoding** — `BitOps`, `IntArray`, `ChessUtil`, `RandomNumbers`. No dependencies on other myChess classes.
- **Move and position primitives** — `Move`, `Moves`, `MovesArray`, `GameStatus`, `MoveDescription`. Depend on the encoding layer.
- **Board** — `Board` (1100 lines, the heart of the rules layer), `PositionEncoding`, `Fen`. Depend on primitives.
- **Generation, sorting, evaluation** — `MoveGenerator`, `MoveSorter`, `SortableMovesBucket`, `KillerMoves`, `WeightingFunction`, `PieceSquareTables`, `QuiescenceSearch`. Depend on board.
- **Notation and import** — `Pgn`, `PGNImporter`, `SimpleNotationImporter`, `GameImporter`, `MoveDescription`.
- **Orchestration** — `Game`, `GameConfig`, `EngineConfig`, `MyChessEnv`, `Statistics`, `Assert`.
- **REPL** — `CommandHandler`, `MyChessMain`. Top of the chain; nothing depends on these.

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
3. Holds engine-level state that persists across calls: the `EngineConfig` and the `Random` instance used for opening-book sampling. (The class also instantiates its own `KillerMoves` and `MoveGenerator`, but `MyChessEngine` and `PositionSearch` build fresh per-search instances rather than reusing those engine-level fields.)

The abstract method `calculateNextMoveSub(NextMoveTask)` is the extension point. The only concrete implementation, [`MyChessEngine`](src/main/java/org/michaelfl/mychess/engines/MyChessEngine.java), delegates straight to `PositionSearch.calculateNextMove(...)`.

**`PositionSearch`** is the pure search. It takes a `ChessEngine` (for config and the shared `Random`), a `NextMoveTask` (for cancellation polling), and the `Game` (only to read the current board and turn). It constructs its own working `Board` copy via `game.getBoard().copy()`, its own `KillerMoves` table, and its own `MoveGenerator` bound to that table — none of those persist across move calculations. Its single public entry point is `calculateNextMove(...)`, which runs iterative deepening until the time budget is consumed or the configured `maxDepth` is reached, then returns the best `MoveAndWeight` it found. Full details are in [Search Algorithm](docs/search.md#6-search-algorithm) and [Search Optimizations](docs/search.md#7-search-optimizations).

The reason the three roles are split this way: it lets the same search class be used both as the user-facing engine *and* as the depth-2 status engine inside `Game`, without recursion and without one search interfering with another's state. Each search call constructs a fresh `PositionSearch` with a fresh working board, fresh `WeightingFunction`, and fresh `Statistics`.

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

1. `NextMoveTask.cancel()` sets the volatile flag *and* calls `resultFuture.cancel(false)`. The interrupt itself does little inside `PositionSearch` (the search does no blocking I/O), so the flag is the load-bearing mechanism.
2. `PositionSearch.alphaBetaSearchI(...)` checks `task.isCanceled()` once per node, after move generation. If true, it throws a `CancellationException`, which unwinds back to the executor.

**Time management is independent of cancellation.** `PositionSearch` records `timeout = System.currentTimeMillis() + secondsPerMove * 1000L` at construction time and checks it inside `isTimeout()` — but only every 10,000 visited nodes, to avoid hitting `currentTimeMillis()` on every leaf. When the deadline is exceeded mid-iteration, the in-flight depth's result is discarded and the best result from the previous completed iteration is returned. This is what makes iterative deepening safe under a hard time budget: there is always a complete previous-depth answer to fall back on. See [Time management and cancellation](docs/search.md#65-time-management-and-cancellation) for the polling details and trade-offs.

**The status engine is a synchronous user of the same machinery.** `Game.calculateGameResult()` calls `statusEngine.calculateNextMove(new NextMoveTask())` directly, not through `nextMoveAsync`. It executes on the calling thread (typically the REPL thread), with the timeout of the status engine's own `EngineConfig` (also 30 s by default, but depth-capped at 2 so it returns within milliseconds in practice).

**Engine shutdown** is initiated by `Game.shutdown()`, which calls `shutdown()` on both user-facing engines. `CommandHandler`'s quit/exit/q handler calls `game.shutdown()`. The status engine's executor is shut down implicitly when the JVM exits (no daemon thread protection, but the thread is idle by then). MapDB is closed via the try-with-resources block in `MyChessMain`.

### 2.5 REPL and main loop

The entry point [`MyChessMain.main(...)`](src/main/java/org/michaelfl/mychess/MyChessMain.java) is intentionally tiny:

```java
public static void main(String[] args) {
    try (OpeningDB openingDB = OpeningDB.open()) {
        var env = new MyChessEnv(openingDB);
        var game = new Game();
        CommandHandler scanner = new CommandHandler(env, game);

        game.print();

        do {
            System.out.print(">");
            System.out.flush();
        } while (scanner.nextCommand());

        System.out.println("Closing DB...");
    }
    System.out.println("DB closed");
}
```

Two things to note: the opening DB is opened with try-with-resources so MapDB is guaranteed to flush and unlock the file even on crash, and the loop terminates as soon as `nextCommand()` returns `false` (set by the `quit` / `exit` / `q` handlers).

[`CommandHandler`](src/main/java/org/michaelfl/mychess/CommandHandler.java) implements a classical Chain-of-Responsibility dispatch:

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
| `import <pgn>` / `imp <pgn>` | `PGNImporter` → list of `MoveDescription` → replay on a fresh `Game` |
| `tip` | Like `go` but does not apply the move; prints the suggestion and the principal variation |
| `o…` | Look up the current Zobrist position string in the opening DB and print all known moves with their win/draw/loss statistics |

Adding a new REPL command is a two-step change: define a new nested `Command` subclass inside `CommandHandler` and append it to the command list assembled in the constructor.
