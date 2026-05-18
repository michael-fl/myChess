package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.michaelfl.mychess.EngineTest.engineConfig;

/**
 * @author Michael Fleischhauer
 */
class ThreefoldRepetitionTest {

    @Test
    void testIsDraw() {
        String moves = """
                1. g3 e6 2. a3 Qh4 3. gxh4 a6 4. Nf3 Nf6 5. Ng1 Ng8 6. Nf3 Nf6 7. Ng1
                """;
        GameImporter importer = GameImporter.importerFor(moves);
        var game = importer.importGame(Game.standardConfig());

        assertEquals(GameResult.ONGOING, game.getResult(), "game must not be finished");
        game.makeMove(MoveDescription.fromString("f6-g8", game.getTurn()));
        assertEquals(GameResult.DRAW, game.getResult(), "game must be draw due to threefold repetition rule");
    }

    @Test
    void testFindDrawMove() throws Exception {
        String moves = """
                1. g3 e6 2. a3 Qh4 3. gxh4 a6 4. Nf3 Nf6 5. Ng1 Ng8 6. Nf3 Nf6 7. Ng1
                """;
        GameImporter importer = GameImporter.importerFor(moves);
        var game = importer.importGame(new GameConfig(MyChessEngine.class, engineConfig()));

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals("f6-g8", ChessUtil.moveToString(move.move), "Unexpected move");
        assertEquals(0f, move.weight, "Weight must be 0 (draw)");
        assertEquals(GameResult.DRAW, move.result, "game must be draw due to threefold repetition rule");
    }

    @Test
    void testIsDraw2() {
        var importer = GameImporter.importerFor("""
                1. e4 c5 2. Be2 Nc6 3. f4 e6 4. Nf3 b6 5. O-O Bb7 6. d3 Qc7 7. c3 Nf6 8. a4 d5 9. e5 Nd7
                10. Na3 a6 11. Qe1 Ne7 12. Nc2 Nf5 13. g4 Ne7 14. Qg3 h5 15. h3 d4 16. c4 Nc6 17. Bd2 g6
                18. Ng5 Be7 19. Ne4 O-O-O 20. a5 Nxa5 21. Rxa5 bxa5 22. Bf3 Nb8 23. Qg2 Nc6 24. g5 Nb4 25.
                Bxb4 axb4 26. Na1 a5 27. Bd1 Bc6 28. Re1 a4 29. b3 a3 30. Qa2 Kb7 31. Bf3 Kb6 32. Nc2 h4
                33. Re2 Rdf8 34. Ne1 Bb7 35. Rg2 Bc6 36. Re2 Ra8 37. Rg2 Rhg8 38. Rg4 Raf8 39. Rg2 Rh8 40.
                Rg4 Bb7 41. Rg2
                """);
        var game = importer.importGame(Game.standardConfig());
        assertEquals(GameResult.ONGOING, game.getResult(), "game must not be finished");
        game.makeMove(MoveDescription.fromString("Bc6", game.getTurn()));
        assertEquals(GameResult.DRAW, game.getResult(), "game must be draw due to threefold repetition rule");
    }

    @Test
    void testDisableThreefoldRepetition() {
        var importer = GameImporter.importerFor("""
                1. e4 c5 2. Be2 Nc6 3. f4 e6 4. Nf3 b6 5. O-O Bb7 6. d3 Qc7 7. c3 Nf6 8. a4 d5 9. e5 Nd7
                10. Na3 a6 11. Qe1 Ne7 12. Nc2 Nf5 13. g4 Ne7 14. Qg3 h5 15. h3 d4 16. c4 Nc6 17. Bd2 g6
                18. Ng5 Be7 19. Ne4 O-O-O 20. a5 Nxa5 21. Rxa5 bxa5 22. Bf3 Nb8 23. Qg2 Nc6 24. g5 Nb4 25.
                Bxb4 axb4 26. Na1 a5 27. Bd1 Bc6 28. Re1 a4 29. b3 a3 30. Qa2 Kb7 31. Bf3 Kb6 32. Nc2 h4
                33. Re2 Rdf8 34. Ne1 Bb7 35. Rg2 Bc6 36. Re2 Ra8 37. Rg2 Rhg8 38. Rg4 Raf8 39. Rg2 Rh8 40.
                Rg4 Bb7 41. Rg2
                """);
        var config = new GameConfig(
                MyChessEngine.class,
                new EngineConfig.Builder().enableThreefoldRepetition(false).build());
        var game = importer.importGame(config);
        game.makeMove(MoveDescription.fromString("Bc6", game.getTurn()));
        assertEquals(GameResult.ONGOING, game.getResult(), "Game must not be finished yet");
    }

}
