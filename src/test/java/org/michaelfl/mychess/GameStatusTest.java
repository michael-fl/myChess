package org.michaelfl.mychess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.michaelfl.mychess.WeightingFunction.checkmateIn;

/**
 * @author Michael Fleischhauer
 */
class GameStatusTest {

    private GameConfig config;

    @BeforeEach
    void setup() {
        config = new GameConfig(MyChessEngine.class,
                new EngineConfig.Builder()
                        .maxDepth(2)
                        .silent(true)
                        .build());
    }

    @Test
    void testWhiteCheckmate() throws Exception {
        GameImporter importer = GameImporter.importerFor("""
                1. f3 e6 2. g4
                """);
        var game = importer.importGame(config);

        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "It must be black's turn");
        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not yet be finished");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals("d8-h4", ChessUtil.moveToString(move.move()), "Unexpected move");
        assertEquals(-checkmateIn(1), move.weight(), "Should be checkmate in 1");

        game.makeMove(MoveDescription.fromString("d8-h4", game.getTurn()));
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "It must be white's turn");
        assertEquals(GameResult.CHECKMATE, game.getResult(), "Black must be checkmate");

        move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0, move.move(), "No move should be possible, since game is finished");
        assertEquals(-checkmateIn(0), move.weight(), "Wrong weight");
    }

    @Test
    void testBlackCheckmate() throws Exception {
        GameImporter importer = GameImporter.importerFor("""
                1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6
                """);
        var game = importer.importGame(config);

        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "It must be white's turn");
        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not yet be finished");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals("h5-f7", ChessUtil.moveToString(move.move()), "Unexpected move");
        assertEquals(checkmateIn(1), move.weight(), "Should be checkmate in 1");

        game.makeMove(MoveDescription.fromString("h5-f7", game.getTurn()));
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "It must be white's turn");
        assertEquals(GameResult.CHECKMATE, game.getResult(), "White must be checkmate");

        move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0, move.move(), "No move should be possible, since game is finished");
        assertEquals(checkmateIn(0), move.weight(), "Wrong weight");
    }

    @Test
    void testWhiteStalemate() throws Exception {
        GameImporter importer = GameImporter.importerFor("""
                1. a4 c5 2. d4 d6 3. Qd2 e5 4. Qf4 e4 5. h3 Be7 6. Qh2 Bh4 7. Ra3 Be6 8. Rg3 Bb3 9. Nd2
                Qa5 10. d5 e3 11. c4 f5 12. f3 f4
                """);
        var game = importer.importGame(config);

        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "It must be white's turn");
        assertEquals(GameResult.STALEMATE, game.getResult(), "White should be stalemate");
        assertTrue(game.getResult().isDraw(), "Game should be draw due to stalemate");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0, move.move(), "No move should be possible, since game is finished");
        assertEquals(0f, move.weight(), "Weight should be 0 (draw)");
    }

    @Test
    void testBlackStalemate() throws Exception {
        GameImporter importer = GameImporter.importerFor("""
                1. e3 a5 2. Qh5 Ra6 3. Qxa5 h5 4. Qxc7 Rah6 5. h4 f6 6. Qxd7+ Kf7 7. Qxb7 Qd3 8. Qxb8 Qh7
                9. Qxc8 Kg6 10. Qe6
                """);
        var game = importer.importGame(config);

        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "It must be black's turn");
        assertEquals(GameResult.STALEMATE, game.getResult(), "Black should be stalemate");
        assertTrue(game.getResult().isDraw(), "Game should be draw due to stalemate");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0, move.move(), "No move should be possible, since game is finished");
        assertEquals(0f, move.weight(), "Weight should be 0 (draw)");
    }
}
