package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.WeightingFunction;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

public final class CheckmateSearch {

    private final static int MAX_CHECKMATE_SEARCH_DEPTH = 10;
    private final static int NO_CHECKMATE = -1;
    private final static int ILLEGAL = -2;

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

    private final Game game;
    private final MoveGenerator moveGenerator;

    private CheckmateSearch(ChessEngine engine, Game game) {
        this.game = game;
        this.moveGenerator = new MoveGenerator(new MoveSorterImpl());
    }

    public static MoveAndWeight findCheckmateMove(ChessEngine engine, Game game) {
        return new CheckmateSearch(engine, game).findCheckmateMove();
    }

    private MoveAndWeight findCheckmateMove() {
        final var workingBoard = game.getBoard().copy();
        final var gameStatus = game.getGameStatus();
        final int[] checkmateMove = new int[1];

        int checkmateDepth = findCheckmate(gameStatus.getOppositeColor(), gameStatus, workingBoard, checkmateMove);
        if (checkmateDepth > 0) {
            final float weight = WeightingFunction.CHECKMATE_WEIGHT_HIGH - checkmateDepth;
            System.out.println("==> opposite checkmate in " + checkmateDepth + ": " + ChessUtil.moveToString(checkmateMove[0]) + ", weight: " + ChessUtil.weightToString(weight));
            return new MoveAndWeight(checkmateMove[0], weight, new int[0]);
        }

        checkmateDepth = findCheckmate(gameStatus.getTurn(), gameStatus, workingBoard, checkmateMove);
        if (checkmateDepth > 0) {
            final float weight = -(WeightingFunction.CHECKMATE_WEIGHT_HIGH - checkmateDepth);
            System.out.println("==> I'm checkmate in " + checkmateDepth + ": " + ChessUtil.moveToString(checkmateMove[0]) + ", weight: " + ChessUtil.weightToString(weight));
            return new MoveAndWeight(checkmateMove[0], weight, new int[0]);
        }

        return MoveAndWeight.NO_MOVE;
    }

    public static int findCheckmate(ChessEngine engine, Game game, int forColor, int[] moveOut) {
        var workingBoard = game.getBoard().copy();
        var gameStatus = game.getGameStatus();

        return new CheckmateSearch(engine, game).findCheckmate(forColor, gameStatus, workingBoard, moveOut);
    }

    private int findCheckmate(int forColor, GameStatus gameStatus, Board workingBoard, int[] moveOut) {
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
            context.gameStatus = gameStatus.makeMove(workingBoard, move);
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
            context.gameStatus = gameStatus.makeMove(workingBoard, move);
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
