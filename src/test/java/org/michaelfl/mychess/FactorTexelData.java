package org.michaelfl.mychess;

import org.michaelfl.mychess.WeightingFunction.FactorBreakdown;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the Zurichess {@code quiet-labeled} EPD dataset into Texel
 * {@link Sample}s for tuning the scalar {@link WeightingFunction} factors
 * (position, mobility, thread, castling, chess, double-pawn, undefended).
 *
 * <p>The evaluation is linear in these factors, so this adapter is trivial: for
 * each position {@link WeightingFunction#analyzeFactors} yields the eval and the
 * per-factor coefficients ({@code features}), and the factor-independent
 * material part becomes {@code baseEval}:
 * <pre>{@code eval = baseEval + features . factors}</pre>
 * Adding or removing a factor is a change in {@code WeightingFunction} alone —
 * the tuner and this adapter are untouched.
 *
 * @author Michael Fleischhauer
 */
public final class FactorTexelData {

    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String RESULT_TAG = " c9 ";

    private FactorTexelData() {
        // static utility
    }

    /** Names of the tunable factors, in parameter order. */
    public static String[] factorNames() {
        return WeightingFunction.TUNABLE_FACTOR_NAMES.clone();
    }

    /** Current factor values (the tuning start point), in parameter order. */
    public static double[] currentFactorValues() {
        return WeightingFunction.tunableFactorValues();
    }

    /**
     * Load up to {@code limit} samples from a Zurichess {@code quiet-labeled} EPD
     * file. Malformed or illegal positions are skipped.
     */
    public static List<Sample> load(Path epd, int limit) {
        var samples = new ArrayList<Sample>();
        var evaluator = new WeightingFunction();

        try (var lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && samples.size() < limit) {
                Sample sample = toSample(iterator.next(), evaluator);

                if (sample != null) {
                    samples.add(sample);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + epd, e);
        }

        return samples;
    }

    /** Parse one EPD line into a {@link Sample}, or {@code null} if it is malformed or illegal. */
    static Sample toSample(String epdLine, WeightingFunction evaluator) {
        int tagIndex = epdLine.indexOf(RESULT_TAG);
        if (tagIndex < 0) {
            return null;
        }

        double result = parseResult(epdLine.substring(tagIndex));
        if (Double.isNaN(result)) {
            return null;
        }

        Board board;
        try {
            board = Fen.importFEN(epdLine.substring(0, tagIndex).trim() + EPD_COUNTER_SUFFIX);
        } catch (IllegalArgumentException e) {
            return null;
        }

        FactorBreakdown breakdown = evaluator.analyzeFactors(board);
        if (WeightingFunction.isIllegalWeight(breakdown.eval())) {
            return null;
        }

        // baseEval is the eval with the factor-dependent part removed — i.e. the
        // factor-independent material term.
        double baseEval = breakdown.eval() - dot(breakdown.features(), WeightingFunction.tunableFactorValues());

        return new Sample(baseEval, breakdown.features(), result);
    }

    private static double parseResult(String tag) {
        int open = tag.indexOf('"');
        int close = tag.indexOf('"', open + 1);
        if (open < 0 || close < 0) {
            return Double.NaN;
        }

        return switch (tag.substring(open + 1, close)) {
            case "1-0" -> 1.0;
            case "1/2-1/2" -> 0.5;
            case "0-1" -> 0.0;
            default -> Double.NaN;
        };
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
