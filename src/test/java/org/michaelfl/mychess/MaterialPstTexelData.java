package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Texel adapter for <b>option B</b> with the <b>pawn frozen</b>: tune the
 * material values and piece-square tables of knight, bishop, rook and queen
 * <b>jointly</b>, with file symmetry enforced. Both the pawn and the king are
 * frozen at their current values and stay inside {@code baseEval}.
 *
 * <p>The pawn is frozen deliberately. Freezing only its <em>material</em> value
 * would be pointless: material value and table mean are collinear, so a free
 * pawn table would just push its mean up to restore the same effective pawn
 * value. Pinning the pawn to exactly 100 cp therefore means freezing the whole
 * pawn (material and table). The king is frozen for the usual reason — its shape
 * is game-phase dependent and cannot be tuned on a phase-mixed set.
 *
 * <p>Making the four remaining material values free is what lets their tables be
 * <b>mean-zero</b>: material value and a table's mean are collinear, so with
 * material free the level lives in the material parameter and each table
 * expresses only positional shape. {@link #foldPstMeansIntoMaterial(double[])}
 * performs that mean-zero gauge fix afterwards, which is eval-neutral.
 *
 * <p>Parameter layout ({@code PARAM_COUNT = 132}):
 * <ul>
 *   <li>indices {@code [0, 4)} — material values for knight, bishop, rook, queen
 *       (unscaled centipawns)</li>
 *   <li>indices {@code [4, 132)} — the four symmetric tables, 32 parameters each</li>
 * </ul>
 * A white piece adds {@code 0.5 * value(kind, s)} and a black piece subtracts
 * {@code 0.5 * value(kind, mirror(s))}; material adds
 * {@code value(kind) * (whiteCount - blackCount)}.
 *
 * @author Michael Fleischhauer
 */
public final class MaterialPstTexelData {

    /** WeightingFunction scales the position-weight difference by this factor. */
    static final double POSITION_FACTOR = 0.5;

    /** Number of tuned kinds: knight, bishop, rook, queen (pawn and king are frozen). */
    static final int MATERIAL_COUNT = 4;

    /** Names of the tuned kinds, indexed by slot ({@code slot = (piece & 7) - 1}). */
    static final String[] KIND_NAMES = {"knight", "bishop", "rook", "queen"};

    /** Absolute start index of each tuned kind's table block (after the 4 material params). */
    static final int[] PST_BLOCK_OFFSET = {4, 36, 68, 100};

    /** Each tuned table has all 8 ranks times 4 file pairs. */
    static final int BLOCK_SIZE = 32;

    /** Total tunable parameters: 4 material + 4*32 tables. */
    static final int PARAM_COUNT = 132;

    private static final int FIRST_TUNED_KIND = 1; // knight
    private static final int LAST_TUNED_KIND = 4;  // queen

    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String RESULT_TAG = " c9 ";

    private static final double[] CURRENT_VALUES = readCurrentParameters();

    private MaterialPstTexelData() {
        // static utility
    }

    /** The current material and table values — the tuning start point. */
    public static double[] currentParameters() {
        return CURRENT_VALUES.clone();
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

        int fullEval = evaluator.calculate(board);
        if (WeightingFunction.isIllegalWeight(fullEval)) {
            return null;
        }

        double[] features = featuresOf(board);
        double baseEval = fullEval - dot(features, CURRENT_VALUES);

        return new Sample(baseEval, features, result);
    }

    /**
     * Feature vector: material count differences and symmetric table placements
     * for knight, bishop, rook and queen. Pawn and king pieces contribute
     * nothing — their weight stays in {@code baseEval}.
     */
    static double[] featuresOf(Board board) {
        double[] features = new double[PARAM_COUNT];
        byte[] raw = board.getRawBoard();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (piece < Board.whitePawn) {
                    continue;
                }

                int kind = piece & 7;
                if (kind < FIRST_TUNED_KIND || kind > LAST_TUNED_KIND) {
                    continue; // pawn and king are frozen
                }

                int slot = kind - FIRST_TUNED_KIND;

                if (piece <= Board.whiteKing) {
                    features[slot] += 1.0;
                    features[paramOf(kind, col, row)] += POSITION_FACTOR;
                } else {
                    features[slot] -= 1.0;
                    features[paramOf(kind, col, 7 - row)] -= POSITION_FACTOR;
                }
            }
        }

        return features;
    }

    /**
     * Return a copy of {@code params} with each tuned table shifted to mean zero
     * and its removed mean folded into that kind's material value. This is the
     * mean-zero gauge fix and is <b>eval-neutral</b>: the material bump
     * {@code mean * 0.5 * (whiteCount - blackCount)} exactly offsets the table's
     * lost contribution {@code -mean * 0.5 * (whiteCount - blackCount)}.
     */
    static double[] foldPstMeansIntoMaterial(double[] params) {
        double[] out = params.clone();

        for (int slot = 0; slot < MATERIAL_COUNT; slot++) {
            int offset = PST_BLOCK_OFFSET[slot];
            double mean = mean(params, offset, BLOCK_SIZE);

            out[slot] += mean * POSITION_FACTOR;
            for (int i = offset; i < offset + BLOCK_SIZE; i++) {
                out[i] = params[i] - mean;
            }
        }

        return out;
    }

    /** Mean of {@code count} entries of {@code values} starting at {@code offset}. */
    static double mean(double[] values, int offset, int count) {
        double sum = 0.0;

        for (int i = offset; i < offset + count; i++) {
            sum += values[i];
        }

        return sum / count;
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

    /** Map a tuned kind (knight..queen) and (col, row) to its absolute table parameter index. */
    static int paramOf(int kind, int col, int row) {
        int slot = kind - FIRST_TUNED_KIND;
        int filePair = Math.min(col, 7 - col);

        return PST_BLOCK_OFFSET[slot] + row * 4 + filePair;
    }

    private static double[] readCurrentParameters() {
        double[] values = new double[PARAM_COUNT];

        for (int slot = 0; slot < MATERIAL_COUNT; slot++) {
            int kind = slot + FIRST_TUNED_KIND;
            byte whitePiece = (byte) (Board.whitePawn + kind);

            values[slot] = WeightingFunction.weightOfPiece[whitePiece];

            for (int row = 0; row < 8; row++) {
                for (int filePair = 0; filePair < 4; filePair++) {
                    int left = PieceSquareTables.getMidGameWeight(whitePiece, ChessUtil.getFieldFromColAndRow(filePair, row));
                    int right = PieceSquareTables.getMidGameWeight(whitePiece, ChessUtil.getFieldFromColAndRow(7 - filePair, row));

                    values[PST_BLOCK_OFFSET[slot] + row * 4 + filePair] = (left + right) / 2.0;
                }
            }
        }

        return values;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;

        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }
}
