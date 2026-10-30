package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.TimeManagement.TimeControlInput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /** A non-zero move code; the bookkeeping does not look at it. */
    private static final int MOVE_A = 1;

    /** First depth whose result is compared with the previous own move: the minimum depth 4. */
    private static final int FIRST_SIGNAL_DEPTH = 4;

    /** Score of the previous own move: three pawns. */
    private static final int PREVIOUS_OWN_SCORE_CENTI = 300;

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

    // ---- calculateHardLimit ----

    @Test
    void hardLimit_withAmpleClock_isTheSoftLimitPlusTheMaxOverrun() {
        assertEquals(ONE_SECOND + TimeManagement.SOFT_LIMIT_MAX_OVERRUN_MS,
                TimeManagement.calculateHardLimit(ONE_SECOND, ONE_MINUTE),
                "1 s soft limit and 60 s on the clock: soft + 1 s (2 s) is below 4 x soft (4 s) and 20 % of the clock (12 s)");
    }

    @Test
    void hardLimit_withTinySoftLimit_isCappedAtFourTimesIt() {
        // 200 ms soft limit: soft + 1 s = 1.2 s, but 4 x soft = 0.8 s is smaller
        assertEquals(800, TimeManagement.calculateHardLimit(200, ONE_MINUTE),
                "for a soft limit below a third of a second, 4 x soft must cap the hard limit below soft + 1 s");
    }

    @Test
    void hardLimit_withShortClock_isCappedAtAFifthOfTheClock() {
        assertEquals(1_000, TimeManagement.calculateHardLimit(ONE_SECOND, 5_000),
                "with 5 s left, 20 % of the clock (1 s) must cap the hard limit below soft + 1 s (2 s)");
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

    @Test
    void hardLimit_withNearlyEmptyClock_isFlooredAtTheMinimum() {
        assertEquals(FLOOR_MILLIS, TimeManagement.calculateHardLimit(ONE_SECOND, 100),
                "20 ms (20 % of 100 ms) is below the 50 ms floor, so the floor must win");
    }

    @Test
    void hardLimit_softLimitAsLargeAsTheClock_isCappedAtAFifthOfIt() {
        int hardLimit = TimeManagement.calculateHardLimit(ONE_MINUTE, ONE_MINUTE);

        assertEquals(12_000, hardLimit, "a soft limit as large as the clock must be capped at 20 % of it");
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
        assertEquals(2_000 + 1_100, TimeManagement.calculateHardLimit(2_000, ONE_MINUTE),
                "with the default, a 2 s soft limit gets 55 % (1.1 s) of overrun, below 4 x soft and 20 % of the clock");
    }

    @Test
    void hardLimit_withOverrunPercent_isTheSoftLimitPlusThatShare() {
        try {
            TimeManagement.setOverrunPercent(50);

            assertEquals(TWO_AND_A_HALF_SECONDS + 1_250, TimeManagement.calculateHardLimit(TWO_AND_A_HALF_SECONDS, ONE_MINUTE),
                    "50 % of a 2.5 s soft limit is 1.25 s, below 4 x soft (10 s) and 20 % of the clock (12 s)");
        } finally {
            TimeManagement.setOverrunPercent(TimeManagement.FIXED_OVERRUN_PERCENT);
        }
    }

    @Test
    void hardLimit_withOverrunPercent_scalesWithTheSoftLimit() {
        try {
            TimeManagement.setOverrunPercent(50);

            assertEquals(15_000, TimeManagement.calculateHardLimit(10_000, 10 * ONE_MINUTE),
                    "at a long control a 10 s soft limit gets 5 s overrun, where the fixed setting would allow only 1 s");
        } finally {
            TimeManagement.setOverrunPercent(TimeManagement.FIXED_OVERRUN_PERCENT);
        }
    }

    @Test
    void hardLimit_withLargeOverrunPercent_isStillCappedAtFourTimesTheSoftLimit() {
        try {
            TimeManagement.setOverrunPercent(500);

            assertEquals(4_000, TimeManagement.calculateHardLimit(ONE_SECOND, ONE_MINUTE),
                    "an overrun of 500 % would give 6 s, but 4 x the soft limit must still cap it at 4 s");
        } finally {
            TimeManagement.setOverrunPercent(TimeManagement.FIXED_OVERRUN_PERCENT);
        }
    }

    @Test
    void hardLimit_withOverrunPercentAndShortClock_isStillCappedAtAFifthOfTheClock() {
        try {
            TimeManagement.setOverrunPercent(100);

            assertEquals(1_000, TimeManagement.calculateHardLimit(ONE_SECOND, 5_000),
                    "with 5 s left, 20 % of the clock (1 s) must still cap the hard limit below soft + 100 % (2 s)");
        } finally {
            TimeManagement.setOverrunPercent(TimeManagement.FIXED_OVERRUN_PERCENT);
        }
    }

    @Test
    void hardLimit_withOverrunPercentButNoClock_equalsTheSoftLimit() {
        try {
            TimeManagement.setOverrunPercent(50);

            assertEquals(ONE_SECOND, TimeManagement.calculateHardLimit(ONE_SECOND, Integer.MAX_VALUE),
                    "without a clock the hard limit must equal the soft limit whatever the overrun setting");
        } finally {
            TimeManagement.setOverrunPercent(TimeManagement.FIXED_OVERRUN_PERCENT);
        }
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
        var instance = newInstance();

        runIteration(instance, 1, MOVE_A, 0.35f);

        assertEquals(2, instance.getDepth(), "after depth 1 the next expected depth must be 2");
    }

    // ---- TimeManagementInstance: the drop below the previous own move (V5) ----

    @Test
    void dropBelowPreviousOwnScore_beforeTheMinimumDepth_isNotFlagged() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 2.5f);

        assertFalse(instance.isDroppedBelowPreviousOwnScore(),
                "a drop up to depth 3 must not count; the signal starts at depth " + FIRST_SIGNAL_DEPTH);
        assertFalse(instance.isDifficultPosition(), "before the minimum depth no position is difficult");
    }

    @Test
    void dropBelowPreviousOwnScore_byTheThreshold_marksThePositionDifficult() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 2.5f);

        assertTrue(instance.isDroppedBelowPreviousOwnScore(),
                "300 cp at the previous move and 250 cp now is a drop of exactly 50 cp");
        assertTrue(instance.isDifficultPosition(), "the drop below the previous own score makes the position difficult");
    }

    @Test
    void dropBelowPreviousOwnScore_belowTheThreshold_isNotFlagged() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 2.51f);

        assertFalse(instance.isDifficultPosition(), "a drop of 49 cp is below the 50 cp threshold");
    }

    @Test
    void riseAbovePreviousOwnScore_isNotFlagged() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 5f);

        assertFalse(instance.isDifficultPosition(), "a rising score is no reason to think longer");
    }

    @Test
    void dropBelowPreviousOwnScore_isJudgedOnTheLastCompletedIteration() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 2.5f);

        assertTrue(instance.isDifficultPosition(), "test premise: depth " + FIRST_SIGNAL_DEPTH + " shows the drop");

        runIteration(instance, FIRST_SIGNAL_DEPTH + 1, MOVE_A, 2.9f);

        assertFalse(instance.isDifficultPosition(),
                "when a deeper iteration recovers to within 50 cp, the early drop must no longer count: "
                        + "the signal is measured at the last completed depth");
    }

    @Test
    void dropBelowPreviousOwnScore_canFireAtALaterDepth() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 2.8f);
        runIteration(instance, FIRST_SIGNAL_DEPTH + 1, MOVE_A, 2.6f);

        assertFalse(instance.isDifficultPosition(), "test premise: 40 cp below the previous move is not yet a drop of 50 cp");

        runIteration(instance, FIRST_SIGNAL_DEPTH + 2, MOVE_A, 2.5f);

        assertTrue(instance.isDifficultPosition(),
                "a drop below the previous move's score found only at a deeper iteration must still fire");
    }

    @Test
    void withoutPreviousOwnScore_nothingIsFlagged() {
        var instance = newInstance();

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, -10f);

        assertFalse(instance.isDifficultPosition(), "without a previous score there is nothing to drop below");
    }

    @Test
    void iterationAborted_clearsTheDifficulty() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 2.5f);
        instance.shouldSkipIteration(FIRST_SIGNAL_DEPTH + 1);
        instance.iterationAborted(FIRST_SIGNAL_DEPTH + 1, System.currentTimeMillis());

        assertFalse(instance.isDifficultPosition(), "after an aborted iteration the search ends; no signal must remain");
    }

    // ---- TimeManagementInstance: mate scores ----

    @Test
    void mateAgainstUsAppearing_isNoDropBelowThePreviousOwnScore() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIterationCenti(instance, FIRST_SIGNAL_DEPTH, MOVE_A, -WeightingFunction.checkmateInCenti(4));

        assertFalse(instance.isDifficultPosition(), "a forced mate against us is settled; more time does not change it");
    }

    @Test
    void mateOfThePreviousOwnMoveVanishing_isADropBelowIt() {
        var instance = newInstance(WeightingFunction.checkmateInCenti(5));

        runStableIterations(instance, MOVE_A, 8);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 8f);

        assertTrue(instance.isDifficultPosition(),
                "a mate announced at the previous move that is gone now must fire the drop against it");
    }

    /** Soft limit 1 s from now, a clock large enough not to cap the hard limit, no previous score. */
    private static TimeManagement.TimeManagementInstance newInstance() {
        return newInstance(null);
    }

    /** Soft limit 1 s from now, a clock large enough not to cap the hard limit, with the given score of the previous own move. */
    private static TimeManagement.TimeManagementInstance newInstance(Integer previousOwnScoreCenti) {
        return TimeManagement.newInstance(System.currentTimeMillis(), ONE_SECOND, 100 * ONE_MINUTE, previousOwnScoreCenti, "");
    }

    /** Runs depths 1 up to the one before {@link #FIRST_SIGNAL_DEPTH}, all with the same move and score. */
    private static void runStableIterations(TimeManagement.TimeManagementInstance instance, int move, float weight) {
        runStableIterationsCenti(instance, move, Math.round(weight * 100));
    }

    /** As {@link #runStableIterations}, with the score in centipawns, e.g. a mate score. */
    private static void runStableIterationsCenti(TimeManagement.TimeManagementInstance instance, int move, int weightCenti) {
        for (int depth = 1; depth < FIRST_SIGNAL_DEPTH; depth++) {
            runIterationCenti(instance, depth, move, weightCenti);
        }
    }

    /** Asks for the decision at {@code depth} and then reports the iteration as completed. */
    private static void runIteration(TimeManagement.TimeManagementInstance instance, int depth, int move, float weight) {
        runIterationCenti(instance, depth, move, Math.round(weight * 100));
    }

    /** As {@link #runIteration}, with the score in centipawns, e.g. a mate score. */
    private static void runIterationCenti(TimeManagement.TimeManagementInstance instance, int depth, int move, int weightCenti) {
        instance.shouldSkipIteration(depth);
        instance.iterationDone(depth, move, weightCenti, System.currentTimeMillis());
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
