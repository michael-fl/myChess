package org.michaelfl.mychess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the material-exchange term of {@link WeightingFunction}: the side that is
 * more than a pawn ahead in material gains, and the side behind loses, a share of the excess
 * that grows as material comes off the board (as the game phase falls).
 *
 * <p>The term is tested through {@code calcMaterialExchangeTerm(phase, materialDelta)} with
 * explicit arguments, so each case states its phase and material rather than depending on a
 * position. The method returns pawns, with {@code materialExchangeFactor} already applied; the
 * expectations are written as the unscaled share times that factor, so they stay right when the
 * factor is retuned. The {@code toString} tests use real positions via
 * {@link Fen#importFEN(String)}.
 *
 * @author Michael Fleischhauer
 */
class WeightingFunctionMaterialExchangeTest {

    private static final float EPSILON = 1e-4f;

    private static final float FACTOR = WeightingFunction.materialExchangeFactor;

    private static final int PAWN = WeightingFunction.weightOfPiece[Board.whitePawn];

    /** White up a rook with nothing else on the board but the kings. */
    private static final String ROOK_UP_FEN = "4k3/8/8/8/8/8/8/R3K3 w - - 0 1";

    private static float penalty(int phase, int materialDelta) {
        return WeightingFunction.calcMaterialExchangeTerm(phase, materialDelta);
    }

    @Nested
    class Threshold {

        @Test
        void equalMaterial_givesNothing() {
            assertEquals(0f, penalty(0, 0), EPSILON, "no imbalance, no term");
        }

        @Test
        void aPawnAhead_givesNothing() {
            assertEquals(0f, penalty(0, PAWN), EPSILON, "a one-pawn lead is inside the threshold");
            assertEquals(0f, penalty(0, -PAWN), EPSILON, "a one-pawn deficit is inside the threshold");
        }

        @Test
        void oneCentipawnBeyondThePawn_countsOneCentipawnOfExcess() {
            assertEquals(0.1f * FACTOR, penalty(0, PAWN + 1), EPSILON,
                    "10 % of a one-centipawn excess, fully exchanged, is 0.1");
        }
    }

    @Nested
    class Phase {

        @Test
        void fullBoard_givesNothing() {
            assertEquals(0f, penalty(WeightingFunction.MAX_PHASE, 4 * PAWN), EPSILON,
                    "at the maximum phase nothing has been exchanged yet");
        }

        @Test
        void fullyExchanged_givesTenPercentOfTheExcess() {
            assertEquals(30f * FACTOR, penalty(0, 4 * PAWN), EPSILON,
                    "four pawns ahead is three beyond the threshold; 10 % of 300 is 30");
        }

        @Test
        void halfExchanged_givesHalfOfThat() {
            assertEquals(15f * FACTOR, penalty(WeightingFunction.MAX_PHASE / 2, 4 * PAWN), EPSILON,
                    "the share scales linearly with the exchanged part of the phase");
        }

        @Test
        void fallingPhase_neverLowersTheTerm() {
            float previous = penalty(WeightingFunction.MAX_PHASE, 5 * PAWN);

            for (int phase = WeightingFunction.MAX_PHASE - 1; phase >= 0; phase--) {
                float current = penalty(phase, 5 * PAWN);

                assertTrue(current >= previous, "the term must not shrink as material comes off, phase " + phase);
                previous = current;
            }
        }
    }

    @Nested
    class Sign {

        @Test
        void whiteAhead_isPositive() {
            assertTrue(penalty(6, 3 * PAWN) > 0f, "a white material lead favors white");
        }

        @Test
        void blackAhead_isNegative() {
            assertTrue(penalty(6, -3 * PAWN) < 0f, "a black material lead favors black");
        }

        @Test
        void colorsSwapped_negatesTheTermExactly() {
            for (int phase = 0; phase <= WeightingFunction.MAX_PHASE; phase++) {
                for (int delta : new int[] {101, 107, 250, 333, 900}) {
                    float whiteAhead = WeightingFunction.calcMaterialExchangeTerm(phase, delta);
                    float blackAhead = WeightingFunction.calcMaterialExchangeTerm(phase, -delta);

                    assertEquals(-whiteAhead, blackAhead, 0f,
                            "the evaluation must stay antisymmetric, including its rounding: phase " + phase + ", delta " + delta);
                }
            }
        }
    }

    @Nested
    class ToString {

        @Test
        void listsEveryTermOfTheEvaluation() {
            var weightingFunction = new WeightingFunction();
            weightingFunction.calculate(Fen.importFEN(ROOK_UP_FEN));

            String text = weightingFunction.toString();

            for (String label : new String[] {"piecesWeight:", "positionWeight:", "mobilityWeight:", "threadWeight:",
                    "castlingState:", "doublePawnCount:", "chessCount:", "undefendedPiecesCount:", "attackUnit:",
                    "bishopCount:", "materialExchange:", "weight: "}) {
                assertTrue(text.contains(label), "toString must list " + label + " - got:\n" + text);
            }
        }

        @Test
        void reportsTheMaterialExchangeTermOfThePosition() {
            var weightingFunction = new WeightingFunction();
            weightingFunction.calculate(Fen.importFEN(ROOK_UP_FEN));

            int phase = weightingFunction.getPhase();
            float expected = Math.round(WeightingFunction.calcMaterialExchangeTerm(phase, WeightingFunction.weightOfPiece[Board.whiteRook]) * 100f) / 100f;

            assertTrue(weightingFunction.toString().contains("materialExchange:      phase=" + phase + ", delta=500, weight=" + expected),
                    "white is a rook up, so the line carries delta 500 and the term's weight in pawns - got:\n"
                            + weightingFunction);
        }

        @Test
        void reportsTheBishopPair() {
            var weightingFunction = new WeightingFunction();
            weightingFunction.calculate(Fen.importFEN("4k3/8/8/8/8/8/8/2B1KB2 w - - 0 1"));

            assertTrue(weightingFunction.toString().contains("bishopCount:           w=2, b=0, delta=1"),
                    "white has the pair and black has no bishop - got:\n" + weightingFunction);
        }
    }
}
