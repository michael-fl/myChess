package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How strong should the king-line danger term be, judged against game results rather than against
 * another engine's static evaluation?
 *
 * <h2>Why a one-dimensional sweep and not {@link TexelFactorTuner}</h2>
 *
 * <p>That tuner moves all nine factors at once, which retunes the whole evaluation — a bigger and
 * different experiment than asking what this one term is worth. Holding the other eight fixed
 * isolates the question.
 *
 * <h2>Why it costs almost nothing</h2>
 *
 * <p>`WeightingFunction.analyzeFactors` guarantees the evaluation is <em>linear</em> in the
 * factors: {@code eval = baseEval + sum(features[i] * factor[i])}. So varying one factor is
 * arithmetic over an already-loaded sample set — no position needs re-evaluating. The whole sweep
 * is one pass to load and then a scan.
 *
 * <h2>What the answer means</h2>
 *
 * <p>The fitted table came from agreement with Stockfish's <em>static</em> evaluation, which
 * optimises for matching an evaluator rather than for winning games. This sweep asks the other
 * question. The term as shipped (`-0.01`, the value at which the table applies 1:1) measured
 * −28.9 Elo, and `KingShelterAnalysis` showed it pulling 43 % off mean own-king danger to buy 19 %
 * fewer shelter-opening moves — far more force than behavior change. If the overshoot hypothesis
 * is right, the optimum here sits well below 0.01 in magnitude.
 *
 * <p><b>A minimum near zero is an answer too</b>, and the cheapest possible one: it would say the
 * term does not help predict results at any strength, and no SPRT is needed to shelve it for good.
 *
 * <h2>The two columns, and why both</h2>
 *
 * <p>`k` scales the sigmoid that maps centipawns to an expected score. Held fixed, the sweep asks
 * which factor predicts results best under one mapping — the honest comparison, because a
 * re-fitted `k` can absorb a weaker factor by steepening the curve and hide the effect. Refitted
 * per point is the purist's version, where every candidate gets its own best mapping. If the two
 * disagree about where the minimum lies, neither should be trusted.
 *
 * <p>A measuring instrument, not a test. The tuning objective is a proxy: whatever this says still
 * needs an SPRT.
 *
 * <pre>
 * java -cp target/classes:target/test-classes:target/dependency/* \
 *      org.michaelfl.mychess.KingLineFactorSweep tuning-data/hybrid.epd 600000
 * </pre>
 *
 * @author Michael Fleischhauer
 */
public final class KingLineFactorSweep {

    private static final String FACTOR = "kingLinePenaltyFactor";
    private static final int VALIDATION_EVERY = 10;

    /** Magnitudes to try, in the factor's own units; the shipped value is 0.01. */
    private static final double[] MAGNITUDES = {
            0.000, 0.001, 0.002, 0.003, 0.004, 0.005, 0.006, 0.008, 0.010, 0.012, 0.015, 0.020
    };

    private KingLineFactorSweep() {
        // measurement driver
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : "tuning-data/hybrid.epd");
        int maxSamples = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;
        int index = indexOfFactor();

        if (index < 0) {
            System.err.printf("%s is not in TUNABLE_FACTOR_NAMES — nothing to sweep%n", FACTOR);
            return;
        }

        System.out.printf("Loading up to %,d positions from %s ...%n", maxSamples, epd);
        List<Sample> all = FactorTexelData.load(epd, maxSamples);
        var training = new ArrayList<Sample>();
        var validation = new ArrayList<Sample>();

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        final double[] shipped = WeightingFunction.tunableFactorValues();
        final double kFixed = TexelTuner.calibrateK(training, shipped);
        System.out.printf("train=%,d  validation=%,d  k(shipped)=%.5f%n", training.size(),
                validation.size(), kFixed);
        System.out.printf("%s is factor %d of %d; shipped value %.4f%n%n",
                FACTOR, index + 1, shipped.length, shipped[index]);

        System.out.printf("%10s%16s%16s%16s%n", "factor", "MSE (fixed k)", "MSE (own k)", "own k");
        System.out.println("-".repeat(58));
        double bestFixed = Double.MAX_VALUE;
        double bestOwn = Double.MAX_VALUE;
        double argBestFixed = 0;
        double argBestOwn = 0;

        for (double magnitude : MAGNITUDES) {
            double[] params = WeightingFunction.tunableFactorValues();
            params[index] = -magnitude;   // the factor is negative: more danger is worse
            double mseFixed = TexelTuner.meanSquaredError(validation, params, kFixed);
            double kOwn = TexelTuner.calibrateK(training, params);
            double mseOwn = TexelTuner.meanSquaredError(validation, params, kOwn);

            if (mseFixed < bestFixed) {
                bestFixed = mseFixed;
                argBestFixed = magnitude;
            }

            if (mseOwn < bestOwn) {
                bestOwn = mseOwn;
                argBestOwn = magnitude;
            }

            System.out.printf("%10.4f%16.8f%16.8f%16.5f%s%n", -magnitude, mseFixed, mseOwn, kOwn,
                    Math.abs(magnitude - Math.abs(shipped[index])) < 1e-9 ? "   <- shipped" : "");
        }

        System.out.printf("%nminimum at factor %.4f (fixed k) and %.4f (own k)%n",
                -argBestFixed, -argBestOwn);
        System.out.println("""

                A minimum at 0.0000 means the term does not help predict game results at any
                strength — the cheapest possible verdict, and no SPRT would be needed.
                A minimum well below the shipped 0.0100 supports the overshoot hypothesis: the
                right idea applied too hard.
                A minimum at or above 0.0100 kills the overshoot hypothesis and leaves capping the
                table's loud top end as the only remaining variant.

                Either way this is a proxy objective on a labeled corpus, not Elo. It selects what
                to measure next; it does not measure it.""");
    }

    private static int indexOfFactor() {
        String[] names = WeightingFunction.TUNABLE_FACTOR_NAMES;

        for (int i = 0; i < names.length; i++) {
            if (FACTOR.equals(names[i])) {
                return i;
            }
        }

        return -1;
    }
}
