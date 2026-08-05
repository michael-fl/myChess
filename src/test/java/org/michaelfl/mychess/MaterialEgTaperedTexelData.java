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
 * Phase-aware ("tapered") Texel adapter for tuning the <b>endgame material
 * values</b> of knight, bishop, rook and queen (the midgame values in
 * {@link WeightingFunction#weightOfPiece} are held fixed). Counterpart of the
 * tapered piece-square-table adapters, but for the material term rather than
 * placement.
 *
 * <p>Motivation: the earlier joint endgame-PST tune measured neutral, and its
 * dominant signal was a uniform per-piece offset — a phase-dependent material
 * re-rating (queen worth more, knight less in the endgame) that a placement
 * table cannot cleanly express. This adapter models it directly.
 *
 * <p>With the tapered material the white-POV eval's material term is
 * {@code blend(mgSum, egSum, phase)} per color; only the endgame values are
 * tuned, so the eval stays linear in the four parameters — each piece's
 * dependence on its endgame value is the per-position constant
 * {@code (MAX_PHASE - phase) / MAX_PHASE} (material carries no positionFactor,
 * unlike the piece-square tables):
 * <pre>{@code eval = baseEval + features . endgameMaterial}</pre>
 *
 * <p>Four parameters (index 0 = knight, 1 = bishop, 2 = rook, 3 = queen). Pawn
 * (the centipawn anchor) and king (value 0) are not tuned. The endgame values
 * are driven by low-phase positions, where the endgame feature weight is largest.
 *
 * @author Michael Fleischhauer
 */
public final class MaterialEgTaperedTexelData {

    /** Full-material phase; the phase is clamped to this. Mirrors {@link WeightingFunction}. */
    private static final int MAX_PHASE = 24;

    /** knight, bishop, rook, queen. */
    static final int PARAM_COUNT = 4;

    /** The tuned white pieces, in parameter order (index 0..3). */
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

    /** white piece byte -> parameter index (0..3), or -1. */
    private static final int[] WHITE_TO_INDEX = buildPieceToIndex(WHITE_PIECES);

    /** black piece byte -> parameter index (0..3), or -1. */
    private static final int[] BLACK_TO_INDEX = buildPieceToIndex(BLACK_PIECES);

    private static final double[] CURRENT_EG_MATERIAL = readCurrentEndgameMaterial();

    private MaterialEgTaperedTexelData() {
        // static utility
    }

    /** The current endgame material values [knight, bishop, rook, queen] — the tuning start point. */
    public static double[] currentTableValues() {
        return CURRENT_EG_MATERIAL.clone();
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

        // Remove only the (tunable) endgame-material part of the four pieces; the
        // fixed midgame material of those pieces, the pawn/king material, and
        // every non-material term stay in baseEval so it is exact.
        double baseEval = fullEval - endgameMaterialContributionActual(board);

        return new Sample(baseEval, featuresOf(board), result);
    }

    /** Feature vector: each piece's phase-weighted dependence on its endgame material value (White POV). */
    static double[] featuresOf(Board board) {
        double[] features = new double[PARAM_COUNT];
        byte[] raw = board.getRawBoard();
        double egCoefficient = endgameCoefficient(board);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                byte piece = raw[ChessUtil.getFieldFromColAndRow(col, row)];

                int white = WHITE_TO_INDEX[piece];
                if (white >= 0) {
                    features[white] += egCoefficient;
                    continue;
                }

                int black = BLACK_TO_INDEX[piece];
                if (black >= 0) {
                    features[black] -= egCoefficient;
                }
            }
        }

        return features;
    }

    /** The four pieces' endgame-material contribution at the current values — for {@code baseEval}. */
    static double endgameMaterialContributionActual(Board board) {
        byte[] raw = board.getRawBoard();
        double sumValues = 0.0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                byte piece = raw[ChessUtil.getFieldFromColAndRow(col, row)];

                if (WHITE_TO_INDEX[piece] >= 0) {
                    sumValues += WeightingFunction.weightOfPieceEndgame[piece];
                } else if (BLACK_TO_INDEX[piece] >= 0) {
                    sumValues -= WeightingFunction.weightOfPieceEndgame[piece];
                }
            }
        }

        return sumValues * endgameCoefficient(board);
    }

    /** Per-position endgame weight of a single material value: {@code (MAX_PHASE - phase) / MAX_PHASE} (no positionFactor). */
    private static double endgameCoefficient(Board board) {
        return (double) (MAX_PHASE - phaseOf(board)) / MAX_PHASE;
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

    /** Display name of parameter {@code index} (0 = knight ... 3 = queen). */
    public static String pieceName(int index) {
        return PIECE_NAMES[index];
    }

    /** The midgame material value of parameter {@code index} (the fixed reference the endgame value diverges from). */
    public static int midGameValue(int index) {
        return WeightingFunction.weightOfPiece[WHITE_PIECES[index]];
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

    private static double[] readCurrentEndgameMaterial() {
        double[] values = new double[PARAM_COUNT];

        for (int index = 0; index < PARAM_COUNT; index++) {
            values[index] = WeightingFunction.weightOfPieceEndgame[WHITE_PIECES[index]];
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
}
