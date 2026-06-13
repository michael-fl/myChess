package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.PositionSearch;
import org.michaelfl.mychess.engines.PositionSearch.SearchNodeContext;

import static org.michaelfl.mychess.Assert.__assert;

/**
 * Tactical extension at search leaves: keeps capturing as long as the last
 * move was a capture, capped by {@link EngineConfig#getMaxQuiescenceDepth()}.
 * Avoids the horizon effect on hanging captures.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("DuplicatedCode")
public final class QuiescenceSearch {

    private final MoveGenerator moveGenerator;
    private final WeightingFunction weightingFunction;
    private final Statistics statistics;
    private final int maxQuiescenceDepth;

    public QuiescenceSearch(MoveGenerator moveGenerator, WeightingFunction weightingFunction, Statistics statistics, int maxQuiescenceDepth) {
        this.moveGenerator = moveGenerator;
        this.weightingFunction = weightingFunction;
        this.statistics = statistics;
        this.maxQuiescenceDepth = maxQuiescenceDepth;
    }

    public int quiescenceSearch(final Board workingBoard, final int capturedOnField, final int depth, final int weightFactor, final int alpha, final int beta) {
        final int maxDepth = depth + maxQuiescenceDepth;

        statistics.startQuiescenceSearch();
        int weight = quiescenceSearch(new SearchNodeContext(depth, maxDepth, null, weightFactor, alpha, beta, workingBoard, null));
        statistics.endQuiescenceSearch();

        return weight;
    }

    private int quiescenceSearch(final SearchNodeContext ctx) {
        final int depth = ctx.depth();
        final GameStatus gameStatus = ctx.workingBoard().getGameStatus();

        __assert(() -> !(WeightingFunction.isIllegalWeight(ctx.alphaWeight()) || WeightingFunction.isIllegalWeight(ctx.betaWeight())),
                () -> "ILLEGAL_WEIGHT as alpha/beta; depth=" + depth + ", alphaWeight=" + ctx.alphaWeight() + ", betaWeight=" + ctx.betaWeight() + "\n" + ctx.workingBoard());

        statistics.incrPositionCount();
        statistics.incrQuiescencePositionsCount();
        statistics.reachedDepth(depth);

        int standPat = calculatePositionWeight(ctx.workingBoard(), ctx.weightFactor());

        // Fail-soft stand-pat cutoff: return the actual stand-pat value, not
        // the beta bound. Caller (and a future TT) get a tighter lower bound.
        //
        // Self-check probe: belt-and-braces guard for the two early-return
        // paths. The WeightingFunction already returns ILLEGAL_WEIGHT_POS/NEG
        // when it detects that the side to move can capture the opposing
        // king (containsIllegalMove sentinel), so this probe is normally
        // redundant — but cheap and explicit. The capture loop is already
        // protected by moves.isIllegal() after calculateMoves.
        if (standPat >= ctx.betaWeight() || depth == ctx.maxDepth()) {
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

        int capturedOnField = Move.getToField(gameStatus.getLastMove());
        __assert(() -> Move.getCapturedPiece(gameStatus.getLastMove()) != 0);

        for (int i = 0; i < countMoves; i++) {
            // TODO: Follow only moves, which are captures. Unfortunately this increases computation time too much.

            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                final int move = plainMoves[i];

                final int alphaLocal = Math.max(ctx.alphaWeight(), bestWeight);

                ctx.workingBoard().makeMove(move);
                int weight = -quiescenceSearch(new SearchNodeContext(depth + 1, ctx.maxDepth(), null, -ctx.weightFactor(), -ctx.betaWeight(), -alphaLocal, ctx.workingBoard(), null));
                ctx.workingBoard().revertMove();

                // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
                if (weight > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
                    // Fail-soft beta cutoff: return actual weight.
                    if (weight >= ctx.betaWeight()) {
                        return weight;
                    }
                    if (weight > bestWeight) {
                        bestWeight = weight;
                    }
                }
            }
        }

        return bestWeight;
    }

    private int calculatePositionWeight(final Board workingBoard, final int weightFactor) {
        return weightingFunction.calculate(workingBoard) * weightFactor;
    }
}
