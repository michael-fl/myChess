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
}
