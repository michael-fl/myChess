package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Moves;

public final class MyChessEngine extends ChessEngine {

    public MyChessEngine(EngineConfig config, Game game) {
        super(config, game);
    }

    @Override
    public Moves getPossibleMoves() {
        return PositionSearch.getPossibleMoves(this, game);
    }

    @Override
    protected MoveAndWeight calculateNextMoveSub(NextMoveTask task) {
        long t1 = System.currentTimeMillis();
        var move = PositionSearch.calculateNextMove(this, task, game);
        long t2 = System.currentTimeMillis();
        log("Position search took " + (t2 - t1) + "ms");

        return move;
    }

}
