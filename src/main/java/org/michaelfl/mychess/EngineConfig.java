package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
@SuppressWarnings("FinalMethodInFinalClass")
public final class EngineConfig {

    private final static int DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH = 20;

    private final int maxDepth;
    private final int iterationDepth;
    private final int nVariants;
    private final boolean checkmateCheck;
    private final boolean useHandicap;
    private final boolean silent;
    private final boolean enableThreefoldRepetition;
    private final boolean enableFiftyMovesRule;

    private EngineConfig(int maxDepth, int iterationDepth, int nVariants, boolean checkmateCheck, boolean useHandicap, boolean silent, boolean enableThreefoldRepetition, boolean enableFiftyMovesRule) {
        this.maxDepth = maxDepth;
        this.iterationDepth = iterationDepth;
        this.nVariants = nVariants;
        this.checkmateCheck = checkmateCheck;
        this.useHandicap = useHandicap;
        this.silent = silent;
        this.enableThreefoldRepetition = enableThreefoldRepetition;
        this.enableFiftyMovesRule = enableFiftyMovesRule;
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

    public final boolean isEnableThreefoldRepetition() {
        return enableThreefoldRepetition;
    }

    public final boolean isEnableFiftyMovesRule() {
        return enableFiftyMovesRule;
    }

    public final static class Builder {
        private int maxDepth = 8;
        private int iterationDepth = 0;
        private int nVariants = 1;
        private boolean checkmateCheck = false;
        private boolean useHandicap = false;
        private boolean silent = false;
        private boolean enableThreefoldRepetition = true;
        private boolean enableFiftyMovesRule = true;

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

        public Builder enableThreefoldRepetition(boolean enableThreefoldRepetition) {
            this.enableThreefoldRepetition = enableThreefoldRepetition;
            return this;
        }

        public Builder enableFiftyMovesRule(boolean enableFiftyMovesRule) {
            this.enableFiftyMovesRule = enableFiftyMovesRule;
            return this;
        }

        public EngineConfig build() {
            return new EngineConfig(maxDepth, iterationDepth, nVariants, checkmateCheck, useHandicap, silent, enableThreefoldRepetition, enableFiftyMovesRule);
        }
    }
}
