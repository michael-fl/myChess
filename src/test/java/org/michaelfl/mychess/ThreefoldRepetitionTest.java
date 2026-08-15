package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.michaelfl.mychess.EngineTest.engineConfig;

/**
 * Threefold-repetition behavior on two levels.
 *
 * <p><b>The rule.</b> A draw is recognized on the <em>third</em> occurrence of a
 * position ({@link #testIsDraw}, {@link #testIsDraw2}), the second is not yet
 * enough ({@link #secondOccurrenceIsNotYetADraw}), and the whole rule can be
 * switched off ({@link #testDisableThreefoldRepetition}). That is correct for the
 * game rule and must stay that way.
 *
 * <p><b>The search.</b> When a draw is what it wants, the engine finds it
 * ({@link #testFindDrawMove}); when it is winning, it must refuse one
 * ({@link #engineNeitherStalematesNorRepeatsWhenWinning}). The second direction used to
 * fail: the search asked for three occurrences, declined at the second, and fell through
 * to a transposition-table entry written before the repetition existed, so the third was
 * never reached at any depth. {@code PositionSearch} now asks
 * {@code Board.isTwofoldRepetition()} instead, which decides the repetition from the search
 * path rather than from the table. The rule above is deliberately <em>not</em> part of that
 * change — {@link #secondOccurrenceIsNotYetADraw} is what keeps the two apart.
 *
 * <p>Two game-derived reproductions live in {@code BlunderTest} —
 * {@code repetition_withColdTable_...} and {@code repetition_withWarmTable_...}, a pair
 * that isolated the defect by searching the same position with a cold and with a warm
 * table; the warm case is now the regression test for the fix.
 * {@code docs/known-issues.md} carries the full mechanism.
 *
 * @author Michael Fleischhauer
 */
class ThreefoldRepetitionTest {

    /**
     * Before white's 62nd move of <a href="https://lichess.org/ljG2b74s">ljG2b74s</a>.
     * White is in check with three legal replies, one of which stalemates black — see
     * {@link #engineNeitherStalematesNorRepeatsWhenWinning()}.
     */
    private static final String STALEMATE_TRAP_FEN = "5Q2/7k/3N4/4P3/6R1/8/2r3P1/2K5 w - - 15 62";

    /** {@code Kxc2}: wins the rook and stalemates black on the spot. */
    private static final String STALEMATE_CAPTURE = "c1-c2";

    /**
     * Lower bound on the score in that position. White is up a queen, a knight and two
     * pawns for a rook, so anything near either draw value fails this. Measured at +15.05.
     */
    private static final float STALEMATE_TRAP_MIN_WEIGHT = 10f;

    /**
     * The depth {@code EngineTestBase.engineConfig(tt)} uses, mirrored because
     * {@link #withRepetitionDetectionDisabledTheShuffleReturns()} has to build its own
     * config to flip one flag and must otherwise match its partner test. If the shared
     * helper ever changes depth, change this with it or the pair stops comparing like
     * with like.
     */
    private static final int SHARED_HELPER_MAX_DEPTH = 8;

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
    void testIsDraw() {
        String moves = """
                1. g3 e6 2. a3 Qh4 3. gxh4 a6 4. Nf3 Nf6 5. Ng1 Ng8 6. Nf3 Nf6 7. Ng1
                """;
        GameImporter importer = GameImporter.importerFor(moves);
        var game = importer.importGame(Game.standardConfig());

        assertEquals(GameResult.ONGOING, game.getResult(), "game must not be finished");
        game.makeMove(MoveDescription.fromString("f6-g8", game.getTurn()));
        assertEquals(GameResult.DRAW, game.getResult(), "game must be draw due to threefold repetition rule");
    }

    @Test
    void testFindDrawMove() throws Exception {
        String moves = """
                1. g3 e6 2. a3 Qh4 3. gxh4 a6 4. Nf3 Nf6 5. Ng1 Ng8 6. Nf3 Nf6 7. Ng1
                """;
        GameImporter importer = GameImporter.importerFor(moves);
        var config = new GameConfig(MyChessEngine.class, engineConfig(tt));
        var game = importer.importGame(config);

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals("f6-g8", ChessUtil.moveToString(move.move()), "Unexpected move");
        assertEquals(0f, move.weight(), "Weight must be 0 (draw)");
        assertEquals(GameResult.DRAW, move.result(), "game must be draw due to threefold repetition rule");
    }

    @Test
    void testIsDraw2() {
        var importer = GameImporter.importerFor("""
                1. e4 c5 2. Be2 Nc6 3. f4 e6 4. Nf3 b6 5. O-O Bb7 6. d3 Qc7 7. c3 Nf6 8. a4 d5 9. e5 Nd7
                10. Na3 a6 11. Qe1 Ne7 12. Nc2 Nf5 13. g4 Ne7 14. Qg3 h5 15. h3 d4 16. c4 Nc6 17. Bd2 g6
                18. Ng5 Be7 19. Ne4 O-O-O 20. a5 Nxa5 21. Rxa5 bxa5 22. Bf3 Nb8 23. Qg2 Nc6 24. g5 Nb4 25.
                Bxb4 axb4 26. Na1 a5 27. Bd1 Bc6 28. Re1 a4 29. b3 a3 30. Qa2 Kb7 31. Bf3 Kb6 32. Nc2 h4
                33. Re2 Rdf8 34. Ne1 Bb7 35. Rg2 Bc6 36. Re2 Ra8 37. Rg2 Rhg8 38. Rg4 Raf8 39. Rg2 Rh8 40.
                Rg4 Bb7 41. Rg2
                """);
        var game = importer.importGame(Game.standardConfig());
        assertEquals(GameResult.ONGOING, game.getResult(), "game must not be finished");
        game.makeMove(MoveDescription.fromString("Bc6", game.getTurn()));
        assertEquals(GameResult.DRAW, game.getResult(), "game must be draw due to threefold repetition rule");
    }

    /**
     * The <em>second</em> occurrence of a position is not yet a draw — the rule
     * needs a third. Genuine assertion, and the behavior must not change: this is
     * what the game rule says.
     *
     * <p>It is spelled out because the same threshold is what used to break the search
     * (see {@link #engineNeitherStalematesNorRepeatsWhenWinning}). The fix introduced a
     * separate, stricter rule for the search path — treating the
     * second occurrence as a draw <em>inside the tree</em> — without loosening the
     * rule tested here.
     */
    @Test
    void secondOccurrenceIsNotYetADraw() {
        // The knight dance returns to the post-3...a6 position: once after
        // 5...Ng8 (second occurrence), once after 7...Ng8 (third).
        var importer = GameImporter.importerFor("""
                1. g3 e6 2. a3 Qh4 3. gxh4 a6 4. Nf3 Nf6 5. Ng1 Ng8
                """);
        var game = importer.importGame(Game.standardConfig());

        assertEquals(GameResult.ONGOING, game.getResult(),
                "the second occurrence of the position must not end the game");
        assertFalse(game.getBoard().isThreefoldRepetition(),
                "isThreefoldRepetition must still be false on the second occurrence");

        game.makeMove(MoveDescription.fromString("Nf3", game.getTurn()));
        game.makeMove(MoveDescription.fromString("Nf6", game.getTurn()));
        game.makeMove(MoveDescription.fromString("Ng1", game.getTurn()));
        game.makeMove(MoveDescription.fromString("Ng8", game.getTurn()));

        assertTrue(game.getBoard().isThreefoldRepetition(),
                "the third occurrence must be reported as a threefold repetition");
        assertEquals(GameResult.DRAW, game.getResult(),
                "the third occurrence must end the game as a draw");
    }

    /**
     * The converse of {@link #testFindDrawMove}: the engine must <em>refuse</em> a
     * repetition when it is winning. Since the second-occurrence fix it does, and this
     * position is the sharpest guard for that, because winning here means threading
     * between two different draws.
     *
     * <p>Position from rated blitz game
     * <a href="https://lichess.org/ljG2b74s">ljG2b74s</a>, before white's 62nd move:
     * white (myChess) has queen, knight, rook and two pawns against a bare rook and is
     * in check from that rook on c2. Only three moves are legal — {@code Kxc2},
     * {@code Kb1}, {@code Kd1} — and one of them is a trap:
     *
     * <ul>
     *   <li><b>{@code Kxc2} is stalemate.</b> Taking the rook leaves black a lone king on
     *       h7 with every escape square covered by {@code Qf8} and {@code Rg4}: g6, g7 and
     *       g8 by the rook on the g-file, h6 and h8 by the queen. Black has no legal move
     *       and the game is drawn on the spot. Declining the free rook is therefore
     *       <em>correct</em>, and myChess gets it right for the right reason — depth 1 does
     *       pick {@code Kxc2} at +20, and depth 2 discovers the stalemate and abandons it.</li>
     *   <li><b>Shuffling between c1 and b1/d1 is the repetition.</b> Black checks along the
     *       second rank, the king steps back, and the position returns every four plies.</li>
     * </ul>
     *
     * <p>So the only winning try is to walk the king <em>out</em> of the rook's reach until
     * the checks run out, which is what the engine now does: {@code Kd1 Rd2 Ke1 Re2 Kf1 Re1
     * Kf2}. The measurement that isolates the fix is the same position with
     * {@code enableThreefoldRepetition} toggled — with the check disabled the principal
     * variation is {@code Kd1 Rd2 Kc1 Rc2 Kd1 Rd2}, the shuffle, at +15.8; with it enabled
     * the shuffle is gone and the score is +15.05.
     *
     * <p><b>What this test used to claim, and why it was wrong.</b> It was written as a
     * characterization asserting that myChess "must still sidestep rather than take the free
     * rook", on the premise that {@code Kxc2} won a piece. It does not — it stalemates. The
     * assertion happened to hold both before and after the fix, so a real improvement in
     * this position was invisible in the suite: the repetition disappeared from the
     * principal variation while the test kept passing unchanged. Two lessons: verify that a
     * capture is actually good before calling its refusal a defect, and prefer asserting the
     * property that the fix changes over one that merely correlates with it.
     *
     * <p><b>Test family:</b> repetition (fixed)
     */
    @Test
    void engineNeitherStalematesNorRepeatsWhenWinning() throws Exception {
        var game = new Game(new GameConfig(MyChessEngine.class, engineConfig(tt)),
                Fen.importFEN(STALEMATE_TRAP_FEN));

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES);

        assertNotEquals(STALEMATE_CAPTURE, ChessUtil.moveToString(move.move()),
                "Kxc2 (" + STALEMATE_CAPTURE + ") takes the rook but stalemates black, throwing a won game; "
                        + "the engine must decline it");
        assertTrue(move.weight() > STALEMATE_TRAP_MIN_WEIGHT,
                "white is a queen, knight and two pawns up for a rook, so the score must stay a large "
                        + "positive one rather than the 0.00 of either draw; got " + move.weight());
        assertFalse(containsRepetition(STALEMATE_TRAP_FEN, move.path()),
                "the principal variation must not repeat a position: with the second-occurrence check "
                        + "disabled it is the shuffle Kd1 Rd2 Kc1 Rc2 Kd1 Rd2, and that is exactly the "
                        + "defect the fix removed; pv " + pathToString(move.path()));
    }

    /**
     * The counterpart that keeps the assertion above honest: with
     * {@code enableThreefoldRepetition} switched off, the same position must produce the
     * shuffle.
     *
     * <p>Without this, {@code engineNeitherStalematesNorRepeatsWhenWinning} could pass for
     * the wrong reason — if some future evaluation change made the engine walk the king away
     * on its own, the assertion would stay green while the repetition handling was gone. The
     * pair localizes it: switching the check off must bring the repetition back. That is
     * also the measurement that attributed the improvement to the fix in the first place
     * (+15.8 with the shuffle, +15.05 without).
     *
     * <p>Note what this does <em>not</em> assert: that the disabled configuration is
     * desirable. It is the configuration a user opts into, and reproducing the shuffle is
     * the correct consequence of opting out of repetition handling.
     *
     * <p><b>Test family:</b> repetition (guard)
     */
    @Test
    void withRepetitionDetectionDisabledTheShuffleReturns() throws Exception {
        // Mirrors engineConfig(tt) exactly, plus the one toggle — the pair is only
        // meaningful if the two searches differ in nothing else.
        var config = new EngineConfig.Builder()
                .maxDepth(SHARED_HELPER_MAX_DEPTH)
                .enableThreefoldRepetition(false)
                .setTranspositionTable(tt)
                .build();
        var game = new Game(new GameConfig(MyChessEngine.class, config), Fen.importFEN(STALEMATE_TRAP_FEN));

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES);

        assertTrue(containsRepetition(STALEMATE_TRAP_FEN, move.path()),
                "with repetition detection off the principal variation must repeat again — otherwise the "
                        + "no-repetition assertion in engineNeitherStalematesNorRepeatsWhenWinning proves "
                        + "nothing about the fix; pv " + pathToString(move.path()));

        // Enable repetition detection again. The engine must now avoid to repeat. Even with warm TT.
        config = new EngineConfig.Builder()
                .maxDepth(SHARED_HELPER_MAX_DEPTH)
                .enableThreefoldRepetition(true)
                .setTranspositionTable(tt)
                .build();
        game = new Game(new GameConfig(MyChessEngine.class, config), Fen.importFEN(STALEMATE_TRAP_FEN));

        move = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES);

        assertFalse(containsRepetition(STALEMATE_TRAP_FEN, move.path()), "with repetition detection enabled the principal variation must not repeat");
    }

    /**
     * Replays {@code path} from {@code fen} on a fresh board and reports whether any
     * position occurs twice, i.e. whether the line the engine intends to play is a
     * repetition.
     *
     * <p>Compares Zobrist keys of the positions reached, which is what the search's own
     * detection compares, so this asks the same question the fix answers.
     */
    private static boolean containsRepetition(String fen, int[] path) {
        var board = Fen.importFEN(fen);
        var seen = new HashSet<Long>();
        seen.add(board.getGameStatus().getPositionHash());

        for (int move : path) {
            if (move == 0) {
                break;
            }
            board.makeMove(move);
            if (!seen.add(board.getGameStatus().getPositionHash())) {
                return true;
            }
        }

        return false;
    }

    /** Renders a principal variation for assertion messages. */
    private static String pathToString(int[] path) {
        var text = new StringBuilder();
        for (int move : path) {
            if (move == 0) {
                break;
            }
            text.append(ChessUtil.moveToString(move)).append(' ');
        }

        return text.toString().trim();
    }

    @Test
    void testDisableThreefoldRepetition() {
        var importer = GameImporter.importerFor("""
                1. e4 c5 2. Be2 Nc6 3. f4 e6 4. Nf3 b6 5. O-O Bb7 6. d3 Qc7 7. c3 Nf6 8. a4 d5 9. e5 Nd7
                10. Na3 a6 11. Qe1 Ne7 12. Nc2 Nf5 13. g4 Ne7 14. Qg3 h5 15. h3 d4 16. c4 Nc6 17. Bd2 g6
                18. Ng5 Be7 19. Ne4 O-O-O 20. a5 Nxa5 21. Rxa5 bxa5 22. Bf3 Nb8 23. Qg2 Nc6 24. g5 Nb4 25.
                Bxb4 axb4 26. Na1 a5 27. Bd1 Bc6 28. Re1 a4 29. b3 a3 30. Qa2 Kb7 31. Bf3 Kb6 32. Nc2 h4
                33. Re2 Rdf8 34. Ne1 Bb7 35. Rg2 Bc6 36. Re2 Ra8 37. Rg2 Rhg8 38. Rg4 Raf8 39. Rg2 Rh8 40.
                Rg4 Bb7 41. Rg2
                """);
        var config = new GameConfig(
                MyChessEngine.class,
                new EngineConfig.Builder().enableThreefoldRepetition(false)
                        .setTranspositionTable(tt).build());
        var game = importer.importGame(config);
        game.makeMove(MoveDescription.fromString("Bc6", game.getTurn()));
        assertEquals(GameResult.ONGOING, game.getResult(), "Game must not be finished yet");
    }

    /**
     * Hamppe&ndash;Meitner, Vienna 1872 &mdash; the "Immortal Draw", and the most demanding
     * repetition in the suite because black has to <em>earn</em> it.
     *
     * <p>Black gave up a queen and two pieces to drag the white king from e1 to c5, and now
     * holds the draw by perpetual: {@code 17...Ba6+ 18.Kc6 Bb7+ 19.Kb5} returns to the
     * position after {@code 17.Kb5}. The test asks for black's 17th move and requires
     * {@code Ba6+} at 0.00.
     *
     * <p>The exact-move assertion is justified rather than over-specific: Stockfish at depth 24
     * has {@code Ba6+} at 0.00 and the next-best move ({@code c5}) at &minus;4.28. It is black's
     * only move that does not lose.
     *
     * <p><b>Why this is stable and not sitting on the depth limit.</b> Since the
     * second-occurrence fix the repetition is <b>four plies</b> from the root
     * ({@code Ba6+ Kc6 Bb7+ Kb5}). Under the old three-occurrence rule it would have taken
     * eight — exactly the depth this configuration provides, with no margin at all.
     *
     * <p><b>What it does not show.</b> myChess agrees with Stockfish only from this move on.
     * Six moves earlier, from {@code 11.Kb4}, Stockfish already reads 0.00, while myChess
     * scores the four positions in between at exactly +8.00 apiece — a piece count, not an
     * evaluation, because white is ten pawns up and the material-only shortcut has switched
     * the positional terms off. It cannot be warned about the king it has walked into, only
     * shown. That half of the story is pinned in
     * {@code MaterialOnlyShortcutEvalTest.immortalDrawIsGradedByCountingPieces()} and
     * {@code BlunderTest.kd7_afterKxb7_engineFindsTheMateThatPunishesTheKingGrab()}.
     *
     * <p><b>Test family:</b> repetition (guard)
     */
    @Test
    void testImmortalDraw() throws ExecutionException, InterruptedException, TimeoutException {
        var importer = GameImporter.importerFor("""
                1. e4 e5 2. Nc3 Bc5 3. Na4 Bxf2+ 4. Kxf2 Qh4+ 5. Ke3 Qf4+ 6. Kd3 d5 7. Kc3 Qxe4 8. Kb3 Na6
                9. a3 Qxa4+ 10. Kxa4 Nc5+ 11. Kb4 a5+ 12. Kxc5 Ne7 13. Bb5+ Kd8 14. Bc6 b6+ 15. Kb5 Nxc6
                16. Kxc6 Bb7+ 17. Kb5
                """);
        var config = new GameConfig(MyChessEngine.class, engineConfig(tt));
        var game = importer.importGame(config);

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals("b7-a6", ChessUtil.moveToString(move.move()), "Unexpected move");
        assertEquals(0f, move.weight(), "Weight must be 0 (draw)");
        assertEquals(GameResult.DRAW, move.result(), "game must be draw due to threefold repetition rule");
    }

}
