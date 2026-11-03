package org.michaelfl.mychess.engines;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.WeightingFunction;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Unit tests for {@link PositionSearch#candidateIndices}.
 *
 * @author Michael Fleischhauer
 */
class CandidateIndicesTest {

    /** A beta so high that every score counts as exact. */
    private static final int OPEN = 100_000;

    @Test
    void winnerIsHighest_comesFirst() {
        int[] weights = {10, 50, 30};

        assertArrayEquals(new int[] {1, 0, 2}, PositionSearch.candidateIndices(weights, open(3), 1, 60));
    }

    @Test
    void repairLoweredTheWinner_theHighestMoveComesFirst() {
        // the winner (index 1) fell to 42 after the PV repair. Index 2 is rated higher now.
        int[] weights = {10, 42, 50};

        assertArrayEquals(new int[] {2, 0, 1}, PositionSearch.candidateIndices(weights, open(3), 1, 60));
    }

    @Test
    void candidatesBeyondTheTolerance_areLeftOut() {
        int[] weights = {-20, 50, 40};

        assertArrayEquals(new int[] {1, 2}, PositionSearch.candidateIndices(weights, open(3), 1, 60));
    }

    @Test
    void failedLowMoves_areLeftOut() {
        // index 0 failed low: its score is only an upper bound
        int[] weights = {45, 50};
        int[] betaUsed = {-45, OPEN};

        assertArrayEquals(new int[] {1}, PositionSearch.candidateIndices(weights, betaUsed, 1, 60));
    }

    @Test
    void illegalMoves_areLeftOut() {
        int[] weights = {WeightingFunction.ILLEGAL_WEIGHT_NEG, 50};

        assertArrayEquals(new int[] {1}, PositionSearch.candidateIndices(weights, open(2), 1, 60));
    }

    private static int[] open(int count) {
        int[] betaUsed = new int[count];
        Arrays.fill(betaUsed, OPEN);

        return betaUsed;
    }
}
