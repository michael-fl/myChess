package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the property the v4.5.0 complete-PV work set out to establish: the score the root
 * reports belongs to the move it is reported for.
 *
 * <p>Measured rather than pinned. The test searches the position, then searches the move the
 * engine chose again — independently, with a table of its own — and compares the two numbers.
 * No expected value is written down, so the test survives every table and evaluation change
 * that moves the absolute score, and only fails when the two ways of measuring the same move
 * disagree.
 *
 * <p><b>Why this is not covered elsewhere.</b> Nothing else in the suite checks score-versus-move
 * consistency. {@code ThreefoldRepetitionTest} covers the same position from the other side, but
 * its assertions are about the principal variation and the game result — the score appears there
 * only in a comment. Move, score and line do all come from the same index at the return
 * statement, so they cannot be mixed up — but that says nothing about whether the value at that
 * index was computed by searching that move at all. That last gap is what this class covers.
 *
 * <p>It does <b>not</b> cover the root's move choice. The re-search can lower the winner's score
 * below another move's recorded score without the selection being re-run; both known repairs
 * measured −44.4 and −166 Elo and were reverted (roadmap § 12.25). This test would stay green
 * through that defect, because the score it checks belongs to the move that was returned either
 * way.
 *
 * <p>Both numbers are White-POV — {@code ChessEngine.calculateNextMove} applies the weightFactor
 * (+1 white, −1 black) at the boundary — so they are directly comparable and must not be negated
 * against each other. Getting that wrong makes the comparison pass trivially, which is how the
 * first draft of this class managed to be green while measuring nothing.
 *
 * <p><b>Deliberately a cold-table test.</b> A warm table can make the two numbers disagree for a
 * legitimate reason: the root's stricter path-local repetition rule scores a line as a draw at
 * its second occurrence, while a reference search started after that move has no path history
 * and cannot see it. Comparing across that difference measures the rule, not the engine. With a
 * cold table the difference cannot arise, so a disagreement has no innocent explanation. The
 * warm-table case — where entries computed with repetition detection off poison a search with it
 * on — is characterized in
 * {@code ThreefoldRepetitionTest.withRepetitionDetectionDisabledTheShuffleReturns} instead.
 *
 * @author Michael Fleischhauer
 */
class ReportedScoreConsistencyTest {

    /**
     * White is winning (mate in 14 by Stockfish). Shared with {@code ThreefoldRepetitionTest} on
     * purpose: a won position with an available shuffle is where a reported score has the most
     * room to drift from the move it belongs to.
     */
    private static final String WON_POSITION_FEN = "5Q2/7k/3N4/4P3/6R1/8/2r3P1/2K5 w - - 15 62";

    /** Mirrors the depth {@code ThreefoldRepetitionTest} uses on the same position. */
    private static final int SEARCH_DEPTH = 8;

    /**
     * How far the reported score may sit below an independent measurement of the same move.
     *
     * <p>Two pawns. Generous on purpose: the reference search runs with its own fresh table
     * while the root searches with a shared one and a narrowing window, so centipawn-level
     * disagreement is normal and must not fail the test. What this catches is a swing of whole
     * pawns — the warm-table case that motivated the class reports 0.0 where the same move is
     * worth 15.05.
     */
    private static final float TOLERANCE_PAWNS = 2.0f;

    private static final int TIMEOUT_S = 300;

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    /**
     * The score the root reports must be what its chosen move is worth.
     *
     * <p>Measured: 15.05 against 15.05. The comparison is one-sided — the root may report
     * <em>more</em> than the reference, since its shared table can see deeper — so only a
     * shortfall fails.
     */
    @Test
    @Timeout(value = TIMEOUT_S, unit = TimeUnit.SECONDS)
    void theReportedScoreMatchesAnIndependentSearchOfTheChosenMove() throws Exception {
        MoveAndWeight chosen = search(tt, SEARCH_DEPTH);
        float independent = scoreOfMoveWithAFreshTable(chosen.move());

        assertTrue(chosen.weight() >= independent - TOLERANCE_PAWNS,
                "the score the root reports must be the score its chosen move is worth: it reports "
                        + chosen.weight() + " for " + ChessUtil.moveToString(chosen.move())
                        + ", an independent search of the same move gives " + independent
                        + ". A gap this large means the reported score was not computed by searching "
                        + "this move. pv " + ChessUtil.pathToString(chosen.path()));
    }

    @SuppressWarnings("SameParameterValue")
    private MoveAndWeight search(TranspositionTable table, int depth) throws Exception {
        var game = new Game(new GameConfig(MyChessEngine.class, configWith(table, depth)),
                Fen.importFEN(WON_POSITION_FEN));

        return game.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES);
    }

    /**
     * @return the White-POV score of the position after {@code move}, searched one ply shallower
     *         with a table of its own so nothing from the root's table can reach it
     */
    private float scoreOfMoveWithAFreshTable(int move) throws Exception {
        var board = Fen.importFEN(WON_POSITION_FEN);

        board.makeMove(move);

        try (var childTt = TestSupport.createTestTT()) {
            var childGame = new Game(new GameConfig(MyChessEngine.class,
                    configWith(childTt, SEARCH_DEPTH - 1)), board);

            return childGame.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES).weight();
        }
    }

    private static EngineConfig configWith(TranspositionTable table, int depth) {
        return new EngineConfig.Builder()
                .maxDepth(depth)
                .enableThreefoldRepetition(true)
                .silent(true)
                .setTranspositionTable(table)
                .build();
    }
}
