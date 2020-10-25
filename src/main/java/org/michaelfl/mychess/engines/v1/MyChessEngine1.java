package org.michaelfl.mychess.engines.v1;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.engines.CheckmateSearch;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.NextMoveTask;

@SuppressWarnings("Duplicates")
public final class MyChessEngine1 extends ChessEngine {

    public MyChessEngine1(EngineConfig config, Game game) {
        super(config, game);
    }

    @Override
    protected MoveAndWeight calculateNextMove(NextMoveTask task) {
        // Phase 1: Checkmate search
        long t1 = System.currentTimeMillis();
        MoveAndWeight move = CheckmateSearch.findCheckmateMove(this, game);
        long t2 = System.currentTimeMillis();
        System.out.println("Checkmate check took " + (t2 - t1) + "ms");

        // Phase 2: Position search
        if (move == MoveAndWeight.NO_MOVE) {
            t1 = System.currentTimeMillis();
            move = PositionSearch1.calculateNextMove(this, task, game);
            t2 = System.currentTimeMillis();
            System.out.println("Position search took " + (t2 - t1) + "ms");
        }

        return move;
    }

    public int findCheckmate(int forColor, int[] moveOut) {
        return CheckmateSearch.findCheckmate(this, game, forColor, moveOut);
    }
}
