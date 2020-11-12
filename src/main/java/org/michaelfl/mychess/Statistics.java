package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public final class Statistics {
    private long positionsCount;
    private long prunedMovesCount;
    private int maximumReachedDepth;
    private long quiescencePositionsCountTotal;
    private long quiescencePositionsCountCurrent;
    private long quiescencePositionsCountMax;
    private long quiescenceSearchesCount;

    public final void incrPositionCount() {
        positionsCount++;
    }

    public final void incrQuiescencePositionsCount() {
        quiescencePositionsCountCurrent++;
    }

    public final void incrPrunedMovesCount(int increment) {
        prunedMovesCount += increment;
    }

    public final void reachedDepth(int depth) {
        if (depth > maximumReachedDepth) {
            maximumReachedDepth = depth;
        }
    }

    public final void startQuiescenceSearch() {
        quiescenceSearchesCount++;
        quiescencePositionsCountCurrent = 0;
    }

    public final void endQuiescenceSearch() {
        if (quiescencePositionsCountCurrent > quiescencePositionsCountMax) {
            quiescencePositionsCountMax = quiescencePositionsCountCurrent;
        }
        quiescencePositionsCountTotal += quiescencePositionsCountCurrent;
        quiescencePositionsCountCurrent = 0;
    }

    public final long getPositionsCount() {
        return positionsCount;
    }

    public final long getQuiescencePositionsCount() {
        return quiescencePositionsCountTotal;
    }

    public final long getQuiescencePositionsCountMax() {
        return quiescencePositionsCountMax;
    }

    public final long getQuiescencePositionsCountAvg() {
        if (quiescenceSearchesCount == 0) {
            return 0;
        }
        return quiescencePositionsCountTotal / quiescenceSearchesCount;
    }

    public final long getPrunedMovesCount() {
        return prunedMovesCount;
    }

    public final int getMaximumReachedDepth() {
        return maximumReachedDepth;
    }
}
