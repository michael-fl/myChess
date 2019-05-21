package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Predicate;

@SuppressWarnings("Duplicates")
public abstract class GenericEngine extends ChessEngine {

    private final static int MAX_DEPTH = 6;

    static class Context {
        final Moves moveStack = new Moves();
        int topLevelMove;
        int depth;
        GameStatus gameStatus;
        Board workingBoard;
        boolean isMyTurn;
        float weight;
        float bestWeight;
    }

    @FunctionalInterface
    public interface MoveSelector {
        int selectMove(Context context, Moves moves, float[] weights);
    }

    private WeightingFunction weightingFunction = new WeightingFunction();
    private final Predicate<Context> rejectMyTurn;
    private final Predicate<Context> rejectOppositeTurn;
    private final Predicate<Context> rejectOnNextOppositeMove;
    private final MoveSelector moveSelector;
    private int countPossibleMoves;
    private int countPositions;
    private int maxReachedDepth;

    GenericEngine(Game game,
                         Predicate<Context> rejectMyTurn,
                         Predicate<Context> rejectOppositeTurn,
                         Predicate<Context> rejectOnNextOppositeMove,
                         MoveSelector moveSelector) {
        super(game);

        this.rejectMyTurn = rejectMyTurn;
        this.rejectOppositeTurn = rejectOppositeTurn;
        this.rejectOnNextOppositeMove = rejectOnNextOppositeMove;
        this.moveSelector = moveSelector;
    }

    protected abstract Context createContext(GameStatus gameStatus, Board board, float currentWeight);

    @Override
    protected int calculateNextMove() {
        final GameStatus gameStatus = game.getGameStatus();
        final float currentWeight = game.getWeight() == null ? weightingFunction.calculate(gameStatus, game.getBoard()) : game.getWeight();
        final Moves moves = moveGenerator.calculateMoves(gameStatus, game.getBoard());
        countPossibleMoves = moves.count();
        countPositions = 0;
        maxReachedDepth = 0;

        if (moves.isIllegal() || moves.count() == 0)
            return 0; // No move possible

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        final float[] weights = new float[countMoves];
        final Board workingBoard = game.getBoard().copy();
        final Context context = createContext(gameStatus, workingBoard, currentWeight);

        context.workingBoard = workingBoard;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];

            context.topLevelMove = move;
            context.depth = 1;
            // TODO: Pass GameStatus as result parameter to avoid allocation of many objects
            context.gameStatus = gameStatus.makeMove(move);
            context.isMyTurn = false;
            context.weight = currentWeight;

            workingBoard.makeMove(move);
            context.moveStack.addMove(move);
            weights[i] = calculateWeightRecursive(context);
            context.moveStack.popMove();
            workingBoard.revertMove(move);
            // TODO MF: Remove this check
            if (!Arrays.equals(game.getBoard().getRawBoard(), workingBoard.getRawBoard())) {
                game.getBoard().print();
                workingBoard.print();
                throw new IllegalStateException("Working board not reset correctly to its original state");
            }
        }

        context.depth = 0;
        context.gameStatus = gameStatus;
        context.isMyTurn = true;
        context.weight = currentWeight;

        // Select best move
        int bestMove = moveSelector.selectMove(context, moves, weights);

        if (bestMove == -1) {
            // No legal move possible
            return 0;
        }

        game.setWeight(weights[bestMove]); // Remember last calculated best position weight
        System.out.println("#positions: " + countPositions + ", maxDepth: " + maxReachedDepth);
        System.out.println("==> move: " + ChessUtil.moveToString(plainMoves[bestMove]) + ", weight: " + weights[bestMove]);
        return plainMoves[bestMove];
    }

    @Override
    public int getCountPossibleMoves() {
        return countPossibleMoves;
    }

    @SuppressWarnings("Duplicates")
    private float calculateWeightRecursive(Context context) {
        final GameStatus gameStatus = context.gameStatus;
        final Board workingBoard = context.workingBoard;
        final int depth = context.depth;
        final boolean isMyTurn = context.isMyTurn;

        countPositions++;
        maxReachedDepth = Math.max(maxReachedDepth, depth);

        final float currentWeight = weightingFunction.calculate(gameStatus, workingBoard);
        if (currentWeight == WeightingFunction.ILLEGAL_WEIGHT)
            return currentWeight;

        context.weight = currentWeight;

        if (depth == MAX_DEPTH)
            return Move.getCapturedPiece(gameStatus.getLastMove()) == 0 ? currentWeight : followCapturedPiecesRecursive(depth, gameStatus, workingBoard);

        final Predicate<Context> rejectFunction = context.isMyTurn ? rejectMyTurn : rejectOppositeTurn;
        if (rejectFunction.test(context))
            return currentWeight;

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard);
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;

        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];

            context.isMyTurn = !isMyTurn;
            context.gameStatus = gameStatus;
            context.depth = depth + 1;
            context.weight = currentWeight;
            context.bestWeight = bestWeight;

            context.gameStatus = gameStatus.makeMove(move);
            Board tmpBoard = workingBoard.copy();
            workingBoard.makeMove(move);
            context.moveStack.addMove(move);
            final float weight = calculateWeightRecursive(context);
            context.moveStack.popMove();
            workingBoard.revertMove(move);
            // TODO MF: Remove this check
            if (!Arrays.equals(tmpBoard.getRawBoard(), workingBoard.getRawBoard())) {
                tmpBoard.print();
                workingBoard.print();
                throw new IllegalStateException("Working board not reset correctly to its original state for move " + ChessUtil.moveToString(move));
            }

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                if (gameStatus.isBetterWeight(weight, bestWeight)) {
                    bestWeight = weight;
                    bestMove = i;
                    context.bestWeight = bestWeight;

                    if (!context.isMyTurn && rejectOnNextOppositeMove.test(context))
                        break;
                }
            }
        }

        if (bestMove == -1) {
            // No legal move possible ==> Checkmate or stalemate
            if (Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator)) {
                // Checkmate
                return gameStatus.isWhiteTurn() ? WeightingFunction.CHECKMATE_WHITE : WeightingFunction.CHECKMATE_BLACK;
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
        float bestWeight = gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        int bestMove = -1;

        for (int i = 0; i < countMoves; i++) {
            // Follow only moves, which capture on that particular field
            if (capturedOnField == Move.getToField(plainMoves[i])) {
                int move = plainMoves[i];
                GameStatus nextGameStatus = gameStatus.makeMove(move);
                Board tmpBoard = workingBoard.copy();
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
                // TODO MF: Remove this check
                if (!Arrays.equals(tmpBoard.getRawBoard(), workingBoard.getRawBoard())) {
                    tmpBoard.print();
                    workingBoard.print();
                    throw new IllegalStateException("Working board not reset correctly to its original state for move " + ChessUtil.moveToString(move));
                }
            }
        }

        if (bestMove == -1)
            bestWeight = weightingFunction.calculate(gameStatus, workingBoard);

        return bestWeight;
    }

}
