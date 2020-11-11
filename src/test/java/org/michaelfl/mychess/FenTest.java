package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Michael Fleischhauer
 */
class FenTest {

    @Test
    void testStartPosition() {
        var game = new Game();

        String expectedFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        assertEquals(expectedFEN, game.exportFEN(), "wrong FEN");
    }

    @Test
    void testPawnDoubleMove() {
        var game = new Game();

        game.makeMove(MoveDescription.fromString("e2-e4", game.getTurn()));

        String expectedFEN = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertEquals(expectedFEN, game.exportFEN(), "wrong FEN");
    }

    @Test
    void testPosition1() {
        SimpleNotationImporter importer = new SimpleNotationImporter("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5 a7-a6 b5-a4 b7-b5 a4-b3 g8-f6 e1-g1 a6-a5 d2-d4 a5-a4 b3-f7 e8-f7 d4-e5 f6-g8 f3-g5 f7-e8 b1-c3 b5-b4 d1-d5 g8-h6 c3-b5 a4-a3 f1-d1 c8-b7]]");
        var game = importer.importGame();

        String expectedFEN = "r2qkb1r/1bpp2pp/2n4n/1N1QP1N1/1p2P3/p7/PPP2PPP/R1BR2K1 w - - 2 15";
        assertEquals(expectedFEN, game.exportFEN(), "wrong FEN");
    }
}
