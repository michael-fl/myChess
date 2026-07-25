package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Validates the option-B adapter with the pawn frozen (material + tables for
 * knight, bishop, rook, queen; pawn and king frozen): parameter layout, an
 * independent feature cross-check, the eval-neutrality of the mean-zero gauge
 * fix, and error reduction on real data.
 *
 * @author Michael Fleischhauer
 */
class MaterialPstTexelDataTest {

    private static final Path DATASET = Path.of("tuning-data", "quiet-labeled.epd");
    private static final double POSITION_FACTOR = 0.5;
    private static final int TOTAL_PARAMS = 132;
    private static final int FIRST_TUNED_KIND = 1; // knight
    private static final int LAST_TUNED_KIND = 4;  // queen

    /** Positions with varied material so the knight/bishop/rook/queen features are non-trivial. */
    private static final String[] FENS = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "r1bqkb1r/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "8/5k2/8/8/3Q4/8/5K2/8 w - - 0 1",
            "r3k2r/pp4pp/8/8/8/8/PP4PP/R3K2R w KQkq - 0 1"
    };

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void parameterLayoutIsContiguousAndFileSymmetric() {
        assertEquals(TOTAL_PARAMS, MaterialPstTexelData.PARAM_COUNT, "total parameter count");

        boolean[] covered = new boolean[MaterialPstTexelData.PARAM_COUNT];

        // material block
        for (int slot = 0; slot < MaterialPstTexelData.MATERIAL_COUNT; slot++) {
            covered[slot] = true;
        }

        for (int kind = FIRST_TUNED_KIND; kind <= LAST_TUNED_KIND; kind++) {
            int slot = kind - FIRST_TUNED_KIND;

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int param = MaterialPstTexelData.paramOf(kind, col, row);
                    int mirrored = MaterialPstTexelData.paramOf(kind, 7 - col, row);

                    assertEquals(param, mirrored,
                            "file symmetry for kind %d, col %d, row %d".formatted(kind, col, row));
                    assertTrue(param >= MaterialPstTexelData.PST_BLOCK_OFFSET[slot]
                                    && param < MaterialPstTexelData.PST_BLOCK_OFFSET[slot] + MaterialPstTexelData.BLOCK_SIZE,
                            "param %d must lie inside the block of kind %d".formatted(param, kind));

                    covered[param] = true;
                }
            }
        }

        for (int i = 0; i < covered.length; i++) {
            assertTrue(covered[i], "parameter %d must be reachable".formatted(i));
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void featureContributionMatchesIndependentMaterialPlusSymmetricTables() {
        double[] current = MaterialPstTexelData.currentParameters();

        for (String fen : FENS) {
            Board board = Fen.importFEN(fen);

            double viaFeatures = dot(MaterialPstTexelData.featuresOf(board), current);
            double independent = independentContribution(board);

            assertEquals(independent, viaFeatures, 1e-9,
                    "feature contribution must match the direct material + symmetric-table sum for " + fen);
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void foldingTableMeansIntoMaterialIsEvalNeutralAndZeroMeansTheTables() {
        double[] current = MaterialPstTexelData.currentParameters();
        double[] folded = MaterialPstTexelData.foldPstMeansIntoMaterial(current);

        for (int slot = 0; slot < MaterialPstTexelData.MATERIAL_COUNT; slot++) {
            double mean = MaterialPstTexelData.mean(folded, MaterialPstTexelData.PST_BLOCK_OFFSET[slot], MaterialPstTexelData.BLOCK_SIZE);

            assertEquals(0.0, mean, 1e-9,
                    "table for slot %d must be mean-zero after folding".formatted(slot));
        }

        for (String fen : FENS) {
            Board board = Fen.importFEN(fen);
            double[] features = MaterialPstTexelData.featuresOf(board);

            assertEquals(dot(features, current), dot(features, folded), 1e-6,
                    "folding the table means into material must not change the eval for " + fen);
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void tuningRealDataReducesTheError() {
        assumeTrue(Files.exists(DATASET), "Zurichess dataset not present — see tuning-data/README.md");

        List<Sample> data = MaterialPstTexelData.load(DATASET, 10_000);
        assertTrue(data.size() > 1000, "expected a usable number of samples, got " + data.size());

        double[] start = MaterialPstTexelData.currentParameters();
        double startError = TexelTuner.meanSquaredError(data, start, TexelTuner.calibrateK(data, start));

        var config = new TexelTuner.Config(8.0, 0.5, 6);
        double[] tuned = TexelTuner.tune(data, start, config, null);
        double tunedError = TexelTuner.meanSquaredError(data, tuned, TexelTuner.calibrateK(data, tuned));

        assertTrue(tunedError < startError,
                "material + PST tuning must reduce the error (start=%.6f, tuned=%.6f)"
                        .formatted(startError, tunedError));
    }

    /**
     * Material and symmetric table contribution for knight, bishop, rook and
     * queen, computed directly from {@link WeightingFunction#weightOfPiece} and
     * the current tables — independently of {@link MaterialPstTexelData#featuresOf}
     * and its parameter indexing. Pawn and king are excluded (frozen).
     */
    private static double independentContribution(Board board) {
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
                if (kind < FIRST_TUNED_KIND || kind > LAST_TUNED_KIND) {
                    continue; // pawn and king excluded
                }

                byte whitePiece = (byte) (Board.whitePawn + kind);
                boolean white = piece <= Board.whiteKing;
                double sign = white ? 1.0 : -1.0;
                int tableRow = white ? row : 7 - row;

                double symValue = (PieceSquareTables.getPieceSquareWeight(whitePiece, ChessUtil.getFieldFromColAndRow(col, tableRow))
                        + PieceSquareTables.getPieceSquareWeight(whitePiece, ChessUtil.getFieldFromColAndRow(7 - col, tableRow))) / 2.0;

                sum += sign * WeightingFunction.weightOfPiece[whitePiece];
                sum += sign * symValue * POSITION_FACTOR;
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
