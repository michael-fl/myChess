package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the phase taper on the castling-state term (branch {@code castling-halved-tapered}).
 *
 * <p>The term used to apply its {@code 0 / -1 / -2 / -4} scale flat across the whole game,
 * so a side that had lost both castling rights carried a full pawn of penalty into a
 * king-and-pawn endgame, where the rights are not merely unlikely to be used but cannot
 * exist. The taper scales the contribution linearly with the game phase, reaching zero
 * when no phase-carrying material is left.
 *
 * <p>Every expectation here is derived from the FEN rather than copied from a run, which
 * is the point: a test that records what the code currently prints cannot fail when the
 * code is wrong. The phase is recomputed from the piece placement with the same weights
 * the evaluation uses (knight 1, bishop 1, rook 2, queen 4, clamped at 24), and the
 * expected contribution is {@code delta * castlingFactor * phase / MAX_PHASE}.
 *
 * <p>The one trap this guards against by construction: {@code phase / MAX_PHASE} written
 * on two ints is integer division and evaluates to 0 for every phase below 24, which
 * would make the term vanish everywhere except the opening while still looking correct
 * in a single full-material probe.
 *
 * @author Michael Fleischhauer
 */
class CastlingTaperTest {

    /** Phase weight per piece kind, matching {@code WeightingFunction.phaseWeightOfPiece}. */
    private static final String PHASE_PIECES = "nbrq";
    private static final int[] PHASE_WEIGHTS = { 1, 1, 2, 4 };

    private static final int MAX_PHASE = 24;

    /** Ramp thresholds, mirroring {@code WeightingFunction}'s two constants. */
    private static final int FULL_PHASE = 16;
    private static final int DEAD_PHASE = 6;

    /** Tolerance in pawns: the term is a float sum, compared against an exact expectation. */
    private static final double EPSILON = 1e-4;

    /** Full material, both sides still hold every right — the term must contribute nothing. */
    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * The phase a position carries, derived from its FEN independently of the evaluation.
     *
     * @param fen a full FEN
     * @return the phase, clamped to {@link #MAX_PHASE}
     */
    private static int phaseOf(String fen) {
        int phase = 0;

        for (char piece : fen.split(" ")[0].toCharArray()) {
            int index = PHASE_PIECES.indexOf(Character.toLowerCase(piece));

            if (index >= 0) {
                phase += PHASE_WEIGHTS[index];
            }
        }

        return Math.min(phase, MAX_PHASE);
    }

    /** The castling-state difference the evaluation sees, white minus black. */
    private static int castlingDeltaOf(Board board) {
        GameStatus status = board.getGameStatus();
        int white = stateOf(status, true);
        int black = stateOf(status, false);

        return white - black;
    }

    /** Mirrors {@code WeightingFunction.calculateCastlingState} for one color. */
    private static int stateOf(GameStatus status, boolean white) {
        if (white ? status.hasWhiteCastled() : status.hasBlackCastled()) {
            return 0;
        }

        boolean kingSide = white
                ? status.isWhiteCastlingKingSidePossible()
                : status.isBlackCastlingKingSidePossible();
        boolean queenSide = white
                ? status.isWhiteCastlingQueenSidePossible()
                : status.isBlackCastlingQueenSidePossible();

        if (kingSide && queenSide) {
            return -1;
        }

        return kingSide || queenSide ? -2 : -4;
    }

    /**
     * The term's contribution in pawns, read out of the evaluation's own report line so
     * the test sees what the weight sum used rather than a separate re-derivation.
     */
    private static double reportedCastlingWeight(Board board) {
        var evaluator = new WeightingFunction();

        evaluator.calculate(board);

        String report = evaluator.toString();
        int line = report.indexOf("castlingState:");

        assertTrue(line >= 0, "the evaluation report carries no castlingState line");

        String tail = report.substring(report.indexOf("weight=", line) + "weight=".length());

        return Double.parseDouble(tail.substring(0, tail.indexOf('\n')).trim());
    }

    /**
     * The expectation as the report would print it.
     *
     * <p>The report rounds to two decimals, so an exact expectation and a printed value
     * differ by up to half a centipawn — and comparing them with a tolerance of exactly
     * that half lands on the boundary, where floating point decides the outcome. Rounding
     * the expectation the same way removes the boundary instead of widening it.
     */
    private static double asReported(double exact) {
        return Math.round(exact * 100f) / 100.0;
    }

    /**
     * The ramp multiplier, read from the production table.
     *
     * <p>The other tests read the table rather than recomputing it, so they test the shipped
     * curve. {@link #everyRampEntryHasItsExpectedValue} is what pins the table itself, and it
     * does so against literals rather than against this formula — otherwise a wrong formula
     * would simply agree with itself.
     */
    private static double rampAt(int phase) {
        return WeightingFunction.CASTLING_RAMP[phase] / (double) MAX_PHASE;
    }

    private static void assertTaperedAt(String fen) {
        Board board = Fen.importFEN(fen);
        int phase = phaseOf(fen);
        int delta = castlingDeltaOf(board);
        double expected = asReported(delta * 0.125 * rampAt(phase));

        assertEquals(expected, reportedCastlingWeight(board), 0.006,
                "castling contribution at phase " + phase + ", delta " + delta + ", fen " + fen);
    }

    @Test
    void theWeightFunctionReturnsTheseExactValues() {
        // The term reduced to what it is: a pure function of two numbers. Called directly,
        // with the expectations written out rather than recomputed, because recomputing them
        // would mean rebuilding blend's rounding and the ramp lookup here and checking one
        // implementation against a copy of itself.
        //
        // The values are in pawns and carry blend's round-half-away-from-zero, which is why
        // phase 12 gives -0.29125 rather than a clean -0.29167: 14/24 of -400 centi-units is
        // -233.33, blended to -233.
        assertWeight(0.0f, 0, MAX_PHASE, "no difference is worth nothing at any phase");
        assertWeight(-0.125f, -1, MAX_PHASE, "one state unit at full phase is the factor");
        assertWeight(-0.5f, -4, MAX_PHASE, "four units, the common endgame case");

        assertWeight(-0.5f, -4, FULL_PHASE, "still undiscounted at the upper knee");
        assertWeight(-0.29125f, -4, 12, "on the slope");
        assertWeight(-0.09375f, -2, 10, "two units further down the slope");
        assertWeight(-0.08375f, -4, 8, "near the bottom of the slope");

        assertWeight(0.0f, -4, DEAD_PHASE, "off at the lower knee, whatever the states say");
        assertWeight(0.0f, -4, 0, "off with no material left");

        // Antisymmetry at the function level, where it is cheapest to see.
        assertWeight(0.29125f, 4, 12, "mirroring the difference mirrors the contribution");
    }

    private static void assertWeight(float expected, int delta, int phase, String what) {
        assertEquals(expected, WeightingFunction.castlingWeight(delta, phase), 1e-6f,
                what + " (delta " + delta + ", phase " + phase + ")");
    }

    @Test
    void everyRampEntryHasItsExpectedValue() {
        // All 25 of them, written out. The table is small, fixed and deliberate, so its
        // contents belong in the test as literals: re-deriving them from the same expression
        // the production code uses would only prove that the expression equals itself, and
        // an off-by-one in the stretch or a sign slip in the clamp would pass unnoticed.
        //
        // Read down the column: flat zero to the lower knee at 6, a rise across the ten
        // phase points to 16, flat at full value above.
        int[] expected = {
                0,  0,  0,  0,  0,  0,  0,     // phase 0-6    below the lower knee
                2,  4,  7,  9, 12, 14, 16,     // phase 7-13   rising
               19, 21, 24,                     // phase 14-16  reaching full at the upper knee
               24, 24, 24, 24, 24, 24, 24, 24  // phase 17-24  above the upper knee
        };

        assertEquals(MAX_PHASE + 1, WeightingFunction.CASTLING_RAMP.length,
                "the table must cover every reachable phase, 0 through " + MAX_PHASE);
        assertEquals(expected.length, WeightingFunction.CASTLING_RAMP.length,
                "the expectation must cover every entry");

        for (int phase = 0; phase <= MAX_PHASE; phase++) {
            assertEquals(expected[phase], WeightingFunction.CASTLING_RAMP[phase],
                    "ramp entry at phase " + phase);
        }
    }

    @Test
    void thisTestsThresholdsStillMatchTheProduction() {
        // The one thing the literal table above cannot catch. Those literals encode a curve
        // built from two thresholds, and this class keeps its own copies of them to express
        // its fixtures in. If production moves a threshold, the table changes and the literal
        // test fails - correctly - but a reader would go looking in the wrong place, because
        // the test's own constants would still say 6 and 16. This says where to look.
        assertEquals(WeightingFunction.CASTLING_FULL_PHASE, FULL_PHASE,
                "this test's copy of the upper knee has drifted from the production constant");
        assertEquals(WeightingFunction.CASTLING_DEAD_PHASE, DEAD_PHASE,
                "this test's copy of the lower knee has drifted from the production constant");
    }

    @Test
    void theContributionFollowsTheRamp() {
        // Same castling states throughout - only the phase-carrying material changes, so
        // any difference between these four is the ramp and nothing else.
        assertTaperedAt("rnbqk2r/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1RK1 w kq - 0 10");
        assertTaperedAt("rnb1k2r/pppppppp/8/8/8/8/PPPPPPPP/RNB2RK1 w kq - 0 20");
        assertTaperedAt("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R4RK1 w kq - 0 20");
        assertTaperedAt("r3k3/pppppppp/8/8/8/8/PPPPPPPP/4K2R w Kq - 0 30");
    }

    @Test
    void theTermVanishesWhenNoPhaseCarryingMaterialIsLeft() {
        // A pawn endgame has no rooks, so neither side can hold a right and the delta is
        // zero anyway. The assertion that matters is the phase: nothing this term says
        // can reach the score here, whatever the states happen to be.
        String fen = "4k3/pppppppp/8/8/8/8/PPPPPPPP/4K3 w - - 0 40";

        assertEquals(0, phaseOf(fen), "a pawn endgame carries no phase material");
        assertEquals(0.0, reportedCastlingWeight(Fen.importFEN(fen)), EPSILON,
                "the castling term must contribute nothing once the phase is zero");
    }

    @Test
    void theFullValueSurvivesTheMidgame() {
        // The reason this is a ramp and not a straight taper. Scaling with the raw phase
        // would already be down to 0.67 here, with both queens still on the board; the ramp
        // holds 1.0 down to FULL_PHASE. If someone replaces the ramp with a plain taper,
        // this is the test that says so.
        String fen = "r2qk2r/pppppppp/8/8/8/8/PPPPPPPP/R2Q1RK1 w kq - 0 15";
        int phase = phaseOf(fen);

        assertEquals(FULL_PHASE, phase, "fixture must sit exactly at the ramp's upper knee");
        assertEquals(1.0, rampAt(phase), EPSILON, "the ramp is still at full value here");

        Board board = Fen.importFEN(fen);
        int delta = castlingDeltaOf(board);

        assertEquals(asReported(delta * 0.125), reportedCastlingWeight(board), 0.006,
                "an undiscounted contribution at phase " + phase);
    }

    @Test
    void theTermIsOffBelowTheLowerKnee() {
        // Below DEAD_PHASE the term must contribute nothing whatever the states are - a
        // rook and a minor each, where the king belongs in the centre.
        String fen = "r3k3/pppppppp/8/8/8/8/PPPPPPPP/4K2R w Kq - 0 30";

        assertTrue(phaseOf(fen) <= DEAD_PHASE, "fixture must sit below the lower knee");
        assertEquals(0.0, rampAt(phaseOf(fen)), EPSILON, "the ramp is off here");
        assertEquals(0.0, reportedCastlingWeight(Fen.importFEN(fen)), EPSILON,
                "no contribution below the lower knee");
    }

    @Test
    void anIntegerDivisionWouldBeCaughtHere() {
        // The regression this class exists for. With `phase / MAX_PHASE` on two ints the
        // contribution is zero for every phase below 24, so a position with a real delta
        // and a mid-range phase must come out non-zero.
        String fen = "rnb1k2r/pppppppp/8/8/8/8/PPPPPPPP/RNB2RK1 w kq - 0 20";
        Board board = Fen.importFEN(fen);

        assertTrue(phaseOf(fen) > DEAD_PHASE && phaseOf(fen) < FULL_PHASE,
                "fixture must sit on the sloping part of the ramp, not on either knee");
        assertNotEquals(0, castlingDeltaOf(board), "fixture must have a non-zero castling delta");
        assertTrue(Math.abs(reportedCastlingWeight(board)) > EPSILON,
                "a mid-phase position with a real delta must carry a non-zero contribution; "
                        + "zero here means the phase ratio was computed in integer arithmetic");
    }

    @Test
    void aSymmetricStartingPositionIsWorthNothing() {
        Board board = Fen.importFEN(START_FEN);

        assertEquals(MAX_PHASE, phaseOf(START_FEN), "the starting position is full phase");
        assertEquals(0, castlingDeltaOf(board), "both sides hold both rights");
        assertEquals(0.0, reportedCastlingWeight(board), EPSILON,
                "a symmetric position must not favor either side");
    }

    @Test
    void theTaperedTermStaysAntisymmetricUnderColorMirroring() {
        // The property the whole evaluation rests on: mirroring colors and ranks must
        // negate the score exactly. A taper is a place where that can break, because an
        // asymmetric rounding rule (Java's Math.round rounds halves toward positive
        // infinity) turns -12.5 into -12 and +12.5 into +13. blend avoids it by rounding
        // away from zero on both sides.
        //
        // Asserted on the TOTAL weight rather than on the report's castling line: that
        // line is printed through the class's round() helper, which is not symmetric, so
        // it can differ by one centipawn in the display while the evaluation is exact.
        assertMirrorNegates("rnbqk2r/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1RK1 w kq - 0 10",
                            "rnbq1rk1/pppppppp/8/8/8/8/PPPPPPPP/RNBQK2R b KQ - 0 10");

        // The pair that lands on an exact half-centipawn, which is where a non-symmetric
        // rounding rule would show itself.
        assertMirrorNegates("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R4RK1 w kq - 0 20",
                            "r4rk1/pppppppp/8/8/8/8/PPPPPPPP/R3K2R b KQ - 0 20");
    }

    private static void assertMirrorNegates(String fen, String mirroredFen) {
        var evaluator = new WeightingFunction();
        int weight = evaluator.calculate(Fen.importFEN(fen));
        int mirrored = evaluator.calculate(Fen.importFEN(mirroredFen));

        assertEquals(-weight, mirrored,
                "mirroring must negate the evaluation exactly: " + fen + " against " + mirroredFen);
    }

    @Test
    void theHalvedFactorIsWhatTheEvaluationUses() {
        // Pins the magnitude, not just the shape: one state unit is worth 0.125 pawns at
        // full phase. If the factor changes, this says so, instead of the change sliding
        // through behind a taper that still looks right.
        //
        // The sign is the part worth stating explicitly, because it is easy to get
        // backwards: white holds both rights (state -1), black only the king-side one
        // (state -2), and the difference is white minus black, so the side with the
        // better state produces a POSITIVE contribution.
        String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKB1R w KQk - 0 5";
        Board board = Fen.importFEN(fen);
        int delta = castlingDeltaOf(board);

        assertEquals(1, delta, "white holds both rights, black lost the queen-side one");
        assertEquals(asReported(delta * 0.125 * rampAt(phaseOf(fen))),
                reportedCastlingWeight(board), 0.006,
                "one state unit at phase " + phaseOf(fen));
    }
}
