package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

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

}
