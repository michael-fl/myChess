package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * <h2>Read the five case analyses below as pre-v4.6.0</h2>
 *
 * <p>Since v4.6.0 the shortcut is disabled in the subtrees of <em>capturing</em> root moves and
 * kept in the subtrees of quiet ones. Every case here is a recapture, so four of the five now
 * run with the full evaluation, and the fifth does not — and the split falls exactly along that
 * line, which is the sharpest confirmation of the change that exists:
 *
 * <table border="1">
 *   <caption>What v4.6.0 did to the five cases</caption>
 *   <tr><th>Case</th><th>chosen root move</th><th>capture?</th><th>then</th><th>now</th></tr>
 *   <tr><td>1 development lead</td><td>{@code Bxd4}</td><td>yes</td><td>~0</td>
 *       <td><b>1.93</b> (SF ~1.6) — fixed</td></tr>
 *   <tr><td>2 trapped knight</td><td>{@code Nxb3}</td><td>yes</td><td>~0</td>
 *       <td><b>1.27</b> (SF ~2.3) — fixed, about half arrives</td></tr>
 *   <tr><td>3 queen recapture</td><td>{@code axb3} → {@code Nxb3}</td><td>yes</td>
 *       <td>tie at 1.0, worst recapture</td><td><b>2.52</b>, best recapture — fixed</td></tr>
 *   <tr><td>4 exchange sacrifice</td><td>{@code Qxb5}</td><td>yes</td><td>exactly 6.00</td>
 *       <td><b>6.21</b>, same wrong move — <b>still open</b></td></tr>
 *   <tr><td>5 Immortal Draw</td><td>{@code Ne7}, {@code Kf8}, {@code b6+}, {@code Ba6+}</td>
 *       <td><b>no</b></td><td>exactly 8.00 ×4</td><td>exactly 8.00 ×4 — unchanged</td></tr>
 * </table>
 *
 * <p><b>Case 5 is the control, and nobody designed it as one.</b> Its four root moves are the
 * only quiet ones in the class, so the shortcut still covers them and the score is still exactly
 * eight pawns, four times over. Four capture subtrees changed, one quiet subtree did not.
 *
 * <p><b>Case 4 refutes its own analysis.</b> The section below argues the blunder is caused by
 * the shortcut discarding the positional value of the exchange sacrifice. The shortcut is gone
 * from that subtree now — the score is no longer a round piece count — and myChess still plays
 * {@code Qxb5}. So the evaluation was not the cause, or not the only one. The analysis is kept
 * because its measurements are correct and its mechanism is real; only its conclusion about
 * <em>this</em> position does not survive.
 *
 * <p>Measured: +14.8 ± 10.5 Elo over 3000 games at tc=40/60 against 4.5.0.
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
 * <h2>5. Not a corner case &mdash; {@code immortalDrawIsGradedByCountingPieces...}</h2>
 *
 * <p>Cases 1&ndash;4 each show the shortcut deciding <em>one</em> position. This one shows how
 * wide it can reach: four consecutive positions from Hamppe&ndash;Meitner, Vienna 1872 (the
 * "Immortal Draw"), where black has sacrificed a queen and two pieces to drag the white king
 * to c5, all score exactly <b>+8.00</b>. White is about ten pawns up, so every line leaves the
 * band and the position is graded by counting pieces from beginning to end. The white king
 * standing in the middle of black's forces is worth nothing, four moves running.
 *
 * <p>Stockfish reads all four as <b>0.00</b> &mdash; the draw is forced from move 11. The
 * disagreement is not a search-depth gap in the ordinary sense: myChess cannot be *warned*
 * here, only *shown*. It first agrees at move 17, when the perpetual is four plies away.
 * {@code BlunderTest.kd7_afterKxb7_engineFindsTheMateThatPunishesTheKingGrab()} measures the
 * cost precisely at the sharpest point of that line: the mating refutation is eight plies
 * deep, so avoiding the losing king grab needs nine, and eight is what a normal search gets.
 *
 * <p>The assertion pins the <em>property</em>, not the number: the score is an exact number of
 * pawns. Pinning +8.00 itself would be too brittle — a piece-square-table change moves the
 * principal variation and with it the leaf balance, turning the test red for a reason that has
 * nothing to do with the shortcut. (Case 4 still pins a fixed value and should be moved to
 * this form.)
 *
 * <p><b>Note which way the implication runs.</b> Material values are multiples of 100 cp, so a
 * material-only score is <em>necessarily</em> a whole number of pawns. The converse does not
 * hold: {@link WeightingFunction} can land on a whole number too, it is simply one outcome
 * among a hundred. A single whole score is therefore evidence, not proof — which is exactly
 * why this case asserts across <b>four</b> positions. One coincidence is unremarkable; four in
 * a row is not something to build an alternative explanation on. Anyone tightening this test
 * should keep the aggregate rather than trade it for a sharper-looking single assertion.
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
     * Floor the deep evaluation must now clear where it used to collapse to ~0.
     *
     * <p>One pawn, deliberately far below the measured values (1.93 and 1.27) and far above
     * zero. The point is to pin that the positional advantage reaches the score at all, not
     * how much of it does — the latter moves with every table change.
     */
    private static final float SEES_POSITIONAL_MIN = 1.0f;

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

    /** Case 4: white to move, a piece up, black's knight just planted on f6 en prise. */
    private static final String EXCHANGE_SACRIFICE_FEN =
            "1Qb2r1k/8/2BR1n1p/1pp1Rp1q/5p2/2P4P/1PP2PP1/6K1 w - - 1 36";

    /** The pawn grab myChess prefers, and the piece-winning exchange sacrifice it declines. */
    private static final String EXCHANGE_SACRIFICE_PAWN_GRAB = "Qxb5";
    private static final String EXCHANGE_SACRIFICE_BEST_MOVE = "Rxf6";

    private static final float STOCKFISH_EXCHANGE_SACRIFICE = 7.76f;
    private static final float STOCKFISH_PAWN_GRAB = 5.71f;


    /** Tolerance for the above: tight enough that any positional contribution breaks it. */
    private static final float PURE_MATERIAL_TOLERANCE = 0.001f;

    /**
     * Case 5: four consecutive positions from the Immortal Draw, black to move in each, white
     * about ten pawns up after black's sacrifices. Stockfish scores every one of them 0.00.
     */
    private static final String[] IMMORTAL_DRAW_FENS = {
            "r1b1k1nr/1pp2ppp/8/p1Kpp3/8/P7/1PPP2PP/R1BQ1BNR b kq - 0 12",
            "r1b1k2r/1pp1nppp/8/pBKpp3/8/P7/1PPP2PP/R1BQ2NR b kq - 2 13",
            "r1bk3r/1pp1nppp/2B5/p1Kpp3/8/P7/1PPP2PP/R1BQ2NR b - - 4 14",
            "r1bk3r/2p1nppp/1pB5/pK1pp3/8/P7/1PPP2PP/R1BQ2NR b - - 1 15"};

    /** The move each of those positions follows, for assertion messages. */
    private static final String[] IMMORTAL_DRAW_LABELS = {"12.Kxc5", "13.Bb5+", "14.Bc6", "15.Kb5"};

    /**
     * Case 1 of the class comment, which carries the full analysis.
     *
     * <p><b>Fixed in v4.6.0.</b> The recapture {@code Bxd4} is a capture, so the shortcut is now
     * off in its subtree and the development lead reaches the score: <b>1.93</b> against
     * Stockfish's ~+1.6, where it used to read ~0.
     *
     * <p><b>Test family:</b> material-only-shortcut (fixed)
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

        // ...and it now scores the resulting equal-material position in White's favor. Before
        // v4.6.0 this read ~0: the recapture's > 200 cp swing tripped the shortcut and discarded
        // the development lead. Bxd4 is a capture, so the shortcut is off in its subtree now.
        assertTrue(eval.weight() >= SEES_POSITIONAL_MIN,
                "the development lead must reach the score (measured 1.93 against Stockfish's ~+"
                        + STOCKFISH_DEVELOPMENT_LEAD + "); a value near 0 means the material-only "
                        + "shortcut is covering this subtree again; got "
                        + ChessUtil.weightToString(eval.weight()));
    }

    /**
     * Case 2 of the class comment, which carries the full analysis.
     *
     * <p><b>Fixed in v4.6.0, partially.</b> Its first half was always a guard — material is the
     * dimension the shortcut never discards, so the move was found even while the score was
     * blind. That half still holds. The second half has flipped: the positional surplus now
     * reaches the score at <b>1.27</b> against Stockfish's ~+2.3, so roughly half of it arrives.
     * Recorded as fixed because the blindness it documented is gone, not because the gap is.
     *
     * <p><b>Test family:</b> material-only-shortcut (fixed)
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

        // The eval now carries part of the positional surplus. Before v4.6.0 it read ~0 — the
        // ~+2.3 edge after Nxb3 is positional and the shortcut discarded all of it.
        assertTrue(eval.weight() >= SEES_POSITIONAL_MIN,
                "the positional surplus must reach the score (measured 1.27 of Stockfish's ~+"
                        + STOCKFISH_TRAPPED_KNIGHT + "); a value near 0 means the shortcut is covering "
                        + "this subtree again; got " + ChessUtil.weightToString(eval.weight()));
    }

    /**
     * Case 3 of the class comment, which carries the full analysis.
     *
     * <p><b>Fixed in v4.6.0, and the strongest of the flips.</b> The three recaptures no longer
     * tie at the material value, so the decision no longer falls to move ordering: myChess plays
     * {@code Nxb3}, the objectively best of them, and scores it <b>2.52</b> instead of the
     * material-only <b>1.0</b>. This was the case where the blindness turned into a real blunder,
     * and it is gone.
     *
     * <p><b>Test family:</b> material-only-shortcut (fixed)
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void queenRecaptureTrapPicksTheInferiorPawnCapture()
            throws InterruptedException, ExecutionException, TimeoutException {

        Eval eval = deepEval(QUEEN_RECAPTURE_TRAP_FEN);

        // Before v4.6.0 the queen-sized delta swing kept the shortcut engaged through the whole
        // subtree, so all three recaptures tied at exactly the material value. With the eval a
        // genuine tie the decision fell to move ordering — MoveSorter ranks captures by
        // (captured - mover), so the cheapest attacker went first and, only tied and never
        // beaten, was kept. myChess played the pawn recapture axb3, the objectively worst of the
        // three (SF Nxb3 +4.2 vs axb3 +3.1: axb3 doubles the b-pawns and leaves the offside a5
        // knight in place). Nxb3 is a capture, so the shortcut is off in its subtree now, the tie
        // is broken on merit, and the best recapture wins.
        assertEquals(TRAP_BEST_MOVE, eval.move(),
                "myChess must play the objectively best recapture " + TRAP_BEST_MOVE + " (SF "
                        + STOCKFISH_TRAP_BEST + ") rather than the inferior " + TRAP_INFERIOR_MOVE
                        + " (SF " + STOCKFISH_TRAP_INFERIOR + "). Reverting to " + TRAP_INFERIOR_MOVE
                        + " means the recaptures tie at the material value again and move ordering "
                        + "decides");

        // And the score is no longer the pure material value: the positional gap between the
        // recaptures is now visible, which is what broke the tie.
        assertTrue(eval.weight() > TRAP_TIE_WEIGHT + SEES_POSITIONAL_MIN,
                "the score must exceed the material-only tie of ~+" + TRAP_TIE_WEIGHT
                        + " that used to make the three recaptures indistinguishable (measured 2.52); got "
                        + ChessUtil.weightToString(eval.weight()));
    }

    /**
     * Case 4 of the class comment, which carries the full analysis. Kept here rather than in
     * {@code BlunderTest} because the mechanism, not the provenance, is what someone chasing
     * this behavior will search for.
     *
     * <p><b>Still open after v4.6.0 — and that refutes what this case used to claim.</b> The
     * shortcut no longer covers this subtree: the score moved from exactly <b>6.00</b> to
     * <b>6.21</b>, so the positional evaluation runs. myChess plays {@code Qxb5} anyway. The
     * blunder was therefore not caused by the shortcut, or not by it alone — the class comment
     * below still argues that it was, and that argument is now known to be incomplete. What
     * remains open is why the piece-winning {@code Rxf6} is rejected with an accurate evaluation
     * available.
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

        // The score is no longer the exact leaf material of its own PV — it was exactly 6.00 at
        // depths 8 to 10 before v4.6.0, and Qxb5 is a capture, so the positional evaluation now
        // runs in this subtree. Pinning that inexactness is what keeps the two halves of this
        // case apart: the evaluation is no longer the explanation, the move choice is still wrong.
        assertFalse(isWholePawns(eval.weight()),
                "the score must no longer be an exact number of pawns: Qxb5 is a capture, so the "
                        + "positional evaluation runs here since v4.6.0 (measured 6.21 against the "
                        + "earlier exactly 6.00). A whole number means the shortcut is covering this "
                        + "subtree again; got " + ChessUtil.weightToString(eval.weight()));
    }

    /**
     * Case 5 of the class comment, which carries the full analysis.
     *
     * <p><b>Test family:</b> material-only-shortcut (defect)
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void immortalDrawIsGradedByCountingPieces()
            throws InterruptedException, ExecutionException, TimeoutException {

        for (int i = 0; i < IMMORTAL_DRAW_FENS.length; i++) {
            Eval eval = deepEval(IMMORTAL_DRAW_FENS[i]);

            // Material values are multiples of 100 cp, so a material-only score is necessarily
            // whole. Asserting the property rather than the value survives table changes that
            // move the principal variation; asserting it four times is what makes it evidence.
            assertTrue(isWholePawns(eval.weight()),
                    "after " + IMMORTAL_DRAW_LABELS[i] + " the score must be an exact number of pawns, "
                            + "which is what a position graded by counting pieces looks like. An unround "
                            + "value means the shortcut no longer covers this subtree; got "
                            + ChessUtil.weightToString(eval.weight()));

            // 0.00 is whole too, so without this the check above would pass unnoticed on the
            // day the engine starts seeing the draw. Stockfish has one from move 11 onwards.
            assertNotEquals(0f, eval.weight(),
                    "after " + IMMORTAL_DRAW_LABELS[i] + " myChess must still miss the forced draw that "
                            + "Stockfish sees from move 11 onwards. If it now reads 0.00, the evaluation "
                            + "has learned something about the exposed king and this case should become a "
                            + "positive assertion");
        }
    }

    // ---------------------------------------------------------------------------------------
    // The gate's boundary. Everything above probes the shortcut through a real search; the two
    // tests below probe the gate itself, by calling QuiescenceSearch with a chosen materialDelta.
    // ---------------------------------------------------------------------------------------

    /**
     * A quiet position — no captures available anywhere, so quiescence returns its stand-pat
     * immediately and the returned score <em>is</em> the gate's output, with no search in between.
     *
     * <p>White king e1, white pawn e2, black king e8. Raw material is +100; the full evaluation
     * differs from it, which is what makes the two branches of the gate distinguishable at all.
     */
    private static final String QUIET_BOUNDARY_FEN = "4k3/8/8/8/8/8/4P3/4K3 w - - 0 1";

    /** Quiescence depth cap and time budget for the direct gate calls; neither is reached. */
    private static final int BOUNDARY_QUIESCENCE_DEPTH = 20;
    private static final int BOUNDARY_BUDGET_MS = 60_000;

    /**
     * The full evaluation must still run at a running swing of <b>200 cp</b>.
     *
     * <p>This is the guard that was missing, and its absence was measured rather than suspected:
     * lowering {@code EVALUATE_MATERIAL_ONLY_THRESHOLD} from 200 to 100 left the entire fast
     * suite green at 1145 tests, because all five cases above turn on piece captures worth 300 to
     * 1000 cp and therefore behave identically at either value. The existing characterizations are
     * one-sided — they detect the shortcut <em>ceasing</em> to fire, never it <em>starting</em> to
     * fire somewhere new.
     *
     * <p>200 cp is the only delta that separates a threshold of 100 from one of 200. Every piece
     * value in {@link WeightingFunction#weightOfPiece} is a multiple of 100 — pawn 100, knight and
     * bishop 300, rook 500, queen 1000 — so {@code materialDelta} is always a multiple of 100 and
     * the "100 to 200 band" this test was originally described as covering is empty. The gate is
     * strict ({@code > threshold}), so a swing of 200 does <em>not</em> fire the gate at a threshold
     * of 200 and does fire it at 100.
     *
     * <p><b>The swing is injected rather than played out</b> — see {@link #SWING_BELOW_GATE}. The
     * fixture holds a single pawn and is not meant to embody the swing; it only has to be quiet and
     * to have a positional score that differs from its material.
     *
     * <p>Asserts against the evaluation computed on the spot rather than against a pinned number,
     * so an evaluation retune cannot break it. What it pins is the <b>gate</b>, and only the gate.
     */
    @Test
    void theFullEvaluationStillRunsAtASwingOf200Centipawns() {
        var board = Fen.importFEN(QUIET_BOUNDARY_FEN);
        int weightFactor = board.getGameStatus().getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        int material = weightFactor * WeightingFunction.calculateMaterialWeight(board);
        int fullEvaluation = weightFactor * new WeightingFunction().calculate(board);

        assertNotEquals(material, fullEvaluation,
                "test premise: the fixture must be a position where the positional terms move the "
                        + "score, otherwise neither branch of the gate is observable");

        for (int delta : new int[]{SWING_BELOW_GATE, -SWING_BELOW_GATE}) {
            assertEquals(fullEvaluation, gateOutput(board, weightFactor, material, delta),
                    "at a swing of " + delta + " cp the gate must not fire, so the full evaluation "
                            + "runs — this fails if EVALUATE_MATERIAL_ONLY_THRESHOLD drops to 100");
        }
    }

    /**
     * The shortcut must take over at a running swing of <b>300 cp</b>.
     *
     * <p>The upper edge of the same gate, and the counterpart to the test above: 300 cp is the
     * delta that separates a threshold of 200 from one of 300. The suite already had an unlabelled case here —
     * {@code QuiescenceSearchTest.testPositionAfterCapture} is a bishop-for-knight trade, so its
     * swing is exactly 300 cp and it failed when the threshold was raised to 300. This states the
     * same edge directly instead of as a side effect.
     *
     * <p>Together the two tests pin the threshold to <b>exactly 200</b>. That is deliberate: moving
     * the constant should be a decision that updates a test, not a change the suite sleeps through.
     */
    @Test
    void theShortcutTakesOverAtASwingOf300Centipawns() {
        var board = Fen.importFEN(QUIET_BOUNDARY_FEN);
        int weightFactor = board.getGameStatus().getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        int material = weightFactor * WeightingFunction.calculateMaterialWeight(board);

        for (int delta : new int[]{SWING_ABOVE_GATE, -SWING_ABOVE_GATE}) {
            assertEquals(material, gateOutput(board, weightFactor, material, delta),
                    "at a swing of " + delta + " cp the gate must fire, so only material is "
                            + "returned — this fails if EVALUATE_MATERIAL_ONLY_THRESHOLD rises to 300");
        }
    }

    /**
     * A running swing of 200 cp — the largest value that does <em>not</em> open the gate at the
     * shipped threshold, and the value that separates a threshold of 100 from one of 200.
     *
     * <p><b>Injected, not enacted.</b> This is handed to {@code QuiescenceSearch} as its
     * {@code materialDelta} argument; the fixture does not play two pawns' worth of captures to
     * produce it. That directness is the point — the gate reads nothing but this number, so
     * supplying it is what isolates the gate from the search around it. A fixture that tried to
     * accumulate the swing through a real capture chain would test move generation, ordering and
     * SEE pruning at the same time, and would break whenever any of those changed.
     *
     * <p>The pairing with the fixture's own material is realistic rather than contrived: the delta
     * is measured from the root while the material balance is absolute, so "+100 on the board after
     * a +200 swing" is simply a root position where the side to move was a pawn down.
     */
    private static final int SWING_BELOW_GATE = 200;

    /** A running swing of 300 cp: the smallest value that opens the gate at the shipped threshold. */
    private static final int SWING_ABOVE_GATE = 300;

    /**
     * The gate's output for one {@code materialDelta}, taken from a fresh quiescence search over a
     * position with no captures — so the stand-pat is returned unchanged and nothing else can
     * influence the number.
     *
     * @param board        the quiet fixture; not mutated, a copy is searched
     * @param weightFactor {@code +1} when white is to move, {@code -1} otherwise
     * @param material     the side-to-move-relative material balance
     * @param materialDelta the running swing to present to the gate
     * @return whatever {@code QuiescenceSearch} returns, which for a quiet position is exactly the
     *         gate's choice between the full evaluation and raw material
     */
    private static int gateOutput(Board board, int weightFactor, int material, int materialDelta) {
        var quiescenceSearch = new QuiescenceSearch(
                MoveGenerator.forQuiescenceSearch(), new WeightingFunction(), new Statistics(),
                BOUNDARY_QUIESCENCE_DEPTH, System.currentTimeMillis() + BOUNDARY_BUDGET_MS);

        return quiescenceSearch.quiescenceSearch(board.copy(), 0, weightFactor,
                WeightingFunction.MIN_ALPHA, WeightingFunction.MAX_BETA, material, materialDelta);
    }

    /** Whether {@code weight} is an exact number of pawns, the signature of a piece count. */
    private static boolean isWholePawns(float weight) {
        return Math.abs(weight - Math.round(weight)) < PURE_MATERIAL_TOLERANCE;
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
