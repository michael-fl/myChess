package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.IllegalChessPositionException;
import org.michaelfl.mychess.KillerMoves;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.QuiescenceSearch;
import org.michaelfl.mychess.Statistics;
import org.michaelfl.mychess.WeightingFunction;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.Arrays;
import java.util.concurrent.CancellationException;

@SuppressWarnings("DuplicatedCode")
public final class PositionSearch {

    public record SearchNodeContext(int depth, int maxDepth, MoveAndWeight bestKnownPath,
                                    int weightFactor,
                                    float alphaWeight, float betaWeight, float materialWeight, float materialDelta,
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

    public record SearchNodeResult(GameResult result, float weight, boolean isTimeout) {

        public final static SearchNodeResult TIMEOUT = new SearchNodeResult(GameResult.ONGOING, 0, true);
        public final static SearchNodeResult INVALID = new SearchNodeResult(GameResult.ONGOING, -WeightingFunction.ILLEGAL_WEIGHT, false);

        public static SearchNodeResult create(GameResult result, float weight) {
            return new SearchNodeResult(result, weight, false);
        }

        public static SearchNodeResult create(GameResult result, float weight, float alpha, float beta) {
            return new SearchNodeResult(result, window(weight, alpha, beta), false);
        }

        public static SearchNodeResult draw(float alpha, float beta) {
            return new SearchNodeResult(GameResult.DRAW, window(0, alpha, beta), false);
        }

        private static float window(float weight, float alpha, float beta) {
            if (weight <= alpha) {
                return alpha;
            }
            return Math.min(weight, beta);
        }

        public static SearchNodeResult checkmateSelf(int depth, float alpha, float beta) {
            return new SearchNodeResult(GameResult.CHECKMATE, window(-(WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth), alpha, beta), false);
        }

        public static SearchNodeResult stalemate(int depth, float alpha, float beta) {
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
        this.quiescenceSearch = new QuiescenceSearch(game, moveGenerator, weightingFunction, statistics, engineConfig.getMaxQuiescenceDepth());
        this.weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        this.silent = engineConfig.isSilent();
        this.timeout = System.currentTimeMillis() + engineConfig.getSecondsPerMove() * 1000L;
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

        for (int depth = 1; depth <= maxDepth && !isTimeout(); depth++) {
            log("Current depth: " + depth);
            bestPath = calculateNextMove(depth, timeout, bestPath);
            MoveAndWeight m = bestPath.weightFactor(weightFactor);
            log("Depth: " + depth + ", move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight) + " [" + ChessUtil.pathToString(m.path) + "]");
            log("#positions: " + statistics.getPositionsCount() + ", #pruned: " + statistics.getPrunedMovesCount());
        }

        return bestPath;
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

        final float materialWeight = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final SearchNodeResult[] results = new SearchNodeResult[countMoves];
        final int[][] allPaths = new int[countMoves][pvMaxLength];
        final int[] pvTable = new int[pvMaxLength * pvMaxLength];
        float alphaWeight = Float.NEGATIVE_INFINITY;
        statistics.incrPositionCount();

        Arrays.fill(results, SearchNodeResult.INVALID);

        if (countMoves > 0 && bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]) {
            throw new IllegalStateException("First move must be the best known move. Expected: " + new Move(bestKnownNextMove) + ", actual: " + new Move(plainMoves[0]) + ", depth: 0");
        }

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            //log("Working on move " + ChessUtil.moveToString(move));

            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, 1);
            final float newMaterialWeight = materialWeight + moveWeight;
            boolean logWeight = false;

            pvTable[0] = move;
            workingBoard.makeMove(move);
            var result = alphaBetaSearch(new SearchNodeContext(1, maxDepth, bestKnownPath, -weightFactor, Float.NEGATIVE_INFINITY, -alphaWeight, -newMaterialWeight, -moveWeight, workingBoard, pvTable)).negate();
            if (result.isTimeout()) {
                return previousBestKnownPath;
            }
            bestKnownPath = null;
            workingBoard.revertMove();

            // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
            if (result.weight > -WeightingFunction.ILLEGAL_WEIGHT) {
                results[i] = result;
                System.arraycopy(pvTable, 0, allPaths[i], 0, pvMaxLength);

                //log("--> weight " + ChessUtil.weightToString(weight));
                if (result.weight > alphaWeight) {
                    alphaWeight = result.weight;
                    logWeight = true;
                }
            }

            log((i + 1) + "/" + countMoves + ": " + ChessUtil.moveToString(move) + (logWeight ? " " + ChessUtil.weightToString(result.weight, weightFactor) : ""));
            //log("quiescence: total=" + statistics.getQuiescencePositionsCount() + ", avg=" + statistics.getQuiescencePositionsCountAvg() + ", max=" + statistics.getQuiescencePositionsCountMax() + ", max depth: " + statistics.getMaximumReachedDepth());
        }

        float bestWeight = -WeightingFunction.ILLEGAL_WEIGHT;
        int bestMoveIndex = -1;

        for (int i = 0; i < countMoves; i++) {
            if (results[i].weight > bestWeight) {
                bestWeight = results[i].weight;
                bestMoveIndex = i;
            }
        }

        if (bestMoveIndex >= 0) {
            // Found a legal move
            return new MoveAndWeight(plainMoves[bestMoveIndex], results[bestMoveIndex].weight, results[bestMoveIndex].result, 0, allPaths[bestMoveIndex]);
        }

        // No legal move possible ==> checkmate or stalemate
        if (Game.testIsKingChecked(workingBoard, moveGenerator)) {
            return new MoveAndWeight(0, -WeightingFunction.CHECKMATE_WEIGHT_HIGH, GameResult.CHECKMATE, 0, new int[0]);
        } else {
            return new MoveAndWeight(0, 0f, GameResult.STALEMATE, 0, new int[0]);
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
        if (result.weight == -WeightingFunction.ILLEGAL_WEIGHT
            || result.weight == Float.NEGATIVE_INFINITY
            || result.weight == Float.POSITIVE_INFINITY) {
                // TODO remove
                throw new IllegalStateException("Unexpected weight " + result.weight + " returned, depth=" + ctx.depth() + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
        }

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
        pvTable[ctx.pvParentIndex() + depth] = 0;
        SearchNodeResult bestResult = SearchNodeResult.create(GameResult.ONGOING, ctx.alphaWeight);

        if (WeightingFunction.isIllegalWeight(ctx.alphaWeight()) || WeightingFunction.isIllegalWeight(ctx.betaWeight())) {
            // TODO remove
            throw new IllegalStateException("ILLEGAL_WEIGHT as alpha/beta; depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
        }

        if ((engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) || (engineConfig.isEnableThreefoldRepetition() && ctx.workingBoard.isThreefoldRepetition())) {
            return SearchNodeResult.draw(ctx.alphaWeight(), ctx.betaWeight());
        }

        if (depth == ctx.maxDepth) {
            return SearchNodeResult.create(GameResult.ONGOING, quiescenceSearch(ctx), ctx.alphaWeight(), ctx.betaWeight());
        }

        final int bestKnownNextMove = getMoveAtDepth(bestKnownPath, depth);
        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard, depth, bestKnownNextMove);
        if (moves.isIllegal()) {
            return SearchNodeResult.create(GameResult.ONGOING, WeightingFunction.ILLEGAL_WEIGHT, ctx.alphaWeight, ctx.betaWeight);
        }
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();

        if (bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]) {
            throw new IllegalStateException("First move must be the best known move. Expected: " + new Move(bestKnownNextMove) + ", actual: " + new Move(plainMoves[0]) + ", depth: " + depth);
        }

        if (task.isCanceled()) {
            throw new CancellationException();
        }

        boolean haveValidMoves = false;

        for (int i = 0; i < countMoves; i++) {
            if (isTimeout()) {
                return SearchNodeResult.TIMEOUT;
            }

            final int move = plainMoves[i];
            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = ctx.materialWeight + moveWeight;
            final float newMaterialDelta = ctx.materialDelta + moveWeight;

            pvTable[pvIndex] = move;
            ctx.workingBoard.makeMove(move);
            var result = alphaBetaSearch(new SearchNodeContext(depth + 1, ctx.maxDepth, bestKnownPath, -ctx.weightFactor, -ctx.betaWeight, -bestResult.weight, -newMaterialWeight, -newMaterialDelta, ctx.workingBoard, pvTable)).negate();
            ctx.workingBoard.revertMove();
            if (result.isTimeout()) {
                return SearchNodeResult.TIMEOUT;
            }
            final float weight = result.weight;
            bestKnownPath = null;

            // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
            if (weight > -WeightingFunction.ILLEGAL_WEIGHT) {
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
        return Game.testIsKingChecked(ctx.workingBoard, moveGenerator) ?
                SearchNodeResult.checkmateSelf(ctx.depth(), alpha, ctx.betaWeight()) :
                SearchNodeResult.stalemate(ctx.depth(), alpha, ctx.betaWeight());
    }

    private float quiescenceSearch(SearchNodeContext ctx) {
        var workingBoard = ctx.workingBoard;
        final int lastMove = workingBoard.getGameStatus().getLastMove();

        if (Move.getCapturedPiece(lastMove) == 0) {
            return calculatePositionWeight(workingBoard, ctx.weightFactor, ctx.materialWeight, ctx.materialDelta);
        } else {
            return quiescenceSearch.quiescenceSearch(workingBoard, Move.getToField(lastMove), ctx.depth, ctx.weightFactor, ctx.alphaWeight, ctx.betaWeight, ctx.materialWeight, ctx.materialDelta);
        }
    }

    private float calculatePositionWeight(final Board workingBoard, final int weightFactor, final float materialWeight, final float materialDelta) {
        if (materialDelta > 2.0f || materialDelta < -2.0f) {
            return materialWeight;
        }
        return weightingFunction.calculate(workingBoard) * weightFactor;
    }

    private void log(String s) {
        if (!silent) {
            System.out.println(s);
        }
    }
}
