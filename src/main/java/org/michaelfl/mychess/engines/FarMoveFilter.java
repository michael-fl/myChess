package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
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

    /** The mobility rule blocks a move only if the piece loses at least this many squares. */
    static final int MOBILITY_MIN_DROP = 2;

    /** Own moves of the side to move, newest first. */
    private final int[] ownMoves;
    private final boolean white;
    private final int returnPlies;
    private final int samePieceMoves;

    /** A copy of the root position for the mobility rule. Null when the rule is off. */
    private final Board board;
    private final int mobilityDropPercent;

    /**
     * @param statusStack    the game's status stack, oldest first
     * @param returnPlies    N of the return rule (UCI option {@code MoveToleranceReturnPlies})
     * @param samePieceMoves M of the same-piece rule (UCI option {@code MoveToleranceSamePiece})
     */
    FarMoveFilter(List<GameStatus> statusStack, int returnPlies, int samePieceMoves) {
        this(ownMoves(statusStack), statusStack.getLast().getTurn() == GameStatus.TURN_WHITE, returnPlies,
                samePieceMoves);
    }

    /**
     * All four rules.
     *
     * @param board               the root position. The filter works on its own copy.
     * @param mobilityDropPercent the mobility rule's limit (UCI option {@code MoveToleranceMobilityDrop}).
     *                            0 disables the rule.
     */
    FarMoveFilter(Board board, int returnPlies, int samePieceMoves, int mobilityDropPercent) {
        this(ownMoves(board.getGameStatusStackCopy()), board.getGameStatus().getTurn() == GameStatus.TURN_WHITE,
                returnPlies, samePieceMoves, mobilityDropPercent > 0 ? board.copy() : null, mobilityDropPercent);
    }

    /** For tests. {@code ownMoves} are the side to move's moves, newest first. The mobility rule is off. */
    FarMoveFilter(int[] ownMoves, boolean white, int returnPlies, int samePieceMoves) {
        this(ownMoves, white, returnPlies, samePieceMoves, null, 0);
    }

    private FarMoveFilter(int[] ownMoves, boolean white, int returnPlies, int samePieceMoves, Board board,
                          int mobilityDropPercent) {
        this.ownMoves = ownMoves;
        this.white = white;
        this.returnPlies = returnPlies;
        this.samePieceMoves = samePieceMoves;
        this.board = board;
        this.mobilityDropPercent = mobilityDropPercent;
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
     * Whether a far candidate must not be played. It is blocked if it breaks at least one of the four
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

        if (movesSamePieceAgain(move)) {
            return "samepiece";
        }

        return losesMobility(move) ? "mobility" : null;
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

    /**
     * Mobility rule. A knight, bishop, rook or queen must not move to a square where its mobility is more
     * than {@code mobilityDropPercent} percent and at least {@value #MOBILITY_MIN_DROP} squares lower than
     * on its current square. Pawns and the king, captures and checks are exempt.
     */
    boolean losesMobility(int move) {
        if (board == null) {
            return false;
        }

        final int from = Move.getFromField(move);
        final int to = Move.getToField(move);
        final byte piece = board.get(from);
        final boolean counted = ChessUtil.isKnight(piece) || ChessUtil.isBishop(piece) || ChessUtil.isRook(piece)
                || ChessUtil.isQueen(piece);

        if (!counted || board.get(to) != Board.empty) {
            return false;
        }

        final int before = mobility(board, from);

        board.makeMove(move);

        try {
            if (board.isKingChecked()) {
                return false;
            }

            final int drop = before - mobility(board, to);

            return drop >= MOBILITY_MIN_DROP && drop * 100 > before * mobilityDropPercent;
        } finally {
            board.revertMove();
        }
    }

    /** The squares the piece on {@code field} can move to: empty ones and ones with an enemy piece. */
    static int mobility(Board board, int field) {
        final byte[] squares = board.getRawBoard();
        final byte piece = squares[field];
        final boolean white = ChessUtil.isWhitePiece(piece);

        return switch (piece) {
            case Board.whiteKnight, Board.blackKnight -> knightMobility(squares, field, white);
            case Board.whiteBishop, Board.blackBishop -> rayMobility(squares, field, white, Board.DIAGONAL_RAY_DIRS);
            case Board.whiteRook, Board.blackRook -> rayMobility(squares, field, white, Board.ORTHOGONAL_RAY_DIRS);
            case Board.whiteQueen, Board.blackQueen -> rayMobility(squares, field, white, Board.DIAGONAL_RAY_DIRS)
                    + rayMobility(squares, field, white, Board.ORTHOGONAL_RAY_DIRS);
            default -> 0;
        };
    }

    private static int knightMobility(byte[] squares, int field, boolean white) {
        int count = 0;

        for (int offset : Board.KNIGHT_OFFSETS) {
            final byte target = squares[field + offset];

            if (target == Board.empty || isEnemy(target, white)) {
                count++;
            }
        }

        return count;
    }

    private static int rayMobility(byte[] squares, int field, boolean white, int[] directions) {
        int count = 0;

        for (int direction : directions) {
            int to = field + direction;

            while (squares[to] == Board.empty) {
                count++;
                to += direction;
            }

            if (isEnemy(squares[to], white)) {
                count++;
            }
        }

        return count;
    }

    /** Whether {@code target} is a piece of the other color. False for an empty or illegal square. */
    private static boolean isEnemy(byte target, boolean white) {
        return white ? ChessUtil.isBlackPiece(target) : ChessUtil.isWhitePiece(target);
    }
}
