package org.michaelfl.mychess;

@SuppressWarnings("Duplicates")
final class FixDepthEngine extends ChessEngine {

    private final static int DEPTH = 5;

    private WeightingFunction weightingFunction = new WeightingFunction();
    private int countPossibleMoves = -1;
    private int countPositions;

    FixDepthEngine(Game game) {
        super(game);
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected int calculateNextMove() {
        Moves moves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());
        countPossibleMoves = moves.count();
        countPositions = 0;

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

            float weight = calculateWeightRecursive(1, gameStatus, workingBoard);
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
            System.out.println("#positions: " + countMoves + " ==> best weight: 0");
            return 0;
        }

        System.out.println("#positions: " + countPositions + " ==> move: " + ChessUtil.moveToString(plainMoves[bestMove]) + ", weight: " + bestWeight);
        return plainMoves[bestMove];
    }

    private float calculateWeightRecursive(int depth, GameStatus gameStatus, Board workingBoard) {
        countPositions++;

        if (depth == DEPTH) {
            return weightingFunction.calculate(gameStatus, workingBoard);
        }

        Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final boolean isWhiteTurn = gameStatus.getTurn() == GameStatus.TURN_WHITE;
        float bestWeight = isWhiteTurn ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            // Make this move and calculate its weight; also check if it is a legal one
            int move = plainMoves[i];
            // TODO: Pass GameStatus as result parameter to avoid allocation of many objects
            GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);

            float weight = calculateWeightRecursive(depth + 1, nextGameStatus, workingBoard);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                if ((isWhiteTurn && weight > bestWeight) || (!isWhiteTurn && weight < bestWeight)) {
                    bestWeight = weight;
                    bestMove = i;
                }
            }

            workingBoard.revertMove(move);
        }

        if (bestMove == -1) {
            // No legal move possible ==> Checkmate or stalemate
            if (Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator)) {
                // Checkmate
                return isWhiteTurn ? WeightingFunction.CHECKMATE_WHITE : WeightingFunction.CHECKMATE_BLACK;
            }
            // Stalemate
            return 0; // draw
        }

        return bestWeight;
    }

    @Override
    public int getCountPossibleMoves() {
        return countPossibleMoves;
    }
}
