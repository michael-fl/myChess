package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Log;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MyChessEnv;
import org.michaelfl.mychess.WeightingFunction;
import org.michaelfl.mychess.openingdb.OpeningDB;
import org.michaelfl.mychess.openingdb.OpeningDB.MoveInfo;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Abstract engine base: owns the single-thread executor behind
 * {@link #nextMoveAsync}, the opening-book lookup with the thresholds
 * (>=100 occurrences, >=20% wins, <45% losses) and the pre-search shortcuts
 * for game-over / threefold / 50-move-rule. Subclasses (currently
 * {@link MyChessEngine}) implement the actual search.
 *
 * @author Michael Fleischhauer
 */
public abstract class ChessEngine {

    @SuppressWarnings("java:S6218")
    public record MoveAndWeight(int move, float weight, GameResult result,
                                @SuppressWarnings("WeakerAccess") int[] path) {

            public static final MoveAndWeight NO_MOVE = new MoveAndWeight(0, 0, GameResult.ONGOING, new int[0]);

            public MoveAndWeight(int move, int weightCenti, GameResult result, int[] path) {
                this(move, weightCenti / 100.0f, result, path);
            }

            public MoveAndWeight weightFactor(int factor) {
                if (weight == 0f) {
                    return this;
                }
                return new MoveAndWeight(move, weight * factor, result, path);
            }
        }

    private final Random rand = new Random();
    private final ExecutorService executor;
    private final EngineConfig config;
    protected final Game game;

    protected ChessEngine(EngineConfig config, Game game) {
        this.config = config;
        this.game = game;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * Drop all per-depth iteration-time statistics. Called by
     * {@code UciHandler} on {@code ucinewgame} so a
     * new game does not inherit stale stats from the previous one. Bridges
     * package-private {@link IterationTimings} state to callers outside
     * the {@code engines} package.
     */
    public static void resetIterationTimings() {
        IterationTimings.reset();
    }

    public final EngineConfig getConfig() {
        return config;
    }

    public final Random getRandom() {
        return rand;
    }

    public final NextMoveTask nextMoveAsync() {
        return nextMoveAsync(null, null);
    }

    public final NextMoveTask nextMoveAsync(MyChessEnv env) {
        return nextMoveAsync(env, null);
    }

    /**
     * Asynchronous move calculation with an optional listener that is invoked
     * after every completed iterative-deepening iteration. Used by the UCI
     * handler to emit {@code info depth N ...} lines as the search progresses.
     */
    public final NextMoveTask nextMoveAsync(MyChessEnv env, Consumer<IterationInfo> iterationListener) {
        var task = new NextMoveTask(env);
        if (iterationListener != null) {
            task.setIterationListener(iterationListener);
        }

        Future<MoveAndWeight> result = executor.submit(() -> calculateNextMove(task));
        task.setResultFuture(result);

        return task;
    }

    public abstract Moves getPossibleMoves();

    @SuppressWarnings({"java:S2095","java:S2589"})
    public final MoveAndWeight calculateNextMove(NextMoveTask task) {
        MoveAndWeight move = MoveAndWeight.NO_MOVE;
        var openingDB = task.getEnv().openingDB();

        // First check if this game is already finished
        if (game.getResult() != GameResult.ONGOING) {
            if (game.getResult() == GameResult.CHECKMATE) {
                move = new MoveAndWeight(0, -WeightingFunction.checkmateInCenti(), GameResult.CHECKMATE, new int[0]);
            } else {
                move = new MoveAndWeight(0, 0, game.getResult(), new int[0]);
            }
        } else if ((getConfig().isEnableFiftyMovesRule() && game.getGameStatus().getHalfMoveClock() >= 100) || isThreefoldRepetition()) {
            move = new MoveAndWeight(0, 0, GameResult.DRAW, new int[0]);
        } else if (openingDB != null) {
            var m = getMoveFromOpeningDB(openingDB);
            if (m != null) {
                move = new MoveAndWeight(m.move(), 0, GameResult.ONGOING, new int[] { move.move });
            }
        }
        // Note: when openingDB is null we silently fall through. The startup log
        // in MyChessMain already reports the DB state once; per-call noise is
        // dominated by the statusEngine (Game's mate/stalemate detector) which
        // intentionally has no env attached.

        if (move == MoveAndWeight.NO_MOVE) {
            move = calculateNextMoveSub(task);
        }

        int weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;

        return move.weightFactor(weightFactor);
    }

    protected abstract MoveAndWeight calculateNextMoveSub(NextMoveTask task);

    protected void log(String s) {
        if (!config.isSilent()) {
            Log.info(s);
        }
    }

    protected boolean isThreefoldRepetition() {
        return config.isEnableThreefoldRepetition() && game.getBoard().isThreefoldRepetition();
    }

    private Move getMoveFromOpeningDB(OpeningDB openingDB) {
        var key = game.getBoard().calculatePositionKey();
        var positionInfo = openingDB.lookupPosition(key);

        if (positionInfo == null) {
            if (BookMissThrottle.recordMissAndShouldLog()) {
                Log.info("[book] miss — no entry for position key=" + key);
            }
            return null;
        }

        // Lookup found the position in the DB — reset the miss streak. Any
        // subsequent run of misses will start logging from scratch.
        BookMissThrottle.recordHit();

        int totalMoves = positionInfo.moves.size();
        var candidates = positionInfo.moves
                .stream()
                .filter(
                        m -> m.getTotalCount() >= 100
                                && m.getWinPercentage() >= 20
                                && m.getLossPercentage() < 45)
                .toList();

        if (candidates.isEmpty()) {
            Log.info("[book] hit but no candidates pass filter (>=100 games, >=20% wins, <45% losses)"
                    + " — " + totalMoves + " moves in DB, all rejected. key=" + key);
            return null;
        }

        int sum = candidates.stream().mapToInt(MoveInfo::getTotalCount).sum();
        int n = getRandom().nextInt(sum);

        int i = 0;
        for (var m : candidates) {
            i += m.getTotalCount();
            if (n < i) {
                Log.info("[book] picked " + m.move + " from " + candidates.size()
                        + "/" + totalMoves + " candidates (weighted random over " + sum + " games). key=" + key);
                return m.move;
            }
        }

        throw new IllegalStateException();
    }

}
