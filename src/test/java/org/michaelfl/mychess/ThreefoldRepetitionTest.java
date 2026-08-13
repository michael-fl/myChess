package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.TimeUnit;

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
 * ({@link #testFindDrawMove}). The converse — refusing a draw while winning —
 * is where myChess currently fails: see
 * {@link #engineDoesNotAvoidRepetitionWhenWinning}, and
 * {@code docs/known-issues.md} for the mechanism. Two game-derived reproductions
 * live in {@code BlunderTest} — {@code repetition_withColdTable_...} and
 * {@code repetition_withWarmTable_...}, a pair that isolates the defect by
 * searching the same position with a cold and with a warm transposition table.
 *
 * @author Michael Fleischhauer
 */
class ThreefoldRepetitionTest {

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
     * <p>It is spelled out because the same threshold is what breaks the search
     * (see {@link #engineDoesNotAvoidRepetitionWhenWinning}). A fix there must
     * introduce a separate, stricter rule for the search path — treating the
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
     * repetition when it is winning. It currently does not.
     *
     * <p>Position from rated blitz game
     * <a href="https://lichess.org/ljG2b74s">ljG2b74s</a>: white (myChess) has
     * queen, knight, rook and two pawns against a bare rook, is in check from that
     * rook on c2, and {@code Kxc2} simply takes it — the black king is stranded on
     * h7 and defends nothing. Instead, myChess sidesteps with {@code Kb1} and its
     * own principal variation is the shuffle {@code Kb1 Rb2 Kc1 Rc2 Kb1 Rb2},
     * priced at about +15 pawns rather than the draw it actually is. In the game it
     * declined the capture six times running and the game was drawn.
     *
     * <p>Cause, in short: on the first return to a position the three-occurrence
     * check declines, the node falls through to the transposition table, and the
     * table answers with the score stored before the repetition existed — so the
     * third occurrence is never reached at any depth. Full write-up in
     * {@code docs/known-issues.md}.
     *
     * <p><b>TODO — invert once the search treats the second occurrence along its
     * path as a draw.</b> Then {@code Kxc2} must be the answer and the weight must
     * be the material win, so replace the assertions below rather than relaxing
     * them.
     *
     * <p><b>Blunder family:</b> repetition
     */
    @Test
    void engineDoesNotAvoidRepetitionWhenWinning() throws Exception {
        var board = Fen.importFEN("5Q2/7k/3N4/4P3/6R1/8/2r3P1/2K5 w - - 15 62");
        var game = new Game(new GameConfig(MyChessEngine.class, engineConfig(tt)), board);

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES);

        // The defect is that the rook is not taken — not which square the king picks
        // instead. Pinning the exact sidestep made this test fail on the v4.4.0 PeSTO
        // tables purely because b1 and d1 swapped rank, so assert the defect itself.
        assertNotEquals("c1-c2", ChessUtil.moveToString(move.move()),
                "characterization: myChess must still sidestep rather than take the free rook with Kxc2 "
                        + "(c1-c2). If it now takes it, the repetition handling improved — replace this "
                        + "characterization with a positive assertion on c1-c2");
        assertTrue(move.weight() > 10f,
                "characterization: the repetition line is priced as a large material advantage instead of "
                        + "the draw it is; got white-POV weight " + move.weight());
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

}
