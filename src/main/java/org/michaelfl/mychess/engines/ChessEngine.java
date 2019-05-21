package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

public abstract class ChessEngine {

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

 
}
