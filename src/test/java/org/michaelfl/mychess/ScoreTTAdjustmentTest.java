package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Round-trip and edge-case tests for
 * {@link WeightingFunction#scoreToTT(int, int)} /
 * {@link WeightingFunction#scoreFromTT(int, int)} — the depth-relative
 * mate-score adjustment used by the transposition table.
 *
 * <p>The original implementation lived inside {@code PositionSearch} and
 * shipped with a sign-loss bug that surfaced only indirectly through
 * {@code GameStatusTest.testWhiteCheckmate}. These tests lock the
 * invariants in place at the unit level so a future regression points
 * directly at the broken transformation.
 *
 * @author Michael Fleischhauer
 */
class ScoreTTAdjustmentTest {

    private static final int DEPTH_AT_ROOT = 0;

    @Test
    void roundTrip_positiveMate_preservesValueAndSign() {
        // White-side mate-in-3 at depth 2 from root: store at depth 2,
        // read back at depth 2 — same score.
        int rootScore = WeightingFunction.checkmateInCenti(3);
        int stored = WeightingFunction.scoreToTT(rootScore, 2);
        int retrieved = WeightingFunction.scoreFromTT(stored, 2);

        assertEquals(rootScore, retrieved,
                "positive mate must round-trip unchanged at the same depth");
    }

    @Test
    void roundTrip_negativeMate_preservesValueAndSign() {
        // The regression: a negative mate score (we are being mated) used
        // to come back positive from the TT.
        int rootScore = -WeightingFunction.checkmateInCenti(3);
        int stored = WeightingFunction.scoreToTT(rootScore, 2);
        int retrieved = WeightingFunction.scoreFromTT(stored, 2);

        assertEquals(rootScore, retrieved,
                "negative mate must stay negative through the TT round-trip");
    }

    @Test
    void roundTrip_acrossDepths_translatesByDelta() {
        // Store at depth A, read at depth B. The retrieved score must
        // encode the mate at the new "depth-from-root" of (originalPlies - A + B),
        // with the original sign preserved.
        int storeAt = 2;
        int readAt = 5;
        int originalPlies = 7;
        int rootScore = WeightingFunction.checkmateInCenti(originalPlies);

        int stored = WeightingFunction.scoreToTT(rootScore, storeAt);
        int retrieved = WeightingFunction.scoreFromTT(stored, readAt);

        int expectedPlies = originalPlies - storeAt + readAt;
        assertEquals(WeightingFunction.checkmateInCenti(expectedPlies), retrieved,
                "positive mate must translate by the depth delta on store/read across depths");
    }

    @Test
    void roundTrip_negativeMateAcrossDepths_translatesByDeltaWithSign() {
        int storeAt = 1;
        int readAt = 4;
        int originalPlies = 6;
        int rootScore = -WeightingFunction.checkmateInCenti(originalPlies);

        int stored = WeightingFunction.scoreToTT(rootScore, storeAt);
        int retrieved = WeightingFunction.scoreFromTT(stored, readAt);

        int expectedPlies = originalPlies - storeAt + readAt;
        assertEquals(-WeightingFunction.checkmateInCenti(expectedPlies), retrieved,
                "negative mate must translate by the depth delta with the sign preserved");
    }

    @Test
    void scoreToTT_nonMateScorePassesThrough() {
        // Positional scores have no depth-relative encoding — they should
        // pass through both transformations unchanged regardless of depth.
        assertEquals(123, WeightingFunction.scoreToTT(123, 0));
        assertEquals(123, WeightingFunction.scoreToTT(123, 7));
        assertEquals(-456, WeightingFunction.scoreToTT(-456, 3));
        assertEquals(0, WeightingFunction.scoreToTT(0, 5));
    }

    @Test
    void scoreFromTT_nonMateScorePassesThrough() {
        assertEquals(123, WeightingFunction.scoreFromTT(123, 0));
        assertEquals(123, WeightingFunction.scoreFromTT(123, 7));
        assertEquals(-456, WeightingFunction.scoreFromTT(-456, 3));
        assertEquals(0, WeightingFunction.scoreFromTT(0, 5));
    }

    @Test
    void scoreToTT_atRootDepthIsIdentityForMate() {
        // depth=0 means "score is already relative to this position";
        // scoreToTT must leave it unchanged.
        int score = WeightingFunction.checkmateInCenti(4);
        assertEquals(score, WeightingFunction.scoreToTT(score, DEPTH_AT_ROOT));
        assertEquals(-score, WeightingFunction.scoreToTT(-score, DEPTH_AT_ROOT));
    }

    @Test
    void scoreFromTT_atRootDepthIsIdentityForMate() {
        int stored = WeightingFunction.checkmateInCenti(4);
        assertEquals(stored, WeightingFunction.scoreFromTT(stored, DEPTH_AT_ROOT));
        assertEquals(-stored, WeightingFunction.scoreFromTT(-stored, DEPTH_AT_ROOT));
    }

    @Test
    void scoreToTT_assertsPliesGreaterEqualDepth() {
        // A positive mate score with plies < depth is internally
        // inconsistent: the stored score would underflow the mate
        // sentinel range. The __assert in scoreToTT catches this.
        int score = WeightingFunction.checkmateInCenti(1);   // mate in 1 ply from root
        int badDepth = 5;                                    // but we are 5 plies deep

        assertThrows(AssertionError.class,
                () -> WeightingFunction.scoreToTT(score, badDepth),
                "scoreToTT must assert plies >= depth");
    }
}
