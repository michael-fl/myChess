package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.IntArray;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveSorter;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MovesArray;
import org.michaelfl.mychess.MovesCounter;
import org.michaelfl.mychess.PieceSquareTables;
import org.michaelfl.mychess.SortableMovesBucket;
import org.michaelfl.mychess.WeightingFunction;

public final class MoveSorterImpl implements MoveSorter {

    private final MovesArray bucketKillerMoves = new MovesArray();
    private int bestMoveCapturingLastPlayedOppositePiece;
    private float bestWeightCapturingLastPlayedOppositePiece = Float.NEGATIVE_INFINITY;
    private final SortableMovesBucket bucketWinningCaptures = new SortableMovesBucket();
    private final SortableMovesBucket bucketOtherCaptures = new SortableMovesBucket();
    private final SortableMovesBucket bucketRemainingMoves = new SortableMovesBucket();
    private final MovesArray bucketKingMoves = new MovesArray();

    private final MovesCounter killerMoves;
    private GameStatus gameStatus;
    private int knownBestMove;
    private int targetFieldOfLastOppositeMove;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board board;
    private short[] topKillerMoves;

    public MoveSorterImpl() {
        this(new MovesCounter(1));
    }

    public MoveSorterImpl(MovesCounter killerMoves) {
        this.killerMoves = killerMoves;
    }

    @Override
    public final void reset(GameStatus gameStatus, Board board, int depth, int knownBestMove) {
        this.gameStatus = gameStatus;
        this.board = board;
        this.knownBestMove = knownBestMove;
        this.topKillerMoves = killerMoves.getMovesOnDepth(depth).getTopMoves();

        targetFieldOfLastOppositeMove = Move.getToField(gameStatus.getLastMove());

        bestMoveCapturingLastPlayedOppositePiece = 0;
        bestWeightCapturingLastPlayedOppositePiece = Float.NEGATIVE_INFINITY;
        bucketWinningCaptures.clear();
        bucketOtherCaptures.clear();
        bucketKillerMoves.clear();
        bucketRemainingMoves.clear();
        bucketKingMoves.clear();
    }

    @Override
    public final void addMove(final int move, final int fromField, final int toField, final byte movingPiece, final byte capturedPiece) {
        if (move == knownBestMove) {
            return;
        }
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
            final boolean isBackwardMove = (gameStatus.isWhiteTurn() && rowDelta < 0) || (gameStatus.isBlackTurn() && rowDelta > 0);

            final int srcWeight = PieceSquareTables.getPieceSquareWeight(movingPiece, fromField);
            final int destWeight = PieceSquareTables.getPieceSquareWeight(movingPiece, toField);
            final int weight = destWeight - srcWeight - (isBackwardMove ? 5 : 0);

            bucketRemainingMoves.add(move, weight);
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
        bucketRemainingMoves.sort();

        if (knownBestMove != 0) {
            movesArray.add(knownBestMove);
        }
        if (bestMoveCapturingLastPlayedOppositePiece != 0) {
            movesArray.add(bestMoveCapturingLastPlayedOppositePiece);
        }
        movesArray.addAll(bucketWinningCaptures.getMoves());
        movesArray.addAll(bucketKillerMoves);
        movesArray.addAll(bucketOtherCaptures.getMoves());
        movesArray.addAll(bucketRemainingMoves.getMoves());
        movesArray.addAll(bucketKingMoves); // TODO: Change this in endgame

        return moves;
    }
}
