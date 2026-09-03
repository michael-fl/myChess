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
    private long materialOnlyLeafCount;

    public void incrPositionCount() {
        positionsCount++;
    }

    public void incrNmpCutoffCount() {
        nmpCutoffCount++;
    }

    public void incrQuiescencePositionsCount() {
        quiescencePositionsCountCurrent++;
    }

    /**
     * Counts one leaf that returned raw material because the material swing since the search root
     * passed {@link org.michaelfl.mychess.engines.PositionSearch#EVALUATE_MATERIAL_ONLY_THRESHOLD}.
     *
     * <p>Exists to make the shortcut's reach observable, which it was not before. The whole
     * positional evaluation is skipped in those leaves, so any evaluation term that measures worse
     * than expected raises the question of how often it was consulted at all — and until this
     * counter there was no way to ask. It was added while a king-safety term was being priced
     * (see {@code docs/king-safety.md}); that term is shelved, the question is not.
     *
     * <p>Read the count against {@link #getQuiescencePositionsCount()}: the check runs once per
     * quiescence node entry, so the quotient is the firing rate.
     */
    public void incrMaterialOnlyLeafCount() {
        materialOnlyLeafCount++;
    }

    public void incrPrunedMovesCount(int increment) {
        prunedMovesCount += increment;
    }

    /**
     * Leaves that returned raw material because the material-only shortcut fired.
     *
     * @return the count for this search, to be read against {@link #getQuiescencePositionsCount()}
     */
    public long getMaterialOnlyLeafCount() {
        return materialOnlyLeafCount;
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
