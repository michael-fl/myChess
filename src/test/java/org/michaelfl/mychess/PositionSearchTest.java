package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.PositionSearch;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct tests for {@link PositionSearch}. Exercised through the public
 * {@code calculateNextMove(...)} static entry point: each test builds a game
 * via {@link GameImporter}, configures a {@link MyChessEngine}, then runs the
 * async API end-to-end.
 *
 * @author Michael Fleischhauer
 */
class PositionSearchTest {

    private static EngineConfig deepConfig(int maxDepth) {
        return new EngineConfig.Builder()
                .maxDepth(maxDepth)
                .silent(true)
                .setTranspositionTable(TestSupport.createTestTT())
                .build();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void findsMateInOne() throws Exception {
        // White to move: Qxf7# (Scholar's mate completion).
        var setup = """
                1. e4 e5
                2. Qh5 Nc6
                3. Bc4 Nf6??
                """;
        var game = GameImporter.importerFor(setup).importGame(
                new GameConfig(MyChessEngine.class, deepConfig(4)));

        var move = game.getEngine().nextMoveAsync().getResult(20, TimeUnit.SECONDS);

        var san = game.getBoard().moveToShortNotation(new Move(move.move())).toString();
        assertEquals("Qxf7#", san, "Search must find the mate-in-1 move Qxf7#");
        assertTrue(WeightingFunction.isCheckmateWeight(move.weight()),
                "Returned weight must be in the checkmate range, was " + move.weight());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void principalVariationLengthAtMostMaxDepth() throws Exception {
        // Use a quiet opening so the engine does not bail with a mate-shortened path.
        var game = GameImporter.importerFor("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6").importGame(
                new GameConfig(MyChessEngine.class, deepConfig(4)));

        var move = game.getEngine().nextMoveAsync().getResult(30, TimeUnit.SECONDS);

        int len = 0;
        for (int i = 0; i < move.path().length && move.path()[i] != 0; i++) {
            len++;
        }
        assertTrue(len <= 4, "Principal variation must not exceed maxDepth, got " + len);
        assertTrue(len > 0, "Principal variation must be non-empty");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void terminatesOnAlreadyOverGame() throws Exception {
        // Set up a checkmate position so the search recognizes the game is over.
        var setup = """
                1. e4 e5
                2. Qh5 Nc6
                3. Bc4 Nf6
                4. Qxf7#
                """;
        var game = GameImporter.importerFor(setup).importGame(
                new GameConfig(MyChessEngine.class, deepConfig(8)));

        var move = game.getEngine().nextMoveAsync().getResult(10, TimeUnit.SECONDS);

        assertEquals(0, move.move(), "No move can be played when the game is already mated");
        assertEquals(Game.GameResult.CHECKMATE, move.result(),
                "Result must be CHECKMATE when the game is already over");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getPossibleMovesAtStartReturnsTwentyMoves() {
        var game = new Game(new GameConfig(MyChessEngine.class, deepConfig(1)));
        Moves moves = PositionSearch.getPossibleMoves(game.getEngine(), game);
        assertEquals(20, moves.count(),
                "From the start position there are exactly 20 legal moves for white");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void searchHonorsMillisPerMoveTimeout() throws Exception {
        // Configure a 1-second budget with a very deep maxDepth — the timeout must trigger.
        var config = new EngineConfig.Builder()
                .maxDepth(20)
                .millisPerMove(1_000)
                .silent(true)
                .setTranspositionTable(TestSupport.createTestTT())
                .build();
        var game = GameImporter.importerFor("1. e4 e5 2. Nf3 Nc6").importGame(
                new GameConfig(MyChessEngine.class, config));

        long t0 = System.currentTimeMillis();
        var move = game.getEngine().nextMoveAsync().getResult(15, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - t0;

        assertNotNull(move, "Search must return a move from the previous completed depth");
        assertNotEquals(0, move.move(), "Returned move must be a real move");
        assertTrue(elapsed < 10_000,
                "Search must abort within a few seconds when millisPerMove=1000, actual: " + elapsed + "ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void cancellationFlagIsObservableAfterSubmission() {
        // Drive cancel() via the engine's real async submission (which sets the
        // future on the task), then verify the flag is observable.
        var config = new EngineConfig.Builder()
                .maxDepth(20)
                .millisPerMove(60_000)
                .silent(true)
                .setTranspositionTable(TestSupport.createTestTT())
                .build();
        var game = GameImporter.importerFor("1. e4 e5 2. Nf3 Nc6").importGame(
                new GameConfig(MyChessEngine.class, config));

        var task = game.getEngine().nextMoveAsync();
        task.cancel();
        assertTrue(task.isCanceled(), "After cancel(), isCanceled() must report true");
    }
}
