package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

@SuppressWarnings("DuplicatedCode")
public final class CheckmateSearch {

    private final static int MAX_CHECKMATE_SEARCH_DEPTH = 10;
    private final static int NO_CHECKMATE = 0;
    private final static int ILLEGAL = -1;
    private final static int CHECKMATE_WEIGHT_LOW = 1000;
    private final static int CHECKMATE_WEIGHT_HIGH = 2000;

    private final static class CheckmateSearchContext {
        final Board workingBoard;
        int depth;
        int bestMove;
        int positionCount;

        CheckmateSearchContext(Board workingBoard) {
            this.workingBoard = workingBoard;
        }
    }

    private final Game game;
    private final MoveGenerator moveGenerator;
    private final boolean silent;

    private CheckmateSearch(ChessEngine engine, Game game) {
        this.game = game;
        this.moveGenerator = new MoveGenerator(new MoveSorterImpl());
        this.silent = engine.getConfig().isSilent();
    }

    public static MoveAndWeight findCheckmateMove(ChessEngine engine, Game game) {
        return new CheckmateSearch(engine, game).findCheckmateMove();
    }

    private MoveAndWeight findCheckmateMove() {
        final var workingBoard = game.getBoard().copy();
        final var gameStatus = game.getGameStatus();
        final int[] checkmateMove = new int[1];

        int weight = findCheckmate(gameStatus.getOppositeColor(), workingBoard, checkmateMove);
        if (weight >= CHECKMATE_WEIGHT_LOW) {
            log("==> opposite checkmate in " + (CHECKMATE_WEIGHT_HIGH - weight) + ": " + ChessUtil.moveToString(checkmateMove[0]) + ", weight: " + ChessUtil.weightToString(weight));
            return new MoveAndWeight(checkmateMove[0], weight, GameResult.CHECKMATE, new int[0]);
        }

        weight = findCheckmate(gameStatus.getTurn(), workingBoard, checkmateMove);
        if (weight >= CHECKMATE_WEIGHT_LOW) {
            log("==> I'm checkmate in " + (CHECKMATE_WEIGHT_HIGH - weight) + ": " + ChessUtil.moveToString(checkmateMove[0]) + ", weight: " + ChessUtil.weightToString(weight));
            return new MoveAndWeight(checkmateMove[0], weight, GameResult.CHECKMATE, new int[0]);
        }

        return MoveAndWeight.NO_MOVE;
    }

    public static int findCheckmate(ChessEngine engine, Game game, int forColor, int[] moveOut) {
        var workingBoard = game.getBoard().copy();

        return new CheckmateSearch(engine, game).findCheckmate(forColor, workingBoard, moveOut);
    }

    private int findCheckmate(int forColor, Board workingBoard, int[] moveOut) {
        CheckmateSearchContext context = new CheckmateSearchContext(workingBoard);
        int move = workingBoard.getGameStatus().getTurn() == forColor ?
                findCheckmateEscapeMove(context, Integer.MIN_VALUE, Integer.MAX_VALUE) :
                findCheckmateMove(context, Integer.MIN_VALUE, Integer.MAX_VALUE);
        moveOut[0] = context.bestMove;

        if (move != 0)
            log("#positions for checkmate check: " + context.positionCount);

        return move;
    }

    // min search
    private int findCheckmateEscapeMove(CheckmateSearchContext context, final int alphaWeight, final int betaWeight) {
        final Board workingBoard = context.workingBoard;
        final int depth = context.depth;

        if (alphaWeight == Integer.MAX_VALUE || betaWeight == Integer.MIN_VALUE) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }

        context.positionCount++;

        if (depth > MAX_CHECKMATE_SEARCH_DEPTH
                || !Game.testIsKingChecked(workingBoard, moveGenerator)) {
            return NO_CHECKMATE;
        }

        final Moves moves = moveGenerator.calculateMoves(workingBoard, depth);
        if (moves.isIllegal())
            return ILLEGAL;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        int bestMove = -1;
        int bestWeight = betaWeight; // Integer.MAX_VALUE
        boolean haveValidMove = false;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingBoard.makeMove(move);
            context.depth = depth + 1;
            int weight = findCheckmateMove(context, alphaWeight, bestWeight);
            if (weight == Integer.MAX_VALUE || weight == Integer.MIN_VALUE) {
                throw new IllegalStateException("depth=" + depth + ", weight=" + weight + "\n" + workingBoard.toString());
            }
            workingBoard.revertMove();

            if (weight != ILLEGAL) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight <= alphaWeight) {
                    return weight;
                }

                if (weight < bestWeight) {
                    bestWeight = weight;
                    bestMove = i;
                }
            }
        }

        if (haveValidMove) {
            if (bestMove != -1) {
                context.bestMove = plainMoves[bestMove];
            }
            return bestWeight;
        }

        // Checkmate
        return CHECKMATE_WEIGHT_HIGH - depth;
    }

    // max search
    private int findCheckmateMove(CheckmateSearchContext context, final int alphaWeight, final int betaWeight) {
        final Board workingBoard = context.workingBoard;
        final int depth = context.depth;

        if (alphaWeight == Integer.MAX_VALUE || betaWeight == Integer.MIN_VALUE) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }

        context.positionCount++;

        if (depth > MAX_CHECKMATE_SEARCH_DEPTH)
            return NO_CHECKMATE;

        final Moves moves = moveGenerator.calculateMoves(workingBoard, depth);
        if (moves.isIllegal())
            return ILLEGAL;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        int bestMove = -1;
        int bestWeight = alphaWeight; // Integer.MIN_VALUE
        boolean haveValidMove = false;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingBoard.makeMove(move);
            context.depth = depth + 1;
            int weight = findCheckmateEscapeMove(context, bestWeight, betaWeight);
            if (weight == Integer.MAX_VALUE || weight == Integer.MIN_VALUE) {
                throw new IllegalStateException("depth=" + depth + ", weight=" + weight + "\n" + workingBoard.toString());
            }
            workingBoard.revertMove();

            if (weight != ILLEGAL) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight >= betaWeight) {
                    return weight;
                }

                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestMove = i;
                }
            }
        }

        if (haveValidMove) {
            if (bestMove != -1) {
                context.bestMove = plainMoves[bestMove];
            }
            return bestWeight;
        }

        return NO_CHECKMATE;
    }

    private void log(String s) {
        if (!silent) {
            System.out.println(s);
        }
    }
}
