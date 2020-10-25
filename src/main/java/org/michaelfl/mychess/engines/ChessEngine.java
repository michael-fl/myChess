package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.MoveSorterImpl;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MovesCounter;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class ChessEngine {

    public final static class MoveAndWeight {

        public final static MoveAndWeight NO_MOVE = new MoveAndWeight(0, 0, new int[0]);

        public final int move;
        public final float weight;
        @SuppressWarnings("WeakerAccess")
        public final int[] path;

        public MoveAndWeight(int move, float weight, int[] path) {
            this.move = move;
            this.weight = weight;
            this.path = path;
        }
    }

    private final Random rand = new Random();
    private final MovesCounter killerMoves = new MovesCounter(2);
    private final ExecutorService executor;
    private final EngineConfig config;
    protected final Game game;
    protected final MoveGenerator moveGenerator = new MoveGenerator(new MoveSorterImpl(rand, killerMoves));

    protected ChessEngine(EngineConfig config, Game game) {
        this.config = config;
        this.game = game;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public final EngineConfig getConfig() {
        return config;
    }

    public final Random getRandom() {
        return rand;
    }

    public final NextMoveTask nextMoveAsync() {
        var task = new NextMoveTask();

        Future<MoveAndWeight> result = executor.submit(() -> calculateNextMove(task));

        task.setResultFuture(result);

        return task;
    }

    public Moves getPossibleMoves() {
        return moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());
    }
    protected abstract MoveAndWeight calculateNextMove(NextMoveTask task);

}
