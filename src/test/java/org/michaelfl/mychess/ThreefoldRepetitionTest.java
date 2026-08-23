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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
@SuppressWarnings("SameParameterValue")
class ThreefoldRepetitionTest {

    /**
     * Before white's 62nd move of <a href="https://lichess.org/ljG2b74s">ljG2b74s</a>.
     * White is in check with three legal replies, one of which stalemates black — see
     * {@link #engineNeitherStalematesNorRepeatsWhenWinning()}.
     */
    private static final String STALEMATE_TRAP_FEN = "5Q2/7k/3N4/4P3/6R1/8/2r3P1/2K5 w - - 15 62";

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

        final String stalemateCapture = "c1-c2";

        assertNotEquals(stalemateCapture, ChessUtil.moveToString(move.move()),
                "Kxc2 (" + stalemateCapture + ") takes the rook but stalemates black, throwing a won game; "
                        + "the engine must decline it");

        // Measured at +15.05; the bound only has to sit clear of the 0.00 of either draw.
        assertTrue(move.weight() > 10f,
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
        // The depth EngineTestBase.engineConfig(tt) uses, mirrored because this test builds its
        // own config to flip one flag and must otherwise match its partner exactly. If the
        // shared helper ever changes depth, change this with it.
        final int sharedHelperMaxDepth = 8;

        var config = new EngineConfig.Builder()
                .maxDepth(sharedHelperMaxDepth)
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
                .maxDepth(sharedHelperMaxDepth)
                .enableThreefoldRepetition(true)
                .setTranspositionTable(tt)
                .build();
        game = new Game(new GameConfig(MyChessEngine.class, config), Fen.importFEN(STALEMATE_TRAP_FEN));

        move = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES);

        // CHARACTERIZATION, and the assertion is deliberately the wrong way round: White is
        // winning here — mate in 14 according to Stockfish — so a correct engine would not
        // settle for a draw. It does, and this pins that it does.
        //
        // The defect is that entries computed with repetition detection OFF are not valid
        // inputs for a search with it ON, and nothing stops the second search from using
        // them. The repetition check is path-local by design and deliberately not stored
        // (roadmap § 12.23), so a table hit of sufficient depth returns a score without
        // visiting the children — and on lines where it hits before the repetition has
        // accumulated on the path, the search never reaches the check and inherits the
        // other rule set's score.
        //
        // Cannot happen in play: both rules default to on, and the one place in src/main
        // that turns them off (PGNImporter.importGame) uses a table of its own. It is
        // reachable through the public importGame(GameConfig), and through any future tool
        // that toggles a rule while sharing a table.
        //
        // Why it surfaced with the v4.5.0 complete-PV work rather than before: the
        // truncating cutoff used to hide it twice over. It cut the variation two plies in,
        // before the repetition became visible, and it supplied a score of +15.8 from the
        // detection-off search above. This line therefore read
        // assertFalse(containsRepetition(...)) and passed — it looked like a win. With a
        // complete PV the engine reports what it actually plays.
        //
        // The cold-table counterpart withAFreshTableTheEngineAvoidsTheRepetitionInTheWonPosition
        // stays green: there the engine walks its king out and keeps +15.05. That pair is
        // what makes this characterization attributable to the poisoned table rather than to
        // the position.
        //
        // WON'T FIX — decided 2026-08-23, and deliberately not left as a TODO.
        //
        // The fix would be to make the rule set part of the table identity: XOR a constant
        // derived from isEnableThreefoldRepetition() and isEnableFiftyMovesRule() into the
        // key at the two access points in PositionSearch (tt.get / tt.put). Two XOR, and
        // provably neutral, since the same rule set gives the same key.
        //
        // It is not worth carrying anyway. The defect cannot occur in play, so the fix
        // would be production code exercised only by this test, and the whole v4.5.0
        // episode is the argument against that trade: two defects that were equally real
        // in the code and equally absent in practice cost -44.4 and -166 Elo to repair
        // (roadmap § 12.25). A defect with zero occurrences does not earn a change to the
        // search.
        //
        // What would change the decision: a caller that hands importGame(GameConfig) a
        // shared table with a rule switched off, or a second config-dependent rule in the
        // early-exit condition of alphaBetaSearchPre — which already tests two flags, so
        // this is a class of hazard rather than one instance. Until then this assertion
        // stays as it is, pinning the behavior so that a future change to the table cannot
        // alter it unnoticed.
        //
        // Test family: repetition (defect)
        assertEquals(GameResult.DRAW, move.result(),
                "characterization: with the table poisoned by the detection-off search above the engine "
                        + "settles for a draw in a won position (mate in 14). If this now reports a "
                        + "non-draw, the defect is fixed — invert the assertion; score " + move.weight()
                        + ", pv " + pathToString(move.path()));
    }

    /**
     * Cold-table counterpart of {@link #withRepetitionDetectionDisabledTheShuffleReturns}:
     * same position, same depth, repetition detection on — but the table has never seen a
     * search with detection off, so no score computed under the other rule set can be
     * grafted into this one.
     *
     * <p>This is what localises the defect the sibling test exposes. Measured here: the
     * engine walks its king out ({@code d1-e1 e1-f1 f1-f2}) instead of shuffling back,
     * scores {@code +15.05} and reports no repetition. Repetition avoidance itself
     * therefore works; what fails above is the reuse of a table across a changed rule.
     */
    @Test
    void withAFreshTableTheEngineAvoidsTheRepetitionInTheWonPosition() throws Exception {
        // Mirrors the depth of the sibling test so the two stay comparable.
        final int sharedHelperMaxDepth = 8;

        var config = new EngineConfig.Builder()
                .maxDepth(sharedHelperMaxDepth)
                .enableThreefoldRepetition(true)
                .setTranspositionTable(tt) // fresh from setup(), never warmed with detection off
                .build();
        var game = new Game(new GameConfig(MyChessEngine.class, config), Fen.importFEN(STALEMATE_TRAP_FEN));

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES);

        assertFalse(containsRepetition(STALEMATE_TRAP_FEN, move.path()),
                "with a fresh table the principal variation must not repeat; pv " + pathToString(move.path()));
        assertNotEquals(GameResult.DRAW, move.result(),
                "White is winning here (mate in 14), so a fresh-table search must not report a draw; score "
                        + move.weight() + ", pv " + pathToString(move.path()));
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

    /**
     * Rated lichess game <a href="https://lichess.org/ImKwjaJy55DV">ImKwjaJy55DV</a>, played by
     * the bot on <b>4.4.0</b> — the last repetition this bug cost before the fix, and the only
     * case in the suite reproduced from a whole game rather than from one position.
     *
     * <p>White (myChess) stands about <b>+0.9</b> and shuffles its knight: {@code 20.Nf5 Ke6
     * 21.Nd4+ Kf7 22.Nf5 Ke6 23.Nd4+ Kf7} — and after that last {@code Kf7} the position that
     * first arose at {@code 19...Rg6} is on the board for the third time. Drawn.
     *
     * <p><b>The table is the whole story here, which is why the test has to warm it.</b> Asked
     * about the position before move 23 with an empty table, <em>both</em> 4.4.0 and 4.4.1 avoid
     * the repetition and play {@code Nh4} from depth 4 upward. The blunder only appears when the
     * engine has answered the earlier questions first, exactly as in a real game. Replayed that
     * way, at depth 8:
     *
     * <table border="1">
     *   <caption>Moves 20&ndash;23 with one shared transposition table</caption>
     *   <tr><th>move</th><th>4.4.0</th><th>4.4.1</th></tr>
     *   <tr><td>20</td><td>{@code Nf5}</td><td>{@code Nf5}</td></tr>
     *   <tr><td>21</td><td>{@code Nd4+}</td><td>{@code Rd3}</td></tr>
     *   <tr><td>22</td><td>{@code Nf5}</td><td>{@code Ne2}</td></tr>
     *   <tr><td>23</td><td>{@code Nd4+} → draw</td><td>{@code Rd3}</td></tr>
     * </table>
     *
     * <p>4.4.0 reproduces the game move for move; 4.4.1 leaves the cycle at the first
     * opportunity. This test therefore does what the SPRT cannot at this effect size: it shows
     * the fix working on the exact game that motivated it.
     *
     * <p>It shows it without needing 4.4.0 built, because the two blocks below run the same
     * fixture with {@code enableThreefoldRepetition} flipped. Off, the search takes no early
     * return at the repeating node — which is precisely how the old three-occurrence test
     * behaved there, the position being only twofold — so it stores the node and then plays
     * {@code Nd4+}. That block is what makes the other one mean something: it demonstrates the
     * fixture can still produce the blunder, so its absence with the check on is attributable
     * to the check rather than to evaluation drift.
     *
     * <p><b>A detail worth keeping.</b> Ask Stockfish about the position before move 23 from a
     * bare FEN, and it calls {@code Nd4+} the <em>best</em> move at +1.46. Give it the same
     * position with the move history and {@code Nd4+} drops out of the top four entirely, best
     * becoming {@code Nh4} at +0.92. The two numbers are the bug in miniature: a transposition
     * entry is a history-less verdict on a position, and consulting one for a position whose
     * value depends on its history is how a won game becomes a draw. It is also a warning about
     * measuring: analysing any repetition case from a bare FEN answers a different question than
     * the one being asked.
     *
     * <p><b>Test family:</b> repetition (fixed)
     */
    @Test
    void repetitionFromLichessGameIsAvoidedWithAWarmTable() throws Exception {
        final String repetitionMove = "f5-d4";

        // Block 1 — the check switched off, which is how 4.4.0 behaved at this node: the
        // position is there for the second time, its three-occurrence test declines, and the
        // node is searched and stored like any other. This block exists to prove the fixture
        // can produce the blunder at all. Without it, block 2 could be green for any unrelated
        // reason — an evaluation change that happens to prefer Rd3 would look like a working fix.
        try (var uncorrectedTable = TestSupport.createTestTT()) {
            var uncorrected = playIntoTheShuffle(false, uncorrectedTable);

            assertEquals(repetitionMove, uncorrected.move(),
                    "with the repetition check off the warm-up must reproduce the game: 23.Nd4+ ("
                            + repetitionMove + "). If it does not, this fixture no longer exercises the "
                            + "defect and the assertion below proves nothing");
            assertNotNull(uncorrected.entry(),
                    "the warm-up must leave a transposition-table entry for the repeating position — that "
                            + "entry, scored as if the position were not a repetition, is the mechanism");
        }

        // Block 2 — the same fixture with the check on. Differs in exactly one setting.
        try (var correctedTable = TestSupport.createTestTT()) {
            var corrected = playIntoTheShuffle(true, correctedTable);

            assertNotEquals(repetitionMove, corrected.move(),
                    "23.Nd4+ (" + repetitionMove + ") lets black claim the threefold repetition and throws "
                            + "away about +0.9; the second-occurrence check is what stops it");

            // Stockfish has the best alternative at +0.92; the bound only has to sit clear of 0.00.
            assertTrue(corrected.weight() > 0.2f,
                    "declining the repetition must also keep the advantage rather than settle for the drawn "
                            + "0.00; got " + corrected.weight());
            assertNull(corrected.entry(),
                    "with the check on, the repeating position must NOT be stored: the draw is decided by an "
                            + "early return that precedes the only tt.put, so a path-dependent value never "
                            + "enters the table. Its absence here is the fix, not a failed warm-up");
        }
    }

    /**
     * Replays moves 20&ndash;22 of the game through the engine and returns what it then answers
     * at move 23, together with the table entry for the position the shuffle returns to.
     *
     * <p>The moves are deliberately not appended to the PGN: an importer would replay them
     * without ever consulting the engine, leaving the table cold — and from a cold table the
     * engine avoids the repetition even with the defect present. The searches interleaved below
     * are the fixture; their results are discarded and only their effect on the table matters.
     */
    private static Warmed playIntoTheShuffle(boolean repetitionCheck, TranspositionTable table) throws Exception {
        var config = new EngineConfig.Builder()
                .maxDepth(8)                                  // as EngineTestBase.engineConfig
                .enableThreefoldRepetition(repetitionCheck)
                .setTranspositionTable(table)
                .build();
        var game = GameImporter.importerFor("""
                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6 dxc6 5. O-O f6 6. d4 Bg4 7. Be3 exd4 8. Qxd4 Bxf3
                9. Qxd8+ Rxd8 10. gxf3 Ne7 11. Nd2 a5 12. Nb3 b6 13. Nd4 Kf7 14. Rad1 g5 15. f4 gxf4
                16. Bxf4 Rg8+ 17. Kh1 Rc8 18. Rfe1 a4 19. h3 Rg6
                """).importGame(new GameConfig(MyChessEngine.class, config));

        // The position after 19...Rg6 — the one 21...Kf7 and 23...Kf7 return to.
        long repeatingPosition = game.getBoard().getGameStatus().getPositionHash();

        for (String[] played : new String[][]{{"Nf5", "Ke6"}, {"Nd4", "Kf7"}, {"Nf5", "Ke6"}}) {
            game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);

            for (String move : played) {
                game.makeMove(MoveDescription.fromString(move, game.getTurn()));
            }
        }

        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "after 22...Ke6 white (myChess) must be to move");

        var result = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);

        return new Warmed(ChessUtil.moveToString(result.move()), result.weight(), table.get(repeatingPosition));
    }

    /** What move 23 came out as, at what score, and whether the repeating position was stored. */
    private record Warmed(String move, float weight, TranspositionTable.TTEntryView entry) {}

}
