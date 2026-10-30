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
 * <p>The extension for a difficult position (variant A of task 7b): when the soft limit says
 * "skip" after depth D and the score of D lies at least 50 cp below the previous own move, the start
 * of D+1 is checked against twice the soft limit, the hard limit is recomputed from it, and the move
 * ends after D+1. As long as the soft limit says "run", nothing changes.
 *
 * @author Michael Fleischhauer
 */
class TimeManagementInstanceTest {

    private static final int SOFT_LIMIT_MS = 1_000;

    /** A clock large enough that its 20 % never caps the hard limit: 100 minutes. */
    private static final int LARGE_CLOCK_MS = 6_000_000;

    /** The factor the soft limit is raised by in a difficult position. */
    private static final int EXTENSION_FACTOR = 2;

    /** Score of the previous own move: three pawns. */
    private static final int PREVIOUS_OWN_SCORE_CENTI = 300;

    /** 50 cp below {@link #PREVIOUS_OWN_SCORE_CENTI}: exactly the drop that makes a position difficult. */
    private static final int DROPPED_SCORE_CENTI = 250;

    /** First depth that may be extended: one above the minimum depth 4, the first one with a signal. */
    private static final int FIRST_EXTENDABLE_DEPTH = 5;

    /** Hard limit with a clock: soft limit plus the maximum overrun. */
    private static final long HARD_LIMIT_MS = SOFT_LIMIT_MS + TimeManagement.SOFT_LIMIT_MAX_OVERRUN_MS;

    /** Duration reported for a single iteration; large against the few milliseconds the test itself takes. */
    private static final long ITERATION_MS = 500;

    /** Tolerance for the milliseconds that pass between the start decision and the reported end. */
    private static final long CLOCK_TOLERANCE_MS = 50;

    /** A non-zero move code; the bookkeeping does not look at it. */
    private static final int MOVE_A = 1;

    private int savedOverrunPercent;

    @BeforeEach
    void enableHeuristic() {
        IterationTimings.reset();
        IterationTimings.setSkipHeuristicEnabled(true);

        // plain comparison of estimate and remaining time, so the sample values below read directly
        TimeManagement.setRiskFactor(1.0);

        // other test classes change this process-wide setting; pin the shipped default
        savedOverrunPercent = TimeManagement.getOverrunPercent();
        TimeManagement.setOverrunPercent(TimeManagement.DEFAULT_OVERRUN_PERCENT);
    }

    @AfterEach
    void restoreSuiteDefault() {
        IterationTimings.setSkipHeuristicEnabled(false);
        IterationTimings.reset();
        TimeManagement.setRiskFactor(TimeManagement.DEFAULT_RISK_FACTOR);
        TimeManagement.setOverrunPercent(savedOverrunPercent);
    }

    @Test
    void depthOne_alwaysRuns() {
        var instance = instanceWithSoftLimitPassed();

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
    void estimateBeyondSoftLimit_isSkippedWhenThePositionIsNotDifficult() {
        recordSamples(2, 2_000);
        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);
        completeDepthOne(instance);

        assertTrue(instance.shouldSkipIteration(2),
                "a 2 s estimate exceeds the 1 s soft limit; without a difficulty signal the soft limit must not be extended");
        assertEquals(IterationDecision.SKIP, instance.getDecisionOnSoftTimeout(), "the soft limit must say SKIP");
    }

    @Test
    void estimateBeyondSoftLimit_isSkippedFromTheDepthTheExtensionIsAllowedAt() {
        recordSamples(FIRST_EXTENDABLE_DEPTH, 2_000);
        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);
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
        var instance = instanceWithSoftLimitPassed();
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

        var instance = instanceWithSoftLimitPassed();
        completeDepthOne(instance);
        instance.shouldSkipIteration(2);
        completeIteration(instance, 2, MOVE_A);

        assertTrue(instance.shouldSkipIteration(3), "with the soft limit passed, depth 3 must stop");
        assertFalse(IterationTimings.isProbingDue(3, 1_200, SOFT_LIMIT_MS),
                "a stop because the time is up says nothing about the estimate and must not bring the next probe closer");
    }

    @Test
    void iterationDone_recordsTheDurationSinceTheStartDecision() {
        var instance = instanceWithSoftLimit(SOFT_LIMIT_MS);

        instance.shouldSkipIteration(1);
        instance.iterationDone(1, MOVE_A, 0, System.currentTimeMillis() + ITERATION_MS);

        assertDurationRecorded(1, ITERATION_MS, "a completed iteration must be recorded with its real duration");
    }

    @Test
    void iterationAborted_whenHardEqualsSoft_recordsTheExtrapolatedDuration() {
        // no clock (go movetime / go depth): hard limit == soft limit, the abort lands there
        var instance = instanceWithoutClock((int) ITERATION_MS);

        instance.shouldSkipIteration(1);
        instance.iterationAborted(1, System.currentTimeMillis() + ITERATION_MS);

        assertDurationRecorded(1, extrapolated(ITERATION_MS),
                "an abort at a hard limit equal to the soft limit must be recorded, extrapolated");
    }

    @Test
    void iterationAborted_atTheOverrunCap_isRecorded() {
        // with a clock the hard limit lies past the soft limit; the abort lands there
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

    // ---- the extension for a difficult position ----

    @Test
    void difficultPosition_startsTheIterationTheSoftLimitWouldSkip() {
        recordSamples(FIRST_EXTENDABLE_DEPTH, 1_500);
        long start = System.currentTimeMillis();
        var instance = difficultInstance(start, LARGE_CLOCK_MS);

        assertFalse(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH),
                "a 1.5 s estimate exceeds the 1 s soft limit but fits into twice the soft limit, so a difficult "
                        + "position must start the iteration");
        assertEquals(start + TimeManagement.calculateHardLimit(EXTENSION_FACTOR * SOFT_LIMIT_MS, LARGE_CLOCK_MS),
                instance.getTimeout(), "the hard limit must be recomputed from twice the soft limit");
    }

    @Test
    void difficultPosition_isExtendedOnlyOnce_theMoveEndsAfterTheExtraIteration() {
        recordSamples(FIRST_EXTENDABLE_DEPTH, 1_500);
        recordSamples(FIRST_EXTENDABLE_DEPTH + 1, 10);
        var instance = difficultInstance(System.currentTimeMillis(), LARGE_CLOCK_MS);

        assertFalse(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH), "test premise: the extension starts depth 5");
        instance.iterationDone(FIRST_EXTENDABLE_DEPTH, MOVE_A, DROPPED_SCORE_CENTI, System.currentTimeMillis());

        assertTrue(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH + 1),
                "after the extra iteration the move must end, even with a 10 ms estimate and a still dropped score");
    }

    @Test
    void difficultPosition_estimateBeyondTwiceTheSoftLimit_isSkipped() {
        recordSamples(FIRST_EXTENDABLE_DEPTH, 3_000);
        var instance = difficultInstance(System.currentTimeMillis(), LARGE_CLOCK_MS);

        assertTrue(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH),
                "a 3 s estimate does not fit into twice the 1 s soft limit, so even a difficult position must skip");
    }

    @Test
    void difficultPosition_extensionIsCappedByTheClockShare() {
        // 6 s on the clock: 20 % of it (1.2 s) is less than twice the soft limit (2 s)
        int clockMs = 6_000;
        recordSamples(FIRST_EXTENDABLE_DEPTH, 1_500);
        long start = System.currentTimeMillis();
        var instance = difficultInstance(start, clockMs);

        assertTrue(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH),
                "with only 1.2 s allowed by the clock, a 1.5 s estimate must not start even in a difficult position");
        assertEquals(start + TimeManagement.calculateHardLimit(EXTENSION_FACTOR * SOFT_LIMIT_MS, clockMs),
                instance.getTimeout(), "test premise: the recomputed hard limit is the 20 % share of the clock");
    }

    @Test
    void difficultPosition_whileTheSoftLimitSaysRun_changesNothing() {
        recordSamples(FIRST_EXTENDABLE_DEPTH, 10);
        recordSamples(FIRST_EXTENDABLE_DEPTH + 1, 10);
        long start = System.currentTimeMillis();
        var instance = difficultInstance(start, LARGE_CLOCK_MS);

        assertFalse(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH), "a 10 ms estimate fits into the soft limit");
        assertEquals(start + TimeManagement.calculateHardLimit(SOFT_LIMIT_MS, LARGE_CLOCK_MS), instance.getTimeout(),
                "an iteration the soft limit allows must keep the normal hard limit");

        instance.iterationDone(FIRST_EXTENDABLE_DEPTH, MOVE_A, DROPPED_SCORE_CENTI, System.currentTimeMillis());

        assertFalse(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH + 1),
                "the extension must never end a search earlier than the soft limit would: depth 6 still fits");
    }

    @Test
    void notDifficult_keepsTheNormalHardLimit() {
        recordSamples(FIRST_EXTENDABLE_DEPTH, 1_500);
        long start = System.currentTimeMillis();
        var instance = instanceUpToDepthFour(start, SOFT_LIMIT_MS, LARGE_CLOCK_MS, PREVIOUS_OWN_SCORE_CENTI,
                PREVIOUS_OWN_SCORE_CENTI);

        assertTrue(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH),
                "without a drop a 1.5 s estimate against a 1 s soft limit must skip");
        assertEquals(start + TimeManagement.calculateHardLimit(SOFT_LIMIT_MS, LARGE_CLOCK_MS), instance.getTimeout(),
                "without a drop the hard limit must stay the normal one");
    }

    @Test
    void difficultPosition_withoutClock_doesNotExceedTheGrantedTime() {
        // go movetime: the GUI expects the move within the granted time, so there is no reserve to extend into
        recordSamples(FIRST_EXTENDABLE_DEPTH, 1_500);
        long start = System.currentTimeMillis();
        var instance = instanceUpToDepthFour(start, SOFT_LIMIT_MS, Integer.MAX_VALUE, PREVIOUS_OWN_SCORE_CENTI,
                DROPPED_SCORE_CENTI);

        instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH);

        assertEquals(start + SOFT_LIMIT_MS, instance.getTimeout(),
                "without a clock the hard limit must stay at the granted time, also in a difficult position");
    }

    @Test
    void difficultPosition_cappedByTheClock_doesNotStartAnIterationThatCannotFinish() {
        // 6 s on the clock: the extension is capped by 20 % of it (1.2 s). The risk factor 1.5 is meant
        // for decisions against the soft limit, where the hard limit leaves room for an overrun; against
        // a cap that IS the hard limit there is no such room. 1.7 s is more than the normal rule starts
        // (1.5 x 1 s) and more than 1.2 s, but less than 1.5 x 1.2 s.
        TimeManagement.setRiskFactor(TimeManagement.DEFAULT_RISK_FACTOR);
        recordSamples(FIRST_EXTENDABLE_DEPTH, 1_700);
        var instance = difficultInstance(System.currentTimeMillis(), 6_000);

        assertTrue(instance.shouldSkipIteration(FIRST_EXTENDABLE_DEPTH),
                "an iteration whose estimate exceeds the hard limit itself must not be started; it could only be aborted");
    }

    // ---- log lines: overrun and abort are measured against the move's soft limit ----

    @Test
    void iterationAborted_logsTheTimePastTheSoftLimitOfTheMove() {
        // started 1.5 s ago with a 1 s soft limit: an abort now lies 500 ms past the soft limit of the move
        var instance = TimeManagement.newInstance(System.currentTimeMillis() - 1_500, SOFT_LIMIT_MS, LARGE_CLOCK_MS, null, "");
        completeDepthOne(instance);
        instance.shouldSkipIteration(2);

        String log = capturedLog(() -> instance.iterationAborted(2, System.currentTimeMillis()));

        assertPastSoftLimit(log, "abort depth 2: ", 500,
                "the abort line must report the time past the move's soft limit, not past one measured from the iteration start");
    }

    @Test
    void iterationDone_afterTheSoftLimitOfTheMove_logsAnOverrun() {
        // started 1.5 s ago with a 1 s soft limit: a short depth 2 that ends now still ends 500 ms past the soft limit
        var instance = TimeManagement.newInstance(System.currentTimeMillis() - 1_500, SOFT_LIMIT_MS, LARGE_CLOCK_MS, null, "");
        completeDepthOne(instance);
        instance.shouldSkipIteration(2);

        String log = capturedLog(() -> completeIteration(instance, 2, MOVE_A));

        assertPastSoftLimit(log, "overrun depth 2: ", 500,
                "an iteration ending after the move's soft limit must be logged as an overrun, however short it was");
    }

    /** Runs {@code action} with {@link Log} in REPL mode and returns what it printed to stdout. */
    private static String capturedLog(Runnable action) {
        var original = System.out;
        var originalMode = Log.getMode();
        var buffer = new java.io.ByteArrayOutputStream();

        try {
            System.setOut(new java.io.PrintStream(buffer, true));
            Log.setMode(Log.Mode.REPL);
            action.run();
        } finally {
            System.setOut(original);
            Log.setMode(originalMode);
        }

        return buffer.toString();
    }

    /** Asserts that {@code log} has a line with {@code prefix} followed by about {@code expectedMs} "ms past soft limit". */
    private static void assertPastSoftLimit(String log, String prefix, long expectedMs, String message) {
        var matcher = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(prefix) + "(-?\\d+) ms past soft limit").matcher(log);

        assertTrue(matcher.find(), message + ": no line '" + prefix + "... ms past soft limit' in the log: " + log);

        long reportedMs = Long.parseLong(matcher.group(1));
        assertTrue(Math.abs(reportedMs - expectedMs) <= CLOCK_TOLERANCE_MS,
                message + ": expected about " + expectedMs + " ms, but the line reports " + reportedMs + " ms");
    }

    /** Instance with a large clock, started now, with the given soft limit; the hard limit follows from the clock. */
    private static TimeManagement.TimeManagementInstance instanceWithSoftLimit(int softMs) {
        return TimeManagement.newInstance(System.currentTimeMillis(), softMs, LARGE_CLOCK_MS, null, "");
    }

    /** Instance without a clock ({@code go movetime}): the hard limit equals the soft limit. */
    private static TimeManagement.TimeManagementInstance instanceWithoutClock(int softMs) {
        return TimeManagement.newInstance(System.currentTimeMillis(), softMs, Integer.MAX_VALUE, null, "");
    }

    /** Instance whose 1 s soft limit has passed: it started 2 s ago. */
    private static TimeManagement.TimeManagementInstance instanceWithSoftLimitPassed() {
        return TimeManagement.newInstance(System.currentTimeMillis() - 2L * SOFT_LIMIT_MS, SOFT_LIMIT_MS, LARGE_CLOCK_MS, null, "");
    }

    /**
     * Instance with soft limit {@link #SOFT_LIMIT_MS}, depths 1-4 completed, and the score of depth 4 at
     * {@link #DROPPED_SCORE_CENTI}, 50 cp below the previous own move: a difficult position before depth 5.
     */
    private static TimeManagement.TimeManagementInstance difficultInstance(long start, int clockMs) {
        var instance = instanceUpToDepthFour(start, SOFT_LIMIT_MS, clockMs, PREVIOUS_OWN_SCORE_CENTI, DROPPED_SCORE_CENTI);

        assertTrue(instance.isDifficultPosition(), "test premise: the position is difficult after depth 4");

        return instance;
    }

    /**
     * Instance started at {@code start}, with depths 1-3 completed at the previous own score and depth 4 at
     * {@code depthFourScoreCenti}. Depths 1-4 have no samples, so they run whatever the limits say.
     */
    private static TimeManagement.TimeManagementInstance instanceUpToDepthFour(long start, int softMs, int clockMs,
                                                                               int previousOwnScoreCenti,
                                                                               int depthFourScoreCenti) {
        var instance = TimeManagement.newInstance(start, softMs, clockMs, previousOwnScoreCenti, "");

        for (int depth = 1; depth < FIRST_EXTENDABLE_DEPTH; depth++) {
            instance.shouldSkipIteration(depth);
            int score = depth == FIRST_EXTENDABLE_DEPTH - 1 ? depthFourScoreCenti : previousOwnScoreCenti;
            instance.iterationDone(depth, MOVE_A, score, System.currentTimeMillis());
        }

        return instance;
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
