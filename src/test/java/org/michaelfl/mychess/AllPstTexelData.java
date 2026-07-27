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
 * Texel adapter that tunes <b>all six</b> piece-square tables jointly (pawn,
 * knight, bishop, rook, queen, king), with left/right <b>file symmetry
 * enforced</b> per table — the generalization of {@link PawnPstTexelData} from
 * the single pawn table to the whole {@link PieceSquareTables} set.
 *
 * <p>The evaluation's position-weight sum is linear in the table values: in
 * {@link WeightingFunction} it is scaled by {@code positionFactor = 0.5} and the
 * result is centipawns, so a white piece on square {@code s} adds
 * {@code 0.5 * value(kind, s)} to the White-POV eval, and a black piece
 * subtracts {@code 0.5 * value(kind, mirror(s))} (Black's table is White's
 * mirrored vertically). Everything else in the eval (material, mobility, threat,
 * chess, castling, double-pawn, undefended) is parameter-independent and stored
 * as {@code baseEval}:
 * <pre>{@code eval = baseEval + features . parameters}</pre>
 *
 * <p>Parameter layout — each file pair {@code a/h, b/g, c/f, d/e} shares one
 * value per rank:
 * <ul>
 *   <li>pawn: ranks 2-7 only &rarr; 24 parameters, indices {@code [0, 24)}</li>
 *   <li>knight/bishop/rook/queen/king: all 8 ranks &rarr; 32 each, at offsets
 *       {@code 24, 56, 88, 120, 152}</li>
 * </ul>
 * for a total of {@code 24 + 5*32 = 184} parameters.
 *
 * <p>The dataset is git-ignored; see {@code tuning-data/README.md} for the fetch
 * command.
 *
 * @author Michael Fleischhauer
 */
public final class AllPstTexelData {

    /** WeightingFunction scales the position-weight difference by this factor. */
    private static final double POSITION_FACTOR = 0.5;

    /** Piece kinds in {@code piece & 7} order: pawn=0 ... king=5. */
    static final int KIND_COUNT = 6;

    /** Human-readable kind names, indexed by {@code piece & 7}. */
    static final String[] KIND_NAMES = {"pawn", "knight", "bishop", "rook", "queen", "king"};

    /** Start index of each kind's parameter block; pawn has 24 params, the rest 32. */
    static final int[] BLOCK_OFFSET = {0, 24, 56, 88, 120, 152};

    /** Total number of tunable parameters across all six symmetric tables. */
    static final int PARAM_COUNT = 184;

    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String RESULT_TAG = " c9 ";

    private static final double[] CURRENT_VALUES = readCurrentParameters();

    private AllPstTexelData() {
        // static utility
    }

    /** Number of parameters in a kind's block (pawn 24, others 32). */
    static int blockSize(int kind) {
        return kind == 0 ? 24 : 32;
    }

    /** The current table values collapsed to the symmetric parameters — the tuning start point. */
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

        // baseEval = eval minus the current symmetric position contribution, so
        // that eval is reconstructed exactly at the start parameters.
        double baseEval = fullEval - dot(features, CURRENT_VALUES);

        return new Sample(baseEval, features, result);
    }

    /** Feature vector: how the eval depends on each symmetric parameter (White POV, centipawns per unit). */
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

                if (piece <= Board.whiteKing) {
                    features[paramOf(kind, col, row)] += POSITION_FACTOR;
                } else {
                    features[paramOf(kind, col, 7 - row)] -= POSITION_FACTOR;
                }
            }
        }

        return features;
    }

    /**
     * Render a kind's tuned parameter block as an 8x8 piece-square table in the
     * same comma grid {@link PieceSquareTables} uses (rank 8 first, rank 1 last),
     * ready to paste. For the pawn, ranks 1 and 8 keep their current values
     * (always 0); every other rank uses the tuned values rounded to whole
     * centipawns.
     *
     * @param kind        the piece kind ({@code piece & 7})
     * @param blockParams the tuned parameters for this kind ({@link #blockSize(int)} entries)
     * @return the formatted table string
     */
    static String formatTable(int kind, double[] blockParams) {
        byte whitePiece = (byte) (Board.whitePawn + kind);
        int rowBase = kind == 0 ? 1 : 0;
        int rankCount = kind == 0 ? 6 : 8;
        int[] grid = new int[64]; // grid[row * 8 + col], row 0 = rank 1

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                grid[row * 8 + col] = PieceSquareTables.getMidGameWeight(whitePiece, field);
            }
        }

        for (int localRank = 0; localRank < rankCount; localRank++) {
            int row = rowBase + localRank;

            for (int col = 0; col < 8; col++) {
                int filePair = Math.min(col, 7 - col);
                grid[row * 8 + col] = (int) Math.round(blockParams[localRank * 4 + filePair]);
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

    /** Map a piece kind and (col, row) to its symmetric parameter index. */
    static int paramOf(int kind, int col, int row) {
        int filePair = Math.min(col, 7 - col);   // a/h -> 0, b/g -> 1, c/f -> 2, d/e -> 3
        int localRank = kind == 0 ? row - 1 : row; // pawn ranks 2-7 -> 0-5; others ranks 1-8 -> 0-7

        return BLOCK_OFFSET[kind] + localRank * 4 + filePair;
    }

    private static double[] readCurrentParameters() {
        double[] values = new double[PARAM_COUNT];

        for (int kind = 0; kind < KIND_COUNT; kind++) {
            byte whitePiece = (byte) (Board.whitePawn + kind);
            int rowBase = kind == 0 ? 1 : 0;
            int rankCount = kind == 0 ? 6 : 8;

            for (int localRank = 0; localRank < rankCount; localRank++) {
                int row = rowBase + localRank;

                for (int filePair = 0; filePair < 4; filePair++) {
                    // Average the two files in the pair (the current tables are
                    // not always perfectly symmetric).
                    int left = PieceSquareTables.getMidGameWeight(whitePiece, ChessUtil.getFieldFromColAndRow(filePair, row));
                    int right = PieceSquareTables.getMidGameWeight(whitePiece, ChessUtil.getFieldFromColAndRow(7 - filePair, row));

                    values[BLOCK_OFFSET[kind] + localRank * 4 + filePair] = (left + right) / 2.0;
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
