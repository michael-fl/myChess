package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.IllegalChessPositionException;
import org.michaelfl.mychess.KillerMoves;
import org.michaelfl.mychess.Log;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.QuiescenceSearch;
import org.michaelfl.mychess.Statistics;
import org.michaelfl.mychess.WeightingFunction;
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
     * Per-node state for one invocation of
     * {@link PositionSearch#alphaBetaSearchI(SearchNodeContext)}: current
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
     * iteration in {@code alphaBetaSearchI}. The slots
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
     *       depth. The leaf shortcut in {@code alphaBetaSearchI} fires
     *       at {@code depth == maxDepth}.</li>
     *   <li>{@code bestKnownPath} — the previous iteration's PV, threaded
     *       through so each depth can take its best-known move from
     *       {@code bestKnownPath.path()[depth]} and pass it to
     *       {@code MoveGenerator} as the head of the move-ordering list.</li>
     *   <li>{@code weightFactor} — {@code +1} for white-to-move,
     *       {@code -1} for black; negated at each child recursion so the
     *       search runs in pure negamax form.</li>
     *   <li>{@code alphaWeight} / {@code betaWeight} — alpha-beta window
     *       in centi-pawns from the side-to-move's perspective.</li>
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
     */
    @SuppressWarnings("java:S6218")
    public record SearchNodeContext(int depth, int maxDepth, MoveAndWeight bestKnownPath,
                                    int weightFactor,
                                    int alphaWeight, int betaWeight, int materialWeight, int materialDelta,
                                    Board workingBoard, int[] pvTable) {

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
         * <p>Called from {@code alphaBetaSearchI}'s move loop whenever
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
         */
        public void truncateParentPv() {
            final int fromIndex = pvParentIndex();
            final int toIndexExclusive = depth * pvMaxLength();
            Arrays.fill(pvTable, fromIndex, toIndexExclusive, 0);
        }
    }

    public record SearchNodeResult(GameResult result, int weight, boolean isTimeout) {

        public static final SearchNodeResult TIMEOUT = new SearchNodeResult(GameResult.ONGOING, 0, true);
        public static final SearchNodeResult INVALID = new SearchNodeResult(GameResult.ONGOING, WeightingFunction.ILLEGAL_WEIGHT_NEG, false);

        public static SearchNodeResult create(GameResult result, int weight) {
            return new SearchNodeResult(result, weight, false);
        }

        public static SearchNodeResult create(GameResult result, int weight, int alpha, int beta) {
            return new SearchNodeResult(result, window(weight, alpha, beta), false);
        }

        public static SearchNodeResult draw(int alpha, int beta) {
            return new SearchNodeResult(GameResult.DRAW, window(0, alpha, beta), false);
        }

        private static int window(int weight, int alpha, int beta) {
            // ILLEGAL_WEIGHT_POS is a sentinel signaling "previous move was a
            // self-check"; it must survive [alpha, beta] clamping or the
            // search loses the ability to reject the offending move. The
            // negative counterpart never appears as a positive return value
            // here, but we treat it symmetrically for consistency.
            if (WeightingFunction.isIllegalWeight(weight)) {
                return weight;
            }
            if (weight <= alpha) {
                return alpha;
            }
            return Math.min(weight, beta);
        }

        public static SearchNodeResult checkmateSelf(int depth, int alpha, int beta) {
            return new SearchNodeResult(GameResult.CHECKMATE, window(-WeightingFunction.checkmateInCenti(depth), alpha, beta), false);
        }

        public static SearchNodeResult stalemate(int alpha, int beta) {
            return new SearchNodeResult(GameResult.STALEMATE, window(0, alpha, beta), false);
        }

        public SearchNodeResult negate() {
            if (weight == 0) {
                return this;
            }
            return new SearchNodeResult(result, -weight, isTimeout);
        }
    }

    private final NextMoveTask task;
    private final Game game;
    private final EngineConfig engineConfig;
    private final KillerMoves killerMoves = new KillerMoves();
    private final MoveGenerator moveGenerator;
    private final WeightingFunction weightingFunction = new WeightingFunction();
    private final QuiescenceSearch quiescenceSearch;
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
        final int maxDepth = engineConfig.getMaxDepth();
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
        final Moves moves = moveGenerator.calculateMoves(workingBoard, 0, bestKnownNextMove);
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
            var result = alphaBetaSearch(new SearchNodeContext(1, maxDepth, bestKnownPath, -weightFactor, WeightingFunction.MIN_ALPHA, -alphaWeight, -newMaterialWeight, -moveWeight, workingBoard, pvTable)).negate();
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

    private SearchNodeResult alphaBetaSearch(final SearchNodeContext ctx) {
        var result = alphaBetaSearchI(ctx);
        // ILLEGAL_WEIGHT_NEG <= weight < ILLEGAL_WEIGHT_POS
        __assert(() -> !(result.weight <= WeightingFunction.ILLEGAL_WEIGHT_NEG || result.weight > WeightingFunction.ILLEGAL_WEIGHT_POS),
                () -> "Unexpected weight " + result.weight + " returned, depth=" + ctx.depth() + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);

        return result;
    }

    @SuppressWarnings("Duplicates")
    private SearchNodeResult alphaBetaSearchI(final SearchNodeContext ctx) {
        final int depth = ctx.depth;
        MoveAndWeight bestKnownPath = ctx.bestKnownPath;
        final GameStatus gameStatus = ctx.workingBoard.getGameStatus();
        statistics.incrPositionCount();
        final var pvTable = ctx.pvTable;
        final int pvIndex = ctx.pvIndex();
        SearchNodeResult bestResult = SearchNodeResult.create(GameResult.ONGOING, ctx.alphaWeight);

        __assert(() -> !(WeightingFunction.isIllegalWeight(ctx.alphaWeight()) || WeightingFunction.isIllegalWeight(ctx.betaWeight())),
                () -> "ILLEGAL_WEIGHT as alpha/beta; depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);

        if ((engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) || (engineConfig.isEnableThreefoldRepetition() && ctx.workingBoard.isThreefoldRepetition())) {
            ctx.truncateParentPv();
            return SearchNodeResult.draw(ctx.alphaWeight(), ctx.betaWeight());
        }

        // Leaf: a cheap "can my side capture the opposing king?" probe is
        // enough to detect that the previous move was an illegal self-check
        // — no need to generate (and sort) the full pseudo-legal move list
        // since we don't iterate at the leaf anyway.
        if (depth == ctx.maxDepth) {
            if (ctx.workingBoard.canCaptureOpposingKing()) {
                // ILLEGAL — parent will reject this branch and skip its own
                // copyUpPV, so the parent's row stays as-is. No truncate needed.
                return SearchNodeResult.create(GameResult.ONGOING, WeightingFunction.ILLEGAL_WEIGHT_POS, ctx.alphaWeight, ctx.betaWeight);
            }
            ctx.truncateParentPv();
            return SearchNodeResult.create(GameResult.ONGOING, quiescenceSearch(ctx), ctx.alphaWeight(), ctx.betaWeight());
        }

        // Non-leaf: full move generation is needed for the iteration; check
        // legality on the resulting Moves.ILLEGAL sentinel.
        final int bestKnownNextMove = getMoveAtDepth(bestKnownPath, depth);
        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard, depth, bestKnownNextMove);
        if (moves.isIllegal()) {
            return SearchNodeResult.create(GameResult.ONGOING, WeightingFunction.ILLEGAL_WEIGHT_POS, ctx.alphaWeight, ctx.betaWeight);
        }
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();

        __assert(() -> !(bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]),
                () -> "First move must be the best known move. Expected: " + new Move(bestKnownNextMove) + ", actual: " + new Move(plainMoves[0]) + ", depth: " + depth);

        if (task.isCanceled()) {
            throw new CancellationException();
        }

        boolean haveValidMoves = false;

        for (int i = 0; i < countMoves; i++) {
            if (isTimeout()) {
                return SearchNodeResult.TIMEOUT;
            }

            final int move = plainMoves[i];
            final int moveWeight = WeightingFunction.getMaterialWeightOfMove(move);
            final int newMaterialWeight = ctx.materialWeight + moveWeight;
            final int newMaterialDelta = ctx.materialDelta + moveWeight;

            pvTable[pvIndex] = move;
            ctx.workingBoard.makeMove(move);
            var result = alphaBetaSearch(new SearchNodeContext(depth + 1, ctx.maxDepth, bestKnownPath, -ctx.weightFactor, -ctx.betaWeight, -bestResult.weight, -newMaterialWeight, -newMaterialDelta, ctx.workingBoard, pvTable)).negate();
            ctx.workingBoard.revertMove();
            if (result.isTimeout()) {
                return SearchNodeResult.TIMEOUT;
            }
            final int weight = result.weight;
            bestKnownPath = null;

            // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
            if (weight > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
                haveValidMoves = true;

                // Alpha-Beta search pruning
                if (weight >= ctx.betaWeight) {
                    statistics.incrPrunedMovesCount(countMoves - i - 1);
                    ctx.copyUpPV();
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return SearchNodeResult.create(result.result, ctx.betaWeight);
                }

                if (weight > bestResult.weight) {
                    bestResult = result;
                    ctx.copyUpPV();
                }
            }
        }

        return haveValidMoves ? bestResult : checkmateOrStalemate(ctx);
    }

    private SearchNodeResult checkmateOrStalemate(SearchNodeContext ctx) {
        // Reached when the move loop found no legal moves at this depth.
        // All three return paths produce a parent-acceptable weight without
        // any further PV moves to propagate, so the parent's row d..end
        // must be cleared to truncate the PV here.
        ctx.truncateParentPv();

        var alpha = ctx.alphaWeight();
        if (alpha >= 0f) {
            return SearchNodeResult.create(GameResult.ONGOING, alpha);
        }
        return ctx.workingBoard.isKingChecked() ?
                SearchNodeResult.checkmateSelf(ctx.depth(), alpha, ctx.betaWeight()) :
                SearchNodeResult.stalemate(alpha, ctx.betaWeight());
    }

    private int quiescenceSearch(SearchNodeContext ctx) {
        final var workingBoard = ctx.workingBoard;
        final int lastMove = workingBoard.getGameStatus().getLastMove();

        if (Move.getCapturedPiece(lastMove) == 0) {
            return calculatePositionWeight(workingBoard, ctx.weightFactor, ctx.materialWeight, ctx.materialDelta);
        } else {
            return quiescenceSearch.quiescenceSearch(workingBoard, Move.getToField(lastMove), ctx.depth, ctx.weightFactor, ctx.alphaWeight, ctx.betaWeight, ctx.materialWeight, ctx.materialDelta);
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
