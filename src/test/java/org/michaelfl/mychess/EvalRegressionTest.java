package org.michaelfl.mychess;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression anchors for positional / evaluation weaknesses. Each test
 * pins the engine's <em>current</em> (often wrong) move choice on a
 * concrete position, with a to-do block explaining what the engine
 * <em>should</em> do and why the current choice is bad. When an eval
 * change makes the engine pick a different move, the test fails and
 * forces a review: is the new move an improvement (update / remove
 * this test) or a different flavor of the same problem (adjust the
 * assertion, keep the to-do)?
 *
 * <p>Not a strength test — for that see the cutechess SPRT infrastructure
 * under {@code test-results/}. This class documents known problem positions
 * and provides a stable reference set to measure evaluation changes against.
 *
 * @author Michael Fleischhauer
 */
@Tag("slow")
class EvalRegressionTest extends EngineTestBase {

    /**
     * Regression anchor (Chess960). myChess 4.0.3 traded its active a6-bishop
     * for the passive e2-knight here (a6-e2), dropping Stockfish's eval from
     * +2.2 to +0.6 (Black's view) — the static eval underweighted active
     * long-diagonal bishops against passive knights.
     * <p>
     * <b>Resolved in v4.2.0.</b> With the all-captures quiescence search
     * ([search § 6.4]) the engine no longer makes that trade — it plays
     * <b>h8-g7</b> (fianchettoes the h8-bishop, keeping the a6-bishop active),
     * which is one of Stockfish's top two choices in this position (~ −2.3,
     * alongside Qb6). The method name reflects the historical 4.0.3 symptom;
     * the test now pins the corrected, strong move.
     */
    @Test
    void tradesActiveA6BishopForPassiveE2Knight() throws InterruptedException, ExecutionException, TimeoutException {
        var pgn = """
                [Date "2026.07.05"]
                [White "Michael Fleischhauer"]
                [Black "myChess 4.0.3"]
                [Result "*"]
                [SetUp "1"]
                [FEN "bnrqkrnb/pppppppp/8/8/8/8/PPPPPPPP/BNRQKRNB w KQkq - 0 1"]
                [Variant "fischerandom"]

                1. g3 Nf6 2. Nf3 d5 3. d4 O-O 4. Nc3 c5 5. b3 g6 6. e3 cxd4
                7. Nxd4 Qa5 8. Qd2 e5 9. Nde2 Rfd8 10. Rd1 b5 11. a4 b4
                12. Na2 Nc6 13. Qc1 Bb7 14. O-O Ba6 15. Rfe1 *
                """;
        var config = new GameConfig(ENGINE, engineConfig());
        try {
            var game = GameImporter.importerFor(pgn).importGame(config);

            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
            var moveStr = ChessUtil.moveToString(move.move());
            assertEquals("h8-g7", moveStr,
                    "v4.2.0 all-captures QSearch now finds Bg7 (a top Stockfish choice), not the old bad a6-e2 trade");
        } finally {
            config.getEngineWhiteConfig().getTranspositionTable().close();
        }
    }

    /**
     * RESOLVED in v4.3.2 — myChess now plays the active 25...Ng4 (f6-g4),
     *   Stockfish's best move here, instead of the passive rook shuffle it used
     *   to pick. The choice had oscillated with eval changes (older Rc6 ≈ +2.2
     *   White-view → SEE-QSearch Rb8 ≈ +1.9 → the v4.3.0 tapered pawn-EG table
     *   flipped it back to Rc6); raising the queen's material value to 1000
     *   (v4.3.2) finally tipped it to the active Ng4. This test now guards
     *   against a regression back to the passive move.
     * <p>
     *   Position context (Chess960, corner-bishop setup): Black just
     *   captured the a2-knight and White recaptured with Qxa2. Both sides
     *   have completed development; Black has an active queen on a5 and
     *   dark-square bishop on g7, but the c8-rook stands passively behind
     *   Black's own pawns. Rb8 neither opens a file nor challenges a White
     *   piece — it just shuffles the rook along the back rank.
     * <p>
     *   Better plans: bring the knight forward Ng4 (Stockfish's choice) or
     *   Ne8, or advance the h-pawn (h5) — active plans that make use of the
     *   dark-square bishop's diagonal.
     * <p>
     *   Root cause: static eval undervalues piece activity and central
     *   knight jumps in closed middlegame positions, and overvalues
     *   passive rook moves. NOT a search-depth issue.
     */
    @Test
    void findsTheActiveNg4() throws InterruptedException, ExecutionException, TimeoutException {
        // Feed the same game up to and including White's 25th move
        // (Qxa2 recapturing the knight). Then it is Black to move —
        // myChess computes the response: as of v4.3.2, the active Ng4 (f6-g4).
        var pgn = """
                [Date "2026.07.05"]
                [White "Michael Fleischhauer"]
                [Black "myChess 4.0.3"]
                [Result "*"]
                [SetUp "1"]
                [FEN "bnrqkrnb/pppppppp/8/8/8/8/PPPPPPPP/BNRQKRNB w KQkq - 0 1"]
                [Variant "fischerandom"]

                1. g3 Nf6 2. Nf3 d5 3. d4 O-O 4. Nc3 c5 5. b3 g6 6. e3 cxd4
                7. Nxd4 Qa5 8. Qd2 e5 9. Nde2 Rfd8 10. Rd1 b5 11. a4 b4
                12. Na2 Nc6 13. Qc1 Bb7 14. O-O Ba6 15. Rfe1 Bxe2 16. Rxe2 Bg7
                17. Red2 Qc5 18. c3 bxc3 19. Nxc3 Nb4 20. Bb2 Qb6 21. Qb1 e4
                22. Bg2 a6 23. Bf1 Qa5 24. Na2 Nxa2 25. Qxa2 *
                """;
        var config = new GameConfig(ENGINE, engineConfig());
        try {
            var game = GameImporter.importerFor(pgn).importGame(config);

            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
            var moveStr = ChessUtil.moveToString(move.move());
            assertEquals("f6-g4", moveStr,
                    "v4.3.2 (queen=1000) tips this Chess960 position to Stockfish's active Ng4 (f6-g4); guards against regression to the passive rook move");
        } finally {
            config.getEngineWhiteConfig().getTranspositionTable().close();
        }
    }
}
