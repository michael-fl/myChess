package org.michaelfl.mychess.engines;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.Random;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link PositionSearch#chooseCandidate}. Draws use a seeded {@link Random}, so the
 * frequencies are reproducible.
 *
 * @author Michael Fleischhauer
 */
class ChooseCandidateTest {

    private static final int DRAWS = 100_000;

    /** Allowed deviation of a frequency. About five standard deviations. */
    private static final double MARGIN = 0.007;

    @Test
    void nearAndFarCandidates_areSplit80To20_uniformWithinEachGroup() {
        // near: 0, 5, 15 cp. far: 20, 50 cp.
        var candidates = candidates(1.00f, 0.95f, 0.85f, 0.80f, 0.50f);

        double[] frequencies = frequencies(candidates, 60, 20);

        for (int i = 0; i < 3; i++) {
            assertEquals(0.8 / 3, frequencies[i], MARGIN, "near candidate " + i + " must get a third of 80 %");
        }
        for (int i = 3; i < 5; i++) {
            assertEquals(0.2 / 2, frequencies[i], MARGIN, "far candidate " + i + " must get half of 20 %");
        }
    }

    @Test
    void candidateAtTheNearBound_belongsToTheFarGroup() {
        // 20 cp equals the near bound. So it is far.
        var candidates = candidates(1.00f, 0.80f);

        double[] frequencies = frequencies(candidates, 60, 20);

        assertEquals(0.8, frequencies[0], MARGIN, "the best move alone is the near group");
        assertEquals(0.2, frequencies[1], MARGIN, "a candidate exactly at the near bound is far");
    }

    @Test
    void toleranceAtOrBelowTheNearBound_makesEveryCandidateEquallyLikely() {
        var candidates = candidates(1.00f, 0.95f, 0.88f);

        for (int tolerance : new int[] {20, 15}) {
            double[] frequencies = frequencies(candidates, tolerance, 20);

            for (int i = 0; i < candidates.length; i++) {
                assertEquals(1.0 / 3, frequencies[i], MARGIN,
                        "tolerance " + tolerance + ": candidate " + i + " must get a third");
            }
        }
    }

    @Test
    void emptyFarGroup_alwaysPlaysANearCandidate() {
        var candidates = candidates(1.00f, 0.90f);

        double[] frequencies = frequencies(candidates, 100, 20);

        assertEquals(0.5, frequencies[0], MARGIN, "without far candidates the 20 % must not be lost");
        assertEquals(0.5, frequencies[1], MARGIN, "without far candidates the 20 % must not be lost");
    }

    @Test
    void candidateBeyondTheTolerance_failsTheAssertion() {
        // 70 cp is beyond the tolerance of 60
        var candidates = candidates(1.00f, 0.50f, 0.30f);

        assertThrows(AssertionError.class, () -> PositionSearch.chooseCandidate(candidates, 60, 20, new Random(1)),
                "a candidate beyond the tolerance must fail the assertion");
    }

    @Test
    void candidateBetterThanTheFirst_failsTheAssertion() {
        // the first candidate must be the best move
        var candidates = candidates(1.00f, 1.10f);

        assertThrows(AssertionError.class, () -> PositionSearch.chooseCandidate(candidates, 60, 20, new Random(1)),
                "a candidate better than the first must fail the assertion");
    }

    @Test
    void nearBoundZero_keepsTheBestMoveAndItsTiesInTheNearGroup() {
        var candidates = candidates(1.00f, 1.00f, 0.90f);

        double[] frequencies = frequencies(candidates, 60, 0);

        assertEquals(0.4, frequencies[0], MARGIN, "the best move must stay near with a near bound of 0");
        assertEquals(0.4, frequencies[1], MARGIN, "a tie with the best move must stay near with a near bound of 0");
        assertEquals(0.2, frequencies[2], MARGIN, "every other candidate is far");
    }

    @Test
    void singleCandidate_isAlwaysPlayed() {
        var candidates = candidates(-3.25f);
        var random = new Random(1);

        for (int i = 0; i < 100; i++) {
            assertSame(candidates[0], PositionSearch.chooseCandidate(candidates, 60, 20, random),
                    "the only candidate must always be played");
        }
    }

    @Test
    void countNear_countsTheCandidatesBelowTheNearBound() {
        var candidates = candidates(1.00f, 0.95f, 0.85f, 0.80f, 0.50f);

        assertEquals(3, PositionSearch.countNear(candidates, 20), "distances 0, 5 and 15 are below 20");
        assertEquals(1, PositionSearch.countNear(candidates, 0), "a bound of 0 must still count the best move");
        assertEquals(5, PositionSearch.countNear(candidates, 60), "every candidate is below 60");
    }

    @Test
    void farFilter_blockedFarCandidate_isNeverPlayed_theOtherTakesTheFarShare() {
        // near: 1. far: 2 and 3. The filter blocks move 2.
        var candidates = candidates(1.00f, 0.70f, 0.60f);

        double[] frequencies = frequencies(candidates, 60, 20, move -> move != 2);

        assertEquals(0.8, frequencies[0], MARGIN, "the near share must not change");
        assertEquals(0.0, frequencies[1], 0.0, "a blocked far candidate must never be played");
        assertEquals(0.2, frequencies[2], MARGIN, "the remaining far candidate must take the whole far share");
    }

    @Test
    void farFilter_allFarCandidatesBlocked_playsANearCandidate() {
        var candidates = candidates(1.00f, 0.95f, 0.70f, 0.60f);

        double[] frequencies = frequencies(candidates, 60, 20, move -> move <= 2);

        assertEquals(0.5, frequencies[0], MARGIN, "with every far candidate blocked the near group gets everything");
        assertEquals(0.5, frequencies[1], MARGIN, "with every far candidate blocked the near group gets everything");
        assertEquals(0.0, frequencies[2] + frequencies[3], 0.0, "no blocked far candidate may be played");
    }

    @Test
    void farFilter_nearCandidates_areNeverFiltered() {
        // the filter rejects everything. Near candidates are still played.
        var candidates = candidates(1.00f, 0.95f);

        double[] frequencies = frequencies(candidates, 60, 20, move -> false);

        assertEquals(0.5, frequencies[0], MARGIN, "the near group is never filtered");
        assertEquals(0.5, frequencies[1], MARGIN, "the near group is never filtered");
    }

    /** Candidates with the given weights in pawns, best first, moves numbered from 1. */
    private static MoveAndWeight[] candidates(float... weights) {
        var candidates = new MoveAndWeight[weights.length];

        for (int i = 0; i < weights.length; i++) {
            candidates[i] = new MoveAndWeight(i + 1, weights[i], GameResult.ONGOING, new int[0], weights.length);
        }

        return candidates;
    }

    private static double[] frequencies(MoveAndWeight[] candidates, int toleranceCenti, int nearCenti) {
        return frequencies(candidates, toleranceCenti, nearCenti, move -> true);
    }

    private static double[] frequencies(MoveAndWeight[] candidates, int toleranceCenti, int nearCenti,
                                        IntPredicate farAllowed) {
        var random = new Random(20261101L);
        var counts = new int[candidates.length];

        for (int i = 0; i < DRAWS; i++) {
            counts[PositionSearch.chooseCandidate(candidates, toleranceCenti, nearCenti, random, farAllowed).move() - 1]++;
        }

        var frequencies = new double[candidates.length];

        for (int i = 0; i < candidates.length; i++) {
            frequencies[i] = counts[i] / (double) DRAWS;
        }

        return frequencies;
    }
}
