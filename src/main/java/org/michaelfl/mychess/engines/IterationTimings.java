package org.michaelfl.mychess.engines;

import java.util.Arrays;

/**
 * Process-wide moving average (SMA over the last
 * {@link EngineTuning#SMA_WINDOW_SIZE} samples) of iteration durations per
 * search depth. Used by {@link PositionSearch} to skip a deepening
 * iteration whose estimated cost exceeds the remaining time budget, with
 * periodic probing to avoid permanently freezing a depth out of the
 * search after one bad sample.
 *
 * <p>State is process-static so it survives the per-{@code go} engine
 * instances. It is reset on {@code ucinewgame} (via
 * {@link ChessEngine#resetIterationTimings()}) so a new game does not
 * inherit stale stats from a different position-complexity profile.
 *
 * <p>The methods touching the statistics are {@code synchronized}; the
 * contention is negligible because the UciHandler runs only one search at a
 * time. {@link #decideIteration} keeps that lock even though the search runs
 * on a single thread: it reads the sample count, the estimate and the skip
 * counter in sequence, and only the lock guarantees that the three belong to
 * the same state. There is also a second writer — {@code ucinewgame} resets
 * the statistics from the UCI handler thread, not from the search thread.
 *
 * <p>The on/off switch is the exception: it is a {@code volatile} flag with a
 * single writer, so it needs visibility but no mutual exclusion.
 *
 * <p>Depths above {@link #MAX_TRACKED_DEPTH} are silently ignored: the
 * search still runs, the skip heuristic simply does not apply for those
 * depths.
 *
 * <p>A window slot of {@code 0} means "empty" — incoming samples are
 * clamped to a minimum of {@code 1} ms in {@link #addSample} so the
 * sentinel is unambiguous and the bookkeeping needs no separate counter.
 *
 * @author Michael Fleischhauer
 */
final class IterationTimings {

    /**
     * Maximum depth for which timing statistics are tracked. Far beyond
     * what any realistic chess search reaches in tournament time controls
     * (engines typically stay below ~30 even at long TCs); a bigger cap
     * would just waste a few KiB on unused array slots.
     */
    static final int MAX_TRACKED_DEPTH = 64;

    /**
     * Whether {@link PositionSearch} may act on these statistics; on in
     * production, switched off for the whole test suite (see
     * {@link #setSkipHeuristicEnabled}).
     */
    private static volatile boolean skipHeuristicEnabled = true;

    private static final long[][] window = new long[MAX_TRACKED_DEPTH + 1][EngineTuning.SMA_WINDOW_SIZE];
    private static final int[] nextWriteIdx = new int[MAX_TRACKED_DEPTH + 1];
    private static final int[] consecutiveSkips = new int[MAX_TRACKED_DEPTH + 1];

    private IterationTimings() {
        throw new IllegalStateException();
    }

    /**
     * @return whether {@link PositionSearch} may skip a deepening iteration
     *         based on these statistics
     */
    static boolean isSkipHeuristicEnabled() {
        return skipHeuristicEnabled;
    }

    /**
     * Turns the skip-hopeless-iteration heuristic on or off process-wide.
     * Recording and querying stay functional either way — only the decision to
     * skip is suppressed, so this class keeps behaving identically for its own
     * unit tests.
     *
     * <p>Exists for the test suite, which switches it off for every test. The
     * heuristic makes a search result depend on two things a test must not
     * depend on: the statistics left behind by whatever ran before it in the
     * same JVM — the state here is process-static — and the speed of the
     * machine, because the estimate is compared against the remaining
     * wall-clock budget. Both make search-based tests order-dependent and
     * hardware-dependent.
     *
     * @param enabled {@code false} to make {@code shouldSkipIteration} always
     *                answer "do not skip"
     */
    static void setSkipHeuristicEnabled(boolean enabled) {
        skipHeuristicEnabled = enabled;
    }

    /** What {@link PositionSearch} should do with the next deepening iteration. */
    enum IterationDecision {
        /** Run it. */
        RUN,
        /** Run it even though the estimate exceeds the budget, to refresh the statistics. */
        PROBE,
        /** Do not run it. */
        SKIP
    }

    /**
     * The complete skip decision for {@code depth}, as a pure function of the
     * recorded statistics and the time left. Kept here rather than in
     * {@link PositionSearch} so it can be tested without a clock: the caller
     * passes {@code remainingMs} in, and the same inputs always produce the
     * same answer.
     *
     * <p>Note that this is a query — {@link #recordSkip} is the caller's job,
     * so the decision itself has no side effect and can be exercised
     * repeatedly in a test.
     *
     * @param depth       the deepening depth about to be started
     * @param remainingMs milliseconds left in the move's time budget
     * @return {@link IterationDecision#RUN} whenever the heuristic is off, the
     *         depth has too little history, or the estimate fits in the
     *         remaining time
     */
    static synchronized IterationDecision decideIteration(int depth, long remainingMs) {
        if (!skipHeuristicEnabled || !hasEnoughSamplesForSkipDecision(depth)) {
            return IterationDecision.RUN;
        }

        long estimateMs = getEstimatedMs(depth);
        if (estimateMs <= remainingMs) {
            return IterationDecision.RUN;
        }

        return isProbingDue(depth, estimateMs, remainingMs) ? IterationDecision.PROBE : IterationDecision.SKIP;
    }

    /**
     * @return estimated milliseconds for {@code depth}, or {@code 0} if no
     *         history has been recorded yet (or the depth is out of range).
     */
    static synchronized long getEstimatedMs(int depth) {
        if (depth < 0 || depth > MAX_TRACKED_DEPTH) {
            return 0;
        }

        long sum = 0;
        int n = 0;
        for (long sample : window[depth]) {
            if (sample > 0) {
                sum += sample;
                n++;
            }
        }

        return n == 0 ? 0 : sum / n;
    }

    /**
     * @return {@code true} if {@code depth} has at least
     *         {@link EngineTuning#MIN_SAMPLES_FOR_SKIP} samples in the
     *         window, so the skip heuristic is allowed to act on its
     *         estimate.
     */
    static synchronized boolean hasEnoughSamplesForSkipDecision(int depth) {
        if (depth < 0 || depth > MAX_TRACKED_DEPTH) {
            return false;
        }

        int n = 0;
        for (long sample : window[depth]) {
            if (sample > 0 && ++n >= EngineTuning.MIN_SAMPLES_FOR_SKIP) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return {@code true} if {@code depth} has been skipped enough times
     *         in a row AND the remaining time is large enough relative to
     *         the estimate that a probing run is worth doing. The caller
     *         should run the iteration anyway when this returns true.
     *
     *         <p>The ratio gate prevents a probe with very little time
     *         left from contributing a misleadingly low aborted sample to
     *         the SMA.
     */
    static synchronized boolean isProbingDue(int depth, long estimateMs, long remainingMs) {
        if (depth < 0 || depth > MAX_TRACKED_DEPTH) {
            return false;
        }
        if (consecutiveSkips[depth] < EngineTuning.SKIPS_BETWEEN_PROBES) {
            return false;
        }

        return remainingMs >= estimateMs * EngineTuning.MIN_PROBE_REMAINING_RATIO;
    }

    /**
     * Record that {@code depth} was skipped by the heuristic (no run, no
     * timing sample).
     */
    static synchronized void recordSkip(int depth) {
        if (depth < 0 || depth > MAX_TRACKED_DEPTH) {
            return;
        }

        consecutiveSkips[depth]++;
    }

    /** Record a completed iteration that took {@code iterationMs}. */
    static synchronized void recordCompletion(int depth, long iterationMs) {
        if (depth < 0 || depth > MAX_TRACKED_DEPTH) {
            return;
        }

        addSample(depth, iterationMs);
        consecutiveSkips[depth] = 0;
    }

    /**
     * Record an aborted iteration: it ran for {@code elapsedMs} before
     * timeout. The recorded sample is
     * {@code max(currentEstimate, elapsedMs × ABORT_EXTRAPOLATION_FACTOR)}
     * — i.e. an abort can only pull the SMA <em>up</em> or hold it steady,
     * never push it down. Rationale: an aborted iteration proves only that
     * the iteration would have cost at least {@code elapsedMs}, never that
     * it would have been <em>cheaper</em> than the prior average. Using
     * the raw extrapolation as a sample would falsely lower the SMA after
     * a probe that aborted early.
     *
     * <p>Only {@link #recordCompletion} can lower the SMA — that's the
     * only event that delivers a real, ground-truth measurement.
     */
    static synchronized void recordAbort(int depth, long elapsedMs) {
        if (depth < 0 || depth > MAX_TRACKED_DEPTH) {
            return;
        }

        long estimatedMs = getEstimatedMs(depth);
        addSample(depth, Math.max(estimatedMs, (long) (elapsedMs * EngineTuning.ABORT_EXTRAPOLATION_FACTOR)));
        consecutiveSkips[depth] = 0;
    }

    /** Drop all timing history and skip counters. Call on {@code ucinewgame}. */
    static synchronized void reset() {
        for (long[] row : window) {
            Arrays.fill(row, 0L);
        }

        Arrays.fill(nextWriteIdx, 0);
        Arrays.fill(consecutiveSkips, 0);
    }

    private static void addSample(int depth, long sampleMs) {
        int idx = nextWriteIdx[depth];
        // Clamp to >= 1: a value of 0 doubles as the "empty slot" sentinel,
        // so a real 0 ms sample (theoretically possible at depth 1) must not
        // be stored as 0 — it would be invisible to the slot-counting logic.
        window[depth][idx] = Math.max(1L, sampleMs);
        nextWriteIdx[depth] = (idx + 1) % EngineTuning.SMA_WINDOW_SIZE;
    }
}
