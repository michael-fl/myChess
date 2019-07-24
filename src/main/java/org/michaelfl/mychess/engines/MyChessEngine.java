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

        final long t1 = System.currentTimeMillis();
        MoveAndWeight move = findCheckmateMove(game, workingBoard);
        final long t2 = System.currentTimeMillis();
        System.out.println("1) Checkmate check took " + (t2 - t1) + "ms");

        if (move.move == 0) {
            final long t3 = System.currentTimeMillis();
            MoveAndWeight combinationMove = findCombinationMove(game, workingBoard);
            final long t4 = System.currentTimeMillis();
            System.out.println("2) Combination check took " + (t4 - t3) + "ms");

            final long t5 = System.currentTimeMillis();
            MoveAndWeight positionMove = findMoveByPositionWeight(game, workingBoard);
            final long t6 = System.currentTimeMillis();
            System.out.println("3) Move calculation took " + (t6 - t5) + "ms");

            move = getBestMove(gameStatus, combinationMove, positionMove);
        }

        if (move.move != 0)
            game.setWeight(move.weight); // Remember last calculated best position weight

        return move.move;
    }

    private MoveAndWeight getBestMove(GameStatus gameStatus, MoveAndWeight ... moves) {
        MoveAndWeight bestMove = MoveAndWeight.NO_MOVE;
        float bestWeight = gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

        for (MoveAndWeight move : moves) {
            if (move.move != 0 && gameStatus.isBetterWeight(move.weight, bestWeight)) {
                bestMove = move;
                bestWeight = move.weight;
            }
        }

        return bestMove;
    }

    private MoveAndWeight findMoveByPositionWeight(Game game, Board workingBoard) {
        final GameStatus gameStatus = game.getGameStatus();
        Moves moves = moveGenerator.calculateMoves(gameStatus, game.getBoard());
        countPossibleMoves = moves.count();
        countPositions = 0;
        maxReachedDepth = 0;

        if (moves.isIllegal() || moves.count() == 0)
            return MoveAndWeight.NO_MOVE; // No move possible

        final boolean isWhiteTurn = gameStatus.getTurn() == GameStatus.TURN_WHITE;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final float[] weights = new float[countMoves];

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            // TODO: Pass GameStatus as result parameter to avoid allocation of many objects
            GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            weights[i] = calculateWeightRecursive(1, nextGameStatus, workingBoard);
            workingBoard.revertMove(move);
        }

        int bestMove = -1;
        float bestWeight = isWhiteTurn ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

        for (int i = 0; i < countPossibleMoves; i++) {
            if (weights[i] != WeightingFunction.ILLEGAL_WEIGHT) {
                System.out.println("  " + ChessUtil.moveToString(plainMoves[i]) + " ==> weight " + weights[i]);
                if (gameStatus.isBetterWeight(weights[i], bestWeight)) {
                    bestMove = i;
                    bestWeight = weights[i];
                }
            }
        }

        System.out.println("#positions: " + countPositions + ", maxDepth: " + maxReachedDepth);

        if (bestMove == -1) {
            // No legal move possible
            return MoveAndWeight.NO_MOVE;
        }

        System.out.println("==> move: " + ChessUtil.moveToString(plainMoves[bestMove]) + ", weight: " + weights[bestMove]);
        return new MoveAndWeight(plainMoves[bestMove], weights[bestMove]);
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
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            // Make this move and calculate its weight; also check if it is a legal one
            final int move = plainMoves[i];
            GameStatus nextGameStatus = gameStatus.makeMove(move);
            workingBoard.makeMove(move);
            countPositions++;

            float weight = calculateWeightRecursive(depth + 1, nextGameStatus, workingBoard);
            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                if (gameStatus.isBetterWeight(weight, bestWeight)) {
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
