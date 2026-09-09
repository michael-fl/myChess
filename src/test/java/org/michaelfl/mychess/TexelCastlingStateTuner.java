package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fits the four castling-state values against game results.
 *
 * <p>The last hand-written numbers in the evaluation. {@code castlingFactor = 0.25f} comes from
 * {@code dabec30}, the original implementation, and the {@code 0 / −1 / −2 / −4} progression from
 * {@code 2cd229a}; neither was ever tuned or swept. See {@link CastlingStateTexelData} for why the
 * levels are fitted rather than the factor.
 *
 * <p><b>Why this term is worth the fit when the king-safety ones were not.</b> With the
 * king-safety family the open question was always whether the term does anything at all, and the
 * answer kept being no. Here it is settled: switching the factor to zero moved myChess's castling
 * rate from 96 % to 63 % over the first 83 games of a 400-game self-play run — the largest
 * behavioral effect any single term has shown in this project. The term is load-bearing, and only
 * its magnitude and shape are unmeasured. That is the configuration in which Texel has worked
 * here: pawn endgame tables +22.3, king endgame +7.7, queen material +12.6, bishop pair +31.3.
 * The failures — joint MG/EG tables, tapered endgame material — were redundant terms.
 *
 * <p>Reports a <b>held-out validation error</b> next to the training error, on a split taken
 * before tuning. Three free parameters against a million positions are not at serious risk of
 * overfitting, but the split costs nothing and it is the number that says whether an improvement
 * is real.
 *
 * <p>Usage — a measurement driver, not a test:
 * <pre>{@code
 * java -cp target/classes:target/test-classes:target/dependency/* \
 *     org.michaelfl.mychess.TexelCastlingStateTuner tuning-data/hybrid.epd
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class TexelCastlingStateTuner {

    private static final String DEFAULT_EPD = "tuning-data/hybrid.epd";

    /** Share of samples held out of the fit. */
    private static final double VALIDATION_SHARE = 0.2;

    /** Deterministic split: every fifth sample validates, so a re-run is comparable. */
    private static final int VALIDATION_EVERY = (int) Math.round(1.0 / VALIDATION_SHARE);

    /** Below this share of king states a fitted level is reported as untrustworthy. */
    private static final double THIN_OCCUPANCY = 0.03;

    private static final String[] NAMES = { "castled", "both rights", "one right", "no rights" };

    private TexelCastlingStateTuner() {
        // measurement driver
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : DEFAULT_EPD);
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : 0;

        System.out.printf(Locale.ROOT, "loading %s%s%n", epd,
                limit > 0 ? " (first " + limit + " positions)" : "");

        List<Sample> all = CastlingStateTexelData.load(epd, limit);
        var training = new ArrayList<Sample>(all.size());
        var validation = new ArrayList<Sample>(all.size() / VALIDATION_EVERY + 1);

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        System.out.printf(Locale.ROOT, "%,d samples: %,d training, %,d validation%n%n",
                all.size(), training.size(), validation.size());

        double[] occupancy = reportOccupancy(all);

        var start = new double[CastlingStateTexelData.PARAMETER_COUNT];
        double k = TexelTuner.calibrateK(training, start);

        System.out.printf(Locale.ROOT, "k = %.6f%n", k);
        System.out.printf(Locale.ROOT, "with the term switched off: training %.6f   validation %.6f%n",
                TexelTuner.meanSquaredError(training, start, k),
                TexelTuner.meanSquaredError(validation, start, k));

        // The shipped values are the reference the fit has to beat, not the starting point. A fit
        // started from them would report an improvement that includes whatever they already got
        // right; started from zero, the run also answers "is the term worth having at all", since
        // the all-zero parameter vector IS the term switched off.
        double[] shipped = { CastlingStateTexelData.SHIPPED_LEVELS[1],
                             CastlingStateTexelData.SHIPPED_LEVELS[2],
                             CastlingStateTexelData.SHIPPED_LEVELS[3] };
        double kShipped = TexelTuner.calibrateK(training, shipped);

        System.out.printf(Locale.ROOT, "with the shipped 0/−25/−50/−100: training %.6f   validation %.6f%n%n",
                TexelTuner.meanSquaredError(training, shipped, kShipped),
                TexelTuner.meanSquaredError(validation, shipped, kShipped));

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

        System.out.printf(Locale.ROOT, "%nafter:  training %.6f   validation %.6f%n%n",
                TexelTuner.meanSquaredError(training, fitted, kAfter),
                TexelTuner.meanSquaredError(validation, fitted, kAfter));

        report(fitted, occupancy, config);
    }

    /** Share of king states in each level — a level rarely reached cannot be trusted. */
    private static double[] reportOccupancy(List<Sample> samples) {
        var counts = new double[CastlingStateTexelData.LEVEL_COUNT];

        for (Sample sample : samples) {
            double[] f = sample.features();
            int white = CastlingStateTexelData.STATE_CASTLED;
            int black = CastlingStateTexelData.STATE_CASTLED;

            for (int i = 0; i < f.length; i++) {
                if (f[i] > 0) {
                    white = i + 1;
                }

                if (f[i] < 0) {
                    black = i + 1;
                }
            }

            counts[white]++;
            counts[black]++;
        }

        double total = 2.0 * samples.size();

        System.out.println("occupancy of the four states, over both kings:");

        for (int i = 0; i < counts.length; i++) {
            System.out.printf(Locale.ROOT, "  %-12s %12.0f  %6.2f %%%s%n", NAMES[i], counts[i],
                    100.0 * counts[i] / total,
                    counts[i] / total < THIN_OCCUPANCY ? "   <-- thin, read with care" : "");
        }

        System.out.println();

        for (int i = 0; i < counts.length; i++) {
            counts[i] /= total;
        }

        return counts;
    }

    private static void report(double[] fitted, double[] occupancy, TexelTuner.Config config) {
        System.out.println("fitted levels, in centipawns:");
        System.out.printf(Locale.ROOT, "  %-12s %10s %10s %10s%n", "", "shipped", "fitted", "delta");

        var full = new double[CastlingStateTexelData.LEVEL_COUNT];

        System.arraycopy(fitted, 0, full, 1, fitted.length);

        for (int i = 0; i < full.length; i++) {
            System.out.printf(Locale.ROOT, "  %-12s %10.0f %10.1f %+10.1f%s%n", NAMES[i],
                    CastlingStateTexelData.SHIPPED_LEVELS[i], full[i],
                    full[i] - CastlingStateTexelData.SHIPPED_LEVELS[i],
                    i == 0 ? "   (pinned)" : occupancy[i] < THIN_OCCUPANCY ? "   thin" : "");
        }

        System.out.println();

        boolean monotone = true;

        for (int i = 1; i < full.length; i++) {
            if (full[i] > full[i - 1]) {
                monotone = false;
            }
        }

        System.out.printf(Locale.ROOT, "monotone (worse state never scores better): %s%n",
                monotone ? "yes" : "NO — check the thin level before believing the shape");

        double reachable = config.maxRoundsPerStep() * 2.0 * config.initialStep();
        boolean saturated = false;

        for (double v : fitted) {
            if (Math.abs(v) >= reachable - 1) {
                saturated = true;
            }
        }

        if (saturated) {
            System.out.printf(Locale.ROOT,
                    "WARNING: a level sits at the edge of what the step schedule can reach (%.0f).%n"
                            + "         That is the signature of a wall, not of a fitted value —%n"
                            + "         widen the schedule and re-run before reading the numbers.%n",
                    reachable);
        }
    }
}
