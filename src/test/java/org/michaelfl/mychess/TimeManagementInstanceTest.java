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
 * and not by the speed of the machine. The soft timeout is set seconds away so the few milliseconds
 * the test itself takes cannot change an outcome.
 *
 * @author Michael Fleischhauer
 */
class TimeManagementInstanceTest {

    private static final long SOFT_LIMIT_MS = 1_000;

    /** Hard limit with a clock: soft limit plus the maximum overrun. */
    private static final long HARD_LIMIT_MS = SOFT_LIMIT_MS + TimeManagement.SOFT_LIMIT_MAX_OVERRUN_MS;

    /** Duration reported for a single iteration; large against the few milliseconds the test itself takes. */
    private static final long ITERATION_MS = 500;

    /** Tolerance for the milliseconds that pass between the start decision and the reported end. */
    private static final long CLOCK_TOLERANCE_MS = 50;

    @BeforeEach
    void enableHeuristic() {
        IterationTimings.reset();
        IterationTimings.setSkipHeuristicEnabled(true);

        // plain comparison of estimate and remaining time, so the sample values below read directly
        TimeManagement.setRiskFactor(1.0);
    }

    @AfterEach
    void restoreSuiteDefault() {
        IterationTimings.setSkipHeuristicEnabled(false);
        IterationTimings.reset();
        TimeManagement.setRiskFactor(TimeManagement.DEFAULT_RISK_FACTOR);
    }

    @Test
    void depthOne_alwaysRuns() {
        var instance = instanceWithSoftLimit(-1);

        assertFalse(instance.shouldSkipIteration(1), "depth 1 must run even when the soft limit has passed");
        assertEquals(IterationDecision.RUN, instance.getDecisionOnSoftTimeout(),
                "depth 1 must be recorded as a regular run");
    }

    @Test
    void estimateWithinSoftLimit_runs() {
        recordSamples(2, 10);
        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);
        completeDepthOne(instance);

        assertFalse(instance.shouldSkipIteration(2), "a 10 ms estimate fits into a 1 s soft limit and must run");
        assertEquals(IterationDecision.RUN, instance.getDecisionOnSoftTimeout(), "the soft limit must say RUN");
    }

    @Test
    void estimateBeyondSoftLimit_isSkipped() {
        recordSamples(2, 2_000);
        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);
        completeDepthOne(instance);

        assertTrue(instance.shouldSkipIteration(2),
                "a 2 s estimate exceeds the 1 s soft limit and must skip");
        assertEquals(IterationDecision.SKIP, instance.getDecisionOnSoftTimeout(), "the soft limit must say SKIP");
    }

    @Test
    void notEnoughSamples_runsEvenBeyondTheSoftLimit() {
        IterationTimings.recordCompletion(2, 2_000);
        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);
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

        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);
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

        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);
        completeDepthOne(instance);

        assertTrue(instance.shouldSkipIteration(2), "a 1.2 s estimate exceeds the 1 s soft limit and must skip");
        assertTrue(IterationTimings.isProbingDue(2, 1_200, SOFT_LIMIT_MS),
                "the skip must be recorded, so the next decision for this depth is a probe");
    }

    @Test
    void softLimitPassed_skipsFromDepthThreeOn() {
        var instance = instanceWithSoftLimit(-1);
        completeDepthOne(instance);

        assertFalse(instance.shouldSkipIteration(2), "depth 2 must still run with the soft limit passed");
        completeIteration(instance, 2);

        assertTrue(instance.shouldSkipIteration(3),
                "from depth 3 on, a passed soft limit (30 ms or less remaining) must skip without an estimate");
    }

    @Test
    void stopBecauseTheLimitIsReached_isNotCountedForTheNextProbe() {
        recordSamples(3, 1_200);

        for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES - 1; i++) {
            IterationTimings.recordSkip(3);
        }

        var instance = instanceWithSoftLimit(-1);
        completeDepthOne(instance);
        instance.shouldSkipIteration(2);
        completeIteration(instance, 2);

        assertTrue(instance.shouldSkipIteration(3), "with the soft limit passed, depth 3 must stop");
        assertFalse(IterationTimings.isProbingDue(3, 1_200, SOFT_LIMIT_MS),
                "a stop because the time is up says nothing about the estimate and must not bring the next probe closer");
    }

    @Test
    void iterationDone_recordsTheDurationSinceTheStartDecision() {
        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);

        instance.shouldSkipIteration(1);
        instance.iterationDone(1, System.currentTimeMillis() + ITERATION_MS);

        assertDurationRecorded(1, ITERATION_MS, "a completed iteration must be recorded with its real duration");
    }

    @Test
    void iterationAborted_whenHardEqualsSoft_recordsTheExtrapolatedDuration() {
        // no clock (go movetime / go depth): hard limit == soft limit, the abort lands there
        var instance = instanceWithSoftLimit(ITERATION_MS);

        instance.shouldSkipIteration(1);
        instance.iterationAborted(1, System.currentTimeMillis() + ITERATION_MS);

        assertDurationRecorded(1, extrapolated(ITERATION_MS),
                "an abort at a hard limit equal to the soft limit must be recorded, extrapolated");
    }

    @Test
    void iterationAborted_atTheOverrunCap_isRecorded() {
        // with a clock the hard limit is soft + the max overrun; the abort lands there
        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);

        instance.shouldSkipIteration(1);
        instance.iterationAborted(1, System.currentTimeMillis() + HARD_LIMIT_MS);

        assertDurationRecorded(1, extrapolated(HARD_LIMIT_MS),
                "an abort at the overrun cap must be recorded like every other abort (honest estimates)");
    }

    @Test
    void shouldSkipIteration_withRiskFactorTwo_startsADepthWhoseEstimateIsTwiceTheRemainingTime() {
        for (int i = 0; i < EngineTuning.MIN_SAMPLES_FOR_SKIP; i++) {
            IterationTimings.recordCompletion(2, 2 * SOFT_LIMIT_MS);
        }

        var plain = instanceWithSoftLimit(SOFT_LIMIT_MS);
        completeDepthOne(plain);
        boolean plainSkips = plain.shouldSkipIteration(2);

        TimeManagement.setRiskFactor(2.5);
        var bold = instanceWithSoftLimit(SOFT_LIMIT_MS);
        completeDepthOne(bold);

        assertTrue(plainSkips, "with factor 1.0 an estimate of twice the soft limit must be skipped");
        assertFalse(bold.shouldSkipIteration(2), "with factor 2.5 the same estimate fits and the depth must start");
    }

    @Test
    void iterationAborted_doesNotAdvanceTheDepth() {
        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);
        completeDepthOne(instance);

        instance.shouldSkipIteration(2);
        instance.iterationAborted(2, System.currentTimeMillis());

        assertEquals(2, instance.getDepth(), "an aborted iteration must not count as completed");
    }

    /** Instance whose soft timeout lies the given milliseconds from now (negative: already passed). */
    private static TimeManagement.TimeManagementInstance instanceWithSoftLimit(long softMs) {
        return TimeManagement.newInstance(System.currentTimeMillis() + softMs, "");
    }

    /** Runs the decision for depth 1 and reports it as completed, so the instance expects depth 2 next. */
    private static void completeDepthOne(TimeManagement.TimeManagementInstance instance) {
        instance.shouldSkipIteration(1);
        completeIteration(instance, 1);
    }

    /** Reports {@code depth} as completed now. */
    private static void completeIteration(TimeManagement.TimeManagementInstance instance, int depth) {
        instance.iterationDone(depth, System.currentTimeMillis());
    }

    /** The duration {@link IterationTimings#recordAbort} records for an abort after {@code elapsedMs}. */
    private static long extrapolated(long elapsedMs) {
        return (long) (elapsedMs * EngineTuning.ABORT_EXTRAPOLATION_FACTOR);
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
