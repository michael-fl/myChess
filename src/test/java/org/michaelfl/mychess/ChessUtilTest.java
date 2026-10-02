package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class ChessUtilTest {

    @Test
    void testGetPieceNumber12() {
        assertEquals(0, ChessUtil.getPieceNumber12(Board.whitePawn));
        assertEquals(1, ChessUtil.getPieceNumber12(Board.whiteKnight));
        assertEquals(2, ChessUtil.getPieceNumber12(Board.whiteBishop));
        assertEquals(3, ChessUtil.getPieceNumber12(Board.whiteRook));
        assertEquals(4, ChessUtil.getPieceNumber12(Board.whiteQueen));
        assertEquals(5, ChessUtil.getPieceNumber12(Board.whiteKing));
        assertEquals(6, ChessUtil.getPieceNumber12(Board.blackPawn));
        assertEquals(7, ChessUtil.getPieceNumber12(Board.blackKnight));
        assertEquals(8, ChessUtil.getPieceNumber12(Board.blackBishop));
        assertEquals(9, ChessUtil.getPieceNumber12(Board.blackRook));
        assertEquals(10, ChessUtil.getPieceNumber12(Board.blackQueen));
        assertEquals(11, ChessUtil.getPieceNumber12(Board.blackKing));
    }

    @Test
    void testGetFieldNumber64() {
        assertEquals(0, ChessUtil.getFieldNumber64(Board.a1));
        assertEquals(1, ChessUtil.getFieldNumber64(Board.b1));
        assertEquals(7, ChessUtil.getFieldNumber64(Board.h1));
        assertEquals(8, ChessUtil.getFieldNumber64(Board.a2));
        assertEquals(63, ChessUtil.getFieldNumber64(Board.h8));
    }

    // ---- Field <-> string round-trips ----

    @Test
    void fieldToString_basicSquares() {
        assertEquals("a1", ChessUtil.fieldToString(Board.a1), "a1 field-to-string");
        assertEquals("h8", ChessUtil.fieldToString(Board.h8), "h8 field-to-string");
        assertEquals("e4", ChessUtil.fieldToString(Board.e4), "e4 field-to-string");
    }

    @Test
    void getColAndRowFromString_validInputs() {
        assertArrayEquals(new int[]{0, 0}, ChessUtil.getColAndRowFromString("a1"));
        assertArrayEquals(new int[]{7, 7}, ChessUtil.getColAndRowFromString("h8"));
        assertArrayEquals(new int[]{4, 3}, ChessUtil.getColAndRowFromString("e4"));
    }

    @Test
    void getColAndRowFromString_rejectsBadInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> ChessUtil.getColAndRowFromString("z3"),
                "z3 is outside a-h");
        assertThrows(IllegalArgumentException.class,
                () -> ChessUtil.getColAndRowFromString("a9"),
                "a9 is outside 1-8");
        assertThrows(IllegalArgumentException.class,
                () -> ChessUtil.getColAndRowFromString("aa"),
                "aa is not a coordinate");
        assertThrows(IllegalArgumentException.class,
                () -> ChessUtil.getColAndRowFromString("e"),
                "single-char input is rejected");
    }

    // ---- moveToString variants ----

    @Test
    void moveToString_normalMove() {
        int move = Move.create(Board.e2, Board.e4, Board.empty, Move.typeNormal);
        assertEquals("e2-e4", ChessUtil.moveToString(move), "Normal move long-algebraic");
    }

    @Test
    void moveToString_nilMove() {
        assertEquals("nil", ChessUtil.moveToString(0),
                "Move 0 must render as 'nil'");
    }

    @Test
    void moveToString_promotionAppendsPieceSymbol() {
        int q = Move.create(Board.a7, Board.a8, Board.empty, Move.typePawnPromotionQueen);
        int n = Move.create(Board.a7, Board.a8, Board.empty, Move.typePawnPromotionKnight);
        int r = Move.create(Board.a7, Board.a8, Board.empty, Move.typePawnPromotionRook);
        int b = Move.create(Board.a7, Board.a8, Board.empty, Move.typePawnPromotionBishop);
        assertEquals("a7-a8Q", ChessUtil.moveToString(q));
        assertEquals("a7-a8N", ChessUtil.moveToString(n));
        assertEquals("a7-a8R", ChessUtil.moveToString(r));
        assertEquals("a7-a8B", ChessUtil.moveToString(b));
    }

    // ---- pathToString ----

    @Test
    void pathToString_stopsAtZeroSentinel() {
        int[] path = new int[] {
                Move.create(Board.e2, Board.e4, Board.empty, Move.typeNormal),
                Move.create(Board.e7, Board.e5, Board.empty, Move.typeNormal),
                0,
                Move.create(Board.g1, Board.f3, Board.empty, Move.typeNormal),
        };
        assertEquals("e2-e4 e7-e5", ChessUtil.pathToString(path),
                "pathToString must stop at the first zero entry");
    }

    @Test
    void pathToString_emptyPath() {
        assertEquals("", ChessUtil.pathToString(new int[] {0, 0}),
                "All-zero path renders as empty string");
    }

    // ---- weightToString ----

    @Test
    void weightToString_normalCentipawnsAsFloat() {
        assertEquals("1.5", ChessUtil.weightToString(150),
                "150 centipawns -> 1.5 pawns");
        assertEquals("-2.3", ChessUtil.weightToString(-230),
                "-230 centipawns -> -2.3 pawns");
        assertEquals("0.0", ChessUtil.weightToString(0),
                "Zero weight renders as 0.0");
    }

    @Test
    void weightToString_checkmate() {
        // CHECKMATE_WEIGHT_HIGH - 0*100 = mate in 0
        assertEquals("M0", ChessUtil.weightToString(WeightingFunction.CHECKMATE_WEIGHT_HIGH),
                "Top of checkmate range renders as M0");
        // Mate-in-3 from white's perspective
        assertEquals("M3", ChessUtil.weightToString(WeightingFunction.CHECKMATE_WEIGHT_HIGH - 300),
                "Mate in 3 plies renders as M3");
    }

    @Test
    void weightToString_illegal() {
        assertEquals("illegal", ChessUtil.weightToString(WeightingFunction.ILLEGAL_WEIGHT_POS),
                "Illegal-weight value renders as 'illegal'");
    }

    // ---- piece <-> symbol round-trips ----

    @Test
    void pieceToString_pieceLetters() {
        assertEquals("P", ChessUtil.pieceToString(Board.whitePawn));
        assertEquals("N", ChessUtil.pieceToString(Board.whiteKnight));
        assertEquals("B", ChessUtil.pieceToString(Board.whiteBishop));
        assertEquals("R", ChessUtil.pieceToString(Board.whiteRook));
        assertEquals("Q", ChessUtil.pieceToString(Board.whiteQueen));
        assertEquals("K", ChessUtil.pieceToString(Board.whiteKing));
        // Black pieces share the same letter (color carried elsewhere)
        assertEquals("P", ChessUtil.pieceToString(Board.blackPawn));
        assertEquals("K", ChessUtil.pieceToString(Board.blackKing));
    }

    @Test
    void symbolToPiece_white_andBlack() {
        assertEquals(Board.whitePawn, ChessUtil.symbolToPiece('P', GameStatus.TURN_WHITE));
        assertEquals(Board.whiteKnight, ChessUtil.symbolToPiece('N', GameStatus.TURN_WHITE));
        assertEquals(Board.whiteKing, ChessUtil.symbolToPiece('K', GameStatus.TURN_WHITE));

        assertEquals(Board.blackPawn, ChessUtil.symbolToPiece('P', GameStatus.TURN_BLACK));
        assertEquals(Board.blackQueen, ChessUtil.symbolToPiece('Q', GameStatus.TURN_BLACK));
    }

    @Test
    void symbolToPiece_rejectsUnknownSymbol() {
        assertThrows(IllegalArgumentException.class,
                () -> ChessUtil.symbolToPiece('X', GameStatus.TURN_WHITE),
                "Unknown symbol must throw");
    }

    // ---- setBit ----

    @Test
    void setBit_setsBit() {
        assertEquals(0b0011, ChessUtil.setBit(0b0001, 0b0010),
                "setBit must OR the bit into the set");
        assertEquals(0b1001, ChessUtil.setBit(0b1001, 0b1000),
                "setBit must be idempotent when the bit is already set");
    }

    // ---- findColOfPieceOnRow ----

    @Test
    void findColOfPieceOnRow_whiteKingOnBackRank_returnsColumn4() {
        var board = Board.createNewGame();

        assertEquals(4, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.whiteKing, 0),
                "white king starts on the e-file (column 4)");
    }

    @Test
    void findColOfPieceOnRow_blackKingOnBackRank_returnsColumn4() {
        var board = Board.createNewGame();

        assertEquals(4, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.blackKing, 7),
                "black king starts on the e-file (column 4)");
    }

    @Test
    void findColOfPieceOnRow_whiteQueenOnBackRank_returnsColumn3() {
        var board = Board.createNewGame();

        assertEquals(3, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.whiteQueen, 0),
                "white queen starts on the d-file (column 3)");
    }

    @Test
    void findColOfPieceOnRow_blackQueenOnBackRank_returnsColumn3() {
        var board = Board.createNewGame();

        assertEquals(3, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.blackQueen, 7),
                "black queen starts on the d-file (column 3)");
    }

    @Test
    void findColOfPieceOnRow_aFileRook_returnsColumn0() {
        // Column 0 is the a-file; the queenside rook starts there. The
        // method's "not found" sentinel is also 0 (Board.empty), so a
        // legitimate hit at column 0 is the case where the sentinel choice
        // is most ambiguous.
        var board = Board.createNewGame();

        assertEquals(0, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.whiteRook, 0),
                "white queenside rook starts on the a-file (column 0)");
    }

    @Test
    void findColOfPieceOnRow_multipleMatches_returnsFirstFromLeft() {
        // Standard chess has white rooks on both a1 (col 0) and h1 (col 7).
        // The method must return the leftmost match — column 0 — as the
        // "first hit wins" semantics implied by a left-to-right scan.
        var board = Board.createNewGame();

        assertEquals(0, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.whiteRook, 0),
                "first matching column wins (a-file before h-file)");
    }

    @Test
    void findColOfPieceOnRow_pieceNotOnRow_returnsMinusOne() {
        // White king is on row 0 (rank 1), never on row 3 in the start
        // position. The not-found sentinel is -1 (not Board.empty, which
        // would collide with the valid column index 0 = a-file).
        var board = Board.createNewGame();

        assertEquals(-1, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.whiteKing, 3),
                "no white king on the middle of the board ⇒ -1");
    }

    @Test
    void findColOfPieceOnRow_chess960Position_findsKingAtNonStandardFile() {
        // RKBBNRNQ — white king on b1 (column 1), not e1.
        var board = Fen.importFEN("rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1");

        assertEquals(1, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.whiteKing, 0),
                "cutechess sample 960 position: white king on column 1");
    }

    @Test
    void findColOfPieceOnRow_chess960Position_findsBlackKingMirrored() {
        // Same 960 position; black king mirrors on b8 (column 1, row 7).
        var board = Fen.importFEN("rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1");

        assertEquals(1, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.blackKing, 7),
                "cutechess sample 960 position: black king on column 1");
    }

    @Test
    void findColOfPieceOnRow_pieceOnDifferentRow_returnsMinusOne() {
        // Black king is on row 7, not row 0. Searching row 0 for the black
        // king must report not-found (-1), not silently confuse with the
        // white king that does sit on row 0.
        var board = Board.createNewGame();

        assertEquals(-1, ChessUtil.findColOfPieceOnRow(board.getRawBoard(), Board.blackKing, 0),
                "black king is not on row 0 in the start position");
    }

    // --- king-zone reachability -------------------------------------------------------
    //
    // These four methods are a FILTER: they decide whether a piece is worth asking about at
    // all, before the per-square zone test runs. They ignore blockers, so they are allowed to
    // say "maybe" where the answer is "no" - a false positive costs a little time and nothing
    // else. A false NEGATIVE is a defect: it drops an attacker from the count silently, and
    // the evaluation is then wrong in a way no single position makes obvious.
    //
    // So the property worth testing exhaustively is one-sided: whenever a piece really does
    // attack the zone on an empty board, the filter must let it through. reachabilityIsNever\
    // WrongInTheDangerousDirection does exactly that over every piece on every square against
    // every zone centre.

    /** The 3x3 box, as {@code markKingZone} builds it: centre plus the eight neighbours. */
    private static final int[] ZONE_OFFSETS = {
            0, 1, -1, Board.LENGTH, -Board.LENGTH,
            Board.LENGTH + 1, Board.LENGTH - 1, -Board.LENGTH + 1, -Board.LENGTH - 1
    };

    private static final int[] KNIGHT_OFFSETS = {
            2 * Board.LENGTH + 1, 2 * Board.LENGTH - 1, Board.LENGTH + 2, Board.LENGTH - 2,
            -Board.LENGTH + 2, -Board.LENGTH - 2, -2 * Board.LENGTH + 1, -2 * Board.LENGTH - 1
    };

    private static final int[] DIAGONAL_OFFSETS = {
            Board.LENGTH + 1, Board.LENGTH - 1, -Board.LENGTH + 1, -Board.LENGTH - 1
    };

    private static final int[] ORTHOGONAL_OFFSETS = {1, -1, Board.LENGTH, -Board.LENGTH};

    /**
     * The border squares of the 12x12 array hold {@link Board#illegal} and nothing ever
     * overwrites them, so one board answers this for the whole class. Asking the board rather
     * than re-deriving the file and rank bounds keeps the reference on the same definition of
     * "on the board" that the evaluation itself uses - a second derivation could drift from it.
     *
     * <p>The two-square border is what makes every index this class produces safe to look up:
     * the widest step taken here is a knight's, and from any real square that lands inside the
     * array even when it lands off the playable board.
     */
    private static final Board BORDER_REFERENCE = Board.createNewGame();

    private static boolean isOnBoard(int field) {
        return BORDER_REFERENCE.get(field) != Board.illegal;
    }

    /** Every real square the piece attacks from {@code from} on an otherwise empty board. */
    private static java.util.Set<Integer> attacksOnEmptyBoard(byte piece, int from) {
        var attacked = new java.util.HashSet<Integer>();

        switch (piece) {
            case Board.whitePawn -> {
                addIfOnBoard(attacked, from + Board.LENGTH + 1);
                addIfOnBoard(attacked, from + Board.LENGTH - 1);
            }
            case Board.blackPawn -> {
                addIfOnBoard(attacked, from - Board.LENGTH + 1);
                addIfOnBoard(attacked, from - Board.LENGTH - 1);
            }
            case Board.whiteKnight, Board.blackKnight -> {
                for (int offset : KNIGHT_OFFSETS) {
                    addIfOnBoard(attacked, from + offset);
                }
            }
            case Board.whiteKing, Board.blackKing -> {
                for (int offset : ZONE_OFFSETS) {
                    if (offset != 0) {
                        addIfOnBoard(attacked, from + offset);
                    }
                }
            }
            case Board.whiteBishop, Board.blackBishop -> addRays(attacked, from, DIAGONAL_OFFSETS);
            case Board.whiteRook, Board.blackRook -> addRays(attacked, from, ORTHOGONAL_OFFSETS);
            case Board.whiteQueen, Board.blackQueen -> {
                addRays(attacked, from, DIAGONAL_OFFSETS);
                addRays(attacked, from, ORTHOGONAL_OFFSETS);
            }
            default -> {
                // Not a piece: attacks nothing.
            }
        }

        return attacked;
    }

    private static void addIfOnBoard(java.util.Set<Integer> attacked, int field) {
        if (isOnBoard(field)) {
            attacked.add(field);
        }
    }

    private static void addRays(java.util.Set<Integer> attacked, int from, int[] offsets) {
        for (int offset : offsets) {
            int field = from + offset;

            while (isOnBoard(field)) {
                attacked.add(field);
                field += offset;
            }
        }
    }

    /** The zone as the evaluation builds it: the 3x3 box, clipped to real squares. */
    private static java.util.Set<Integer> zoneOf(int centre) {
        var zone = new java.util.HashSet<Integer>();

        for (int offset : ZONE_OFFSETS) {
            addIfOnBoard(zone, centre + offset);
        }

        return zone;
    }

    private static final byte[] ALL_PIECES = {
            Board.whitePawn, Board.whiteKnight, Board.whiteBishop,
            Board.whiteRook, Board.whiteQueen, Board.whiteKing,
            Board.blackPawn, Board.blackKnight, Board.blackBishop,
            Board.blackRook, Board.blackQueen, Board.blackKing
    };

    @Test
    void reachabilityIsNeverWrongInTheDangerousDirection() {
        var failures = new java.util.ArrayList<String>();

        for (byte piece : ALL_PIECES) {
            for (int from = Board.a1; from <= Board.h8; from++) {
                if (!isOnBoard(from)) {
                    continue;
                }

                var attacked = attacksOnEmptyBoard(piece, from);

                for (int centre = Board.a1; centre <= Board.h8; centre++) {
                    if (!isOnBoard(centre) || centre == from) {
                        continue;
                    }

                    var zone = zoneOf(centre);
                    boolean reallyAttacks = zone.stream().anyMatch(attacked::contains);

                    if (reallyAttacks && !ChessUtil.pseudoAttacksKingZone(piece, from, centre)) {
                        failures.add(String.format(
                                "piece %d from %s really attacks the zone around %s, but the filter says no",
                                piece, ChessUtil.fieldToString(from), ChessUtil.fieldToString(centre)));
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(),
                failures.size() + " false negatives, each one silently drops an attacker. First five: "
                        + failures.stream().limit(5).toList());
    }

    @Test
    void chebyshevDistanceIsTheKingMoveDistance() {
        assertEquals(0, ChessUtil.chebyshevDistance(Board.a1, Board.a1), "a square to itself");
        assertEquals(7, ChessUtil.chebyshevDistance(Board.a1, Board.h8), "a1 to h8, the long diagonal");
        assertEquals(7, ChessUtil.chebyshevDistance(Board.a1, Board.h1), "a1 to h1, along the rank");
        assertEquals(1, ChessUtil.chebyshevDistance(Board.a1, Board.b2), "diagonal neighbour");

        final int e1 = ChessUtil.getFieldFromColAndRow(4, 0);
        final int h4 = ChessUtil.getFieldFromColAndRow(7, 3);
        final int h1 = ChessUtil.getFieldFromColAndRow(7, 0);

        assertEquals(3, ChessUtil.chebyshevDistance(e1, h4),
                "three files and three ranks is three king moves, not six");
        assertEquals(3, ChessUtil.chebyshevDistance(e1, h1),
                "straight and diagonal cost the same for a king");
    }

    @Test
    void chebyshevDistanceIsSymmetric() {
        for (int a = Board.a1; a <= Board.h8; a++) {
            for (int b = Board.a1; b <= Board.h8; b++) {
                if (isOnBoard(a) && isOnBoard(b)) {
                    assertEquals(ChessUtil.chebyshevDistance(a, b), ChessUtil.chebyshevDistance(b, a),
                            "distance must not depend on the order of its arguments");
                }
            }
        }
    }

    /**
     * Anchored on d4 rather than on a corner: around a corner the real zone is clipped to
     * four squares while the test still reasons about a full 3x3 box, so a corner case
     * exercises the over-approximation instead of the rule.
     */
    @Test
    void diagonalReachabilityRejectsWhatNoDiagonalCanTouch() {
        final int d4 = ChessUtil.getFieldFromColAndRow(3, 3);
        final int a1 = ChessUtil.getFieldFromColAndRow(0, 0);
        final int h1 = ChessUtil.getFieldFromColAndRow(7, 0);
        final int d1 = ChessUtil.getFieldFromColAndRow(3, 0);

        assertTrue(ChessUtil.canReachKingZoneDiagonal(a1, d4),
                "the a1-h8 diagonal runs through c3 and d4, both inside the zone");
        assertTrue(ChessUtil.canReachKingZoneDiagonal(h1, d4),
                "the h1-a8 diagonal runs through d5 and e4, both inside the zone");
        assertFalse(ChessUtil.canReachKingZoneDiagonal(d1, d4),
                "from d1 both diagonals pass the zone by: c2 and e2 are a rank below it, "
                        + "b3 and f3 a file beside it");
    }

    @Test
    void orthogonalReachabilityNeedsAFileOrARankInCommon() {
        final int d4 = ChessUtil.getFieldFromColAndRow(3, 3);
        final int c8 = ChessUtil.getFieldFromColAndRow(2, 7);
        final int h3 = ChessUtil.getFieldFromColAndRow(7, 2);
        final int a1 = ChessUtil.getFieldFromColAndRow(0, 0);

        assertTrue(ChessUtil.canReachKingZoneOrthogonal(c8, d4),
                "the c file runs through c3, c4 and c5, all inside the zone");
        assertTrue(ChessUtil.canReachKingZoneOrthogonal(h3, d4),
                "rank 3 runs through c3, d3 and e3, all inside the zone");
        assertFalse(ChessUtil.canReachKingZoneOrthogonal(a1, d4),
                "a rook on a1 sweeps the a file and rank 1, and the zone touches neither");
    }

    @Test
    void anythingThatIsNotAPieceReachesNothing() {
        final int e4 = ChessUtil.getFieldFromColAndRow(4, 3);
        final int e5 = ChessUtil.getFieldFromColAndRow(4, 4);

        assertFalse(ChessUtil.pseudoAttacksKingZone(Board.empty, e4, e5), "an empty square attacks nothing");
        assertFalse(ChessUtil.pseudoAttacksKingZone(Board.illegal, e4, e5), "a border square attacks nothing");
    }

    // --- orthogonal distances to the king zone, packed into one int ---------------------
    //
    // getKingZoneDistancesOrthogonalEncoded answers, for each of the four rook directions,
    // how many steps it takes to set foot in the zone, with DISTANCE_SENTINEL for "that
    // direction never gets there". The reference below does not reproduce the arithmetic; it
    // walks the ray one square at a time and counts. Two implementations of the same question
    // that agree over every square pair are hard to be wrong about together.

    private static final int[] RAY_BY_INDEX = {
            Board.LENGTH,      // ChessUtil.INDEX_UP
            1,                 // ChessUtil.INDEX_RIGHT
            -Board.LENGTH,     // ChessUtil.INDEX_DOWN
            -1                 // ChessUtil.INDEX_LEFT
    };

    /** Steps along {@code ray} until the first zone square, or the sentinel if there is none. */
    private static byte stepsIntoZone(int from, int centre, int ray) {
        var zone = zoneOf(centre);

        if (zone.contains(from)) {
            return 0;
        }

        int field = from + ray;
        byte steps = 1;

        while (isOnBoard(field)) {
            if (zone.contains(field)) {
                return steps;
            }

            field += ray;
            steps++;
        }

        return ChessUtil.DISTANCE_SENTINEL;
    }

    private static byte decode(int encoded, int index) {
        return switch (index) {
            case 0 -> BitOps.getByte0(encoded);
            case 1 -> BitOps.getByte1(encoded);
            case 2 -> BitOps.getByte2(encoded);
            default -> BitOps.getByte3(encoded);
        };
    }

    private static final String[] DIRECTION_NAMES = {"up", "right", "down", "left"};

    /**
     * The center is a CORRECTED king field, which by construction never sits on the a or h
     * file - that is what the correction is for. Feeding an edge file would ask the method
     * something it does not promise to answer.
     */
    private static boolean isCorrectedKingField(int field) {
        final int file = field % Board.LENGTH - 2;

        return file >= 1 && file <= 6;
    }

    @Test
    void orthogonalDistancesAgreeWithWalkingTheRay() {
        var failures = new java.util.ArrayList<String>();

        for (int from = Board.a1; from <= Board.h8; from++) {
            if (!isOnBoard(from)) {
                continue;
            }

            for (int centre = Board.a1; centre <= Board.h8; centre++) {
                if (!isOnBoard(centre) || !isCorrectedKingField(centre)) {
                    continue;
                }

                int encoded = ChessUtil.getKingZoneDistancesOrthogonalEncoded(from, centre);

                for (int index = 0; index < RAY_BY_INDEX.length; index++) {
                    byte expected = stepsIntoZone(from, centre, RAY_BY_INDEX[index]);
                    byte actual = decode(encoded, index);

                    if (expected != actual) {
                        failures.add(String.format("%s from %s, zone around %s: walking the ray says %d, the method says %d",
                                DIRECTION_NAMES[index], ChessUtil.fieldToString(from),
                                ChessUtil.fieldToString(centre), expected, actual));
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(),
                failures.size() + " disagreements with the ray walk. First five: "
                        + failures.stream().limit(5).toList());
    }

    @Test
    void aPieceInsideTheZoneIsZeroStepsAwayInEveryDirection() {
        final int d4 = ChessUtil.getFieldFromColAndRow(3, 3);

        for (int offset : ZONE_OFFSETS) {
            int inside = d4 + offset;
            int encoded = ChessUtil.getKingZoneDistancesOrthogonalEncoded(inside, d4);

            assertEquals(0, encoded,
                    ChessUtil.fieldToString(inside) + " lies in the zone around d4, so no direction costs a step");
        }
    }

    @Test
    void aPieceOnNeitherBandCannotGetThereAtAll() {
        final int d4 = ChessUtil.getFieldFromColAndRow(3, 3);
        final int a1 = ChessUtil.getFieldFromColAndRow(0, 0);
        final int g7 = ChessUtil.getFieldFromColAndRow(6, 6);

        assertEquals(ChessUtil.ALL_SENTINELS_ENCODED,
                ChessUtil.getKingZoneDistancesOrthogonalEncoded(a1, d4),
                "a1 shares neither a file nor a rank with the zone around d4");
        assertEquals(ChessUtil.ALL_SENTINELS_ENCODED,
                ChessUtil.getKingZoneDistancesOrthogonalEncoded(g7, d4),
                "g7 is two files and two ranks clear of the zone around d4");
    }

    @Test
    void theEncodingPutsEachDirectionInItsOwnByte() {
        final int d4 = ChessUtil.getFieldFromColAndRow(3, 3);
        final int d8 = ChessUtil.getFieldFromColAndRow(3, 7);

        int encoded = ChessUtil.getKingZoneDistancesOrthogonalEncoded(d8, d4);

        assertEquals(3, BitOps.getByte2(encoded),
                "from d8 the zone starts at d5, three steps down");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte0(encoded), "up leads away from the zone");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte1(encoded), "rank 8 never meets the zone");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte3(encoded), "nor does it going left");
    }

    @Test
    void allSentinelsEncodedSurvivesTheRoundTrip() {
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte0(ChessUtil.ALL_SENTINELS_ENCODED), "up");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte1(ChessUtil.ALL_SENTINELS_ENCODED), "right");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte2(ChessUtil.ALL_SENTINELS_ENCODED), "down");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte3(ChessUtil.ALL_SENTINELS_ENCODED), "left");
        assertNotEquals(0, ChessUtil.ALL_SENTINELS_ENCODED,
                "the sentinel word must differ from the in-zone answer, which is a plain 0");
    }

    // --- diagonal distances to the king zone ---------------------------------------------
    //
    // Same shape as the orthogonal test and for the same reason: the reference walks the ray
    // rather than repeating the arithmetic, so the two can only agree by both being right.

    private static final int[] DIAGONAL_RAY_BY_INDEX = {
            Board.LENGTH + 1,      // ChessUtil.INDEX_UP_RIGHT
            -Board.LENGTH + 1,     // ChessUtil.INDEX_DOWN_RIGHT
            -Board.LENGTH - 1,     // ChessUtil.INDEX_DOWN_LEFT
            Board.LENGTH - 1       // ChessUtil.INDEX_UP_LEFT
    };

    private static final String[] DIAGONAL_NAMES = {"up-right", "down-right", "down-left", "up-left"};

    @Test
    void diagonalDistancesAgreeWithWalkingTheRay() {
        var failures = new java.util.ArrayList<String>();

        for (int from = Board.a1; from <= Board.h8; from++) {
            if (!isOnBoard(from)) {
                continue;
            }

            for (int centre = Board.a1; centre <= Board.h8; centre++) {
                if (!isOnBoard(centre) || !isCorrectedKingField(centre)) {
                    continue;
                }

                int encoded = ChessUtil.getKingZoneDistancesDiagonalEncoded(from, centre);

                for (int index = 0; index < DIAGONAL_RAY_BY_INDEX.length; index++) {
                    byte expected = stepsIntoZone(from, centre, DIAGONAL_RAY_BY_INDEX[index]);
                    byte actual = decode(encoded, index);

                    if (expected != actual) {
                        failures.add(String.format("%s from %s, zone around %s: walking the ray says %d, the method says %d",
                                DIAGONAL_NAMES[index], ChessUtil.fieldToString(from),
                                ChessUtil.fieldToString(centre), expected, actual));
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(),
                failures.size() + " disagreements with the ray walk. First five: "
                        + failures.stream().limit(5).toList());
    }

    @Test
    void aBishopInsideTheZoneIsZeroStepsAwayInEveryDirection() {
        final int d4 = ChessUtil.getFieldFromColAndRow(3, 3);

        for (int offset : ZONE_OFFSETS) {
            int inside = d4 + offset;

            assertEquals(0, ChessUtil.getKingZoneDistancesDiagonalEncoded(inside, d4),
                    ChessUtil.fieldToString(inside) + " lies in the zone around d4, so no diagonal costs a step");
        }
    }

    @Test
    void theDiagonalEncodingPutsEachRayInItsOwnByte() {
        final int d4 = ChessUtil.getFieldFromColAndRow(3, 3);
        final int a1 = ChessUtil.getFieldFromColAndRow(0, 0);

        int encoded = ChessUtil.getKingZoneDistancesDiagonalEncoded(a1, d4);

        assertEquals(2, BitOps.getByte0(encoded), "from a1 the zone starts at c3, two steps up-right");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte1(encoded), "down-right leaves the board at once");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte2(encoded), "so does down-left");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte3(encoded), "and up-left as well");
    }

    /**
     * The case the orthogonal sibling cannot produce: the two axes are coupled, so solving them
     * together can name a step count whose square is off the edge. Walking off the board is not
     * a way into the zone, and the method has to say so rather than report the arithmetic.
     */
    @Test
    void aDiagonalThatSolvesOffTheBoardIsNotAWayIn() {
        final int e1 = ChessUtil.getFieldFromColAndRow(4, 0);
        final int a3 = ChessUtil.getFieldFromColAndRow(0, 2);

        int encoded = ChessUtil.getKingZoneDistancesDiagonalEncoded(a3, e1);

        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte1(encoded),
                "down-right from a3 would meet the zone's file band on rank minus one, off the board");
    }

    /**
     * Two rays at once is possible here and impossible for the rook, which is why the class
     * doc of the diagonal method says so. From b4 the zone around d4 lies both up-right and
     * down-right, because a 3x3 box is three ranks tall and a bishop beside it sees two corners.
     */
    @Test
    void aBishopCanEnterTheZoneOnTwoDiagonalsAtOnce() {
        final int d4 = ChessUtil.getFieldFromColAndRow(3, 3);
        final int b4 = ChessUtil.getFieldFromColAndRow(1, 3);

        int encoded = ChessUtil.getKingZoneDistancesDiagonalEncoded(b4, d4);

        assertEquals(1, BitOps.getByte0(encoded), "c5 is one step up-right from b4 and inside the zone");
        assertEquals(1, BitOps.getByte1(encoded), "c3 is one step down-right from b4 and also inside it");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte2(encoded), "down-left leads away");
        assertEquals(ChessUtil.DISTANCE_SENTINEL, BitOps.getByte3(encoded), "so does up-left");
    }
}
