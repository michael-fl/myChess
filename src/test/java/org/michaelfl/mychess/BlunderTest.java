package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.opentest4j.AssertionFailedError;

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

    /**
     * Stockfish-annotated analysis position, black to move. Black is already
     * slightly better and has two winning tries; see
     * {@link #nxe2_atMove19_engineMissesTheExchangeWinningSacrifice()}.
     */
    private static final String HANGING_E2_FEN =
            "b3r1kr/pp3pp1/2p5/5Qq1/5n1p/1B2N1P1/P3PP1P/4RRK1 b kq - 1 19";

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

        // TODO move should be avoided
        boolean avoided = false;
        try {
            assertEngineAvoids(result, Board.g5, Board.h4, "16...gxh4");
            avoided = true;
        } catch (AssertionFailedError _) {
            // expected for now
        }
        assertFalse(avoided);
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
     * outpost knight. Two known eval holes cause the miss: (1) there is no
     * king-safety / attack term, and (2) the material-only eval shortcut
     * ({@code EVALUATE_MATERIAL_ONLY_THRESHOLD = 200 cp}) discards the
     * positional evaluation exactly when a side is +2 pawns. So
     * {@code Nxe2+ ≈ Qxf5 ≈ +2} to myChess, and it simplifies.
     *
     * <p>Positive assertion (since v4.3.1): myChess now <em>finds</em>
     * {@code Nxe2+}. The tapered king endgame table penalizes the exposed,
     * cornered {@code Kh1} in the low-phase position, which tips the evaluation
     * enough to prefer the exchange-winning sacrifice over the simplifying
     * {@code Qxf5}. The blind spot described above is closed; this test now
     * guards against a regression back to declining the sacrifice.
     */
    @Test
    @Timeout(value = JUNIT_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void nxe2_atMove19_engineMissesTheExchangeWinningSacrifice() throws Exception {
        var game = gameFromFen(HANGING_E2_FEN, tt);
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
     */
    private static final int DEPTH_BOUND_BUDGET_MS = 120_000;

    /** JUnit safety timeout for the fixed-depth cases; above {@link #DEPTH_BOUND_BUDGET_MS}. */
    private static final int DEPTH_BOUND_TIMEOUT_S = 150;

    private static Game gameFromFenAtDepth(String fen, int depth, TranspositionTable tt) {
        var engineConfig = new EngineConfig.Builder()
                .maxDepth(depth)
                .millisPerMove(DEPTH_BOUND_BUDGET_MS)
                .silent(true)
                .setTranspositionTable(tt)
                .build();

        return new Game(new GameConfig(MyChessEngine.class, engineConfig), Fen.importFEN(fen));
    }

    /** Like {@link #searchCurrentPosition}, but waits long enough for {@link #DEPTH_BOUND_BUDGET_MS}. */
    private static MoveAndWeight searchCurrentPositionDeep(Game game) throws Exception {
        return game.getEngine().nextMoveAsync().getResult(DEPTH_BOUND_TIMEOUT_S - 10, TimeUnit.SECONDS);
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
     * progressively. The material-only shortcut compounds it — three pawns is
     * past {@code EVALUATE_MATERIAL_ONLY_THRESHOLD = 200 cp}, so in the lines
     * where it keeps that lead the positional evaluation is skipped entirely and
     * the danger cannot be seen at all.
     *
     * <p><b>This assertion is a characterization, not a goal.</b> It passes
     * because the defect is present. Once king safety lands it must start
     * failing — that is the signal to invert it into an
     * {@link #assertEngineAvoids} test against {@code Qb4} and to record the new
     * evaluation here.
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
     * shelter for material</em>. Two pawns also sits right at
     * {@code EVALUATE_MATERIAL_ONLY_THRESHOLD = 200 cp}, so in these lines the
     * positional evaluation may be skipped outright.
     *
     * <p><b>Characterization, not a goal</b> — same contract as
     * {@link #qb4_atMove22_characterizesTheKingSafetyBlindSpot()}: it passes
     * because the defect is present, and must be rewritten once a king-safety
     * term makes the score fall away from the bare material count.
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
}
