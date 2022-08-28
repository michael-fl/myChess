package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.PositionSearch;
import org.michaelfl.mychess.engines.PositionSearch.SearchNodeContext;

/**
 * @author Michael Fleischhauer
 */
@SuppressWarnings("DuplicatedCode")
public final class QuiescenceSearch {

    private final MoveGenerator moveGenerator;
    private final WeightingFunction weightingFunction;
    private final Statistics statistics;
    private final int maxQuiescenceDepth;

    public QuiescenceSearch(Game game, MoveGenerator moveGenerator, WeightingFunction weightingFunction, Statistics statistics, int maxQuiescenceDepth) {
        this.moveGenerator = moveGenerator;
        this.weightingFunction = weightingFunction;
        this.statistics = statistics;
        this.maxQuiescenceDepth = maxQuiescenceDepth;
    }

    public int quiescenceSearch(final Board workingBoard, final int capturedOnField, final int depth, final int weightFactor, final int alpha, final int beta, final int materialWeight, final int materialDelta) {
        final int maxDepth = depth + maxQuiescenceDepth;

        statistics.startQuiescenceSearch();
        int weight = quiescenceSearch(new SearchNodeContext(depth, maxDepth, null, weightFactor, alpha, beta, materialWeight, materialDelta, workingBoard, null));
        statistics.endQuiescenceSearch();

        return weight;
    }

    private int quiescenceSearch(final SearchNodeContext ctx) {
        final int depth = ctx.depth();
        final GameStatus gameStatus = ctx.workingBoard().getGameStatus();

        if (WeightingFunction.isIllegalWeight(ctx.alphaWeight()) || WeightingFunction.isIllegalWeight(ctx.betaWeight())) {
            // TODO remove
            throw new IllegalStateException("ILLEGAL_WEIGHT as alpha/beta; depth=" + depth + ", alphaWeight=" + ctx.alphaWeight() + ", betaWeight=" + ctx.betaWeight() + "\n" + ctx.workingBoard());
        }

        statistics.incrPositionCount();
        statistics.incrQuiescencePositionsCount();
        statistics.reachedDepth(depth);

        int standPat = calculatePositionWeight(ctx.workingBoard(), ctx.weightFactor(), ctx.materialWeight(), ctx.materialDelta());

        if (standPat >= ctx.betaWeight()) {
            return ctx.betaWeight();
        }
        if (depth == ctx.maxDepth()) {
            return standPat;
        }
        int bestWeight = Math.max(ctx.alphaWeight(), standPat);

        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard(), depth);
        if (moves.isIllegal()) {
            return WeightingFunction.ILLEGAL_WEIGHT_POS;
        }

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();

        int capturedOnField = Move.getToField(gameStatus.getLastMove());
        if (Move.getCapturedPiece(gameStatus.getLastMove()) == 0) {
            throw new IllegalStateException();
        }

        for (int i = 0; i < countMoves; i++) {
            // TODO: Follow only moves, which are captures. Unfortunately this increases computation time too much.
            //if (Move.getCapturedPiece(plainMoves[i]) != 0) {

            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                final int move = plainMoves[i];
                final int moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
                final int newMaterialWeight = ctx.materialWeight() + moveWeight;
                final int newMaterialDelta = ctx.materialDelta() + moveWeight;

                ctx.workingBoard().makeMove(move);
                int weight = -quiescenceSearch(new SearchNodeContext(depth + 1, ctx.maxDepth(), null, -ctx.weightFactor(), -ctx.betaWeight(), -bestWeight, -newMaterialWeight, -newMaterialDelta, ctx.workingBoard(), null));
                ctx.workingBoard().revertMove();

                // -ILLEGAL_WEIGHT is possible to be returned, but never +ILLEGAL_WEIGHT
                if (weight > WeightingFunction.ILLEGAL_WEIGHT_NEG) {
                    if (weight >= ctx.betaWeight()) {
                        return ctx.betaWeight(); // beta cutoff
                    }
                    if (weight > bestWeight) {
                        bestWeight = weight;
                    }
                }
            }
        }

        return bestWeight;
    }

    private int calculatePositionWeight(final Board workingBoard, final int weightFactor, final int materialWeight, final int materialDelta) {
        if (materialDelta > PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD || materialDelta < -PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD) {
            return materialWeight;
        }
        return weightingFunction.calculate(workingBoard) * weightFactor;
    }
}
