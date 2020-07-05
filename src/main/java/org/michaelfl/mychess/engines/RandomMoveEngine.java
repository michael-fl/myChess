package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

import java.util.Random;

public final class RandomMoveEngine extends ChessEngine {

    private final Random rand = new Random();

    public RandomMoveEngine(Game game) {
        super(game);
    }

    @Override
    protected MoveAndWeight calculateNextMove(NextMoveTask task) {
        Moves moves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());

        if (moves.isIllegal() || moves.count() == 0)
            return MoveAndWeight.NO_MOVE; // No move possible

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final Board workingBoard = game.getBoard().copy();
        int moveIndex = rand.nextInt(countMoves);

        for (int i = 0; i < countMoves; i++) {
            // Make this move and check if it is a legal one
            int move = plainMoves[moveIndex];
            GameStatus gameStatus = game.getGameStatus().makeMove(move);
            workingBoard.makeMove(move);
            Moves nextMoves = moveGenerator.calculateMoves(gameStatus, workingBoard);

            if (!nextMoves.isIllegal())
                return new MoveAndWeight(move, 0, new int[] { move });

            workingBoard.revertMove(move);
            moveIndex = (moveIndex + 1) % countMoves; // try next move
        }

        // No legal move possible
        return MoveAndWeight.NO_MOVE;
    }
}
