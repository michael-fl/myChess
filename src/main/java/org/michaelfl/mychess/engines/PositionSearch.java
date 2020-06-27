package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MovesCounter;
import org.michaelfl.mychess.WeightingFunction;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

@SuppressWarnings("DuplicatedCode")
final class PositionSearch {

    private final static int N_DEEPEN_LOOPS = 5;
    private final static int N_VARIANTS = 4;
    private final static int FIRST_ITERATION_DEPTH = 6;
    private final static int SECOND_ITERATION_DEPTH = 6;
    private final static int MAX_QUIESCENCE_SEARCH_DEPTH = 20;

    private final MovesCounter killerMoves = new MovesCounter(2);
    private final MovesCounter badMoves = new MovesCounter(5);
    private final MoveGenerator moveGenerator;
    private final WeightingFunction weightingFunction = new WeightingFunction();

    private long positionsCount;
    private long prunedPositionsCount;
    private int maximumReachedDepth = 0;
    private int weightFactor;

    PositionSearch(MyChessEngine engine) {
        moveGenerator = new MoveGenerator(engine.getRandom(), killerMoves, badMoves);
    }

    int getMaximumReachedDepth() {
        return maximumReachedDepth;
    }

    @SuppressWarnings("Duplicates")
    MoveAndWeight calculateNextMove(Game game, Board workingBoard) {
        final GameStatus gameStatus = game.getGameStatus();

        killerMoves.clear();
        badMoves.clear();
        positionsCount = 0;
        prunedPositionsCount = 0;
        maximumReachedDepth = 0;
        weightFactor = gameStatus.isWhiteTurn() ? 1 : -1;

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, 0);
        if (moves.isIllegal() || moves.count() == 0)
            return MoveAndWeight.NO_MOVE; // No move possible

        final float materialDelta = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);
        final ArrayList<Integer> skipMoves = new ArrayList<>(N_VARIANTS);
        final MoveAndWeight[] bestMoves = new MoveAndWeight[N_VARIANTS];

        Arrays.fill(bestMoves, MoveAndWeight.NO_MOVE);

        for (int i = 0; i < N_VARIANTS; i++) {
            System.out.println("VARIANT " + (i+1) + "...");
            MoveAndWeight nextBestMove = findNextBestMove(gameStatus, workingBoard, moves, skipMoves, materialDelta);
            var m = bestMoves[i] = nextBestMove;
            if (nextBestMove == MoveAndWeight.NO_MOVE)
                break;
            System.out.println("move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight * weightFactor) + " [" + ChessUtil.pathToString(m.path) + "]");
            skipMoves.add(nextBestMove.move);
        }

        System.out.println("#positions: " + positionsCount + ", #pruned: " + prunedPositionsCount);

        for (int depth = 1; depth <= N_DEEPEN_LOOPS; depth++) {
            System.out.println("DEPTH: " + depth);
            for (int i = 0; i < N_VARIANTS; i++) {
                if (bestMoves[i] == MoveAndWeight.NO_MOVE)
                    break;
                if (bestMoves[i].path[depth] != 0) {
                    System.out.println("DEEPEN VARIANT " + (i + 1) + "...");
                    var m = bestMoves[i] = deepenPath(depth, gameStatus, workingBoard, bestMoves[i]);
                    System.out.println("move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight * weightFactor) + " [" + ChessUtil.pathToString(m.path) + "]");
                }
            }
        }

        if (bestMoves[0] != MoveAndWeight.NO_MOVE) {
            System.out.println("\nBEST MOVES:");
            sortByWeightDescending(bestMoves);

            for (int i = 0; i < N_VARIANTS; i++) {
                if (bestMoves[i] == MoveAndWeight.NO_MOVE)
                    break;
                MoveAndWeight m = bestMoves[i];
                System.out.println((i + 1) + ". move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight * weightFactor) + " [" + ChessUtil.pathToString(m.path) + "]");
            }

            return bestMoves[0];
        }

        return MoveAndWeight.NO_MOVE;
    }

    private static void sortByWeightDescending(MoveAndWeight[] bestMoves) {
        Arrays.sort(bestMoves, Comparator.comparingDouble(m -> m != MoveAndWeight.NO_MOVE ? -m.weight : Double.MAX_VALUE));
    }

    private MoveAndWeight deepenPath(int startDepth, GameStatus gameStatus, Board workingBoard, MoveAndWeight moveAndWeight) {
        int[] path = moveAndWeight.path;

        float weight = continueSearch(path, startDepth, gameStatus, workingBoard);

        return new MoveAndWeight(moveAndWeight.move, weight, path);
    }

    @SuppressWarnings("Duplicates")
    private MoveAndWeight findNextBestMove(GameStatus gameStatus, Board workingBoard, Moves moves, ArrayList<Integer> skipMoves, float materialDelta) {
        final int[] bestPath = new int[50];
        final int[] workingPath = new int[bestPath.length];

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = Float.NEGATIVE_INFINITY;
        int bestMove = 0;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            if (skipMoves.contains(move))
                continue;

            workingPath[0] = move;
            positionsCount++;
            GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            float newMaterialDelta = materialDelta + WeightingFunction.getMaterialWeightOfMove(move, 1);
            float weight = minSearch(1, FIRST_ITERATION_DEPTH, bestWeight, Float.POSITIVE_INFINITY, newMaterialDelta, nextGameStatus, workingBoard, workingPath, false);
            workingBoard.revertMove(move);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                //System.out.println("  " + ChessUtil.moveToString(move) + " ==> " + ChessUtil.weightToString(factor * weight) + " (" + move + ") [" + ChessUtil.pathToString(workingPath) + "]");
                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestMove = move;
                    System.arraycopy(workingPath, 0, bestPath, 0, bestPath.length);
                }
            }

            // Find and store current killer moves
            killerMoves.sample();
            //badMoves.sample();
        }

        if (bestMove != 0)
            return new MoveAndWeight(bestMove, bestWeight, bestPath);

        return MoveAndWeight.NO_MOVE;
    }

    private float continueSearch(final int[] bestPathInOut, final int continueOnDepth, GameStatus gameStatus, final Board workingBoard) {
        if (continueOnDepth == 0)
            throw new IllegalArgumentException();
        float materialDelta = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);

        for (int depth = 0; depth < continueOnDepth; depth++) {
            final int move = bestPathInOut[depth];
            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
            if (depth % 2 == 0)
                materialDelta += moveWeight;
            else
                materialDelta -= moveWeight;

            gameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
        }

        int maxDepth = continueOnDepth + SECOND_ITERATION_DEPTH;
        float weight;
        if (continueOnDepth % 2 == 0)
            weight = maxSearch(continueOnDepth, maxDepth, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialDelta, gameStatus, workingBoard, bestPathInOut, true);
        else
            weight = minSearch(continueOnDepth, maxDepth, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialDelta, gameStatus, workingBoard, bestPathInOut, true);

        for (int depth = continueOnDepth - 1; depth >= 0; depth--) {
            workingBoard.revertMove(bestPathInOut[depth]);
        }

        return weight;
    }

    @SuppressWarnings("Duplicates")
    private float maxSearch(final int depth, final int maxDepth, final float alphaWeight, final float betaWeight, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut, boolean doSamples) {
        if (alphaWeight == Float.POSITIVE_INFINITY || betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }
        final int[] workingPath = new int[bestPathOut.length];

        if (depth == maxDepth) {
            final int lastMove = gameStatus.getLastMove();

            if (Move.getCapturedPiece(lastMove) == 0) {
                bestPathOut[depth] = 0;
                return calculatePositionWeight(gameStatus, workingBoard);
            }

            float weight = quiescenceMaxSearch(Move.getToField(lastMove), depth, maxDepth + MAX_QUIESCENCE_SEARCH_DEPTH, materialDelta, gameStatus, workingBoard, workingPath);
            System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
            return weight;
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = alphaWeight; // Float.NEGATIVE_INFINITY
        boolean haveValidMove = false;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            positionsCount++;
            float newMaterialDelta = materialDelta + WeightingFunction.getMaterialWeightOfMove(move, depth);

            final GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            final float weight = minSearch(depth + 1, maxDepth, bestWeight, betaWeight, newMaterialDelta, nextGameStatus, workingBoard, workingPath, false);
            workingBoard.revertMove(move);

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
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
        if (Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator)) {
            // Checkmate
            return (100 - depth) * -WeightingFunction.CHECKMATE_WEIGHT;
        }

        // Stalemate
        return 0; // draw
    }

    @SuppressWarnings("Duplicates")
    private float minSearch(final int depth, final int maxDepth, final float alphaWeight, final float betaWeight, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut, boolean doSamples) {
        if (alphaWeight == Float.POSITIVE_INFINITY || betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }
        final int[] workingPath = new int[bestPathOut.length];

        if (depth == maxDepth) {
            final int lastMove = gameStatus.getLastMove();

            if (Move.getCapturedPiece(lastMove) == 0) {
                bestPathOut[depth] = 0;
                return calculatePositionWeight(gameStatus, workingBoard);
            }

            float weight = quiescenceMinSearch(Move.getToField(lastMove), depth, maxDepth + MAX_QUIESCENCE_SEARCH_DEPTH, materialDelta, gameStatus, workingBoard, workingPath);
            System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
            return weight;
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = betaWeight; // Float.POSITIVE_INFINITY
        boolean haveValidMove = false;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            positionsCount++;
            float newMaterialDelta = materialDelta - WeightingFunction.getMaterialWeightOfMove(move, depth);

            final GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            final float weight = maxSearch(depth + 1, maxDepth, alphaWeight, bestWeight, newMaterialDelta, nextGameStatus, workingBoard, workingPath, false);
            workingBoard.revertMove(move);

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                haveValidMove = true;

//                if (weight > materialDelta + 0.5f)
//                    badMoves.addMove(move, depth);

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
        if (Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator)) {
            // Checkmate
            return (100 - depth) * WeightingFunction.CHECKMATE_WEIGHT;
        }

        // Stalemate
        return 0; // draw
    }

    private float quiescenceMaxSearch(final int capturedOnField, final int depth, final int maxDepth, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut) {
        final int[] workingPath = new int[bestPathOut.length];
        bestPathOut[depth] = 0;
        if (depth > maximumReachedDepth)
            maximumReachedDepth = depth;

        if (depth == maxDepth) {
            bestPathOut[depth] = 0;
            return calculatePositionWeight(gameStatus, workingBoard);
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = calculatePositionWeight(gameStatus, workingBoard);

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                positionsCount++;
                final int move = plainMoves[i];
                workingPath[depth] = move;
                float newMaterialDelta = materialDelta + WeightingFunction.getMaterialWeightOfMove(move, depth);
                GameStatus nextGameStatus = gameStatus.makeMove(move);
                workingBoard.makeMove(move);
                float weight = quiescenceMinSearch(capturedOnField, depth + 1, maxDepth, newMaterialDelta, nextGameStatus, workingBoard, workingPath);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT && weight > bestWeight) {
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

    private float quiescenceMinSearch(final int capturedOnField, final int depth, final int maxDepth, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut) {
        final int[] workingPath = new int[bestPathOut.length];
        bestPathOut[depth] = 0;

        if (depth == maxDepth) {
            bestPathOut[depth] = 0;
            return calculatePositionWeight(gameStatus, workingBoard);
        }

        if (depth > maximumReachedDepth)
            maximumReachedDepth = depth;

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = calculatePositionWeight(gameStatus, workingBoard);

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                positionsCount++;
                final int move = plainMoves[i];
                workingPath[depth] = move;
                float newMaterialDelta = materialDelta - WeightingFunction.getMaterialWeightOfMove(move, depth);
                GameStatus nextGameStatus = gameStatus.makeMove(move);
                workingBoard.makeMove(move);
                float weight = quiescenceMaxSearch(capturedOnField, depth + 1, maxDepth, newMaterialDelta, nextGameStatus, workingBoard, workingPath);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT && weight < bestWeight) {
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

    private float calculatePositionWeight(GameStatus gameStatus, Board workingBoard) {
        float weight = weightingFunction.calculate(gameStatus, workingBoard);
        return weight != WeightingFunction.ILLEGAL_WEIGHT ? weight * weightFactor : WeightingFunction.ILLEGAL_WEIGHT;
    }
}
