package org.michaelfl.mychess;

/**
 * Tuning parameters for a {@link org.michaelfl.mychess.engines.ChessEngine}:
 * max search depth, millisecond-per-move budget, log verbosity and whether the
 * threefold-repetition / 50-move draw rules are honored. Built via
 * {@link Builder}.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("FinalMethodInFinalClass")
public final class EngineConfig {

    private static final int DEFAULT_MAX_QUIESCENCE_SEARCH_DEPTH = 20;
    private static final int DEFAULT_MILLIS_PER_MOVE = 30_000;

    private final int maxDepth;
    private final int millisPerMove;
    private final boolean silent;
    private final boolean enableThreefoldRepetition;
    private final boolean enableFiftyMovesRule;

    private EngineConfig(int maxDepth, int millisPerMove, boolean silent, boolean enableThreefoldRepetition, boolean enableFiftyMovesRule) {
        this.maxDepth = maxDepth;
        this.millisPerMove = millisPerMove;
        this.silent = silent;
        this.enableThreefoldRepetition = enableThreefoldRepetition;
        this.enableFiftyMovesRule = enableFiftyMovesRule;
    }

    public final int getMaxDepth() {
        return maxDepth;
    }

    public int getMillisPerMove() {
        return millisPerMove;
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
        private int millisPerMove = DEFAULT_MILLIS_PER_MOVE;
        private boolean silent = false;
        private boolean enableThreefoldRepetition = true;
        private boolean enableFiftyMovesRule = true;

        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder millisPerMove(int millisPerMove) {
            this.millisPerMove = millisPerMove;
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
            return new EngineConfig(maxDepth, millisPerMove, silent, enableThreefoldRepetition, enableFiftyMovesRule);
        }
    }
}
