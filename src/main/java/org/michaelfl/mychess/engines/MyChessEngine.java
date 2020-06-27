package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.GameStatus;

@SuppressWarnings("Duplicates")
public final class MyChessEngine extends ChessEngine {

    private final CombinationSearch combinationSearch;
    private final CheckmateSearch checkmateSearch;
    private final PositionSearch positionSearch;

    public MyChessEngine(Game game) {
        super(game);

        combinationSearch = new CombinationSearch(this);
        checkmateSearch = new CheckmateSearch(this);
        positionSearch = new PositionSearch(this);
    }

    @Override
    protected MoveAndWeight calculateNextMove() {
        final Board workingBoard = game.getBoard().copy();

        // Phase 1: Checkmate search
        long t1 = System.currentTimeMillis();
        MoveAndWeight move = checkmateSearch.findCheckmateMove(game, workingBoard);
        long t2 = System.currentTimeMillis();
        System.out.println("Checkmate check took " + (t2 - t1) + "ms");

        // Phase 2: Combination/material search
        if (false && move == MoveAndWeight.NO_MOVE) {
            t1 = System.currentTimeMillis();
            move = combinationSearch.calculateNextMove(game, workingBoard);
            t2 = System.currentTimeMillis();
            System.out.println("Combination search took " + (t2 - t1) + "ms; maximum depth " + combinationSearch.getMaximumReachedDepth());
        }

        // Phase 3: Position search
        if (move == MoveAndWeight.NO_MOVE) {
            t1 = System.currentTimeMillis();
            move = positionSearch.calculateNextMove(game, workingBoard);
            t2 = System.currentTimeMillis();
            System.out.println("Position search took " + (t2 - t1) + "ms; maximum depth " + positionSearch.getMaximumReachedDepth());
        }

        return move;
    }

    public int findCheckmate(int forColor, GameStatus gameStatus, Board workingBoard, int[] moveOut) {
        return checkmateSearch.findCheckmate(forColor, gameStatus, workingBoard, moveOut);
    }
}
