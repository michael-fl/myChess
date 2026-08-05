package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class PieceSquareTablesTest {

    @Test
    void testPawnTable() {
        var s = """
                 0,  0,  0,  0,  0,  0,  0,  0,
                50, 50, 50, 50, 50, 50, 50, 50,
                10, 10, 20, 30, 30, 20, 10, 10,
                 5,  5, 10, 25, 25, 10,  5,  5,
                 0,  0,  0, 20, 20,  0,  0,  0,
                 5,  0,-10,  0,  0,-10,  0,  5,
                 5,  0,  0,-20,-20, 10,  0,  5,
                 0,  0,  0,  0,  0,  0,  0,  0
                """;
        testTable(Board.whitePawn, s);

        s = """
                 0,  0,  0,  0,  0,  0,  0,  0,
                 5,  0,  0,-20,-20, 10,  0,  5,
                 5,  0,-10,  0,  0,-10,  0,  5,
                 0,  0,  0, 20, 20,  0,  0,  0,
                 5,  5, 10, 25, 25, 10,  5,  5,
                10, 10, 20, 30, 30, 20, 10, 10,
                50, 50, 50, 50, 50, 50, 50, 50,
                 0,  0,  0,  0,  0,  0,  0,  0
                """;
        testTable(Board.blackPawn, s);
    }

    @Test
    void testKnightTable() {
        var s = """
                -50,-40,-30,-30,-30,-30,-40,-50,
                -40,-20,  0,  0,  0,  0,-20,-40,
                -30,  0, 10, 15, 15, 10,  0,-30,
                -30,  5, 15, 20, 20, 15,  5,-30,
                -30,  0, 15, 20, 20, 15,  0,-30,
                -30,  5, 10, 15, 15, 10,  5,-30,
                -40,-20,  0,  5,  5,  0,-20,-40,
                -50,-40,-30,-30,-30,-30,-40,-50""";
        testTable(Board.whiteKnight, s);

        s = """
                -50,-40,-30,-30,-30,-30,-40,-50,
                -40,-20,  0,  5,  5,  0,-20,-40,
                -30,  5, 10, 15, 15, 10,  5,-30,
                -30,  0, 15, 20, 20, 15,  0,-30,
                -30,  5, 15, 20, 20, 15,  5,-30,
                -30,  0, 10, 15, 15, 10,  0,-30,
                -40,-20,  0,  0,  0,  0,-20,-40,
                -50,-40,-30,-30,-30,-30,-40,-50""";
        testTable(Board.blackKnight, s);
    }

    @Test
    void testBishopTable() {
        var s = """
                -20,-10,-10,-10,-10,-10,-10,-20,
                -10,  0,  0,  0,  0,  0,  0,-10,
                -10,  0,  5, 10, 10,  5,  0,-10,
                -10,  5,  5, 10, 10,  5,  5,-10,
                -10,  0, 10, 10, 10, 10,  0,-10,
                -10, 10, 10, 10, 10, 10, 10,-10,
                -10,  5,  0,  0,  0,  0,  5,-10,
                -20,-10,-10,-10,-10,-10,-10,-20""";
        testTable(Board.whiteBishop, s);

        s = """
                -20,-10,-10,-10,-10,-10,-10,-20,
                -10,  5,  0,  0,  0,  0,  5,-10,
                -10, 10, 10, 10, 10, 10, 10,-10,
                -10,  0, 10, 10, 10, 10,  0,-10,
                -10,  5,  5, 10, 10,  5,  5,-10,
                -10,  0,  5, 10, 10,  5,  0,-10,
                -10,  0,  0,  0,  0,  0,  0,-10,
                -20,-10,-10,-10,-10,-10,-10,-20""";
        testTable(Board.blackBishop, s);
    }

    @Test
    void testRookTable() {
        var s = """
                  0,  0,  0,  0,  0,  0,  0,  0,
                  5, 10, 10, 10, 10, 10, 10,  5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                  0,  0,  0,  5,  5,  0,  0,  0""";
        testTable(Board.whiteRook, s);

        s = """
                  0,  0,  0,  5,  5,  0,  0,  0,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                 -5,  0,  0,  0,  0,  0,  0, -5,
                  5, 10, 10, 10, 10, 10, 10,  5,
                  0,  0,  0,  0,  0,  0,  0,  0""";
        testTable(Board.blackRook, s);
    }

    @Test
    void testQueenTable() {
        var s = """
                -20,-10,-10, -5, -5,-10,-10,-20,
                -10,  0,  0,  0,  0,  0,  0,-10,
                -10,  0,  5,  5,  5,  5,  0,-10,
                 -5,  0,  5,  5,  5,  5,  0, -5,
                  0,  0,  5,  5,  5,  5,  0, -5,
                -10,  5,  5,  5,  5,  5,  0,-10,
                -10,  0,  5,  0,  0,  0,  0,-10,
                -20,-10,-10, -5, -5,-10,-10,-20""";
        testTable(Board.whiteQueen, s);

        s = """
                -20,-10,-10, -5, -5,-10,-10,-20,
                -10,  0,  5,  0,  0,  0,  0,-10,
                -10,  5,  5,  5,  5,  5,  0,-10,
                  0,  0,  5,  5,  5,  5,  0, -5,
                 -5,  0,  5,  5,  5,  5,  0, -5,
                -10,  0,  5,  5,  5,  5,  0,-10,
                -10,  0,  0,  0,  0,  0,  0,-10,
                -20,-10,-10, -5, -5,-10,-10,-20""";
        testTable(Board.blackQueen, s);
    }

    @Test
    void testKingTable() {
        var s = """
                -30,-40,-40,-50,-50,-40,-40,-30,
                -30,-40,-40,-50,-50,-40,-40,-30,
                -30,-40,-40,-50,-50,-40,-40,-30,
                -30,-40,-40,-50,-50,-40,-40,-30,
                -20,-30,-30,-40,-40,-30,-30,-20,
                -10,-20,-20,-20,-20,-20,-20,-10,
                 20, 20,  0,  0,  0,  0, 20, 20,
                 20, 30, 10,  0,  0, 10, 30, 20""";
        testTable(Board.whiteKing, s);

        s = """
                 20, 30, 10,  0,  0, 10, 30, 20,
                 20, 20,  0,  0,  0,  0, 20, 20,
                -10,-20,-20,-20,-20,-20,-20,-10,
                -20,-30,-30,-40,-40,-30,-30,-20,
                -30,-40,-40,-50,-50,-40,-40,-30,
                -30,-40,-40,-50,-50,-40,-40,-30,
                -30,-40,-40,-50,-50,-40,-40,-30,
                -30,-40,-40,-50,-50,-40,-40,-30""";
        testTable(Board.blackKing, s);
    }

    /**
     * Exercises {@link PieceSquareTables#getEndGameTable} (otherwise unused) and
     * pins the structure and values of the endgame table.
     *
     * <p>The tables are stored packed — {@code pack(mg, eg) = (eg << 16) + mg} —
     * and the endgame half is unpacked with the make_score carry correction
     * {@code (short) ((packed + 0x8000) >> 16)}. A broken correction surfaces as
     * an off-by-one on every square whose value is negative (the borrow case).
     *
     * <p>Every piece kind now has a separately tuned endgame table that
     * <em>differs</em> from its midgame table — pawn (v4.3.0), king (v4.3.1), and
     * knight/bishop/rook/queen jointly (v4.3.2). This test pins that divergence
     * plus each endgame table's length; the explicit values (and, through their
     * negative squares, the carry-corrected unpacking) are pinned by the
     * {@code test*EndgameTable} methods below.
     */
    @Test
    void allEndgameTablesDivergeFromTheirMidgameTables() {
        byte[] allPieces = {
                Board.whitePawn, Board.whiteKnight, Board.whiteBishop, Board.whiteRook, Board.whiteQueen, Board.whiteKing,
                Board.blackPawn, Board.blackKnight, Board.blackBishop, Board.blackRook, Board.blackQueen, Board.blackKing,
        };
        int expectedLength = Board.createEmptyRawBoard().length;

        for (byte piece : allPieces) {
            short[] endGameTable = PieceSquareTables.getEndGameTable(piece);

            assertNotNull(endGameTable, "no endgame table for piece " + piece);
            assertEquals(expectedLength, endGameTable.length, "wrong endgame-table length for piece " + piece);
            assertFalse(Arrays.equals(PieceSquareTables.getMidGameTable(piece), endGameTable),
                    "the tuned endgame table must differ from its midgame table (piece " + piece + ")");
        }
    }

    void testTable(byte piece, String tableString) {
        var table = PieceSquareTables.getMidGameTable(piece);

        assertNotNull(table, "No table");
        assertEquals(Board.createEmptyRawBoard().length, table.length, "Wrong board length");

        int field = Board.a8;
        for (String weightStr : tableString.split(",")) {
            int expectedWeight = Integer.parseInt(weightStr.trim());
            int weight = table[field];
            assertEquals(expectedWeight, weight, "Wrong weight at field " + ChessUtil.fieldToString(field));

            if (ChessUtil.getColOfField(field) < 7) {
                field++;
            } else {
                field -= Board.LENGTH + 7;
            }
        }
    }

    /**
     * Pins the tuned white pawn <b>endgame</b> table value for value (via
     * {@link PieceSquareTables#getEndGameTable}), guarding both the offline-tuned
     * values and the carry-corrected endgame unpacking on the negative squares.
     */
    @Test
    void testPawnEndgameTable() {
        var s = """
                  0,   0,   0,   0,   0,   0,   0,   0,
                290, 290, 290, 290, 290, 290, 290, 290,
                199, 207, 182, 159, 159, 182, 207, 199,
                 39,  50,  27,   6,   6,  27,  50,  39,
                -24,  -5, -26, -27, -27, -26,  -5, -24,
                -28,   2,  -9, -16, -16,  -9,   2, -28,
                -12,   9,  11,  -5,  -5,  11,   9, -12,
                  0,   0,   0,   0,   0,   0,   0,   0
                """;
        testEndGameTable(Board.whitePawn, s);
    }

    /**
     * Pins the tuned white king <b>endgame</b> table (centralization) value for
     * value. Candidate on this branch — the values track the king endgame table
     * under cutechess measurement; if that table is re-tuned or reverted, update
     * this expectation with it.
     */
    @Test
    void testKingEndgameTable() {
        var s = """
                -45,  24,  26,  24,  24,  26,  24, -45,
                 38,  79,  75,  55,  55,  75,  79,  38,
                 49,  96,  86,  72,  72,  86,  96,  49,
                 32,  67,  80,  72,  72,  80,  67,  32,
                  2,  50,  70,  79,  79,  70,  50,   2,
                  6,  51,  70,  72,  72,  70,  51,   6,
                 14,  44,  60,  56,  56,  60,  44,  14,
                -52,  57,   6,-135,-135,   6,  57, -52
                """;
        testEndGameTable(Board.whiteKing, s);
    }

    /**
     * Pins the jointly tuned white knight/bishop/rook/queen <b>endgame</b> tables
     * (v4.3.2, anchored to each midgame table's mean to strip the material leak)
     * value for value. Candidate — the values track the joint endgame tune under
     * cutechess measurement; if any is re-tuned or reverted, update it here.
     */
    @Test
    void testKnightEndgameTable() {
        var s = """
                -106, -90, -81, -34, -34, -81, -90,-106,
                 -88, -40, -11, -14, -14, -11, -40, -88,
                 -26, -16,  21,  29,  29,  21, -16, -26,
                  20,  19,  26,  45,  45,  26,  19,  20,
                   6,   5,  29,  38,  38,  29,   5,   6,
                 -34, -11,  -1,  32,  32,  -1, -11, -34,
                  20, -20, -20,   9,   9, -20, -20,  20,
                 -94, -22,  -4,  -6,  -6,  -4, -22, -94
                """;
        testEndGameTable(Board.whiteKnight, s);
    }

    @Test
    void testBishopEndgameTable() {
        var s = """
                 -38, -53, -45, -29, -29, -45, -53, -38,
                 -65, -13, -23, -19, -19, -23, -13, -65,
                  15,   8,  14,   9,   9,  14,   8,  15,
                  -6,   7, -10,  17,  17, -10,   7,  -6,
                 -25,  -3,  25,  15,  15,  25,  -3, -25,
                   3,  13,  29,  39,  39,  29,  13,   3,
                 -15,  74,   1,  45,  45,   1,  74, -15,
                 -33, -33,  11,   3,   3,  11, -33, -33
                """;
        testEndGameTable(Board.whiteBishop, s);
    }

    @Test
    void testRookEndgameTable() {
        var s = """
                  38,  25,  31,  29,  29,  31,  25,  38,
                  10,  19,  27,  31,  31,  27,  19,  10,
                  12,  13,   7,   9,   9,   7,  13,  12,
                   4,   1,   9,   5,   5,   9,   1,   4,
                 -20, -13,  -7,  -3,  -3,  -7, -13, -20,
                 -32, -27, -21, -10, -10, -21, -27, -32,
                 -36, -15,  -7, -11, -11,  -7, -15, -36,
                 -99, -15,  37,  18,  18,  37, -15, -99
                """;
        testEndGameTable(Board.whiteRook, s);
    }

    @Test
    void testQueenEndgameTable() {
        var s = """
                   3,   5,  17,  22,  22,  17,   5,   3,
                 -73, -73,  27,  27,  27,  27, -73, -73,
                   1,  10,  32,  32,  32,  32,  10,   1,
                 -22,  -1,  32,  32,  32,  32,  -1, -22,
                 -56,  27,  -2,  32,  32,  -2,  27, -56,
                 -71, -19,  32,  16,  16,  32, -19, -71,
                 -39, -21,  13,  27,  27,  13, -21, -39,
                   7, -79, -39,  22,  22, -39, -79,   7
                """;
        testEndGameTable(Board.whiteQueen, s);
    }

    void testEndGameTable(byte piece, String tableString) {
        var table = PieceSquareTables.getEndGameTable(piece);

        assertNotNull(table, "No endgame table");
        assertEquals(Board.createEmptyRawBoard().length, table.length, "Wrong board length");

        int field = Board.a8;
        for (String weightStr : tableString.split(",")) {
            int expectedWeight = Integer.parseInt(weightStr.trim());
            assertEquals(expectedWeight, table[field], "Wrong endgame weight at field " + ChessUtil.fieldToString(field));

            if (ChessUtil.getColOfField(field) < 7) {
                field++;
            } else {
                field -= Board.LENGTH + 7;
            }
        }
    }
}
