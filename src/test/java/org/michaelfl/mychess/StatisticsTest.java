package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Statistics}, the search's counters.
 *
 * <p>The class had no tests until the material-only leaf counter arrived, and that counter is the
 * reason it has them now: it exists to be *read as a diagnosis*, so a counter that silently counts
 * the wrong thing is worse than no counter — it would send the next investigation the wrong way.
 * The aggregates carry the same risk, so they are pinned here too. `quiescencePositionsCount` in
 * particular is a total over calls, fed from a per-call counter that {@link Statistics#endQuiescenceSearch}
 * folds in, and that two-stage shape is easy to break.
 *
 * <p>The last two tests drive a real quiescence search rather than poking counters, because the
 * counter is only useful if it fires where the shortcut fires — and only there. Calling
 * {@link Statistics#incrMaterialOnlyLeafCount()} by hand proves arithmetic, not wiring, and an
 * increment in unreachable code would satisfy every other test in this class.
 *
 * @author Michael Fleischhauer
 */
class StatisticsTest {

    /** White to move with an undefended black queen on d5: capturing it swings 1000 cp at once. */
    private static final String QUEEN_CAN_BE_TAKEN =
            "rnb1kbnr/ppp1pppp/8/3q4/8/2N5/PPPPPPPP/R1BQKBNR w KQkq - 0 1";

    /** Kings and one pawn each, nothing capturable: no material swing is reachable at all. */
    private static final String NOTHING_TO_CAPTURE = "4k3/4p3/8/8/8/8/4P3/4K3 w - - 0 1";

    private static final int MAX_QUIESCENCE_DEPTH = 20;
    private static final long ONE_MINUTE = 60_000L;

    /** Runs a quiescence search the way {@code PositionSearch} wires one, and returns its counters. */
    private static Statistics quiescenceOver(String fen) {
        var board = Fen.importFEN(fen);
        var statistics = new Statistics();
        var qsearch = new QuiescenceSearch(MoveGenerator.forQuiescenceSearch(),
                new WeightingFunction(), statistics, MAX_QUIESCENCE_DEPTH,
                System.currentTimeMillis() + ONE_MINUTE);
        final int weightFactor = 1;

        qsearch.quiescenceSearch(board, 0, weightFactor, WeightingFunction.MIN_ALPHA,
                WeightingFunction.MAX_BETA,
                weightFactor * WeightingFunction.calculateMaterialWeight(board), 0);

        return statistics;
    }

    @Test
    void theMaterialOnlyLeafCounterStartsAtZeroAndCountsEachCall() {
        var statistics = new Statistics();

        assertEquals(0, statistics.getMaterialOnlyLeafCount(), "a fresh Statistics counts nothing");

        statistics.incrMaterialOnlyLeafCount();
        statistics.incrMaterialOnlyLeafCount();
        statistics.incrMaterialOnlyLeafCount();

        assertEquals(3, statistics.getMaterialOnlyLeafCount(), "three calls, three leaves");
    }

    @Test
    void theMaterialOnlyLeafCounterIsIndependentOfTheOtherCounters() {
        var statistics = new Statistics();

        statistics.incrPositionCount();
        statistics.incrQuiescencePositionsCount();
        statistics.incrNmpCutoffCount();
        statistics.incrPrunedMovesCount(7);

        assertEquals(0, statistics.getMaterialOnlyLeafCount(),
                "no other counter may leak into it — the firing rate is read as a quotient "
                        + "against the quiescence count, so a shared increment would make the "
                        + "quotient meaningless rather than merely wrong");

        statistics.incrMaterialOnlyLeafCount();

        assertEquals(1, statistics.getPositionsCount(), "and it must not leak back");
        assertEquals(7, statistics.getPrunedMovesCount(), "nor into the pruned-move total");
    }

    /**
     * The denominator the firing rate is read against. The quiescence total is fed per call and
     * folded in by {@link Statistics#endQuiescenceSearch()}, so it is the one counter here whose
     * value depends on two methods being called in the right order.
     */
    @Test
    void theQuiescenceTotalAccumulatesAcrossCalls() {
        var statistics = new Statistics();

        statistics.incrQuiescencePositionsCount();
        statistics.incrQuiescencePositionsCount();
        statistics.endQuiescenceSearch();
        statistics.incrQuiescencePositionsCount();
        statistics.endQuiescenceSearch();

        assertEquals(3, statistics.getQuiescencePositionsCount(),
                "two nodes in the first quiescence search and one in the second");
        assertEquals(2, statistics.getQuiescencePositionsCountMax(),
                "the deepest single quiescence search visited two nodes");
    }

    @Test
    void reachedDepthKeepsTheMaximumRatherThanTheLast() {
        var statistics = new Statistics();

        statistics.reachedDepth(4);
        statistics.reachedDepth(9);
        statistics.reachedDepth(2);

        assertEquals(9, statistics.getMaximumReachedDepth(),
                "a shallower later call must not lower the maximum");
    }

    /**
     * The wiring, not the arithmetic: the counter has to fire where the shortcut fires. Without
     * this the increment could sit in unreachable code and every test above would still pass.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void capturingAQueenFiresTheShortcut() {
        var statistics = quiescenceOver(QUEEN_CAN_BE_TAKEN);

        assertTrue(statistics.getMaterialOnlyLeafCount() > 0,
                "taking the queen swings material by 1000 cp, far past the 200 cp threshold, so "
                        + "leaves beyond it must return raw material; got "
                        + statistics.getMaterialOnlyLeafCount() + " of "
                        + statistics.getQuiescencePositionsCount() + " quiescence nodes");
    }

    /**
     * The other half of the claim: the counter must stay at zero when nothing can swing. A counter
     * that only ever goes up is indistinguishable from one wired to the wrong condition.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void aPositionWithNothingToCaptureLeavesTheCounterAtZero() {
        var statistics = quiescenceOver(NOTHING_TO_CAPTURE);

        assertEquals(0, statistics.getMaterialOnlyLeafCount(),
                "no capture exists, so the material swing stays at 0 and the shortcut must never "
                        + "fire; quiescence nodes visited: "
                        + statistics.getQuiescencePositionsCount());
    }
}
