package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.MoveSorter;
import org.michaelfl.mychess.TranspositionTable;
import org.michaelfl.mychess.TranspositionTable.Bound;
import org.michaelfl.mychess.WeightingFunction;

/**
 * One alpha-beta node's return value. The {@code bound} and
 * {@code bestMove} fields exist for the transposition table:
 * {@code bound} classifies {@code weight} as exact / lower / upper
 * (see {@link Bound}), and {@code bestMove} carries the move that
 * produced {@code weight} so a future re-visit of the same position
 * can use it as the first move tried (see
 * {@link MoveSorter#reset}'s {@code ttMove} parameter). Both are
 * read back via {@link TranspositionTable#put}.
 */
public record SearchNodeResult(GameResult result, int weight, Bound bound, int bestMove, boolean isTimeout) {

    public static final SearchNodeResult TIMEOUT = new SearchNodeResult(GameResult.ONGOING, 0, Bound.EXACT, 0, true);
    public static final SearchNodeResult INVALID = new SearchNodeResult(GameResult.ONGOING, WeightingFunction.ILLEGAL_WEIGHT_NEG, Bound.EXACT, 0, false);

    /**
     * Initial "no result yet" placeholder used as the starting value of
     * {@code bestResult} in {@code alphaBetaSearchMain}. Any real return
     * value (in {@code (ILLEGAL_WEIGHT_NEG, ILLEGAL_WEIGHT_POS]}) is
     * strictly greater, so the first valid move always replaces it.
     */
    public static final SearchNodeResult INITIAL = new SearchNodeResult(GameResult.ONGOING, WeightingFunction.MIN_ALPHA, Bound.EXACT, 0, false);

    public SearchNodeResult(GameResult result, int weight, Bound bound, int bestMove) {
        this(result, weight, bound, bestMove, false);
    }

    public boolean isIllegal() {
        return weight == WeightingFunction.ILLEGAL_WEIGHT_POS || weight == WeightingFunction.ILLEGAL_WEIGHT_NEG;
    }

    public static SearchNodeResult create(GameResult result, int weight, Bound bound, int bestMove) {
        return new SearchNodeResult(result, weight, bound, bestMove, false);
    }

    public static SearchNodeResult draw() {
        return new SearchNodeResult(GameResult.DRAW, 0, Bound.EXACT, 0, false);
    }

    /**
     * Sentinel result for "previous move left own king capturable". The
     * {@code ILLEGAL_WEIGHT_POS} weight is preserved unchanged through
     * the rest of the search — fail-soft does not clamp it.
     */
    public static SearchNodeResult illegal() {
        return new SearchNodeResult(GameResult.ONGOING, WeightingFunction.ILLEGAL_WEIGHT_POS, Bound.EXACT, 0, false);
    }

    public static SearchNodeResult checkmateSelf(int depth) {
        return new SearchNodeResult(GameResult.CHECKMATE, -WeightingFunction.checkmateInCenti(depth), Bound.EXACT, 0, false);
    }

    public static SearchNodeResult stalemate() {
        return new SearchNodeResult(GameResult.STALEMATE, 0, Bound.EXACT, 0, false);
    }

    public SearchNodeResult negate() {
        if (weight == 0) {
            return this;
        }
        return new SearchNodeResult(result, -weight, bound, bestMove, isTimeout);
    }
}
