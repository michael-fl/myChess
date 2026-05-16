package org.michaelfl.mychess.engines.v2;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.NextMoveTask;

@SuppressWarnings("Duplicates")
public final class MyChessEngine2 extends ChessEngine {

    public MyChessEngine2(EngineConfig config, Game game) {
        super(config, game);
    }

    @Override
    public Moves getPossibleMoves() {
        return PositionSearch2.getPossibleMoves(this, game);
    }

    @Override
    protected MoveAndWeight calculateNextMoveSub(NextMoveTask task) {

        long t1 = System.currentTimeMillis();
        var move = PositionSearch2.calculateNextMove(this, task, game);
        long t2 = System.currentTimeMillis();
        log("Position search took " + (t2 - t1) + "ms");

        return move;
    }
}
