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
 * Times {@link Board#canCaptureOpposingKing()} against {@link WeightingFunction#calculate(Board)}
 * on the same corpus, so the trade behind roadmap § 12.26's probe-hoisting idea can be decided by
 * arithmetic instead of intuition.
 *
 * <p><b>The trade.</b> At a quiescence entry an illegal position pays a full evaluation before the
 * {@code containsIllegalMove} sentinel is read. Hoisting the probe above the stand-pat would settle
 * those leaves earlier — but the probe would then run on <i>every</i> leaf, while only
 * <b>6.13 %</b> of evaluation calls are illegal positions (measured at depth 6, § 12.26). The
 * change pays off only if
 *
 * <pre>{@code probeCost < 0.0613 x evalCost}</pre>
 *
 * i.e. if the probe is more than about <b>16x cheaper</b> than the evaluation. That is a much
 * harder bar than it first looks, which is the reason for measuring: the probe calls
 * {@code findKingField}, a linear scan over all 64 squares, before it runs {@code isFieldAttackedBy}
 * — and the evaluation also walks the board exactly once.
 *
 * <p>Methodology is deliberately identical to {@link EvalThroughputBenchmark} so the two numbers are
 * comparable: same corpus, warm-up passes discarded, best-of-N reported with its spread, results
 * consumed into a checksum so the JIT cannot delete the calls as dead code.
 *
 * <p>Usage — a measurement driver, not a test:
 * <pre>{@code
 * java -cp target/classes:target/test-classes:target/dependency/* \
 *     org.michaelfl.mychess.ProbeVsEvalBenchmark tuning-data/hybrid.epd 50000 7
 * }</pre>
 */
public final class ProbeVsEvalBenchmark {

    private static final String DEFAULT_EPD = "tuning-data/hybrid.epd";
    private static final int DEFAULT_POSITIONS = 50_000;
    private static final int DEFAULT_REPETITIONS = 7;

    /** Discarded passes before timing starts, to let the JIT settle. */
    private static final int WARMUP_PASSES = 3;

    private static final long NANOS_PER_MS = 1_000_000L;

    /** Share of evaluation calls that hit an illegal position, measured at depth 6 (§ 12.26). */
    private static final double ILLEGAL_SHARE = 0.0613;

    private ProbeVsEvalBenchmark() {
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
            probePass(boards);
            attackPass(boards);
        }

        var evalNanos = new long[repetitions];
        var probeNanos = new long[repetitions];
        var attackNanos = new long[repetitions];
        long evalChecksum = 0;
        long probeChecksum = 0;

        // Interleaved rather than one block each, so a drift in machine load over the run
        // disturbs every measurement equally instead of only the later ones.
        for (int i = 0; i < repetitions; i++) {
            long startNanos = System.nanoTime();
            evalChecksum = evalPass(evaluator, boards);
            evalNanos[i] = System.nanoTime() - startNanos;

            startNanos = System.nanoTime();
            probeChecksum = probePass(boards);
            probeNanos[i] = System.nanoTime() - startNanos;

            startNanos = System.nanoTime();
            attackPass(boards);
            attackNanos[i] = System.nanoTime() - startNanos;
        }

        report(evalNanos, probeNanos, attackNanos, boards.size(), evalChecksum, probeChecksum);
    }

    private static long evalPass(WeightingFunction evaluator, List<Board> boards) {
        long checksum = 0;

        for (Board board : boards) {
            checksum += evaluator.calculate(board);
        }

        return checksum;
    }

    private static long probePass(List<Board> boards) {
        long checksum = 0;

        for (Board board : boards) {
            if (board.canCaptureOpposingKing()) {
                checksum++;
            }
        }

        return checksum;
    }

    private static void report(long[] evalNanos, long[] probeNanos, long[] attackNanos, int positions,
                               long evalChecksum, long probeChecksum) {
        double evalPer = (double) min(evalNanos) / positions;
        double probePer = (double) min(probeNanos) / positions;
        double attackPer = (double) min(attackNanos) / positions;
        double breakEven = 1.0 / ILLEGAL_SHARE;

        System.out.printf(Locale.ROOT, "checksums: eval=%d probe=%d (probe = illegal positions in corpus)%n%n",
                evalChecksum, probeChecksum);

        printSeries("evaluation      ", evalNanos, positions);
        printSeries("probe, as it is ", probeNanos, positions);
        printSeries("attack test only", attackNanos, positions);

        System.out.printf(Locale.ROOT, "%nBEST  evaluation %6.1f ns   probe %6.1f ns   attack test %6.1f ns%n",
                evalPer, probePer, attackPer);
        System.out.printf(Locale.ROOT, "break-even needs %.1fx (the probe runs on 100 %% of leaves to save %.2f %% of them)%n%n",
                breakEven, 100.0 * ILLEGAL_SHARE);

        printVerdict("probe as it is today", evalPer, probePer, breakEven);
        printVerdict("with the king square tracked incrementally", evalPer, attackPer, breakEven);

        System.out.printf(Locale.ROOT, "%nThe second line is the optimistic bound: it assumes findKingField costs "
                + "nothing at all, which no implementation achieves. Read both against the spread above — a ratio "
                + "close to break-even means 'no measurable difference', not 'marginally worth it'.%n");
    }

    private static void printVerdict(String label, double evalPer, double costPer, double breakEven) {
        double ratio = evalPer / costPer;

        System.out.printf(Locale.ROOT, "  %-44s %5.1fx cheaper -> %s, net %+.2f %% of evaluation time%n",
                label, ratio, ratio > breakEven ? "A WIN " : "A LOSS",
                100.0 * (ILLEGAL_SHARE - costPer / evalPer));
    }

    /**
     * The probe without its king search: {@code isFieldAttackedBy} on a fixed central square.
     *
     * <p>Isolates what the probe would cost if {@code findKingField}'s linear scan were replaced by
     * an incrementally tracked king square. The square is fixed rather than the real king field so
     * the pass measures the attack test alone, with no lookup of any kind in it — an optimistic
     * bound on any incremental scheme, since a real one still has to read the tracked value.
     */
    private static long attackPass(List<Board> boards) {
        long checksum = 0;

        for (Board board : boards) {
            if (board.isFieldAttackedBy(Board.e4, GameStatus.TURN_WHITE)) {
                checksum++;
            }
        }

        return checksum;
    }

    private static void printSeries(String label, long[] nanos, int positions) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);

        long best = sorted[0];
        long worst = sorted[sorted.length - 1];

        System.out.printf(Locale.ROOT, "%s best %,7d ms  %6.1f ns/call   worst %,7d ms   (spread %+.1f %%)%n",
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
