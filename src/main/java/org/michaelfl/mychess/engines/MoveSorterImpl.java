package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.IntArray;
import org.michaelfl.mychess.KillerMoves;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveSorter;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MovesArray;
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

    private final KillerMoves killerMoves;
    private int knownBestMove;
    private int targetFieldOfLastOppositeMove;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board board;
    private int depth;

    public MoveSorterImpl() {
        this(new KillerMoves());
    }

    public MoveSorterImpl(KillerMoves killerMoves) {
        this.killerMoves = killerMoves;
    }

    @Override
    public void reset(GameStatus gameStatus, Board board, int depth, int knownBestMove) {
        this.board = board;
        this.depth = depth;
        this.knownBestMove = knownBestMove;

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
    public void addMove(final int move, final int fromField, final int toField, final byte movingPiece, final byte capturedPiece) {
        if (move == knownBestMove) {
            return;
        }
        if (killerMoves.isKillerMove(move, depth)) {
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
            final int srcWeight = PieceSquareTables.getPieceSquareWeight(movingPiece, fromField);
            final int destWeight = PieceSquareTables.getPieceSquareWeight(movingPiece, toField);
            final int weight = destWeight - srcWeight;

            bucketRemainingMoves.add(move, weight);
        }
    }

    private SortableMovesBucket getCapturesBucket(float deltaWeight) {
        return deltaWeight > 0 ? bucketWinningCaptures : bucketOtherCaptures;
    }

    @Override
    public Moves getSortedMoves() {
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
        movesArray.addAll(bucketKillerMoves); // TODO Killer moves seem to increase calculation time!?
        movesArray.addAll(bucketOtherCaptures.getMoves());
        movesArray.addAll(bucketRemainingMoves.getMoves());
        movesArray.addAll(bucketKingMoves); // TODO: Change this in endgame

        return moves;
    }
}
