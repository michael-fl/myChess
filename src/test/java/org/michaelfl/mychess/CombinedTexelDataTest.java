package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Validates the combined (pawn + factors) adapter: its feature vector must be
 * exactly the already-validated pawn features followed by the selected factor
 * features. Skipped when the (git-ignored) dataset is absent.
 *
 * @author Michael Fleischhauer
 */
class CombinedTexelDataTest {

    private static final Path DATASET = Path.of("tuning-data", "quiet-labeled.epd");
    private static final String RESULT_TAG = " c9 ";

    /** The factor indices the combined adapter keeps tunable (skip 0=position, 3=castling). */
    private static final int[] TUNABLE_FACTORS = {1, 2, 4, 5, 6};

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void combinedFeaturesConcatenatePawnAndFactorFeatures() throws IOException {
        assumeTrue(Files.exists(DATASET), "Zurichess dataset not present — see tuning-data/README.md");

        int pawnCount = CombinedTexelData.pawnParameterCount();
        int checked = 0;

        try (var lines = Files.lines(DATASET, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && checked < 2000) {
                String line = iterator.next();
                int tagIndex = line.indexOf(RESULT_TAG);
                if (tagIndex < 0) {
                    continue;
                }

                Board board;
                try {
                    board = Fen.importFEN(line.substring(0, tagIndex).trim() + " 0 1");
                } catch (IllegalArgumentException e) {
                    continue;
                }

                Sample sample = CombinedTexelData.toSample(line, new WeightingFunction());
                if (sample == null) {
                    continue;
                }

                double[] features = sample.features();
                double[] pawnFeatures = PawnPstTexelData.featuresOf(board);
                double[] factorFeatures = new WeightingFunction().analyzeFactors(board).features();

                for (int j = 0; j < pawnCount; j++) {
                    assertEquals(pawnFeatures[j], features[j], 1e-9, "pawn feature " + j + " must match");
                }
                for (int i = 0; i < TUNABLE_FACTORS.length; i++) {
                    assertEquals(factorFeatures[TUNABLE_FACTORS[i]], features[pawnCount + i], 1e-9,
                            "factor feature " + i + " must match the selected factor");
                }

                checked++;
            }
        }

        assertTrue(checked > 500, "expected to cross-check many positions, got " + checked);
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void tuningRealDataReducesTheError() {
        assumeTrue(Files.exists(DATASET), "Zurichess dataset not present — see tuning-data/README.md");

        List<Sample> data = CombinedTexelData.load(DATASET, 10_000);
        assertTrue(data.size() > 1000, "expected a usable number of samples, got " + data.size());

        double[] start = CombinedTexelData.currentParameters();
        double startError = TexelTuner.meanSquaredError(data, start, TexelTuner.calibrateK(data, start));

        var config = new TexelTuner.Config(8.0, 0.01, 6);
        double[] tuned = TexelTuner.tune(data, start, config, null);
        double tunedError = TexelTuner.meanSquaredError(data, tuned, TexelTuner.calibrateK(data, tuned));

        assertTrue(tunedError < startError,
                "joint tuning must reduce the error (start=%.6f, tuned=%.6f)"
                        .formatted(startError, tunedError));
    }
}
