package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.TranspositionTable.Bound;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

import static org.michaelfl.mychess.Assert.*;

/**
 * Iterative-deepening negamax alpha-beta search with PV reuse, killer-move
 * heuristic and {@link QuiescenceSearch} extension. Cooperatively cancellable
 * via {@link NextMoveTask} and time-bounded by
 * {@link EngineConfig#getMillisPerMove()}.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("DuplicatedCode")
public final class PositionSearch {

    /**
     * If the player has gained/lost more than this threshold in material weight during the current search,
     * only material weight on the board is considered in the evaluation function.
     * Otherwise, the full evaluation of the position is done.
     */
    public static final int EVALUATE_MATERIAL_ONLY_THRESHOLD = 200;

    /**
     * Hard cap on the iterative-deepening target depth. UCI's
     * {@code go depth ...} accepts arbitrary integers and a missing
     * {@code depth} keyword defaults to {@code Integer.MAX_VALUE}; with no
     * cap the iteration loop has been observed to run to depth 10000+ on
     * pathological positions where every node early-returns (50-move /
     * threefold draws), consuming the full time budget on busy-loop work
     * and allocating {@code (maxDepth+1)^2} sized PV tables per iteration.
     * 64 is well beyond what myChess actually reaches in any practical
     * time control (typical ≈ 8–12 plies).
     */
    public static final int MAX_SEARCH_DEPTH = 64;

    /**
     * Per-node state for one invocation of
     * {@link PositionSearch#alphaBetaSearch(SearchNodeContext, int, int)}: current
     * search depth, alpha-beta window, side-to-move sign, running material
     * counters, the working board, and a reference to the shared
     * principal-variation (PV) table. Non-PV fields are straightforward;
     * the PV table has a triangular layout that this class encapsulates.
     *
     * <h2>PV table layout</h2>
     *
     * <p>{@code pvTable} is a single flat {@code int[]} of length
     * {@code pvMaxLength * pvMaxLength}, where {@code pvMaxLength =
     * maxDepth + 1}. It is logically a square matrix indexed by depth:
     * row {@code d} starts at index {@code d * pvMaxLength} and holds the
     * PV that a node at depth {@code d} is building. Only the upper
     * triangle is ever filled with moves — row {@code d}'s diagonal slot
     * is at column {@code d}, and slots left of the diagonal stay empty.
     *
     * <p>The example below shows a fully populated table for
     * {@code maxDepth = 4} (so {@code pvMaxLength = 5}, five columns,
     * five rows). Moves {@code M0 .. M3} are the four PV plies. The
     * rightmost column (column 4) is never written by anyone and so
     * always reads as {@code 0}; that final {@code 0} serves as a
     * zero-terminator for PV-consuming code that walks the PV until
     * it hits a {@code 0}. Row 4 corresponds to the leaf (depth
     * {@code == maxDepth}) — the leaf takes the static-eval shortcut
     * and never writes its own diagonal, so row 4 is empty too.
     *
     * <pre>{@code
     *   col          0    1    2    3    4
     *             +----+----+----+----+----+
     *   row 0     | M0 | M1 | M2 | M3 |  0 |   <- root PV: 4 moves + 0-terminator
     *             +----+----+----+----+----+
     *   row 1     |    | M1 | M2 | M3 |  0 |
     *             +----+----+----+----+----+
     *   row 2     |    |    | M2 | M3 |  0 |
     *             +----+----+----+----+----+
     *   row 3     |    |    |    | M3 |  0 |   <- only this depth's diagonal move
     *             +----+----+----+----+----+
     *   row 4     |    |    |    |    |  0 |   <- leaf row: never written
     *             +----+----+----+----+----+
     * }</pre>
     *
     * <p>The diagonal slot of row {@code d} — column {@code d}, addressed
     * by {@link #pvIndex()} — is the move currently being considered at
     * depth {@code d}. It is rewritten at the top of every move-loop
     * iteration in {@code alphaBetaSearchMain}. The slots
     * {@code d+1 .. maxDepth-1} to the right of the diagonal hold the
     * sub-PV continuing from that candidate; deeper nodes fill them via
     * {@link #copyUpPV()}. Slots to the left of the diagonal are written
     * by shallower nodes (or, for row 0, by the root's direct
     * {@code pvTable[0] = move}).
     *
     * <h2>PV propagation</h2>
     *
     * <p>Two operations move data between rows:
     *
     * <ul>
     *   <li>{@link #copyUpPV()} — "I found a good continuation": copies
     *       this depth's row slots {@code d .. maxDepth} (the diagonal
     *       move plus its sub-PV) into the parent's row slots
     *       {@code d .. maxDepth}. Called whenever a recursive child
     *       result improves the parent's best so far.</li>
     *   <li>{@link #truncateParentPv()} — "I have no continuation":
     *       writes zeros into the same parent-row range. Called at
     *       terminal returns (mate / stalemate / draw / leaf static
     *       eval). Without this, the parent's later {@code copyUpPV}
     *       would carry forward stale slots from an earlier sibling's
     *       deeper exploration.</li>
     * </ul>
     *
     * <p>By induction up the chain, row 0 ends up containing the full
     * principal variation {@code [M0, M1, ..., M_{maxDepth-1}]} when the
     * search returns to the root.
     *
     * <h2>Why the diagonal layout?</h2>
     *
     * <p>Storing each row from column 0 instead of column {@code d}
     * would also work. The diagonal layout chosen here has a useful
     * property: the source range of {@code copyUpPV} (row {@code d},
     * columns {@code d..maxDepth}) sits adjacent in memory to its
     * destination (row {@code d-1}, columns {@code d..maxDepth}) —
     * same column range, stride {@code pvMaxLength}. {@code copyUpPV}
     * is therefore a single {@link System#arraycopy} with no per-element
     * offset arithmetic.
     *
     * <h2>Non-PV fields</h2>
     *
     * <ul>
     *   <li>{@code depth} — current ply, starting at 1 for the first
     *       recursion below the root. The root's own loop in
     *       {@link PositionSearch#calculateNextMove(int, MoveAndWeight)}
     *       does not create a {@code SearchNodeContext}; it writes
     *       {@code pvTable[0]} directly.</li>
     *   <li>{@code maxDepth} — the iterative-deepening iteration's target
     *       depth. The leaf shortcut in {@code alphaBetaSearchMain} fires
     *       at {@code depth == maxDepth}.</li>
     *   <li>{@code bestKnownPath} — the previous iteration's PV, threaded
     *       through so each depth can take its best-known move from
     *       {@code bestKnownPath.path()[depth]} and pass it to
     *       {@code MoveGenerator} as the head of the move-ordering list.</li>
     *   <li>{@code weightFactor} — {@code +1} for white-to-move,
     *       {@code -1} for black; negated at each child recursion so the
     *       search runs in pure negamax form.</li>
     *   <li>{@code materialWeight} — cumulative material from the
     *       side-to-move's perspective. Used by the leaf shortcut when
     *       {@code materialDelta} exceeds
     *       {@link PositionSearch#EVALUATE_MATERIAL_ONLY_THRESHOLD}.</li>
     *   <li>{@code materialDelta} — running material change since the
     *       search root; gates the material-only leaf shortcut.</li>
     *   <li>{@code workingBoard} — shared mutable board. One per
     *       search; every recursion calls {@code makeMove} /
     *       {@code revertMove} on it.</li>
     *   <li>{@code pvTable} — see above. Shared across every
     *       {@code SearchNodeContext} within one deepening iteration.</li>
     * </ul>
     *
     * <p>The alpha-beta window ({@code alphaWeight} / {@code betaWeight})
     * and the TT-supplied move-ordering hint ({@code ttMove}) used to be
     * record fields in earlier iterations. They have been promoted to
     * plain method parameters on {@code alphaBetaSearch} /
     * {@code alphaBetaSearchMain} so that the per-node alpha/beta narrowing
     * does not require allocating a new record at every recursion — the
     * record state ({@code workingBoard}, {@code pvTable}, etc.) is shared,
     * the window is per-call.
     */
    @SuppressWarnings("java:S6218")
    public record SearchNodeContext(int depth, int maxDepth, MoveAndWeight bestKnownPath,
                                    int weightFactor,
                                    int materialWeight, int materialDelta,
                                    Board workingBoard, int[] pvTable) {

        /**
         * Plies still to search below this node ({@code maxDepth - depth}).
         * Used as the TT lookup-depth comparison: an entry stored at
         * remaining-depth {@code r_stored} is only usable for a score
         * read when {@code r_stored >= this.remainingDepth()}.
         */
        public int remainingDepth() {
            return maxDepth - depth;
        }

        /**
         * Side length of the (conceptual) PV-table matrix: both the
         * number of rows and the number of columns per row. Equals
         * {@code maxDepth + 1} — one extra slot past the last move
         * acts as a zero-terminator for PV consumers.
         */
        private int pvMaxLength() {
            return maxDepth + 1;
        }

        /**
         * Flat index of this depth's diagonal slot: column {@code d} of
         * row {@code d}, where {@code d == this.depth}. The move
         * currently being tried at this depth is stored here.
         */
        public int pvIndex() {
            return depth * pvMaxLength() + depth;
        }

        /**
         * Flat index of the slot in the parent's row that
         * {@link #copyUpPV()} and {@link #truncateParentPv()} write
         * into: column {@code d} of row {@code d-1}, i.e. the slot
         * directly to the right of the parent's own diagonal entry.
         */
        public int pvParentIndex() {
            return (depth - 1) * pvMaxLength() + depth;
        }

        /**
         * Propagate "my move plus its sub-PV" one level up. Copies
         * row {@code d} slots {@code d .. maxDepth} into row
         * {@code d-1} at the same column range, so that the parent's
         * PV reads {@code [parent's diagonal move, my diagonal move,
         * my sub-PV, ...]}.
         *
         * <p>Example, {@code copyUpPV()} called at depth 2 of a depth-4
         * search (so {@code maxDepth=4}, {@code pvMaxLength=5}). The
         * trailing column 4 stays at the {@code 0} terminator throughout:
         *
         * <pre>{@code
         *   col           0    1    2    3    4
         *               +----+----+----+----+----+
         *   row 1 before |    | M1 | ?? | ?? |  0 |   <- parent's M1 in slot 1; slots 2..3 not
         *               +----+----+----+----+----+      yet set in this iteration
         *   row 2       |    |    | M2 | M3 |  0 |   <- depth 2's M2 in slot 2; M3 already
         *               +----+----+----+----+----+      filled in by an earlier copyUpPV from
         *                                              depth 3
         *
         *                       || copy row 2, slots 2..4 --> row 1, slots 2..4
         *                       v
         *
         *               +----+----+----+----+----+
         *   row 1 after |    | M1 | M2 | M3 |  0 |   <- row 1 now reads as the PV
         *               +----+----+----+----+----+      [M1, M2, M3] from depth 1's perspective
         * }</pre>
         *
         * <p>Called from {@code alphaBetaSearchMain}'s move loop whenever
         * the current child move improves the parent's best result so
         * far (and on a β-cutoff before the early return).
         */
        public void copyUpPV() {
            System.arraycopy(pvTable, pvIndex(), pvTable, pvParentIndex(), pvMaxLength() - depth);
        }

        /**
         * Dual of {@link #copyUpPV()}: tell the parent "from my depth
         * onwards there is no PV continuation" by writing zeros into the
         * same parent-row range that {@code copyUpPV} would write into.
         *
         * <p>Required at every return path that produces a parent-
         * acceptable (non-ILLEGAL) weight without further moves to
         * propagate: terminal mate / stalemate at the move-loop exit,
         * draw by 50-move / threefold-repetition, and the leaf
         * static-eval return at {@code depth == maxDepth}. Without it,
         * the parent's subsequent {@code copyUpPV} carries forward
         * stale slots written by an earlier sibling's deeper
         * exploration — the defect reproduced by
         * {@code IllegalPvRegressionTest}'s test02 cases.
         *
         * <p>Example, {@code truncateParentPv()} called at depth 2 of a
         * depth-4 search (so {@code maxDepth=4}, {@code pvMaxLength=5}).
         * The trailing column 4 stays at the {@code 0} terminator
         * throughout:
         *
         * <pre>{@code
         *   col           0    1    2    3    4
         *               +----+----+----+----+----+
         *   row 1 before |    | M1 | M2'| M3'|  0 |   <- parent has stale slots 2..3
         *               +----+----+----+----+----+      from an earlier sibling's
         *                                              copyUpPV at this depth
         *   row 2       |    |    | ?? | ?? |  ? |   <- depth 2 returns terminal
         *               +----+----+----+----+----+      (no move to propagate)
         *
         *                       || zero row 1, slots 2..4
         *                       v
         *
         *               +----+----+----+----+----+
         *   row 1 after |    | M1 |  0 |  0 |  0 |   <- parent's row reads as the PV
         *               +----+----+----+----+----+      [M1] only; no stale tail
         * }</pre>
         */
        public void truncateParentPv() {
            final int fromIndex = pvParentIndex();
            final int toIndexExclusive = depth * pvMaxLength();
            Arrays.fill(pvTable, fromIndex, toIndexExclusive, 0);
        }

        /**
         * PV-table maintenance for the transposition-table early-return
         * paths in {@code alphaBetaSearchPre}. When a TT lookup serves the
         * result for this node (EXACT bound, or LOWER/UPPER cutoff with
         * {@code alpha >= beta}), the recursive descent that would
         * normally fill row {@code d}'s slots {@code d..maxDepth} via
         * {@link #copyUpPV()} from the child never happens. Without this
         * helper, the parent's subsequent {@link #copyUpPV()} would
         * carry the row's stale slots — left over from an earlier
         * sibling's exploration — up into its own row, and the iteration
         * would emit a principal variation that includes moves which are
         * not legal at the positions the PV claims to reach.
         *
         * <p>This is the same hazard {@link #truncateParentPv()} addresses
         * for terminal returns (mate/stalemate/draw/leaf eval), specialized
         * for the TT case where we DO have a non-zero move to propagate
         * (the TT entry's stored best move). Concretely:
         *
         * <ol>
         *   <li>Write {@code ttMove} into this depth's diagonal slot
         *       (row {@code d}, column {@code d}), matching what an
         *       in-tree search would have written via
         *       {@code pvTable[pvIndex] = move} in the move loop.</li>
         *   <li>Zero this depth's row past the diagonal (columns
         *       {@code d+1..maxDepth}) — no further continuation is
         *       known beyond the TT-stored best move.</li>
         *   <li>Propagate the result one level up via {@link #copyUpPV()},
         *       so the parent's row reads
         *       {@code [..., parent's move, ttMove, 0, 0, ...]} after the
         *       parent picks this child as its best.</li>
         * </ol>
         *
         * <p>Without this propagation, the next iterative-deepening pass
         * picks up the stale PV as its {@code bestKnownPath}, hands a
         * not-legal move to {@code MoveGenerator} as the move-ordering
         * hint, and (depending on the MoveSorter's tolerance) either
         * trips the sorter's "pvMove not produced" diagnostic skip or
         * crashes inside {@code Board.makeMove}.
         *
         * <p>Example, {@code writeTTCachedPv(TT)} called at depth 2 of a
         * depth-4 search (so {@code maxDepth=4}, {@code pvMaxLength=5})
         * after a TT lookup serves the result for this node:
         *
         * <pre>{@code
         *   col           0    1    2    3    4
         *               +----+----+----+----+----+
         *   row 1 before |    | M1 | M2'| M3'|  0 |   <- parent's stale slots
         *               +----+----+----+----+----+
         *   row 2 before |    |    | M2'| M3'|  0 |   <- own row also stale
         *               +----+----+----+----+----+      (from a previous visit)
         *
         *                       || step 1: write TT-bestMove to own
         *                       ||         diagonal (row 2, col 2)
         *                       v
         *
         *               +----+----+----+----+----+
         *   row 2 step1 |    |    | TT | M3'|  0 |   <- diagonal correct, tail stale
         *               +----+----+----+----+----+
         *
         *                       || step 2: zero own row past the diagonal
         *                       ||         (row 2, cols 3..4)
         *                       v
         *
         *               +----+----+----+----+----+
         *   row 2 step2 |    |    | TT |  0 |  0 |
         *               +----+----+----+----+----+
         *
         *                       || step 3: copyUpPV — row 2, slots 2..4
         *                       ||         -> row 1, slots 2..4
         *                       v
         *
         *               +----+----+----+----+----+
         *   row 1 after |    | M1 | TT |  0 |  0 |   <- parent's row now reads
         *               +----+----+----+----+----+      [M1, TT] with no tail
         * }</pre>
         *
         * @param ttMove the {@link TranspositionTable.TTEntry#getBestMove()}
         *               of the entry being returned. Must be non-zero; the
         *               TT only stores a best move alongside a real score.
         */
        public void writeTTCachedPv(int ttMove) {
            pvTable[pvIndex()] = ttMove;
            Arrays.fill(pvTable, pvIndex() + 1, (depth + 1) * pvMaxLength(), 0);
            copyUpPV();
        }

    }

    /**
     * One alpha-beta node's return value. The {@code bound} and
     * {@code bestMove} fields exist for the transposition table:
     * {@code bound} classifies {@code weight} as exact / lower / upper
     * (see {@link Bound}), and {@code bestMove} carries the move that
     * produced {@code weight} so a future re-visit of the same position
     * can use it as the first move tried (see
     * {@link MoveSorter#reset}'s {@code ttMove} parameter). Both are
     * read back via {@link TranspositionTable#put}.
     */
    public record SearchNodeResult(GameResult result, int weight, Bound bound, int bestMove, boolean isTimeout) {

        public static final SearchNodeResult TIMEOUT = new SearchNodeResult(GameResult.ONGOING, 0, Bound.EXACT, 0, true);
        public static final SearchNodeResult INVALID = new SearchNodeResult(GameResult.ONGOING, WeightingFunction.ILLEGAL_WEIGHT_NEG, Bound.EXACT, 0, false);

        /**
         * Initial "no result yet" placeholder used as the starting value of
         * {@code bestResult} in {@code alphaBetaSearchMain}. Any real return
         * value (in {@code (ILLEGAL_WEIGHT_NEG, ILLEGAL_WEIGHT_POS]}) is
         * strictly greater, so the first valid move always replaces it.
         */
        public static final SearchNodeResult INITIAL = new SearchNodeResult(GameResult.ONGOING, WeightingFunction.MIN_ALPHA, Bound.EXACT, 0, false);

        public SearchNodeResult(GameResult result, int weight, Bound bound, int bestMove) {
            this(result, weight, bound, bestMove, false);
        }

        public boolean isIllegal() {
            return weight ==  WeightingFunction.ILLEGAL_WEIGHT_POS || weight ==  WeightingFunction.ILLEGAL_WEIGHT_NEG;
        }

        public static SearchNodeResult create(GameResult result, int weight, Bound bound, int bestMove) {
            return new SearchNodeResult(result, weight, bound, bestMove, false);
        }

        public static SearchNodeResult draw() {
            return new SearchNodeResult(GameResult.DRAW, 0, Bound.EXACT, 0, false);
        }

        /**
         * Sentinel result for "previous move left own king capturable". The
         * {@code ILLEGAL_WEIGHT_POS} weight is preserved unchanged through
         * the rest of the search — fail-soft does not clamp it.
         */
        public static SearchNodeResult illegal() {
            return new SearchNodeResult(GameResult.ONGOING, WeightingFunction.ILLEGAL_WEIGHT_POS, Bound.EXACT, 0, false);
        }

        public static SearchNodeResult checkmateSelf(int depth) {
            return new SearchNodeResult(GameResult.CHECKMATE, -WeightingFunction.checkmateInCenti(depth), Bound.EXACT, 0, false);
        }

        public static SearchNodeResult stalemate() {
            return new SearchNodeResult(GameResult.STALEMATE, 0, Bound.EXACT, 0, false);
        }

        public SearchNodeResult negate() {
            if (weight == 0) {
                return this;
            }
            return new SearchNodeResult(result, -weight, bound, bestMove, isTimeout);
        }
    }

    private final NextMoveTask task;
    private final Game game;
    private final EngineConfig engineConfig;
    private final KillerMoves killerMoves = new KillerMoves();
    private final MoveGenerator moveGenerator;
    private final WeightingFunction weightingFunction = new WeightingFunction();
    private final QuiescenceSearch quiescenceSearch;
    private final TranspositionTable tt;
    private final int weightFactor;
    private final Statistics statistics = new Statistics();
    private final boolean silent;
    private final long timeout;
    private boolean isTimeout;

    private PositionSearch(ChessEngine engine, NextMoveTask task, Game game) {
        this.task = task;
        this.game = game;
        this.moveGenerator = new MoveGenerator(new MoveSorterImpl(killerMoves));
        this.engineConfig = engine.getConfig();
        this.quiescenceSearch = new QuiescenceSearch(moveGenerator, weightingFunction, statistics, engineConfig.getMaxQuiescenceDepth());
        this.tt = engine.getTranspositionTable();
        this.weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        this.silent = engineConfig.isSilent();
        this.timeout = System.currentTimeMillis() + engineConfig.getMillisPerMove();
    }

    public static MoveAndWeight calculateNextMove(ChessEngine engine, NextMoveTask task, Game game) {
        return new PositionSearch(engine, task, game).calculateNextMove();
    }

    public static Moves getPossibleMoves(ChessEngine engine, Game game) {
        return new PositionSearch(engine, new NextMoveTask(), game).getPossibleMoves();
    }

    private Moves getPossibleMoves() {
        return moveGenerator.calculateMoves(game.getBoard());
    }

    private boolean isTimeout() {
        if (!isTimeout) {
            isTimeout = statistics.getPositionsCount() % 10000 == 0 && System.currentTimeMillis() >= timeout;
        }
        return isTimeout;
    }

    private MoveAndWeight calculateNextMove() {
        MoveAndWeight bestPath = null;
        // Clamp to MAX_SEARCH_DEPTH so a UCI `go depth 5000` (or the default
        // Integer.MAX_VALUE for depth-unlimited go commands) cannot drive the
        // iteration loop into a runaway — see MAX_SEARCH_DEPTH JavaDoc.
        final int maxDepth = Math.min(engineConfig.getMaxDepth(), MAX_SEARCH_DEPTH);
        final long startMs = System.currentTimeMillis();
        long previousIterationEndMs = startMs;

        for (int depth = 1; depth <= maxDepth && !isTimeout(); depth++) {
            if (depth > 1 && shouldSkipIteration(depth)) {
                break;
            }

            log("Current depth: " + depth);
            bestPath = calculateNextMove(depth, bestPath);
            long iterationEndMs = System.currentTimeMillis();
            long iterationMs = iterationEndMs - previousIterationEndMs;
            previousIterationEndMs = iterationEndMs;

            if (isTimeout) {
                IterationTimings.recordAbort(depth, iterationMs);
                break;
            }

            IterationTimings.recordCompletion(depth, iterationMs);

            MoveAndWeight m = bestPath.weightFactor(weightFactor);

            log("Depth: " + depth + ", move: " + ChessUtil.moveToString(m.move()) + ", weight: " + ChessUtil.weightToString(m.weight()) + " [" + ChessUtil.pathToString(m.path()) + "]");
            log("#positions: " + statistics.getPositionsCount() + ", #pruned: " + statistics.getPrunedMovesCount());

            // IterationInfo is documented to carry the raw negamax score
            // (positive = side-to-move advantage), which is what UCI's
            // `score cp` expects. Use bestPath.weight() directly — the
            // White-POV value `m.weight()` would be wrong here when
            // playing Black and made cutechess's resign-threshold fire
            // on winning positions.
            task.fireIteration(new IterationInfo(
                    game.getGeneration(),
                    depth,
                    statistics.getPositionsCount(),
                    iterationEndMs - startMs,
                    bestPath.weight(),
                    Arrays.copyOf(bestPath.path(), bestPath.path().length)));
        }

        // The last path component may be an illegal move (because this is not checked on the leaf nodes).
        // Hence, we just shorten the path by one to avoid returning an invalid path.
        if (bestPath != null && bestPath.path().length >= maxDepth) {
            bestPath.path()[maxDepth - 1] = 0;
        }

        return bestPath;
    }

    /**
     * Skip-decision for the iterative-deepening loop: returns {@code true}
     * if the estimated cost for {@code depth} exceeds the remaining time
     * budget and the heuristic is allowed to act on it (enough samples,
     * not currently due for a probing run). Records the skip as a side
     * effect when it returns {@code true}.
     */
    private boolean shouldSkipIteration(int depth) {
        if (!IterationTimings.hasEnoughSamplesForSkipDecision(depth)) {
            return false;
        }

        long estimateMs = IterationTimings.getEstimatedMs(depth);
        long remainingMs = timeout - System.currentTimeMillis();
        if (estimateMs <= remainingMs) {
            return false;
        }

        if (IterationTimings.isProbingDue(depth, estimateMs, remainingMs)) {
            Log.info("[time] probe depth " + depth + ": est " + estimateMs
                    + " ms > remaining " + remainingMs + " ms");
            return false;
        }

        IterationTimings.recordSkip(depth);
        Log.info("[time] skip depth " + depth + ": est " + estimateMs
                + " ms > remaining " + remainingMs + " ms");

        return true;
    }

    @SuppressWarnings("Duplicates")
    private MoveAndWeight calculateNextMove(final int maxDepth, MoveAndWeight bestKnownPath) {
        final MoveAndWeight previousBestKnownPath = bestKnownPath;
        final int pvMaxLength = maxDepth + 1;
        final Board workingBoard = game.getBoard().copy();

        final int bestKnownNextMove = getMoveAtDepth(bestKnownPath, 0);
        final Moves moves = moveGenerator.calculateMoves(workingBoard, 0, bestKnownNextMove, 0);
        if (moves.isIllegal()) {
            throw new IllegalChessPositionException(workingBoard);
        }

        final int materialWeight = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final SearchNodeResult[] results = new SearchNodeResult[countMoves];
        final int[][] allPaths = new int[countMoves][pvMaxLength];
        final int[] pvTable = new int[pvMaxLength * pvMaxLength];
        int alphaWeight = WeightingFunction.MIN_ALPHA;
        statistics.incrPositionCount();

        Arrays.fill(results, SearchNodeResult.INVALID);

        __assert(() -> !(countMoves > 0 && bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]),
                () -> "First move must be the best known move. Expected: " + new Move(bestKnownNextMove) + ", actual: " + new Move(plainMoves[0]) + ", depth: 0");

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];

            final int moveWeight = WeightingFunction.getMaterialWeightOfMove(move);
            final int newMaterialWeight = materialWeight + moveWeight;
            boolean logWeight = false;

            pvTable[0] = move;
            workingBoard.makeMove(move);
            var result = alphaBetaSearch(
                    new SearchNodeContext(1, maxDepth, bestKnownPath, -weightFactor, -newMaterialWeight, -moveWeight, workingBoard, pvTable),
                    WeightingFunction.MIN_ALPHA, -alphaWeight)
                    .negate();
            if (result.isTimeout()) {
                return previousBestKnownPath;
            }
            bestKnownPath = null;
            workingBoard.revertMove();

            // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
            if (result.weight > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
                results[i] = result;
                System.arraycopy(pvTable, 0, allPaths[i], 0, pvMaxLength);

                if (result.weight > alphaWeight) {
                    alphaWeight = result.weight;
                    logWeight = true;
                }
            }

            log((i + 1) + "/" + countMoves + ": " + ChessUtil.moveToString(move) + (logWeight ? " " + ChessUtil.weightToString(result.weight, weightFactor) : ""));
        }

        int bestWeight = WeightingFunction.ILLEGAL_WEIGHT_NEG;
        int bestMoveIndex = -1;

        for (int i = 0; i < countMoves; i++) {
            if (results[i].weight > bestWeight) {
                bestWeight = results[i].weight;
                bestMoveIndex = i;
            }
        }

        if (bestMoveIndex >= 0) {
            // Found a legal move
            return new MoveAndWeight(plainMoves[bestMoveIndex], results[bestMoveIndex].weight, results[bestMoveIndex].result, allPaths[bestMoveIndex]);
        }

        // No legal move possible ==> checkmate or stalemate
        if (workingBoard.isKingChecked()) {
            return new MoveAndWeight(0, -WeightingFunction.checkmateInCenti(), GameResult.CHECKMATE, new int[0]);
        } else {
            return new MoveAndWeight(0, 0, GameResult.STALEMATE, new int[0]);
        }
    }

    private int getMoveAtDepth(MoveAndWeight m, int depth) {
        if (m != null && m.path().length > depth) {
            return m.path()[depth];
        }

        return 0;
    }

    private SearchNodeResult alphaBetaSearch(final SearchNodeContext ctx, final int alphaWeight, final int betaWeight) {
        __assert(() -> !(WeightingFunction.isIllegalWeight(alphaWeight) || WeightingFunction.isIllegalWeight(betaWeight)),
                () -> "ILLEGAL_WEIGHT as alpha/beta; depth=" + ctx.depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + ctx.workingBoard);

        var result = alphaBetaSearchPre(ctx, alphaWeight, betaWeight);

        // ILLEGAL_WEIGHT_NEG <= weight < ILLEGAL_WEIGHT_POS
        __assert(() -> !(result.weight <= WeightingFunction.ILLEGAL_WEIGHT_NEG || result.weight > WeightingFunction.ILLEGAL_WEIGHT_POS),
                () -> "Unexpected weight " + result.weight + " returned, depth=" + ctx.depth() + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + ctx.workingBoard);

        return result;
    }

    private SearchNodeResult alphaBetaSearchPre(final SearchNodeContext ctx, int alphaWeight, int betaWeight) {
        final GameStatus gameStatus = ctx.workingBoard.getGameStatus();
        statistics.incrPositionCount();

        if ((engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) || (engineConfig.isEnableThreefoldRepetition() && ctx.workingBoard.isThreefoldRepetition())) {
            ctx.truncateParentPv();
            if (ctx.workingBoard().canCaptureOpposingKing()) {
                // ILLEGAL
                return SearchNodeResult.illegal();
            }
            return SearchNodeResult.draw();
        }

        // Leaf: a cheap "can my side capture the opposing king?" probe is
        // enough to detect that the previous move was an illegal self-check
        // — no need to generate (and sort) the full pseudo-legal move list
        // since we don't iterate at the leaf anyway.
        if (ctx.remainingDepth() == 0) {
            if (ctx.workingBoard.canCaptureOpposingKing()) {
                // ILLEGAL — parent will reject this branch and skip its own
                // copyUpPV, so the parent's row stays as-is. No truncate needed.
                return SearchNodeResult.illegal();
            }
            ctx.truncateParentPv();
            return SearchNodeResult.create(GameResult.ONGOING, quiescenceSearch(ctx, alphaWeight, betaWeight), Bound.EXACT, 0);
        }

        // Transposition table lookup
        final var ttEntry = tt.get(ctx.workingBoard.getGameStatus().getPositionHash());
        if (ttEntry != null && ttEntry.getDepth() >= ctx.remainingDepth()) {
            final int score = WeightingFunction.scoreFromTT(ttEntry.getScore(), ctx.depth);

            switch (ttEntry.getBound()) {
                case EXACT -> {
                    return exactTTResult(ctx, score, ttEntry.getBestMove());
                }
                case LOWER -> alphaWeight = Math.max(alphaWeight, score);
                case UPPER -> betaWeight = Math.min(betaWeight, score);
            }

            if (alphaWeight >= betaWeight) {
                return exactTTResult(ctx, score, ttEntry.getBestMove());
            }
        }

        final int bestMove = ttEntry != null ? ttEntry.getBestMove(): 0;
        final SearchNodeResult result = alphaBetaSearchMain(ctx, alphaWeight, betaWeight, bestMove);

        if (!result.isTimeout() && !result.isIllegal()) {
            // Store result in transposition table
            int score = WeightingFunction.scoreToTT(result.weight, ctx.depth);
            tt.put(gameStatus.getPositionHash(), ctx.remainingDepth(), score, result.bound, result.bestMove);
        }

        return result;
    }

    /**
     * Shared return path for the two TT-cache early-exit branches in
     * {@link #alphaBetaSearchPre} (EXACT bound, and LOWER/UPPER cutoff
     * with {@code alpha &gt;= beta}). Updates the PV table via
     * {@link SearchNodeContext#writeTTCachedPv(int)} so the parent's
     * subsequent {@link SearchNodeContext#copyUpPV()} does not propagate
     * stale slots from an earlier sibling's exploration, then wraps the
     * given depth-adjusted score and best move in a {@link SearchNodeResult}.
     */
    private static SearchNodeResult exactTTResult(SearchNodeContext ctx, int score, int bestMove) {
        ctx.writeTTCachedPv(bestMove);

        return SearchNodeResult.create(GameResult.ONGOING, score, Bound.EXACT, bestMove);
    }

    @SuppressWarnings("Duplicates")
    private PositionSearch.SearchNodeResult alphaBetaSearchMain(final PositionSearch.SearchNodeContext ctx, final int alphaWeight, final int betaWeight, final int ttMove) {
        final int depth = ctx.depth;
        MoveAndWeight bestKnownPath = ctx.bestKnownPath;
        statistics.incrPositionCount();
        final var pvTable = ctx.pvTable;
        final int pvIndex = ctx.pvIndex();

        // Fail-soft: bestResult starts below any legal weight; the first valid
        // move always replaces it, so the eventual return value is the true
        // best score even when it falls below ctx.alphaWeight.
        SearchNodeResult bestResult = SearchNodeResult.INITIAL;

        // Non-leaf: full move generation is needed for the iteration; check
        // legality on the resulting Moves.ILLEGAL sentinel.
        final int bestKnownNextMove = getMoveAtDepth(bestKnownPath, depth);
        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard, depth, bestKnownNextMove, ttMove);
        if (moves.isIllegal()) {
            return SearchNodeResult.illegal();
        }
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();

        __assert(() -> !(bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]),
                () -> "First move must be the best known move. Expected: " + new Move(bestKnownNextMove) + ", actual: " + new Move(plainMoves[0]) + ", depth: " + depth);

        if (task.isCanceled()) {
            throw new CancellationException();
        }

        boolean haveValidMoves = false;
        int bestMove = 0;

        for (int i = 0; i < countMoves; i++) {
            if (isTimeout()) {
                return SearchNodeResult.TIMEOUT;
            }

            final int move = plainMoves[i];
            final int moveWeight = WeightingFunction.getMaterialWeightOfMove(move);
            final int newMaterialWeight = ctx.materialWeight + moveWeight;
            final int newMaterialDelta = ctx.materialDelta + moveWeight;

            // alpha-beta cutoff threshold for the child: never below the
            // parent's alpha (fail-soft may pull bestResult.weight below it
            // when the first move fails low).
            final int alphaLocal = Math.max(alphaWeight, bestResult.weight);

            pvTable[pvIndex] = move;
            ctx.workingBoard.makeMove(move);
            var result = alphaBetaSearch(new SearchNodeContext(depth + 1, ctx.maxDepth, bestKnownPath, -ctx.weightFactor, -newMaterialWeight, -newMaterialDelta, ctx.workingBoard, pvTable), -betaWeight, -alphaLocal).negate();
            ctx.workingBoard.revertMove();
            if (result.isTimeout()) {
                return SearchNodeResult.TIMEOUT;
            }
            final int weight = result.weight;
            bestKnownPath = null;

            // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
            if (weight > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
                haveValidMoves = true;

                // Alpha-Beta search pruning — fail-soft: return the actual
                // weight that triggered the cutoff, not the beta bound. The
                // unclamped value lets the caller (and a future TT) record a
                // tighter lower bound on this position's true score.
                if (weight >= betaWeight) {
                    statistics.incrPrunedMovesCount(countMoves - i - 1);
                    ctx.copyUpPV();
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return new SearchNodeResult(result.result(), weight, Bound.LOWER, move);
                }

                if (weight > bestResult.weight) {
                    bestResult = result;
                    bestMove = move;
                    ctx.copyUpPV();
                }
            }
        }

        if (haveValidMoves) {
            Bound bound = bestResult.weight > alphaWeight ? Bound.EXACT : Bound.UPPER;
            return new SearchNodeResult(bestResult.result, bestResult.weight, bound, bestMove);
        }

        return checkmateOrStalemate(ctx);
    }

    private PositionSearch.SearchNodeResult checkmateOrStalemate(PositionSearch.SearchNodeContext ctx) {
        // Reached when the move loop found no legal moves at this depth.
        // Fail-soft: return the true checkmate/stalemate score regardless of
        // the alpha-beta window — the caller (and a future TT) gets a sharp
        // bound instead of a window-clamped one. The PV is truncated because
        // there is no continuation.
        ctx.truncateParentPv();

        return ctx.workingBoard.isKingChecked() ?
                SearchNodeResult.checkmateSelf(ctx.depth()) :
                SearchNodeResult.stalemate();
    }

    private int quiescenceSearch(final SearchNodeContext ctx, final int alphaWeight, final int betaWeight) {
        final var workingBoard = ctx.workingBoard;
        final int lastMove = workingBoard.getGameStatus().getLastMove();

        if (Move.getCapturedPiece(lastMove) == 0) {
            return calculatePositionWeight(workingBoard, ctx.weightFactor, ctx.materialWeight, ctx.materialDelta);
        } else {
            return quiescenceSearch.quiescenceSearch(workingBoard, ctx.depth, ctx.weightFactor, alphaWeight, betaWeight, ctx.materialWeight, ctx.materialDelta);
        }
    }

    private int calculatePositionWeight(final Board workingBoard, final int weightFactor, final int materialWeight, final int materialDelta) {
        if (materialDelta > EVALUATE_MATERIAL_ONLY_THRESHOLD || materialDelta < -EVALUATE_MATERIAL_ONLY_THRESHOLD) {
            return materialWeight;
        }
        return weightingFunction.calculate(workingBoard) * weightFactor;
    }

    private void log(String s) {
        if (!silent) {
            Log.info(s);
        }
    }
}
