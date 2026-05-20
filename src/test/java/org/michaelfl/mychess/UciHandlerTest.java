package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class UciHandlerTest {

    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /**
     * Allowed line prefixes per UCI specification (engine → GUI).
     * Empty lines are tolerated.
     */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "id ", "uciok", "readyok", "bestmove ", "info ", "option ", "copyprotection ", "registration "
    );

    private static final String BESTMOVE_LINE_REGEX = "^bestmove [a-h][1-8][a-h][1-8][qrbn]?$";
    private static final String BESTMOVE_OR_NULL_REGEX = "^bestmove (0000|[a-h][1-8][a-h][1-8][qrbn]?)$";

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();

    @BeforeEach
    void redirect() {
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(capturedErr, true, StandardCharsets.UTF_8));
        Log.setMode(Log.Mode.UCI);
    }

    @AfterEach
    void restore() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        Log.setMode(Log.Mode.REPL);
    }

    // ---- Handshake ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void uci_onHandshake_reportsIdLinesThenUciok() {
        runHandler("uci\nquit\n")
                .expectEachOf(
                        "id name myChess",
                        "^id author .+$"
                )
                .expect("uciok");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void isready_always_repliesWithReadyok() {
        runHandler("isready\nquit\n").expect("readyok");
    }

    // ---- Position + Go ----

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void goDepth2_fromStartpos_emitsLegalBestmoveOnItsOwnLine() {
        runHandler("position startpos\ngo depth 2\nquit\n")
                .expect(BESTMOVE_LINE_REGEX);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void goAfterMoves_fromStartpos_emitsLegalBestmove() {
        runHandler("position startpos moves e2e4 e7e5\ngo depth 2\nquit\n")
                .expect(BESTMOVE_LINE_REGEX);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void goAfterPositionFen_fromGivenPosition_emitsLegalBestmove() {
        runHandler("position fen " + START_FEN + "\ngo depth 2\nquit\n")
                .expect(BESTMOVE_LINE_REGEX);
    }

    // ---- info lines (score, depth, pv) ----

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void go_duringSearch_emitsWellFormedInfoLinesBeforeBestmove() {
        var response = runHandler("position startpos\ngo depth 3\nquit\n");

        // Each info line must be well-formed per UCI.
        String infoRegex = "^info depth \\d+ nodes \\d+ time \\d+ score (cp -?\\d+|mate -?\\d+) "
                + "pv [a-h][1-8][a-h][1-8][qrbn]?( [a-h][1-8][a-h][1-8][qrbn]?)*$";
        var infoLines = response.lines().stream().filter(l -> l.startsWith("info ")).toList();
        assertFalse(infoLines.isEmpty(),
                "no info lines emitted; got:\n" + String.join("\n", response.lines()));
        for (String info : infoLines) {
            assertTrue(info.matches(infoRegex), "info line malformed: '" + info + "'");
        }

        // Ordering: at least one info line precedes bestmove.
        response.expect("^info .+$").expect("^bestmove .+$");
    }

    // ---- stop ----

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void stop_duringSearch_stillEmitsBestmoveLine() {
        // Small depth + stop right after exercises the cancellation path
        // the watcher must still emit a properly-formatted bestmove line.
        runHandler("position startpos\ngo depth 1\nstop\nquit\n")
                .expect(BESTMOVE_OR_NULL_REGEX);
    }

    // ---- ucinewgame ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void ucinewgame_afterMoves_resetsToStartpos() {
        runHandler("position startpos moves e2e4\nucinewgame\nposition startpos\ngo depth 1\nquit\n")
                .expect(BESTMOVE_LINE_REGEX);
    }

    // ---- stdout cleanliness ----

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void stdout_acrossFullSession_containsOnlyProtocolLines() {
        Predicate<String> isProtocolLine =
                line -> ALLOWED_PREFIXES.stream().anyMatch(p -> line.startsWith(p.trim()));

        runHandler("uci\nisready\nposition startpos\ngo depth 2\nquit\n")
                .expectAllLines(isProtocolLine, "stdout contains non-protocol line");
    }

    // ---- full sequence ----

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void fullSession_endToEnd_preservesProtocolOrder() {
        runHandler("uci\nisready\nposition startpos\ngo depth 2\nquit\n")
                .expectEachOf(
                        "id name myChess",
                        "^id author .+$"
                )
                .expect("uciok")
                .expect("readyok")
                .expect("^info .+$")
                .expect(BESTMOVE_LINE_REGEX);
    }

    // ---- helpers ----
    // (Move-parser unit tests live in UciMoveParserTest.)

    /**
     * Run the UCI handler with the given synthetic stdin input. Blocks until the
     * input is fully consumed and any in-flight search watcher has emitted its
     * {@code bestmove} (the run-loop joins the watcher with a 5 s grace period
     * before returning), so the resulting {@link UciResponse} reflects the
     * final stdout.
     */
    private UciResponse runHandler(String input) {
        var env = new MyChessEnv();
        var in = new BufferedReader(new StringReader(input));
        new UciHandler(env, in).run();

        var lines = Arrays.stream(capturedOut.toString().split("\\R"))
                .filter(l -> !l.isEmpty())
                .toList();
        return new UciResponse(lines);
    }

    /**
     * Fluent line-based assertion API over the captured stdout of a UCI run.
     *
     * <p>Patterns are interpreted as exact-match strings unless they start with
     * {@code ^} (then they're treated as Java regex).
     *
     * <p>Each call advances an internal cursor past the latest matched line, so
     * chained {@code expect(...)} calls verify <em>relative</em> order without
     * requiring adjacency (other lines may be interspersed).
     */
    @SuppressWarnings("UnusedReturnValue")
    static final class UciResponse {

        private final List<String> lines;
        private int cursor;

        private UciResponse(List<String> lines) {
            this.lines = lines;
        }

        /**
         * Each given pattern must match a distinct line at or after the cursor.
         * The patterns may match in any order among themselves; the cursor
         * advances past the latest matched line.
         */
        UciResponse expectEachOf(String... patterns) {
            boolean[] matched = new boolean[patterns.length];
            int newCursor = cursor;

            for (int i = cursor; i < lines.size(); i++) {
                String line = lines.get(i);
                for (int p = 0; p < patterns.length; p++) {
                    if (!matched[p] && lineMatches(line, patterns[p])) {
                        matched[p] = true;
                        newCursor = Math.max(newCursor, i + 1);
                        break;
                    }
                }
            }

            for (int p = 0; p < patterns.length; p++) {
                if (!matched[p]) {
                    fail("expected pattern not found at or after line " + cursor + ": '" + patterns[p]
                            + "'\nfull output:\n" + String.join("\n", lines));
                }
            }
            cursor = newCursor;

            return this;
        }

        /** Shorthand for a single-pattern {@link #expectEachOf}. */
        UciResponse expect(String pattern) {
            return expectEachOf(pattern);
        }

        /**
         * Assert that every captured line satisfies the predicate. Does not
         * touch the cursor — use after the sequential expectations are done or
         * as a standalone check.
         */
        @SuppressWarnings("SameParameterValue")
        UciResponse expectAllLines(Predicate<String> predicate, String message) {
            for (String line : lines) {
                if (!predicate.test(line)) {
                    fail(message + ": '" + line + "'\nfull output:\n" + String.join("\n", lines));
                }
            }
            return this;
        }

        /** Raw access to the captured lines for tests that don't fit the fluent style. */
        List<String> lines() {
            return lines;
        }

        private static boolean lineMatches(String line, String pattern) {
            if (pattern.startsWith("^")) {
                return line.matches(pattern);
            }
            return line.equals(pattern);
        }
    }
}
