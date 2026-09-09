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
 * Turns an EPD corpus into {@link Sample}s for fitting the four castling-state values.
 *
 * <p>The term is the last hand-written number in the evaluation. `castlingFactor = 0.25f` dates
 * back to {@code dabec30}, the original implementation, and the level progression
 * {@code 0 / −1 / −2 / −4} to {@code 2cd229a}; neither has ever been tuned or swept. Effectively
 * the four states are worth <b>0 / −25 / −50 / −100 cp</b>, chosen by intuition.
 *
 * <p><b>Why fit the levels and not the factor.</b> The factor can only stretch the four values
 * together — it turns {@code 0/−25/−50/−100} into {@code 0/−20/−40/−80} and leaves the *ratios*
 * alone. The ratios are the untested claim: that one right lost is half as bad as both, and both
 * twice as bad as one. Fitting the levels can say something a factor cannot, for instance that
 * holding both rights is barely a disadvantage while losing them is a disaster. King-safety § 4.1
 * records what happens when only the scale is checked: the shelved king-line table was
 * near-optimally scaled — a sweep put the optimum within 0.07 % MSE of the shipped value — and
 * still lost 29 Elo, because the error was in the shape.
 *
 * <p><b>The feature is linear, which is what makes this fittable.</b> The term enters the
 * evaluation as {@code LEVEL[stateWhite] − LEVEL[stateBlack]}, so with the level values as
 * parameters the derivative is a vector of +1, −1 and 0 — exactly what
 * {@link org.michaelfl.mychess.tuning.TexelTuner} expects.
 *
 * <p><b>The castled state is pinned at zero and is not a parameter.</b> Only the difference
 * reaches the score, so adding a constant to all four levels is an exact null direction and the
 * fit would wander along it. Three free parameters remain.
 *
 * <p><b>Occupancy, measured over the 2000-game anchor bracket</b> (217,692 positions, 435,384
 * king states): castled 63.05 %, both rights 17.73 %, <b>one right 1.83 %</b>, no rights 17.39 %.
 * Three of the four are solid; the one-right level is thin and its fitted value should be read
 * with that in mind. It is an order of magnitude better than the king-line table's worst entries
 * at 0.02 % and 0.00 %, which is the standard this project set for "a coefficient with no
 * occupancy behind it means nothing" — but it is the weakest of the four and the one to check
 * against its neighbours for monotonicity rather than to trust on its own.
 *
 * <p><b>The base evaluation has the term removed, which the king-attack adapter did not have to
 * do.</b> That curve does not exist on master, so its full evaluation was already the base. Here
 * the term is live, so {@link #baseEvalWithoutCastling} subtracts its current contribution. The
 * subtraction happens after {@code roundSymmetric}, so it can be off by one centipawn where the
 * remaining sum lands exactly on a rounding boundary and the sign flips — rounding half away from
 * zero is not translation-invariant there. Against a corpus of this size and a sigmoid that maps
 * centipawns to expected score, one centipawn on a fraction of the positions is far below the
 * resolution of anything this fit can claim.
 *
 * @author Michael Fleischhauer
 */
final class CastlingStateTexelData {

    /** Level indices: the array position each castling state maps to. */
    static final int STATE_CASTLED = 0;
    static final int STATE_BOTH_RIGHTS = 1;
    static final int STATE_ONE_RIGHT = 2;
    static final int STATE_NO_RIGHTS = 3;

    static final int LEVEL_COUNT = 4;

    /** Free parameters: levels 1..3; {@link #STATE_CASTLED} is pinned at 0. */
    static final int PARAMETER_COUNT = LEVEL_COUNT - 1;

    /** The shipped values in centipawns, for the initial guess and for comparison. */
    static final double[] SHIPPED_LEVELS = { 0, -25, -50, -100 };

    private static final String RESULT_TAG = " c9 ";
    private static final String COUNTER_SUFFIX = " 0 1";

    /** Centipawns per unit of the shipped state scale: {@code castlingFactor * 100}. */
    private static final int SHIPPED_CP_PER_UNIT = 25;

    private CastlingStateTexelData() {
        // data adapter
    }

    /** The state index of one color, matching {@code WeightingFunction.calculateCastlingState}. */
    static int stateOf(GameStatus status, boolean white) {
        if (white ? status.hasWhiteCastled() : status.hasBlackCastled()) {
            return STATE_CASTLED;
        }

        boolean kingSide = white
                ? status.isWhiteCastlingKingSidePossible()
                : status.isBlackCastlingKingSidePossible();
        boolean queenSide = white
                ? status.isWhiteCastlingQueenSidePossible()
                : status.isBlackCastlingQueenSidePossible();

        if (kingSide && queenSide) {
            return STATE_BOTH_RIGHTS;
        }

        return kingSide || queenSide ? STATE_ONE_RIGHT : STATE_NO_RIGHTS;
    }

    /** The shipped scale value the state carries, {@code 0 / −1 / −2 / −4}. */
    private static int shippedUnits(int state) {
        return switch (state) {
            case STATE_CASTLED -> 0;
            case STATE_BOTH_RIGHTS -> -1;
            case STATE_ONE_RIGHT -> -2;
            default -> -4;
        };
    }

    /**
     * The evaluation with the castling term's current contribution removed, so the fit starts
     * from an evaluation that does not already contain what it is trying to place.
     */
    static int baseEvalWithoutCastling(Board board, int fullEval) {
        GameStatus status = board.getGameStatus();
        int delta = shippedUnits(stateOf(status, true)) - shippedUnits(stateOf(status, false));

        return fullEval - delta * SHIPPED_CP_PER_UNIT;
    }

    /**
     * Feature vector for the three free levels: {@code +1} where white sits in that state,
     * {@code −1} where black does, and both when they share it, which cancels to zero.
     */
    static double[] featuresOf(Board board) {
        GameStatus status = board.getGameStatus();
        int white = stateOf(status, true);
        int black = stateOf(status, false);
        var features = new double[PARAMETER_COUNT];

        if (white != STATE_CASTLED) {
            features[white - 1] += 1;
        }

        if (black != STATE_CASTLED) {
            features[black - 1] -= 1;
        }

        return features;
    }

    /**
     * Reads positions and builds one sample each.
     *
     * @param epd   an EPD file in the {@code <FEN> c9 "<result>";} form of the Zurichess set
     * @param limit maximum positions to read, or 0 for all
     * @return the samples, skipping lines that do not parse
     */
    static List<Sample> load(Path epd, int limit) {
        var samples = new ArrayList<Sample>();
        var evaluator = new WeightingFunction();

        try (var lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && (limit <= 0 || samples.size() < limit)) {
                Sample sample = sampleOf(iterator.next(), evaluator);

                if (sample != null) {
                    samples.add(sample);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + epd, e);
        }

        return samples;
    }

    /** Occupancy of the four states over the corpus, for the report the fit prints. */
    static long[] occupancy(Path epd, int limit) {
        var counts = new long[LEVEL_COUNT];
        var evaluator = new WeightingFunction();
        long seen = 0;

        try (var lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && (limit <= 0 || seen < limit)) {
                Board board = boardOf(iterator.next());

                if (board == null) {
                    continue;
                }

                evaluator.calculate(board);

                GameStatus status = board.getGameStatus();

                counts[stateOf(status, true)]++;
                counts[stateOf(status, false)]++;
                seen++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + epd, e);
        }

        return counts;
    }

    private static Board boardOf(String line) {
        int tag = line.indexOf(RESULT_TAG);

        if (tag < 0) {
            return null;
        }

        try {
            return Fen.importFEN(line.substring(0, tag).trim() + COUNTER_SUFFIX);
        } catch (RuntimeException ignore) {
            // A corpus line the importer rejects contributes nothing; the caller reports how
            // many samples were built, which is where a broken file would show up.
            return null;
        }
    }

    private static Sample sampleOf(String line, WeightingFunction evaluator) {
        int tag = line.indexOf(RESULT_TAG);

        if (tag < 0) {
            return null;
        }

        Double result = resultOf(line.substring(tag + RESULT_TAG.length()));

        if (result == null) {
            return null;
        }

        Board board = boardOf(line);

        if (board == null) {
            return null;
        }

        int fullEval = evaluator.calculate(board);

        if (WeightingFunction.isIllegalWeight(fullEval)) {
            return null; // an illegal position has no meaningful score to fit against
        }

        return new Sample(baseEvalWithoutCastling(board, fullEval), featuresOf(board), result);
    }

    /** The {@code c9} payload as a score for white: {@code "1-0"}, {@code "1/2-1/2"}, {@code "0-1"}. */
    private static Double resultOf(String tail) {
        String value = tail.trim();

        if (value.startsWith("\"")) {
            int end = value.indexOf('"', 1);

            if (end < 0) {
                return null;
            }

            value = value.substring(1, end);
        }

        return switch (value) {
            case "1-0", "1.0" -> 1.0;
            case "1/2-1/2", "0.5" -> 0.5;
            case "0-1", "0.0" -> 0.0;
            default -> null;
        };
    }
}
