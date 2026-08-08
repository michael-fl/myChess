package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.MyChessEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the search counts each visited position exactly once.
 *
 * <p>The benchmark command ("bench") uses {@code Statistics.getPositionsCount()}
 * — surfaced through {@link org.michaelfl.mychess.engines.IterationInfo#nodes()}
 * — as its node signature. A miscount would silently corrupt every bench
 * comparison, so this guards the counting itself.
 *
 * @author Michael Fleischhauer
 */
class NodeCountTest {

    /**
     * A depth-1 search from the start position visits exactly 21 distinct
     * positions: the root plus its 20 legal replies (each reply is a leaf,
     * evaluated once). The reported node count must equal that — a larger
     * value means at least one position is counted more than once.
     */
    private static final int EXPECTED_DEPTH_1_NODES = 21;

    /**
     * A depth-2 search from the start position visits 138 positions with the
     * current search. Depth 1 has no interior nodes, so it cannot catch a
     * re-introduced interior double-count (a node counted in both
     * {@code alphaBetaSearchPre} and {@code alphaBetaSearchMain}); this guards
     * that case.
     *
     * <p>Unlike the depth-1 count, this value depends on move ordering and
     * pruning, so it is an empirical signature: update it deliberately when an
     * intentional search or evaluation change alters the tree (as one would a
     * Stockfish bench number), never to mask an accidental miscount.
     */
    private static final int EXPECTED_DEPTH_2_NODES = 140;

    private static final int SEARCH_TIMEOUT_SECONDS = 20;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void depth1FromStartPositionCountsEachPositionOnce() throws Exception {
        long nodes = searchNodeCount(Board.createNewGame(), 1);

        assertEquals(EXPECTED_DEPTH_1_NODES, nodes,
                "a depth-1 search visits the root + 20 legal replies (21 distinct positions); "
                        + "a higher count means positions are counted more than once "
                        + "(a leaf counted both as a main-search node and as the quiescence root, "
                        + "and interior nodes counted in both alphaBetaSearchPre and alphaBetaSearchMain)");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void depth2FromStartPositionDoesNotDoubleCountInteriorNodes() throws Exception {
        long nodes = searchNodeCount(Board.createNewGame(), 2);

        assertEquals(EXPECTED_DEPTH_2_NODES, nodes,
                "a depth-2 search must count each position once; a higher value suggests interior "
                        + "nodes are counted in both alphaBetaSearchPre and alphaBetaSearchMain");
    }

    /**
     * Runs a fixed-depth search and returns the total node count reported by
     * the final iteration — the same value {@code bench} records.
     *
     * @param board the position to search
     * @param depth the fixed search depth in plies
     * @return the cumulative node count of the completed search
     */
    private static long searchNodeCount(Board board, int depth) throws Exception {
        var tt = TranspositionTable.getDefaultInstance();
        tt.clear();

        var config = new EngineConfig.Builder()
                .maxDepth(depth)
                .millisPerMove(SEARCH_TIMEOUT_SECONDS * 1_000)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
        var game = new Game(new GameConfig(MyChessEngine.class, config), board);

        var nodes = new AtomicLong();

        try {
            game.getEngine()
                    .nextMoveAsync(new MyChessEnv(), info -> nodes.set(info.nodes()))
                    .getResult(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            game.shutdown();
        }

        return nodes.get();
    }
}
