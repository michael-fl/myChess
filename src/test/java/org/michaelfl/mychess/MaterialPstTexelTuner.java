package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command-line runner for option B: joint material + piece-square-table Texel
 * tuning (king frozen); see {@link MaterialPstTexelData}. After tuning, each
 * table's mean is folded into that piece's material value so the printed tables
 * are mean-zero (pure position) and the printed material values carry the level.
 *
 * <p>Usage: {@code MaterialPstTexelTuner [epdFile] [maxSamples]}. Feature vectors
 * are dense (157 doubles per sample), so run with a large heap, e.g.
 * {@code -Xmx4g}, and cap the sample count for a first pass.
 *
 * @author Michael Fleischhauer
 */
public final class MaterialPstTexelTuner {

    private static final int VALIDATION_EVERY = 10;

    private MaterialPstTexelTuner() {
        // entry point only
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : "tuning-data/quiet-labeled.epd");
        int maxSamples = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.printf("Loading up to %,d positions from %s ...%n", maxSamples, epd);
        List<Sample> all = MaterialPstTexelData.load(epd, maxSamples);
        System.out.printf("Loaded %,d samples%n", all.size());

        var training = new ArrayList<Sample>();
        var validation = new ArrayList<Sample>();

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        System.out.printf("train=%,d  validation=%,d%n%n", training.size(), validation.size());

        double[] start = MaterialPstTexelData.currentParameters();
        double kStart = TexelTuner.calibrateK(training, start);
        System.out.printf("start:  K=%.6f  trainMSE=%.6f  valMSE=%.6f%n",
                kStart,
                TexelTuner.meanSquaredError(training, start, kStart),
                TexelTuner.meanSquaredError(validation, start, kStart));

        var config = new TexelTuner.Config(8.0, 0.5, 12);
        long startedMs = System.currentTimeMillis();

        double[] tuned = TexelTuner.tune(training, start, config,
                (step, error, k) -> System.out.printf("  step=%.4f  trainMSE=%.6f  K=%.6f%n", step, error, k));

        double seconds = (System.currentTimeMillis() - startedMs) / 1000.0;
        double kTuned = TexelTuner.calibrateK(training, tuned);

        System.out.printf("%ntuned:  K=%.6f  trainMSE=%.6f  valMSE=%.6f  (%.1fs)%n",
                kTuned,
                TexelTuner.meanSquaredError(training, tuned, kTuned),
                TexelTuner.meanSquaredError(validation, tuned, kTuned),
                seconds);

        double[] folded = MaterialPstTexelData.foldPstMeansIntoMaterial(tuned);

        printMaterial(start, tuned, folded);
        printTables(folded);
    }

    /** Kind ({@code piece & 7}) of a tuned slot: slot 0 = knight (kind 1). */
    private static final int FIRST_TUNED_KIND = 1;

    /** Material values: current, raw-tuned, and effective after folding each table's mean. */
    private static void printMaterial(double[] start, double[] tuned, double[] folded) {
        System.out.println("\n=== material values (paste into WeightingFunction.weightOfPiece) ===");
        System.out.printf("%-8s %8s %8s %10s%n", "kind", "current", "tuned", "effective");

        for (int slot = 0; slot < MaterialPstTexelData.MATERIAL_COUNT; slot++) {
            System.out.printf("%-8s %8.0f %8.1f %10.0f%n",
                    MaterialPstTexelData.KIND_NAMES[slot], start[slot], tuned[slot], folded[slot]);
        }
    }

    /** The four mean-zero tables (pawn and king excluded — they stay at the current master tables). */
    private static void printTables(double[] folded) {
        for (int slot = 0; slot < MaterialPstTexelData.MATERIAL_COUNT; slot++) {
            int offset = MaterialPstTexelData.PST_BLOCK_OFFSET[slot];
            double[] block = Arrays.copyOfRange(folded, offset, offset + MaterialPstTexelData.BLOCK_SIZE);

            System.out.printf("%n=== tuned white %s table, mean-zero (paste into PieceSquareTables) ===%n",
                    MaterialPstTexelData.KIND_NAMES[slot]);
            System.out.print(AllPstTexelData.formatTable(slot + FIRST_TUNED_KIND, block));
        }

        System.out.println("\nNote: the pawn and king tables are frozen (kept at the current master tables).");
    }
}
