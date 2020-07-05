package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.MoveGenerator;
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

        MoveAndWeight(int move, float weight, int[] path) {
            this.move = move;
            this.weight = weight;
            this.path = path;
        }
    }

    private final Random rand = new Random();
    private final MovesCounter killerMoves = new MovesCounter(2);
    private final MovesCounter badMoves = new MovesCounter(5);
    final Game game;
    final MoveGenerator moveGenerator = new MoveGenerator(rand, killerMoves, badMoves);
    private final ExecutorService executor;

    ChessEngine(Game game) {
        this.game = game;
        this.executor = Executors.newSingleThreadExecutor();
    }

    final Random getRandom() {
        return rand;
    }

    public final NextMoveTask nextMoveAsync() {
        var task = new NextMoveTask();

        Future<MoveAndWeight> result = executor.submit(() -> calculateNextMove(task));

        task.setResultFuture(result);

        return task;
    }

    protected abstract MoveAndWeight calculateNextMove(NextMoveTask task);

}
