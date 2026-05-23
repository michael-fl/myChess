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
    private boolean handleLine(String line) {
        if (line.isEmpty()) {
            return true;
        }
        String first = firstToken(line);

        return switch (first) {
            case "uci" -> { handleUci(); yield true; }
            case "isready" -> { handleIsReady(); yield true; }
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

    private void handleNewGame() {
        cancelCurrentTask();
        shutdownCurrentGame();
        this.board = Board.createNewGame();
        ChessEngine.resetIterationTimings();
        this.gameId = UUID.randomUUID().toString();
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
                newBoard = Fen.importFEN(stem.substring("fen ".length()));
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

        // Snapshot for the per-move [move] status log: color and full-move
        // number are derived from the position at search start, not from any
        // intermediate state inside the search.
        final int turnAtStart = board.getGameStatus().getTurn();
        final int plyCountAtStart = board.getGameStatus().getPlyCount();

        var engineConfig = new EngineConfig.Builder()
                .maxDepth(args.maxDepth)
                .millisPerMove(args.timeBudgetMillis)
                .silent(true)
                .build();
        var gameConfig = new GameConfig(MyChessEngine.class, engineConfig);

        var game = new Game(gameConfig, board);
        currentGame.set(game);

        long searchStartMs = System.currentTimeMillis();
        ChessEngine engine = game.getEngine();
        NextMoveTask task = engine.nextMoveAsync(env, info -> emitInfo(info, searchStartMs));
        currentTask.set(task);

        // Spawn a virtual thread to wait on the search result; the main UCI
        // loop stays unblocked and can process stop/quit immediately.
        // Virtual threads are always daemons, so no explicit daemon flag.
        Thread watcher = Thread.ofVirtual()
                .name("uci-search-watcher")
                .start(() -> awaitAndEmitBestmove(game, task, args.timeBudgetMillis, turnAtStart, plyCountAtStart));
        currentWatcher.set(watcher);
    }

    private void handleStop() {
        cancelCurrentTask();
    }

    // ---- Search lifecycle ----

    private void awaitAndEmitBestmove(Game game, NextMoveTask task, int budgetMillis,
                                      int turnAtStart, int plyCountAtStart) {
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
            int bestmove   = (result != null) ? result.move()   : lastIterationFirstMove.get();
            float bestWeight = (result != null) ? result.weight() : lastIterationWeight.get();

            writeLine("bestmove " + (bestmove == 0 ? "0000" : UciMoveParser.toUci(bestmove)));
            logMoveStatus(bestmove, bestWeight, turnAtStart, plyCountAtStart);
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

    private void emitInfo(IterationInfo info, long searchStartMs) {
        int[] pv = info.pv();
        if (pv.length > 0 && pv[0] != 0) {
            lastIterationFirstMove.set(pv[0]);
            lastIterationWeight.set(info.weight());
        }

        var sb = new StringBuilder();
        sb.append("info depth ").append(info.depth());
        sb.append(" nodes ").append(info.nodes());
        sb.append(" time ").append(System.currentTimeMillis() - searchStartMs);

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

                sb.append(' ').append(UciMoveParser.toUci(packed));
            }
        }

        writeLine(sb.toString());

        validatePv(pv);
    }

    /**
     * One-line status entry written to stderr per move myChess plays.
     * Intended for skimming a {@code mychess-stderr.log} during long
     * cutechess matches, especially under {@code -concurrency > 1} where
     * several games stream their move records into the same file.
     *
     * <p>Format:
     * <pre>{@code [move] game=<8-char> color=<W|B> move=<N> uci=<...> evalW=<...>}</pre>
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
     *   <li>{@code evalW} — the search's reported eval, *always from
     *       White's perspective* (negated when we play Black) so the sign
     *       is consistent across both engine roles. Plain centipawns in
     *       pawn units (e.g. {@code +0.30}) for normal evals;
     *       {@code +M3} / {@code -M3} for mate-in-N-full-moves scores.</li>
     * </ul>
     *
     * <p>No log line is written when no move was actually played
     * (search returned {@code 0} — checkmate / stalemate at the root, or
     * the search aborted before producing any iteration).
     */
    private void logMoveStatus(int bestmove, float weight, int turnAtStart, int plyCountAtStart) {
        if (bestmove == 0) {
            return;
        }

        String color = (turnAtStart == GameStatus.TURN_WHITE) ? "W" : "B";
        int fullMoveNumber = (plyCountAtStart / 2) + 1;
        String uciMove = UciMoveParser.toUci(bestmove);
        float evalWhitePov = (turnAtStart == GameStatus.TURN_WHITE) ? weight : -weight;
        String evalStr = formatEvalForLog(evalWhitePov);
        String shortGameId = gameId.length() >= 8 ? gameId.substring(0, 8) : gameId;

        Log.info(String.format(Locale.ROOT,
                "[move] game=%s color=%s move=%d uci=%s evalW=%s",
                shortGameId, color, fullMoveNumber, uciMove, evalStr));
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
    private void validatePv(int[] pv) {
        if (pv.length == 0 || pv[0] == 0) {
            return;
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
                logIllegalPv("ply " + lastAppliedPly + " (" + UciMoveParser.toUci(lastAppliedMove)
                        + ") leaves own king in check", pv, probe);
                return;
            }

            if (!pseudoLegal.contains(move)) {
                logIllegalPv("ply " + i + " (" + UciMoveParser.toUci(move)
                        + ") is not pseudo-legal", pv, probe);
                return;
            }

            probe.makeMove(move);
            pseudoLegal = moveGen.calculateMoves(probe);
            lastAppliedMove = move;
            lastAppliedPly = i;
        }

        if (pseudoLegal.isIllegal()) {
            logIllegalPv("ply " + lastAppliedPly + " (" + UciMoveParser.toUci(lastAppliedMove)
                    + ") leaves own king in check", pv, probe);
        }
    }

    private void logIllegalPv(String detail, int[] pv, Board atPosition) {
        Log.error("[pv-validate] " + detail
                + " — PV: " + formatPv(pv)
                + " — root FEN: " + Fen.exportFEN(board)
                + " — illegal at FEN: " + Fen.exportFEN(atPosition));
    }

    private static String formatPv(int[] pv) {
        var sb = new StringBuilder();
        for (int m : pv) {
            if (m == 0) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }

            sb.append(UciMoveParser.toUci(m));
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

    private record GoArgs(int maxDepth, int timeBudgetMillis) {}

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

        return new GoArgs(raw.maxDepth, budgetMillis);
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
