package org.michaelfl.mychess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Characterizes the error the material-only shortcut introduces, and derives the margin a
 * lazy-evaluation replacement would need.
 *
 * <p>{@code QuiescenceSearch.calculatePositionWeight} returns {@code materialWeight} instead of
 * the full evaluation whenever {@code |materialDelta|} exceeds
 * {@link org.michaelfl.mychess.engines.PositionSearch#EVALUATE_MATERIAL_ONLY_THRESHOLD}. The
 * quantity measured here is
 *
 * <pre>{@code   diff = (evaluation(board) - materialWeight(board)) * weightFactor }</pre>
 *
 * — the positional component, from the side to move's perspective. That is <b>exactly the error
 * the shortcut introduces when it fires</b>, so its distribution answers two questions at once:
 *
 * <ul>
 *   <li><b>Is there anything left here?</b> If {@code diff} is usually small, the shortcut
 *       discards little information and the -34 Elo measured for removing it was almost entirely
 *       the depth its cheapness buys. If {@code diff} is often large, today's coarse gate throws
 *       away a lot, and replacing the gate is worth trying.</li>
 *   <li><b>What margin does lazy evaluation need?</b> A lazy cutoff {@code material - MARGIN >=
 *       beta} is only sound if {@code material - MARGIN} is a lower bound on the true evaluation,
 *       so {@code MARGIN} must bound how far the positional terms pull the evaluation
 *       <i>below</i> material. That is the <b>negative</b> tail of {@code diff}. The positive tail
 *       is a different number serving the fail-low side; the two are reported separately, because
 *       conflating them into {@code |diff|} would size both bounds by the worse of the two.</li>
 * </ul>
 *
 * <p><b>Three decompositions are reported</b>, because the answer depends entirely on what the
 * "cheap part" is taken to be — and getting that wrong once already produced a wrong conclusion:
 *
 * <ul>
 *   <li><b>A</b> — full evaluation minus <i>material alone</i>. This is what today's gate
 *       discards.</li>
 *   <li><b>B</b> — minus <i>material and the tapered PST</i>. This is what textbook lazy
 *       evaluation discards, since the PST is a table sum in a piece loop that runs anyway.</li>
 *   <li><b>C</b> — minus <i>every cheap term</i> (material, PST, castling state, doubled pawns,
 *       bishop pair), leaving only mobility, threats, the check count and undefended pieces.</li>
 * </ul>
 *
 * <p>The decomposition goes through {@link WeightingFunction#analyzeFactors} rather than a formula
 * re-implemented here, because the evaluation is linear in its factors: every term's contribution
 * is therefore computed by production code. It is cross-checked per position — the evaluation minus
 * every factored term must equal {@link WeightingFunction#calculateMaterialWeight} — and the run
 * aborts rather than reporting numbers if that ever fails.
 *
 * <p><b>Two limits on what this can claim.</b> The positions come from an EPD corpus, so they are
 * root-like — whereas the shortcut fires at quiescence leaves, reached after a capture sequence
 * and therefore often materially lopsided. The faithful version instruments the search itself and
 * samples where the gate actually opens; it is deliberately not done here, because it needs a
 * search run and would contend for cores with a time-controlled SPRT. And {@code diff} is the
 * error in the returned <i>score</i>, not in Elo: a large error at a node whose value never
 * propagates costs nothing.
 *
 * <p>Usage — a measurement driver, not a test:
 * <pre>{@code
 * mvn -q test-compile exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=org.michaelfl.mychess.MaterialShortcutMarginAnalysis \
 *     -Dexec.args="tuning-data/hybrid.epd 200000"
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class MaterialShortcutMarginAnalysis {

    private static final String DEFAULT_EPD = "tuning-data/hybrid.epd";
    private static final int DEFAULT_LIMIT = 200_000;

    /**
     * Indices into {@link WeightingFunction#TUNABLE_FACTOR_NAMES} whose terms are <b>expensive</b>
     * — they need per-piece move generation or attack detection rather than a table lookup in the
     * piece loop that runs anyway.
     *
     * <p>mobility (1) and threats (2) walk each piece's moves; the check count (4) and the
     * undefended-pieces count (6) need attack detection. The remaining four — tapered PST (0),
     * castling state (3), doubled pawns (5) and the bishop pair (7) — fall out of counters the
     * evaluation already maintains.
     *
     * <p><b>This is a claim about cost, and it is not measured here.</b> Whether skipping these
     * terms actually saves wall clock depends on how much of their work is shared with the piece
     * loop; that needs its own timing measurement. What is measured here is only how much
     * <i>accuracy</i> they carry.
     */
    private static final int[] EXPENSIVE_FACTORS = {1, 2, 4, 6};

    /** Index of the tapered piece-square-table factor. */
    private static final int PST_FACTOR = 0;

    /**
     * Factors a cheap pass could produce <b>without restructuring</b> {@code WeightingFunction}:
     * the tapered PST (0), summed in the main piece loop, and the castling state (3), which comes
     * from its own call.
     *
     * <p>Doubled pawns (5) and the bishop pair (7) are cheap <i>in principle</i> but are
     * accumulated <b>inside</b> the per-piece calculation functions — {@code doublePawnCount} in
     * the pawn routine, {@code bishopCount} in the bishop routine — so a pass that skips the piece
     * walk loses them too. That is why row C of this report is not achievable today and row B2 is:
     * B2 is the honest cheap part, C is what a small refactor would unlock.
     */
    private static final int[] CHEAP_WITHOUT_REFACTOR = {0, 3};

    /** Percentiles reported for the signed distribution. */
    private static final double[] PERCENTILES = {0.1, 1, 5, 25, 50, 75, 95, 99, 99.9};

    /** Thresholds the sweep is exploring, so the table connects directly to it. */
    private static final int[] GATES = {50, 100, 150, 200, 300, 500};

    private MaterialShortcutMarginAnalysis() {
        // measurement driver
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : DEFAULT_EPD);
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_LIMIT;

        System.out.printf(Locale.ROOT, "corpus=%s limit=%,d%n", epd, limit);

        int[][] residuals = collect(epd, limit);

        System.out.println("=== A: full evaluation - material only ".repeat(1)
                + "(what today's shortcut discards) ===");
        report(residuals[0]);

        System.out.println();
        System.out.println("=== B: full evaluation - (material + tapered PST) "
                + "(what textbook lazy evaluation would discard) ===");
        report(residuals[1]);

        System.out.println();
        System.out.println("=== B2: full evaluation - (material + PST + castling) "
                + "(the cheap part achievable WITHOUT restructuring) ===");
        report(residuals[2]);

        System.out.println();
        System.out.println("=== C: full evaluation - every cheap term "
                + "(what a small refactor would unlock) ===");
        report(residuals[3]);

        System.out.println();
        System.out.println("=== work done per evaluation (counts, not times) ===");
        System.out.println(EvalWorkCounters.report());
    }

    /**
     * Reads positions and returns the side-to-move-relative positional component of each.
     *
     * @param epd   EPD or FEN-per-line file; anything after the first four FEN fields is ignored
     * @param limit maximum number of positions to read
     * @return one entry per parsed position, in file order
     */
    private static int[][] collect(Path epd, int limit) {
        var evaluator = new WeightingFunction();
        double[] factors = WeightingFunction.tunableFactorValues();

        var withoutMaterialOnly = new int[limit];
        var withoutMaterialAndPst = new int[limit];
        var withoutCheapAchievable = new int[limit];
        var expensiveOnly = new int[limit];

        int count = 0;
        int rejected = 0;
        int crossCheckFailures = 0;

        // Reports "no evaluations counted" unless WeightingFunction.COUNT_EVAL_WORK was flipped
        // and the build redone — which is the honest answer for a normal build rather than a
        // silent zero.
        EvalWorkCounters.reset();

        try (var lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && count < limit) {
                Board board = toBoard(iterator.next());

                if (board == null) {
                    rejected++;
                    continue;
                }

                int weightFactor = board.getGameStatus().getTurn() == GameStatus.TURN_WHITE ? 1 : -1;

                // The evaluation is linear in the factors, so every term's contribution comes
                // from production code rather than from a formula re-implemented here — which is
                // the whole reason for going through analyzeFactors.
                var breakdown = evaluator.analyzeFactors(board);
                double all = weighted(breakdown, factors, 0, factors.length);
                double pst = weighted(breakdown, factors, PST_FACTOR, PST_FACTOR + 1);
                double expensive = 0;

                for (int index : EXPENSIVE_FACTORS) {
                    expensive += breakdown.features()[index] * factors[index];
                }

                double cheapAchievable = 0;

                for (int index : CHEAP_WITHOUT_REFACTOR) {
                    cheapAchievable += breakdown.features()[index] * factors[index];
                }

                // Cross-check: eval minus every factored term must be the material part, which
                // calculateMaterialWeight computes independently. A mismatch beyond rounding
                // means the factor list and the evaluation have drifted apart.
                if (Math.abs(breakdown.eval() - all - WeightingFunction.calculateMaterialWeight(board)) > 2.0) {
                    crossCheckFailures++;
                }

                withoutMaterialOnly[count] = (int) Math.round(all) * weightFactor;
                withoutMaterialAndPst[count] = (int) Math.round(all - pst) * weightFactor;
                withoutCheapAchievable[count] = (int) Math.round(all - cheapAchievable) * weightFactor;
                expensiveOnly[count] = (int) Math.round(expensive) * weightFactor;
                count++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + epd, e);
        }

        System.out.printf(Locale.ROOT, "parsed=%,d rejected=%,d cross-check failures=%,d%n%n",
                count, rejected, crossCheckFailures);

        if (crossCheckFailures > 0) {
            throw new IllegalStateException("the factor decomposition does not reproduce the "
                    + "evaluation on " + crossCheckFailures + " positions — the factor list and "
                    + "WeightingFunction have drifted apart, so every number below would be wrong");
        }

        return new int[][]{
                Arrays.copyOf(withoutMaterialOnly, count),
                Arrays.copyOf(withoutMaterialAndPst, count),
                Arrays.copyOf(withoutCheapAchievable, count),
                Arrays.copyOf(expensiveOnly, count)
        };
    }

    /** Sum of {@code features[i] * factors[i]} over {@code [from, to)}. */
    private static double weighted(WeightingFunction.FactorBreakdown breakdown, double[] factors, int from, int to) {
        double sum = 0;

        for (int i = from; i < to; i++) {
            sum += breakdown.features()[i] * factors[i];
        }

        return sum;
    }

    /**
     * Turns one corpus line into a board, or {@code null} when it cannot be parsed.
     *
     * <p>EPD carries four FEN fields plus opcodes; {@code Fen.importFEN} wants six, so the
     * halfmove/fullmove counters are appended. Positions are never imported as Chess960 — every
     * corpus here is classical, and the 960 path would read the castling field differently.
     */
    private static Board toBoard(String line) {
        String trimmed = line.strip();

        if (trimmed.isEmpty()) {
            return null;
        }

        String[] parts = trimmed.split("\\s+");

        if (parts.length < 4) {
            return null;
        }

        String fen = parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3] + " 0 1";

        try {
            return Fen.importFEN(fen);
        } catch (RuntimeException ignore) {
            // A corpus line the importer rejects is not a measurement error — it is counted and
            // skipped, and the count is printed so a corrupt corpus cannot pass unnoticed.
            return null;
        }
    }

    private static void report(int[] diffs) {
        if (diffs.length == 0) {
            System.out.println("no positions parsed — nothing to report");
            return;
        }

        int[] sorted = diffs.clone();
        Arrays.sort(sorted);

        long sum = 0;
        for (int d : diffs) {
            sum += d;
        }
        double mean = (double) sum / diffs.length;

        double variance = 0;
        for (int d : diffs) {
            variance += (d - mean) * (d - mean);
        }
        double stdDev = Math.sqrt(variance / diffs.length);

        System.out.printf(Locale.ROOT, "n=%,d  mean=%+.1f cp  sd=%.1f cp  min=%+d  max=%+d%n%n",
                diffs.length, mean, stdDev, sorted[0], sorted[sorted.length - 1]);

        System.out.println("signed distribution of (eval - material), side to move's view:");
        for (double p : PERCENTILES) {
            System.out.printf(Locale.ROOT, "  p%-5s %+6d cp%n", trim(p), percentile(sorted, p));
        }

        System.out.printf(Locale.ROOT, "%n%-8s %14s %14s   %s%n",
                "bound", "eval below", "eval above", "interpretation");
        System.out.printf(Locale.ROOT, "%-8s %14s %14s%n", "", "material by >", "material by >", "");

        for (int gate : GATES) {
            long below = count(diffs, -gate, true);
            long above = count(diffs, gate, false);

            System.out.printf(Locale.ROOT, "  %-6d %13.2f %% %13.2f %%%n",
                    gate, 100.0 * below / diffs.length, 100.0 * above / diffs.length);
        }

        System.out.printf(Locale.ROOT, "%nMARGIN candidates — the bound a sound lazy cutoff needs:%n");
        for (double p : new double[]{99, 99.9, 99.99}) {
            int m = -percentile(sorted, 100 - p);
            System.out.printf(Locale.ROOT, "  covers %-6s of positions: MARGIN = %d cp%n", trim(p) + " %", Math.max(m, 0));
        }
        System.out.printf(Locale.ROOT, "  covers every position:      MARGIN = %d cp%n", Math.max(-sorted[0], 0));
    }

    private static long count(int[] diffs, int bound, boolean below) {
        long n = 0;

        for (int d : diffs) {
            if (below ? d < bound : d > bound) {
                n++;
            }
        }

        return n;
    }

    private static int percentile(int[] sorted, double p) {
        int index = (int) Math.round(p / 100.0 * (sorted.length - 1));

        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static String trim(double p) {
        return p == Math.floor(p) ? String.valueOf((long) p) : String.valueOf(p);
    }
}
