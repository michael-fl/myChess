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
 * Turns an EPD corpus into {@link Sample}s for fitting the king-attack curve.
 *
 * <p><b>Why a fit and not a measurement.</b> An earlier attempt read the curve straight out of
 * the data: take positions where the opponent has zero attack units, group by own units, and the
 * mean game result gives the table entry directly. The algebra is right and the result was
 * useless — a single pawn bearing on the zone measured +47 cp while a single queen measured
 * +14. Selecting on "the opponent has nothing" sorts positions by *which piece* is attacking,
 * not by how much pressure there is, so the index measured its own selection. Details in
 * `docs/king-safety.md`. A joint fit avoids that: every position contributes, and the piece-type
 * mix averages out instead of choosing the sample.
 *
 * <p><b>The feature is linear, which is what makes this fittable at all.</b> The term enters the
 * evaluation as {@code f(unitsWhite) − f(unitsBlack)}, so with the table entries as parameters
 * the derivative is a vector of +1 and −1 — exactly the shape {@link TexelTuner} expects.
 *
 * <p><b>Index 0 is pinned at zero and is not a parameter.</b> Only the difference reaches the
 * score, so adding a constant to every entry is an exact null direction: the fit would wander
 * along it without improving anything. Pinning one entry removes it.
 *
 * <p><b>The table stops at {@link #MAX_UNITS}.</b> Measured over `hybrid.epd`, indices 0–8 carry
 * **99.7 %** of all king samples and everything from 9 upward together carries 0.3 %. Entries
 * above that cannot be fitted from this data — and, more to the point, the engine would almost
 * never ask for them. Units beyond the cap are clamped onto it.
 *
 * @author Michael Fleischhauer
 */
final class KingAttackTexelData {

    /** Highest table index that real play asks for often enough to fit. */
    static final int MAX_UNITS = 8;

    /** Free parameters: one per index from 1 to {@link #MAX_UNITS}; index 0 is pinned at 0. */
    static final int PARAMETER_COUNT = MAX_UNITS;

    private static final String RESULT_TAG = " c9 ";
    private static final String COUNTER_SUFFIX = " 0 1";

    private KingAttackTexelData() {
        // data adapter
    }

    /** Keeps every position; the value used when no material window is requested. */
    static final int NO_MATERIAL_WINDOW = Integer.MAX_VALUE;

    /**
     * Reads positions and builds one sample each.
     *
     * @param epd   an EPD file in the {@code <FEN> c9 "<result>";} form of the Zurichess set
     * @param limit maximum positions to read, or 0 for all
     * @return the samples, skipping lines that do not parse
     */
    static List<Sample> load(Path epd, int limit) {
        return load(epd, limit, NO_MATERIAL_WINDOW);
    }

    /**
     * As {@link #load(Path, int)}, but keeping only positions whose material is within
     * {@code materialWindow} centipawns of equal.
     *
     * <p>Exists to test a confound rather than to produce a better fit. The corpora disagree on
     * the sign of the low table entries, and they also differ sharply in composition: only
     * 25.1 % of `hybrid`'s positions are material-balanced against 46–68 % elsewhere
     * (§ 4.3). In a corpus full of materially decided positions, "bears on the enemy king" may
     * simply correlate with being the side that is worse and has to attack. Fitting both corpora
     * under the same material window asks whether the disagreement survives that.
     */
    static List<Sample> load(Path epd, int limit, int materialWindow) {
        var samples = new ArrayList<Sample>();
        var evaluator = new WeightingFunction();

        try (var lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && (limit <= 0 || samples.size() < limit)) {
                Sample sample = sampleOf(iterator.next(), evaluator, materialWindow);

                if (sample != null) {
                    samples.add(sample);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + epd, e);
        }

        return samples;
    }

    /**
     * The boards behind {@link #load}, in the same order and with the same lines skipped.
     *
     * <p>Exists so the cross-check against {@code tools/king-attack-curve.py} compares like with
     * like: any difference in which lines are skipped would misalign the two outputs and read as
     * a disagreement about units.
     *
     * @param epd   the corpus
     * @param limit maximum positions, or 0 for all
     * @return one board per usable line
     */
    static List<Board> loadBoards(Path epd, int limit) {
        var boards = new ArrayList<Board>();
        var evaluator = new WeightingFunction();

        try (var lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && (limit <= 0 || boards.size() < limit)) {
                Board board = boardOf(iterator.next(), evaluator);

                if (board != null) {
                    boards.add(board);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + epd, e);
        }

        return boards;
    }

    private static Sample sampleOf(String line, WeightingFunction evaluator, int materialWindow) {
        int tag = line.indexOf(RESULT_TAG);

        if (tag < 0) {
            return null;
        }

        Double result = resultOf(line.substring(tag + RESULT_TAG.length()));

        if (result == null) {
            return null;
        }

        Board board;

        try {
            board = Fen.importFEN(line.substring(0, tag).trim() + COUNTER_SUFFIX);
        } catch (RuntimeException ignore) {
            // A corpus line the importer rejects contributes nothing; the caller reports how
            // many samples were built, which is where a broken file would show up.
            return null;
        }

        if (materialWindow != NO_MATERIAL_WINDOW
                && Math.abs(WeightingFunction.calculateMaterialWeight(board)) > materialWindow) {
            return null;
        }

        int baseEval = evaluator.calculate(board);

        if (WeightingFunction.isIllegalWeight(baseEval)) {
            return null; // an illegal position has no meaningful score to fit against
        }

        return new Sample(baseEval, featuresOf(board, evaluator.getPhase()), result);
    }

    /** The board of a usable line, applying exactly the filters {@link #sampleOf} applies. */
    private static Board boardOf(String line, WeightingFunction evaluator) {
        int tag = line.indexOf(RESULT_TAG);

        if (tag < 0 || resultOf(line.substring(tag + RESULT_TAG.length())) == null) {
            return null;
        }

        Board board;

        try {
            board = Fen.importFEN(line.substring(0, tag).trim() + COUNTER_SUFFIX);
        } catch (RuntimeException ignore) {
            // Same skip as sampleOf; see there.
            return null;
        }

        return WeightingFunction.isIllegalWeight(evaluator.calculate(board)) ? null : board;
    }

    /** Full midgame material, matching {@code WeightingFunction.MAX_PHASE}. */
    static final int MAX_PHASE = 24;

    /**
     * The feature vector: {@code ±phase} at each side's index — {@code +} for white.
     *
     * <p><b>Scaled by the game phase, and that is the point.</b> A first version of this class
     * pooled all phases, which is the same mistake `king-safety-signal.py` made before it was
     * split: § 4.2 measured the effect *inverting* toward the endgame (−34 cp per attacker in
     * the midgame against +12 in the endgame), so a pooled fit averages two opposite effects and
     * reports something smaller than either. Weighting each sample by {@code phase / MAX_PHASE}
     * makes endgame positions contribute almost nothing, and what comes out is the **midgame**
     * table — which is exactly what the implementation stores, since the term is to be
     * multiplied by the phase at evaluation time.
     *
     * <p>Fitting the feature the implementation will actually compute is the whole idea. A
     * curve fitted phase-blind and then used phase-scaled would be neither the fitted nor the
     * measured thing.
     *
     * @param board the position
     * @param phase the game phase, 0 (bare kings) to {@link #MAX_PHASE}
     * @return a vector of length {@link #PARAMETER_COUNT}
     */
    static double[] featuresOf(Board board, int phase) {
        var features = new double[PARAMETER_COUNT];
        double scale = (double) phase / MAX_PHASE;

        add(features, KingAttackUnits.of(board, GameStatus.TURN_WHITE), +scale);
        add(features, KingAttackUnits.of(board, GameStatus.TURN_BLACK), -scale);

        return features;
    }

    private static void add(double[] features, int units, double weight) {
        if (units <= 0) {
            return; // index 0 is pinned at zero and has no parameter
        }

        features[Math.min(units, MAX_UNITS) - 1] += weight;
    }

    private static Double resultOf(String tail) {
        int open = tail.indexOf('"');
        int close = open < 0 ? -1 : tail.indexOf('"', open + 1);

        if (close < 0) {
            return null;
        }

        return switch (tail.substring(open + 1, close)) {
            case "1-0" -> 1.0;
            case "1/2-1/2" -> 0.5;
            case "0-1" -> 0.0;
            default -> null;
        };
    }
}
