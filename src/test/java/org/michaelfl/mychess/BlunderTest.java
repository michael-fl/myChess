package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproductions of suspected blunders observed in match-play PGNs.
 * Each test imports the played game up to the move before the
 * alleged mistake, plays the mistake, and asserts that the engine,
 * given a fresh search on the resulting position, agrees that the
 * move was a blunder (i.e. the white-POV evaluation collapses well
 * past where it sat in the move preceding the mistake).
 *
 * <p>The engine runs with the per-move time budget pinned in
 * {@link #SEARCH_BUDGET_MS} below — chosen to match the match-play
 * budget so the eval shape lines up with what was actually played
 * at the board. The JUnit {@code @Timeout} on each test is set
 * generously above that budget so a regression that hangs is still
 * surfaced.
 *
 * @author Michael Fleischhauer
 */
class BlunderTest {

    /**
     * Per-move thinking time the engine is given when re-searching
     * a reproduced position. Matches the {@code tc=40/1200}
     * cutechess setting used in the match runs against Pulse / SF
     * — 1 200 s for 40 moves works out to roughly 30 s per move.
     */
    private static final int SEARCH_BUDGET_MS = 30_000;

    /**
     * JUnit-level safety timeout. Long enough that a clean
     * {@link #SEARCH_BUDGET_MS}-ms search plus a few seconds of
     * overhead fit, short enough that a hung search fails fast.
     */
    private static final int JUNIT_TIMEOUT_S = 60;

    private static Game gameWithExplicitBudget(String pgn) {
        var engineConfig = new EngineConfig.Builder()
                .millisPerMove(SEARCH_BUDGET_MS)
                .silent(true)
                .build();
        var gameConfig = new GameConfig(MyChessEngine.class, engineConfig);

        return GameImporter.importerFor(pgn).importGame(gameConfig);
    }

    /**
     * Round 1 of {@code mychess-vs-pulse2000.pgn} (myChess vs
     * Pulse-2000, 0-1). The chess-objective blunder is
     * {@code 66.Nxe5}: until move 65 the position is roughly
     * balanced and white can still hold the draw, but trading the
     * last minor piece on e5 ({@code 66.Nxe5 Kxe5}) leaves a king-
     * and-pawn endgame that is decisively lost for white. The
     * engine itself missed this — the PGN comment shows white's
     * eval drifting near zero ({@code -0.04} at depth 12) for the
     * Nxe5 move itself and only collapsing to {@code -8.00} eight
     * moves later when the deeper search finally sees the endgame
     * shape.
     *
     * <p>The test reproduces the position after {@code 65...Ne5},
     * plays {@code 66.Nxe5}, and asserts that a fresh search on the
     * resulting position returns a clearly negative white-POV
     * evaluation. <b>Expected to fail with the current engine</b> —
     * it documents an evaluation blind spot: at the depths reachable
     * within the standard time budget, myChess still sees the
     * post-Nxe5 endgame as roughly equal. The test will start
     * passing once the eval (or the search depth in this kind of
     * pawn endgame) is strong enough to recognise the loss before
     * playing into it.
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void nxe5_atMove66_vsPulse2000_collapsesEvaluation() throws Exception {
        var pgn = """
                1. c4 Nc6 2. e3 d5 3. cxd5 Qxd5 4. Nc3 Qd6 5. Nf3 Nf6 6. Bc4 Be6 7. Nb5 Qd7
                8. Bxe6 fxe6 9. Qb3 e5 10. Ng5 Nd8 11. O-O e6 12. d4 a6 13. dxe5 Qxb5
                14. Qxb5+ axb5 15. exf6 gxf6 16. Ne4 f5 17. Nf6+ Kf7 18. Nd7 Bg7 19. Rd1 Nc6
                20. e4 Bd4 21. exf5 exf5 22. Bg5 Rhe8 23. Be3 Bxe3 24. fxe3 Rxe3 25. Rd5 Rd8
                26. Rad1 Ke7 27. Nc5 Rxd5 28. Rxd5 Re1+ 29. Kf2 Re5 30. Rxe5+ Nxe5 31. Nxb7 Nd3+
                32. Ke3 Nxb2 33. Kd4 Kd7 34. Kc5 c6 35. Kd4 Nc4 36. Nc5+ Kd6 37. Nb7+ Ke7
                38. Kc5 Ne5 39. Kd4 Nd7 40. Ke3 Kf6 41. Kd4 Ke6 42. Nd8+ Kd6 43. Ke3 c5
                44. Kf4 Ke7 45. Nc6+ Kf6 46. Na7 b4 47. Nb5 Nf8 48. Nd6 Ne6+ 49. Kf3 Nd4+
                50. Ke3 b3 51. axb3 Nxb3 52. Nc4 Nd4 53. Nb6 Ne6 54. Kf3 h6 55. Nd7+ Kg7
                56. Ke3 Kf7 57. Ne5+ Kf6 58. Nd7+ Ke7 59. Nb6 Nf8 60. Kf4 Ke6 61. Kf3 Ng6
                62. Na4 Kd6 63. Ke3 Kd5 64. Nb6+ Ke6 65. Nc4 Ne5
                """;
        var game = gameWithExplicitBudget(pgn);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "after 65...Ne5 white must be to move");

        // Play the actual blunder — trading the last minor piece off
        // into a pawn endgame that turns out to be lost for white.
        game.makeMove(MoveDescription.fromString("Nxe5", game.getTurn()));

        // Black to move now. The MoveAndWeight returned by the engine
        // carries its weight in White-POV (see weightFactor handling
        // in ChessEngine.calculateNextMove): negative = bad for white.
        var result = game.getEngine().nextMoveAsync().getResult(JUNIT_TIMEOUT_S - 5, TimeUnit.SECONDS);

        // TODO: should be weight < -6
        assertTrue(result.weight() < -0.9f,
                "after 66.Nxe5 the engine must see white as clearly lost; "
                        + "got white-POV eval " + result.weight());
    }
}
