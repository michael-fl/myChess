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
 *       aborts a running iteration. With a clock it leaves room above the soft limit, without a
 *       clock it equals the soft limit.</li>
 * </ul>
 *
 * <p>Within one search, a {@link TimeManagementInstance} (from {@link #newInstance}) decides before
 * every iteration whether to start it. An iteration that was started may run past the soft limit up
 * to the hard limit; an aborted iteration is discarded.
 *
 * <p>Usage: {@link UciHandler} computes the soft limit, the search derives the hard limit and drives
 * the instance:
 * <pre>{@code
 * var input = new TimeManagement.TimeControlInput(null, 60_000, 60_000, null, false, 1_000, 1_000);
 * int softLimitMs = TimeManagement.computeMoveBudgetMillis(input, board.getGameStatus().getTurn());
 * int hardLimitMs = TimeManagement.calculateHardLimit(softLimitMs, 60_000);
 *
 * long start = System.currentTimeMillis();
 * var timeManagement = TimeManagement.newInstance(start + softLimitMs, start + hardLimitMs, null);
 *
 * for (int depth = 1; !timeManagement.shouldSkipIteration(depth); depth++) {
 *     // search depth, then:
 *     timeManagement.iterationDone(depth, bestMove, weightCenti, System.currentTimeMillis());
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

    private static final int MIN_DEPTH_ENABLE_TIME_OPTIMIZATION = 4;

    private static final int MAX_EXTENSIONS = 2;

    private static final float HARD_LIMIT_SOFT_LIMIT_FACTOR = 4f;

    private static final float HARD_LIMIT_REMAINING_TIME_FACTOR = 0.2f;

    private static final int WEIGHT_DROP_THRESHOLD = 50;

    /** Log prefix of the lines reporting which difficulty signal fired, followed by the depth. */
    private static final String SIGNAL_LOG_PREFIX = "[time] signal depth ";

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
     * keeps track of the completed iterations (best-move changes, extensions used).
     *
     * <p>Not thread-safe and not reusable: create one per search via {@link #newInstance}, then call
     * {@link #shouldSkipIteration} and {@link #iterationDone} alternately for depth 1, 2, 3, …
     */
    public static final class TimeManagementInstance {
        private final long softTimeout;
        private final long hardTimeout;
        private final Integer previousOwnScoreCenti;
        private int depth = 1;
        private int previousIterationBestMove = 0;
        private int previousIterationWeightCenti;
        private int moveChangedCounter = 0;
        private int extraIterationsUsedCounter;
        private IterationDecision decisionOnSoftTimeout;
        private long iterationStartTimestamp;
        private boolean droppedBelowPreviousOwnScore;
        private boolean weightDropped;

        private TimeManagementInstance(long softTimeout, long hardTimeout, Integer previousOwnScoreCenti) {
            this.softTimeout = softTimeout;
            this.hardTimeout = hardTimeout;
            this.previousOwnScoreCenti = previousOwnScoreCenti;
        }

        /**
         * Decides whether the iterative deepening should stop before {@code currentDepth}.
         *
         * <p>Depth 1 always runs. For deeper iterations {@link IterationTimings#decideIteration}
         * compares the estimated cost of the depth with the time left until the soft limit; in a
         * position judged difficult, the time left until the hard limit is used instead (at most
         * {@link #MAX_EXTENSIONS} times per search, and only for depths above
         * {@link #MIN_DEPTH_ENABLE_TIME_OPTIMIZATION}).
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
        @SuppressWarnings({"java:S1192"})
        public boolean shouldSkipIteration(int currentDepth) {
            __assert(() -> currentDepth == depth, () -> "unexpected depth: expected " + depth + ", but is " + currentDepth);

            iterationStartTimestamp = System.currentTimeMillis();

            if (depth == 1) {
                decisionOnSoftTimeout = IterationDecision.RUN;
                return false;
            }

            final long now = System.currentTimeMillis();
            decisionOnSoftTimeout = IterationTimings.decideIteration(currentDepth, softTimeout - now);
            var decisionOnHardTimeout = IterationTimings.decideIteration(currentDepth, hardTimeout - now);

            final boolean useSoftTimeout = checkUseSoftTimeout();
            final long effectiveTimeout = useSoftTimeout ? softTimeout : hardTimeout;
            final var decision = useSoftTimeout ? decisionOnSoftTimeout : decisionOnHardTimeout;
            final long remainingMs = effectiveTimeout - now;
            final long estimateMs = IterationTimings.getEstimatedMs(depth);

            return recordAndLogSkip(decision, estimateMs, remainingMs);
        }

        @SuppressWarnings({"java:S6205", "java:S1192"})
        private boolean recordAndLogSkip(final IterationDecision decision, final long estimateMs, final long remainingMs) {
            return switch (decision) {
                case RUN -> false;
                case PROBE -> {
                    Log.info("[time] probe depth " + depth + ": est " + estimateMs + " ms > remaining " + remainingMs + " ms");
                    yield false;
                }
                case SKIP -> {
                    if (remainingMs > IterationTimings.MIN_REMAINING_MILLIS) {
                        IterationTimings.recordSkip(depth);
                        Log.info("[time] skip depth " + depth + ": est " + estimateMs + " ms > remaining " + remainingMs + " ms");
                    } else {
                        Log.info("[time] stop depth " + depth + ": limit reached");
                    }
                    yield true;
                }
            };
        }

        /**
         * Whether the start decision for the current depth is made against the soft limit.
         *
         * <p>{@code false} only for a difficult position, only for depths above
         * {@link #MIN_DEPTH_ENABLE_TIME_OPTIMIZATION} (the signals of that depth decide the next
         * one), and only while fewer than {@link #MAX_EXTENSIONS} extensions have been used; then
         * the hard limit decides.
         *
         * @return {@code true} to decide against the soft limit, {@code false} for the hard limit
         */
        boolean checkUseSoftTimeout() {
            if (depth <= MIN_DEPTH_ENABLE_TIME_OPTIMIZATION || extraIterationsUsedCounter >= MAX_EXTENSIONS) {
                return true;
            }

            return !isDifficultPosition();
        }

        boolean isDifficultPosition() {
            // A position looks difficult if the best move is unstable or the weight dropped significantly
            // TODO
            //return weightDropped || moveChangedCounter > 0;
            return false;
        }

        /**
         * Reports a completed iteration, so the next decision can take its result into account.
         *
         * <p>Records the iteration's duration, measured from the {@link #shouldSkipIteration} call
         * that started it, as a completed sample in {@link IterationTimings}. From depth
         * {@link #MIN_DEPTH_ENABLE_TIME_OPTIMIZATION} on, it updates the signals for the next
         * decision:
         * <ul>
         *   <li>best-move changes against the previous iteration (counted up, halved when the move
         *       stays the same);</li>
         *   <li>a score drop of at least {@link #WEIGHT_DROP_THRESHOLD} against the previous
         *       iteration, or against the previous own move's score. The latter fires at most once
         *       per search, at whichever depth the drop first appears.</li>
         * </ul>
         * It stores move and score, and counts an extension if the iteration ran only because the
         * position was judged difficult. An iteration ending past the soft limit is logged as
         * {@code [time] overrun}. Must not be called for an aborted iteration; use
         * {@link #iterationAborted} instead.
         *
         * @param currentDepth          the depth just completed, as passed to {@link #shouldSkipIteration}
         * @param move                  the best move of this iteration
         * @param weightCenti           its score in centipawns, from the side to move's point of view
         * @param iterationEndTimestamp the time the iteration ended, in epoch milliseconds
         */
        public void iterationDone(final int currentDepth, final int move, final int weightCenti, final long iterationEndTimestamp) {
            __assert(() -> decisionOnSoftTimeout != null, "iterationDone must be called after shouldSkipIteration");
            __assert(() -> currentDepth == depth, () -> "unexpected depth: expected " + depth + ", but is " + currentDepth);
            __assert(() -> !WeightingFunction.isIllegalWeight(weightCenti), "illegal weight");

            weightDropped = false;

            long iterationDurationMs = iterationEndTimestamp - iterationStartTimestamp;
            IterationTimings.recordCompletion(depth, iterationDurationMs);

            if (depth >= MIN_DEPTH_ENABLE_TIME_OPTIMIZATION) {
                calculateSignals(move, weightCenti);
            }

            previousIterationBestMove = move;
            previousIterationWeightCenti = weightCenti;

            boolean extended = decisionOnSoftTimeout == IterationDecision.SKIP;
            if (extended) {
                // Made an extra iteration
                extraIterationsUsedCounter++;
            }

            if (iterationEndTimestamp > softTimeout) {
                // Exceeded the soft timeout
                Log.info("[time] overrun depth " + depth + ": " + (iterationEndTimestamp - softTimeout)
                        + " ms past soft limit, extended=" + extended + ", extra iterations=" + extraIterationsUsedCounter);
            }

            depth++;
            decisionOnSoftTimeout = null;
        }

        private void calculateSignals(int move, int weightCenti) {
            if (WeightingFunction.isCheckmateWeight(weightCenti)) {
                // A forced mate, for or against us, is settled: more time does not change it. Both
                // signals would only report noise here. A mate score moves by 100 cp per ply of mate
                // distance, so a mate that merely gets one ply longer or shorter already crosses the
                // drop threshold. And when several moves mate, the best move may switch between
                // equivalent mating moves without any uncertainty. Hence, the early return skips the
                // move-change signal as well. The move and score of this iteration are still stored
                // by the caller, so a mate that vanishes in a later iteration fires as a regular drop.
                return;
            }

            if (previousIterationBestMove != move) {
                moveChangedCounter++;
                Log.info(SIGNAL_LOG_PREFIX + depth + ": best move changed, change counter " + moveChangedCounter);
            } else {
                moveChangedCounter = moveChangedCounter / 2;
            }

            if (!droppedBelowPreviousOwnScore
                    && previousOwnScoreCenti != null
                    && weightCenti + WEIGHT_DROP_THRESHOLD <= previousOwnScoreCenti) {
                droppedBelowPreviousOwnScore = true;
                weightDropped = true;
                Log.info(SIGNAL_LOG_PREFIX + depth + ": score dropped below the previous own move, "
                        + previousOwnScoreCenti + " -> " + weightCenti + " cp");
            } else if (weightCenti + WEIGHT_DROP_THRESHOLD <= previousIterationWeightCenti) {
                weightDropped = true;
                Log.info(SIGNAL_LOG_PREFIX + depth + ": score dropped against the previous iteration, "
                        + previousIterationWeightCenti + " -> " + weightCenti + " cp");
            }
        }

        /**
         * Reports an iteration that was aborted at the hard limit; its result is discarded.
         *
         * <p>Records the elapsed time, measured from the {@link #shouldSkipIteration} call that
         * started the iteration, with {@link IterationTimings#recordAbort}, which extrapolates it
         * to a lower bound of the full duration. Depth, best move and counters stay as they were
         * after the last completed iteration, so the search is expected to stop and return that
         * iteration's result.
         *
         * @param currentDepth          the depth that was aborted, as passed to {@link #shouldSkipIteration}
         * @param iterationEndTimestamp the time the iteration was aborted, in epoch milliseconds
         */
        public void iterationAborted(final int currentDepth, final long iterationEndTimestamp) {
            __assert(() -> decisionOnSoftTimeout != null, "iterationAborted must be called after shouldSkipIteration");
            __assert(() -> currentDepth == depth, () -> "unexpected depth: expected " + depth + ", but is " + currentDepth);

            long iterationDurationMs = iterationEndTimestamp - iterationStartTimestamp;
            IterationTimings.recordAbort(depth, iterationDurationMs);

            decisionOnSoftTimeout = null;
            weightDropped = false;
        }

        // for tests only
        int getDepth() {
            return depth;
        }

        // for tests only
        int getPreviousIterationBestMove() {
            return previousIterationBestMove;
        }

        // for tests only
        int getPreviousIterationWeightCenti() {
            return previousIterationWeightCenti;
        }

        // for tests only
        int getMoveChangedCounter() {
            return moveChangedCounter;
        }

        // for tests only
        int getExtraIterationsUsedCounter() {
            return extraIterationsUsedCounter;
        }

        // for tests only
        IterationDecision getDecisionOnSoftTimeout() {
            return decisionOnSoftTimeout;
        }

        // for tests only
        boolean isWeightDropped() {
            return weightDropped;
        }

        // for tests only
        boolean isDroppedBelowPreviousOwnScore() {
            return droppedBelowPreviousOwnScore;
        }
    }

    private TimeManagement() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Creates the time management for one search.
     *
     * @param softTimeout           epoch milliseconds of the soft limit: no new iteration is started
     *                              after it unless the position is judged difficult
     * @param hardTimeout           epoch milliseconds of the hard limit: no iteration is started that
     *                              is expected to end after it; the search aborts at this time anyway
     * @param previousOwnScoreCenti score of this side's previous search in centipawns, from its own
     *                              point of view, or {@code null} if none is comparable
     * @return a new instance, positioned before depth 1
     */
    public static TimeManagement.TimeManagementInstance newInstance(long softTimeout, long hardTimeout, Integer previousOwnScoreCenti) {
        return new TimeManagement.TimeManagementInstance(softTimeout, hardTimeout, previousOwnScoreCenti);
    }

    /**
     * Hard limit for one move: the time after which the search aborts a running iteration.
     *
     * <p>Without a clock ({@code go movetime}, {@code go depth}, {@code go infinite}) it equals the
     * soft limit, because there is no reserve to draw on and a GUI expects the answer within the
     * granted time. With a clock it is {@value #HARD_LIMIT_SOFT_LIMIT_FACTOR} times the soft limit,
     * but at most 20 % of the remaining clock and never more than the clock minus
     * {@link #TIME_SAFETY_MARGIN_MS}; never less than {@link #MIN_BUDGET_MS}.
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

        int timeLimit = Math.min(
                Math.round(softLimitMs * HARD_LIMIT_SOFT_LIMIT_FACTOR),
                Math.round(remainingClockMs * HARD_LIMIT_REMAINING_TIME_FACTOR));

        return Math.max(Math.min(timeLimit, remainingClockMs - TIME_SAFETY_MARGIN_MS), MIN_BUDGET_MS);
    }

    /**
     * Soft limit for one move, derived from the arguments of a UCI {@code go} command: the regular
     * search budget, after which no new iteration is started unless the position is judged difficult.
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
