package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.MyChessEnv;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Future-like handle for an asynchronous {@link ChessEngine#nextMoveAsync()}
 * call. Cooperative cancellation: the search polls {@link #isCanceled()} and
 * throws {@link java.util.concurrent.CancellationException}; carries the
 * {@link MyChessEnv} into the search task.
 *
 * @author Michael Fleischhauer
 */
public final class NextMoveTask {

    private final MyChessEnv env;
    private Future<MoveAndWeight> resultFuture;
    private volatile boolean isCanceled;

    public NextMoveTask() {
        this(null);
    }

    public NextMoveTask(MyChessEnv env) {
        this.env = env != null ? env : new MyChessEnv();
    }

    public MyChessEnv getEnv() {
        return env;
    }

    public void cancel() {
        isCanceled = true;
        resultFuture.cancel(false);
    }

    public MoveAndWeight getResult(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return resultFuture.get(timeout, unit);
    }

    void setResultFuture(Future<MoveAndWeight> resultFuture) {
        this.resultFuture = resultFuture;
    }

    public boolean isCanceled() {
        return isCanceled;
    }
}
