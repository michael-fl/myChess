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
 * Phase-aware ("tapered") counterpart of {@link PawnPstTexelData} for tuning the
 * white pawn <b>endgame</b> piece-square table while holding the midgame table
 * fixed (the staged tapered rollout, step 1).
 *
 * <p>With the tapered evaluation a white pawn on square {@code s} contributes
 * <pre>{@code positionFactor * (mg(s)*phase + eg(s)*(MAX_PHASE - phase)) / MAX_PHASE}</pre>
 * to the White-POV eval, with {@code positionFactor = 0.5} and
 * {@code MAX_PHASE = 24} (see {@link WeightingFunction#blend} and the phase
 * weights: queen 4, rook 2, minor 1, pawn/king 0). Only the endgame values are
 * tuned, so the midgame part is a per-position constant that folds into
 * {@code baseEval} together with material and every other piece. The eval stays
 * <b>linear</b> in the 24 symmetric endgame parameters, because each pawn's
 * dependence on its parameter is the per-position constant
 * {@code positionFactor * (MAX_PHASE - phase) / MAX_PHASE}:
 * <pre>{@code eval = baseEval + features . endgameParameters}</pre>
 *
 * <p>File symmetry (a/h, b/g, c/f, d/e) is enforced exactly as in
 * {@link PawnPstTexelData}: the 48 pawn squares collapse to 24 parameters. As a
 * consequence, midgame-heavy positions (phase near {@code MAX_PHASE}) carry
 * almost no endgame signal, so the endgame table is tuned predominantly from
 * low-phase (endgame) positions — exactly the intent.
 *
 * <p>The dataset is git-ignored; see {@code tuning-data/README.md}.
 *
 * @author Michael Fleischhauer
 */
public final class PawnPstTaperedTexelData {

    /** WeightingFunction scales the position-weight difference by this factor. */
    private static final double POSITION_FACTOR = 0.5;

    /** Full-material phase; the phase is clamped to this. Mirrors {@link WeightingFunction}. */
    private static final int MAX_PHASE = 24;

    /** field -> phase weight of the piece there (queen 4, rook 2, minor 1, else 0). */
    private static final int[] PHASE_WEIGHT = buildPhaseWeights();

    /** Six tunable ranks (2-7) times four file pairs (a/h, b/g, c/f, d/e). */
    static final int PARAM_COUNT = 24;

    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String RESULT_TAG = " c9 ";

    /** field -> symmetric parameter index, or -1 for non-pawn squares (ranks 1 and 8). */
    private static final int[] FIELD_TO_PARAM = buildFieldToParam();

    /** field -> vertically mirrored field (rank 1 &harr; 8), for the White/Black color symmetry. */
    private static final int[] MIRROR = buildMirror();

    private static final double[] CURRENT_EG_VALUES = readCurrentEndgameParameters();

    private PawnPstTaperedTexelData() {
        // static utility
    }

    /** The current endgame pawn-table values collapsed to the 24 symmetric parameters — the tuning start point. */
    public static double[] currentTableValues() {
        return CURRENT_EG_VALUES.clone();
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

        // Remove only the (tunable) endgame pawn part from the real eval; the
        // fixed midgame pawn part, material, and every other piece stay in
        // baseEval so it is exact.
        double baseEval = fullEval - endgamePawnContributionActual(board);

        return new Sample(baseEval, featuresOf(board), result);
    }

    /** Feature vector: each pawn's phase-weighted dependence on its symmetric endgame parameter (White POV). */
    static double[] featuresOf(Board board) {
        double[] features = new double[PARAM_COUNT];
        byte[] raw = board.getRawBoard();
        double egCoefficient = endgameCoefficient(board);

        for (int row = 1; row <= 6; row++) {          // ranks 2-7
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (piece == Board.whitePawn) {
                    features[FIELD_TO_PARAM[field]] += egCoefficient;
                } else if (piece == Board.blackPawn) {
                    features[FIELD_TO_PARAM[MIRROR[field]]] -= egCoefficient;
                }
            }
        }

        return features;
    }

    /** The endgame pawn-table contribution at the current values — for {@code baseEval}. */
    static double endgamePawnContributionActual(Board board) {
        byte[] raw = board.getRawBoard();
        double egCoefficient = endgameCoefficient(board);
        double sumValues = 0.0;

        for (int row = 1; row <= 6; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (piece == Board.whitePawn) {
                    sumValues += PieceSquareTables.getEndGameWeight(Board.whitePawn, field);
                } else if (piece == Board.blackPawn) {
                    sumValues -= PieceSquareTables.getEndGameWeight(Board.blackPawn, field);
                }
            }
        }

        return sumValues * egCoefficient;
    }

    /** Per-position endgame weight of a single pawn's table value: {@code positionFactor * (MAX_PHASE - phase) / MAX_PHASE}. */
    private static double endgameCoefficient(Board board) {
        return POSITION_FACTOR * (MAX_PHASE - phaseOf(board)) / MAX_PHASE;
    }

    /** Tapered game phase of {@code board}, clamped to {@link #MAX_PHASE}; mirrors {@link WeightingFunction}. */
    static int phaseOf(Board board) {
        byte[] raw = board.getRawBoard();
        int phase = 0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                byte piece = raw[ChessUtil.getFieldFromColAndRow(col, row)];

                if (piece != Board.empty && piece != Board.illegal) {
                    phase += PHASE_WEIGHT[piece];
                }
            }
        }

        return Math.min(phase, MAX_PHASE);
    }

    /**
     * Render tuned endgame parameters as a white pawn table in the same 8x8 comma
     * grid {@code PieceSquareTables} uses (rank 8 first), ready to paste into the
     * endgame pawn table. Ranks 1 and 8 keep their current endgame values; ranks
     * 2-7 use the tuned values rounded to whole centipawns.
     */
    public static String formatEndgameTable(double[] parameters) {
        int[] grid = new int[64];

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                int param = FIELD_TO_PARAM[field];
                grid[row * 8 + col] = param < 0
                        ? PieceSquareTables.getEndGameWeight(Board.whitePawn, field)
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
     * Human-readable view of the tuned endgame table: each square's <b>actual
     * eval contribution</b> in centipawns at the pure-endgame phase, i.e. the
     * table value scaled by {@code positionFactor} (0.5). Ranks 2-7 only.
     */
    public static String formatEndgameContributions(double[] parameters) {
        var view = new StringBuilder();

        for (int row = 6; row >= 1; row--) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                double contribution = parameters[FIELD_TO_PARAM[field]] * POSITION_FACTOR;

                view.append("%7.1f".formatted(contribution));
            }
            view.append('\n');
        }

        return view.toString();
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

    private static double[] readCurrentEndgameParameters() {
        double[] values = new double[PARAM_COUNT];

        for (int param = 0; param < PARAM_COUNT; param++) {
            int row = param / 4 + 1;
            int filePair = param % 4;

            // Average the current values of the two files in the pair (the
            // current table is not necessarily perfectly symmetric).
            int left = PieceSquareTables.getEndGameWeight(Board.whitePawn, ChessUtil.getFieldFromColAndRow(filePair, row));
            int right = PieceSquareTables.getEndGameWeight(Board.whitePawn, ChessUtil.getFieldFromColAndRow(7 - filePair, row));

            values[param] = (left + right) / 2.0;
        }

        return values;
    }

    private static int[] buildPhaseWeights() {
        int[] weights = new int[Board.blackKing + 1];

        weights[Board.whiteKnight] = 1;
        weights[Board.whiteBishop] = 1;
        weights[Board.whiteRook] = 2;
        weights[Board.whiteQueen] = 4;
        weights[Board.blackKnight] = 1;
        weights[Board.blackBishop] = 1;
        weights[Board.blackRook] = 2;
        weights[Board.blackQueen] = 4;
        // pawns and kings contribute 0 (array default)

        return weights;
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
