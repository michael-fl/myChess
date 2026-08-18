package org.michaelfl.mychess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Command-line front end for {@code Sts}: runs the Strategic Test Suite and prints
 * the per-theme score.
 *
 * <p>Run it through {@code tools/run-sts.sh}, which compiles the test sources and
 * forks a JVM with the right classpath:
 *
 * <pre>
 * tools/run-sts.sh          # full suite at the default depth (~30 min)
 * tools/run-sts.sh 6 king   # King Activity only, depth 6 (~30 s)
 * tools/run-sts.sh 8 11 5   # 5 positions of theme 11, for a shape check
 * </pre>
 *
 * <p>Not {@code mvn exec:java}: that runs the class inside the Maven JVM and then
 * waits for every non-daemon thread, and {@code Game.shutdown()} does not release
 * {@code statusEngine}. The work finishes, the command never returns — measured on
 * 2026-08-18, five positions at depth 4 completed in 0.2 s directly and hung
 * {@code exec:java} past seven minutes.
 *
 * <p>Arguments are positional and all optional:
 *
 * <ol>
 *   <li><b>depth</b> — fixed search depth, default {@code Sts.DEFAULT_DEPTH}. The
 *       release measurement uses the default and nothing else; a shallower depth is
 *       for calibration and produces a number that is <em>not</em> comparable to
 *       the series in {@code docs/sts-history.md}.</li>
 *   <li><b>theme</b> — {@code all} (default), a theme number 1-15, or a
 *       case-insensitive substring of a theme name ({@code king} selects
 *       King Activity).</li>
 *   <li><b>limit</b> — maximum positions per theme, for a quick shape check.</li>
 * </ol>
 *
 * <p>The full suite at the default depth takes about half an hour, which is why output
 * is streamed per position rather than collected and printed at the end. Redirect
 * stdout to a file if the run should be archived; the runner deliberately writes
 * no files of its own.
 *
 * @author Michael Fleischhauer
 */
public final class StsRunner {

    private static final String ALL_THEMES = "all";
    private static final String SEPARATOR = "===============================================================";
    private static final int MISSES_SHOWN = 40;

    private StsRunner() {
        // command-line entry point
    }

    /**
     * Run the suite and print the report.
     *
     * @param args {@code [depth] [theme] [limit]}, see the class documentation
     */
    @SuppressWarnings("UnnecessaryModifier")
    public static void main(String[] args) {
        int depth = args.length > 0 ? Integer.parseInt(args[0]) : Sts.DEFAULT_DEPTH;
        String themeArg = args.length > 1 ? args[1] : ALL_THEMES;
        int limit = args.length > 2 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;

        var positions = select(Sts.loadSuite(), themeArg, limit);

        if (positions.isEmpty()) {
            System.out.printf(Locale.ROOT, "no positions match theme '%s'%n", themeArg);

            return;
        }

        System.out.printf(Locale.ROOT, "sts suite=%s depth=%d positions=%d%n",
                themeArg, depth, positions.size());

        var progress = new StreamingProgress(positions.size());
        var result = Sts.run(depth, positions, progress::report);

        printThemes(result);
        printTotals(result);
        printMisses(result);
    }

    /** Prints one line per searched position so a long run reports as it goes. */
    private static final class StreamingProgress {

        private final int total;
        private int index;

        private StreamingProgress(int total) {
            this.total = total;
        }

        private void report(Sts.PositionResult result) {
            System.out.printf(Locale.ROOT, "%4d/%d  %-46s played %-5s pts %3d  nodes %,12d  time %6d ms%n",
                    ++index, total, result.position().label(), result.chosenMove(),
                    result.points(), result.nodes(), result.timeMs());
        }
    }

    private static List<Sts.Position> select(List<Sts.Position> suite, String themeArg, int limit) {
        var selected = ALL_THEMES.equalsIgnoreCase(themeArg) ? suite : matching(suite, themeArg);

        if (limit == Integer.MAX_VALUE) {
            return selected;
        }

        var limited = new ArrayList<Sts.Position>();
        var perTheme = HashMap.<Integer, Integer>newHashMap(Sts.THEME_COUNT);

        for (Sts.Position position : selected) {
            int taken = perTheme.getOrDefault(position.theme(), 0);

            if (taken < limit) {
                limited.add(position);
                perTheme.put(position.theme(), taken + 1);
            }
        }

        System.out.printf(Locale.ROOT, "limit=%d per theme: %d of %d positions selected%n",
                limit, limited.size(), selected.size());

        return limited;
    }

    private static List<Sts.Position> matching(List<Sts.Position> suite, String themeArg) {
        try {
            return Sts.filterByTheme(suite, Integer.parseInt(themeArg));
        } catch (NumberFormatException e) {
            // Not a theme number, so treat it as a name substring instead.
            String needle = themeArg.toLowerCase(Locale.ROOT);

            return suite.stream()
                    .filter(position -> position.themeName().toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
        }
    }

    private static void printThemes(Sts.StsResult result) {
        System.out.println(SEPARATOR);
        System.out.printf(Locale.ROOT, "STS  %-44s %4s %14s %7s %6s %6s%n",
                "Theme", "pos", "score", "pct", "best", "miss");

        for (var theme : result.themes()) {
            System.out.printf(Locale.ROOT, "%3d  %-44s %4d %6d/%-7d %6.1f%% %6d %6d%n",
                    theme.theme(), theme.themeName(), theme.positions(),
                    theme.points(), theme.maxPoints(), theme.percent(),
                    theme.bestMoveHits(), theme.misses());
        }
    }

    private static void printTotals(Sts.StsResult result) {
        int positions = result.positions().size();

        System.out.println(SEPARATOR);
        System.out.printf(Locale.ROOT, "Weakest themes : %s%n", weakestSummary(result));
        System.out.printf(Locale.ROOT, "Positions      : %,d at depth %d%n", positions, result.depth());
        System.out.printf(Locale.ROOT, "STS score      : %,d / %,d (%.1f %%)%n",
                result.points(), result.maxPoints(), result.percent());
        System.out.printf(Locale.ROOT, "Best-move hits : %,d (%.1f %%)%n",
                result.bestMoveHits(), percentOf(result.bestMoveHits(), positions));
        System.out.printf(Locale.ROOT, "Misses (0 pts) : %,d (%.1f %%)%n",
                result.misses(), percentOf(result.misses(), positions));
        System.out.printf(Locale.ROOT, "Total time     : %,d ms%n", result.totalTimeMs());
        System.out.printf(Locale.ROOT, "Nodes searched : %,d%n", result.totalNodes());
        System.out.printf(Locale.ROOT, "NPS            : %,d%n", result.nps());
        System.out.println();
        System.out.println("Read the best/miss columns with the percentage: the same score can mean");
        System.out.println("\"half-good everywhere\" or \"a third perfect, the rest off the list\".");
    }

    private static void printMisses(Sts.StsResult result) {
        var missed = result.missedPositions();

        if (missed.isEmpty()) {
            return;
        }

        System.out.println();
        System.out.printf(Locale.ROOT, "Positions scoring zero (%d) — candidates for a characterization test:%n",
                missed.size());

        int shown = Math.min(missed.size(), MISSES_SHOWN);

        for (int i = 0; i < shown; i++) {
            var missedResult = missed.get(i);

            System.out.printf(Locale.ROOT, "  %-30s played %-5s expected %-5s  %s%n",
                    missedResult.position().label(), missedResult.chosenMove(),
                    missedResult.position().bestMove(), missedResult.position().fen());
        }

        if (shown < missed.size()) {
            System.out.printf(Locale.ROOT, "  ... %d more not shown%n", missed.size() - shown);
        }
    }

    private static String weakestSummary(Sts.StsResult result) {
        var weakest = result.weakestFirst();
        var summary = new ArrayList<String>();

        for (int i = 0; i < Math.min(3, weakest.size()); i++) {
            var theme = weakest.get(i);

            summary.add("%d %s %.1f%%".formatted(theme.theme(), theme.themeName(), theme.percent()));
        }

        return String.join(" | ", summary);
    }

    private static double percentOf(int part, int total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }
}
