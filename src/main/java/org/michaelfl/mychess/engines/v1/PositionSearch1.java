package org.michaelfl.mychess.engines.v1;

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
import org.michaelfl.mychess.WeightingFunction;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.NextMoveTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CancellationException;

@SuppressWarnings("DuplicatedCode")
final class PositionSearch1 {

    private final NextMoveTask task;
    private final Game game;
    private final EngineConfig engineConfig;
    private final MovesCounter killerMoves = new MovesCounter(2);
    private final MoveGenerator moveGenerator;
    private final WeightingFunction1 weightingFunction = new WeightingFunction1();
    private final int weightFactor;

    private long positionsCount;
    private long prunedPositionsCount;
    private int maximumReachedDepth = 0;

    private PositionSearch1(ChessEngine engine, NextMoveTask task, Game game) {
        this.task = task;
        this.game = game;
        this.moveGenerator = new MoveGenerator(new MoveSorterImpl1(engine.getRandom(), killerMoves, new MovesCounter(0)));
        this.engineConfig = engine.getConfig();
        this.weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
    }

    public static MoveAndWeight calculateNextMove(ChessEngine engine, NextMoveTask task, Game game) {
        return new PositionSearch1(engine, task, game).calculateNextMove();
    }

    public static Moves getPossibleMoves(ChessEngine engine, Game game) {
        return new PositionSearch1(engine, new NextMoveTask(), game).getPossibleMoves();
    }

    private Moves getPossibleMoves() {
        return moveGenerator.calculateMoves(game.getBoard());
    }

    @SuppressWarnings("Duplicates")
    private MoveAndWeight calculateNextMove() {
        final Board workingBoard = game.getBoard().copy();

        final Moves moves = moveGenerator.calculateMoves(workingBoard);
        if (moves.isIllegal() || moves.count() == 0)
            return MoveAndWeight.NO_MOVE; // No move possible

        final int nVariants = engineConfig.getNVariants();
        final int iterationDepth = engineConfig.getIterationDepth();
        final int maxDepth = engineConfig.getMaxDepth();
        final ArrayList<Integer> skipMoves = new ArrayList<>(nVariants);
        final MoveAndWeight[] bestMoves = new MoveAndWeight[nVariants];

        Arrays.fill(bestMoves, MoveAndWeight.NO_MOVE);

        for (int i = 0; i < nVariants; i++) {
            log("VARIANT " + (i+1) + "...");
            MoveAndWeight nextBestMove = findNextBestMove(workingBoard, moves, skipMoves);
            var m = bestMoves[i] = nextBestMove;
            if (nextBestMove == MoveAndWeight.NO_MOVE)
                break;
            log("move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight * weightFactor) + " [" + ChessUtil.pathToString(m.path) + "]");
            skipMoves.add(nextBestMove.move);
        }

        log("#positions: " + positionsCount + ", #pruned: " + prunedPositionsCount);

        for (int depth = 1; depth + iterationDepth < maxDepth; depth++) {
            log("DEPTH: " + (depth + iterationDepth) + "/" + maxDepth);
            for (int i = 0; i < nVariants; i++) {
                if (bestMoves[i] != MoveAndWeight.NO_MOVE) {
                    if (bestMoves[i].path[depth] != 0) {
                        log("DEEPEN VARIANT " + (i + 1) + "...");
                        var m = bestMoves[i] = deepenPath(depth, workingBoard, bestMoves[i]);
                        log("move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight * weightFactor) + " [" + ChessUtil.pathToString(m.path) + "]");
                    } else {
                        bestMoves[i].path[depth + 1] = 0;
                    }
                }
            }
        }

        for (int i = 0; i < nVariants; i++) {
            if (bestMoves[i] != MoveAndWeight.NO_MOVE) {
                MoveAndWeight m = bestMoves[i];
                if (WeightingFunction.isCheckmateWeight(m.weight) && m.result != GameResult.CHECKMATE) {
                    bestMoves[i] = new MoveAndWeight(m.move, m.weight, GameResult.CHECKMATE, m.path);
                }
            }
        }

        if (bestMoves[0] != MoveAndWeight.NO_MOVE) {
            log("\nBEST MOVES:");
            sortByWeightDescending(bestMoves);

            for (int i = 0; i < nVariants; i++) {
                if (bestMoves[i] != MoveAndWeight.NO_MOVE) {
                    MoveAndWeight m = bestMoves[i].weightFactor(weightFactor);
                    log((i + 1) + ". move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight) + " [" + ChessUtil.pathToString(m.path) + "]");
                }
            }

            return bestMoves[0];
        }

        return MoveAndWeight.NO_MOVE;
    }

    private static void sortByWeightDescending(MoveAndWeight[] bestMoves) {
        Arrays.sort(bestMoves, Comparator.comparingDouble(m -> m.move != 0 ? -m.weight : Double.MAX_VALUE));
    }

    private MoveAndWeight deepenPath(int startDepth, Board workingBoard, MoveAndWeight moveAndWeight) {
        int[] path = moveAndWeight.path;

        float weight = continueSearch(path, startDepth, workingBoard);

        return new MoveAndWeight(moveAndWeight.move, weight, moveAndWeight.result, path);
    }

    @SuppressWarnings("Duplicates")
    private MoveAndWeight findNextBestMove(Board workingBoard, Moves moves, ArrayList<Integer> skipMoves) {
        final int[] bestPath = new int[50];
        final int[] workingPath = new int[bestPath.length];

        final float materialWeight = weightFactor * WeightingFunction1.calculateMaterialWeight(workingBoard);
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = Float.NEGATIVE_INFINITY;
        final float betaWeight = Float.POSITIVE_INFINITY;
        final int iterationDepth = engineConfig.getIterationDepth();
        int bestMove = 0;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            if (skipMoves.contains(move))
                continue;
            //log("Working on move " + ChessUtil.moveToString(move));

            final float moveWeight = WeightingFunction1.getMaterialWeightOfMove(move, 1);
            final float newMaterialDelta = WeightingFunction1.getMaterialWeightOfMove(move, 1);
            final float newMaterialWeight = materialWeight + moveWeight;

            workingPath[0] = move;
            positionsCount++;
            workingBoard.makeMove(move);
            float weight = minSearch(1, iterationDepth, bestWeight, betaWeight, newMaterialWeight, newMaterialDelta, workingBoard, workingPath, false);
            workingBoard.revertMove();
            //log("--> weight " + ChessUtil.weightToString(weight));
            if (weight != WeightingFunction1.ILLEGAL_WEIGHT) {
                //log("  " + ChessUtil.moveToString(move) + " ==> " + ChessUtil.weightToString(factor * weight) + " (" + move + ") [" + ChessUtil.pathToString(workingPath) + "]");
                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestMove = move;
                    System.arraycopy(workingPath, 0, bestPath, 0, bestPath.length);
                }
            }

            // Find and store current killer moves
            killerMoves.sample();
        }

        if (bestMove != 0)
            return new MoveAndWeight(bestMove, bestWeight, GameResult.ONGOING, bestPath);

        return MoveAndWeight.NO_MOVE;
    }

    private float continueSearch(final int[] bestPathInOut, final int continueOnDepth, final Board workingBoard) {
        if (continueOnDepth == 0)
            throw new IllegalArgumentException();

        for (int depth = 0; depth < continueOnDepth; depth++) {
            final int move = bestPathInOut[depth];
            workingBoard.makeMove(move);
        }

        float materialWeight = weightFactor * WeightingFunction1.calculateMaterialWeight(workingBoard);

        int maxDepth = continueOnDepth + engineConfig.getIterationDepth();
        float weight;
        if (continueOnDepth % 2 == 0)
            weight = maxSearch(continueOnDepth, maxDepth, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialWeight, 0f, workingBoard, bestPathInOut, true);
        else
            weight = minSearch(continueOnDepth, maxDepth, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialWeight, 0f, workingBoard, bestPathInOut, true);

        for (int depth = 0; depth < continueOnDepth; depth++) {
            workingBoard.revertMove();
        }

        return weight;
    }

    @SuppressWarnings("Duplicates")
    private float maxSearch(final int depth, final int maxDepth, final float alphaWeight, final float betaWeight, final float materialWeight, final float materialDelta, final Board workingBoard, final int[] bestPathOut, boolean doSamples) {
        final GameStatus gameStatus = workingBoard.getGameStatus();
        if (alphaWeight == Float.POSITIVE_INFINITY || betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }
        if (engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) {
            bestPathOut[depth] = 0;
            return 0; // draw
        }

        final int[] workingPath = new int[bestPathOut.length];

        if (depth == maxDepth) {
            final int lastMove = gameStatus.getLastMove();

            if (Move.getCapturedPiece(lastMove) == 0) {
                bestPathOut[depth] = 0;
                return calculatePositionWeight(workingBoard, materialWeight, materialDelta);
            }

            float weight = quiescenceMaxSearch(Move.getToField(lastMove), depth, maxDepth + engineConfig.getMaxQuiescenceDepth(), materialWeight, materialDelta, workingBoard, workingPath);
            System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
            return weight;
        }

        final Moves moves = moveGenerator.calculateMoves(workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction1.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = alphaWeight; // Float.NEGATIVE_INFINITY
        boolean haveValidMove = false;

        if (task.isCanceled()) {
            throw new CancellationException();
        }

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            positionsCount++;
            final float moveWeight = WeightingFunction1.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = materialWeight + moveWeight;
            final float newMaterialDelta = materialDelta + moveWeight;

            workingBoard.makeMove(move);
            final float weight = minSearch(depth + 1, maxDepth, bestWeight, betaWeight, newMaterialWeight, newMaterialDelta, workingBoard, workingPath, false);
            workingBoard.revertMove();

            if (weight != WeightingFunction1.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight >= betaWeight) {
                    prunedPositionsCount += countMoves - i - 1;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                    return weight;
                }

                if (weight > bestWeight) {
                    bestWeight = weight;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                }
            }

            if (doSamples)
                killerMoves.sample();
        }

        if (haveValidMove) {
            if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
            }
            return bestWeight;
        }

        // No legal move possible ==> Checkmate or stalemate
        bestPathOut[depth] = 0;
        if (Game.testIsKingChecked(workingBoard, moveGenerator)) {
            // Computer checkmate
            return -WeightingFunction1.checkmateIn(depth);
        }

        // Stalemate
        return 0; // draw
    }

    @SuppressWarnings("Duplicates")
    private float minSearch(final int depth, final int maxDepth, final float alphaWeight, final float betaWeight, final float materialWeight, final float materialDelta, final Board workingBoard, final int[] bestPathOut, boolean doSamples) {
        final GameStatus gameStatus = workingBoard.getGameStatus();
        if (alphaWeight == Float.POSITIVE_INFINITY || betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }
        if (engineConfig.isEnableFiftyMovesRule() && gameStatus.getHalfMoveClock() >= 100) {
            bestPathOut[depth] = 0;
            return 0; // draw
        }

        final int[] workingPath = new int[bestPathOut.length];

        if (depth == maxDepth) {
            final int lastMove = gameStatus.getLastMove();

            if (Move.getCapturedPiece(lastMove) == 0) {
                bestPathOut[depth] = 0;
                return calculatePositionWeight(workingBoard, materialWeight, materialDelta);
            }

            float weight = quiescenceMinSearch(Move.getToField(lastMove), depth, maxDepth + engineConfig.getMaxQuiescenceDepth(), materialWeight, materialDelta, workingBoard, workingPath);
            System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
            return weight;
        }

        final Moves moves = moveGenerator.calculateMoves(workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction1.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = betaWeight; // Float.POSITIVE_INFINITY
        boolean haveValidMove = false;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            positionsCount++;
            final float moveWeight = WeightingFunction1.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = materialWeight - moveWeight;
            final float newMaterialDelta = materialDelta - moveWeight;

            workingBoard.makeMove(move);
            final float weight = maxSearch(depth + 1, maxDepth, alphaWeight, bestWeight, newMaterialWeight, newMaterialDelta, workingBoard, workingPath, false);
            workingBoard.revertMove();

            if (weight != WeightingFunction1.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight <= alphaWeight) {
                    prunedPositionsCount += countMoves - i - 1;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                    killerMoves.addMove(move, depth);
                    return weight;
                }

                if (weight < bestWeight) {
                    bestWeight = weight;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                }
            }

            if (doSamples)
                killerMoves.sample();
        }

        if (haveValidMove) {
            if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
            }
            return bestWeight;
        }

        // No legal move possible ==> Checkmate or stalemate
        bestPathOut[depth] = 0;
        if (Game.testIsKingChecked(workingBoard, moveGenerator)) {
            // Opposite checkmate
            return WeightingFunction1.checkmateIn(depth);
        }

        // Stalemate
        return 0; // draw
    }

    private float quiescenceMaxSearch(final int capturedOnField, final int depth, final int maxDepth, final float materialWeight, final float materialDelta, final Board workingBoard, final int[] bestPathOut) {
        final int[] workingPath = new int[bestPathOut.length];
        bestPathOut[depth] = 0;
        if (depth > maximumReachedDepth)
            maximumReachedDepth = depth;

        if (depth == maxDepth) {
            bestPathOut[depth] = 0;
            return calculatePositionWeight(workingBoard, materialWeight, materialDelta);
        }

        final Moves moves = moveGenerator.calculateMoves(workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction1.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = calculatePositionWeight(workingBoard, materialWeight, materialDelta);

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                positionsCount++;
                final int move = plainMoves[i];
                workingPath[depth] = move;

                final float moveWeight = WeightingFunction1.getMaterialWeightOfMove(move, depth);
                final float newMaterialWeight = materialWeight + moveWeight;
                final float newMaterialDelta = materialDelta + moveWeight;

                workingBoard.makeMove(move);
                float weight = quiescenceMinSearch(capturedOnField, depth + 1, maxDepth, newMaterialWeight, newMaterialDelta, workingBoard, workingPath);
                workingBoard.revertMove();
                if (weight != WeightingFunction1.ILLEGAL_WEIGHT && weight > bestWeight) {
                    bestWeight = weight;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                }
            }
        }

        if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + "\n" + workingBoard.toString());
        }

        return bestWeight;
    }

    private float quiescenceMinSearch(final int capturedOnField, final int depth, final int maxDepth, final float materialWeight, final float materialDelta, final Board workingBoard, final int[] bestPathOut) {
        final int[] workingPath = new int[bestPathOut.length];
        bestPathOut[depth] = 0;

        if (depth == maxDepth) {
            bestPathOut[depth] = 0;
            return calculatePositionWeight(workingBoard, materialWeight, materialDelta);
        }

        if (depth > maximumReachedDepth)
            maximumReachedDepth = depth;

        final Moves moves = moveGenerator.calculateMoves(workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction1.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = calculatePositionWeight(workingBoard, materialWeight, materialDelta);

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                positionsCount++;
                final int move = plainMoves[i];
                workingPath[depth] = move;

                final float moveWeight = WeightingFunction1.getMaterialWeightOfMove(move, depth);
                final float newMaterialWeight = materialWeight - moveWeight;
                final float newMaterialDelta = materialDelta - moveWeight;

                workingBoard.makeMove(move);
                float weight = quiescenceMaxSearch(capturedOnField, depth + 1, maxDepth, newMaterialWeight, newMaterialDelta, workingBoard, workingPath);
                workingBoard.revertMove();
                if (weight != WeightingFunction1.ILLEGAL_WEIGHT && weight < bestWeight) {
                    bestWeight = weight;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                }
            }
        }

        if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + "\n" + workingBoard.toString());
        }

        return bestWeight;
    }

    private float calculatePositionWeight(final Board workingBoard, final float materialWeight, final float materialDelta) {
        if (materialDelta > 2.0f || materialDelta < -2.0f) {
            return materialWeight;
        }
        float weight = weightingFunction.calculate(workingBoard);
        return weight != WeightingFunction1.ILLEGAL_WEIGHT ? weight * weightFactor : WeightingFunction1.ILLEGAL_WEIGHT;
    }

    private void log(String s) {
        System.out.println(s);
    }
}
