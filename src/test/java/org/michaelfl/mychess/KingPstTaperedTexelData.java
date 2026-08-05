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
 * Phase-aware ("tapered") Texel adapter for tuning the white king <b>endgame</b>
 * piece-square table while holding the midgame table fixed — the tapered rollout
 * step after the pawn endgame table (see {@link PawnPstTaperedTexelData}).
 *
 * <p>This is the highest-value tapered term: the midgame king table keeps the
 * king safe (behind its pawns), while a tuned endgame table rewards
 * centralization — king-safety-lite plus endgame recognition in one term,
 * replacing the crude {@code isEndGame() / plyCount > 60} king-PST skip that
 * {@link WeightingFunction} used to apply (that skip is removed on this branch so
 * the king PST is always blended by phase).
 *
 * <p>With the tapered evaluation a white king on square {@code s} contributes
 * {@code positionFactor * (mg(s)*phase + eg(s)*(MAX_PHASE - phase)) / MAX_PHASE}
 * to the White-POV eval ({@code positionFactor = 0.5}, {@code MAX_PHASE = 24}).
 * Only the endgame values are tuned, so the eval stays linear in the 32 symmetric
 * endgame parameters (each king's dependence on its parameter is the per-position
 * constant {@code positionFactor * (MAX_PHASE - phase) / MAX_PHASE}):
 * <pre>{@code eval = baseEval + features . endgameParameters}</pre>
 *
 * <p>Unlike pawns, the king occupies all eight ranks, so the 64 squares collapse
 * to <b>32 parameters</b> under left/right file symmetry (a/h, b/g, c/f, d/e). A
 * position has exactly one king per side. The endgame values are driven by
 * low-phase positions, where the endgame feature weight is largest — exactly the
 * positions where king centralization matters.
 *
 * @author Michael Fleischhauer
 */
public final class KingPstTaperedTexelData {

    /** WeightingFunction scales the position-weight difference by this factor. */
    private static final double POSITION_FACTOR = 0.5;

    /** Full-material phase; the phase is clamped to this. Mirrors {@link WeightingFunction}. */
    private static final int MAX_PHASE = 24;

    /** field -> phase weight of the piece there (queen 4, rook 2, minor 1, else 0). */
    private static final int[] PHASE_WEIGHT = buildPhaseWeights();

    /** Eight ranks (1-8) times four file pairs (a/h, b/g, c/f, d/e). */
    static final int PARAM_COUNT = 32;

    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String RESULT_TAG = " c9 ";

    /** field -> symmetric parameter index (every on-board square maps to one; kings roam all ranks). */
    private static final int[] FIELD_TO_PARAM = buildFieldToParam();

    /** field -> vertically mirrored field (rank 1 &harr; 8), for the White/Black color symmetry. */
    private static final int[] MIRROR = buildMirror();

    private static final double[] CURRENT_EG_VALUES = readCurrentEndgameParameters();

    private KingPstTaperedTexelData() {
        // static utility
    }

    /** The current endgame king-table values collapsed to the 32 symmetric parameters — the tuning start point. */
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

        // Remove only the (tunable) endgame king part from the real eval; the
        // fixed midgame king part, material, and every other piece stay in
        // baseEval so it is exact.
        double baseEval = fullEval - endgameKingContributionActual(board);

        return new Sample(baseEval, featuresOf(board), result);
    }

    /** Feature vector: each king's phase-weighted dependence on its symmetric endgame parameter (White POV). */
    static double[] featuresOf(Board board) {
        double[] features = new double[PARAM_COUNT];
        byte[] raw = board.getRawBoard();
        double egCoefficient = endgameCoefficient(board);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (piece == Board.whiteKing) {
                    features[FIELD_TO_PARAM[field]] += egCoefficient;
                } else if (piece == Board.blackKing) {
                    features[FIELD_TO_PARAM[MIRROR[field]]] -= egCoefficient;
                }
            }
        }

        return features;
    }

    /** The endgame king-table contribution at the current values — for {@code baseEval}. */
    static double endgameKingContributionActual(Board board) {
        byte[] raw = board.getRawBoard();
        double egCoefficient = endgameCoefficient(board);
        double sumValues = 0.0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (piece == Board.whiteKing) {
                    sumValues += PieceSquareTables.getEndGameWeight(Board.whiteKing, field);
                } else if (piece == Board.blackKing) {
                    sumValues -= PieceSquareTables.getEndGameWeight(Board.blackKing, field);
                }
            }
        }

        return sumValues * egCoefficient;
    }

    /** Per-position endgame weight of a single king's table value: {@code positionFactor * (MAX_PHASE - phase) / MAX_PHASE}. */
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
     * Render tuned endgame parameters as a white king table in the same 8x8 comma
     * grid {@code PieceSquareTables} uses (rank 8 first), ready to paste into the
     * king endgame table. Every square is tuned (the king roams all ranks).
     */
    public static String formatEndgameTable(double[] parameters) {
        var table = new StringBuilder();

        for (int row = 7; row >= 0; row--) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                int value = (int) Math.round(parameters[FIELD_TO_PARAM[field]]);

                table.append("%4d".formatted(value));

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
     * table value scaled by {@code positionFactor} (0.5). Rank 8 first.
     */
    public static String formatEndgameContributions(double[] parameters) {
        var view = new StringBuilder();

        for (int row = 7; row >= 0; row--) {
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
        int filePair = Math.min(col, 7 - col);   // a/h -> 0, b/g -> 1, c/f -> 2, d/e -> 3

        return row * 4 + filePair;                // rows 0-7 -> ranks 1-8
    }

    private static double[] readCurrentEndgameParameters() {
        double[] values = new double[PARAM_COUNT];

        for (int param = 0; param < PARAM_COUNT; param++) {
            int row = param / 4;
            int filePair = param % 4;

            // Average the current values of the two files in the pair (the
            // current table may not be perfectly symmetric).
            int left = PieceSquareTables.getEndGameWeight(Board.whiteKing, ChessUtil.getFieldFromColAndRow(filePair, row));
            int right = PieceSquareTables.getEndGameWeight(Board.whiteKing, ChessUtil.getFieldFromColAndRow(7 - filePair, row));

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

        for (int row = 0; row < 8; row++) {
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
