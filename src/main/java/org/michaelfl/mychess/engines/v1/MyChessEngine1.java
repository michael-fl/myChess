package org.michaelfl.mychess.engines.v1;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.NextMoveTask;

@SuppressWarnings("Duplicates")
public final class MyChessEngine1 extends ChessEngine {

    public MyChessEngine1(EngineConfig config, Game game) {
        super(config, game);
    }

    @Override
    public Moves getPossibleMoves() {
        return PositionSearch1.getPossibleMoves(this, game);
    }

    @Override
    protected MoveAndWeight calculateNextMoveSub(NextMoveTask task) {
        long t1 = System.currentTimeMillis();
        var move = PositionSearch1.calculateNextMove(this, task, game);
        long t2 = System.currentTimeMillis();
        log("Position search took " + (t2 - t1) + "ms");

        return move;
    }
}
