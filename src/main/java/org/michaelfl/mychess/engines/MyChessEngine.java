package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.WeightingFunction;

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
    public MoveAndWeight calculateNextMove(NextMoveTask task) {
        MoveAndWeight move = MoveAndWeight.NO_MOVE;

        // First check if this game is already finished
        if (game.getResult() != GameResult.ONGOING) {
            if (game.getResult() == GameResult.CHECKMATE) {
                move = new MoveAndWeight(0, -WeightingFunction.CHECKMATE_WEIGHT_HIGH, GameResult.CHECKMATE, new int[0]);
            } else {
                move = new MoveAndWeight(0, 0f, game.getResult(), new int[0]);
            }
        } else if (game.getGameStatus().getHalfMoveClock() >= 100 || isThreefoldRepetition()) {
            move = new MoveAndWeight(0, 0f, GameResult.DRAW, new int[0]);
        } else {
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
        }

        float weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;

        return move.weightFactor(weightFactor);
    }

    public int findCheckmate(int forColor, int[] moveOut) {
        return CheckmateSearch.findCheckmate(this, game, forColor, moveOut);
    }
}
