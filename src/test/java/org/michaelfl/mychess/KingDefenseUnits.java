package org.michaelfl.mychess;

/**
 * Own pieces bearing on their own king's 3×3 zone, computed from a {@link Board} alone.
 *
 * <p>The defense counterpart of {@link KingAttackUnits}, written the same way: it looks from
 * every zone square back to the pieces that attack it. Each piece counts once however many zone
 * squares it bears on. The zone is the production zone, shifted off the a- and h-file by
 * {@link WeightingFunction#KING_FIELD_CORRECTION_OFFSET}.
 *
 * <p>It counts per piece kind, not weighted. The tests weight the counts with the production
 * constants. The measurement weights them with candidate values.
 *
 * <p>Pawns count when they attack a zone square, like every other piece. A pawn that stands in
 * the zone but attacks no zone square is a shield pawn. {@link #shieldPawns} counts those
 * separately. The own king always stands in its own zone and always counts.
 */
final class KingDefenseUnits {

    static final int PAWN = 0;
    static final int KNIGHT = 1;
    static final int BISHOP = 2;
    static final int ROOK = 3;
    static final int QUEEN = 4;
    static final int KING = 5;
    static final int KINDS = 6;

    private KingDefenseUnits() {
        // utility
    }

    /**
     * Distinct own pieces per kind that bear on the own king zone.
     *
     * @param board the position
     * @param color {@link GameStatus#TURN_WHITE} or {@link GameStatus#TURN_BLACK}, the defending side
     * @return counts indexed by {@link #PAWN} .. {@link #KING}, all zero without a king
     */
    static int[] countsOf(Board board, int color) {
        final byte[] squares = board.getRawBoard();
        final boolean white = color == GameStatus.TURN_WHITE;
        final int[] counts = new int[KINDS];
        final int corrected = zoneCenter(squares, white);

        if (corrected < 0) {
            return counts;
        }

        final boolean[] counted = new boolean[Board.LENGTH * Board.LENGTH];

        countOn(squares, corrected, white, counted, counts);

        for (int offset : Board.KING_ADJACENCY_OFFSETS) {
            final int zoneField = corrected + offset;

            if (squares[zoneField] != Board.illegal) {
                countOn(squares, zoneField, white, counted, counts);
            }
        }

        return counts;
    }

    /** The counts of {@link #countsOf} weighted per kind. */
    static int weighted(Board board, int color, int[] weights) {
        final int[] counts = countsOf(board, color);
        int sum = 0;

        for (int kind = 0; kind < KINDS; kind++) {
            sum += counts[kind] * weights[kind];
        }

        return sum;
    }

    /**
     * Own pawns standing in the own king zone, whether or not they attack a zone square.
     *
     * @param board the position
     * @param color the defending side
     * @return the number of own pawns inside the zone, 0 without a king
     */
    static int shieldPawns(Board board, int color) {
        final byte[] squares = board.getRawBoard();
        final boolean white = color == GameStatus.TURN_WHITE;
        final byte pawn = white ? Board.whitePawn : Board.blackPawn;
        final int corrected = zoneCenter(squares, white);

        if (corrected < 0) {
            return 0;
        }

        int pawns = squares[corrected] == pawn ? 1 : 0;

        for (int offset : Board.KING_ADJACENCY_OFFSETS) {
            if (squares[corrected + offset] == pawn) {
                pawns++;
            }
        }

        return pawns;
    }

    private static int zoneCenter(byte[] squares, boolean white) {
        final byte king = white ? Board.whiteKing : Board.blackKing;

        for (int field = Board.a1; field <= Board.h8; field++) {
            if (squares[field] == king) {
                return field + WeightingFunction.KING_FIELD_CORRECTION_OFFSET[field % Board.LENGTH - 2];
            }
        }

        return -1;
    }

    private static void countOn(byte[] squares, int field, boolean white, boolean[] counted, int[] counts) {
        final byte pawn = white ? Board.whitePawn : Board.blackPawn;
        final int back = white ? -Board.LENGTH : Board.LENGTH;

        take(squares, field + back - 1, pawn, PAWN, counted, counts);
        take(squares, field + back + 1, pawn, PAWN, counted, counts);

        final byte knight = white ? Board.whiteKnight : Board.blackKnight;

        for (int offset : Board.KNIGHT_OFFSETS) {
            take(squares, field + offset, knight, KNIGHT, counted, counts);
        }

        final byte king = white ? Board.whiteKing : Board.blackKing;

        take(squares, field, king, KING, counted, counts);

        for (int offset : Board.KING_ADJACENCY_OFFSETS) {
            take(squares, field + offset, king, KING, counted, counts);
        }

        final byte bishop = white ? Board.whiteBishop : Board.blackBishop;
        final byte rook = white ? Board.whiteRook : Board.blackRook;
        final byte queen = white ? Board.whiteQueen : Board.blackQueen;

        alongRays(squares, field, Board.DIAGONAL_RAY_DIRS, bishop, BISHOP, queen, counted, counts);
        alongRays(squares, field, Board.ORTHOGONAL_RAY_DIRS, rook, ROOK, queen, counted, counts);

        // A slider standing on a zone square bears on the zone by itself.
        take(squares, field, bishop, BISHOP, counted, counts);
        take(squares, field, rook, ROOK, counted, counts);
        take(squares, field, queen, QUEEN, counted, counts);
    }

    private static void alongRays(byte[] squares, int field, int[] directions, byte slider, int sliderKind,
                                  byte queen, boolean[] counted, int[] counts) {
        for (int direction : directions) {
            int to = field + direction;

            while (squares[to] == Board.empty) {
                to += direction;
            }

            take(squares, to, slider, sliderKind, counted, counts);
            take(squares, to, queen, QUEEN, counted, counts);
        }
    }

    private static void take(byte[] squares, int field, byte wanted, int kind, boolean[] counted, int[] counts) {
        if (squares[field] == wanted && !counted[field]) {
            counted[field] = true;
            counts[kind]++;
        }
    }
}
