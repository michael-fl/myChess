package org.michaelfl.mychess;

import org.michaelfl.mychess.IterationTimings.IterationDecision;

import static org.michaelfl.mychess.Assert.__assert;

/**
 * Time management for one move: two time limits derived from the UCI {@code go} command, and the
 * per-iteration decision whether the iterative deepening starts another depth.
 *
 * <p>The two limits:
 * <ul>
 *   <li>the <em>soft limit</em>, {@link #computeMoveBudgetMillis}: the regular budget for the move.
 *       It decides which argument governs the move ({@code infinite}, {@code movetime}, the clock,
 *       or none); {@link #computeClockBudgetMillis} holds the clock formula, a share of the
 *       remaining time plus most of the increment, capped by the clock and floored at a minimum;</li>
 *   <li>the <em>hard limit</em>, {@link #calculateHardLimit}: the absolute bound at which the search
 *       aborts a running iteration. With a clock it lets a started iteration run at most
 *       {@link #SOFT_LIMIT_MAX_OVERRUN_MS} past the soft limit, or a share of the soft limit set
 *       with {@link #setOverrunPercent}; without a clock it equals the soft limit.</li>
 * </ul>
 *
 * <p>Within one search, a {@link TimeManagementInstance} (from {@link #newInstance}) decides before
 * every iteration whether to start it. An iteration that was started may run past the soft limit up
 * to the hard limit; an aborted iteration is discarded. The UCI watcher in {@link UciHandler} waits
 * one second beyond the hard limit and is only a safety net behind it.
 *
 * <p>Usage: {@link UciHandler} computes the soft limit, the search derives the hard limit and drives
 * the instance:
 * <pre>{@code
 * var input = new TimeManagement.TimeControlInput(null, 60_000, 60_000, null, false, 1_000, 1_000);
 * int softLimitMs = TimeManagement.computeMoveBudgetMillis(input, board.getGameStatus().getTurn());
 * int hardLimitMs = TimeManagement.calculateHardLimit(softLimitMs, 60_000);
 *
 * long start = System.currentTimeMillis();
 * long hardTimeout = start + hardLimitMs;   // the search aborts a running iteration here
 * var timeManagement = TimeManagement.newInstance(start + softLimitMs, "");
 *
 * for (int depth = 1; !timeManagement.shouldSkipIteration(depth); depth++) {
 *     // search depth, aborting at hardTimeout, then:
 *     timeManagement.iterationDone(depth, System.currentTimeMillis());
 * }
 * }</pre>
 *
 * <p>The static methods are stateless. The skip decision additionally reads the per-depth timing
 * statistics of {@link IterationTimings}, which outlive a single search.
 *
 * @author Michael Fleischhauer
 */
public final class TimeManagement {

    /** Default fallback when wtime/btime is given without movestogo. */
    private static final int DEFAULT_MOVES_TO_GO = 30;

    /** Safety margin per move when computing time budget from clock. */
    private static final int TIME_SAFETY_MARGIN_MS = 50;

    /** Floor on per-move time so search has at least a fraction of a second. */
    private static final int MIN_BUDGET_MS = 50;

    /** Ceiling on go infinite / go depth N (effectively unbounded). 24 h in ms. */
    private static final int INFINITE_MILLIS = 24 * 60 * 60 * 1_000;

    /** Percentage of the increment to be used per move. */
    private static final int INCREMENT_USE_PERCENTAGE = 80;

    private static final float HARD_LIMIT_SOFT_LIMIT_FACTOR = 4f;

    /**
     * The most a started iteration may run past the soft limit before the search aborts it.
     *
     * <p>This reproduces what 4.9.0 actually did: its UCI watcher canceled the search at the soft
     * limit plus one second, so the computed hard limit (up to {@value #HARD_LIMIT_SOFT_LIMIT_FACTOR}
     * times the soft limit) was never reached, and the +21 Elo of 4.9.0 was measured with this cap.
     * Letting iterations run to the full factor cost about 48 Elo (match {@code watcher-hard-limit}).
     *
     * <p>As an absolute time it only fits short controls: at 60+1, with a soft limit of about 1-3 s,
     * one second is a substantial overrun, while at long controls it is negligible and the cap
     * effectively disappears. Since 4.11.0 the default is a share of the soft limit,
     * {@value #DEFAULT_OVERRUN_PERCENT} %; this fixed value applies only when {@link #setOverrunPercent}
     * is given {@value #FIXED_OVERRUN_PERCENT}.
     */
    static final int SOFT_LIMIT_MAX_OVERRUN_MS = 1_000;

    /**
     * Value of {@link #overrunPercent} that selects the fixed {@link #SOFT_LIMIT_MAX_OVERRUN_MS}
     * instead of a share of the soft limit, the behavior of 4.10.0.
     */
    static final int FIXED_OVERRUN_PERCENT = 0;

    /**
     * Default of {@link #overrunPercent}: a started iteration may run 55 % of the soft limit past it.
     * At 60+1 that equals the fixed second at the median move (soft limit 1,812 ms in the 4.9.2
     * games); unlike the second it scales with the control. Smoke tests against the fixed second:
     * +12.7 +- 23.1 Elo at 60+1 (600 games) and +12.4 +- 34.6 at 180+2 (280 games), no time losses.
     */
    static final int DEFAULT_OVERRUN_PERCENT = 55;

    private static final float HARD_LIMIT_REMAINING_TIME_FACTOR = 0.2f;

    /**
     * Default of {@link #riskFactor}. Chosen in a calibration so that the honest estimates (every
     * abort recorded) use the clock like 4.9.1 did, then measured at +23.7 +- 8.9 Elo against 4.9.0
     * at 60+1 (match {@code honest-k150}).
     */
    static final double DEFAULT_RISK_FACTOR = 1.5;

    /**
     * Multiplier on the remaining time in the start decision: an iteration starts when its
     * estimated duration is at most this factor times the time left before the soft limit.
     * The estimates count every aborted iteration, so they are honest but high; the factor sets
     * how boldly the search starts a deeper iteration against them. Set through the UCI option
     * {@code TimeRiskPercent}. Process-wide like {@link IterationTimings}, which it qualifies.
     */
    private static volatile double riskFactor = DEFAULT_RISK_FACTOR;

    /**
     * How far a started iteration may run past the soft limit, in percent of the soft limit;
     * {@value #FIXED_OVERRUN_PERCENT} keeps the fixed {@link #SOFT_LIMIT_MAX_OVERRUN_MS}. Set through
     * the UCI option {@code TimeOverrunPercent}. Process-wide like {@link #riskFactor}.
     */
    private static volatile int overrunPercent = DEFAULT_OVERRUN_PERCENT;

    /**
     * The time-related arguments of one UCI {@code go} command, as sent by the GUI.
     *
     * <p>All times are in milliseconds. An argument the GUI did not send is {@code null}; only
     * {@code infinite} is a plain flag, {@code false} unless {@code go infinite} was sent.
     *
     * @param movetime  {@code movetime}: fixed time for this move
     * @param wtime     {@code wtime}: white's remaining clock
     * @param btime     {@code btime}: black's remaining clock
     * @param movestogo {@code movestogo}: moves until the next time control, a count, not a time
     * @param infinite  {@code infinite}: search until {@code stop}
     * @param winc      {@code winc}: white's increment per move
     * @param binc      {@code binc}: black's increment per move
     */
    public record TimeControlInput(
            Integer movetime,
            Integer wtime,
            Integer btime,
            Integer movestogo,
            boolean infinite,
            Integer winc,
            Integer binc) { }

    /**
     * Time management state of one search: decides before each iteration whether to start it, and
     * records the duration of every iteration in {@link IterationTimings}.
     *
     * <p>Not thread-safe and not reusable: create one per search via {@link #newInstance}, then call
     * {@link #shouldSkipIteration} and {@link #iterationDone} alternately for depth 1, 2, 3, …
     */
    public static final class TimeManagementInstance {
        private final long softTimeout;
        private final String logPrefix;
        private int depth = 1;
        private IterationDecision decisionOnSoftTimeout;
        private long iterationStartTimestamp;

        private TimeManagementInstance(long softTimeout, String logTag) {
            this.softTimeout = softTimeout;
            this.logPrefix = logTag == null || logTag.isEmpty() ? "[time] " : "[time] " + logTag + " ";
        }

        /**
         * Decides whether the iterative deepening should stop before {@code currentDepth}.
         *
         * <p>Depth 1 always runs. For deeper iterations {@link IterationTimings#decideIteration}
         * compares the estimated cost of the depth with the time left until the soft limit.
         *
         * <p>Side effects: a skip because of the estimate is recorded with
         * {@link IterationTimings#recordSkip}, so a probing run becomes due eventually; a stop
         * because the limit is (nearly) reached is not recorded. Skips, probes and stops are logged
         * with the {@code [time]} tag.
         *
         * @param currentDepth the depth about to be started; must be the depth following the last
         *                     one reported to {@link #iterationDone}
         * @return {@code true} if the iteration must not be started and the search should return
         *         the result of the previous depth
         */
        public boolean shouldSkipIteration(int currentDepth) {
            __assert(() -> currentDepth == depth, () -> "unexpected depth: expected " + depth + ", but is " + currentDepth);

            iterationStartTimestamp = System.currentTimeMillis();

            if (depth == 1) {
                decisionOnSoftTimeout = IterationDecision.RUN;
                return false;
            }

            final long remainingMs = softTimeout - System.currentTimeMillis();
            decisionOnSoftTimeout = IterationTimings.decideIteration(currentDepth, remainingMs, riskFactor);
            final long estimateMs = IterationTimings.getEstimatedMs(depth);

            return recordAndLogSkip(decisionOnSoftTimeout, estimateMs, remainingMs);
        }

        @SuppressWarnings({"java:S6205", "java:S1192"})
        private boolean recordAndLogSkip(final IterationDecision decision, final long estimateMs, final long remainingMs) {
            return switch (decision) {
                case RUN -> false;
                case PROBE -> {
                    Log.info(logPrefix + "probe depth " + depth + ": est " + estimateMs + " ms > remaining " + remainingMs + " ms");
                    yield false;
                }
                case SKIP -> {
                    if (remainingMs > IterationTimings.MIN_REMAINING_MILLIS) {
                        IterationTimings.recordSkip(depth);
                        Log.info(logPrefix + "skip depth " + depth + ": est " + estimateMs + " ms > remaining " + remainingMs + " ms");
                    } else {
                        Log.info(logPrefix + "stop depth " + depth + ": limit reached");
                    }
                    yield true;
                }
            };
        }

        /**
         * Reports a completed iteration.
         *
         * <p>Records the iteration's duration, measured from the {@link #shouldSkipIteration} call
         * that started it, as a completed sample in {@link IterationTimings}. An iteration ending
         * past the soft limit is logged as {@code [time] overrun}. Must not be called for an aborted
         * iteration; use {@link #iterationAborted} instead.
         *
         * @param currentDepth          the depth just completed, as passed to {@link #shouldSkipIteration}
         * @param iterationEndTimestamp the time the iteration ended, in epoch milliseconds
         */
        public void iterationDone(final int currentDepth, final long iterationEndTimestamp) {
            __assert(() -> decisionOnSoftTimeout != null, "iterationDone must be called after shouldSkipIteration");
            __assert(() -> currentDepth == depth, () -> "unexpected depth: expected " + depth + ", but is " + currentDepth);

            long iterationDurationMs = iterationEndTimestamp - iterationStartTimestamp;
            IterationTimings.recordCompletion(depth, iterationDurationMs);

            if (iterationEndTimestamp > softTimeout) {
                // Exceeded the soft timeout
                Log.info(logPrefix + "overrun depth " + depth + ": " + (iterationEndTimestamp - softTimeout) + " ms past soft limit");
            }

            depth++;
            decisionOnSoftTimeout = null;
        }

        /**
         * Reports an iteration that was aborted at the hard limit; its result is discarded.
         *
         * <p>The elapsed time, measured from the {@link #shouldSkipIteration} call that started the
         * iteration, is recorded with {@link IterationTimings#recordAbort}, which extrapolates it to a
         * lower bound of the full duration. Every abort is recorded, whichever cap set the hard limit
         * ({@link #calculateHardLimit}), so the per-depth estimates are honest; the
         * {@linkplain #setRiskFactor risk factor} compensates for their height. The abort is logged
         * as {@code [time] abort}.
         *
         * <p>The depth stays as it was after the last completed iteration, so the search is expected
         * to stop and return that iteration's result.
         *
         * @param currentDepth          the depth that was aborted, as passed to {@link #shouldSkipIteration}
         * @param iterationEndTimestamp the time the iteration was aborted, in epoch milliseconds
         */
        public void iterationAborted(final int currentDepth, final long iterationEndTimestamp) {
            __assert(() -> decisionOnSoftTimeout != null, "iterationAborted must be called after shouldSkipIteration");
            __assert(() -> currentDepth == depth, () -> "unexpected depth: expected " + depth + ", but is " + currentDepth);

            long iterationDurationMs = iterationEndTimestamp - iterationStartTimestamp;
            IterationTimings.recordAbort(depth, iterationDurationMs);
            Log.info(logPrefix + "abort depth " + depth + ": " + (iterationEndTimestamp - softTimeout) + " ms past soft limit");

            decisionOnSoftTimeout = null;
        }

        // for tests only
        int getDepth() {
            return depth;
        }

        // for tests only
        IterationDecision getDecisionOnSoftTimeout() {
            return decisionOnSoftTimeout;
        }
    }

    private TimeManagement() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Creates the time management for one search.
     *
     * <p>The hard limit is enforced by the search alone, which aborts a running iteration when it
     * is reached and reports it with {@link TimeManagementInstance#iterationAborted}.
     *
     * @param softTimeout epoch milliseconds of the soft limit: no iteration is started that is
     *                    expected to end after it
     * @param logTag      tag put after {@code [time]} in every log line, e.g. {@code game=4174bf06}, so
     *                    lines of engines sharing one log can be told apart; empty or {@code null} for none
     * @return a new instance, positioned before depth 1
     */
    public static TimeManagement.TimeManagementInstance newInstance(long softTimeout, String logTag) {
        return new TimeManagement.TimeManagementInstance(softTimeout, logTag);
    }

    /**
     * Sets the risk factor of the start decision: an iteration starts when its estimate is at most
     * {@code factor} times the remaining time. Used by the UCI option {@code TimeRiskPercent}.
     *
     * @param factor the multiplier, greater than zero; {@code 1.0} is the plain comparison
     * @throws IllegalArgumentException if {@code factor} is not greater than zero
     */
    public static void setRiskFactor(double factor) {
        if (!(factor > 0)) {
            throw new IllegalArgumentException("risk factor must be greater than zero: " + factor);
        }

        riskFactor = factor;
    }

    /** @return the current risk factor of the start decision */
    public static double getRiskFactor() {
        return riskFactor;
    }

    /**
     * Sets how far a started iteration may run past the soft limit, as a share of the soft limit.
     * Used by the UCI option {@code TimeOverrunPercent}.
     *
     * @param percent the overrun in percent of the soft limit, e.g. {@code 50} for half of it;
     *                {@value #FIXED_OVERRUN_PERCENT} selects the fixed {@link #SOFT_LIMIT_MAX_OVERRUN_MS}
     * @throws IllegalArgumentException if {@code percent} is negative
     */
    public static void setOverrunPercent(int percent) {
        if (percent < 0) {
            throw new IllegalArgumentException("overrun percent must not be negative: " + percent);
        }

        overrunPercent = percent;
    }

    /** @return the current overrun share in percent of the soft limit, {@value #FIXED_OVERRUN_PERCENT} for the fixed value */
    public static int getOverrunPercent() {
        return overrunPercent;
    }

    /**
     * The most a started iteration may run past the soft limit, before the other caps of
     * {@link #calculateHardLimit} apply.
     *
     * @param softLimitMs the soft limit in milliseconds
     * @return {@link #SOFT_LIMIT_MAX_OVERRUN_MS} for the fixed setting, otherwise the configured
     *         percentage of {@code softLimitMs}, rounded
     */
    static long maxOverrunMillis(int softLimitMs) {
        final int percent = overrunPercent;

        if (percent == FIXED_OVERRUN_PERCENT) {
            return SOFT_LIMIT_MAX_OVERRUN_MS;
        }

        return Math.round(softLimitMs * (percent / 100.0));
    }

    /**
     * Hard limit for one move: the time after which the search aborts a running iteration.
     *
     * <p>Without a clock ({@code go movetime}, {@code go depth}, {@code go infinite}) it equals the
     * soft limit, because there is no reserve to draw on and a GUI expects the answer within the
     * granted time. With a clock it is the soft limit plus {@link #maxOverrunMillis} (the fixed
     * {@link #SOFT_LIMIT_MAX_OVERRUN_MS} or a share of the soft limit, see {@link #setOverrunPercent}), but at
     * most {@value #HARD_LIMIT_SOFT_LIMIT_FACTOR} times the soft limit, at most 20 % of the remaining
     * clock and never more than the clock minus {@link #TIME_SAFETY_MARGIN_MS}; never less than
     * {@link #MIN_BUDGET_MS}.
     *
     * @param softLimitMs      the soft limit from {@link #computeMoveBudgetMillis}, in milliseconds
     * @param remainingClockMs remaining clock of the side to move in milliseconds, or
     *                         {@link Integer#MAX_VALUE} if no clock was sent
     * @return the hard limit in milliseconds, at least {@link #MIN_BUDGET_MS}
     */
    @SuppressWarnings({"MathClampMigration", "java:S6885"})
    public static int calculateHardLimit(int softLimitMs, int remainingClockMs) {
        if (remainingClockMs == Integer.MAX_VALUE) { // No clock given
            return softLimitMs;
        }

        long softPlusOverrun = softLimitMs + maxOverrunMillis(softLimitMs);
        int timeLimit = (int) Math.min(softPlusOverrun, Math.min(
                Math.round(softLimitMs * HARD_LIMIT_SOFT_LIMIT_FACTOR),
                Math.round(remainingClockMs * HARD_LIMIT_REMAINING_TIME_FACTOR)));

        return Math.max(Math.min(timeLimit, remainingClockMs - TIME_SAFETY_MARGIN_MS), MIN_BUDGET_MS);
    }

    /**
     * Soft limit for one move, derived from the arguments of a UCI {@code go} command: the regular
     * search budget, after which no new iteration is started.
     *
     * <p>The first applicable rule decides:
     * <ol>
     *   <li>{@code go infinite} gives {@link #INFINITE_MILLIS}, whatever else was sent;</li>
     *   <li>{@code movetime} gives that time minus {@link #TIME_SAFETY_MARGIN_MS}, floored at
     *       {@link #MIN_BUDGET_MS}: the search checks the clock only every 10 000 nodes and can
     *       overshoot by a few milliseconds;</li>
     *   <li>a clock of the side to move ({@code wtime} for white, {@code btime} for black) gives
     *       {@link #computeClockBudgetMillis}, with that side's increment ({@code winc} /
     *       {@code binc}, zero if absent) and {@code movestogo} or, if absent,
     *       {@link #DEFAULT_MOVES_TO_GO};</li>
     *   <li>otherwise, e.g. {@code go depth N}, the search is not time-limited:
     *       {@link #INFINITE_MILLIS}.</li>
     * </ol>
     *
     * <p>Only the side to move's own clock and increment are read; the opponent's are ignored.
     *
     * @param input  the parsed {@code go} tokens; absent arguments are {@code null}
     * @param turn the side to move, {@link GameStatus#TURN_WHITE} or {@link GameStatus#TURN_BLACK}
     * @return the soft limit for this move in milliseconds, at least {@link #MIN_BUDGET_MS}
     */
    public static int computeMoveBudgetMillis(TimeManagement.TimeControlInput input, int turn) {
        if (input.infinite) {
            return INFINITE_MILLIS;
        }
        if (input.movetime != null) {
            // Subtract a safety margin: the search checks isTimeout() only
            // every 10 000 nodes, so a hard limit can overshoot by a few ms.
            // Strict GUIs treat that as a time forfeit.
            return Math.max(MIN_BUDGET_MS, input.movetime - TIME_SAFETY_MARGIN_MS);
        }

        Integer ourMs = (turn == GameStatus.TURN_WHITE) ? input.wtime : input.btime;
        if (ourMs != null) {
            Integer ourIncMs = (turn == GameStatus.TURN_WHITE) ? input.winc : input.binc;
            int movesToGo = input.movestogo != null ? input.movestogo : DEFAULT_MOVES_TO_GO;
            return computeClockBudgetMillis(ourMs, ourIncMs != null ? ourIncMs : 0, movesToGo);
        }

        return INFINITE_MILLIS;   // depth-only or no args
    }

    /**
     * Per-move budget from the side-to-move's own clock: a share of the remaining
     * time plus {@link #INCREMENT_USE_PERCENTAGE} of the increment it is about to
     * earn back, never more than the clock itself minus
     * {@link #TIME_SAFETY_MARGIN_MS} and never less than {@link #MIN_BUDGET_MS}.
     *
     * <p>{@code movesToGo} is a spending <em>rate</em>, not a prediction of the
     * game's length: it is re-applied to the shrinking remainder on every move, so
     * the budget decays geometrically rather than running out at move
     * {@code movesToGo}. Spending less than the full increment keeps that decay
     * from being cancelled out on increment controls.
     *
     * @param ourMs     remaining clock of the side to move, in milliseconds; may
     *                  be zero or negative when the GUI reports an overstepped clock
     * @param ourIncMs  per-move increment of the side to move, zero if none was sent
     * @param movesToGo moves the budget is spread over — {@code movestogo} if the
     *                  GUI sent one, otherwise {@link #DEFAULT_MOVES_TO_GO}
     * @return the search budget in milliseconds, at least {@link #MIN_BUDGET_MS}
     */
    @SuppressWarnings({"MathClampMigration", "java:S6885"})
    static int computeClockBudgetMillis(int ourMs, int ourIncMs, int movesToGo) {
        int clockBudgetMs = ourMs / (movesToGo + 1) + (INCREMENT_USE_PERCENTAGE * ourIncMs) / 100;

        return Math.max(Math.min(clockBudgetMs, ourMs - TIME_SAFETY_MARGIN_MS), MIN_BUDGET_MS);
    }

}
