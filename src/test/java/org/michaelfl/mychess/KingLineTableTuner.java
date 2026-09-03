package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Config;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Re-fits {@link WeightingFunction#KING_LINE_PENALTY} against game results.
 *
 * <p>The shipped table came from agreement with Stockfish's static evaluation and the term lost
 * 28.9 Elo. A sweep of its scale factor then showed the shipped strength already near the
 * game-result optimum, so the values are not simply too loud as a group. This asks the remaining
 * question: does the table's <em>shape</em> change when the objective is results rather than
 * agreement?
 *
 * <h2>Three things this run does that the default tuner would get wrong</h2>
 *
 * <p><b>A wide step schedule.</b> {@link Config#defaults()} can move a parameter by at most about
 * 90 from its start, and its own JavaDoc records a fit where the top entry stopped near that wall
 * in all four corpora while a bootstrap reported an interval width of 1.0 — precision that was its
 * opposite. This table already contains 223, so {@code defaults()} could not reach a correct answer
 * from zero. The schedule here is {@code (32.0, 0.5, 24)}.
 *
 * <p><b>Two starting points.</b> From the shipped table, and from all zeros. Starting where the
 * old fit ended biases toward it; starting from zero does not, but has further to travel. If the
 * two land in the same place, the result is the data's and not the initialisation's — and if they
 * do not, that is the finding.
 *
 * <p><b>Monotonicity is checked, not imposed.</b> The original fit enforced "more danger is not
 * less bad" as a constraint. Here it is left free and reported, because a re-fit that comes back
 * non-monotone would say the ordering itself is not supported by results — which the constrained
 * fit could never have told us.
 *
 * <p>Whatever comes out is a proxy objective on a labeled corpus, not Elo. It selects what to
 * measure; the match measures it.
 *
 * <pre>
 * java -Xmx8g -cp target/classes:target/test-classes:target/dependency/* \
 *      org.michaelfl.mychess.KingLineTableTuner tuning-data/hybrid.epd
 * </pre>
 *
 * @author Michael Fleischhauer
 */
public final class KingLineTableTuner {

    private static final int VALIDATION_EVERY = 10;
    private static final int LINEARITY_SAMPLE = 5_000;

    /** Wide enough to reach the shipped table's 223 from zero; see the class comment. */
    private static final Config SCHEDULE = new Config(32.0, 0.5, 24);

    private KingLineTableTuner() {
        // measurement driver
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : "tuning-data/hybrid.epd");
        int maxSamples = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.printf(Locale.ROOT, "checking the linear model on %,d positions ...%n",
                LINEARITY_SAMPLE);
        double[] residual = KingLineTableTexelData.verifyLinearity(epd, LINEARITY_SAMPLE);
        System.out.printf(Locale.ROOT,
                "  worst |eval - model| = %.3f cp, mean %.4f cp, over %.0f positions%n",
                residual[0], residual[1], residual[2]);
        System.out.println("  (blend rounds to an int, so a residual below ~1 cp is the expected"
                + " floor; anything larger invalidates the method rather than the numbers)\n");

        System.out.println("""
                === which scaling, and what table under it ===

                Each scaling is tuned from ZERO. Holding the table at the shipped values would rig
                the comparison: those values were fitted under PHASE and are calibrated for it, so
                PHASE would win whatever the data said. From zero, no scaling starts with an
                advantage.
                """);

        double[] shipped = KingLineTableTexelData.currentParameters();
        var results = new java.util.LinkedHashMap<KingLineTableTexelData.Scaling, double[]>();
        var scores = new java.util.LinkedHashMap<KingLineTableTexelData.Scaling, double[]>();

        for (var scaling : KingLineTableTexelData.Scaling.values()) {
            List<Sample> all = KingLineTableTexelData.load(epd, maxSamples, scaling);
            var training = new ArrayList<Sample>();
            var validation = new ArrayList<Sample>();

            for (int i = 0; i < all.size(); i++) {
                (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
            }

            double[] zero = new double[KingLineTableTexelData.PARAMETERS];
            double kZero = TexelTuner.calibrateK(training, zero);
            double termless = TexelTuner.meanSquaredError(validation, zero, kZero);

            System.out.printf(Locale.ROOT, "=== %s ===  (train=%,d  validation=%,d)%n",
                    scaling, training.size(), validation.size());
            System.out.printf(Locale.ROOT, "  no term at all:        validation MSE %.8f%n",
                    termless);

            double kShipped = TexelTuner.calibrateK(training, shipped);
            System.out.printf(Locale.ROOT, "  shipped table:         validation MSE %.8f%n",
                    TexelTuner.meanSquaredError(validation, shipped, kShipped));

            double[] tuned = TexelTuner.tune(training, zero, SCHEDULE, (step, error, ownK) -> { });
            double kTuned = TexelTuner.calibrateK(training, tuned);
            double tunedMse = TexelTuner.meanSquaredError(validation, tuned, kTuned);
            System.out.printf(Locale.ROOT, "  tuned from zero:       validation MSE %.8f%n",
                    tunedMse);
            System.out.printf(Locale.ROOT, "  table: %s%n  monotone: %s%n%n",
                    format(tuned), monotone(tuned) ? "yes" : "NO");

            results.put(scaling, tuned);
            scores.put(scaling, new double[] {termless, tunedMse});
        }

        System.out.printf(Locale.ROOT, "%-22s%18s%18s%14s%n",
                "scaling", "no term", "tuned", "gain");
        System.out.println("-".repeat(72));
        KingLineTableTexelData.Scaling best = null;
        double bestGain = -1;

        for (var entry : scores.entrySet()) {
            double[] pair = entry.getValue();
            double gain = pair[0] - pair[1];
            System.out.printf(Locale.ROOT, "%-22s%18.8f%18.8f%14.8f%n",
                    entry.getKey(), pair[0], pair[1], gain);

            if (gain > bestGain) {
                bestGain = gain;
                best = entry.getKey();
            }
        }

        System.out.printf(Locale.ROOT, "%nlargest gain over having no term at all: %s%n", best);
        System.out.println("""

                'gain' is what the term buys under that scaling, against the same corpus with the
                term switched off entirely. That is the comparison that matters: a scaling whose
                tuned MSE is lower only because its termless baseline is lower has bought nothing.

                A proxy objective on a labeled corpus, not Elo. It selects what to measure.""");

        System.out.printf(Locale.ROOT, "%n%6s%12s%14s%14s%14s%n", "index", "shipped",
                "PHASE", "OPP_HEAVY", "OPP_NON_PAWN");
        System.out.println("-".repeat(60));

        for (int i = 0; i < KingLineTableTexelData.PARAMETERS; i++) {
            System.out.printf(Locale.ROOT, "%6d%12.0f%14.1f%14.1f%14.1f%n", i + 1, shipped[i],
                    results.get(KingLineTableTexelData.Scaling.PHASE)[i],
                    results.get(KingLineTableTexelData.Scaling.OPPONENT_HEAVY)[i],
                    results.get(KingLineTableTexelData.Scaling.OPPONENT_NON_PAWN)[i]);
        }
    }

    private static String format(double[] values) {
        var out = new StringBuilder("0");

        for (double value : values) {
            out.append(' ').append(Math.round(value));
        }

        return out.toString();
    }

    private static double[] run(String label, List<Sample> training, List<Sample> validation,
                                double[] start, double k) {
        System.out.printf(Locale.ROOT, "=== tuning %s ===%n", label);
        double[] tuned = TexelTuner.tune(training, start, SCHEDULE,
                (step, error, ownK) -> System.out.printf(Locale.ROOT,
                        "  step %6.2f  train MSE %.8f  k %.5f%n", step, error, ownK));
        System.out.printf(Locale.ROOT, "  validation MSE after: %.8f%n%n",
                TexelTuner.meanSquaredError(validation, tuned, k));

        return tuned;
    }

    private static void report(double[] shipped, double[] fromShipped, double[] fromZero) {
        System.out.printf(Locale.ROOT, "%6s%12s%14s%12s%n",
                "index", "shipped", "from shipped", "from zero");
        System.out.println("-".repeat(44));

        for (int i = 0; i < KingLineTableTexelData.PARAMETERS; i++) {
            System.out.printf(Locale.ROOT, "%6d%12.0f%14.1f%12.1f%n",
                    i + 1, shipped[i], fromShipped[i], fromZero[i]);
        }

        System.out.printf(Locale.ROOT, "%nmonotone (non-decreasing)?  from shipped: %s   from zero: %s%n",
                monotone(fromShipped) ? "yes" : "NO", monotone(fromZero) ? "yes" : "NO");
        System.out.printf(Locale.ROOT, "agreement between the two starts: max |diff| = %.1f cp%n",
                maxDiff(fromShipped, fromZero));
        System.out.println("""

                If the two starts disagree widely, the coordinate descent is finding local minima
                and neither column is the answer.
                If a fit comes back non-monotone, the ordering "more danger is worse" is not
                supported by results at those indices — the original fit imposed it as a
                constraint and could not have shown that.
                Either way: a proxy objective, not Elo. Whatever ships needs a match.""");
    }

    private static boolean monotone(double[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[i - 1] - 1e-9) {
                return false;
            }
        }

        return true;
    }

    private static double maxDiff(double[] a, double[] b) {
        double worst = 0;

        for (int i = 0; i < a.length; i++) {
            worst = Math.max(worst, Math.abs(a[i] - b[i]));
        }

        return worst;
    }
}
