package org.michaelfl.mychess.engines;

/**
 * Bundled tuning knobs for the search heuristics. Centralizing them here
 * makes experimentation easy: change one number, rebuild, observe the
 * effect in Cute Chess or {@code mvn test}.
 *
 * <p>Only knobs that a human is plausibly going to tune live here. Things
 * like UCI-protocol time margins ({@code UciHandler.TIME_SAFETY_MARGIN_MS})
 * or pure diagnostic counters ({@code BookMissThrottle.MAX_LOGGED_MISSES})
 * stay where they're used.
 *
 * @author Michael Fleischhauer
 */
final class EngineTuning {

    /** Number of recent iteration-time samples kept per depth (SMA window). */
    static final int SMA_WINDOW_SIZE = 5;

    /**
     * Factor applied to the elapsed-at-abort time to estimate how long an
     * aborted iteration would have taken if it had run to completion. The
     * abort happens somewhere mid-flight, so the elapsed time alone would
     * understate the true cost.
     */
    static final double ABORT_EXTRAPOLATION_FACTOR = 1.2;

    /**
     * Maximum number of consecutive skips of the same depth before the
     * skip decision is overridden with a probing run. Without this, a
     * depth flagged "too expensive" once would never be re-measured, even
     * if the position later simplifies and the depth would be cheap.
     */
    static final int SKIPS_BETWEEN_PROBES = 5;

    /**
     * Minimum samples required in the window before the skip heuristic is
     * allowed to fire. Prevents a single unlucky early sample (typically
     * an abort estimate inflated by {@link #ABORT_EXTRAPOLATION_FACTOR})
     * from triggering skips on its own.
     */
    static final int MIN_SAMPLES_FOR_SKIP = 2;

    /**
     * Minimum ratio of {@code remainingMs / estimateMs} required for a
     * probing run to be considered productive. Below this ratio, the probe
     * would almost certainly abort early and only contribute a
     * misleadingly low sample (because the abort extrapolation
     * underestimates how far short the iteration really fell). When the
     * ratio is not met, the probe is deferred and the depth is skipped
     * normally; the skip counter keeps growing and the probe fires later
     * when enough time is actually available.
     */
    static final double MIN_PROBE_REMAINING_RATIO = 0.7;

    private EngineTuning() {
        throw new IllegalStateException();
    }
}
