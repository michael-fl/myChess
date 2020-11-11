package org.michaelfl.mychess;

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
        this.weightFactor = game.getGameStatus().isWhiteTurn() ? 1 : -1;
    }

    public float quiescenceMaxSearch(final GameStatus gameStatus, final Board workingBoard, final int capturedOnField, final int depth, final float materialWeight, final float materialDelta) {
        final int maxDepth = depth + maxQuiescenceDepth;
        return quiescenceMaxSearch(gameStatus, workingBoard, capturedOnField, depth, maxDepth, materialWeight, materialDelta);
    }

    public float quiescenceMinSearch(final GameStatus gameStatus, final Board workingBoard, final int capturedOnField, final int depth, final float materialWeight, final float materialDelta) {
        final int maxDepth = depth + maxQuiescenceDepth;
        return quiescenceMinSearch(gameStatus, workingBoard, capturedOnField, depth, maxDepth, materialWeight, materialDelta);
    }

    private float quiescenceMaxSearch(final GameStatus gameStatus, final Board workingBoard, final int capturedOnField, final int depth, final int maxDepth, final float materialWeight, final float materialDelta) {
        statistics.incrPositionCount();
        statistics.reachedDepth(depth);

        if (depth == maxDepth) {
            return calculatePositionWeight(gameStatus, workingBoard, materialWeight, materialDelta);
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth, 0);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = calculatePositionWeight(gameStatus, workingBoard, materialWeight, materialDelta);

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                final int move = plainMoves[i];

                final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
                final float newMaterialWeight = materialWeight + moveWeight;
                final float newMaterialDelta = materialDelta + moveWeight;

                GameStatus nextGameStatus = gameStatus.makeMove(workingBoard, move);
                float weight = quiescenceMinSearch(nextGameStatus, workingBoard, capturedOnField, depth + 1, maxDepth, newMaterialWeight, newMaterialDelta);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT && weight > bestWeight) {
                    bestWeight = weight;
                }
            }
        }

        if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + "\n" + workingBoard.toString());
        }

        return bestWeight;
    }

    private float quiescenceMinSearch(final GameStatus gameStatus, final Board workingBoard, final int capturedOnField, final int depth, final int maxDepth, final float materialWeight, final float materialDelta) {
        statistics.incrPositionCount();
        statistics.reachedDepth(depth);

        if (depth == maxDepth) {
            return calculatePositionWeight(gameStatus, workingBoard, materialWeight, materialDelta);
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth, 0);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = calculatePositionWeight(gameStatus, workingBoard, materialWeight, materialDelta);

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture on the same field, until no further capture is possible on that field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                final int move = plainMoves[i];

                final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
                final float newMaterialWeight = materialWeight - moveWeight;
                final float newMaterialDelta = materialDelta - moveWeight;

                GameStatus nextGameStatus = gameStatus.makeMove(workingBoard, move);
                float weight = quiescenceMaxSearch(nextGameStatus, workingBoard, capturedOnField, depth + 1, maxDepth, newMaterialWeight, newMaterialDelta);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT && weight < bestWeight) {
                    bestWeight = weight;
                }
            }
        }

        if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + "\n" + workingBoard.toString());
        }

        return bestWeight;
    }

    private float calculatePositionWeight(final GameStatus gameStatus, final Board workingBoard, final float materialWeight, final float materialDelta) {
        if (materialDelta > 2.0f || materialDelta < -2.0f) {
            return materialWeight;
        }
        float weight = weightingFunction.calculate(gameStatus, workingBoard);
        return weight != WeightingFunction.ILLEGAL_WEIGHT ? weight * weightFactor : WeightingFunction.ILLEGAL_WEIGHT;
    }
}
