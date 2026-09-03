package org.michaelfl.mychess;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * What the three king-line file scans cost on their own, in the same process as the evaluation
 * they sit inside.
 *
 * <h2>Why, when the bench already answered it</h2>
 *
 * <p>The bench did answer it: at {@code kingLinePenaltyFactor = 0} the scans still run and only
 * the multiplication yields zero, so that build searches a bit-identical tree at
 * <b>−5.55 % NPS</b> — the scans and nothing else. The flaw in that figure is the comparison, not
 * the idea: the baseline NPS came from a run recorded weeks earlier, on a machine in an unknown
 * state. This measures both halves back-to-back in one process, so the number carries no
 * cross-run confound.
 *
 * <h2>How the isolation works</h2>
 *
 * <p>{@link WeightingFunction#calculateKingLineDanger} reads the evaluator's board reference,
 * which {@link WeightingFunction#calculate} installs — so the scans cannot simply be called on
 * their own. Instead two passes are timed:
 *
 * <ul>
 *   <li><b>A:</b> {@code calculate(board)} per position — the full evaluation, scans included.</li>
 *   <li><b>B:</b> the same, plus one extra set of six scans (three files × two kings).</li>
 * </ul>
 *
 * <p>B − A is one extra set of scans, measured in place with realistic cache behavior rather than
 * in a synthetic loop over one position. The king squares are looked up before the clock starts,
 * because production gets them for free inside its piece walk.
 *
 * <h2>The two percentages are not the same number</h2>
 *
 * <p>This reports the scans as a share of <b>evaluation</b> time. The bench's −5.55 % is their
 * share of <b>node</b> time, and a node is move generation, make/revert, transposition probing and
 * sorting as well as one evaluation. The eval-level share is therefore the larger figure, and the
 * ratio between the two says how much of a node this engine spends evaluating. Neither is Elo:
 * evaluation time converts into plies, and only a match prices plies.
 *
 * <pre>
 * java -cp target/classes:target/test-classes:target/dependency/* \
 *      org.michaelfl.mychess.KingLineCostBenchmark tuning-data/mychess-selfplay-960.epd 20000 7
 * </pre>
 *
 * @author Michael Fleischhauer
 */
public final class KingLineCostBenchmark {

    private static final String DEFAULT_EPD = "tuning-data/mychess-selfplay-960.epd";
    private static final int DEFAULT_POSITIONS = 20_000;
    private static final int DEFAULT_REPETITIONS = 7;
    private static final int WARMUP_PASSES = 3;
    private static final String RESULT_TAG = " c9 ";
    private static final String EPD_COUNTER_SUFFIX = " 0 1";
    private static final int FILE_SPREAD = 1;

    private KingLineCostBenchmark() {
        // measurement driver
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : DEFAULT_EPD);
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_POSITIONS;
        int repetitions = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_REPETITIONS;

        List<Board> boards = load(epd, limit);
        int[][] kings = kingSquares(boards);
        var evaluator = new WeightingFunction();

        System.out.printf(Locale.ROOT, "corpus=%s positions=%,d repetitions=%d warmup=%d%n%n",
                epd, boards.size(), repetitions, WARMUP_PASSES);

        for (int i = 0; i < WARMUP_PASSES; i++) {
            evalOnly(evaluator, boards);
            evalPlusScans(evaluator, boards, kings);
        }

        var plain = new long[repetitions];
        var withScans = new long[repetitions];

        for (int i = 0; i < repetitions; i++) {
            plain[i] = evalOnly(evaluator, boards);
            withScans[i] = evalPlusScans(evaluator, boards, kings);
        }

        long bestPlain = min(plain);
        long bestWithScans = min(withScans);
        long scanNanos = bestWithScans - bestPlain;
        int n = boards.size();

        System.out.printf(Locale.ROOT, "%-34s%14s%14s%n", "pass", "best ms", "ns/position");
        System.out.println("-".repeat(62));
        System.out.printf(Locale.ROOT, "%-34s%14.1f%14.1f%n", "A: calculate only",
                bestPlain / 1e6, (double) bestPlain / n);
        System.out.printf(Locale.ROOT, "%-34s%14.1f%14.1f%n", "B: calculate + 6 extra scans",
                bestWithScans / 1e6, (double) bestWithScans / n);
        System.out.printf(Locale.ROOT, "%-34s%14.1f%14.1f%n", "B - A: the six scans",
                scanNanos / 1e6, (double) scanNanos / n);
        System.out.printf(Locale.ROOT, "%nthe six scans are %.2f %% of one evaluation%n",
                100.0 * scanNanos / bestPlain);
        System.out.printf(Locale.ROOT, "spread over repetitions: A %.1f %%, B %.1f %%%n",
                spread(plain), spread(withScans));
        System.out.println("""

                Read the spread first. If it is of the same order as the difference between A and
                B, the difference is not measured, it is noise — that is how an earlier assertion
                benchmark in this project came out with the instrumented build faster than the
                stripped one.

                Best-of-N is used rather than the mean, because the fastest pass is the one least
                disturbed by everything else on the machine.""");
    }

    private static long evalOnly(WeightingFunction evaluator, List<Board> boards) {
        long started = System.nanoTime();
        int sink = 0;

        for (Board board : boards) {
            sink += evaluator.calculate(board);
        }

        return guard(sink, System.nanoTime() - started);
    }

    private static long evalPlusScans(WeightingFunction evaluator, List<Board> boards,
                                      int[][] kings) {
        long started = System.nanoTime();
        int sink = 0;

        for (int i = 0; i < boards.size(); i++) {
            sink += evaluator.calculate(boards.get(i));

            for (int color = 0; color < 2; color++) {
                for (int offset = -FILE_SPREAD; offset <= FILE_SPREAD; offset++) {
                    sink += evaluator.calculateKingLineDanger(color, kings[i][color] + offset);
                }
            }
        }

        return guard(sink, System.nanoTime() - started);
    }

    /** Keeps the sum observable so the JIT cannot delete the work being timed. */
    private static long guard(int sink, long elapsed) {
        if (sink == Integer.MIN_VALUE) {
            System.out.print("");
        }

        return elapsed;
    }

    private static int[][] kingSquares(List<Board> boards) {
        var out = new int[boards.size()][2];

        for (int i = 0; i < boards.size(); i++) {
            byte[] squares = boards.get(i).getRawBoard();

            for (int color = 0; color < 2; color++) {
                byte king = color == 0 ? Board.whiteKing : Board.blackKing;
                out[i][color] = Board.a1;

                for (int rank = 0; rank < 8; rank++) {
                    for (int file = 0; file < 8; file++) {
                        int field = Board.a1 + rank * Board.LENGTH + file;

                        if (squares[field] == king) {
                            out[i][color] = field;
                        }
                    }
                }
            }
        }

        return out;
    }

    private static List<Board> load(Path epd, int limit) {
        var out = new ArrayList<Board>();

        try (Stream<String> lines = Files.lines(epd, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (out.size() >= limit) {
                    break;
                }

                int tag = line.indexOf(RESULT_TAG);
                String fen = (tag < 0 ? line : line.substring(0, tag)).trim();

                if (fen.isEmpty()) {
                    continue;
                }

                try {
                    out.add(Fen.importChess960FEN(
                            fen.split(" ").length < 6 ? fen + EPD_COUNTER_SUFFIX : fen));
                } catch (RuntimeException _) {
                    // a line the importer rejects contributes nothing
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + epd, e);
        }

        return out;
    }

    private static long min(long[] values) {
        long best = Long.MAX_VALUE;

        for (long value : values) {
            best = Math.min(best, value);
        }

        return best;
    }

    private static double spread(long[] values) {
        long best = min(values);
        long worst = 0;

        for (long value : values) {
            worst = Math.max(worst, value);
        }

        return 100.0 * (worst - best) / best;
    }
}
