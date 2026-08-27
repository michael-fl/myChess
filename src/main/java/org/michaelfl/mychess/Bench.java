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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

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
 * Bench.BenchResult r = Bench.run(Bench.DEFAULT_DEPTH, false);
 * System.out.println("signature: " + r.totalNodes());
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class Bench {

    /**
     * Default search depth when the caller does not specify one.
     *
     * <p>Eight plies, because the node signature is equally strict at any fixed
     * depth — the depth is part of the signature's label, not of its quality —
     * while the cost is not: the suite's two artificial many-piece stress
     * positions dominate the run, and every extra ply multiplies the whole
     * benchmark by roughly the branching factor. Eight is also the lowest depth
     * that still sits inside the depth 6-8 band the roadmap calibrates myChess's
     * own resolution to ({@code docs/roadmap-backlog.md} § 12.10.2).
     *
     * <p><b>It no longer keeps a run in the low minutes.</b> That was true through
     * v4.4.1 (about three minutes) and stopped being true in v4.6.0, which takes
     * <b>17 minutes</b> at this depth and 29 at depth 9 — not because the search
     * got broadly more expensive, but because a single position did. See
     * {@link BenchResult#largestPosition()}; on the other 54 positions the
     * depth-8 count actually fell. The depth stays at eight regardless, since a
     * signature is only comparable to signatures at the same depth, and the whole
     * value of {@code docs/bench-history.md} is the series.
     */
    public static final int DEFAULT_DEPTH = 8;

    /**
     * Effectively unbounded per-move time budget so depth is the only limit (24 h in ms).
     *
     * <p>Package-private rather than private because {@code Sts} (the Strategic
     * Test Suite runner in the test sources) needs the identical budget for the
     * identical reason, and a second copy of the constant would be a second thing
     * to keep in sync. Both runners depend on the budget being large enough that
     * {@code PositionSearch}'s skip-hopeless-iteration heuristic can never fire,
     * which is what makes a fixed-depth run reproducible.
     */
    static final int INFINITE_MILLIS = 24 * 60 * 60 * 1_000;

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

        /**
         * The single most expensive position of the run, by node count.
         *
         * <p>Reported next to the total because the total alone hides how it is composed, and
         * in this suite it is composed very unevenly. The standard suite's position 37 — an
         * artificial 26-piece, no-pawn stress position inherited from Stockfish's bench — grew
         * from 5.2 % of the depth-8 signature in v4.1.0 to <b>86.9 %</b> in v4.6.0, a factor of
         * 112 in its own node count. That went unnoticed for four releases because only the
         * sum was ever recorded, and the sum moved for what looked like ordinary reasons.
         *
         * <p>Deliberately the largest position rather than that one by index or FEN: an index
         * shifts the moment a suite file is edited, and privileging one position in code would
         * stop being true as soon as a different one dominated. What matters is that the
         * concentration is visible at all, whichever position carries it.
         *
         * @return the position with the highest node count
         * @throws BenchException if the run contains no positions, which means a broken suite
         *                        resource rather than an empty result
         */
        public PositionResult largestPosition() {
            if (positions.isEmpty()) {
                throw new BenchException("benchmark run contains no positions");
            }

            return positions.stream()
                    .max(Comparator.comparingLong(PositionResult::nodes))
                    .orElseThrow();
        }

        /**
         * Share of {@link #totalNodes()} consumed by {@link #largestPosition()}, in percent.
         *
         * @return the share in percent, or 0 when the run visited no nodes at all
         * @throws BenchException if the run contains no positions
         */
        public double largestPositionShare() {
            // Resolved before the zero check so an empty run fails here too, rather than
            // reporting a share of 0 for a suite that never ran.
            long largest = largestPosition().nodes();

            return totalNodes == 0 ? 0.0 : 100.0 * largest / totalNodes;
        }

        /**
         * Node count over every position except {@link #largestPosition()}.
         *
         * <p>The signature of the rest of the suite. It answers a question the total cannot
         * once one position dominates: the depth-8 total rose 286 % from v4.5.0 to v4.6.0
         * while this figure <i>fell</i>, from 189 M in v4.3.4 to 170 M — the search got
         * cheaper on the 54 realistic positions and more expensive on the one artificial
         * stress position, and the sum reports only the second half of that.
         *
         * @return the total minus the largest position's nodes
         * @throws BenchException if the run contains no positions
         */
        public long nodesWithoutLargestPosition() {
            return totalNodes - largestPosition().nodes();
        }

        /**
         * Nodes per second over every position except {@link #largestPosition()}.
         *
         * <p>Worth reading next to {@link #nps()} because the dominant position is not a
         * typical one — it is figure-dense and pawnless, so its cost per node differs from the
         * rest of the suite, and while it carries most of the run it also sets most of the
         * headline NPS. Machine-dependent and informative only, exactly like {@link #nps()}:
         * never assert on it (policy rule 4 in {@code docs/bench-history.md}).
         *
         * @return nodes per second excluding the largest position, or 0 when no time remains
         *         outside it
         * @throws BenchException if the run contains no positions
         */
        public long npsWithoutLargestPosition() {
            long timeMs = totalTimeMs - largestPosition().timeMs();

            return timeMs <= 0 ? 0 : nodesWithoutLargestPosition() * 1_000L / timeMs;
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
     * Run the benchmark suite at a fixed depth, reporting nothing until it finishes.
     *
     * <p>Prefer {@link #run(int, boolean, Consumer)} for anything interactive: a depth-8 run
     * takes minutes and a depth-9 run tens of minutes, and this overload is silent for all of
     * it.
     *
     * @param depth    fixed search depth in plies
     * @param chess960 when {@code true}, run the Chess960 suite in 960 mode;
     *                 otherwise the standard suite (regular + middlegame FENs)
     * @return the per-position and aggregate node/time results
     * @throws BenchException if a suite resource is missing or a search fails
     */
    public static BenchResult run(int depth, boolean chess960) {
        return run(depth, chess960, position -> {
            // Nothing to report — the silent overload exists for callers that only want the
            // aggregate, such as EvalBenchmarkTest.
        });
    }

    /**
     * Run the benchmark suite at a fixed depth, reporting each position as it completes.
     *
     * <p>{@code onPosition} fires once per position, in suite order, immediately after that
     * position's search returns. It exists because the aggregate arrives only at the end: a
     * depth-8 run is minutes and a depth-9 run tens of minutes, and without this there is no
     * way to tell a working run from a hung one, nor to keep anything if it is interrupted.
     * That cost real time twice in August 2026 — see the long-running-process rules in
     * {@code CLAUDE.md}, whose first requirement this overload is what satisfies.
     *
     * <p>Deliberately a callback rather than printing here: the caller decides whether the
     * lines go to the console, to the per-position archive of
     * {@code docs/bench-history.md} § 7, or nowhere. {@code Sts#run} uses the same shape.
     *
     * <p><b>No remaining-time estimate is offered, and one should not be added from the
     * position count.</b> The suite's positions span 48 nodes to 19.2 million, so extrapolating
     * over positions completed is badly wrong for the first half of a run. Report what is
     * known — how many are done and how long it has taken.
     *
     * @param depth      fixed search depth in plies
     * @param chess960   when {@code true}, run the Chess960 suite in 960 mode;
     *                   otherwise the standard suite (regular + middlegame FENs)
     * @param onPosition invoked once per completed position, in suite order
     * @return the per-position and aggregate node/time results
     */
    /**
     * Number of positions in a suite, without running it.
     *
     * <p>Exists so a progress callback can print {@code n/55} rather than a bare counter: the
     * per-position lines are redirected into the archive of § 7 in
     * {@code docs/bench-history.md}, and that archive is only useful if a line from one version
     * diffs cleanly against the same line from another. The leading fields therefore have to
     * stay byte-identical across releases, which means the total has to be known before the
     * first position finishes.
     *
     * @param chess960 when {@code true}, the Chess960 suite; otherwise the standard one
     * @return the number of positions the corresponding {@code run} will search
     * @throws BenchException if a suite resource is missing
     */
    public static int suiteSize(boolean chess960) {
        return chess960
                ? loadFens(CHESS960_FENS).size()
                : loadFens(STANDARD_FENS).size() + loadFens(MIDDLEGAME_FENS).size();
    }

    public static BenchResult run(int depth, boolean chess960, Consumer<PositionResult> onPosition) {
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
            var positionResult = new PositionResult(fen, nodes.get(), timeMs);

            results.add(positionResult);
            totalNodes += nodes.get();
            totalTimeMs += timeMs;

            onPosition.accept(positionResult);
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
