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
 * Phase-aware ("tapered") Texel adapter for jointly tuning the <b>endgame</b>
 * piece-square tables of the four remaining pieces — knight, bishop, rook, queen
 * — in a single tuner run, holding the midgame tables fixed. The tapered rollout
 * step after the pawn ({@link PawnPstTaperedTexelData}) and king
 * ({@link KingPstTaperedTexelData}) endgame tables.
 *
 * <p>Tuning the four together (rather than one piece at a time) bundles four
 * small per-piece effects into a single, decisively measurable SPRT, and lets the
 * tuner trade the pieces' endgame placement off against each other on the same
 * data set.
 *
 * <p>With the tapered evaluation a white piece on square {@code s} contributes
 * {@code positionFactor * (mg(s)*phase + eg(s)*(MAX_PHASE - phase)) / MAX_PHASE}
 * to the White-POV eval ({@code positionFactor = 0.5}, {@code MAX_PHASE = 24}).
 * Only the endgame values are tuned, so the eval stays linear in the parameters
 * (each piece's dependence on its parameter is the per-position constant
 * {@code positionFactor * (MAX_PHASE - phase) / MAX_PHASE}):
 * <pre>{@code eval = baseEval + features . endgameParameters}</pre>
 *
 * <p>Each of the four pieces contributes <b>32 parameters</b> under left/right
 * file symmetry (a/h, b/g, c/f, d/e) across the eight ranks, for
 * {@link #PARAM_COUNT} = 128 in total. The parameter block for piece {@code p}
 * (0 = knight, 1 = bishop, 2 = rook, 3 = queen) starts at {@code p * 32}. The
 * endgame values are driven by low-phase positions, where the endgame feature
 * weight is largest.
 *
 * @author Michael Fleischhauer
 */
public final class JointEndgamePstTaperedTexelData {

    /** WeightingFunction scales the position-weight difference by this factor. */
    private static final double POSITION_FACTOR = 0.5;

    /** Full-material phase; the phase is clamped to this. Mirrors {@link WeightingFunction}. */
    private static final int MAX_PHASE = 24;

    /** Eight ranks (1-8) times four file pairs (a/h, b/g, c/f, d/e). */
    static final int PARAMS_PER_PIECE = 32;

    /** knight, bishop, rook, queen. */
    static final int PIECE_COUNT = 4;

    /** 4 pieces times 32 symmetric squares each. */
    static final int PARAM_COUNT = PARAMS_PER_PIECE * PIECE_COUNT;

    /** The tuned white pieces, in parameter-block order (index 0..3). */
    private static final byte[] WHITE_PIECES =
            {Board.whiteKnight, Board.whiteBishop, Board.whiteRook, Board.whiteQueen};

    /** The tuned black pieces, same order. */
    private static final byte[] BLACK_PIECES =
            {Board.blackKnight, Board.blackBishop, Board.blackRook, Board.blackQueen};

    private static final String[] PIECE_NAMES = {"knight", "bishop", "rook", "queen"};

    /** field -> phase weight of the piece there (queen 4, rook 2, minor 1, else 0). */
    private static final int[] PHASE_WEIGHT = buildPhaseWeights();

    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String RESULT_TAG = " c9 ";

    /** white piece byte -> block index (0..3), or -1. */
    private static final int[] WHITE_TO_INDEX = buildPieceToIndex(WHITE_PIECES);

    /** black piece byte -> block index (0..3), or -1. */
    private static final int[] BLACK_TO_INDEX = buildPieceToIndex(BLACK_PIECES);

    /** field -> symmetric parameter offset within a piece block (0..31). */
    private static final int[] FIELD_TO_PARAM = buildFieldToParam();

    /** field -> vertically mirrored field (rank 1 &harr; 8), for the White/Black color symmetry. */
    private static final int[] MIRROR = buildMirror();

    private static final double[] CURRENT_EG_VALUES = readCurrentEndgameParameters();

    private JointEndgamePstTaperedTexelData() {
        // static utility
    }

    /** The current endgame values of all four pieces collapsed to the 128 symmetric parameters — the tuning start point. */
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

        // Remove only the (tunable) endgame part of the four pieces from the real
        // eval; the fixed midgame part of those pieces, material, and every other
        // piece (pawns, kings) stay in baseEval so it is exact.
        double baseEval = fullEval - endgameContributionActual(board);

        return new Sample(baseEval, featuresOf(board), result);
    }

    /** Feature vector: each piece's phase-weighted dependence on its symmetric endgame parameter (White POV). */
    static double[] featuresOf(Board board) {
        double[] features = new double[PARAM_COUNT];
        byte[] raw = board.getRawBoard();
        double egCoefficient = endgameCoefficient(board);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                int white = WHITE_TO_INDEX[piece];
                if (white >= 0) {
                    features[white * PARAMS_PER_PIECE + FIELD_TO_PARAM[field]] += egCoefficient;
                    continue;
                }

                int black = BLACK_TO_INDEX[piece];
                if (black >= 0) {
                    features[black * PARAMS_PER_PIECE + FIELD_TO_PARAM[MIRROR[field]]] -= egCoefficient;
                }
            }
        }

        return features;
    }

    /** The four pieces' endgame-table contribution at the current values — for {@code baseEval}. */
    static double endgameContributionActual(Board board) {
        byte[] raw = board.getRawBoard();
        double egCoefficient = endgameCoefficient(board);
        double sumValues = 0.0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                if (WHITE_TO_INDEX[piece] >= 0) {
                    sumValues += PieceSquareTables.getEndGameWeight(piece, field);
                } else if (BLACK_TO_INDEX[piece] >= 0) {
                    sumValues -= PieceSquareTables.getEndGameWeight(piece, field);
                }
            }
        }

        return sumValues * egCoefficient;
    }

    /** Per-position endgame weight of a single table value: {@code positionFactor * (MAX_PHASE - phase) / MAX_PHASE}. */
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
     * Render one piece's tuned endgame parameters as a white piece table in the
     * same 8x8 comma grid {@code PieceSquareTables} uses (rank 8 first), ready to
     * paste into that piece's endgame table.
     *
     * @param parameters the full 128-parameter vector
     * @param pieceIndex 0 = knight, 1 = bishop, 2 = rook, 3 = queen
     */
    public static String formatEndgameTable(double[] parameters, int pieceIndex) {
        int base = pieceIndex * PARAMS_PER_PIECE;
        var table = new StringBuilder();

        for (int row = 7; row >= 0; row--) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                int value = (int) Math.round(parameters[base + FIELD_TO_PARAM[field]]);

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
     * Human-readable view of one piece's tuned endgame table: each square's
     * <b>actual eval contribution</b> in centipawns at the pure-endgame phase,
     * i.e. the table value scaled by {@code positionFactor} (0.5). Rank 8 first.
     */
    public static String formatEndgameContributions(double[] parameters, int pieceIndex) {
        int base = pieceIndex * PARAMS_PER_PIECE;
        var view = new StringBuilder();

        for (int row = 7; row >= 0; row--) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                double contribution = parameters[base + FIELD_TO_PARAM[field]] * POSITION_FACTOR;

                view.append("%7.1f".formatted(contribution));
            }
            view.append('\n');
        }

        return view.toString();
    }

    /** Display name of parameter block {@code pieceIndex} (0 = knight ... 3 = queen). */
    public static String pieceName(int pieceIndex) {
        return PIECE_NAMES[pieceIndex];
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

        for (int pieceIndex = 0; pieceIndex < PIECE_COUNT; pieceIndex++) {
            byte piece = WHITE_PIECES[pieceIndex];
            int base = pieceIndex * PARAMS_PER_PIECE;

            for (int param = 0; param < PARAMS_PER_PIECE; param++) {
                int row = param / 4;
                int filePair = param % 4;

                // Average the current values of the two files in the pair (the
                // current table may not be perfectly symmetric).
                int left = PieceSquareTables.getEndGameWeight(piece, ChessUtil.getFieldFromColAndRow(filePair, row));
                int right = PieceSquareTables.getEndGameWeight(piece, ChessUtil.getFieldFromColAndRow(7 - filePair, row));

                values[base + param] = (left + right) / 2.0;
            }
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

    private static int[] buildPieceToIndex(byte[] pieces) {
        int[] map = new int[Board.blackKing + 1];
        Arrays.fill(map, -1);

        for (int index = 0; index < pieces.length; index++) {
            map[pieces[index]] = index;
        }

        return map;
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
