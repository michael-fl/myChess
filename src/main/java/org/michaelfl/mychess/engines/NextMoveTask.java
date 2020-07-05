package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Michael Fleischhauer
 */
public final class NextMoveTask {

    private Future<MoveAndWeight> resultFuture;
    private volatile boolean isCanceled;

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

    public final boolean isCanceled() {
        return isCanceled;
    }
}
