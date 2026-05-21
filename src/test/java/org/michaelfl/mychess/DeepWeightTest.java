package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.fail;
import static org.michaelfl.mychess.EngineTest.*;
import static org.michaelfl.mychess.WeightingFunction.checkmateIn;

/**
 * @author Michael Fleischhauer
 */
class DeepWeightTest {

    @Test
    void testPosition01() {
        var pgn = """
                1. e4
                """;
        testPosition(pgn,
                0.3f,
                0.5f,
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    @Test
    void testPosition02() {
        var pgn = """
                1. e4 c5
                """;
        testPosition(pgn,
                0.3f,
                0.5f,
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    @Test
    void testPosition03() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4
                """;
        testPosition(pgn,
                0.2f,
                0.4f,
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    @Test
    void testPosition04() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4
                """;
        testPosition(pgn,
                0.60f,
                0.70f,
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    @Test
    void testPosition05() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6
                """;
        testPosition(pgn,
                0.0f,
                0.1f,
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    @Test
    void testPosition06() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6
                """;
        testPosition(pgn,
                0.20f,
                0.30f,
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    @Test
    void testPosition08() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5
                """;
        testPosition(pgn,
                0.60f,
                0.70f,
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    @Test
    void testPosition09() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O
                """;
        testPosition(pgn,
                0.05f,
                0.2f,
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    @Test
    void testPosition10() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O 18. Rg1
                """;
        testPosition(pgn,
                0.14f,
                0.34f,
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    @Test
    void testPosition11() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O 18. Rg1 Bf6 19. Bh3 Re8 20. exf7+ Nxf7 21. Bf5 h6 22. Nd5 a5 23.
                Qg6 a4 24. Nxf6+ Kf8
                """;
        testPosition(pgn,
                checkmateIn(3),
                checkmateIn(3),
                new GameConfig(MyChessEngine.class, engineConfig())
        );
    }

    private void testPosition(String gameNotation, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        try {
            GameImporter importer = GameImporter.importerFor(gameNotation);
            var game = importer.importGame(config);
            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
            var weight = move.weight();

            if (weight < expectedMinWeight) {
                game.print();
                fail("Wrong weight: " + ChessUtil.weightToString(weight) + ". Expected minimum of " + ChessUtil.weightToString(expectedMinWeight));
            }
            if (weight > expectedMaxWeight) {
                game.print();
                fail("Wrong weight: " + ChessUtil.weightToString(weight) + ". Expected maximum of " + ChessUtil.weightToString(expectedMaxWeight));
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

}
