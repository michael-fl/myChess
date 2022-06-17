package org.michaelfl.mychess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

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
                        .checkmateCheck(false)
                        .silent(true)
                        .build());
    }

    @Test
    void testWhiteCheckmate() throws Exception {
        SimpleNotationImporter importer = new SimpleNotationImporter("[[f2-f3 e7-e6 g2-g4]]");
        var game = importer.importGame(config);

        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "It must be black's turn");
        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not yet be finished");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals("d8-h4", ChessUtil.moveToString(move.move), "Unexpected move");
        assertEquals(-EngineTest.checkmateIn(1), move.weight, "Should be checkmate in 1");

        game.makeMove(MoveDescription.fromString("d8-h4", game.getTurn()));
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "It must be white's turn");
        assertEquals(GameResult.CHECKMATE, game.getResult(), "Black must be checkmate");

        move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0, move.move, "No move should be possible, since game is finished");
        assertEquals(-EngineTest.checkmateIn(0), move.weight, "Wrong weight");
    }

    @Test
    void testBlackCheckmate() throws Exception {
        SimpleNotationImporter importer = new SimpleNotationImporter("[[e2-e4 e7-e5 d1-h5 b8-c6 f1-c4 g8-f6]]");
        var game = importer.importGame(config);

        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "It must be white's turn");
        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not yet be finished");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals("h5-f7", ChessUtil.moveToString(move.move), "Unexpected move");
        assertEquals(EngineTest.checkmateIn(1), move.weight, "Should be checkmate in 1");

        game.makeMove(MoveDescription.fromString("h5-f7", game.getTurn()));
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "It must be white's turn");
        assertEquals(GameResult.CHECKMATE, game.getResult(), "White must be checkmate");

        move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0, move.move, "No move should be possible, since game is finished");
        assertEquals(EngineTest.checkmateIn(0), move.weight, "Wrong weight");
    }

    @Test
    void testWhiteStalemate() throws Exception {
        SimpleNotationImporter importer = new SimpleNotationImporter("[[a2-a4 c7-c5 d2-d4 d7-d6 d1-d2 e7-e5 d2-f4 e5-e4 h2-h3 f8-e7 f4-h2 e7-h4 a1-a3 c8-e6 a3-g3 e6-b3 b1-d2 d8-a5 d4-d5 e4-e3 c2-c4 f7-f5 f2-f3 f5-f4]]");
        var game = importer.importGame(config);

        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "It must be white's turn");
        assertEquals(GameResult.STALEMATE, game.getResult(), "White should be stalemate");
        assertTrue(game.getResult().isDraw(), "Game should be draw due to stalemate");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0, move.move, "No move should be possible, since game is finished");
        assertEquals(0f, move.weight, "Weight should be 0 (draw)");
    }

    @Test
    void testBlackStalemate() throws Exception {
        SimpleNotationImporter importer = new SimpleNotationImporter("[[e2-e3 a7-a5 d1-h5 a8-a6 h5-a5 h7-h5 a5-c7 a6-h6 h2-h4 f7-f6 c7-d7 e8-f7 d7-b7 d8-d3 b7-b8 d3-h7 b8-c8 f7-g6 c8-e6]]");
        var game = importer.importGame(config);

        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "It must be black's turn");
        assertEquals(GameResult.STALEMATE, game.getResult(), "Black should be stalemate");
        assertTrue(game.getResult().isDraw(), "Game should be draw due to stalemate");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0, move.move, "No move should be possible, since game is finished");
        assertEquals(0f, move.weight, "Weight should be 0 (draw)");
    }
}
