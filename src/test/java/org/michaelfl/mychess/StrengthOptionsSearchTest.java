package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.IterationInfo;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The strength options {@code MaxDepth} and {@code MoveTolerance} in a real search. The move choice
 * itself is tested in {@code ChooseCandidateTest}.
 *
 * @author Michael Fleischhauer
 */
class StrengthOptionsSearchTest {

    /** Italian Game after 4...Bc5 5.d3. Many moves of similar score. */
    private static final String QUIET_FEN = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R b KQkq - 0 5";

    /** White wins the queen with Rxd5. Every other move loses at least the rook. */
    private static final String FORCED_FEN = "4k3/8/8/3q4/8/8/3R4/4K3 w - - 0 1";

    private static final int DEPTH = 4;

    /** Tolerance of the move-choice test, in centipawns. */
    private static final int TOLERANCE_CENTI = 60;

    /** Enough searches that playing only the best move by chance is negligible (0.8^50). */
    private static final int SEARCHES = 50;

    /**
     * Fewest distinct moves the searches must play. Measured on 2026-11-01: 7 near and 9 far candidates,
     * 13 distinct moves on average. 6 or fewer has a probability below 3e-5. Re-measure when the
     * evaluation changes.
     */
    private static final int MIN_DISTINCT_MOVES = 7;

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
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void maxDepth_stopsTheIterativeDeepeningThere() throws Exception {
        var builder = baseConfig(6).strengthMaxDepth(3);

        assertEquals(List.of(1, 2, 3), iterationDepths(builder), "MaxDepth 3 must stop a depth-6 search after depth 3");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void maxDepth_aboveTheSearchDepth_changesNothing() throws Exception {
        assertEquals(List.of(1, 2, 3), iterationDepths(baseConfig(3).strengthMaxDepth(8)),
                "MaxDepth 8 must not deepen a depth-3 search");
        assertEquals(List.of(1, 2, 3), iterationDepths(baseConfig(3)), "MaxDepth 0 must mean no limit");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void moveTolerance_playsDifferentMoves_allWithinTheTolerance() throws Exception {
        final int tolerance = TOLERANCE_CENTI;
        final MoveAndWeight best = search(QUIET_FEN, baseConfig(DEPTH));
        final Set<Integer> played = new HashSet<>();
        boolean farPlayed = false;

        for (int i = 0; i < SEARCHES; i++) {
            final MoveAndWeight chosen = search(QUIET_FEN, baseConfig(DEPTH).moveToleranceCenti(tolerance));
            played.add(chosen.move());

            // black to move. The weights are White's view, so lower is better.
            final int distanceCenti = Math.round((chosen.weight() - best.weight()) * 100);
            farPlayed |= distanceCenti >= EngineConfig.DEFAULT_MOVE_TOLERANCE_NEAR_CENTI;
            assertTrue(distanceCenti >= 0 && distanceCenti < tolerance,
                    "move " + ChessUtil.moveToString(chosen.move()) + " is " + distanceCenti
                            + " cp below the best move " + ChessUtil.moveToString(best.move()) + ", tolerance " + tolerance);
        }

        assertTrue(played.size() >= MIN_DISTINCT_MOVES, SEARCHES + " searches with a tolerance of " + tolerance
                + " cp must play at least " + MIN_DISTINCT_MOVES + " distinct moves, played " + played.size());
        // no far move in 50 searches has a probability of 0.8^50
        assertTrue(farPlayed, "a far candidate (" + EngineConfig.DEFAULT_MOVE_TOLERANCE_NEAR_CENTI
                + " cp or more below the best move) must be played at least once");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void moveTolerance_withOneGoodMove_alwaysPlaysIt() throws Exception {
        final MoveAndWeight best = search(FORCED_FEN, baseConfig(DEPTH));

        for (int i = 0; i < 30; i++) {
            final MoveAndWeight chosen = search(FORCED_FEN, baseConfig(DEPTH).moveToleranceCenti(200));

            assertEquals(best.move(), chosen.move(), "only the queen capture lies within 200 cp of the best move");
            assertEquals(best.weight(), chosen.weight(), 0.0f, "the score of the capture must be the best move's");
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void moveToleranceZero_playsTheSameMoveAndScoreAsWithoutTheOption() throws Exception {
        final MoveAndWeight without = search(QUIET_FEN, baseConfig(DEPTH));
        final MoveAndWeight zero = search(QUIET_FEN, baseConfig(DEPTH).moveToleranceCenti(0));

        assertEquals(without.move(), zero.move(), "tolerance 0 must play the best move");
        assertEquals(without.weight(), zero.weight(), 0.0f, "tolerance 0 must report the best score");
    }

    private EngineConfig.Builder baseConfig(int depth) {
        return new EngineConfig.Builder()
                .maxDepth(depth)
                .silent(true)
                .setTranspositionTable(tt);
    }

    private MoveAndWeight search(String fen, EngineConfig.Builder builder) throws Exception {
        tt.clear();
        var game = new Game(new GameConfig(MyChessEngine.class, builder.build()), Fen.importFEN(fen));
        try {
            return game.getEngine().nextMoveAsync().getResult(30, TimeUnit.SECONDS);
        } finally {
            game.shutdown();
        }
    }

    private List<Integer> iterationDepths(EngineConfig.Builder builder) throws Exception {
        tt.clear();
        var depths = new ArrayList<Integer>();
        var game = new Game(new GameConfig(MyChessEngine.class, builder.build()), Fen.importFEN(QUIET_FEN));
        try {
            game.getEngine().nextMoveAsync(null, (IterationInfo info) -> depths.add(info.depth()))
                    .getResult(30, TimeUnit.SECONDS);
        } finally {
            game.shutdown();
        }
        return depths;
    }
}
