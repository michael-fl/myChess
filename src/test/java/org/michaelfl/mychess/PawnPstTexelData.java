package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns the Zurichess {@code quiet-labeled} EPD dataset into Texel
 * {@link Sample}s for tuning the (single) white pawn piece-square table, with
 * left/right <b>file symmetry enforced</b>.
 *
 * <p>Pawn value has no inherent a-file vs h-file asymmetry, so the 48 pawn
 * squares (ranks 2-7) are collapsed to <b>24 parameters</b>: the file pairs
 * {@code a/h, b/g, c/f, d/e} share one value per rank. This halves the free
 * parameters and removes the left/right noise a free tune picks up.
 *
 * <p>The pawn table's contribution to the evaluation is linear in its values.
 * In {@link WeightingFunction} the position-weight sum is scaled by
 * {@code positionFactor = 0.5} and the result is centipawns, so a white pawn on
 * square {@code s} adds {@code 0.5 * value(s)} to the White-POV eval, and a
 * black pawn subtracts {@code 0.5 * value(mirror(s))} (Black's table is White's
 * mirrored vertically). Each position's dependence on the 24 symmetric
 * parameters is captured by a feature vector, and the parameter-independent rest
 * of the eval is stored as {@code baseEval}:
 * <pre>{@code eval = baseEval + features . parameters}</pre>
 *
 * <p>The dataset is git-ignored; see {@code tuning-data/README.md} for the fetch
 * command.
 *
 * @author Michael Fleischhauer
 */
public final class PawnPstTexelData {

    /** WeightingFunction scales the position-weight difference by this factor. */
    private static final double POSITION_FACTOR = 0.5;

    /** Six tunable ranks (2-7) times four file pairs (a/h, b/g, c/f, d/e). */
    static final int PARAM_COUNT = 24;

    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String RESULT_TAG = " c9 ";

    /** field -> symmetric parameter index, or -1 for non-pawn squares (ranks 1 and 8). */
    private static final int[] FIELD_TO_PARAM = buildFieldToParam();

    /** field -> vertically mirrored field (rank 1 &harr; 8), for the White/Black color symmetry. */
    private static final int[] MIRROR = buildMirror();

    private static final double[] CURRENT_VALUES = readCurrentParameters();

    private PawnPstTexelData() {
        // static utility
    }

    /** The current pawn-table values collapsed to the 24 symmetric parameters — the tuning start point. */
    public static double[] currentTableValues() {
        return CURRENT_VALUES.clone();
    }

    /**
     * Render tuned parameters as a white pawn piece-square table in the same 8x8
     * comma grid {@code PieceSquareTables} uses (rank 8 first, rank 1 last),
     * ready to paste. The table is left/right symmetric by construction; ranks 1
     * and 8 keep their current values, ranks 2-7 use the tuned values rounded to
     * whole centipawns.
     *
     * @param parameters the 24 tuned symmetric parameters
     * @return the formatted table string
     */
    public static String formatPawnTable(double[] parameters) {
        int[] grid = new int[64]; // grid[row * 8 + col], row 0 = rank 1

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                int param = FIELD_TO_PARAM[field];
                grid[row * 8 + col] = param < 0
                        ? PieceSquareTables.getPieceSquareWeight(Board.whitePawn, field)
                        : (int) Math.round(parameters[param]);
            }
        }

        var table = new StringBuilder();

        for (int row = 7; row >= 0; row--) {
            for (int col = 0; col < 8; col++) {
                table.append("%4d".formatted(grid[row * 8 + col]));

                if (!(row == 0 && col == 7)) {
                    table.append(',');
                }
            }
            table.append('\n');
        }

        return table.toString();
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

        // baseEval is the eval with the (parameter-dependent) pawn-table part
        // removed, computed from the real table so it is exact.
        double baseEval = fullEval - pawnContributionActual(board);

        return new Sample(baseEval, featuresOf(board), result);
    }

    /** Feature vector: how the eval depends on each symmetric parameter (White POV, centipawns per unit). */
    static double[] featuresOf(Board board) {
        double[] features = new double[PARAM_COUNT];
        byte[] raw = board.getRawBoard();

        for (int row = 1; row <= 6; row++) {          // ranks 2-7
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (piece == Board.whitePawn) {
                    features[FIELD_TO_PARAM[field]] += POSITION_FACTOR;
                } else if (piece == Board.blackPawn) {
                    features[FIELD_TO_PARAM[MIRROR[field]]] -= POSITION_FACTOR;
                }
            }
        }

        return features;
    }

    /** The pawn-table contribution using the real (current, possibly asymmetric) table — for {@code baseEval}. */
    static double pawnContributionActual(Board board) {
        byte[] raw = board.getRawBoard();
        double sum = 0.0;

        for (int row = 1; row <= 6; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (piece == Board.whitePawn) {
                    sum += PieceSquareTables.getPieceSquareWeight(Board.whitePawn, field);
                } else if (piece == Board.blackPawn) {
                    sum -= PieceSquareTables.getPieceSquareWeight(Board.blackPawn, field);
                }
            }
        }

        return sum * POSITION_FACTOR;
    }

    /**
     * The pawn-table contribution for a <b>symmetric</b> table given by the 24
     * parameters, computed directly (independently of {@link #featuresOf}). Used
     * to cross-check the feature construction.
     */
    static double symmetricContribution(Board board, double[] parameters) {
        byte[] raw = board.getRawBoard();
        double sum = 0.0;

        for (int row = 1; row <= 6; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (piece == Board.whitePawn) {
                    sum += parameters[FIELD_TO_PARAM[field]];
                } else if (piece == Board.blackPawn) {
                    sum -= parameters[FIELD_TO_PARAM[MIRROR[field]]];
                }
            }
        }

        return sum * POSITION_FACTOR;
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

    private static int paramOf(int col, int row) {
        int rankIndex = row - 1;                 // rows 1-6 -> 0-5
        int filePair = Math.min(col, 7 - col);   // a/h -> 0, b/g -> 1, c/f -> 2, d/e -> 3

        return rankIndex * 4 + filePair;
    }

    private static double[] readCurrentParameters() {
        double[] values = new double[PARAM_COUNT];

        for (int param = 0; param < PARAM_COUNT; param++) {
            int row = param / 4 + 1;
            int filePair = param % 4;

            // Average the current values of the two files in the pair (the
            // current table is not perfectly symmetric).
            int left = PieceSquareTables.getPieceSquareWeight(Board.whitePawn, ChessUtil.getFieldFromColAndRow(filePair, row));
            int right = PieceSquareTables.getPieceSquareWeight(Board.whitePawn, ChessUtil.getFieldFromColAndRow(7 - filePair, row));

            values[param] = (left + right) / 2.0;
        }

        return values;
    }

    private static int[] buildFieldToParam() {
        int[] map = new int[Board.LENGTH * Board.LENGTH];
        Arrays.fill(map, -1);

        for (int row = 1; row <= 6; row++) {
            for (int col = 0; col < 8; col++) {
                map[ChessUtil.getFieldFromColAndRow(col, row)] = paramOf(col, row);
            }
        }

        return map;
    }

    private static int[] buildMirror() {
        int[] mirror = new int[Board.LENGTH * Board.LENGTH];

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                mirror[ChessUtil.getFieldFromColAndRow(col, row)] = ChessUtil.getFieldFromColAndRow(col, 7 - row);
            }
        }

        return mirror;
    }
}
