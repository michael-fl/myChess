package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.fail;
import static org.michaelfl.mychess.EngineTest.engineConfig;

/**
 * @author Michael Fleischhauer
 */
class DeepWeightTest {

    @Test
    void testPosition01() {
        testPosition("[[e2-e4]]",
                0.3f,
                0.5f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @Test
    void testPosition02() {
        testPosition("[[e2-e4 c7-c5]]",
                0.3f,
                0.5f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @Test
    void testPosition03() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4]]",
                0.2f,
                0.4f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @Test
    void testPosition04() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4]]",
                0.60f,
                0.70f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @Test
    void testPosition05() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6]]",
                0.0f,
                0.1f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @Test
    void testPosition06() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6]]",
                0.05f,
                0.25f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @Test
    void testPosition07() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6]]",
                0.05f,
                0.25f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @Test
    void testPosition08() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5]]",
                0.60f,
                0.70f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @Test
    void testPosition09() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8]]",
                0.05f,
                0.2f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    @Test
    void testPosition10() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1]]",
                0.14f,
                0.34f,
                new GameConfig(MyChessEngine.class, engineConfig(false))
        );
    }

    private void testPosition(String gameNotation, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        try {
            SimpleNotationImporter importer = new SimpleNotationImporter(gameNotation);
            var game = importer.importGame(config);
            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
            var weight = move.weight;

            if (weight < expectedMinWeight) {
                game.print();
                fail("Wrong weight: " + ChessUtil.weightToString(weight) + ". Expected minimum of " + ChessUtil.weightToString(expectedMinWeight));
            }
            if (weight > expectedMaxWeight) {
                game.print();
                fail("Wrong weight: " + ChessUtil.weightToString(weight) + ". Expected maximum of " + ChessUtil.weightToString(expectedMinWeight));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

}
