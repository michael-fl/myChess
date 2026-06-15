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

    private final TranspositionTable tt;
    private final int maxDepth;
    private final int millisPerMove;
    private final boolean silent;
    private final boolean enableThreefoldRepetition;
    private final boolean enableFiftyMovesRule;

    private EngineConfig(TranspositionTable tt, int maxDepth, int millisPerMove, boolean silent, boolean enableThreefoldRepetition, boolean enableFiftyMovesRule) {
        this.tt = tt;
        this.maxDepth = maxDepth;
        this.millisPerMove = millisPerMove;
        this.silent = silent;
        this.enableThreefoldRepetition = enableThreefoldRepetition;
        this.enableFiftyMovesRule = enableFiftyMovesRule;
    }

    /**
     * The transposition table the engine should consult and update during
     * search. Either an explicit instance supplied via
     * {@link Builder#setTranspositionTable(TranspositionTable)} or, if
     * the builder caller did not set one, the lazy process-wide singleton
     * obtained from {@link TranspositionTable#getDefaultInstance()}.
     */
    public final TranspositionTable getTranspositionTable() {
        return tt;
    }

    public final int getMaxDepth() {
        return maxDepth;
    }

    public final int getMillisPerMove() {
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
        private TranspositionTable tt;
        private int maxDepth = Integer.MAX_VALUE;
        private int millisPerMove = DEFAULT_MILLIS_PER_MOVE;
        private boolean silent = false;
        private boolean enableThreefoldRepetition = true;
        private boolean enableFiftyMovesRule = true;

        /**
         * Provide an explicit {@link TranspositionTable} instance for the
         * engine to use. When this is not called, {@link #build()} falls
         * back to {@link TranspositionTable#getDefaultInstance()} — the
         * lazy process-wide singleton, appropriate for production / UCI
         * use. Tests should pass an isolated TT instance instead so
         * cached entries from one test cannot leak into the next.
         */
        public Builder setTranspositionTable(TranspositionTable tt) {
            this.tt = tt;
            return this;
        }

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
            if (tt == null) {
                tt = TranspositionTable.getDefaultInstance();
            }

            return new EngineConfig(tt, maxDepth, millisPerMove, silent, enableThreefoldRepetition, enableFiftyMovesRule);
        }
    }
}
