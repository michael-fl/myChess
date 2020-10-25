package org.michaelfl.mychess;

import java.util.Random;

public final class MoveSorterImpl implements MoveSorter {

    private final MovesArray bucketKillerMoves = new MovesArray();
    private int bestMoveCapturingLastPlayedOppositePiece;
    private float bestWeightCapturingLastPlayedOppositePiece = Float.NEGATIVE_INFINITY;
    private final SortableMovesBucket bucketWinningCaptures = new SortableMovesBucket();
    private final SortableMovesBucket bucketOtherCaptures = new SortableMovesBucket();
    private final MovesArray bucketForwardMoves = new MovesArray();
    private final MovesArray bucketRemainingMoves = new MovesArray();
    private final MovesArray bucketKingMoves = new MovesArray();

    private final MovesCounter killerMoves;
    private GameStatus gameStatus;
    private int targetFieldOfLastOppositeMove;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board board;
    private short[] topKillerMoves;

    public MoveSorterImpl(Random rand) {
        this(rand, new MovesCounter(1));
    }

    public MoveSorterImpl(Random rand, MovesCounter killerMoves) {
        this.killerMoves = killerMoves;
    }

    @Override
    public final void reset(GameStatus gameStatus, Board board, int depth) {
        this.gameStatus = gameStatus;
        this.board = board;
        this.topKillerMoves = killerMoves.getMovesOnDepth(depth).getTopMoves();

        targetFieldOfLastOppositeMove = Move.getToField(gameStatus.getLastMove());

        bestMoveCapturingLastPlayedOppositePiece = 0;
        bestWeightCapturingLastPlayedOppositePiece = Float.NEGATIVE_INFINITY;
        bucketWinningCaptures.clear();
        bucketOtherCaptures.clear();
        bucketKillerMoves.clear();
        bucketForwardMoves.clear();
        bucketRemainingMoves.clear();
        bucketKingMoves.clear();
    }

    @Override
    public final void addMove(final int move, final int fromField, final int toField, final byte movingPiece, final byte capturedPiece) {
        if (isKillerMove(move)) {
            bucketKillerMoves.add(move);
        } else if (capturedPiece != 0) {
            final float deltaWeight = WeightingFunction.weightOfPiece[capturedPiece] - WeightingFunction.weightOfPiece[movingPiece];
            if (toField == targetFieldOfLastOppositeMove && deltaWeight > bestWeightCapturingLastPlayedOppositePiece) {
                if (bestMoveCapturingLastPlayedOppositePiece != 0) {
                    getCapturesBucket(deltaWeight).add(bestMoveCapturingLastPlayedOppositePiece, (int) bestWeightCapturingLastPlayedOppositePiece);
                }
                bestMoveCapturingLastPlayedOppositePiece = move;
                bestWeightCapturingLastPlayedOppositePiece = deltaWeight;
            } else {
                getCapturesBucket(deltaWeight).add(move, (int) deltaWeight);
            }
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

    private SortableMovesBucket getCapturesBucket(float deltaWeight) {
        return deltaWeight > 0 ? bucketWinningCaptures : bucketOtherCaptures;
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

    @Override
    public final Moves getSortedMoves() {
        final Moves moves = new Moves();
        final IntArray movesArray = moves.moves;

        bucketWinningCaptures.sort();
        bucketOtherCaptures.sort();

        if (bestMoveCapturingLastPlayedOppositePiece != 0) {
            movesArray.add(bestMoveCapturingLastPlayedOppositePiece);
        }
        movesArray.addAll(bucketWinningCaptures.getMoves());
        movesArray.addAll(bucketKillerMoves);
        movesArray.addAll(bucketOtherCaptures.getMoves());
        movesArray.addAll(bucketForwardMoves);
        movesArray.addAll(bucketRemainingMoves);
        movesArray.addAll(bucketKingMoves);

        return moves;
    }
}
