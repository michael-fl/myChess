package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.TimeManagement.TimeControlInput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** 80 % of {@link #ONE_SECOND}. */
    private static final int EIGHTY_PERCENT_OF_ONE_SECOND = 800;

    private static final int DEFAULT_MOVES_TO_GO = 30;

    /** Two distinct non-zero move codes; the bookkeeping only compares them. */
    private static final int MOVE_A = 1;
    private static final int MOVE_B = 2;

    /** First depth whose result is compared with the one before: the minimum depth 4. */
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

    // ---- calculateHardLimit ----

    @Test
    void hardLimit_withAmpleClock_isFourTimesTheSoftLimit() {
        assertEquals(4_000, TimeManagement.calculateHardLimit(ONE_SECOND, ONE_MINUTE),
                "1 s soft limit and 60 s on the clock: 4 x soft (4 s) is below 20 % of the clock (12 s)");
    }

    @Test
    void hardLimit_withShortClock_isCappedAtAFifthOfTheClock() {
        assertEquals(2_000, TimeManagement.calculateHardLimit(ONE_SECOND, 10_000),
                "with 10 s left, 20 % of the clock (2 s) must cap the hard limit below 4 x soft (4 s)");
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

    // ---- TimeManagementInstance: bookkeeping per completed iteration ----

    @Test
    void iterationDone_advancesTheDepthAndStoresMoveAndScore() {
        var instance = newInstance();

        runIteration(instance, 1, MOVE_A, 0.35f);

        assertEquals(2, instance.getDepth(), "after depth 1 the next expected depth must be 2");
        assertEquals(MOVE_A, instance.getPreviousIterationBestMove(), "the best move of depth 1 must be stored");
        assertEquals(35, instance.getPreviousIterationWeightCenti(), "the score of depth 1 must be stored in centipawns");
    }

    @Test
    void iterationDone_atDepthOne_neverCountsAMoveChange() {
        var instance = newInstance();

        runIteration(instance, 1, MOVE_A, 0f);

        assertEquals(0, instance.getMoveChangedCounter(),
                "depth 1 has no previous best move, so it cannot count as a change");
    }

    @Test
    void iterationDone_upToTheMinimumDepth_countsNoMoveChange() {
        var instance = newInstance();

        runIteration(instance, 1, MOVE_A, 0f);
        runIteration(instance, 2, MOVE_B, 0f);
        runIteration(instance, 3, MOVE_A, 0f);

        assertEquals(0, instance.getMoveChangedCounter(),
                "best-move changes up to depth 3 must not count; the signals start at depth " + FIRST_SIGNAL_DEPTH);
    }

    @Test
    void iterationDone_newBestMove_countsAChange() {
        var instance = newInstance();

        runStableIterations(instance, MOVE_A, 0);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_B, 0f);

        assertEquals(1, instance.getMoveChangedCounter(),
                "a different best move at depth " + FIRST_SIGNAL_DEPTH + " must count as one change");
    }

    @Test
    void iterationDone_sameBestMove_halvesTheChangeCounter() {
        var instance = newInstance();

        runStableIterations(instance, MOVE_A, 0);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_B, 0f);
        runIteration(instance, FIRST_SIGNAL_DEPTH + 1, MOVE_A, 0f);
        runIteration(instance, FIRST_SIGNAL_DEPTH + 2, MOVE_A, 0f);

        assertEquals(1, instance.getMoveChangedCounter(),
                "two changes then one stable iteration must decay the counter from 2 to 1");
    }

    // ---- TimeManagementInstance: score-drop signals ----

    @Test
    void weightDrop_beforeTheMinimumDepth_isNotFlagged() {
        var instance = newInstance();

        runIteration(instance, 1, MOVE_A, 0f);
        runIteration(instance, 2, MOVE_A, -5f);

        assertFalse(instance.isWeightDropped(), "a drop at depth 2 is below the minimum depth and must not count");
    }

    @Test
    void weightDrop_byTheThresholdAgainstThePreviousIteration_isFlagged() {
        var instance = newInstance();

        runStableIterations(instance, MOVE_A, 0);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, -0.5f);

        assertTrue(instance.isWeightDropped(),
                "a drop of exactly 50 cp against the previous iteration must flag the next decision");
    }

    @Test
    void weightDrop_belowTheThreshold_isNotFlagged() {
        var instance = newInstance();

        runStableIterations(instance, MOVE_A, 0);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, -0.49f);

        assertFalse(instance.isWeightDropped(), "a drop of 49 cp is below the 50 cp threshold");
    }

    @Test
    void weightRise_isNotFlagged() {
        var instance = newInstance();

        runStableIterations(instance, MOVE_A, 0);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 5f);

        assertFalse(instance.isWeightDropped(), "a rising score is no reason to think longer");
    }

    @Test
    void weightDrop_holdsOnlyUntilTheNextIteration() {
        var instance = newInstance();

        runStableIterations(instance, MOVE_A, 0);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, -0.5f);
        runIteration(instance, FIRST_SIGNAL_DEPTH + 1, MOVE_A, -0.5f);

        assertFalse(instance.isWeightDropped(), "a score that stays at its new level is no new drop");
    }

    @Test
    void dropBelowPreviousOwnScore_isFlaggedAndUsedUp() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 2.5f);

        assertTrue(instance.isDroppedBelowPreviousOwnScore(),
                "300 cp at the previous move and 250 cp now is a drop of 50 cp");
        assertTrue(instance.isWeightDropped(), "the drop below the previous own score must flag the next decision");
    }

    @Test
    void dropBelowPreviousOwnScore_firesOnlyOncePerMove() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 2.5f);
        runIteration(instance, FIRST_SIGNAL_DEPTH + 1, MOVE_A, 2.5f);

        assertFalse(instance.isWeightDropped(),
                "once used up, a score that stays below the previous move's must not flag again");
    }

    @Test
    void dropBelowPreviousOwnScore_canFireAtALaterDepth() {
        var instance = newInstance(PREVIOUS_OWN_SCORE_CENTI);

        runStableIterations(instance, MOVE_A, 3);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, 2.8f);
        runIteration(instance, FIRST_SIGNAL_DEPTH + 1, MOVE_A, 2.6f);

        assertFalse(instance.isDroppedBelowPreviousOwnScore(),
                "test premise: 40 cp below the previous move is not yet a drop of 50 cp");

        runIteration(instance, FIRST_SIGNAL_DEPTH + 2, MOVE_A, 2.5f);

        assertTrue(instance.isDroppedBelowPreviousOwnScore(),
                "a drop below the previous move's score found only at a deeper iteration must still fire");
        assertTrue(instance.isWeightDropped(), "the late drop must flag the next decision");
    }

    @Test
    void withoutPreviousOwnScore_onlyTheIterationDropCounts() {
        var instance = newInstance();

        runStableIterations(instance, MOVE_A, -10);
        runIteration(instance, FIRST_SIGNAL_DEPTH, MOVE_A, -10f);

        assertFalse(instance.isDroppedBelowPreviousOwnScore(), "without a previous score there is nothing to drop below");
        assertFalse(instance.isWeightDropped(), "a stable score without a previous move's score must not flag");
    }

    @Test
    void iterationDone_withoutSkipDecision_usesNoExtension() {
        var instance = newInstance();

        runIteration(instance, 1, MOVE_A, 0f);
        runIteration(instance, 2, MOVE_B, 0f);

        assertEquals(0, instance.getExtraIterationsUsedCounter(),
                "iterations the soft limit allowed anyway must not be counted as extensions");
    }

    /** Soft limit 1 s, hard limit 4 s from now, no previous score. */
    private static TimeManagement.TimeManagementInstance newInstance() {
        return newInstance(null);
    }

    /** Soft limit 1 s, hard limit 4 s from now, with the given score of the previous own move. */
    private static TimeManagement.TimeManagementInstance newInstance(Integer previousOwnScoreCenti) {
        long now = System.currentTimeMillis();

        return TimeManagement.newInstance(now + ONE_SECOND, now + 4 * ONE_SECOND, previousOwnScoreCenti);
    }

    /** Runs depths 1 up to the one before {@link #FIRST_SIGNAL_DEPTH}, all with the same move and score. */
    private static void runStableIterations(TimeManagement.TimeManagementInstance instance, int move, float weight) {
        for (int depth = 1; depth < FIRST_SIGNAL_DEPTH; depth++) {
            runIteration(instance, depth, move, weight);
        }
    }

    /** Asks for the decision at {@code depth} and then reports the iteration as completed. */
    private static void runIteration(TimeManagement.TimeManagementInstance instance, int depth, int move, float weight) {
        instance.shouldSkipIteration(depth);
        instance.iterationDone(depth, move, Math.round(weight * 100), System.currentTimeMillis());
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
