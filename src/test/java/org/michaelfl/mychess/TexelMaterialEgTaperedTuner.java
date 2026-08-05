package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line runner for the offline Texel tuning of the <b>endgame material
 * values</b> of knight, bishop, rook and queen (midgame values held fixed) via
 * the phase-aware {@link MaterialEgTaperedTexelData} adapter. Counterpart of the
 * tapered piece-square-table tuners, for the material term.
 *
 * <p>Usage: {@code TexelMaterialEgTaperedTuner [epdFile] [maxSamples]} — defaults
 * to {@code tuning-data/quiet-labeled.epd} and all samples. Loads the labeled
 * positions, splits them 90/10 into a training and a validation set, tunes the
 * four endgame material values on the training set, reports the mean squared
 * error before/after on both sets, and prints each tuned value alongside its
 * midgame value and the endgame delta (paste the values into
 * {@code WeightingFunction.weightOfPieceEndgame}).
 *
 * <p>The tuning objective (MSE against game outcomes) is only a proxy: the tuned
 * values MUST be confirmed with a cutechess SPRT against v4.3.1 before shipping.
 *
 * @author Michael Fleischhauer
 */
public final class TexelMaterialEgTaperedTuner {

    private static final int VALIDATION_EVERY = 10;

    private TexelMaterialEgTaperedTuner() {
        // entry point only
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : "tuning-data/quiet-labeled.epd");
        int maxSamples = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.printf("Loading up to %,d positions from %s ...%n", maxSamples, epd);
        List<Sample> all = MaterialEgTaperedTexelData.load(epd, maxSamples);
        System.out.printf("Loaded %,d samples%n", all.size());

        var training = new ArrayList<Sample>();
        var validation = new ArrayList<Sample>();

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        System.out.printf("train=%,d  validation=%,d%n%n", training.size(), validation.size());

        double[] start = MaterialEgTaperedTexelData.currentTableValues();
        double kStart = TexelTuner.calibrateK(training, start);
        System.out.printf("start:  K=%.6f  trainMSE=%.6f  valMSE=%.6f%n",
                kStart,
                TexelTuner.meanSquaredError(training, start, kStart),
                TexelTuner.meanSquaredError(validation, start, kStart));

        var config = new TexelTuner.Config(8.0, 0.5, 30);
        long startedMs = System.currentTimeMillis();

        double[] tuned = TexelTuner.tune(training, start, config,
                (step, error, k) -> System.out.printf("  step=%.2f  trainMSE=%.6f  K=%.6f%n", step, error, k));

        double seconds = (System.currentTimeMillis() - startedMs) / 1000.0;
        double kTuned = TexelTuner.calibrateK(training, tuned);

        System.out.printf("%ntuned:  K=%.6f  trainMSE=%.6f  valMSE=%.6f  (%.1fs)%n",
                kTuned,
                TexelTuner.meanSquaredError(training, tuned, kTuned),
                TexelTuner.meanSquaredError(validation, tuned, kTuned),
                seconds);

        System.out.println("\n=== tuned endgame material values (paste into WeightingFunction.weightOfPieceEndgame) ===");
        System.out.printf("%-8s %6s %6s %8s%n", "piece", "MG", "EG", "delta");
        for (int index = 0; index < MaterialEgTaperedTexelData.PARAM_COUNT; index++) {
            int mg = MaterialEgTaperedTexelData.midGameValue(index);
            int eg = (int) Math.round(tuned[index]);
            System.out.printf("%-8s %6d %6d %+8d%n", MaterialEgTaperedTexelData.pieceName(index), mg, eg, eg - mg);
        }
    }
}
