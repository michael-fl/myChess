package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class BoardTest {
    @Test
    void testIsKingChecked() {
        var pgn = """
                1. c4 e5 2. Nc3 Nf6 3. a3 Nc6 4. e4 Nd4 5. Nf3 d6 6. h3 Be7 7. Be2 O-O 8. O-O
                h6 9. d3 a6 10. Be3 c5 11. Nd5 Be6 12. Nxd4 cxd4 13. Bd2 Nxd5 14. cxd5 Bd7 15. Rc1
                Rc8 16. Bg4 f5 17. Bxf5 Bxf5 18. exf5 Rxf5 19. Qg4 Rxc1 20. Rxc1 Qf8 21. Bxh6
                Kh7 22. Bxg7 Qf7 23. Rc8 Qg6
                """;
        GameImporter importer = GameImporter.importerFor(pgn);
        var game = importer.importGame();

        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var board = game.getBoard();
        assertFalse(board.isKingChecked(moveGenerator), "King should not yet be checked");

        game.makeMove(MoveDescription.fromString("Rh8+", board.getGameStatus().getTurn()));
        assertTrue(board.isKingChecked(moveGenerator), "King should now be checked");

        game.makeMove(MoveDescription.fromString("Kxg7", board.getGameStatus().getTurn()));
        assertFalse(board.isKingChecked(moveGenerator), "King should no longer be checked");
    }

    @Test
    void testIsCheckmate1() {
        // Scholar's mate
        var pgn = """
                1. e4 e5
                2. Qh5 Nc6
                3. Bc4 Nf6??
                """;
        GameImporter importer = GameImporter.importerFor(pgn);
        var game = importer.importGame();

        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var board = game.getBoard();
        assertFalse(board.isCheckmate(moveGenerator), "Should not yet be checkmate");

        game.makeMove(MoveDescription.fromString("Qxf7", board.getGameStatus().getTurn()));
        assertTrue(board.isCheckmate(moveGenerator), "Should be checkmate now");
    }

    @Test
    void testIsCheckmate2() {
        // Fool's mate
        var pgn = """
                1. f3 e6
                2. g4
                """;
        GameImporter importer = GameImporter.importerFor(pgn);
        var game = importer.importGame();

        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var board = game.getBoard();
        assertFalse(board.isCheckmate(moveGenerator), "Should not yet be checkmate");

        game.makeMove(MoveDescription.fromString("Qh4", board.getGameStatus().getTurn()));
        assertTrue(board.isCheckmate(moveGenerator), "Should be checkmate now");
    }

    @Test
    void testIsCheckmate3() {
        // Smothered mate
        var pgn = """
                1. e4 c6 2. d4 d5
                3. Nc3 dxe4
                4. Nxe4 Nd7
                5. Qe2 Ngf6
                """;
        GameImporter importer = GameImporter.importerFor(pgn);
        var game = importer.importGame();

        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var board = game.getBoard();
        assertFalse(board.isCheckmate(moveGenerator), "Should not yet be checkmate");

        game.makeMove(MoveDescription.fromString("Nd6#", board.getGameStatus().getTurn()));
        assertTrue(board.isCheckmate(moveGenerator), "Should be checkmate now");
    }

}
