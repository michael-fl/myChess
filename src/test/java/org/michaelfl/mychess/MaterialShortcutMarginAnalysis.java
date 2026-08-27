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

        int[] diffs = collect(epd, limit);

        report(diffs);
    }

    /**
     * Reads positions and returns the side-to-move-relative positional component of each.
     *
     * @param epd   EPD or FEN-per-line file; anything after the first four FEN fields is ignored
     * @param limit maximum number of positions to read
     * @return one entry per parsed position, in file order
     */
    private static int[] collect(Path epd, int limit) {
        var evaluator = new WeightingFunction();
        var diffs = new int[limit];
        int count = 0;
        int rejected = 0;

        try (var lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && count < limit) {
                Board board = toBoard(iterator.next());

                if (board == null) {
                    rejected++;
                    continue;
                }

                int weightFactor = board.getGameStatus().getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
                int positional = evaluator.calculate(board) - WeightingFunction.calculateMaterialWeight(board);

                diffs[count++] = positional * weightFactor;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + epd, e);
        }

        System.out.printf(Locale.ROOT, "parsed=%,d rejected=%,d%n%n", count, rejected);

        return Arrays.copyOf(diffs, count);
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
