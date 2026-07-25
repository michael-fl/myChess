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
 * Validates the all-piece-square-table adapter: the parameter layout is
 * contiguous and file-symmetric, the feature vector reproduces an independent
 * symmetric position contribution, and tuning on real data reduces the error.
 * The data-driven tests are skipped when the git-ignored dataset is absent.
 *
 * @author Michael Fleischhauer
 */
@Tag("slow")
class AllPstTexelDataTest {

    private static final Path DATASET = Path.of("tuning-data", "quiet-labeled.epd");
    private static final String RESULT_TAG = " c9 ";
    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final double POSITION_FACTOR = 0.5;
    private static final int TOTAL_PARAMS = 184;

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void parameterLayoutIsContiguousAndFileSymmetric() {
        assertEquals(TOTAL_PARAMS, AllPstTexelData.PARAM_COUNT, "total parameter count");

        boolean[] covered = new boolean[AllPstTexelData.PARAM_COUNT];
        int totalBlockSize = 0;

        for (int kind = 0; kind < AllPstTexelData.KIND_COUNT; kind++) {
            totalBlockSize += AllPstTexelData.blockSize(kind);

            int rowBase = kind == 0 ? 1 : 0;
            int rankCount = kind == 0 ? 6 : 8;

            for (int localRank = 0; localRank < rankCount; localRank++) {
                int row = rowBase + localRank;

                for (int col = 0; col < 8; col++) {
                    int param = AllPstTexelData.paramOf(kind, col, row);
                    int mirrored = AllPstTexelData.paramOf(kind, 7 - col, row);

                    assertEquals(param, mirrored,
                            "file symmetry for kind %d, col %d, row %d".formatted(kind, col, row));
                    assertTrue(param >= AllPstTexelData.BLOCK_OFFSET[kind]
                                    && param < AllPstTexelData.BLOCK_OFFSET[kind] + AllPstTexelData.blockSize(kind),
                            "param %d must lie inside the block of kind %d".formatted(param, kind));

                    covered[param] = true;
                }
            }
        }

        assertEquals(TOTAL_PARAMS, totalBlockSize, "sum of block sizes");

        for (int i = 0; i < covered.length; i++) {
            assertTrue(covered[i], "parameter %d must be reachable from some square".formatted(i));
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void featureContributionMatchesIndependentSymmetricSum() throws IOException {
        assumeTrue(Files.exists(DATASET), "Zurichess dataset not present — see tuning-data/README.md");

        double[] current = AllPstTexelData.currentParameters();
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
                    board = Fen.importFEN(line.substring(0, tagIndex).trim() + EPD_COUNTER_SUFFIX);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                double viaFeatures = dot(AllPstTexelData.featuresOf(board), current);
                double independent = directSymmetricContribution(board);

                assertEquals(independent, viaFeatures, 1e-9,
                        "feature-derived symmetric contribution must match the direct table sum");

                checked++;
            }
        }

        assertTrue(checked > 500, "expected to cross-check many positions, got " + checked);
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void tuningRealDataReducesTheError() {
        assumeTrue(Files.exists(DATASET), "Zurichess dataset not present — see tuning-data/README.md");

        List<Sample> data = AllPstTexelData.load(DATASET, 10_000);
        assertTrue(data.size() > 1000, "expected a usable number of samples, got " + data.size());

        double[] start = AllPstTexelData.currentParameters();
        double startError = TexelTuner.meanSquaredError(data, start, TexelTuner.calibrateK(data, start));

        var config = new TexelTuner.Config(8.0, 0.5, 6);
        double[] tuned = TexelTuner.tune(data, start, config, null);
        double tunedError = TexelTuner.meanSquaredError(data, tuned, TexelTuner.calibrateK(data, tuned));

        assertTrue(tunedError < startError,
                "full-PST tuning must reduce the error (start=%.6f, tuned=%.6f)"
                        .formatted(startError, tunedError));
    }

    /**
     * The symmetric position contribution computed directly from the current
     * tables (averaging each file pair), independently of
     * {@link AllPstTexelData#featuresOf} and its parameter indexing.
     */
    private static double directSymmetricContribution(Board board) {
        byte[] raw = board.getRawBoard();
        double sum = 0.0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (piece < Board.whitePawn) {
                    continue;
                }

                int kind = piece & 7;
                byte whitePiece = (byte) (Board.whitePawn + kind);
                boolean white = piece <= Board.whiteKing;
                int tableRow = white ? row : 7 - row;

                double symValue = (PieceSquareTables.getPieceSquareWeight(whitePiece, ChessUtil.getFieldFromColAndRow(col, tableRow))
                        + PieceSquareTables.getPieceSquareWeight(whitePiece, ChessUtil.getFieldFromColAndRow(7 - col, tableRow))) / 2.0;

                sum += (white ? 1 : -1) * symValue;
            }
        }

        return sum * POSITION_FACTOR;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;

        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }
}
