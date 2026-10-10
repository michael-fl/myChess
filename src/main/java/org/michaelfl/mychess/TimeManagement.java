package org.michaelfl.mychess;

/**
 * Turns the time arguments of a UCI {@code go} command into a search budget for one move.
 *
 * <p>Two levels: {@link #computeMoveBudgetMillis} decides which argument governs the move
 * ({@code infinite}, {@code movetime}, the clock, or none), and {@link #computeClockBudgetMillis}
 * holds the clock formula: a share of the remaining time plus most of the increment, capped by
 * the clock itself and floored at a minimum.
 *
 * <p>Usage, as in {@link UciHandler}:
 * <pre>{@code
 * var input = new TimeManagement.TimeControlInput(null, 60_000, 60_000, null, false, 1_000, 1_000);
 * int budgetMillis = TimeManagement.computeMoveBudgetMillis(input, board.getGameStatus().getTurn());
 * }</pre>
 *
 * <p>Stateless: the budget depends only on the arguments of the current {@code go} command.
 *
 * @author Michael Fleischhauer
 */
final class TimeManagement {

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

    private TimeManagement() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Search budget for one move, derived from the arguments of a UCI {@code go} command.
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
     * @return the budget for this move in milliseconds, at least {@link #MIN_BUDGET_MS}
     */
    public static int computeMoveBudgetMillis(TimeControlInput input, int turn) {
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
