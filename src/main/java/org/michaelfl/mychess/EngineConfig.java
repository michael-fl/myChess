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
    private final int remainingClockMillis;
    private final boolean silent;
    private final boolean enableThreefoldRepetition;
    private final boolean enableFiftyMovesRule;
    private final String logTag;
    private final int strengthMaxDepth;
    private final int moveToleranceCenti;
    private final int moveToleranceNearCenti;
    private final boolean moveToleranceFilter;
    private final int moveToleranceReturnPlies;
    private final int moveToleranceSamePiece;

    /** Default of the UCI option {@code MoveToleranceNear}. */
    public static final int DEFAULT_MOVE_TOLERANCE_NEAR_CENTI = 20;

    /** Default of the UCI option {@code MoveToleranceReturnPlies}. */
    public static final int DEFAULT_MOVE_TOLERANCE_RETURN_PLIES = 3;

    /** Default of the UCI option {@code MoveToleranceSamePiece}. */
    public static final int DEFAULT_MOVE_TOLERANCE_SAME_PIECE = 3;

    private EngineConfig(Builder builder) {
        this.tt = builder.tt;
        this.maxDepth = builder.maxDepth;
        this.millisPerMove = builder.millisPerMove;
        this.remainingClockMillis = builder.remainingClockMillis;
        this.silent = builder.silent;
        this.enableThreefoldRepetition = builder.enableThreefoldRepetition;
        this.enableFiftyMovesRule = builder.enableFiftyMovesRule;
        this.logTag = builder.logTag;
        this.strengthMaxDepth = builder.strengthMaxDepth;
        this.moveToleranceCenti = builder.moveToleranceCenti;
        this.moveToleranceNearCenti = builder.moveToleranceNearCenti;
        this.moveToleranceFilter = builder.moveToleranceFilter;
        this.moveToleranceReturnPlies = builder.moveToleranceReturnPlies;
        this.moveToleranceSamePiece = builder.moveToleranceSamePiece;
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

    /**
     * UCI option {@code MaxDepth} in plies. 0 means no limit. Unlike {@link #getMaxDepth()} it holds for
     * every search of the session.
     */
    public final int getStrengthMaxDepth() {
        return strengthMaxDepth;
    }

    /**
     * UCI option {@code MoveTolerance} in centipawns. The engine plays a move less than this below the
     * best move. 0 plays the best move.
     */
    public final int getMoveToleranceCenti() {
        return moveToleranceCenti;
    }

    /**
     * UCI option {@code MoveToleranceNear} in centipawns. Candidates below it form the near group. That
     * group is played 80 % of the time.
     */
    public final int getMoveToleranceNearCenti() {
        return moveToleranceNearCenti;
    }

    /** UCI option {@code MoveToleranceFilter}. Keeps unnatural moves out of the far group. */
    public final boolean isMoveToleranceFilter() {
        return moveToleranceFilter;
    }

    /** UCI option {@code MoveToleranceReturnPlies}. N of the filter's return rule. */
    public final int getMoveToleranceReturnPlies() {
        return moveToleranceReturnPlies;
    }

    /** UCI option {@code MoveToleranceSamePiece}. M of the filter's same-piece rule. */
    public final int getMoveToleranceSamePiece() {
        return moveToleranceSamePiece;
    }

    public final int getMillisPerMove() {
        return millisPerMove;
    }

    /**
     * Remaining clock of the side to move at the start of this search, in milliseconds.
     *
     * @return the clock the GUI sent ({@code wtime} or {@code btime}), possibly zero or negative
     *         for an overstepped clock; {@link Integer#MAX_VALUE} if no clock was sent
     *         ({@code go movetime}, {@code go depth}, {@code go infinite}, or a config built
     *         without one) — that value means "no clock", not a real remaining time
     */
    public final int getRemainingClockMillis() {
        return remainingClockMillis;
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

    /**
     * Short tag that the search puts into its {@code [time]} log lines, so lines of engines writing
     * into one log at once can be told apart, e.g. {@code game=4174bf06}.
     *
     * @return the tag, or an empty string if none was set
     */
    public String getLogTag() {
        return logTag;
    }

    public static final class Builder {
        private TranspositionTable tt;
        private int maxDepth = Integer.MAX_VALUE;
        private int millisPerMove = DEFAULT_MILLIS_PER_MOVE;
        private int remainingClockMillis = Integer.MAX_VALUE;
        private boolean silent = false;
        private boolean enableThreefoldRepetition = true;
        private boolean enableFiftyMovesRule = true;
        private String logTag = "";
        private int strengthMaxDepth = 0;
        private int moveToleranceCenti = 0;
        private int moveToleranceNearCenti = DEFAULT_MOVE_TOLERANCE_NEAR_CENTI;
        private boolean moveToleranceFilter = true;
        private int moveToleranceReturnPlies = DEFAULT_MOVE_TOLERANCE_RETURN_PLIES;
        private int moveToleranceSamePiece = DEFAULT_MOVE_TOLERANCE_SAME_PIECE;

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

        /**
         * Sets the tag for the search's {@code [time]} log lines; see {@link EngineConfig#getLogTag()}.
         *
         * @param logTag the tag, e.g. {@code game=4174bf06}; {@code null} is treated as no tag
         * @return this builder
         */
        public Builder logTag(String logTag) {
            this.logTag = logTag == null ? "" : logTag;
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

        public Builder remainingClockMillis(int remainingClockMillis) {
            this.remainingClockMillis = remainingClockMillis;
            return this;
        }

        public Builder strengthMaxDepth(int strengthMaxDepth) {
            this.strengthMaxDepth = strengthMaxDepth;
            return this;
        }

        public Builder moveToleranceCenti(int moveToleranceCenti) {
            this.moveToleranceCenti = moveToleranceCenti;
            return this;
        }

        public Builder moveToleranceNearCenti(int moveToleranceNearCenti) {
            this.moveToleranceNearCenti = moveToleranceNearCenti;
            return this;
        }

        public Builder moveToleranceFilter(boolean moveToleranceFilter) {
            this.moveToleranceFilter = moveToleranceFilter;
            return this;
        }

        public Builder moveToleranceReturnPlies(int moveToleranceReturnPlies) {
            this.moveToleranceReturnPlies = moveToleranceReturnPlies;
            return this;
        }

        public Builder moveToleranceSamePiece(int moveToleranceSamePiece) {
            this.moveToleranceSamePiece = moveToleranceSamePiece;
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

            return new EngineConfig(this);
        }
    }
}
