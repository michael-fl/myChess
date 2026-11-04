package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.IterationTimings.IterationDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the easy-move stop of {@link TimeManagement.TimeManagementInstance} (owner's rule, three
 * iterations): with a clock, the search does not start a depth of at least
 * {@link TimeManagement#EASY_MOVE_MIN_START_DEPTH} when the last
 * {@link TimeManagement#EASY_MOVE_STABLE_ITERATIONS} completed iterations found the same best move.
 *
 * <p>The skip heuristic of {@link IterationTimings} stays off, as in the whole suite, and the soft
 * limit lies far ahead. So every stop seen here comes from the easy-move rule alone.
 *
 * @author Michael Fleischhauer
 */
class EasyMoveStopTest {

    private static final long SOFT_LIMIT_MS = 10_000;

    private static final int MOVE_A = 1;
    private static final int MOVE_B = 2;

    @AfterEach
    void restoreDefault() {
        TimeManagement.setEasyMoveStopEnabled(TimeManagement.DEFAULT_EASY_MOVE_STOP);
    }

    @Test
    void sameMoveAtDepthsFourToSix_stopsBeforeDepthSeven() {
        var instance = searchWith(true, MOVE_B, MOVE_B, MOVE_B, MOVE_A, MOVE_A, MOVE_A);

        assertTrue(instance.shouldSkipIteration(7), "depths 4, 5 and 6 agree, so depth 7 must not start");
        assertEquals(IterationDecision.SKIP, instance.getDecisionOnSoftTimeout(), "the stop must be recorded as a skip");
    }

    @Test
    void sameMoveAtDepthsFiveAndSixOnly_doesNotStopBeforeDepthSeven() {
        var instance = searchWith(true, MOVE_B, MOVE_B, MOVE_B, MOVE_B, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(7), "two agreeing iterations are not enough, depth 7 must start");
    }

    @Test
    void sameMoveAtDepthsThreeToFive_doesNotStopBeforeDepthSix() {
        var instance = searchWith(true, MOVE_B, MOVE_B, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(6), "depth 6 is below the minimum and must start");
    }

    @Test
    void moveChangedAtDepthSix_doesNotStop() {
        var instance = searchWith(true, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_B);

        assertFalse(instance.shouldSkipIteration(7), "a new best move at depth 6 must let depth 7 start");
    }

    @Test
    void moveStableAgainLater_stopsOnceThreeIterationsAgree() {
        var instance = searchWith(true, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_B, MOVE_A, MOVE_A, MOVE_A);

        assertTrue(instance.shouldSkipIteration(10), "depths 7, 8 and 9 agree, so depth 10 must not start");
    }

    @Test
    void withoutAClock_doesNotStop() {
        var instance = searchWith(false, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(7), "go depth, go infinite and the bench must search their full depth");
    }

    @Test
    void switchedOff_doesNotStop() {
        TimeManagement.setEasyMoveStopEnabled(false);
        var instance = searchWith(true, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(7), "with the option off a stable move must not stop the search");
    }

    @Test
    void defaultInstance_hasNoClock() {
        var instance = TimeManagement.newInstance(System.currentTimeMillis() + SOFT_LIMIT_MS, "");
        completeAll(instance, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(7), "the instance without the clock argument must not use the stop");
    }

    /** A search that completed one depth per given best move, starting at depth 1. */
    private static TimeManagement.TimeManagementInstance searchWith(boolean clock, int... bestMoves) {
        var instance = TimeManagement.newInstance(System.currentTimeMillis() + SOFT_LIMIT_MS, clock, "");
        completeAll(instance, bestMoves);

        return instance;
    }

    private static void completeAll(TimeManagement.TimeManagementInstance instance, int... bestMoves) {
        for (int i = 0; i < bestMoves.length; i++) {
            int depth = i + 1;

            assertFalse(instance.shouldSkipIteration(depth), "depth " + depth + " must start");
            instance.iterationDone(depth, System.currentTimeMillis());
            instance.bestMoveFound(bestMoves[i]);
        }
    }
}
