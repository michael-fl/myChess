package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for the material-only evaluation shortcut
 * ({@link org.michaelfl.mychess.engines.PositionSearch#EVALUATE_MATERIAL_ONLY_THRESHOLD},
 * 200 centipawns), pinned through the engine's <em>deep</em> evaluation (a real
 * search, not a bare {@link WeightingFunction} call).
 *
 * <p>All three positions are contrived so that recapturing a hanging black piece
 * swings the running {@code materialDelta} past the 200 cp threshold. From that
 * point every leaf in the recapture subtree takes the material-only path in
 * {@code QuiescenceSearch#calculatePositionWeight}: the positional evaluation
 * ({@link WeightingFunction} + {@code PieceSquareTables}) is skipped and only the
 * raw material balance is returned. The tests probe three faces of that behavior.
 *
 * <h2>1. Blind to a positional advantage &mdash; {@code developmentLead...}</h2>
 *
 * <pre>{@code r1bq1bnr/ppppkppp/4p3/8/3nP3/2NPB3/PPP1BPPP/R1Q1R1K1 w - - 0 1}</pre>
 *
 * <p>White's only recapture is {@code Bxd4}. Afterward material is dead equal
 * but White is hugely better developed (Stockfish ~+1.6). Because the deciding
 * advantage is <em>positional</em>, the shortcut discards it and the deep eval
 * reads ~0.
 *
 * <h2>2. Sharp on a material difference &mdash; {@code trappedKnight...}</h2>
 *
 * <pre>{@code rrbqnk2/ppppbpp1/3p3p/N7/1PP1P3/Pn1P4/Q2B1PPP/1R1BR1K1 w - - 0 1}</pre>
 *
 * <p>Four white pieces can recapture the knight on b3, and
 * all four leave material equal, so one might expect the eval, again stuck at ~0,
 * to leave the moves indistinguishable. It does not: it plays {@code Nxb3}, the
 * only recapture that also rescues White's trapped a5 knight. Every other
 * recapture leaves that knight to be won by {@code ...b6} &mdash; a <em>material</em>
 * loss the shortcut evaluates perfectly. Material is the one dimension the
 * shortcut never blinds.
 *
 * <h2>3. Blindness turns into a real blunder &mdash; {@code queenRecaptureTrap...}</h2>
 *
 * <pre>{@code 1rnbrkb1/ppp1pppp/8/N2P4/1P2P3/1qP5/P2BBPPP/1R2R1K1 w - - 0 1}</pre>
 *
 * <p>This is where the blindness bites. A black queen hangs on b3, attacked by a
 * pawn (a2), a knight (a5) and a rook (b1). Recapturing the queen swings
 * {@code materialDelta} by ~900 cp, so far past the threshold that no realistic
 * number of later pawn trades can bring it back &mdash; the shortcut stays engaged
 * through the whole subtree and all three recaptures tie at exactly the material
 * value (+1.0, White up a pawn). With the eval a genuine tie, the decision falls
 * to move ordering, and {@code MoveSorter} ranks captures by {@code captured -
 * mover} (cheapest attacker first). So the <em>pawn</em> capture {@code axb3} is
 * tried first and, being only tied and never beaten (the search improves on a
 * strict {@code >}), is never displaced. myChess plays {@code axb3} &mdash; the
 * objectively <em>worst</em> of the three recaptures (Stockfish: {@code Nxb3}
 * +4.2 vs {@code axb3} +3.1): it doubles White's b-pawns and leaves the offside
 * a5 knight in place, both invisible to the shortcut.
 *
 * <h2>4. The exchange sacrifice it will not play &mdash; {@code qxb5AtMove36...}</h2>
 *
 * <pre>{@code 1Qb2r1k/8/2BR1n1p/1pp1Rp1q/5p2/2P4P/1PP2PP1/6K1 w - - 1 36}</pre>
 *
 * <p>The only case here taken from a real game &mdash; rated classical
 * <a href="https://lichess.org/5z59X4aB">5z59X4aB</a> (myChessJava 2013 vs Bot5551
 * 1827, 1440+3, 1-0), move 36 &mdash; and the sharpest of the four. Black has just
 * blundered with {@code 35...Nf6}, leaving the knight en prise while {@code Rf8} is
 * the sole defender of {@code Bc8}, which {@code Qb8} attacks and behind which
 * {@code Bc6} covers {@code e8}. So {@code 36.Rxf6} simply wins a piece:
 * {@code 36.Rxf6 Rxf6 37.Bf3 Qh4 38.Qxc8+ Kg7 39.Re7+ Rf7}, Stockfish
 * <b>+7.76</b>. myChess played <b>{@code 36.Qxb5}</b>, a free pawn and a winning
 * move (<b>+5.71</b>) that is two pawns short of the winning move.
 *
 * <p>Its score is <b>+6.00</b> at depths 8, 9 and 10, and that number is not an
 * evaluation but a piece count. The depth-9 line is
 * {@code 36.Qxb5 Qf7 37.Qxc5 f3 38.gxf3 Kg7 39.Kh2 Bd7}; from the root balance of
 * +300 cp its three pawn captures give a leaf balance of exactly +600. Note that
 * the threshold applies to the swing <em>since the root</em>, not to the balance at
 * it, and that the comparison is a strict {@code >}: the delta reads +100 after
 * {@code Qxb5}, +200 after {@code Qxc5} (the boundary is spared exactly), and only
 * {@code gxf3} at +300 crosses and returns raw material.
 *
 * <p>The tell is the roundness, and it holds on the other branch too: searching
 * from the position after {@code 36.Rxf6 Rxf6} also reports exactly +600, again the
 * leaf balance of its own line ({@code Qxc8+ Kh7 Bf3 Qf7 Rxc5 Qa2 Rxb5 Qb1+}, from
 * +100 up three captures). Depth 11 is the control that proves the rule: it
 * switches to {@code Re7} and reports +6.27, an unround number, because that line
 * keeps the delta inside the band and the positional evaluation runs.
 *
 * <p>So the root comparison happens almost entirely in material. myChess scores
 * {@code Rxf6} at +5.44 against +6.00 for {@code Qxb5}, rejecting the
 * piece-winning move by 56 cp. {@code Rxf6} is an exchange sacrifice &mdash; rook
 * for knight, {@code -200} after the recapture, of which {@code Qxc8} returns 300
 * &mdash; and what makes it stronger is positional: rook on the seventh, bishop
 * pair against nothing, a bare black king. Exactly the component the shortcut
 * discards.
 *
 * <h2>Takeaway</h2>
 *
 * <p>The material-only shortcut is an intended, load-bearing pruning heuristic:
 * it blinds the engine to <em>positional</em> distinctions but never to
 * <em>material</em> ones. That blindness is usually harmless — the move is forced
 * (1), or material decides it anyway (2). If the shortcut or the capture ordering
 * (e.g. an SEE-based sort) ever changes, the affected assertion turns red and
 * should be updated.
 *
 * <p>Cases 3 and 4 are the two ways it does bite, and they are not the same. This
 * class used to conclude that a genuine blunder needs <em>both</em> a material tie
 * <em>and</em> the best move running against the cheapest-attacker ordering, which
 * is case 3. Case 4 shows that is too narrow: nothing ties there and no ordering
 * accident is needed, because the best move gives material <em>back</em>, and that
 * is the one thing a material yardstick must always score as a loss. Case 3 is the
 * more dangerous in practice, since a tie is settled by whatever the ordering tries
 * first; case 4 is the more fundamental, because no ordering change can reach it.
 *
 * <p><b>None of this is a defect to fix on sight.</b> Removing the shortcut
 * measured <b>-34 Elo</b> (<a href="../docs/roadmap.md">roadmap § 12.18</a>), and
 * the error in case 4 cost nothing on the scoreboard &mdash; the game was won. These
 * tests are the concrete cost side of a trade that pays: if the search cluster ever
 * revisits the threshold, case 4 is a position where it demonstrably picks the worse
 * move, which is worth more than the Elo number alone. Should that assertion start
 * failing after such a change, it is information rather than a regression &mdash;
 * check whether {@code Rxf6} is now found before touching it.
 *
 * @author Michael Fleischhauer
 */
class MaterialOnlyShortcutEvalTest {

    /** Fixed search depth so the deep evaluation is deterministic across runs. */
    private static final int SEARCH_DEPTH = 8;

    /**
     * Generous per-move budget so the {@link #SEARCH_DEPTH}-ply search always
     * completes and depth — not the clock — is the bound (deterministic score).
     */
    private static final int SEARCH_BUDGET_MS = 60_000;

    /**
     * Tolerance around 0 for the material-only collapse: comfortably clear of
     * both positions' large Stockfish advantages, so the tests' meaning ("the
     * positional surplus is erased") is unambiguous while tolerating minor
     * quiescence leakage. Observed on v4.2.3: both positions read exactly 0.00.
     */
    private static final float COLLAPSE_TOLERANCE = 0.10f;

    // --- Position 1: shortcut blind to a positional (development) advantage ---

    /** Black knight hanging on d4, White far better developed. */
    private static final String DEVELOPMENT_LEAD_FEN =
            "r1bq1bnr/ppppkppp/4p3/8/3nP3/2NPB3/PPP1BPPP/R1Q1R1K1 w - - 0 1";

    /** White's only recapture on d4. */
    private static final String DEVELOPMENT_LEAD_RECAPTURE = "Bxd4";

    /** Stockfish's white-POV evaluation of position 1 (development lead). */
    private static final float STOCKFISH_DEVELOPMENT_LEAD = 1.6f;

    // --- Position 2: shortcut sharp on a material (trapped-knight) difference ---

    /** Black knight hanging on b3; White's own a5 knight is all but trapped. */
    private static final String TRAPPED_KNIGHT_FEN =
            "rrbqnk2/ppppbpp1/3p3p/N7/1PP1P3/Pn1P4/Q2B1PPP/1R1BR1K1 w - - 0 1";

    /** The only recapture that also rescues the trapped a5 knight. */
    private static final String TRAPPED_KNIGHT_RECAPTURE = "Nxb3";

    /** Stockfish's white-POV evaluation of position 2 after Nxb3. */
    private static final float STOCKFISH_TRAPPED_KNIGHT = 2.3f;

    // --- Position 3: material tie + cheapest-attacker ordering pick the WORSE move ---

    /**
     * Black queen hanging on b3, attacked by a pawn (a2), a knight (a5) and a
     * rook (b1). All three recaptures keep material equal (White up a pawn), so
     * the queen-sized {@code materialDelta} swing keeps the shortcut engaged and
     * ties them at +1.0.
     */
    private static final String QUEEN_RECAPTURE_TRAP_FEN =
            "1rnbrkb1/ppp1pppp/8/N2P4/1P2P3/1qP5/P2BBPPP/1R2R1K1 w - - 0 1";

    /** The inferior pawn recapture myChess actually plays (cheapest attacker). */
    private static final String TRAP_INFERIOR_MOVE = "axb3";

    /** The objectively best recapture (documentation only; not what myChess plays). */
    private static final String TRAP_BEST_MOVE = "Nxb3";

    /** Stockfish white-POV evals: the best recapture vs. the one myChess plays. */
    private static final float STOCKFISH_TRAP_BEST = 4.2f;
    private static final float STOCKFISH_TRAP_INFERIOR = 3.1f;

    /** The material-only tie value the three recaptures collapse to (White +1 pawn). */
    private static final float TRAP_TIE_WEIGHT = 1.0f;
    private static final float TRAP_TIE_TOLERANCE = 0.20f;

    /** Case 4: white to move, a piece up, black's knight just planted on f6 en prise. */
    private static final String EXCHANGE_SACRIFICE_FEN =
            "1Qb2r1k/8/2BR1n1p/1pp1Rp1q/5p2/2P4P/1PP2PP1/6K1 w - - 1 36";

    /** The pawn grab myChess prefers, and the piece-winning exchange sacrifice it declines. */
    private static final String EXCHANGE_SACRIFICE_PAWN_GRAB = "Qxb5";
    private static final String EXCHANGE_SACRIFICE_BEST_MOVE = "Rxf6";

    private static final float STOCKFISH_EXCHANGE_SACRIFICE = 7.76f;
    private static final float STOCKFISH_PAWN_GRAB = 5.71f;

    /**
     * The score myChess reports for {@code Qxb5}: exactly six pawns, the material
     * balance at the leaf of its own principal variation. Pinned to the pawn because
     * the <em>exactness</em> is the finding — a positional evaluation does not land
     * on a whole number of pawns.
     */
    private static final float PURE_MATERIAL_WEIGHT = 6.0f;

    /** Tolerance for the above: tight enough that any positional contribution breaks it. */
    private static final float PURE_MATERIAL_TOLERANCE = 0.001f;

    /**
     * Case 1 of the class comment, which carries the full analysis: the move is forced and
     * correct, and the evaluation of the resulting position is what fails.
     *
     * <p><b>Test family:</b> material-only-shortcut (defect)
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void developmentLeadIsErasedByTheMaterialOnlyShortcut()
            throws InterruptedException, ExecutionException, TimeoutException {

        Eval eval = deepEval(DEVELOPMENT_LEAD_FEN);

        // The engine plays correctly here (Bxd4 is the only recapture) — the
        // blind spot is not the move.
        assertEquals(DEVELOPMENT_LEAD_RECAPTURE, eval.move(),
                "engine must recapture the hanging knight on d4 to restore material equality");

        // ...yet it scores the resulting equal-material position at ~0: the
        // recapture's > 200 cp material swing trips the material-only shortcut,
        // discarding White's decisive development lead (SF ~+1.6).
        assertTrue(Math.abs(eval.weight()) <= COLLAPSE_TOLERANCE,
                "deep eval collapses to ~0 via the material-only shortcut instead of Stockfish's ~+"
                        + STOCKFISH_DEVELOPMENT_LEAD + " for White; got " + ChessUtil.weightToString(eval.weight()));
    }

    /**
     * Case 2 of the class comment, which carries the full analysis. The one case here that
     * asserts myChess doing the right thing: it marks the <em>limit</em> of the blindness,
     * since material is the dimension the shortcut never discards.
     *
     * <p><b>Test family:</b> material-only-shortcut (guard)
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void trappedKnightRecaptureIsFoundThroughTheMaterialDifference()
            throws InterruptedException, ExecutionException, TimeoutException {

        Eval eval = deepEval(TRAPPED_KNIGHT_FEN);

        // Four white pieces can take on b3 and all keep material equal, yet the
        // engine does not grab the first-generated recapture: it plays Nxb3, the
        // only one that also rescues the trapped a5 knight. Every other recapture
        // leaves that knight to be won by ...b6 — a material loss the shortcut
        // evaluates correctly, so the four recaptures are not tied at 0.
        assertEquals(TRAPPED_KNIGHT_RECAPTURE, eval.move(),
                "engine must recapture with the trapped a5 knight (Nxb3); any other recapture loses it to ...b6");

        // The eval itself still reads ~0: the ~+2.3 edge remaining after Nxb3 is
        // positional and stays invisible to the shortcut — material discriminates
        // the move, positional value never reaches the score.
        assertTrue(Math.abs(eval.weight()) <= COLLAPSE_TOLERANCE,
                "deep eval stays at ~0 (shortcut blind to the positional surplus) instead of Stockfish's ~+"
                        + STOCKFISH_TRAPPED_KNIGHT + " for White; got " + ChessUtil.weightToString(eval.weight()));
    }

    /**
     * Case 3 of the class comment, which carries the full analysis: a material tie the move
     * ordering then resolves in favor of the worst of the three recaptures.
     *
     * <p><b>Test family:</b> material-only-shortcut (defect)
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void queenRecaptureTrapPicksTheInferiorPawnCapture()
            throws InterruptedException, ExecutionException, TimeoutException {

        Eval eval = deepEval(QUEEN_RECAPTURE_TRAP_FEN);

        // The blunder: three pieces recapture the hanging queen, all keep material
        // equal, so the queen-sized delta swing keeps the shortcut engaged and
        // ties them at +1.0. MoveSorter orders captures by (captured - mover), so
        // the cheapest attacker (the pawn) is tried first and, only tied and never
        // beaten, is kept. myChess plays axb3 — the objectively worst recapture
        // (SF Nxb3 +4.2 vs axb3 +3.1: axb3 doubles the b-pawns and keeps the
        // offside a5 knight), a difference the material-only shortcut cannot see.
        assertEquals(TRAP_INFERIOR_MOVE, eval.move(),
                "material-only tie + cheapest-attacker ordering must make myChess play the inferior pawn recapture "
                        + TRAP_INFERIOR_MOVE + " instead of the objectively best " + TRAP_BEST_MOVE
                        + " (SF " + STOCKFISH_TRAP_BEST + " vs " + STOCKFISH_TRAP_INFERIOR + ")");

        // The three recaptures collapse to the pure material value (White up one
        // pawn); the ~1.1-pawn positional gap between them is invisible.
        assertTrue(Math.abs(eval.weight() - TRAP_TIE_WEIGHT) <= TRAP_TIE_TOLERANCE,
                "the recaptures tie at the material-only value (~+" + TRAP_TIE_WEIGHT
                        + ", White up a pawn); got " + ChessUtil.weightToString(eval.weight()));
    }

    /**
     * Case 4 of the class comment, which carries the full analysis. Kept here rather than in
     * {@code BlunderTest} because the mechanism, not the provenance, is what someone chasing
     * this behavior will search for.
     *
     * <p><b>Test family:</b> material-only-shortcut (defect)
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void qxb5AtMove36GrabsThePawnInsteadOfTheExchangeSacrifice()
            throws InterruptedException, ExecutionException, TimeoutException {

        Eval eval = deepEval(EXCHANGE_SACRIFICE_FEN);

        // The blunder: Rxf6 wins a piece outright, because the f8 rook is the only
        // defender of the c8 bishop that Qb8 attacks. myChess takes the free pawn
        // instead, a winning move two pawns short of the winning move.
        assertEquals(EXCHANGE_SACRIFICE_PAWN_GRAB, eval.move(),
                "characterization: myChess must still grab the pawn with " + EXCHANGE_SACRIFICE_PAWN_GRAB
                        + " (SF +" + STOCKFISH_PAWN_GRAB + ") rather than win the piece with "
                        + EXCHANGE_SACRIFICE_BEST_MOVE + " (SF +" + STOCKFISH_EXCHANGE_SACRIFICE
                        + "). If it now plays the sacrifice, the shortcut no longer decides this position — "
                        + "check roadmap § 12.18 before adjusting");

        // The score must be exactly the leaf material of its own PV. That exactness
        // is what shows the positional evaluation never ran, so an unround value
        // means the shortcut no longer covers this subtree.
        assertEquals(PURE_MATERIAL_WEIGHT, eval.weight(), PURE_MATERIAL_TOLERANCE,
                "the score must be exactly the leaf material of its own principal variation, which is what "
                        + "shows the positional evaluation was skipped; got " + ChessUtil.weightToString(eval.weight()));
    }

    /**
     * Runs a deterministic fixed-depth search from {@code fen} and returns the
     * engine's chosen move (short algebraic notation) together with its white-POV
     * weight. The transposition table is created fresh and closed before return.
     */
    private static Eval deepEval(String fen)
            throws InterruptedException, ExecutionException, TimeoutException {

        try (var tt = TestSupport.createTestTT()) {
            var engineConfig = new EngineConfig.Builder()
                    .maxDepth(SEARCH_DEPTH)
                    .millisPerMove(SEARCH_BUDGET_MS)
                    .silent(true)
                    .setTranspositionTable(tt)
                    .build();
            var game = new Game(new GameConfig(MyChessEngine.class, engineConfig), Fen.importChess960FEN(fen));

            MoveAndWeight result = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.MINUTES);
            String move = game.getBoard().moveToShortNotation(new Move(result.move())).toString();

            return new Eval(move, result.weight());
        }
    }

    /** Chosen move (short algebraic) and its white-POV weight from a deep search. */
    private record Eval(String move, float weight) {}
}
