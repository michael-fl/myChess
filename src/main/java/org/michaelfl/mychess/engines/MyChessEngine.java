package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

@SuppressWarnings("Duplicates")
public final class MyChessEngine extends ChessEngine {

    private final static int MAX_DEPTH = 4;

    private WeightingFunction weightingFunction = new WeightingFunction();
    private int countPossibleMoves;
    private int countPositions;
    private int maxReachedDepth;

    public MyChessEngine(Game game) {
        super(game);
    }

    @SuppressWarnings("Duplicates")
    @Override
    protected int calculateNextMove() {
        final GameStatus gameStatus = game.getGameStatus();
        final Board workingBoard = game.getBoard().copy();
        final int weightCorrectionFactor = gameStatus.isWhiteTurn() ? 1 : -1;

        final long t1 = System.currentTimeMillis();
        MoveAndWeight move = findCheckmateMove(game, workingBoard);
        final long t2 = System.currentTimeMillis();
        System.out.println("1) Checkmate check took " + (t2 - t1) + "ms");

//        if (move.move == 0) {
            final long t3 = System.currentTimeMillis();
            move = findCombinationMove(game, workingBoard);
            final long t4 = System.currentTimeMillis();
            System.out.println("2) Combination check took " + (t4 - t3) + "ms");

//        }

        if (move.move != 0)
            game.setWeight(move.weight * weightCorrectionFactor); // Remember last calculated best position weight

        return move.move;
    }

    @Override
    public int getCountPossibleMoves() {
        return countPossibleMoves;
    }

    private float calculateWeightRecursive(int depth, GameStatus gameStatus, Board workingBoard) {
        maxReachedDepth = Math.max(maxReachedDepth, depth);

        if (depth == MAX_DEPTH) {
            final int lastMove = gameStatus.getLastMove();
            if (Move.getCapturedPiece(lastMove) == 0)
                return weightingFunction.calculate(gameStatus, workingBoard);

            return followCapturedPiecesRecursive(depth, gameStatus, workingBoard);
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final boolean isWhiteTurn = gameStatus.getTurn() == GameStatus.TURN_WHITE;
        float bestWeight = isWhiteTurn ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int bestMove = 0;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];

            // Make this move and calculate its weight; also check if it is a legal one
            GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            countPositions++;

            float weight = calculateWeightRecursive(depth + 1, nextGameStatus, workingBoard);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT && gameStatus.isBetterWeight(weight, bestWeight)) {
                bestWeight = weight;
                bestMove = move;
            }

            workingBoard.revertMove(move);
        }

        if (bestMove == 0) {
            // No legal move possible ==> Checkmate or stalemate
            if (Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator)) {
                // Checkmate
                return (100 - depth) * (isWhiteTurn ? WeightingFunction.CHECKMATE_WHITE : WeightingFunction.CHECKMATE_BLACK);
            }
            // Stalemate
            return 0; // draw
        }

        return bestWeight;
    }

    private float followCapturedPiecesRecursive(int depth, GameStatus gameStatus, Board workingBoard) {
        final int capturedOnField = Move.getToField(gameStatus.getLastMove());
        maxReachedDepth = Math.max(maxReachedDepth, depth);

        Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final boolean isWhiteTurn = gameStatus.getTurn() == GameStatus.TURN_WHITE;
        float bestWeight = isWhiteTurn ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture pieces
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                // Make this move and calculate its weight; also check if it is a legal one
                int move = plainMoves[i];
                GameStatus nextGameStatus = gameStatus.makeMove(move);
                workingBoard.makeMove(move);
                countPositions++;

                float weight = followCapturedPiecesRecursive(depth + 1, nextGameStatus, workingBoard);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                    if (gameStatus.isBetterWeight(weight, bestWeight)) {
                        bestWeight = weight;
                        bestMove = i;
                    }
                }

                workingBoard.revertMove(move);
            }
        }

        if (bestMove == -1)
            bestWeight = weightingFunction.calculate(gameStatus, workingBoard);

        return bestWeight;
    }

}
