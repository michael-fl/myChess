package org.michaelfl.mychess.engines.v2;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.IllegalChessPositionException;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MovesCounter;
import org.michaelfl.mychess.Statistics;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.NextMoveTask;
import org.michaelfl.mychess.engines.v1.WeightingFunction1;

import java.util.concurrent.CancellationException;

@SuppressWarnings("DuplicatedCode")
public final class PositionSearch2 {

    public record SearchNodeContext(int depth, int maxDepth, MoveAndWeight bestKnownPath,
                                    float alphaWeight, float betaWeight, float materialWeight, float materialDelta,
                                    Board workingBoard, int[] path) {
    }

    public record SearchNodeResult(GameResult result, float weight) {
        public final static SearchNodeResult ILLEGAL = new SearchNodeResult(GameResult.ONGOING, WeightingFunction1.ILLEGAL_WEIGHT);
        public final static SearchNodeResult DRAW = new SearchNodeResult(GameResult.DRAW, 0);
        public final static SearchNodeResult STALEMATE = new SearchNodeResult(GameResult.STALEMATE, 0);

        public static SearchNodeResult checkmateSelf(int depth) {
            return new SearchNodeResult(GameResult.CHECKMATE, -(WeightingFunction1.CHECKMATE_WEIGHT_HIGH - depth));
        }

        public static SearchNodeResult checkmateOpposite(int depth) {
            return new SearchNodeResult(GameResult.CHECKMATE, WeightingFunction1.CHECKMATE_WEIGHT_HIGH - depth);
        }
    }

    private final NextMoveTask task;
    private final Game game;
    private final EngineConfig engineConfig;
    private final MovesCounter killerMoves = new MovesCounter(2);
    private final MoveGenerator moveGenerator;
    private final WeightingFunction1 weightingFunction = new WeightingFunction1();
    private final QuiescenceSearch2 quiescenceSearch;
    private final int weightFactor;
    private final Statistics statistics = new Statistics();
    private boolean silent;

    private PositionSearch2(ChessEngine engine, NextMoveTask task, Game game) {
        this.task = task;
        this.game = game;
        this.moveGenerator = new MoveGenerator(new MoveSorterImpl2(killerMoves));
        this.engineConfig = engine.getConfig();
        this.quiescenceSearch = new QuiescenceSearch2(game, moveGenerator, weightingFunction, statistics, engineConfig.getMaxQuiescenceDepth());
        this.weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        this.silent = engineConfig.isSilent();
    }

    public static MoveAndWeight calculateNextMove(ChessEngine engine, NextMoveTask task, Game game) {
        return new PositionSearch2(engine, task, game).calculateNextMove();
    }

    public static Moves getPossibleMoves(ChessEngine engine, Game game) {
        return new PositionSearch2(engine, new NextMoveTask(), game).getPossibleMoves();
    }

    private Moves getPossibleMoves() {
        return moveGenerator.calculateMoves(game.getBoard());
    }

    private MoveAndWeight calculateNextMove() {
        MoveAndWeight bestMove = null;
        if (engineConfig.getMaxDepth() > 6) {
            silent = true;
            bestMove = calculateNextMove(6, null);
            MoveAndWeight m2 = bestMove.weightFactor(weightFactor);
            log("depth: 6, move: " + ChessUtil.moveToString(m2.move) + ", weight: " + ChessUtil.weightToString(m2.weight) + " [" + ChessUtil.pathToString(m2.path) + "]");
        }

        silent = engineConfig.isSilent();
        bestMove = calculateNextMove(engineConfig.getMaxDepth(), bestMove);
        MoveAndWeight m2 = bestMove.weightFactor(weightFactor);
        log("depth: " + engineConfig.getMaxDepth() + ", move: " + ChessUtil.moveToString(m2.move) + ", weight: " + ChessUtil.weightToString(m2.weight) + " [" + ChessUtil.pathToString(m2.path) + "]");

        log("#positions: " + statistics.getPositionsCount() + ", #pruned: " + statistics.getPrunedMovesCount());

        return bestMove;
    }

    @SuppressWarnings("Duplicates")
    private MoveAndWeight calculateNextMove(int maxDepth, MoveAndWeight bestKnownPath) {
        final int maxPathLength = 50;
        final Board workingBoard = game.getBoard().copy();
        final int[] workingPath = new int[maxPathLength];

        final Moves moves = moveGenerator.calculateMoves(workingBoard, 0, getMoveAtDepth(bestKnownPath, 0));
        if (moves.isIllegal()) {
            throw new IllegalChessPositionException(workingBoard);
        }

        final float materialWeight = weightFactor * WeightingFunction1.calculateMaterialWeight(workingBoard);
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final float[] weights = new float[countMoves];
        final GameResult[] gameResults = new GameResult[countMoves];
        final int[][] allPaths = new int[countMoves][maxPathLength];
        float alphaWeight = Float.NEGATIVE_INFINITY;
        statistics.incrPositionCount();

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            //log("Working on move " + ChessUtil.moveToString(move));

            final float moveWeight = WeightingFunction1.getMaterialWeightOfMove(move, 1);
            final float newMaterialDelta = WeightingFunction1.getMaterialWeightOfMove(move, 1);
            final float newMaterialWeight = materialWeight + moveWeight;

            workingPath[0] = move;
            workingBoard.makeMove(move);
            var result = minSearch(new SearchNodeContext(1, maxDepth, bestKnownPath, alphaWeight, Float.POSITIVE_INFINITY, newMaterialWeight, newMaterialDelta, workingBoard, workingPath));
            bestKnownPath = null;
            workingBoard.revertMove();
            weights[i] = result.weight;
            gameResults[i] = result.result;
            System.arraycopy(workingPath, 0, allPaths[i], 0, maxPathLength);

            //log("--> weight " + ChessUtil.weightToString(weight));
            if (result.weight > alphaWeight) {
                alphaWeight = result.weight;
            }

            log((i + 1) + "/" + countMoves + ": " + ChessUtil.moveToString(move) + ", weight=" + ChessUtil.weightToString(result.weight, weightFactor));
            //log("quiescence: total=" + statistics.getQuiescencePositionsCount() + ", avg=" + statistics.getQuiescencePositionsCountAvg() + ", max=" + statistics.getQuiescencePositionsCountMax() + ", max depth: " + statistics.getMaximumReachedDepth());
        }

        float bestWeight = Float.NEGATIVE_INFINITY;
        int bestMoveIndex = -1;

        for (int i = 0; i < countMoves; i++) {
            if (weights[i] > bestWeight) {
                bestWeight = weights[i];
                bestMoveIndex = i;
            }
        }

        if (bestMoveIndex >= 0) {
            // Return the best move
            return new MoveAndWeight(plainMoves[bestMoveIndex], weights[bestMoveIndex], gameResults[bestMoveIndex], 0, allPaths[bestMoveIndex]);
        } else if (Game.testIsKingChecked(workingBoard, moveGenerator)) {
            return new MoveAndWeight(0, -WeightingFunction1.CHECKMATE_WEIGHT_HIGH, GameResult.CHECKMATE, 0, new int[0]);
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
        final var bestPath = ctx.path;
        bestPath[depth] = 0;
        GameResult bestResult = GameResult.ONGOING;

        if (ctx.alphaWeight == Float.POSITIVE_INFINITY || ctx.betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
        }
        if ((engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) || (engineConfig.isEnableThreefoldRepetition() && ctx.workingBoard.isThreefoldRepetition())) {
            return SearchNodeResult.DRAW;
        }

        if (depth == ctx.maxDepth) {
            return new SearchNodeResult(bestResult, quiescenceSearch(depth, true, ctx.workingBoard, ctx.materialWeight, ctx.materialDelta));
        }

        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard, depth, getMoveAtDepth(bestKnownPath, depth));
        if (moves.isIllegal()) {
            return SearchNodeResult.ILLEGAL;
        }
        final int[] workingPath = new int[ctx.path.length];
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = ctx.alphaWeight; // Float.NEGATIVE_INFINITY
        boolean haveValidMove = false;

        if (task.isCanceled()) {
            throw new CancellationException();
        }

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            final float moveWeight = WeightingFunction1.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = ctx.materialWeight + moveWeight;
            final float newMaterialDelta = ctx.materialDelta + moveWeight;

            ctx.workingBoard.makeMove(move);
            var result = minSearch(new SearchNodeContext(depth + 1, ctx.maxDepth, bestKnownPath, bestWeight, ctx.betaWeight, newMaterialWeight, newMaterialDelta, ctx.workingBoard, workingPath));
            final float weight = result.weight;
            bestKnownPath = null;
            ctx.workingBoard.revertMove();

            if (weight != WeightingFunction1.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight >= ctx.betaWeight) {
                    statistics.incrPrunedMovesCount(countMoves - i - 1);
                    System.arraycopy(workingPath, depth, bestPath, depth, bestPath.length - depth);
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return new SearchNodeResult(bestResult, weight);
                }

                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestResult = result.result;
                    System.arraycopy(workingPath, depth, bestPath, depth, bestPath.length - depth);
                }
            }
        }

        if (haveValidMove) {
            //noinspection ConstantConditions
            if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
            }
            return new SearchNodeResult(bestResult, bestWeight);
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
        final var bestPath = ctx.path;
        bestPath[depth] = 0;
        GameResult bestResult = GameResult.ONGOING;

        if (ctx.alphaWeight == Float.POSITIVE_INFINITY || ctx.betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
        }
        if ((engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) || (engineConfig.isEnableThreefoldRepetition() && ctx.workingBoard.isThreefoldRepetition())) {
            return SearchNodeResult.DRAW;
        }

        if (depth == ctx.maxDepth) {
            return new SearchNodeResult(bestResult, quiescenceSearch(depth, false, ctx.workingBoard, ctx.materialWeight, ctx.materialDelta));
        }

        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard, depth, getMoveAtDepth(bestKnownPath, depth));
        if (moves.isIllegal()) {
            return SearchNodeResult.ILLEGAL;
        }
        final int[] workingPath = new int[bestPath.length];
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = ctx.betaWeight; // Float.POSITIVE_INFINITY
        boolean haveValidMove = false;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            final float moveWeight = WeightingFunction1.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = ctx.materialWeight - moveWeight;
            final float newMaterialDelta = ctx.materialDelta - moveWeight;

            ctx.workingBoard.makeMove(move);
            var result = maxSearch(new SearchNodeContext(depth + 1, ctx.maxDepth, bestKnownPath, ctx.alphaWeight, bestWeight, newMaterialWeight, newMaterialDelta, ctx.workingBoard, workingPath));
            final float weight = result.weight;
            bestKnownPath = null;
            ctx.workingBoard.revertMove();

            if (weight != WeightingFunction1.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight <= ctx.alphaWeight) {
                    statistics.incrPrunedMovesCount(countMoves - i - 1);
                    System.arraycopy(workingPath, depth, bestPath, depth, bestPath.length - depth);
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return new SearchNodeResult(bestResult, weight);
                }

                if (weight < bestWeight) {
                    bestWeight = weight;
                    bestResult = result.result;
                    System.arraycopy(workingPath, depth, bestPath, depth, bestPath.length - depth);
                }
            }
        }

        if (haveValidMove) {
            if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + ", alphaWeight=" + ctx.alphaWeight + ", betaWeight=" + ctx.betaWeight + "\n" + ctx.workingBoard);
            }
            return new SearchNodeResult(bestResult, bestWeight);
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
        return weight != WeightingFunction1.ILLEGAL_WEIGHT ? weight * weightFactor : WeightingFunction1.ILLEGAL_WEIGHT;
    }

    private void log(String s) {
        if (!silent) {
            System.out.println(s);
        }
    }
}
