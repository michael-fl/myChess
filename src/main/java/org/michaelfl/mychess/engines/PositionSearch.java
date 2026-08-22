package org.michaelfl.mychess.engines;

import org.jspecify.annotations.Nullable;
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

    /** Depth reduction R for null-move pruning. */
    private static final int NMP_REDUCTION_R = 2;

    /**
     * Minimum plies the reduced child must still search for NMP to give
     * meaningful signal (as opposed to a near-static probe).
     * Setting this to 2 means the child sees at least 2 plies + quiescence,
     * so it can detect two-ply threats and quiet build-ups.
     */
    private static final int NMP_MIN_CHILD_DEPTH = 2;

    private final NextMoveTask task;
    private final Game game;
    private final EngineConfig engineConfig;
    private final KillerMoves killerMoves = new KillerMoves();
    private final MoveGenerator moveGenerator;
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
        this.timeout = System.currentTimeMillis() + engineConfig.getMillisPerMove();
        this.quiescenceSearch = new QuiescenceSearch(
                MoveGenerator.forQuiescenceSearch(),
                new WeightingFunction(), statistics, engineConfig.getMaxQuiescenceDepth(), timeout);
        this.tt = engine.getTranspositionTable();
        this.weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        this.silent = engineConfig.isSilent();
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
        // iteration loop into a runaway — see MAX_SEARCH_DEPTH Javadoc.
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
     * Re-searches {@code bestMove} with the child marked as a PV node, so the
     * subtree reports a complete principal variation instead of ending at a
     * transposition-table cutoff.
     *
     * <p>The window is the node's own {@code (alphaWeight, betaWeight)} and deliberately not the
     * tightened {@code alphaLocal} of the move loop: the winner's score sits at that tightened
     * bound, so re-searching against it would fail low and destroy the exact value this is
     * trying to keep.
     *
     * <p>The window is the node's own {@code (alphaWeight, betaWeight)} and
     * deliberately not the tightened {@code alphaLocal} of the move loop: the
     * winner's score sits at that tightened bound, so re-searching against it
     * would fail low and destroy the exact value we are trying to keep.
     *
     * <p><b>Known residual case.</b> The repair is deferred to after the move
     * loop, so it is never reached when the node leaves the loop early through a
     * beta cutoff. Such a node reports {@code Bound.LOWER}, and its line is a
     * refutation rather than a principal variation, but it has already copied
     * its row upward — a truncated tail can therefore still survive there.
     * Measured over 400 searches at depth 7 with a warm table this happened
     * once, against 1094 truncations without the PV-node rule at all. Closing it
     * would mean repairing before the cutoff returns, which costs a re-search on
     * a line that is about to be discarded.
     *
     * @return the re-searched result when it repaired the line,
     *         {@link SearchNodeResult#TIMEOUT} on timeout,
     *         otherwise {@code bestResult} unchanged
     */
    private SearchNodeResult researchWinnerAsPvNode(SearchNodeContext ctx, SearchNodeResult bestResult,
                                                    int bestMove, int alphaWeight, int betaWeight) {
        final int moveWeight = WeightingFunction.getMaterialWeightOfMove(bestMove);

        ctx.pvTable()[ctx.pvIndex()] = bestMove;
        ctx.workingBoard().makeMove(bestMove);
        var pvResult = alphaBetaSearch(new SearchNodeContext(ctx.depth() + 1, ctx.maxDepth(), null,
                -ctx.weightFactor(), -(ctx.materialWeight() + moveWeight), -(ctx.materialDelta() + moveWeight),
                ctx.workingBoard(), ctx.pvTable(), ctx.pvMaxLength(), true, false),
                -betaWeight, -alphaWeight).negate();
        ctx.workingBoard().revertMove();

        if (pvResult.isTimeout()) {
            return SearchNodeResult.TIMEOUT;
        }
        if (pvResult.weight() > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
            ctx.copyUpPV();
            return pvResult;
        }

        return bestResult;
    }

    /**
     * Skip-decision for the iterative-deepening loop: returns {@code true}
     * if the estimated cost for {@code depth} exceeds the remaining time
     * budget and the heuristic is allowed to act on it (enough samples,
     * not currently due for a probing run). Records the skip as a side
     * effect when it returns {@code true}.
     */
    private boolean shouldSkipIteration(int depth) {
        final long remainingMs = timeout - System.currentTimeMillis();
        final var decision = IterationTimings.decideIteration(depth, remainingMs);

        if (decision == IterationTimings.IterationDecision.RUN) {
            return false;
        }

        final long estimateMs = IterationTimings.getEstimatedMs(depth);

        if (decision == IterationTimings.IterationDecision.PROBE) {
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
        // The beta each root move was actually searched with — see RootMoves#betaUsed.
        final int[] betaUsedPerMove = new int[countMoves];
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
            boolean isPvMove = i == 0;

            betaUsedPerMove[i] = -alphaWeight;
            pvTable[0] = move;
            workingBoard.makeMove(move);
            var result = alphaBetaSearch(
                    new SearchNodeContext(1, maxDepth, bestKnownPath, -weightFactor, -newMaterialWeight, -moveWeight, workingBoard, pvTable, pvMaxLength, isPvMove, false),
                    WeightingFunction.MIN_ALPHA, -alphaWeight)
                    .negate();
            if (result.isTimeout()) {
                return previousBestKnownPath;
            }
            bestKnownPath = null;
            workingBoard.revertMove();

            // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
            if (result.weight() > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
                results[i] = result;
                System.arraycopy(pvTable, 0, allPaths[i], 0, pvMaxLength);

                if (result.weight() > alphaWeight) {
                    alphaWeight = result.weight();
                    logWeight = true;
                }
            }

            log((i + 1) + "/" + countMoves + ": " + ChessUtil.moveToString(move) + (logWeight ? " " + ChessUtil.weightToString(result.weight(), weightFactor) : ""));
        }

        int bestWeight = WeightingFunction.ILLEGAL_WEIGHT_NEG;
        int bestMoveIndex = -1;

        for (int i = 0; i < countMoves; i++) {
            if (results[i].weight() > bestWeight) {
                bestWeight = results[i].weight();
                bestMoveIndex = i;
            }
        }

        if (bestMoveIndex >= 0) {
            // Found a legal move

            final var rootMoves = new RootMoves(plainMoves, results, allPaths, betaUsedPerMove);

            if (repairWinnerPv(bestMoveIndex, maxDepth, workingBoard, pvTable, rootMoves, materialWeight)) {
                return previousBestKnownPath;
            }

            return new MoveAndWeight(plainMoves[bestMoveIndex], results[bestMoveIndex].weight(), results[bestMoveIndex].result(), allPaths[bestMoveIndex]);
        }

        // No legal move possible ==> checkmate or stalemate
        if (workingBoard.isKingChecked()) {
            return new MoveAndWeight(0, -WeightingFunction.checkmateInCenti(), GameResult.CHECKMATE, new int[0]);
        } else {
            return new MoveAndWeight(0, 0, GameResult.STALEMATE, new int[0]);
        }
    }

    /**
     * Repairs the winning root move's principal variation.
     *
     * <p>Only the first root move is searched as a PV node. If a later move won, its subtree
     * ran with transposition-table cutoffs enabled. Then {@code allPaths[bestMoveIndex]} can
     * end at a cutoff instead of at the leaf. Its score can also come from the entry that
     * produced that cutoff, rather than from searching this move. That array becomes
     * {@code bestKnownPath} for the next iteration, so a truncated line costs move ordering
     * there too.
     *
     * <p>Skipped when the line legitimately ends in a terminal position: mate, stalemate and the
     * fifty-move/repetition branch all call {@code truncateParentPv()} because there is no
     * continuation to report, and those cases carry a non-ONGOING result. Filtering them out is
     * what leaves truncation with no chess reason — a transposition-table cutoff that returned a
     * score without searching the children, which {@code ttResult} reports as ONGOING.
     *
     * <p><b>The corrected score is not re-selected on, and that is a known open defect.</b> The
     * re-search can lower the winner's score below another move's recorded score while the argmax
     * that picked the winner is not re-run, so the root can return a move it now rates second.
     * Both known repairs were measured and both are unusable: re-selecting after a full-window
     * verification cost 44 Elo, and re-selecting after a re-search against the recorded beta is
     * *wrong* — such a re-search fails high by construction, so its value is an over-estimate and
     * the comparison picks the worse move. That measured −166. See {@code docs/roadmap.md}
     * § 12.25, and do not attempt either again before PVS (§ 12.20) makes a cheap verification
     * possible.
     *
     * @return {@code true} if the search timed out, in which case the caller must fall back to
     *         the previous iteration and nothing computed here may be trusted
     */
    private boolean repairWinnerPv(final int bestMoveIndex, final int maxDepth,
                                   final Board workingBoard, final int[] pvTable,
                                   final RootMoves rootMoves, final int materialWeight) {
        final SearchNodeResult[] results = rootMoves.results();
        final int[][] allPaths = rootMoves.paths();

        if (results[bestMoveIndex].result() != GameResult.ONGOING || countPathLength(allPaths[bestMoveIndex]) == maxDepth) {
            return false;
        }

        var pvResult = researchRootWinnerAsPvNode(workingBoard, pvTable, maxDepth, materialWeight,
                rootMoves.moves()[bestMoveIndex], rootMoves.betaUsed()[bestMoveIndex]);
        if (pvResult.isTimeout()) {
            return true;
        }

        final var repaired = pvResult;
        __assert(() -> !repaired.isIllegal(), () -> "The best found move must be a legal one");

        if (pvResult.weight() > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
            results[bestMoveIndex] = pvResult;
            System.arraycopy(pvTable, 0, allPaths[bestMoveIndex], 0, maxDepth + 1);
        }

        return false;
    }

    /**
     * Root variant of {@link #researchWinnerAsPvNode}. The window is {@code beta} as recorded
     * per move while the loop ran, not the running alpha — that has moved on and would make the
     * winner fail low against itself.
     *
     * <p>Leaves the caller to store the result and to copy the refreshed
     * {@code pvTable} row, because only the caller knows which slot of
     * {@code allPaths} belongs to this move.
     *
     * @return the re-searched result, or {@link SearchNodeResult#TIMEOUT}
     */
    private SearchNodeResult researchRootWinnerAsPvNode(Board workingBoard, int[] pvTable, int maxDepth,
                                                        int materialWeight, int move, int beta) {
        final int moveWeight = WeightingFunction.getMaterialWeightOfMove(move);

        pvTable[0] = move;
        workingBoard.makeMove(move);
        var pvResult = alphaBetaSearch(
                new SearchNodeContext(1, maxDepth, null, -weightFactor, -(materialWeight + moveWeight),
                        -moveWeight, workingBoard, pvTable, maxDepth + 1, true, false),
                WeightingFunction.MIN_ALPHA, beta)
                .negate();
        workingBoard.revertMove();

        return pvResult;
    }

    private int countPathLength(int[] path) {
        int count = 0;
        for (int move : path) {
            if (move == 0) {
                break;
            }
            count++;
        }

        return count;
    }

    /**
     * The root's four index-parallel arrays: the generated moves, their search results, their
     * principal variations, and the beta each move was actually searched with.
     *
     * <p>Bundled into one type because they are only correct together: every write must land in
     * the slot of the move it was computed for. That is the invariant the PV re-search broke
     * twice while the arrays traveled as separate parameters — once copying a rejected
     * candidate's line onto the winner, once copying a candidate's line to the previous winner's
     * slot. Both looked plausible at the call site.
     *
     * <p>{@code betaUsed} is what the deferred re-search reuses: the running alpha has moved on
     * by then and would make the winner fail low against itself.
     *
     * <p>{@code moves} is the move generator's own array and may carry entries beyond the number
     * of root moves; the other three are sized exactly.
     */
    private record RootMoves(int[] moves, SearchNodeResult[] results, int[][] paths, int[] betaUsed) {
    }

    private int getMoveAtDepth(MoveAndWeight m, int depth) {
        if (m != null && m.path().length > depth) {
            return m.path()[depth];
        }

        return 0;
    }

    private SearchNodeResult alphaBetaSearch(final SearchNodeContext ctx, final int alphaWeight, final int betaWeight) {
        __assert(() -> !(WeightingFunction.isIllegalWeight(alphaWeight) || WeightingFunction.isIllegalWeight(betaWeight)),
                () -> "ILLEGAL_WEIGHT as alpha/beta; depth=" + ctx.depth() + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + ctx.workingBoard());

        var result = alphaBetaSearchPre(ctx, alphaWeight, betaWeight);

        // ILLEGAL_WEIGHT_NEG <= weight < ILLEGAL_WEIGHT_POS
        __assert(() -> !(result.weight() <= WeightingFunction.ILLEGAL_WEIGHT_NEG || result.weight() > WeightingFunction.ILLEGAL_WEIGHT_POS),
                () -> "Unexpected weight " + result.weight() + " returned, depth=" + ctx.depth() + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + ctx.workingBoard());

        return result;
    }

    private SearchNodeResult alphaBetaSearchPre(final SearchNodeContext ctx, int alphaWeight, int betaWeight) {
        final GameStatus gameStatus = ctx.workingBoard().getGameStatus();
        statistics.incrPositionCount();

        if ((engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) || (engineConfig.isEnableThreefoldRepetition() && ctx.workingBoard().isTwofoldRepetition())) {
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
            if (ctx.workingBoard().canCaptureOpposingKing()) {
                // ILLEGAL — parent will reject this branch and skip its own
                // copyUpPV, so the parent's row stays as-is. No truncate needed.
                return SearchNodeResult.illegal();
            }
            ctx.truncateParentPv();

            int weight = quiescenceSearch(ctx, alphaWeight, betaWeight);
            if (quiescenceSearch.isTimeout()) {
                this.isTimeout = true;
                return SearchNodeResult.TIMEOUT;
            }

            return SearchNodeResult.create(GameResult.ONGOING, weight, Bound.EXACT, 0);
        }

        // Transposition table lookup
        final var ttEntryView = tt.get(ctx.workingBoard().getGameStatus().getPositionHash());
        if (ttEntryView != null && ttEntryView.getDepth() >= ctx.remainingDepth()) {
            final int score = WeightingFunction.scoreFromTT(ttEntryView.getScore(), ctx.depth());

            if (!ctx.isPvNode()) {
                switch (ttEntryView.getBound()) {
                    case EXACT -> {
                        return ttResult(ctx, score, Bound.EXACT, ttEntryView.getBestMove());
                    }
                    case LOWER -> alphaWeight = Math.max(alphaWeight, score);
                    case UPPER -> betaWeight = Math.min(betaWeight, score);
                }
                if (alphaWeight >= betaWeight) {
                    // A LOWER/UPPER entry that pushes alpha >= beta produces a
                    // LOWER/UPPER cutoff, not an exact score — return the entry's
                    // own bound, never EXACT, so any consumer of this result's
                    // bound sees the correct fail-high/fail-low.
                    return ttResult(ctx, score, ttEntryView.getBound(), ttEntryView.getBestMove());
                }
            }
        }

        final int bestMove = ttEntryView != null ? ttEntryView.getBestMove() : 0;

        // Null move pruning (NMP)
        SearchNodeResult result = nmp(ctx, betaWeight);
        if (result != null) {
            return result;
        }

        result = alphaBetaSearchMain(ctx, alphaWeight, betaWeight, bestMove);

        if (!result.isTimeout() && !result.isIllegal()) {
            // Store result in transposition table
            int score = WeightingFunction.scoreToTT(result.weight(), ctx.depth());
            tt.put(gameStatus.getPositionHash(), ctx.remainingDepth(), score, result.bound(), result.bestMove());
        }

        return result;
    }

    private @Nullable SearchNodeResult nmp(final SearchNodeContext ctx, final int betaWeight) {
        if (canDoNMP(ctx)) {
            ctx.workingBoard().makeNullMove();
            var result = alphaBetaSearch(
                    new SearchNodeContext(ctx.depth() + 1, ctx.maxDepth() - NMP_REDUCTION_R, MoveAndWeight.NO_MOVE, -ctx.weightFactor(), -ctx.materialWeight(), -ctx.materialDelta(), ctx.workingBoard(), ctx.pvTable(), ctx.pvMaxLength(), false, true),
                    -betaWeight, -betaWeight + 1).negate();
            ctx.workingBoard().revertNullMove();
            ctx.truncateParentPv();

            if (result.isTimeout()) {
                return SearchNodeResult.TIMEOUT;
            }
            if (!result.isIllegal() && result.weight() >= betaWeight) { // beta cutoff
                statistics.incrNmpCutoffCount();
                return SearchNodeResult.create(GameResult.ONGOING, result.weight(), Bound.LOWER, 0);
            }
        }

        return null;
    }

    private boolean canDoNMP(SearchNodeContext ctx) {
        final GameStatus gameStatus = ctx.workingBoard().getGameStatus();

        return gameStatus.hasNonPawnMaterial() // Has at least 1 non-pawn piece?
                && ctx.remainingDepth() - 1 - NMP_REDUCTION_R >= NMP_MIN_CHILD_DEPTH // Child depth deep enough?
                && !ctx.lastMoveWasNull(); // No consecutive null moves (makes no sense)
    }

    /**
     * Shared return path for the two TT-cache early-exit branches in
     * {@link #alphaBetaSearchPre}: the EXACT-bound hit, and the LOWER/UPPER
     * cutoff with {@code alpha &gt;= beta}. Updates the PV table via
     * {@link SearchNodeContext#writeTTCachedPv(int)} so the parent's
     * subsequent {@link SearchNodeContext#copyUpPV()} does not propagate
     * stale slots from an earlier sibling's exploration, then wraps the
     * given depth-adjusted score and best move in a {@link SearchNodeResult}
     * carrying the caller-supplied {@code bound}.
     */
    private static SearchNodeResult ttResult(SearchNodeContext ctx, int score, Bound bound, int bestMove) {
        ctx.writeTTCachedPv(bestMove);

        return SearchNodeResult.create(GameResult.ONGOING, score, bound, bestMove);
    }

    @SuppressWarnings("Duplicates")
    private SearchNodeResult alphaBetaSearchMain(final SearchNodeContext ctx, final int alphaWeight, final int betaWeight, final int ttMove) {
        final int depth = ctx.depth();
        MoveAndWeight bestKnownPath = ctx.bestKnownPath();
        final var pvTable = ctx.pvTable();
        final int pvIndex = ctx.pvIndex();

        // Fail-soft: bestResult starts below any legal weight; the first valid
        // move always replaces it, so the eventual return value is the true
        // best score even when it falls below ctx.alphaWeight.
        SearchNodeResult bestResult = SearchNodeResult.INITIAL;

        // Non-leaf: full move generation is needed for the iteration; check
        // legality on the resulting Moves.ILLEGAL sentinel.
        final int bestKnownNextMove = getMoveAtDepth(bestKnownPath, depth);
        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard(), depth, bestKnownNextMove, ttMove);
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
            final int newMaterialWeight = ctx.materialWeight() + moveWeight;
            final int newMaterialDelta = ctx.materialDelta() + moveWeight;
            final boolean isPvMove = ctx.isPvNode() && i == 0;

            // alpha-beta cutoff threshold for the child: never below the
            // parent's alpha (fail-soft may pull bestResult.weight below it
            // when the first move fails low).
            final int alphaLocal = Math.max(alphaWeight, bestResult.weight());

            pvTable[pvIndex] = move;
            ctx.workingBoard().makeMove(move);
            var result = alphaBetaSearch(new SearchNodeContext(depth + 1, ctx.maxDepth(), bestKnownPath, -ctx.weightFactor(), -newMaterialWeight, -newMaterialDelta, ctx.workingBoard(), pvTable, ctx.pvMaxLength(), isPvMove, false), -betaWeight, -alphaLocal).negate();
            ctx.workingBoard().revertMove();
            if (result.isTimeout()) {
                return SearchNodeResult.TIMEOUT;
            }
            final int weight = result.weight();
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

                if (weight > bestResult.weight()) {
                    bestResult = result;
                    bestMove = move;
                    ctx.copyUpPV();
                }
            }
        }

        if (haveValidMoves) {
            Bound bound = bestResult.weight() > alphaWeight ? Bound.EXACT : Bound.UPPER;

            // Only plainMoves[0] was searched as a PV node, so a different winner
            // may carry a truncated line. EXACT only — UPPER
            // (fail-low) and LOWER (beta cutoff, returned from inside the loop)
            // have no principal variation worth repairing.
            if (bound == Bound.EXACT && ctx.isPvNode() && bestMove != plainMoves[0]) {
                bestResult = researchWinnerAsPvNode(ctx, bestResult, bestMove, alphaWeight, betaWeight);
                if (bestResult.isTimeout()) {
                    return SearchNodeResult.TIMEOUT;
                }

                // The re-search may move the weight out of the window the bound above was
                // derived from, so derive it again — otherwise a corrected fail-low is
                // handed to the transposition table labelled EXACT.
                if (bestResult.weight() <= alphaWeight) {
                    bound = Bound.UPPER;
                } else if (bestResult.weight() >= betaWeight) {
                    bound = Bound.LOWER;
                } // else: EXACT
            }

            return new SearchNodeResult(bestResult.result(), bestResult.weight(), bound, bestMove);
        }

        return checkmateOrStalemate(ctx);
    }

    private SearchNodeResult checkmateOrStalemate(SearchNodeContext ctx) {
        // Reached when the move loop found no legal moves at this depth.
        // Fail-soft: return the true checkmate/stalemate score regardless of
        // the alpha-beta window — the caller (and a future TT) gets a sharp
        // bound instead of a window-clamped one. The PV is truncated because
        // there is no continuation.
        ctx.truncateParentPv();

        return ctx.workingBoard().isKingChecked() ?
                SearchNodeResult.checkmateSelf(ctx.depth()) :
                SearchNodeResult.stalemate();
    }

    private int quiescenceSearch(final SearchNodeContext ctx, final int alphaWeight, final int betaWeight) {
        return quiescenceSearch.quiescenceSearch(ctx.workingBoard(), ctx.depth(), ctx.weightFactor(), alphaWeight, betaWeight, ctx.materialWeight(), ctx.materialDelta());
    }

    private void log(String s) {
        if (!silent) {
            Log.info(s);
        }
    }
}
