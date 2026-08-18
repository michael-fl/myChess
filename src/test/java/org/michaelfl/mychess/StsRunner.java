package org.michaelfl.mychess;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

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
 * <h2>{@code --out <file>}: the report file, and resume</h2>
 *
 * <p>With {@code --out} the runner writes the report itself and <b>flushes after every
 * position</b>, and an existing file at that path is read first so the positions it already
 * holds are not measured again. Without it, behavior is as before: stdout only, no resume.
 *
 * <p>Both halves of that matter for a long run, and shell redirection gives neither.
 * {@code System.out} is block-buffered once redirected into a file, so roughly eighty
 * measured positions sit unwritten at any moment and a kill loses all of them — and there
 * would be nothing to resume from anyway. At depth 8 the full suite is half an hour and
 * survivable; at depth 10 it is six hours, which no longer fits between two interruptions.
 * Closing a laptop lid only suspends the process, but a battery running out during that
 * sleep does not.
 *
 * <p>The report file is its own progress store — no second format that could drift out of
 * step with it. {@link StreamingProgress} writes the per-position lines and
 * {@code readCompleted} parses them back; the format constant and its pattern sit next to
 * each other so a change to one without the other cannot go unnoticed. Resuming a file
 * measured against a *different* suite is refused rather than silently mixed, because a
 * label it does not recognize is reported as an error.
 *
 * <p>A resumed run's aggregate covers every position, measured now or earlier: the theme
 * table, totals and misses are computed over the union.
 *
 * @author Michael Fleischhauer
 */
public final class StsRunner {

    private static final String ALL_THEMES = "all";
    private static final String SEPARATOR = "===============================================================";
    private static final int MISSES_SHOWN = 40;

    /**
     * Option naming the report file. Its presence also switches resume on: an existing file
     * is read first and the positions it already holds are not measured again.
     */
    private static final String OUT_OPTION = "--out";

    private StsRunner() {
        // command-line entry point
    }

    /**
     * Run the suite and print the report.
     *
     * @param args {@code [depth] [theme] [limit] [--out <file>]}, see the class documentation
     */
    @SuppressWarnings("UnnecessaryModifier")
    public static void main(String[] args) throws IOException {
        Path out = outFile(args);
        var positional = positionalArgs(args);
        int depth = !positional.isEmpty() ? Integer.parseInt(positional.get(0)) : Sts.DEFAULT_DEPTH;
        String themeArg = positional.size() > 1 ? positional.get(1) : ALL_THEMES;
        // A limit of 0 -- or anything below 1 -- means "no limit", which is how the third
        // argument is documented in tools/run-sts.sh. Read literally it selected nothing and
        // the run exited immediately, which is exactly what happened the first time the
        // documented example was used.
        int limit = positional.size() > 2 ? unlimitedIfNotPositive(Integer.parseInt(positional.get(2)))
                : Integer.MAX_VALUE;

        var suite = Sts.loadSuite();
        var positions = select(suite, themeArg, limit);

        if (positions.isEmpty()) {
            System.out.printf(Locale.ROOT, "no positions match theme '%s'%n", themeArg);

            return;
        }

        var completed = out == null ? List.<Sts.PositionResult>of() : readCompleted(out, suite);
        var doneLabels = new HashSet<String>();

        for (Sts.PositionResult result : completed) {
            doneLabels.add(result.position().label());
        }

        var todo = positions.stream().filter(p -> !doneLabels.contains(p.label())).toList();

        try (var sink = new Report(out)) {
            sink.line(fmt("sts suite=%s depth=%d positions=%d%s", 
                    themeArg, depth, positions.size(),
                    completed.isEmpty() ? "" : fmt(" (resumed, %d already measured)", completed.size())));

            var progress = new StreamingProgress(positions.size(), completed.size(), sink);
            var measured = new ArrayList<>(completed);

            if (!todo.isEmpty()) {
                measured.addAll(Sts.run(depth, todo, progress::report).positions());
            }

            var result = aggregateAll(depth, measured);

            printThemes(result, sink);
            printTotals(result, sink);
            printMisses(result, sink);
        }
    }

    /**
     * Writes every report line to stdout and, when one was requested, to a file that is
     * flushed after each line.
     *
     * <p>The flush is what makes an interrupted run recoverable: {@code System.out} is
     * block-buffered once it is redirected into a file, so up to ~80 measured positions sit
     * unwritten at any moment and a kill loses all of them. See the resume note in the class
     * documentation.
     */
    private static final class Report implements AutoCloseable {

        private final BufferedWriter file;

        private Report(Path out) throws IOException {
            this.file = out == null ? null : Files.newBufferedWriter(out, CREATE, APPEND);
        }

        private void line(String text) {
            System.out.println(text);

            if (file == null) {
                return;
            }

            try {
                file.write(text);
                file.newLine();
                // Per line, not per block: the whole point of the file is that it survives
                // a lid closing mid-run.
                file.flush();
            } catch (IOException e) {
                throw new UncheckedIOException("failed writing the STS report", e);
            }
        }

        @Override
        public void close() throws IOException {
            if (file != null) {
                file.close();
            }
        }
    }

    /**
     * The per-position line format, and its inverse.
     *
     * <p>Kept adjacent on purpose: {@link StreamingProgress} writes this shape and
     * {@link #readCompleted} parses it back on resume, so a change to one that is not made to
     * the other would silently re-measure everything.
     *
     * <p>The label is captured non-greedily up to {@code " played "} rather than as
     * {@code \S+}: eleven of the fifteen theme names contain spaces, and a {@code \S+} label
     * matched only the four single-word themes when {@code tools/scan-sts-misses.py} first
     * did this — quietly dropping 60 of 87 positions.
     */
    private static final String POSITION_LINE =
            "%4d/%d  %-46s played %-5s pts %3d  nodes %,12d  time %6d ms";

    private static final Pattern POSITION_LINE_PATTERN = Pattern.compile(
            "^\\s*\\d+/\\d+\\s+(.+?)\\s+played\\s+(\\S+)\\s+pts\\s+(\\d+)\\s+nodes\\s+([\\d,]+)"
                    + "\\s+time\\s+(\\d+) ms\\s*$");

    /** Prints one line per searched position so a long run reports as it goes. */
    private static final class StreamingProgress {

        private final int total;
        private final Report sink;
        private int index;

        private StreamingProgress(int total, int alreadyDone, Report sink) {
            this.total = total;
            this.index = alreadyDone;
            this.sink = sink;
        }

        private void report(Sts.PositionResult result) {
            sink.line(fmt(POSITION_LINE, ++index, total, result.position().label(),
                    result.chosenMove(), result.points(), result.nodes(), result.timeMs()));
        }
    }

    /**
     * Read back the positions an earlier run of the same file already measured.
     *
     * <p>The report file is its own progress store — no second format that could drift out of
     * step with it. Lines that are not position lines (the header, the theme table, a footer
     * from an earlier completed run) are ignored.
     *
     * @param out   the report file; missing or empty means nothing was measured yet
     * @param suite the loaded suite, used to map a label back to its position
     * @return the results already on file, in file order
     * @throws Sts.StsException if a line names a position the suite does not contain
     */
    private static List<Sts.PositionResult> readCompleted(Path out, List<Sts.Position> suite)
            throws IOException {

        if (!Files.exists(out)) {
            return List.of();
        }

        var byLabel = HashMap.<String, Sts.Position>newHashMap(suite.size());

        for (Sts.Position position : suite) {
            byLabel.put(position.label(), position);
        }

        var completed = new ArrayList<Sts.PositionResult>();
        var seen = new HashSet<String>();

        for (String line : Files.readAllLines(out)) {
            var match = POSITION_LINE_PATTERN.matcher(line);

            if (!match.matches()) {
                continue;
            }

            String label = match.group(1).strip();
            Sts.Position position = byLabel.get(label);

            if (position == null) {
                throw new Sts.StsException("resume file names a position the suite does not have: "
                        + label + " (was it measured against a different suite file?)");
            }

            // A file resumed more than once can hold the same position twice; keep the first.
            if (!seen.add(label)) {
                continue;
            }

            String chosenMove = match.group(2);
            completed.add(new Sts.PositionResult(position, chosenMove,
                    Integer.parseInt(match.group(3)),
                    Long.parseLong(match.group(4).replace(",", "")),
                    Long.parseLong(match.group(5))));
        }

        return completed;
    }

    private static Sts.StsResult aggregateAll(int depth, List<Sts.PositionResult> measured) {
        long nodes = 0;
        long timeMs = 0;

        for (Sts.PositionResult result : measured) {
            nodes += result.nodes();
            timeMs += result.timeMs();
        }

        return Sts.aggregate(depth, measured, nodes, timeMs);
    }

    /** @return {@code limit}, or {@link Integer#MAX_VALUE} when it is not a positive count */
    private static int unlimitedIfNotPositive(int limit) {
        return limit < 1 ? Integer.MAX_VALUE : limit;
    }

    private static Path outFile(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if (OUT_OPTION.equals(args[i])) {
                return Path.of(args[i + 1]);
            }
        }

        return null;
    }

    private static List<String> positionalArgs(String[] args) {
        var positional = new ArrayList<String>();

        for (int i = 0; i < args.length; i++) {
            if (OUT_OPTION.equals(args[i])) {
                i++;
                continue;
            }

            positional.add(args[i]);
        }

        return positional;
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

    private static void printThemes(Sts.StsResult result, Report sink) {
        sink.line(SEPARATOR);
        sink.line(fmt("STS  %-44s %4s %14s %7s %6s %6s", "Theme", "pos", "score", "pct", "best", "miss"));

        for (var theme : result.themes()) {
            sink.line(fmt("%3d  %-44s %4d %6d/%-7d %6.1f%% %6d %6d", theme.theme(), theme.themeName(), theme.positions(),
                    theme.points(), theme.maxPoints(), theme.percent(),
                    theme.bestMoveHits(), theme.misses()));
        }
    }

    private static void printTotals(Sts.StsResult result, Report sink) {
        int positions = result.positions().size();

        sink.line(SEPARATOR);
        sink.line(fmt("Weakest themes : %s", weakestSummary(result)));
        sink.line(fmt("Positions      : %,d at depth %d", positions, result.depth()));
        sink.line(fmt("STS score      : %,d / %,d (%.1f %%)", result.points(), result.maxPoints(), result.percent()));
        sink.line(fmt("Best-move hits : %,d (%.1f %%)", result.bestMoveHits(), percentOf(result.bestMoveHits(), positions)));
        sink.line(fmt("Misses (0 pts) : %,d (%.1f %%)", result.misses(), percentOf(result.misses(), positions)));
        sink.line(fmt("Total time     : %,d ms", result.totalTimeMs()));
        sink.line(fmt("Nodes searched : %,d", result.totalNodes()));
        sink.line(fmt("NPS            : %,d", result.nps()));
        sink.line("");
        sink.line("Read the best/miss columns with the percentage: the same score can mean");
        sink.line("\"half-good everywhere\" or \"a third perfect, the rest off the list\".");
    }

    private static void printMisses(Sts.StsResult result, Report sink) {
        var missed = result.missedPositions();

        if (missed.isEmpty()) {
            return;
        }

        sink.line("");
        sink.line(fmt("Positions scoring zero (%d) — candidates for a characterization test:", missed.size()));

        int shown = Math.min(missed.size(), MISSES_SHOWN);

        for (int i = 0; i < shown; i++) {
            var missedResult = missed.get(i);

            sink.line(fmt("  %-30s played %-5s expected %-5s  %s", missedResult.position().label(), missedResult.chosenMove(),
                    missedResult.position().bestMove(), missedResult.position().fen()));
        }

        if (shown < missed.size()) {
            sink.line(fmt("  ... %d more not shown", missed.size() - shown));
        }
    }

    private static String weakestSummary(Sts.StsResult result) {
        var weakest = result.weakestFirst();
        var summary = new ArrayList<String>();

        for (int i = 0; i < Math.min(3, weakest.size()); i++) {
            var theme = weakest.get(i);

            summary.add(fmt("%d %s %.1f%%", theme.theme(), theme.themeName(), theme.percent()));
        }

        return String.join(" | ", summary);
    }

    /**
     * {@code String.format} pinned to {@link Locale#ROOT}.
     *
     * <p>Not {@code String.formatted}, which uses the default locale: on a German system that
     * turns every percentage into "62,6%" and makes the report file depend on where it was
     * produced. Same reasoning as the node-signature output in {@code docs/bench-history.md}.
     */
    private static String fmt(String format, Object... args) {
        return String.format(Locale.ROOT, format, args);
    }

    private static double percentOf(int part, int total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }
}
