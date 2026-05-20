package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.IterationInfo;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.NextMoveTask;

import java.io.BufferedReader;
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

    /** Ceiling on go infinite / go depth N max seconds (effectively unbounded). */
    private static final int INFINITE_SECONDS = 24 * 60 * 60;

    private final MyChessEnv env;
    private final BufferedReader in;

    private Board board;
    private final AtomicReference<NextMoveTask> currentTask = new AtomicReference<>();
    private final AtomicReference<Game> currentGame = new AtomicReference<>();
    private final AtomicReference<Thread> currentWatcher = new AtomicReference<>();

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
        writeLine("uciok");
    }

    private void handleIsReady() {
        writeLine("readyok");
    }

    private void handleNewGame() {
        cancelCurrentTask();
        shutdownCurrentGame();
        this.board = Board.createNewGame();
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

        var engineConfig = new EngineConfig.Builder()
                .maxDepth(args.maxDepth)
                .secondsPerMove(args.timeBudgetSeconds)
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
                .start(() -> awaitAndEmitBestmove(game, task, args.timeBudgetSeconds));
        currentWatcher.set(watcher);
    }

    private void handleStop() {
        cancelCurrentTask();
    }

    // ---- Search lifecycle ----

    private void awaitAndEmitBestmove(Game game, NextMoveTask task, int budgetSeconds) {
        int bestmove = 0;
        try {
            MoveAndWeight result = task.getResult(budgetSeconds + 1L, TimeUnit.SECONDS);
            bestmove = result.move;
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof CancellationException)) {
                Log.error("Search failed", e);
            }
            bestmove = lastIterationFirstMove.get();
        } catch (CancellationException _) {
            bestmove = lastIterationFirstMove.get();
        } catch (TimeoutException _) {
            task.cancel();
            bestmove = lastIterationFirstMove.get();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            bestmove = lastIterationFirstMove.get();
        } finally {
            writeLine("bestmove " + (bestmove == 0 ? "0000" : UciMoveParser.toUci(bestmove)));
            game.shutdown();

            currentTask.compareAndSet(task, null);
            currentGame.compareAndSet(game, null);
            currentWatcher.compareAndSet(Thread.currentThread(), null);
            lastIterationFirstMove.set(0);
        }
    }

    /** Best move from the most recent iteration that completed, for stop/timeout fallback. */
    private final AtomicReference<Integer> lastIterationFirstMove = new AtomicReference<>(0);

    private void emitInfo(IterationInfo info, long searchStartMs) {
        int[] pv = info.pv();
        if (pv.length > 0 && pv[0] != 0) {
            lastIterationFirstMove.set(pv[0]);
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

    private record GoArgs(int maxDepth, int timeBudgetSeconds) {}

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
        int budgetSeconds = computeBudgetSeconds(raw, turn);

        return new GoArgs(raw.maxDepth, budgetSeconds);
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

    private static int computeBudgetSeconds(RawGoTokens raw, int turn) {
        if (raw.infinite) {
            return INFINITE_SECONDS;
        }
        if (raw.movetimeMs != null) {
            return msToSeconds(raw.movetimeMs);
        }
        if (raw.wtime != null && raw.btime != null) {
            return computeClockBudgetSeconds(raw, turn);
        }

        return INFINITE_SECONDS;   // depth-only or no args
    }

    private static int computeClockBudgetSeconds(RawGoTokens raw, int turn) {
        int ourMs = (turn == GameStatus.TURN_WHITE) ? raw.wtime : raw.btime;
        int movesToGo = raw.movestogo != null ? raw.movestogo : DEFAULT_MOVES_TO_GO;
        int budgetMs = Math.max(MIN_BUDGET_MS, ourMs / (movesToGo + 1) - TIME_SAFETY_MARGIN_MS);

        return msToSeconds(budgetMs);
    }

    private static int msToSeconds(int ms) {
        return Math.max(1, (ms + 999) / 1000);
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
