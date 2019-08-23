package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("Duplicates")
public abstract class ChessEngine {

    final static class MoveAndWeight {

        final static MoveAndWeight NO_MOVE = new MoveAndWeight(0, 0);

        final int move;
        final float weight;

        MoveAndWeight(int move, float weight) {
            this.move = move;
            this.weight = weight;
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
    private final static int MAX_COMBINATION_SEARCH_DEPTH = 6;
    private final static int NO_CHECKMATE = -1;
    private final static int ILLEGAL = -2;

    protected final Game game;
    protected final MoveGenerator moveGenerator = new MoveGenerator();

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
            return new MoveAndWeight(checkmateMove[0], weight);
        }

        checkmateDepth = findCheckmate(gameStatus.getTurn(), gameStatus, workingBoard, checkmateMove);
        if (checkmateDepth > 0) {
            final float weight = (100 - checkmateDepth) * (gameStatus.isWhiteTurn() ? WeightingFunction.CHECKMATE_WHITE : WeightingFunction.CHECKMATE_BLACK);
            System.out.println("==> I'm checkmate in " + checkmateDepth + ": " + ChessUtil.moveToString(checkmateMove[0]) + ", weight: " + weight);
            return new MoveAndWeight(checkmateMove[0], weight);
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

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
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

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
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
        final AtomicInteger positionsCount = new AtomicInteger();
        final AtomicInteger prunedPositionsCount = new AtomicInteger();
        final GameStatus gameStatus = game.getGameStatus();
        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal() || moves.count() == 0)
            return MoveAndWeight.NO_MOVE; // No move possible

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final int factor = gameStatus.isWhiteTurn() ? 1 : -1;
        float bestWeight = gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            positionsCount.incrementAndGet();
            final byte piece = Move.getCapturedPiece(move);
            GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            float weight = findCombination(1, bestWeight, factor * WeightingFunction.weightOfPiece[piece], -factor, nextGameStatus, workingBoard, positionsCount, prunedPositionsCount);
            workingBoard.revertMove(move);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                System.out.println("  " + ChessUtil.moveToString(move) + " ==> " + ChessUtil.weightToString(weight));
                if (gameStatus.isBetterWeight(weight, bestWeight)) {
                    bestWeight = weight;
                    bestMove = move;
                }
            }
        }

        System.out.println("#positions for combination check: " + positionsCount + ", #pruned: " + prunedPositionsCount);
        if (bestMove != 0 && gameStatus.getPositiveWeight(bestWeight) >= 0.9f) {
            System.out.println("==> combination move: " + ChessUtil.moveToString(bestMove) + ", weight: " + ChessUtil.weightToString(bestWeight));
            return new MoveAndWeight(bestMove, bestWeight);
        }

        return MoveAndWeight.NO_MOVE;
    }

    @SuppressWarnings("Duplicates")
    private float findCombination(final int depth, final float bestKnownWeight, final float materialWeight, final int factor, final GameStatus gameStatus, final Board workingBoard, final AtomicInteger positionsCount, final AtomicInteger prunedPositionsCount) {
        final boolean isOppositeTurnAndWeightKnown = depth % 2 == 1 && bestKnownWeight != Float.NEGATIVE_INFINITY && bestKnownWeight != Float.POSITIVE_INFINITY;

        if (depth == MAX_COMBINATION_SEARCH_DEPTH) {
            final int lastMove = gameStatus.getLastMove();
            //if (Move.getCapturedPiece(lastMove) == 0)
                return materialWeight;

            //return followCapturedPiecesRecursive(Move.getToField(lastMove), depth, materialWeight, factor, gameStatus, workingBoard, positionsCount);
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        int bestMove = 0;
        float bestWeight = gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        final float depthFactor = (1000 - depth) / 1000.0f;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            positionsCount.incrementAndGet();
            final byte piece = Move.getCapturedPiece(move);
            float weight = materialWeight + factor * (WeightingFunction.weightOfPiece[piece] * depthFactor);

            final GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            weight = findCombination(depth + 1, bestWeight, weight, -factor, nextGameStatus, workingBoard, positionsCount, prunedPositionsCount);
            workingBoard.revertMove(move);

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                // Prune as soon as an opposite's move is found, which is at least as good as the best known move so far
                if (isOppositeTurnAndWeightKnown && gameStatus.isBetterOrEqualWeight(weight, bestKnownWeight)) {
                    prunedPositionsCount.addAndGet(countMoves - i - 1);
                    return weight;
                }

                if (gameStatus.isBetterWeight(weight, bestWeight)) {
                    bestMove = move;
                    bestWeight = weight;
                }
            }
        }

        if (bestMove != 0)
            return bestWeight;

        // No legal move possible ==> Checkmate or stalemate
        if (Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator)) {
            // Checkmate
            return (100 - depth) * (gameStatus.isWhiteTurn() ? WeightingFunction.CHECKMATE_WHITE : WeightingFunction.CHECKMATE_BLACK);
        }

        // Stalemate
        return 0; // draw
    }

    private float followCapturedPiecesRecursive(final int capturedOnField, final int depth, final float materialWeight, final int factor, final GameStatus gameStatus, final Board workingBoard, AtomicInteger positionsCount) {
        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        int bestMove = -1;
        float bestWeight = gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

        for (int i = 0; i < countMoves; i++) {
            positionsCount.incrementAndGet();
            // Follow only moves, which capture pieces
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                final int move = plainMoves[i];
                final byte piece = Move.getCapturedPiece(move);
                float weight = materialWeight + factor * WeightingFunction.weightOfPiece[piece];
                GameStatus nextGameStatus = gameStatus.makeMove(move);
                workingBoard.makeMove(move);
                weight = followCapturedPiecesRecursive(capturedOnField, depth + 1, weight, -factor, nextGameStatus, workingBoard, positionsCount);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT && gameStatus.isBetterWeight(weight, bestWeight)) {
                    bestMove = i;
                    bestWeight = weight;
                }
            }
        }

        return bestMove != -1 ? bestWeight : materialWeight;
    }
}
