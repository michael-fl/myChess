package org.michaelfl.mychess;

import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class EngineTestBase {
    protected static final Class<? extends ChessEngine> ENGINE = MyChessEngine.class;

    @SuppressWarnings("SameParameterValue")
    protected static EngineConfig engineConfig() {
        return engineConfig(TestSupport.createTestTT());
    }

    protected static EngineConfig engineConfig(TranspositionTable tt) {
        return new EngineConfig.Builder()
                .maxDepth(8)
                .setTranspositionTable(tt)
                .build();
    }

    protected static void testPosition(String gameNotation, String expectedMove, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        testPosition(gameNotation, Set.of(expectedMove), expectedMinWeight, expectedMaxWeight, config);
    }

    protected static void testPosition(String gameNotation, Set<String> expectedMoves, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        testPosition(gameNotation, expectedMoves, null, expectedMinWeight, expectedMaxWeight, config);
    }

    protected static void testPosition(String gameNotation, Set<String> expectedMoves, String[] expectedPathOpt, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        try {
            GameImporter importer = GameImporter.importerFor(gameNotation);
            var game = importer.importGame(config);

            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);

            var maxExpectedPathDepth = config.getEngineWhiteConfig().getMaxDepth() - 1;
            if (WeightingFunction.isCheckmateWeight(expectedMinWeight)) {
                maxExpectedPathDepth = Math.min(maxExpectedPathDepth, WeightingFunction.checkmateWeightToPlies(expectedMinWeight));
            }

            // PV length can be shorter than maxDepth-1 if a transposition-table
            // cache hit truncated the principal variation (see
            // PositionSearch.SearchNodeContext.writeTTCachedPv). Accept any
            // non-empty PV that doesn't exceed the maximum search depth; below
            // we walk only the actual length to validate expectedPathOpt.
            int actualPathLength = pathLength(move.path());
            assertTrue(actualPathLength >= 1,
                    "Path is empty: " + ChessUtil.pathToString(move.path()));
            assertTrue(actualPathLength <= maxExpectedPathDepth,
                    "Path longer than maxDepth-1=" + maxExpectedPathDepth + ": "
                            + ChessUtil.pathToString(move.path()));

            if (expectedPathOpt != null) {
                assertTrue(expectedPathOpt.length <= maxExpectedPathDepth,
                        "Test setup error: expected path longer than maxDepth-1=" + maxExpectedPathDepth);
            }

            assertMoveAndWeight(game, move, expectedMoves, expectedMinWeight, expectedMaxWeight);

            int pathDepthToCheck = Math.min(actualPathLength,
                    expectedPathOpt != null ? expectedPathOpt.length : actualPathLength);
            for (int i = 0; i < pathDepthToCheck; i++) {
                if (expectedPathOpt != null && notContainsMove(game, Set.of(expectedPathOpt[i]), move.path()[i])) {
                    game.print();
                    fail("Unexpected move at path depth " + i + ": " + game.getBoard().moveToShortNotation(new Move(move.path()[i])) + ", expected " + expectedPathOpt[i] + ", expected path=" + Arrays.toString(expectedPathOpt) + ", actual path=" + ChessUtil.pathToString(move.path()));
                }
                try {
                    game.makeMove(new Move(move.path()[i]));
                } catch (Exception e) {
                    System.out.println("Failed to execute move " + ChessUtil.moveToString(move.path()[i]));
                    game.getBoard().print();
                    throw e;
                }
            }

            if (WeightingFunction.isCheckmateWeight(expectedMinWeight)) {
                assertEquals(GameResult.CHECKMATE, game.getResult(), "Game result should be checkmate");
            }
            assertEquals(move.result(), game.getResult(), "Unexpected game result");

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            config.getEngineWhiteConfig().getTranspositionTable().close();
        }
    }

    /**
     * Runs the engine on a position given directly as a FEN (no move history)
     * and asserts the chosen move and its weight, delegating both checks to
     * {@link #assertMoveAndWeight}. Uses {@link Fen#importChess960FEN(String)}
     * so Chess960 castling rights parse correctly (a standard FEN is handled as
     * a special case). The {@code config}'s transposition table is closed on
     * exit, mirroring {@link #testPosition}.
     */
    protected static void testPositionFromFen(String fen, String expectedMove, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        testPositionFromFen(fen, Set.of(expectedMove), expectedMinWeight, expectedMaxWeight, config);
    }

    protected static void testPositionFromFen(String fen, Set<String> expectedMoves, float expectedMinWeight, float expectedMaxWeight, GameConfig config) {
        try {
            var game = new Game(config, Fen.importChess960FEN(fen));

            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);

            assertMoveAndWeight(game, move, expectedMoves, expectedMinWeight, expectedMaxWeight);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            config.getEngineWhiteConfig().getTranspositionTable().close();
        }
    }

    /**
     * Asserts the engine's chosen move is one of {@code expectedMoves} (long or
     * short notation) and that its weight lies within
     * {@code [expectedMinWeight, expectedMaxWeight]}. On failure the board and
     * FEN are printed to aid diagnosis. Shared by {@link #testPosition} and
     * {@link #testPositionFromFen}.
     */
    private static void assertMoveAndWeight(Game game, MoveAndWeight move, Set<String> expectedMoves, float expectedMinWeight, float expectedMaxWeight) {
        if (notContainsMove(game, expectedMoves, move.move())) {
            game.print();
            System.out.println(game.exportFEN());
            fail("Wrong move: " + ChessUtil.moveToString(move.move()) + ". Expected one of " + expectedMoves);
        }

        var weight = move.weight();
        if (weight < expectedMinWeight) {
            game.print();
            fail("Wrong weight: " + ChessUtil.weightToString(weight) + ". Expected minimum of " + ChessUtil.weightToString(expectedMinWeight));
        }
        if (weight > expectedMaxWeight) {
            game.print();
            fail("Wrong weight: " + ChessUtil.weightToString(weight) + ". Expected maximum of " + ChessUtil.weightToString(expectedMaxWeight));
        }
    }

    private static boolean notContainsMove(Game game, Collection<String> moveStrings, int move) {
        return !moveStrings.contains(ChessUtil.moveToString(move)) && !moveStrings.contains(game.getBoard().moveToShortNotation(new Move(move)).toString());
    }

    private static int pathLength(int[] path) {
        int len = 0;
        //noinspection StatementWithEmptyBody
        for (int i = 0; i < path.length && path[i] != 0; i++, len++) {
            // empty - only calculating len
        }
        return len;
    }

}
