package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

import java.util.Random;

final class RandomMoveEngine extends ChessEngine {

    private final Random rand = new Random();
    private int countPossibleMoves = -1;

    RandomMoveEngine(Game game) {
        super(game);
    }

    @Override
    protected int calculateNextMove() {
        Moves moves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());
        countPossibleMoves = moves.count();

        if (moves.isIllegal() || moves.count() == 0)
            return 0; // No move possible

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
                return move;

            workingBoard.revertMove(move);
            moveIndex = (moveIndex + 1) % countMoves; // try next move
        }

        // No legal move possible
        return 0;
    }

    @Override
    public int getCountPossibleMoves() {
        return countPossibleMoves;
    }
}
