package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public final class GameConfig {

    private final static int DEFAULT_MAX_DEPTH = 14;
    private final static int DEFAULT_N_VARIANTS = 4;
    private final static int DEFAULT_ITERATION_DEPTH = 6;

    private final int maxDepth;
    private final int iterationDepth;
    private final int nVariants;

    GameConfig() {
        maxDepth = DEFAULT_MAX_DEPTH;
        iterationDepth = DEFAULT_ITERATION_DEPTH;
        nVariants = DEFAULT_N_VARIANTS;
    }

    private GameConfig(int maxDepth, int iterationDepth, int nVariants) {
        this.maxDepth = maxDepth;
        this.iterationDepth = iterationDepth;
        this.nVariants = nVariants;
    }

    public GameConfig setMaxDepth(int maxDepth) {
        return new GameConfig(maxDepth, this.iterationDepth, this.nVariants);
    }

    public GameConfig setIterationDepth(int iterationDepth) {
        return new GameConfig(this.maxDepth, iterationDepth, this.nVariants);
    }

    public GameConfig setNVariants(int nVariants) {
        return new GameConfig(this.maxDepth, this.iterationDepth, nVariants);
    }

    public final int getMaxDepth() {
        return maxDepth;
    }

    public final int getIterationDepth() {
        return iterationDepth;
    }

    public final int getNVariants() {
        return nVariants;
    }
}
