package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.TimeManagement.TimeControlInput;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link TimeManagement}: the per-move budget derived from the {@code go} arguments
 * ({@link TimeManagement#computeMoveBudgetMillis}) and the clock formula behind it
 * ({@link TimeManagement#computeClockBudgetMillis}).
 *
 * <p>The expected values are worked out from the formula
 * {@code clock / (movesToGo + 1) + 80 % of increment}, capped at {@code clock - 50 ms} and floored
 * at 50 ms, with 30 as the default {@code movesToGo}. A change to any of these constants is meant
 * to turn the corresponding test red.
 *
 * @author Michael Fleischhauer
 */
class TimeManagementTest {

    /** 24 h in milliseconds: the budget for {@code go infinite} and for a {@code go} without a clock. */
    private static final int INFINITE_MILLIS = 24 * 60 * 60 * 1_000;

    /** Floor of every budget, and the safety margin subtracted from {@code movetime} and the clock. */
    private static final int FLOOR_MILLIS = 50;

    /** A one-minute clock, as in a 1+x bullet game. */
    private static final int ONE_MINUTE = 60_000;

    /** A one-second increment. */
    private static final int ONE_SECOND = 1_000;

    /** A one-minute clock spread over the default 30 moves: 60 000 / 31. */
    private static final int ONE_MINUTE_DEFAULT_RATE = 1_935;

    /** 80 % of {@link #ONE_SECOND}. */
    private static final int EIGHTY_PERCENT_OF_ONE_SECOND = 800;

    private static final int DEFAULT_MOVES_TO_GO = 30;

    // ---- computeMoveBudgetMillis: which go argument decides ----

    @Test
    void infiniteIgnoresEveryOtherArgument() {
        var input = new TimeControlInput(ONE_SECOND, ONE_MINUTE, null, null, true, null, null);

        assertEquals(INFINITE_MILLIS, TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_WHITE),
                "go infinite must give the 24 h budget even when movetime and a clock are also sent");
    }

    @Test
    void movetimeIsReducedByTheSafetyMargin() {
        var input = movetime(ONE_SECOND);

        assertEquals(ONE_SECOND - FLOOR_MILLIS, TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_WHITE),
                "go movetime 1000 must leave the 50 ms safety margin: 950 ms");
    }

    @Test
    void movetimeBelowTheMarginIsFloored() {
        var input = movetime(30);

        assertEquals(FLOOR_MILLIS, TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_WHITE),
                "go movetime 30 would go negative after the margin and must be floored at 50 ms");
    }

    @Test
    void movetimeTakesPrecedenceOverTheClock() {
        var input = new TimeControlInput(ONE_SECOND, ONE_MINUTE, null, null, false, null, null);

        assertEquals(ONE_SECOND - FLOOR_MILLIS, TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_WHITE),
                "with both movetime and wtime sent, movetime must decide");
    }

    @Test
    void withoutClockOrMovetimeTheBudgetIsUnbounded() {
        var input = new TimeControlInput(null, null, null, null, false, null, null);

        assertEquals(INFINITE_MILLIS, TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_WHITE),
                "go depth N (no clock, no movetime) must not be time-limited");
    }

    @Test
    void whiteUsesWtimeAndWinc() {
        var input = clockInput();

        assertEquals(ONE_MINUTE_DEFAULT_RATE + EIGHTY_PERCENT_OF_ONE_SECOND,
                TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_WHITE),
                "white to move: 60 000 / 31 + 80 % of the 1 s increment");
    }

    @Test
    void blackUsesBtimeAndBinc() {
        var input = clockInput();

        // btime 3000 / 31 = 96, plus 80 % of binc 500 = 400
        assertEquals(496, TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_BLACK),
                "black to move must read btime and binc, not white's clock");
    }

    @Test
    void incrementOfTheOtherSideIsIgnored() {
        var input = new TimeControlInput(null, null, ONE_MINUTE, null, false, ONE_SECOND, null);

        assertEquals(ONE_MINUTE_DEFAULT_RATE, TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_BLACK),
                "black to move with only winc sent must count no increment");
    }

    @Test
    void movestogoReplacesTheDefaultRate() {
        var input = new TimeControlInput(null, ONE_MINUTE, null, 40, false, null, null);

        assertEquals(ONE_MINUTE / 41, TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_WHITE),
                "an explicit movestogo 40 must spread the clock over 41 shares, not the default 31");
    }

    // ---- computeClockBudgetMillis: the formula ----

    @Test
    void clockWithoutIncrementUsesTheRateAlone() {
        assertEquals(ONE_MINUTE_DEFAULT_RATE, TimeManagement.computeClockBudgetMillis(ONE_MINUTE, 0, DEFAULT_MOVES_TO_GO),
                "60 s and no increment at the default rate: 60 000 / 31");
    }

    @Test
    void eightyPercentOfTheIncrementIsAdded() {
        assertEquals(ONE_MINUTE_DEFAULT_RATE + EIGHTY_PERCENT_OF_ONE_SECOND,
                TimeManagement.computeClockBudgetMillis(ONE_MINUTE, ONE_SECOND, DEFAULT_MOVES_TO_GO),
                "a 1 s increment adds 800 ms to the rate share");
    }

    @Test
    void budgetNeverExceedsTheClockMinusTheMargin() {
        // 500 / 31 + 800 = 816 would overrun a 500 ms clock: the increment is credited only after the move
        assertEquals(500 - FLOOR_MILLIS, TimeManagement.computeClockBudgetMillis(500, ONE_SECOND, DEFAULT_MOVES_TO_GO),
                "with 0.5 s left the budget must be capped at clock - 50 ms, increment or not");
    }

    @Test
    void emptyClockIsFlooredAtTheMinimum() {
        assertEquals(FLOOR_MILLIS, TimeManagement.computeClockBudgetMillis(0, 0, DEFAULT_MOVES_TO_GO),
                "an empty clock must still give the 50 ms floor");
    }

    @Test
    void oversteppedClockIsFlooredAtTheMinimum() {
        assertEquals(FLOOR_MILLIS, TimeManagement.computeClockBudgetMillis(-200, ONE_SECOND, DEFAULT_MOVES_TO_GO),
                "a negative clock reported by the GUI must still give the 50 ms floor");
    }

    @Test
    void movesToGoOfZeroSpendsTheWholeClockUpToTheMargin() {
        // movestogo 0 would mean "this is the last move before the control": clock / 1, capped by the margin
        assertEquals(ONE_MINUTE - FLOOR_MILLIS, TimeManagement.computeClockBudgetMillis(ONE_MINUTE, 0, 0),
                "movesToGo 0 divides by 1 and must be capped at clock - 50 ms");
    }

    /** Only {@code movetime} sent. */
    private static TimeControlInput movetime(int millis) {
        return new TimeControlInput(millis, null, null, null, false, null, null);
    }

    /** White 60 s + 1 s, black 3 s + 0.5 s, no movestogo. */
    private static TimeControlInput clockInput() {
        return new TimeControlInput(null, ONE_MINUTE, 3_000, null, false, ONE_SECOND, 500);
    }
}
