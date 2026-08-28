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
 * Times {@link WeightingFunction#calculate(Board)} in a warm in-process loop, so a change to the
 * evaluation's structure can be costed without running a full bench.
 *
 * <p><b>Why not the bench.</b> A depth-8 bench answers "did behavior change" exactly and "what did
 * it cost" badly: 19 minutes per run, 87 % of it spent inside one artificial position, and the
 * figure that comes out is a whole-search wall clock in which the evaluation's share is diluted and
 * mixed with move generation, the transposition table and the search itself. Two such runs differ by
 * 12 % told us something was wrong but not what. This measures the changed thing directly, in
 * seconds, and can be repeated enough times to see its own noise.
 *
 * <p><b>Warm, deliberately.</b> {@code roadmap-backlog.md} § 12.10.1 records that a fresh JVM per
 * search inflates the apparent cost of added per-node work — interpreter, no JIT — so overhead must
 * be read from a warmed loop. Warm-up passes run before any timing and their results are discarded.
 *
 * <p><b>Best-of, not mean.</b> Repetitions are reported with their minimum highlighted: the fastest
 * pass is the one least disturbed by whatever else the machine was doing, so it is the better
 * estimator of the code's own cost. The spread across repetitions is printed next to it, because a
 * best-of figure without its spread hides whether the machine was quiet.
 *
 * <p><b>The result is consumed</b> into a checksum that is printed, so the JIT cannot delete the
 * evaluation as dead code. The checksum is also a coarse correctness signal: two builds that
 * evaluate identically must print the same one.
 *
 * <p>Usage — a measurement driver, not a test:
 * <pre>{@code
 * java -cp target/classes:target/test-classes:target/dependency/* \
 *     org.michaelfl.mychess.EvalThroughputBenchmark tuning-data/hybrid.epd 50000 7
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class EvalThroughputBenchmark {

    private static final String DEFAULT_EPD = "tuning-data/hybrid.epd";
    private static final int DEFAULT_POSITIONS = 50_000;
    private static final int DEFAULT_REPETITIONS = 7;

    /** Discarded passes before timing starts, to let the JIT settle. */
    private static final int WARMUP_PASSES = 3;

    private static final long NANOS_PER_MS = 1_000_000L;

    private EvalThroughputBenchmark() {
        // measurement driver
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : DEFAULT_EPD);
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_POSITIONS;
        int repetitions = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_REPETITIONS;

        List<Board> boards = load(epd, limit);
        var evaluator = new WeightingFunction();

        System.out.printf(Locale.ROOT, "corpus=%s positions=%,d repetitions=%d warmup=%d%n",
                epd, boards.size(), repetitions, WARMUP_PASSES);

        for (int i = 0; i < WARMUP_PASSES; i++) {
            onePass(evaluator, boards);
        }

        var nanosPerPass = new long[repetitions];
        long checksum = 0;

        for (int i = 0; i < repetitions; i++) {
            long startNanos = System.nanoTime();
            checksum = onePass(evaluator, boards);
            nanosPerPass[i] = System.nanoTime() - startNanos;
        }

        report(nanosPerPass, boards.size(), checksum);
    }

    /**
     * One full pass over every position.
     *
     * @return a checksum of every score, so the calls cannot be optimized away and two builds can
     *         be compared for having evaluated identically
     */
    private static long onePass(WeightingFunction evaluator, List<Board> boards) {
        long checksum = 0;

        for (Board board : boards) {
            checksum += evaluator.calculate(board);
        }

        return checksum;
    }

    private static void report(long[] nanosPerPass, int positions, long checksum) {
        long[] sorted = nanosPerPass.clone();
        Arrays.sort(sorted);

        long best = sorted[0];
        long worst = sorted[sorted.length - 1];
        long median = sorted[sorted.length / 2];

        System.out.printf(Locale.ROOT, "%nchecksum=%d  (two builds that evaluate identically print the same)%n%n",
                checksum);
        System.out.println("per pass, in order:");

        for (int i = 0; i < nanosPerPass.length; i++) {
            System.out.printf(Locale.ROOT, "  %d: %,7d ms   %6.1f ns/eval%n",
                    i + 1, nanosPerPass[i] / NANOS_PER_MS, (double) nanosPerPass[i] / positions);
        }

        System.out.printf(Locale.ROOT, "%nBEST   %,7d ms   %6.1f ns/eval   <- compare this across builds%n",
                best / NANOS_PER_MS, (double) best / positions);
        System.out.printf(Locale.ROOT, "median %,7d ms   %6.1f ns/eval%n",
                median / NANOS_PER_MS, (double) median / positions);
        System.out.printf(Locale.ROOT, "worst  %,7d ms   %6.1f ns/eval   (spread %+.1f %% over best)%n",
                worst / NANOS_PER_MS, (double) worst / positions, 100.0 * (worst - best) / best);
        System.out.printf(Locale.ROOT, "%nA spread above ~5 %% means the machine was not quiet enough to trust "
                + "a difference smaller than that.%n");
    }

    /** Reads positions eagerly, so parsing never lands inside a timed pass. */
    private static List<Board> load(Path epd, int limit) {
        var boards = new ArrayList<Board>(limit);

        try (var lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            var iterator = lines.iterator();

            while (iterator.hasNext() && boards.size() < limit) {
                String[] parts = iterator.next().strip().split("\\s+");

                if (parts.length >= 4) {
                    try {
                        boards.add(Fen.importFEN(parts[0] + " " + parts[1] + " " + parts[2] + " " + parts[3] + " 0 1"));
                    } catch (RuntimeException ignore) {
                        // A corpus line the importer rejects is skipped; the count printed above
                        // shows how many positions actually made it into the measurement.
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + epd, e);
        }

        return boards;
    }
}
