package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command-line runner for the full piece-square-table Texel tuning; see
 * {@link AllPstTexelData}. Tunes all six symmetric tables jointly and prints
 * each one ready to paste into {@link PieceSquareTables}. The scalar factors are
 * <b>not</b> tuned here (they stay at their current values inside {@code baseEval}).
 *
 * <p>Usage: {@code AllPstTexelTuner [epdFile] [maxSamples]}. Because the feature
 * vectors are dense (184 doubles per sample), run with a large heap, e.g.
 * {@code -Xmx4g}, and cap the sample count for a first pass.
 *
 * @author Michael Fleischhauer
 */
public final class AllPstTexelTuner {

    private static final int VALIDATION_EVERY = 10;

    private AllPstTexelTuner() {
        // entry point only
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : "tuning-data/quiet-labeled.epd");
        int maxSamples = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.printf("Loading up to %,d positions from %s ...%n", maxSamples, epd);
        List<Sample> all = AllPstTexelData.load(epd, maxSamples);
        System.out.printf("Loaded %,d samples%n", all.size());

        var training = new ArrayList<Sample>();
        var validation = new ArrayList<Sample>();

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        System.out.printf("train=%,d  validation=%,d%n%n", training.size(), validation.size());

        double[] start = AllPstTexelData.currentParameters();
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

        printBiggestChanges(start, tuned);

        for (int kind = 0; kind < AllPstTexelData.KIND_COUNT; kind++) {
            int offset = AllPstTexelData.BLOCK_OFFSET[kind];
            double[] block = Arrays.copyOfRange(tuned, offset, offset + AllPstTexelData.blockSize(kind));

            System.out.printf("%n=== tuned white %s table (paste into PieceSquareTables) ===%n",
                    AllPstTexelData.KIND_NAMES[kind]);
            System.out.print(AllPstTexelData.formatTable(kind, block));
        }
    }

    /** Print the largest per-parameter shifts, grouped by piece kind, to spot where the tune moved most. */
    private static void printBiggestChanges(double[] start, double[] tuned) {
        System.out.println("\n=== max absolute parameter shift per kind (centipawns) ===");

        for (int kind = 0; kind < AllPstTexelData.KIND_COUNT; kind++) {
            int offset = AllPstTexelData.BLOCK_OFFSET[kind];
            int size = AllPstTexelData.blockSize(kind);
            double maxShift = 0.0;

            for (int i = offset; i < offset + size; i++) {
                maxShift = Math.max(maxShift, Math.abs(tuned[i] - start[i]));
            }

            System.out.printf("%-8s max |Δ| = %.1f%n", AllPstTexelData.KIND_NAMES[kind], maxShift);
        }
    }
}
