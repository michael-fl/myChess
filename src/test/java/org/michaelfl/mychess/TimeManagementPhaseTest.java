package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.TimeManagement.GamePhase;
import org.michaelfl.mychess.TimeManagement.TimeControlInput;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the phase time management of {@link TimeManagement}: the game phase of a
 * position ({@link TimeManagement#gamePhaseOf}), the budget per phase and the depth limit in the
 * opening ({@link TimeManagement#maxDepthFor}).
 *
 * <p>The phase counts knight and bishop 1, rook 2 and queen 4, out of 24. Opening is 21 to 24,
 * endgame 0 to 8, middlegame 9 to 20. The middlegame spreads the clock over 15 moves, the other
 * phases over 30. The opening searches at most depth 8 when a clock governs the move.
 *
 * @author Michael Fleischhauer
 */
class TimeManagementPhaseTest {

    /** Ten minutes on the clock. */
    private static final int TEN_MINUTES = 600_000;

    /** A {@code go} without a depth limit. */
    private static final int NO_DEPTH_LIMIT = Integer.MAX_VALUE;

    /** Full material: 24. */
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** One knight and one rook gone: 21, the lowest opening phase. */
    private static final String PHASE_21_FEN = "1nbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/R1BQKBNR w KQk - 0 1";

    /** Both a-rooks gone: 20, the highest middlegame phase. */
    private static final String PHASE_20_FEN = "1nbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/1NBQKBNR w Kk - 0 1";

    /** Queen and rook against rook and knight: 9, the lowest middlegame phase. */
    private static final String PHASE_9_FEN = "4k3/pppr1ppp/4n3/8/8/8/PPP2PPP/3QR1K1 w - - 0 1";

    /** Two rooks each: 8, the highest endgame phase. */
    private static final String PHASE_8_FEN = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1";

    /** Kings and pawns only: 0. */
    private static final String PAWN_ENDGAME_FEN = "4k3/pppp4/8/8/8/8/4PPPP/4K3 w - - 0 1";

    // ---- gamePhaseOf ----

    @Test
    void fullMaterialIsOpening() {
        assertEquals(GamePhase.OPENING, phaseOf(START_FEN));
    }

    @Test
    void phase21IsStillOpening() {
        assertEquals(GamePhase.OPENING, phaseOf(PHASE_21_FEN),
                "21 of 24 is 0.875, at or above 0.85: still opening");
    }

    @Test
    void phase20IsMiddlegame() {
        assertEquals(GamePhase.MIDDLEGAME, phaseOf(PHASE_20_FEN),
                "20 of 24 is 0.83, below 0.85: middlegame");
    }

    @Test
    void phase9IsStillMiddlegame() {
        assertEquals(GamePhase.MIDDLEGAME, phaseOf(PHASE_9_FEN),
                "9 of 24 is 0.375, above 0.35: middlegame");
    }

    @Test
    void phase8IsEndgame() {
        assertEquals(GamePhase.ENDGAME, phaseOf(PHASE_8_FEN),
                "8 of 24 is 0.33, at or below 0.35: endgame");
    }

    @Test
    void pawnEndgameIsEndgame() {
        assertEquals(GamePhase.ENDGAME, phaseOf(PAWN_ENDGAME_FEN));
    }

    // ---- computeMoveBudgetMillis per phase ----

    @Test
    void middlegameSpreadsTheClockOver15Moves() {
        assertEquals(TEN_MINUTES / 16, budget(GamePhase.MIDDLEGAME),
                "middlegame: 600 000 / (15 + 1)");
    }

    @Test
    void openingKeepsTheDefault30Moves() {
        assertEquals(TEN_MINUTES / 31, budget(GamePhase.OPENING),
                "opening: 600 000 / (30 + 1), as without the phase");
    }

    @Test
    void endgameKeepsTheDefault30Moves() {
        assertEquals(TEN_MINUTES / 31, budget(GamePhase.ENDGAME),
                "endgame: 600 000 / (30 + 1), as without the phase");
    }

    @Test
    void movestogoFromTheGuiOverridesThePhase() {
        var input = new TimeControlInput(null, TEN_MINUTES, TEN_MINUTES, 9, false, null, null);

        assertEquals(TEN_MINUTES / 10, TimeManagement.computeMoveBudgetMillis(input, GameStatus.TURN_WHITE, GamePhase.MIDDLEGAME),
                "a movestogo sent by the GUI must win over the middlegame fallback");
    }

    // ---- maxDepthFor ----

    @Test
    void openingWithClockStopsAtDepth8() {
        assertEquals(8, TimeManagement.maxDepthFor(clock(), GameStatus.TURN_WHITE, GamePhase.OPENING, NO_DEPTH_LIMIT));
    }

    @Test
    void openingKeepsASmallerRequestedDepth() {
        assertEquals(5, TimeManagement.maxDepthFor(clock(), GameStatus.TURN_WHITE, GamePhase.OPENING, 5),
                "go depth 5 with a clock must stay at 5");
    }

    @Test
    void middlegameAndEndgameHaveNoDepthLimit() {
        assertEquals(NO_DEPTH_LIMIT, TimeManagement.maxDepthFor(clock(), GameStatus.TURN_WHITE, GamePhase.MIDDLEGAME, NO_DEPTH_LIMIT));
        assertEquals(NO_DEPTH_LIMIT, TimeManagement.maxDepthFor(clock(), GameStatus.TURN_WHITE, GamePhase.ENDGAME, NO_DEPTH_LIMIT));
    }

    @Test
    void openingWithoutClockHasNoDepthLimit() {
        var depthOnly = new TimeControlInput(null, null, null, null, false, null, null);
        var movetime = new TimeControlInput(1_000, TEN_MINUTES, TEN_MINUTES, null, false, null, null);
        var infinite = new TimeControlInput(null, TEN_MINUTES, TEN_MINUTES, null, true, null, null);

        assertEquals(12, TimeManagement.maxDepthFor(depthOnly, GameStatus.TURN_WHITE, GamePhase.OPENING, 12),
                "go depth 12 must reach 12: benches and analysis stay unchanged");
        assertEquals(NO_DEPTH_LIMIT, TimeManagement.maxDepthFor(movetime, GameStatus.TURN_WHITE, GamePhase.OPENING, NO_DEPTH_LIMIT),
                "with movetime the clock does not govern the move");
        assertEquals(NO_DEPTH_LIMIT, TimeManagement.maxDepthFor(infinite, GameStatus.TURN_WHITE, GamePhase.OPENING, NO_DEPTH_LIMIT),
                "go infinite must not be limited");
    }

    @Test
    void onlyTheClockOfTheSideToMoveCounts() {
        var whiteClockOnly = new TimeControlInput(null, TEN_MINUTES, null, null, false, null, null);

        assertEquals(NO_DEPTH_LIMIT, TimeManagement.maxDepthFor(whiteClockOnly, GameStatus.TURN_BLACK, GamePhase.OPENING, NO_DEPTH_LIMIT),
                "black to move without btime has no clock of its own");
    }

    private static GamePhase phaseOf(String fen) {
        return TimeManagement.gamePhaseOf(Fen.importFEN(fen));
    }

    private static TimeControlInput clock() {
        return new TimeControlInput(null, TEN_MINUTES, TEN_MINUTES, null, false, null, null);
    }

    private static int budget(GamePhase phase) {
        return TimeManagement.computeMoveBudgetMillis(clock(), GameStatus.TURN_WHITE, phase);
    }
}
