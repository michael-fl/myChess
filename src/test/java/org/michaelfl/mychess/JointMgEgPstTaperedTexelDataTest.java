package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.michaelfl.mychess.JointMgEgPstTaperedTexelData.PHASE_EG;
import static org.michaelfl.mychess.JointMgEgPstTaperedTexelData.PHASE_MG;
import static org.michaelfl.mychess.JointMgEgPstTaperedTexelData.SLOTS_PER_BLOCK;
import static org.michaelfl.mychess.JointMgEgPstTaperedTexelData.blockBase;

/**
 * Unit tests for {@link JointMgEgPstTaperedTexelData}: the linear-model identity,
 * the feature placement (piece / phase / square / color), and the
 * material-leak re-centering.
 *
 * @author Michael Fleischhauer
 */
class JointMgEgPstTaperedTexelDataTest {

    private static final double POSITION_FACTOR = 0.5;
    private static final int MAX_PHASE = 24;
    private static final double EPSILON = 1e-9;

    /** pawn=0, knight=1, bishop=2, rook=3, queen=4, king=5. */
    private static final int KNIGHT = 1;
    private static final int KING = 5;

    @Test
    void parameterCountIsSixPiecesTimesTwoPhasesTimes32() {
        assertEquals(6 * 2 * 32, JointMgEgPstTaperedTexelData.PARAM_COUNT, "parameter count");
    }

    @Test
    void evalAtCurrentValuesReconstructsTheFullEvalExactly() {
        // The linear-model identity: baseEval + features . currentValues == fullEval.
        // EPD FENs carry only the first four fields; the adapter appends " 0 1".
        String fen4 = "r1bqkbnr/pp1ppppp/2n5/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq -";
        var evaluator = new WeightingFunction();
        Board board = Fen.importFEN(fen4 + " 0 1");

        int fullEval = evaluator.calculate(board);
        Sample sample = JointMgEgPstTaperedTexelData.toSample(fen4 + " c9 \"1/2-1/2\";", evaluator);

        assertEquals(fullEval, TexelTuner.evaluate(sample, JointMgEgPstTaperedTexelData.currentTableValues()), EPSILON,
                "eval at the current table values must reconstruct the full eval");
    }

    @Test
    void featuresPlaceAWhiteKnightInTheRightMgAndEgSlotsAndNowhereElse() {
        // A lone white knight on e4; the two kings map to the same symmetric king
        // slot with opposite colors, so their features cancel — leaving only the
        // knight's midgame and endgame contributions non-zero.
        Board board = Fen.importFEN("7k/8/8/8/4N3/8/8/K7 w - - 0 1");
        double[] features = JointMgEgPstTaperedTexelData.featuresOf(board);

        int phase = 1;                                   // one knight, kings weigh 0
        double mgCoefficient = POSITION_FACTOR * phase / MAX_PHASE;
        double egCoefficient = POSITION_FACTOR * (MAX_PHASE - phase) / MAX_PHASE;

        int e4Slot = 3 * 4 + Math.min(4, 7 - 4);         // row 3, file pair min(col,7-col) = 15
        int mgIndex = blockBase(KNIGHT, PHASE_MG) + e4Slot;
        int egIndex = blockBase(KNIGHT, PHASE_EG) + e4Slot;

        assertEquals(mgCoefficient, features[mgIndex], EPSILON, "white knight midgame coefficient on e4");
        assertEquals(egCoefficient, features[egIndex], EPSILON, "white knight endgame coefficient on e4");

        double sumOfAllOthers = 0.0;
        for (int j = 0; j < features.length; j++) {
            if (j != mgIndex && j != egIndex) {
                sumOfAllOthers += Math.abs(features[j]);
            }
        }

        assertEquals(0.0, sumOfAllOthers, EPSILON, "the symmetric kings must cancel; no other feature may be set");
    }

    @Test
    void featuresOfBlackPieceAreTheNegativeOfTheMirroredWhitePiece() {
        Board white = Fen.importFEN("7k/8/8/8/4N3/8/8/K7 w - - 0 1");
        // Same setup mirrored: black knight on e5 (the vertical mirror of e4), kings swapped.
        Board black = Fen.importFEN("k7/8/8/4n3/8/8/8/7K w - - 0 1");

        double[] whiteFeatures = JointMgEgPstTaperedTexelData.featuresOf(white);
        double[] blackFeatures = JointMgEgPstTaperedTexelData.featuresOf(black);

        for (int j = 0; j < whiteFeatures.length; j++) {
            assertEquals(-whiteFeatures[j], blackFeatures[j], EPSILON,
                    "black features must mirror-negate white features at index " + j);
        }
    }

    @Test
    void recenterRemovesAUniformPerBlockOffset() {
        double[] params = JointMgEgPstTaperedTexelData.currentTableValues();
        int base = blockBase(KNIGHT, PHASE_EG);
        double offset = 7.0;

        for (int slot = 0; slot < SLOTS_PER_BLOCK; slot++) {
            params[base + slot] += offset;
        }

        double[] recentered = JointMgEgPstTaperedTexelData.recenterToCurrentMeans(params);

        for (int j = 0; j < recentered.length; j++) {
            assertEquals(JointMgEgPstTaperedTexelData.currentTableValues()[j], recentered[j], EPSILON,
                    "a uniform per-block offset (a material re-rating) must be removed at index " + j);
        }
    }

    @Test
    void recenterPreservesTheTunedShapeAndTheBlockMean() {
        double[] params = JointMgEgPstTaperedTexelData.currentTableValues();
        int base = blockBase(KING, PHASE_MG);
        params[base + 5] += 5.0;                         // a single-slot (shape) change

        double[] recentered = JointMgEgPstTaperedTexelData.recenterToCurrentMeans(params);

        double currentMean = mean(JointMgEgPstTaperedTexelData.currentTableValues(), base);
        double recenteredMean = mean(recentered, base);

        assertEquals(currentMean, recenteredMean, EPSILON, "re-centering must preserve the block mean");
        assertTrue(recentered[base + 5] > recentered[base + 6],
                "the tuned shape (slot 5 raised relative to its neighbors) must survive re-centering");
    }

    @Test
    void startPositionHasFullPhase() {
        assertEquals(MAX_PHASE, JointMgEgPstTaperedTexelData.phaseOf(Board.createNewGame()),
                "the opening position must have full material phase");
    }

    private static double mean(double[] values, int base) {
        double sum = 0.0;

        for (int slot = 0; slot < SLOTS_PER_BLOCK; slot++) {
            sum += values[base + slot];
        }

        return sum / SLOTS_PER_BLOCK;
    }
}
