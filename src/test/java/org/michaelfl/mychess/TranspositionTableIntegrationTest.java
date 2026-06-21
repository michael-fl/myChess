package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.TranspositionTable.TTEntryView;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * End-to-end smoke tests that drive the actual search and inspect the
 * {@link TranspositionTable} afterwards. These verify the plumbing
 * between {@code EngineConfig}, {@code ChessEngine},
 * {@code PositionSearch.alphaBetaSearchPre}, and the TT instance — a
 * silent break in any of those layers would let the unit tests in
 * {@link TranspositionTableTest} stay green while the TT effectively
 * does nothing during search.
 *
 * @author Michael Fleischhauer
 */
class TranspositionTableIntegrationTest {

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    private static EngineConfig configWith(TranspositionTable tt) {
        return new EngineConfig.Builder()
                .maxDepth(4)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
    }

    /**
     * Plays the engine's chosen first move on a copy of the board and
     * returns the resulting Zobrist hash. The root position itself is
     * not stored in the TT (the root loop in
     * {@code PositionSearch.calculateNextMove} iterates moves directly,
     * without going through {@code alphaBetaSearchPre} for the root);
     * the first position the TT actually sees is the one *after* the
     * root's best move.
     */
    private static long hashAfterMove(Game game, int packedMove) {
        var board = game.getBoard().copy();
        board.makeMove(packedMove);
        return board.getGameStatus().getPositionHash();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void afterSearch_ttContainsPostBestMoveEntry() throws Exception {
        var game = GameImporter.importerFor("1. e4 e5").importGame(
                new GameConfig(MyChessEngine.class, configWith(tt)));

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(20, TimeUnit.SECONDS);

        // The position reached by playing the engine's chosen move is
        // searched by alphaBetaSearchPre at depth 1 of the root iteration
        // and therefore must end up in the TT.
        long postMoveHash = hashAfterMove(game, move.move());
        TTEntryView entry = tt.get(postMoveHash);

        assertNotNull(entry,
                "TT must contain an entry for the position reached by playing the engine's "
                        + "chosen move (the first position alphaBetaSearchPre visits)");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void transposedPositions_shareTTEntries() throws Exception {
        // Two move orders reaching the same position after their respective
        // root move. Both searches feed the same TT instance — the second
        // search's lookup of the shared post-move hash must hit an entry
        // populated by the first search (later overwritten or kept by the
        // depth-preferred policy, but never null).

        // Knight-only moves so neither sequence leaves an en-passant
        // square nor resets the half-move clock — those would otherwise
        // diverge between the two move orders and break the precondition.
        var gameA = GameImporter.importerFor("1. Nf3 Nf6 2. Nc3 Nc6").importGame(
                new GameConfig(MyChessEngine.class, configWith(tt)));
        var gameB = GameImporter.importerFor("1. Nc3 Nc6 2. Nf3 Nf6").importGame(
                new GameConfig(MyChessEngine.class, configWith(tt)));

        // Precondition: after both games have played their move 2, the
        // resulting positions transpose to the same Zobrist hash.
        long hashA = gameA.getBoard().getGameStatus().getPositionHash();
        long hashB = gameB.getBoard().getGameStatus().getPositionHash();
        assertEquals(hashA, hashB,
                "test precondition: the two move orders must reach the same Zobrist hash");

        // First search populates the TT for the position reached by
        // gameA's chosen first move.
        MoveAndWeight moveA = gameA.getEngine().nextMoveAsync().getResult(30, TimeUnit.SECONDS);
        long postMoveHashA = hashAfterMove(gameA, moveA.move());

        // Second search runs against the same TT. The entry for gameA's
        // post-move position must still be present (it may have been
        // overwritten by gameB's search but never dropped).
        gameB.getEngine().nextMoveAsync().getResult(30, TimeUnit.SECONDS);

        assertNotNull(tt.get(postMoveHashA),
                "after the second (transposed) search, the TT must still hold an entry "
                        + "for the position the first search populated");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void clear_dropsEntriesPopulatedBySearch() throws Exception {
        var game = GameImporter.importerFor("1. e4 e5").importGame(
                new GameConfig(MyChessEngine.class, configWith(tt)));

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(20, TimeUnit.SECONDS);
        long postMoveHash = hashAfterMove(game, move.move());
        assertNotNull(tt.get(postMoveHash),
                "search must populate the TT before the clear() check");

        tt.clear();

        assertNull(tt.get(postMoveHash),
                "clear() must drop search-populated entries (UCI ucinewgame relies on this)");
    }
}
