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
import static org.junit.jupiter.api.Assertions.fail;

public class EngineTestBase {
    protected static final Class<? extends ChessEngine> ENGINE = MyChessEngine.class;

    @SuppressWarnings("SameParameterValue")
    protected static EngineConfig engineConfig() {
        return new EngineConfig.Builder()
                .maxDepth(8)
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

            var expectedPathDepth = config.getEngineWhiteConfig().getMaxDepth() - 1;
            if (WeightingFunction.isCheckmateWeight(expectedMinWeight)) {
                expectedPathDepth = Math.min(expectedPathDepth, WeightingFunction.checkmateWeightToPlies(expectedMinWeight));
            }
            assertEquals(expectedPathDepth, pathLength(move.path()), "Unexpected path length: " + ChessUtil.pathToString(move.path()));
            if (expectedPathOpt != null) {
                assertEquals(expectedPathDepth, expectedPathOpt.length, "Test setup error: Wrong length of expected path");
            }

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

            for (int i = 0; i < expectedPathDepth; i++) {
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
        }
    }

    private static boolean notContainsMove(Game game, Collection<String> moveStrings, int move) {
        return !moveStrings.contains(ChessUtil.moveToString(move)) && !moveStrings.contains(game.getBoard().moveToShortNotation(new Move(move)).toString());
    }

    private static Object pathLength(int[] path) {
        int len = 0;
        //noinspection StatementWithEmptyBody
        for (int i = 0; i < path.length && path[i] != 0; i++, len++) {
            // empty - only calculating len
        }
        return len;
    }

}
