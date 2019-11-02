package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.WeightingFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("Duplicates")
public final class MyChessEngine extends ChessEngine {

    private final static int N_COMBINATION_VARIANTS = 6;
    private final static float COMBINATION_SEARCH_WEIGHT_TRESHOLD = 0.9f;
    private final static int COMBINATION_FIRST_ITERATION_DEPTH = 7;
    private final static int COMBINATION_SECOND_ITERATION_DEPTH = 7;
    private final static int MAX_COMBINATION_SEARCH_DEPTH = 18;
    private final static int MAX_QUIESCENCE_SEARCH_DEPTH = 20;

    private final WeightingFunction weightingFunction = new WeightingFunction();

    private final AtomicLong positionsCount = new AtomicLong();
    private final AtomicLong prunedPositionsCount = new AtomicLong();

    public MyChessEngine(Game game) {
        super(game);
    }

    @Override
    protected MoveAndWeight calculateNextMove() {
        final Board workingBoard = game.getBoard().copy();

        // Phase 1: Checkmate search
        long t1 = System.currentTimeMillis();
        MoveAndWeight move = findCheckmateMove(game, workingBoard);
        long t2 = System.currentTimeMillis();
        System.out.println("Checkmate check took " + (t2 - t1) + "ms");

        // Phase 2: Combination/material search
        if (move == MoveAndWeight.NO_MOVE) {
            t1 = System.currentTimeMillis();
            move = findCombinationMove(game, workingBoard);
            t2 = System.currentTimeMillis();
            System.out.println("Combination check took " + (t2 - t1) + "ms");
        }

        System.out.println("Max depth: " + maximumReachedDepth);

        return move;
    }

    @SuppressWarnings("Duplicates")
    private MoveAndWeight findCombinationMove(Game game, Board workingBoard) {
        killerMoves.clear();
        badMoves.clear();
        positionsCount.set(0);
        prunedPositionsCount.set(0);

        final GameStatus gameStatus = game.getGameStatus();
        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, 0);
        if (moves.isIllegal() || moves.count() == 0)
            return MoveAndWeight.NO_MOVE; // No move possible

        final int factor = gameStatus.isWhiteTurn() ? 1 : -1;
        final float materialDelta = factor * WeightingFunction.calculateMaterialWeight(workingBoard);
        final ArrayList<Integer> skipMoves = new ArrayList<>(N_COMBINATION_VARIANTS);
        final MoveAndWeight[] bestMoves = new MoveAndWeight[N_COMBINATION_VARIANTS];

        for (int i = 0; i < N_COMBINATION_VARIANTS; i++) {
            MoveAndWeight nextBestMove = findNextBestCombinationMove(gameStatus, workingBoard, moves, skipMoves, materialDelta);
            bestMoves[i] = nextBestMove;
            if (nextBestMove == MoveAndWeight.NO_MOVE)
                break;
            skipMoves.add(nextBestMove.move);
            //System.out.println("----------------------------------------------------------");
        }

        System.out.println("#positions for combination check: " + positionsCount + ", #pruned: " + prunedPositionsCount);

        for (int i = 0; i < N_COMBINATION_VARIANTS; i++) {
            if (bestMoves[i] == MoveAndWeight.NO_MOVE)
                break;
            bestMoves[i] = deepenPath(gameStatus, workingBoard, bestMoves[i]);
        }

        if (bestMoves[0] != MoveAndWeight.NO_MOVE) {
            sortByWeightDescending(bestMoves);

            for (int i = 0; i < N_COMBINATION_VARIANTS; i++) {
                if (bestMoves[i] == MoveAndWeight.NO_MOVE)
                    break;
                MoveAndWeight m = bestMoves[i];
                System.out.println((i + 1) + ". move: " + ChessUtil.moveToString(m.move) + ", weight: " + ChessUtil.weightToString(m.weight * factor) + " [" + ChessUtil.pathToString(m.path) + "]");
            }

            MoveAndWeight bestMove = bestMoves[0];
            if (Math.abs(bestMove.weight - materialDelta) >= COMBINATION_SEARCH_WEIGHT_TRESHOLD
                    || bestMoves[1] == MoveAndWeight.NO_MOVE
                    || Math.abs(bestMoves[1].weight - materialDelta) >= COMBINATION_SEARCH_WEIGHT_TRESHOLD
                    || bestMoves[2] == MoveAndWeight.NO_MOVE
                    || Math.abs(bestMoves[2].weight - materialDelta) >= COMBINATION_SEARCH_WEIGHT_TRESHOLD) {
                System.out.println("Found combination move: " + ChessUtil.moveToString(bestMove.move) + ", weight: " + ChessUtil.weightToString(bestMove.weight * factor) + " [" + ChessUtil.pathToString(bestMove.path) + "]");
                return bestMove;
            }
        }

        return MoveAndWeight.NO_MOVE;
    }

    private static void sortByWeightDescending(MoveAndWeight[] bestMoves) {
        Arrays.sort(bestMoves, Comparator.comparingDouble(m -> m != MoveAndWeight.NO_MOVE ? -m.weight : Double.MAX_VALUE));
    }

    private MoveAndWeight deepenPath(GameStatus gameStatus, Board workingBoard, MoveAndWeight moveAndWeight) {
        int[] path = moveAndWeight.path;
        float weight = moveAndWeight.weight;

        for (int depth = 1; depth < MAX_COMBINATION_SEARCH_DEPTH; depth += COMBINATION_SECOND_ITERATION_DEPTH - 2) {
            weight = continueCombinationSearch(path, depth, gameStatus, workingBoard);
        }

        return new MoveAndWeight(moveAndWeight.move, weight, path);
    }

    @SuppressWarnings("Duplicates")
    private MoveAndWeight findNextBestCombinationMove(GameStatus gameStatus, Board workingBoard, Moves moves, ArrayList<Integer> skipMoves, float materialDelta) {
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
            positionsCount.incrementAndGet();
            GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            float newMaterialDelta = materialDelta + WeightingFunction.getMaterialWeightOfMove(move, 1);
            float weight = combinationMinSearch(1, COMBINATION_FIRST_ITERATION_DEPTH, bestWeight, Float.POSITIVE_INFINITY, newMaterialDelta, nextGameStatus, workingBoard, workingPath, false);
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

    private float continueCombinationSearch(final int[] bestPathInOut, final int continueOnDepth, GameStatus gameStatus, final Board workingBoard) {
        if (continueOnDepth == 0)
            throw new IllegalArgumentException();
        final int factor = gameStatus.isWhiteTurn() ? 1 : -1;
        float materialDelta = factor * WeightingFunction.calculateMaterialWeight(workingBoard);

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

        int maxDepth = Math.min(continueOnDepth + COMBINATION_SECOND_ITERATION_DEPTH, MAX_COMBINATION_SEARCH_DEPTH);
        float weight;
        if (continueOnDepth % 2 == 0)
            weight = combinationMaxSearch(continueOnDepth, maxDepth, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialDelta, gameStatus, workingBoard, bestPathInOut, true);
        else
            weight = combinationMinSearch(continueOnDepth, maxDepth, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialDelta, gameStatus, workingBoard, bestPathInOut, true);

        for (int depth = continueOnDepth - 1; depth >= 0; depth--) {
            workingBoard.revertMove(bestPathInOut[depth]);
        }

        return weight;
    }

    @SuppressWarnings("Duplicates")
    private float combinationMaxSearch(final int depth, final int maxDepth, final float alphaWeight, final float betaWeight, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut, boolean doSamples) {
        final int[] workingPath = new int[bestPathOut.length];

        if (depth == maxDepth) {
            final int lastMove = gameStatus.getLastMove();

            if (Move.getCapturedPiece(lastMove) == 0) {
                bestPathOut[depth] = 0;
                return materialDelta;
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
            positionsCount.incrementAndGet();
            float newMaterialDelta = materialDelta + WeightingFunction.getMaterialWeightOfMove(move, depth);

            final GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            final float weight = combinationMinSearch(depth + 1, maxDepth, bestWeight, betaWeight, newMaterialDelta, nextGameStatus, workingBoard, workingPath, false);
            workingBoard.revertMove(move);

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight >= betaWeight) {
                    prunedPositionsCount.addAndGet(countMoves - i - 1);
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

        if (haveValidMove)
            return bestWeight;

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
    private float combinationMinSearch(final int depth, final int maxDepth, final float alphaWeight, final float betaWeight, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut, boolean doSamples) {
        final int[] workingPath = new int[bestPathOut.length];

        if (depth == maxDepth) {
            final int lastMove = gameStatus.getLastMove();

            if (Move.getCapturedPiece(lastMove) == 0) {
                bestPathOut[depth] = 0;
                return materialDelta;
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
            positionsCount.incrementAndGet();
            float newMaterialDelta = materialDelta - WeightingFunction.getMaterialWeightOfMove(move, depth);

            final GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            final float weight = combinationMaxSearch(depth + 1, maxDepth, alphaWeight, bestWeight, newMaterialDelta, nextGameStatus, workingBoard, workingPath, false);
            workingBoard.revertMove(move);

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                haveValidMove = true;

//                if (weight > materialDelta + 0.5f)
//                    badMoves.addMove(move, depth);

                // Alpha-Beta search pruning
                if (weight <= alphaWeight) {
                    prunedPositionsCount.addAndGet(countMoves - i - 1);
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

        if (haveValidMove)
            return bestWeight;

        // No legal move possible ==> Checkmate or stalemate
        bestPathOut[depth] = 0;
        if (Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator)) {
            // Checkmate
            return (100 - depth) * WeightingFunction.CHECKMATE_WEIGHT;
        }

        // Stalemate
        return 0; // draw
    }

    private int maximumReachedDepth = 0;

    private float quiescenceMaxSearch(final int capturedOnField, final int depth, final int maxDepth, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut) {
        final int[] workingPath = new int[bestPathOut.length];
        bestPathOut[depth] = 0;
        if (depth > maximumReachedDepth)
            maximumReachedDepth = depth;

        if (depth == maxDepth) {
            bestPathOut[depth] = 0;
            return materialDelta;
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = materialDelta;

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                positionsCount.incrementAndGet();
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

        return bestWeight;
    }

    private float quiescenceMinSearch(final int capturedOnField, final int depth, final int maxDepth, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut) {
        final int[] workingPath = new int[bestPathOut.length];
        bestPathOut[depth] = 0;

        if (depth == maxDepth) {
            bestPathOut[depth] = 0;
            return materialDelta;
        }

        if (depth > maximumReachedDepth)
            maximumReachedDepth = depth;

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = materialDelta;

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                positionsCount.incrementAndGet();
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

        return bestWeight;
    }
}
