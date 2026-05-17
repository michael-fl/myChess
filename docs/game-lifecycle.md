# 8. Game Lifecycle and Result Detection

A `Game` transitions through exactly four states defined by [`Game.GameResult`](../src/main/java/org/michaelfl/mychess/Game.java):

```java
public enum GameResult {
    CHECKMATE,      // side to move has no legal reply and is in check
    STALEMATE,      // side to move has no legal reply and is NOT in check
    DRAW,           // any other terminal: 50-move, threefold, agreed
    ONGOING;        // starting state
}
```

The state lives on `Game.result` (a single field, not a stack — game results are terminal). Initial value is `ONGOING`. Transition rules:

- `ONGOING → CHECKMATE` / `STALEMATE` — set by `Game.calculateAndSetGameResult()`, run automatically after every `Game.makeMove(...)`. The decision is made by the [status engine](#81-status-engine).
- `ONGOING → DRAW` — set by the engine's pre-search short-circuit when the [fifty-move rule](#84-fifty-move-rule) or [threefold repetition](#83-threefold-repetition) fires, or set manually by the REPL `auto` command on its 1000-move safety bound.
- `CHECKMATE` / `STALEMATE` / `DRAW → ONGOING` — only via `Game.revertMove()`, which pops the status stack and resets `result` back to `ONGOING`. There is no other path out of a terminal state.

`Game` enforces the terminal invariant defensively: `makeMove(MoveDescription)` throws `IllegalStateException("Game is already over.")` if the result is not `ONGOING`, and every REPL command that produces a move (`go`, `auto`, `tip`, algebraic input, …) tests `game.getResult() == GameResult.ONGOING` before proceeding.

The actual rule-checking logic is **not** in `Game` itself — it is delegated to a dedicated *status engine* described next.

## 8.1 Status engine

`Game` owns three engines, not two: `engineWhite`, `engineBlack`, and a third **`statusEngine`** constructed in the `Game` constructor:

```java
statusEngine = new MyChessEngine(
        new EngineConfig.Builder()
                .maxDepth(2)
                .enableThreefoldRepetition(engineWhite.getConfig().isEnableThreefoldRepetition())
                .enableFiftyMovesRule(engineWhite.getConfig().isEnableFiftyMovesRule())
                .silent(true)
                .build(), this);
```

The status engine is a **plain `MyChessEngine` capped at depth 2**, used only to answer one question after every move: *does the side to move still have at least one legal reply?* If yes, the game continues; if no, it has just ended in either checkmate or stalemate, and the result tells which.

**Why depth 2, not depth 1?** Because mate detection relies on the [king-capture trick](move-generation.md#45-pseudo-legal-moves-and-king-capture-detection). At depth 1, the search tries every legal-looking move for the side to move. At depth 2, it generates the opponent's pseudo-legal replies, and any move that captured the king there indicates that the side-to-move's depth-1 candidate was actually illegal (it left their king in check). The two layers together correctly classify a position as terminal.

A depth-1 status engine would over-report `ONGOING`: it would accept any pseudo-legal move as "the side to move has a reply", even if every such move would lose the king on the next ply (which is the definition of mate).

**Invocation point.** `Game.calculateAndSetGameResult()` is called from inside `Game.makeMove(MoveDescription)` after every applied move:

```java
public void calculateAndSetGameResult() {
    setResult(calculateGameResult());
}

private GameResult calculateGameResult() {
    MoveAndWeight move = statusEngine.calculateNextMove(new NextMoveTask());
    if (move.path.length > 0 && move.path[0] != 0) {
        // at least one move still possible ==> ongoing
        return GameResult.ONGOING;
    } else {
        return move.result;
    }
}
```

The check is simple: ask the status engine for a move; if `move.path[0] != 0`, there is at least one legal reply and the game is `ONGOING`; otherwise the result the engine returned (`CHECKMATE`, `STALEMATE`, or `DRAW`) is the final answer.

**Synchronous execution.** Unlike the user-facing engines, the status engine is called via `calculateNextMove(new NextMoveTask())` directly — not through `nextMoveAsync(...)`. It runs on the calling thread (typically the REPL thread). At depth 2 it returns in milliseconds even on complex positions, so blocking the REPL is not a concern. This also avoids the executor / `Future` overhead for a query that fires after every single move.

**Configuration inheritance.** The status engine inherits the threefold-repetition and fifty-move-rule settings from `engineWhite`'s config. The assumption — currently uncontested in the code — is that both user-facing engines agree on which draw rules are active. Asymmetric draw-rule configurations across colors would silently use white's setting for status detection.

**Cost.** The status engine call adds one `MyChessEngine` depth-2 search per `Game.makeMove(...)`. In code that imports a long PGN game (or builds the opening DB), this matters: 200 plies × 1 status check per ply = 200 depth-2 searches just to replay a game. The comment in `Game.java` line 60 acknowledges this:

```java
// OPT MF: Expensive hotspot method!
// Check if game is over
calculateAndSetGameResult();
```

…but no optimization has been applied. A cheaper "do any legal moves exist?" check (without running the full search) would save substantial time in import-heavy flows.

## 8.2 Checkmate and stalemate

The decision *checkmate vs stalemate* is made deep inside the status engine's search, in [`PositionSearch.checkmateOrStalemate`](search.md#66-checkmate-and-stalemate-scoring). This section is about how `Game` consumes that decision.

When `calculateGameResult()` finds `move.path[0] == 0` (no legal reply), it returns whatever `move.result` is — and `move.result` was set by the search at the terminal node:

| Terminal condition (inside search) | `move.result` | `Game.result` becomes |
|---|---|---|
| No legal reply, side to move is in check (`isKingChecked` true) | `CHECKMATE` | `CHECKMATE` |
| No legal reply, side to move is NOT in check | `STALEMATE` | `STALEMATE` |
| Repetition or 50-move rule fires inside the search | `DRAW` | `DRAW` |

The terminal classification depends entirely on `Board.isKingChecked(MoveGenerator)`, which itself uses the [king-capture trick](move-generation.md#45-pseudo-legal-moves-and-king-capture-detection) — switch the turn, generate opponent moves, check if `Moves.ILLEGAL` is returned (meaning the opponent could capture the king now → we are in check).

**Reporting to the user.** `Game.print()` formats the result line based on which color is to move *at the terminal position*:

```java
if (getResult() == GameResult.CHECKMATE || getResult() == GameResult.STALEMATE)
    System.out.println("Result: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black") + " " + getResult());
else if (getResult() == GameResult.DRAW)
    System.out.println("Result: DRAW");
```

A checkmate where it is black's turn to move is printed as `"Result: black CHECKMATE"` — meaning *black has been mated* (white delivered the mate). Same convention for stalemate.

**`Board.isCheckmate(MoveGenerator)`** also exists as a standalone helper, used by `Board.moveToShortNotation` to annotate the `#` suffix on the produced `MoveDescription`. It re-implements the check inline (test for `isKingChecked`, then iterate all legal moves and try each on a board copy looking for one that escapes the check). It is not used by `Game.calculateGameResult` — that path always goes through the status engine. Two implementations of the same logic exist because each call site needs a slightly different return value (`boolean` vs `GameResult`).

## 8.3 Threefold repetition

A position counts as "repeated" if the same Zobrist hash (see [§ 3.8](data-types.md#38-zobrist-hashing-and-positionencoding)) has appeared at least two prior times in the game's status stack, with the **same side to move** and **within the current irreversible-move window**. When the count of repetitions reaches three (the current position plus two prior matches), the side to move may claim a draw — myChess claims it automatically.

**Detection** is `Board.isThreefoldRepetition()`:

```java
public boolean isThreefoldRepetition() {
    final GameStatus gameStatus = getGameStatus();
    final int halfMoveClock = gameStatus.getHalfMoveClock();
    if (halfMoveClock < 4 || stackSize < 4) {
        return false;
    }
    final long hash = gameStatus.getPositionHash();
    final int lowerLimit = Math.max(stackSize - 1 - halfMoveClock, 0);
    int count = 0;

    for (int i = stackSize - 3; i >= lowerLimit; i -= 2) {
        if (hash == statusStack[i].getPositionHash()) {
            count++;
        }
        if (count == 2) {
            return true;
        }
    }

    return false;
}
```

Three properties worth noting:

1. **Walks the stack in steps of 2.** `i -= 2` skips opponent positions — repetition requires *the same side to move*, which only happens every second ply.
2. **Lower bound is `halfMoveClock`-derived.** A pawn move or capture is irreversible: positions before it cannot match any position after it (the piece arrangement has fundamentally changed). `halfMoveClock` is exactly the count of plies since the last such irreversible event (see [§ 8.4](#84-fifty-move-rule)), so positions older than that cannot repeat the current one. The loop stops at `stackSize - 1 - halfMoveClock`, which is the last *reversible* position before the current.
3. **Fast bail-out.** If `halfMoveClock < 4`, fewer than two prior same-color positions can exist within the reversible window → return `false` without scanning.

**Use** is in two places:

- **`ChessEngine.calculateNextMove`** (pre-search short-circuit): if the *current root* position is a threefold repetition, return `(0, 0, DRAW)` without running the search. This is the rule-conforming behavior — the engine claims the draw rather than playing a move that resolves the repetition.
- **`PositionSearch.alphaBetaSearchI`** (inside the search): the same check fires for every position reached during the search, returning a `SearchNodeResult.draw(...)`. This is what lets the engine *avoid* lines that lead to a draw when it is winning — a forced-draw position scores 0, which loses to the engine's actual winning evaluation, so the search rejects it.

**Opt-out.** Threefold repetition is gated by `EngineConfig.isEnableThreefoldRepetition()` (default `true`). The status engine inherits the setting from `engineWhite`. Disabling it means the engine will neither claim repetition draws nor avoid lines that produce them — useful only for debugging or for unit tests that need a deterministic search horizon.

## 8.4 Fifty-move rule

The **fifty-move rule** terminates a game as a draw if 50 full moves (100 plies / half-moves) pass without a pawn move or capture by either side.

**The counter** is `GameStatus.halfMoveClock`, updated inside `Board.makeMove(int)`:

```java
// Reset halfMoveClock if a pawn was moved or a piece was captured
int newHalfMoveClock = capturedPiece != 0 || Board.isPawn(movedPiece) ? 0 : gameStatus.getHalfMoveClock() + 1;
```

Two reset conditions: a piece was captured (`capturedPiece != 0`) or a pawn was moved (`Board.isPawn(movedPiece)`). Anything else — a knight move, a king move, a rook move with no capture — increments the clock by one.

The counter is part of the immutable `GameStatus` snapshot pushed onto the board's status stack, so `revertMove()` automatically restores the previous value without bookkeeping.

**Termination threshold** is `halfMoveClock >= 100` (50 full moves × 2 plies). Checked in the same two places as threefold repetition:

```java
// ChessEngine.calculateNextMove (pre-search)
} else if ((getConfig().isEnableFiftyMovesRule() && game.getGameStatus().getHalfMoveClock() >= 100) || isThreefoldRepetition()) {
    move = new MoveAndWeight(0, 0, GameResult.DRAW, new int[0]);
}

// PositionSearch.alphaBetaSearchI (inside search)
if ((engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) || (engineConfig.isEnableThreefoldRepetition() && ctx.workingBoard.isThreefoldRepetition())) {
    return SearchNodeResult.draw(ctx.alphaWeight(), ctx.betaWeight());
}
```

So the engine both **claims the draw at the root** (refuses to make any move if the rule has already fired) and **scores it as 0 inside the search** (refuses to play into it if winning). The asymmetry with how a normal mate score works — see [§ 6.6 Checkmate and stalemate scoring](search.md#66-checkmate-and-stalemate-scoring) — is that a 50-move-rule draw has no depth-discount: a draw 50 moves away scores the same as a draw next move (both 0).

**Opt-out.** `EngineConfig.isEnableFiftyMovesRule()` (default `true`). Same inheritance from `engineWhite` for the status engine.

**Not implemented:** the optional FIDE *seventy-five-move rule* (automatic draw after 75 moves without pawn move or capture, regardless of claim). myChess implements only the classical claim-after-fifty form, automatically claimed.

## 8.5 Insufficient material

**Status: not implemented.**

myChess does **not** claim insufficient-material draws automatically. Positions like K vs K, K + B vs K, or K + N vs K remain `ONGOING`, the search continues to evaluate them at a score of 0 (material balance), and the game ends only via:

- the [fifty-move rule](#84-fifty-move-rule) eventually firing, or
- the REPL's `auto` command hitting its 1000-move safety bound and forcing `DRAW`, or
- a user typing `quit`.

An earlier `Board.isDrawByMaterial()` helper (covering K vs K, K + minor vs K, K + minor vs K + minor) lived in the codebase for a while but was never wired into `Game.calculateGameResult` or any search short-circuit. It was removed as dead code; reintroducing the check would mean adding a clause next to the existing 50-move and threefold checks in `ChessEngine.calculateNextMove`, plus a parallel check inside `PositionSearch.alphaBetaSearchI` to prevent the search from trying to mate in positions where it cannot.
