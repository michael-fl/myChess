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
 * <p>White's only recapture is {@code Bxd4}. Afterwards material is dead equal
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
 * <h2>Takeaway</h2>
 *
 * <p>The material-only shortcut is an intended, load-bearing pruning heuristic:
 * it blinds the engine to <em>positional</em> distinctions but never to
 * <em>material</em> ones. That blindness is usually harmless — the move is forced
 * (1), or material decides it anyway (2). It becomes a genuine blunder only when
 * all candidate moves tie on material <em>and</em> the best one runs against the
 * cheapest-attacker move ordering (3). If the shortcut or the capture ordering
 * (e.g. an SEE-based sort) ever changes, the affected assertion turns red and
 * should be updated.
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
