package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line runner for the offline Texel tuning of the scalar
 * {@link WeightingFunction} evaluation factors.
 *
 * <p>Usage: {@code TexelFactorTuner [epdFile] [maxSamples]} — defaults to
 * {@code tuning-data/quiet-labeled.epd} and all samples. Loads the labeled
 * positions, splits them 90/10 into a training and a validation set, tunes all
 * factors jointly on the training set, reports the mean squared error before /
 * after on both sets, and prints each factor's start and tuned value. The tuned
 * factors must still be confirmed with a real cutechess match; the tuning
 * objective is only a proxy.
 *
 * @author Michael Fleischhauer
 */
public final class TexelFactorTuner {

    private static final int VALIDATION_EVERY = 10;

    private TexelFactorTuner() {
        // entry point only
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : "tuning-data/quiet-labeled.epd");
        int maxSamples = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.printf("Loading up to %,d positions from %s ...%n", maxSamples, epd);
        List<Sample> all = FactorTexelData.load(epd, maxSamples);
        System.out.printf("Loaded %,d samples%n", all.size());

        var training = new ArrayList<Sample>();
        var validation = new ArrayList<Sample>();

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        System.out.printf("train=%,d  validation=%,d%n%n", training.size(), validation.size());

        String[] names = FactorTexelData.factorNames();
        double[] start = FactorTexelData.currentFactorValues();
        double kStart = TexelTuner.calibrateK(training, start);
        System.out.printf("start:  K=%.6f  trainMSE=%.6f  valMSE=%.6f%n",
                kStart,
                TexelTuner.meanSquaredError(training, start, kStart),
                TexelTuner.meanSquaredError(validation, start, kStart));

        var config = new TexelTuner.Config(0.5, 0.002, 40);
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

        System.out.println("\n=== tuned factors (start -> tuned) ===");
        for (int i = 0; i < names.length; i++) {
            System.out.printf("%-24s %+.4f -> %+.4f%n", names[i], start[i], tuned[i]);
        }
    }
}
