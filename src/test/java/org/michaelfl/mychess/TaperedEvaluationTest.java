package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Dedicated tests for the tapered (phase-interpolated) evaluation added to
 * {@link WeightingFunction} / {@link PieceSquareTables}: the {@code blend}
 * interpolation, the null-test invariant that a middlegame table equal to the
 * endgame table leaves the position weight unchanged, and two findings that the
 * null-test config (MG = EG) hides — see the {@code EXPOSES_BUG} /
 * {@code DIVERGENCE} tests and the accompanying review.
 *
 * @author Michael Fleischhauer
 */
class TaperedEvaluationTest {

    /** Mirrors the private {@code WeightingFunction.MAX_PHASE}. */
    private static final int MAX_PHASE = 24;

    private static final String START_POS = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String MIDDLEGAME = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4";
    private static final String ENDGAME = "8/5k2/8/8/3K4/8/8/8 w - - 0 60";

    // ---------- blend interpolation ----------

    @Test
    void blend_returnsMidGameValueAtFullPhase() {
        assertEquals(100, WeightingFunction.blend(100, 40, MAX_PHASE),
                "at phase = MAX the blend must be the pure midgame value");
    }

    @Test
    void blend_returnsEndGameValueAtZeroPhase() {
        assertEquals(40, WeightingFunction.blend(100, 40, 0),
                "at phase = 0 the blend must be the pure endgame value");
    }

    @Test
    void blend_interpolatesLinearlyAtHalfPhase() {
        assertEquals(70, WeightingFunction.blend(100, 40, MAX_PHASE / 2),
                "at half phase the blend must be the midpoint of MG and EG");
        assertEquals(20, WeightingFunction.blend(80, -40, MAX_PHASE / 2),
                "half-phase blend must handle negative endgame values");
    }

    @Test
    void blend_isConstantWhenMidGameEqualsEndGame() {
        // The step-0 null-test relies on this: with MG == EG the phase cannot
        // influence the result, so the tapered eval collapses to the old eval.
        for (int phase = 0; phase <= MAX_PHASE; phase++) {
            assertEquals(37, WeightingFunction.blend(37, 37, phase),
                    "with MG == EG the blend must equal that value at phase " + phase);
        }
    }

    @Test
    void blend_roundsToNearestNotTowardZero() {
        // blend(1, 0, 12) has the exact value 0.5. Symmetric (nearest) rounding
        // yields 1; plain integer truncation toward zero would yield 0. This
        // locks in the more accurate rounding, so reverting to integer division
        // is caught. The negative half-value also rejects Math.round, which
        // rounds -0.5 toward +infinity (to 0) instead of to nearest (-1).
        assertEquals(1, WeightingFunction.blend(1, 0, 12),
                "blend must round the exact value 0.5 to nearest (1), not toward zero (0)");
        assertEquals(-1, WeightingFunction.blend(-1, 0, 12),
                "blend must round the exact value -0.5 to nearest (-1), not toward zero (0)");
    }

    // ---------- phase computation (via the exposed getPhase) ----------

    @Test
    void phase_reflectsRemainingMaterialAndClampsToMax() {
        // getPhase() makes the phase observable, so the phase computation and
        // its clamp can be verified directly — impossible while MG == EG hid
        // the phase behind the blend.
        var evaluator = new WeightingFunction();

        evaluator.calculate(Fen.importFEN(START_POS));
        assertEquals(MAX_PHASE, evaluator.getPhase(), "full starting material sums to MAX_PHASE (24)");

        evaluator.calculate(Fen.importFEN("8/5k2/8/8/3K4/8/8/8 w - - 0 60"));
        assertEquals(0, evaluator.getPhase(), "bare kings have phase 0");

        evaluator.calculate(Fen.importFEN("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 20"));
        assertEquals(4, evaluator.getPhase(), "two rooks contribute phase 4");

        // An extra (promoted) queen pushes the raw sum to 28: it must clamp.
        evaluator.calculate(Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/5Q2/PPPPPPPP/RNBQKBNR w KQkq - 0 1"));
        assertEquals(MAX_PHASE, evaluator.getPhase(),
                "phase must clamp to MAX_PHASE even when an extra queen pushes the raw sum above it");
    }

    @Test
    void blend_isOddUnderSimultaneousNegation() {
        // Integer division truncates toward zero, which is odd: (-x)/n == -(x/n).
        // That is what keeps the per-color blend from breaking the eval's color
        // antisymmetry (MirrorEvalTest) once the endgame tables differ from the
        // midgame ones. Verified directly here because MirrorEvalTest cannot yet
        // exercise MG != EG (both table sets are currently identical).
        for (int phase : new int[] {0, 5, 12, 19, MAX_PHASE}) {
            assertEquals(-WeightingFunction.blend(137, -53, phase), WeightingFunction.blend(-137, 53, phase),
                    "blend must be odd under negating both MG and EG at phase " + phase);
        }

        // The decisive case: a value of exactly +/-0.5 (blend(1, 0, 12) = 0.5).
        // A non-symmetric rounding such as Math.round (which rounds .5 toward
        // +infinity) would break oddness here; symmetric rounding and truncation
        // toward zero both stay odd. The loop above never lands on a .5 boundary.
        assertEquals(-WeightingFunction.blend(1, 0, 12), WeightingFunction.blend(-1, 0, 12),
                "blend must stay odd at a half-integer value (0.5)");
    }

    // ---------- pawn tables diverge (tuned EG); all other pieces stay MG == EG ----------

    @Test
    void tapered_onlyPawnTablesDiverge_nonPawnAccumulatorsStillMatch() {
        var evaluator = new WeightingFunction();

        // The pawn endgame table was tuned to diverge from the midgame table
        // (v4.3.0), so any position with pawns now has MG != EG.
        evaluator.calculate(Fen.importFEN(START_POS));
        assertNotEquals(evaluator.getPstMidGameWeight()[0], evaluator.getPstEndGameWeight()[0],
                "pawns diverge: white MG/EG position weight must differ in the start position");

        // Every non-pawn piece still shares one table for both phases, so a
        // pawnless position keeps MG == EG.
        evaluator.calculate(Fen.importFEN("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 20"));
        assertEquals(evaluator.getPstMidGameWeight()[0], evaluator.getPstEndGameWeight()[0],
                "non-pawn tables still match: white MG/EG must be equal in a pawnless position");
        assertEquals(evaluator.getPstMidGameWeight()[1], evaluator.getPstEndGameWeight()[1],
                "non-pawn tables still match: black MG/EG must be equal in a pawnless position");
    }

}
