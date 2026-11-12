package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.TimeManagement.TimeControlInput;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the blended phase time management of {@link TimeManagement}: the movestogo
 * fallback over the material phase ({@link TimeManagement#phaseMovesToGo}) and the budget built
 * from it ({@link TimeManagement#computePhaseBudgetMillis}).
 *
 * <p>The phase counts knight and bishop 1, rook 2 and queen 4, out of 24. The fallback is a table
 * over the phase. Its values follow a curve that is linear between the support points 1.0:120,
 * 0.85:15, 0.4:15, 0.3:20 and 0:20, rounded to whole moves. There is no depth limit in any phase.
 *
 * @author Michael Fleischhauer
 */
class TimeManagementPhaseTest {

    /** Ten minutes on the clock. */
    private static final int TEN_MINUTES = 600_000;

    /** Full material: 24. */
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** Queen and rook against rook and knight: 9. */
    private static final String PHASE_9_FEN = "4k3/pppr1ppp/4n3/8/8/8/PPP2PPP/3QR1K1 w - - 0 1";

    /** Two rooks each: 8. */
    private static final String PHASE_8_FEN = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1";

    // ---- the material phase of a position ----

    @Test
    void fullMaterialIsPhase24() {
        assertEquals(24, phaseOf(START_FEN));
    }

    @Test
    void queenAndRookAgainstRookAndKnightIsPhase9() {
        assertEquals(9, phaseOf(PHASE_9_FEN));
    }

    // ---- phaseMovesToGo at and between the support points ----

    @Test
    void fullMaterialSpreadsTheClockOver120Moves() {
        assertEquals(120, TimeManagement.phaseMovesToGo(24));
    }

    @Test
    void phase21LiesASixthOfTheWayFrom15To120() {
        assertEquals(33, TimeManagement.phaseMovesToGo(21),
                "21 of 24 is 0.875: 15 + 105 x (0.025 / 0.15) = 32.5, rounded to 33");
    }

    @Test
    void middlegameSpreadsTheClockOver15Moves() {
        assertEquals(15, TimeManagement.phaseMovesToGo(20), "20 of 24 is 0.83, between 0.4 and 0.85");
        assertEquals(15, TimeManagement.phaseMovesToGo(10), "10 of 24 is 0.42, between 0.4 and 0.85");
    }

    @Test
    void theEndgameBlendFrom15To20() {
        assertEquals(16, TimeManagement.phaseMovesToGo(9), "9 of 24 is 0.375: 20 - 5 x 0.75 = 16.25");
        assertEquals(18, TimeManagement.phaseMovesToGo(8), "8 of 24 is 0.33: 20 - 5 x 0.33 = 18.3");
    }

    @Test
    void endgameSpreadsTheClockOver20Moves() {
        assertEquals(20, TimeManagement.phaseMovesToGo(7), "7 of 24 is 0.29, below 0.3");
        assertEquals(20, TimeManagement.phaseMovesToGo(0));
    }

    @Test
    void phaseOutsideTheTableIsClamped() {
        assertEquals(120, TimeManagement.phaseMovesToGo(30), "more than full material, after promotions, counts as 24");
        assertEquals(20, TimeManagement.phaseMovesToGo(-1));
    }

    // ---- computePhaseBudgetMillis ----

    @Test
    void budgetWithFullMaterial() {
        assertEquals(TEN_MINUTES / 121, budget(24), "600 000 / (120 + 1)");
    }

    @Test
    void budgetInTheMiddlegame() {
        assertEquals(TEN_MINUTES / 16, budget(15), "600 000 / (15 + 1)");
    }

    @Test
    void movestogoFromTheGuiOverridesThePhase() {
        var input = new TimeControlInput(null, TEN_MINUTES, TEN_MINUTES, 9, false, null, null);

        assertEquals(TEN_MINUTES / 10, TimeManagement.computePhaseBudgetMillis(input, GameStatus.TURN_WHITE, 24),
                "a movestogo sent by the GUI must win over the phase fallback");
    }

    private static int phaseOf(String fen) {
        return WeightingFunction.gamePhase(Fen.importFEN(fen));
    }

    private static int budget(int phase) {
        var clock = new TimeControlInput(null, TEN_MINUTES, TEN_MINUTES, null, false, null, null);

        return TimeManagement.computePhaseBudgetMillis(clock, GameStatus.TURN_WHITE, phase);
    }
}
