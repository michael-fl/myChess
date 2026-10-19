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
 *       {@link #SOFT_LIMIT_MAX_OVERRUN_MS} past the soft limit, without a clock it equals the soft
 *       limit.</li>
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
 * var timeManagement = TimeManagement.newInstance(start + softLimitMs, hardTimeout, "");
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
     * <p>TODO: tune this value. As an absolute time it only fits short controls: at 60+1, with a
     * soft limit of about 1-3 s, one second is a substantial overrun, while at long controls it is
     * negligible and the cap effectively disappears. It probably belongs as a factor of the soft
     * limit instead, measured against this fixed value.
     */
    static final int SOFT_LIMIT_MAX_OVERRUN_MS = 1_000;

    private static final float HARD_LIMIT_REMAINING_TIME_FACTOR = 0.2f;

    /** Default of {@link #riskFactor}: the plain comparison of estimate and remaining time. */
    static final double DEFAULT_RISK_FACTOR = 1.0;

    /**
     * Multiplier on the remaining time in the start decision: an iteration starts when its
     * estimated duration is at most this factor times the time left before the soft limit.
     * Experimental, set through the UCI option {@code TimeRiskPercent}; the default keeps 4.9.1's
     * behavior. Process-wide like {@link IterationTimings}, which it qualifies.
     */
    private static volatile double riskFactor = DEFAULT_RISK_FACTOR;

    /**
     * Whether aborts at the soft + {@link #SOFT_LIMIT_MAX_OVERRUN_MS} cap are recorded in
     * {@link IterationTimings} like every other abort ("honest estimates"). Experimental, set
     * through the UCI option {@code HonestEstimates}; off by default, which keeps 4.9.1's rule of
     * not recording them.
     */
    private static volatile boolean honestEstimates = false;

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
        private final boolean recordAborts;
        private final String logPrefix;
        private int depth = 1;
        private IterationDecision decisionOnSoftTimeout;
        private long iterationStartTimestamp;

        private TimeManagementInstance(long softTimeout, long hardTimeout, String logTag) {
            this.softTimeout = softTimeout;
            this.recordAborts = honestEstimates || hardTimeout < softTimeout + SOFT_LIMIT_MAX_OVERRUN_MS;
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
         * <p>Whether the abort is recorded depends on which cap set the hard limit
         * ({@link #calculateHardLimit}):
         * <ul>
         *   <li><b>the overrun cap</b>, soft limit + {@link #SOFT_LIMIT_MAX_OVERRUN_MS}: the abort is
         *       <em>not</em> recorded, by design. These are the most expensive iterations of all;
         *       feeding them into the per-depth estimate makes the start decision against the soft
         *       limit so cautious that the engine leaves its clock unused and searches shallower.
         *       4.9.0 discarded them too, because its watcher canceled them before they reached
         *       {@code IterationTimings}, and was measured at +21 Elo that way. The abort is logged
         *       as {@code [time] abort}.</li>
         *   <li><b>any other cap</b> (no clock, so hard equals soft; 4 x soft for a tiny soft limit;
         *       20 % of a short clock): the elapsed time, measured from the
         *       {@link #shouldSkipIteration} call that started the iteration, is recorded with
         *       {@link IterationTimings#recordAbort}, which extrapolates it to a lower bound of the
         *       full duration. 4.9.0 recorded these as well, because its search reached its own
         *       hard limit before the watcher fired.</li>
         * </ul>
         * So the rule reproduces 4.9.0 exactly; it is a stopgap until the estimate itself is made
         * honest.
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

            if (recordAborts) {
                IterationTimings.recordAbort(depth, iterationDurationMs);
            } else {
                Log.info(logPrefix + "abort depth " + depth + ": " + (iterationEndTimestamp - softTimeout)
                        + " ms past soft limit, not recorded");
            }

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
     * <p>The hard limit itself is enforced by the search, which aborts a running iteration when it
     * is reached; the instance only needs it to tell which cap set the hard limit, and with it
     * whether an abort is recorded (see {@link TimeManagementInstance#iterationAborted}).
     *
     * @param softTimeout epoch milliseconds of the soft limit: no iteration is started that is
     *                    expected to end after it
     * @param hardTimeout epoch milliseconds of the hard limit; usually {@code softTimeout} +
     *                    {@link #SOFT_LIMIT_MAX_OVERRUN_MS}, lower when another cap binds
     * @param logTag      tag put after {@code [time]} in every log line, e.g. {@code game=4174bf06}, so
     *                    lines of engines sharing one log can be told apart; empty or {@code null} for none
     * @return a new instance, positioned before depth 1
     */
    public static TimeManagement.TimeManagementInstance newInstance(long softTimeout, long hardTimeout, String logTag) {
        return new TimeManagement.TimeManagementInstance(softTimeout, hardTimeout, logTag);
    }

    /**
     * Sets the risk factor of the start decision: an iteration starts when its estimate is at most
     * {@code factor} times the remaining time. Used by the UCI option {@code TimeRiskPercent}.
     *
     * @param factor the multiplier, greater than zero; {@link #DEFAULT_RISK_FACTOR} keeps the plain rule
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
     * Switches honest estimates on or off: when on, aborts at the soft limit + one second cap are
     * recorded in {@link IterationTimings} like every other abort. Used by the UCI option
     * {@code HonestEstimates}.
     *
     * @param enabled {@code true} to record those aborts
     */
    public static void setHonestEstimates(boolean enabled) {
        honestEstimates = enabled;
    }

    /** @return whether aborts at the overrun cap are recorded */
    public static boolean isHonestEstimates() {
        return honestEstimates;
    }

    /**
     * Hard limit for one move: the time after which the search aborts a running iteration.
     *
     * <p>Without a clock ({@code go movetime}, {@code go depth}, {@code go infinite}) it equals the
     * soft limit, because there is no reserve to draw on and a GUI expects the answer within the
     * granted time. With a clock it is the soft limit plus {@link #SOFT_LIMIT_MAX_OVERRUN_MS}, but at
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

        int timeLimit = Math.min(softLimitMs + SOFT_LIMIT_MAX_OVERRUN_MS, Math.min(
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
