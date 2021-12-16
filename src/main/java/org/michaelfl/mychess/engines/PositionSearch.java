package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MovesCounter;
import org.michaelfl.mychess.QuiescenceSearch;
import org.michaelfl.mychess.Statistics;
import org.michaelfl.mychess.WeightingFunction;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;

@SuppressWarnings("DuplicatedCode")
final class PositionSearch {

    private final float MAX_PER_MOVE_HANDICAP = 0.3f;

    private final NextMoveTask task;
    private final Game game;
    private final EngineConfig engineConfig;
    private final MovesCounter killerMoves = new MovesCounter(2);
    private final MoveGenerator moveGenerator;
    private final WeightingFunction weightingFunction = new WeightingFunction();
    private final QuiescenceSearch quiescenceSearch;
    private final int weightFactor;
    private final Statistics statistics = new Statistics();
    private boolean silent;
    private final Random rand = new Random();

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
            throw new IllegalStateException("Illegal chess position");
        }

        final float materialWeight = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final GameResult[] result = new GameResult[1];
        final float[] weights = new float[countMoves];
        final GameResult[] gameResults = new GameResult[countMoves];
        final int[][] allPaths = new int[countMoves][maxPathLength];
        float alphaWeight = Float.NEGATIVE_INFINITY;
        statistics.incrPositionCount();
        final GameStatus gameStatus = game.getGameStatus();
        final float handicap = gameStatus.getHandicap();
        final boolean useAlphaPruning = !(engineConfig.isUseHandicap() && handicap > 0f);

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            //log("Working on move " + ChessUtil.moveToString(move));

            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, 1);
            final float newMaterialDelta = WeightingFunction.getMaterialWeightOfMove(move, 1);
            final float newMaterialWeight = materialWeight + moveWeight;

            workingPath[0] = move;
            workingBoard.makeMove(move);
            float weight = minSearch(1, maxDepth, bestKnownPath, alphaWeight, Float.POSITIVE_INFINITY, newMaterialWeight, newMaterialDelta, workingBoard, result, workingPath);
            bestKnownPath = null;
            workingBoard.revertMove();
            weights[i] = weight;
            gameResults[i] = result[0];
            System.arraycopy(workingPath, 0, allPaths[i], 0, maxPathLength);

            //log("--> weight " + ChessUtil.weightToString(weight));
            if (useAlphaPruning && weight > alphaWeight) {
                alphaWeight = weight;
            }

            // Find and store current killer moves
            killerMoves.sample();
            log((i + 1) + "/" + countMoves + ": " + ChessUtil.moveToString(move) + ", weight=" + ChessUtil.weightToString(weight, weightFactor));
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
            if (useAlphaPruning) {
                // Return the best move
                return new MoveAndWeight(plainMoves[bestMoveIndex], weights[bestMoveIndex], gameResults[bestMoveIndex], 0, allPaths[bestMoveIndex]);
            }

            List<MoveAndWeight> moveCandidates = new ArrayList<>(countMoves);
            final float variance = Math.min(handicap, MAX_PER_MOVE_HANDICAP);
            final float lowerWeightLimit = bestWeight - variance;
            for (int i = 0; i < countMoves; i++) {
                if (weights[i] >= lowerWeightLimit) {
                    moveCandidates.add(new MoveAndWeight(plainMoves[i], weights[i], gameResults[i], bestWeight - weights[i], allPaths[i]));
                }
            }

            // Choose one of the move candidates randomly
            return moveCandidates.get(rand.nextInt(moveCandidates.size()));

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
    private float maxSearch(final int depth, final int maxDepth, MoveAndWeight bestKnownPath, final float alphaWeight, final float betaWeight, final float materialWeight, final float materialDelta, final Board workingBoard, final GameResult[] resultOut, final int[] bestPathOut) {
        final GameStatus gameStatus = workingBoard.getGameStatus();
        statistics.incrPositionCount();
        bestPathOut[depth] = 0;
        resultOut[0] = GameResult.ONGOING;

        if (alphaWeight == Float.POSITIVE_INFINITY || betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }
        if (gameStatus.getHalfMoveClock() >= 100 || workingBoard.isThreefoldRepetition()) {
            resultOut[0] = GameResult.DRAW;
            return 0; // draw
        }

        if (depth == maxDepth) {
            return quiescenceSearch(depth, true, workingBoard, materialWeight, materialDelta);
        }

        final Moves moves = moveGenerator.calculateMoves(workingBoard, depth, getMoveAtDepth(bestKnownPath, depth));
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] workingPath = new int[bestPathOut.length];
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = alphaWeight; // Float.NEGATIVE_INFINITY
        boolean haveValidMove = false;
        final GameResult[] result = new GameResult[1];

        if (task.isCanceled()) {
            throw new CancellationException();
        }

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = materialWeight + moveWeight;
            final float newMaterialDelta = materialDelta + moveWeight;

            workingBoard.makeMove(move);
            final float weight = minSearch(depth + 1, maxDepth, bestKnownPath, bestWeight, betaWeight, newMaterialWeight, newMaterialDelta, workingBoard, result, workingPath);
            bestKnownPath = null;
            workingBoard.revertMove();

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight >= betaWeight) {
                    statistics.incrPrunedMovesCount(countMoves - i - 1);
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return weight;
                }

                if (weight > bestWeight) {
                    bestWeight = weight;
                    resultOut[0] = result[0];
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                }
            }
        }

        if (haveValidMove) {
            if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
            }
            return bestWeight;
        }

        // No legal move possible ==> Checkmate or stalemate
        if (Game.testIsKingChecked(workingBoard, moveGenerator)) {
            // Computer checkmate
            resultOut[0] = GameResult.CHECKMATE;
            return -(WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth);
        }

        // Stalemate
        resultOut[0] = GameResult.STALEMATE;
        return 0; // draw
    }

    @SuppressWarnings("Duplicates")
    private float minSearch(final int depth, final int maxDepth, MoveAndWeight bestKnownPath, final float alphaWeight, final float betaWeight, final float materialWeight, final float materialDelta, final Board workingBoard, final GameResult[] resultOut, final int[] bestPathOut) {
        final GameStatus gameStatus = workingBoard.getGameStatus();
        statistics.incrPositionCount();
        bestPathOut[depth] = 0;
        resultOut[0] = GameResult.ONGOING;

        if (alphaWeight == Float.POSITIVE_INFINITY || betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }
        if (gameStatus.getHalfMoveClock() >= 100 || workingBoard.isThreefoldRepetition()) {
            resultOut[0] = GameResult.DRAW;
            return 0; // draw
        }

        if (depth == maxDepth) {
            return quiescenceSearch(depth, false, workingBoard, materialWeight, materialDelta);
        }

        final Moves moves = moveGenerator.calculateMoves(workingBoard, depth, getMoveAtDepth(bestKnownPath, depth));
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] workingPath = new int[bestPathOut.length];
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = betaWeight; // Float.POSITIVE_INFINITY
        boolean haveValidMove = false;
        final GameResult[] result = new GameResult[1];

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = materialWeight - moveWeight;
            final float newMaterialDelta = materialDelta - moveWeight;

            workingBoard.makeMove(move);
            final float weight = maxSearch(depth + 1, maxDepth, bestKnownPath, alphaWeight, bestWeight, newMaterialWeight, newMaterialDelta, workingBoard, result, workingPath);
            bestKnownPath = null;
            workingBoard.revertMove();

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight <= alphaWeight) {
                    statistics.incrPrunedMovesCount(countMoves - i - 1);
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return weight;
                }

                if (weight < bestWeight) {
                    bestWeight = weight;
                    resultOut[0] = result[0];
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                }
            }
        }

        if (haveValidMove) {
            if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
            }
            return bestWeight;
        }

        // No legal move possible ==> Checkmate or stalemate
        if (Game.testIsKingChecked(workingBoard, moveGenerator)) {
            // Opposite checkmate
            resultOut[0] = GameResult.CHECKMATE;
            return WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth;
        }

        // Stalemate
        resultOut[0] = GameResult.STALEMATE;
        return 0; // draw
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
