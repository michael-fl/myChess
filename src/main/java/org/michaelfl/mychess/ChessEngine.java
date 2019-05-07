package org.michaelfl.mychess;

abstract class ChessEngine {

    protected final Game game;
    protected final MoveGenerator moveGenerator = new MoveGenerator();

    ChessEngine(Game game) {
        this.game = game;
    }

    final int nextMove() {
        return calculateNextMove();
    }

    protected abstract int calculateNextMove();

    protected abstract int getCountPossibleMoves();
}
