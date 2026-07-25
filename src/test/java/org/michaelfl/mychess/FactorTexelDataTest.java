package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.WeightingFunction.FactorBreakdown;
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
 * Validates the factor breakdown / adapter against the real evaluation and end
 * to end against the {@link TexelTuner}. Skipped when the (git-ignored) dataset
 * is absent — see {@code tuning-data/README.md}.
 *
 * @author Michael Fleischhauer
 */
class FactorTexelDataTest {

    private static final Path DATASET = Path.of("tuning-data", "quiet-labeled.epd");
    private static final String RESULT_TAG = " c9 ";

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void breakdownReconstructsTheRealEvaluation() throws IOException {
        assumeTrue(Files.exists(DATASET), "Zurichess dataset not present — see tuning-data/README.md");

        var evaluator = new WeightingFunction();
        double[] factors = WeightingFunction.tunableFactorValues();
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

                FactorBreakdown breakdown = evaluator.analyzeFactors(board);
                if (WeightingFunction.isIllegalWeight(breakdown.eval())) {
                    continue;
                }

                // The eval must equal the factor-independent material part plus
                // the factor contributions (up to the eval's rounding).
                double reconstructed = material(board) + dot(breakdown.features(), factors);

                assertEquals(breakdown.eval(), reconstructed, 1.0,
                        "eval must equal material + sum(feature * factor) (guards the factor coefficients)");
                checked++;
            }
        }

        assertTrue(checked > 500, "expected to cross-check many positions, got " + checked);
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void tuningRealDataReducesTheError() {
        assumeTrue(Files.exists(DATASET), "Zurichess dataset not present — see tuning-data/README.md");

        List<Sample> data = FactorTexelData.load(DATASET, 10_000);
        assertTrue(data.size() > 1000, "expected a usable number of samples, got " + data.size());

        double[] start = FactorTexelData.currentFactorValues();
        double startError = TexelTuner.meanSquaredError(data, start, TexelTuner.calibrateK(data, start));

        var config = new TexelTuner.Config(0.5, 0.01, 6);
        double[] tuned = TexelTuner.tune(data, start, config, null);
        double tunedError = TexelTuner.meanSquaredError(data, tuned, TexelTuner.calibrateK(data, tuned));

        assertTrue(tunedError < startError,
                "tuning real data must reduce the error (start=%.6f, tuned=%.6f)"
                        .formatted(startError, tunedError));
    }

    /** Factor-independent material term: sum of piece values, White minus Black. */
    private static double material(Board board) {
        byte[] raw = board.getRawBoard();
        double sum = 0.0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                byte piece = raw[ChessUtil.getFieldFromColAndRow(col, row)];

                if (piece != Board.empty && piece != Board.illegal) {
                    boolean white = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE;
                    sum += white ? WeightingFunction.weightOfPiece[piece] : -WeightingFunction.weightOfPiece[piece];
                }
            }
        }

        return sum;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
