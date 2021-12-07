package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public final class EngineConfig {

    private final static int DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH = 20;

    private final int maxDepth;
    private final int iterationDepth;
    private final int nVariants;
    private final boolean checkmateCheck;
    private final boolean useHandicap;
    private final boolean silent;

    private EngineConfig(int maxDepth, int iterationDepth, int nVariants, boolean checkmateCheck, boolean useHandicap, boolean silent) {
        this.maxDepth = maxDepth;
        this.iterationDepth = iterationDepth;
        this.nVariants = nVariants;
        this.checkmateCheck = checkmateCheck;
        this.useHandicap = useHandicap;
        this.silent = silent;
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

    public final boolean isCheckmateCheck() {
        return checkmateCheck;
    }

    public final boolean isUseHandicap() {
        return useHandicap;
    }

    public final boolean isSilent() {
        return silent;
    }

    public final static class Builder {
        private int maxDepth = 8;
        private int iterationDepth = 0;
        private int nVariants = 1;
        private boolean checkmateCheck = false;
        private boolean useHandicap = true;
        private boolean silent = false;

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder iterationDepth(int iterationDepth) {
            this.iterationDepth = iterationDepth;
            return this;
        }

        public Builder variants(int variants) {
            this.nVariants = variants;
            return this;
        }

        public Builder checkmateCheck(boolean checkmateCheck) {
            this.checkmateCheck = checkmateCheck;
            return this;
        }

        public Builder useHandicap(boolean useHandicap) {
            this.useHandicap = useHandicap;
            return this;
        }

        public Builder silent(boolean silent) {
            this.silent = silent;
            return this;
        }

        public EngineConfig build() {
            return new EngineConfig(maxDepth, iterationDepth, nVariants, checkmateCheck, useHandicap, silent);
        }
    }
}
