package org.michaelfl.mychess;

import org.junit.jupiter.api.Tag;
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
 * Validates the Zurichess -> {@link Sample} adapter against the real evaluation
 * and end to end against the {@link TexelTuner}. Skipped when the (git-ignored)
 * dataset is absent — see {@code tuning-data/README.md}.
 *
 * @author Michael Fleischhauer
 */
@Tag("slow")
class PawnPstTexelDataTest {

    private static final Path DATASET = Path.of("tuning-data", "quiet-labeled.epd");
    private static final String RESULT_TAG = " c9 ";

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void featureVectorMatchesTheSymmetricPawnContribution() throws IOException {
        assumeTrue(Files.exists(DATASET), "Zurichess dataset not present — see tuning-data/README.md");

        // Two arbitrary symmetric parameter vectors: the current table and a
        // distinct spread, so the check is not trivially satisfied.
        double[] probeA = PawnPstTexelData.currentTableValues();
        double[] probeB = new double[PawnPstTexelData.PARAM_COUNT];
        for (int i = 0; i < probeB.length; i++) {
            probeB[i] = (i - 12) * 3.5;
        }

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

                double[] features = PawnPstTexelData.featuresOf(board);

                for (double[] probe : new double[][] {probeA, probeB}) {
                    assertEquals(PawnPstTexelData.symmetricContribution(board, probe), dot(features, probe), 1e-9,
                            "the feature-reconstructed pawn contribution must equal the direct symmetric one "
                                    + "(guards the file/rank mapping)");
                }

                checked++;
            }
        }

        assertTrue(checked > 500, "expected to cross-check many positions, got " + checked);
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void tuningRealDataReducesTheError() {
        assumeTrue(Files.exists(DATASET), "Zurichess dataset not present — see tuning-data/README.md");

        List<Sample> data = PawnPstTexelData.load(DATASET, 10_000);
        assertTrue(data.size() > 1000, "expected a usable number of samples, got " + data.size());

        double[] start = PawnPstTexelData.currentTableValues();
        double startError = TexelTuner.meanSquaredError(data, start, TexelTuner.calibrateK(data, start));

        var config = new TexelTuner.Config(4.0, 1.0, 4);
        double[] tuned = TexelTuner.tune(data, start, config, null);
        double tunedError = TexelTuner.meanSquaredError(data, tuned, TexelTuner.calibrateK(data, tuned));

        assertTrue(tunedError < startError,
                "tuning real data must reduce the error (start=%.6f, tuned=%.6f)"
                        .formatted(startError, tunedError));
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
