package org.michaelfl.mychess;

import java.util.Random;

final class MoveSorter {

    private final MovesArray bucketCapturingLastPlayedOppositePiece = new MovesArray();
    private final MovesArray bucketKillerMoves = new MovesArray();
    private final MovesArray bucketCapturingPositiveWeight = new MovesArray();
    private final MovesArray bucketCapturingSameWeight = new MovesArray();
    private final MovesArray bucketCapturingNegativeWeight = new MovesArray();
    private final MovesArray bucketForwardMoves = new MovesArray();
    private final MovesArray bucketRemainingMoves = new MovesArray();
    private final MovesArray bucketKingMoves = new MovesArray();

    private final Random rand;
    private GameStatus gameStatus;
    private int targetFieldOfLastOppositeMove;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board board;

    MoveSorter(Random rand) {
        this.rand = rand;
    }

    final void reset(GameStatus gameStatus, Board board) {
        this.gameStatus = gameStatus;
        this.board = board;

        targetFieldOfLastOppositeMove = Move.getToField(gameStatus.getLastMove());

        bucketCapturingLastPlayedOppositePiece.clear();
        bucketKillerMoves.clear();
        bucketCapturingPositiveWeight.clear();
        bucketCapturingSameWeight.clear();
        bucketCapturingNegativeWeight.clear();
        bucketForwardMoves.clear();
        bucketRemainingMoves.clear();
        bucketKingMoves.clear();
    }

    final void addMove(int move, int fromField, int toField, byte movingPiece, byte capturedPiece) {
        if (capturedPiece != 0) {
            final float deltaWeight = WeightingFunction.weightOfPiece[capturedPiece] - WeightingFunction.weightOfPiece[movingPiece];
            if (toField == targetFieldOfLastOppositeMove)
                bucketCapturingLastPlayedOppositePiece.add(move);
            else if (deltaWeight > 0)
                bucketCapturingPositiveWeight.add(move);
            else if (deltaWeight == 0)
                bucketCapturingSameWeight.add(move);
            else
                bucketCapturingNegativeWeight.add(move);
        } else if (Board.isKing(movingPiece)) {
            bucketKingMoves.add(move);
        } else {
            final int rowDelta = ChessUtil.getRowOfField(toField) - ChessUtil.getRowOfField(fromField);
            if ((gameStatus.isWhiteTurn() && rowDelta > 0) || (gameStatus.isBlackTurn() && rowDelta < 0))
                bucketForwardMoves.add(move);
            else
                bucketRemainingMoves.add(move);
        }
    }

    final Moves getSortedMoves() {
        final Moves moves = new Moves();
        final IntArray movesArray = moves.moves;

        bucketRemainingMoves.mayShuffle(rand);
        bucketForwardMoves.mayShuffle(rand);

        // TODO: Add move to capture last played piece of opposite (if possible)

        movesArray.addAll(bucketKillerMoves);
        movesArray.addAll(bucketCapturingPositiveWeight);
        movesArray.addAll(bucketCapturingLastPlayedOppositePiece);
        movesArray.addAll(bucketCapturingSameWeight);
        movesArray.addAll(bucketCapturingNegativeWeight);
        movesArray.addAll(bucketForwardMoves);
        movesArray.addAll(bucketRemainingMoves);
        movesArray.addAll(bucketKingMoves);

        return moves;
    }
}
