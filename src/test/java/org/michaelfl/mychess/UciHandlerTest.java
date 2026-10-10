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
@SuppressWarnings({"SameParameterValue", "BusyWait", "java:S2925"})
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

    /** A well-formed UCI {@code info} line with depth, nodes, time, score and PV. */
    private static final Pattern INFO_LINE_PATTERN = Pattern.compile(
            "^info depth \\d+ nodes \\d+ time \\d+ score (cp|mate) -?\\d+ "
                    + "pv [a-h][1-8][a-h][1-8][qrbn]?( [a-h][1-8][a-h][1-8][qrbn]?)*$");

    /** A move in UCI long algebraic notation, e.g. {@code e2e4} or {@code e7e8q}. */
    private static final Pattern UCI_MOVE_PATTERN = Pattern.compile("[a-h][1-8][a-h][1-8][qrbn]?");

    /** The game id in a {@code [move]} log line. */
    private static final Pattern MOVE_LOG_GAME_ID_PATTERN = Pattern.compile("\\[move] game=(\\S+) ");

    /** The {@code elapsed=<ms>} field of a {@code [move]} log line. */
    private static final Pattern ELAPSED_FIELD_PATTERN = Pattern.compile("\\belapsed=\\d+");

    /** The fixed head of a {@code [go]} log line. */
    private static final Pattern GO_LOG_HEAD_PATTERN = Pattern.compile("\\[go] game=\\S+ color=[WB] move=\\d+ ");

    /** The {@code budget=<ms>} field of a {@code [go]} log line. */
    private static final Pattern BUDGET_FIELD_PATTERN = Pattern.compile("\\bbudget=(\\d+)");

    /** How long a test waits for a {@code go depth 2} watcher to store its score. */
    private static final long SEARCH_STORE_WAIT_MS = 10_000;

    /** Ply of the stored search result in the {@code previousOwnScoreCentiFor} tests. */
    private static final int STORED_PLY = 10;

    /** Score of the stored search result in the {@code previousOwnScoreCentiFor} tests, in centipawns. */
    private static final int STORED_SCORE = 35;

    private static final String GO_DEPTH_2 = "go depth 2";
    private static final String POSITION_START = "position startpos";
    private static final String POSITION_AFTER_TWO_PLIES = "position startpos moves e2e4 e7e5";
    private static final String POSITION_AFTER_FOUR_PLIES = "position startpos moves e2e4 e7e5 g1f3 b8c6";

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
        var infoLines = response.lines().stream().filter(l -> l.startsWith("info ")).toList();
        assertFalse(infoLines.isEmpty(),
                "no info lines emitted; got:\n" + String.join("\n", response.lines()));
        for (String info : infoLines) {
            assertTrue(INFO_LINE_PATTERN.matcher(info).matches(), "info line malformed: '" + info + "'");
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
                assertTrue(UCI_MOVE_PATTERN.matcher(uci).matches(),
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
                assertTrue(UCI_MOVE_PATTERN.matcher(uci).matches(),
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
            List<String> idsPerPly = stderr.lines()
                    .map(MOVE_LOG_GAME_ID_PATTERN::matcher)
                    .filter(Matcher::find)
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
                    .filter(l -> l.contains("[move] ") && ELAPSED_FIELD_PATTERN.matcher(l).find())
                    .count();
            assertEquals(plies, elapsedFieldCount,
                    "every [move] log line must carry an elapsed=<ms> field");

            long goLineCount = stderr.lines()
                    .filter(l -> GO_LOG_HEAD_PATTERN.matcher(l).find() && BUDGET_FIELD_PATTERN.matcher(l).find())
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

    // ---- Time increment (winc / binc) ----

    /**
     * Budget the handler computed for the last {@code go}, read back from the
     * {@code [go] … budget=<ms>} line it writes to stderr.
     *
     * <p>{@code UciHandler} keeps the budget internal, and it never reaches stdout, so this
     * log line is the only seam. That is not a workaround: the line exists precisely so a
     * time-forfeit episode can be reconstructed afterward, and pinning it here also protects
     * its format.
     */
    private int budgetOf(String goLine) {
        runHandler("uci\nposition startpos\n" + goLine + "\nquit\n");
        Integer budget = lastGoBudget("");

        assertNotNull(budget, "the handler must log a [go] line with a budget for: " + goLine);

        return budget;
    }

    /**
     * Budget from the last {@code [go]} log line that contains {@code requiredToken}, or {@code null}.
     *
     * <p>capturedErr accumulates across runs within one test method, so the LAST matching line counts
     * rather than the first — otherwise a second call silently reads the first run's budget, which is
     * exactly the way the budget helper failed when it was written. Works line by line, so no regex has
     * to scan across the whole log.
     */
    private Integer lastGoBudget(String requiredToken) {
        Integer budget = null;

        for (String line : capturedErr.toString(StandardCharsets.UTF_8).split("\\R")) {
            if (line.contains("[go]") && line.contains(requiredToken)) {
                var matcher = BUDGET_FIELD_PATTERN.matcher(line);

                if (matcher.find()) {
                    budget = Integer.parseInt(matcher.group(1));
                }
            }
        }

        return budget;
    }

    /**
     * The increment is what a Fischer time control refunds after each move, so a side may
     * spend it every move without the clock falling. The handler adds
     * {@code INCREMENT_USE_PERCENTAGE} = 80 % of it on top of the usual share of the
     * remaining clock — 80 rather than 100 because transmission and GUI overhead would
     * otherwise drift the clock slowly downwards.
     *
     * <p>{@code go depth 1} bounds the search so the test costs milliseconds; the budget is
     * still computed from the clock, since {@code TimeManagement.computeMoveBudgetMillis}
     * only short-circuits on {@code infinite} and {@code movetime}.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void goWithWinc_whiteToMove_addsEightyPercentOfTheIncrement() {
        int withoutIncrement = budgetOf("go depth 1 wtime 600000 btime 600000");
        int withIncrement = budgetOf("go depth 1 wtime 600000 btime 600000 winc 5000 binc 5000");

        assertEquals(600_000 / 31, withoutIncrement,
                "without an increment the budget must stay the plain share of the remaining clock");
        assertEquals(600_000 / 31 + 4_000, withIncrement,
                "80 % of a 5 s increment is 4 s and must be added to that share");
    }

    /** Black to move must be budgeted from {@code btime}/{@code binc}, not from white's pair. */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void goWithBinc_blackToMove_usesBlacksClockAndIncrement() {
        runHandler("uci\nposition startpos moves e2e4\ngo depth 1 wtime 600000 btime 60000 winc 9000 binc 1000\nquit\n");
        Integer budget = lastGoBudget("color=B");

        assertNotNull(budget, "the handler must log a [go] line for black");
        assertEquals(60_000 / 31 + 800, budget,
                "black must be budgeted from btime=60000 and binc=1000, not from white's 600000/9000");
    }

    /**
     * cutechess emits each increment only when it is greater than zero
     * (`if (whiteTc->timeIncrement() > 0)` in `uciengine.cpp`), and time controls may be
     * asymmetric, so {@code winc} can arrive without {@code binc}. Neither side may depend on
     * the other being present.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void goWithWincOnly_isAccepted_andBlackFallsBackToNoIncrement() {
        assertEquals(600_000 / 31 + 4_000, budgetOf("go depth 1 wtime 600000 btime 600000 winc 5000"),
                "a winc without a matching binc must still be applied for white");
    }

    /**
     * The hard cap, and the reason the increment cannot simply be added.
     *
     * <p>An increment is credited to the clock, and the clock is what the flag falls on: with
     * 2 s left and a 5 s increment a side still has only 2 s for this move. Budgeting the
     * increment on top would be a guaranteed forfeit rather than a risk, so the result must be
     * bounded by the remaining time less the safety margin.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void goWithIncrementLargerThanTheClock_isCappedByTheRemainingTime() {
        int budget = budgetOf("go depth 1 wtime 2000 btime 600000 winc 5000 binc 5000");

        assertEquals(2_000 - 50, budget,
                "with 2 s left the budget must be capped at the clock minus the 50 ms safety margin, "
                        + "however large the increment is");
    }

    /**
     * An increment without a clock is not computable — there is no remaining time to bound it
     * against — so it must be ignored rather than used on its own. UCI does not guarantee that
     * {@code winc} implies {@code wtime}; python-chess, for instance, emits every token
     * independently of whatever the caller set.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void goWithIncrementButNoClock_ignoresTheIncrement() {
        int budget = budgetOf("go depth 1 winc 5000 binc 5000");

        assertTrue(budget > 60_000,
                "without wtime/btime the handler must fall back to the effectively unbounded budget "
                        + "rather than deriving one from the increment alone; got " + budget);
    }

    /**
     * <b>Characterization of a behavior change, not an assertion that it is right.</b>
     *
     * <p>The safety margin used to be subtracted from every clock budget
     * ({@code ourMs / (movestogo + 1) − TIME_SAFETY_MARGIN_MS}). It is now applied only to
     * the hard cap, so a game <em>without</em> an increment — which is every anchor match and
     * most cutechess runs — gets 50 ms more per move than before. At 40/120 that is 50 ms on
     * a ~3 s budget, well under two percent, and arguably the better design: the margin exists
     * to prevent a flag fall, and a budget of one thirty-first of the clock cannot cause one.
     *
     * <p>It is pinned separately because it is a change to the pre-existing path rather than
     * part of adding increments, and because a reader comparing the two formulas should find
     * the difference recorded rather than have to spot it. If the margin was meant to stay in
     * the normal path, this test is the one that should fail.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void goWithoutIncrement_noLongerSubtractsTheSafetyMargin() {
        int budget = budgetOf("go depth 1 wtime 600000 btime 600000");

        assertEquals(600_000 / 31, budget,
                "the plain share of the clock, with no margin deducted — the previous formula "
                        + "returned " + (600_000 / 31 - 50));
    }

    /**
     * {@code movestogo 0} makes the divisor 1, so the whole remaining clock is budgeted for a
     * single move — bounded only by the hard cap.
     *
     * <p>cutechess never sends it (`if (myTc->movesLeft() > 0)`), and the behavior predates the
     * increment work, so this is a robustness pin rather than a defect report: it records what
     * happens if some other GUI does, and it will fail if a future guard changes it.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void goWithMovestogoZero_budgetsTheEntireRemainingClock() {
        int budget = budgetOf("go depth 1 wtime 60000 btime 60000 movestogo 0");

        assertEquals(60_000 - 50, budget,
                "movestogo 0 divides by one and is then limited only by the hard cap");
    }

    /**
     * A clock that has already run past zero must not produce a negative or absurd budget.
     *
     * <p>Some GUIs report a negative {@code wtime} once a side has overstepped. Every term of
     * the formula goes negative there, and only the {@code MIN_BUDGET_MS} floor keeps the
     * result sane — worth pinning, because the floor is easy to drop when refactoring a
     * three-way {@code min}/{@code max}.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void goWithNegativeClock_fallsBackToTheMinimumBudget() {
        int budget = budgetOf("go depth 1 wtime -5000 btime 600000 winc 1000 binc 1000");

        assertEquals(50, budget,
                "a negative clock must yield the MIN_BUDGET_MS floor, never a negative budget");
    }

    /** With a tournament control both {@code movestogo} and an increment can arrive together. */
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void goWithMovestogoAndIncrement_usesBoth() {
        assertEquals(60_000 / 11 + 800, budgetOf("go depth 1 wtime 60000 btime 60000 movestogo 10 winc 1000 binc 1000"),
                "movestogo must set the divisor and the increment must be added on top of that share");
    }

    // ---- previous own score (input for the time management) ----

    /**
     * After a completed search the handler remembers the ply the search started at and the
     * score it reported, so the next {@code go} can compare against it.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void go_afterSearchCompletes_storesStartPlyAndScore() throws InterruptedException {
        var handler = new UciHandler(new MyChessEnv(), new BufferedReader(new StringReader("")));

        handler.handleLine("position startpos");
        handler.handleLine("go depth 2");
        awaitStoredScore(handler);

        assertEquals(0, handler.getPreviousPly(),
                "a search from the start position must be stored with start ply 0");
        assertNotNull(handler.getPreviousWeightCenti(),
                "a completed search must leave a score for the next go to compare against");
    }

    /** The stored ply is the ply of the searched position, i.e. the number of moves played. */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void go_afterMoves_storesTheSearchedPly() throws InterruptedException {
        var handler = new UciHandler(new MyChessEnv(), new BufferedReader(new StringReader("")));

        handler.handleLine("position startpos moves e2e4 e7e5");
        handler.handleLine("go depth 2");
        awaitStoredScore(handler);

        assertEquals(2, handler.getPreviousPly(),
                "after 1.e4 e5 the searched position is at ply 2, and that ply must be stored");
    }

    /** A new game must not inherit the previous game's score. */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void ucinewgame_afterCompletedSearch_clearsTheStoredScore() throws InterruptedException {
        var handler = new UciHandler(new MyChessEnv(), new BufferedReader(new StringReader("")));

        handler.handleLine("position startpos moves e2e4 e7e5");
        handler.handleLine("go depth 2");
        awaitStoredScore(handler);
        handler.handleLine("ucinewgame");

        assertNull(handler.getPreviousWeightCenti(), "ucinewgame must clear the stored score");
        assertNull(handler.getPreviousPly(), "ucinewgame must clear the stored ply");
    }

    // ---- EngineConfig.getPreviousOwnScoreCenti during a game ----

    /** The very first search of a session has nothing to compare against. */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void engineConfig_firstGo_hasNoPreviousScore() throws InterruptedException {
        var handler = new UciHandler(new MyChessEnv(), new BufferedReader(new StringReader("")));

        var config = goAndAwait(handler, POSITION_START, 0);

        assertNull(config.getPreviousOwnScoreCenti(), "the first go must reach the engine without a previous score");
    }

    /**
     * A regular game: our move, the opponent's reply, our next move. Each search must receive exactly
     * the score the previous own search reported.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void engineConfig_duringAGame_carriesTheScoreOfOurPreviousMove() throws InterruptedException {
        var handler = new UciHandler(new MyChessEnv(), new BufferedReader(new StringReader("")));

        goAndAwait(handler, POSITION_START, 0);
        Integer scoreAtPly0 = handler.getPreviousWeightCenti();

        var configAtPly2 = goAndAwait(handler, POSITION_AFTER_TWO_PLIES, 2);
        Integer scoreAtPly2 = handler.getPreviousWeightCenti();

        var configAtPly4 = goAndAwait(handler, POSITION_AFTER_FOUR_PLIES, 4);

        assertEquals(scoreAtPly0, configAtPly2.getPreviousOwnScoreCenti(),
                "the search at ply 2 must receive the score of our search at ply 0");
        assertEquals(scoreAtPly2, configAtPly4.getPreviousOwnScoreCenti(),
                "the search at ply 4 must receive the score of our search at ply 2");
    }

    /** The GUI jumps to a position that does not follow our previous move. */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void engineConfig_afterAJumpToAnotherPosition_hasNoPreviousScore() throws InterruptedException {
        var handler = new UciHandler(new MyChessEnv(), new BufferedReader(new StringReader("")));

        goAndAwait(handler, POSITION_START, 0);
        var config = goAndAwait(handler, POSITION_AFTER_FOUR_PLIES, 4);

        assertNull(config.getPreviousOwnScoreCenti(),
                "four plies after our last search a move of ours is missing, so no previous score may be passed");
    }

    /** The same position searched again, e.g. a repeated go. */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void engineConfig_samePositionAgain_hasNoPreviousScore() throws InterruptedException {
        var handler = new UciHandler(new MyChessEnv(), new BufferedReader(new StringReader("")));

        goAndAwait(handler, POSITION_START, 0);
        handler.handleLine(POSITION_START);
        handler.handleLine(GO_DEPTH_2);
        var config = handler.getLastEngineConfig();

        assertNull(config.getPreviousOwnScoreCenti(),
                "searching the same position again has no previous own move to compare against");
    }

    /** A new game must not receive the last score of the previous game, even at a matching ply distance. */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void engineConfig_afterUcinewgame_hasNoPreviousScore() throws InterruptedException {
        var handler = new UciHandler(new MyChessEnv(), new BufferedReader(new StringReader("")));

        goAndAwait(handler, POSITION_START, 0);
        handler.handleLine("ucinewgame");
        handler.handleLine(POSITION_AFTER_TWO_PLIES);
        handler.handleLine(GO_DEPTH_2);
        var config = handler.getLastEngineConfig();

        assertNull(config.getPreviousOwnScoreCenti(),
                "after ucinewgame the first search must not get the old game's score, although it is two plies later");
    }

    /**
     * Sends the position and {@code go depth 2}, captures the engine configuration of that search and
     * waits until the search has stored its result for the given ply.
     */
    private static EngineConfig goAndAwait(UciHandler handler, String positionLine, int ply)
            throws InterruptedException {
        handler.handleLine(positionLine);
        handler.handleLine(GO_DEPTH_2);
        var config = handler.getLastEngineConfig();
        long deadline = System.currentTimeMillis() + SEARCH_STORE_WAIT_MS;

        while (!isStoredFor(handler, ply) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertTrue(isStoredFor(handler, ply),
                "the search at ply " + ply + " must store its score within " + SEARCH_STORE_WAIT_MS + " ms");

        return config;
    }

    private static boolean isStoredFor(UciHandler handler, int ply) {
        Integer storedPly = handler.getPreviousPly();

        return storedPly != null && storedPly == ply && handler.getPreviousWeightCenti() != null;
    }

    // ---- previousOwnScoreCentiFor: when the stored score is comparable ----

    /** Our previous move plus the opponent's reply: exactly two plies, so the score is used. */
    @Test
    void previousOwnScore_twoPliesLater_isTheStoredScore() {
        var stored = new UciHandler.PlyAndWeight(STORED_PLY, STORED_SCORE);

        assertEquals(STORED_SCORE, UciHandler.previousOwnScoreCentiFor(stored, STORED_PLY + 2),
                "a search two plies after the stored one follows our previous move and must get its score");
    }

    @Test
    void previousOwnScore_samePly_isNull() {
        var stored = new UciHandler.PlyAndWeight(STORED_PLY, STORED_SCORE);

        assertNull(UciHandler.previousOwnScoreCentiFor(stored, STORED_PLY),
                "the same position searched again (e.g. a repeated go) has no previous own move");
    }

    @Test
    void previousOwnScore_onePlyLater_isNull() {
        var stored = new UciHandler.PlyAndWeight(STORED_PLY, STORED_SCORE);

        assertNull(UciHandler.previousOwnScoreCentiFor(stored, STORED_PLY + 1),
                "one ply later is the opponent's turn, so the stored score is not ours to compare");
    }

    @Test
    void previousOwnScore_fourPliesLater_isNull() {
        var stored = new UciHandler.PlyAndWeight(STORED_PLY, STORED_SCORE);

        assertNull(UciHandler.previousOwnScoreCentiFor(stored, STORED_PLY + 4),
                "a gap of four plies means a move of ours was skipped, so the score is stale");
    }

    @Test
    void previousOwnScore_earlierPly_isNull() {
        var stored = new UciHandler.PlyAndWeight(STORED_PLY, STORED_SCORE);

        assertNull(UciHandler.previousOwnScoreCentiFor(stored, STORED_PLY - 2),
                "a jump back to an earlier position must not reuse a score from a later one");
    }

    @Test
    void previousOwnScore_storedWithoutMove_isNull() {
        var stored = new UciHandler.PlyAndWeight(STORED_PLY, null);

        assertNull(UciHandler.previousOwnScoreCentiFor(stored, STORED_PLY + 2),
                "a search that produced no move stored no score, and none must come back");
    }

    @Test
    void previousOwnScore_negativeScore_isPassedThroughUnchanged() {
        var stored = new UciHandler.PlyAndWeight(STORED_PLY, -120);

        assertEquals(-120, UciHandler.previousOwnScoreCentiFor(stored, STORED_PLY + 2),
                "a losing score must come back with its sign, both scores are from our point of view");
    }

    /**
     * Waits until the search watcher has stored its score. The watcher stores it just before
     * it writes {@code bestmove}, on its own thread, so the test has to poll.
     */
    private static void awaitStoredScore(UciHandler handler) throws InterruptedException {
        long deadline = System.currentTimeMillis() + SEARCH_STORE_WAIT_MS;

        while (handler.getPreviousWeightCenti() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        assertNotNull(handler.getPreviousWeightCenti(),
                "the search must store a score within " + SEARCH_STORE_WAIT_MS + " ms");
    }

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
            Pattern[] compiled = new Pattern[patterns.length];

            for (int p = 0; p < patterns.length; p++) {
                compiled[p] = patterns[p].startsWith("^") ? Pattern.compile(patterns[p]) : null;
            }

            boolean[] matched = new boolean[patterns.length];
            int newCursor = cursor;

            for (int i = cursor; i < lines.size(); i++) {
                String line = lines.get(i);
                for (int p = 0; p < patterns.length; p++) {
                    if (!matched[p] && lineMatches(line, patterns[p], compiled[p])) {
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

        /** Regex match if {@code compiled} is set (pattern starting with {@code ^}), exact match otherwise. */
        private static boolean lineMatches(String line, String pattern, Pattern compiled) {
            if (compiled != null) {
                return compiled.matcher(line).matches();
            }

            return line.equals(pattern);
        }
    }
}
