package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("Duplicates")
public abstract class ChessEngine {

    final static class MoveAndWeight {

        final static MoveAndWeight NO_MOVE = new MoveAndWeight(0, 0, new int[0]);

        final int move;
        final float weight;
        final int[] path;

        MoveAndWeight(int move, float weight, int[] path) {
            this.move = move;
            this.weight = weight;
            this.path = path;
        }
    }

    private final static class CheckmateSearchContext {
        final Board workingBoard;
        GameStatus gameStatus;
        int depth;
        int bestMove;
        int positionCount;

        CheckmateSearchContext(Board workingBoard, GameStatus gameStatus) {
            this.workingBoard = workingBoard;
            this.gameStatus = gameStatus;
        }
    }

    private final static int MAX_CHECKMATE_SEARCH_DEPTH = 10;
    private final static int MAX_COMBINATION_SEARCH_DEPTH = 9;
    private final static int MAX_QUIESCENCE_SEARCH_DEPTH = 20;
    private final static int NO_CHECKMATE = -1;
    private final static int ILLEGAL = -2;

    private final Random rand = new Random();

    protected final WeightingFunction weightingFunction = new WeightingFunction();
    protected final Game game;
    protected final MovesCounter killerMoves = new MovesCounter(2);
    protected final MovesCounter badMoves = new MovesCounter(5);
    protected final MoveGenerator moveGenerator = new MoveGenerator(rand, killerMoves, badMoves);

    private final AtomicLong positionsCount = new AtomicLong();
    private final AtomicLong prunedPositionsCount = new AtomicLong();

    ChessEngine(Game game) {
        this.game = game;
    }

    public final int nextMove() {
        return calculateNextMove();
    }

    protected abstract int calculateNextMove();

    public abstract int getCountPossibleMoves();

    protected MoveAndWeight findCheckmateMove(Game game, Board workingBoard) {
        final GameStatus gameStatus = game.getGameStatus();
        final int[] checkmateMove = new int[1];

        int checkmateDepth = findCheckmate(gameStatus.getOppositeColor(), gameStatus, workingBoard, checkmateMove);
        if (checkmateDepth > 0) {
            final float weight = (100 - checkmateDepth) * (gameStatus.isWhiteTurn() ? WeightingFunction.CHECKMATE_BLACK : WeightingFunction.CHECKMATE_WHITE);
            System.out.println("==> opposite checkmate in " + checkmateDepth + ": " + ChessUtil.moveToString(checkmateMove[0]) + ", weight: " + weight);
            return new MoveAndWeight(checkmateMove[0], weight, new int[0]);
        }

        checkmateDepth = findCheckmate(gameStatus.getTurn(), gameStatus, workingBoard, checkmateMove);
        if (checkmateDepth > 0) {
            final float weight = (100 - checkmateDepth) * (gameStatus.isWhiteTurn() ? WeightingFunction.CHECKMATE_WHITE : WeightingFunction.CHECKMATE_BLACK);
            System.out.println("==> I'm checkmate in " + checkmateDepth + ": " + ChessUtil.moveToString(checkmateMove[0]) + ", weight: " + weight);
            return new MoveAndWeight(checkmateMove[0], weight, new int[0]);
        }

        return MoveAndWeight.NO_MOVE;
    }

    public final int findCheckmate(int forColor, GameStatus gameStatus, Board workingBoard, int[] moveOut) {
        CheckmateSearchContext context = new CheckmateSearchContext(workingBoard, gameStatus);
        int move = gameStatus.getTurn() == forColor ?
                findCheckmateEscapeMove(context) :
                findCheckmateMove(context);
        moveOut[0] = context.bestMove;

        if (move != 0)
            System.out.println("#positions for checkmate check: " + context.positionCount);

        return move;
    }

    private int findCheckmateEscapeMove(CheckmateSearchContext context) {
        final GameStatus gameStatus = context.gameStatus;
        final Board workingBoard = context.workingBoard;
        final int depth = context.depth;

        context.positionCount++;

        if (depth > MAX_CHECKMATE_SEARCH_DEPTH
                || !Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator))
            return NO_CHECKMATE;

        int maxCheckmateDepth = -1;

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth);
        if (moves.isIllegal())
            return ILLEGAL;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            context.gameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            context.depth = depth + 1;
            int checkmateDepth = findCheckmateMove(context);
            workingBoard.revertMove(move);
            if (checkmateDepth == NO_CHECKMATE)
                return NO_CHECKMATE;
            if (checkmateDepth != ILLEGAL && checkmateDepth > maxCheckmateDepth) {
                maxCheckmateDepth = checkmateDepth;
                bestMove = i;
            }
        }

        // Checkmate found
        if (bestMove == -1)
            return depth;

        context.bestMove = plainMoves[bestMove];
        return maxCheckmateDepth;
    }

    private int findCheckmateMove(CheckmateSearchContext context) {
        final GameStatus gameStatus = context.gameStatus;
        final Board workingBoard = context.workingBoard;
        final int depth = context.depth;

        context.positionCount++;

        if (depth > MAX_CHECKMATE_SEARCH_DEPTH)
            return NO_CHECKMATE;

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth);
        if (moves.isIllegal())
            return ILLEGAL;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        int minCheckmateDepth = Integer.MAX_VALUE;
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            context.gameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            context.depth = depth + 1;
            int checkmateDepth = findCheckmateEscapeMove(context);
            workingBoard.revertMove(move);

            if (checkmateDepth >= 0 && checkmateDepth < minCheckmateDepth) {
                minCheckmateDepth = checkmateDepth;
                bestMove = i;
            }
        }

        if (minCheckmateDepth == Integer.MAX_VALUE)
            return NO_CHECKMATE;

        context.bestMove = plainMoves[bestMove];
        return minCheckmateDepth;
    }

    @SuppressWarnings("Duplicates")
    public MoveAndWeight findCombinationMove(Game game, Board workingBoard) {
        final int[] bestPath = new int[50];
        final int[] workingPath = new int[bestPath.length];
        killerMoves.clear();
        badMoves.clear();
        positionsCount.set(0);
        prunedPositionsCount.set(0);

        final GameStatus gameStatus = game.getGameStatus();
        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, 0);
        if (moves.isIllegal() || moves.count() == 0)
            return MoveAndWeight.NO_MOVE; // No move possible

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final int factor = gameStatus.isWhiteTurn() ? 1 : -1;
        final float materialDelta = factor * WeightingFunction.calculateMaterialWeight(workingBoard);
        float bestWeight = Float.NEGATIVE_INFINITY;
        int bestMove = 0;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[0] = move;
            positionsCount.incrementAndGet();
            GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            float newMaterialDelta = materialDelta + WeightingFunction.getMaterialWeightOfMove(move, 1);
            float weight = combinationMinSearch(1, MAX_COMBINATION_SEARCH_DEPTH, bestWeight, Float.POSITIVE_INFINITY, newMaterialDelta, nextGameStatus, workingBoard, workingPath, false);
            workingBoard.revertMove(move);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                System.out.println("  " + ChessUtil.moveToString(move) + " ==> " + ChessUtil.weightToString(factor * weight) + " (" + move + ") [" + ChessUtil.pathToString(workingPath) + "]");
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

        if (bestMove != 0) {
//            System.out.println("#positions for combination check: " + positionsCount + ", #pruned: " + prunedPositionsCount);
//            System.out.println("==> combination move: " + ChessUtil.moveToString(bestMove) + ", weight: " + ChessUtil.weightToString(bestWeight * factor) + " [" + ChessUtil.pathToString(bestPath) + "]");
//
//            for (int d = 1; d < 40 - MAX_COMBINATION_SEARCH_DEPTH; d += 1) {
//                bestWeight = continueCombinationSearch(bestPath, d, gameStatus, workingBoard);
//                System.out.println("Depth: " + (d + MAX_COMBINATION_SEARCH_DEPTH));
//                System.out.println("#positions for combination check: " + positionsCount + ", #pruned: " + prunedPositionsCount);
//                System.out.println("==> combination move: " + ChessUtil.moveToString(bestMove) + ", weight: " + ChessUtil.weightToString(bestWeight * factor) + " [" + ChessUtil.pathToString(bestPath) + "]");
//            }

            System.out.println("#positions for combination check: " + positionsCount + ", #pruned: " + prunedPositionsCount);
            //if (gameStatus.getPositiveWeight(bestWeight) >= 0.9f) {
            System.out.println("==> combination move: " + ChessUtil.moveToString(bestMove) + ", weight: " + ChessUtil.weightToString(bestWeight * factor) + " [" + ChessUtil.pathToString(bestPath) + "]");
            return new MoveAndWeight(bestMove, bestWeight, bestPath);
        }

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

        float weight;
        if (continueOnDepth % 2 == 0)
            weight = combinationMaxSearch(continueOnDepth, continueOnDepth + MAX_COMBINATION_SEARCH_DEPTH - 1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialDelta, gameStatus, workingBoard, bestPathInOut, true);
        else
            weight = combinationMinSearch(continueOnDepth, continueOnDepth + MAX_COMBINATION_SEARCH_DEPTH - 1, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialDelta, gameStatus, workingBoard, bestPathInOut, true);

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
        if (depth > maximumReachedDepth) {
            maximumReachedDepth = depth;
            System.out.println("Max depth: " + maximumReachedDepth + " on field " + ChessUtil.fieldToString(capturedOnField));
        }

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

        if (depth > maximumReachedDepth) {
            maximumReachedDepth = depth;
            System.out.println("Max depth: " + maximumReachedDepth + " on field " + ChessUtil.fieldToString(capturedOnField));
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
