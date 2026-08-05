package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Correctness tests for {@link JointEndgamePstTaperedTexelData} — the 128-parameter
 * joint endgame Texel adapter for knight, bishop, rook and queen.
 *
 * <p>The load-bearing property is that the adapter's linear decomposition is exact:
 * {@code baseEval + features · currentParameters} must reproduce the real
 * {@link WeightingFunction} evaluation, so the tune actually acts on the same
 * quantity the engine plays with. Equivalently, {@code features · currentParameters}
 * must reconstruct {@code endgameContributionActual} — that is what these tests pin.
 *
 * @author Michael Fleischhauer
 */
class JointEndgamePstTaperedTexelDataTest {

    private static final double EPS = 1e-6;

    // Endgame / middlegame positions exercising each tuned piece at low and high phase.
    private static final String KNIGHT_EG = "8/3k4/8/8/3N4/8/3K4/8 w - - 0 1";
    private static final String BISHOP_EG = "8/3k4/8/2b5/8/8/3K4/8 w - - 0 1";
    private static final String ROOK_EG = "8/3k4/8/8/4R3/8/3K4/8 w - - 0 1";
    private static final String QUEEN_EG = "8/3k4/3q4/8/8/8/3K4/8 w - - 0 1";
    private static final String MIXED = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 0 1";
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    void parameterCountIs128() {
        assertEquals(128, JointEndgamePstTaperedTexelData.PARAM_COUNT,
                "four pieces times 32 symmetric squares each");
        assertEquals(128, JointEndgamePstTaperedTexelData.currentTableValues().length,
                "the start-value vector must have one entry per parameter");
    }

    @Test
    void phaseMatchesRemainingMaterial() {
        assertEquals(24, JointEndgamePstTaperedTexelData.phaseOf(Fen.importFEN(START)),
                "full starting material sums to MAX_PHASE (24)");
        assertEquals(0, JointEndgamePstTaperedTexelData.phaseOf(Fen.importFEN("8/3k4/8/8/8/8/3K4/8 w - - 0 1")),
                "bare kings are phase 0");
        assertEquals(1, JointEndgamePstTaperedTexelData.phaseOf(Fen.importFEN(KNIGHT_EG)),
                "a lone knight contributes phase 1");
        assertEquals(4, JointEndgamePstTaperedTexelData.phaseOf(Fen.importFEN(QUEEN_EG)),
                "a lone queen contributes phase 4");
    }

    @Test
    void featuresReconstructTheCurrentEndgameContribution() {
        double[] current = JointEndgamePstTaperedTexelData.currentTableValues();

        for (String fen : new String[] {KNIGHT_EG, BISHOP_EG, ROOK_EG, QUEEN_EG, MIXED, START}) {
            Board board = Fen.importFEN(fen);

            double reconstructed = dot(JointEndgamePstTaperedTexelData.featuresOf(board), current);
            double actual = JointEndgamePstTaperedTexelData.endgameContributionActual(board);

            assertEquals(actual, reconstructed, EPS,
                    "features . currentParameters must equal the actual endgame contribution for " + fen);
        }
    }

    @Test
    void sampleDecompositionReproducesTheFullEval() {
        double[] current = JointEndgamePstTaperedTexelData.currentTableValues();
        var evaluator = new WeightingFunction();

        for (String fen : new String[] {KNIGHT_EG, BISHOP_EG, ROOK_EG, QUEEN_EG, MIXED}) {
            String fenFourFields = fen.replace(" 0 1", "");
            Sample sample = JointEndgamePstTaperedTexelData.toSample(fenFourFields + " c9 \"1/2-1/2\";", evaluator);

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
