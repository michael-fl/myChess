package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public final class Statistics {
    private long positionsCount;
    private long prunedMovesCount;
    private int maximumReachedDepth;

    public final void incrPositionCount() {
        positionsCount++;
    }

    public final void incrPrunedMovesCount(int increment) {
        prunedMovesCount += increment;
    }

    public final void reachedDepth(int depth) {
        if (depth > maximumReachedDepth) {
            maximumReachedDepth = depth;
        }
    }

    public final long getPositionsCount() {
        return positionsCount;
    }

    public final long getPrunedMovesCount() {
        return prunedMovesCount;
    }

    public final int getMaximumReachedDepth() {
        return maximumReachedDepth;
    }
}
