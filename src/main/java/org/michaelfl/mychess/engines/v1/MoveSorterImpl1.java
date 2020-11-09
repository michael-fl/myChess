package org.michaelfl.mychess.engines.v1;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.IntArray;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveSorter;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MovesArray;
import org.michaelfl.mychess.MovesCounter;
import org.michaelfl.mychess.WeightingFunction;

import java.util.Random;

final class MoveSorterImpl1 implements MoveSorter {

    private final MovesArray bucketCapturingLastPlayedOppositePiece = new MovesArray();
    private final MovesArray bucketKillerMoves = new MovesArray();
    private final MovesArray bucketBadMoves = new MovesArray();
    private final MovesArray bucketCapturingPositiveWeight = new MovesArray();
    private final MovesArray bucketCapturingSameWeight = new MovesArray();
    private final MovesArray bucketCapturingNegativeWeight = new MovesArray();
    private final MovesArray bucketForwardMoves = new MovesArray();
    private final MovesArray bucketRemainingMoves = new MovesArray();
    private final MovesArray bucketKingMoves = new MovesArray();

    private final MovesCounter killerMoves;
    private final MovesCounter badMoves;
    private GameStatus gameStatus;
    private int targetFieldOfLastOppositeMove;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board board;
    private short[] topKillerMoves;
    private short[] topBadMoves;

    MoveSorterImpl1(Random rand, MovesCounter killerMoves, MovesCounter badMoves) {
        this.killerMoves = killerMoves;
        this.badMoves = badMoves;
    }

    @Override
    public final void reset(GameStatus gameStatus, Board board, int depth, int knownBestMove) {
        this.gameStatus = gameStatus;
        this.board = board;
        this.topKillerMoves = killerMoves.getMovesOnDepth(depth).getTopMoves();
        this.topBadMoves = badMoves.getMovesOnDepth(depth).getTopMoves();

        targetFieldOfLastOppositeMove = Move.getToField(gameStatus.getLastMove());

        bucketCapturingLastPlayedOppositePiece.clear();
        bucketKillerMoves.clear();
        bucketBadMoves.clear();
        bucketCapturingPositiveWeight.clear();
        bucketCapturingSameWeight.clear();
        bucketCapturingNegativeWeight.clear();
        bucketForwardMoves.clear();
        bucketRemainingMoves.clear();
        bucketKingMoves.clear();
    }

    @Override
    public final void addMove(final int move, final int fromField, final int toField, final byte movingPiece, final byte capturedPiece) {
        if (isKillerMove(move)) {
            bucketKillerMoves.add(move);
        } else //noinspection PointlessBooleanExpression,ConstantConditions
            if (false && isBadMove(move)) {
            bucketBadMoves.add(move);
        } else if (capturedPiece != 0) {
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

    private boolean isKillerMove(final int move) {
        // Cut off captured piece and move type
        final short m1 = (short) move;

        for (short m2 : topKillerMoves) {
            if (m1 == m2)
                return true;
        }

        return false;
    }

    private boolean isBadMove(final int move) {
        // Cut off captured piece and move type
        final short m1 = (short) move;

        for (short m2 : topBadMoves) {
            if (m1 == m2)
                return true;
        }

        return false;
    }

    @Override
    public final Moves getSortedMoves() {
        final Moves moves = new Moves();
        final IntArray movesArray = moves.moves;

        // TODO: Add move to capture last played piece of opposite (if possible)

        movesArray.addAll(bucketCapturingPositiveWeight);
        movesArray.addAll(bucketKillerMoves);
        movesArray.addAll(bucketCapturingLastPlayedOppositePiece);
        movesArray.addAll(bucketCapturingSameWeight);
        movesArray.addAll(bucketCapturingNegativeWeight);
        movesArray.addAll(bucketForwardMoves);
        movesArray.addAll(bucketRemainingMoves);
        movesArray.addAll(bucketKingMoves);
        movesArray.addAll(bucketBadMoves);

        return moves;
    }
}
