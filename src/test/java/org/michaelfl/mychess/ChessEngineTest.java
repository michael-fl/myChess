package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.openingdb.OpeningDB;

import java.nio.file.Path;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class ChessEngineTest {

    private static EngineConfig defaultConfig() {
        return new EngineConfig.Builder()
                .maxDepth(2)
                .silent(true)
                .setTranspositionTable(TestSupport.createTestTT())
                .build();
    }

    private static MoveGenerator newGen() {
        return new MoveGenerator(MoveSorter.defaultImplementation());
    }

    private static int packMoveFor(Game game, String notation) {
        var board = game.getBoard();
        var resolved = board.resolveMoveDescription(
                MoveDescription.fromString(notation, game.getTurn()), newGen());
        return board.moveDescriptionToMove(resolved).move();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void preSearchShortcut_gameAlreadyOverReturnsCheckmateResult() throws Exception {
        // Fool's mate played to completion: engine asked on a finished game.
        var pgn = """
                1. f3 e6 2. g4 Qh4
                """;
        var game = GameImporter.importerFor(pgn).importGame(
                new GameConfig(MyChessEngine.class, defaultConfig()));

        var move = game.getEngine().nextMoveAsync().getResult(10, TimeUnit.SECONDS);

        assertEquals(Game.GameResult.CHECKMATE, move.result(),
                "Pre-search shortcut must return CHECKMATE on a finished game");
        assertEquals(0, move.move(), "No move is played when the game is mated");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void preSearchShortcut_fiftyMovesRuleReturnsDraw() throws Exception {
        var pgn = """
                1. e4 e5 2. d3 d6 3. Be2 Nf6 4. Nf3 Qd7 5. Nc3 Kd8 6. O-O Rg8 7. Rb1 Rh8 8. Be3 Qe8 9. Rc1
                Nc6 10. Ne1 Be6 11. Qd2 Kc8 12. Ra1 Kb8 13. Rd1 Ng8 14. Ra1 Nh6 15. Nf3 Nf5 16. Nh4 Nfe7
                17. Nf3 Qc8 18. Rfb1 Nd4 19. Nd1 Nec6 20. Ne1 Be7 21. Kh1 Rd8 22. Kg1 Re8 23. Kf1 Rf8 24.
                Kg1 Rg8 25. Kh1 Qf8 26. Bf4 Bh4 27. Bf3 Bc4 28. Qe3 Ne6 29. Nc3 Ne7 30. Bg4 Ng6 31. Nf3
                Nh8 32. Ng1 Nd8 33. Qc1 Be6 34. Bd1 Bc8 35. Bd2 Be7 36. Be1 Qe8 37. Nce2 Bf8 38. Ng3 Qd7
                39. Nf1 Qe8 40. Ne3 Qe6 41. Nc4 Qe8 42. Na5 Qe6 43. Nb3 Qe8 44. Nc5 Qe7 45. Ne6 Qe8 46.
                Ng5 Qe7 47. N5h3 Qe6 48. Nf4 Qe8 49. Nfe2 Qe6 50. Nc3 Qe8 51. Nd5 Nc6 52. Nf3
                """;
        var game = GameImporter.importerFor(pgn).importGame(Game.standardConfig());

        game.makeMove(MoveDescription.fromString("c8-d7", game.getTurn()));

        var move = game.getEngine().nextMoveAsync().getResult(10, TimeUnit.SECONDS);
        assertEquals(Game.GameResult.DRAW, move.result(),
                "After 50-move rule fires, pre-search shortcut must return DRAW");
        assertEquals(0, move.move(), "No move is returned on the 50-move-rule shortcut");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void preSearchShortcut_threefoldRepetitionReturnsDraw() throws Exception {
        var pgn = """
                1. g3 e6 2. a3 Qh4 3. gxh4 a6 4. Nf3 Nf6 5. Ng1 Ng8 6. Nf3 Nf6 7. Ng1
                """;
        var game = GameImporter.importerFor(pgn).importGame(Game.standardConfig());
        game.makeMove(MoveDescription.fromString("f6-g8", game.getTurn()));

        var move = game.getEngine().nextMoveAsync().getResult(10, TimeUnit.SECONDS);
        assertEquals(Game.GameResult.DRAW, move.result(),
                "After threefold repetition, pre-search shortcut must return DRAW");
    }

    // ---- Opening-book filter ----

    /** Build a DBValue blob with one move whose stats clear all three filter thresholds. */
    private static byte[] singleMoveBlob(int packedMove, int total, int win, int loss) {
        byte[] buf = new byte[20];
        writeInt(buf, 0, 1);          // position count
        writeInt(buf, 4, packedMove); // move
        writeInt(buf, 8, total);      // total
        writeInt(buf, 12, win);       // win
        writeInt(buf, 16, loss);      // loss
        return buf;
    }

    private static void writeInt(byte[] buf, int offset, int value) {
        buf[offset]     = BitOps.getByte0(value);
        buf[offset + 1] = BitOps.getByte1(value);
        buf[offset + 2] = BitOps.getByte2(value);
        buf[offset + 3] = BitOps.getByte3(value);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void openingBookHit_playsTheBookMove(@TempDir Path tmp) throws Exception {
        var game = new Game(new GameConfig(MyChessEngine.class, defaultConfig()));
        var positionKey = game.getBoard().calculatePositionKey();
        int e2e4 = packMoveFor(game, "e4");

        try (OpeningDB db = OpeningDB.openAt(tmp.resolve("openings.db").toString())) {
            // Thresholds in ChessEngine.getMoveFromOpeningDB: total >= 100, winPct >= 20, lossPct < 45.
            db.put(positionKey, singleMoveBlob(e2e4, 100, 50, 30));
            db.commit();

            var env = new MyChessEnv(db);
            var move = game.getEngine().nextMoveAsync(env).getResult(10, TimeUnit.SECONDS);

            assertEquals(e2e4, move.move(),
                    "Engine must play the single qualifying book move");
            assertEquals(0f, move.weight(), "Book moves are returned with weight 0");
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void openingBookFiltersOutLowSampleEntry(@TempDir Path tmp) throws Exception {
        var game = new Game(new GameConfig(MyChessEngine.class, defaultConfig()));
        var positionKey = game.getBoard().calculatePositionKey();
        int e2e4 = packMoveFor(game, "e4");

        try (OpeningDB db = OpeningDB.openAt(tmp.resolve("openings.db").toString())) {
            // total < 100 -> filtered out, engine must search normally.
            db.put(positionKey, singleMoveBlob(e2e4, 50, 25, 10));
            db.commit();

            var env = new MyChessEnv(db);
            var move = game.getEngine().nextMoveAsync(env).getResult(20, TimeUnit.SECONDS);

            assertNotEquals(0, move.move(),
                    "Engine must fall through to search and return a real move");
        }
    }

    // ---- Async wiring ----

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void nextMoveAsync_returnsTaskWithEnv() {
        var game = new Game(new GameConfig(MyChessEngine.class, defaultConfig()));
        var task = game.getEngine().nextMoveAsync();
        assertNotNull(task.getEnv(), "Task must always carry an env (real or empty)");
    }

    /**
     * Regression test for the depth cap added in 2026-06-06 after cutechess
     * SPRT runs surfaced engine traces with {@code [iter] depth=5500+ pv=…}
     * patterns: in K+R-vs-K endgames with a half-move clock about to roll
     * over the 50-move-rule threshold, every {@code alphaBetaSearchI} node
     * at depth ≥ 1 early-returns {@code draw=0}. The iterative-deepening
     * loop then spins at ~1 ms per iteration to whatever {@code maxDepth}
     * is set; UCI's {@code go} without an explicit depth keyword passes
     * {@code Integer.MAX_VALUE}, so the runaway is unbounded by depth and
     * only stops on timeout. The cap in
     * {@link org.michaelfl.mychess.engines.PositionSearch#MAX_SEARCH_DEPTH}
     * limits this to a sane ceiling.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void depthCap_fiftyMovesAboutToTrigger_searchHonorsMaxSearchDepth() throws Exception {
        // K+R vs K, half-move clock at 99: any white non-capture/non-pawn move
        // crosses 100 at depth 1 inside alphaBetaSearchI and the search returns
        // draw immediately at every node.
        var board = Fen.importFEN("4k3/8/8/8/8/8/3R4/4K3 w - - 99 60");
        var config = new EngineConfig.Builder()
                .maxDepth(Integer.MAX_VALUE)
                .millisPerMove(2000)
                .silent(true)
                .setTranspositionTable(TestSupport.createTestTT())
                .build();
        var game = new Game(new GameConfig(MyChessEngine.class, config), board);
        try {
            var maxDepthReached = new java.util.concurrent.atomic.AtomicInteger();
            var task = game.getEngine().nextMoveAsync(new MyChessEnv(), info ->
                    maxDepthReached.updateAndGet(prev -> Math.max(prev, info.depth())));
            task.getResult(10, TimeUnit.SECONDS);

            assertTrue(maxDepthReached.get() > 0,
                    "search must have run at least one iteration; observed "
                            + maxDepthReached.get());
            assertTrue(maxDepthReached.get() <= org.michaelfl.mychess.engines.PositionSearch.MAX_SEARCH_DEPTH,
                    "iterative-deepening must respect MAX_SEARCH_DEPTH = "
                            + org.michaelfl.mychess.engines.PositionSearch.MAX_SEARCH_DEPTH
                            + "; observed " + maxDepthReached.get());
        } finally {
            game.shutdown();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void shutdown_rejectsFurtherSubmissions() {
        var game = new Game(new GameConfig(MyChessEngine.class, defaultConfig()));
        var engine = game.getEngine();
        game.shutdown();
        assertThrows(RejectedExecutionException.class,
                engine::nextMoveAsync,
                "Submitting after shutdown must be rejected");
    }
}
