package org.michaelfl.mychess;

/**
 * Mutable counters collected during a search run: visited positions, pruned
 * moves, max reached depth, and per-call quiescence aggregates. Read by
 * {@link org.michaelfl.mychess.engines.PositionSearch} for its 10000-node
 * timeout check.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("unused")
public final class Statistics {
    private long positionsCount;
    private long nmpCutoffCount;
    private long prunedMovesCount;
    private int maximumReachedDepth;
    private long quiescencePositionsCountTotal;
    private long quiescencePositionsCountCurrent;
    private long quiescencePositionsCountMax;
    private long quiescenceSearchesCount;

    public void incrPositionCount() {
        positionsCount++;
    }

    public void incrNmpCutoffCount() {
        nmpCutoffCount++;
    }

    public void incrQuiescencePositionsCount() {
        quiescencePositionsCountCurrent++;
    }

    public void incrPrunedMovesCount(int increment) {
        prunedMovesCount += increment;
    }

    public void reachedDepth(int depth) {
        if (depth > maximumReachedDepth) {
            maximumReachedDepth = depth;
        }
    }

    public void startQuiescenceSearch() {
        quiescenceSearchesCount++;
        quiescencePositionsCountCurrent = 0;
    }

    public void endQuiescenceSearch() {
        if (quiescencePositionsCountCurrent > quiescencePositionsCountMax) {
            quiescencePositionsCountMax = quiescencePositionsCountCurrent;
        }
        quiescencePositionsCountTotal += quiescencePositionsCountCurrent;
        quiescencePositionsCountCurrent = 0;
    }

    public long getPositionsCount() {
        return positionsCount;
    }

    public long getNmpCutoffCount() {
        return nmpCutoffCount;
    }

    public long getQuiescencePositionsCount() {
        return quiescencePositionsCountTotal;
    }

    public long getQuiescencePositionsCountMax() {
        return quiescencePositionsCountMax;
    }

    public long getQuiescencePositionsCountAvg() {
        if (quiescenceSearchesCount == 0) {
            return 0;
        }
        return quiescencePositionsCountTotal / quiescenceSearchesCount;
    }

    public long getPrunedMovesCount() {
        return prunedMovesCount;
    }

    public int getMaximumReachedDepth() {
        return maximumReachedDepth;
    }

    @Override
    public String toString() {
        return "Statistics{" +
                "positionsCount=" + positionsCount +
                ", nmpCutoffCount=" + nmpCutoffCount +
                ", prunedMovesCount=" + prunedMovesCount +
                ", maximumReachedDepth=" + maximumReachedDepth +
                ", quiescencePositionsCountTotal=" + quiescencePositionsCountTotal +
                ", quiescencePositionsCountCurrent=" + quiescencePositionsCountCurrent +
                ", quiescencePositionsCountMax=" + quiescencePositionsCountMax +
                ", quiescenceSearchesCount=" + quiescenceSearchesCount +
                '}';
    }
}
