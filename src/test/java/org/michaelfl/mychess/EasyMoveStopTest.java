package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.IterationTimings.IterationDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the easy-move stop of {@link TimeManagement.TimeManagementInstance}. With a clock, a move
 * is easy when its best move was the same in the last two completed iterations, the last of them at
 * least {@link TimeManagement#EASY_MOVE_MIN_DEPTH} deep, without a mate score. For an easy move the
 * start decision compares against {@link TimeManagement#EASY_MOVE_SOFT_LIMIT_PERCENT} of the soft limit.
 *
 * <p>The skip heuristic is switched on here, as in {@link TimeManagementInstanceTest}, and fed with
 * chosen samples. The next depth is estimated at {@link #NEXT_DEPTH_MS}. That fits the regular soft
 * limit of {@link #SOFT_LIMIT_MS}, but not its easy share. So a stop seen here comes from the easy rule.
 *
 * @author Michael Fleischhauer
 */
class EasyMoveStopTest {

    private static final long SOFT_LIMIT_MS = 4_000;

    /** Estimate of the next depth: below the soft limit, above its easy share of 1,400 ms. */
    private static final long NEXT_DEPTH_MS = 2_000;

    /** Estimate of a next depth that fits even the easy share of the soft limit. */
    private static final long CHEAP_DEPTH_MS = 100;

    private static final int MOVE_A = 1;
    private static final int MOVE_B = 2;

    @BeforeEach
    void enableHeuristic() {
        IterationTimings.reset();
        IterationTimings.setSkipHeuristicEnabled(true);
        TimeManagement.setRiskFactor(1.0);
    }

    @AfterEach
    void restoreSuiteDefault() {
        IterationTimings.setSkipHeuristicEnabled(false);
        IterationTimings.reset();
        TimeManagement.setRiskFactor(TimeManagement.DEFAULT_RISK_FACTOR);
        TimeManagement.setEasyMoveStopEnabled(TimeManagement.DEFAULT_EASY_MOVE_STOP);
    }

    @Test
    void stableMoveAtTheMinimumDepth_stopsAtTheEasyLimit() {
        var instance = searchWith(true, MOVE_A, MOVE_A, MOVE_A, MOVE_A);

        assertTrue(instance.shouldSkipIteration(5), "a stable move must not start a depth that exceeds the easy limit");
        assertEquals(IterationDecision.SKIP, instance.getDecisionOnSoftTimeout(), "the stop must be recorded as a skip");
    }

    @Test
    void stableMove_stillStartsADepthThatFitsTheEasyLimit() {
        var instance = searchWith(true, CHEAP_DEPTH_MS, MOVE_A, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(5), "a cheap depth fits the easy limit and must start");
    }

    @Test
    void stableMoveBelowTheMinimumDepth_usesTheRegularLimit() {
        var instance = searchWith(true, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(4), "a move stable up to depth 3 only must not use the easy limit");
    }

    @Test
    void moveChangedInTheLastIteration_usesTheRegularLimit() {
        var instance = searchWith(true, MOVE_A, MOVE_A, MOVE_A, MOVE_B);

        assertFalse(instance.shouldSkipIteration(5), "a new best move at depth 4 must not use the easy limit");
    }

    @Test
    void moveStableAgainAfterAChange_stopsOnceTwoIterationsAgree() {
        var instance = searchWith(true, MOVE_A, MOVE_A, MOVE_A, MOVE_B, MOVE_B);

        assertTrue(instance.shouldSkipIteration(6), "the new move at depths 4 and 5 must make the move easy");
    }

    @Test
    void mateScore_usesTheRegularLimit() {
        var instance = searchWith(true, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(4));
        instance.iterationDone(4, System.currentTimeMillis());
        instance.bestMoveFound(MOVE_A, true);
        recordSamples(5, NEXT_DEPTH_MS);

        assertFalse(instance.shouldSkipIteration(5), "a mate score at depth 4 must not make the move easy");
    }

    @Test
    void withoutAClock_usesTheRegularLimit() {
        var instance = searchWith(false, MOVE_A, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(5), "go depth, go infinite and the bench must not use the easy limit");
    }

    @Test
    void switchedOff_usesTheRegularLimit() {
        TimeManagement.setEasyMoveStopEnabled(false);
        var instance = searchWith(true, MOVE_A, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(5), "with the option off a stable move must not use the easy limit");
    }

    @Test
    void defaultInstance_hasNoClock() {
        var instance = TimeManagement.newInstance(System.currentTimeMillis() + SOFT_LIMIT_MS, "");
        completeAll(instance, MOVE_A, MOVE_A, MOVE_A, MOVE_A);

        assertFalse(instance.shouldSkipIteration(5), "the instance without the clock argument must not use the easy limit");
    }

    /**
     * A search that completed one depth per given best move, starting at depth 1, with the depth after
     * the last one estimated at {@link #NEXT_DEPTH_MS}.
     */
    private static TimeManagement.TimeManagementInstance searchWith(boolean clock, int... bestMoves) {
        return searchWith(clock, NEXT_DEPTH_MS, bestMoves);
    }

    /** As {@link #searchWith(boolean, int...)}, with the depth after the last one estimated at {@code nextDepthMs}. */
    private static TimeManagement.TimeManagementInstance searchWith(boolean clock, long nextDepthMs, int... bestMoves) {
        var instance = TimeManagement.newInstance(System.currentTimeMillis() + SOFT_LIMIT_MS, clock, "");
        completeAll(instance, bestMoves);
        recordSamples(bestMoves.length + 1, nextDepthMs);

        return instance;
    }

    private static void completeAll(TimeManagement.TimeManagementInstance instance, int... bestMoves) {
        for (int i = 0; i < bestMoves.length; i++) {
            int depth = i + 1;

            assertFalse(instance.shouldSkipIteration(depth), "depth " + depth + " must start");
            instance.iterationDone(depth, System.currentTimeMillis());
            instance.bestMoveFound(bestMoves[i], false);
        }
    }

    /** Records two completed iterations of {@code millis} at {@code depth}, enough for a trusted estimate. */
    private static void recordSamples(int depth, long millis) {
        IterationTimings.recordCompletion(depth, millis);
        IterationTimings.recordCompletion(depth, millis);
    }
}
