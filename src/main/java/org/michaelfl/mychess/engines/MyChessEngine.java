package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.GameConfig;
import org.michaelfl.mychess.GameStatus;

@SuppressWarnings("Duplicates")
public final class MyChessEngine extends ChessEngine {

    public MyChessEngine(Game game) {
        super(game);
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
            move = PositionSearch.calculateNextMove(this, task, game);
            t2 = System.currentTimeMillis();
            System.out.println("Position search took " + (t2 - t1) + "ms");
        }

        return move;
    }

    public int findCheckmate(int forColor, int[] moveOut) {
        return CheckmateSearch.findCheckmate(this, game, forColor, moveOut);
    }
}
