package org.michaelfl.mychess;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Micro-benchmarks for the evaluation hot path.
 *
 * <p>Two builds can be proven <em>value-</em> and <em>tree-identical</em> at a
 * fixed depth (same score, same best move, same node count) and still differ in
 * strength at a time control, because the evaluation does more work per node.
 * The tapered-evaluation null test surfaced exactly this: identical trees, but a
 * measurable Elo loss at {@code tc=40/60} — the cost of the blend. These
 * benchmarks quantify that cost so an optimization can be guided by data and
 * guarded against regression.
 *
 * <p>The absolute numbers are machine- and JIT-dependent and are never a
 * correctness signal — the value is the <em>ratio between two builds</em>: run
 * this on the flat-eval build and on the tapered-eval build and compare
 * {@code ns/call} (and the search NPS). The assertions here are deliberately
 * loose floors that only catch a pathological breakage; the printed metrics are
 * the deliverable.
 *
 * <p>Both methods carry {@code @Tag("slow")}; run them explicitly, e.g.
 * {@code mvn -Dtest=EvalBenchmarkTest#evalThroughput test -Dsurefire.useFile=false}.
 *
 * @author Michael Fleischhauer
 */
@Tag("slow")
class EvalBenchmarkTest {

    private static final String STANDARD_FENS = "/bench/stockfish-standard.fen";
    private static final String MIDDLEGAME_FENS = "/bench/mychess-middlegames.fen";

    /** Total {@code calculate()} calls to time; large enough to amortize timer and loop overhead. */
    private static final long TARGET_MEASURE_CALLS = 20_000_000L;

    /** Untimed calls before measuring, so the JIT has compiled the hot path. */
    private static final long TARGET_WARMUP_CALLS = 3_000_000L;

    /** Search depth for the NPS proxy — moderate so the run stays reasonable. */
    private static final int SEARCH_NPS_DEPTH = 7;

    /**
     * Times {@link WeightingFunction#calculate(Board)} in isolation across the
     * frozen bench position set and reports nanoseconds per call and millions of
     * evaluations per second. This is the direct measure of the evaluation's
     * per-node cost — the quantity the tapered blend inflates.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void evalThroughput() {
        List<Board> boards = loadBoards();
        assertTrue(boards.size() >= 20, "expected a representative position set, got " + boards.size());

        var evaluator = new WeightingFunction();
        long warmupRounds = Math.max(1, TARGET_WARMUP_CALLS / boards.size());
        long measureRounds = Math.max(1, TARGET_MEASURE_CALLS / boards.size());

        // Warm up the JIT; fold every result into a checksum so the calls cannot
        // be dead-code-eliminated.
        long checksum = 0;

        for (long round = 0; round < warmupRounds; round++) {
            for (Board board : boards) {
                checksum += evaluator.calculate(board);
            }
        }

        long calls = 0;
        long startNs = System.nanoTime();

        for (long round = 0; round < measureRounds; round++) {
            for (Board board : boards) {
                checksum += evaluator.calculate(board);
                calls++;
            }
        }

        long elapsedNs = System.nanoTime() - startNs;
        double nsPerCall = (double) elapsedNs / calls;
        double mEvalPerSec = calls / (elapsedNs / 1_000_000_000.0) / 1_000_000.0;

        System.out.printf(
                "%nBENCH eval-throughput | positions=%d calls=%,d | %.1f ns/call | %.2f Meval/s | checksum=%d%n",
                boards.size(), calls, nsPerCall, mEvalPerSec, checksum);

        assertTrue(Double.isFinite(nsPerCall) && nsPerCall > 0, "ns/call must be a positive, finite number");
        assertTrue(mEvalPerSec > 0.05,
                "eval throughput collapsed to " + mEvalPerSec + " Meval/s — the hot path is broken");
    }

    /**
     * Real-play speed proxy: runs the frozen bench suite at a fixed depth and
     * reports nodes-per-second (full search, so evaluation cost shows up diluted
     * by move generation and search overhead — the way it actually affects
     * time-bounded play). The node count is the deterministic build signature; a
     * slower NPS at an identical node count is a pure speed regression.
     */
    @Test
    @Timeout(value = 300, unit = TimeUnit.SECONDS)
    void searchNps() {
        Bench.BenchResult result = Bench.run(SEARCH_NPS_DEPTH, false);

        System.out.printf(
                "%nBENCH search-nps | positions=%d depth=%d | nodes=%,d | time=%,d ms | %,d NPS%n",
                result.positions().size(), result.depth(), result.totalNodes(), result.totalTimeMs(), result.nps());

        assertTrue(result.totalNodes() > 0, "search visited no nodes — the benchmark did not run");
    }

    private static List<Board> loadBoards() {
        var boards = new ArrayList<Board>();

        for (String resource : List.of(STANDARD_FENS, MIDDLEGAME_FENS)) {
            try (var reader = new BufferedReader(new InputStreamReader(
                    Objects.requireNonNull(EvalBenchmarkTest.class.getResourceAsStream(resource),
                            "missing bench resource on classpath: " + resource),
                    StandardCharsets.UTF_8))) {

                String line;

                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();

                    if (!trimmed.isEmpty()) {
                        boards.add(Fen.importFEN(trimmed));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("failed to read bench resource " + resource, e);
            }
        }

        return boards;
    }
}
