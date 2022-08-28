package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
@SuppressWarnings("FinalMethodInFinalClass")
public final class EngineConfig {

    private final static int DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH = 20;
    private final static int DEFAULT_SECONDS_PER_MOVE = 30;

    private final int maxDepth;
    private final int secondsPerMove;
    private final int iterationDepth;
    private final int nVariants;
    private final boolean checkmateCheck;
    private final boolean silent;
    private final boolean enableThreefoldRepetition;
    private final boolean enableFiftyMovesRule;

    private EngineConfig(int maxDepth, int secondsPerMove, int iterationDepth, int nVariants, boolean checkmateCheck, boolean silent, boolean enableThreefoldRepetition, boolean enableFiftyMovesRule) {
        this.maxDepth = maxDepth;
        this.secondsPerMove = secondsPerMove;
        this.iterationDepth = iterationDepth;
        this.nVariants = nVariants;
        this.checkmateCheck = checkmateCheck;
        this.silent = silent;
        this.enableThreefoldRepetition = enableThreefoldRepetition;
        this.enableFiftyMovesRule = enableFiftyMovesRule;
    }

    public final int getMaxDepth() {
        return maxDepth;
    }

    public int getSecondsPerMove() {
        return secondsPerMove;
    }

    // Only used by engine V1
    public final int getIterationDepth() {
        return iterationDepth;
    }

    public final int getMaxQuiescenceDepth() {
        return DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH;
    }

    // Only used by engine V1
    public final int getNVariants() {
        return nVariants;
    }

    public final boolean isCheckmateCheck() {
        return checkmateCheck;
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
        private int maxDepth = Integer.MAX_VALUE;
        private int secondsPerMove = DEFAULT_SECONDS_PER_MOVE;
        private int iterationDepth = 0;
        private int nVariants = 1;
        private boolean checkmateCheck = false;
        private boolean silent = false;
        private boolean enableThreefoldRepetition = true;
        private boolean enableFiftyMovesRule = true;

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder secondsPerMove(int secondsPerMove) {
            this.secondsPerMove = secondsPerMove;
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
            return new EngineConfig(maxDepth, secondsPerMove, iterationDepth, nVariants, checkmateCheck, silent, enableThreefoldRepetition, enableFiftyMovesRule);
        }
    }
}
