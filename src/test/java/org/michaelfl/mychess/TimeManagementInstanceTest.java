package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.IterationTimings.IterationDecision;
import org.michaelfl.mychess.engines.EngineTuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the per-iteration start decision of {@link TimeManagement.TimeManagementInstance}, with the
 * skip heuristic of {@link IterationTimings} switched on.
 *
 * <p>The heuristic is switched off for the whole suite by {@link DisableSkipHeuristicExtension}, because
 * timing-dependent decisions would make search tests flaky. These tests turn it on for themselves and
 * feed {@link IterationTimings} with chosen samples, so every decision is determined by the test data
 * and not by the speed of the machine. The timeouts are set far apart (seconds) so the few milliseconds
 * the test itself takes cannot change an outcome.
 *
 * <p>Not covered yet: the extension to the hard limit. It depends on
 * {@code isDifficultInternal()}, which still always answers {@code false}.
 *
 * @author Michael Fleischhauer
 */
class TimeManagementInstanceTest {

    private static final long SOFT_LIMIT_MS = 1_000;
    private static final long HARD_LIMIT_MS = 10_000;

    /** First depth that may be extended to the hard limit: one above the minimum depth 4. */
    private static final int FIRST_EXTENDABLE_DEPTH = 5;

    /** Duration reported for a single iteration; large against the few milliseconds the test itself takes. */
    private static final long ITERATION_MS = 500;

    /** Tolerance for the milliseconds that pass between the start decision and the reported end. */
    private static final long CLOCK_TOLERANCE_MS = 50;

    /** Two distinct non-zero move codes; the bookkeeping only compares them. */
    private static final int MOVE_A = 1;
    private static final int MOVE_B = 2;

    @BeforeEach
    void enableHeuristic() {
        IterationTimings.reset();
        IterationTimings.setSkipHeuristicEnabled(true);
    }

    @AfterEach
    void restoreSuiteDefault() {
        IterationTimings.setSkipHeuristicEnabled(false);
        IterationTimings.reset();
    }

    @Test
    void depthOne_alwaysRuns() {
        var instance = instanceWithLimits(-1, -1);

        assertFalse(instance.shouldSkipIteration(1), "depth 1 must run even when both limits have passed");
        assertEquals(IterationDecision.RUN, instance.getDecisionOnSoftTimeout(),
                "depth 1 must be recorded as a regular run");
    }

    @Test
    void estimateWithinSoftLimit_runs() {
        recordSamples(2, 10);
        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);
        completeDepthOne(instance);

        assertFalse(instance.shouldSkipIteration(2), "a 10 ms estimate fits into a 1 s soft limit and must run");
        assertEquals(IterationDecision.RUN, instance.getDecisionOnSoftTimeout(), "the soft limit must say RUN");
    }

    @Test
    void estimateBeyondSoftLimit_isSkippedWhenThePositionIsNotDifficult() {
        recordSamples(2, 2_000);
        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);
        completeDepthOne(instance);

        assertTrue(instance.shouldSkipIteration(2),
                "a 2 s estimate exceeds the 1 s soft limit; without a difficulty signal the hard limit must not be used");
        assertEquals(IterationDecision.SKIP, instance.getDecisionOnSoftTimeout(), "the soft limit must say SKIP");
    }

    @Test
    void estimateBeyondSoftLimit_isSkippedFromTheDepthTheExtensionIsAllowedAt() {
        recordSamples(FIRST_EXTENDABLE_DEPTH, 2_000);
        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);
        completeDepthOne(instance);

        for (int depth = 2; depth < FIRST_EXTENDABLE_DEPTH; depth++) {
            instance.shouldSkipIteration(depth);
            completeIteration(instance, depth, MOVE_A);
        }

        assertTrue(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH),
                "at depth 5 extensions are allowed, but a position that is not difficult must still be held "
                        + "to the soft limit: a 2 s estimate against 1 s left must skip");
    }

    @Test
    void notEnoughSamples_runsEvenBeyondTheSoftLimit() {
        IterationTimings.recordCompletion(2, 2_000);
        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);
        completeDepthOne(instance);

        assertFalse(instance.shouldSkipIteration(2),
                "with a single sample the estimate is not trusted yet, so the iteration must run");
    }

    @Test
    void afterEnoughConsecutiveSkips_aProbeRunsTheIteration() {
        recordSamples(2, 1_200);

        for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES; i++) {
            IterationTimings.recordSkip(2);
        }

        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);
        completeDepthOne(instance);

        assertFalse(instance.shouldSkipIteration(2),
                "after " + EngineTuning.SKIPS_BETWEEN_PROBES + " skips in a row a probe must run the iteration "
                        + "(1 s remaining is at least 70 % of the 1.2 s estimate)");
        assertEquals(IterationDecision.PROBE, instance.getDecisionOnSoftTimeout(), "the soft limit must say PROBE");
    }

    @Test
    void skip_isCountedForTheNextProbe() {
        recordSamples(2, 1_200);

        for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES - 1; i++) {
            IterationTimings.recordSkip(2);
        }

        assertFalse(IterationTimings.isProbingDue(2, 1_200, SOFT_LIMIT_MS),
                "test premise: one skip short of the threshold, no probe is due yet");

        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);
        completeDepthOne(instance);

        assertTrue(instance.shouldSkipIteration(2), "a 1.2 s estimate exceeds the 1 s soft limit and must skip");
        assertTrue(IterationTimings.isProbingDue(2, 1_200, SOFT_LIMIT_MS),
                "the skip must be recorded, so the next decision for this depth is a probe");
    }

    @Test
    void softLimitPassed_skipsFromDepthThreeOn() {
        var instance = instanceWithLimits(-1, HARD_LIMIT_MS);
        completeDepthOne(instance);

        assertFalse(instance.shouldSkipIteration(2), "depth 2 must still run with the soft limit passed");
        completeIteration(instance, 2, MOVE_A);

        assertTrue(instance.shouldSkipIteration(3),
                "from depth 3 on, a passed soft limit (30 ms or less remaining) must skip without an estimate");
    }

    @Test
    void stopBecauseTheLimitIsReached_isNotCountedForTheNextProbe() {
        recordSamples(3, 1_200);

        for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES - 1; i++) {
            IterationTimings.recordSkip(3);
        }

        var instance = instanceWithLimits(-1, HARD_LIMIT_MS);
        completeDepthOne(instance);
        instance.shouldSkipIteration(2);
        completeIteration(instance, 2, MOVE_A);

        assertTrue(instance.shouldSkipIteration(3), "with the soft limit passed, depth 3 must stop");
        assertFalse(IterationTimings.isProbingDue(3, 1_200, SOFT_LIMIT_MS),
                "a stop because the time is up says nothing about the estimate and must not bring the next probe closer");
    }

    @Test
    void iterationDone_recordsTheDurationSinceTheStartDecision() {
        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);

        instance.shouldSkipIteration(1);
        instance.iterationDone(1, MOVE_A, 0, System.currentTimeMillis() + ITERATION_MS);

        assertDurationRecorded(1, ITERATION_MS, "a completed iteration must be recorded with its real duration");
    }

    @Test
    void iterationAborted_recordsTheExtrapolatedDuration() {
        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);

        instance.shouldSkipIteration(1);
        instance.iterationAborted(1, System.currentTimeMillis() + ITERATION_MS);

        long extrapolatedMs = (long) (ITERATION_MS * EngineTuning.ABORT_EXTRAPOLATION_FACTOR);

        assertDurationRecorded(1, extrapolatedMs,
                "an aborted iteration must be recorded as its elapsed time times the extrapolation factor");
    }

    @Test
    void iterationAborted_doesNotAdvanceTheDepth() {
        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);
        completeDepthOne(instance);

        instance.shouldSkipIteration(2);
        instance.iterationAborted(2, System.currentTimeMillis());

        assertEquals(2, instance.getDepth(), "an aborted iteration must not count as completed");
        assertEquals(MOVE_A, instance.getPreviousIterationBestMove(),
                "an aborted iteration must keep the best move of the last completed one");
    }

    @Test
    void ranIterationWithoutSkipDecision_isNotCountedAsExtension() {
        recordSamples(2, 10);
        var instance = instanceWithLimits(SOFT_LIMIT_MS, HARD_LIMIT_MS);
        completeDepthOne(instance);

        instance.shouldSkipIteration(2);
        completeIteration(instance, 2, MOVE_B);

        assertEquals(0, instance.getExtraIterationsUsedCounter(),
                "an iteration the soft limit allowed must not count as an extension");
    }

    /** Instance whose soft and hard timeout lie the given milliseconds from now (negative: already passed). */
    private static TimeManagement.TimeManagementInstance instanceWithLimits(long softMs, long hardMs) {
        long now = System.currentTimeMillis();

        return TimeManagement.newInstance(now + softMs, now + hardMs, null, "");
    }

    /** Runs the decision for depth 1 and reports it as completed, so the instance expects depth 2 next. */
    private static void completeDepthOne(TimeManagement.TimeManagementInstance instance) {
        instance.shouldSkipIteration(1);
        completeIteration(instance, 1, MOVE_A);
    }

    /** Reports {@code depth} as completed with best move {@code move}, and a zero score. */
    private static void completeIteration(TimeManagement.TimeManagementInstance instance, int depth, int move) {
        instance.iterationDone(depth, move, 0, System.currentTimeMillis());
    }

    /** Asserts that the estimate for {@code depth} lies within {@link #CLOCK_TOLERANCE_MS} above {@code expectedMs}. */
    private static void assertDurationRecorded(int depth, long expectedMs, String message) {
        long estimateMs = IterationTimings.getEstimatedMs(depth);

        assertTrue(estimateMs >= expectedMs - CLOCK_TOLERANCE_MS && estimateMs <= expectedMs + CLOCK_TOLERANCE_MS,
                message + ": expected about " + expectedMs + " ms, but the estimate is " + estimateMs + " ms");
    }

    /** Records two completed iterations of {@code millis} at {@code depth}, enough for a trusted estimate. */
    private static void recordSamples(int depth, long millis) {
        IterationTimings.recordCompletion(depth, millis);
        IterationTimings.recordCompletion(depth, millis);
    }
}
