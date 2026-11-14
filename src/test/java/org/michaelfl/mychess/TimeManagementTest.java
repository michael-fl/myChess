package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.TimeManagement.TimeControlInput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /** Close to the soft limit at the start of a 60+1 game (60 000 / 31 + 800 = 2,735 ms), rounded to 2.5 s for readable sums. */
    private static final int TWO_AND_A_HALF_SECONDS = 2_500;

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

    /** The hard-limit tests below are written for the fixed one-second overrun; the share is tested separately. */
    @BeforeEach
    void useTheFixedOverrun() {
        TimeManagement.setOverrunPercent(TimeManagement.FIXED_OVERRUN_PERCENT);
    }

    @AfterEach
    void restoreTheDefaultOverrun() {
        TimeManagement.setOverrunPercent(TimeManagement.DEFAULT_OVERRUN_PERCENT);
    }

    // ---- calculateHardLimit (variant finish-iteration: a move may use at most 25 % of the clock) ----

    @Test
    void hardLimit_withAClock_isAQuarterOfIt() {
        assertEquals(15_000, TimeManagement.calculateHardLimit(ONE_SECOND, ONE_MINUTE),
                "with 60 s on the clock a started iteration may run until the move has used 15 s");
    }

    @Test
    void hardLimit_withAClock_doesNotDependOnTheSoftLimit() {
        assertEquals(15_000, TimeManagement.calculateHardLimit(200, ONE_MINUTE),
                "a small soft limit must not shorten the hard limit");
        assertEquals(15_000, TimeManagement.calculateHardLimit(ONE_MINUTE, ONE_MINUTE),
                "a soft limit as large as the clock must not raise the hard limit");
    }

    @Test
    void hardLimit_withShortClock_isAQuarterOfIt() {
        assertEquals(300, TimeManagement.calculateHardLimit(ONE_SECOND, 1_200),
                "with 1.2 s left, a quarter (300 ms) is below the clock minus the 50 ms margin");
    }

    @Test
    void hardLimit_withNearlyEmptyClock_isFlooredAtTheMinimum() {
        assertEquals(FLOOR_MILLIS, TimeManagement.calculateHardLimit(ONE_SECOND, 100),
                "with 100 ms left, a quarter (25 ms) is below the 50 ms floor, so the floor must win");
    }

    @Test
    void hardLimit_withAClock_ignoresTheOverrunSetting() {
        try {
            TimeManagement.setOverrunPercent(500);

            assertEquals(15_000, TimeManagement.calculateHardLimit(ONE_SECOND, ONE_MINUTE),
                    "the overrun setting must have no effect on the hard limit in this variant");
        } finally {
            TimeManagement.setOverrunPercent(TimeManagement.FIXED_OVERRUN_PERCENT);
        }
    }

    @Test
    void hardLimit_withoutClock_equalsTheSoftLimit() {
        assertEquals(ONE_SECOND, TimeManagement.calculateHardLimit(ONE_SECOND, Integer.MAX_VALUE),
                "without a clock (Integer.MAX_VALUE) there is no reserve to draw on, so hard must equal soft");
    }

    @Test
    void hardLimit_forMovetime_staysWithinTheGrantedTime() {
        int softLimit = TimeManagement.computeMoveBudgetMillis(movetime(ONE_SECOND), GameStatus.TURN_WHITE);

        assertEquals(softLimit, TimeManagement.calculateHardLimit(softLimit, Integer.MAX_VALUE),
                "go movetime 1000 sends no clock: the hard limit must not let the search run past the granted second");
    }

    @Test
    void hardLimit_withoutClockAndInfiniteBudget_doesNotOverflow() {
        assertEquals(INFINITE_MILLIS, TimeManagement.calculateHardLimit(INFINITE_MILLIS, Integer.MAX_VALUE),
                "go infinite / go depth must keep the infinite budget unchanged, not multiply it");
    }

    // ---- the overrun as a share of the soft limit (TimeOverrunPercent) ----

    @Test
    void overrunPercent_fixedSetting_keepsTheFixedSecond() {
        assertEquals(TimeManagement.SOFT_LIMIT_MAX_OVERRUN_MS, TimeManagement.maxOverrunMillis(TWO_AND_A_HALF_SECONDS),
                "the fixed setting must give one second, whatever the soft limit");
    }

    @Test
    void overrunPercent_default_isTheMeasuredFiftyFivePercent() {
        TimeManagement.setOverrunPercent(TimeManagement.DEFAULT_OVERRUN_PERCENT);

        assertEquals(55, TimeManagement.DEFAULT_OVERRUN_PERCENT, "the default must stay the measured 55 %");
        assertEquals(1_100, TimeManagement.maxOverrunMillis(2_000),
                "with the default, a 2 s soft limit gets 55 % (1.1 s) of overrun");
    }

    @Test
    void setOverrunPercent_negative_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> TimeManagement.setOverrunPercent(-1),
                "a negative overrun would put the hard limit before the soft limit and must be rejected");
        assertEquals(TimeManagement.FIXED_OVERRUN_PERCENT, TimeManagement.getOverrunPercent(),
                "a rejected value must leave the setting unchanged");
    }

    // ---- TimeManagementInstance: bookkeeping per completed iteration ----

    @Test
    void iterationDone_advancesTheDepth() {
        long now = System.currentTimeMillis();
        var instance = TimeManagement.newInstance(now + ONE_SECOND, "");

        instance.shouldSkipIteration(1);
        instance.iterationDone(1, System.currentTimeMillis());

        assertEquals(2, instance.getDepth(), "after depth 1 the next expected depth must be 2");
    }

    /** Only {@code movetime} sent. */
    private static TimeControlInput movetime(int millis) {
        return new TimeControlInput(millis, null, null, null, false, null, null);
    }

    /** White 60 s + 1 s, black 3 s + 0.5 s, no movestogo. */
    private static TimeControlInput clockInput() {
        return new TimeControlInput(null, ONE_MINUTE, 3_000, null, false, ONE_SECOND, 500);
    }

    @Test
    void setRiskFactor_zeroOrNegative_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> TimeManagement.setRiskFactor(0),
                "a risk factor of zero would never start an iteration and must be rejected");
        assertThrows(IllegalArgumentException.class, () -> TimeManagement.setRiskFactor(-1),
                "a negative risk factor must be rejected");
        assertEquals(TimeManagement.DEFAULT_RISK_FACTOR, TimeManagement.getRiskFactor(), 1e-9,
                "a rejected value must leave the factor unchanged");
    }

    @Test
    void riskFactor_default_isTheMeasuredOneAndAHalf() {
        assertEquals(1.5, TimeManagement.DEFAULT_RISK_FACTOR, 1e-9,
                "the default risk factor must be 1.5, the value measured in the honest-k150 match");
    }
}
