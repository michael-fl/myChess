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
     * <p>Every piece whose endgame table is <em>not</em> separately tuned is still
     * seeded {@code MG == EG}, so its endgame table must equal the — separately
     * value-checked, see the {@code test*Table} methods above — midgame table
     * square for square, including all the negative squares. That validates those
     * endgame tables' structure and values at once and, in particular, guards the
     * carry-corrected unpacking across the whole board.
     *
     * <p>The <b>pawn</b> and <b>king</b> endgame tables have been tuned to diverge,
     * so they are checked to <em>differ</em> from their midgame tables instead;
     * their explicit values are pinned by {@code testPawnEndgameTable} /
     * {@code testKingEndgameTable}.
     */
    @Test
    void endGameTablesMatchMidGameTablesExceptForTheTunedPawnAndKingTables() {
        byte[] sharedTablePieces = {
                Board.whiteKnight, Board.whiteBishop, Board.whiteRook, Board.whiteQueen,
                Board.blackKnight, Board.blackBishop, Board.blackRook, Board.blackQueen,
        };
        int expectedLength = Board.createEmptyRawBoard().length;

        for (byte piece : sharedTablePieces) {
            short[] endGameTable = PieceSquareTables.getEndGameTable(piece);
            short[] midGameTable = PieceSquareTables.getMidGameTable(piece);

            assertNotNull(endGameTable, "no endgame table for piece " + piece);
            assertEquals(expectedLength, endGameTable.length, "wrong endgame-table length for piece " + piece);
            assertArrayEquals(midGameTable, endGameTable,
                    "non-tuned endgame table must still match the midgame table (piece " + piece + ")");
        }

        // The pawn and king endgame tables were tuned to diverge from their midgame tables.
        for (byte piece : new byte[] {Board.whitePawn, Board.blackPawn, Board.whiteKing, Board.blackKing}) {
            assertFalse(Arrays.equals(PieceSquareTables.getMidGameTable(piece), PieceSquareTables.getEndGameTable(piece)),
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
