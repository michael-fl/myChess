package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Move;

import java.util.Arrays;
import java.util.List;

/**
 * Keeps unnatural moves out of the far group of {@code MoveTolerance} (UCI option
 * {@code MoveToleranceFilter}). The near group is never filtered.
 *
 * <p>A piece is tracked by its square. An own move that ended on that square moved the piece.
 *
 * @author Michael Fleischhauer
 */
final class FarMoveFilter {

    /** How many own moves back the return rule looks, unless {@code returnPlies} is larger. */
    static final int RETURN_WINDOW = 10;

    /** Own moves of the side to move, newest first. */
    private final int[] ownMoves;
    private final boolean white;
    private final int returnPlies;
    private final int samePieceMoves;

    /**
     * @param statusStack    the game's status stack, oldest first
     * @param returnPlies    N of the return rule (UCI option {@code MoveToleranceReturnPlies})
     * @param samePieceMoves M of the same-piece rule (UCI option {@code MoveToleranceSamePiece})
     */
    FarMoveFilter(List<GameStatus> statusStack, int returnPlies, int samePieceMoves) {
        this(ownMoves(statusStack), statusStack.getLast().getTurn() == GameStatus.TURN_WHITE, returnPlies,
                samePieceMoves);
    }

    /** For tests. {@code ownMoves} are the side to move's moves, newest first. */
    FarMoveFilter(int[] ownMoves, boolean white, int returnPlies, int samePieceMoves) {
        this.ownMoves = ownMoves;
        this.white = white;
        this.returnPlies = returnPlies;
        this.samePieceMoves = samePieceMoves;
    }

    /**
     * The side to move's own moves, newest first. The last status was reached by the opponent's move.
     * So the own moves lead to every second status before it.
     */
    static int[] ownMoves(List<GameStatus> statusStack) {
        final int[] moves = new int[statusStack.size() / 2];
        int count = 0;

        for (int i = statusStack.size() - 2; i >= 1; i -= 2) {
            final int move = statusStack.get(i).getLastMove();

            if (move == 0) {
                break;
            }
            moves[count++] = move;
        }

        return Arrays.copyOf(moves, count);
    }

    /**
     * Whether a far candidate must not be played. It is blocked if it breaks at least one of the three
     * rules. A blocked candidate is left out of the draw. If every far candidate is blocked, a near
     * candidate is played.
     */
    boolean isBlocked(int move) {
        return blockingRule(move) != null;
    }

    /** The name of the first rule the move breaks, for the log. Null if it breaks none. */
    String blockingRule(int move) {
        if (returnsToRecentSquare(move)) {
            return "return";
        }

        if (retreatsToBackRank(move)) {
            return "backrank";
        }

        return movesSamePieceAgain(move) ? "samepiece" : null;
    }

    /**
     * Return rule. The piece must not go back to a square it left in one of its last {@code returnPlies}
     * moves. Only the last max({@value #RETURN_WINDOW}, {@code returnPlies}) own moves count.
     */
    boolean returnsToRecentSquare(int move) {
        final int window = Math.clamp(ownMoves.length, 0, Math.max(RETURN_WINDOW, returnPlies));
        final int target = Move.getToField(move);
        int square = Move.getFromField(move);
        int pieceMoves = 0;

        for (int i = 0; i < window && pieceMoves < returnPlies; i++) {
            if (Move.getToField(ownMoves[i]) == square) {
                pieceMoves++;
                square = Move.getFromField(ownMoves[i]);
                if (square == target) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Back-rank rule. No piece may retreat onto the own back rank. Castling is not a retreat. */
    boolean retreatsToBackRank(int move) {
        final byte type = Move.getMoveType(move);

        if (type == Move.typeCastlingKingSide || type == Move.typeCastlingQueenSide) {
            return false;
        }

        final int fromRow = Move.getFromRow(move);
        final int toRow = Move.getToRow(move);

        return white ? toRow == 0 && fromRow > 0 : toRow == 7 && fromRow < 7;
    }

    /**
     * Same-piece rule. A piece that made each of the last {@code samePieceMoves} own moves must not move
     * again. A value of 0 disables the rule.
     */
    boolean movesSamePieceAgain(int move) {
        if (samePieceMoves <= 0 || ownMoves.length < samePieceMoves) {
            return false;
        }

        int square = Move.getFromField(move);

        for (int i = 0; i < samePieceMoves; i++) {
            if (Move.getToField(ownMoves[i]) != square) {
                return false;
            }
            square = Move.getFromField(ownMoves[i]);
        }

        return true;
    }
}
