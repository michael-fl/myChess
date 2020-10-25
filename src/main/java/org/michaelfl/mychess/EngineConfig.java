package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public final class EngineConfig {

    private final static int DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH = 20;

    private final int maxDepth;
    private final int iterationDepth;
    private final int nVariants;

    public EngineConfig(int maxDepth) {
        this.maxDepth = maxDepth;
        this.iterationDepth = 0;
        this.nVariants = 1;
    }

    public EngineConfig(int maxDepth, int iterationDepth, int nVariants) {
        this.maxDepth = maxDepth;
        this.iterationDepth = iterationDepth;
        this.nVariants = nVariants;
    }

    public EngineConfig setMaxDepth(int maxDepth) {
        return new EngineConfig(maxDepth, this.iterationDepth, this.nVariants);
    }

    public EngineConfig setIterationDepth(int iterationDepth) {
        return new EngineConfig(this.maxDepth, iterationDepth, this.nVariants);
    }

    public EngineConfig setNVariants(int nVariants) {
        return new EngineConfig(this.maxDepth, this.iterationDepth, nVariants);
    }

    public final int getMaxDepth() {
        return maxDepth;
    }

    public final int getIterationDepth() {
        return iterationDepth;
    }

    public final int getMaxQuiescenceDepth() {
        return DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH;
    }

    public final int getNVariants() {
        return nVariants;
    }
}
