package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Michael Fleischhauer
 */
class PGNImporterTest {

    @Test
    void testPGNImport1() {
        testPGN(PgnTest.PGN_1, GameResult.ONGOING, GameStatus.TURN_BLACK, 51, Board.whiteQueen, Board.c4, Board.blackPawn, Board.a5);
    }

    @Test
    void testPGNImport2() {
        testPGN(PgnTest.PGN_2, GameResult.ONGOING, GameStatus.TURN_WHITE, 62, Board.blackKing, Board.f5, Board.whiteKing, Board.g7);
    }

    @Test
    void testPGNImport3() {
        testPGN(PgnTest.PGN_3, GameResult.ONGOING, GameStatus.TURN_BLACK, 46, Board.whiteKing, Board.e4, Board.blackPawn, Board.c4);
    }

    @Test
    void testPGNImport4() {
        testPGN(PgnTest.PGN_4, GameResult.CHECKMATE, GameStatus.TURN_BLACK, 44, Board.whiteKnight, Board.g6, Board.blackKnight, Board.h7);
    }

    @Test
    void testPGNImport5() {
        testPGN(PgnTest.PGN_5, GameResult.CHECKMATE, GameStatus.TURN_WHITE, 76, Board.blackQueen, Board.h1, Board.whiteKing, Board.h3);
    }

    @Test
    void testPGNImport6() {
        testPGN(PgnTest.PGN_6, GameResult.CHECKMATE, GameStatus.TURN_BLACK, 96, Board.whiteKnight, Board.g6, Board.blackKing, Board.h8);
    }

    private void testPGN(String pgn, GameResult result, int turn, int moveCount, byte piece1, int field1, byte piece2, int field2) {
        var importer = new PGNImporter(pgn);
        var game = importer.importGame();
        game.print();

        assertEquals(result, game.getResult(), "unexpected game result");
        assertEquals(turn, game.getTurn(), "unexpected turn");
        assertEquals(moveCount, game.getMoveCount(), "unexpected move count");
        assertEquals(piece1, game.getBoard().get(field1), "unexpected piece");
        assertEquals(piece2, game.getBoard().get(field2), "unexpected piece");
    }
}
