package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("Duplicates")
public abstract class ChessEngine {

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

    protected int findCheckmateMove(Game game, Board workingBoard) {
        final GameStatus gameStatus = game.getGameStatus();
        final int[] checkmateMove = new int[1];

        int checkmateDepth = findCheckmate(gameStatus.getOppositeColor(), gameStatus, workingBoard, checkmateMove);
        if (checkmateDepth > 0) {
            final float weight = (100 - checkmateDepth) * (gameStatus.isWhiteTurn() ? WeightingFunction.CHECKMATE_BLACK : WeightingFunction.CHECKMATE_WHITE);
            game.setWeight(weight); // Remember last calculated best position weight
            System.out.println("==> checkmate in " + checkmateDepth + ": " + ChessUtil.moveToString(checkmateMove[0]) + ", weight: " + weight);
            return checkmateMove[0];
        }

        checkmateDepth = findCheckmate(gameStatus.getTurn(), gameStatus, workingBoard, checkmateMove);
        if (checkmateDepth > 0) {
            final float weight = (100 - checkmateDepth) * (gameStatus.isWhiteTurn() ? WeightingFunction.CHECKMATE_WHITE : WeightingFunction.CHECKMATE_BLACK);
            game.setWeight(weight); // Remember last calculated best position weight
            System.out.println("==> checkmate in " + checkmateDepth + ": " + ChessUtil.moveToString(checkmateMove[0]) + ", weight: " + weight);
            return checkmateMove[0];
        }

        return 0;
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

    private static int pruneCount;

    @SuppressWarnings("Duplicates")
    public int findCombinationMove(Game game, Board workingBoard) {
        final AtomicInteger positionsCount = new AtomicInteger();
        final GameStatus gameStatus = game.getGameStatus();
        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal())
            return 0;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final int factor = gameStatus.isWhiteTurn() ? 1 : -1;
        float bestWeight = gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int bestMove = -1;
        final int[] moveStack = new int[MAX_COMBINATION_SEARCH_DEPTH + 1];
        final int[] workingStack = new int[MAX_COMBINATION_SEARCH_DEPTH + 1];

        for (int i = 0; i < countMoves; i++) {
            positionsCount.incrementAndGet();
            final int move = plainMoves[i];
            final byte piece = Move.getCapturedPiece(move);
            GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            float weight = findCombination(1, factor * WeightingFunction.weightOfPiece[piece], -factor, false, nextGameStatus, workingBoard, positionsCount, workingStack);
            System.out.println("  " + ChessUtil.moveToString(move) + " ==> " + weight);
            workingBoard.revertMove(move);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT && gameStatus.isBetterWeight(weight, bestWeight)) {
                bestWeight = weight;
                bestMove = move;
                System.arraycopy(workingStack, 0, moveStack, 0, workingStack.length);
            }
        }

        if (bestMove != -1 && bestWeight >= 1.0f) {
            System.out.println("==> combination move: " + ChessUtil.moveToString(bestMove) + ", weight: " + bestWeight + ", #positions: " + positionsCount);
            System.out.println("#pruned: " + pruneCount);
            moveStack[0] = bestMove;
            for (int i = 0; i < moveStack.length && moveStack[i] != 0; i++) {
                System.out.println("-- " + ChessUtil.moveToString(moveStack[i]));
            }
            return bestMove;
        } else
            System.out.println("#positions for combination check: " + positionsCount);

        return 0;
    }

    @SuppressWarnings("Duplicates")
    private float findCombination(final int depth, final float currentWeight, final int factor, boolean isMyTurn, final GameStatus gameStatus, final Board workingBoard, AtomicInteger positionsCount, final int[] moveStack) {
        if (depth == MAX_COMBINATION_SEARCH_DEPTH) {
            moveStack[depth] = 0;
            final int lastMove = gameStatus.getLastMove();
            if (Move.getCapturedPiece(lastMove) == 0)
                return currentWeight;

            return followCapturedPiecesRecursive(depth, currentWeight, factor, gameStatus, workingBoard, positionsCount);
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        int bestMove = -1;
        float bestWeight = gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int[] workingStack = Arrays.copyOf(moveStack, moveStack.length);

        for (int i = 0; i < countMoves; i++) {
            positionsCount.incrementAndGet();
            final int move = plainMoves[i];
            final byte piece = Move.getCapturedPiece(move);
            float weight = currentWeight + factor * WeightingFunction.weightOfPiece[piece] + factor * depth * -0.01f;

            if (!isMyTurn || depth < 4 || (piece != 0 && gameStatus.isBetterWeight(weight, 0))) {
                GameStatus nextGameStatus = gameStatus.makeMove(move);
                workingBoard.makeMove(move);
                weight = findCombination(depth + 1, weight, -factor, !isMyTurn, nextGameStatus, workingBoard, positionsCount, workingStack);
                workingBoard.revertMove(move);
            } else {
                workingStack[depth + 1] = 0;
                pruneCount++;
            }

            if (weight != WeightingFunction.ILLEGAL_WEIGHT && gameStatus.isBetterWeight(weight, bestWeight)) {
                bestMove = i;
                bestWeight = weight;
                workingStack[depth] = plainMoves[bestMove];
                System.arraycopy(workingStack, 0, moveStack, 0, moveStack.length);
            }
        }

        if (bestMove != -1)
            return bestWeight;

        moveStack[depth] = 0;
        return currentWeight;
    }

    private float followCapturedPiecesRecursive(final int depth, final float currentWeight, final int factor, final GameStatus gameStatus, final Board workingBoard, AtomicInteger positionsCount) {
        final int capturedOnField = Move.getToField(gameStatus.getLastMove());

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        int bestMove = -1;
        float bestWeight = gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

        for (int i = 0; i < countMoves; i++) {
            positionsCount.incrementAndGet();
            final int move = plainMoves[i];
            // Follow only moves, which capture pieces
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                final byte piece = Move.getCapturedPiece(move);
                float weight = currentWeight + factor * WeightingFunction.weightOfPiece[piece];
                GameStatus nextGameStatus = gameStatus.makeMove(move);
                workingBoard.makeMove(move);
                weight = followCapturedPiecesRecursive(depth + 1, weight, -factor, nextGameStatus, workingBoard, positionsCount);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT && gameStatus.isBetterWeight(weight, bestWeight)) {
                    bestMove = i;
                    bestWeight = weight;
                }
            }
        }

        return bestMove != -1 ? bestWeight : currentWeight;
    }
}
