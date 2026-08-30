package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fits the {@code KING_ATTACK_PENALTY} table against game results.
 *
 * <p>This is the first curve in the project placed by a fit rather than by hand. All three
 * shelved king-safety attempts wrote their tables from intuition and then measured the whole
 * thing at once, which is why none of them could say afterwards whether the idea or the numbers
 * were wrong. See `docs/king-safety.md`.
 *
 * <p>Reports a **held-out validation error** alongside the training error, on a split taken
 * before tuning. A table with eight free parameters against a million positions is not at
 * serious risk of overfitting, but the split costs nothing and it is the number that says
 * whether an improvement is real: the fitted defense curve in the Audax fork moved its
 * validation error by 0.02 %, which is what "reproducible but tiny" looks like and is worth
 * knowing before shipping anything.
 *
 * <p>Usage — a measurement driver, not a test:
 * <pre>{@code
 * java -cp target/classes:target/test-classes:target/dependency/* \
 *     org.michaelfl.mychess.TexelKingAttackTuner tuning-data/hybrid.epd
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class TexelKingAttackTuner {

    private static final String DEFAULT_EPD = "tuning-data/hybrid.epd";

    /** Share of samples held out of the fit. */
    private static final double VALIDATION_SHARE = 0.2;

    /** Deterministic split: every fifth sample validates, so a re-run is comparable. */
    private static final int VALIDATION_EVERY = (int) Math.round(1.0 / VALIDATION_SHARE);

    private TexelKingAttackTuner() {
        // measurement driver
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--dump-units".equals(args[0])) {
            dumpUnits(Path.of(args[1]), Integer.parseInt(args[2]));
            return;
        }

        Path epd = Path.of(args.length > 0 ? args[0] : DEFAULT_EPD);
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : 0;

        System.out.printf(Locale.ROOT, "loading %s%s%n", epd,
                limit > 0 ? " (first " + limit + " positions)" : "");

        int materialWindow = args.length > 4 ? Integer.parseInt(args[4])
                : KingAttackTexelData.NO_MATERIAL_WINDOW;
        List<Sample> all = KingAttackTexelData.load(epd, limit, materialWindow);

        if (materialWindow != KingAttackTexelData.NO_MATERIAL_WINDOW) {
            System.out.printf(Locale.ROOT, "material window: +-%d cp%n", materialWindow);
        }
        var training = new ArrayList<Sample>(all.size());
        var validation = new ArrayList<Sample>(all.size() / VALIDATION_EVERY + 1);

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        System.out.printf(Locale.ROOT, "%,d samples: %,d training, %,d validation%n%n",
                all.size(), training.size(), validation.size());

        reportDistribution(all);

        var start = new double[KingAttackTexelData.PARAMETER_COUNT];
        double k = TexelTuner.calibrateK(training, start);

        System.out.printf(Locale.ROOT, "k = %.6f%n", k);
        System.out.printf(Locale.ROOT, "before: training %.6f   validation %.6f%n%n",
                TexelTuner.meanSquaredError(training, start, k),
                TexelTuner.meanSquaredError(validation, start, k));

        // Step schedule from the command line, because the default one turned out to be a
        // ceiling rather than a detail: coordinate descent starting at 0 with steps 4/2/1/0.5
        // and 12 rounds each cannot move a parameter past 12*(4+2+1+0.5) = 90. The anchor fit
        // saturated at 73.5 across 40 bootstrap replicates with an interval width of 1.0 --
        // the signature of every replicate hitting the same wall, not of a well-determined
        // parameter. Widen the schedule and the question answers itself: if the values climb,
        // the earlier fit was a lower bound.
        var config = args.length > 3
                ? new TexelTuner.Config(Double.parseDouble(args[2]), 0.5, Integer.parseInt(args[3]))
                : TexelTuner.Config.defaults();

        System.out.printf(Locale.ROOT, "step schedule: initial %.1f, min 0.5, max %d rounds "
                        + "(reachable magnitude ~%.0f)%n%n",
                config.initialStep(), config.maxRoundsPerStep(),
                config.maxRoundsPerStep() * 2.0 * config.initialStep());

        double[] fitted = TexelTuner.tune(training, start, config,
                (step, error, kk) -> System.out.printf(Locale.ROOT,
                        "  step %5.2f  error %.6f%n", step, error));

        double kAfter = TexelTuner.calibrateK(training, fitted);

        System.out.printf(Locale.ROOT, "%nafter:  training %.6f   validation %.6f%n",
                TexelTuner.meanSquaredError(training, fitted, kAfter),
                TexelTuner.meanSquaredError(validation, fitted, kAfter));

        report(fitted);
    }

    /**
     * Prints {@code white black} units per position, for cross-checking against
     * {@code tools/king-attack-curve.py}, which computes the same quantity on a different board
     * representation. Two independent implementations agreeing is the only practical guard here:
     * a wrong unit count would not fail anything, it would quietly fit a plausible curve to the
     * wrong index.
     */
    private static void dumpUnits(Path epd, int limit) {
        // Reads the units off the board rather than off the feature vector. The first version
        // reconstructed them from the features and disagreed with the Python implementation on
        // 8.7 % of positions -- every one of them a position where both sides had the *same*
        // count, so the +1 and the -1 cancelled and the vector was all zeros. Correct for the
        // fit, unreadable as a dump, and a good reminder that a cross-check can fail on the
        // check rather than on the thing being checked.
        for (Board board : KingAttackTexelData.loadBoards(epd, limit)) {
            System.out.println(Math.min(KingAttackUnits.of(board, GameStatus.TURN_WHITE),
                                        KingAttackTexelData.MAX_UNITS)
                    + " " + Math.min(KingAttackUnits.of(board, GameStatus.TURN_BLACK),
                                     KingAttackTexelData.MAX_UNITS));
        }
    }

    /**
     * How often each index is asked for — an entry never reached cannot be trusted.
     *
     * <p>Reads the sign of each feature, not its magnitude: since the features are scaled by the
     * game phase they are no longer ±1, and a position at phase 0 carries a feature of exactly
     * zero. Those are reported as index 0, which is what they are for the fit.
     */
    private static void reportDistribution(List<Sample> samples) {
        var counts = new long[KingAttackTexelData.PARAMETER_COUNT + 1];

        for (Sample sample : samples) {
            double[] features = sample.features();
            int white = 0;
            int black = 0;

            for (int i = 0; i < features.length; i++) {
                if (features[i] > 0) {
                    white = i + 1;
                }
                if (features[i] < 0) {
                    black = i + 1;
                }
            }

            counts[white]++;
            counts[black]++;
        }

        long total = 2L * samples.size();

        System.out.println("how often each index occurs (both sides counted):");

        for (int units = 0; units < counts.length; units++) {
            System.out.printf(Locale.ROOT, "  %2d units %,12d  %5.1f %%%n",
                    units, counts[units], 100.0 * counts[units] / total);
        }

        System.out.println();
    }

    private static void report(double[] fitted) {
        System.out.println("\nfitted KING_ATTACK_PENALTY (centipawns, index 0 pinned at 0):");
        System.out.printf(Locale.ROOT, "  %2d -> %7.1f%n", 0, 0.0);

        double previous = 0.0;

        for (int i = 0; i < fitted.length; i++) {
            System.out.printf(Locale.ROOT, "  %2d -> %7.1f   step %+7.1f%n",
                    i + 1, fitted[i], fitted[i] - previous);
            previous = fitted[i];
        }

        System.out.printf(Locale.ROOT, "%nRead the steps, not only the values: a curve that rises "
                + "steeply late is the progressive shape the design assumes, and one that rises "
                + "and then falls is a sign the upper entries are fitted on too little data.%n");
    }
}
