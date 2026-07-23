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
        assertPawnTable(Board.whitePawn, Board.e1, s); // king on e1 -> CENTER bucket

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
        assertPawnTable(Board.blackPawn, Board.e8, s); // king on e8 -> CENTER bucket
    }

    // Spot-check that the pawn table is SELECTED by the own king's zone.
    @Test
    void pawnTableIsSelectedByKingPosition() {
        // c2: only the queenside-king table gives the c2 pawn a bonus.
        assertEquals(0,  PieceSquareTables.getPieceSquareWeight(Board.whitePawn, Board.c2, Board.e1), "c2, king e1 (center)");
        assertEquals(10, PieceSquareTables.getPieceSquareWeight(Board.whitePawn, Board.c2, Board.c1), "c2, king c1 (queenside)");
        // a3: the kingside-king table drops the queenside a3 bonus.
        assertEquals(5, PieceSquareTables.getPieceSquareWeight(Board.whitePawn, Board.a3, Board.e1), "a3, king e1 (center)");
        assertEquals(0, PieceSquareTables.getPieceSquareWeight(Board.whitePawn, Board.a3, Board.g1), "a3, king g1 (kingside)");
        // a4: only the endgame table (king off its back ranks) rewards the a4 advance.
        assertEquals(0, PieceSquareTables.getPieceSquareWeight(Board.whitePawn, Board.a4, Board.e1), "a4, king e1 (center)");
        assertEquals(5, PieceSquareTables.getPieceSquareWeight(Board.whitePawn, Board.a4, Board.e4), "a4, king e4 (endgame)");
    }

    void assertPawnTable(byte pawn, int kingField, String tableString) {
        int field = Board.a8;
        for (String weightStr : tableString.split(",")) {
            int expectedWeight = Integer.parseInt(weightStr.trim());
            int weight = PieceSquareTables.getPieceSquareWeight(pawn, field, kingField);
            assertEquals(expectedWeight, weight, "Wrong weight at field " + ChessUtil.fieldToString(field));

            if (ChessUtil.getColOfField(field) < 7) {
                field++;
            } else {
                field -= Board.LENGTH + 7;
            }
        }
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

    void testTable(byte piece, String tableString) {
        var table = PieceSquareTables.getPieceSquareTable(piece);

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
