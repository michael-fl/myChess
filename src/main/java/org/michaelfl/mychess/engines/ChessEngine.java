package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.MoveGenerator;

public abstract class ChessEngine {

    protected final Game game;
    protected final MoveGenerator moveGenerator = new MoveGenerator();

    ChessEngine(Game game) {
        this.game = game;
    }

    public static ChessEngine newEngine(Game game) {
        return new FixDepthEngine(game);
    }

    public final int nextMove() {
        return calculateNextMove();
    }

    protected abstract int calculateNextMove();

    public abstract int getCountPossibleMoves();
}
