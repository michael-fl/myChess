package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.ChessUtil;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.MoveGenerator;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.MovesCounter;
import org.michaelfl.mychess.WeightingFunction;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.concurrent.CancellationException;

@SuppressWarnings("DuplicatedCode")
final class PositionSearch {

    private final NextMoveTask task;
    private final Game game;
    private final EngineConfig engineConfig;
    private final MovesCounter killerMoves = new MovesCounter(2);
    private final MoveGenerator moveGenerator;
    private final WeightingFunction weightingFunction = new WeightingFunction();
    private final int weightFactor;

    private long positionsCount;
    private long prunedMovesCount;
    private int maximumReachedDepth = 0;
    private boolean silent;

    private PositionSearch(ChessEngine engine, NextMoveTask task, Game game) {
        this.task = task;
        this.game = game;
        this.moveGenerator = new MoveGenerator(new MoveSorterImpl(killerMoves));
        this.engineConfig = engine.getConfig();
        this.weightFactor = game.getGameStatus().isWhiteTurn() ? 1 : -1;
    }

    public static MoveAndWeight calculateNextMove(ChessEngine engine, NextMoveTask task, Game game) {
        return new PositionSearch(engine, task, game).calculateNextMove();
    }

    public static Moves getPossibleMoves(ChessEngine engine, Game game) {
        return new PositionSearch(engine, new NextMoveTask(), game).getPossibleMoves();
    }

    private Moves getPossibleMoves() {
        return moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard(), 0, 0);
    }

    private MoveAndWeight calculateNextMove() {
        MoveAndWeight bestMove = null;
        if (engineConfig.getMaxDepth() > 6) {
            silent = true;
            bestMove = calculateNextMove(6, null);
            MoveAndWeight m2 = bestMove.weightFactor(weightFactor);
            System.out.println("depth: 6, move: " + ChessUtil.moveToString(m2.move) + ", weight: " + ChessUtil.weightToString(m2.weight) + " [" + ChessUtil.pathToString(m2.path) + "]");
        }

        silent = false;
        bestMove = calculateNextMove(engineConfig.getMaxDepth(), bestMove);
        MoveAndWeight m2 = bestMove.weightFactor(weightFactor);
        System.out.println("depth: " + engineConfig.getMaxDepth() + ", move: " + ChessUtil.moveToString(m2.move) + ", weight: " + ChessUtil.weightToString(m2.weight) + " [" + ChessUtil.pathToString(m2.path) + "]");

        System.out.println("#positions: " + positionsCount + ", #pruned: " + prunedMovesCount);

        return bestMove;
    }

    @SuppressWarnings("Duplicates")
    private MoveAndWeight calculateNextMove(int maxDepth, MoveAndWeight bestKnownPath) {
        final Board workingBoard = game.getBoard().copy();
        final GameStatus gameStatus = game.getGameStatus();
        final int[] bestPath = new int[50];
        final int[] workingPath = new int[bestPath.length];

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, 0, getMoveAtDepth(bestKnownPath, 0));
        if (moves.isIllegal() || moves.count() == 0)
            return MoveAndWeight.NO_MOVE; // No move possible

        final float materialWeight = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = Float.NEGATIVE_INFINITY;
        final float betaWeight = Float.POSITIVE_INFINITY;
        int bestMove = 0;

        positionsCount++;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            //System.out.println("Working on move " + ChessUtil.moveToString(move));

            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, 1);
            final float newMaterialDelta = WeightingFunction.getMaterialWeightOfMove(move, 1);
            final float newMaterialWeight = materialWeight + moveWeight;

            workingPath[0] = move;
            GameStatus nextGameStatus = gameStatus.makeMove(workingBoard, move);
            float weight = minSearch(1, maxDepth, bestKnownPath, bestWeight, betaWeight, newMaterialWeight, newMaterialDelta, nextGameStatus, workingBoard, workingPath);
            bestKnownPath = null;
            workingBoard.revertMove(move);
            //System.out.println("--> weight " + ChessUtil.weightToString(weight));
            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                //System.out.println("  " + ChessUtil.moveToString(move) + " ==> " + ChessUtil.weightToString(factor * weight) + " (" + move + ") [" + ChessUtil.pathToString(workingPath) + "]");
                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestMove = move;
                    System.arraycopy(workingPath, 0, bestPath, 0, bestPath.length);
                }
            }

            // Find and store current killer moves
            killerMoves.sample();
            if (!silent) {
                System.out.println((i + 1) + "/" + countMoves + ": " + ChessUtil.moveToString(bestMove) + ", weight=" + ChessUtil.weightToString(bestWeight, weightFactor));
            }
        }

        if (bestMove != 0)
            return new MoveAndWeight(bestMove, bestWeight, bestPath);

        return MoveAndWeight.NO_MOVE;
    }

    private int getMoveAtDepth(MoveAndWeight m, int depth) {
        if (m != null && m.path.length > depth) {
            return m.path[depth];
        }

        return 0;
    }

    @SuppressWarnings("Duplicates")
    private float maxSearch(final int depth, final int maxDepth, MoveAndWeight bestKnownPath, final float alphaWeight, final float betaWeight, final float materialWeight, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut) {
        positionsCount++;

        if (alphaWeight == Float.POSITIVE_INFINITY || betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }
        if (gameStatus.getHalfMoveClock() >= 100) {
            bestPathOut[depth] = 0;
            return 0; // draw
        }

        final int[] workingPath = new int[bestPathOut.length];

        if (depth == maxDepth) {
            final int lastMove = gameStatus.getLastMove();

            if (Move.getCapturedPiece(lastMove) == 0) {
                bestPathOut[depth] = 0;
                return calculatePositionWeight(gameStatus, workingBoard, materialWeight, materialDelta);
            }

            float weight = quiescenceMaxSearch(Move.getToField(lastMove), depth, maxDepth + engineConfig.getMaxQuiescenceDepth(), materialWeight, materialDelta, gameStatus, workingBoard, workingPath);
            System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
            return weight;
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth, getMoveAtDepth(bestKnownPath, depth));
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = alphaWeight; // Float.NEGATIVE_INFINITY
        boolean haveValidMove = false;

        if (task.isCanceled()) {
            throw new CancellationException();
        }

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = materialWeight + moveWeight;
            final float newMaterialDelta = materialDelta + moveWeight;

            final GameStatus nextGameStatus = gameStatus.makeMove(workingBoard, move);
            final float weight = minSearch(depth + 1, maxDepth, bestKnownPath, bestWeight, betaWeight, newMaterialWeight, newMaterialDelta, nextGameStatus, workingBoard, workingPath);
            bestKnownPath = null;
            workingBoard.revertMove(move);

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight >= betaWeight) {
                    prunedMovesCount += countMoves - i - 1;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return weight;
                }

                if (weight > bestWeight) {
                    bestWeight = weight;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                }
            }
        }

        if (haveValidMove) {
            if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
            }
            return bestWeight;
        }

        // No legal move possible ==> Checkmate or stalemate
        bestPathOut[depth] = 0;
        if (Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator)) {
            // Computer checkmate
            return -(WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth);
        }

        // Stalemate
        return 0; // draw
    }

    @SuppressWarnings("Duplicates")
    private float minSearch(final int depth, final int maxDepth, MoveAndWeight bestKnownPath, final float alphaWeight, final float betaWeight, final float materialWeight, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut) {
        positionsCount++;

        if (alphaWeight == Float.POSITIVE_INFINITY || betaWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
        }
        if (gameStatus.getHalfMoveClock() >= 100) {
            bestPathOut[depth] = 0;
            return 0; // draw
        }

        final int[] workingPath = new int[bestPathOut.length];

        if (depth == maxDepth) {
            final int lastMove = gameStatus.getLastMove();

            if (Move.getCapturedPiece(lastMove) == 0) {
                bestPathOut[depth] = 0;
                return calculatePositionWeight(gameStatus, workingBoard, materialWeight, materialDelta);
            }

            float weight = quiescenceMinSearch(Move.getToField(lastMove), depth, maxDepth + engineConfig.getMaxQuiescenceDepth(), materialWeight, materialDelta, gameStatus, workingBoard, workingPath);
            System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
            return weight;
        }

        final Moves moves = moveGenerator.calculateMoves(gameStatus, workingBoard, depth, getMoveAtDepth(bestKnownPath, depth));
        if (moves.isIllegal())
            return WeightingFunction.ILLEGAL_WEIGHT;
        final int[] plainMoves = moves.getMoves();
        final int countMoves = moves.count();
        float bestWeight = betaWeight; // Float.POSITIVE_INFINITY
        boolean haveValidMove = false;

        for (int i = 0; i < countMoves; i++) {
            final int move = plainMoves[i];
            workingPath[depth] = move;
            final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
            final float newMaterialWeight = materialWeight - moveWeight;
            final float newMaterialDelta = materialDelta - moveWeight;

            final GameStatus nextGameStatus = gameStatus.makeMove(workingBoard, move);
            final float weight = maxSearch(depth + 1, maxDepth, bestKnownPath, alphaWeight, bestWeight, newMaterialWeight, newMaterialDelta, nextGameStatus, workingBoard, workingPath);
            bestKnownPath = null;
            workingBoard.revertMove(move);

            if (weight != WeightingFunction.ILLEGAL_WEIGHT) {
                haveValidMove = true;

                // Alpha-Beta search pruning
                if (weight <= alphaWeight) {
                    prunedMovesCount += countMoves - i - 1;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                    if (Move.getCapturedPiece(move) == 0) {
                        killerMoves.addMove(move, depth);
                    }
                    return weight;
                }

                if (weight < bestWeight) {
                    bestWeight = weight;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                }
            }
        }

        if (haveValidMove) {
            if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
                throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + ", alphaWeight=" + alphaWeight + ", betaWeight=" + betaWeight + "\n" + workingBoard.toString());
            }
            return bestWeight;
        }

        // No legal move possible ==> Checkmate or stalemate
        bestPathOut[depth] = 0;
        if (Game.checkIsKingUnderChess(gameStatus, workingBoard, moveGenerator)) {
            // Opposite checkmate
            return WeightingFunction.CHECKMATE_WEIGHT_HIGH - depth;
        }

        // Stalemate
        return 0; // draw
    }

    private float quiescenceMaxSearch(final int capturedOnField, final int depth, final int maxDepth, final float materialWeight, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut) {
        positionsCount++;

        final int[] workingPath = new int[bestPathOut.length];
        bestPathOut[depth] = 0;
        if (depth > maximumReachedDepth)
            maximumReachedDepth = depth;

        if (depth == maxDepth) {
            bestPathOut[depth] = 0;
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
                workingPath[depth] = move;

                final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
                final float newMaterialWeight = materialWeight + moveWeight;
                final float newMaterialDelta = materialDelta + moveWeight;

                GameStatus nextGameStatus = gameStatus.makeMove(workingBoard, move);
                float weight = quiescenceMinSearch(capturedOnField, depth + 1, maxDepth, newMaterialWeight, newMaterialDelta, nextGameStatus, workingBoard, workingPath);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT && weight > bestWeight) {
                    bestWeight = weight;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
                }
            }
        }

        if (bestWeight == Float.POSITIVE_INFINITY || bestWeight == Float.NEGATIVE_INFINITY) {
            throw new IllegalStateException("bestWeight=" + bestWeight + ", depth=" + depth + "\n" + workingBoard.toString());
        }

        return bestWeight;
    }

    private float quiescenceMinSearch(final int capturedOnField, final int depth, final int maxDepth, final float materialWeight, final float materialDelta, final GameStatus gameStatus, final Board workingBoard, final int[] bestPathOut) {
        positionsCount++;

        final int[] workingPath = new int[bestPathOut.length];
        bestPathOut[depth] = 0;

        if (depth == maxDepth) {
            bestPathOut[depth] = 0;
            return calculatePositionWeight(gameStatus, workingBoard, materialWeight, materialDelta);
        }

        if (depth > maximumReachedDepth)
            maximumReachedDepth = depth;

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
                workingPath[depth] = move;

                final float moveWeight = WeightingFunction.getMaterialWeightOfMove(move, depth);
                final float newMaterialWeight = materialWeight - moveWeight;
                final float newMaterialDelta = materialDelta - moveWeight;

                GameStatus nextGameStatus = gameStatus.makeMove(workingBoard, move);
                float weight = quiescenceMaxSearch(capturedOnField, depth + 1, maxDepth, newMaterialWeight, newMaterialDelta, nextGameStatus, workingBoard, workingPath);
                workingBoard.revertMove(move);
                if (weight != WeightingFunction.ILLEGAL_WEIGHT && weight < bestWeight) {
                    bestWeight = weight;
                    System.arraycopy(workingPath, depth, bestPathOut, depth, bestPathOut.length - depth);
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
