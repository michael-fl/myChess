# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run

Maven project, Java 25, JUnit Jupiter 5.

The build is configured via `maven.compiler.release` in `pom.xml`. Run with `JAVA_HOME` pointing to a JDK 25 (the system default JDK may be older — Maven uses `JAVA_HOME` to pick the compiler).

```sh
mvn compile                     # compile
mvn test                        # run all tests
mvn -Dtest=BoardTest test       # run one test class
mvn -Dtest=BoardTest#methodName test
mvn exec:java                   # launch REPL (org.michaelfl.mychess.MyChessMain)
mvn package                     # build jar in target/
```

The REPL opens `db/openings.db` (MapDB) on start and creates it on first run if missing. `db/` is git-ignored.

## REPL command surface

`MyChessMain` runs an interactive loop dispatched through `CommandHandler`. Each command is a nested `Command` subclass with `canHandle(line)`/`handle(line)`. To add a command, add a new inner class and register it in the command list inside `CommandHandler`. Existing commands:

`quit`/`exit`/`q`, `new`, `auto` (engine self-play), `import <pgn-or-moves>`/`imp`, `l` (last imported), `print`/`p`, `board`, `export`/`exp`, `pgn` (game as PGN move text), `revert`/`r`, `tip`, `last`, `dw` (deep weight), `weight`/`w`, `go`/`g` (engine plays one move), `moves`, `fen`, `hash`, `o…` (opening DB lookup). Anything else is parsed as a move in algebraic notation.

## Architecture

Single Java package `org.michaelfl.mychess` plus two sub-packages: `engines` (search) and `openingdb` (MapDB-backed opening book). Recent history removed two earlier engine versions — only `MyChessEngine` remains.

### Board representation (`Board.java`)

12×12 byte array with a 2-square illegal border on all sides — off-board detection is a single comparison against `Board.illegal` instead of bounds math. Square constants `a1…h8` are precomputed indices into this array. Pieces are encoded as small byte constants (`whitePawn`=8 … `blackKing`=21); white/black share the low 3 bits so `piece & 7` gives the piece kind.

The board also owns:
- a `GameStatus` stack — every `makeMove` pushes status (turn, castling rights, en-passant square, half-move clock, last move, Zobrist hash) so `revertMove()` is a pop, not a recomputation.
- Zobrist hashing via `RandomNumbers` (precomputed table) → `calculatePositionKey()` feeds threefold-repetition detection and the opening DB lookup.

### Move encoding (`Move.java`, `BitOps.java`)

Moves are packed into a single `int` (`fromField | toField<<8 | capturedPiece<<16 | moveType<<24`). All hot-path APIs (`Moves`, `MovesArray`, `IntArray`, `MoveGenerator`, search) pass moves as `int`, not as `Move` objects, to avoid allocation. The `Move` wrapper class exists for printing/equality at boundaries.

### Search (`engines/PositionSearch.java`)

`MyChessEngine.calculateNextMoveSub` delegates to `PositionSearch`. The search is:

- **Iterative deepening** from depth 1 up to `EngineConfig.maxDepth`, bounded by `millisPerMove` (timeout checked every 10 000 nodes via `Statistics.getPositionsCount()`). Before each iteration `PositionSearch.shouldSkipIteration` consults `IterationTimings` — a process-static per-depth SMA — and bails out early if the next deepening iteration is unlikely to complete in the remaining budget. A probing override with a remaining-time ratio gate keeps the SMA from freezing. All tuning knobs live in `engines/EngineTuning.java`.
- **Negamax alpha-beta** with a principal-variation table flattened into a single `int[pvMaxLength * pvMaxLength]` indexed by `depth * pvMaxLength + depth`.
- **Best-known-move ordering**: the previous iteration's PV is passed in as `bestKnownPath` and the `MoveSorterImpl` places it first; an `__assert` in `PositionSearch` enforces this invariant.
- **Killer-move heuristic** via `KillerMoves` (only non-capturing moves that caused beta cut-offs).
- **Quiescence search** (`QuiescenceSearch`) extends past `maxDepth` whenever the last move was a capture, capped at `EngineConfig.getMaxQuiescenceDepth()` (20).
- **Material-only shortcut**: if cumulative material delta during search exceeds `EVALUATE_MATERIAL_ONLY_THRESHOLD` (200 centipawns) the full positional eval (`WeightingFunction` + `PieceSquareTables`) is skipped — only material is returned. This is a load-bearing pruning heuristic, not a defensive bail-out.
- **Async execution**: `ChessEngine.nextMoveAsync` runs the search on a single-thread executor and returns a `NextMoveTask` that exposes a `Future`-style API plus cooperative cancellation (`task.isCanceled()` is polled inside the search and throws `CancellationException`).

`ChessEngine.calculateNextMove` short-circuits the search when the game is already over, when the 50-move / threefold-repetition rule fires, or when the opening DB has a candidate move (≥100 occurrences, ≥20% win, <45% loss — weighted random pick by frequency). The `weightFactor` (`+1` for white, `−1` for black) is applied at the boundary so the search itself runs in pure negamax form.

### Game lifecycle (`Game.java`)

`Game` owns three engines: `engineWhite`, `engineBlack`, and a `statusEngine` (always `MyChessEngine` at depth 2) used only by `calculateGameResult()` to detect checkmate/stalemate after each move. After every successful `makeMove`, `calculateAndSetGameResult()` runs the status engine — if no legal reply exists the result transitions from `ONGOING` to `CHECKMATE`/`STALEMATE`. On any failure during move validation or post-move verification the move is reverted, so `Board`'s status stack stays consistent.

### Opening database (`openingdb/`)

`OpeningDB` wraps a MapDB `BTreeMap<String, byte[]>` at `db/openings.db` with transactions enabled. Keys are Zobrist position strings; values are `DBValue` byte blobs encoding `(positionCount, [move, win, draw, loss]*)`. `OpeningDBImporter` builds the DB from PGN files (hard-coded path `/Users/mf/_PRIVAT_/Schach/KingBase2019-pgn/`, `maxMoveDepth=16`). The DB is opened in `MyChessMain` via try-with-resources — `OpeningDB.close()` must run on shutdown or MapDB leaves the file locked.

### PGN / FEN / notation

- `Fen` — full FEN export/import (used by `fen`/`export` REPL commands and by `Board.exportFEN`).
- `Pgn` + `PGNImporter` — parse PGN files into `MoveDescription` lists.
- `MoveDescription` — symbolic move (piece, target square, disambiguation, capture/check/checkmate/promotion flags). `Board.resolveMoveDescription` turns a symbolic move into a concrete `Move` using the current `MoveGenerator`.
- `SimpleNotationImporter` — pure long-algebraic input from the REPL.

## Project-specific conventions

- **Don't allocate in the search hot path.** Moves are `int`s, move lists are `Moves`/`MovesArray` backed by reusable `int[]`, and `Board.makeMove`/`revertMove` mutate the same board (no copy-on-make in the inner loop — `calculateNextMove` copies the board once at the root). New code in `PositionSearch`, `MoveGenerator`, `WeightingFunction`, `QuiescenceSearch` should preserve this.
- **The `GameStatus` stack is the source of truth for reversibility.** Any mutation of board state inside `Board.makeMove` must have a matching undo in `revertMove`, or threefold-repetition and the search's `makeMove`/`revertMove` pairing will silently corrupt state.
- **Invariants are encoded via `Assert.__assert(Supplier, Supplier)`** with lazy message construction so they're cheap when disabled — use the same pattern when adding new invariants in the search.
- **The `engines/` package is a one-way dependency** on the root package, not vice-versa. Root-package classes (`Game`, `Board`, …) reference engines only through the abstract `ChessEngine` base class.
- **US English everywhere — no British spellings.** Identifiers, comments, JavaDoc, log/exception messages, commit subjects, doc files under `docs/`, and chat-facing summaries about code all use US English. `color` not `colour`, `center` not `centre`, `behavior` not `behaviour`, `analyze` not `analyse`, `optimize` not `optimise`, `serialize` not `serialise`, `cancel(l)ed` (single `l`), `favor` not `favour`. The global rule in `~/.claude/CLAUDE.md` covers this; this entry is a local reminder because the convention is easy to slip on when writing prose comments.
