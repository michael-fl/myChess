package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.michaelfl.mychess.EngineTest.engineConfig;

/**
 * @author Michael Fleischhauer
 */
class ThreefoldRepetitionTest {

    @Test
    void testIsDraw() {
        String moves = "[[g2-g3 e7-e6 a2-a3 d8-h4 g3-h4 a7-a6 g1-f3 g8-f6 f3-g1 f6-g8 g1-f3 g8-f6 f3-g1]]";
        SimpleNotationImporter importer = new SimpleNotationImporter(moves);
        var game = importer.importGame();

        assertEquals(GameResult.ONGOING, game.getResult(), "game must not be finished");
        game.makeMove(MoveDescription.fromString("f6-g8", game.getTurn()));
        assertEquals(GameResult.DRAW, game.getResult(), "game must be draw due to threefold repetition rule");
    }

    @Test
    void testFindDrawMove() throws Exception {
        String moves = "[[g2-g3 e7-e6 a2-a3 d8-h4 g3-h4 a7-a6 g1-f3 g8-f6 f3-g1 f6-g8 g1-f3 g8-f6 f3-g1]]";
        SimpleNotationImporter importer = new SimpleNotationImporter(moves);
        var game = importer.importGame(new GameConfig(MyChessEngine.class, engineConfig(false)));

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals("f6-g8", ChessUtil.moveToString(move.move), "Unexpected move");
        assertEquals(0f, move.weight, "Weight must be 0 (draw)");
        assertEquals(GameResult.DRAW, move.result, "game must be draw due to threefold repetition rule");
    }

    @Test
    void testIsDraw2() {
        var importer = new SimpleNotationImporter("[[e2-e4 c7-c5 f1-e2 b8-c6 f2-f4 e7-e6 g1-f3 b7-b6 e1-g1 c8-b7 d2-d3 d8-c7 c2-c3 g8-f6 a2-a4 d7-d5 e4-e5 f6-d7 b1-a3 a7-a6 d1-e1 c6-e7 a3-c2 e7-f5 g2-g4 f5-e7 e1-g3 h7-h5 h2-h3 d5-d4 c3-c4 e7-c6 c1-d2 g7-g6 f3-g5 f8-e7 g5-e4 e8-c8 a4-a5 c6-a5 a1-a5 b6-a5 e2-f3 d7-b8 g3-g2 b8-c6 g4-g5 c6-b4 d2-b4 a5-b4 c2-a1 a6-a5 f3-d1 b7-c6 f1-e1 a5-a4 b2-b3 a4-a3 g2-a2 c8-b7 d1-f3 b7-b6 a1-c2 h5-h4 e1-e2 d8-f8 c2-e1 c6-b7 e2-g2 b7-c6 g2-e2 f8-a8 e2-g2 h8-g8 g2-g4 a8-f8 g4-g2 g8-h8 g2-g4 c6-b7 g4-g2]]");
        var game = importer.importGame();
        assertEquals(GameResult.ONGOING, game.getResult(), "game must not be finished");
        game.makeMove(MoveDescription.fromString("Bc6", game.getTurn()));
        assertEquals(GameResult.DRAW, game.getResult(), "game must be draw due to threefold repetition rule");
    }

    @Test
    void testDisableThreefoldRepetition() {
        var importer = new SimpleNotationImporter("[[e2-e4 c7-c5 f1-e2 b8-c6 f2-f4 e7-e6 g1-f3 b7-b6 e1-g1 c8-b7 d2-d3 d8-c7 c2-c3 g8-f6 a2-a4 d7-d5 e4-e5 f6-d7 b1-a3 a7-a6 d1-e1 c6-e7 a3-c2 e7-f5 g2-g4 f5-e7 e1-g3 h7-h5 h2-h3 d5-d4 c3-c4 e7-c6 c1-d2 g7-g6 f3-g5 f8-e7 g5-e4 e8-c8 a4-a5 c6-a5 a1-a5 b6-a5 e2-f3 d7-b8 g3-g2 b8-c6 g4-g5 c6-b4 d2-b4 a5-b4 c2-a1 a6-a5 f3-d1 b7-c6 f1-e1 a5-a4 b2-b3 a4-a3 g2-a2 c8-b7 d1-f3 b7-b6 a1-c2 h5-h4 e1-e2 d8-f8 c2-e1 c6-b7 e2-g2 b7-c6 g2-e2 f8-a8 e2-g2 h8-g8 g2-g4 a8-f8 g4-g2 g8-h8 g2-g4 c6-b7 g4-g2]]");
        var config = new GameConfig(
                MyChessEngine.class,
                new EngineConfig.Builder().enableThreefoldRepetition(false).build());
        var game = importer.importGame(config);
        game.makeMove(MoveDescription.fromString("Bc6", game.getTurn()));
        assertEquals(GameResult.ONGOING, game.getResult(), "Game must not be finished yet");
    }

}
