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
     * TODO — in this Chess960 position myChess plays the mediocre rook move
     *   d8-d7: Stockfish drops from -1.9 to -1.0 (Black's view), giving up
     *   ~0.9 of Black's advantage. Not a blunder, but well short of the best
     *   positional continuation.
     * <p>
     *   History: myChess 4.0.3 played the outright-bad a6-e2 here, trading
     *   its active a6-bishop for the passive e2-knight. The aggressive-R +
     *   null-move verification search moved the engine off that trade to
     *   d8-d7 — a mild improvement (d8-d7 evaluates better than a6-e2), but
     *   incidental: the engine keeps the bishop yet still misses the
     *   strongest move.
     * <p>
     *   Root cause is eval-side, not search: the static eval underweights
     *   the strongest positional continuation (active long-diagonal bishop,
     *   piece activity). NOT fixable by deeper search or NMP alone.
     *   Candidates: mobility term in {@link WeightingFunction}, PST tweaks,
     *   or a piece-activity penalty for out-of-play knights.
     * <p>
     *   This test now pins the current suboptimal choice d8-d7 as a behavior
     *   anchor. It is search-sensitive and may change again on the next search
     *   tweak; when it does, re-check against Stockfish and re-anchor. The eval
     *   weakness is resolved only when the engine finds a move at or near
     *   Stockfish's best (~ -1.9).
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
            assertEquals("d8-d7", moveStr,
                    "current suboptimal choice; see TODO — still ~0.9 below best, eval weakness unresolved");
        } finally {
            config.getEngineWhiteConfig().getTranspositionTable().close();
        }
    }

    /**
     * TODO — myChess plays 25...Rc6 (c8-rook to c6), dropping Stockfish's
     *   eval from +0.7 to +2.5 (White's view) — nearly two pawn units in
     *   a single move. myChess sees the resulting position at only −0.10
     *   from Black's perspective, so once again the engine has no
     *   awareness of the impending drop.
     * <p>
     *   Position context (Chess960, corner-bishop setup): Black just
     *   captured the a2-knight and White recaptured with Qxa2. Both sides
     *   have completed development; Black has an active queen on a5 and
     *   dark-square bishop on g7, but the c8-rook stands passively behind
     *   Black's own pawns. Rc6 does not open a file or challenge a White
     *   piece — it just repositions to a square where it can be attacked
     *   and where it interferes with Black's own pieces.
     * <p>
     *   Better plans: bring the knight forward Ng4 or advance the h-pawn (h5).
     *   Both are active plans that make use of the dark-square bishop's diagonal.
     * <p>
     *   Root cause: static eval undervalues piece activity and central
     *   knight jumps in closed middlegame positions, and overvalues
     *   passive rook lifts. NOT a search-depth issue.
     */
    @Test
    void movesRookToC6DroppingAlmostTwoPawns() throws InterruptedException, ExecutionException, TimeoutException {
        // Feed the same game up to and including White's 25th move
        // (Qxa2 recapturing the knight). Then it is Black to move —
        // myChess computes the response, which is expected to be the
        // known-bad c8-c6 rook lift.
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
            assertEquals("c8-c6", moveStr,
                    "current known-bad choice; see TODO");
        } finally {
            config.getEngineWhiteConfig().getTranspositionTable().close();
        }
    }
}
