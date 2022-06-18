package org.michaelfl.mychess;

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
    private final int weightFactor;

    public QuiescenceSearch(Game game, MoveGenerator moveGenerator, WeightingFunction weightingFunction, Statistics statistics, int maxQuiescenceDepth) {
        this.moveGenerator = moveGenerator;
        this.weightingFunction = weightingFunction;
        this.statistics = statistics;
        this.maxQuiescenceDepth = maxQuiescenceDepth;
        this.weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
    }

    public float quiescenceMaxSearch(final Board workingBoard, final int capturedOnField, final int depth, final float materialWeight, final float materialDelta) {
        final int maxDepth = depth + maxQuiescenceDepth;

        statistics.startQuiescenceSearch();
        float weight = quiescenceMaxSearch(new SearchNodeContext(depth, maxDepth, null, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialWeight, materialDelta, workingBoard));
        statistics.endQuiescenceSearch();

        return weight;
    }

    public float quiescenceMinSearch(final Board workingBoard, final int capturedOnField, final int depth, final float materialWeight, final float materialDelta) {
        final int maxDepth = depth + maxQuiescenceDepth;

        statistics.startQuiescenceSearch();
        float weight = quiescenceMinSearch(new SearchNodeContext(depth, maxDepth, null, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, materialWeight, materialDelta, workingBoard));
        statistics.endQuiescenceSearch();

        return weight;
    }

    private float quiescenceMaxSearch(final SearchNodeContext ctx) {
        final int depth = ctx.depth;
        final GameStatus gameStatus = ctx.workingBoard.getGameStatus();

        statistics.incrPositionCount();
        statistics.incrQuiescencePositionsCount();
        statistics.reachedDepth(depth);

        float standPat = calculatePositionWeight(ctx.workingBoard, ctx.materialWeight, ctx.materialDelta);

        if (standPat >= ctx.betaWeight) {
            return ctx.betaWeight;
        }
        if (depth == ctx.maxDepth) {
            return standPat;
        }
        float bestWeight = Math.max(ctx.alphaWeight, standPat);

        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard, depth);
        if (moves.isIllegal()) {
            return WeightingFunction.ILLEGAL_WEIGHT;
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
                final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
                final float newMaterialWeight = ctx.materialWeight + moveWeight;
                final float newMaterialDelta = ctx.materialDelta + moveWeight;

                ctx.workingBoard.makeMove(move);
                if (depth == 0)
                    System.out.println();
                float weight = quiescenceMinSearch(new SearchNodeContext(depth + 1, ctx.maxDepth, null, bestWeight, ctx.betaWeight,  newMaterialWeight, newMaterialDelta, ctx.workingBoard));
                ctx.workingBoard.revertMove();
                if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                    if (weight >= ctx.betaWeight) {
                        return ctx.betaWeight; // beta cutoff
                    }
                    if (weight > bestWeight) {
                        bestWeight = weight;
                    }
                }
            }
        }

        return bestWeight;
    }

    private float quiescenceMinSearch(final SearchNodeContext ctx) {
        final int depth = ctx.depth;
        final GameStatus gameStatus = ctx.workingBoard.getGameStatus();

        statistics.incrPositionCount();
        statistics.incrQuiescencePositionsCount();
        statistics.reachedDepth(depth);

        float standPat = calculatePositionWeight(ctx.workingBoard, ctx.materialWeight, ctx.materialDelta);

        if (standPat <= ctx.alphaWeight) {
            return ctx.alphaWeight;
        }
        if (depth == ctx.maxDepth) {
            return standPat;
        }
        float bestWeight = Math.min(ctx.betaWeight, standPat);

        final Moves moves = moveGenerator.calculateMoves(ctx.workingBoard, depth);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();

        int capturedOnField = Move.getToField(gameStatus.getLastMove());
        if (Move.getCapturedPiece(gameStatus.getLastMove()) == 0) {
            throw new IllegalStateException();
        }

        for (int i = 0; i < countMoves; i++) {
            // OPT: Follow only moves, which are captures
            // if (Move.getCapturedPiece(plainMoves[i]) != 0) {

            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                final int move = plainMoves[i];
                final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
                final float newMaterialWeight = ctx.materialWeight - moveWeight;
                final float newMaterialDelta = ctx.materialDelta - moveWeight;

                ctx.workingBoard.makeMove(move);
                float weight = quiescenceMaxSearch(new SearchNodeContext(depth + 1, ctx.maxDepth, null, ctx.alphaWeight, bestWeight, newMaterialWeight, newMaterialDelta, ctx.workingBoard));
                ctx.workingBoard.revertMove();
                if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                    if (weight <= ctx.alphaWeight) {
                        return ctx.alphaWeight; // alpha cutoff
                    }
                    if (weight < bestWeight) {
                        bestWeight = weight;
                    }
                }
            }
        }

        return bestWeight;
    }

    private float calculatePositionWeight(final Board workingBoard, final float materialWeight, final float materialDelta) {
        if (materialDelta > 2.0f || materialDelta < -2.0f) {
            return materialWeight;
        }
        float weight = weightingFunction.calculate(workingBoard);
        return weight != WeightingFunction.ILLEGAL_WEIGHT ? weight * weightFactor : WeightingFunction.ILLEGAL_WEIGHT;
    }
}
