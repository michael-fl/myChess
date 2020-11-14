package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
