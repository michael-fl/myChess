package org.michaelfl.mychess;

import org.michaelfl.mychess.TranspositionTable.Bound;
import org.michaelfl.mychess.engines.PositionSearch;
import org.michaelfl.mychess.engines.SearchNodeContext;

import static org.michaelfl.mychess.Assert.__assert;

/**
 * Tactical extension at search leaves: evaluates the stand-pat position and
 * recursively follows available captures, capped by
 * {@link EngineConfig#getMaxQuiescenceDepth()}. Avoids the horizon effect on
 * hanging captures while keeping the normal alpha-beta leaf evaluation quiet.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("DuplicatedCode")
public final class QuiescenceSearch {

    private record QuiescenceSearchResult(int weight, Bound bound, int bestMove) {}

    private final MoveGenerator moveGenerator;
    private final WeightingFunction weightingFunction;
    private final Statistics statistics;
    private final int maxQuiescenceDepth;
    private final long timeout;
    private final TranspositionTable tt;
    private boolean isTimeout;

    public QuiescenceSearch(MoveGenerator moveGenerator, WeightingFunction weightingFunction, Statistics statistics, int maxQuiescenceDepth, long timeout) {
        this.moveGenerator = moveGenerator;
        this.weightingFunction = weightingFunction;
        this.statistics = statistics;
        this.maxQuiescenceDepth = maxQuiescenceDepth;
        this.tt = TranspositionTable.getDefaultQSearchInstance();
        this.timeout = timeout;
    }

    public int quiescenceSearch(final Board workingBoard, final int depth, final int weightFactor, final int alphaWeight, final int betaWeight, final int materialWeight, final int materialDelta) {
        final int maxDepth = depth + maxQuiescenceDepth;

        statistics.startQuiescenceSearch();
        var result = quiescenceSearchPre(
                new SearchNodeContext(depth, maxDepth, null, weightFactor, materialWeight, materialDelta, workingBoard, null),
                                      alphaWeight, betaWeight);
        statistics.endQuiescenceSearch();

        return result.weight();
    }

    private QuiescenceSearchResult quiescenceSearchPre(final SearchNodeContext ctx, int alphaWeight, int betaWeight) {
        final int depth = ctx.depth();

        final int alphaFinal = alphaWeight;
        final int betaFinal = betaWeight;
        __assert(() -> !(WeightingFunction.isIllegalWeight(alphaFinal) || WeightingFunction.isIllegalWeight(betaFinal)),
                () -> "ILLEGAL_WEIGHT as alpha/beta; depth=" + depth + ", alphaWeight=" + alphaFinal + ", betaWeight=" + betaFinal + "\n" + ctx.workingBoard());

        statistics.incrPositionCount();
        statistics.incrQuiescencePositionsCount();
        statistics.reachedDepth(depth);

        if (isTimeout()) {
            return new QuiescenceSearchResult(0, Bound.EXACT, 0);
        }

        // Transposition table lookup
        final var ttEntryView = tt.get(ctx.workingBoard().getGameStatus().getPositionHash());
        if (ttEntryView != null && ttEntryView.getDepth() >= ctx.remainingDepth()) {
            final int score = WeightingFunction.scoreFromTT(ttEntryView.getScore(), ctx.depth());

            switch (ttEntryView.getBound()) {
                case EXACT -> {
                    return new QuiescenceSearchResult(score, Bound.EXACT, ttEntryView.getBestMove());
                }
                case LOWER -> alphaWeight = Math.max(alphaWeight, score);
                case UPPER -> betaWeight = Math.min(betaWeight, score);
            }

            if (alphaWeight >= betaWeight) {
                return new QuiescenceSearchResult(score, ttEntryView.getBound(), ttEntryView.getBestMove());
            }
        }

        final int ttMove = ttEntryView != null ? ttEntryView.getBestMove() : 0;

        var result = quiescenceSearch(ctx, alphaWeight, betaWeight, ttMove);
        int weight = result.weight();

        if (!isTimeout() && weight != WeightingFunction.ILLEGAL_WEIGHT_POS && weight != WeightingFunction.ILLEGAL_WEIGHT_NEG) {
            // Store result in transposition table
            int score = WeightingFunction.scoreToTT(weight, ctx.depth());
            tt.put(ctx.workingBoard().getGameStatus().getPositionHash(), ctx.remainingDepth(), score, result.bound(), result.bestMove());
        }

        return result;
    }

    private QuiescenceSearchResult quiescenceSearch(final SearchNodeContext ctx, int alphaWeight, int betaWeight, int ttMove) {
        final int depth = ctx.depth();

        int standPat = calculatePositionWeight(ctx.workingBoard(), ctx.weightFactor(), ctx.materialWeight(), ctx.materialDelta());

        // Fail-soft stand-pat cutoff: return the actual stand-pat value, not
        // the beta bound. Caller (and a future TT) get a tighter lower bound.
        //
        // Self-check probe: an illegal previous move can push materialDelta
        // past EVALUATE_MATERIAL_ONLY_THRESHOLD so calculatePositionWeight
        // returns the raw materialWeight (skipping the eval's
        // containsIllegalMove sentinel). Without this probe we would hand
        // the parent a legitimate-looking score for an illegal move. Limited
        // to the two early-return paths below; the capture loop is already
        // protected by moves.isIllegal() after calculateMoves.
        if (standPat >= betaWeight || depth == ctx.maxDepth()) {
            if (ctx.workingBoard().canCaptureOpposingKing()) {
                return new QuiescenceSearchResult(WeightingFunction.ILLEGAL_WEIGHT_POS, Bound.EXACT, 0);
            }
            var bound = standPat >= betaWeight ? Bound.LOWER : Bound.EXACT;
            return new QuiescenceSearchResult(standPat, bound, 0);
        }

        // Fail-soft: bestWeight tracks the true best score (may be below
        // ctx.alphaWeight). The alpha-beta cutoff threshold for children is
        // computed as max(alpha, bestWeight) at the call site.
        int bestWeight = standPat;

        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard(), depth, 0, ttMove);
        if (moves.isIllegal()) {
            return new QuiescenceSearchResult(WeightingFunction.ILLEGAL_WEIGHT_POS, Bound.EXACT, 0);
        }

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        int bestMove = 0;

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which are captures
            if (Move.getCapturedPiece(plainMoves[i]) != 0) {
                final int move = plainMoves[i];
                final int moveWeight = WeightingFunction.getMaterialWeightOfMove(move);
                final int newMaterialWeight = ctx.materialWeight() + moveWeight;
                final int newMaterialDelta = ctx.materialDelta() + moveWeight;

                final int alphaLocal = Math.max(alphaWeight, bestWeight);

                ctx.workingBoard().makeMove(move);
                var result = quiescenceSearchPre(
                        new SearchNodeContext(depth + 1, ctx.maxDepth(), null, -ctx.weightFactor(), -newMaterialWeight, -newMaterialDelta, ctx.workingBoard(), null),
                        -betaWeight, -alphaLocal);
                int weight = -result.weight();

                ctx.workingBoard().revertMove();

                if (isTimeout()) {
                    return new QuiescenceSearchResult(0, Bound.EXACT, 0);
                }

                // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
                if (weight > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
                    // Fail-soft beta cutoff: return actual weight.
                    if (weight >= betaWeight) {
                        bestMove = move;
                        return new QuiescenceSearchResult(weight, Bound.LOWER, bestMove);
                    }
                    if (weight > bestWeight) {
                        bestWeight = weight;
                        bestMove = move;
                    }
                }
            }
        }

        Bound bound = bestWeight > alphaWeight ? Bound.EXACT : Bound.UPPER;
        return new QuiescenceSearchResult(bestWeight, bound, bestMove);
    }

    private int calculatePositionWeight(final Board workingBoard, final int weightFactor, final int materialWeight, final int materialDelta) {
        if (materialDelta > PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD || materialDelta < -PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD) {
            return materialWeight;
        }
        return weightingFunction.calculate(workingBoard) * weightFactor;
    }

    public boolean isTimeout() {
        if (!isTimeout) {
            isTimeout = statistics.getPositionsCount() % 10000 == 0 && System.currentTimeMillis() >= timeout;
        }
        return isTimeout;
    }

}
