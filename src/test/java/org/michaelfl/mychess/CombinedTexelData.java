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
 * Combined Texel adapter: tunes the symmetric pawn piece-square table
 * (24 parameters, {@link PawnPstTexelData}) and the scalar factors
 * <em>jointly</em>, in one linear model.
 *
 * <p>Two factors are deliberately frozen and therefore not tuned:
 * <ul>
 *   <li>{@code positionFactor} — it multiplies the pawn table, so tuning both at
 *       once would be non-linear (a product of two parameters); the pawn values
 *       absorb the scaling instead.</li>
 *   <li>{@code castlingFactor} — it is largely redundant with the king
 *       piece-square table, so a free tune drives it negative on the proxy; kept
 *       fixed pending a separate match.</li>
 * </ul>
 * The remaining tunable factors are mobility, thread, chess, double-pawn and
 * undefended. Parameter layout: indices {@code [0, 24)} are the pawn table,
 * {@code [24, 29)} the five factors.
 *
 * @author Michael Fleischhauer
 */
public final class CombinedTexelData {

    /** Factor indices (into {@link WeightingFunction#TUNABLE_FACTOR_NAMES}) that stay tunable. */
    private static final int[] TUNABLE_FACTORS = {1, 2, 4, 5, 6}; // skip 0=position, 3=castling

    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String RESULT_TAG = " c9 ";

    private CombinedTexelData() {
        // static utility
    }

    /** Number of pawn-table parameters at the front of the parameter vector. */
    public static int pawnParameterCount() {
        return PawnPstTexelData.PARAM_COUNT;
    }

    /** Names of the tunable factors, in the order they follow the pawn parameters. */
    public static String[] factorNames() {
        String[] all = WeightingFunction.TUNABLE_FACTOR_NAMES;
        String[] names = new String[TUNABLE_FACTORS.length];

        for (int i = 0; i < TUNABLE_FACTORS.length; i++) {
            names[i] = all[TUNABLE_FACTORS[i]];
        }

        return names;
    }

    /** The combined start vector: current pawn-table values followed by the current tunable factor values. */
    public static double[] currentParameters() {
        double[] pawn = PawnPstTexelData.currentTableValues();
        double[] allFactors = WeightingFunction.tunableFactorValues();
        double[] params = new double[pawn.length + TUNABLE_FACTORS.length];

        System.arraycopy(pawn, 0, params, 0, pawn.length);
        for (int i = 0; i < TUNABLE_FACTORS.length; i++) {
            params[pawn.length + i] = allFactors[TUNABLE_FACTORS[i]];
        }

        return params;
    }

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

        double[] pawnFeatures = PawnPstTexelData.featuresOf(board);
        double[] factorFeatures = breakdown.features();

        double[] features = new double[pawnFeatures.length + TUNABLE_FACTORS.length];
        System.arraycopy(pawnFeatures, 0, features, 0, pawnFeatures.length);
        for (int i = 0; i < TUNABLE_FACTORS.length; i++) {
            features[pawnFeatures.length + i] = factorFeatures[TUNABLE_FACTORS[i]];
        }

        // baseEval = eval minus the current contribution of everything tunable
        // (frozen factors and material stay inside baseEval).
        double baseEval = breakdown.eval() - dot(features, currentParameters());

        return new Sample(baseEval, features, result);
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
