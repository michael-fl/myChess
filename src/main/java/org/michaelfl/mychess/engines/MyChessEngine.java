package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.openingdb.OpeningDB;
import org.michaelfl.mychess.openingdb.OpeningDB.MoveInfo;

import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
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
        MoveAndWeight move = MoveAndWeight.NO_MOVE;

        // Phase 1: Checkmate search
        if (getConfig().isCheckmateCheck()) {
            long t1 = System.currentTimeMillis();
            move = CheckmateSearch.findCheckmateMove(this, game); // TODO: return path from checkmate search
            long t2 = System.currentTimeMillis();
            log("Checkmate check took " + (t2 - t1) + "ms");
        }

        // Phase 2: Position search
        if (move.move == 0) {
            long t1 = System.currentTimeMillis();
            move = PositionSearch.calculateNextMove(this, task, game);
            long t2 = System.currentTimeMillis();
            log("Position search took " + (t2 - t1) + "ms");
        }

        return move;
    }

    public int findCheckmate(int forColor, int[] moveOut) {
        return CheckmateSearch.findCheckmate(this, game, forColor, moveOut);
    }
}
