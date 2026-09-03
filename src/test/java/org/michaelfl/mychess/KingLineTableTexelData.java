package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Texel corpus for re-fitting {@link WeightingFunction#KING_LINE_PENALTY} against <b>game
 * results</b> instead of against another engine's static evaluation.
 *
 * <h2>Why re-fit at all</h2>
 *
 * <p>The shipped table came from isotonic least squares against Stockfish's static NNUE
 * evaluation. That objective optimises for <em>agreement with an evaluator</em>, which is not the
 * same thing as winning games, and the term went on to lose 28.9 Elo. A one-dimensional sweep of
 * its scale factor then showed the shipped strength was already near the game-result optimum
 * (−0.008 against the shipped −0.010, a 0.07 % difference in mean squared error), so the values
 * are not merely too loud as a group. What has never been tried is fitting the <em>shape</em> to
 * results.
 *
 * <h2>The linearity that makes it cheap</h2>
 *
 * <p>The term contributes
 * {@code (blend(TABLE[dWhite]) - blend(TABLE[dBlack])) * kingLinePenaltyFactor * 100} centipawns,
 * and {@code blend(x, 0, phase) = x * phase / MAX_PHASE}. So the evaluation is <b>linear in the
 * table entries</b>: entry {@code i} carries the coefficient
 * {@code phase / MAX_PHASE * factor * 100} when white's danger index equals {@code i}, the
 * negation of that when black's does, and zero otherwise. One pass over the corpus therefore
 * produces a design matrix the tuner can search without ever re-evaluating a position.
 *
 * <p><b>One approximation, stated:</b> {@code blend} rounds to an int, so the relation is linear
 * only up to ±0.5 cp per side. {@link #verifyLinearity(Path, int)} measures the residual rather
 * than assuming it is small.
 *
 * <h2>Index 0 is not a parameter</h2>
 *
 * <p>"No danger, no penalty" is a definition, not a measurement, so {@code TABLE[0]} stays 0 and
 * the parameter vector covers indices 1–12. A tuner allowed to move index 0 would just shift the
 * whole table against a constant and report a meaningless offset.
 *
 * @author Michael Fleischhauer
 */
public final class KingLineTableTexelData {

    /** Tunable entries: table indices 1 through 12. Index 0 is pinned at zero by definition. */
    public static final int PARAMETERS = 12;

    /**
     * How the term's strength is scaled with material. The shipped code uses {@link #PHASE},
     * which is a modelling error worth measuring rather than arguing about: the standard phase
     * counts the non-pawn material of <em>both</em> sides, so a side with a queen and two rooks
     * facing an opponent with none is still penalised at full strength for open files that nobody
     * can use. King danger depends on who can attack, which is the opponent's material alone.
     *
     * <p>{@link #OPPONENT_HEAVY} is the closer fit for <em>this</em> term specifically: it scores
     * open <em>files</em>, and files are threatened by rooks and queens. Its own top level is
     * literally "open file with an enemy rook or queen"; knights and bishops contribute nothing to
     * file pressure. {@link #OPPONENT_NON_PAWN} is the wider variant, included because the
     * argument for the narrower one is an argument and not a measurement.
     */
    public enum Scaling {
        /** Shipped: `phase / 24`, counting both sides' non-pawn material. */
        PHASE,
        /** Rooks and queens of the side attacking this king, over their maximum of 8. */
        OPPONENT_HEAVY,
        /** All non-pawn material of the side attacking this king, over its maximum of 12. */
        OPPONENT_NON_PAWN
    }

    private static final int FILES = 8;
    private static final int MAX_PHASE = 24;
    private static final int MAX_HEAVY_PER_SIDE = 8;      // 2 rooks * 2 + 1 queen * 4
    private static final int MAX_NON_PAWN_PER_SIDE = 12;  // 2*1 + 2*1 + 2*2 + 1*4

    private static final String RESULT_TAG = " c9 ";
    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final String FACTOR = "kingLinePenaltyFactor";

    private KingLineTableTexelData() {
        // static utility
    }

    /** The shipped table's entries 1–12, as the tuner's starting point. */
    public static double[] currentParameters() {
        double[] out = new double[PARAMETERS];

        for (int i = 0; i < PARAMETERS; i++) {
            out[i] = WeightingFunction.KING_LINE_PENALTY[i + 1];
        }

        return out;
    }

    public static List<Sample> load(Path epd, int limit) {
        return load(epd, limit, Scaling.PHASE);
    }

    public static List<Sample> load(Path epd, int limit, Scaling scaling) {
        var evaluator = new WeightingFunction();
        var out = new ArrayList<Sample>();

        try (Stream<String> lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (out.size() >= limit) {
                    break;
                }

                Sample sample = sampleOf(line, evaluator, scaling);

                if (sample != null) {
                    out.add(sample);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + epd, e);
        }

        return out;
    }

    /**
     * One position with its design row under <em>every</em> scaling, plus how far the scalings
     * disagree here.
     *
     * <p>Exists because comparing scalings by mean squared error over a whole corpus dilutes the
     * question. Two scalings differ only where the material they read differs — the standard phase
     * counts both sides' non-pawn material, the opponent variants count one side's — so in the
     * majority of quiet positions they agree and contribute identical error. A large effect on a
     * small subset then shows up as a small effect in the average, which is what the first
     * comparison found and could not distinguish from no effect at all.
     *
     * @param baseEval          the evaluation with the king-line term removed, scaling-independent
     * @param result            the game result, 0 / 0.5 / 1
     * @param divergence        the largest gap between the phase scale and the opponent-heavy
     *                          scale over the two defending kings, in 0..1
     * @param featuresByScaling design rows indexed by {@link Scaling#ordinal()}
     */
    public record Row(double baseEval, double result, double divergence,
                      double[][] featuresByScaling) {}

    /** Every scaling's design row for every usable position, in one pass over the corpus. */
    public static List<Row> loadAll(Path epd, int limit) {
        var evaluator = new WeightingFunction();
        var out = new ArrayList<Row>();

        try (Stream<String> lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (out.size() >= limit) {
                    break;
                }

                Row row = rowOf(line, evaluator);

                if (row != null) {
                    out.add(row);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + epd, e);
        }

        return out;
    }

    private static Row rowOf(String epdLine, WeightingFunction evaluator) {
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
        } catch (IllegalArgumentException _) {
            return null;
        }

        int eval = evaluator.calculate(board);

        if (WeightingFunction.isIllegalWeight(eval)) {
            return null;
        }

        var scalings = Scaling.values();
        var features = new double[scalings.length][];

        for (Scaling scaling : scalings) {
            features[scaling.ordinal()] = featuresOf(evaluator, board, scaling);
        }

        double shippedContribution = 0;

        for (int i = 0; i < PARAMETERS; i++) {
            shippedContribution += features[Scaling.PHASE.ordinal()][i]
                    * WeightingFunction.KING_LINE_PENALTY[i + 1];
        }

        double phaseScale = scaleFor(evaluator, board, 0, Scaling.PHASE);
        double divergence = Math.max(
                Math.abs(phaseScale - scaleFor(evaluator, board, 0, Scaling.OPPONENT_HEAVY)),
                Math.abs(phaseScale - scaleFor(evaluator, board, 1, Scaling.OPPONENT_HEAVY)));

        return new Row(eval - shippedContribution, result, divergence, features);
    }

    private static Sample sampleOf(String epdLine, WeightingFunction evaluator,
                                   Scaling scaling) {
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
        } catch (IllegalArgumentException _) {
            return null;
        }

        int eval = evaluator.calculate(board);

        if (WeightingFunction.isIllegalWeight(eval)) {
            return null;
        }

        // The base must be the evaluation WITHOUT the term, so it is scaling-independent and every
        // candidate scaling sits on top of the same ground. Subtracting the candidate's own
        // contribution instead would make baseEval + sum(features * shippedTable) reproduce the
        // evaluation exactly for ANY scaling — a tautology that reports identical error for all of
        // them. That bug produced three MSEs equal to eight decimals before it was found.
        double[] asShipped = featuresOf(evaluator, board, Scaling.PHASE);
        double shippedContribution = 0;

        for (int i = 0; i < PARAMETERS; i++) {
            shippedContribution += asShipped[i] * WeightingFunction.KING_LINE_PENALTY[i + 1];
        }

        return new Sample(eval - shippedContribution, featuresOf(evaluator, board, scaling), result);
    }

    /**
     * The design row for one position: what each table entry is worth to the evaluation here.
     *
     * <p>Must be called after {@link WeightingFunction#calculate}, whose walk fills the per-color
     * danger and the phase.
     */
    private static double[] featuresOf(WeightingFunction evaluator, Board board, Scaling scaling) {
        final int[] danger = evaluator.getKingLineDanger();
        final double weight = factor() * 100.0;
        double[] features = new double[PARAMETERS];

        // Each king is scaled by what threatens IT, so the two sides can differ — which is the
        // whole point of the opponent-material variants and cannot happen under PHASE.
        if (danger[0] > 0) {
            features[danger[0] - 1] += scaleFor(evaluator, board, 0, scaling) * weight;
        }

        if (danger[1] > 0) {
            features[danger[1] - 1] -= scaleFor(evaluator, board, 1, scaling) * weight;
        }

        return features;
    }

    /** The 0..1 strength multiplier for {@code defender}'s king under the given scaling. */
    private static double scaleFor(WeightingFunction evaluator, Board board, int defender,
                                   Scaling scaling) {
        return switch (scaling) {
            case PHASE -> evaluator.getPhase() / (double) MAX_PHASE;
            case OPPONENT_HEAVY -> Math.min(1.0,
                    heavyMaterial(board, defender ^ 1) / (double) MAX_HEAVY_PER_SIDE);
            case OPPONENT_NON_PAWN -> Math.min(1.0,
                    nonPawnMaterial(board, defender ^ 1) / (double) MAX_NON_PAWN_PER_SIDE);
        };
    }

    /**
     * Rooks and queens of {@code color}, weighted 2 and 4 as in the phase computation.
     *
     * <p>Clamped by the caller rather than here: promotions can put more than one queen on the
     * board, and an uncapped multiplier would scale the term past its fitted range.
     */
    private static int heavyMaterial(Board board, int color) {
        final byte rook = color == 0 ? Board.whiteRook : Board.blackRook;
        final byte queen = color == 0 ? Board.whiteQueen : Board.blackQueen;
        final byte[] squares = board.getRawBoard();
        int sum = 0;

        for (int rank = 0; rank < FILES; rank++) {
            for (int file = 0; file < FILES; file++) {
                byte piece = squares[Board.a1 + rank * Board.LENGTH + file];

                if (piece == rook) {
                    sum += 2;
                } else if (piece == queen) {
                    sum += 4;
                }
            }
        }

        return sum;
    }

    /** All non-pawn material of {@code color}, weighted 1/1/2/4 as in the phase computation. */
    private static int nonPawnMaterial(Board board, int color) {
        final byte[] squares = board.getRawBoard();
        final byte knight = color == 0 ? Board.whiteKnight : Board.blackKnight;
        final byte bishop = color == 0 ? Board.whiteBishop : Board.blackBishop;
        int sum = heavyMaterial(board, color);

        for (int rank = 0; rank < FILES; rank++) {
            for (int file = 0; file < FILES; file++) {
                byte piece = squares[Board.a1 + rank * Board.LENGTH + file];

                if (piece == knight || piece == bishop) {
                    sum++;
                }
            }
        }

        return sum;
    }

    private static double factor() {
        String[] names = WeightingFunction.TUNABLE_FACTOR_NAMES;

        for (int i = 0; i < names.length; i++) {
            if (FACTOR.equals(names[i])) {
                return WeightingFunction.tunableFactorValues()[i];
            }
        }

        throw new IllegalStateException(FACTOR + " is not in TUNABLE_FACTOR_NAMES");
    }

    /**
     * How far the linear model is from the real evaluation, over the first {@code limit} usable
     * positions. Reports the worst and mean absolute residual in centipawns.
     *
     * <p>Exists because the linearity claim carries the whole method: if it were wrong, the tuner
     * would optimise a model of the evaluation rather than the evaluation.
     *
     * <p><b>It perturbs the table and re-evaluates.</b> The obvious check — rebuild
     * {@code baseEval + sum(features * table)} and compare against the eval — is a tautology,
     * because {@code baseEval} was <em>defined</em> as the eval minus that same sum. It returns
     * exactly zero for any position and any bug. So instead one table entry is moved by a known
     * amount, the position is evaluated again, and the measured change in the evaluation is
     * compared against the change the model predicts. That can fail, which is the point.
     *
     * <p>{@code KING_LINE_PENALTY} is a {@code static final int[]} — the reference is final, the
     * contents are not — so the entry is restored before returning.
     *
     * @return {@code {maxAbsResidual, meanAbsResidual, count}}
     */
    public static double[] verifyLinearity(Path epd, int limit) {
        var evaluator = new WeightingFunction();
        final int perturbedIndex = 1;
        final int delta = 50;
        double worst = 0;
        double sum = 0;
        int count = 0;

        try (Stream<String> lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (count >= limit) {
                    break;
                }

                int tagIndex = line.indexOf(RESULT_TAG);

                if (tagIndex < 0) {
                    continue;
                }

                Board board;

                try {
                    board = Fen.importFEN(line.substring(0, tagIndex).trim() + EPD_COUNTER_SUFFIX);
                } catch (IllegalArgumentException _) {
                    continue;
                }

                int before = evaluator.calculate(board);

                if (WeightingFunction.isIllegalWeight(before)) {
                    continue;
                }

                // The design row for this position, read from the same evaluation.
                double predicted =
                        featuresOf(evaluator, board, Scaling.PHASE)[perturbedIndex - 1] * delta;
                final int original = WeightingFunction.KING_LINE_PENALTY[perturbedIndex];
                int after;

                try {
                    WeightingFunction.KING_LINE_PENALTY[perturbedIndex] = original + delta;
                    after = evaluator.calculate(board);
                } finally {
                    WeightingFunction.KING_LINE_PENALTY[perturbedIndex] = original;
                }

                double residual = Math.abs((after - before) - predicted);
                worst = Math.max(worst, residual);
                sum += residual;
                count++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + epd, e);
        }

        return new double[] {worst, count == 0 ? 0 : sum / count, count};
    }

    private static double parseResult(String tag) {
        int open = tag.indexOf('"');
        int close = tag.indexOf('"', open + 1);

        if (open < 0 || close < 0) {
            return Double.NaN;
        }

        return switch (tag.substring(open + 1, close)) {
            case "1-0" -> 1.0;
            case "0-1" -> 0.0;
            case "1/2-1/2" -> 0.5;
            default -> Double.NaN;
        };
    }
}
