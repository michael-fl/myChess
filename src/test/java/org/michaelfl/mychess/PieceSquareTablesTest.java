package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

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
     * <p>The tables are currently seeded {@code MG == EG} (the tapered null
     * configuration), so the endgame table must equal the — separately
     * value-checked, see the {@code test*Table} methods above — midgame table
     * square for square, for every piece, including all the negative squares.
     * That validates the endgame table's structure and values at once and, in
     * particular, guards the carry-corrected unpacking across the whole board.
     *
     * <p>Once the endgame tables are tuned to their own values this must become
     * an explicit expected-table check like the midgame tests above.
     */
    @Test
    void endGameTableRoundTripsToTheMidGameTableInTheNullConfiguration() {
        byte[] pieces = {
                Board.whitePawn, Board.whiteKnight, Board.whiteBishop, Board.whiteRook, Board.whiteQueen, Board.whiteKing,
                Board.blackPawn, Board.blackKnight, Board.blackBishop, Board.blackRook, Board.blackQueen, Board.blackKing,
        };
        int expectedLength = Board.createEmptyRawBoard().length;

        for (byte piece : pieces) {
            short[] endGameTable = PieceSquareTables.getEndGameTable(piece);
            short[] midGameTable = PieceSquareTables.getMidGameTable(piece);

            assertNotNull(endGameTable, "no endgame table for piece " + piece);
            assertEquals(expectedLength, endGameTable.length, "wrong endgame-table length for piece " + piece);
            assertArrayEquals(midGameTable, endGameTable,
                    "endgame table must round-trip to the midgame table in the MG==EG null config (piece " + piece + ")");
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
}
