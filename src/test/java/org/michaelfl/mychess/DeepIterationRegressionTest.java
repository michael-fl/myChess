package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.PositionSearch;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The search must survive the depths its own cap permits.
 *
 * <p>Two constants disagree about how deep the search may go:
 *
 * <ul>
 *   <li>{@code PositionSearch.MAX_SEARCH_DEPTH = 64} — the hard cap on the iterative-deepening
 *       target depth, deliberately set "well beyond what myChess actually reaches in any
 *       practical time control" so that pathological positions cannot run away.</li>
 *   <li>{@code KillerMoves.moves = new int[50][2]} — indexed by search depth, dimensioned
 *       with a hard-coded 50.</li>
 * </ul>
 *
 * Between depth 50 and depth 64 every search therefore throws
 * {@code ArrayIndexOutOfBoundsException: Index 50 out of bounds for length 50}. The two
 * numbers need one source, not two guesses.
 *
 * <p><b>Why this is reachable in a real game rather than only in theory.</b> The cap exists
 * because in a fully blocked position every node returns immediately — a repetition, a
 * fifty-move draw, no legal progress — so each further ply costs almost nothing and the
 * iteration races upward. Measured on the position below: depth 39 at 2 495 nodes, depth 50
 * at 16 760, the whole search in four milliseconds.
 *
 * <p><b>Observed in play.</b> Rated rapid game
 * <a href="https://lichess.org/WuqwLqOw">WuqwLqOw</a> (laura-bot vs myChessJava, drawn),
 * move 64, three times in a row in the same game. It went unnoticed because
 * {@code UciHandler.awaitAndEmitBestmove} catches it, logs "Search failed" and still emits
 * the move from the last iteration that completed — so myChess kept playing and the game
 * finished normally. That masking is what let it survive undetected; the price is that
 * which iteration provides the move becomes a matter of chance, and with little time on the
 * clock that can be a very shallow one.
 *
 * @author Michael Fleischhauer
 */
class DeepIterationRegressionTest {

    /**
     * Move 64 of <a href="https://lichess.org/WuqwLqOw">WuqwLqOw</a>, black (myChess) to
     * move. A pure pawn endgame with every pawn blocked and no pieces left: only the two
     * kings can move, so every continuation repeats. That is what lets the iteration reach
     * depth 50 in milliseconds.
     */
    private static final String BLOCKED_PAWN_ENDGAME_FEN = "8/3k1p2/8/3p1p2/p1pP1P1p/P1P2PpP/2P3P1/5K2 b - - 16 64";

    /**
     * The {@code KillerMoves} dimension, mirrored here because the field itself is private.
     * If the fix widens that array, this number goes stale but nothing breaks — the test
     * still searches deeper than 50 and still must not throw.
     */
    private static final int KILLER_TABLE_DEPTHS = 50;

    /**
     * Target depth: above the killer table, comfortably below
     * {@link PositionSearch#MAX_SEARCH_DEPTH}. Any value in that window reproduces; the
     * margin on both sides keeps the test meaningful if either constant is nudged rather
     * than unified.
     */
    private static final int DEPTH_PAST_KILLER_TABLE = KILLER_TABLE_DEPTHS + 5;

    /** Generous per-move budget: the point is the depth, and this search takes milliseconds. */
    private static final int BUDGET_MS = 60_000;

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    private Game gameAtDepth(String fen, int depth) {
        var engineConfig = new EngineConfig.Builder()
                .maxDepth(depth)
                .millisPerMove(BUDGET_MS)
                .silent(true)
                .setTranspositionTable(tt)
                .build();

        return new Game(new GameConfig(MyChessEngine.class, engineConfig), Fen.importFEN(fen));
    }

    /**
     * Searching past depth 50 must not throw.
     *
     * <p>This is the regression itself and it is <b>expected to fail</b> until the
     * {@code KillerMoves} dimension is derived from the same constant as
     * {@code MAX_SEARCH_DEPTH}. It fails with
     * {@code ArrayIndexOutOfBoundsException: Index 50 out of bounds for length 50}, wrapped
     * in an {@code ExecutionException} by {@code NextMoveTask.getResult}.
     *
     * <p>No characterization variant is offered here, deliberately. The blunder tests in
     * {@code BlunderTest} are relaxed to green because the defects they pin are open
     * evaluation questions with no obvious fix, and a permanently red suite trains people to
     * ignore it. A crash is different in kind: it has a one-line fix, and pinning it green
     * would mean asserting that myChess *does* throw — a promise nobody wants to keep.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void searchBeyondTheKillerTableDoesNotThrow() {
        assertTrue(PositionSearch.MAX_SEARCH_DEPTH > KILLER_TABLE_DEPTHS,
                "premise of this test: the search cap must allow depths the killer table cannot index. "
                        + "If MAX_SEARCH_DEPTH has been lowered to " + KILLER_TABLE_DEPTHS + " or below, the "
                        + "contradiction is gone by a different route and this test needs rewriting rather "
                        + "than deleting");

        var game = gameAtDepth(BLOCKED_PAWN_ENDGAME_FEN, DEPTH_PAST_KILLER_TABLE);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "at move 64 of WuqwLqOw black (myChess) must be to move");

        var result = assertDoesNotThrow(
                () -> game.getEngine().nextMoveAsync().getResult(110, TimeUnit.SECONDS),
                "searching to depth " + DEPTH_PAST_KILLER_TABLE + " must not throw: MAX_SEARCH_DEPTH allows "
                        + PositionSearch.MAX_SEARCH_DEPTH + " while KillerMoves is dimensioned new int["
                        + KILLER_TABLE_DEPTHS + "][2], so every depth from " + KILLER_TABLE_DEPTHS
                        + " upward indexes past the end of that array");

        assertTrue(result.move() != 0,
                "the search must return a real move, not the fallback of a failed iteration");
    }

    /**
     * The control case: the same position one ply below the killer table's dimension.
     *
     * <p>It passes today, which is what makes the failure above a boundary problem rather
     * than something wrong with this position in general. Keep both: if a future change makes
     * the deep case pass for the wrong reason — by capping the iteration at 49, say — this
     * one still shows the search works, and the pair localises any regression to the
     * boundary itself.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void searchUpToTheKillerTableWorks() {
        var game = gameAtDepth(BLOCKED_PAWN_ENDGAME_FEN, KILLER_TABLE_DEPTHS - 1);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "at move 64 of WuqwLqOw black (myChess) must be to move");

        var result = assertDoesNotThrow(
                () -> game.getEngine().nextMoveAsync().getResult(110, TimeUnit.SECONDS),
                "depth " + (KILLER_TABLE_DEPTHS - 1) + " stays inside the KillerMoves dimension and "
                        + "must always have worked");

        assertTrue(result.move() != 0, "the search must return a real move");
    }
}
