package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.Moves;

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
    protected MoveAndWeight calculateNextMove(NextMoveTask task) {
        MoveAndWeight move = MoveAndWeight.NO_MOVE;

        // First check if this game is already finished
        game.calculateAndSetGameResult();
        if (game.getResult() != GameResult.ONGOING) {
            return move;
        }

        // Phase 1: Checkmate search
        if (getConfig().isCheckmateCheck()) {
            long t1 = System.currentTimeMillis();
            move = CheckmateSearch.findCheckmateMove(this, game); // TODO: return path from checkmate search
            long t2 = System.currentTimeMillis();
            System.out.println("Checkmate check took " + (t2 - t1) + "ms");
        }

        // Phase 2: Position search
        if (move == MoveAndWeight.NO_MOVE) {
            long t1 = System.currentTimeMillis();
            move = PositionSearch.calculateNextMove(this, task, game);
            long t2 = System.currentTimeMillis();
            System.out.println("Position search took " + (t2 - t1) + "ms");
        }

        float weightFactor = game.getGameStatus().isWhiteTurn() ? 1 : -1;

        return move.weightFactor(weightFactor);
    }

    public int findCheckmate(int forColor, int[] moveOut) {
        return CheckmateSearch.findCheckmate(this, game, forColor, moveOut);
    }
}
