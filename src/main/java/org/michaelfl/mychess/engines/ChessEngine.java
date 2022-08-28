package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.KillerMoves;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MyChessEnv;
import org.michaelfl.mychess.WeightingFunction;
import org.michaelfl.mychess.openingdb.OpeningDB;
import org.michaelfl.mychess.openingdb.OpeningDB.MoveInfo;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public abstract class ChessEngine {

    public final static class MoveAndWeight {

        public final static MoveAndWeight NO_MOVE = new MoveAndWeight(0, 0, GameResult.ONGOING, new int[0]);

        public final int move;
        public final float weight;
        public final GameResult result;

        @SuppressWarnings("WeakerAccess")
        public final int[] path;

        public MoveAndWeight(int move, int weightCenti, GameResult result, int[] path) {
            this(move, weightCenti / 100.0f, result, path);
        }

        public MoveAndWeight(int move, float weight, GameResult result, int[] path) {
            this.move = move;
            this.weight = weight;
            this.result = result;
            this.path = path;
        }

        public MoveAndWeight weightFactor(int factor) {
            if (weight == 0f) {
                return this;
            }
            return new MoveAndWeight(move, weight * factor, result, path);
        }
    }

    private final Random rand = new Random();
    private final KillerMoves killerMoves = new KillerMoves();
    private final ExecutorService executor;
    private final EngineConfig config;
    protected final Game game;
    protected final MoveGenerator moveGenerator = new MoveGenerator(new MoveSorterImpl(killerMoves));

    protected ChessEngine(EngineConfig config, Game game) {
        this.config = config;
        this.game = game;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public final EngineConfig getConfig() {
        return config;
    }

    public final Random getRandom() {
        return rand;
    }

    public final NextMoveTask nextMoveAsync() {
        return nextMoveAsync(null);
    }

    public final NextMoveTask nextMoveAsync(MyChessEnv env) {
        var task = new NextMoveTask(env);

        Future<MoveAndWeight> result = executor.submit(() -> calculateNextMove(task));

        task.setResultFuture(result);

        return task;
    }

    public Moves getPossibleMoves() {
        return moveGenerator.calculateMoves(game.getBoard());
    }

    public final MoveAndWeight calculateNextMove(NextMoveTask task) {
        MoveAndWeight move = MoveAndWeight.NO_MOVE;
        var openingDB = task.getEnv().getOpeningDB();

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
                move = new MoveAndWeight(m.getMove(), 0, GameResult.ONGOING, new int[] { move.move });
            }
        }

        if (move == MoveAndWeight.NO_MOVE) {
            move = calculateNextMoveSub(task);
        }

        int weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;

        return move.weightFactor(weightFactor);
    }

    protected abstract MoveAndWeight calculateNextMoveSub(NextMoveTask task);

    protected void log(String s) {
        if (!config.isSilent()) {
            System.out.println(s);
        }
    }

    protected boolean isThreefoldRepetition() {
        return config.isEnableThreefoldRepetition() && game.getBoard().isThreefoldRepetition();
    }

    private Move getMoveFromOpeningDB(OpeningDB openingDB) {
        var key = game.getBoard().calculatePositionKey();
        var positionInfo = openingDB.lookupPosition(key);
        if (positionInfo == null) {
            return null;
        }

        var candidates = positionInfo.moves
                .stream()
                .filter(
                        m -> m.getTotalCount() >= 100
                                && m.getWinPercentage() >= 20
                                && m.getLossPercentage() < 45)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return null;
        }

        int sum = candidates.stream().mapToInt(MoveInfo::getTotalCount).sum();
        int n = getRandom().nextInt(sum);

        int i = 0;
        for (var m : candidates) {
            i += m.getTotalCount();
            if (n < i) {
                return m.move;
            }
        }

        throw new IllegalStateException();
    }

}
