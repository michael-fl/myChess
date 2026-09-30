package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Fits one scalar {@link WeightingFunction} factor against game results while the other
 * tunable factors stay at their shipped values.
 *
 * <p>{@link TexelFactorTuner} moves all factors jointly, which answers a different question:
 * a joint tune re-balances every factor at once, so a changed value for one of them cannot be
 * matched on its own. This driver folds the fixed factors into each sample's base evaluation,
 * leaving a one-dimensional problem. It then does two things with it:
 * <ol>
 *   <li>a <b>scan</b> over a grid of multiples of the current value, printing training and
 *       validation error, so the shape of the curve is visible, not just its minimum;</li>
 *   <li>a <b>golden-section search</b> on the training error inside the bracket the scan
 *       found. That search has no step schedule and so no reachable-magnitude ceiling, which
 *       {@link TexelTuner.Config#defaults()} warns about for coordinate descent.</li>
 * </ol>
 * {@code K} is calibrated once at the current value and held fixed for the scan and the
 * search, as in the other tuners; the result is re-checked with {@code K} re-calibrated at
 * the optimum.
 *
 * <p>With a replicate count, a <b>block bootstrap</b> follows: the training set is resampled
 * in contiguous blocks, so the positions of one game stay together. Positions from one game
 * are correlated, and resampling them one by one reports intervals far too narrow; the
 * block should be longer than the corpus's positions per game (see
 * {@code tuning-data/README.md}).
 *
 * <p>Usage, a measurement driver rather than a test:
 * <pre>{@code
 * java -cp target/classes:target/test-classes:target/dependency/* \
 *     org.michaelfl.mychess.TexelSingleFactorScan tuning-data/hybrid.epd kingAttackFactor [limit [replicates [block]]]
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class TexelSingleFactorScan {

    /** Every fifth sample validates: a deterministic split, so a re-run is comparable. */
    private static final int VALIDATION_EVERY = 5;

    /** Scan points, as multiples of the factor's current value. */
    private static final double[] SCAN_MULTIPLES = {0, 0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3, 4, 6};

    private static final double GOLDEN = (Math.sqrt(5) - 1) / 2;

    private static final int GOLDEN_ITERATIONS = 60;

    private static final int DEFAULT_BLOCK = 50;

    private static final long BOOTSTRAP_SEED = 20260930L;

    private TexelSingleFactorScan() {
        // measurement driver
    }

    /**
     * Entry point.
     *
     * @param args EPD file, factor name, optional sample limit ({@code 0} = all), bootstrap
     *             replicates and block length
     */
    public static void main(String[] args) {
        Path epd = Path.of(args[0]);
        String name = args[1];
        int limit = args.length > 2 && Integer.parseInt(args[2]) > 0 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;
        int replicates = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int block = args.length > 4 ? Integer.parseInt(args[4]) : DEFAULT_BLOCK;

        int index = Arrays.asList(FactorTexelData.factorNames()).indexOf(name);

        if (index < 0) {
            throw new IllegalArgumentException("unknown factor " + name + ", expected one of "
                    + String.join(", ", FactorTexelData.factorNames()));
        }

        double[] factors = FactorTexelData.currentFactorValues();
        double current = factors[index];

        System.out.printf(Locale.ROOT, "loading %s, fitting %s (current %.6f)%n", epd, name, current);
        List<Sample> all = project(FactorTexelData.load(epd, limit), index, factors);
        var training = new ArrayList<Sample>(all.size());
        var validation = new ArrayList<Sample>(all.size() / VALIDATION_EVERY + 1);

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        long active = all.stream().filter(s -> s.features()[0] != 0).count();
        System.out.printf(Locale.ROOT, "%,d samples (%,d training, %,d validation), %.1f %% with a non-zero %s feature%n",
                all.size(), training.size(), validation.size(), 100.0 * active / all.size(), name);

        double k = TexelTuner.calibrateK(training, new double[] {current});
        System.out.printf(Locale.ROOT, "K = %.6f, calibrated at the current value%n%n", k);
        System.out.printf(Locale.ROOT, "%10s %10s %12s %12s%n", "multiple", name, "trainMSE", "valMSE");

        int best = 0;
        double bestError = Double.MAX_VALUE;

        for (int i = 0; i < SCAN_MULTIPLES.length; i++) {
            double value = SCAN_MULTIPLES[i] * current;
            double trainError = TexelTuner.meanSquaredError(training, new double[] {value}, k);

            System.out.printf(Locale.ROOT, "%10.2f %10.6f %12.8f %12.8f%n", SCAN_MULTIPLES[i], value, trainError,
                    TexelTuner.meanSquaredError(validation, new double[] {value}, k));

            if (trainError < bestError) {
                bestError = trainError;
                best = i;
            }
        }

        if (best == SCAN_MULTIPLES.length - 1) {
            System.out.println("\nWARNING: the minimum is at the edge of the scan - the optimum may lie beyond it");
        }

        double lower = best == 0 ? 0 : SCAN_MULTIPLES[best - 1] * current;
        double upper = SCAN_MULTIPLES[Math.min(best + 1, SCAN_MULTIPLES.length - 1)] * current;
        double tuned = minimize(training, k, lower, upper);
        double kTuned = TexelTuner.calibrateK(training, new double[] {tuned});

        System.out.printf(Locale.ROOT, "%ngolden section in [%.6f, %.6f]: %s = %.6f (%.2f x current)%n",
                lower, upper, name, tuned, tuned / current);
        System.out.printf(Locale.ROOT, "  current: trainMSE %.8f  valMSE %.8f%n",
                TexelTuner.meanSquaredError(training, new double[] {current}, k),
                TexelTuner.meanSquaredError(validation, new double[] {current}, k));
        System.out.printf(Locale.ROOT, "  tuned:   trainMSE %.8f  valMSE %.8f%n",
                TexelTuner.meanSquaredError(training, new double[] {tuned}, k),
                TexelTuner.meanSquaredError(validation, new double[] {tuned}, k));
        System.out.printf(Locale.ROOT, "  re-calibrated K at the optimum: %.6f, then %s = %.6f%n",
                kTuned, name, minimize(training, kTuned, lower, upper));

        if (replicates > 0) {
            double wideUpper = SCAN_MULTIPLES[SCAN_MULTIPLES.length - 1] * current;
            double[] optima = bootstrap(training, k, wideUpper, replicates, block, new Random(BOOTSTRAP_SEED));

            System.out.printf(Locale.ROOT, "%nblock bootstrap, %d replicates, block %d, search range [0, %.6f]:%n",
                    replicates, block, wideUpper);
            System.out.printf(Locale.ROOT, "  2.5 %%: %.6f (%.2f x)   median: %.6f (%.2f x)   97.5 %%: %.6f (%.2f x)%n",
                    percentile(optima, 0.025), percentile(optima, 0.025) / current,
                    percentile(optima, 0.5), percentile(optima, 0.5) / current,
                    percentile(optima, 0.975), percentile(optima, 0.975) / current);
        }
    }

    /**
     * Refits the parameter on block-resampled copies of the data.
     *
     * @param data       one-feature samples, in corpus order
     * @param k          the sigmoid scaling constant, held fixed
     * @param upper      the upper end of the search range; the lower end is zero
     * @param replicates how many resampled copies to fit
     * @param block      the length of a resampled block, in samples
     * @param random     the source of block starts
     * @return the fitted parameter of each replicate, sorted ascending
     */
    static double[] bootstrap(List<Sample> data, double k, double upper, int replicates, int block, Random random) {
        double[] optima = new double[replicates];
        int blocks = (data.size() + block - 1) / block;

        for (int r = 0; r < replicates; r++) {
            var resampled = new ArrayList<Sample>(blocks * block);

            for (int b = 0; b < blocks; b++) {
                int start = random.nextInt(data.size());

                for (int i = 0; i < block; i++) {
                    resampled.add(data.get((start + i) % data.size()));
                }
            }

            optima[r] = minimize(resampled, k, 0, upper);
        }

        Arrays.sort(optima);

        return optima;
    }

    /**
     * Nearest-rank percentile of sorted values.
     *
     * @param sorted   values in ascending order
     * @param fraction the percentile as a fraction in {@code [0, 1]}
     * @return the value at that rank
     */
    static double percentile(double[] sorted, double fraction) {
        int rank = (int) Math.ceil(fraction * sorted.length) - 1;

        return sorted[Math.clamp(rank, 0, sorted.length - 1)];
    }

    /**
     * Reduces samples over all tunable factors to samples over one of them: the other factors'
     * contributions, at the given values, move into the base evaluation.
     *
     * @param samples the samples over all factors
     * @param index   the index of the factor that stays free
     * @param factors the values the other factors are held at
     * @return one-feature samples, in the same order
     */
    static List<Sample> project(List<Sample> samples, int index, double[] factors) {
        var projected = new ArrayList<Sample>(samples.size());

        for (Sample sample : samples) {
            double base = sample.baseEval();
            double[] features = sample.features();

            for (int j = 0; j < features.length; j++) {
                if (j != index) {
                    base += features[j] * factors[j];
                }
            }

            projected.add(new Sample(base, new double[] {features[index]}, sample.result()));
        }

        return projected;
    }

    /**
     * Golden-section search for the single parameter that minimizes the mean squared error.
     * Assumes the error is unimodal on {@code [lower, upper]}.
     *
     * @param data  one-feature samples
     * @param k     the sigmoid scaling constant, held fixed
     * @param lower the lower end of the bracket
     * @param upper the upper end of the bracket
     * @return the minimizing parameter value
     */
    static double minimize(List<Sample> data, double k, double lower, double upper) {
        double a = lower;
        double b = upper;
        double c = b - GOLDEN * (b - a);
        double d = a + GOLDEN * (b - a);
        double errorC = TexelTuner.meanSquaredError(data, new double[] {c}, k);
        double errorD = TexelTuner.meanSquaredError(data, new double[] {d}, k);

        for (int i = 0; i < GOLDEN_ITERATIONS; i++) {
            if (errorC < errorD) {
                b = d;
                d = c;
                errorD = errorC;
                c = b - GOLDEN * (b - a);
                errorC = TexelTuner.meanSquaredError(data, new double[] {c}, k);
            } else {
                a = c;
                c = d;
                errorC = errorD;
                d = a + GOLDEN * (b - a);
                errorD = TexelTuner.meanSquaredError(data, new double[] {d}, k);
            }
        }

        return (a + b) / 2;
    }
}
