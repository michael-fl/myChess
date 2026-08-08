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
 * Full-joint, phase-aware ("tapered") Texel adapter that tunes the
 * <b>midgame and endgame</b> piece-square tables of all six piece kinds
 * simultaneously, holding material and every non-PST eval term fixed.
 *
 * <p>With the tapered evaluation a white piece of kind {@code p} on square
 * {@code s} contributes
 * <pre>{@code positionFactor * (mg(s)*phase + eg(s)*(MAX_PHASE - phase)) / MAX_PHASE}</pre>
 * to the White-POV eval, with {@code positionFactor = 0.5} and
 * {@code MAX_PHASE = 24} (see {@link WeightingFunction#blend}). Both the midgame
 * value {@code mg(s)} and the endgame value {@code eg(s)} are tunable here, so
 * each piece gets a genuine midgame/endgame split (the four mobile pieces
 * currently share one table for both phases). Because the contribution is linear
 * in every table value, the eval stays linear in the parameters:
 * <pre>{@code eval = baseEval + features . params}</pre>
 * where {@code baseEval = fullEval - features . currentValues} — defined via the
 * symmetric features so the identity is <b>exact by construction</b>, regardless
 * of any residual left/right asymmetry in the shipped tables.
 *
 * <p>Layout: six pieces (pawn, knight, bishop, rook, queen, king) times two
 * phases (midgame, endgame) times {@value #SLOTS_PER_BLOCK} file-mirror-symmetric
 * squares (8 ranks times 4 file pairs a/h, b/g, c/f, d/e) = {@value #PARAM_COUNT}
 * parameters. The block for {@code (pieceIndex, phaseIndex)} starts at
 * {@code (pieceIndex * 2 + phaseIndex) * 32}; phase index 0 = midgame, 1 =
 * endgame. Pawns never stand on ranks 1 and 8, so those pawn slots receive no
 * feature weight and stay frozen at their initial value.
 *
 * <p><b>Material leak.</b> Since material is fixed, the tuner can express a
 * per-piece material re-rating as a uniform offset of that piece's table. Such an
 * offset is a material signal, not placement (see the project notes), so
 * {@link #recenterToCurrentMeans(double[])} restores each block's original mean
 * after tuning, keeping only the tuned <em>shape</em>.
 *
 * <p>The dataset is git-ignored; see {@code tuning-data/README.md}.
 *
 * @author Michael Fleischhauer
 */
public final class JointMgEgPstTaperedTexelData {

    /** WeightingFunction scales the position-weight difference by this factor. */
    private static final double POSITION_FACTOR = 0.5;

    /** Full-material phase; the phase is clamped to this. Mirrors {@link WeightingFunction}. */
    private static final int MAX_PHASE = 24;

    /** Eight ranks (1-8) times four file pairs (a/h, b/g, c/f, d/e). */
    static final int SLOTS_PER_BLOCK = 32;

    /** Midgame and endgame. */
    static final int PHASE_COUNT = 2;

    /** Midgame phase index. */
    static final int PHASE_MG = 0;

    /** Endgame phase index. */
    static final int PHASE_EG = 1;

    /** pawn, knight, bishop, rook, queen, king. */
    static final int PIECE_COUNT = 6;

    /** 6 pieces times 2 phases times 32 symmetric squares. */
    static final int PARAM_COUNT = PIECE_COUNT * PHASE_COUNT * SLOTS_PER_BLOCK;

    private static final byte[] WHITE_PIECES = {
            Board.whitePawn, Board.whiteKnight, Board.whiteBishop,
            Board.whiteRook, Board.whiteQueen, Board.whiteKing
    };

    private static final byte[] BLACK_PIECES = {
            Board.blackPawn, Board.blackKnight, Board.blackBishop,
            Board.blackRook, Board.blackQueen, Board.blackKing
    };

    private static final String[] PIECE_NAMES = {"pawn", "knight", "bishop", "rook", "queen", "king"};

    private static final String[] PHASE_NAMES = {"midgame", "endgame"};

    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String RESULT_TAG = " c9 ";

    /** field -> phase weight of the piece there (queen 4, rook 2, minor 1, else 0). */
    private static final int[] PHASE_WEIGHT = buildPhaseWeights();

    /** white piece byte -> piece index (0..5), or -1. */
    private static final int[] WHITE_TO_INDEX = buildPieceToIndex(WHITE_PIECES);

    /** black piece byte -> piece index (0..5), or -1. */
    private static final int[] BLACK_TO_INDEX = buildPieceToIndex(BLACK_PIECES);

    /** field -> symmetric slot offset within a block (0..31). */
    private static final int[] FIELD_TO_PARAM = buildFieldToParam();

    /** field -> vertically mirrored field (rank 1 &harr; 8), for the White/Black color symmetry. */
    private static final int[] MIRROR = buildMirror();

    private static final double[] CURRENT_VALUES = readCurrentValues();

    private JointMgEgPstTaperedTexelData() {
        // static utility
    }

    /** The current midgame+endgame table values collapsed to the {@value #PARAM_COUNT} symmetric parameters — the tuning start point. */
    public static double[] currentTableValues() {
        return CURRENT_VALUES.clone();
    }

    /** Block start index for {@code (pieceIndex, phaseIndex)} in the parameter vector. */
    static int blockBase(int pieceIndex, int phaseIndex) {
        return (pieceIndex * PHASE_COUNT + phaseIndex) * SLOTS_PER_BLOCK;
    }

    /**
     * Load up to {@code limit} samples from a {@code c9}-labeled EPD file
     * (Zurichess or self-play). Malformed or illegal positions are skipped.
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

        // baseEval is defined via the symmetric features so eval(currentValues)
        // reconstructs fullEval exactly; the residual (material, non-PST terms,
        // and any left/right asymmetry of the shipped tables) folds in here.
        double baseEval = fullEval - dot(features, CURRENT_VALUES);

        return new Sample(baseEval, features, result);
    }

    /** Feature vector: each piece's phase-weighted dependence on its symmetric midgame and endgame slots (White POV). */
    static double[] featuresOf(Board board) {
        double[] features = new double[PARAM_COUNT];
        byte[] raw = board.getRawBoard();
        double mgCoefficient = midgameCoefficient(board);
        double egCoefficient = endgameCoefficient(board);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                byte piece = raw[field];

                int white = WHITE_TO_INDEX[piece];
                if (white >= 0) {
                    int slot = FIELD_TO_PARAM[field];
                    features[blockBase(white, PHASE_MG) + slot] += mgCoefficient;
                    features[blockBase(white, PHASE_EG) + slot] += egCoefficient;
                    continue;
                }

                int black = BLACK_TO_INDEX[piece];
                if (black >= 0) {
                    int slot = FIELD_TO_PARAM[MIRROR[field]];
                    features[blockBase(black, PHASE_MG) + slot] -= mgCoefficient;
                    features[blockBase(black, PHASE_EG) + slot] -= egCoefficient;
                }
            }
        }

        return features;
    }

    private static double dot(double[] a, double[] b) {
        double sum = 0.0;

        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }

        return sum;
    }

    /** Per-position weight of a single midgame table value: {@code positionFactor * phase / MAX_PHASE}. */
    private static double midgameCoefficient(Board board) {
        return POSITION_FACTOR * phaseOf(board) / MAX_PHASE;
    }

    /** Per-position weight of a single endgame table value: {@code positionFactor * (MAX_PHASE - phase) / MAX_PHASE}. */
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
     * Restore each block's original mean after tuning, removing any uniform
     * per-piece/per-phase offset the tuner introduced (a material re-rating in
     * disguise). Returns a fresh vector; the tuned shape within each block is
     * preserved, only the level is re-anchored to the current table's mean.
     *
     * <p>Structurally inert pawn slots (ranks 1 and 8, where a pawn can never
     * stand) are excluded from the mean and left at their current value, so the
     * re-centering preserves the mean over the <em>occupiable</em> pawn squares
     * only. Averaging them in would spread the correction over eight
     * always-zero slots and leave a residual pawn material shift.
     */
    public static double[] recenterToCurrentMeans(double[] tuned) {
        double[] result = tuned.clone();

        for (int pieceIndex = 0; pieceIndex < PIECE_COUNT; pieceIndex++) {
            boolean pawn = isPawn(pieceIndex);

            for (int phaseIndex = 0; phaseIndex < PHASE_COUNT; phaseIndex++) {
                int base = blockBase(pieceIndex, phaseIndex);
                double shift = activeMean(tuned, base, pawn) - activeMean(CURRENT_VALUES, base, pawn);

                for (int slot = 0; slot < SLOTS_PER_BLOCK; slot++) {
                    if (pawn && isInertPawnSlot(slot)) {
                        result[base + slot] = CURRENT_VALUES[base + slot];
                    } else {
                        result[base + slot] = tuned[base + slot] - shift;
                    }
                }
            }
        }

        return result;
    }

    /** Whether {@code pieceIndex} is the pawn block (index 0). */
    private static boolean isPawn(int pieceIndex) {
        return pieceIndex == 0;
    }

    /** A pawn slot on rank 1 or 8 (row 0 or 7) — never occupiable, hence inert. */
    private static boolean isInertPawnSlot(int slot) {
        int row = slot / 4;

        return row == 0 || row == 7;
    }

    /** Mean over the occupiable slots of a block (all 32, minus the inert pawn ranks for pawns). */
    private static double activeMean(double[] values, int base, boolean pawn) {
        double sum = 0.0;
        int count = 0;

        for (int slot = 0; slot < SLOTS_PER_BLOCK; slot++) {
            if (pawn && isInertPawnSlot(slot)) {
                continue;
            }

            sum += values[base + slot];
            count++;
        }

        return sum / count;
    }

    /**
     * Render one piece/phase block as a white piece table in the same 8x8 comma
     * grid {@code PieceSquareTables} uses (rank 8 first), ready to paste into that
     * piece's midgame or endgame table.
     */
    public static String formatTable(double[] parameters, int pieceIndex, int phaseIndex) {
        int base = blockBase(pieceIndex, phaseIndex);
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

    /** Display name of piece {@code pieceIndex} (0 = pawn ... 5 = king). */
    public static String pieceName(int pieceIndex) {
        return PIECE_NAMES[pieceIndex];
    }

    /** Display name of phase {@code phaseIndex} (0 = midgame, 1 = endgame). */
    public static String phaseName(int phaseIndex) {
        return PHASE_NAMES[phaseIndex];
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

    private static int weightAt(byte piece, int phaseIndex, int field) {
        return phaseIndex == PHASE_MG
                ? PieceSquareTables.getMidGameWeight(piece, field)
                : PieceSquareTables.getEndGameWeight(piece, field);
    }

    private static double[] readCurrentValues() {
        double[] values = new double[PARAM_COUNT];

        for (int pieceIndex = 0; pieceIndex < PIECE_COUNT; pieceIndex++) {
            byte piece = WHITE_PIECES[pieceIndex];

            for (int phaseIndex = 0; phaseIndex < PHASE_COUNT; phaseIndex++) {
                int base = blockBase(pieceIndex, phaseIndex);

                for (int slot = 0; slot < SLOTS_PER_BLOCK; slot++) {
                    int row = slot / 4;
                    int filePair = slot % 4;

                    // Average the two files of the pair (the shipped table may
                    // not be perfectly left/right symmetric).
                    int left = weightAt(piece, phaseIndex, ChessUtil.getFieldFromColAndRow(filePair, row));
                    int right = weightAt(piece, phaseIndex, ChessUtil.getFieldFromColAndRow(7 - filePair, row));

                    values[base + slot] = (left + right) / 2.0;
                }
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

        for (int i = 0; i < pieces.length; i++) {
            map[pieces[i]] = i;
        }

        return map;
    }

    private static int[] buildFieldToParam() {
        int[] map = new int[Board.LENGTH * Board.LENGTH];
        Arrays.fill(map, -1);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int filePair = Math.min(col, 7 - col);   // a/h -> 0, b/g -> 1, c/f -> 2, d/e -> 3
                map[ChessUtil.getFieldFromColAndRow(col, row)] = row * 4 + filePair;
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
