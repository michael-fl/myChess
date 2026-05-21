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

    @SuppressWarnings("java:S6218")
    public record SearchNodeContext(int depth, int maxDepth, MoveAndWeight bestKnownPath,
                                    int weightFactor,
                                    int alphaWeight, int betaWeight, int materialWeight, int materialDelta,
                                    Board workingBoard, int[] pvTable) {

        private int pvMaxLength() {
            return maxDepth + 1;
        }

        public int pvIndex() {
            return depth * pvMaxLength() + depth;
        }

        public int pvParentIndex() {
            return (depth - 1) * pvMaxLength() + depth;
        }

        public void copyUpPV() {
            System.arraycopy(pvTable, pvIndex(), pvTable, pvParentIndex(), pvMaxLength() - depth);
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
            bestPath = calculateNextMove(depth, timeout, bestPath);
            long iterationEndMs = System.currentTimeMillis();
            long iterationMs = iterationEndMs - previousIterationEndMs;
            previousIterationEndMs = iterationEndMs;

            if (isTimeout) {
                IterationTimings.recordAbort(depth, iterationMs);
                break;
            }

            IterationTimings.recordCompletion(depth, iterationMs);

            MoveAndWeight m = bestPath.weightFactor(weightFactor);

            log("Depth: " + depth + ", move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight) + " [" + ChessUtil.pathToString(m.path) + "]");
            log("#positions: " + statistics.getPositionsCount() + ", #pruned: " + statistics.getPrunedMovesCount());

            task.fireIteration(new IterationInfo(
                    depth,
                    statistics.getPositionsCount(),
                    iterationEndMs - startMs,
                    m.weight,
                    Arrays.copyOf(m.path, m.path.length)));
        }

        // The last path component may be an illegal move (because this is not checked on the leaf nodes).
        // Hence, we just shorten the path by one to avoid returning an invalid path.
        if (bestPath != null && bestPath.path.length >= maxDepth) {
            bestPath.path[maxDepth - 1] = 0;
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
    private MoveAndWeight calculateNextMove(final int maxDepth, final long timeout, MoveAndWeight bestKnownPath) {
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
        if (workingBoard.isKingChecked(moveGenerator)) {
            return new MoveAndWeight(0, -WeightingFunction.checkmateInCenti(), GameResult.CHECKMATE, new int[0]);
        } else {
            return new MoveAndWeight(0, 0, GameResult.STALEMATE, new int[0]);
        }
    }

    private int getMoveAtDepth(MoveAndWeight m, int depth) {
        if (m != null && m.path.length > depth) {
            return m.path[depth];
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
            return SearchNodeResult.draw(ctx.alphaWeight(), ctx.betaWeight());
        }

        if (depth == ctx.maxDepth) {
            return SearchNodeResult.create(GameResult.ONGOING, quiescenceSearch(ctx), ctx.alphaWeight(), ctx.betaWeight());
        }

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
        var alpha = ctx.alphaWeight();
        if (alpha >= 0f) {
            return SearchNodeResult.create(GameResult.ONGOING, alpha);
        }
        return ctx.workingBoard.isKingChecked(moveGenerator) ?
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
