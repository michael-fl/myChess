package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

public final class PositionEngine extends ChessEngine {

    private WeightingFunction weightingFunction = new WeightingFunction();

    public PositionEngine(EngineConfig config, Game game) {
        super(config, game);
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected MoveAndWeight calculateNextMove(NextMoveTask task) {
        Moves moves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());

        if (moves.isIllegal() || moves.count() == 0)
            return MoveAndWeight.NO_MOVE; // No move possible

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final Board workingBoard = game.getBoard().copy();
        final boolean isWhiteTurn = game.getTurn() == GameStatus.TURN_WHITE;
        float bestWeight = isWhiteTurn ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            // Make this move and calculate its weight; also check if it is a legal one
            int move = plainMoves[i];
            // TODO: Pass GameStatus as result parameter to avoid allocation of many objects
            GameStatus gameStatus = game.getGameStatus().makeMove(move);
            workingBoard.makeMove(move);

            float weight = weightingFunction.calculate(gameStatus, workingBoard);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                System.out.println(ChessUtil.moveToString(move) + " ==> weight " + weight);
                if ((isWhiteTurn && weight > bestWeight) || (!isWhiteTurn && weight < bestWeight)) {
                    bestWeight = weight;
                    bestMove = i;
                }
            }

            workingBoard.revertMove(move);
        }

        if (bestMove == -1) {
            // No legal move possible
            return MoveAndWeight.NO_MOVE;
        }

        return new MoveAndWeight(plainMoves[bestMove], bestWeight, new int[] { plainMoves[bestMove] });
    }
}
