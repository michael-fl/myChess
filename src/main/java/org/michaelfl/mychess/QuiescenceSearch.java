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

        statistics.startQuiescenceSearch();
        float weight = quiescenceMaxSearch(gameStatus, workingBoard, depth, maxDepth, materialWeight, materialDelta, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
        statistics.endQuiescenceSearch();

        return weight;
    }

    public float quiescenceMinSearch(final GameStatus gameStatus, final Board workingBoard, final int capturedOnField, final int depth, final float materialWeight, final float materialDelta) {
        final int maxDepth = depth + maxQuiescenceDepth;

        statistics.startQuiescenceSearch();
        float weight = quiescenceMinSearch(gameStatus, workingBoard, depth, maxDepth, materialWeight, materialDelta, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
        statistics.endQuiescenceSearch();

        return weight;
    }

    private float quiescenceMaxSearch(final GameStatus gameStatus, final Board workingBoard, final int depth, final int maxDepth, final float materialWeight, final float materialDelta, final float alpha, final float beta) {
        statistics.incrPositionCount();
        statistics.incrQuiescencePositionsCount();
        statistics.reachedDepth(depth);

        float standPat = calculatePositionWeight(gameStatus, workingBoard, materialWeight, materialDelta);

        if (standPat >= beta) {
            return beta;
        }
        if (depth == maxDepth) {
            return standPat;
        }
        float bestWeight = Math.max(alpha, standPat);

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth, 0);
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
                final float newMaterialWeight = materialWeight + moveWeight;
                final float newMaterialDelta = materialDelta + moveWeight;

                GameStatus nextGameStatus = gameStatus.makeMove(workingBoard, move);
                if (depth == 0)
                    System.out.println();
                float weight = quiescenceMinSearch(nextGameStatus, workingBoard, depth + 1, maxDepth, newMaterialWeight, newMaterialDelta, bestWeight, beta);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                    if (weight >= beta) {
                        return beta; // beta cutoff
                    }
                    if (weight > bestWeight) {
                        bestWeight = weight;
                    }
                }
            }
        }

        return bestWeight;
    }

    private float quiescenceMinSearch(final GameStatus gameStatus, final Board workingBoard, final int depth, final int maxDepth, final float materialWeight, final float materialDelta, final float alpha, final float beta) {
        statistics.incrPositionCount();
        statistics.incrQuiescencePositionsCount();
        statistics.reachedDepth(depth);

        float standPat = calculatePositionWeight(gameStatus, workingBoard, materialWeight, materialDelta);

        if (standPat <= alpha) {
            return alpha;
        }
        if (depth == maxDepth) {
            return standPat;
        }
        float bestWeight = Math.min(beta, standPat);

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth, 0);
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
                final float newMaterialWeight = materialWeight - moveWeight;
                final float newMaterialDelta = materialDelta - moveWeight;

                GameStatus nextGameStatus = gameStatus.makeMove(workingBoard, move);
                float weight = quiescenceMaxSearch(nextGameStatus, workingBoard, depth + 1, maxDepth, newMaterialWeight, newMaterialDelta, alpha, bestWeight);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                    if (weight <= alpha) {
                        return alpha; // alpha cutoff
                    }
                    if (weight < bestWeight) {
                        bestWeight = weight;
                    }
                }
            }
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
