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
    private final boolean isQuiescenceSearch;
    private final StaticExchangeEvaluation see;

    private int pvMove;
    private int ttMove;
    private boolean pvMoveSeen;
    private boolean ttMoveSeen;
    private int targetFieldOfLastOppositeMove;
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Board board;
    private int depth;

    /**
     * Full-search sorter with a private, empty killer-move table. Convenience for
     * callers (mainly tests) that do not share killers across nodes; the engine
     * itself uses {@link #MoveSorterImpl(KillerMoves)} to share one table.
     */
    public MoveSorterImpl() {
        this(new KillerMoves(), false);
    }

    /**
     * Full-search sorter that shares the given killer-move table, so beta-cutoff
     * killers recorded at one node are reused for move ordering at sibling nodes of
     * the same depth. Captures are ordered by the material delta
     * ({@code captured − mover}); no static exchange evaluation and no pruning.
     *
     * @param killerMoves the killer table shared across the whole search
     */
    public MoveSorterImpl(KillerMoves killerMoves) {
        this(killerMoves, false);
    }

    /**
     * Shared implementation of the two sorting modes.
     *
     * <p>In quiescence mode captures are scored and ordered by their static
     * exchange value and losing captures ({@code SEE < 0}) are dropped; the
     * {@link StaticExchangeEvaluation} is created eagerly here and re-initialised on
     * every {@link #reset}. In full-search mode captures are ordered by the material
     * delta and nothing is pruned.
     *
     * @param killerMoves        the shared killer table, or {@code null} in
     *                           quiescence mode — quiescence only searches captures
     *                           and a capture can never be a killer (killers are
     *                           quiet moves), so the table is never consulted
     * @param isQuiescenceSearch {@code true} for SEE ordering plus losing-capture
     *                           pruning, {@code false} for the full search
     */
    private MoveSorterImpl(KillerMoves killerMoves, boolean isQuiescenceSearch) {
        this.killerMoves = killerMoves;
        this.isQuiescenceSearch = isQuiescenceSearch;
        this.see = isQuiescenceSearch ? new StaticExchangeEvaluation() : null;
    }

    /**
     * Builds the sorter used by the quiescence search: it orders captures by static
     * exchange value and prunes losing captures ({@code SEE < 0}). It carries no
     * killer-move table (see the private constructor for why).
     */
    public static MoveSorterImpl forQuiescenceSearch() {
        return new MoveSorterImpl(null, true);
    }

    @Override
    public void reset(GameStatus gameStatus, Board board, int depth, int pvMove, int ttMove) {
        this.board = board;
        this.depth = depth;
        this.pvMove = pvMove;
        this.ttMove = ttMove;
        this.pvMoveSeen = false;
        this.ttMoveSeen = false;

        if (see != null) {
            see.init(board);
        }

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
        if (killerMoves != null && killerMoves.isKillerMove(move, depth)) {
            bucketKillerMoves.add(move);
        } else if (capturedPiece != 0) {
            final int deltaWeight = captureWeight(move, movingPiece, capturedPiece);
            if (!isQuiescenceSearch || deltaWeight >= 0) { // Prune losing captures in quiescence search
                if (toField == targetFieldOfLastOppositeMove && deltaWeight > bestWeightCapturingLastPlayedOppositePiece) {
                    if (bestMoveCapturingLastPlayedOppositePiece != 0) {
                        getCapturesBucket(deltaWeight).add(bestMoveCapturingLastPlayedOppositePiece, (int) bestWeightCapturingLastPlayedOppositePiece);
                    }
                    bestMoveCapturingLastPlayedOppositePiece = move;
                    bestWeightCapturingLastPlayedOppositePiece = deltaWeight;
                } else {
                    getCapturesBucket(deltaWeight).add(move, deltaWeight);
                }
            }
        } else if (Board.isKing(movingPiece)) {
            bucketKingMoves.add(move);
        } else {
            final int srcWeight = PieceSquareTables.getMidGameWeight(movingPiece, fromField);
            final int destWeight = PieceSquareTables.getMidGameWeight(movingPiece, toField);
            final int weight = destWeight - srcWeight;

            bucketRemainingMoves.add(move, weight);
        }
    }

    private int captureWeight(int move, byte movingPiece, byte capturedPiece) {
        if (!isQuiescenceSearch) {
            return WeightingFunction.weightOfPiece[capturedPiece] - WeightingFunction.weightOfPiece[movingPiece];
        } else {
            return see.see(move);
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
                int lastMove = board.getGameStatus().getLastMove();
                Log.info("[sort] ttMove " + ChessUtil.moveToString(ttMove)
                        + " (" + ChessUtil.pieceToDebugString(board.get(Move.getFromField(ttMove))) + ")"
                        + " not produced by MoveGenerator at depth=" + depth
                        + ", last move=" + ChessUtil.moveToString(lastMove)
                        + " (" + ChessUtil.pieceToDebugString(board.get(Move.getToField(lastMove))) + ")"
                        + ", turn=" + (((board.getGameStatus().getTurn() & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE) ? "white" : "black")
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
