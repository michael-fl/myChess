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

        assertEquals(4, ChessUtil.findColOfPieceOnRow(board, Board.whiteKing, 0),
                "white king starts on the e-file (column 4)");
    }

    @Test
    void findColOfPieceOnRow_blackKingOnBackRank_returnsColumn4() {
        var board = Board.createNewGame();

        assertEquals(4, ChessUtil.findColOfPieceOnRow(board, Board.blackKing, 7),
                "black king starts on the e-file (column 4)");
    }

    @Test
    void findColOfPieceOnRow_whiteQueenOnBackRank_returnsColumn3() {
        var board = Board.createNewGame();

        assertEquals(3, ChessUtil.findColOfPieceOnRow(board, Board.whiteQueen, 0),
                "white queen starts on the d-file (column 3)");
    }

    @Test
    void findColOfPieceOnRow_blackQueenOnBackRank_returnsColumn3() {
        var board = Board.createNewGame();

        assertEquals(3, ChessUtil.findColOfPieceOnRow(board, Board.blackQueen, 7),
                "black queen starts on the d-file (column 3)");
    }

    @Test
    void findColOfPieceOnRow_aFileRook_returnsColumn0() {
        // Column 0 is the a-file; the queenside rook starts there. The
        // method's "not found" sentinel is also 0 (Board.empty), so a
        // legitimate hit at column 0 is the case where the sentinel choice
        // is most ambiguous.
        var board = Board.createNewGame();

        assertEquals(0, ChessUtil.findColOfPieceOnRow(board, Board.whiteRook, 0),
                "white queenside rook starts on the a-file (column 0)");
    }

    @Test
    void findColOfPieceOnRow_multipleMatches_returnsFirstFromLeft() {
        // Standard chess has white rooks on both a1 (col 0) and h1 (col 7).
        // The method must return the leftmost match — column 0 — as the
        // "first hit wins" semantics implied by a left-to-right scan.
        var board = Board.createNewGame();

        assertEquals(0, ChessUtil.findColOfPieceOnRow(board, Board.whiteRook, 0),
                "first matching column wins (a-file before h-file)");
    }

    @Test
    void findColOfPieceOnRow_pieceNotOnRow_returnsMinusOne() {
        // White king is on row 0 (rank 1), never on row 3 in the start
        // position. The not-found sentinel is -1 (not Board.empty, which
        // would collide with the valid column index 0 = a-file).
        var board = Board.createNewGame();

        assertEquals(-1, ChessUtil.findColOfPieceOnRow(board, Board.whiteKing, 3),
                "no white king on the middle of the board ⇒ -1");
    }

    @Test
    void findColOfPieceOnRow_chess960Position_findsKingAtNonStandardFile() {
        // RKBBNRNQ — white king on b1 (column 1), not e1.
        var board = Fen.importFEN("rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1");

        assertEquals(1, ChessUtil.findColOfPieceOnRow(board, Board.whiteKing, 0),
                "cutechess sample 960 position: white king on column 1");
    }

    @Test
    void findColOfPieceOnRow_chess960Position_findsBlackKingMirrored() {
        // Same 960 position; black king mirrors on b8 (column 1, row 7).
        var board = Fen.importFEN("rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1");

        assertEquals(1, ChessUtil.findColOfPieceOnRow(board, Board.blackKing, 7),
                "cutechess sample 960 position: black king on column 1");
    }

    @Test
    void findColOfPieceOnRow_pieceOnDifferentRow_returnsMinusOne() {
        // Black king is on row 7, not row 0. Searching row 0 for the black
        // king must report not-found (-1), not silently confuse with the
        // white king that does sit on row 0.
        var board = Board.createNewGame();

        assertEquals(-1, ChessUtil.findColOfPieceOnRow(board, Board.blackKing, 0),
                "black king is not on row 0 in the start position");
    }
}
