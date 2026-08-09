package org.michaelfl.mychess.engines;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.michaelfl.mychess.engines.IterationTimings.IterationDecision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic unit tests for {@link IterationTimings}. Tests run in-VM
 * and share the same process-static state, so each test resets the
 * statistics in {@link #resetState()} to start from a clean slate.
 *
 * <p>Assertions express the SMA window size, the abort extrapolation
 * factor, the skip-decision threshold, and the probing interval in terms
 * of the {@link EngineTuning} constants so that a tuning change does not
 * silently invalidate the test results without the human noticing.
 *
 * @author Michael Fleischhauer
 */
class IterationTimingsTest {

    private static final int DEPTH = 7;

    @BeforeEach
    void resetState() {
        IterationTimings.reset();
    }

    @Test
    void getEstimatedMs_unknownDepth_returnsZero() {
        assertEquals(0, IterationTimings.getEstimatedMs(DEPTH),
                "Estimate must be 0 for a depth with no history");
    }

    @Test
    void recordCompletion_singleSample_returnsSampleAsEstimate() {
        IterationTimings.recordCompletion(DEPTH, 1234);

        assertEquals(1234, IterationTimings.getEstimatedMs(DEPTH),
                "With a single sample, the estimate must equal that sample");
    }

    @Test
    void recordCompletion_overflowingWindow_dropsOldestSample() {
        // Push window-size + 1 samples; the oldest (100) falls off, average is over the rest.
        long[] samples = new long[EngineTuning.SMA_WINDOW_SIZE + 1];
        long sumOfKept = 0;
        for (int i = 0; i < samples.length; i++) {
            samples[i] = 100L * (i + 1);   // 100, 200, 300, ...
            IterationTimings.recordCompletion(DEPTH, samples[i]);
            if (i >= 1) {
                sumOfKept += samples[i];
            }
        }

        long expectedAverage = sumOfKept / EngineTuning.SMA_WINDOW_SIZE;
        assertEquals(expectedAverage, IterationTimings.getEstimatedMs(DEPTH),
                "SMA must drop the oldest sample once the window overflows");
    }

    @Test
    void recordAbort_emptyWindow_extrapolatesBy1Point5Factor() {
        // Warm-up path: no prior samples → the floor (currentEstimate = 0)
        // is below the extrapolation, so the extrapolation wins.
        long elapsed = 1000;
        IterationTimings.recordAbort(DEPTH, elapsed);

        long expected = (long) (elapsed * EngineTuning.ABORT_EXTRAPOLATION_FACTOR);
        assertEquals(expected, IterationTimings.getEstimatedMs(DEPTH),
                "Abort estimate must equal elapsed * ABORT_EXTRAPOLATION_FACTOR when no prior samples exist");
    }

    @Test
    void recordAbort_extrapolationBelowCurrentEstimate_preservesEstimate() {
        // Establish a higher SMA with completion samples first.
        long completionMs = 25_000;
        IterationTimings.recordCompletion(DEPTH, completionMs);
        IterationTimings.recordCompletion(DEPTH, completionMs);
        assertEquals(completionMs, IterationTimings.getEstimatedMs(DEPTH),
                "Precondition: SMA must reflect the completion samples");

        // An abort whose extrapolation (5200 × 1.5 = 7800) is well below the
        // current estimate (25000) must NOT pull the SMA down. The sample
        // actually recorded must be the current estimate.
        long shortAbortElapsed = 5_200;

        IterationTimings.recordAbort(DEPTH, shortAbortElapsed);

        // After the abort: window holds {25000, 25000, 25000}. Average = 25000.
        assertEquals(completionMs, IterationTimings.getEstimatedMs(DEPTH),
                "Abort below the prior estimate must not pull the SMA down");
    }

    @Test
    void recordAbort_extrapolationAboveCurrentEstimate_raisesEstimate() {
        // Establish a low SMA, then record an abort that suggests the depth
        // is more expensive than we thought — the SMA must move upward.
        IterationTimings.recordCompletion(DEPTH, 5_000);
        IterationTimings.recordCompletion(DEPTH, 5_000);
        assertEquals(5_000, IterationTimings.getEstimatedMs(DEPTH),
                "Precondition: SMA reflects the low completion samples");

        long longAbortElapsed = 12_000;
        long extrapolation = (long) (longAbortElapsed * EngineTuning.ABORT_EXTRAPOLATION_FACTOR);

        IterationTimings.recordAbort(DEPTH, longAbortElapsed);

        // Window holds {5000, 5000, 18000}; average = (5000+5000+18000)/3.
        long expected = (5_000 + 5_000 + extrapolation) / 3;
        assertEquals(expected, IterationTimings.getEstimatedMs(DEPTH),
                "Abort with extrapolation above the prior estimate must raise the SMA");
    }

    @Test
    void hasEnoughSamplesForSkipDecision_falseUntilMinReached() {
        assertFalse(IterationTimings.hasEnoughSamplesForSkipDecision(DEPTH),
                "No samples yet — heuristic must not be allowed to act");

        for (int i = 1; i < EngineTuning.MIN_SAMPLES_FOR_SKIP; i++) {
            IterationTimings.recordCompletion(DEPTH, 100);
            assertFalse(IterationTimings.hasEnoughSamplesForSkipDecision(DEPTH),
                    "Below MIN_SAMPLES_FOR_SKIP samples — heuristic must still be inactive");
        }

        IterationTimings.recordCompletion(DEPTH, 100);
        assertTrue(IterationTimings.hasEnoughSamplesForSkipDecision(DEPTH),
                "At/above MIN_SAMPLES_FOR_SKIP samples — heuristic must be allowed to act");
    }

    @Test
    void recordSkip_incrementsCounter_isProbingDueAfterMaxSkips() {
        // Pure skip-counter test: after exactly SKIPS_BETWEEN_PROBES consecutive
        // recordSkip calls, isProbingDue must flip to true. The ratio gate is
        // not what's being tested here — `remaining` is set well above
        // `estimate` so MIN_PROBE_REMAINING_RATIO is trivially satisfied.
        long estimate = 1000;
        long remaining = 10 * estimate;

        for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES; i++) {
            assertFalse(IterationTimings.isProbingDue(DEPTH, estimate, remaining),
                    "Probing must not be due before SKIPS_BETWEEN_PROBES skips (i=" + i + ")");
            IterationTimings.recordSkip(DEPTH);
        }

        assertTrue(IterationTimings.isProbingDue(DEPTH, estimate, remaining),
                "Probing must be due after SKIPS_BETWEEN_PROBES consecutive skips");
    }

    @Test
    void isProbingDue_skipCounterHighButRemainingTooLow_returnsFalse() {
        // Skip counter has reached the probing threshold, but only a tiny
        // fraction of the estimated cost is left — the probe would just
        // pollute the SMA with an under-sampled abort. Must return false.
        for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES; i++) {
            IterationTimings.recordSkip(DEPTH);
        }

        long estimate = 1000;
        long stingyRemaining = (long) (estimate * EngineTuning.MIN_PROBE_REMAINING_RATIO) - 1;

        assertFalse(IterationTimings.isProbingDue(DEPTH, estimate, stingyRemaining),
                "Probe must be deferred when remaining < MIN_PROBE_REMAINING_RATIO * estimate");

        long justEnoughRemaining = (long) (estimate * EngineTuning.MIN_PROBE_REMAINING_RATIO);
        assertTrue(IterationTimings.isProbingDue(DEPTH, estimate, justEnoughRemaining),
                "Probe must fire once remaining >= MIN_PROBE_REMAINING_RATIO * estimate");
    }

    @Test
    void recordCompletion_resetsSkipCounter() {
        // Once recordCompletion is called, the skip counter resets — even if
        // the probing-due precondition was already satisfied. Ratio gate is
        // again deliberately out of scope (remaining >> estimate).
        long estimate = 1000;
        long remaining = 10 * estimate;

        for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES; i++) {
            IterationTimings.recordSkip(DEPTH);
        }
        assertTrue(IterationTimings.isProbingDue(DEPTH, estimate, remaining),
                "Precondition: probing due after consecutive skips");

        IterationTimings.recordCompletion(DEPTH, 500);

        assertFalse(IterationTimings.isProbingDue(DEPTH, estimate, remaining),
                "Completion must clear the skip counter");
    }

    @Test
    void reset_clearsAllDepthsIncludingSkipAndSampleCounters() {
        IterationTimings.recordCompletion(DEPTH, 1234);
        IterationTimings.recordCompletion(DEPTH + 1, 5678);
        IterationTimings.recordSkip(DEPTH);

        IterationTimings.reset();

        assertEquals(0, IterationTimings.getEstimatedMs(DEPTH),
                "Reset must clear timing samples for depth " + DEPTH);
        assertEquals(0, IterationTimings.getEstimatedMs(DEPTH + 1),
                "Reset must clear timing samples for depth " + (DEPTH + 1));
        assertFalse(IterationTimings.hasEnoughSamplesForSkipDecision(DEPTH),
                "Reset must clear the sample counter");
        assertFalse(IterationTimings.isProbingDue(DEPTH, 1000, 1000),
                "Reset must clear the skip counter");
    }

    // ---- decideIteration: the composite skip decision ----
    //
    // The whole suite runs with the heuristic switched off (see
    // DisableSkipHeuristicExtension), so this branch of PositionSearch is
    // otherwise never exercised. These tests turn it back on for their own
    // duration and restore the suite-wide state afterwards — nothing that runs
    // later may inherit an enabled heuristic, or it becomes order-dependent
    // again, which is the very thing the extension exists to prevent.

    /** Comfortably above {@link EngineTuning#MIN_SAMPLES_FOR_SKIP}. */
    private static final long SAMPLE_MS = 1_000;

    private static void withSkipHeuristicEnabled(Runnable body) {
        IterationTimings.setSkipHeuristicEnabled(true);
        try {
            body.run();
        } finally {
            IterationTimings.setSkipHeuristicEnabled(false);
        }
    }

    /** Feed enough samples that the depth passes the sample gate. */
    private static void primeSamples() {
        for (int i = 0; i < EngineTuning.MIN_SAMPLES_FOR_SKIP; i++) {
            IterationTimings.recordCompletion(DEPTH, SAMPLE_MS);
        }
    }

    @Test
    void decideIteration_heuristicDisabled_alwaysRuns() {
        primeSamples();

        assertEquals(IterationDecision.RUN, IterationTimings.decideIteration(DEPTH, 1),
                "with the heuristic off the estimate must be ignored, however hopeless it looks");
    }

    @Test
    void decideIteration_tooFewSamples_runs() {
        withSkipHeuristicEnabled(() -> {
            IterationTimings.recordCompletion(DEPTH, SAMPLE_MS);

            assertEquals(IterationDecision.RUN, IterationTimings.decideIteration(DEPTH, 1),
                    "a single sample is below MIN_SAMPLES_FOR_SKIP, so there is nothing to decide on");
        });
    }

    @Test
    void decideIteration_estimateFitsInRemainingTime_runs() {
        withSkipHeuristicEnabled(() -> {
            primeSamples();

            assertEquals(IterationDecision.RUN, IterationTimings.decideIteration(DEPTH, SAMPLE_MS),
                    "an estimate equal to the remaining time still fits — the comparison is not strict");
            assertEquals(IterationDecision.RUN, IterationTimings.decideIteration(DEPTH, SAMPLE_MS + 1),
                    "an estimate below the remaining time fits");
        });
    }

    @Test
    void decideIteration_estimateExceedsRemainingTime_skips() {
        withSkipHeuristicEnabled(() -> {
            primeSamples();

            assertEquals(IterationDecision.SKIP, IterationTimings.decideIteration(DEPTH, SAMPLE_MS - 1),
                    "an estimate above the remaining time must skip while no probe is due");
        });
    }

    @Test
    void decideIteration_afterEnoughSkips_probesInsteadOfSkipping() {
        withSkipHeuristicEnabled(() -> {
            primeSamples();
            // The ratio gate also has to pass, so leave nearly the full
            // estimate as remaining time — just short of fitting.
            long remainingMs = SAMPLE_MS - 1;

            for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES; i++) {
                assertEquals(IterationDecision.SKIP, IterationTimings.decideIteration(DEPTH, remainingMs),
                        "skip #" + (i + 1) + " before the probe is due");
                IterationTimings.recordSkip(DEPTH);
            }

            assertEquals(IterationDecision.PROBE, IterationTimings.decideIteration(DEPTH, remainingMs),
                    "after SKIPS_BETWEEN_PROBES consecutive skips the depth must be probed again");
        });
    }

    @Test
    void decideIteration_probeDueButTooLittleTimeLeft_skips() {
        withSkipHeuristicEnabled(() -> {
            primeSamples();

            for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES; i++) {
                IterationTimings.recordSkip(DEPTH);
            }

            long farTooLittle = (long) (SAMPLE_MS * EngineTuning.MIN_PROBE_REMAINING_RATIO) - 1;

            assertEquals(IterationDecision.SKIP, IterationTimings.decideIteration(DEPTH, farTooLittle),
                    "the ratio gate must suppress a probe that would only contribute a misleadingly short sample");
        });
    }

    @Test
    void decideIteration_isAQueryAndDoesNotRecordSkips() {
        withSkipHeuristicEnabled(() -> {
            primeSamples();

            for (int i = 0; i < EngineTuning.SKIPS_BETWEEN_PROBES + 2; i++) {
                IterationTimings.decideIteration(DEPTH, SAMPLE_MS - 1);
            }

            assertEquals(IterationDecision.SKIP, IterationTimings.decideIteration(DEPTH, SAMPLE_MS - 1),
                    "asking the question must not advance the skip counter; only recordSkip may do that, "
                            + "otherwise a probe would become due without a single iteration being skipped");
        });
    }

    @Test
    void decideIteration_restoresTheSuiteWideDisabledState() {
        withSkipHeuristicEnabled(() -> { /* nothing — the finally block is what is under test */ });

        assertFalse(IterationTimings.isSkipHeuristicEnabled(),
                "a test that enables the heuristic must hand it back disabled, or every test running after it "
                        + "becomes order-dependent");
    }
}
