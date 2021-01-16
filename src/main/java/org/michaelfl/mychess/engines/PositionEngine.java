package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.WeightingFunction;

@SuppressWarnings("unused")
public final class PositionEngine extends ChessEngine {

    private final WeightingFunction weightingFunction = new WeightingFunction();

    public PositionEngine(EngineConfig config, Game game) {
        super(config, game);
    }

    @SuppressWarnings("Duplicates")
    @Override
    public MoveAndWeight calculateNextMove(NextMoveTask task) {
        Moves moves = moveGenerator.calculateMoves(game.getBoard());

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
            workingBoard.makeMove(move);

            float weight = weightingFunction.calculate(workingBoard);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                log(ChessUtil.moveToString(move) + " ==> weight " + weight);
                if ((isWhiteTurn && weight > bestWeight) || (!isWhiteTurn && weight < bestWeight)) {
                    bestWeight = weight;
                    bestMove = i;
                }
            }

            workingBoard.revertMove();
        }

        if (bestMove == -1) {
            // No legal move possible
            return MoveAndWeight.NO_MOVE;
        }

        return new MoveAndWeight(plainMoves[bestMove], bestWeight, GameResult.ONGOING, new int[] { plainMoves[bestMove] });
    }
}
