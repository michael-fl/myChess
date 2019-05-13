package org.michaelfl.mychess;

final class PositionEngine extends ChessEngine {

    private WeightingFunction weightingFunction = new WeightingFunction();
    private int countPossibleMoves = -1;

    PositionEngine(Game game) {
        super(game);
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected int calculateNextMove() {
        Moves moves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());
        countPossibleMoves = moves.count();

        if (moves.isIllegal() || moves.count() == 0)
            return 0; // No move possible

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final Board workingBoard = game.getBoard().copy();
        final boolean isWhiteTurn = game.getTurn() == GameStatus.TURN_WHITE;
        float bestWeight = isWhiteTurn ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            // Make this move and calculate its weight; also check if it is a legal one
            int move = plainMoves[i];
            // TODO: Pass GameStatus as result parameter to avoid allocation of many objects
            GameStatus gameStatus = game.getGameStatus().makeMove(move);
            workingBoard.makeMove(move);

            float weight = weightingFunction.calculate(gameStatus, workingBoard);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                System.out.println(ChessUtil.moveToString(move) + " ==> weight " + weight);
                if ((isWhiteTurn && weight > bestWeight) || (!isWhiteTurn && weight < bestWeight)) {
                    bestWeight = weight;
                    bestMove = i;
                }
            }

            workingBoard.revertMove(move);
        }

        if (bestMove == -1) {
            // No legal move possible
            return 0;
        }

        return plainMoves[bestMove];
    }

    @Override
    public int getCountPossibleMoves() {
        return countPossibleMoves;
    }
}
