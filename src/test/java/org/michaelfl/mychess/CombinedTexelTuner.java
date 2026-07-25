package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command-line runner for the joint (pawn table + factors) Texel tuning; see
 * {@link CombinedTexelData}. {@code positionFactor} and {@code castlingFactor}
 * are frozen. Usage: {@code CombinedTexelTuner [epdFile] [maxSamples]}.
 *
 * @author Michael Fleischhauer
 */
public final class CombinedTexelTuner {

    private static final int VALIDATION_EVERY = 10;

    private CombinedTexelTuner() {
        // entry point only
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : "tuning-data/quiet-labeled.epd");
        int maxSamples = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.printf("Loading up to %,d positions from %s ...%n", maxSamples, epd);
        List<Sample> all = CombinedTexelData.load(epd, maxSamples);
        System.out.printf("Loaded %,d samples%n", all.size());

        var training = new ArrayList<Sample>();
        var validation = new ArrayList<Sample>();

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        System.out.printf("train=%,d  validation=%,d%n%n", training.size(), validation.size());

        double[] start = CombinedTexelData.currentParameters();
        double kStart = TexelTuner.calibrateK(training, start);
        System.out.printf("start:  K=%.6f  trainMSE=%.6f  valMSE=%.6f%n",
                kStart,
                TexelTuner.meanSquaredError(training, start, kStart),
                TexelTuner.meanSquaredError(validation, start, kStart));

        var config = new TexelTuner.Config(8.0, 0.002, 40);
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

        int pawnCount = CombinedTexelData.pawnParameterCount();
        double[] pawnTable = Arrays.copyOfRange(tuned, 0, pawnCount);
        double[] tunedFactors = Arrays.copyOfRange(tuned, pawnCount, tuned.length);
        double[] startFactors = Arrays.copyOfRange(start, pawnCount, start.length);
        String[] names = CombinedTexelData.factorNames();

        System.out.println("\n=== tuned white pawn table (paste into PieceSquareTables.pawnTableWhiteString) ===");
        System.out.print(PawnPstTexelData.formatPawnTable(pawnTable));

        System.out.println("\n=== tuned factors (start -> tuned; positionFactor and castlingFactor frozen) ===");
        for (int i = 0; i < names.length; i++) {
            System.out.printf("%-24s %+.4f -> %+.4f%n", names[i], startFactors[i], tunedFactors[i]);
        }
    }
}
