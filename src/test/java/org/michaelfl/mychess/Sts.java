package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Runs the <a href="https://www.chessprogramming.org/Strategic_Test_Suite">Strategic
 * Test Suite</a> (STS) and scores it per theme, so the weakest evaluation component
 * can be named rather than guessed at.
 *
 * <p>The suite holds 1188 positions in 15 themes. Every position carries up to ten
 * candidate moves annotated by Stockfish 15 together with a point value each, the
 * best move being worth 100. myChess earns the value of whichever candidate it
 * plays, or zero if it plays none of them; the score is the sum over the suite
 * divided by the sum of the per-position maxima. Partial credit is the point:
 * a move worth 46 is a different diagnosis from one worth 1, and binary
 * best-move-only scoring throws that resolution away.
 *
 * <h2>Fixed depth, not fixed time</h2>
 *
 * <p>Every position is searched to a fixed depth with a 24 h per-move budget
 * ({@code Bench.INFINITE_MILLIS}), so the score is reproducible and independent of
 * machine speed and load. That budget is also what makes the run safe from
 * {@code PositionSearch}'s skip-hopeless-iteration heuristic: the heuristic compares
 * an estimated iteration cost against the remaining budget, and against 24 h no
 * estimate can win, so it never fires. Do not "fix" this by touching
 * {@code DisableSkipHeuristicExtension} — that extension is registered only under
 * JUnit, so a {@code main()} run would not see it anyway, and this runner does not
 * need it.
 *
 * <p>Two consequences of the fixed depth are easy to miss. Published STS ratings
 * use fixed <em>time</em>, so <b>myChess's number is not comparable to them</b> —
 * only to another myChess run at the same depth. And the quantity measured is
 * "move quality at depth N", evaluation <em>and</em> search together: a search
 * change that surfaces a different move at the same depth moves the score too.
 *
 * <h2>Usage</h2>
 *
 * <pre>
 * var result = Sts.run(Sts.DEFAULT_DEPTH);
 * for (var theme : result.weakestFirst()) {
 *     System.out.printf("%-45s %5.1f%%%n", theme.theme(), theme.percent());
 * }
 * </pre>
 *
 * <p>{@link StsRunner} is the command-line front end and {@code tools/run-sts.sh}
 * launches it; this class does no printing. The full suite at depth 8 takes about
 * half an hour, so it is a measurement tool run by hand — not a graded test. See
 * {@code docs/sts-history.md} for the policy, including why a run is only worth
 * doing when {@code bench}'s node signature moved.
 *
 * @author Michael Fleischhauer
 */
final class Sts {

    /**
     * Default search depth, matching {@link Bench#DEFAULT_DEPTH}.
     *
     * <p>Eight plies sits in the depth 6-8 resolution band the roadmap calibrates
     * myChess to ({@code docs/roadmap-backlog.md} § 12.10.2) and is the depth the
     * king-safety regressions in {@code EngineTest} are written at, so the STS
     * number and those cases talk about the same engine. The depth must stay
     * frozen across releases or the per-release series is not comparable.
     */
    static final int DEFAULT_DEPTH = 8;

    /** Classpath location of the frozen suite (LAN v6, 1188 positions). */
    static final String SUITE_RESOURCE = "/sts/STS1-STS15_LAN_v6.epd";

    /** Number of positions in the frozen suite. */
    static final int SUITE_SIZE = 1188;

    /** Number of themes in the suite; also the highest valid suite number. */
    static final int THEME_COUNT = 15;

    /** Theme 11, the one that speaks to the open king-safety defect family. */
    static final int KING_ACTIVITY_THEME = 11;

    /** Point value of the best move in every position. */
    static final int BEST_MOVE_POINTS = 100;

    /**
     * Halfmove-clock and fullmove-number fields appended to every EPD position.
     *
     * <p>EPD ships a four-field FEN while {@code Fen.importFEN} requires all six,
     * so the counters have to be supplied. They are irrelevant here: no STS
     * position is near the fifty-move boundary. Same trick as
     * {@code CombinedTexelData}, which reads the Zurichess EPD dialect.
     */
    private static final String EPD_COUNTER_SUFFIX = " 0 1";

    private static final String BEST_MOVE_TAG = "bm";
    private static final String ID_TAG = "id";
    private static final String POINTS_TAG = "c8";
    private static final String CANDIDATES_TAG = "c9";
    private static final String SAN_CANDIDATES_TAG = "c7";

    /** Length of a candidate move in the {@code c9} operation, e.g. {@code f4f5}. */
    private static final int CANDIDATE_LENGTH = 4;

    /** Upper bound for a single search; a depth-8 position needs seconds, not hours. */
    private static final int SEARCH_TIMEOUT_HOURS = 1;

    /**
     * One EPD record: the position plus its scored candidate moves.
     *
     * @param fen        complete six-field FEN, ready for {@code Fen.importFEN}
     * @param id         the raw {@code id} operation, e.g. {@code STS(v1.0) Undermine.001}
     * @param theme      theme number 1-15, parsed from the version in {@code id}
     * @param themeName  theme name, parsed from {@code id}
     * @param number     position number within the theme
     * @param candidates candidate moves in from-to notation, best move first
     * @param points     point value per candidate, positionally parallel to
     *                   {@code candidates}, descending, first always 100
     */
    record Position(String fen, String id, int theme, String themeName, int number,
                    List<String> candidates, List<Integer> points) {

        /** @return the point value of {@code uciMove}, or 0 if it is not a candidate */
        int pointsFor(String uciMove) {
            int index = candidates.indexOf(uciMove);

            return index < 0 ? 0 : points.get(index);
        }

        /** @return the point value of the best move; 100 for every suite position */
        int maxPoints() {
            return points.getFirst();
        }

        /** @return the best move in from-to notation */
        String bestMove() {
            return candidates.getFirst();
        }

        /** @return a short human-readable label, e.g. {@code Undermine.001} */
        String label() {
            return "%s.%03d".formatted(themeName, number);
        }
    }

    /**
     * Outcome of searching one position.
     *
     * @param position    the position that was searched
     * @param chosenMove  what myChess played, in from-to notation
     * @param points      the credit earned, 0-100
     * @param nodes       nodes visited
     * @param timeMs      wall-clock time for this position
     */
    record PositionResult(Position position, String chosenMove, int points, long nodes, long timeMs) {

        /** @return whether myChess found the annotated best move */
        boolean best() {
            return points == position.maxPoints();
        }

        /** @return whether myChess played a move outside the candidate list */
        boolean miss() {
            return points == 0;
        }
    }

    /**
     * Aggregate over one theme.
     *
     * @param theme        theme number 1-15
     * @param themeName    theme name as spelled in the suite
     * @param positions    positions in this theme
     * @param points       points earned
     * @param maxPoints    points available
     * @param bestMoveHits positions where the annotated best move was found
     * @param misses       positions scoring zero
     */
    record ThemeScore(int theme, String themeName, int positions, int points,
                      int maxPoints, int bestMoveHits, int misses) {

        /** @return points earned as a percentage of points available */
        double percent() {
            return maxPoints == 0 ? 0.0 : 100.0 * points / maxPoints;
        }
    }

    /**
     * Aggregate over a whole run.
     *
     * <p>Read {@link #bestMoveHits()} and {@link #misses()} alongside
     * {@link #percent()}: a bare percentage cannot distinguish "half-good
     * everywhere" from "a third perfect, the rest outside the candidate list",
     * and those are different diagnoses.
     *
     * @param depth        the fixed search depth used
     * @param positions    per-position outcomes in suite order
     * @param themes       per-theme aggregates ordered by theme number
     * @param points       points earned
     * @param maxPoints    points available
     * @param bestMoveHits positions where the annotated best move was found
     * @param misses       positions scoring zero
     * @param totalNodes   nodes visited across the run
     * @param totalTimeMs  wall-clock time across the run
     */
    record StsResult(int depth, List<PositionResult> positions, List<ThemeScore> themes,
                     int points, int maxPoints, int bestMoveHits, int misses,
                     long totalNodes, long totalTimeMs) {

        /** @return points earned as a percentage of points available */
        double percent() {
            return maxPoints == 0 ? 0.0 : 100.0 * points / maxPoints;
        }

        /** @return nodes per second across the whole run (0 when no time elapsed) */
        long nps() {
            return totalTimeMs == 0 ? 0 : totalNodes * 1_000L / totalTimeMs;
        }

        /** @return the themes, worst score first — the ranked answer to "what is weakest?" */
        List<ThemeScore> weakestFirst() {
            var sorted = new ArrayList<>(themes);
            sorted.sort((a, b) -> Double.compare(a.percent(), b.percent()));

            return List.copyOf(sorted);
        }

        /** @return the positions that scored zero, the feed for new characterization tests */
        List<PositionResult> missedPositions() {
            return positions.stream().filter(PositionResult::miss).toList();
        }
    }

    /**
     * Signals a malformed suite, a missing resource, or a failed search.
     *
     * <p>Deliberately loud where {@code CombinedTexelData} skips: a silently
     * dropped position changes the denominator, and then two builds' scores are no
     * longer comparable — the one property a scored benchmark has to guarantee.
     */
    static final class StsException extends RuntimeException {

        StsException(String message) {
            super(message);
        }

        StsException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private Sts() {
        // static utility
    }

    /** @return every position of the frozen suite, fully validated */
    static List<Position> loadSuite() {
        return loadSuite(SUITE_RESOURCE);
    }

    /**
     * Load and validate an EPD suite from the classpath.
     *
     * @param resource absolute classpath path, e.g. {@link #SUITE_RESOURCE}
     * @return the parsed positions in file order
     * @throws StsException if the resource is missing or any line is malformed
     */
    static List<Position> loadSuite(String resource) {
        InputStream stream = Sts.class.getResourceAsStream(resource);

        if (stream == null) {
            throw new StsException("STS resource not found on classpath: " + resource);
        }

        var lines = new ArrayList<String>();

        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();

                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read STS resource " + resource, e);
        }

        return parse(lines);
    }

    /**
     * Parse and validate EPD lines.
     *
     * <p>Loading is eager and complete before any search starts: a half-hour run
     * must not die at position 900 on a parse bug.
     *
     * @param lines EPD lines without surrounding whitespace
     * @return the parsed positions in input order
     * @throws StsException if any line is malformed
     */
    static List<Position> parse(List<String> lines) {
        var positions = new ArrayList<Position>(lines.size());

        for (int i = 0; i < lines.size(); i++) {
            positions.add(parseLine(lines.get(i), i + 1));
        }

        return List.copyOf(positions);
    }

    /**
     * Parse and validate a single EPD line.
     *
     * @param epdLine    the line, without surrounding whitespace
     * @param lineNumber one-based line number, used in error messages
     * @return the parsed position
     * @throws StsException if the line is malformed or its FEN is not importable
     */
    static Position parseLine(String epdLine, int lineNumber) {
        int bestMoveAt = epdLine.indexOf(" " + BEST_MOVE_TAG + " ");

        if (bestMoveAt < 0) {
            throw malformed(lineNumber, "no ' bm ' operation", epdLine);
        }

        String fen = epdLine.substring(0, bestMoveAt).strip() + EPD_COUNTER_SUFFIX;
        String bestMoveSan = untilSemicolon(epdLine, bestMoveAt, lineNumber);
        String id = tagValue(epdLine, ID_TAG, lineNumber);
        var candidates = List.of(tagValue(epdLine, CANDIDATES_TAG, lineNumber).split("\\s+"));
        var sanCandidates = List.of(tagValue(epdLine, SAN_CANDIDATES_TAG, lineNumber).split("\\s+"));
        var points = parsePoints(tagValue(epdLine, POINTS_TAG, lineNumber), lineNumber, epdLine);

        validateFen(fen, lineNumber, epdLine);
        validateCandidates(candidates, sanCandidates, points, bestMoveSan, lineNumber, epdLine);

        return new Position(fen, id, themeOf(id, lineNumber, epdLine), themeNameOf(id, lineNumber, epdLine),
                numberOf(id, lineNumber, epdLine), candidates, points);
    }

    /**
     * Restrict a suite to one theme.
     *
     * @param all   the loaded suite
     * @param theme theme number 1-15
     * @return the positions belonging to {@code theme}, in suite order
     */
    static List<Position> filterByTheme(List<Position> all, int theme) {
        return all.stream().filter(p -> p.theme() == theme).toList();
    }

    /** @return the whole suite searched at {@code depth} */
    static StsResult run(int depth) {
        return run(depth, loadSuite(), result -> { });
    }

    /** @return {@code positions} searched at {@code depth} */
    static StsResult run(int depth, List<Position> positions) {
        return run(depth, positions, result -> { });
    }

    /**
     * Search every position at a fixed depth and score the chosen moves.
     *
     * <p>Structurally the same loop as {@link Bench#run}, with one addition: the
     * chosen move is kept rather than discarded. Each position gets a cleared
     * transposition table so the result is order-independent, and the engine
     * environment carries no opening book so every position is really searched.
     *
     * @param depth     fixed search depth in plies
     * @param positions the positions to search
     * @param progress  called after each position, so a long run can report as it
     *                  goes; pass a no-op when the output is not wanted
     * @return per-position outcomes and the aggregates over them
     * @throws StsException if a search fails or returns no move
     */
    static StsResult run(int depth, List<Position> positions, Consumer<PositionResult> progress) {
        var tt = TranspositionTable.getDefaultInstance();
        var env = new MyChessEnv(); // no opening book -> every position is searched
        ChessEngine.resetIterationTimings();

        var results = new ArrayList<PositionResult>(positions.size());
        long totalNodes = 0;
        long totalTimeMs = 0;

        for (Position position : positions) {
            var result = search(position, depth, tt, env);

            results.add(result);
            totalNodes += result.nodes();
            totalTimeMs += result.timeMs();
            progress.accept(result);
        }

        return aggregate(depth, results, totalNodes, totalTimeMs);
    }

    /**
     * Aggregate per-position outcomes into per-theme scores and run totals.
     *
     * <p>Themes are keyed by <b>number</b>, not by name: theme 3 appears in the
     * suite under two orderings of the same name
     * ({@code Knight Outposts/Repositioning/Centralization} 85 times and
     * {@code Knight Outposts/Centralization/Repositioning} once), and keying by
     * name would split it into two rows. The display name is taken from the first
     * position seen for that theme, which also gives the report a deterministic
     * theme order without any name table to maintain.
     *
     * @param depth        the depth the positions were searched at
     * @param results      per-position outcomes
     * @param totalNodes   nodes visited across the run
     * @param totalTimeMs  wall-clock time across the run
     * @return the aggregate
     */
    static StsResult aggregate(int depth, List<PositionResult> results, long totalNodes, long totalTimeMs) {
        var byTheme = LinkedHashMap.<Integer, ThemeScore>newLinkedHashMap(THEME_COUNT);
        int points = 0;
        int maxPoints = 0;
        int bestMoveHits = 0;
        int misses = 0;

        for (PositionResult result : results) {
            Position position = result.position();
            ThemeScore current = byTheme.get(position.theme());

            if (current == null) {
                current = new ThemeScore(position.theme(), position.themeName(), 0, 0, 0, 0, 0);
            }

            byTheme.put(position.theme(), new ThemeScore(
                    current.theme(),
                    current.themeName(),
                    current.positions() + 1,
                    current.points() + result.points(),
                    current.maxPoints() + position.maxPoints(),
                    current.bestMoveHits() + (result.best() ? 1 : 0),
                    current.misses() + (result.miss() ? 1 : 0)));

            points += result.points();
            maxPoints += position.maxPoints();
            bestMoveHits += result.best() ? 1 : 0;
            misses += result.miss() ? 1 : 0;
        }

        var themes = new ArrayList<>(byTheme.values());
        themes.sort((a, b) -> Integer.compare(a.theme(), b.theme()));

        return new StsResult(depth, List.copyOf(results), List.copyOf(themes),
                points, maxPoints, bestMoveHits, misses, totalNodes, totalTimeMs);
    }

    private static PositionResult search(Position position, int depth, TranspositionTable tt, MyChessEnv env) {
        Board board = Fen.importFEN(position.fen());

        // Fresh hash per position so the score is order-independent and
        // reproducible across runs and builds.
        tt.clear();

        var engineConfig = new EngineConfig.Builder()
                .maxDepth(depth)
                .millisPerMove(Bench.INFINITE_MILLIS)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
        var game = new Game(new GameConfig(MyChessEngine.class, engineConfig), board);

        var nodes = new AtomicLong();
        long startMs = System.currentTimeMillis();
        MoveAndWeight chosen;

        try {
            chosen = game.getEngine()
                    .nextMoveAsync(env, info -> nodes.set(info.nodes()))
                    .getResult(SEARCH_TIMEOUT_HOURS, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StsException("STS search interrupted at " + position.id(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new StsException("STS search failed at " + position.id() + " (" + position.fen() + ")", e);
        } finally {
            game.shutdown();
        }

        if (chosen.move() == 0) {
            throw new StsException("engine returned no move at " + position.id() + " (" + position.fen() + ")");
        }

        // The search works on a copy of the board, so this one still holds the
        // position the candidate moves were annotated against.
        String uciMove = UciMoveParser.toUci(chosen.move(), board);

        return new PositionResult(position, uciMove, position.pointsFor(uciMove),
                nodes.get(), System.currentTimeMillis() - startMs);
    }

    private static List<Integer> parsePoints(String rawPoints, int lineNumber, String epdLine) {
        var points = new ArrayList<Integer>();

        for (String token : rawPoints.split("\\s+")) {
            try {
                points.add(Integer.valueOf(token));
            } catch (NumberFormatException e) {
                throw malformed(lineNumber, "non-numeric point value '" + token + "'", epdLine);
            }
        }

        return List.copyOf(points);
    }

    private static void validateFen(String fen, int lineNumber, String epdLine) {
        try {
            Fen.importFEN(fen);
        } catch (RuntimeException e) {
            throw malformed(lineNumber, "FEN not importable: " + fen, epdLine);
        }
    }

    private static void validateCandidates(List<String> candidates, List<String> sanCandidates,
                                           List<Integer> points, String bestMoveSan,
                                           int lineNumber, String epdLine) {
        if (candidates.isEmpty()) {
            throw malformed(lineNumber, "empty candidate list", epdLine);
        }

        if (candidates.size() != points.size() || candidates.size() != sanCandidates.size()) {
            throw malformed(lineNumber, "c7/c8/c9 token counts differ (%d/%d/%d)"
                    .formatted(sanCandidates.size(), points.size(), candidates.size()), epdLine);
        }

        if (points.getFirst() != BEST_MOVE_POINTS) {
            throw malformed(lineNumber, "c8 does not start at " + BEST_MOVE_POINTS, epdLine);
        }

        for (int i = 1; i < points.size(); i++) {
            if (points.get(i) > points.get(i - 1)) {
                throw malformed(lineNumber, "c8 is not descending at index " + i, epdLine);
            }
        }

        for (String candidate : candidates) {
            if (candidate.length() != CANDIDATE_LENGTH) {
                throw malformed(lineNumber, "candidate '" + candidate + "' is not "
                        + CANDIDATE_LENGTH + " characters", epdLine);
            }
        }

        // bm is SAN while c9 is from-to notation, so bm cannot be compared against
        // c9 directly. It does equal c7's first token on every suite line, which
        // makes this a cheap check that the two candidate lists line up.
        if (!bestMoveSan.equals(sanCandidates.getFirst())) {
            throw malformed(lineNumber, "bm '" + bestMoveSan + "' is not c7's first token '"
                    + sanCandidates.getFirst() + "'", epdLine);
        }
    }

    private static int themeOf(String id, int lineNumber, String epdLine) {
        int versionAt = id.indexOf("(v");
        int dotAt = versionAt < 0 ? -1 : id.indexOf('.', versionAt);

        if (dotAt < 0) {
            throw malformed(lineNumber, "id carries no '(v<theme>.' version: " + id, epdLine);
        }

        int theme = parsePositiveInt(id.substring(versionAt + 2, dotAt), lineNumber, epdLine, id);

        if (theme < 1 || theme > THEME_COUNT) {
            throw malformed(lineNumber, "theme " + theme + " outside 1-" + THEME_COUNT, epdLine);
        }

        return theme;
    }

    private static String themeNameOf(String id, int lineNumber, String epdLine) {
        int nameAt = id.indexOf(") ");
        // The theme name itself may contain dots in principle, and the version
        // certainly does, so the position number is whatever follows the LAST dot.
        int lastDot = id.lastIndexOf('.');

        if (nameAt < 0 || lastDot < nameAt) {
            throw malformed(lineNumber, "id carries no ') <theme>.<number>' part: " + id, epdLine);
        }

        return id.substring(nameAt + 2, lastDot);
    }

    private static int numberOf(String id, int lineNumber, String epdLine) {
        int lastDot = id.lastIndexOf('.');

        if (lastDot < 0) {
            throw malformed(lineNumber, "id carries no position number: " + id, epdLine);
        }

        return parsePositiveInt(id.substring(lastDot + 1), lineNumber, epdLine, id);
    }

    private static int parsePositiveInt(String text, int lineNumber, String epdLine, String id) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw malformed(lineNumber, "'" + text + "' in id '" + id + "' is not a number", epdLine);
        }
    }

    /**
     * Read an EPD operation's quoted operand.
     *
     * <p>No operand in the suite contains an embedded quote, so scanning to the
     * next quote is sufficient and needs no escape handling.
     */
    private static String tagValue(String epdLine, String tag, int lineNumber) {
        String marker = " " + tag + " \"";
        int start = epdLine.indexOf(marker);

        if (start < 0) {
            throw malformed(lineNumber, "no '" + tag + "' operation", epdLine);
        }

        int valueAt = start + marker.length();
        int end = epdLine.indexOf('"', valueAt);

        if (end < 0) {
            throw malformed(lineNumber, "unterminated '" + tag + "' operand", epdLine);
        }

        return epdLine.substring(valueAt, end).strip();
    }

    private static String untilSemicolon(String epdLine, int tagAt, int lineNumber) {
        int end = epdLine.indexOf(';', tagAt);

        if (end < 0) {
            throw malformed(lineNumber, "unterminated 'bm' operation", epdLine);
        }

        return epdLine.substring(tagAt + BEST_MOVE_TAG.length() + 2, end).strip();
    }

    private static StsException malformed(int lineNumber, String problem, String epdLine) {
        return new StsException("malformed EPD at line %d: %s%n  %s".formatted(lineNumber, problem, epdLine));
    }
}
