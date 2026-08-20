package org.michaelfl.mychess;

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

    private final MoveGenerator moveGenerator;
    private final WeightingFunction weightingFunction;
    private final Statistics statistics;
    private final int maxQuiescenceDepth;
    private final long timeout;
    private boolean isTimeout;
    private boolean timeoutDisabled;
    private boolean materialWeightShortcutDisabled;

    public QuiescenceSearch(MoveGenerator moveGenerator, WeightingFunction weightingFunction, Statistics statistics, int maxQuiescenceDepth, long timeout) {
        this.moveGenerator = moveGenerator;
        this.weightingFunction = weightingFunction;
        this.statistics = statistics;
        this.maxQuiescenceDepth = maxQuiescenceDepth;
        this.timeout = timeout;
    }

    public int quiescenceSearch(final Board workingBoard, final int depth, final int weightFactor, final int alphaWeight, final int betaWeight, final int materialWeight, final int materialDelta) {
        final int maxDepth = depth + maxQuiescenceDepth;

        statistics.startQuiescenceSearch();
        int weight = quiescenceSearch(
                new SearchNodeContext(depth, maxDepth, null, weightFactor, materialWeight, materialDelta, workingBoard, null, maxDepth + 1),
                alphaWeight, betaWeight);
        statistics.endQuiescenceSearch();

        return weight;
    }

    public int quiescenceSearchNoMaterialWeightShortcut(final Board workingBoard, final int depth, final int weightFactor, final int alphaWeight, final int betaWeight) {
        materialWeightShortcutDisabled = true;
        timeoutDisabled = true;
        int weight = quiescenceSearch(workingBoard, depth, weightFactor, alphaWeight, betaWeight, 0, 0);
        materialWeightShortcutDisabled = false;
        timeoutDisabled = false;

        return weight;
    }

    private int quiescenceSearch(final SearchNodeContext ctx, final int alphaWeight, final int betaWeight) {
        final int depth = ctx.depth();

        __assert(() -> !(WeightingFunction.isIllegalWeight(alphaWeight) || WeightingFunction.isIllegalWeight(betaWeight)),
                () -> "ILLEGAL_WEIGHT as alpha/beta; depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + ctx.workingBoard());

        statistics.incrQuiescencePositionsCount();
        statistics.reachedDepth(depth);

        if (isTimeout()) {
            return 0;
        }

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
                return WeightingFunction.ILLEGAL_WEIGHT_POS;
            }
            return standPat;
        }

        // Fail-soft: bestWeight tracks the true best score (may be below
        // ctx.alphaWeight). The alpha-beta cutoff threshold for children is
        // computed as max(alpha, bestWeight) at the call site.
        int bestWeight = standPat;

        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard(), depth);
        if (moves.isIllegal()) {
            return WeightingFunction.ILLEGAL_WEIGHT_POS;
        }

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            final int moveWeight = WeightingFunction.getMaterialWeightOfMove(move);
            final int newMaterialWeight = ctx.materialWeight() + moveWeight;
            final int newMaterialDelta = ctx.materialDelta() + moveWeight;

            final int alphaLocal = Math.max(alphaWeight, bestWeight);

            ctx.workingBoard().makeMove(move);

            // Count this capture continuation as a visited position. The
            // quiescence entry itself is the search's horizon leaf, already
            // counted in alphaBetaSearchPre, so it is deliberately not counted
            // again here — otherwise every leaf would be counted twice.
            // (incrQuiescencePositionsCount above still counts every quiescence
            // node, entry included, for the separate quiescence-size stat.)
            statistics.incrPositionCount();

            int weight = -quiescenceSearch(
                    new SearchNodeContext(depth + 1, ctx.maxDepth(), null, -ctx.weightFactor(), -newMaterialWeight, -newMaterialDelta, ctx.workingBoard(), null, ctx.pvMaxLength()),
                    -betaWeight, -alphaLocal);
            ctx.workingBoard().revertMove();

            if (isTimeout()) {
                return 0;
            }

            // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
            if (weight > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
                // Fail-soft beta cutoff: return actual weight.
                if (weight >= betaWeight) {
                    return weight;
                }
                if (weight > bestWeight) {
                    bestWeight = weight;
                }
            }
        }

        return bestWeight;
    }

    private int calculatePositionWeight(final Board workingBoard, final int weightFactor, final int materialWeight, final int materialDelta) {
        if (!materialWeightShortcutDisabled && (materialDelta > PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD || materialDelta < -PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD)) {
            return materialWeight;
        }
        return weightingFunction.calculate(workingBoard) * weightFactor;
    }

    public boolean isTimeout() {
        if (timeoutDisabled) {
            return false;
        }
        if (!isTimeout) {
            isTimeout = statistics.getPositionsCount() % 10000 == 0 && System.currentTimeMillis() >= timeout;
        }
        return isTimeout;
    }

}
