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

import java.util.concurrent.CancellationException;

@SuppressWarnings("DuplicatedCode")
public final class PositionSearch {

    public record SearchNodeContext(int depth, int maxDepth, MoveAndWeight bestKnownPath,
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

    public enum NodeState {
        ALPHA_CUTOFF,
        BETA_CUTOFF,
        COMPLETE,
        UNKNOWN
    }

    public record SearchNodeResult(GameResult result, float weight, NodeState state) {
        public final static SearchNodeResult ILLEGAL = new SearchNodeResult(GameResult.ONGOING, WeightingFunction.ILLEGAL_WEIGHT, NodeState.COMPLETE);
        public final static SearchNodeResult DRAW = new SearchNodeResult(GameResult.DRAW, 0, NodeState.COMPLETE);
        public final static SearchNodeResult STALEMATE = new SearchNodeResult(GameResult.STALEMATE, 0, NodeState.COMPLETE);

        public static SearchNodeResult checkmateSelf(int depth) {
            return new SearchNodeResult(GameResult.CHECKMATE, -(WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth), NodeState.COMPLETE);
        }

        public static SearchNodeResult checkmateOpposite(int depth) {
            return new SearchNodeResult(GameResult.CHECKMATE, WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth, NodeState.COMPLETE);
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

    private PositionSearch(ChessEngine engine, NextMoveTask task, Game game) {
        this.task = task;
        this.game = game;
        this.moveGenerator = new MoveGenerator(new MoveSorterImpl(killerMoves));
        this.engineConfig = engine.getConfig();
        this.quiescenceSearch = new QuiescenceSearch(game, moveGenerator, weightingFunction, statistics, engineConfig.getMaxQuiescenceDepth());
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

    private MoveAndWeight calculateNextMove() {
        MoveAndWeight bestPath = null;
        final int maxDepth = engineConfig.getMaxDepth();

        for (int depth = 1; depth <= maxDepth; depth++) {
            log("Current depth: " + depth);
            bestPath = calculateNextMove(depth, bestPath);
            MoveAndWeight m = bestPath.weightFactor(weightFactor);
            log("Depth: " + depth + ", move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight) + " [" + ChessUtil.pathToString(m.path) + "]");
            log("#positions: " + statistics.getPositionsCount() + ", #pruned: " + statistics.getPrunedMovesCount());
        }

        return bestPath;
    }

    @SuppressWarnings("Duplicates")
    private MoveAndWeight calculateNextMove(int maxDepth, MoveAndWeight bestKnownPath) {
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

        if (countMoves > 0 && bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]) {
            throw new IllegalStateException("First move must be the best known move. Expected: " + new Move(bestKnownNextMove) + ", actual: " + new Move(plainMoves[0]) + ", depth: 0");
        }

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            //log("Working on move " + ChessUtil.moveToString(move));

            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, 1);
            final float newMaterialDelta = WeightingFunction.getMaterialWeightOfMove(move, 1);
            final float newMaterialWeight = materialWeight + moveWeight;

            pvTable[0] = move;
            workingBoard.makeMove(move);
            var result = minSearch(new SearchNodeContext(1, maxDepth, bestKnownPath, alphaWeight, Float.POSITIVE_INFINITY, newMaterialWeight, newMaterialDelta, workingBoard, pvTable));
            bestKnownPath = null;
            workingBoard.revertMove();
            results[i] = result;
            System.arraycopy(pvTable, 0, allPaths[i], 0, pvMaxLength);

            //log("--> weight " + ChessUtil.weightToString(weight));
            if (result.weight > alphaWeight) {
                alphaWeight = result.weight;
            }

            var weightLogStr = result.state == NodeState.COMPLETE ? ", weight=" + ChessUtil.weightToString(result.weight, weightFactor) : "";
            log((i + 1) + "/" + countMoves + ": " + ChessUtil.moveToString(move) + weightLogStr);
            //log("quiescence: total=" + statistics.getQuiescencePositionsCount() + ", avg=" + statistics.getQuiescencePositionsCountAvg() + ", max=" + statistics.getQuiescencePositionsCountMax() + ", max depth: " + statistics.getMaximumReachedDepth());
        }

        float bestWeight = Float.NEGATIVE_INFINITY;
        int bestMoveIndex = -1;

        for (int i = 0; i < countMoves; i++) {
            if (results[i].state == NodeState.UNKNOWN) {
                throw new IllegalStateException("Unknown node state for move " + ChessUtil.moveToString(plainMoves[i]));
            }
            if (results[i].weight > bestWeight) {
                bestWeight = results[i].weight;
                bestMoveIndex = i;
            }
        }

        if (bestMoveIndex >= 0) {
            // Return the best move
            return new MoveAndWeight(plainMoves[bestMoveIndex], results[bestMoveIndex].weight, results[bestMoveIndex].result, 0, allPaths[bestMoveIndex]);
        } else if (Game.testIsKingChecked(workingBoard, moveGenerator)) {
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

    @SuppressWarnings("Duplicates")
    private SearchNodeResult maxSearch(final SearchNodeContext ctx) {
        final int depth = ctx.depth;
        MoveAndWeight bestKnownPath = ctx.bestKnownPath;
        final GameStatus gameStatus = ctx.workingBoard.getGameStatus();
        statistics.incrPositionCount();
        final var pvTable = ctx.pvTable;
        final int pvIndex = ctx.pvIndex();
        pvTable[ctx.pvParentIndex() + depth] = 0;
        SearchNodeResult bestResult = new SearchNodeResult(GameResult.ONGOING, ctx.alphaWeight, NodeState.UNKNOWN);

        if (ctx.alphaWeight == Float.POSITIVE_INFINITY || ctx.betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
        }
        if ((engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) || (engineConfig.isEnableThreefoldRepetition() && ctx.workingBoard.isThreefoldRepetition())) {
            return SearchNodeResult.DRAW;
        }

        if (depth == ctx.maxDepth) {
            return new SearchNodeResult(GameResult.ONGOING, quiescenceSearch(depth, true, ctx.workingBoard, ctx.materialWeight, ctx.materialDelta), NodeState.COMPLETE);
        }

        final int bestKnownNextMove = getMoveAtDepth(bestKnownPath, depth);
        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard, depth, bestKnownNextMove);
        if (moves.isIllegal()) {
            return SearchNodeResult.ILLEGAL;
        }
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        boolean haveValidMove = false;

        if (countMoves > 0 && bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]) {
            throw new IllegalStateException("First move must be the best known move. Expected: " + new Move(bestKnownNextMove) + ", actual: " + new Move(plainMoves[0]) + ", depth: " + depth);
        }

        if (task.isCanceled()) {
            throw new CancellationException();
        }

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = ctx.materialWeight + moveWeight;
            final float newMaterialDelta = ctx.materialDelta + moveWeight;

            pvTable[pvIndex] = move;
            ctx.workingBoard.makeMove(move);
            var result = minSearch(new SearchNodeContext(depth + 1, ctx.maxDepth, bestKnownPath, bestResult.weight, ctx.betaWeight, newMaterialWeight, newMaterialDelta, ctx.workingBoard, pvTable));
            final float weight = result.weight;
            bestKnownPath = null;
            ctx.workingBoard.revertMove();

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight >= ctx.betaWeight) {
                    statistics.incrPrunedMovesCount(countMoves - i - 1);
                    ctx.copyUpPV();
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return new SearchNodeResult(result.result, ctx.betaWeight, NodeState.BETA_CUTOFF);
                }

                if (weight > bestResult.weight) {
                    bestResult = result;
                    ctx.copyUpPV();
                }
            }
        }

        if (haveValidMove) {
            if (bestResult.weight == Float.POSITIVE_INFINITY || bestResult.weight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestResult.weight + ", depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
            }
            return bestResult;
        }

        // No legal move possible ==> Checkmate or stalemate
        if (Game.testIsKingChecked(ctx.workingBoard, moveGenerator)) {
            // Computer checkmate
            return SearchNodeResult.checkmateSelf(depth);
        }

        // Stalemate
        return SearchNodeResult.STALEMATE;
    }

    @SuppressWarnings("Duplicates")
    private SearchNodeResult minSearch(final SearchNodeContext ctx) {
        final int depth = ctx.depth;
        MoveAndWeight bestKnownPath = ctx.bestKnownPath;
        final GameStatus gameStatus = ctx.workingBoard.getGameStatus();
        statistics.incrPositionCount();
        final var pvTable = ctx.pvTable;
        final int pvIndex = ctx.pvIndex();
        pvTable[ctx.pvParentIndex() + depth] = 0;
        SearchNodeResult bestResult = new SearchNodeResult(GameResult.ONGOING, ctx.betaWeight, NodeState.UNKNOWN);

        if (ctx.alphaWeight == Float.POSITIVE_INFINITY || ctx.betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
        }
        if ((engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) || (engineConfig.isEnableThreefoldRepetition() && ctx.workingBoard.isThreefoldRepetition())) {
            return SearchNodeResult.DRAW;
        }

        if (depth == ctx.maxDepth) {
            return new SearchNodeResult(GameResult.ONGOING, quiescenceSearch(depth, false, ctx.workingBoard, ctx.materialWeight, ctx.materialDelta), NodeState.COMPLETE);
        }

        final int bestKnownNextMove = getMoveAtDepth(bestKnownPath, depth);
        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard, depth, bestKnownNextMove);
        if (moves.isIllegal()) {
            return SearchNodeResult.ILLEGAL;
        }
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        boolean haveValidMove = false;

        if (countMoves > 0 && bestKnownNextMove != 0 && bestKnownNextMove != plainMoves[0]) {
            throw new IllegalStateException("First move must be the best known move. Expected: " + new Move(bestKnownNextMove) + ", actual: " + new Move(plainMoves[0]) + ", depth: " + depth);
        }

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = ctx.materialWeight - moveWeight;
            final float newMaterialDelta = ctx.materialDelta - moveWeight;

            pvTable[pvIndex] = move;
            ctx.workingBoard.makeMove(move);
            var result = maxSearch(new SearchNodeContext(depth + 1, ctx.maxDepth, bestKnownPath, ctx.alphaWeight, bestResult.weight, newMaterialWeight, newMaterialDelta, ctx.workingBoard, pvTable));
            final float weight = result.weight;
            bestKnownPath = null;
            ctx.workingBoard.revertMove();

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight <= ctx.alphaWeight) {
                    statistics.incrPrunedMovesCount(countMoves - i - 1);
                    ctx.copyUpPV();
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return new SearchNodeResult(result.result, ctx.alphaWeight, NodeState.ALPHA_CUTOFF);
                }

                if (weight < bestResult.weight) {
                    bestResult = result;
                    ctx.copyUpPV();
                }
            }
        }

        if (haveValidMove) {
            if (bestResult.weight == Float.POSITIVE_INFINITY || bestResult.weight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestResult.weight + ", depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
            }
            return bestResult;
        }

        // No legal move possible ==> Checkmate or stalemate
        if (Game.testIsKingChecked(ctx.workingBoard, moveGenerator)) {
            // Opposite checkmate
            return SearchNodeResult.checkmateOpposite(depth);
        }

        // Stalemate
        return SearchNodeResult.STALEMATE;
    }

    private float quiescenceSearch(final int depth, final boolean isMax, final Board workingBoard, final float materialWeight, final float materialDelta) {
        final int lastMove = workingBoard.getGameStatus().getLastMove();

        if (Move.getCapturedPiece(lastMove) == 0) {
            return calculatePositionWeight(workingBoard, materialWeight, materialDelta);
        } else if (isMax) {
            return quiescenceSearch.quiescenceMaxSearch(workingBoard, Move.getToField(lastMove), depth, materialWeight, materialDelta);
        } else {
            return quiescenceSearch.quiescenceMinSearch(workingBoard, Move.getToField(lastMove), depth, materialWeight, materialDelta);
        }
    }

    private float calculatePositionWeight(final Board workingBoard, final float materialWeight, final float materialDelta) {
        if (materialDelta > 2.0f || materialDelta < -2.0f) {
            return materialWeight;
        }
        float weight = weightingFunction.calculate(workingBoard);
        return weight != WeightingFunction.ILLEGAL_WEIGHT ? weight * weightFactor : WeightingFunction.ILLEGAL_WEIGHT;
    }

    private void log(String s) {
        if (!silent) {
            System.out.println(s);
        }
    }
}
