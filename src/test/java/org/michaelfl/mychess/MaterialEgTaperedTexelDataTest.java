package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness tests for {@link MaterialEgTaperedTexelData} — the 4-parameter
 * tapered endgame-material adapter for knight, bishop, rook and queen.
 *
 * <p>The load-bearing property is the exact linear decomposition:
 * {@code baseEval + features · currentParameters} must reproduce the real
 * {@link WeightingFunction} evaluation, so the tune acts on the same quantity the
 * engine plays with. Equivalently {@code features · currentParameters} must equal
 * {@code endgameMaterialContributionActual}.
 *
 * @author Michael Fleischhauer
 */
class MaterialEgTaperedTexelDataTest {

    private static final double EPS = 1e-6;

    private static final String KNIGHT_EG = "8/3k4/8/8/3N4/8/3K4/8 w - - 0 1";
    private static final String ROOK_EG = "8/3k4/8/8/4R3/8/3K4/8 w - - 0 1";
    private static final String QUEEN_EG = "8/3k4/3q4/8/8/8/3K4/8 w - - 0 1";
    private static final String MIXED = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1";
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    void parameterCountAndStage0SeedAreTheMidgameMaterial() {
        assertEquals(4, MaterialEgTaperedTexelData.PARAM_COUNT, "knight, bishop, rook, queen");
        // Stage 0: the endgame material values are seeded equal to the midgame values.
        assertArrayEquals(new double[] {300, 300, 500, 900}, MaterialEgTaperedTexelData.currentTableValues(), EPS,
                "the start values must be the current endgame material (knight/bishop/rook/queen)");
    }

    @Test
    void featuresReconstructTheCurrentEndgameMaterialContribution() {
        double[] current = MaterialEgTaperedTexelData.currentTableValues();

        for (String fen : new String[] {KNIGHT_EG, ROOK_EG, QUEEN_EG, MIXED, START}) {
            Board board = Fen.importFEN(fen);

            double reconstructed = dot(MaterialEgTaperedTexelData.featuresOf(board), current);
            double actual = MaterialEgTaperedTexelData.endgameMaterialContributionActual(board);

            assertEquals(actual, reconstructed, EPS,
                    "features . currentParameters must equal the actual endgame-material contribution for " + fen);
        }
    }

    @Test
    void sampleDecompositionReproducesTheFullEval() {
        double[] current = MaterialEgTaperedTexelData.currentTableValues();
        var evaluator = new WeightingFunction();

        for (String fen : new String[] {KNIGHT_EG, ROOK_EG, QUEEN_EG, MIXED}) {
            String fenFourFields = fen.replace(" 0 1", "");
            Sample sample = MaterialEgTaperedTexelData.toSample(fenFourFields + " c9 \"1/2-1/2\";", evaluator);

            assertTrue(sample != null, "the sample must parse for " + fen);

            double fullEval = evaluator.calculate(Fen.importFEN(fen));
            double modelEval = TexelTuner.evaluate(sample, current);

            assertEquals(fullEval, modelEval, EPS,
                    "baseEval + features . currentParameters must reproduce the full eval for " + fen);
        }
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;

        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }
}
