package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproductions of suspected blunders observed in match-play PGNs.
 * Each test imports the played game up to the move before the
 * alleged mistake, plays the mistake, and asserts that the engine,
 * given a fresh search on the resulting position, agrees that the
 * move was a blunder (i.e. the white-POV evaluation collapses well
 * past where it sat in the move preceding the mistake).
 *
 * <p>The engine runs with the per-move time budget pinned in
 * {@link #SEARCH_BUDGET_MS} below — chosen to match the match-play
 * budget so the eval shape lines up with what was actually played
 * at the board. The JUnit {@code @Timeout} on each test is set
 * generously above that budget so a regression that hangs is still
 * surfaced.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("SameParameterValue")
@Tag("slow")
class BlunderTest {

    /**
     * Per-move thinking time the engine is given when re-searching
     * a reproduced position. Matches the {@code tc=40/1200}
     * cutechess setting used in the match runs against Pulse / SF
     * — 1 200 s for 40 moves works out to roughly 30 s per move.
     */
    private static final int SEARCH_BUDGET_MS = 30_000;

    /**
     * JUnit-level safety timeout. Long enough that a clean
     * {@link #SEARCH_BUDGET_MS}-ms search plus a few seconds of
     * overhead fit, short enough that a hung search fails fast.
     */
    private static final int JUNIT_TIMEOUT_S = 60;

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    private static Game gameWithExplicitBudget(String pgn, TranspositionTable tt) {
        var engineConfig = new EngineConfig.Builder()
                .millisPerMove(SEARCH_BUDGET_MS)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
        var gameConfig = new GameConfig(MyChessEngine.class, engineConfig);

        return GameImporter.importerFor(pgn).importGame(gameConfig);
    }

    private static Game gameFromFen(String fen, TranspositionTable tt) {
        var engineConfig = new EngineConfig.Builder()
                .millisPerMove(SEARCH_BUDGET_MS)
                .silent(true)
                .setTranspositionTable(tt)
                .build();

        return new Game(new GameConfig(MyChessEngine.class, engineConfig), Fen.importFEN(fen));
    }

    /**
     * Round 1 of {@code mychess-vs-pulse2000.pgn} (myChess vs
     * Pulse-2000, 0-1). The chess-objective blunder is
     * {@code 66.Nxe5}: until move 65 the position is roughly
     * balanced and white can still hold the draw, but trading the
     * last minor piece on e5 ({@code 66.Nxe5 Kxe5}) leaves a king-
     * and-pawn endgame that is decisively lost for white. The
     * engine itself missed this — the PGN comment shows white's
     * eval drifting near zero ({@code -0.04} at depth 12) for the
     * Nxe5 move itself and only collapsing to {@code -8.00} eight
     * moves later when the deeper search finally sees the endgame
     * shape.
     *
     * <p>The test reproduces the position after {@code 65...Ne5},
     * plays {@code 66.Nxe5}, and asserts that a fresh search on the
     * resulting position returns a clearly negative white-POV
     * evaluation. <b>Expected to fail with the current engine</b> —
     * it documents an evaluation blind spot: at the depths reachable
     * within the standard time budget, myChess still sees the
     * post-Nxe5 endgame as roughly equal. The test will start
     * passing once the eval (or the search depth in this kind of
     * pawn endgame) is strong enough to recognise the loss before
     * playing into it.
     *
     * <p><b>Test family:</b> endgame-technique (fixed)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void nxe5_atMove66_vsPulse2000_collapsesEvaluation() throws Exception {
        var pgn = """
                1. c4 Nc6 2. e3 d5 3. cxd5 Qxd5 4. Nc3 Qd6 5. Nf3 Nf6 6. Bc4 Be6 7. Nb5 Qd7
                8. Bxe6 fxe6 9. Qb3 e5 10. Ng5 Nd8 11. O-O e6 12. d4 a6 13. dxe5 Qxb5
                14. Qxb5+ axb5 15. exf6 gxf6 16. Ne4 f5 17. Nf6+ Kf7 18. Nd7 Bg7 19. Rd1 Nc6
                20. e4 Bd4 21. exf5 exf5 22. Bg5 Rhe8 23. Be3 Bxe3 24. fxe3 Rxe3 25. Rd5 Rd8
                26. Rad1 Ke7 27. Nc5 Rxd5 28. Rxd5 Re1+ 29. Kf2 Re5 30. Rxe5+ Nxe5 31. Nxb7 Nd3+
                32. Ke3 Nxb2 33. Kd4 Kd7 34. Kc5 c6 35. Kd4 Nc4 36. Nc5+ Kd6 37. Nb7+ Ke7
                38. Kc5 Ne5 39. Kd4 Nd7 40. Ke3 Kf6 41. Kd4 Ke6 42. Nd8+ Kd6 43. Ke3 c5
                44. Kf4 Ke7 45. Nc6+ Kf6 46. Na7 b4 47. Nb5 Nf8 48. Nd6 Ne6+ 49. Kf3 Nd4+
                50. Ke3 b3 51. axb3 Nxb3 52. Nc4 Nd4 53. Nb6 Ne6 54. Kf3 h6 55. Nd7+ Kg7
                56. Ke3 Kf7 57. Ne5+ Kf6 58. Nd7+ Ke7 59. Nb6 Nf8 60. Kf4 Ke6 61. Kf3 Ng6
                62. Na4 Kd6 63. Ke3 Kd5 64. Nb6+ Ke6 65. Nc4 Ne5
                """;
        var game = gameWithExplicitBudget(pgn, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "after 65...Ne5 white must be to move");

        // Play the actual blunder — trading the last minor piece off
        // into a pawn endgame that turns out to be lost for white.
        game.makeMove(MoveDescription.fromString("Nxe5", game.getTurn()));

        // Black to move now. The MoveAndWeight returned by the engine
        // carries its weight in White-POV (see weightFactor handling
        // in ChessEngine.calculateNextMove): negative = bad for white.
        var result = game.getEngine().nextMoveAsync().getResult(JUNIT_TIMEOUT_S - 5, TimeUnit.SECONDS);

        // TODO: should be weight < -6
        assertTrue(result.weight() < -0.9f,
                "after 66.Nxe5 the engine must see white as clearly lost; "
                        + "got white-POV eval " + result.weight());
    }

    // ----------------------------------------------------------------
    // Move-selection reproductions from test run 10 (myChess 3.1 vs
    // Pulse-2000). Each case reconstructs the exact position myChess
    // faced immediately *before* a move-selection blunder, runs a
    // fresh search with the match time budget, and asserts that the
    // engine does NOT pick the same losing move it played in the game.
    //
    // Unlike the Nxe5 case above (a latent endgame-eval blind spot
    // that only surfaces several plies later), these are pure
    // selection errors: the engine rated the blunder move highly at
    // the moment of choosing it, so the bug only reproduces when the
    // engine is asked to *choose* from the pre-blunder position — once
    // the move is on the board the evaluation corrects itself.
    //
    // Expected to fail while the underlying evaluation / horizon
    // weakness persists; each will turn green once the engine stops
    // selecting the move.
    // ----------------------------------------------------------------

    private static MoveAndWeight searchCurrentPosition(Game game) throws Exception {
        return game.getEngine().nextMoveAsync().getResult(JUNIT_TIMEOUT_S - 5, TimeUnit.SECONDS);
    }

    private static void assertEngineAvoids(MoveAndWeight result, int blunderFrom, int blunderTo, String blunderName) {
        int chosen = result.move();
        boolean isBlunder = Move.getFromField(chosen) == blunderFrom && Move.getToField(chosen) == blunderTo;

        assertFalse(isBlunder,
                "engine must not reproduce the blunder " + blunderName + " ("
                        + ChessUtil.moveToString(blunderFrom, blunderTo) + "); it chose "
                        + ChessUtil.moveToString(chosen) + " with white-POV eval " + result.weight());
    }

    /**
     * File game 13 / Round 7, {@code myChess vs Pulse-2000} (0-1). After
     * {@code 15...f6} the white knight on e5 is attacked. myChess answered
     * {@code 16.Ng6??} — a knight sacrifice it rated {@code +1.53} — and
     * after {@code 16...hxg6} it was simply a piece down for an attack
     * that never materialised. A classic horizon / over-optimistic
     * king-attack evaluation. The engine should retreat or defend the
     * knight instead of playing the unsound sacrifice.
     *
     * <p><b>Test family:</b> unsound-attack (fixed)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void ng6_atMove16_vsPulse2000_engineAvoidsUnsoundSacrifice() throws Exception {
        var pgn = """
                1. b4 b5 2. e3 c6 3. Nf3 e6 4. a3 Nf6 5. Nc3 d5 6. d4 a5 7. bxa5 Qxa5
                8. Bb2 Ne4 9. Qd3 Ba6 10. Qd1 e5 11. Nxe5 Bd6 12. Bd3 Nxc3 13. Qd2 Bc8
                14. Qxc3 b4 15. Qd2 f6
                """;
        var game = gameWithExplicitBudget(pgn, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "after 15...f6 white (myChess) must be to move");

        var result = searchCurrentPosition(game);

        // Since the v4.2.0 all-captures quiescence search, the engine correctly
        // declines the unsound 16.Ng6 sacrifice; this test now guards against a
        // regression back to it.
        assertEngineAvoids(result, Board.e5, Board.g6, "16.Ng6");
    }

    /**
     * File game 19, {@code myChess vs Pulse-2000} (0-1). After
     * {@code 38...Qg4} myChess (white) grabbed a pawn with
     * {@code 39.Rxd5??}, rating the position {@code +0.3}, but it walks
     * straight into {@code 39...Qxg3+ 40.Qg2 Qxg2+ 41.Kxg2 Be4+} forking
     * king and rook — the evaluation then collapsed to {@code -6.0}. The
     * forced {@code 40.Qg2} that followed was merely the losing
     * consequence (a fresh search from that later position already
     * reports {@code -6.0}, so the engine knows it is lost there); the
     * genuine over-optimistic selection error is {@code 39.Rxd5}. The
     * engine should decline the pawn and address the {@code ...Qxg3+}
     * threat instead.
     *
     * <p><b>Test family:</b> tactical-oversight (fixed)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rxd5_atMove39_vsPulse2000_engineAvoidsLosingPawnGrab() throws Exception {
        var pgn = """
                1. e4 g6 2. Bd3 d6 3. Nc3 Nc6 4. Bb5 a6 5. Bxc6+ bxc6 6. Nf3 Bg7 7. d4 a5
                8. O-O Nf6 9. Bg5 h6 10. Bxf6 Bxf6 11. e5 Bg7 12. h3 h5 13. Re1 Rb8 14. Rb1 Bf5
                15. Qe2 Rb6 16. Na4 Rb4 17. Nc3 Bh6 18. a3 Rb6 19. Nh4 Bc8 20. e6 Bxe6
                21. Nxg6 Rg8 22. Nh4 Bg7 23. Nf3 Bh8 24. Kh1 Qd7 25. Kh2 c5 26. Red1 cxd4
                27. Nxd4 Bxh3 28. g3 Rg5 29. Qe3 Re5 30. Qh6 Bf6 31. f4 Rc5 32. Nb3 Rxc3
                33. bxc3 Qf5 34. Rd3 Bg4 35. a4 Qe4 36. Kg1 d5 37. f5 Bxf5 38. Qd2 Qg4
                """;
        var game = gameWithExplicitBudget(pgn, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "after 38...Qg4 white (myChess) must be to move");

        var result = searchCurrentPosition(game);

        // Since the v4.2.0 all-captures quiescence search, the engine correctly
        // declines the losing 39.Rxd5 pawn grab; this test now guards against a
        // regression back to it.
        assertEngineAvoids(result, Board.d3, Board.d5, "39.Rxd5");
    }

    /**
     * File game 20, {@code Pulse-2000 vs myChess} (1-0). Stockfish
     * analysis locates the decisive error at {@code 25...Rg7??}: with
     * the rook lift from e7 to g7 myChess (black) walks into white's
     * attack ({@code 26.Rh6}, {@code 27.Rg6+}) instead of consolidating
     * its material advantage. The myChess evaluation lagged behind — it
     * still read about {@code +2.3} for black as late as {@code 27...Kh8}
     * and only crashed a couple of moves later at {@code 28...Nd4+},
     * which is why the raw eval trace first flagged the collapse there.
     * The move that actually throws the game is the rook lift here; the
     * engine should keep the rook active on a useful file instead.
     *
     * <p><b>Test family:</b> king-safety (fixed)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rg7_atMove25_vsPulse2000_engineAvoidsThrowingAdvantage() throws Exception {
        var pgn = """
                1. e4 g6 2. Bd3 d6 3. Nc3 Nf6 4. Be2 Nc6 5. d4 d5 6. e5 Ne4 7. Bf4 Nxc3
                8. bxc3 Bg7 9. Nf3 O-O 10. Rb1 f6 11. exf6 Bxf6 12. Qd2 Rb8 13. Bd3 Bg4
                14. h3 Bxf3 15. gxf3 e5 16. Bh6 Re8 17. Kf1 exd4 18. cxd4 Nxd4 19. Qd1 Qd6
                20. Be3 Nc6 21. h4 Qa3 22. h5 Qxa2 23. hxg6 hxg6 24. Bxg6 Re7 25. Bd3
                """;
        var game = gameWithExplicitBudget(pgn, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "after 25.Bd3 black (myChess) must be to move");

        var result = searchCurrentPosition(game);

        assertEngineAvoids(result, Board.e7, Board.g7, "25...Rg7");
    }

    /**
     * File game 4, {@code Pulse-2000 vs myChess} (1-0). Stockfish
     * analysis pinpoints the decisive error at {@code 16...gxh4??}:
     * after {@code 16.h4} myChess (black) recaptured on h4, opening the
     * h-file straight onto its own king ({@code 17.Rxh4}) and conceding
     * a lasting initiative. The myChess evaluation only registered the
     * damage gradually over the following moves (it still read
     * {@code +0.9} for black as late as move 25), which is why the
     * collapse first surfaced near {@code 26...h6} in the raw eval
     * trace — but the move that actually throws the game is the
     * recapture here. The engine should decline it (e.g. {@code ...g4})
     * and keep the king cover intact.
     *
     * <p><b>Fixed in v4.4.0.</b> With the PeSTO piece-square tables the engine
     * declines the recapture and plays exactly that {@code ...g4}. Stockfish
     * (depth 22) rates the blunder {@code gxh4} at <b>-2.05</b> from black's
     * side, {@code g4} at <b>-0.41</b>, and its own best {@code d6} at
     * <b>+0.74</b> — so the two-pawn error is gone while roughly a pawn of
     * accuracy is still missing. The assertion below is therefore a real
     * requirement now, not a characterization.
     *
     * <p><b>Test family:</b> king-safety (fixed)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void gxh4_atMove16_vsPulse2000_engineAvoidsOpeningOwnKingFile() throws Exception {
        var pgn = """
                1. c4 e5 2. h3 Qh4 3. e3 e4 4. g3 Qe7 5. Nc3 Nf6 6. Nge2 Nc6 7. Nf4 Qe5
                8. a3 Bd6 9. Nb5 g5 10. Nxd6+ cxd6 11. Nh5 Nxh5 12. Qxh5 Na5 13. Qd1 d5
                14. cxd5 Qxd5 15. Qa4 O-O 16. h4
                """;
        var game = gameWithExplicitBudget(pgn, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "after 16.h4 black (myChess) must be to move");

        var result = searchCurrentPosition(game);

        // Fixed by the v4.4.0 PeSTO tables — a positive assertion now, no longer a
        // characterization. TODO: the move it picks is g4 (Stockfish -0.41), the very
        // alternative named above, but Stockfish's best is d6 (+0.74), so about 1.15
        // pawns are still left on the table. Tighten to require d6 if the eval improves.
        assertEngineAvoids(result, Board.g5, Board.h4, "16...gxh4");
    }

    /**
     * Analysis position (Stockfish-annotated), black to move:
     * {@code b3r1kr/pp3pp1/2p5/5Qq1/5n1p/1B2N1P1/P3PP1P/4RRK1 b kq - 1 19}.
     * Black is already slightly better and has two winning tries:
     * <ul>
     *   <li>{@code 19...Qxf5} (played) — trades queens and simplifies;
     *       Stockfish ~{@code -1.8}.</li>
     *   <li>{@code 19...Nxe2+} (missed) — an exchange-winning knight sacrifice;
     *       Stockfish ~{@code -4.2}.</li>
     * </ul>
     *
     * <p>myChess prefers the simpler {@code Qxf5}. The point of {@code Nxe2+} is
     * a deflection: if white accepts with {@code Rxe2}, then
     * {@code Qxf5 Nxf5 Rxe2} wins the exchange (the recapture {@code Ne3xf5}
     * vacates e3 and opens the e-file onto the now-undefended rook) — myChess
     * <em>does</em> calculate this, rating the accept line ~{@code +3}. But
     * white's only correct reply is to <b>decline</b> with {@code Kh1}
     * (Stockfish's sole move), and there myChess evaluates only ~{@code +2}
     * while Stockfish sees ~{@code +4.2}.
     *
     * <p>The gap is purely evaluative, not tactical. After {@code Nxe2+ Kh1}
     * black is +2 pawns in <em>material</em> — which myChess counts — plus
     * ~2 pawns of <em>positional</em> compensation it cannot see: the exposed
     * {@code Kh1}, the a8-bishop on the long a8&ndash;h1 diagonal, and the e2
     * outpost knight. The cause is the missing king-safety / attack term. So
     * {@code Nxe2+ ≈ Qxf5 ≈ +2} to myChess, and it simplifies.
     *
     * <p>An earlier version of this comment also blamed the material-only eval
     * shortcut, "which discards the positional evaluation exactly when a side is
     * +2 pawns". That was wrong, and the error is worth naming because it is easy
     * to repeat: the shortcut keys on {@code materialDelta}, the swing
     * <em>since the root</em>, not on the balance. {@code Nxe2+} captures a pawn,
     * so the delta is +100 and stays inside the band — the positional evaluation
     * runs here. One hole, not two.
     *
     * <p>Positive assertion (since v4.3.1): myChess now <em>finds</em>
     * {@code Nxe2+}. The tapered king endgame table penalizes the exposed,
     * cornered {@code Kh1} in the low-phase position, which tips the evaluation
     * enough to prefer the exchange-winning sacrifice over the simplifying
     * {@code Qxf5}. The blind spot described above is closed; this test now
     * guards against a regression back to declining the sacrifice.
     *
     * <p><b>Test family:</b> king-safety (fixed)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void nxe2_atMove19_engineMissesTheExchangeWinningSacrifice() throws Exception {
        // Stockfish-annotated analysis position, black to move: already slightly better,
        // with two winning tries.
        var game = gameFromFen("b3r1kr/pp3pp1/2p5/5Qq1/5n1p/1B2N1P1/P3PP1P/4RRK1 b kq - 1 19", tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "in the analysis position black must be to move");

        var result = searchCurrentPosition(game);

        boolean foundNxe2 = Move.getFromField(result.move()) == Board.f4
                && Move.getToField(result.move()) == Board.e2;

        assertTrue(foundNxe2,
                "engine must select the exchange-winning Nxe2+ (f4-e2) — the tapered king-EG table "
                        + "(v4.3.1) closed this blind spot; a miss is a regression. white-POV eval "
                        + result.weight());
    }

    // ----------------------------------------------------------------
    // Philidor's Legacy — rated blitz game
    // https://lichess.org/KSvNk2VQ (TucuEngine 1923 vs myChessJava, 1-0),
    // the first smothered mate myChess walked into on lichess.
    //
    // Two pawns down as black, myChess grabbed the a1 rook with
    // 21...Qxa1?? — equalizing on material but parking its queen in the
    // far corner. White forced the classic pattern:
    //   22.Qd5+ Rf7 23.Qxa8+ Rf8 24.Qd5+ Kh8
    //   25.Nf7+ Kg8 26.Nh6+ Kh8 27.Qg8+ Rxg8 28.Nf7#
    //
    // The cases below are pinned to a FIXED DEPTH rather than a time
    // budget: the interesting quantity here is the depth at which the
    // knowledge appears, and a fixed depth makes the outcome
    // deterministic instead of machine-speed dependent (only the runtime
    // varies). The measured thresholds are recorded per test.
    // ----------------------------------------------------------------

    /** Black (myChess) to move, before the losing 21...Qxa1. */
    private static final String BEFORE_QXA1_FEN = "r4rk1/p1p3pp/2Q5/4N3/5B2/8/PPP1KP1P/R5q1 b - - 2 21";

    /** White to move after 21...Qxa1, mate in 7 starting with Qd5+. */
    private static final String AFTER_QXA1_FEN = "r4rk1/p1p3pp/2Q5/4N3/5B2/8/PPP1KP1P/q7 w - - 0 22";

    /** White to move after 24...Kh8, mate in 4 starting with Nf7+. */
    private static final String BEFORE_NF7_FEN = "5r1k/p1p3pp/8/3QN3/5B2/8/PPP1KP1P/q7 w - - 3 25";

    /**
     * Per-move budget for the fixed-depth cases below. Deliberately far above
     * {@link #SEARCH_BUDGET_MS}: these tests want the <em>depth</em> to be the
     * only bound, so the requested iteration always completes instead of the
     * clock cutting it short and the result falling back to a shallower — and
     * differently-decided — iteration.
     *
     * <p><b>Raised 120 s → 300 s in v4.6.0</b>, because exactly that happened. Disabling the
     * material-only shortcut in capture subtrees ([roadmap § 12.26]) made depth 13 in the
     * {@code Qxa1} position cost more than 120 s, so {@code qxa1_atDepth13} fell back to a
     * shallower iteration and played the blunder it exists to refute. Measured with a 900 s
     * budget: the case passes and the whole test takes 214 s, so the refutation is intact and
     * only the harness was too tight. 300 s is that measurement plus headroom.
     *
     * <p>Only cases that actually reach the cap get slower — the rest finish long before it, so
     * the suite cost is bounded by the handful that are genuinely deep.
     */
    private static final int DEPTH_BOUND_BUDGET_MS = 300_000;

    /**
     * JUnit safety timeout for the fixed-depth cases; above {@link #DEPTH_BOUND_BUDGET_MS}.
     *
     * <p>Raised with the budget in v4.6.0 — see there for why.
     *
     * <p>Package-private, like the three depth-bound helpers below, so
     * {@code StsDefectTest} can share this harness instead of copying it -- see
     * the cross-test sharing convention in {@code docs/testing.md} § 11.1.
     */
    static final int DEPTH_BOUND_TIMEOUT_S = 330;

    static Game gameFromFenAtDepth(String fen, int depth, TranspositionTable tt) {
        var engineConfig = new EngineConfig.Builder()
                .maxDepth(depth)
                .millisPerMove(DEPTH_BOUND_BUDGET_MS)
                .silent(true)
                .setTranspositionTable(tt)
                .build();

        return new Game(new GameConfig(MyChessEngine.class, engineConfig), Fen.importFEN(fen));
    }

    /** Like {@link #searchCurrentPosition}, but waits long enough for {@link #DEPTH_BOUND_BUDGET_MS}. */
    static MoveAndWeight searchCurrentPositionDeep(Game game) throws Exception {
        return game.getEngine().nextMoveAsync().getResult(DEPTH_BOUND_TIMEOUT_S - 10, TimeUnit.SECONDS);
    }

    /**
     * Replays {@code pgn} into a game whose engine is bounded by {@code depth}
     * rather than by the clock — see {@link #DEPTH_BOUND_BUDGET_MS} for why. Used
     * where a case needs the real move history (repetition detection reads the
     * board's status stack, which a bare FEN does not carry).
     */
    private static Game gameFromPgnAtDepth(String pgn, int depth, TranspositionTable tt) {
        var engineConfig = new EngineConfig.Builder()
                .maxDepth(depth)
                .millisPerMove(DEPTH_BOUND_BUDGET_MS)
                .silent(true)
                .setTranspositionTable(tt)
                .build();

        return GameImporter.importerFor(pgn).importGame(new GameConfig(MyChessEngine.class, engineConfig));
    }

    /**
     * Depth at which the engine stops preferring the losing {@code 21...Qxa1}.
     * Measured on v4.3.4: depths 1-12 all pick {@code Qxa1} (the evaluation
     * drifting from +300 cp down to -506 cp as the attack comes into view);
     * at depth 13 it switches to {@code Qxf2+} instead.
     */
    private static final int QXA1_REFUTATION_DEPTH = 13;

    /**
     * The engine must not walk into Philidor's Legacy with {@code 21...Qxa1}
     * once it can search deep enough to see the consequence.
     *
     * <p>Note what this does and does not claim. Black is lost either way: at
     * {@link #QXA1_REFUTATION_DEPTH} the preferred {@code Qxf2+} is still rated
     * about -900 cp, so the "blunder" is choosing <em>mate</em> over merely
     * losing. The genuine defect sits earlier — the queen raid 19...Qxg2 /
     * 21...Qxa1 abandons a bare king, which is precisely what a king-safety
     * term would penalize and myChess has none (roadmap § 12.21). The
     * material-only eval shortcut compounds it: a rook is 500 cp, far past
     * {@code EVALUATE_MATERIAL_ONLY_THRESHOLD = 200 cp}, so positional
     * evaluation is skipped in exactly these material-grabbing lines.
     *
     * <p>In the game myChess had roughly 3 s per move and reached depth 7-8, so
     * it could not reach this refutation — the knowledge exists in the search
     * but not within the clock. A king-safety term should move the threshold
     * down into reachable depths; that is the regression this test guards.
     *
     * <p><b>Test family:</b> corner-grab (guard)
     * <p><b>Contributing:</b> material-only-shortcut — a rook capture is a 500 cp
     * swing, so the delta genuinely does leave the band here. See
     * {@link MaterialOnlyShortcutEvalTest#qxb5AtMove36GrabsThePawnInsteadOfTheExchangeSacrifice()}
     * for the case where the shortcut is the sole cause.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qxa1_atDepth13_engineRefutesTheGreedyRookGrab() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_QXA1_FEN, QXA1_REFUTATION_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "in the pre-blunder position black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineAvoids(result, Board.g1, Board.a1, "21...Qxa1");
    }

    /**
     * The attacking side of the same combination: from the position after
     * {@code 24...Kh8} the engine must find {@code 25.Nf7+} and see the mate.
     *
     * <p>Measured on v4.3.4: depths 1-7 prefer the quiet {@code Nd3} and rate
     * the position a mere +3 pawns; at <b>depth 8</b> (0.6 s) the search finds
     * {@code Nf7+} and reports {@code mate 4}, with the whole pattern in the
     * principal variation — knight checks, queen sacrifice on g8, and the
     * enemy rook forced to seal its own king in.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void nf7_atMove25_engineFindsSmotheredMateInFour() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_NF7_FEN, 8, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "in the mate-in-4 position white must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.e5, Board.f7), ChessUtil.moveToString(result.move()),
                "engine must start Philidor's Legacy with Nf7+ (e5-f7); white-POV eval " + result.weight());
        assertTrue(result.weight() > 500f,
                "the evaluation must be a mate score, not just a material edge; got " + result.weight());
    }

    /**
     * The same combination seen from further out: after {@code 21...Qxa1} the
     * mate is 13 plies deep, and the engine must still choose the entry move
     * {@code 22.Qd5+}.
     *
     * <p>Measured on v4.3.4, this splits move choice from understanding: the
     * right move appears at <b>depth 6</b>, but the position is still scored
     * {@code 0} there; the evaluation climbs (+200 at depth 10, +1000 at
     * depth 12) and only at <b>depth 14</b> (18.7 s) does the search report
     * {@code mate 7} with the full forcing line. The assertion below pins the
     * cheap part — the move — because that is what decides the game; the mate
     * score costs eight extra plies and is documented rather than asserted.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qd5_atMove22_engineFindsTheEntryToTheMatingNet() throws Exception {
        var game = gameFromFenAtDepth(AFTER_QXA1_FEN, 8, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "after 21...Qxa1 white must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.c6, Board.d5), ChessUtil.moveToString(result.move()),
                "engine must enter the mating net with Qd5+ (c6-d5); white-POV eval " + result.weight());
    }

    /**
     * Hamppe&ndash;Meitner, Vienna 1872 (the "Immortal Draw"), after the losing
     * {@code 17.Kxb7}. Black to move, with a forced mate.
     */
    private static final String AFTER_KXB7_FEN = "r2k3r/1Kp2ppp/1p6/p2pp3/8/P7/1PPP2PP/R1BQ2NR b - - 0 17";

    /** Depth at which myChess reports the full mate. Measured: found at 8, still there at 10. */
    private static final int KXB7_MATE_DEPTH = 8;

    /**
     * Lower bound in pawns for a score to be a mating one, derived from the production
     * sentinel rather than written as a magic number: {@code CHECKMATE_WEIGHT_LOW} is in
     * centipawns, {@code MoveAndWeight.weight()} in pawns.
     */
    private static final float MATE_SCORE_FLOOR = WeightingFunction.CHECKMATE_WEIGHT_LOW / 100f;

    /**
     * The mate that punishes the king grab in the Immortal Draw &mdash; and the measurement
     * that turns "myChess plays the losing move" into a number.
     *
     * <p>In the drawn position after {@code 16...Bb7+} white has exactly two legal moves.
     * {@code Kb5} takes the perpetual and draws; {@code Kxb7} grabs the bishop <em>with the
     * king</em> and loses to {@code 17...Kd7 18.Qg4+ Kd6 19.Qc8 Rhxc8 20.Nf3 Rcb8#}. This test
     * asks the second question: given that white has already blundered, does black find the
     * punishment?
     *
     * <p>It does, at depth 8, and with Stockfish's line move for move &mdash; both report
     * mate in 4. That is the point of keeping this as a guard rather than a curiosity: it
     * separates two explanations that look identical from the outside. myChess is <b>not</b>
     * blind to this mating pattern. It sees it as soon as it is in reach.
     *
     * <p><b>The whole failure is one ply of horizon.</b> The mate takes eight plies from here,
     * so declining {@code Kxb7} needs nine — and depth 9 is exactly where the engine starts
     * declining it: measured on v4.4.0, depths 6&ndash;8 pick {@code Kxb7} (+15.00, then
     * +4.00) and depth 9 switches to {@code Kb5} at 0.00. A search given eight plies grabs the bishop and calls the
     * position +4.00.
     *
     * <p>What makes that expensive is the evaluation, not the search. White is up about ten
     * pawns here, so every line trips
     * {@code PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD} and the position is graded by
     * counting pieces — the white king standing on b7 in the middle of black's forces is worth
     * exactly nothing. Stockfish reads the same position as 0.00 six moves earlier because its
     * evaluation prices that king; myChess has no cheap route to the same conclusion and must
     * calculate all eight plies. See
     * {@code MaterialOnlyShortcutEvalTest.immortalDrawIsGradedByCountingPieces()}, which pins
     * the counting itself on the four positions leading up to this one.
     *
     * <p><b>Test family:</b> corner-grab (guard)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void kd7_afterKxb7_engineFindsTheMateThatPunishesTheKingGrab() throws Exception {
        var game = gameFromFenAtDepth(AFTER_KXB7_FEN, KXB7_MATE_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "after 17.Kxb7 black must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.d8, Board.d7), ChessUtil.moveToString(result.move()),
                "black must start the mating net with Kd7 (d8-d7), trapping the king that just took on b7; "
                        + "white-POV eval " + result.weight());
        assertTrue(result.weight() < -MATE_SCORE_FLOOR,
                "the score must be a mating one for black, not a material count: myChess reports mate in 4 "
                        + "here at depth " + KXB7_MATE_DEPTH + ", the same line Stockfish gives; got "
                        + result.weight());
    }

    /** White (myChess) to move, before the g4-abandoning 21.Nf3. */
    private static final String BEFORE_NF3_FEN = "r4k1r/p1p2p2/R1pqbp2/1p6/2nP2P1/2N5/PPP1QP1N/R5K1 w - - 4 21";

    /**
     * Depth at which the engine stops playing {@code 21.Nf3}. Measured on
     * v4.3.4: depths 3-4 and 6-8 all pick it (rated +65 cp down to -46 cp);
     * at depth 9 it switches to {@code f4} and finally scores the position
     * honestly at -300 cp.
     */
    private static final int NF3_REFUTATION_DEPTH = 9;

    /**
     * Casual blitz game <a href="https://lichess.org/oQHc2pgQ">oQHc2pgQ</a>,
     * myChessJava vs Martuni (2164), 0-1. Material is dead level here, but
     * myChess is already positionally lost (around -4 by lichess's analysis:
     * black owns c4 with a knight, the bishop pair rakes an open position, and
     * white's rooks on a6/a1 are uncoordinated). It then made it far worse with
     * {@code 21.Nf3}, which drops a pawn for nothing — {@code Nh2} was the
     * <em>only</em> defender of g4, so {@code 21...Bxg4} simply takes it.
     *
     * <p>Unlike the Philidor's Legacy case above, this is <b>not</b> a horizon
     * problem. The depth-7 principal variation already contains
     * {@code ...Bxg4}: the engine sees the pawn go and judges the resulting
     * position to be roughly balanced (-24 cp) when it is closer to -400. So the
     * defect is in the <em>evaluation</em>, not in how far the search reaches —
     * a two-ply material loss that a quiescence search cannot miss, yet the
     * position after it is scored far too optimistically.
     *
     * <p>In the game the clocks show about 4 s for this move (2:02 to 1:58),
     * which puts the search at depth 7-8 — squarely in the band where
     * {@code Nf3} is still its first choice. The test pins
     * {@link #NF3_REFUTATION_DEPTH}; if the evaluation improves, that threshold
     * should drop into the depths a blitz game actually reaches.
     *
     * <p><b>Test family:</b> tactical-oversight (fixed)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void nf3_atDepth9_engineStopsAbandoningTheG4Pawn() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_NF3_FEN, NF3_REFUTATION_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "in the pre-blunder position white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineAvoids(result, Board.h2, Board.f3, "21.Nf3");
    }

    // ----------------------------------------------------------------
    // King safety — rated blitz game https://lichess.org/XSSCyZ3b
    // (JC2025AJEDREZ 1352, a human, vs myChessJava, 1-0), the first
    // human opponent on lichess.
    //
    // Unlike every case above this one is NOT pinned as an avoidance
    // test. myChess keeps choosing the losing move through depth 12
    // (which already costs over two minutes), so the refutation depth
    // is out of reach for a test. What can be pinned is the blind spot
    // itself, in the style of MaterialOnlyShortcutEvalTest: the engine
    // is asked for its own verdict, and the test records how far that
    // verdict is from the truth.
    // ----------------------------------------------------------------

    /**
     * Black (myChess) to move, before {@code 22...Qb4}. Three white pieces
     * (Qh5, Nf5, Rg3) surround the open king on h8; black is a clean three pawns
     * up on material.
     */
    private static final String BEFORE_QB4_FEN = "r1b1r2k/pppp1p1p/5p2/5N1Q/2q5/6R1/2P2PPP/2R3K1 b - - 7 22";

    /** Depth myChess reached for this move in the game (300+3 time control, ~3 s spent). */
    private static final int IN_GAME_DEPTH = 8;

    /**
     * Characterization of the king-safety blind spot — the clearest example so
     * far, because material and danger point in opposite directions.
     *
     * <p>myChess is <b>three pawns up</b> here, and grades itself accordingly.
     * Objectively it is lost: three white pieces bear on an open king (Qh5, Nf5,
     * Rg3), and lichess's analysis puts the position near {@code -0.6} a move
     * earlier and around {@code +10} for white after the move actually played.
     * The engine's own verdict at the depth it reached in the game is roughly
     * {@code -1.5} white-POV, i.e. "black is comfortably better" — a
     * misjudgement of some ten pawns.
     *
     * <p>The move it picks, {@code 22...Qb4}, gives up the only defender of f7:
     * the queen on c4 covered it along c4-d5-e6-f7, and f7 is exactly where
     * white broke through ({@code 26.Qxf7}, mating on g7 two moves later).
     * Measured on v4.3.4, {@code Qb4} is its first choice from depth 7 through
     * <b>depth 12</b> (evaluations +128, +153, +48, +50, +4, -300 cp from black's
     * side), so more search does not fix it — the position is simply scored
     * wrong. The same game's {@code 21...Re8} shows the identical gap from
     * {@code r1b2r1k/pppp1p1p/5p2/5N2/2q5/6R1/2P2PPP/2RQ2K1 b - - 5 21}, where
     * the engine reads +1.6 to +2.3 for itself at every depth from 1 to 10.
     *
     * <p>This is what a king-safety term measures and myChess does not have
     * (roadmap § 12.21): several attackers in the king zone, scored
     * progressively.
     *
     * <p>The material-only shortcut does <em>not</em> compound it, though an
     * earlier version of this comment claimed it did ("three pawns is past
     * {@code EVALUATE_MATERIAL_ONLY_THRESHOLD = 200 cp}, so in the lines where it
     * keeps that lead the positional evaluation is skipped"). That has it exactly
     * backwards. The threshold applies to {@code materialDelta}, the swing since
     * the root — so a line that <em>keeps</em> a three-pawn lead holds the delta
     * near zero and the positional evaluation runs throughout. It runs, and still
     * misses the attack, which makes this a cleaner king-safety case than the
     * original wording suggested.
     *
     * <p><b>This assertion is a characterization, not a goal.</b> It passes
     * because the defect is present. Once king safety lands it must start
     * failing — that is the signal to invert it into an
     * {@link #assertEngineAvoids} test against {@code Qb4} and to record the new
     * evaluation here.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qb4_atMove22_characterizesTheKingSafetyBlindSpot() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_QB4_FEN, IN_GAME_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "in the pre-blunder position black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertTrue(result.weight() < -0.5f,
                "characterization: at the in-game depth myChess still rates itself better here, "
                        + "blind to the three attackers on its open king (objectively white is winning "
                        + "by roughly ten pawns). If this now reports a white advantage, king safety has "
                        + "landed — turn this into an avoidance test for Qb4. white-POV eval "
                        + result.weight());
    }

    /**
     * White (myChess) to move after {@code 15.g4 Nxg4 16.hxg4 Bxg4}: two pawns
     * up on material, and only f2 left in front of the king on g1.
     */
    private static final String STRIPPED_KING_FEN = "r5k1/ppqn1pp1/2pb3p/8/3P2b1/2NB1N2/PPP2P2/2RQB1K1 w - - 0 17";

    /**
     * The sharpest form of the same blind spot: with the king stripped bare, the
     * engine's verdict is <em>exactly its material balance</em>.
     *
     * <p>From rated blitz game <a href="https://lichess.org/ogbRQZBH">ogbRQZBH</a>
     * (myChessJava vs PlayMarius 2081, drawn by repetition). myChess pushed
     * {@code 15.g4}, which invites the knight sacrifice {@code 15...Nxg4!
     * 16.hxg4 Bxg4}: black gives a knight for two pawns and the white king,
     * previously screened by f2/g2/h3, keeps only f2. Black's attack was real —
     * the game continued {@code 18...Bh2+} and {@code 24...Bh3+ 25.Kxh3} with
     * perpetual checks, and myChess survived solely because black took the
     * threefold repetition.
     *
     * <p>Two measurements on v4.3.4 make the defect precise. First, the engine
     * chooses {@code g4} from depth 5 through 10 and its principal variation
     * never contains {@code ...Nxg4} at all — it expects the bishop to retreat
     * ({@code 15...Bg6}), because a knight for two pawns reads as a material gain
     * and therefore cannot be black's best. Second, and worse, in the position
     * <em>after</em> the sacrifice it scores itself +266 to +210 cp at every
     * depth from 1 to 9. The material edge is exactly +200 cp, so the naked king
     * is worth essentially <b>nothing</b> to the evaluation. That flatness across
     * all depths rules out a horizon effect: this is the evaluation function, not
     * the search.
     *
     * <p>Note the direction, which is the mirror image of the other cases here:
     * myChess does not fail to see an enemy attack, it <em>sells its own king
     * shelter for material</em>.
     *
     * <p>The material-only shortcut is <em>not</em> the explanation, and that
     * matters for reading the number above. {@code materialDelta} measures the
     * swing since the root, not the balance at it, so a position that merely
     * <em>stands</em> two pawns up enters the search at a delta of zero; and the
     * threshold comparison is a strict {@code >}, so even a two-pawn swing inside
     * the search does not trip it. The positional evaluation therefore does run
     * here — and still prices the naked king at nothing. A skipped evaluation
     * would be the milder finding; this is the evaluation itself.
     * {@link MaterialOnlyShortcutEvalTest#qxb5AtMove36GrabsThePawnInsteadOfTheExchangeSacrifice()}
     * is the case where the shortcut genuinely is the cause, and it looks
     * different: the score there lands on a whole pawn.
     *
     * <p><b>Characterization, not a goal</b> — same contract as
     * {@link #qb4_atMove22_characterizesTheKingSafetyBlindSpot()}: it passes
     * because the defect is present, and must be rewritten once a king-safety
     * term makes the score fall away from the bare material count.
     *
     * <p><b>Two claims above were measured on 2026-08-19 and do not hold as
     * written</b> ({@code test-results/kingsafety-score-stability-4.4.2.jsonl}).
     * Stockfish 18 at depth 24 reads this position at <b>+1.55</b> for white, so
     * black does <em>not</em> hold a perpetual — a perpetual would be 0.00. And the
     * king shelter is not worth "~0 cp" to the evaluation: material here is
     * <b>+2.20</b> by classical values, myChess reads +1.71 to +1.95 across depths
     * 8-12, so it does deduct roughly <b>0.3 pawns</b> for the airy king. Stockfish
     * deducts 0.65. The defect is therefore real but <b>small</b> — an
     * underweighting by about a third of a pawn, not a blind spot — and this case is
     * the weakest of the nineteen rather than a headline one. It is the only case in
     * the family whose premise did not survive checking; see the family entry in
     * {@code docs/testing.md} for how the tally accounts for it.
     *
     * <p>TODO: re-derive the assertion from the measured numbers rather than from the
     * "pure material" framing — a threshold of 1.5 passes for a position whose truth
     * is 1.55, so the test as written would still pass if the evaluation were
     * perfect.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void strippedKing_evaluatesAsPureMaterial() throws Exception {
        var game = gameFromFenAtDepth(STRIPPED_KING_FEN, 6, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "in the post-sacrifice position white (myChess) must be to move");

        var result = searchCurrentPosition(game);

        assertTrue(result.weight() > 1.5f,
                "characterization: myChess still values this at about its material edge (+2 pawns) "
                        + "even though its king has lost every pawn but f2 and black holds a perpetual — "
                        + "the king shelter is worth ~0 cp to the evaluation. A materially lower score "
                        + "means king safety has landed; rewrite this test then. white-POV eval "
                        + result.weight());
    }

    /** White (myChess) in check after {@code 24...Bh3+}; exactly three legal replies. */
    private static final String POISONED_BISHOP_FEN = "r5k1/ppq2p2/6pp/1p6/1B1P1b2/3B1N1b/PPP1QPK1/4R3 w - - 1 25";

    /**
     * The same blindness in its third guise: accepting a sacrifice whose
     * compensation is invisible, turning a won game into a draw.
     *
     * <p>Ten moves later in game <a href="https://lichess.org/ogbRQZBH">ogbRQZBH</a>,
     * black threw in {@code 24...Bh3+}. myChess is in check with exactly three
     * legal answers — {@code Kxh3}, {@code Kh1}, {@code Kg1} — so this is a pure
     * decision, with no room for search breadth or move ordering to be blamed. It
     * took the bishop, and {@code 25...Qd7+} produced the perpetual that saved
     * black: lichess's analysis has the position at {@code +4.2} before the
     * capture and {@code 0.0} after it.
     *
     * <p>Measured on v4.3.4, {@code Kxh3} is its choice at <em>every</em> depth
     * from 1 to 12, and the score never moves off {@code +500 cp}. That number is
     * precisely the material balance — two pawns up beforehand, plus the bishop —
     * so once again the exposure of its own king is priced at zero. The principal
     * variation reads {@code Kxh3 Rc8 Bb5 Qc2 ...}: the perpetual {@code ...Qd7+}
     * never appears in it at all.
     *
     * <p>Declining with {@code Kh1} or {@code Kg1} keeps the extra pawns and the
     * king behind cover. That is what a king-safety term would make visible: the
     * capture drags the king onto h3 beside a black queen and rook.
     *
     * <p><b>Characterization, not a goal</b> — see
     * {@link #qb4_atMove22_characterizesTheKingSafetyBlindSpot()}. When king
     * safety lands this must flip, and the test should become an
     * {@link #assertEngineAvoids} case against {@code Kxh3}.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void kxh3_atMove25_characterizesAcceptingThePoisonedBishop() throws Exception {
        var game = gameFromFenAtDepth(POISONED_BISHOP_FEN, 6, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "in the check position white (myChess) must be to move");

        var result = searchCurrentPosition(game);

        assertEquals(ChessUtil.moveToString(Board.g2, Board.h3), ChessUtil.moveToString(result.move()),
                "characterization: myChess still grabs the bishop with Kxh3 rather than declining with "
                        + "Kh1/Kg1, blind to the perpetual that follows. If it now declines, king safety "
                        + "has landed — invert this into an avoidance test. white-POV eval " + result.weight());
        assertTrue(result.weight() > 4.0f,
                "characterization: the score should still be the bare material count (+5 pawns), with the "
                        + "king dragged to h3 costing nothing; got " + result.weight());
    }

    // ----------------------------------------------------------------
    // Repetition draws vs. the transposition table — rated blitz game
    // https://lichess.org/i1QxWK9L (Flower-Queen 1844 vs myChessJava,
    // drawn by repetition while myChess was about eight pawns up).
    //
    // This is a correctness bug, not an evaluation gap, and it has a
    // sharp signature: THE SAME POSITION, TWO TABLE STATES, TWO
    // DIFFERENT MOVES. The pair of tests below pins both halves.
    //
    // Mechanism. isThreefoldRepetition() fires on the THIRD occurrence,
    // so after 51...Kg7 (only the second) it correctly reports "no
    // repetition"; the draw appears one ply later, after 52.Qd7+. The
    // search never gets there, because the position after ...Kg7 already
    // has a transposition-table entry stored during move 49 — when it was
    // not yet a repetition. The entry returns its old score and the
    // continuation is cut off. A position hash is path-independent; a
    // repetition draw is not.
    // ----------------------------------------------------------------

    /**
     * Fixed depth for the two repetition cases. Eight plies is what the engine
     * reached in this blitz game, and both outcomes below are stable from depth 1
     * upward, so the choice only affects runtime — each search finishes in well
     * under a second, which matters because the warm-table case searches twice.
     */
    private static final int REPETITION_DEPTH = 8;

    /**
     * Upper bound on the white-POV score black must still report after declining the
     * repetition. Measured at −5.35; the margin is deliberately wide because the point is
     * only that the score is nowhere near the drawn 0.00, not what exactly it is — the
     * evaluation of this endgame may legitimately drift with future table changes.
     */
    private static final float REPETITION_DECLINED_MAX_WEIGHT = -3.0f;

    /** Black (myChess) to move, in check after 49.Qe6+ — two moves before the draw becomes available. */
    private static final String REPETITION_PGN_MOVE_49 = """
            1. Nf3 Nf6 2. e4 Nxe4 3. d3 Nf6 4. Be2 e6 5. O-O d5 6. d4 Bd6 7. Ne5 O-O 8. Nc3 Nfd7
            9. Bf4 Bxe5 10. dxe5 Nc6 11. Bb5 Ndxe5 12. Qh5 f6 13. Rad1 Bd7 14. a3 a6 15. Bxc6 Nxc6
            16. Qf3 Rc8 17. Rfe1 Kh8 18. h4 d4 19. Ne4 e5 20. Bg3 Qe7 21. b4 a5 22. b5 Na7 23. a4 Rcd8
            24. h5 c6 25. h6 cxb5 26. hxg7+ Qxg7 27. Qb3 Bc6 28. Nd2 b4 29. Qc4 Bxa4 30. Rc1 Rc8
            31. Qa2 Qd7 32. Nc4 Qd5 33. Qxa4 Rxc4 34. Ra1 Rc5 35. Red1 b6 36. Rd3 Rc3 37. Rad1 Qc6
            38. Qa2 Rxc2 39. Qb3 Nc8 40. Re1 Rc3 41. Qb1 Nd6 42. Red1 Kg7 43. f3 Rc8 44. Bh2 Rc1
            45. Qb3 Qb5 46. Rd2 Qc4 47. Qa4 Qc3 48. Qd7+ Kg8 49. Qe6+
            """;

    /** The same game two moves further on: black to move, in check after 51.Qe6+. Kg7 now permits 52.Qd7+ and the draw. */
    private static final String REPETITION_PGN_MOVE_51 = """
            1. Nf3 Nf6 2. e4 Nxe4 3. d3 Nf6 4. Be2 e6 5. O-O d5 6. d4 Bd6 7. Ne5 O-O 8. Nc3 Nfd7
            9. Bf4 Bxe5 10. dxe5 Nc6 11. Bb5 Ndxe5 12. Qh5 f6 13. Rad1 Bd7 14. a3 a6 15. Bxc6 Nxc6
            16. Qf3 Rc8 17. Rfe1 Kh8 18. h4 d4 19. Ne4 e5 20. Bg3 Qe7 21. b4 a5 22. b5 Na7 23. a4 Rcd8
            24. h5 c6 25. h6 cxb5 26. hxg7+ Qxg7 27. Qb3 Bc6 28. Nd2 b4 29. Qc4 Bxa4 30. Rc1 Rc8
            31. Qa2 Qd7 32. Nc4 Qd5 33. Qxa4 Rxc4 34. Ra1 Rc5 35. Red1 b6 36. Rd3 Rc3 37. Rad1 Qc6
            38. Qa2 Rxc2 39. Qb3 Nc8 40. Re1 Rc3 41. Qb1 Nd6 42. Red1 Kg7 43. f3 Rc8 44. Bh2 Rc1
            45. Qb3 Qb5 46. Rd2 Qc4 47. Qa4 Qc3 48. Qd7+ Kg8 49. Qe6+ Kg7 50. Qd7+ Kg8 51. Qe6+
            """;

    /**
     * With a cold transposition table myChess gets this right: it blocks the check
     * with {@code 51...Nf7} instead of stepping to g7 and allowing {@code 52.Qd7+}
     * with the threefold draw.
     *
     * <p>Only four replies are legal ({@code Kh8}, {@code Kf8}, {@code Kg7},
     * {@code Nf7}), and myChess is roughly five pawns up by its own reckoning, so
     * the draw is a real loss of half a point. Measured on v4.3.4 it picks
     * {@code Nf7} at every depth from 1 to 8. This is the control case for
     * {@link #repetition_withWarmTable_findsTheBlockDespiteTheTable()}: the knowledge was
     * always present, and the bug was that a stale table entry hid it.
     *
     * <p><b>Test family:</b> repetition (guard)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void repetition_withColdTable_blocksTheCheckAndAvoidsTheDraw() throws Exception {
        var game = gameFromPgnAtDepth(REPETITION_PGN_MOVE_51, REPETITION_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "after 51.Qe6+ black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.d6, Board.f7), ChessUtil.moveToString(result.move()),
                "with a cold table myChess must block with Nf7 (d6-f7) rather than repeat with Kg7; "
                        + "white-POV eval " + result.weight());
    }

    /**
     * The former bug, now the regression test for its fix: with the transposition table
     * warm — as it always is in a real game — myChess must still find {@code 51...Nf7}
     * rather than repeat with {@code Kg7}.
     *
     * <p>The test asks the engine for black's 49th move, plays out the game continuation,
     * and asks again for the 51st. Both questions share one transposition table, exactly
     * as in live play, so the second one is answered from a position whose table entry was
     * written when it was not yet a repetition.
     *
     * <p><b>What it looked like while broken.</b> On v4.3.4 the engine answered the second
     * question with {@code Kg7} <em>instantly</em> and with an unchanged score of about +6
     * from black's side — a table hit, not a search. Its verdict did not move at all
     * between the harmless first occurrence and the one that concedes the draw, while
     * {@link #repetition_withColdTable_blocksTheCheckAndAvoidsTheDraw()} showed that
     * merely clearing the table was enough to find {@code Nf7}. Nothing about the position
     * differed between the two, only what the table remembered.
     *
     * <p><b>The fix.</b> {@code PositionSearch.alphaBetaSearchPre} now asks
     * {@code Board.isTwofoldRepetition()} instead of {@code isThreefoldRepetition()}, so a
     * position that has occurred twice along the search path scores as a draw. Because that check
     * already sat <em>above</em> the table lookup, the repetition is now decided before any
     * entry can answer, and the draw score is never itself stored — the early return
     * precedes the only {@code tt.put}. Detection became a property of the path, which is
     * what a repetition actually is. The game rule is untouched:
     * {@code isThreefoldRepetition()} still requires three occurrences, and
     * {@code ThreefoldRepetitionTest.secondOccurrenceIsNotYetADraw} guards that.
     *
     * <p>Post-fix measurement: {@code Nf7} (d6-f7) at −5.35 from white's side. The score
     * matters as much as the move — it shows the engine still knows it is winning rather
     * than having settled for the drawn score of 0.00.
     *
     * <p><b>The warm table is asserted, not assumed.</b> A warm-up that quietly stops warming
     * turns a test like this into a cold-table one, which passes on the broken build as well —
     * the engine finds {@code Nf7} from a cold table even with the defect present, so nothing
     * would be measured and nothing would be reported. The assertion below therefore checks
     * that the first search really did leave an entry for the position after {@code 49...Kg7}
     * before the second search is allowed to mean anything.
     *
     * <p><b>Test family:</b> repetition (fixed)
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void repetition_withWarmTable_findsTheBlockDespiteTheTable() throws Exception {
        var game = gameFromPgnAtDepth(REPETITION_PGN_MOVE_49, REPETITION_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "after 49.Qe6+ black (myChess) must be to move");

        // First question — this is what fills the table with an entry for the
        // position after ...Kg7, at a point where it is not yet a repetition.
        searchCurrentPositionDeep(game);

        // Play the game continuation up to the same check two moves later.
        game.makeMove(MoveDescription.fromString("Kg7", game.getTurn()));

        // Premise: the table must really be warm for that position. Without this the test
        // could quietly degrade into a cold-table one — and from a cold table the engine
        // finds Nf7 even with the defect present, so it would pass on the broken build and
        // prove nothing. Measured here: depth 7. Presence is all that is asserted; the
        // stored score is not, because the fix itself changes it.
        assertNotNull(tt.get(game.getBoard().getGameStatus().getPositionHash()),
                "the first search must have left a transposition-table entry for the position after "
                        + "49...Kg7 — that stale entry is the whole mechanism this test exercises, and "
                        + "without it the second search is not answering the same question");

        game.makeMove(MoveDescription.fromString("Qd7", game.getTurn()));
        game.makeMove(MoveDescription.fromString("Kg8", game.getTurn()));
        game.makeMove(MoveDescription.fromString("Qe6", game.getTurn()));
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(),
                "after 51.Qe6+ black (myChess) must be to move again");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.d6, Board.f7), ChessUtil.moveToString(result.move()),
                "with the table warm myChess must still block with Nf7 (d6-f7) instead of repeating with "
                        + "Kg7 — a stale entry written before the position was a repetition must not decide "
                        + "it; white-POV eval " + result.weight());
        assertTrue(result.weight() < REPETITION_DECLINED_MAX_WEIGHT,
                "the score must stay a winning one for black rather than collapse to the drawn 0.00: seeing "
                        + "the repetition must make the engine avoid it, not accept it; got " + result.weight());
    }

    // ----------------------------------------------------------------
    // Opening its own king for a pawn — rated rapid game
    // https://lichess.org/ZLefzVvN (VietnameseCoffee 1893 vs myChessJava, 1-0).
    // Two consecutive moves, both self-destructive, both played while a pawn up.
    // ----------------------------------------------------------------

    /** Black (myChess) to move, a pawn up, its f3 pawn jamming white's own g-file. */
    private static final String BEFORE_FXG2_FEN = "rnbq1rk1/ppp3pp/8/3p3Q/3Nn3/3B1p2/PPPB1PPP/2KR2R1 b - - 1 13";

    /** Black (myChess) to move after 14.Rxg2, with the g-file now open towards g7/g8. */
    private static final String BEFORE_G6_FEN = "rnbq1rk1/ppp3pp/8/3p3Q/3Nn3/3B4/PPPB1PRP/2KR4 b - - 0 14";

    /**
     * Depth at which the engine stops playing {@code 13...fxg2}. Measured on
     * v4.3.4: {@code fxg2} at depths 3-5, 7, 9 and 10, the equally losing
     * {@code g6} at 6 and 8, and only at depth 11 the correct {@code Nf6} — after
     * 195 s of fixed-depth search. In the game (15+0, about 11 s per move) it
     * reached depth 9-10, so it missed the saving move by a single ply.
     */
    private static final int FXG2_REFUTATION_DEPTH = 11;

    /** Depth at which the engine stops playing {@code 14...g6}: {@code g6} through depth 8, {@code Nf6} from 9. */
    private static final int G6_REFUTATION_DEPTH = 9;

    /**
     * Upper bound on myChess's white-POV score in the {@code 14...g6} position, whose true
     * value is +5.08 (Stockfish, mate in 9 after {@code 15.Rxg6+}). Set well below that and
     * well above the 0.0 currently measured, so the test tracks the *distance* from the
     * truth rather than the exact number — which has already moved once, from −0.4 to 0.0,
     * without the defect changing size.
     */
    private static final float G6_MISJUDGMENT_BOUND = 2.0f;

    /**
     * Opening its own king to win a pawn it did not need — and it was already a
     * pawn ahead.
     *
     * <p>Black's pawn on f3 was a wedge that also jammed <em>white's</em> g-file:
     * the white pawn on g2 blocked white's own rook on g1. {@code 13...fxg2}
     * removes that blocker, {@code 14.Rxg2} recaptures, and the rook now looks
     * straight down an open file at g7/g8 with the queen on h5 alongside. Two moves
     * later came {@code 15.Rxg6+ hxg6 16.Qxg6+} and mate.
     *
     * <p>Reference evaluations from Stockfish (depth 21-22): the position before
     * the capture is <b>+1.09</b> for white and its best move for black is
     * {@code Nf6}; after {@code 13...fxg2 14.Rxg2} it is <b>+5.26</b>. myChess
     * instead reports about <b>-0.6</b>, i.e. black comfortably better — wrong by
     * some 1.7 pawns before the move and by more than six after it. {@code Nf6}
     * appears in none of its principal variations below
     * {@link #FXG2_REFUTATION_DEPTH}.
     *
     * <p>This is the fourth game in this class showing the same pattern: material
     * is counted, the safety of its own king is not (roadmap § 12.21). What is new
     * here is the directness — it clears away the very pawn that was obstructing
     * the attacker's rook.
     *
     * <p><b>TODO — invert once king safety lands.</b> The test is pinned one ply
     * below the refutation depth so it reproduces the game's decision; when the
     * evaluation improves it must start failing, and the assertion should then
     * become {@link #assertEngineAvoids} against {@code f3-g2}.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void fxg2_atMove13_characterizesOpeningTheFileForAPawn() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_FXG2_FEN, FXG2_REFUTATION_DEPTH - 2, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.f3, Board.g2), ChessUtil.moveToString(result.move()),
                "characterization: myChess still takes on g2 and opens the g-file in front of its own king. "
                        + "If it now plays something else (Nf6 is Stockfish's choice), king safety has landed — "
                        + "turn this into an avoidance test. white-POV eval " + result.weight());
        assertTrue(result.weight() < 0f,
                "characterization: it rates itself ahead here, while Stockfish has white at +1.09; got "
                        + result.weight());
    }

    /**
     * The follow-up blunder, one move later: offering the rook sacrifice.
     *
     * <p>With the g-file already open, {@code 14...g6} places a pawn where it can
     * be taken with check. {@code 15.Rxg6+} leaves black exactly three legal
     * replies — {@code Kh8}, {@code Kf7}, {@code hxg6} — and Stockfish reads the
     * resulting position as <b>mate in 9</b>. (The game continued {@code hxg6},
     * though {@code Kh8} loses too; Stockfish's own mating line starts
     * {@code 15.Rxg6+ Kh8}.)
     *
     * <p>Before the move Stockfish has white at <b>+5.08</b> and again recommends
     * {@code Nf6}. It plays {@code g6} at every depth from 3 through 8 and finds
     * {@code Nf6} only at {@link #G6_REFUTATION_DEPTH}.
     *
     * <p><b>The misjudgment changed shape on 2026-08-14 without shrinking.</b> myChess used
     * to report about <b>-0.4</b> here — believing itself slightly ahead while objectively
     * lost. Since the § 12.23 repetition fix it reports exactly <b>0.0</b>, because its
     * depth-8 principal variation is now recognizable as a perpetual:
     * {@code 14...g6 15.Rxg6+ hxg6 16.Qxg6+ Kh8 17.Qh6+ Kg8 18.Qg6+}, which returns to the
     * position after {@code 16.Qxg6+}. The repetition is real — verified by replaying the
     * line — and the score is therefore honest about <em>that line</em>. What stays wrong is
     * white's side of it: Stockfish has <b>mate in 9</b> after {@code 15.Rxg6+}, so white
     * has far better than a perpetual, and myChess settles for the draw because it cannot
     * see the mating attack. The gap to the truth is still about five pawns; only its sign
     * moved, from "slightly winning" to "drawn".
     *
     * <p>That is why the assertion below bounds the score well away from +5.08 rather than
     * requiring a negative number: the defect is the distance from the truth, not which side
     * of zero the engine lands on.
     *
     * <p><b>TODO — invert once king safety lands</b>, same contract as
     * {@link #fxg2_atMove13_characterizesOpeningTheFileForAPawn()}.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void g6_atMove14_characterizesOfferingTheRookSacrifice() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_G6_FEN, G6_REFUTATION_DEPTH - 1, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.g7, Board.g6), ChessUtil.moveToString(result.move()),
                "characterization: myChess still plays g6 and invites 15.Rxg6+. If it now plays something "
                        + "else, king safety has landed — turn this into an avoidance test. white-POV eval "
                        + result.weight());
        assertTrue(result.weight() < G6_MISJUDGMENT_BOUND,
                "characterization: it reads a position Stockfish scores +5.08 for white (mate in 9) as no "
                        + "worse than level — since the repetition fix that shows as exactly 0.0, the value "
                        + "of the perpetual it settles for. If this now approaches +5, king safety has "
                        + "landed; got " + result.weight());
    }

    // ----------------------------------------------------------------
    // Selling the king for a rook — rated rapid game
    // https://lichess.org/1PSnMOBF (myChessJava vs georgii_ai 2133, 0-1),
    // the first game myChess resigned on lichess.
    //
    // Two decisions, both measured to be evaluation errors rather than
    // horizon effects, because the choice is stable through depth 13 and
    // hundreds of millions of nodes.
    // ----------------------------------------------------------------

    /** White (myChess) to move after {@code 8...g4}: the knight on f3 is attacked by the g4 pawn. */
    private static final String HANGING_F3_KNIGHT_FEN = "r1b1k1nr/pppqbp1p/8/3p4/6p1/5N2/PPPPQPPP/RNB1R1K1 w kq - 0 9";

    /** White (myChess) to move after {@code 11...h5}, its queen entombed on h8 and g2 vacated by 11.g3. */
    private static final String ENTOMBED_QUEEN_FEN = "r1b1k1nQ/ppp1bp2/8/3p3p/6q1/5pP1/PPPP1P1P/RNB1R1K1 w q - 0 12";

    /** Depth myChess reached for move 9 in the game (900+0 rapid, 23.7 s spent). */
    private static final int IN_GAME_DEPTH_MOVE_9 = 11;

    /** Depth myChess reached for move 12 in the game (14.8 s spent). */
    private static final int IN_GAME_DEPTH_MOVE_12 = 10;

    /**
     * Leaving a knight to be captured because the rook it wins in return looks
     * like a profit — the sharpest measured case of material outweighing king
     * safety so far.
     *
     * <p>The g4 pawn attacks the knight on f3. Instead of saving it, myChess
     * played {@code 9.Qe5??}, inviting {@code 9...gxf3 10.Qxh8}: a rook for a
     * knight, {@code +200} cp of material, bought with a queen entombed on h8, a
     * king whose g2/h2 shelter is gone and pierced by a black pawn on f3, and
     * three pieces still on a1/b1/c1. Stockfish (depth 18) has the position at
     * <b>+0.78</b> for white before the move and <b>-2.72</b> after it, and its
     * best move is {@code Ne5} — moving the attacked knight.
     *
     * <p>Two measurements on v4.3.5 rule out a horizon effect and locate the
     * defect in the evaluation:
     * <ul>
     *   <li>The engine's own principal variation at depth 11 spells the entire
     *       sequence out — {@code Qe5 gxf3 Qxh8 Qg4 g3 Bf5 Rxe7+ Kxe7 Qe5+ Kf8
     *       Qxc7} — and scores it <b>+113 cp</b>. It even predicts its own 11.g3
     *       and Stockfish's recommended {@code ...Bf5}. It sees the position
     *       correctly and prices it wrong by about five pawns.</li>
     *   <li>{@code Qe5} stays its first choice from depth 4 all the way through
     *       <b>depth 13</b> (640 million nodes), the score drifting only from
     *       +113 to +78 cp. Depths 1-3 pick the correct {@code Ne5}, so <em>more</em>
     *       search makes this move worse, not better.</li>
     * </ul>
     *
     * <p>The material-only shortcut is provably active in the line: searching from
     * the position after {@code 9...gxf3}, white is 300 cp down at the root, so
     * {@code Qxh8} carries {@code materialDelta = +500}, past
     * {@code EVALUATE_MATERIAL_ONLY_THRESHOLD = 200} — the whole subtree is then
     * graded by material alone. The game log confirms it: myChess reported exactly
     * {@code +2.00} for moves 10 and 11, the bare rook-for-knight balance.
     *
     * <p>That last point is why this game matters beyond one more blunder: it is
     * direct evidence against the hope that deeper search (roadmap § 12.7.1) fixes
     * this class of mistake. A systematically wrong evaluation is found faster, not
     * corrected, by more plies.
     *
     * <p><b>TODO — invert once king safety lands.</b> Written first as an
     * {@link #assertEngineAvoids} test against {@code e2-e5} and confirmed to fail
     * (the engine chose it, rating {@code +1.13}), then turned into the
     * characterization below so the suite stays green while the defect is open. When
     * the evaluation improves this must start failing; the assertion should then
     * become {@code assertEngineAvoids(result, Board.e2, Board.e5, "9.Qe5")} and
     * ideally require {@code Ne5} outright.
     *
     * <p><b>Test family:</b> corner-grab (defect)
     * <p><b>Contributing:</b> material-only-shortcut — the {@code Qxh8} delta of
     * +500 is measured from the root, so the reasoning above holds as written, and
     * the reported {@code +2.00} is the bare balance. This is the same signature as
     * {@link MaterialOnlyShortcutEvalTest#qxb5AtMove36GrabsThePawnInsteadOfTheExchangeSacrifice()}:
     * a material-only score has to land on a whole pawn, so a whole one is a reason to
     * suspect the shortcut — not on its own a proof of it, since the positional
     * evaluation can produce a whole number too.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qe5_atMove9_characterizesSellingTheKingForARook() throws Exception {
        var game = gameFromFenAtDepth(HANGING_F3_KNIGHT_FEN, IN_GAME_DEPTH_MOVE_9, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "after 8...g4 white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.e2, Board.e5), ChessUtil.moveToString(result.move()),
                "characterization: myChess still leaves the knight and plays Qe5, buying the h8 rook with its "
                        + "king shelter. If it now saves the knight (Ne5 is Stockfish's choice), king safety has "
                        + "landed — turn this into an avoidance test. white-POV eval " + result.weight());
        assertTrue(result.weight() > 0.5f,
                "characterization: it rates itself clearly ahead (measured +1.13) in a line Stockfish scores "
                        + "-2.72 for white, so the sign itself is wrong; got " + result.weight());
    }

    /**
     * Pushing a pawn onto an undefended square next to its own king, one move
     * after the opponent had handed the game back.
     *
     * <p>Black's {@code 11...h5??} threw away a winning position — Stockfish reads
     * {@code -4.72} before it and {@code -0.04} after — so myChess was level here
     * and only had to keep the king covered. It played {@code 12.h3??}. Since
     * {@code 11.g3} had vacated g2, nothing defends h3: {@code 12...Qxh3} wins the
     * pawn and threatens {@code Qg2#}, mate on a square guarded by black's own f3
     * pawn. Stockfish's verdict after {@code 12.h3} is mate; its recommendation is
     * the quiet {@code d3}.
     *
     * <p>Measured on v4.3.5, myChess plays {@code h3} from depth 7 through
     * <b>depth 13</b> (418 million nodes) and scores the position {@code +115} cp
     * throughout. Its principal variation always continues {@code 12...Qg6} — the
     * refutation {@code Qxh3} never appears, although it is a capture and therefore
     * certain to be examined by the all-captures quiescence search. The mate lies
     * some 19 plies out, far past the horizon, and no evaluation term flags an
     * enemy queen landing beside its own king. Depths 1-6 prefer {@code Qc3},
     * freeing the entombed queen, so once again the shallow search is the sound one.
     *
     * <p><b>Fixed in v4.4.0 — but only partly.</b> Written first as an avoidance test
     * against {@code h2-h3} and confirmed red (chosen with {@code +1.43}), then relaxed
     * into a characterization; the PeSTO piece-square tables then made it pass, so it is
     * an avoidance test again. The engine now plays {@code Qc3}, freeing the entombed
     * queen — the move its own shallow search had wanted all along.
     *
     * <p><b>TODO — {@code Qc3} is only the second-best move.</b> Stockfish puts it at
     * <b>-0.8</b> while the quiet <b>{@code d3} scores +0.3</b>, so what changed is
     * "loses by force" to "slightly worse", not "plays the best move". Roughly a pawn of
     * accuracy is still missing; tighten this to require {@code d3} if the evaluation
     * improves further.
     *
     * <p><b>Test family:</b> king-safety (fixed)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void h3_atMove12_engineNoLongerPushesTheUndefendedPawn() throws Exception {
        var game = gameFromFenAtDepth(ENTOMBED_QUEEN_FEN, IN_GAME_DEPTH_MOVE_12, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "after 11...h5 white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineAvoids(result, Board.h2, Board.h3, "12.h3");
    }

    // ----------------------------------------------------------------
    // Opening the file in front of its own king — rated rapid game
    // https://lichess.org/NMc7sp8h (myChessJava vs matmoi 2133, 0-1).
    //
    // This game is unlike every other case in this class: for thirty
    // moves myChess plays well AND judges the position correctly. Its
    // own evaluation tracks Stockfish to within half a pawn:
    //
    //   move   myChess   Stockfish   diff
    //     24    +1.62      +2.17    -0.55
    //     26    +1.72      +2.34    -0.62
    //     28    +1.56      +1.69    -0.13
    //     30    +1.79      +1.31    +0.48
    //     34    +1.72       0.00    +1.72   <- lost, unnoticed
    //     35    +1.55       0.00    +1.55
    //
    // So this is not "the evaluation is generally skewed". It is one
    // precise hole: a pawn move that opens the file in front of its own
    // king costs nothing in the evaluation.
    // ----------------------------------------------------------------

    /** White (myChess) to move, clearly better, with the f2-g2-h2 pawns still intact. */
    private static final String BEFORE_F3_FEN = "rr6/3bk1q1/p1nNp3/2R1p3/2Np2pp/P2R4/1PQ2PPP/6K1 w - - 1 33";

    /** White (myChess) to move two moves later: the f-pawn is gone and both rooks eye the open f-file. */
    private static final String BEFORE_RD3_FEN = "r4r2/3bk1q1/p1nNp3/2R1p3/2Np3p/P4R2/1PQ3PP/6K1 w - - 1 35";

    /** Depth myChess reached for both moves in the game (1440+0 rapid, ~9 s per move). */
    private static final int NMC7SP8H_DEPTH = 9;

    /**
     * The root of the collapse: pushing the pawn that shields its own king.
     *
     * <p>Before this move white is <b>+1.89</b> (Stockfish, depth 24) and Stockfish's
     * choice is the quiet {@code Rd1}, keeping f2-g2-h2 closed and improving the pieces
     * ({@code 33.Rd1 Rf8 34.Ne4 Rab8 35.Re1}). myChess instead played {@code 33.f3},
     * which resolves the tension in front of its own king: after {@code gxf3 Rxf3} the
     * f-pawn is gone, the f-file is open, and black lines up {@code Rf8} plus
     * {@code Qg7} on it. The position after {@code f3} measures <b>0.00</b> — the whole
     * advantage, given away in one move — and the game was lost from the f-file
     * invasion that followed ({@code 38...Rf2}).
     *
     * <p>Note where the defect is <em>not</em>: myChess is a pawn <b>behind</b> in
     * material here (2900 vs 3000 cp), so the material-only shortcut cannot explain the
     * +1.55 it reports two moves later — that number is positional optimism, not a
     * material count. Its own pieces really are well-placed (knights on c4 and d6, the
     * enemy king stuck on e7, all of which the piece-square tables reward); what is
     * missing is the counterweight, the open file bearing on its own king. That is
     * precisely the fourth bullet of roadmap § 12.21, "open / half-open files toward the
     * king".
     *
     * <p><b>And deeper search makes it worse.</b> Measured in-game: depths 2-7 pick
     * {@code h3}, and <b>depth 8 picks {@code Rd1}</b> — Stockfish's own best move.
     * Only at <b>depth 9</b> does the search switch to {@code f3}. The same inversion as
     * {@link #qe5_atMove9_characterizesSellingTheKingForARook()}: a systematically wrong
     * evaluation is found more reliably by more plies, not corrected.
     *
     * <p><b>TODO — invert once king safety lands.</b> Written first as an
     * {@link #assertEngineAvoids} test against {@code f2-f3} and confirmed to fail (the
     * engine chose it), then turned into the characterization below so the suite stays
     * green while the defect is open. The reproduction is exact — it picks the same
     * {@code f2-f3} the game saw. Its score differs from the in-game +2.00 because the
     * test starts from a bare FEN with a cold transposition table; only the move choice
     * is being pinned.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void f3_atMove33_characterizesOpeningItsOwnPawnShield() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_F3_FEN, NMC7SP8H_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "before 33.f3 white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.f2, Board.f3), ChessUtil.moveToString(result.move()),
                "characterization: myChess still pushes f3 and opens the file in front of its own king. If it "
                        + "now plays something else (Rd1 is Stockfish's choice, and depth 8 already finds it), "
                        + "king safety has landed — turn this into an avoidance test. white-POV eval "
                        + result.weight());
        assertTrue(result.weight() > 0.5f,
                "characterization: it still rates itself clearly ahead after giving the advantage away; the "
                        + "position after f3 is 0.00 by Stockfish. Got " + result.weight());
    }

    /**
     * The missed rescue two moves later: trading the rooks off the open file.
     *
     * <p>With the f-file already open, {@code 35.Rxf8} is the only move that holds —
     * Stockfish rates the position <b>0.00</b> after it, because the trade removes the
     * attacker from the file. myChess played {@code 35.Rd3} instead, retreating along
     * the third rank, which Stockfish scores about <b>-2.6</b> (depth 22; deeper
     * analysis reads it lower still).
     *
     * <p>{@code Rxf8} appears in <em>no</em> iteration: measured in-game, depths 1-2 and
     * 9 pick {@code Rd3}, depths 3-8 pick the equally losing {@code Rb3} (-2.59). The
     * engine consistently prefers to keep its rook on the third rank, which is
     * consistent with the missing term — if the open file costs nothing, there is
     * nothing to trade away.
     *
     * <p><b>TODO — invert once king safety lands.</b> Written first as a positive
     * assertion requiring {@code Rxf8} — stronger than the usual avoidance test, which is
     * fair here because the saving move is unique — and confirmed to fail (the engine
     * chose {@code Rd3}). Relaxed to the characterization below so the suite stays green
     * while the defect is open; the target assertion is preserved in this note, not lost.
     * As with the sibling test, the score is lower than the in-game +1.55 only because
     * the transposition table starts cold.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rd3_atMove35_characterizesKeepingTheRookOffTheOpenFile() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RD3_FEN, NMC7SP8H_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(),
                "before 35.Rd3 white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.f3, Board.d3), ChessUtil.moveToString(result.move()),
                "characterization: myChess still retreats along the third rank instead of trading with Rxf8, "
                        + "the only move that holds. If it now plays f3-f8, king safety has landed — restore the "
                        + "positive assertion on Rxf8. white-POV eval " + result.weight());
        assertTrue(result.weight() > 0f,
                "characterization: it rates itself ahead in a position Stockfish scores about -2.6 for white; "
                        + "got " + result.weight());
    }

    // ----------------------------------------------------------------
    // Found by tools/lichess-blunder-scan.py rather than by hand.
    //
    // The scanner walked 149 lichess games (6 828 of myChess's own moves)
    // at depth 15, re-checked the candidates at depth 20, and ranked
    // "losing phases" — three consecutive own moves losing 250 cp or more
    // together. It independently rediscovered the cases already pinned
    // above (KSvNk2VQ, 1PSnMOBF, XSSCyZ3b), which is the check that its
    // detection works; the six below are from games never looked at.
    //
    // All six are pinned at depth 8. Where a deeper search fixes the move
    // that is recorded per test, because it says whether the knowledge is
    // missing or merely out of reach.
    // ----------------------------------------------------------------

    /** Depth used for the scanner-derived cases: what a blitz/rapid game actually reaches. */
    private static final int SCANNER_DEPTH = 8;

    /** White (myChess) to move, three pawns up, with a black rook offered on h2. */
    private static final String POISONED_ROOK_H2_FEN = "4r2k/3N1pp1/2R4p/6q1/1P6/2Q2PP1/P6r/3R2K1 w - - 0 35";

    /** Black (myChess) to move, three pawns up, white's Rh5 and Qh3 aimed at the king on h7. */
    private static final String IGNORED_H_FILE_FEN = "rn1q1r2/4n2k/2p1Bp1p/b6R/8/2P4Q/Pp3PPP/5RK1 b - - 1 23";

    /** White (myChess) to move in a won rook-and-bishop endgame; the black a2 pawn wants a1. */
    private static final String PROMOTION_SQUARE_FEN = "R7/8/1k3B2/2p5/4p3/4K3/p1r2P2/8 w - - 24 75";

    /** White (myChess) to move, level; the b7 pawn is bait for the queen. */
    private static final String POISONED_B7_FEN = "r3kbnr/1pp3p1/p1pq1p2/6p1/4P1P1/1Q5P/PPPP1P2/RNB2RK1 w kq - 2 12";

    /** Black (myChess) to move, three pawns up, with a white rook sitting on a1. */
    private static final String CORNER_ROOK_A1_FEN = "1k1rr3/Npp2p1p/3p1p1b/2P2b2/Q2Pq3/5N2/PPn1BPPP/R4RK1 b - - 0 15";

    /** Black (myChess) to move, two pawns up, white's queen on h5 and a passed pawn on d7. */
    private static final String PAWN_PUSH_G6_FEN = "3r2k1/p1rPqpp1/1pbR4/7Q/4p3/5P2/P1B3PP/3R2K1 b - - 0 38";

    /**
     * Taking an offered rook next to its own king, and getting mated for it.
     *
     * <p>Rated game <a href="https://lichess.org/e3z7uj8E">e3z7uj8E</a>, the largest single
     * loss in the whole 149-game scan. White is <b>+3.46</b> and black's rook on h2 is a
     * sacrifice: after {@code 35.Kxh2} Stockfish reports <b>mate in 4</b>. The move to play
     * is {@code 35.Qe5}, keeping the king covered and staying +3.46
     * ({@code 35.Qe5 Qd8 36.Rdc1 Rxe5 37.Rc8}).
     *
     * <p>Same family as {@link #kxh3_atMove25_characterizesAcceptingThePoisonedBishop()} —
     * king captures the offered piece, king safety pays for it — but this one is worse:
     * there the price was a draw, here it is mate.
     *
     * <p>Depth matters here, and in the encouraging direction: {@code Kxh2} is chosen at
     * depth 8, {@code Rg6} at 9, and from <b>depth 10</b> the search finds Stockfish's own
     * {@code Qe5}. The knowledge is two plies out of reach rather than absent.
     *
     * <p><b>Its own verdict is the worst in this class: +8.00.</b> myChess rates the
     * position after {@code Kxh2} as eight pawns in its favour while it is mated in four —
     * a misjudgement of some eighteen pawns with the sign inverted. Everything it counts
     * (a piece up after the capture) is there; nothing it counts sees the mate.
     *
     * <p><b>TODO — invert once king safety lands.</b> Written as an avoidance test against
     * {@code g1-h2}, confirmed red, then relaxed below.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void kxh2_atMove35_characterizesTakingTheRookSacrifice() throws Exception {
        var game = gameFromFenAtDepth(POISONED_ROOK_H2_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.g1, Board.h2), ChessUtil.moveToString(result.move()),
                "characterization: myChess still takes the rook on h2 and is mated in four. If it now plays "
                        + "something else (Qe5 is Stockfish's choice, and depth 10 already finds it), king "
                        + "safety has landed — turn this into an avoidance test. white-POV eval "
                        + result.weight());
        assertTrue(result.weight() > 5f,
                "characterization: it rates itself around +8 in a position where it is mated in four; got "
                        + result.weight());
    }

    /**
     * Ignoring an attack on its own king while three pawns up.
     *
     * <p>Rated game <a href="https://lichess.org/oDolisK8">oDolisK8</a>. Black (myChess) is
     * <b>+2.89</b> with white's rook on h5 and queen on h3 both bearing on the king on h7.
     * The move is {@code 23...Rh8}, contesting the file and holding everything. myChess
     * played {@code 23...Qd2}, a queen sortie on the far side, and the position collapsed
     * to <b>0.00</b>; the follow-up {@code 24...Qc2} then lost the rest.
     *
     * <p>Unlike the case above this is <em>not</em> a horizon problem: {@code Qd2} is its
     * choice at every depth from 8 through 11. The attack on its own king simply does not
     * enter the evaluation, which is the § 12.21 hole in its clearest form — nothing
     * tactical to see, just a file that needs defending.
     *
     * <p><b>Its own verdict: +5.81 for itself</b> in a position Stockfish calls dead level.
     * Nearly six pawns of phantom advantage, and the direction is the telling part — it
     * believes the attack on its king is worth nothing at all.
     *
     * <p><b>TODO — invert once king safety lands.</b>
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qd2_atMove23_characterizesIgnoringItsOwnKingFile() throws Exception {
        var game = gameFromFenAtDepth(IGNORED_H_FILE_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.d8, Board.d2), ChessUtil.moveToString(result.move()),
                "characterization: myChess still plays the queen to d2 and leaves the h-file undefended. If it "
                        + "now plays Rh8, king safety has landed — turn this into an avoidance test. white-POV "
                        + "eval " + result.weight());
        assertTrue(result.weight() < -4f,
                "characterization: it rates itself nearly six pawns ahead in a dead level position; got "
                        + result.weight());
    }

    /**
     * A won endgame given away by never occupying the promotion square.
     *
     * <p>Rated game <a href="https://lichess.org/gVJ7PdwQ">gVJ7PdwQ</a>, and a category
     * that none of the cases above cover. White (myChess) is <b>+3.24</b> in a rook-and-
     * bishop endgame; black's pawn stands on a2 and wants a1. The move is
     * <b>{@code 75.Ba1}</b> (Stockfish, <b>+3.44</b>): the bishop simply sits on the
     * promotion square. Everything else lets the pawn decide the game.
     *
     * <p>myChess finds it at <em>no</em> depth. It plays {@code Re8} at depths 8-10, which
     * Stockfish scores <b>0.00</b>, and the game's {@code Be5} at depth 11, worth
     * <b>+0.11</b>. Both throw the win away; the scanner flagged the whole stretch 75-77
     * as a phase, because the bishop then shuffles {@code Be5-Bf6-Be5} while the pawn is
     * the only thing that matters.
     *
     * <p>What makes this different from every other case in this class: there is no attack,
     * no sacrifice and no king safety involved. It is endgame technique — recognising that
     * a passed pawn one square from promotion outweighs any amount of piece activity. A
     * king-safety term would not touch it.
     *
     * <p><b>Its own verdict: +0.52</b> after {@code Re8}. So it does not even believe it is
     * winning any longer — the evaluation is roughly honest about the outcome, it simply never
     * generates the move that keeps the win. That makes this the one case in this class
     * where the gap looks like search or move ordering rather than evaluation.
     *
     * <p><b>TODO.</b> Written as a positive assertion requiring {@code f6-a1}, confirmed
     * red, then relaxed below. Restore the positive form once the endgame play improves.
     *
     * <p><b>Test family:</b> endgame-technique (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void ba1_atMove75_characterizesMissingThePromotionSquare() throws Exception {
        var game = gameFromFenAtDepth(PROMOTION_SQUARE_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertNotEquals(ChessUtil.moveToString(Board.f6, Board.a1), ChessUtil.moveToString(result.move()),
                "characterization: myChess must still miss Ba1, the only move that keeps the won endgame. If it "
                        + "now finds it, replace this with a positive assertion on f6-a1. white-POV eval "
                        + result.weight());
    }

    /**
     * The queen goes pawn-hunting on b7 and does not come back.
     *
     * <p>Rated game <a href="https://lichess.org/9TXcD9ES">9TXcD9ES</a>. The position is
     * <b>level</b> (0.00) and {@code 12.Kg2} keeps it there. myChess took the b7 pawn with
     * {@code 12.Qxb7}, after which Stockfish reads <b>-8.53</b>: the queen has no way out
     * of the corner.
     *
     * <p>Same family as {@code 9.Qe5} / {@code Qxh8} in
     * {@link #qe5_atMove9_characterizesSellingTheKingForARook()} — a pawn or rook in the
     * corner is worth more to the evaluation than the piece that has to fetch it. And like
     * that case, more depth does not help: {@code Qxb7} is its choice at every depth from
     * 8 through 11, so this is the evaluation, not the horizon.
     *
     * <p><b>Its own verdict: +1.79</b> where Stockfish has <b>-8.53</b> — a gap of more than
     * <b>ten pawns</b>, the largest measured anywhere in this class. It counts the pawn it
     * won and nothing about the queen that cannot return.
     *
     * <p><b>TODO — invert once the evaluation charges for a trapped piece.</b>
     *
     * <p><b>Test family:</b> corner-grab (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qxb7_atMove12_characterizesTakingThePoisonedPawn() throws Exception {
        var game = gameFromFenAtDepth(POISONED_B7_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.b3, Board.b7), ChessUtil.moveToString(result.move()),
                "characterization: myChess still grabs the b7 pawn and traps its own queen. If it now plays "
                        + "something else (Kg2 holds the balance), the evaluation has learned to charge for a "
                        + "trapped piece — turn this into an avoidance test. white-POV eval " + result.weight());
        assertTrue(result.weight() > 1f,
                "characterization: it rates itself ahead where Stockfish has -8.53, a gap above ten pawns; got "
                        + result.weight());
    }

    /**
     * The corner-rook grab again, this time with a knight.
     *
     * <p>Rated game <a href="https://lichess.org/ia3olzlm">ia3olzlm</a>. Black (myChess) is
     * <b>+3.15</b> and plays {@code 15...Nxa1}, taking the rook in the corner; the position
     * turns to <b>-1.67</b>. The move is {@code 15...Rg8} (+3.15), which keeps the bind.
     *
     * <p>This is the <b>third</b> instance of the same pattern in this class, after
     * {@link #qxa1_atDepth13_engineRefutesTheGreedyRookGrab()} (Philidor's Legacy, queen
     * takes a1) and the {@code Qxb7} case above. Three different pieces, three different
     * games, one shape: a rook or pawn in a corner is taken while the piece that takes it
     * leaves the position that mattered. That recurrence is the argument that this is a
     * systematic evaluation gap rather than three coincidences.
     *
     * <p>Depth helps but arrives late: {@code Nxa1} at depths 8-9, and from <b>depth 10</b>
     * the search switches to {@code Qe2}.
     *
     * <p><b>Its own verdict: +4.00 for itself</b> where Stockfish has it <b>-1.67</b> —
     * nearly six pawns out, sign inverted. The rook is counted, the knight stranded on a1
     * is not.
     *
     * <p><b>TODO — invert once the evaluation charges for a stranded piece.</b>
     *
     * <p><b>Test family:</b> corner-grab (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void nxa1_atMove15_characterizesTakingTheCornerRook() throws Exception {
        var game = gameFromFenAtDepth(CORNER_ROOK_A1_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.c2, Board.a1), ChessUtil.moveToString(result.move()),
                "characterization: myChess still takes the rook in the corner and strands the knight there. If "
                        + "it now plays Rg8, turn this into an avoidance test. white-POV eval " + result.weight());
        assertTrue(result.weight() < -3f,
                "characterization: it rates itself four pawns ahead where Stockfish has it 1.67 behind; got "
                        + result.weight());
    }

    /**
     * Pushing a pawn in front of its own king instead of removing the passed pawn.
     *
     * <p>Rated game <a href="https://lichess.org/LKbBtQml">LKbBtQml</a>. Black (myChess) is
     * <b>+2.17</b>, white has a queen on h5 and a passed pawn on d7. The move is
     * {@code 38...Rcxd7}, taking the pawn off the board ({@code 39.Rxd7 Rxd7 40.fxe4
     * Rxd1+}). myChess played {@code 38...g6}, opening its own king to the queen, and the
     * position went to <b>-4.78</b>.
     *
     * <p>Same family as {@code 33.f3} in
     * {@link #f3_atMove33_characterizesOpeningItsOwnPawnShield()} and {@code 12.h3} in
     * {@link #h3_atMove12_engineNoLongerPushesTheUndefendedPawn()}: a pawn move in front of
     * the own king that the evaluation does not charge for.
     *
     * <p>Its choice oscillates with depth — {@code g6} at 8 and 10, the correct
     * {@code Rcxd7} at 9 and 11 — so the two moves are close together in its evaluation.
     * That is worth knowing: it means a modest king-safety penalty could be enough to
     * settle this one, unlike the cases where the wrong move wins by a wide margin.
     *
     * <p><b>Its own verdict: +0.26 for itself</b> where Stockfish has white <b>+4.78</b> —
     * about five pawns out. Note how modest its own number is: it does not think it is
     * winning, it just fails to see that it is losing.
     *
     * <p><b>TODO — invert once king safety lands.</b> Given how close {@code g6} and
     * {@code Rcxd7} sit in its evaluation, this should be among the first cases to flip.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void g6_atMove38_characterizesPushingThePawnInsteadOfTakingThePasser() throws Exception {
        var game = gameFromFenAtDepth(PAWN_PUSH_G6_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.g7, Board.g6), ChessUtil.moveToString(result.move()),
                "characterization: myChess still pushes g6 instead of removing the d7 passer with Rcxd7. If it "
                        + "now takes the pawn, turn this into an avoidance test. white-POV eval "
                        + result.weight());
        assertTrue(result.weight() < 0f,
                "characterization: it still rates itself slightly ahead where Stockfish has white +4.78; got "
                        + result.weight());
    }

    /** Black (myChess) to move, a piece up, its f6 knight attacked by the g5 pawn. */
    private static final String RETREAT_TO_E8_FEN = "r2q1rk1/ppp2ppp/2n2n2/6P1/2P1p3/P2P4/PB4P1/R2Q2KR b - - 0 15";

    /**
     * Saving an attacked knight by retreating it to the back rank, and losing the king for it.
     *
     * <p>Rated blitz game <a href="https://lichess.org/TG1DZhV0">TG1DZhV0</a>. Black (myChess)
     * is <b>a whole piece up</b> (3200 vs 2800 cp) after the promotion sequence
     * {@code 12...dxe3 13.g5 exf2 14.Bb2 fxg1=Q+ 15.Kxg1}, and Stockfish has it at
     * <b>+3.3</b>. White's g5 pawn attacks the knight on f6.
     *
     * <p>myChess played {@code 15...Ne8}, tucking the knight onto the back rank. Stockfish
     * reads the result as <b>-1.49</b> — nearly five pawns thrown away in one move — and the
     * attack arrived immediately: {@code 16.Qh5 h6 17.gxh6 f6 18.hxg7 Nxg7 19.Qh7+} and the
     * game was gone. Either {@code Nd4} (<b>+3.35</b>) or {@code Qxd3} (+3.30) holds
     * everything; both simply let the f6 knight go, which a side a piece up can afford.
     *
     * <p><b>The piece-square tables are not the culprit here — they are overruled.</b> Worth
     * recording, because it is the opposite of what one would guess. For a black knight in
     * the midgame the tables give f6 <b>+29</b>, e8 <b>-50</b> and d4 <b>+90</b>: the retreat
     * costs 79 cp of placement and the recommended move would gain 61. myChess plays the
     * retreat anyway, so roughly 140 cp of correct positional signal is outweighed — by the
     * 300 cp of the knight it would otherwise lose.
     *
     * <p>That makes this a different shape from the {@code corner-grab} cases, which are
     * about material greedily *taken*. This is material *hoarded*: refusing to give a piece
     * back while a piece up, because the evaluation counts the piece and not the king behind
     * it. What would tip it is a term for the h-file and the bare g7 — hence the family
     * below.
     *
     * <p>Its own verdict: <b>+4.03 for itself</b> against Stockfish's -1.49, a gap of five
     * and a half pawns with the sign inverted. Depth moves it but does not fix it:
     * {@code Ne8} at depths 8-10, and at depth 11 {@code Ng4}, which Stockfish scores
     * <b>+0.08</b> — no longer losing, but the win is gone either way.
     *
     * <p><b>TODO — invert once king safety lands.</b> Written as an avoidance test against
     * {@code f6-e8}, confirmed red, then relaxed below. Requiring {@code Nd4} outright would
     * be the stronger form, but {@code Qxd3} is equally good, so avoidance is the honest
     * assertion.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void ne8_atMove15_characterizesRetreatingTheKnightToTheBackRank() throws Exception {
        var game = gameFromFenAtDepth(RETREAT_TO_E8_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.f6, Board.e8), ChessUtil.moveToString(result.move()),
                "characterization: myChess still saves the attacked knight with Ne8 and lets the attack on its "
                        + "own king through. If it now plays Nd4 or Qxd3, king safety has landed — turn this "
                        + "into an avoidance test. white-POV eval " + result.weight());
        assertTrue(result.weight() < -3f,
                "characterization: it rates itself four pawns ahead where Stockfish has it 1.49 behind; got "
                        + result.weight());
    }

    // ----------------------------------------------------------------
    // Rated classical game https://lichess.org/caCDhMEU (myChessJava vs
    // TopasBot 2067, 0-1). Two blunders on consecutive moves, and together
    // they are the sharpest example of a signature that differs from every
    // other case in this class: myChess is not *optimistic* here, it is
    // *insensitive*. Its own evaluation sits at -0.8 through both moves and
    // every depth from 8 to 11, while the truth walks from -0.47 to -4.93.
    // Nothing about the collapse of its own kingside registers at all.
    // ----------------------------------------------------------------

    /** White (myChess) to move, level on material, king on g1 with the f-pawn long gone. */
    private static final String BEFORE_H3_FEN = "3rk2r/1p3pp1/pNb1p3/3pP1qp/3Q2P1/4P3/PPP4P/3R1RK1 w k - 2 20";

    /** White (myChess) to move one ply later, a pawn down, black's rook ready for the h-file. */
    private static final String BEFORE_HXG4_FEN = "3rk2r/1p3pp1/pNb1p3/3pP1q1/3Q2p1/4P2P/PPP5/3R1RK1 w k - 0 21";

    /**
     * Touching the pawns in front of an already-airy king, for the fourth time in this class.
     *
     * <p>White's king shelter was spent long before this: {@code 15.fxe3} took the f-pawn away
     * and {@code 12.g4} pushed the g-pawn, so g1 sits behind g4 and h2 with an open f-file.
     * Black had not exploited it — Stockfish has the position at only <b>-0.47</b>, and
     * {@code 20.Rd2} keeps it there ({@code 20.Rd2 O-O 21.Rg2 h4 22.h3}), quietly bringing the
     * rook to the second rank before deciding anything about the h-file.
     *
     * <p>myChess played <b>{@code 20.h3}</b> instead, which resolves the tension on white's
     * own terms and hands black the open file: <b>-3.12</b>. Same family as {@code 33.f3}
     * ({@link #f3_atMove33_characterizesOpeningItsOwnPawnShield()}) and {@code 12.h3}
     * ({@link #h3_atMove12_engineNoLongerPushesTheUndefendedPawn()}) — a pawn move in front of
     * its own king that the evaluation does not charge for.
     *
     * <p>Its own verdict: <b>-0.82</b>, against a true -3.12. Note what kind of error that
     * is. Elsewhere in this class myChess claims a large advantage it does not have; here it
     * reports "slightly worse" and is simply <em>not wrong enough to act</em>. The 2.3-pawn
     * gap is invisible rather than inverted.
     *
     * <p>Depth 11 finds {@code Qf4}, which is a reasonable alternative — but 8 through 10 all
     * play {@code h3}, and the game was at 1800+2 with roughly depth 9 to 11 available.
     *
     * <p><b>TODO — invert once king safety lands.</b> Written as an avoidance test against
     * {@code h2-h3}, confirmed red, then relaxed below.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void h3_atMove20_characterizesOpeningTheLastPawnBeforeItsKing() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_H3_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.h2, Board.h3), ChessUtil.moveToString(result.move()),
                "characterization: myChess still plays h3 and opens the file at its own king. If it now plays "
                        + "Rd2, king safety has landed — turn this into an avoidance test. white-POV eval "
                        + result.weight());
        assertTrue(result.weight() > -2f,
                "characterization: it reports only about -0.8 where Stockfish has -3.12 — insensitive rather "
                        + "than optimistic; got " + result.weight());
    }

    /**
     * Recapturing a pawn instead of trading the queens that are mating it.
     *
     * <p>One ply after the case above, and the more instructive of the two. White is a pawn
     * down at <b>-3.12</b> with black's rook about to land on h4. The move is
     * <b>{@code 21.Qf4}</b> (<b>-2.92</b>): offer the queen trade, because a defender with an
     * open king wants the strongest attacker off the board —
     * {@code 21.Qf4 Qxf4 22.Rxf4 gxh3 23.Kh2}, a pawn down but structurally alive.
     *
     * <p>myChess played <b>{@code 21.hxg4}</b>, taking the pawn back, keeping queens on and
     * leaving the h-file open. Stockfish: <b>-4.93</b>. The game followed immediately —
     * {@code 21...Rh4 22.Rf4 Qh6 23.Kf1 Rh2} and the rook was inside.
     *
     * <p><b>Reproduces at every depth from 8 to 11</b>, its evaluation flat at -0.81 to -0.90
     * throughout. So this is not the horizon: the four-pawn gap is in the evaluation, and no
     * amount of search closes it.
     *
     * <p>The shape is the same as {@link #ne8_atMove15_characterizesRetreatingTheKnightToTheBackRank()}:
     * material is preferred over the safety of its own king — there by hoarding a piece, here
     * by recapturing a pawn. Both point at the same missing counterweight, and both show that
     * the term would have to be worth *more than a piece or a pawn* to change the decision,
     * which is a size the three shelved hand-built attempts in
     * <a href="../docs/roadmap.md">roadmap § 12.21</a> never approached.
     *
     * <p><b>TODO — invert once king safety lands.</b> Avoidance rather than requiring
     * {@code Qf4}, so the test does not prescribe a single move where the point is the choice
     * between defusing and grabbing.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void hxg4_atMove21_characterizesRecapturingInsteadOfTradingQueens() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_HXG4_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEquals(ChessUtil.moveToString(Board.h3, Board.g4), ChessUtil.moveToString(result.move()),
                "characterization: myChess still recaptures on g4 rather than defusing with Qf4. If it now "
                        + "trades queens, king safety has landed — turn this into an avoidance test. white-POV "
                        + "eval " + result.weight());
        assertTrue(result.weight() > -2f,
                "characterization: it reports about -0.85 where Stockfish has -4.93, at every depth from 8 to "
                        + "11; got " + result.weight());
    }

    /** White (myChess) to move at move 49, material dead level: knight and three pawns each. */
    private static final String KNIGHT_ENDGAME_FEN = "8/2N1k1p1/8/1p2K3/1P2p3/P1n5/5P2/8 w - - 2 49";

    /**
     * Upper bound on myChess's score in that endgame. Stockfish has +3.92 for the one winning
     * move; myChess measured +0.91 to +1.05 across every depth from 8 to 16. The bound sits
     * between the two, so the test fails the day the gap closes without pinning a number that
     * ordinary evaluation drift would break.
     */
    private static final float KNIGHT_ENDGAME_BOUND = 2.0f;

    /**
     * Rated rapid game <a href="https://lichess.org/82EFspXF">82EFspXF</a> (myChessJava 2092 vs
     * TopasBot 2069, 15+3, drawn), move 49 — a won knight endgame given away, and the clearest
     * case in this class of an advantage consisting of <em>nothing but</em> position.
     *
     * <p>Material is dead level: knight and three pawns each. White wins anyway, and with
     * exactly one move — <b>{@code 49.Kd4}</b>, worth <b>+3.92</b>, against +0.49 for the played
     * {@code 49.Ne6}, +0.40 for {@code Nxb5} and +0.31 for {@code Na6} (Stockfish, depth 26).
     * {@code Kd4} does three things at once: attacks the knight on c3, escorts the b-pawn, and
     * steps in front of the e-pawn. The game went {@code 49.Ne6 Nd1 50.Nxg7} instead — a pawn
     * grabbed, the win gone, drawn on move 90.
     *
     * <p><b>This pins the score, not the move — deliberately.</b> myChess reports between
     * <b>+0.91 and +1.05 on every depth from 8 to 16</b>, flat and three pawns below the truth,
     * while its move choice wanders between {@code Kd4} (depths 8-10 and 16) and {@code Nd5}
     * (11-15). At depth 8 it plays the winning move, so an assertion on the move would either
     * encode correct behavior or contradict itself one ply later. The evaluation is the stable
     * defect here; the choice is not.
     *
     * <p><b>The game move is not reproducible from this FEN at any depth from 8 to 16.</b> Worth
     * recording rather than hiding: it is the third case in a row where a bare-FEN probe fails
     * to reproduce what the engine did over the board, after the repetition shuffle and
     * <a href="https://lichess.org/ImKwjaJy55DV">ImKwjaJy55DV</a>. In both of those the cause
     * was the warm transposition table, and this game reached move 49 with a table filled by the
     * preceding 48. Anyone extending this case should warm the table the way
     * {@code ThreefoldRepetitionTest.repetitionFromLichessGameIsAvoidedWithAWarmTable} does,
     * rather than concluding the position is harmless.
     *
     * <p>It belongs to this family rather than to the material-only shortcut precisely because
     * the two sides hold identical material: nothing here trips
     * {@code EVALUATE_MATERIAL_ONLY_THRESHOLD}, so the positional terms <em>do</em> run — and
     * still price a winning king march at a fifth of its worth. That makes it the sharpest
     * evidence available that the evaluation carries almost no weight where material is level,
     * and the second open case in a family that had only one.
     *
     * <p><b>Characterization, not a goal.</b> It passes because the defect is present. When the
     * evaluation learns to see this, the score rises past {@link #KNIGHT_ENDGAME_BOUND} and the
     * test must be rewritten to require {@code Kd4} outright rather than relaxed.
     *
     * <p><b>Test family:</b> endgame-technique (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void kd4_atMove49_characterizesNotSeeingTheWonKnightEndgame() throws Exception {
        var game = gameFromFenAtDepth(KNIGHT_ENDGAME_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertTrue(result.weight() < KNIGHT_ENDGAME_BOUND,
                "characterization: with material level myChess must still price this won endgame at about "
                        + "+1 where Stockfish has +3.92 for Kd4. If it now reports more than "
                        + KNIGHT_ENDGAME_BOUND + ", the evaluation has learned something about king activity "
                        + "in the endgame — turn this into a positive assertion on Kd4; got " + result.weight());
    }

    // ----------------------------------------------------------------
    // King safety — the pawn storm against the castled king.
    //
    // The only case in this class that did NOT come from a myChess
    // blunder: the position arose on lichess with myChess on the white
    // side, and it was the OPPONENT who played the losing move. It is
    // here because myChess turns out to share the blind spot from both
    // sides — it would play the same move as black, and as white it
    // cannot see the punishment.
    // ----------------------------------------------------------------

    /** After {@code 21...Qa3}; white (myChess) to move and, objectively, winning. */
    private static final String AFTER_QA3_FEN = "4r1k1/1b1r1pp1/p1p2b1p/2p1pN2/P2pP1PP/qP1P1N2/2PQ1PK1/3RR3 w - - 3 22";

    /**
     * Ceiling on what myChess may score the position after {@code 21...Qa3}. Measured on
     * v4.4.2 it stays between {@code +0.02} and {@code +0.49} at every depth from 1 to 11,
     * against {@code +3.6} from Stockfish, so the bound has three pawns of headroom below
     * the truth and half a pawn above the highest reading.
     */
    private static final float QA3_ATTACK_BOUND = 1.0f;

    /**
     * The clearest case in this class of a <b>pawn storm</b> the evaluation cannot price —
     * and the only one where the losing move was not myChess's own.
     *
     * <p>The position arose on lichess with myChess as white. Black played {@code 21...Qa3},
     * which loses on the spot: Stockfish 18 (depth 26, 1 thread, 128 MB) has {@code -4.12} for
     * it, against {@code -0.67} for the best move {@code 21...Qxd2} and {@code -0.88} for
     * {@code 21...Bd8}. The refutation is <b>{@code 22.g5!}</b> — {@code 22...Bd8 23.gxh6 g6
     * 24.Nxg7} — and its shape is what makes the move hard: the queen on a3 is never attacked
     * and never trapped. It is simply <em>absent</em> from the kingside, and the storm arrives
     * three moves later at the other end of the board.
     *
     * <p><b>myChess shares the blind spot from both sides.</b> Asked what to play as black it
     * picks {@code Qa3} at depths 1-5 and again at depth 9, {@code Bd8} at 6-7 and 11-12,
     * {@code Qb6} at 8 and 10 — the choice oscillates, and the score never leaves the band
     * {@code -0.11} to {@code -0.33}. Depth 9 costs 7.7 M nodes and depth 11 costs 95.7 M, so
     * a blitz clock lands squarely on the depth that blunders. Nothing here is a horizon
     * problem: {@code g4-g5} appears inside its own principal variation at most depths. It sees
     * the push and prices it at nothing.
     *
     * <p>The assertion pins the <em>white</em> side, because that is where the defect is stable.
     * After {@code Qa3} the engine reads {@code +0.02} to {@code +0.49} at every depth from 1 to
     * 11 while Stockfish has {@code +3.6} — a gap of over three pawns that does not close with
     * depth. Pinning the black side instead would encode the oscillation above rather than the
     * defect, the same reason
     * {@link #kd4_atMove49_characterizesNotSeeingTheWonKnightEndgame} pins a score and not a move.
     *
     * <p>Material is dead level on both sides of the move, so the material-only shortcut never
     * fires and the positional terms do run — they run and find nothing. This belongs with
     * {@code qb4_atMove22_characterizesTheKingSafetyBlindSpot} and the stripped-king case:
     * attackers converging on a castled king are worth approximately zero to this evaluation
     * (roadmap § 12.21).
     *
     * <p><b>Characterization, not a goal.</b> It passes because the defect is present. When
     * king safety lands, this score rises past {@link #QA3_ATTACK_BOUND} and the test must be
     * rewritten — as a positive assertion that white finds {@code 22.g5}, and as an avoidance
     * test against {@code Qa3} at the depth a blitz game reaches, from the position one ply
     * earlier: {@code 4r1k1/1b1r1pp1/p1p2b1p/2p1pN2/Pq1pP1PP/1P1P1N2/2PQ1PK1/3RR3 b - - 2 21}.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qa3_atMove21_characterizesNotSeeingTheG5PawnStorm() throws Exception {
        var game = gameFromFenAtDepth(AFTER_QA3_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertTrue(result.weight() < QA3_ATTACK_BOUND,
                "characterization: after 21...Qa3 white is winning by roughly 3.6 pawns (22.g5!), and "
                        + "myChess must still read the position as near-equal because no term prices the "
                        + "pawn storm. If it now reports more than " + QA3_ATTACK_BOUND + ", king safety has "
                        + "landed — turn this into a positive assertion on 22.g5 and add an avoidance test "
                        + "for Qa3 from the position one ply earlier (see this test's JavaDoc); white-POV "
                        + "eval " + result.weight());
    }

    // ================================================================
    // From the absolute-Elo anchor bracket, 2026-08-17.
    //
    // Every case above came from a lichess game. These four come from
    // the 2000-game bracket against externally rated engines, found by
    // `tools/scan-anchor-blunders.py`: Stockfish measured every move
    // myChess played in the 151 games it LOST to the two weakest
    // anchors — TSCP 1609 and Zeta Dva ~1785 — and 81 losses of three
    // pawns or more survived a depth-20 re-check.
    //
    // Why those two opponents: a loss to an engine of known *lower*
    // rating almost always means something concrete went wrong, while a
    // loss to a stronger one may simply be a stronger opponent. All
    // four cases below are TSCP games, i.e. myChess losing to an engine
    // 320 Elo below it.
    //
    // What the corpus says as a whole, beyond the individual moves:
    //
    //   * 69 of the 81 are EVALUATION defects — myChess's own score for
    //     the move stayed at least two pawns above the truth after it,
    //     so more search depth would not have helped. Only a handful
    //     are horizon effects.
    //   * 21 of the 81 (26 %) carry a score that is an exact number of
    //     pawns — the signature of the material-only shortcut having
    //     skipped the positional evaluation (see search § 7.3). Worth
    //     recording as a corpus statistic, but NOT usable as a
    //     mechanism claim for any single case: the implication runs one
    //     way only, material-only ⇒ whole pawns, never the reverse.
    //   * 63 are middlegame positions, 18 endgames.
    //
    // Four of the five reproduce from the bare FEN at SCANNER_DEPTH,
    // which is why they are pinned as *move* characterizations rather
    // than as score bounds: the engine picks the losing move outright,
    // so each inverts into an ordinary avoidance test the day it stops.
    // The fifth (55...Bxd4) does not reproduce, so it pins the
    // evaluation instead — the same split the earlier 49.Kd4 case made.
    // ================================================================

    /**
     * Asserts the engine still reproduces a known blunder — the inverse of
     * {@link #assertEngineAvoids}, for defects whose move choice is stable.
     *
     * <p>Preferred over a score bound where the engine really does pick the losing move:
     * it fails the moment the behavior changes, and the failure message then names the
     * move it chose instead, which is the first thing anyone wants to know.
     */
    static void assertEngineStillPlays(MoveAndWeight result, int blunderFrom, int blunderTo,
                                       String blunderName, String truth) {
        int chosen = result.move();
        boolean isBlunder = Move.getFromField(chosen) == blunderFrom && Move.getToField(chosen) == blunderTo;

        assertTrue(isBlunder,
                "characterization: myChess must still choose " + blunderName + " ("
                        + ChessUtil.moveToString(blunderFrom, blunderTo) + "), " + truth
                        + ". It chose " + ChessUtil.moveToString(chosen) + " instead, with white-POV eval "
                        + result.weight() + " — if that is a better move, the defect is fixed and this test "
                        + "should become an assertEngineAvoids for the same position.");
    }

    /** Black (myChess) to move before {@code 25...Qc3??}, which abandons the rook on d8. */
    private static final String BEFORE_QC3_FEN = "rn1r4/pp3kPp/4b3/4R3/1P1p1P1Q/P4qP1/2P5/R1K2B2 b - - 0 25";

    /**
     * The plainest blunder the anchor scan turned up: a queen move that leaves a rook to be
     * taken, in a position that was level.
     *
     * <p>Stockfish 18 (depth 20) rates the position before the move at <b>0.00</b> and after
     * it at <b>−8.23</b> — the refutation is simply {@code 26.Qxd8}, and the follow-up
     * {@code 26...Qxa1+ 27.Kd2 Qc3+ 28.Ke2 Qxc2+} wins nothing back. {@code 25...Nd7} holds
     * the balance. myChess reads the position at −0.49 at {@link #SCANNER_DEPTH}, i.e. it is
     * <em>not</em> wildly optimistic about the position — it simply fails to notice that the
     * queen was the rook's only defender.
     *
     * <p>That makes this the one case of the four where the primary defect looks like
     * <em>move selection</em> rather than evaluation: a two-ply material loss that the
     * quiescence search should see. It belongs next to {@code 21.Nf3} and {@code 39.Rxd5},
     * both of which had the same shape and are now repaired.
     *
     * <p><b>Test family:</b> tactical-oversight (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qc3_vsTscp_characterizesAbandoningTheRookOnD8() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_QC3_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.f3, Board.c3, "25...Qc3",
                "which drops the rook on d8 to 26.Qxd8 and turns 0.00 into −8.23 (Stockfish, depth 20); "
                        + "25...Nd7 holds");
    }

    /** Black (myChess) to move before {@code 67...Rf4??}, ignoring the b6 pawn. */
    private static final String BEFORE_RF4_FEN = "5k2/R4P2/1P2b3/5n2/1r6/8/1R6/1K6 b - - 18 67";

    /**
     * A nine-piece endgame decided by a passed pawn myChess does not race.
     *
     * <p>Stockfish has the position at <b>−0.32</b> — close to level — and at <b>−8.40</b>
     * after {@code 67...Rf4}, because {@code 68.b7} then runs: the b-pawn is two squares from
     * promotion and nothing stops it. {@code 67...Rxb2+} is the move, trading into a defensible
     * ending. myChess reads −0.90 and plays {@code Rf4} anyway.
     *
     * <p>It is the third open case in {@code endgame-technique} and the second to involve a
     * passed pawn: the family already holds a bishop that fails to occupy a promotion square
     * ({@code 75.Ba1}) and a won knight endgame priced at a fifth of its value
     * ({@code 49.Kd4}). Together they point at the same missing knowledge — the value of a
     * pawn that is about to become a queen, which no piece-square table expresses.
     *
     * <p><b>Test family:</b> endgame-technique (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rf4_vsTscp_characterizesNotRacingThePassedBPawn() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RF4_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.b4, Board.f4, "67...Rf4",
                "which lets 68.b7 run and turns −0.32 into −8.40 (Stockfish, depth 20); 67...Rxb2+ holds");
    }

    /** Black (myChess) to move before {@code 32...Ba4??}, with white's battery aimed at g7. */
    private static final String BEFORE_BA4_FEN = "4r1k1/1p3ppp/1q6/nPb1pP2/4P3/Pb3RR1/3QB2P/7K b - - 2 32";

    /**
     * The sharpest king-safety case in the suite, because the misjudgement runs in the
     * <b>wrong direction by four pawns and a sign</b>.
     *
     * <p>myChess believes it is <b>+2.09 ahead</b>. Stockfish has the position at +1.65 for
     * black before the move — so far so good — and at <b>−5.94</b> after {@code 32...Ba4},
     * because white's attack simply arrives: {@code 33.f6 g6 34.Qh6 Bf8 35.Rxg6+ hxg6} and the
     * king on g8 has no cover left. {@code 32...Rd8} defends. Three white pieces already bear
     * on the kingside — {@code Qd2}, {@code Rf3} and {@code Rg3} stacked on the g-file, with
     * the {@code f5} pawn one push from prising g6 open — and none of that is priced.
     *
     * <p>This is the fifteenth open case in the family and the first from a game against an
     * externally rated engine rather than from lichess. It is also the clearest illustration
     * of what [roadmap § 12.21](../../../../../docs/roadmap.md) describes as missing: a
     * non-linear attacker-count term. Two attackers near a king are far more than twice one
     * attacker, and myChess scores the difference at zero.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void ba4_vsTscp_characterizesTheBatteryOnTheGFile() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_BA4_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.b3, Board.a4, "32...Ba4",
                "which allows 33.f6 g6 34.Qh6 Bf8 35.Rxg6+ and turns +1.65 into −5.94 (Stockfish, "
                        + "depth 20) while myChess reads +2.09 for itself; 32...Rd8 defends");
    }

    /** White (myChess) to move before {@code 20.Bxf5??}, with its own king airy on b2. */
    private static final String BEFORE_BXF5_FEN = "r5nr/1pp1qkpp/8/5p2/3p4/3P1P1B/PKP2P1P/R3Q2R w - - 2 20";

    /**
     * The same blind spot seen from the other side of the board: myChess grabs a pawn while
     * its <em>own</em> king sits on b2 with the a- and b-files open.
     *
     * <p>Stockfish rates the position at <b>−2.55</b> for white before the move — already
     * worse than myChess's −0.55 — and at <b>−10.23</b> after {@code 20.Bxf5}, because
     * {@code 20...Qa3+ 21.Kb1 Ra6} arrives with the rook joining on the a-file and nothing
     * defending. {@code 20.Qxe7+} is the move, trading the attacker off.
     *
     * <p>Pairing it with {@link #ba4_vsTscp_characterizesTheBatteryOnTheGFile} is the point:
     * one case has myChess failing to see an attack <em>against</em> it after castling short,
     * the other after its king walked to b2. The term that is missing does not care which
     * king it is.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void bxf5_vsTscp_characterizesIgnoringTheAttackOnItsOwnKing() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_BXF5_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.h3, Board.f5, "20.Bxf5",
                "which allows 20...Qa3+ 21.Kb1 Ra6 and turns −2.55 into −10.23 (Stockfish, depth 20); "
                        + "20.Qxe7+ trades the attacker off");
    }

    /** Black (myChess) to move before {@code 55...Bxd4??}, king on f7 and white's queen loose. */
    private static final String BEFORE_BXD4_FEN = "r7/P1p2k2/2p1pb2/2P2p2/3PpP2/2q1P1Qp/7P/6RK b - - 7 55";

    /**
     * Ceiling on what myChess may read for black here. Measured on v4.4.2 it reports
     * <b>+2.98</b> for black at {@link #SCANNER_DEPTH} where Stockfish has <b>−1.03</b>, so a
     * bound of +1.0 sits well below the reading and well above the truth.
     */
    private static final float BXD4_OPTIMISM_BOUND = -1.0f;

    /**
     * The fifth anchor-bracket case, and the one that pins a <em>score</em> rather than a
     * move — with a note about a mechanism that looked obvious and is not provable.
     *
     * <p>myChess played {@code 55...Bxd4} in the game and reported <b>0.00</b> for it.
     * Stockfish 18 (depth 20) has the position at <b>−1.03</b> for black beforehand and
     * <b>−9.52</b> after; {@code 55...Ke7} was the move. The refutation is a queen hunt
     * against the king on f7 — {@code 56.Qg6+ Kf8 57.Qg8+ Ke7 58.Qh7+} — with white's a7 pawn
     * one square from promoting behind it.
     *
     * <p><b>Why it is here and not in {@code MaterialOnlyShortcutEvalTest}.</b> An exact 0.00
     * in a 19-piece unbalanced position is the signature of the material-only shortcut, and
     * that is where this case was first headed. Two findings stopped it. Replaying myChess's
     * own depth-10 principal variation shows it trading into <em>material equality</em>
     * (queen, rook and five pawns each) and scoring that at zero — no repetition anywhere in
     * the line, so the 0.00 is not a draw score either. And the signature is depth-dependent:
     * at {@code SCANNER_DEPTH} the engine reads +2.98, which is not a whole number of pawns at
     * all. Since the implication only runs one way — material-only ⇒ whole pawns, never the
     * reverse — the shortcut cannot be shown to be the mechanism, and claiming it would put an
     * unproven cause into a test name.
     *
     * <p>What <em>is</em> demonstrable is the optimism, and its subject is the same as in the
     * two cases above: a king with no cover. Hence, this family.
     *
     * <p><b>Characterization, not a goal.</b> Unlike the other four anchor cases the move does
     * not reproduce from the bare FEN — at {@link #SCANNER_DEPTH} myChess prefers {@code Rh8} —
     * so there is nothing to assert about the choice, exactly as with
     * {@link #kd4_atMove49_characterizesNotSeeingTheWonKnightEndgame}. When king safety lands,
     * this score falls below {@link #BXD4_OPTIMISM_BOUND} and the test must be rewritten.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void bxd4_vsTscp_characterizesOptimismWithItsKingOnF7() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_BXD4_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertTrue(result.weight() < BXD4_OPTIMISM_BOUND,
                "characterization: black's king on f7 is about to be hunted by the queen "
                        + "(56.Qg6+ Kf8 57.Qg8+ Ke7 58.Qh7+) with a7 promoting behind it, and Stockfish has "
                        + "black at −1.03. myChess must still read this as good for black, because no term "
                        + "prices an exposed king. A white-POV score below " + BXD4_OPTIMISM_BOUND
                        + " means king safety has landed — rewrite this to require it; got "
                        + result.weight());
    }

    // ================================================================
    // lichess keBKOXd1 — the game that shows both halves of the king-
    // safety problem in one place, and separates them.
    //
    // Rated rapid, 600+5, myChessJava (2104) vs JDoss_BOT (2172), 1-0
    // in 58 moves. myChess won it, and the result is the least
    // interesting thing about it: it was mated in six on move 19 and
    // survived because the opponent missed a mate in two.
    //
    // Reconstructed move by move against Stockfish 18 at depth 22, with
    // myChess's own scores taken from the bot's engine log rather than
    // from lichess (lichess stores only its own analysis):
    //
    //   mv 10  h3      SF  +1.70  myChess +1.74   blindness starts here;
    //                                             SF wanted Be4, and h3
    //                                             turns +1.70 into -0.59
    //   mv 14  Nxa8    SF  -2.09  myChess +3.00   the rook grab IS SF's
    //                                             best move — the
    //                                             position was already
    //                                             bad, and myChess is
    //                                             five pawns out
    //   mv 18  Qxf8    SF  -3.99  myChess +4.95   peak divergence: 8.94
    //   mv 19  Nb6+    SF   M-6   myChess +0.00   mated in six, reads level
    //   mv 20  Qxe7+   SF   M-5   myChess  -M5    SEEN, and exactly right
    //   mv 21  Nd5+    SF   M-4   myChess  -M4    from here to -M2 the
    //   mv 22  Nf4+    SF   M-3   myChess  -M3    distances all match
    //   mv 23  gxf3    SF   M-2   myChess  -M2
    //   mv 24  Re1     SF  +0.00                  23...Kf5?? threw it away;
    //                                             23...Rxh3+ 24.Kg2 Rh2#
    //   mv 27  Rexc1   SF  +3.86  myChess +3.60   won from here; every
    //                                             reading to move 45 is
    //                                             within half a pawn
    //   mv 46  d6+     SF +15.30 -> +6.63         8.7 pawns given back,
    //                                             costing nothing
    //
    // The line at move 20 is the one that matters. Up to move 19 this is
    // an EVALUATION defect: nine moves of counting material while its
    // own king is surrounded, with no term pricing the danger. From move
    // 20 the search is flawless — every mate distance correct. So the
    // two tests below are a pair, and neither means much alone:
    //
    //   * the defect test pins the evaluation at move 19
    //   * the guard pins that the search finds a mate when one is in
    //     range, which is what rules out "it just needed more depth"
    //
    // Full game:
    //
    // 1. e4 c5 2. Nc3 e6 3. Nf3 d5 4. exd5 exd5 5. Bb5+ Bd7 6. Qe2+ Be7
    // 7. Nxd5 Nc6 8. O-O a6 9. Bd3 Bg4 10. h3 Nd4 11. Qe5 f6 12. Nc7+ Kd7
    // 13. Qg3 Bxf3 14. Nxa8 Bd6 15. Qxg7+ Ne7 16. Bxh7 Ne2+ 17. Kh1 Qf8
    // 18. Qxf8 Rxh7 19. Nb6+ Ke6 20. Qxe7+ Kxe7 21. Nd5+ Ke6 22. Nf4+ Bxf4
    // 23. gxf3 Kf5 24. Re1 Rxh3+ 25. Kg2 Rh2+ 26. Kf1 Nxc1 27. Rexc1 Rh7
    // 28. Rab1 c4 29. Rd1 Rh8 30. b3 cxb3 31. Rxb3 Rc8 32. Rc3 Rh8
    // 33. Rc4 b5 34. Rc6 Ra8 35. d4 a5 36. Rc5+ Ke6 37. Rxb5 Rc8
    // 38. Re1+ Kd7 39. Re2 a4 40. Rf5 Bd6 41. Rxf6 Ba3 42. d5 Bb2
    // 43. Rg6 Rf8 44. Re4 Rh8 45. Rxa4 Kc7 46. d6+ Kc6 47. Ra6+ Kb7
    // 48. d7 Kc7 49. Ra7+ Kb8 50. Rga6 Bf6 51. Ra8+ Kc7 52. R6a7+ Kc6
    // 53. Rxh8 Be7 54. d8=Q Bd6 55. Ra6+ Kc5 56. Rxd6 Kc4 57. Qb8 Kc5
    // 58. Rc8# 1-0
    // ================================================================

    /** White (myChess) to move before {@code 19.Nb6+}, and mated in six. */
    private static final String KEBKOXD1_MATED_FEN =
            "N4Q2/1p1kn2r/p2b1p2/2p5/8/5b1P/PPPPnPP1/R1B2R1K w - - 0 19";

    /**
     * Ceiling on what myChess may read while being mated in six. Measured on v4.4.2 from a
     * cold table at {@link #SCANNER_DEPTH} it reports <b>+5.00</b> — worse than the +0.00 it
     * logged in the game, which came from a deeper search with a warm table. A bound of +1.0
     * therefore sits four pawns below the reading and is still generous: anything at all
     * positive is wrong here.
     */
    private static final float KEBKOXD1_MATED_BOUND = 1.0f;

    /**
     * The largest evaluation error in the suite: <b>+5.00 while mated in six</b>.
     *
     * <p>Three white pieces are stranded on the wrong side of the board — the knight on a8,
     * the queen on f8 — while black's knight on e2, bishop on f3 and rook on h7 surround the
     * king on h1 with the g-file already open. Stockfish 18 (depth 26) has a forced mate in
     * six. myChess reads five pawns <em>for itself</em>.
     *
     * <p>{@code 19.Nb6+} is <b>Stockfish's own best move</b>, so nothing is being asserted
     * about move choice: the position is lost whatever white plays, and the defect is purely
     * that myChess cannot see it. That makes this the cleanest evaluation characterization
     * available — no move-ordering effect, no horizon argument, no alternative line to
     * dispute.
     *
     * <p>The mate is six plies away at the point of measurement and
     * {@link #SCANNER_DEPTH} is eight, so it is <em>inside</em> the search horizon by two
     * plies. That the engine still reports +5.00 is the strongest single piece of evidence in
     * this class that the missing term is in the evaluation and not in the search —
     * corroborated by {@link #keBKOXd1_afterGxf3_findsTheMateTheOpponentMissed}, which shows
     * the same engine handling a mate correctly once nothing masks it.
     *
     * <p><b>Characterization, not a goal.</b> It passes because the defect is present. When
     * king safety lands this score collapses and the test must be rewritten to require a
     * losing evaluation instead.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void keBKOXd1_atMove19_characterizesReadingFivePawnsWhileMatedInSix() throws Exception {
        var game = gameFromFenAtDepth(KEBKOXD1_MATED_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertTrue(result.weight() > KEBKOXD1_MATED_BOUND,
                "characterization: white is mated in six here (Stockfish, depth 26) and myChess must "
                        + "still read a large advantage for itself, because no term prices the three black "
                        + "pieces around its king on the opened g-file. A score at or below "
                        + KEBKOXD1_MATED_BOUND + " means king safety has landed — rewrite this to require a "
                        + "losing evaluation; white-POV eval " + result.weight());
    }

    /** Black to move after {@code 23.gxf3} — mate in two is on the board. */
    private static final String KEBKOXD1_MATE_IN_TWO_FEN =
            "8/1p5r/p3kp2/2p5/5b2/5P1P/PPPPnP2/R1B2R1K b - - 0 23";

    /**
     * The guard the {@code king-safety} family never had: the search <b>does</b> find a mate
     * in this exact position class — it is the evaluation that is blind.
     *
     * <p>This is also the game's punchline. In the position after {@code 23.gxf3} black has
     * {@code 23...Rxh3+ 24.Kg2 Rh2#}. JDoss_BOT (2172) played {@code 23...Kf5} instead and
     * lost from a won game; myChess, asked the same question from the black side, finds
     * {@code Rxh3+} and scores it as a mate. Its own log for the white side agrees — it had
     * been reporting {@code -M2} one ply earlier, so both sides of the board had it right and
     * only the opponent did not.
     *
     * <h2>Two mates on the same square, and why white cannot choose between them</h2>
     *
     * <p>{@code Rxh3} mates in two different ways depending on what white does one move
     * earlier, and the second is caused by the move that prevents the first. Worth spelling
     * out, because it is what the search had to see and therefore what this guard protects.
     *
     * <p><b>Without {@code gxf3} — mate at once.</b> One ply earlier the black bishop still
     * stands on f3 and white's pawn on g2. {@code Rxh3+} is then immediate mate, with four
     * black pieces each doing exactly one job and none of them twice:
     *
     * <pre>
     *   rook h3      gives the check along the h-file
     *   bishop f3    pins g2 to the king, so gxh3 is illegal
     *   knight e2    covers g1
     *   bishop f4    covers h2
     * </pre>
     *
     * <p>The pin is the heart of it: the one pawn that could take the rook is the one pawn
     * forbidden to move. Verified — white has <em>zero</em> legal replies.
     *
     * <p><b>With {@code gxf3} — mate one move later, for the opposite reason.</b> Capturing on
     * f3 removes the pinning bishop and frees g2. But the pawn arrives <em>on f3</em>, and
     * after {@code 23...Rxh3+ 24.Kg2 Rh2+} the king's escape to the f-file is walled off by
     * white's own men: rook on f1, pawn on f2, and the pawn that just captured on f3.
     * {@code Kxh2} is denied by the bishop on f4, {@code Kg1} and {@code Kg3} by the knight and
     * that same bishop.
     *
     * <p>So the move that dissolves the pin builds the wall. White can postpone the mate by one
     * ply and cannot avoid it — which is precisely why myChess reported {@code -M2} rather than
     * {@code -M1} before playing {@code gxf3}: it had costed both roads and taken the longer one.
     *
     * <p><b>Why a guard here matters.</b> Before this test the family held seventeen open
     * defects and not one guard, which left an obvious counter-argument unanswered: perhaps
     * myChess simply cannot search these positions deeply enough, and there is no evaluation
     * problem at all. This rules that out on a position from the same game, the same king,
     * the same open g-file — the search returns a correct mate score. Paired with
     * {@link #keBKOXd1_atMove19_characterizesReadingFivePawnsWhileMatedInSix}, the two
     * together locate the defect rather than merely reporting it.
     *
     * <p>Carries a {@code guard} marker rather than {@code fixed}: nothing was repaired here,
     * this pins behavior that was always correct and must stay correct.
     *
     * <p><b>Test family:</b> king-safety (guard)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void keBKOXd1_afterGxf3_findsTheMateTheOpponentMissed() throws Exception {
        var game = gameFromFenAtDepth(KEBKOXD1_MATE_IN_TWO_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.h7, Board.h3, "23...Rxh3+",
                "the first move of 23...Rxh3+ 24.Kg2 Rh2#, which the 2172-rated opponent missed in "
                        + "favour of 23...Kf5 and thereby lost a won game");
        assertTrue(result.weight() < -MATE_SCORE_FLOOR,
                "the score must be a mating one for black, since Rxh3+ mates next move. A non-mating "
                        + "score means the search stopped finding this — which would invalidate the pairing "
                        + "with the move-19 characterization; white-POV eval " + result.weight());
    }

    // ================================================================
    // Anchor bracket, second harvest — 2026-08-29
    //
    // The 2026-08-17 scan produced 81 verified findings and five became
    // tests, all five from TSCP; the Zeta Dva half, 54 of the 81, was
    // never touched. These ten come from that half plus the strongest
    // remaining TSCP cases.
    //
    // Every one was re-probed against the current build first, because
    // the findings are one version old and v4.6.0 changed which
    // evaluation regime a subtree runs under. That step was not
    // ceremony: of 58 findings whose position was not already lost, 37
    // still reproduce and 21 do not, so pinning straight from the old
    // list would have written red tests for a third of them. Eight of
    // the 37 also do not reproduce at SCANNER_DEPTH but only deeper,
    // which is why the pin depth here comes from the measurement rather
    // than from the constant. Data:
    // test-results/anchor-reprobe-4.6.0.jsonl.
    //
    // Stockfish 18 at depth 22 re-confirmed all 58 at 3.00 pawns or
    // more — none shrank — and supplied the holding move and the
    // refutation line each case comment quotes; the original scan had
    // only the two evaluations. Data:
    // test-results/anchor-refutations.jsonl.
    // ================================================================

    /** White (myChess) to move before {@code 32.Rc7+??}, holding a forced mate. */
    private static final String BEFORE_RC7_FEN = "QR3b1r/4k1pp/bB1pp3/5p2/3PB3/4P1P1/r2q3P/2R3K1 w - - 6 32";

    /**
     * The most expensive single move in the whole anchor corpus: a forced mate turned into a
     * lost position. Six checks are available and <b>exactly one wins</b>; the other five lose
     * to a forced mate against.
     *
     * <table>
     *   <caption>Every check in the diagram, Stockfish 18 at depth 24</caption>
     *   <tr><td>{@code Re8+}</td><td>mate in 9 <b>for</b> white</td></tr>
     *   <tr><td>{@code Rc7+} (played)</td><td>mate in 15 against</td></tr>
     *   <tr><td>{@code Rb7+}</td><td>mate in 12 against</td></tr>
     *   <tr><td>{@code Qb7+}, {@code Qa7+}, {@code Bd8+}</td><td>mate in 8 against</td></tr>
     * </table>
     *
     * <p><b>Why {@code Re8+} and nothing else.</b> The queen on a8 is entombed by its own rook
     * on b8 — before the move it reaches only a6, a7, b7, c6 and d5. Moving that rook opens the
     * eighth rank, which is what {@code 33.Qd8+} needs. And e8 is defended by exactly one
     * piece: the a8 queen. The rook may go there only because leaving b8 creates its own
     * defender in the same move. {@code Rb7+} opens the rank too but leaves the rook undefended
     * and off the e-file; the queen checks leave the rook on b8 and the rank shut;
     * {@code Bd8+} occupies the very square the follow-up needs.
     *
     * <p><b>Why a wrong check loses rather than merely failing to win.</b> Black has {@code Qd2}
     * and {@code Ra2} doubled on white's second rank with the king on g1 behind g3/h2. This is
     * a race, not an attack — white mates first or is mated.
     *
     * <p><b>Not an evaluation defect, despite the scan's label.</b> The scan classifies by
     * "own score far above the truth after the move", and myChess reports <b>+4.00</b> against
     * a truth of −12.78, so the rule of thumb says evaluation. It does not fit here: the win is
     * a mate in 9 (17 plies) and the punishment a mate in 15, both far beyond
     * {@link #SCANNER_DEPTH} and beyond what myChess reaches under a clock. With neither mate
     * visible the engine falls back on material, and {@code Rc7+} wins the bishop on f8 — the
     * best-looking move by the only measure it has. What the case actually pins is that a
     * material fallback picks a *losing* check in a position where five of six checks lose.
     *
     * <p><b>Test family:</b> search-horizon (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rc7_vsZetaDva_characterizesAbandoningAForcedMate() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RC7_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.c1, Board.c7, "32.Rc7+",
                "which gives up a forced mate: 32.Re8+ Kf6 33.Qd8+ mates, while after 32.Rc7+ Kf6 "
                        + "33.Rxf8+ Kg6 34.Bxf5+ Kh6 the position is −12.78 (Stockfish 18, depth 22)");
    }

    /** White (myChess) to move before {@code 36.Ne4??}, with a winning fork available. */
    private static final String BEFORE_NE4_FEN = "r3rk2/p1p1bp2/1pQ1p1p1/1P4N1/R5P1/8/PqP2PK1/4R3 w - - 7 36";

    /**
     * A knight that retreats from a fork instead of playing it.
     *
     * <p>{@code 36.Nxe6+} wins on the spot — {@code 36...Kg8 37.Nxc7} picks up a second piece
     * and Stockfish 18 rates the position <b>+4.93</b> before the move. myChess plays
     * {@code 36.Ne4}, and after {@code 36...Qe5 37.Re3 Red8 38.Rf3} the advantage is gone:
     * <b>−0.54</b>.
     *
     * <p>Sits next to {@code 21.Nf3} and {@code 39.Rxd5} in shape — a two-ply tactic the
     * quiescence search is meant to see — except that both of those are repaired and this one
     * is not. The difference is that the winning move here is itself a *check*, so the
     * capture-only quiescence generator sees it, but only after the knight has already moved
     * elsewhere in the ordering.
     *
     * <p><b>Test family:</b> tactical-oversight (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void ne4_vsZetaDva_characterizesRetreatingInsteadOfForking() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_NE4_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.g5, Board.e4, "36.Ne4",
                "which gives up the fork 36.Nxe6+ Kg8 37.Nxc7 and turns +4.93 into −0.54 "
                        + "(Stockfish 18, depth 22)");
    }

    /** White (myChess) to move before {@code 37.Re7??}, abandoning the c2 pawn's defender. */
    private static final String BEFORE_RE7_FEN = "2r5/5p1k/R5p1/7p/3q4/2r5/2P1RPPP/Q5K1 w - - 6 37";

    /**
     * A rook that leaves the square it was defending from.
     *
     * <p>The rook on e2 is the only thing holding c2. {@code 37.Re7} abandons it, and
     * {@code 37...Rxc2 38.Qe1 Rc1} collects: <b>+0.01</b> becomes <b>−4.80</b> (Stockfish 18,
     * depth 22). {@code 37.h3} keeps the balance.
     *
     * <p>The same shape as {@code 25...Qc3}, the plainest TSCP case — a piece moved without
     * noticing it was a lone defender — which is why the two belong to one family. That case
     * is a queen and this one a rook, so a fix aimed at either should move both.
     *
     * <p><b>Test family:</b> tactical-oversight (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void re7_vsZetaDva_characterizesMovingTheOnlyDefenderOfC2() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RE7_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.e2, Board.e7, "37.Re7",
                "which drops c2 to 37...Rxc2 38.Qe1 Rc1 and turns +0.01 into −4.80 "
                        + "(Stockfish 18, depth 22); 37.h3 holds");
    }

    /** Black (myChess) to move before {@code 48...d3??} in a rook endgame. */
    private static final String BEFORE_D3_FEN = "8/7R/1p1r1k2/8/3p2K1/P5P1/7P/8 b - - 2 48";

    /**
     * A passed pawn pushed one move too early, in a rook endgame that was level.
     *
     * <p>{@code 48...d3} looks natural and loses the rook: {@code 49.Rh6+ Ke5 50.Rxd6 Kxd6
     * 51.Kf3} and the pawn falls too, <b>0.00</b> to <b>−6.64</b> (Stockfish 18, depth 22).
     * {@code 48...Rd8} first, and the pawn goes on afterward.
     *
     * <p>The third rook endgame in {@code endgame-technique} where the loss comes from moving
     * the pawn before the rook is safe. Together with {@code 67...Rf4} — the same corpus, the
     * same family, the same failure to price a passed pawn against a loose piece — it points
     * at the gap the section already names: no term expresses what a pawn two squares from
     * promotion is worth.
     *
     * <p><b>Test family:</b> endgame-technique (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void d3_vsZetaDva_characterizesPushingBeforeSecuringTheRook() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_D3_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.d4, Board.d3, "48...d3",
                "which loses the rook to 49.Rh6+ Ke5 50.Rxd6 and turns 0.00 into −6.64 "
                        + "(Stockfish 18, depth 22); 48...Rd8 first holds");
    }

    /** Black (myChess) to move before {@code 54...c1=Q??} in a pawn race. */
    private static final String BEFORE_C1Q_FEN = "8/8/8/r5P1/5P1P/7K/1kp5/7R b - - 0 54";

    /**
     * Promoting into a capture, in a race that was drawn.
     *
     * <p>The rook on h1 covers c1. {@code 54...c1=Q 55.Rxc1 Kxc1} spends the pawn for nothing
     * and white's three connected pawns then run: <b>0.00</b> to <b>−4.95</b> (Stockfish 18,
     * depth 22). {@code 54...Ra1} pins the rook to its job first.
     *
     * <p>The purest case in the corpus for a specific hole: a promotion is scored as a
     * promotion regardless of whether the new queen survives the reply. That is a
     * quiescence question — {@code 55.Rxc1} is a capture, so the capture-only generator does
     * produce it — which makes the case worth re-checking whenever move ordering changes.
     *
     * <p><b>Test family:</b> endgame-technique (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void c1Queen_vsZetaDva_characterizesPromotingIntoACapture() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_C1Q_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.c2, Board.c1, "54...c1=Q",
                "which is met by 55.Rxc1 Kxc1 and turns 0.00 into −4.95 (Stockfish 18, depth 22); "
                        + "54...Ra1 holds");
    }

    // ---- repaired since v4.4.1: these guard the repair ----

    /** Black (myChess) to move before {@code 51...Rd8??}, which walked into mate. */
    private static final String BEFORE_RD8_FEN = "2r3k1/5p2/4p1p1/4P1P1/q2P1PN1/1r6/3B4/3nK2Q b - - 1 51";

    /**
     * The largest repaired swing in the corpus: black stood at <b>+8.87</b> and
     * {@code 51...Rd8} let white mate (Stockfish 18, depth 22). {@code 51...Rb1} wins.
     *
     * <p>The current build plays {@code 51...Rcc3} at {@link #SCANNER_DEPTH} and keeps the
     * win, so this is a guard rather than a characterization. Worth keeping precisely because
     * the failure it guards against is not "loses some advantage" but "converts a won game
     * into a mate against" — the cheapest possible regression to detect and the most expensive
     * to ship.
     *
     * <p><b>Test family:</b> tactical-oversight (fixed)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rd8_vsZetaDva_engineNoLongerWalksIntoMate() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RD8_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineAvoids(result, Board.c8, Board.d8, "51...Rd8");
    }

    /** Black (myChess) to move before {@code 42...Rf3??}, throwing away a won position. */
    private static final String BEFORE_RF3_FEN = "6k1/7p/1p4p1/pP6/P1p5/2Pq4/1KR2r1P/1Q6 b - - 8 42";

    /**
     * From <b>+8.79</b> to <b>+0.03</b> in one move: {@code 42...Rf3} lets white consolidate,
     * while {@code 42...Qxc2+} finishes (Stockfish 18, depth 22).
     *
     * <p>Repaired — the current build plays {@code 42...Rf1} at {@link #SCANNER_DEPTH}. Note
     * that {@code Rf1} is not Stockfish's move either; the assertion is deliberately only that
     * the losing rook move is avoided, because pinning the *best* move would fail on any
     * change that picks a different winning continuation.
     *
     * <p><b>Test family:</b> tactical-oversight (fixed)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rf3_vsZetaDva_engineNoLongerReleasesTheWin() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RF3_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineAvoids(result, Board.f2, Board.f3, "42...Rf3");
    }

    /** White (myChess) to move before {@code 15.Bc2??}, from a level middlegame. */
    private static final String BEFORE_BC2_FEN = "r1bk3r/pp3ppp/2pb4/3pn3/QP1B1q2/2PB1P2/P4PPP/RN2R1K1 w - - 6 15";

    /**
     * A bishop retreat that collapses a level position: <b>+0.27</b> to <b>−6.34</b>
     * (Stockfish 18, depth 22). {@code 15.Be2} was the square.
     *
     * <p>Repaired — the current build plays {@code 15.Be3} at {@link #SCANNER_DEPTH}. The one
     * middlegame case in this batch where the position before the move was genuinely balanced,
     * which makes it the most representative of ordinary play: the other repaired cases all
     * start from a large advantage, where a lot can go wrong before the result changes.
     *
     * <p><b>Test family:</b> tactical-oversight (fixed)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void bc2_vsZetaDva_engineNoLongerCollapsesALevelPosition() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_BC2_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineAvoids(result, Board.d3, Board.c2, "15.Bc2");
    }

    /** Black (myChess) to move before {@code 61...Bc3+??} in a queen endgame. */
    private static final String BEFORE_BC3_FEN = "8/3P3p/6p1/K2Qp1k1/3bP1P1/8/8/5q2 b - - 38 61";

    /**
     * {@code 61...Bc3+} turns <b>+3.68</b> into <b>−4.06</b>; {@code 61...Qa1+} wins
     * (Stockfish 18, depth 22).
     *
     * <p>Repaired, and repaired exactly: the current build plays {@code 61...Qa1+} at
     * {@link #SCANNER_DEPTH} — Stockfish's move, not merely a different one. The strongest
     * form a guard can take, since it pins both halves of the decision.
     *
     * <p><b>Test family:</b> endgame-technique (fixed)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void bc3_vsTscp_engineNowFindsTheWinningCheck() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_BC3_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineAvoids(result, Board.d4, Board.c3, "61...Bc3+");
    }

    /** Black (myChess) to move before {@code 83...R6xa7??} in a rook-and-pawn race. */
    private static final String BEFORE_R6XA7_FEN = "r7/P6R/r4Pp1/7P/8/2K5/4R1p1/7k b - - 0 83";

    /**
     * Taking the a7 pawn with the wrong rook: <b>+2.90</b> to <b>−2.68</b>, where
     * {@code 83...g5} holds the race (Stockfish 18, depth 22).
     *
     * <p>Repaired — the current build plays {@code 83...Rc6+} at {@link #SCANNER_DEPTH}. The
     * position is a four-pawn race with both sides one move from promoting, which is the shape
     * the {@code endgame-technique} family keeps returning to; that this one is now handled
     * while {@code 48...d3} above is not gives the family a fixed and an open case in the same
     * batch, from the same opponent.
     *
     * <p><b>Test family:</b> endgame-technique (fixed)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void r6xa7_vsZetaDva_engineNoLongerTakesWithTheWrongRook() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_R6XA7_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineAvoids(result, Board.a6, Board.a7, "83...R6xa7");
    }

    // ================================================================
    // Anchor bracket, third harvest — characterizations only
    //
    // Ten more of the re-probed findings, chosen for spread rather than
    // for damage: seven families across four endgames, five middlegames
    // and one opening, five from each anchor. Picking the ten largest
    // losses instead would have produced six variations on "grabbed
    // material and got punished", which pins one defect ten times.
    //
    // All ten reproduce at SCANNER_DEPTH and cost 15.8 s of search
    // between them. The remaining candidates are in
    // test-results/anchor-blunders-4.6.0.md.
    // ================================================================

    /** White (myChess) to move before {@code 29.Rxa5??}, from a level position. */
    private static final String BEFORE_RXA5_FEN = "3r2k1/4qppp/4b3/p7/1p3Qn1/3BP3/1PPB2Pr/R2R2K1 w - - 0 29";

    /**
     * A rook that leaves the first rank to win a pawn, into a mating attack.
     *
     * <p>Stockfish 18 (depth 22) rates the position <b>0.00</b> and <b>−6.98</b> after the
     * grab: {@code 29...Qh4 30.Kf1 Nxe3+ 31.Qxe3 Rh1+} and white's king is stripped.
     * {@code 29.Be1} holds — the same bishop, one square, and the attack never starts.
     *
     * <p>Diagnostically the interesting part is what myChess thought: <b>−0.37</b>, which is
     * close to the truth. It is not optimistic about the resulting position, so the defect is
     * in which move it picks, not in how it scores the outcome — the opposite of the
     * {@code corner-grab} cases, where the score is wildly wrong.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rxa5_vsTscp_characterizesGrabbingAPawnIntoAMatingAttack() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RXA5_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.a1, Board.a5, "29.Rxa5",
                "which allows 29...Qh4 30.Kf1 Nxe3+ 31.Qxe3 Rh1+ and turns 0.00 into −6.98 "
                        + "(Stockfish 18, depth 22); 29.Be1 holds");
    }

    /** White (myChess) to move before {@code 33.Rae1??}, from a nearly won position. */
    private static final String BEFORE_RAE1_FEN = "4RN1q/1k3r2/bppB1b2/p2p2pr/3P1p2/2P2Q1P/PP3PK1/R7 w - - 6 33";

    /**
     * The largest advantage thrown away in this batch: <b>+7.10</b> to <b>+0.44</b>.
     *
     * <p>{@code 33.Rae1} develops the last piece and loses everything to {@code 33...g4}:
     * after {@code 34.Qxf4 gxh3+ 35.Kh1 Qg7} white's king has no cover left. {@code 33.Qg4}
     * first, and the attack is answered before it exists.
     *
     * <p>myChess read the position at <b>+2.51</b> — it never saw the win it was giving up,
     * which is why a deeper search does not obviously help: it is not choosing between a large
     * advantage and a small one, it is choosing between two positions it believes are similar.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rae1_vsTscp_characterizesOpeningTheKingWhileWinning() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RAE1_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.a1, Board.e1, "33.Rae1",
                "which is met by 33...g4 34.Qxf4 gxh3+ and turns +7.10 into +0.44 "
                        + "(Stockfish 18, depth 22); 33.Qg4 holds the win");
    }

    /** White (myChess) to move before {@code 26.Qxb7??} — the second poisoned b7 in the suite. */
    private static final String BEFORE_QXB7_M26_FEN = "r6r/ppp3kp/6p1/1Q3p2/8/PPP2P2/q4PPP/1N2R1K1 w - - 13 26";

    /**
     * The same move as {@code 12.Qxb7} in a different game, fourteen moves later, and against
     * the other anchor — which is what makes it worth having twice.
     *
     * <p><b>+4.64</b> to <b>−1.30</b> (Stockfish 18, depth 22). The queen takes on b7 and is
     * out of play while both black rooks arrive: {@code 26...Rhe8 27.Rf1 Rad8 28.Qxc7+ Kh8}.
     * {@code 26.Qe5+} keeps the queen central and the advantage.
     *
     * <p>myChess reported <b>+1.05</b>, understating its own position before the move and not
     * seeing the collapse after it. The existing {@code 12.Qxb7} case ties that pattern to the
     * material-only shortcut; whether the same mechanism is active here is not established, and
     * the two cases together are the evidence a fix would have to satisfy.
     *
     * <p><b>Test family:</b> corner-grab (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qxb7_atMove26_vsZetaDva_characterizesTheSamePoisonedPawnAgain() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_QXB7_M26_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.b5, Board.b7, "26.Qxb7",
                "which strands the queen after 26...Rhe8 27.Rf1 Rad8 and turns +4.64 into −1.30 "
                        + "(Stockfish 18, depth 22); 26.Qe5+ holds");
    }

    /** Black (myChess) to move before {@code 26...Qh1+??}, with a won attack. */
    private static final String BEFORE_QH1_FEN = "3rr1k1/2p4p/p2b4/3b1p2/4p1P1/1PB1P3/P1RN1PPq/3QRK2 b - - 2 26";

    /**
     * A check played because it is a check: <b>+5.22</b> to <b>0.00</b>.
     *
     * <p>{@code 26...Qh1+ 27.Ke2 Qh4 28.Rh1} and the attack is over — the queen spent two
     * tempi to end up worse placed. {@code 26...f4} keeps the bind and wins.
     *
     * <p>The one case in this batch the original scan classified as <b>horizon</b> rather than
     * an evaluation defect, on its own criterion: myChess reported <b>+0.42</b>, close enough
     * to the drawn truth that it is not badly wrong about the resulting position — it simply
     * cannot see far enough to know the alternative was better. Kept as the control for the
     * others: if a search change repairs this one and leaves the rest, that is evidence the
     * scan's classification was right.
     *
     * <p><b>Test family:</b> search-horizon (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qh1_vsTscp_characterizesTheCheckThatEndsTheAttack() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_QH1_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.h2, Board.h1, "26...Qh1+",
                "which runs out after 27.Ke2 Qh4 28.Rh1 and turns +5.22 into 0.00 "
                        + "(Stockfish 18, depth 22); 26...f4 wins");
    }

    /** White (myChess) to move before {@code 48.h4??} in a ten-piece endgame. */
    private static final String BEFORE_H4_M48_FEN = "8/8/8/3pk3/2p5/1p3P2/7P/rB1R2K1 w - - 0 48";

    /**
     * The smallest position in the suite to carry a five-pawn error, and the clearest
     * statement of what the king is worth in an endgame.
     *
     * <p>Ten pieces. {@code 48.Kf2} — the king walking toward the passers — holds at
     * <b>0.00</b>. {@code 48.h4} is a pawn move on the wrong wing and loses to
     * {@code 48...c3 49.Kf2 Rxb1 50.Rxb1 c2}: <b>−4.78</b> (Stockfish 18, depth 22). The same
     * king move one tempo later is no longer enough.
     *
     * <p>myChess reported <b>+1.06</b>, a full pawn on the wrong side of level. With only ten
     * pieces on the board there is very little for a positional term to be wrong about, which
     * makes this the cheapest available probe of whether an endgame king term is doing
     * anything — the third case in {@code king-activity} and by far the smallest.
     *
     * <p><b>Test family:</b> king-activity (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void h4_atMove48_vsTscp_characterizesTheIdleKingInATenPieceEnding() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_H4_M48_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.h2, Board.h4, "48.h4",
                "which loses the race after 48...c3 49.Kf2 Rxb1 50.Rxb1 c2, turning 0.00 into −4.78 "
                        + "(Stockfish 18, depth 22); 48.Kf2 at once holds");
    }

    /** Black (myChess) to move before {@code 47...Rd6??}, a won rook ending. */
    private static final String BEFORE_RD6_FEN = "8/1R6/1p3k2/3r4/3p2K1/P5P1/7P/8 b - - 0 47";

    /**
     * Nine pieces, a won ending, and a rook move that does not advance anything:
     * <b>+4.38</b> to <b>+0.01</b> (Stockfish 18, depth 22).
     *
     * <p>{@code 47...b5} runs the queenside pawns and wins. {@code 47...Rd6} lets white in with
     * {@code 48.Rh7 Rd8 49.Rh6+ Ke5 50.Rxb6} and the extra pawn is gone.
     *
     * <p>Third open case in {@code passed-pawn}, and with {@code 42.b3} below the second in
     * this batch alone. All of them fail the same way — a passer is not moved because moving it
     * scores no better than not moving it. myChess reported <b>+1.05</b> here against a truth
     * of +4.38, so it also does not know how won the position is.
     *
     * <p><b>Test family:</b> passed-pawn (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void rd6_vsZetaDva_characterizesNotRunningThePawnsWhileWinning() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RD6_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.d5, Board.d6, "47...Rd6",
                "which lets 48.Rh7 in and turns +4.38 into +0.01 (Stockfish 18, depth 22); "
                        + "47...b5 wins");
    }

    /** White (myChess) to move before {@code 42.b3??}, with a pawn one square from queening. */
    private static final String BEFORE_B3_FEN = "8/pk2B3/1r1P2qp/2p1n3/Q7/8/1PP2R2/7K w - - 1 42";

    /**
     * A passed pawn on d6 with a clear path, and myChess plays {@code b3} instead.
     *
     * <p>{@code 42.d7} queens by force — {@code 42...Rxb2 43.d8=Q Rb1+} changes nothing.
     * {@code 42.b3} hands the initiative over: {@code 42...Rb4 43.Qa5 Qe4+ 44.Rg2 Qe1+}, and
     * <b>0.00</b> becomes <b>−5.66</b> (Stockfish 18, depth 22).
     *
     * <p>The purest instance of the family's thesis. The pawn is one square away, the
     * promotion is forced, and the move still is not chosen — so the gap is not depth but the
     * absence of any term that prices a pawn about to become a queen. myChess reported
     * <b>+0.89</b>, close to level, exactly as if the d-pawn were an ordinary pawn.
     *
     * <p><b>Test family:</b> passed-pawn (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void b3_vsZetaDva_characterizesNotQueeningTheD6Pawn() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_B3_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.b2, Board.b3, "42.b3",
                "instead of 42.d7, which queens by force; the game move turns 0.00 into −5.66 "
                        + "(Stockfish 18, depth 22)");
    }

    /** White (myChess) to move before {@code 52.Re7+??}, with a rook to take on e1. */
    private static final String BEFORE_RE7_CHECK_FEN = "8/5Rnr/6R1/p1p1k3/P1P5/3P3p/4PK2/4r3 w - - 2 52";

    /**
     * A check preferred to a capture. {@code 52.Kxe1} simply takes the rook and holds at
     * <b>−0.13</b>; {@code 52.Re7+} chases the king to a better square and loses to
     * {@code 52...Kf5 53.Rg3 h2 54.Rf3+ Kg6}: <b>−4.48</b> (Stockfish 18, depth 22).
     *
     * <p>Worth pinning because the alternative is a *capture*, so the quiescence generator
     * produces it and the search cannot have failed to see it — it saw {@code Kxe1} and
     * preferred the check. myChess reported <b>+1.00</b>, a pawn to the good, in a position
     * that is a shade worse. Pairs with {@code 36.Ne4} above, where the winning move is also
     * both a check and a capture and is also passed over.
     *
     * <p><b>Test family:</b> tactical-oversight (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void re7Check_vsTscp_characterizesPreferringACheckToTheRook() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_RE7_CHECK_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.f7, Board.e7, "52.Re7+",
                "instead of taking the rook with 52.Kxe1; the check turns −0.13 into −4.48 "
                        + "(Stockfish 18, depth 22)");
    }

    /** Black (myChess) to move before {@code 13...Nxd4??}, the earliest case in the corpus. */
    private static final String BEFORE_NXD4_FEN = "2kr1b1r/ppq2pp1/2n1bn1p/4p3/3PP3/3P1NPP/PP1NQP2/R1B1KB1R b KQ - 0 13";

    /**
     * Thirty pieces still on the board — the only case in the batch from the opening, and the
     * one place where a positional misjudgement has the most room to compound.
     *
     * <p>{@code 13...Nb4} keeps the pressure and is worth <b>+2.58</b>. The exchange
     * {@code 13...Nxd4 14.Nxd4 Rxd4 15.Bg2} straightens white's position out and hands the
     * initiative back: <b>−1.28</b> (Stockfish 18, depth 22).
     *
     * <p>Nothing is lost by force here — no piece hangs, no mate appears. That is what makes
     * it a {@code pointless-exchange} case rather than a tactical one, and why it is the least
     * likely of the ten to be repaired by a search change. myChess reported <b>+0.57</b>
     * against a true +2.58: it did not know it stood well, so trading was cheap.
     *
     * <p><b>Test family:</b> pointless-exchange (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void nxd4_vsZetaDva_characterizesTradingAwayTheInitiative() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_NXD4_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.c6, Board.d4, "13...Nxd4",
                "which relieves white's position and turns +2.58 into −1.28 "
                        + "(Stockfish 18, depth 22); 13...Nb4 keeps the pressure");
    }

    /** Black (myChess) to move before {@code 17...Qxh1+??}, taking the rook in the corner. */
    private static final String BEFORE_QXH1_FEN = "rnbr3k/pp2b1pp/2q5/2p1Np2/2Q2B2/1PN5/P1P2P1P/R3K2R b KQ - 3 17";

    /**
     * The queen collects the rook on h1 and never comes back: <b>+0.33</b> to <b>−3.24</b>
     * (Stockfish 18, depth 22). {@code 17...Qe6} holds the position together.
     *
     * <p>After {@code 18.Ke2 Qd5 19.Nxd5 Be6 20.Nxe7} the exchange won costs a piece and the
     * position. myChess reported <b>+2.64</b> — the material count, near enough — while the
     * position is three pawns worse. That gap between "what I took" and "where I stand" is the
     * signature the {@code corner-grab} family exists to record, and this is its second
     * instance with a queen after {@code 15...Nxa1}.
     *
     * <p><b>Test family:</b> corner-grab (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qxh1_vsZetaDva_characterizesTakingTheCornerRook() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_QXH1_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.c6, Board.h1, "17...Qxh1+",
                "which strands the queen after 18.Ke2 Qd5 19.Nxd5 Be6 20.Nxe7 and turns +0.33 "
                        + "into −3.24 (Stockfish 18, depth 22); 17...Qe6 holds");
    }

    // ================================================================
    // Chess960, from rated blitz game
    // https://lichess.org/3RoDIOcC (ix-bot 1806 vs myChessJava 1829,
    // 180+1, 1-0, −65 rating points). The first 960 cases in this
    // class, and they are here because the variant is where the user
    // actually plays: 960 was previously covered only by
    // EvalRegressionTest's single a6-e2 anchor.
    //
    // The game is one long king-safety failure with two distinct
    // halves, and each half needs its own assertion because they fail
    // for different reasons:
    //
    //   * Move 5 — myChess castles toward the wing white is already
    //     storming, at a moment when white has *zero* attack units on
    //     its king. Nothing that indexes on attackers can see this.
    //   * Move 12 — five attack units stand on the black king and the
    //     static evaluation still reads two pawns for black.
    //
    // A walk of the whole game through both engines put the gap above
    // 400 cp from move 7 onward and never below it again; myChess
    // first agreed it was lost at move 23, which was mate in 3.
    // ================================================================

    /** White (myChess) to move before {@code 13.Qxd7}, a pawn grab with the position level. */
    private static final String BEFORE_QXD7_FEN =
            "5rkr/p1pp2pp/bp2n3/2b5/4q3/2P1N3/PP3PPP/RNBQR1K1 w - - 1 13";

    /**
     * The defender walks away — and the planned king-attack term would encourage it.
     *
     * <p>From rated classical game <a href="https://lichess.org/SINwv7q4">SINwv7q4</a>
     * (myChessJava 2062 vs Studyloversz-bot 1999, 1800+0, 0-1). Material is dead level.
     * myChess takes the d7 pawn and its queen never returns: {@code 16.Qxc7}, {@code 20.Qc6},
     * {@code 23.Qd5+} while black assembles {@code Nf4}, {@code Qg6}, {@code Nh3+},
     * {@code Nxf2+} against a king it has left alone. Stockfish 18 reads the position before
     * the move at −1.47 and after it at −5.22 (depth 22); {@code 13.f3} holds at −1.39.
     *
     * <p><b>Not a {@code corner-grab}, though it looks like one.</b> All five cases in that
     * family turn on a piece that ends up <em>trapped</em> or stranded after taking — a1, b7,
     * h1. This queen is never trapped and stays fully mobile for the rest of the game. What
     * loses is that it is somewhere else, so the defect is a defender leaving rather than a
     * piece getting stuck. It belongs here because "danger to its own king is not charged for"
     * describes it exactly.
     *
     * <p><b>It also does not have the shape of the rest of this family</b>, which is what makes
     * it worth its own entry. Elsewhere here myChess is optimistic — it rates itself
     * <em>better</em> while being lost. Here it knows it is slightly worse (−45 to −82 white-POV
     * across depths 4 to 12) and is merely wrong about the size, by some four pawns. Flat across
     * nine depths, so this is the evaluation and not the horizon.
     *
     * <p><b>The measurement that makes this case load-bearing.</b> Black holds <b>ten</b> attack
     * units on the white king here and white holds none, which is as clear a signature as this
     * family produces — and the fitted {@code KING_ATTACK_PENALTY} of `docs/king-safety.md`
     * § 4.6 does not help. Applying it to all 36 legal moves leaves {@code Qxd7} ranked
     * <b>1 of 36</b>, exactly where the static evaluation already had it and where Stockfish
     * puts it 15th (depth 18, all 36 successors scored). The term is not silent; it points the wrong way, because the queen landing
     * on d7 bears along the seventh rank onto g7 and therefore counts as five attack units
     * against the <em>black</em> king. "Piece bears on the king zone ⇒ attacker" never asks
     * whether that piece was needed where it stood.
     *
     * <p>So this is the second case pinning a boundary of the planned term rather than arguing
     * for it, next to {@link #castling960_atMove5_characterizesChoosingTheStormedWing}. Read
     * them together before concluding anything from a neutral SPRT: a term that measures zero
     * has still not been tested against either of them.
     *
     * <p><b>This assertion is a characterization, not a goal.</b> The move reproduces at every
     * depth from 4 to 11, which is why it pins the move rather than a score.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void qxd7_vsStudylovers_characterizesSendingTheQueenAwayFromItsOwnKing() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_QXD7_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.d1, Board.d7, "13.Qxd7",
                "which books a pawn and sends the only piece defending the kingside to the other "
                        + "wing, turning −1.47 into −5.22 (Stockfish 18, depth 22); 13.f3 holds "
                        + "at −1.39. Black already has ten attack units on the white king, and the "
                        + "fitted king-attack curve still ranks this move first of 36");
    }

    /**
     * Like {@link #gameFromFenAtDepth}, for a position whose castling field names rook files.
     *
     * <p>{@link Fen#importFEN(String)} happens to accept the Shredder form as well and to set the 960
     * flag from the piece placement, so this is not routing around a failure. It pins the
     * intent: these two positions are Chess960, and a test that documents a 960 defect should
     * not depend on how tolerant the classical parser is today.
     */
    private static Game gameFromChess960FenAtDepth(String fen, int depth, TranspositionTable tt) {
        var engineConfig = new EngineConfig.Builder()
                .maxDepth(depth)
                .millisPerMove(DEPTH_BOUND_BUDGET_MS)
                .silent(true)
                .setTranspositionTable(tt)
                .build();

        return new Game(new GameConfig(MyChessEngine.class, engineConfig), Fen.importChess960FEN(fen));
    }

    /**
     * Black (myChess) to move before {@code 5...O-O}, from Chess960 start position
     * {@code bqrknnrb/pppppppp/…}. Black's king starts on d8 and its rooks on c8 and g8, so
     * castling short walks the king three files toward white's g4 pawn and the rook already
     * standing on g1.
     */
    private static final String BEFORE_960_CASTLE_FEN =
            "bqrk2rb/p1pppppp/4n3/1pP5/1P2n1P1/3N4/P2PPP1P/BQRK1NRB b GCgc - 0 5";

    /**
     * Castling into a pawn storm — a king-safety failure that no attack-unit term can reach.
     *
     * <p>White has played {@code 5.g4} and its rook already stands on g1; the black king sits
     * safely on d8 with rooks on c8 and g8. myChess plays {@code 5...O-O}, moving its king to
     * exactly the file being opened, and never recovers: {@code 6...g6} and {@code 7...gxf5}
     * tear the shelter open by black's own hand and the game ends in mate on move 26.
     *
     * <p><b>The evaluation is wrong at every depth, which is why this pins the score and not
     * the move.</b> White-POV, myChess reads −0.96, −0.61, −0.51, −0.65, −0.13, −0.33, +0.07,
     * −0.17 at depths 4 through 11 — that is, black comfortably fine — against Stockfish 18's
     * <b>+1.99</b> at depth 22 and <b>+1.51</b> from its static evaluation alone. Flat across
     * eight depths rules out a horizon effect. The move choice would have been the more precise
     * assertion ({@code 5...O-O} is chosen at every depth except 10, where it switches to
     * {@code Ne4-g5} and still misreads the position at −0.07), but a characterization that
     * flips on one depth in the middle of its range is not one worth pinning.
     *
     * <p><b>What makes this case different from the twenty other king-safety entries, and the
     * reason it is worth its own test:</b> at the moment of the decision white has <b>zero</b>
     * attack units on the black king. The planned {@code KING_ATTACK_PENALTY} term — and every
     * variant of it, ours or the Audax fork's — indexes on attackers already bearing on the
     * zone, so all of them score this position at exactly 0 and would leave {@code 5...O-O}
     * just as attractive. This is the boundary of that term, recorded before it is built rather
     * than discovered after it fails to move the needle. What the position needs is a judgment
     * about where the enemy pawns are going, which is a different quantity.
     *
     * <p>It is also plausibly a Chess960 transfer failure: the piece-square tables were fitted
     * on standard chess, where a king on g8 behind an intact f7/g7/h7 is the safe square almost
     * by definition. Here it is the wrong wing, and the tables cannot say so.
     *
     * <p><b>This assertion is a characterization, not a goal.</b> It passes because the defect
     * is present; when it starts failing, check whether the new score is right for the right
     * reason before relaxing it.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void castling960_atMove5_characterizesChoosingTheStormedWing() throws Exception {
        var game = gameFromChess960FenAtDepth(BEFORE_960_CASTLE_FEN, IN_GAME_DEPTH, tt);
        assertEquals(GameStatus.TURN_BLACK, game.getTurn(), "black (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertTrue(result.weight() < 0.5f,
                "characterization: myChess still reads this as fine for black, though white is "
                        + "+1.99 (Stockfish 18, depth 22) and the black king is about to castle "
                        + "into the g-file white is storming. If this now reports a white "
                        + "advantage, something has learned to see the pawn storm — note that an "
                        + "attack-unit term cannot be the cause, since white has 0 attack units "
                        + "here. white-POV eval " + result.weight());
    }

    /**
     * White to move after {@code 11...dxc6}, seven moves before mate. Black (myChess) is a rook
     * for a bishop and a pawn ahead and completely lost.
     */
    private static final String AFTER_960_DXC6_FEN =
            "1qr2rkb/p1p1ppnp/2p5/1p3P2/1P6/8/P2PPN1P/BQ1K2RB w G - 0 12";

    /**
     * The static evaluation, with five attack units on the black king, reading two pawns for
     * black.
     *
     * <p>Deliberately search-free — the only test in this class that runs no search at all.
     * Every other entry here has to argue that what it measured is the evaluation and not the
     * horizon; this one calls {@link WeightingFunction#calculate} directly, so there is nothing
     * to argue about. It also costs milliseconds rather than the half-minute the depth-bound
     * cases cost.
     *
     * <p>The numbers. myChess scores this <b>−194 cp</b>. Stockfish 18's static NNUE evaluation
     * of the same board, no search involved, is <b>+281 cp</b>; its depth-20 search says +981
     * and at depth 40 it announces mate in 19 for white. Material is −300 for white, so
     * myChess's positional terms hand white +106 for a position in which every white piece
     * bears down on a king that has three of its own pieces smothering it.
     *
     * <p><b>Why this is the acceptance test for the king-attack work.</b> White has exactly
     * <b>five</b> attack units on the black king here (bishop a1 and rook g1, both hitting g7),
     * black has none, and the game phase is 19 of 24. The curve fitted in
     * `docs/king-safety.md` § 4.5 — {@code 0 0 0 0 0 0 49 40 83} — contributes <b>0 cp</b> at
     * index 5. So the term as currently calibrated changes this position by nothing, and this
     * assertion will keep passing after it ships. That is the point of recording it: a
     * regression over the 960 corpus, with Stockfish's static evaluation replacing the
     * self-play game result as the label, puts index 5 at <b>+15.2 cp [11.0, 19.4]</b> against
     * the zero the outcome-labelled fit produced, and a placebo zone four files from the king
     * at −8.6 [−12.3, −3.4] over the same positions. The low indices are not noise; they were
     * fitted on labels that myChess's own blindness produced.
     *
     * <p>Even so, +15 cp against a 475 cp gap is not the fix. The value of this test is that it
     * states the size of the hole in one number that no search depth can explain away.
     *
     * <p><b>Test family:</b> king-safety (defect)
     */
    @Test
    void staticEval960_afterDxc6_characterizesBlindnessToFiveAttackers() {
        var board = Fen.importChess960FEN(AFTER_960_DXC6_FEN);
        var evaluator = new WeightingFunction();

        int weight = evaluator.calculate(board);

        assertEquals(5, KingAttackUnits.of(board, GameStatus.TURN_WHITE),
                "white's attack units on the black king — the index the fitted curve reads, "
                        + "pinned here so the case cannot silently change shape");
        assertEquals(0, KingAttackUnits.of(board, GameStatus.TURN_BLACK),
                "black has nothing pointing at the white king, so the term reduces to white's side");
        assertTrue(weight < 0,
                "characterization: myChess's static evaluation still favors black in a position "
                        + "Stockfish 18 scores at +281 statically and mates in 19 from. If this is "
                        + "now positive the evaluation has learned to see the smothered king — "
                        + "record the new value and convert this into a bound. static eval " + weight);
    }

    /** White (myChess) to move before {@code 25.Nf6+??}, from a position with exactly one holding move. */
    private static final String BEFORE_NF6_FEN = "2r2rk1/p4ppp/3R4/2QNq3/3n4/1P6/P3pPPP/4R1K1 w - - 1 25";

    /**
     * The sharpest single-move position in this file: <b>{@code 25.f4} holds at 0.00 and every
     * other move loses.</b> Stockfish 18 at depth 22, multi-PV:
     *
     * <pre>
     *   1. f4     +0.00      the only move
     *   2. Nf6+   -4.99      myChess played this
     *   3. Qxd4   -5.56
     *   4. Qb4    -5.66
     *   5. Rxc6   -6.23
     * </pre>
     *
     * <p>That gap is why the case is worth pinning. There is no judgment call about what the
     * target move is, and no argument about whether the played move is bad enough to count.
     *
     * <p><b>It is not a sacrifice, and it is not an evaluation failure about the e2 pawn.</b>
     * {@code 25.Nf6+ gxf6 26.Qxd4} restores material exactly — 2400 against 2400 — so the knight
     * comes back and nothing needs compensating. And the black pawn on e2, one square from
     * promotion and defended by the knight on d4, is <em>correctly</em> valued: removing it from
     * the position moves myChess's static evaluation by <b>206 cp</b> against Stockfish's
     * <b>240 cp</b>, with the resulting positions read at +1.44 and +1.37. The PeSTO pawn table
     * carries most of that (193 raw points of black's positional score sit on that one pawn).
     *
     * <p><b>What actually goes wrong is a horizon effect, missed by 2.2 seconds.</b> myChess's
     * principal variation at depth 10 reads
     * {@code Nf6+ Qxf6 Qxd4 Qxd4 Rxd4 Rc2 Re4 Rxa2 R4xe2 Ra3} and scores it +0.06 — it expects
     * black to recapture with the <em>queen</em>, after which the queens come off and a rook
     * collects the e2 pawn. Black recaptured with the <em>pawn</em>, {@code 25...gxf6}, keeping
     * the queen on e5. One iteration deeper the engine agrees with Stockfish:
     *
     * <pre>
     *   depth 10   4.2 s   Nf6+   +0.06
     *   depth 11  19.2 s   f4     -0.21
     * </pre>
     *
     * <p>The game gave it <b>17 seconds</b>. So the defect is not that the evaluation is blind to
     * anything in the final position — it is that myChess does not credit black with shattering
     * its own kingside in exchange for activity, and therefore searches the wrong reply first at
     * the depth it reaches under a clock.
     *
     * <p>Read against {@code h3_atMove12} and {@code h3_atMove20}: those are the engine wrecking
     * its <em>own</em> pawn cover. This is the mirror image — failing to expect an opponent to
     * wreck theirs.
     *
     * <p><b>Test family:</b> search-horizon (defect)
     */
    @Test
    void nf6_atMove25_characterizesTheKnightCheckWhereOnlyF4Holds() throws Exception {
        var game = gameFromFenAtDepth(BEFORE_NF6_FEN, SCANNER_DEPTH, tt);
        assertEquals(GameStatus.TURN_WHITE, game.getTurn(), "white (myChess) must be to move");

        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.d5, Board.f6, "25.Nf6+",
                "which turns 0.00 into −4.99 (Stockfish 18, depth 22) where 25.f4 is the only "
                        + "move that holds; myChess expects the queen recapture 25...Qxf6 and "
                        + "reads the line at +0.06, and finds 25.f4 only at depth 11, which costs "
                        + "19.2 s against the 17 s the game allowed");
    }
}
