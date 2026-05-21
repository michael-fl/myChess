package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class MoveDescriptionTest {

    @Test
    void testMove1() {
        var moveDesc = testMoveDescription("e2-e4", GameStatus.TURN_WHITE, Board.whitePawn, Board.e2, Board.e4);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @Test
    void testMove2() {
        var moveDesc = testMoveDescription("Ng1-f3", GameStatus.TURN_WHITE, Board.whiteKnight, Board.g1, Board.f3);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @Test
    void testMove3() {
        var moveDesc = testMoveDescription("d2-d4", GameStatus.TURN_WHITE, Board.whitePawn, Board.d2, Board.d4);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @Test
    void testMove4() {
        var moveDesc = testMoveDescription("Qd1xd4", GameStatus.TURN_WHITE, Board.whiteQueen, Board.d1, Board.d4);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertTrue(moveDesc.isCapture(), "isCapture should be true");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @Test
    void testEnPassant1() {
        var moveDesc = testMoveDescription("c4xb3e.p.", GameStatus.TURN_BLACK, Board.blackPawn, Board.c4, Board.b3);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertTrue(moveDesc.isCapture(), "isCapture should be true");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertTrue(moveDesc.isEnPassant(), "isEnPassant should be true");
    }

    @Test
    void testEnPassant2() {
        var moveDesc = testMoveDescription("e5xf6 e.p.", GameStatus.TURN_WHITE, Board.whitePawn, Board.e5, Board.f6);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertTrue(moveDesc.isCapture(), "isCapture should be true");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertTrue(moveDesc.isEnPassant(), "isEnPassant should be true");
    }

    @Test
    void testMove5() {
        var moveDesc = testMoveDescription("Bc1-g5", GameStatus.TURN_WHITE, Board.whiteBishop, Board.c1, Board.g5);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @Test
    void testChess1() {
        var moveDesc = testMoveDescription("Qd4-e3+", GameStatus.TURN_BLACK, Board.blackQueen, Board.d4, Board.e3);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertTrue(moveDesc.isCheck(), "isCheck should be true");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @Test
    void testChess2() {
        var moveDesc = testMoveDescription("Qd1xh5++", GameStatus.TURN_WHITE, Board.whiteQueen, Board.d1, Board.h5);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertTrue(moveDesc.isCapture(), "isCapture should be true");
        assertTrue(moveDesc.isCheck(), "isCheck should be true");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @Test
    void testMove6() {
        var turn = GameStatus.TURN_WHITE;
        var moveDesc = MoveDescription.fromString("e4", turn);

        assertEquals(-1, moveDesc.fromCol(), "wrong fromCol");
        assertEquals(-1, moveDesc.fromRow(), "wrong fromRow");
        assertEquals(Board.e4, ChessUtil.colAndRowToField(moveDesc.toCol(), moveDesc.toRow()), "wrong target field");
        assertEquals(Board.whitePawn, moveDesc.piece(), "wrong piece");
    }

    @Test
    void testMove7() {
        var turn = GameStatus.TURN_WHITE;
        var moveDesc = MoveDescription.fromString("ee4", turn);

        assertEquals(4, moveDesc.fromCol(), "wrong fromCol");
        assertEquals(-1, moveDesc.fromRow(), "wrong fromRow");
        assertEquals(Board.e4, ChessUtil.colAndRowToField(moveDesc.toCol(), moveDesc.toRow()), "wrong target field");
        assertEquals(Board.whitePawn, moveDesc.piece(), "wrong piece");
    }

    @Test
    void testMove8() {
        var turn = GameStatus.TURN_WHITE;
        var moveDesc = MoveDescription.fromString("2e4", turn);

        assertEquals(-1, moveDesc.fromCol(), "wrong fromCol");
        assertEquals(1, moveDesc.fromRow(), "wrong fromRow");
        assertEquals(Board.e4, ChessUtil.colAndRowToField(moveDesc.toCol(), moveDesc.toRow()), "wrong target field");
        assertEquals(Board.whitePawn, moveDesc.piece(), "wrong piece");
    }

    @Test
    void testMove9() {
        var turn = GameStatus.TURN_WHITE;
        var moveDesc = MoveDescription.fromString("Re1", turn);

        assertEquals(-1, moveDesc.fromCol(), "wrong fromCol");
        assertEquals(-1, moveDesc.fromRow(), "wrong fromRow");
        assertEquals(Board.e1, ChessUtil.colAndRowToField(moveDesc.toCol(), moveDesc.toRow()), "wrong target field");
        assertEquals(Board.whiteRook, moveDesc.piece(), "wrong piece");
    }

    @Test
    void testMove10() {
        var turn = GameStatus.TURN_WHITE;
        var moveDesc = MoveDescription.fromString("Rfe1", turn);

        assertEquals(5, moveDesc.fromCol(), "wrong fromCol");
        assertEquals(-1, moveDesc.fromRow(), "wrong fromRow");
        assertEquals(Board.e1, ChessUtil.colAndRowToField(moveDesc.toCol(), moveDesc.toRow()), "wrong target field");
        assertEquals(Board.whiteRook, moveDesc.piece(), "wrong piece");
    }

    @Test
    void testMove11() {
        var turn = GameStatus.TURN_WHITE;
        var moveDesc = MoveDescription.fromString("R1e1", turn);

        assertEquals(-1, moveDesc.fromCol(), "wrong fromCol");
        assertEquals(0, moveDesc.fromRow(), "wrong fromRow");
        assertEquals(Board.e1, ChessUtil.colAndRowToField(moveDesc.toCol(), moveDesc.toRow()), "wrong target field");
        assertEquals(Board.whiteRook, moveDesc.piece(), "wrong piece");
    }

    @Test
    void testPawnPromotion1() {
        var moveDesc = testMoveDescription("c7c8Q", GameStatus.TURN_WHITE, Board.whitePawn, Board.c7, Board.c8);

        assertEquals(Board.whiteQueen, moveDesc.pawnPromotionPiece(), "wrong pawnPromotionPiece");
    }

    @Test
    void testPawnPromotion2() {
        var moveDesc = testMoveDescription("e2xd1R", GameStatus.TURN_BLACK, Board.blackPawn, Board.e2, Board.d1);

        assertEquals(Board.blackRook, moveDesc.pawnPromotionPiece(), "wrong pawnPromotionPiece");
    }

    @Test
    void testPawnPromotion3() {
        var moveDesc = testMoveDescription("c8N", GameStatus.TURN_WHITE, Board.whitePawn, 0, Board.c8);

        assertEquals(Board.whiteKnight, moveDesc.pawnPromotionPiece(), "wrong pawnPromotionPiece");
    }

    @Test
    void testPawnPromotion4() {
        var moveDesc = testMoveDescription("xc8B", GameStatus.TURN_WHITE, Board.whitePawn, 0, Board.c8);

        assertEquals(Board.whiteBishop, moveDesc.pawnPromotionPiece(), "wrong pawnPromotionPiece");
        assertTrue(moveDesc.isCapture(), "isCapture should be true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"e4!", "e4!!", "Nc5e4+!", "Nc5e4!?", "Nce4?!", "Kh1??", "Qd1d8++??"})
    void testMoveRating(String move) {
        assertDoesNotThrow(() -> MoveDescription.fromString(move, GameStatus.TURN_WHITE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"O-O", "0-0"})
    void testCastling1(String move) {
        var moveDesc = testMoveDescription(move, GameStatus.TURN_WHITE, Board.whiteKing, Board.e1, Board.g1);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"O-O", "0-0"})
    void testCastling2(String move) {
        var moveDesc = testMoveDescription(move, GameStatus.TURN_BLACK, Board.blackKing, Board.e8, Board.g8);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"O-O-O", "0-0-0"})
    void testCastling3(String move) {
        var moveDesc = testMoveDescription(move, GameStatus.TURN_WHITE, Board.whiteKing, Board.e1, Board.c1);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"O-O-O", "0-0-0"})
    void testCastling4(String move) {
        var moveDesc = testMoveDescription(move, GameStatus.TURN_BLACK, Board.blackKing, Board.e8, Board.c8);

        assertEquals(-1, moveDesc.pawnPromotionPiece(), "pawnPromotionPiece should no be set");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "e2--e4", "N", "Xe2-e4", "i1-a1", "h9-h1", "a0-a1", "Bf8Xb4", "Bf8xb4 +", "Bf8xb4-", "Bf8xb4##", "Nc5e4!?!", "Qd1d8++???"})
    void testWrongMove(String move) {
        assertThrows(IllegalArgumentException.class, () -> MoveDescription.fromString(move, GameStatus.TURN_WHITE));
    }

    private MoveDescription testMoveDescription(String move, int turn, byte expectedPiece, int expectedFromField, int expectedToField) {
        var moveDesc = MoveDescription.fromString(move, turn);

        assertEquals(turn, moveDesc.turn(), "wrong turn");
        if (expectedFromField > 0) {
            assertEquals(expectedFromField, ChessUtil.colAndRowToField(moveDesc.fromCol(), moveDesc.fromRow()), "wrong from field");
        }
        assertEquals(expectedToField, ChessUtil.colAndRowToField(moveDesc.toCol(), moveDesc.toRow()), "wrong target field");
        assertEquals(expectedPiece, moveDesc.piece(), "wrong piece");

        return moveDesc;
    }

    @ParameterizedTest
    @ValueSource(strings = {"e8Q", "e8=Q", "e7e8Q", "e7e8=Q", "e7e8Q!", "e7e8=Q!"})
    void testPawnPromotion(String move) {
        var moveDesc = testMoveDescription(move, GameStatus.TURN_WHITE, Board.whitePawn, -1, Board.e8);

        assertEquals(Board.whiteQueen, moveDesc.pawnPromotionPiece(), "wrong pawnPromotionPiece");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertFalse(moveDesc.isCheck(), "isCheck should be unset");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

    @ParameterizedTest
    @ValueSource(strings = {"e8Q+", "e8=Q+", "e7e8Q+", "e7e8=Q+", "e7e8Q+!", "e7e8=Q+!"})
    void testPawnPromotionWithCheck(String move) {
        var moveDesc = testMoveDescription(move, GameStatus.TURN_WHITE, Board.whitePawn, -1, Board.e8);

        assertEquals(Board.whiteQueen, moveDesc.pawnPromotionPiece(), "wrong pawnPromotionPiece");
        assertFalse(moveDesc.isCapture(), "isCapture should be unset");
        assertTrue(moveDesc.isCheck(), "isCheck should be true");
        assertFalse(moveDesc.isCheckmate(), "isCheckmate should be unset");
        assertFalse(moveDesc.isEnPassant(), "isEnPassant should be unset");
    }

}
