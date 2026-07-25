package org.michaelfl.mychess.tuning;

import java.util.List;

/**
 * Texel-style evaluation tuner (offline, no external tool).
 *
 * <p>The idea: a good evaluation should predict game outcomes. For a set of
 * labeled positions — each with a game result from White's point of view
 * ({@code 1.0} White won, {@code 0.5} draw, {@code 0.0} White lost) — we adjust
 * the tunable parameters so the evaluation, mapped to an expected score via a
 * sigmoid, matches those results as closely as possible.
 *
 * <p>The evaluation is written in a <b>linear</b> form so that changing a
 * parameter does not require re-running the whole engine evaluation:
 * <pre>{@code
 *   eval(position) = baseEval + sum_j ( features[j] * params[j] )
 * }</pre>
 * where {@code baseEval} is the (parameter-independent) part of
 * {@link org.michaelfl.mychess.WeightingFunction} eval in centipawns, and
 * {@code features[j]} captures how the position depends on parameter {@code j}
 * (e.g. for a pawn-square value: {@code +1} per White pawn on that square minus
 * {@code +1} per Black pawn on its mirror square). Both {@code baseEval} and the
 * features are precomputed once per position, so tuning is pure, fast linear
 * algebra.
 *
 * <p>The objective minimized is the mean squared error
 * <pre>{@code
 *   E = mean( ( result - sigmoid(K * eval) )^2 )
 * }</pre>
 * {@code K} is a single scaling constant calibrated once so the sigmoid's
 * steepness matches the data (centipawns -> win probability). Parameters are
 * then optimized by coordinate descent: each parameter is nudged up/down and
 * the change is kept only if {@code E} over the whole set drops.
 *
 * @author Michael Fleischhauer
 */
public final class TexelTuner {

    /**
     * One labeled training position in linear form.
     *
     * @param baseEval the parameter-independent evaluation in centipawns (White POV)
     * @param features how the position depends on each tunable parameter (White POV),
     *                 same length and order as the parameter vector
     * @param result   the game result from White's POV: 1.0, 0.5 or 0.0
     */
    public record Sample(double baseEval, double[] features, double result) {}

    /** Tuning knobs with sensible defaults for integer-valued tables. */
    public record Config(double initialStep, double minStep, int maxRoundsPerStep) {

        public Config {
            if (initialStep <= 0 || minStep <= 0 || initialStep < minStep) {
                throw new IllegalArgumentException("require initialStep >= minStep > 0");
            }
            if (maxRoundsPerStep < 1) {
                throw new IllegalArgumentException("maxRoundsPerStep must be >= 1");
            }
        }

        /** Anneal from a step of 4 down to 0.5, up to 12 passes per step. */
        public static Config defaults() {
            return new Config(4.0, 0.5, 12);
        }
    }

    private static final double K_SEARCH_LOWER = 1e-6;
    private static final double K_SEARCH_UPPER = 1.0;
    private static final int K_SEARCH_ITERATIONS = 80;
    private static final double IMPROVEMENT_EPSILON = 1e-12;

    private TexelTuner() {
        // static utility
    }

    /** Logistic function mapping any real number to (0, 1); {@code sigmoid(0) = 0.5}. */
    public static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /** Evaluation of a sample for the given parameter vector: {@code baseEval + features·params}. */
    public static double evaluate(Sample sample, double[] params) {
        double eval = sample.baseEval();
        double[] features = sample.features();

        for (int j = 0; j < features.length; j++) {
            eval += features[j] * params[j];
        }

        return eval;
    }

    /** Mean squared error between predicted score {@code sigmoid(k·eval)} and the actual result. */
    public static double meanSquaredError(List<Sample> data, double[] params, double k) {
        double sum = 0.0;

        for (Sample sample : data) {
            double predicted = sigmoid(k * evaluate(sample, params));
            double diff = sample.result() - predicted;
            sum += diff * diff;
        }

        return sum / data.size();
    }

    /**
     * Calibrate the scaling constant {@code K} that minimizes the error for the
     * given (fixed) parameters, by ternary search over a unimodal range.
     *
     * @param data   the training positions
     * @param params the current parameter vector (held fixed)
     * @return the best scaling constant found
     */
    public static double calibrateK(List<Sample> data, double[] params) {
        double lo = K_SEARCH_LOWER;
        double hi = K_SEARCH_UPPER;

        for (int i = 0; i < K_SEARCH_ITERATIONS; i++) {
            double m1 = lo + (hi - lo) / 3.0;
            double m2 = hi - (hi - lo) / 3.0;

            if (meanSquaredError(data, params, m1) < meanSquaredError(data, params, m2)) {
                hi = m2;
            } else {
                lo = m1;
            }
        }

        return (lo + hi) / 2.0;
    }

    /**
     * Tune the parameters by coordinate descent, annealing the step size.
     *
     * @param data          the training positions
     * @param initialParams the starting parameter vector (copied, not mutated)
     * @param config        step-size and iteration limits
     * @param onRound       called after each pass with the current error (may be {@code null})
     * @return a fresh, tuned parameter vector
     */
    public static double[] tune(List<Sample> data, double[] initialParams, Config config, RoundListener onRound) {
        double[] params = initialParams.clone();

        for (double step = config.initialStep(); step >= config.minStep(); step /= 2.0) {
            // Re-anchor the scaling constant once per step size, then hold it
            // fixed while the coordinate descent runs (standard Texel practice —
            // recalibrating every pass is far costlier for little benefit).
            double k = calibrateK(data, params);
            double bestError = meanSquaredError(data, params, k);

            for (int round = 0; round < config.maxRoundsPerStep(); round++) {
                boolean improved = false;

                for (int j = 0; j < params.length; j++) {
                    double before = params[j];

                    params[j] = before + step;
                    double up = meanSquaredError(data, params, k);

                    params[j] = before - step;
                    double down = meanSquaredError(data, params, k);

                    if (up < bestError - IMPROVEMENT_EPSILON && up <= down) {
                        params[j] = before + step;
                        bestError = up;
                        improved = true;
                    } else if (down < bestError - IMPROVEMENT_EPSILON) {
                        params[j] = before - step;
                        bestError = down;
                        improved = true;
                    } else {
                        params[j] = before;
                    }
                }

                if (onRound != null) {
                    onRound.onRound(step, bestError, k);
                }

                if (!improved) {
                    break;
                }
            }
        }

        return params;
    }

    /** Progress callback fired once per coordinate-descent pass. */
    @FunctionalInterface
    public interface RoundListener {
        void onRound(double step, double error, double k);
    }
}
