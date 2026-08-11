package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
@SuppressWarnings("SameParameterValue")
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
        originalErr.print(capturedErr.toString(StandardCharsets.UTF_8));
    }

    // ---- Handshake ----

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void uci_onHandshake_reportsIdLinesThenUciok() {
        runHandler("uci\nquit\n")
                .expectEachOf(
                        "^id name myChess \\S+$",
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

    // ---- position sent as a bare current FEN (no moves list) ----

    /**
     * The UCI spec does not require a GUI to send
     * {@code position [startpos|fen <start>] moves <history>}; it may
     * equally send only the FEN of the <em>current</em> position with no
     * {@code moves} list on every position command. This test drives a
     * Chess960 game that way — a fresh full FEN per ply — and asserts the
     * engine keeps recognizing it as a 960 game (the value behind
     * {@link Game#is960()}, i.e. {@link Board#isChess960()}) after each
     * command.
     *
     * <p>The start RBBNKNQR is the worst case for FEN-only 960 detection:
     * its rooks sit on the standard a/h files and its king on e, so the
     * rook-file and king-file checks in {@link Board#isChess960Position}
     * never fire, and the only structural evidence — the non-standard back
     * rank — is read by that heuristic only from a pristine start position
     * ({@code seemsToBeStartPosition}). The pure FEN heuristic would
     * therefore flag the initial FEN but lose the flag after the very
     * first move, even though the untouched back rank still spells out a
     * non-standard setup.
     *
     * <p>The engine nevertheless keeps the flag, because the GUI's
     * {@code setoption name UCI_Chess960 value true} (sent below) is
     * authoritative: the handler then imports every position via
     * {@code Fen.importChess960FEN}, which forces the board's 960 flag
     * regardless of structure. The FEN heuristic is only the fallback for
     * when that option is absent (e.g. the REPL). This test guards exactly
     * that contract — once the option is set, bare-FEN play stays a 960
     * game for the whole session.
     *
     * <p>960 detection is not observable over the UCI protocol, so the
     * handler is stepped command-by-command and its internal board is read
     * through the package-private {@link UciHandler#getBoard()}.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void position_bareCurrentFenPerPly_keepsChess960Detection() {
        String start960Fen = "rbbnknqr/pppppppp/8/8/8/8/PPPPPPPP/RBBNKNQR w KQkq - 0 1";

        // Build a realistic sequence of "current position" FENs by playing
        // a few pawn moves on a shadow game and exporting the FEN after
        // each — exactly the full-position strings a GUI would send.
        var currentFens = new ArrayList<String>();
        var shadow = new Game(Game.standardConfig(), Fen.importFEN(start960Fen));
        try {
            currentFens.add(shadow.exportFEN());
            for (String uci : List.of("d2d4", "d7d5", "e2e3")) {
                shadow.makeMove(UciMoveParser.parse(uci, shadow.getBoard()));
                currentFens.add(shadow.exportFEN());
            }
        } finally {
            shadow.shutdown();
        }

        // Drive a single UCI session line-by-line and inspect the engine's board after each
        // command without spawning a search.
        var handler = new UciHandler(new MyChessEnv(), new BufferedReader(new StringReader("")));

        handler.handleLine("uci");
        handler.handleLine("setoption name UCI_Chess960 value true");
        handler.handleLine("ucinewgame");

        for (String fen : currentFens) {
            handler.handleLine("position fen " + fen);

            assertTrue(handler.getBoard().isChess960(),
                    "engine must still recognize a 960 game when the position arrives as a bare "
                            + "current FEN with no moves list; FEN: " + fen);
        }
    }

    // ---- outbound 960 castle formatter (currently broken — Phase 3 pending) ----

    //
    // With UCI_Chess960 set, castles must be emitted in king-captures-rook
    // form (e1h1 / e8h8 kingside, e1a1 / e8a8 queenside) — not the
    // king-destination form (e1g1, e1c1, …) — so strict 960-aware GUIs
    // accept them. UciMoveParser.toUci(int) currently takes only the packed
    // move and has no board / 960 context, so it always emits the
    // king-destination form regardless of the option.
    //
    // The four tests below cover all color × side combinations (W/B × K/Q)
    // by driving deterministic depth-7 searches in positions whose
    // principal variation reliably contains the corresponding castle. All
    // four are currently red — pinned to the remaining Phase 3
    // outbound-formatter work; cf. docs/Chess960-project.md.

    /** Italian-like position with both sides poised to castle kingside; used by the two kingside tests. */
    private static final String KINGSIDE_TEST_FEN =
            "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 0 1";

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void infoPv_whiteKingsideCastle_emittedInKingDestinationForm_violatesChess960OutboundContract() {
        var input = """
                uci
                setoption name UCI_Chess960 value true
                ucinewgame
                position fen %s
                go depth 7
                """.formatted(KINGSIDE_TEST_FEN);

        assertAnyPvContains(input, "e1h1", "white's kingside castle");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void infoPv_blackKingsideCastle_emittedInKingDestinationForm_violatesChess960OutboundContract() {
        var input = """
                uci
                setoption name UCI_Chess960 value true
                ucinewgame
                position fen %s
                go depth 7
                """.formatted(KINGSIDE_TEST_FEN);

        assertAnyPvContains(input, "e8h8", "black's kingside castle");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void infoPv_whiteQueensideCastle_emittedInKingDestinationForm_violatesChess960OutboundContract() {
        // f1 bishop blocks white's kingside castle, so white queenside-castles in the PV.
        var input = """
                uci
                setoption name UCI_Chess960 value true
                ucinewgame
                position fen r3kb1r/pppq1ppp/2npbn2/4p3/4P3/2NPBN2/PPPQ1PPP/R3KB1R w KQkq - 0 1
                go depth 7
                """;

        assertAnyPvContains(input, "e1a1", "white's queenside castle");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void infoPv_blackQueensideCastle_emittedInKingDestinationForm_violatesChess960OutboundContract() {
        // Same structural setup as the white queenside test, but black to move: the f8 bishop
        // blocks black's kingside castle, so black queenside-castles in the PV.
        var input = """
                uci
                setoption name UCI_Chess960 value true
                ucinewgame
                position fen r3kb1r/pppq1ppp/2npbn2/4p3/4P3/2NPBN2/PPPQ1PPP/R3KB1R b KQkq - 1 1
                go depth 7
                """;

        assertAnyPvContains(input, "e8a8", "black's queenside castle");
    }

    /**
     * Drive the UCI handler with {@code input}, scan every emitted
     * {@code info ... pv ...} line, and assert that at least one of them
     * contains {@code expectedCastleUci} (the king-captures-rook castle
     * token). Any-depth match is enough — the bug under test is the
     * <em>output format</em> of the castle, so wherever the engine includes
     * it in a PV, it must use the Chess960 form. {@code castleDescription}
     * is woven into the failure message for readability.
     */
    private void assertAnyPvContains(String input, String expectedCastleUci, String castleDescription) {
        var response = runHandler(input);

        var pvLines = response.lines().stream()
                .filter(l -> l.startsWith("info ") && l.contains(" pv "))
                .toList();

        if (pvLines.isEmpty()) {
            throw new AssertionError(
                    "no info pv lines emitted; full output:\n" + String.join("\n", response.lines()));
        }

        boolean anyMatch = pvLines.stream().anyMatch(l -> l.contains(expectedCastleUci));
        assertTrue(anyMatch,
                "at least one info pv must report " + castleDescription + " as " + expectedCastleUci
                        + " (king-captures-rook), not the king-destination form, when UCI_Chess960 is set. "
                        + "All info pv lines:\n" + String.join("\n", pvLines));
    }

    // ---- validatePv: position-mismatch guard ----

    /**
     * Regression test for the validatePv guard added in 2026-06-07 after a
     * cutechess SPRT run surfaced {@code Illegal PV move …} warnings: when
     * the engine's iteration listener still emits an {@code info pv …}
     * line after the UCI thread has already processed the next game's
     * {@code position fen …} command, the PV holds moves that were legal
     * in the *old* board but no longer in the *new* one. {@code validatePv}
     * detects exactly this mismatch and returns {@code false} so the
     * caller can suppress the outbound UCI line.
     */
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void validatePv_pvLegalInOriginalButNotCurrentBoard_returnsFalse() {
        var handler = new UciHandler(new MyChessEnv(),
                new BufferedReader(new StringReader("")));

        // Board A: after 1.e4 e5 2.Nf3 Nc6 3.Bb5 d6 4.O-O Nf6 5.Re1 — black to move
        // with a black knight on f6 and d7 empty, so Nf6-d7 is legal.
        handler.handleLine("position fen "
                + "r1bqkb1r/ppp2ppp/2np1n2/1B2p3/4P3/5N2/PPPP1PPP/RNBQR1K1 b kq - 5 5");
        int nf6d7 = Move.create(Board.f6, Board.d7, Board.empty, Move.typeNormal);
        assertTrue(handler.validatePv(new int[]{nf6d7}),
                "Nf6-d7 must validate in board A where f6 holds a black knight");

        // Board B: standard starting position — no knight on f6, the same PV
        // can no longer be replayed.
        handler.handleLine("position startpos");
        assertFalse(handler.validatePv(new int[]{nf6d7}),
                "Nf6-d7 must fail validation once the board has changed and f6 is empty");
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

    // ---- self-play ----

    /**
     * Drives 8 self-play plies from the start position over the UCI handler,
     * simulating what a GUI like cutechess does between moves:
     * {@code position startpos moves <accumulated>}, {@code go movetime N},
     * read {@code bestmove}, append, repeat.
     *
     * <p>Each emitted bestmove is replayed on a shadow {@link Game} so that an
     * illegal move at any ply surfaces as an {@link IllegalMoveException}
     * rather than slipping through unnoticed (the handler itself would just
     * log a stderr warning and keep going from a stale board).
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void selfPlay_eightPliesFromStartpos_producesOnlyLegalMoves() {
        final int plies = 8;
        final int movetimeMillis = 200;

        var shadow = new Game(Game.standardConfig(), Board.createNewGame());
        try {
            var moves = new StringBuilder();

            for (int ply = 1; ply <= plies; ply++) {
                capturedOut.reset();

                String posCmd = moves.isEmpty()
                        ? "position startpos\n"
                        : "position startpos moves " + moves + "\n";
                var response = runHandler(posCmd + "go movetime " + movetimeMillis + "\nquit\n");

                final int finalPly = ply;
                String bestmoveLine = response.lines().stream()
                        .filter(l -> l.startsWith("bestmove "))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "no bestmove emitted at ply " + finalPly
                                        + "; full output:\n" + String.join("\n", response.lines())));

                String uci = bestmoveLine.substring("bestmove ".length());
                assertTrue(uci.matches("[a-h][1-8][a-h][1-8][qrbn]?"),
                        "ill-formed bestmove at ply " + ply + ": '" + bestmoveLine + "'");

                MoveDescription md = UciMoveParser.parse(uci, shadow.getBoard());
                shadow.makeMove(md);

                if (!moves.isEmpty()) {
                    moves.append(' ');
                }
                moves.append(uci);
            }
        } finally {
            shadow.shutdown();
        }
    }

    /**
     * Variant of {@link #selfPlay_eightPliesFromStartpos_producesOnlyLegalMoves}
     * that runs all 8 plies through a <em>single</em> UCI handler — the way
     * cutechess or any real GUI does it — instead of spinning up a fresh
     * handler per move.
     *
     * <p>The handler runs in a virtual worker thread reading from a piped
     * stdin; the test thread writes commands into that pipe and reads
     * protocol output line-by-line from a second pipe redirected from
     * {@code System.out}. Between {@code go} commands the test blocks until
     * the {@code bestmove} line for the just-issued search arrives, then
     * appends and continues.
     *
     * <p>Beyond the legality check, the test asserts that all 8 {@code [move]}
     * stderr log lines emitted during the session share the same
     * {@code game=…} identifier — the marker that distinguishes one
     * persistent UCI session from a sequence of separate handler instances.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void selfPlay_eightPliesInSingleSession_keepsGameIdStable() throws Exception {
        final int plies = 8;
        final int movetimeMillis = 200;
        final int pipeBufferBytes = 64 * 1024;
        final long perPlyTimeoutMillis = 10_000L;
        final long pollIntervalMillis = 10L;

        // Only stdin goes through a pipe — the test thread is the sole writer
        // and stays alive for the whole session. Stdout deliberately re-uses
        // the @BeforeEach-supplied ByteArrayOutputStream and is polled
        // line-by-line: PipedInputStream tracks the writing thread and would
        // throw "Write end dead" the moment the search-executor thread
        // terminates between iterations, even while the watcher thread is
        // still actively writing.
        var stdinSink = new PipedOutputStream();
        var stdinSource = new PipedInputStream(stdinSink, pipeBufferBytes);
        var handlerStdin = new BufferedReader(new InputStreamReader(stdinSource, StandardCharsets.UTF_8));
        var testStdinWriter = new BufferedWriter(new OutputStreamWriter(stdinSink, StandardCharsets.UTF_8));

        Thread worker = Thread.ofVirtual().name("uci-session-test").start(() ->
                new UciHandler(new MyChessEnv(), handlerStdin).run());

        var shadow = new Game(Game.standardConfig(), Board.createNewGame());
        int[] outCursor = { 0 };
        try {
            var moves = new StringBuilder();

            for (int ply = 1; ply <= plies; ply++) {
                String posCmd = moves.isEmpty()
                        ? "position startpos\n"
                        : "position startpos moves " + moves + "\n";
                testStdinWriter.write(posCmd);
                testStdinWriter.write("go movetime " + movetimeMillis + "\n");
                testStdinWriter.flush();

                String bestmoveLine = pollForBestmove(capturedOut, outCursor,
                        perPlyTimeoutMillis, pollIntervalMillis);
                assertNotNull(bestmoveLine, "no bestmove emitted at ply " + ply);

                String uci = bestmoveLine.substring("bestmove ".length());
                assertTrue(uci.matches("[a-h][1-8][a-h][1-8][qrbn]?"),
                        "ill-formed bestmove at ply " + ply + ": '" + bestmoveLine + "'");

                MoveDescription md = UciMoveParser.parse(uci, shadow.getBoard());
                shadow.makeMove(md);

                if (!moves.isEmpty()) {
                    moves.append(' ');
                }
                moves.append(uci);
            }

            testStdinWriter.write("quit\n");
            testStdinWriter.flush();
            worker.join();

            // Stability of the gameId across the session is the discriminator
            // between this single-session test and the multi-handler variant.
            // `.*` prefix tolerates the leading timestamp that Log prepends.
            String stderr = capturedErr.toString(StandardCharsets.UTF_8);
            Pattern moveLogPattern = Pattern.compile(".*\\[move] game=(\\S+) .*");
            List<String> idsPerPly = stderr.lines()
                    .map(moveLogPattern::matcher)
                    .filter(Matcher::matches)
                    .map(m -> m.group(1))
                    .toList();
            assertEquals(plies, idsPerPly.size(),
                    "expected one [move] log line per ply, got " + idsPerPly.size());
            Set<String> uniqueIds = new HashSet<>(idsPerPly);
            assertEquals(1, uniqueIds.size(),
                    "expected one stable gameId across the session, got " + uniqueIds);

            // Every [move] must carry an elapsed=<ms> field for post-mortem
            // time-management analysis, and there must be a matching [go]
            // log line per ply (same count). Both lines tolerate the
            // timestamp prefix that Log now prepends.
            long elapsedFieldCount = stderr.lines()
                    .filter(l -> l.matches(".*\\[move] .*\\belapsed=\\d+.*"))
                    .count();
            assertEquals(plies, elapsedFieldCount,
                    "every [move] log line must carry an elapsed=<ms> field");

            long goLineCount = stderr.lines()
                    .filter(l -> l.matches(".*\\[go] game=\\S+ color=[WB] move=\\d+ .*budget=\\d+.*"))
                    .count();
            assertEquals(plies, goLineCount,
                    "expected one [go] log line per ply, got " + goLineCount);
            assertFalse(worker.isAlive(), "worker shouldn't be alive");
        } finally {
            shadow.shutdown();
            try {
                testStdinWriter.close();
            } catch (java.io.IOException _) {
                // Best-effort: pipe may already be closed by the handler's own shutdown.
            }
            worker.join(5_000L);
        }
    }

    /**
     * Poll {@code buf} starting at {@code cursor[0]} until a line starting
     * with {@code "bestmove "} appears or the timeout elapses. The cursor
     * is advanced past every line consumed (including non-matching ones)
     * so that the next call resumes correctly.
     *
     * @return the matched line (without trailing newline), or {@code null} on timeout
     */
    @SuppressWarnings("java:S2925")
    private static String pollForBestmove(ByteArrayOutputStream buf, int[] cursor,
                                          long timeoutMillis, long pollIntervalMillis)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        while (System.nanoTime() < deadlineNanos) {
            byte[] snapshot = buf.toByteArray();
            while (cursor[0] < snapshot.length) {
                int newlineIdx = indexOf(snapshot, (byte) '\n', cursor[0]);
                if (newlineIdx < 0) {
                    break; // partial line — wait for more bytes
                }
                String line = new String(snapshot, cursor[0],
                        newlineIdx - cursor[0], StandardCharsets.UTF_8).stripTrailing();
                cursor[0] = newlineIdx + 1;
                if (line.startsWith("bestmove ")) {
                    return line;
                }
            }

            //noinspection BusyWait
            Thread.sleep(pollIntervalMillis);
        }
        return null;
    }

    private static int indexOf(byte[] arr, byte target, int from) {
        for (int i = from; i < arr.length; i++) {
            if (arr[i] == target) {
                return i;
            }
        }
        return -1;
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
                        "^id name myChess \\S+$",
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
