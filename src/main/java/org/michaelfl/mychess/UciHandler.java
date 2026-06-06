package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.IterationInfo;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.NextMoveTask;

import java.io.BufferedReader;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Minimal UCI (Universal Chess Interface) protocol handler.
 *
 * <p>Spawned by {@link MyChessMain} when {@code "uci"} is passed as the first
 * CLI argument. Reads UCI commands from stdin, writes protocol-compliant
 * responses to stdout. All non-protocol output (engine diagnostics, errors)
 * goes through {@link Log} and ends up on stderr, never on stdout.
 *
 * <p>Supported commands: {@code uci}, {@code isready}, {@code ucinewgame},
 * {@code position [startpos|fen ...] [moves ...]},
 * {@code go [movetime N | wtime A btime B [movestogo K] | depth D | infinite]},
 * {@code stop}, {@code quit}. Anything else is silently ignored per the UCI
 * specification.
 *
 * @author Michael Fleischhauer
 */
final class UciHandler {

    private static final String ENGINE_NAME = "myChess";
    private static final String ENGINE_AUTHOR = "Michael Fleischhauer";

    /** Default fallback when wtime/btime is given without movestogo. */
    private static final int DEFAULT_MOVES_TO_GO = 30;

    /** Safety margin per move when computing time budget from clock. */
    private static final int TIME_SAFETY_MARGIN_MS = 50;

    /** Floor on per-move time so search has at least a fraction of a second. */
    private static final int MIN_BUDGET_MS = 50;

    /** Ceiling on go infinite / go depth N (effectively unbounded). 24 h in ms. */
    private static final int INFINITE_MILLIS = 24 * 60 * 60 * 1_000;

    private final MyChessEnv env;
    private final BufferedReader in;

    private Board board;
    private boolean is960;
    private final AtomicReference<NextMoveTask> currentTask = new AtomicReference<>();
    private final AtomicReference<Game> currentGame = new AtomicReference<>();
    private final AtomicReference<Thread> currentWatcher = new AtomicReference<>();

    /**
     * Per-game identifier emitted on every {@code [move]} status line so that
     * concurrent cutechess games (e.g. {@code -concurrency 2}) can be told
     * apart in a shared {@code mychess-stderr.log}. Regenerated on every
     * {@code ucinewgame}; a fresh value at constructor time covers the case
     * where the GUI omits {@code ucinewgame} before the first game.
     */
    private String gameId = UUID.randomUUID().toString();

    /**
     * Wall-clock millis at the start of the current game — set on the same
     * {@code ucinewgame} (or constructor) event as {@link #gameId}. Used by
     * {@link #logMoveStatus} to emit a {@code gameElapsed=<ms>} field so the
     * cumulative game duration is visible without diffing log timestamps.
     */
    private long gameStartMs = System.currentTimeMillis();

    /** Time the run-loop's finally block waits for an in-flight watcher to emit bestmove. */
    private static final long QUIT_GRACE_MS = 5_000L;

    UciHandler(MyChessEnv env, BufferedReader in) {
        this.env = env;
        this.in = in;
        this.board = Board.createNewGame();
    }

    void run() {
        try {
            String line;
            while ((line = readLine()) != null) {
                if (!handleLine(line.trim())) {
                    break;
                }
            }
        } finally {
            shutdown();
        }
    }

    Board getBoard() {
        return board;
    }

    /**
     * Tear down the handler: give any in-flight search watcher a chance to
     * emit its {@code bestmove} (capped by {@link #QUIT_GRACE_MS}), then
     * cancel a still-running task and shut down the per-go engine.
     */
    private void shutdown() {
        Thread watcher = currentWatcher.get();
        if (watcher != null) {
            try {
                watcher.join(QUIT_GRACE_MS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }

        cancelCurrentTask();
        shutdownCurrentGame();
    }

    private String readLine() {
        try {
            return in.readLine();
        } catch (java.io.IOException e) {
            Log.error("UCI stdin read failed", e);
            return null;
        }
    }

    /** @return {@code false} when {@code quit} was received. */
    boolean handleLine(String line) {
        if (line.isEmpty()) {
            return true;
        }
        String first = firstToken(line);

        return switch (first) {
            case "uci" -> { handleUci(); yield true; }
            case "isready" -> { handleIsReady(); yield true; }
            case "setoption" -> { handleSetOption(line); yield true; }
            case "ucinewgame" -> { handleNewGame(); yield true; }
            case "position" -> { handlePosition(line); yield true; }
            case "go" -> { handleGo(line); yield true; }
            case "stop" -> { handleStop(); yield true; }
            case "quit" -> false;
            default -> true;   // UCI spec: ignore unknown commands silently
        };
    }

    // ---- UCI command handlers ----

    private void handleUci() {
        writeLine("id name " + ENGINE_NAME);
        writeLine("id author " + ENGINE_AUTHOR);
        writeLine("option name UCI_Chess960 type check default false");
        writeLine("uciok");
    }

    private void handleIsReady() {
        writeLine("readyok");
    }

    private void handleSetOption(String line) {
        if (line.equals("setoption name UCI_Chess960 value true")) {
            // Enable Chess960 game
            this.is960 = true;
        } else if (line.equals("setoption name UCI_Chess960 value false")) {
            this.is960 = false;
        }
    }

    private void handleNewGame() {
        cancelCurrentTask();
        shutdownCurrentGame();
        this.board = Board.createNewGame();
        ChessEngine.resetIterationTimings();
        this.gameId = UUID.randomUUID().toString();
        this.gameStartMs = System.currentTimeMillis();
    }

    private void handlePosition(String line) {
        // position [startpos | fen <FEN-6-fields>] [moves <m1> <m2> ...]
        var rest = line.substring("position".length()).trim();
        var movesIdx = rest.indexOf(" moves ");
        String stem = movesIdx < 0 ? rest : rest.substring(0, movesIdx);
        String movesPart = movesIdx < 0 ? "" : rest.substring(movesIdx + " moves ".length()).trim();

        Board newBoard;
        if (stem.equals("startpos") || stem.isEmpty()) {
            newBoard = Board.createNewGame();
        } else if (stem.startsWith("fen ")) {
            try {
                var fen = stem.substring("fen ".length());
                newBoard = is960 ?
                        Fen.importChess960FEN(fen) :
                        Fen.importFEN(fen);
            } catch (IllegalArgumentException e) {
                Log.error("Failed to parse FEN in position command: " + e.getMessage());
                return;
            }
        } else {
            Log.error("Malformed position command (expected startpos|fen ...): " + line);
            return;
        }

        if (movesPart.isEmpty()) {
            this.board = newBoard;
            return;
        }

        // Replay UCI moves on the new board.
        var game = new Game(Game.standardConfig(), newBoard);
        try {
            for (String uciMove : movesPart.split("\\s+")) {
                MoveDescription md = UciMoveParser.parse(uciMove, game.getBoard());
                game.makeMove(md);
            }

            this.board = game.getBoard();
        } catch (IllegalMoveException | IllegalArgumentException e) {
            Log.error("Failed to replay moves in position command: " + e.getMessage());
        } finally {
            game.shutdown();
        }
    }

    private void handleGo(String line) {
        var args = parseGoArgs(line, board.getGameStatus().getTurn());

        cancelCurrentTask();
        shutdownCurrentGame();

        // Snapshot for the per-move [move] / [go] status logs: color and
        // full-move number are derived from the position at search start,
        // not from any intermediate state inside the search.
        final int turnAtStart = board.getGameStatus().getTurn();
        final int plyCountAtStart = board.getGameStatus().getPlyCount();

        logGoStatus(args, turnAtStart, plyCountAtStart);

        var engineConfig = new EngineConfig.Builder()
                .maxDepth(args.maxDepth)
                .millisPerMove(args.timeBudgetMillis)
                .silent(true)
                .build();
        var gameConfig = new GameConfig(MyChessEngine.class, engineConfig);

        var game = new Game(gameConfig, board);
        currentGame.set(game);

        long goStartMs = System.currentTimeMillis();
        ChessEngine engine = game.getEngine();
        NextMoveTask task = engine.nextMoveAsync(env, info -> emitInfo(info, goStartMs, turnAtStart));
        currentTask.set(task);

        // Spawn a virtual thread to wait on the search result; the main UCI
        // loop stays unblocked and can process stop/quit immediately.
        // Virtual threads are always daemons, so no explicit daemon flag.
        Thread watcher = Thread.ofVirtual()
                .name("uci-search-watcher")
                .start(() -> awaitAndEmitBestmove(game, task, args.timeBudgetMillis,
                        turnAtStart, plyCountAtStart, goStartMs));
        currentWatcher.set(watcher);
    }

    private void handleStop() {
        cancelCurrentTask();
    }

    // ---- Search lifecycle ----

    private void awaitAndEmitBestmove(Game game, NextMoveTask task, int budgetMillis,
                                      int turnAtStart, int plyCountAtStart, long goStartMs) {
        MoveAndWeight result = null;
        try {
            try {
                // Give the watcher a 1-second grace period over the search budget.
                result = task.getResult(budgetMillis + 1_000L, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof CancellationException)) {
                    Log.error("Search failed", e);
                }
            } catch (CancellationException _) {
                // Expected on `stop` / shutdown — fall through to the fallback.
            } catch (TimeoutException _) {
                task.cancel();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }

            // On any of the above catches `result` stays null, and we fall back
            // to the most recent iteration's best move/weight tracked by
            // emitInfo. Without a completed iteration these stay at their
            // initial 0/0f, which surfaces as a "bestmove 0000" reply.
            //
            // POV normalization: `lastIterationWeight` is the raw
            // negamax score (side-to-move POV — see PositionSearch /
            // IterationInfo). `result.weight()` has the White-POV factor
            // applied by ChessEngine.calculateNextMove. Convert the
            // completed-result branch back to side-to-move POV so the
            // logging downstream sees one consistent convention.
            int bestmove   = (result != null) ? result.move()   : lastIterationFirstMove.get();
            float bestWeightStm;
            if (result != null) {
                bestWeightStm = (turnAtStart == GameStatus.TURN_WHITE) ? result.weight() : -result.weight();
            } else {
                bestWeightStm = lastIterationWeight.get();
            }

            if (bestmove != 0 && !isLegalInCurrentBoard(bestmove)) {
                int fallback = firstLegalInCurrentBoard();
                Log.error("[bestmove-validate] selected " + UciMoveParser.toUci(bestmove, board)
                        + " is illegal in current position — falling back to "
                        + (fallback == 0 ? "0000" : UciMoveParser.toUci(fallback, board))
                        + " — root FEN: " + Fen.exportFEN(board));
                bestmove = fallback;
            }

            writeLine("bestmove " + (bestmove == 0 ? "0000" : UciMoveParser.toUci(bestmove, board)));
            logMoveStatus(bestmove, bestWeightStm, turnAtStart, plyCountAtStart, goStartMs);
        } finally {
            game.shutdown();
            currentTask.compareAndSet(task, null);
            currentGame.compareAndSet(game, null);
            currentWatcher.compareAndSet(Thread.currentThread(), null);
            lastIterationFirstMove.set(0);
            lastIterationWeight.set(0f);
        }
    }

    /** Best move from the most recent iteration that completed, for stop/timeout fallback. */
    private final AtomicReference<Integer> lastIterationFirstMove = new AtomicReference<>(0);

    /** Weight from the most recent iteration that completed, for stop/timeout fallback. */
    private final AtomicReference<Float> lastIterationWeight = new AtomicReference<>(0f);

    private void emitInfo(IterationInfo info, long searchStartMs, int turnAtStart) {
        int[] pv = info.pv();
        if (pv.length > 0 && pv[0] != 0) {
            lastIterationFirstMove.set(pv[0]);
            lastIterationWeight.set(info.weight());
        }

        // Validate FIRST so we never write an illegal PV to UCI. validatePv
        // also logs a [pv-validate] diagnostic on failure (its original
        // purpose); the new contract is that its boolean result gates the
        // subsequent writeLine. The [iter] stderr line still goes out
        // unconditionally so post-mortem analysis sees the bad iteration.
        boolean pvOk = validatePv(pv);

        // info.weight() is the raw negamax score in pawn units — positive
        // means the side to move is winning. UCI's `score cp` and
        // `score mate` use exactly that convention, so emit info.weight()
        // unmodified here. White-POV (only used in the [iter] log line
        // below for cross-side comparison) is the same value negated when
        // we play Black.
        long elapsedMs = System.currentTimeMillis() - searchStartMs;

        if (pvOk) {
            var sb = new StringBuilder();
            sb.append("info depth ").append(info.depth());
            sb.append(" nodes ").append(info.nodes());
            sb.append(" time ").append(elapsedMs);

            if (WeightingFunction.isCheckmateWeight(info.weight())) {
                int plies = WeightingFunction.checkmateWeightToPlies(info.weight());
                int moves = (plies + 1) / 2;
                int signed = info.weight() >= 0 ? moves : -moves;
                sb.append(" score mate ").append(signed);
            } else {
                int cp = Math.round(info.weight() * 100f);
                sb.append(" score cp ").append(cp);
            }

            if (pv.length > 0 && pv[0] != 0) {
                sb.append(" pv");
                for (int packed : pv) {
                    if (packed == 0) {
                        break;
                    }

                    sb.append(' ').append(UciMoveParser.toUci(packed, board));
                }
            }

            writeLine(sb.toString());
        }

        logIterStatus(info, elapsedMs, turnAtStart);
    }

    /**
     * Per-iteration stderr log written alongside each {@code info ...}
     * line. Captures the same score in both conventions so a future
     * PGN-vs-stderr correlation is trivial:
     *
     * <pre>{@code [iter] game=<8-char> color=<W|B> depth=<D> nodes=<N> elapsed=<ms> evalStm=<X> evalW=<Y> pv=<first-move-uci>}</pre>
     *
     * <ul>
     *   <li>{@code evalStm} — score as actually sent via UCI
     *       ({@code score cp} / {@code score mate}). cutechess writes
     *       this value into the PGN move comment, and its
     *       {@code -resign} adjudication tests against this number.</li>
     *   <li>{@code evalW} — same score in pawn units from White's
     *       perspective. Negated relative to {@code evalStm} when we
     *       play Black. Lets you compare consecutive moves across both
     *       sides on a single yardstick.</li>
     * </ul>
     */
    private void logIterStatus(IterationInfo info, long elapsedMs, int turnAtStart) {
        String color = (turnAtStart == GameStatus.TURN_WHITE) ? "W" : "B";
        float evalStm = info.weight();
        float evalWhitePov = (turnAtStart == GameStatus.TURN_WHITE) ? evalStm : -evalStm;
        String firstMove = (info.pv().length > 0 && info.pv()[0] != 0) ? UciMoveParser.toUci(info.pv()[0], board) : "-";

        Log.info(String.format(Locale.ROOT,
                "[iter] game=%s color=%s depth=%d nodes=%d elapsed=%d evalStm=%s evalW=%s pv=%s",
                shortGameId(), color, info.depth(), info.nodes(), elapsedMs,
                formatEvalForLog(evalStm), formatEvalForLog(evalWhitePov), firstMove));
    }

    /**
     * One-line status entry written to stderr per move myChess plays.
     * Intended for skimming a {@code mychess-stderr.log} during long
     * cutechess matches, especially under {@code -concurrency > 1} where
     * several games stream their move records into the same file.
     *
     * <p>Format:
     * <pre>{@code [move] game=<8-char> color=<W|B> move=<N> uci=<...> evalStm=<...> evalW=<...> elapsed=<ms> gameElapsed=<M:SS>}</pre>
     *
     * <ul>
     *   <li>{@code game} — first 8 chars of a UUID regenerated on every
     *       {@code ucinewgame}, so concurrent games can be told apart.</li>
     *   <li>{@code color} — {@code W} or {@code B}, derived from whose
     *       turn it was at search start (always our own turn, since the
     *       GUI only asks an engine to move on its own turn).</li>
     *   <li>{@code move} — full move number in PGN convention
     *       ({@code (plyCount / 2) + 1}); both halves of move N report
     *       {@code N}.</li>
     *   <li>{@code uci} — the played move in UCI notation.</li>
     *   <li>{@code evalStm} — score as emitted via UCI
     *       ({@code score cp} / {@code score mate}), i.e. from the
     *       side-to-move's perspective. This is the number cutechess
     *       writes into the PGN move comment and tests against the
     *       {@code -resign} threshold. Identical to {@code evalW} when
     *       we play White; negated when we play Black.</li>
     *   <li>{@code evalW} — same score in pawn units from White's
     *       perspective, so the sign is consistent across both engine
     *       roles. Use this when comparing consecutive moves across
     *       sides.</li>
     *   <li>{@code elapsed} — wall-clock milliseconds from {@code go}
     *       receipt to {@code bestmove} emission. Compare with the
     *       budget logged on the matching {@code [go]} line to spot
     *       overshoots that the cancellation path failed to prevent.</li>
     *   <li>{@code gameElapsed} — wall-clock duration since the game's
     *       {@code ucinewgame} (or process start, if the GUI skipped
     *       that), formatted as {@code M:SS}. Cumulative game duration
     *       without having to diff log timestamps; the last
     *       {@code [move]} line of a game gives its total length.</li>
     * </ul>
     *
     * <p>Eval format: plain centipawns in pawn units (e.g. {@code +0.30})
     * for normal evals; {@code +M3} / {@code -M3} for mate-in-N-full-moves
     * scores.
     *
     * <p>No log line is written when no move was actually played
     * (search returned {@code 0} — checkmate / stalemate at the root, or
     * the search aborted before producing any iteration).
     */
    private void logMoveStatus(int bestmove, float weightStm, int turnAtStart, int plyCountAtStart, long goStartMs) {
        if (bestmove == 0) {
            return;
        }

        String color = (turnAtStart == GameStatus.TURN_WHITE) ? "W" : "B";
        int fullMoveNumber = (plyCountAtStart / 2) + 1;
        String uciMove = UciMoveParser.toUci(bestmove, board);
        float evalWhitePov = (turnAtStart == GameStatus.TURN_WHITE) ? weightStm : -weightStm;
        String evalStmStr = formatEvalForLog(weightStm);
        String evalWhiteStr = formatEvalForLog(evalWhitePov);
        String shortGameId = shortGameId();
        long now = System.currentTimeMillis();
        long elapsedMs = now - goStartMs;
        String gameElapsedStr = formatElapsedMinSec(now - gameStartMs);

        Log.info(String.format(Locale.ROOT,
                "[move] game=%s color=%s move=%d uci=%s evalStm=%s evalW=%s elapsed=%d gameElapsed=%s",
                shortGameId, color, fullMoveNumber, uciMove, evalStmStr, evalWhiteStr, elapsedMs, gameElapsedStr));
    }

    /**
     * Companion to {@link #logMoveStatus} that records the GUI's clock
     * inputs at the moment a {@code go} arrived, plus the budget we
     * derived from them. Paired with the matching {@code [move]} line's
     * {@code elapsed} field, this lets us reconstruct the full
     * input/output of each search slot from the log alone — essential
     * for diagnosing time-forfeit episodes after the fact.
     *
     * <p>Format:
     * <pre>{@code [go] game=<8-char> color=<W|B> move=<N> wtime=<ms|-> btime=<ms|-> movestogo=<n|-> movetime=<ms|-> budget=<ms>}</pre>
     *
     * <p>Missing fields are emitted as {@code -}, so the line shape is
     * stable regardless of which {@code go} sub-mode the GUI sent.
     */
    private void logGoStatus(GoArgs args, int turnAtStart, int plyCountAtStart) {
        String color = (turnAtStart == GameStatus.TURN_WHITE) ? "W" : "B";
        int fullMoveNumber = (plyCountAtStart / 2) + 1;

        Log.info(String.format(Locale.ROOT,
                "[go] game=%s color=%s move=%d wtime=%s btime=%s movestogo=%s movetime=%s budget=%d",
                shortGameId(), color, fullMoveNumber,
                formatNullable(args.wtime()),
                formatNullable(args.btime()),
                formatNullable(args.movestogo()),
                formatNullable(args.movetimeMs()),
                args.timeBudgetMillis()));
    }

    private String shortGameId() {
        return gameId.length() >= 8 ? gameId.substring(0, 8) : gameId;
    }

    private static String formatNullable(Integer value) {
        return value == null ? "-" : value.toString();
    }

    private static String formatEvalForLog(float weight) {
        if (WeightingFunction.isCheckmateWeight(weight)) {
            int plies = WeightingFunction.checkmateWeightToPlies(weight);
            int fullMoves = (plies + 1) / 2;
            return (weight >= 0 ? "+M" : "-M") + fullMoves;
        }
        return String.format(Locale.ROOT, "%+.2f", weight);
    }

    /**
     * Renders an elapsed duration as {@code M:SS} (e.g. {@code 5:23},
     * {@code 47:18}, {@code 105:09}). Minutes have no width cap so durations
     * past 99 min stay readable; seconds are always two digits.
     */
    private static String formatElapsedMinSec(long elapsedMs) {
        long totalSec = (elapsedMs + 500) / 1000;
        long minutes = totalSec / 60;
        long seconds = totalSec % 60;

        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    /**
     * Diagnostic guard: replay the PV from the search's root position and
     * log the offending FEN, ply index, and full PV on the first illegal
     * move encountered. Helps track down PV-table corruption bugs that
     * cutechess flags as {@code "Illegal PV move … from myChess"}.
     *
     * <p>Catches two failure modes:
     * <ul>
     * <li><b>Not pseudo-legal</b> — the PV move is not in
     *     {@link MoveGenerator#calculateMoves(Board)} for the current
     *     position. Covers moves with no piece on the source square,
     *     captures of own pieces, blocked sliders, etc.</li>
     * <li><b>Leaves own king in check</b> — the PV move is pseudo-legal
     *     but, once applied, lets the opponent capture our king. The
     *     generator surfaces this on the NEXT ply by returning
     *     {@link Moves#ILLEGAL}.</li>
     * </ul>
     *
     * <p>No-op on success — does not throw, does not alter the search,
     * does not modify {@link #board}.
     */
    /**
     * Last-resort check for the {@code bestmove} the engine wants to emit:
     * must be pseudo-legal in the current {@link #board} and must not leave
     * our king capturable on the next ply. Same legality contract as
     * {@link #validatePv(int[])} but for a single move. Used to filter the
     * outbound {@code bestmove} line so cutechess never receives an
     * illegal move under any circumstance.
     */
    private boolean isLegalInCurrentBoard(int move) {
        var probe = board.copy();
        var moveGen = new MoveGenerator(MoveSorter.defaultImplementation());
        var pseudoLegal = moveGen.calculateMoves(probe);
        if (pseudoLegal.isIllegal() || !pseudoLegal.contains(move)) {
            return false;
        }
        probe.makeMove(move);
        return !probe.canCaptureOpposingKing();
    }

    /**
     * Pick the first move from the current {@link #board}'s pseudo-legal
     * set that does not leave our king capturable on the next ply.
     * Fallback used when the engine's chosen {@code bestmove} fails
     * {@link #isLegalInCurrentBoard(int)}. Returns {@code 0} when no legal
     * move exists (checkmate / stalemate at the root), which surfaces as
     * {@code bestmove 0000} per UCI convention.
     */
    private int firstLegalInCurrentBoard() {
        var moveGen = new MoveGenerator(MoveSorter.defaultImplementation());
        var pseudoLegal = moveGen.calculateMoves(board.copy());
        if (pseudoLegal.isIllegal()) {
            return 0;
        }
        int[] moves = pseudoLegal.getMoves();
        for (int i = 0; i < pseudoLegal.count(); i++) {
            int candidate = moves[i];
            var probe = board.copy();
            probe.makeMove(candidate);
            if (!probe.canCaptureOpposingKing()) {
                return candidate;
            }
        }
        return 0;
    }

    /**
     * @return {@code true} if every PV move is pseudo-legal from the root
     *         and none leaves the own king capturable on the next ply.
     *         Empty PVs return {@code true} (nothing to validate). On any
     *         failure a {@code [pv-validate]} stderr diagnostic is logged
     *         and {@code false} is returned so the caller can skip the
     *         outbound UCI {@code info pv ...} line.
     */
    private boolean validatePv(int[] pv) {
        if (pv.length == 0 || pv[0] == 0) {
            return true;
        }

        var probe = board.copy();
        var moveGen = new MoveGenerator(MoveSorter.defaultImplementation());
        Moves pseudoLegal = moveGen.calculateMoves(probe);

        int lastAppliedMove = 0;
        int lastAppliedPly = -1;

        for (int i = 0; i < pv.length; i++) {
            int move = pv[i];
            if (move == 0) {
                break;
            }

            if (pseudoLegal.isIllegal()) {
                logIllegalPv("ply " + lastAppliedPly + " (" + UciMoveParser.toUci(lastAppliedMove, board)
                        + ") leaves own king in check", pv, probe);
                return false;
            }

            if (!pseudoLegal.contains(move)) {
                logIllegalPv("ply " + i + " (" + UciMoveParser.toUci(move, board)
                        + ") is not pseudo-legal", pv, probe);
                return false;
            }

            probe.makeMove(move);
            pseudoLegal = moveGen.calculateMoves(probe);
            lastAppliedMove = move;
            lastAppliedPly = i;
        }

        if (pseudoLegal.isIllegal()) {
            logIllegalPv("ply " + lastAppliedPly + " (" + UciMoveParser.toUci(lastAppliedMove, board)
                    + ") leaves own king in check", pv, probe);
            return false;
        }

        return true;
    }

    private void logIllegalPv(String detail, int[] pv, Board atPosition) {
        Log.error("[pv-validate] " + detail
                + " — PV: " + formatPv(pv)
                + " — root FEN: " + Fen.exportFEN(board)
                + " — illegal at FEN: " + Fen.exportFEN(atPosition));
    }

    private String formatPv(int[] pv) {
        var sb = new StringBuilder();
        for (int m : pv) {
            if (m == 0) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }

            sb.append(UciMoveParser.toUci(m, board));
        }

        return sb.toString();
    }

    private void cancelCurrentTask() {
        NextMoveTask task = currentTask.getAndSet(null);
        if (task != null) {
            task.cancel();
        }
    }

    private void shutdownCurrentGame() {
        Game game = currentGame.getAndSet(null);
        if (game != null) {
            game.shutdown();
        }
    }

    // ---- Time management ----

    /**
     * Decoded {@code go} command. {@code wtime}/{@code btime}/{@code movestogo}/
     * {@code movetimeMs} are kept as nullable carry-throughs from the raw
     * tokens so that {@link #handleGo} can log them verbatim in the
     * {@code [go]} diagnostic line for post-mortem time-budget analysis.
     */
    private record GoArgs(int maxDepth, int timeBudgetMillis,
                          Integer wtime, Integer btime, Integer movestogo,
                          Integer movetimeMs) {}

    /** Mutable intermediate holder for the raw {@code go ...} tokens. */
    private static final class RawGoTokens {
        int maxDepth = Integer.MAX_VALUE;
        Integer movetimeMs;
        Integer wtime;
        Integer btime;
        Integer movestogo;
        boolean infinite;
    }

    private static GoArgs parseGoArgs(String line, int turn) {
        RawGoTokens raw = readGoTokens(line);
        int budgetMillis = computeBudgetMillis(raw, turn);

        return new GoArgs(raw.maxDepth, budgetMillis,
                raw.wtime, raw.btime, raw.movestogo, raw.movetimeMs);
    }

    @SuppressWarnings("java:S127")
    private static RawGoTokens readGoTokens(String line) {
        var tokens = line.split("\\s+");
        var raw = new RawGoTokens();

        for (int i = 1; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "depth" -> raw.maxDepth = readIntValue(tokens, ++i, Integer.MAX_VALUE);
                case "movetime" -> raw.movetimeMs = readIntValue(tokens, ++i, 0);
                case "wtime" -> raw.wtime = readIntValue(tokens, ++i, 0);
                case "btime" -> raw.btime = readIntValue(tokens, ++i, 0);
                case "movestogo" -> raw.movestogo = readIntValue(tokens, ++i, 0);
                case "infinite" -> raw.infinite = true;
                default -> { /* ignore unknown go-args */ }
            }
        }

        return raw;
    }

    private static int computeBudgetMillis(RawGoTokens raw, int turn) {
        if (raw.infinite) {
            return INFINITE_MILLIS;
        }
        if (raw.movetimeMs != null) {
            // Subtract a safety margin: the search checks isTimeout() only
            // every 10 000 nodes, so a hard limit can overshoot by a few ms.
            // Strict GUIs treat that as a time forfeit.
            return Math.max(MIN_BUDGET_MS, raw.movetimeMs - TIME_SAFETY_MARGIN_MS);
        }
        if (raw.wtime != null && raw.btime != null) {
            return computeClockBudgetMillis(raw, turn);
        }

        return INFINITE_MILLIS;   // depth-only or no args
    }

    private static int computeClockBudgetMillis(RawGoTokens raw, int turn) {
        int ourMs = (turn == GameStatus.TURN_WHITE) ? raw.wtime : raw.btime;
        int movesToGo = raw.movestogo != null ? raw.movestogo : DEFAULT_MOVES_TO_GO;

        return Math.max(MIN_BUDGET_MS, ourMs / (movesToGo + 1) - TIME_SAFETY_MARGIN_MS);
    }

    /**
     * Read the int token at {@code valueIdx} if it exists, otherwise return
     * {@code fallback}. Caller is expected to have already advanced past the
     * keyword position.
     */
    private static int readIntValue(String[] tokens, int valueIdx, int fallback) {
        return valueIdx < tokens.length ? safeParseInt(tokens[valueIdx], fallback) : fallback;
    }

    private static int safeParseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    // ---- Output ----

    private static final Object stdoutLock = new Object();

    private static void writeLine(String line) {
        synchronized (stdoutLock) {
            IO.println(line);
        }
    }

    private static String firstToken(String line) {
        int space = line.indexOf(' ');
        return space < 0 ? line : line.substring(0, space);
    }
}
