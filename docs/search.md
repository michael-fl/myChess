# 6. Search Algorithm

The search lives in [`PositionSearch`](src/main/java/org/michaelfl/mychess/engines/PositionSearch.java) (374 lines). It is constructed fresh per move, runs to completion (or to timeout), and returns a `MoveAndWeight` containing the chosen move, its score in centipawns, the game-result classification, and the full principal variation.

The control flow has three nested loops:

```
calculateNextMove()                       ─ iterative deepening
└─ for depth in 1..maxDepth:
    calculateNextMove(depth)              ─ root: try each move, no alpha cap on root
    └─ for each root move:
        alphaBetaSearch(ctx)              ─ recursive negamax with α/β window
        └─ for each move at this depth:
            alphaBetaSearch(child ctx)    ─ recurse
            // OR (at maxDepth):
            quiescenceSearch(...)         ─ extend through captures
```

Each layer has its own job. The outer loop manages time and re-uses the previous iteration's best move. The root layer differs from inner layers only in that it tracks the *winning move*, not just the winning score. The inner alpha-beta layer is pure negamax. The quiescence layer extends past the nominal max depth by resolving **all captures** at every leaf until the position is quiet (the all-captures upgrade of [§ 12.6.1](roadmap.md#1261-enter-at-every-leaf-and-follow-all-captures--done-60-elo), +60.4 Elo); see [§ 6.4](#64-quiescence-search) for the details.

## 6.1 Negamax / alpha-beta foundation

myChess uses the **negamax** formulation of minimax: a single recursive function in which the score is always "from the side to move's perspective", and the recursive call returns the negation of the child's score.

```
weight(node) =  evaluate(node)                                 if at leaf
             = -min over children c of weight(c)               otherwise
             = +max over children c of (-weight(c))            equivalent rewrite
```

The advantage over a literal minimax (two mutually recursive `max` and `min` functions, or a player-flag switch inside one function) is that the same code handles both sides — just call `.negate()` on the child result. In `PositionSearch.alphaBetaSearchI`:

```java
ctx.workingBoard.makeMove(move);
var result = alphaBetaSearch(new SearchNodeContext(
        depth + 1, ctx.maxDepth, bestKnownPath,
        -ctx.weightFactor,            // flip color sign
        -ctx.betaWeight, -bestResult.weight,   // swap and negate the α/β window
        -newMaterialWeight, -newMaterialDelta,
        ctx.workingBoard, pvTable)).negate();
ctx.workingBoard.revertMove();
```

The boundary translation lives in `ChessEngine.calculateNextMove`:

```java
int weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
return move.weightFactor(weightFactor);
```

White's score is positive-is-better in the engine's external API; black's score is reported the same way, so a "+1.20" reported by `weight` for black-to-move means *white* is up a bit more than a pawn, regardless of which side asked. Inside the search, however, everything is signed *from the side to move*.

**Alpha-beta** layers on top of negamax by passing two bounds:

- `alpha` — the best score the side to move has been *guaranteed* by some sibling line. Any further candidate move at this node must do at least this well to be relevant.
- `beta` — the best score the *opponent* (one level up) is willing to allow. If a candidate at this node scores ≥ `beta`, the opponent will never choose to enter this node — so further candidates can be skipped (the "beta cutoff" or "fail-high").

In negamax form both bounds are passed as positive numbers from the side-to-move's perspective; on recursion they are negated and swapped:

```java
//  parent passes (alphaWeight, betaWeight) = (α, β)
//  child receives                            = (-β, -bestSoFar)
```

The window starts at the root as `(MIN_ALPHA, +∞)` (no upper cap on the root). Children start with `(-betaWeight, -alphaLocal)` where `alphaLocal = max(parent's α, bestResult.weight)` — i.e. the running α inside the move loop, never below the parent's α.

**The beta cutoff** in `alphaBetaSearchI`:

```java
if (weight >= ctx.betaWeight) {
    statistics.incrPrunedMovesCount(countMoves - i - 1);
    ctx.copyUpPV();
    if (Move.getCapturedPiece(move) == 0) {
        killerMoves.addMove(move, depth);     // see § 7.2
    }
    return result;
}
```

When a cutoff happens, three things are recorded:

1. **Statistics.** The number of pruned siblings is added to the pruning counter — visible in the search log line `"#positions: N, #pruned: M"`.
2. **PV update.** Even though we're cutting off, the PV table is updated so the calling code can still print *a* principal variation (though it may be truncated).
3. **Killer move registration.** If the cutting move was a quiet move (no capture), it gets remembered in the per-depth `KillerMoves` table for ordering at sibling positions of the same depth (see [§ 7.2](#72-killer-moves)).

**Fail-soft.** The returned value on a beta cutoff is the actual `result.weight` that triggered it, not `ctx.betaWeight`. Fail-soft exposes *how far* above β the cutoff went — the same alpha-beta tree is explored as under fail-hard, but the unclamped value lets the caller (and the [transposition table](#79-transposition-table)) record a tighter lower bound. Symmetrically, when no move improves on α the returned `bestResult.weight` may fall below α — fail-hard would have clamped it. The same principle applies in `QuiescenceSearch`: stand-pat cutoffs return the actual stand-pat value and capture cutoffs return the actual weight, both unclamped. See [roadmap § 12.13](roadmap.md#1213-switch-alpha-beta-from-fail-hard-to-fail-soft--s-no-direct-elo-enables-aspiration--tt-tightening) for the design rationale and how it unlocks aspiration windows and TT-bound sharpening.

**Search-node context** is a `record`:

```java
public record SearchNodeContext(
        int depth, int maxDepth, MoveAndWeight bestKnownPath,
        int weightFactor,
        int alphaWeight, int betaWeight, int materialWeight, int materialDelta,
        Board workingBoard, int[] pvTable) { … }
```

It carries everything a child node needs to recurse, plus the PV-table indexing helpers (`pvIndex()`, `pvParentIndex()`, `copyUpPV()` — see [§ 6.3](#63-principal-variation-table)). Constructing a record per call is one allocation per node; the JIT escape-analyzes most of it away in practice.

## 6.2 Iterative deepening

The top-level loop in `PositionSearch.calculateNextMove()`:

```java
private MoveAndWeight calculateNextMove() {
    MoveAndWeight bestPath = null;
    final int maxDepth = engineConfig.getMaxDepth();

    for (int depth = 1; depth <= maxDepth && !isTimeout(); depth++) {
        log("Current depth: " + depth);
        bestPath = calculateNextMove(depth, timeout, bestPath);
        MoveAndWeight m = bestPath.weightFactor(weightFactor);
        log("Depth: " + depth + ", move: " + … + ", weight: " + … + " [" + … + "]");
        log("#positions: " + … + ", #pruned: " + …);
    }

    // The last path component may be an illegal move (not checked on the leaf).
    // Shorten the path by one to avoid returning an invalid PV tail.
    if (bestPath != null && bestPath.path.length >= maxDepth) {
        bestPath.path[maxDepth - 1] = 0;
    }

    return bestPath;
}
```

The search runs **depth 1, then 2, then 3, …** up to `engineConfig.getMaxDepth()` (default `Integer.MAX_VALUE`, so usually bounded by the time budget instead). Each iteration is a complete search at that depth and produces a `MoveAndWeight` that is *passed back into the next iteration* as `bestKnownPath`.

The reason for re-doing the work is **move ordering**. Alpha-beta pruning is most effective when the best move is searched first at each node — then α tightens fast and most siblings are cut off. The previous iteration's principal variation is the engine's best guess for what those best moves are, so iteration `k+1` orders moves with iteration `k`'s PV in front. In a typical position this makes depth `k+1` only marginally slower than depth `k` alone — far less than the 30×-or-so naive branching-factor estimate.

`PositionSearch.calculateNextMove(depth, timeout, bestKnownPath)` then enters the **root layer**, which is structurally similar to `alphaBetaSearchI` but with two differences:

1. It tracks the **chosen move**, not just the chosen score. The root's `bestMoveIndex` is returned in the final `MoveAndWeight`.
2. It does **not** prune on α — α is initialized to `MIN_ALPHA` and only ratchets upward as moves are tried. There is no upper cap (`betaWeight` is effectively infinite at the root, modulo how the children negate to `-MIN_ALPHA = MAX_BETA`).

**Timeout interaction.** When `alphaBetaSearch` returns `SearchNodeResult.TIMEOUT`, the root layer aborts the in-flight iteration and returns the *previous* `bestKnownPath` unchanged:

```java
if (result.isTimeout()) {
    return previousBestKnownPath;
}
```

This is what makes iterative deepening robust under a hard time budget: there is *always* a complete result from the previous depth to fall back on. The exception is depth 1 — if depth 1 itself times out (e.g. a 0-second budget) the search will return `null` and the engine will report no move; in practice depth 1 over 30 legal moves takes microseconds and never times out.

**The PV-trim at the end** is a defensive measure. The deepest node in the PV is a *leaf* that ran static evaluation, not alpha-beta — and the move that led to that leaf wasn't verified against the king-capture test. Trimming the last entry guarantees the returned PV contains only verified-legal moves.

## 6.3 Principal variation table

The principal variation (PV) is the sequence of moves the search currently believes both sides will play. It is collected via a triangular `int[]` shared across every recursion in one iterative-deepening iteration. The **data layout, index arithmetic, and propagation operations** (`copyUpPV`, `truncateParentPv`) are documented in detail in [§ 3.11 Principal-variation table](data-types.md#311-principal-variation-table). This section covers only the *search-side* usage: when each operation is called, how the final PV is extracted at the root, and how it is threaded into the next deepening iteration.

**Writing the PV inside `alphaBetaSearchI`.** At the top of each move-loop iteration the search writes the candidate into its own diagonal slot:

```java
pvTable[pvIndex] = move;                    // tentatively claim this move
ctx.workingBoard.makeMove(move);
var result = alphaBetaSearch(...).negate();
ctx.workingBoard.revertMove();
```

If the child establishes itself as the new best move (either by beating `bestResult.weight` or by causing a beta cutoff), `ctx.copyUpPV()` runs and propagates this row's PV one level up.

If the child instead returns a parent-acceptable result *without* a continuation — terminal mate / stalemate, a 50-move or threefold-repetition draw, or the leaf static-eval return — the child calls `ctx.truncateParentPv()` from inside its own return path. That writes zeros into the same parent-row range, so the parent's later `copyUpPV` carries a clean "this branch stops here" signal up the chain rather than stale data from an earlier sibling's deeper exploration. The four `*_test02` cases in [`IllegalPvRegressionTest`](../src/test/java/org/michaelfl/mychess/IllegalPvRegressionTest.java) reproduce the regression that would re-appear without this call.

**Extraction at the root.** The root iterates the move list itself (in `PositionSearch.calculateNextMove(int, MoveAndWeight)`, not in `alphaBetaSearchI`) and keeps per-candidate PV copies in `allPaths[i]` so the returned `MoveAndWeight` can carry a private copy of the chosen line:

```java
final int[][] allPaths = new int[countMoves][pvMaxLength];
…
System.arraycopy(pvTable, 0, allPaths[i], 0, pvMaxLength);
…
return new MoveAndWeight(plainMoves[bestMoveIndex], results[bestMoveIndex].weight,
                         results[bestMoveIndex].result, allPaths[bestMoveIndex]);
```

**Threading into the next iterative-deepening iteration.** The PV that just finished is handed to the next iteration as `bestKnownPath`; its first entry is used by `MoveSorterImpl` as the "PV move" placed first in every node's move ordering (see [§ 7.1](#71-best-known-move-pv-ordering)). An assertion inside the search enforces that the generator actually puts the requested PV move first:

```java
__assert(() -> !(countMoves > 0 && bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]),
         () -> "First move must be the best known move. Expected: " + … + ", actual: " + …);
```

## 6.4 Quiescence search

A purely fixed-depth alpha-beta search suffers from the **horizon effect**: if the last move at `maxDepth` was a capture, the search evaluates a position mid-exchange. White trades a queen for a pawn at depth 6, the search bottoms out and reports "+8 pawns for white", and never sees that black recaptures the queen at depth 7. The static evaluation is wildly wrong because the position isn't *quiet*.

myChess handles this with [`QuiescenceSearch`](src/main/java/org/michaelfl/mychess/QuiescenceSearch.java) — a second alpha-beta layer that takes over at **every** leaf and resolves **all available captures** until the position is quiet.

> **Status (v4.2.1, July 2026).** Since v4.2.0 this is a proper all-captures quiescence search: it is entered at **every** leaf (not only after a capturing move) and follows **every** legal capture (not only recaptures on the last-contested square). That upgrade ([roadmap § 12.6.1](roadmap.md#1261-enter-at-every-leaf-and-follow-all-captures--done-60-elo)) measured **+60.4 ± 9.7 Elo** vs v4.1.1 (3200-game fixed-N, LOS 100 %). **v4.2.1** then added **Static Exchange Evaluation**: QSearch captures are now ordered by SEE and losing captures (SEE < 0) are pruned ([§ 12.6.2](roadmap.md#1262-capture-ordering-in-qsearch--done-v421-shipped-as-see-ordering) + [§ 12.6.3](roadmap.md#1263-see-pruning-of-losing-captures-in-qsearch--done-406-elo), **+40.6 ± 9.4 Elo** vs v4.2.0, LOS 100 %). It still does *not* delta-prune, extend on checks, or consult the transposition table — the remaining refinements in [§ 12.6.4–12.6.5](roadmap.md#126-quiescence-search-upgrade). What follows describes the current (v4.2.1) behavior.

**Trigger.** Inside `PositionSearch.alphaBetaSearchPre`, when `remainingDepth == 0`, the leaf enters quiescence **unconditionally** (after a cheap illegal-position probe):

```java
if (ctx.remainingDepth() == 0) {
    if (ctx.workingBoard().canCaptureOpposingKing()) {
        return SearchNodeResult.illegal();
    }
    ctx.truncateParentPv();
    int weight = quiescenceSearch(ctx, alphaWeight, betaWeight);
    if (quiescenceSearch.isTimeout()) {          // new in v4.2.0 — see Depth cap
        this.isTimeout = true;
        return SearchNodeResult.TIMEOUT;
    }
    return SearchNodeResult.create(GameResult.ONGOING, weight, Bound.EXACT, 0);
}
```

**Every** leaf enters quiescence now — including those reached by a *quiet* move. This closes the old blind spot where a quiet move that left an enemy piece en prise was scored statically: quiescence always computes stand-pat and then examines the side-to-move's captures, regardless of how the leaf was reached. (Before v4.2.0 there was an entry gate that ran quiescence only after a *capturing* leaf move; it is gone.)

**The stand-pat trick.** A quiescence node's first move is to compute the static evaluation *as if the side to move did nothing* — the so-called *stand-pat* score. This is the lower bound: the side can refuse to enter further captures by accepting the current evaluation.

```java
int standPat = calculatePositionWeight(...);

if (standPat >= ctx.betaWeight()) {
    return ctx.betaWeight();         // even stand-pat is good enough → cutoff
}
if (depth == ctx.maxDepth()) {
    return standPat;                 // hit quiescence cap → bail
}
int bestWeight = Math.max(ctx.alphaWeight(), standPat);
```

`bestWeight` is initialized to the *maximum* of α and stand-pat — the side to move can always do at least as well as stand-pat, so that becomes the floor for further capture searches. Subsequent capture moves can improve on it or trigger a cutoff.

**Following all captures.** Quiescence follows **every** legal capture at the node — a plain check on each generated move:

```java
for (int i = 0; i < countMoves; i++) {
    if (Move.getCapturedPiece(plainMoves[i]) != 0) {
        // … make the capture, recurse, revert
    }
}
```

Since **v4.2.1** the moves come from a quiescence-specific `MoveGenerator.forQuiescenceSearch()`, whose `MoveSorterImpl` runs in quiescence mode: it scores every capture by its **static exchange value** (`StaticExchangeEvaluation.see`), orders winning exchanges (SEE > 0) first, and **drops losing captures (SEE < 0) altogether** ([§ 12.6.2](roadmap.md#1262-capture-ordering-in-qsearch--done-v421-shipped-as-see-ordering) + [§ 12.6.3](roadmap.md#1263-see-pruning-of-losing-captures-in-qsearch--done-406-elo), +40.6 Elo). The loop above therefore walks an already SEE-ordered, SEE-pruned list — the `getCapturedPiece(...) != 0` check is a defensive guard, since the quiescence generator emits only captures. (In v4.2.0 the QSearch instead reused the *main* sorter's victim-first ordering, which was already good enough for the wider tree to pay off — the roadmap's "all-captures without ordering is a net loss" worry never materialized here, see [§ 12.6.1](roadmap.md#1261-enter-at-every-leaf-and-follow-all-captures--done-60-elo) — and v4.2.1 then sharpened both the move order and the node count with SEE.)

This resolves exactly the tactical motifs the old same-square-only search missed: pieces hanging on any square, forks that switch attack squares (e.g. capture on e5, then win the queen on d8), discovered captures, and captures available after a quiet move. Before v4.2.0 the search followed only recaptures on the last-contested square — a pragmatic compromise that predated the [transposition table § 7.9](#79-transposition-table) and capture ordering.

The all-captures search also changes the premise of an earlier eval-side closure: [§ 12.16 `threadWeight`](roadmap.md#1216-discontinue-the-threadweight-investigation--done) kept the `threadWeight` term *because* the old quiescence missed captures on squares other than the contested one — the exact gap that is now closed. That removal is therefore worth **re-testing**. (The [§ 12.17 `chessFactor`](roadmap.md#1217-discontinue-the-chessfactor-investigation--done) closure is *not* affected: it compensates for missing *checks*, which the all-captures search still does not cover.)

**What is still missing relative to the textbook quiescence search** — the remaining [§ 12.6](roadmap.md#126-quiescence-search-upgrade) sub-items, in rough order of expected impact:

- **Delta pruning** — skip captures where stand-pat + captured material + safety margin still falls below α; [§ 12.6.4](roadmap.md#1264-delta-pruning-in-qsearch--s--515-elo). The only remaining QSearch sub-item still open.
- **Check extensions** — pursue forcing check sequences past the QSearch border; [§ 12.4 check extensions](roadmap.md#124-check-extensions--s--1530-elo).
- **TT lookup / store inside QSearch** — score and best-move reuse for transposed leaves; **tried and shelved** ([§ 12.6.5](roadmap.md#1265-tt-integration-in-qsearch--tried-shelved-11-elo-los-4), a separate full table cost −11 Elo — only a shared-TT retry would be worth revisiting).

(SEE-based capture ordering and SEE < 0 pruning — formerly the top items on this list — shipped in v4.2.1; see the *Following all captures* paragraph above.)

**Depth cap.** Quiescence has its own depth budget:

```java
this.quiescenceSearch = new QuiescenceSearch(game, moveGenerator, weightingFunction, statistics,
                                              engineConfig.getMaxQuiescenceDepth());
```

`EngineConfig.getMaxQuiescenceDepth() = DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH = 20`. Since every quiescence ply is a capture (each consumes at least one piece), hitting depth 20 means a capture chain more than 10 deep — astronomically rare. The cap is a safety net; the cooperative `isTimeout()` check added in v4.2.0 is the primary bound that keeps the now-wider all-captures search inside the move budget.

**Recursion is still negamax with α/β.** Quiescence honors the same pruning rules as the main search and propagates `materialWeight`/`materialDelta` to keep the material-only shortcut working (see [§ 7.3](#73-material-only-evaluation-shortcut)).

## 6.5 Time management and cancellation

Two distinct mechanisms control when the search stops: **timeout** (soft — return the best result so far) and **cancellation** (hard — abort entirely).

**Timeout.** `PositionSearch` records its deadline at construction time:

```java
this.timeout = System.currentTimeMillis() + engineConfig.getMillisPerMove();
```

`EngineConfig.millisPerMove` defaults to 30 000 ms. The deadline is then polled by `isTimeout()`:

```java
private boolean isTimeout() {
    if (!isTimeout) {
        isTimeout = statistics.getPositionsCount() % 10000 == 0
                 && System.currentTimeMillis() >= timeout;
    }
    return isTimeout;
}
```

Two performance choices in this method:

1. **Lazy caching.** Once `isTimeout` flips true, it stays true. Subsequent calls don't even look at the clock.
2. **Sub-sampled checks.** The clock is only read every 10,000 visited nodes (`statistics.getPositionsCount() % 10000 == 0`). `System.currentTimeMillis()` is cheap but not free, and the search visits hundreds of thousands of nodes per second. Checking only every 10k means at most a few hundred microseconds of "overshoot" past the deadline.

When `isTimeout()` returns true inside `alphaBetaSearchI`, the search returns the `SearchNodeResult.TIMEOUT` singleton:

```java
if (isTimeout()) {
    return SearchNodeResult.TIMEOUT;
}
```

`TIMEOUT` is a sentinel — reference comparison with `result.isTimeout()` recognizes it on the way back up. It propagates all the way to the root layer, which discards the in-flight depth and returns the previous iteration's `bestKnownPath`. The result is that the *worst* case for a timed search is wasting one iteration's compute; the answer is always at least as good as the deepest completed iteration.

The same `TIMEOUT` is also recognized at the **root layer** (`calculateNextMove(int, long, MoveAndWeight)`):

```java
var result = alphaBetaSearch(...).negate();
if (result.isTimeout()) {
    return previousBestKnownPath;
}
```

**Cancellation.** External callers can abort the search via `NextMoveTask.cancel()`, which sets a volatile flag. The search polls that flag once per node:

```java
if (task.isCanceled()) {
    throw new CancellationException();
}
```

The poll happens *after* move generation but *before* the move loop, so cancellation can only take effect at node boundaries — never mid-evaluation. `CancellationException` propagates straight out of the search, through the executor, and back to the calling `Future.get()`.

**Why two mechanisms?**

- Timeout is *internal*: the search decides itself when to stop. The caller gets a valid answer back, just not as deep as it could have been.
- Cancellation is *external*: someone else (typically the REPL, or a future GUI integration) decides to abort. The caller gets an exception, not an answer.

Both mechanisms operate at node granularity. There is no thread interruption check (`Thread.interrupted()`) because the search does no blocking I/O — interruption would not do anything useful.

**Concurrency story** for the executor and `NextMoveTask` data layout is in [§ 2.4](../README.md#24-concurrency-and-async-move-calculation) and [§ 3.10](data-types.md#310-nextmovetask-async-result-handle); this section is just about *what triggers the search to stop*.

### 6.5.1 Skip-hopeless-iteration heuristic

The vanilla deadline above tells the search *when* to abort mid-iteration, but it does not address a related waste: a deepening iteration that is almost certainly too expensive to complete in the remaining time still gets started, runs for the rest of the budget, and is then discarded mid-flight. Across a typical 30-second move that often costs 10–20 seconds of compute with no usable result — the bestmove handed back to the GUI is the one from the previous, completed iteration.

`PositionSearch` therefore tracks rolling per-depth iteration times in [`IterationTimings`](../src/main/java/org/michaelfl/mychess/engines/IterationTimings.java) (a process-static simple moving average over the last `SMA_WINDOW_SIZE` samples per depth) and short-circuits the iterative-deepening loop in `calculateNextMove()` before starting the next iteration:

```java
if (depth > 1 && shouldSkipIteration(depth)) {
    break;
}
```

The decision is "skip" when all of the following hold:

- the depth has at least `MIN_SAMPLES_FOR_SKIP` samples in the window (avoids reacting to a single early outlier),
- `getEstimatedMs(depth) > remainingMs`,
- `isProbingDue(depth, …)` returns false (see below).

When the heuristic fires, the previous-iteration `bestmove` is returned immediately and the recovered time stays on the clock — feeding the next move's budget in clock-based time controls.

**Probing.** To keep the SMA from freezing after one expensive sample (e.g. a depth-10 abort recorded as "expensive forever"), a probing override fires after `SKIPS_BETWEEN_PROBES` consecutive skips of the same depth: the iteration runs anyway, refreshing the window with current data. The probe is gated by `MIN_PROBE_REMAINING_RATIO` so it never starts with so little time left that it would just abort and pollute the SMA with a meaningless under-sampled result — instead the skip counter keeps growing and the probe fires the next time enough time is actually available.

**Aborted iterations** are recorded as `max(currentEstimate, elapsedMs × ABORT_EXTRAPOLATION_FACTOR)`. An abort proves only that the iteration would have cost *at least* `elapsedMs`, never that it would have been cheaper than the prior average, so the floor prevents the SMA from being pulled down by a probe that aborted too early. Only `recordCompletion` — a real, ground-truth measurement — can lower the SMA.

**Tuning knobs** live in [`EngineTuning`](../src/main/java/org/michaelfl/mychess/engines/EngineTuning.java) so they can be swept independently:

| Knob | Default | Purpose |
|---|---|---|
| `SMA_WINDOW_SIZE` | 5 | Number of recent samples per depth |
| `MIN_SAMPLES_FOR_SKIP` | 2 | Samples required before the heuristic fires |
| `ABORT_EXTRAPOLATION_FACTOR` | 1.2 | Multiplier for the elapsed-at-abort floor |
| `SKIPS_BETWEEN_PROBES` | 5 | Consecutive skips before a probe forces a run |
| `MIN_PROBE_REMAINING_RATIO` | 0.7 | Min `remaining / estimate` for the probe to fire |

**Lifetime.** State is process-static so it survives the per-`go` engine instances, and reset on `ucinewgame` via `ChessEngine.resetIterationTimings()` — a new game shouldn't inherit timing stats from a different position-complexity profile.

This is a deliberately small subset of the broader work outlined in [roadmap § 12.12](roadmap.md#1212-real-time-management-heuristics--s--m--3060-elo): it addresses the single most visible waste (the always-aborted top iteration) without yet introducing soft/hard split budgets, panic mode, complexity scaling, or multi-phase awareness.

## 6.6 Checkmate and stalemate scoring

When `alphaBetaSearchI` runs out of legal moves to try (every candidate returned `ILLEGAL_WEIGHT_NEG`), it has reached a terminal position — either checkmate (the side to move is in check and has no legal moves) or stalemate (the side to move is not in check but has no legal moves). The branch is `checkmateOrStalemate(ctx)`:

```java
private SearchNodeResult checkmateOrStalemate(SearchNodeContext ctx) {
    var alpha = ctx.alphaWeight();
    if (alpha >= 0f) {
        return SearchNodeResult.create(GameResult.ONGOING, alpha);
    }
    return ctx.workingBoard.isKingChecked(moveGenerator) ?
            SearchNodeResult.checkmateSelf(ctx.depth(), alpha, ctx.betaWeight()) :
            SearchNodeResult.stalemate(ctx.depth(), alpha, ctx.betaWeight());
}
```

**The `alpha >= 0` short-circuit** is a small but subtle optimization. The expensive part here is `isKingChecked`, which generates the opponent's moves (see [§ 4.5](move-generation.md#45-pseudo-legal-moves-and-king-capture-detection)). The branch skips that call when the parent has already established alpha ≥ 0, on the reasoning that the parent has already found *some* line scoring ≥ 0 and won't choose this branch regardless of whether it ends in mate (very negative) or stalemate (zero). Returning alpha as a placeholder is conservative — it doesn't claim a better score than already-known.

**Checkmate score.** The mated side's score is a large negative number whose magnitude encodes "mate in how many plies":

```java
public static SearchNodeResult checkmateSelf(int depth, int alpha, int beta) {
    return new SearchNodeResult(GameResult.CHECKMATE,
            window(-WeightingFunction.checkmateInCenti(depth), alpha, beta), false);
}

public static int checkmateInCenti(int depth) {
    return WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth * 100;
}
```

So at depth `d`, `checkmateSelf` returns `−(200_000 − d×100)` = `d×100 − 200_000`.

| Depth at which mate is found | `checkmateInCenti(d)` | Returned score |
|---|---|---|
| 1 | 199 900 | −199 900 |
| 2 | 199 800 | −199 800 |
| 3 | 199 700 | −199 700 |
| 5 | 199 500 | −199 500 |
| 10 | 199 000 | −199 000 |

After being `.negate()`d on the way back up to the parent (which is the *mating* side), these become `+199_900`, `+199_800`, … — **shallower mates score higher**, so the search naturally prefers mate-in-2 over mate-in-5.

The score range `[CHECKMATE_WEIGHT_LOW, CHECKMATE_WEIGHT_HIGH] = [100_000, 200_000]` is reserved for mate scores. `WeightingFunction.isCheckmateWeight(w)` recognizes them, and `checkmateWeightToPlies(w)` recovers the depth:

```java
public static int checkmateWeightToPlies(int weightCenti) {
    final int w = Math.abs(weightCenti);
    return (CHECKMATE_WEIGHT_HIGH - w) / 100;
}
```

…which `ChessUtil.weightToString` uses to print mate scores as `M3` (white mates in 3) or `-M5` (white gets mated in 5) instead of as raw integers.

**Stalemate score.** Stalemate is a draw, so the score is exactly 0:

```java
public static SearchNodeResult stalemate() {
    return new SearchNodeResult(GameResult.STALEMATE, 0, false);
}
```

**Fail-soft at terminal nodes.** Terminal-node factories (`checkmateSelf`, `stalemate`, `draw`) return the true score without any α/β clamping. This is consistent with the [main-loop fail-soft behavior](#61-negamax--alpha-beta-foundation): the value is allowed to escape the parent's window, and the caller (or the [transposition table](#79-transposition-table)) records a sharper bound. Conversion from a previous fail-hard `window(weight, α, β)` helper happened as part of [roadmap § 12.13](roadmap.md#1213-switch-alpha-beta-from-fail-hard-to-fail-soft--s-no-direct-elo-enables-aspiration--tt-tightening).

**Where does the search realize the game is over at the *root*, not at an interior node?** Two places intercept this *before* the search ever runs:

1. `ChessEngine.calculateNextMove` short-circuits when `game.getResult() != ONGOING`, returning a synthesized `MoveAndWeight` with `move = 0` and the appropriate game-over score (`−CHECKMATE_WEIGHT_HIGH` for mate, `0` for stalemate/draw).
2. The same method also short-circuits on the 50-move rule and threefold repetition, returning `(0, 0, DRAW)`.

The search proper only sees terminal positions that arise **mid-search**, never as the starting position. That terminal detection is what `checkmateOrStalemate` and the `Moves.ILLEGAL` machinery are for.

# 7. Search Optimizations

This chapter collects the techniques that make the basic alpha-beta from [Chapter 6](#6-search-algorithm) actually fast. They fall in three groups:

- **Move ordering** (§§ 7.1, 7.2, 7.8) — reach a beta cutoff sooner by trying likely-best moves first.
- **Shortcut evaluation** (§§ 7.3, 7.7) — skip work that won't change the answer.
- **Allocation discipline** (§§ 7.4, 7.5) — keep the hot loop GC-free.

Plus the diagnostics (§ 7.6) that make all of the above measurable.

## 7.1 Best-known-move (PV) ordering

The single most effective optimization. After every iteration of iterative deepening, the principal variation found at depth `k` is passed into the depth `k+1` search as `bestKnownPath`. At each node, the move at the corresponding depth in that path is the *best-known move* and is forced to the front of the move list.

**Plumbing.** The path travels through three components:

1. **`PositionSearch.getMoveAtDepth(bestKnownPath, depth)`** returns the move at `bestKnownPath.path[depth]` (or 0 if unknown). Called at every node in `alphaBetaSearchI`.
2. **`MoveGenerator.calculateMoves(board, depth, knownBestMove)`** forwards `knownBestMove` to the sorter.
3. **`MoveSorterImpl.reset(... knownBestMove)`** stores it. `MoveSorterImpl.addMove(...)` then *short-circuits* on it:

   ```java
   public void addMove(final int move, ...) {
       if (move == knownBestMove) {
           return;                  // ← do not bucket; it's already accounted for
       }
       …
   }
   ```

4. **`MoveSorterImpl.getSortedMoves()`** prepends the known-best move when assembling the final list:

   ```java
   if (knownBestMove != 0) {
       movesArray.add(knownBestMove);
   }
   if (bestMoveCapturingLastPlayedOppositePiece != 0) { … }
   movesArray.addAll(bucketWinningCaptures.getMoves());
   …
   ```

**Invariant.** The first generated move at every node must be the requested `bestKnownMove` (if one was given and any moves were generated). The search asserts it:

```java
__assert(() -> !(countMoves > 0 && bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]),
         () -> "First move must be the best known move. Expected: " + new Move(bestKnownNextMove)
             + ", actual: " + new Move(plainMoves[0]) + ", depth: 0");
```

**Lifetime.** The `bestKnownPath` is *consumed* one node at a time: once a node has used its slot, it nulls out `bestKnownPath` for the rest of its subtree:

```java
var result = alphaBetaSearch(new SearchNodeContext(depth + 1, …, bestKnownPath, …)).negate();
…
bestKnownPath = null;
```

This is because the PV is only meaningful for the *first* sibling tried at each node — once we recurse and that child has explored its own subtree, the PV move at deeper plies no longer corresponds to what we're searching. Killing `bestKnownPath` after the first sibling means subsequent siblings (and the grandchildren they spawn) order their moves with the fallback heuristics (captures + killers + PST) instead.

**Effect.** This is what makes iterative deepening cheap relative to a one-shot depth-N search. At a depth-N search of an "average" middlegame position, the unordered branching factor is around 35; with PV-first ordering plus the rest of `MoveSorterImpl`, the effective branching factor seen by alpha-beta typically drops into the low single digits, so depth N+1 costs only a few times more than depth N rather than 35× more.

## 7.2 Killer moves

[`KillerMoves`](src/main/java/org/michaelfl/mychess/KillerMoves.java) stores, per search depth, the two most recent **quiet** moves that caused a beta cutoff at that depth in a *sibling* node:

```java
public final class KillerMoves {
    private final int[][] moves = new int[50][2];     // 50 depths × 2 slots

    public boolean isKillerMove(int move, int depth) {
        final var m = moves[depth];
        return m[0] == move || m[1] == move;
    }

    public void addMove(int move, int depth) {
        final var m = moves[depth];
        if (m[0] != move) {
            m[1] = m[0];                              // shift old killer to slot 1
            m[0] = move;                              // new killer takes slot 0
        }
    }
}
```

**The heuristic.** At a given ply depth, sibling positions often share tactical themes — if move *X* refuted line A at depth `d`, the same move *X* often refutes line B at depth `d`. The pattern is especially strong for quiet positional moves (a knight outpost, a key blockade square) that recur across many candidate lines.

**Population.** Inside `alphaBetaSearchI`, when a move causes a beta cutoff:

```java
if (weight >= ctx.betaWeight) {
    statistics.incrPrunedMovesCount(countMoves - i - 1);
    ctx.copyUpPV();
    if (Move.getCapturedPiece(move) == 0) {           // ← quiet moves only
        killerMoves.addMove(move, depth);
    }
    return SearchNodeResult.create(result.result, ctx.betaWeight);
}
```

Captures are *not* registered as killers because they already get ordered high by the captures-bucket logic in `MoveSorterImpl`; the killer slot is most valuable for non-capture moves that would otherwise sit in the low-priority "remaining moves" bucket.

**Use.** `MoveSorterImpl.addMove` consults the table:

```java
if (killerMoves.isKillerMove(move, depth)) {
    bucketKillerMoves.add(move);
}
```

The killers bucket lands between winning captures and other captures in the final output order (see [§ 7.8](#78-move-sorting-sortablemovesbucket)). Its contents are not internally sorted — only two entries exist per depth, so ordering them is moot.

**Lifetime.** A fresh `KillerMoves` table is constructed inside each `PositionSearch`:

```java
// PositionSearch
private final KillerMoves killerMoves = new KillerMoves();
```

Because `ChessEngine.calculateNextMoveSub` builds a new `PositionSearch` on every call, the killer table is reset between successive engine moves. It persists only across the iterative-deepening iterations *within* one move calculation, where the same depth values are revisited and killers from earlier (shallower) iterations stay useful for the deeper ones.

**An honest TODO.** The code-base carries a comment in `MoveSorterImpl.getSortedMoves`:

```java
movesArray.addAll(bucketKillerMoves);   // TODO Killer moves seem to increase calculation time!?
```

The killer heuristic is widely beneficial in the chess-engine literature, but the author's measurements suggested marginal-or-negative impact in *this* engine, possibly due to interaction with the existing capture/PST ordering or because the per-depth table is too small. The optimization is kept on by default; the comment documents the open question.

## 7.3 Material-only evaluation shortcut

The static evaluator from [Chapter 5](evaluation.md) walks every own piece's pseudo-moves to compute mobility, threats, and checks. This is by far the most expensive single operation in the search — for many positions it costs more than the alpha-beta machinery itself.

**The shortcut.** If material has already swung wildly during the current search, return material weight alone and skip the positional pass:

```java
public static final int EVALUATE_MATERIAL_ONLY_THRESHOLD = 200;   // 200 centipawns = 2 pawns

private int calculatePositionWeight(final Board workingBoard, final int weightFactor,
                                    final int materialWeight, final int materialDelta) {
    if (materialDelta > EVALUATE_MATERIAL_ONLY_THRESHOLD
     || materialDelta < -EVALUATE_MATERIAL_ONLY_THRESHOLD) {
        return materialWeight;
    }
    return weightingFunction.calculate(workingBoard) * weightFactor;
}
```

`materialDelta` is the **cumulative material change** since the root of the *current* iteration: how much material has been won or lost by the side to move over the path that led to this leaf. If that delta is more than ±2 pawns in either direction, the leaf score is dominated by material and the few centipawns of positional refinement won't change the alpha-beta decision.

**Bookkeeping.** Both `materialWeight` (signed, from white's perspective adjusted to side-to-move) and `materialDelta` (signed, cumulative) are threaded through the search context and updated incrementally on each move:

```java
final int moveWeight        = WeightingFunction.getMaterialWeightOfMove(move, depth);
final int newMaterialWeight = materialWeight + moveWeight;     // total material score
final int newMaterialDelta  = materialDelta  + moveWeight;     // running swing from root
```

`getMaterialWeightOfMove(move, depth)` returns the centipawn delta this move causes — `+weightOfPiece[captured]` for captures, plus `weightOfQueen - weightOfPawn` for promotions, zero for quiet moves. Cost: one packed-int decode plus an array lookup.

At the root, `materialWeight` is seeded by one full board scan:

```java
final int materialWeight = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);
```

…and `materialDelta` starts at zero. From there, the entire search maintains both values without ever re-scanning the board.

**Quiescence honors the shortcut too.** `QuiescenceSearch.calculatePositionWeight` is byte-for-byte the same check:

```java
private int calculatePositionWeight(...) {
    if (materialDelta > PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD
     || materialDelta < -PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD) {
        return materialWeight;
    }
    return weightingFunction.calculate(workingBoard) * weightFactor;
}
```

The threshold reference is shared (same constant) so any tuning is uniform.

**Why ±2 pawns?** Below 2 pawns of material swing, the positional components (~half a pawn at most for piece-square, similar magnitudes for mobility / castling / threats) can plausibly flip the sign of the leaf evaluation. Above 2 pawns, they cannot — positional adjustments do not flip a "white is two pawns up" into "black is better" or vice versa. The threshold is a heuristic; lowering it would speed up the search at the cost of weaker positional play.

## 7.4 Make / undo on a single board

The search uses **exactly one `Board` instance** for the entire search tree of one iteration. The instance is cloned once at the root of each iteration:

```java
final Board workingBoard = game.getBoard().copy();
```

From there, every recursion step is **make / recurse / undo**:

```java
ctx.workingBoard.makeMove(move);
var result = alphaBetaSearch(...).negate();
ctx.workingBoard.revertMove();
```

The board is mutated in place. There is no copy-on-make, no per-node board allocation, no per-node `GameStatus` deep copy. After `revertMove()` returns, the board's piece array and status stack are byte-for-byte identical to what they were before `makeMove`.

**What `revertMove` actually does:**

```java
public void revertMove() {
    if (stackSize <= 1)
        throw new IllegalStateException("No move to revert");

    int move = getGameStatus().getLastMove();
    MOVE_REVERT_FUNCTIONS[Move.getMoveType(move)].revert(this, move);
    pop();
}
```

Two cheap operations:

1. Dispatch on `moveType` and call the per-type revert function. For a normal move:

   ```java
   private void _revertNormalMove(int move) {
       final byte fromField = Move.getFromField(move);
       final byte toField   = Move.getToField(move);
       board[fromField] = board[toField];                          // piece back to source
       board[toField]   = Move.getCapturedPiece(move);             // captured piece restored
   }
   ```

   Two array writes. No table lookups, no allocations. The captured piece comes from the move encoding itself — see [§ 3.3](data-types.md#33-move-encoding-packed-int) for why it lives there.

2. `pop()` decrements `stackSize` and nulls the popped slot. The previous `GameStatus` (with its old castling rights, position hash, half-move clock, en-passant field) becomes current again.

**The contrast.** A copy-on-make search clones the board on every move attempt. At ~200,000 nodes per second and a 144-byte mailbox plus a ~80-byte `GameStatus`, that's ~45 MB/s of allocation churn — heavy GC pressure, frequent young-generation collections, and cache thrashing. Make / undo allocates zero per node and stays cache-hot.

**The risk.** If `revertMove` is ever skipped — e.g. an exception unwinds the search without restoring the board — the whole search tree is corrupted. The code guards this in two places:

- `Game.makeMove(MoveDescription)` wraps the make-verify-then-set-result sequence in try/catch and calls `revertMove` on every failure path.
- `Board.revertMove` itself fails fast with `IllegalStateException("No move to revert")` if the stack is exhausted — catching imbalanced make/undo pairs early rather than silently corrupting state.

The search itself never throws mid-move (the only checked exception in the path is `CancellationException`, which is thrown *before* `makeMove`).

## 7.5 Packed-int move representation

[Section 3.3](data-types.md#33-move-encoding-packed-int) describes the format; this section is about its impact on search throughput.

A `Moves` produced by `MoveGenerator.calculateMoves(...)` is at heart an `int[]` plus a length counter. Generating moves for one node:

- allocates **no** wrapper objects per move,
- writes 32-bit `int`s into a pre-grown buffer (`IntArray` starts at capacity 30, accommodates a typical chess position without growing),
- and is consumed via direct array indexing (`Moves.getMoves()` returns the backing `int[]`).

The numbers add up. Say the search visits 200,000 nodes per second, and each node generates ~35 moves on average. That's 7,000,000 moves per second. With a `record Move(byte, byte, byte, byte)` representation, each move would be a 24-byte object header plus 4 bytes of payload — 168 MB/s of allocation. With the packed int, it's 4 bytes × 7M = 28 MB/s of `int` writes into reused buffers, with zero GC pressure.

**Specific hot-path consumers** that decode the int directly via `Move.getFromField(int)`, `Move.getToField(int)`, `Move.getCapturedPiece(int)`, `Move.getMoveType(int)` — no wrapper construction:

- `MoveGenerator.move(byte, int, int)` and the per-piece variants.
- `Board.makeMove(int)`, `_makeNormalMove`, `_makeCastlingKingSideMove`, … and their revert counterparts.
- `MoveSorterImpl.addMove(int, int, int, byte, byte)`.
- `KillerMoves.isKillerMove(int, int)` and `addMove(int, int)`.
- `SortableMovesBucket.add(int, int)`.
- `WeightingFunction.getMaterialWeightOfMove(int, int)`.
- `PositionSearch.alphaBetaSearchI` itself (entire move loop).
- `QuiescenceSearch.quiescenceSearch` (entire capture loop).

The `Move` *wrapper class* exists only for boundary uses: printing in error messages, equality in tests, and `MoveDescription` construction. It is never instantiated in the search loop.

## 7.6 Beta cutoff and pruning statistics

The search records two principal counters via [`Statistics`](src/main/java/org/michaelfl/mychess/Statistics.java):

```java
public final class Statistics {
    private long positionsCount;       // every node visited (main + quiescence)
    private long prunedMovesCount;     // moves never tried because of beta cutoff
    private int  maximumReachedDepth;
    private long quiescencePositionsCountTotal;
    private long quiescencePositionsCountCurrent;
    private long quiescencePositionsCountMax;
    private long quiescenceSearchesCount;
    …
}
```

**`positionsCount`** is incremented on every `alphaBetaSearchI` and every `quiescenceSearch` entry. It is also what `isTimeout()` (see [§ 6.5](#65-time-management-and-cancellation)) uses to throttle clock checks to once every 10,000 nodes.

**`prunedMovesCount`** is incremented by the *remaining* sibling count whenever a beta cutoff fires:

```java
if (weight >= ctx.betaWeight) {
    statistics.incrPrunedMovesCount(countMoves - i - 1);    // ← sibling moves we skipped
    …
}
```

If a node had 30 moves and cutoff fired on move 3 (index 2), 27 moves are credited as pruned. This is *exactly* the search work that alpha-beta saves over a pure minimax.

**Pruning ratio** is therefore `prunedMovesCount / (prunedMovesCount + positionsCount × averageBranchingFactor)`. In a well-ordered search of a typical middlegame position, this ratio sits around 80–95% — most of the candidate moves the engine *could* have explored are pruned by alpha-beta plus the move-ordering heuristics. The log line printed at the end of each iteration is:

```
#positions: 487 213, #pruned: 4 102 558
```

…which makes the effective branching factor easy to read off: the search visited ~487 k positions but skipped over 4.1 M sibling moves it would have had to look at without alpha-beta. That's a ~9× effective speedup for this single iteration on top of the raw alpha-beta gains.

**Quiescence statistics** are tracked separately so it's possible to see how much of the total node count is "real" alpha-beta nodes vs capture-chain extensions:

- `quiescencePositionsCountTotal` — cumulative quiescence node visits.
- `quiescencePositionsCountCurrent` — within the current quiescence call (reset on entry, accumulated on exit).
- `quiescencePositionsCountMax` — longest single quiescence chain seen.
- `quiescenceSearchesCount` — number of times quiescence has been entered.
- `getQuiescencePositionsCountAvg()` — average chain length.

The log line for these is commented out in the production search loop but available for diagnostic enabling:

```java
//log("quiescence: total=" + … + ", avg=" + … + ", max=" + … + ", max depth: " + …);
```

**`maximumReachedDepth`** is updated by `QuiescenceSearch.reachedDepth(depth)`, tracking how deep the quiescence extension went in absolute search terms (main depth + quiescence offset). Useful for sanity-checking that the `MAX_QUIESCENCE_SEARCH_DEPTH = 20` cap is not regularly being hit.

## 7.7 Opening-book lookup

The single biggest "shortcut" in the entire engine is **not searching at all** for known opening positions. Inside `ChessEngine.calculateNextMove`:

```java
} else if (openingDB != null) {
    var m = getMoveFromOpeningDB(openingDB);
    if (m != null) {
        move = new MoveAndWeight(m.getMove(), 0, GameResult.ONGOING, new int[] { move.move });
    }
}

if (move == MoveAndWeight.NO_MOVE) {
    move = calculateNextMoveSub(task);                  // ← only runs if no book hit
}
```

If the book hits, the search is skipped entirely. The engine plays the book move with `weight = 0` (no static evaluation done) and a one-entry path containing only the chosen move.

**Lookup.** The key is the FEN-prefix `calculatePositionKey()` of the current board (board state + turn + castling — see [§ 3.8](data-types.md#38-zobrist-hashing-and-positionencoding) and [§ 9.2](opening-database.md#92-lookup-policy)). The DB returns a `PositionInfo` with a list of `MoveInfo` entries, each carrying the move and aggregate `winCount` / `drawCount` / `lossCount` from the import-time PGN database.

**Filter.** Not every book entry is usable. The engine applies three filters:

```java
var candidates = positionInfo.moves.stream()
    .filter(m -> m.getTotalCount() >= 100              // popularity: ≥ 100 games
              && m.getWinPercentage() >= 20            // not too lossy from this side's view
              && m.getLossPercentage() < 45)           // not catastrophic
    .collect(Collectors.toList());
if (candidates.isEmpty()) {
    return null;                                       // fall through to search
}
```

| Filter | Threshold | Rationale |
|---|---|---|
| Total game count | ≥ 100 | Avoid statistically unreliable lines from a handful of games. |
| Win % | ≥ 20 | Reject moves that almost never win. |
| Loss % | < 45 | Reject moves that lose nearly half the time. |

**Selection.** When multiple candidates pass the filter, one is picked by **weighted random sampling** proportional to game count:

```java
int sum = candidates.stream().mapToInt(MoveInfo::getTotalCount).sum();
int n = getRandom().nextInt(sum);

int i = 0;
for (var m : candidates) {
    i += m.getTotalCount();
    if (n < i) {
        return m.move;
    }
}
```

A popular line is more likely to be chosen than a rare one, but the engine doesn't always play the *most* popular move — adding move variety so it doesn't replay identical openings game after game. Reproducibility seekers can replace the `Random` with a fixed-seed one via the engine.

**Win/loss perspective.** Note: `getWinPercentage()` is from *white's* perspective in the underlying DB. When black is to move, the engine should actually be filtering on the opponent's win-percentage. Whether the current implementation correctly mirrors the percentages for black-to-move queries is worth checking — there is no explicit swap in `getMoveFromOpeningDB`, so the filter behaves slightly asymmetrically for black. (Marked here as a known modeling gap, not a bug fix.)

## 7.8 Move sorting (`SortableMovesBucket`)

The full ordering policy lives in [`MoveSorterImpl`](src/main/java/org/michaelfl/mychess/engines/MoveSorterImpl.java). Every generated move is routed into one of six **buckets** plus two singleton slots, and the final move list is concatenated bucket-by-bucket in a fixed order.

**The buckets:**

```java
private final MovesArray         bucketKillerMoves    = new MovesArray();
private       int                bestMoveCapturingLastPlayedOppositePiece;
private       float              bestWeightCapturingLastPlayedOppositePiece;
private final SortableMovesBucket bucketWinningCaptures = new SortableMovesBucket();
private final SortableMovesBucket bucketOtherCaptures   = new SortableMovesBucket();
private final SortableMovesBucket bucketRemainingMoves  = new SortableMovesBucket();
private final MovesArray         bucketKingMoves       = new MovesArray();
```

Plus two hidden front-of-list slots: the **`pvMove`** (previous iteration's principal-variation move at this depth, see [§ 7.1](#71-best-known-move-pv-ordering)) and the **`ttMove`** (best move from a transposition-table lookup at this position, see [§ 7.9](#79-transposition-table)).

**The dispatch** in `addMove(move, fromField, toField, movingPiece, capturedPiece)`:

```java
if (move == pvMove) {
    pvMoveSeen = true;
    return;                                            // already accounted for at output time
}
if (move == ttMove) {
    ttMoveSeen = true;
    return;                                            // already accounted for at output time
}
if (killerMoves.isKillerMove(move, depth)) {
    bucketKillerMoves.add(move);
} else if (capturedPiece != 0) {
    final float deltaWeight = WeightingFunction.weightOfPiece[capturedPiece]
                            - WeightingFunction.weightOfPiece[movingPiece];
    if (toField == targetFieldOfLastOppositeMove
        && deltaWeight > bestWeightCapturingLastPlayedOppositePiece) {
        // promote/replace the "best immediate recapture" singleton
        if (bestMoveCapturingLastPlayedOppositePiece != 0) {
            getCapturesBucket(deltaWeight).add(bestMoveCapturingLastPlayedOppositePiece,
                                              (int) bestWeightCapturingLastPlayedOppositePiece);
        }
        bestMoveCapturingLastPlayedOppositePiece = move;
        bestWeightCapturingLastPlayedOppositePiece = deltaWeight;
    } else {
        getCapturesBucket(deltaWeight).add(move, (int) deltaWeight);
    }
} else if (Board.isKing(movingPiece)) {
    bucketKingMoves.add(move);
} else {
    final int srcWeight  = PieceSquareTables.getPieceSquareWeight(movingPiece, fromField);
    final int destWeight = PieceSquareTables.getPieceSquareWeight(movingPiece, toField);
    final int weight     = destWeight - srcWeight;
    bucketRemainingMoves.add(move, weight);
}
```

`getCapturesBucket(deltaWeight)` chooses between `bucketWinningCaptures` and `bucketOtherCaptures` based on the sign of `deltaWeight`:

```java
private SortableMovesBucket getCapturesBucket(float deltaWeight) {
    return deltaWeight > 0 ? bucketWinningCaptures : bucketOtherCaptures;
}
```

**The output order** in `getSortedMoves()`:

```java
bucketWinningCaptures.sort();
bucketOtherCaptures.sort();
bucketRemainingMoves.sort();

if (pvMove != 0 && pvMoveSeen)                           movesArray.add(pvMove);   // else: log + skip
if (ttMove != 0 && ttMove != pvMove && ttMoveSeen)       movesArray.add(ttMove);   // else: log + skip
if (bestMoveCapturingLastPlayedOppositePiece != 0)       movesArray.add(bestMoveCapturingLastPlayedOppositePiece);
movesArray.addAll(bucketWinningCaptures.getMoves());
movesArray.addAll(bucketKillerMoves);                    // TODO: see § 7.2
movesArray.addAll(bucketOtherCaptures.getMoves());
movesArray.addAll(bucketRemainingMoves.getMoves());
movesArray.addAll(bucketKingMoves);                      // TODO: change in endgame
```

The `pvMoveSeen` / `ttMoveSeen` flags guard against stale hints: a `pvMove` or `ttMove` is only added to the output when the move generator actually reported it via `addMove`. Unseen hints are dropped with a `Log.info` diagnostic instead — see [§ 7.9](#79-transposition-table)'s "Move-ordering integration" subsection for the rationale and the failure modes the flags protect against.

Final order at every node:

| # | Bucket | Sorted by | Rationale |
|---|---|---|---|
| 1 | `pvMove` (previous iteration's PV) | (one entry) | Most likely best by previous iteration. |
| 2 | `ttMove` (TT lookup's best move) | (one entry) | Likely best by a previous visit to this position via a different move order. Distinct from `pvMove` because the TT can return a hint from a transposed subtree, not just the on-PV continuation. |
| 3 | Best immediate recapture of opponent's last-moved piece | (one entry) | Captures-resolve-first: if they just played X to attack/defend, our best response that recaptures on their landing square is usually critical. |
| 4 | Winning captures | descending by `value(captured) − value(moving)` | MVV-LVA-like: capturing a queen with a pawn (+8) > capturing a pawn with a queen (-8). |
| 5 | Killer moves (this depth) | (insertion order, two entries max) | Quiet moves that have caused cutoffs at sibling depths. |
| 6 | Other captures (non-winning) | descending by same delta | Trades that lose material may still defend or attack. |
| 7 | Other quiet moves | descending by PST delta (`destWeight − srcWeight`) | Moves that improve a piece's square (e.g. centralize a knight) before moves that worsen it. |
| 8 | King moves | (insertion order) | Usually defensive / forced; trying them last reduces wasted exploration. |

**Why `ttMove` sits between `pvMove` and the recapture slot.** Conceptually both `pvMove` and `ttMove` are "the previous best move at this position", so they share the same priority tier — try them before any heuristic-classified bucket. They are listed in two slots rather than one because they can disagree: `pvMove` is the move on the iteration's principal path (only meaningful at PV-following nodes), while `ttMove` is whatever the last visit to this exact position stored, which is often a different transposition that reaches the same position from a different ancestor. When the search is still inside the PV, the two usually agree and the duplicate-guard (`ttMove != pvMove`) skips the second add; once the search branches off the PV, only `ttMove` carries forward, and it does so for every transposed visit until a deeper iteration overwrites the entry.

**Why the "recapture-of-last-moved-piece" slot comes right after the `pvMove`/`ttMove` pair** — this is a small but high-impact special case. When the opponent's last move targeted a particular square (a capture, a check, an attack on our queen), our most urgent response is typically to deal with that piece. Trying it first (after exhausting the previous-search hints) means alpha-beta gets a cutoff candidate immediately when one exists. It is checked by:

```java
toField == targetFieldOfLastOppositeMove
```

where `targetFieldOfLastOppositeMove` is set on `reset()` from `Move.getToField(gameStatus.getLastMove())`.

The slot only holds the *best* such recapture (highest `deltaWeight`); subsequent same-square recaptures get demoted to the regular captures bucket.

**Why king moves are last.** Moving the king out of its safe home is usually a positional concession — either forced (king in check) or in the endgame. In the middlegame, king moves rarely win material or improve position. Putting them last means alpha-beta searches the more impactful pieces first; if a king move is actually best, the iteration still finds it, just later (and probably with little speed cost because PV-first ordering will have it cached for the next iteration). The `TODO` in the code notes this assumption breaks in endgames where the king is an active piece — a more sophisticated version would key off `gameStatus.isEndGame()`.

**Why insertion sort for the buckets.** `SortableMovesBucket.sort()` uses insertion sort with fast paths for `n=1` and `n=2`. Move-list buckets are small (rarely > 15 entries), and insertion sort wins for small `n`. Special-casing the trivial sizes avoids any sort overhead on the common path. Details on the bucket data structure are in [§ 3.7](data-types.md#37-sortablemovesbucket).

## 7.9 Transposition table

The transposition table (TT) caches per-position search results keyed by Zobrist hash. Positions reached through different move orders are searched only once: subsequent visits read the cached score (when the stored depth is at least as deep as the new search) and use the stored best move as a move-ordering hint at any depth. Implementation: [`TranspositionTable`](../src/main/java/org/michaelfl/mychess/TranspositionTable.java), wired through [`EngineConfig.getTranspositionTable()`](../src/main/java/org/michaelfl/mychess/EngineConfig.java).

### Storage layout

Bucketed hash table backed by one off-heap `MemorySegment` (rewritten in v4.0.3; previous v4.0.2 layout was an in-heap `TTEntry[]`). The table owns `size` 24-byte records, logically split into `size / BUCKET_SIZE` fixed-size buckets of `BUCKET_SIZE = 4` slots each. Both `size` and `size / BUCKET_SIZE` are powers of two, so the bucket-selection index is a low-bit mask of the Zobrist hash:

```java
private int hash(final long hashKey) {
    return ((int) hashKey & (hashSize - 1)) * BUCKET_SIZE;
}
```

`hash(key)` returns the flat byte offset of the bucket's first slot record; the bucket then spans records `[hash(key), hash(key) + BUCKET_SIZE)`. Each record is 24 bytes: one `long` (hashKey) followed by four `int` values (depth, score, bound ordinal, bestMove). `TTEntryView` is a reused view that points at one record in the segment; callers must read its values before the next `get()` or `put()` repositions the view.

Each record carries five fields:

| Field | Meaning |
|---|---|
| `hashKey` | Full 64-bit Zobrist key. Also doubles as the occupancy marker: a freshly cleared slot has `hashKey == 0`. |
| `depth` | `remainingDepth` (= `maxDepth - currentDepth`) at which this entry was searched. NOT the distance from the root. |
| `score` | Centipawn score relative to the cached position. Mate scores are stored as "mate-in-N plies from here". |
| `bound` | One of `EXACT` / `LOWER` / `UPPER` — see "Bound semantics" below. |
| `bestMove` | Packed-int move that produced `score`. Used as a move-ordering hint even when the entry's depth is too shallow to return the score directly. |

The default singleton is `2^22` entries (~96 MB at 24 bytes per entry), raised from the original `2^20` in v4.0.1 after analysis showed that at TC 40/60 the smaller table got rewritten ~30-60× per game and lost much of its mid-depth signal to evictions; tests use isolated `2^14`-entry instances via `TestSupport.createTestTT()` (see "Lifecycle" below).

The v4.0.3 off-heap rewrite delivered **+15.6 ± 9.8 Elo** against the v4.0.2 in-heap layout (3200-game fixed-N match at TC 40/60, LOS 99.9 %). The gain is attributed to two combined effects: cache locality (a 4-slot bucket occupies ~96 bytes contiguous — about 1.5 cache lines — versus a `TTEntry[]` variant where each of the four referenced Java objects may reside on a separate cache line, worst case 5-8 cache-line loads per bucket scan) and reduced Serial-GC card-table work (the old generation no longer contains 4 M `TTEntry` reference cells that must be scanned on every minor collection).

### Lookup

The lookup happens in [`PositionSearch.alphaBetaSearchPre`](../src/main/java/org/michaelfl/mychess/engines/PositionSearch.java) before recursion into the move loop:

```java
final var ttEntryView = tt.get(gameStatus.getPositionHash());
if (ttEntryView != null && ttEntryView.getDepth() >= ctx.remainingDepth()) {
    final int score = scoreFromTT(ttEntryView.getScore(), ctx.depth());

    switch (ttEntryView.getBound()) {
        case EXACT -> { return ttResult(ctx, score, Bound.EXACT, ttEntryView.getBestMove()); }
        case LOWER -> alphaWeight = Math.max(alphaWeight, score);
        case UPPER -> betaWeight = Math.min(betaWeight, score);
    }
    if (alphaWeight >= betaWeight) {
        // LOWER/UPPER cutoff — return the entry's own bound, not EXACT
        return ttResult(ctx, score, ttEntryView.getBound(), ttEntryView.getBestMove());
    }
}
```

Two things to notice:

1. **`get()` scans the target bucket linearly.** The hash function only uses the low bits of the 64-bit Zobrist key, so many genuinely different positions can land in the same bucket. `get()` scans all `BUCKET_SIZE = 4` slots of the target bucket, repositioning the reused `TTEntryView` on each slot, and returns the view when its `getHashKey()` matches the argument exactly (full 64-bit identity check); if none match, it returns `null`. Up to four distinct keys can coexist in one bucket without evicting each other — only a bucket-full-of-different-keys forces a replacement decision on the next `put()`. True 64-bit Zobrist collisions (two genuinely different positions producing identical 64-bit keys) are astronomically rare (~1 in 10^19 per pair) and treated as ignorable.
2. **The depth gate (`ttEntryView.getDepth() >= ctx.remainingDepth()`) controls *the score*, not the bestMove.** Below the depth gate the score is ignored but the stored bestMove is still extracted and threaded into the move sorter as `ttMove` (see § 7.1 and "Move-ordering integration" below).

### Storage

After the recursive search returns, the result is stored:

```java
if (!result.isTimeout() && !result.isIllegal()) {
    int score = scoreToTT(result.weight(), ctx.depth());
    tt.put(gameStatus.getPositionHash(), ctx.remainingDepth(), score, result.bound(), result.bestMove());
}
```

The replacement policy in `put()` is **depth-preferred-EXACT** with a bucket-scan eviction pass:

```java
public void put(...) {
    int index = hash(hashKey);
    final int endIndex = index + BUCKET_SIZE;
    int replaceIndex = index;
    int replaceDepth, replaceBoundOrd;
    // scan the bucket, tracking the best eviction candidate;
    // TTEntryView is repositioned on each slot in turn
    for (; index < endIndex; index++) {
        currentEntryView.position(index);
        if (currentEntryView.getHashKey() == hashKey) {
            if (currentEntryView.getDepth() > depth
                    && currentEntryView.getBound() == Bound.EXACT) {
                return;                          // keep deeper exact entry
            }
            replaceIndex = index;                // same-key hit → overwrite in place
            break;
        }
        // eviction candidate: lowest depth, break ties by preferring
        // to evict a non-EXACT slot over an EXACT one of equal depth
        ...
    }
    // ... reposition the view on replaceIndex and write() the new fields ...
}
```

Two branches:

- **Same key in the bucket.** If the incumbent is a strictly deeper `EXACT` entry, the put is a no-op — the deeper cached result would be lost to a shallower re-visit. Otherwise the incumbent slot is overwritten in place with the new fields (`hashKey`, `depth`, `score`, `bound`, `bestMove`).
- **Key not in bucket.** The loop tracks a single eviction candidate as it scans: initially the first slot, then any subsequent slot whose stored `depth` is lower (or equal-depth-but-non-EXACT against an EXACT incumbent). At loop end, that candidate is overwritten. Effectively: evict the least-informative slot, with EXACT scores enjoying a small extra survival margin on ties.

This is the winner of an eight-variant investigation of bucket replacement policies (see [roadmap § 12.1 follow-up](roadmap.md#follow-up-tt-bucket-replacement-strategies--explored-depth-only-chosen)). More elaborate schemes (age / hit-count / two-tier lanes / admission control) were measured and did not measurably outperform this simplest depth-aware rule at TC 40/60.

### Bound semantics

The `Bound` field classifies what `score` means relative to the alpha-beta window at store time:

| Bound | Meaning | Stored after |
|---|---|---|
| `EXACT` | `score` is the position's exact value: `alpha < score < beta` held when stored. | A move-loop completion where some move beat alpha and none reached beta. |
| `LOWER` | `score` is a lower bound: the true value is at least `score`. | A beta cutoff: `weight >= beta` triggered an early return; further moves were pruned and might have been better still. |
| `UPPER` | `score` is an upper bound: the true value is at most `score`. | A "fail-low" exit where every legal move returned a score not exceeding alpha. |

The LOWER/UPPER bounds let the lookup narrow the next call's window without returning a final value: a stored LOWER `s` raises alpha to at least `s` (we know the true value is at least that), a stored UPPER `s` lowers beta to at most `s` (we know it cannot exceed that). When that narrowing produces `alpha >= beta`, the position is decided either way and the cached value is returned just as if it had been EXACT.

### Mate-score depth adjustment

Mate scores encode "mate in N plies" via a high-magnitude sentinel range (`CHECKMATE_WEIGHT_HIGH - plies * 100`, see [`WeightingFunction.checkmateInCenti`](../src/main/java/org/michaelfl/mychess/WeightingFunction.java)). A score returned from the search is *root-relative* — "mate at depth D in the current tree" — but the TT is keyed by position and the same mate, looked up from a different depth, refers to a different "mate at depth D′". Two helpers translate between the two frames:

```java
private int scoreToTT(int score, int depth) {
    if (WeightingFunction.isCheckmateWeight(score)) {
        int plies = WeightingFunction.checkmateWeightToPlies(score);
        int checkmateCenti = WeightingFunction.checkmateInCenti(plies - depth);
        return score >= 0 ? checkmateCenti : -checkmateCenti;
    }
    return score;
}

private int scoreFromTT(int score, int depth) {
    if (WeightingFunction.isCheckmateWeight(score)) {
        int plies = WeightingFunction.checkmateWeightToPlies(score);
        int checkmateCenti = WeightingFunction.checkmateInCenti(depth + plies);
        return score >= 0 ? checkmateCenti : -checkmateCenti;
    }
    return score;
}
```

On store: subtract the current depth so the stored value reads as "mate in `(plies - depth)` plies from this position". On read: add the current depth back so the returned value reads as "mate at depth `(depth + storedPlies)` of the current search tree".

**Sign preservation matters.** `isCheckmateWeight` and `checkmateWeightToPlies` both take the absolute value of the input — they accept positive (delivering mate) and negative (being mated) scores symmetrically. The `score >= 0 ? +ck : -ck` conditional preserves the sign through the round-trip. An earlier version of this code dropped the sign; the regression that surfaced this was `GameStatusTest.testWhiteCheckmate` flipping its expected mate move because a "we are mated" entry came back from the TT as "we are mating".

### PV-table interaction

A TT-cached early return short-circuits the recursion, which means the parent's PV-table row will *not* be filled in by a child's `copyUpPV` (see § 6.3). Without intervention, the parent's subsequent `copyUpPV()` would propagate whatever stale slots an earlier sibling's exploration left behind, and the iteration would emit a principal variation containing moves not legal at the positions it claims to reach.

The fix lives in `SearchNodeContext.writeTTCachedPv(int ttMove)`, called from both TT-cached return paths via the `ttResult` helper:

```java
public void writeTTCachedPv(int ttMove) {
    pvTable[pvIndex()] = ttMove;                                                // own row, diagonal slot
    Arrays.fill(pvTable, pvIndex() + 1, (depth + 1) * pvMaxLength(), 0);        // truncate own row past diagonal
    copyUpPV();                                                                 // propagate via the standard mechanism
}
```

The result, after the parent's normal `copyUpPV` chain runs, is a PV that reads `[..., parent's move, ttMove, 0, 0, ...]` — semantically "the TT says the best move at this depth is `ttMove` and we do not have a continuation beyond that". The full diagram is in the method's JavaDoc.

A known side effect: when a TT hit fires at depth `d`, the visible PV terminates at `d` — even though the search behind it was depth-`maxDepth`-deep. The played move and its score are correct; only the displayed continuation is shorter than the underlying search saw. Reconstructing the full PV by walking the TT (apply the stored bestMove, look up the resulting position, recurse) is a known follow-up — see [roadmap § 12.1](roadmap.md#121-transposition-table--done-93-elo).

### Move-ordering integration

When the TT lookup neither serves a final result nor cuts off the window, the entry's `bestMove` is still forwarded to the move generator as `ttMove` (alongside the previous iteration's PV move):

```java
final int bestMove = ttEntryView != null ? ttEntryView.getBestMove() : 0;
final SearchNodeResult result = alphaBetaSearchMain(ctx, alphaWeight, betaWeight, bestMove);
```

`MoveSorter.reset` accepts both `pvMove` and `ttMove`. The sorter emits them in that order at the front of the move list, before the recapture / winning-capture / killer / quiet-move buckets described in § 7.8.

**Protection against stale hints.** Either hint may be illegal at the current position — a TT bestMove from a Zobrist-collision-neighbor (vanishingly rare but possible), or a PV entry that does not survive a tree-shape change. Blindly prepending an illegal move to the sorter's output would crash inside `Board.makeMove`. `MoveSorterImpl` defends by tracking per-hint `pvMoveSeen` / `ttMoveSeen` flags:

```java
public void addMove(int move, ...) {
    if (move == pvMove) { pvMoveSeen = true; return; }
    if (move == ttMove) { ttMoveSeen = true; return; }
    // … normal bucketing
}

public Moves getSortedMoves() {
    …
    if (pvMove != 0) {
        if (pvMoveSeen) { movesArray.add(pvMove); }
        else            { Log.info("[sort] pvMove " + … + " not produced by MoveGenerator …"); }
    }
    if (ttMove != 0 && ttMove != pvMove) {
        if (ttMoveSeen) { movesArray.add(ttMove); }
        else            { Log.info("[sort] ttMove " + … + " not produced by MoveGenerator …"); }
    }
    …
}
```

A hint is added to the sorted output only when the move generator actually reports it via `addMove`. Unseen hints are dropped and logged; the search continues with the bucket-sorted moves as if the hint had never been set.

The seen-flags reset to `false` at the top of every `reset()` call so they cannot leak across nodes — `MoveSorterImpl` is reused for every search node, not allocated fresh.

### Lifecycle

- **Production / UCI:** `TranspositionTable.getDefaultInstance()` lazily creates a single `2^22`-entry off-heap instance the first time it is requested (raised from `2^20` in v4.0.1). `EngineConfig.Builder.build()` picks it up when no explicit instance was set, so the UCI handler and the REPL automatically share one TT across all moves of one process. This singleton is process-lifetime state and is normally not closed manually.
- **`ucinewgame` clears the table.** `UciHandler.handleNewGame()` calls `tt.clear()` so cached scores from a prior game (which may have been played with different time controls or against a different opponent) cannot influence the new game.
- **Explicit instances must be closed.** A non-singleton TT owns native memory through its `MemorySegment` / `Arena`; release it with try-with-resources or by closing a test field in `@AfterEach`.
- **Tests use isolated instances.** Every test that builds an `EngineConfig` wires its own TT via `TestSupport.createTestTT()` (default `2^14` entries). Test order would otherwise change search outcomes — entries from an earlier test could serve as move-ordering hints in a later one. The `MoveSorterImplTest.ttMoveSeenFlag_isResetBetweenInvocations` regression is the historical reminder of why this matters: with shared state, a sticky `ttMoveSeen` flag from a prior reset led to illegal moves entering the search loop.

### Limitations

- **Not thread-safe.** `put()` writes five fields of an entry non-atomically; a concurrent `get()` could observe a half-updated slot. The engine runs a single-threaded search executor, so this is not an issue today. A future Lazy SMP search would need to switch the entry to a packed-`long` representation with `volatile` reads/writes.
- **No "always-replace" tier.** A two-tier scheme (always-replace + depth-preferred) typically improves hit rate by 5–10% on tactical search. The current single-tier `put()` keeps the code one line shorter; the trade-off is worth re-measuring after the next round of search optimizations.
- **Shortened PV display** — see "PV-table interaction" above and [roadmap § 12.1](roadmap.md#121-transposition-table--done-93-elo).
