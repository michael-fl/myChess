package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.michaelfl.mychess.EngineTestBase.engineConfig;

/**
 * @author Michael Fleischhauer
 */
class FiftyMovesRuleTest {

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

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
                new EngineConfig.Builder().enableFiftyMovesRule(false)
                        .setTranspositionTable(tt).build()
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

    /**
     * Filipowicz&ndash;Smederevac, Polanica Zdrój 1966 &mdash; the textbook fifty-move game,
     * and the counterpart to {@code ThreefoldRepetitionTest.testFindDrawMove}: the engine must
     * take a draw the rules offer it.
     *
     * <p>The last irreversible move was <b>20...h5</b>. Fifty moves of manoeuvring later the
     * position after {@code 70.Qg2} carries a half-move clock of <b>99</b>, black to move.
     *
     * <p><b>That 99 is the whole point, and it is easy to misread this test without it.</b>
     * The root short-circuit in {@code ChessEngine.calculateNextMove} fires at
     * {@code halfMoveClock >= 100}, so it does <em>not</em> trigger here. The draw has to be
     * found one ply deeper, inside {@code PositionSearch.alphaBetaSearchPre}. This therefore
     * exercises the search, not the rule check at the root — which is what makes it worth
     * having next to {@link #testFiftyMovesRule()}, where the clock has already run out.
     * The premise is asserted below so that an edit to the move list cannot silently turn
     * this into a test of something else.
     *
     * <p><b>Why no assertion on the move.</b> Black has 38 legal moves, and <b>33</b> of them
     * reach the fifty-move draw; only {@code Bxe5}, {@code Bxa4}, {@code Nxf4}, {@code b5} and
     * {@code c4} reset the clock and play on. There is no unique drawing move to pin, so the
     * assertions are on the score and the result. myChess picks {@code Nc3} and reports 0.00
     * from depth 1 upward.
     *
     * <p>The choice is not a throwaway: three of the five continuations are captures. That the
     * engine prefers 0.00 to grabbing a pawn is correct here — Stockfish rates the position
     * with the clock artificially cleared at only +0.24 for black, so the draw costs
     * essentially nothing, and with the real clock its own top three moves all evaluate to 0.
     *
     * <p>Deliberately carries no {@code Test family:} marker, for the same reason
     * {@code testFindDrawMove} does not: it guards correct behavior in a theme that has no
     * open defect, and a family counting only guards would add nothing to the tally that is
     * evidence of anything.
     */
    @Test
    void testFilipowiczSmederevac1966() throws ExecutionException, InterruptedException, TimeoutException {
        var importer = GameImporter.importerFor("""
                1. e4 e6 2. d3 Ne7 3. g3 c5 4. Bg2 Nbc6 5. Be3 b6 6. Ne2 d5 7. O-O d4 8. Bc1 g6 9. Nd2 Bg7 10. f4 f5
                11. a3 O-O 12. e5 a5 13. a4 Ba6 14. b3 Rb8 15. Nc4 Qc7 16. Kh1 Nd5 17. Bd2 Rfd8 18. Ng1 Bf8 19. Nf3 Be7
                20. h4 h5 21. Qe2 Ncb4 22. Rfc1 Bb7 23. Kh2 Bc6 24. Na3 Ra8 25. Qe1 Rdb8 26. Qg1 Qb7 27. Qf1 Kg7
                28. Qh1 Qd7 29. Ne1 Ra7 30. Nf3 Rba8 31. Ne1 Bd8 32. Nf3 Rb8 33. Ne1 Bc7 34. Nf3 Rh8 35. Ng5 Bd8
                36. Nf3 Be7 37. Qg1 Bb7 38. Nb5 Raa8 39. Na3 Ba6 40. Qf1 Rab8 41. Nc4 Bd8 42. Qd1 Ne7 43. Nd6 Bc7
                44. Qe2 Ng8 45. Ng5 Nh6 46. Bf3 Bd8 47. Nh3 Ng4+ 48. Kg1 Be7 49. Nc4 Nd5 50. Nf2 Bb7 51. Nh3 Bc6
                52. Qg2 Rhc8 53. Re1 Rc7 54. Re2 Ra7 55. Ree1 Ra6 56. Re2 Rba8 57. Ree1 R8a7 58. Na3 Ra8 59. Nc4 Nh6
                60. Na3 Nf7 61. Nf2 Rd8 62. Nc4 Rb8 63. Nh3 Bd8 64. Na3 Ra7 65. Qh1 Bc7 66. Qg2 Rd8 67. Qh1 Nh6
                68. Ng5 Qe8 69. Kh2 Rd7 70. Qg2
                """);
        var config = new GameConfig(MyChessEngine.class, engineConfig(tt));
        var game = importer.importGame(config);

        // 99, not 100: one ply below what the rule needs, so the draw has to come from the
        // search rather than from the root check in ChessEngine.
        assertEquals(99, game.getGameStatus().getHalfMoveClock(),
                "premise of this test: the clock must be one ply short of the rule, so the draw has to be "
                        + "found by the search rather than by the root check in ChessEngine");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0f, move.weight(), "Weight must be 0 (draw)");
        assertEquals(GameResult.DRAW, move.result(), "game must be draw due to the fifty-move rule");
    }

}
