package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

/**
 * Default {@link MoveSorter}. Emits moves in this order:
 * pvMove (previous iteration's principal-variation move at this depth),
 * ttMove (best move from a transposition-table hit at this position),
 * recapture of the last moved piece, winning captures,
 * {@link KillerMoves}, other captures, quiet moves, king moves.
 *
 * <p>The two front-loaded ordering hints ({@code pvMove}, {@code ttMove})
 * are passed in via {@link #reset(GameStatus, Board, int, int, int)} and
 * are added to the output only when the move generator actually reports
 * them through {@link #addMove}. Tracking this via per-hint
 * {@code pvMoveSeen} / {@code ttMoveSeen} flags protects against stale
 * hints (e.g. a TT bestMove from an unreachable Zobrist-collision
 * neighbor, or a PV entry that survived a search-tree shape change):
 * unseen hints are skipped and logged via {@link Log#info} rather than
 * blindly added — handing an unplayable move to the search loop would
 * otherwise crash inside {@link Board#makeMove(int)}.
 *
 * @author Michael Fleischhauer
 */
public final class MoveSorterImpl implements MoveSorter {

    private final MovesArray bucketKillerMoves = new MovesArray();
    private int bestMoveCapturingLastPlayedOppositePiece;
    private float bestWeightCapturingLastPlayedOppositePiece = Float.NEGATIVE_INFINITY;
    private final SortableMovesBucket bucketWinningCaptures = new SortableMovesBucket();
    private final SortableMovesBucket bucketOtherCaptures = new SortableMovesBucket();
    private final SortableMovesBucket bucketRemainingMoves = new SortableMovesBucket();
    private final MovesArray bucketKingMoves = new MovesArray();

    private final KillerMoves killerMoves;
    private int pvMove;
    private int ttMove;
    private boolean pvMoveSeen;
    private boolean ttMoveSeen;
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
    public void reset(GameStatus gameStatus, Board board, int depth, int pvMove, int ttMove) {
        this.board = board;
        this.depth = depth;
        this.pvMove = pvMove;
        this.ttMove = ttMove;
        this.pvMoveSeen = false;
        this.ttMoveSeen = false;

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
        if (move == pvMove) {
            pvMoveSeen = true;
            return;
        }
        if (move == ttMove) {
            ttMoveSeen = true;
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

        if (pvMove != 0) {
            if (pvMoveSeen) {
                movesArray.add(pvMove);
            } else {
                Log.info("[sort] pvMove " + ChessUtil.moveToString(pvMove)
                        + " not produced by MoveGenerator at depth=" + depth
                        + ", hash=" + Long.toHexString(board.getGameStatus().getPositionHash())
                        + " — skipping (invariant violation, see roadmap)");
            }
        }

        if (ttMove != 0 && ttMove != pvMove) {
            if (ttMoveSeen) {
                movesArray.add(ttMove);
            } else {
                Log.info("[sort] ttMove " + ChessUtil.moveToString(ttMove)
                        + " not produced by MoveGenerator at depth=" + depth
                        + ", hash=" + Long.toHexString(board.getGameStatus().getPositionHash())
                        + " — skipping (invariant violation, see roadmap)");
            }
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
