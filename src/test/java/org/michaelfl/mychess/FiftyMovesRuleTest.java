package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.MyChessEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Michael Fleischhauer
 */
class FiftyMovesRuleTest {

    @Test
    void testFiftyMovesRule() {
        GameImporter importer = GameImporter.importerFor("""
                1. e4 e5 2. d3 d6 3. Be2 Nf6 4. Nf3 Qd7 5. Nc3 Kd8 6. O-O Rg8 7. Rb1 Rh8 8. Be3 Qe8 9. Rc1
                Nc6 10. Ne1 Be6 11. Qd2 Kc8 12. Ra1 Kb8 13. Rd1 Ng8 14. Ra1 Nh6 15. Nf3 Nf5 16. Nh4 Nfe7
                17. Nf3 Qc8 18. Rfb1 Nd4 19. Nd1 Nec6 20. Ne1 Be7 21. Kh1 Rd8 22. Kg1 Re8 23. Kf1 Rf8 24.
                Kg1 Rg8 25. Kh1 Qf8 26. Bf4 Bh4 27. Bf3 Bc4 28. Qe3 Ne6 29. Nc3 Ne7 30. Bg4 Ng6 31. Nf3
                Nh8 32. Ng1 Nd8 33. Qc1 Be6 34. Bd1 Bc8 35. Bd2 Be7 36. Be1 Qe8 37. Nce2 Bf8 38. Ng3 Qd7
                39. Nf1 Qe8 40. Ne3 Qe6 41. Nc4 Qe8 42. Na5 Qe6 43. Nb3 Qe8 44. Nc5 Qe7 45. Ne6 Qe8 46.
                Ng5 Qe7 47. N5h3 Qe6 48. Nf4 Qe8 49. Nfe2 Qe6 50. Nc3 Qe8 51. Nd5 Nc6 52. Nf3
                """);
        var game = importer.importGame(Game.standardConfig());

        var halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(99, halfMoveClock, "Wrong half move clock");

        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not yet be finished - one move left");

        game.makeMove(MoveDescription.fromString("c8-d7", game.getTurn()));
        halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(100, halfMoveClock, "Wrong half move clock");
        assertEquals(GameResult.DRAW, game.getResult(), "Game should be draw due to 50 moves rule");

        String expectedFEN = "rk2qbrn/pppb1ppp/2np4/3Np3/4P3/3P1N2/PPP2PPP/RRQBB2K w - - 100 53";
        assertEquals(expectedFEN, game.exportFEN(), "wrong FEN");
    }

    @Test
    void testDisableFiftyMovesRule() {
        GameImporter importer = GameImporter.importerFor("""
                1. e4 e5 2. d3 d6 3. Be2 Nf6 4. Nf3 Qd7 5. Nc3 Kd8 6. O-O Rg8 7. Rb1 Rh8 8. Be3 Qe8 9. Rc1
                Nc6 10. Ne1 Be6 11. Qd2 Kc8 12. Ra1 Kb8 13. Rd1 Ng8 14. Ra1 Nh6 15. Nf3 Nf5 16. Nh4 Nfe7
                17. Nf3 Qc8 18. Rfb1 Nd4 19. Nd1 Nec6 20. Ne1 Be7 21. Kh1 Rd8 22. Kg1 Re8 23. Kf1 Rf8 24.
                Kg1 Rg8 25. Kh1 Qf8 26. Bf4 Bh4 27. Bf3 Bc4 28. Qe3 Ne6 29. Nc3 Ne7 30. Bg4 Ng6 31. Nf3
                Nh8 32. Ng1 Nd8 33. Qc1 Be6 34. Bd1 Bc8 35. Bd2 Be7 36. Be1 Qe8 37. Nce2 Bf8 38. Ng3 Qd7
                39. Nf1 Qe8 40. Ne3 Qe6 41. Nc4 Qe8 42. Na5 Qe6 43. Nb3 Qe8 44. Nc5 Qe7 45. Ne6 Qe8 46.
                Ng5 Qe7 47. N5h3 Qe6 48. Nf4 Qe8 49. Nfe2 Qe6 50. Nc3 Qe8 51. Nd5 Nc6 52. Nf3
                """);
        var config = new GameConfig(
                MyChessEngine.class,
                new EngineConfig.Builder().enableFiftyMovesRule(false).build()
        );
        var game = importer.importGame(config);

        var halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(99, halfMoveClock, "Wrong half move clock");

        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not be finished");

        game.makeMove(MoveDescription.fromString("c8-d7", game.getTurn()));
        halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(100, halfMoveClock, "Wrong half move clock");
        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not be finished");
    }
}
