package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Bench.BenchException;
import org.michaelfl.mychess.Bench.BenchResult;
import org.michaelfl.mychess.Bench.PositionResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Arithmetic of {@link BenchResult}'s aggregate accessors, on synthetic results.
 *
 * <p>Deliberately builds {@code PositionResult}s by hand instead of running the suite: the
 * accessors are pure functions of the list, so a real run would add minutes without adding
 * coverage. The signature itself is covered by {@code NodeCountTest} and
 * {@code EvalBenchmarkTest}.
 *
 * <p>The concentration accessors exist because the total hides how it is composed — see
 * {@link BenchResult#largestPosition()} for the measurement that motivated them.
 *
 * @author Michael Fleischhauer
 */
class BenchResultTest {

    private static final String FEN_SMALL = "8/8/8/8/5kp1/P7/8/1K1N4 w - - 0 1";
    private static final String FEN_DOMINANT = "k7/2n1n3/1nbNbn2/2NbRBn1/1nbRQR2/2NBRBN1/3N1N2/7K w - - 0 1";
    private static final String FEN_MIDDLE = "2rqr1k1/1p1bbppp/p3p3/2npP3/3Q4/P1N1BN2/1PP2PPP/R3R1K1 w - - 4 17";

    private static final double SHARE_TOLERANCE = 0.05;

    private static BenchResult resultOf(List<PositionResult> positions, long totalTimeMs) {
        long totalNodes = positions.stream().mapToLong(PositionResult::nodes).sum();

        return new BenchResult(8, false, positions, totalNodes, totalTimeMs);
    }

    @Test
    void largestPositionPicksTheHighestNodeCountRegardlessOfOrder() {
        var small = new PositionResult(FEN_SMALL, 48, 1);
        var dominant = new PositionResult(FEN_DOMINANT, 1_129_861_147, 936_000);
        var middle = new PositionResult(FEN_MIDDLE, 3_282, 3);

        // The dominant entry sits in the middle, so a first- or last-element bug would show.
        var result = resultOf(List.of(small, dominant, middle), 1_040_000);

        assertSame(dominant, result.largestPosition(), "largest position of the run");
    }

    @Test
    void largestPositionShareIsItsPercentageOfTheTotal() {
        var dominant = new PositionResult(FEN_DOMINANT, 870, 900);
        var rest = new PositionResult(FEN_SMALL, 130, 10);

        var result = resultOf(List.of(dominant, rest), 910);

        assertEquals(1_000, result.totalNodes(), "total nodes over both positions");
        assertEquals(87.0, result.largestPositionShare(), SHARE_TOLERANCE,
                "share of the total consumed by the largest position");
    }

    @Test
    void theReducedSignatureAndNpsExcludeOnlyTheLargestPosition() {
        // The dominant entry burns 900 of the 1000 ms for 870 of the 1000 nodes, so its own
        // NPS is far below the rest's — which is the reason for reporting them separately.
        var dominant = new PositionResult(FEN_DOMINANT, 870, 900);
        var middle = new PositionResult(FEN_MIDDLE, 100, 80);
        var small = new PositionResult(FEN_SMALL, 30, 20);

        var result = resultOf(List.of(dominant, middle, small), 1_000);

        assertEquals(130, result.nodesWithoutLargestPosition(), "nodes over the remaining positions");
        assertEquals(1_300, result.npsWithoutLargestPosition(), "NPS over the remaining 130 nodes in 100 ms");
        assertEquals(1_000, result.nps(), "NPS over the whole run, for contrast");
    }

    @Test
    void reducedNpsIsZeroRatherThanUndefinedWhenTheLargestPositionTookTheWholeRun() {
        var only = new PositionResult(FEN_DOMINANT, 500, 700);

        var result = resultOf(List.of(only), 700);

        assertEquals(0, result.nodesWithoutLargestPosition(), "nodes outside a one-position run");
        assertEquals(0, result.npsWithoutLargestPosition(),
                "NPS must be 0 rather than a division by zero when no time remains outside it");
    }

    @Test
    void aSingletonRunIsEntirelyItsOwnLargestPosition() {
        var only = new PositionResult(FEN_DOMINANT, 4_711, 42);

        var result = resultOf(List.of(only), 42);

        assertSame(only, result.largestPosition(), "largest position of a one-position run");
        assertEquals(100.0, result.largestPositionShare(), SHARE_TOLERANCE,
                "share when there is nothing else in the run");
    }

    @Test
    void aRunThatVisitedNoNodesReportsZeroShareInsteadOfDividingByZero() {
        var empty = new PositionResult(FEN_SMALL, 0, 0);

        var result = resultOf(List.of(empty), 0);

        assertEquals(0, result.totalNodes(), "total nodes when nothing was searched");
        assertEquals(0.0, result.largestPositionShare(), SHARE_TOLERANCE,
                "share must be 0 rather than NaN when the total is 0");
    }

    @Test
    void anEmptyRunIsABrokenSuiteAndSaysSo() {
        var result = resultOf(List.of(), 0);

        var thrown = assertThrows(BenchException.class, result::largestPosition,
                "a run with no positions means a broken suite resource");

        assertEquals("benchmark run contains no positions", thrown.getMessage(),
                "exception message naming the cause");

        // Every accessor that depends on the largest position must fail the same way. The
        // share accessor is the one that could silently return 0 instead, since an empty run
        // also has a zero total.
        assertThrows(BenchException.class, result::largestPositionShare,
                "share of an empty run");
        assertThrows(BenchException.class, result::nodesWithoutLargestPosition,
                "reduced node count of an empty run");
        assertThrows(BenchException.class, result::npsWithoutLargestPosition,
                "reduced NPS of an empty run");
    }

    @Test
    void npsIsZeroRatherThanUndefinedWhenNoTimeElapsed() {
        var result = resultOf(List.of(new PositionResult(FEN_SMALL, 1_000, 0)), 0);

        assertEquals(0, result.nps(), "NPS with a zero elapsed time");
    }
}
