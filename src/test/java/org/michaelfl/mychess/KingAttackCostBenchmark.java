package org.michaelfl.mychess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What a king-attack term would cost per evaluation, measured before it is built.
 *
 * <p>The project rule for anything in the search or the evaluation is that it stays on a branch
 * until a measurement says it is at worst free ({@code CLAUDE.md}, "Hot-path production code").
 * This produces that number ahead of the implementation, so the decision to build is not also a
 * decision to find out afterwards.
 *
 * <p><b>Why the number decides more than it looks.</b> The Audax fork's version of this term
 * cost <b>1.17× per node</b>, which under a clock became 0.68 plies of search depth and — with
 * the raised material threshold it shipped alongside — a swing from +31.9 Elo at fixed depth to
 * −46.5 under the clock. In this engine evaluation time converts into plies, and plies are what
 * tracks Elo. A term worth 40 cp that costs 10 % of evaluation time is not obviously a gain.
 *
 * <p>Two costs are separated, because they are paid differently:
 *
 * <ul>
 *   <li><b>The zone scan</b> — {@link KingAttackUnits}, run for both colors. This is the honest
 *       upper bound: a standalone implementation that walks the rays itself.</li>
 *   <li><b>The evaluation it would sit inside</b> — {@link WeightingFunction#calculate}, for
 *       the ratio that matters.</li>
 * </ul>
 *
 * <p><b>The upper bound is deliberate and should be read as one.</b> On branch
 * {@code attack-units} the units fall out of the evaluation's existing per-piece scan, which
 * already visits every piece and every ray; the marginal cost there is an array write and a
 * comparison, far below what this measures. What this bounds is the worst case — and if even
 * the worst case is small, the question is closed without building anything.
 *
 * <p>Methodology follows {@link EvalThroughputBenchmark}: warm loop, warm-up passes discarded,
 * best-of-N with the spread printed, results consumed into a checksum so the JIT cannot delete
 * the calls.
 *
 * <pre>{@code
 * java -cp target/classes:target/test-classes:target/dependency/* \
 *     org.michaelfl.mychess.KingAttackCostBenchmark tuning-data/hybrid.epd 50000 9
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class KingAttackCostBenchmark {

    private static final String DEFAULT_EPD = "tuning-data/hybrid.epd";
    private static final int DEFAULT_POSITIONS = 50_000;
    private static final int DEFAULT_REPETITIONS = 9;
    private static final int WARMUP_PASSES = 3;
    private static final long NANOS_PER_MS = 1_000_000L;

    private KingAttackCostBenchmark() {
        // measurement driver
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : DEFAULT_EPD);
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_POSITIONS;
        int repetitions = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_REPETITIONS;

        List<Board> boards = load(epd, limit);
        var evaluator = new WeightingFunction();

        System.out.printf(Locale.ROOT, "corpus=%s positions=%,d repetitions=%d warmup=%d%n%n",
                epd, boards.size(), repetitions, WARMUP_PASSES);

        for (int i = 0; i < WARMUP_PASSES; i++) {
            evalPass(evaluator, boards);
            unitsPass(boards);
        }

        var evalNanos = new long[repetitions];
        var unitsNanos = new long[repetitions];
        long evalChecksum = 0;
        long unitsChecksum = 0;

        // Interleaved, so a drift in machine load disturbs both equally.
        for (int i = 0; i < repetitions; i++) {
            long start = System.nanoTime();
            evalChecksum = evalPass(evaluator, boards);
            evalNanos[i] = System.nanoTime() - start;

            start = System.nanoTime();
            unitsChecksum = unitsPass(boards);
            unitsNanos[i] = System.nanoTime() - start;
        }

        report(evalNanos, unitsNanos, boards.size(), evalChecksum, unitsChecksum);
    }

    private static long evalPass(WeightingFunction evaluator, List<Board> boards) {
        long checksum = 0;

        for (Board board : boards) {
            checksum += evaluator.calculate(board);
        }

        return checksum;
    }

    /** Both colors, as the evaluation would need them. */
    private static long unitsPass(List<Board> boards) {
        long checksum = 0;

        for (Board board : boards) {
            checksum += KingAttackUnits.of(board, GameStatus.TURN_WHITE);
            checksum += KingAttackUnits.of(board, GameStatus.TURN_BLACK);
        }

        return checksum;
    }

    private static void report(long[] evalNanos, long[] unitsNanos, int positions,
                               long evalChecksum, long unitsChecksum) {
        double evalPer = (double) min(evalNanos) / positions;
        double unitsPer = (double) min(unitsNanos) / positions;

        System.out.printf(Locale.ROOT, "checksums: eval=%d units=%d%n%n", evalChecksum, unitsChecksum);

        printSeries("evaluation        ", evalNanos, positions);
        printSeries("zone scan, 2 sides", unitsNanos, positions);

        System.out.printf(Locale.ROOT, "%nBEST  evaluation %7.1f ns   zone scan %7.1f ns%n",
                evalPer, unitsPer);
        System.out.printf(Locale.ROOT, "the scan adds %+.1f %% to an evaluation%n",
                100.0 * unitsPer / evalPer);
        System.out.printf(Locale.ROOT,
                "%nThat is the standalone upper bound. Inside WeightingFunction's existing "
                        + "per-piece scan the marginal cost is a comparison and an array write, so "
                        + "read this as the ceiling rather than the estimate. Compare against the "
                        + "Audax fork's measured 1.17x per node for its version of the term.%n");
    }

    private static void printSeries(String label, long[] nanos, int positions) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);

        long best = sorted[0];
        long worst = sorted[sorted.length - 1];

        System.out.printf(Locale.ROOT, "%s best %,7d ms  %7.1f ns   worst %,7d ms  (spread %+.1f %%)%n",
                label, best / NANOS_PER_MS, (double) best / positions, worst / NANOS_PER_MS,
                100.0 * (worst - best) / best);
    }

    private static long min(long[] values) {
        long best = values[0];

        for (long value : values) {
            if (value < best) {
                best = value;
            }
        }

        return best;
    }

    private static List<Board> load(Path epd, int limit) {
        var boards = new ArrayList<Board>(limit);

        try (var lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && boards.size() < limit) {
                String[] parts = iterator.next().strip().split("\\s+");

                if (parts.length >= 4) {
                    try {
                        boards.add(Fen.importFEN(parts[0] + " " + parts[1] + " " + parts[2] + " "
                                + parts[3] + " 0 1"));
                    } catch (RuntimeException ignore) {
                        // A corpus line the importer rejects is skipped; the printed count shows
                        // how many positions made it into the measurement.
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + epd, e);
        }

        return boards;
    }
}
