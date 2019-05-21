package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.*;

@SuppressWarnings("Duplicates")
public final class MyChessEngine extends GenericEngine {

    private final static class MyChessContext extends Context {
        final float weightLimit;

        MyChessContext(float weightLimit) {
            this.weightLimit = weightLimit;
        }
    }

    public MyChessEngine(Game game) {
        super(game, MyChessEngine::rejectMyTurn,
                    MyChessEngine::rejectOppositeTurn,
                    MyChessEngine::rejectOnNextOppositeMove,
                    MyChessEngine::selectMove);
    }

    @Override
    protected Context createContext(GameStatus gameStatus, Board board, float currentWeight) {
        return new MyChessContext(currentWeight + (gameStatus.isWhiteTurn() ? 0.1f : -0.1f));
    }

    private static boolean rejectMyTurn(Context context) {
        return false;
    }

    private static boolean rejectOppositeTurn(Context context) {
        boolean result = context.depth > 2
                && Move.getCapturedPiece(context.gameStatus.getLastMove()) == 0
                && context.gameStatus.isBetterWeight(context.weight, ((MyChessContext) context).weightLimit);
        if (result) {
            byte movedPiece = context.workingBoard.getRawBoard()[Move.getToField(context.gameStatus.getLastMove())];
            if (context.weight < -7.0f)
                context.workingBoard.print();
            System.out.println("rejecting on top level move " + ChessUtil.moveToString(context.topLevelMove)
                    + ", move " + Board.toPrintSymbol(movedPiece)
                    + " " + ChessUtil.moveToString(context.gameStatus.getLastMove())
                    + ", weight: " + context.weight + ", limit: " + ((MyChessContext) context).weightLimit + ", depth: " + context.depth
                    + ", moves: " + context.moveStack);
        }
        return result;
    }

    private static boolean rejectOnNextOppositeMove(Context context) {
        boolean result = context.gameStatus.isBetterWeight(context.bestWeight, ((MyChessContext) context).weightLimit);
        if (result)
            System.out.println("rejecting on next move " + ChessUtil.moveToString(context.gameStatus.getLastMove())
                    + ", weight: " + context.weight + ", limit: " + ((MyChessContext) context).weightLimit + ", depth: " + context.depth);
        return result;
    }

    private static int selectMove(Context context, Moves moves, float[] weights) {
        final GameStatus gameStatus = context.gameStatus;
        final int countPossibleMoves = moves.count();
        final int[] plainMoves = moves.getMoves();
        int bestMove = -1;
        float bestWeight = context.gameStatus.isWhiteTurn() ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;

        for (int i = 0; i < countPossibleMoves; i++) {
            if (weights[i] != WeightingFunction.ILLEGAL_WEIGHT) {
                System.out.println("  " + ChessUtil.moveToString(plainMoves[i]) + " ==> weight " + weights[i]);
                if (gameStatus.isBetterWeight(weights[i], bestWeight)) {
                    bestMove = i;
                    bestWeight = weights[i];
                }
            }
        }

        return bestMove;
    }

}
