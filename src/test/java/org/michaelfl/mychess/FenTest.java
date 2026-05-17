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
        GameImporter importer = GameImporter.importerFor("""
                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 b5 5. Bb3 Nf6 6. O-O a5 7. d4 a4 8. Bxf7+ Kxf7 9.
                dxe5 Ng8 10. Ng5+ Ke8 11. Nc3 b4 12. Qd5 Nh6 13. Nb5 a3 14. Rd1 Bb7
                """);
        var game = importer.importGame();

        String expectedFEN = "r2qkb1r/1bpp2pp/2n4n/1N1QP1N1/1p2P3/p7/PPP2PPP/R1BR2K1 w - - 2 15";
        assertEquals(expectedFEN, game.exportFEN(), "wrong FEN");
    }
}
