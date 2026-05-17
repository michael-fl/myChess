package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
@SuppressWarnings("FinalMethodInFinalClass")
public final class EngineConfig {

    private static final int DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH = 20;
    private static final int DEFAULT_SECONDS_PER_MOVE = 30;

    private final int maxDepth;
    private final int secondsPerMove;
    private final boolean silent;
    private final boolean enableThreefoldRepetition;
    private final boolean enableFiftyMovesRule;

    private EngineConfig(int maxDepth, int secondsPerMove, boolean silent, boolean enableThreefoldRepetition, boolean enableFiftyMovesRule) {
        this.maxDepth = maxDepth;
        this.secondsPerMove = secondsPerMove;
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

    public final int getMaxQuiescenceDepth() {
        return DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH;
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

    public static final class Builder {
        private int maxDepth = Integer.MAX_VALUE;
        private int secondsPerMove = DEFAULT_SECONDS_PER_MOVE;
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
            return new EngineConfig(maxDepth, secondsPerMove, silent, enableThreefoldRepetition, enableFiftyMovesRule);
        }
    }
}
