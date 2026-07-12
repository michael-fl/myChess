package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Correctness coverage for storing null-move-pruning cutoffs in the
 * transposition table (roadmap § 12.2 follow-up: "TT-store the NMP
 * cutoffs"). The change routes the NMP fail-high through the shared
 * TT-store path in {@code PositionSearch.alphaBetaSearchPre}, so a
 * cutoff is now written as a {@link TranspositionTable.Bound#LOWER}
 * entry at the node's full {@code remainingDepth} with {@code bestMove = 0}.
 *
 * <p><b>Why not a direct "the entry is in the TT" assertion?</b> An NMP
 * cutoff only fires deep in the tree where the alpha-beta window is
 * narrow (never near the root, where the window is wide and
 * {@code weight >= beta} is unreachable), and those deep entries are
 * overwritten by regular stores when the same position is re-entered
 * with a wider window later in the same search. The NMP signature
 * (LOWER bound + no best move) is therefore transient and not reliably
 * present once the search finishes. Combined with {@code TranspositionTable}
 * being {@code final} (no put-spy injectable) and {@code Statistics}
 * being private to the search, an atomic "this exact entry was written"
 * assertion is not achievable without opening production code.
 *
 * <p>What is both achievable and load-bearing is the <em>correctness</em>
 * property: a broken NMP store (wrong bound, depth, or score) would
 * corrupt later searches that reuse the entry, producing a different —
 * and wrong — result. This test drives the same position twice through
 * one shared, non-cleared TT: the second, TT-warm search reuses whatever
 * NMP cutoffs were stored, and must return exactly the same move and
 * score as the cold search. It runs deep enough (`maxDepth ≥ 7`) that
 * NMP is active, so the reused entries include NMP cutoffs.
 *
 * @author Michael Fleischhauer
 */
class NmpTranspositionStoreTest extends EngineTestBase {

    /** Deep enough that NMP is active (child still gets ≥ 2 plies after R=2). */
    private static final int SEARCH_DEPTH = 7;

    /**
     * A quiet middlegame position (Ruy-Lopez-ish) with pieces on both
     * sides — plenty of NMP-eligible nodes with narrow windows, and a
     * non-trivial best move that a corrupt TT reuse would be likely to
     * flip.
     */
    private static final String MIDGAME_FEN =
            "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQK2R w KQkq - 0 1";

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void warmTtSearch_reusingNmpCutoffs_matchesColdSearch() throws Exception {
        var config = new EngineConfig.Builder()
                .maxDepth(SEARCH_DEPTH)
                .silent(true)
                .setTranspositionTable(tt)
                .build();

        // Cold search: TT starts empty, NMP cutoffs get stored.
        MoveAndWeight cold = searchOnce(config);

        // Warm search: same TT, not cleared. The second search now reuses
        // the stored NMP cutoffs (LOWER-bound entries). A store written with
        // the wrong depth/bound/score would trigger a wrong cutoff here and
        // diverge from the cold result.
        MoveAndWeight warm = searchOnce(config);

        assertEquals(ChessUtil.moveToString(cold.move()), ChessUtil.moveToString(warm.move()),
                "warm-TT search (reusing NMP cutoffs) must pick the same move as the cold search");
        assertEquals(cold.weight(), warm.weight(),
                "warm-TT search (reusing NMP cutoffs) must return the same score as the cold search");
    }

    private MoveAndWeight searchOnce(EngineConfig config) throws Exception {
        var board = Fen.importFEN(MIDGAME_FEN);
        var game = new Game(new GameConfig(ENGINE, config), board);

        return game.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES);
    }
}
