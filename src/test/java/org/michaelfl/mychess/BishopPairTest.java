package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the bishop-pair evaluation term (v4.3.2 candidate): a side holding
 * both bishops gets a fixed bonus ({@code bishopPairFactor}) in the final weight.
 *
 * <p>The exact magnitude cannot be isolated at the eval level — giving one side
 * the pair inevitably swaps a knight for a bishop somewhere, which also moves the
 * piece-square-table and mobility terms. So these tests pin the correctness
 * properties that a bug would break: the term must not disturb the evaluation's
 * color antisymmetry (symmetric positions stay 0), and the pair must favor the
 * side that holds it (with the black-pair position the exact negation of the
 * white-pair one). The magnitude (0.4 pawns) is a tuning constant verified by
 * inspection.
 *
 * @author Michael Fleischhauer
 */
class BishopPairTest {

    // Kings on e1/e8; the two minor pieces on c- and f-file back-rank squares,
    // mirrored between the colors so every position below is color-symmetric in
    // structure (only the piece TYPES differ between the sides).
    private static final String BOTH_PAIRS = "2b1kb2/8/8/8/8/8/8/2B1KB2 w - - 0 1"; // both sides BB
    private static final String NO_PAIRS   = "2b1kn2/8/8/8/8/8/8/2B1KN2 w - - 0 1"; // both sides B+N
    private static final String WHITE_PAIR = "2b1kn2/8/8/8/8/8/8/2B1KB2 w - - 0 1"; // white BB, black B+N
    private static final String BLACK_PAIR = "2b1kb2/8/8/8/8/8/8/2B1KN2 w - - 0 1"; // white B+N, black BB (mirror of WHITE_PAIR)

    private static int eval(String fen) {
        return new WeightingFunction().calculate(Fen.importFEN(fen));
    }

    @Test
    void symmetricPositionsStayZero() {
        // Antisymmetry guard: with both sides holding the pair (or neither) the
        // bishop-pair term cancels and the mirror position must evaluate to 0.
        assertEquals(0, eval(BOTH_PAIRS), "both sides hold the bishop pair -> symmetric -> 0");
        assertEquals(0, eval(NO_PAIRS), "neither side holds the pair -> symmetric -> 0");
    }

    @Test
    void theSideHoldingThePairIsFavored() {
        int whitePair = eval(WHITE_PAIR);
        int blackPair = eval(BLACK_PAIR);

        assertTrue(whitePair > 0, "the side with the bishop pair must be favored (white-POV > 0), was " + whitePair);
        assertEquals(-whitePair, blackPair,
                "the black-pair position is the mirror of the white-pair one, so it must be its exact negation");
    }
}
