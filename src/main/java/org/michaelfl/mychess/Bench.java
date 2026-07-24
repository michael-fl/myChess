package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Node-count benchmark ("bench"): runs the engine over a fixed, frozen suite of
 * positions at a fixed search depth and reports, per position and in total, the
 * cumulative node count, elapsed time, and nodes-per-second.
 *
 * <p>The total node count is a deterministic, bit-reproducible signature of the
 * engine's search behavior at a given depth: two builds that visit the identical
 * node count on every position are search- and eval-identical, so {@code bench}
 * is the primary "is this refactor neutral?" check — compare the signature of
 * build A against build B (see {@code docs/roadmap.md} § 12.10.1). Wall-clock
 * time and NPS are machine-dependent and only informative, never a correctness
 * signal.
 *
 * <p>Determinism guarantees, per position: a freshly cleared transposition
 * table, an empty {@link MyChessEnv} (no opening book, so every position is
 * actually searched instead of being answered from the book), and a search
 * bounded only by depth (the time budget is effectively infinite).
 *
 * <p>Usage example:
 * <pre>{@code
 * Bench.BenchResult r = Bench.run(9, false);
 * System.out.println("signature: " + r.totalNodes());
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class Bench {

    /** Default search depth when the caller does not specify one. */
    public static final int DEFAULT_DEPTH = 9;

    /** Effectively unbounded per-move time budget so depth is the only limit (24 h in ms). */
    private static final int INFINITE_MILLIS = 24 * 60 * 60 * 1_000;

    private static final String STANDARD_FENS = "/bench/stockfish-standard.fen";
    private static final String MIDDLEGAME_FENS = "/bench/mychess-middlegames.fen";
    private static final String CHESS960_FENS = "/bench/chess960.fen";

    /** Result of searching a single benchmark position. */
    public record PositionResult(String fen, long nodes, long timeMs) {}

    /** Aggregate result of a full benchmark run. */
    public record BenchResult(int depth, boolean chess960, List<PositionResult> positions, long totalNodes, long totalTimeMs) {

        /** Nodes per second across the whole run (0 when no time elapsed). */
        public long nps() {
            return totalTimeMs == 0 ? 0 : totalNodes * 1_000L / totalTimeMs;
        }
    }

    /** Signals a failure while running the benchmark (bad resource or failed search). */
    public static final class BenchException extends RuntimeException {

        BenchException(String message) {
            super(message);
        }

        BenchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private Bench() {
        // static utility
    }

    /**
     * Run the benchmark suite at a fixed depth.
     *
     * @param depth    fixed search depth in plies
     * @param chess960 when {@code true}, run the Chess960 suite in 960 mode;
     *                 otherwise the standard suite (regular + middlegame FENs)
     * @return the per-position and aggregate node/time results
     * @throws BenchException if a suite resource is missing or a search fails
     */
    public static BenchResult run(int depth, boolean chess960) {
        List<String> fens = chess960
                ? loadFens(CHESS960_FENS)
                : concat(loadFens(STANDARD_FENS), loadFens(MIDDLEGAME_FENS));

        var tt = TranspositionTable.getDefaultInstance();
        var env = new MyChessEnv(); // no opening book -> every position is searched
        ChessEngine.resetIterationTimings();

        var results = new ArrayList<PositionResult>(fens.size());
        long totalNodes = 0;
        long totalTimeMs = 0;

        for (String fen : fens) {
            Board board = chess960 ? Fen.importChess960FEN(fen) : Fen.importFEN(fen);

            // Fresh hash per position so node counts are order-independent and
            // reproducible across runs and builds.
            tt.clear();

            var engineConfig = new EngineConfig.Builder()
                    .maxDepth(depth)
                    .millisPerMove(INFINITE_MILLIS)
                    .silent(true)
                    .setTranspositionTable(tt)
                    .build();
            var game = new Game(new GameConfig(MyChessEngine.class, engineConfig), board);

            var nodes = new AtomicLong();
            long startMs = System.currentTimeMillis();

            try {
                // The final iteration's IterationInfo carries the cumulative
                // node count for the whole search; getResult blocks until it fired.
                game.getEngine()
                        .nextMoveAsync(env, info -> nodes.set(info.nodes()))
                        .getResult(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BenchException("bench search interrupted at position " + fen, e);
            } catch (ExecutionException | TimeoutException e) {
                throw new BenchException("bench search failed at position " + fen, e);
            } finally {
                game.shutdown();
            }

            long timeMs = System.currentTimeMillis() - startMs;
            results.add(new PositionResult(fen, nodes.get(), timeMs));
            totalNodes += nodes.get();
            totalTimeMs += timeMs;
        }

        return new BenchResult(depth, chess960, List.copyOf(results), totalNodes, totalTimeMs);
    }

    private static List<String> concat(List<String> first, List<String> second) {
        var all = new ArrayList<String>(first.size() + second.size());

        all.addAll(first);
        all.addAll(second);

        return all;
    }

    private static List<String> loadFens(String resource) {
        InputStream stream = Bench.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new BenchException("bench resource not found on classpath: " + resource);
        }

        var fens = new ArrayList<String>();

        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();

                if (!trimmed.isEmpty()) {
                    fens.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read bench resource " + resource, e);
        }

        return fens;
    }
}
