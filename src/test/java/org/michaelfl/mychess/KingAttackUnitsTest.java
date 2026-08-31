package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link KingAttackUnits} against positions counted by hand.
 *
 * <p>The class exists to fit the king-attack curve, so an error in it would not fail loudly —
 * it would quietly produce a plausible-looking table calibrated on the wrong quantity. That is
 * the failure mode these tests guard: every case states the expected units *and why*, so a
 * disagreement points at which rule broke rather than only at the total.
 *
 * <p>A second, independent check lives outside JUnit: {@code tools/king-attack-curve.py}
 * computes the same quantity on python-chess's board representation, and the two agreed on a
 * sample of corpus positions when the fit was built.
 *
 * @author Michael Fleischhauer
 */
class KingAttackUnitsTest {

    /** Nothing bears on either king. */
    private static final String QUIET = "4k3/8/8/8/8/8/8/4K3 w - - 0 1";

    /** White rook on e7 attacks e8 (the king) and d7/f7 of its zone — one piece, 3 units. */
    private static final String ROOK_ON_THE_ZONE = "4k3/4R3/8/8/8/8/8/4K3 w - - 0 1";

    /** White queen on e6: bears on the zone along the file and both diagonals — 5 units, once. */
    private static final String QUEEN_ON_THE_ZONE = "4k3/8/4Q3/8/8/8/8/4K3 w - - 0 1";

    /** Queen on e6 and rook on a7: 5 + 3, both counted, neither twice. */
    private static final String QUEEN_AND_ROOK = "4k3/R7/4Q3/8/8/8/8/4K3 w - - 0 1";

    /** White knight on f6 attacks e8 and d7 — one piece, 2 units. */
    private static final String KNIGHT_NEAR = "4k3/8/5N2/8/8/8/8/4K3 w - - 0 1";

    /** White pawn on d6 attacks e7, which is in the zone — 1 unit. */
    private static final String PAWN_NEAR = "4k3/8/3P4/8/8/8/8/4K3 w - - 0 1";

    /** Rook on e1 with a rook on e4 in front of it: the rear rook is screened off. */
    private static final String BATTERY_SCREENED = "4k3/8/8/8/4R3/8/8/4RK2 w - - 0 1";

    @Test
    void quietPosition_scoresZeroForBothSides() {
        Board board = Fen.importFEN(QUIET);

        assertEquals(0, KingAttackUnits.of(board, GameStatus.TURN_WHITE),
                "no white piece bears on the black king's zone");
        assertEquals(0, KingAttackUnits.of(board, GameStatus.TURN_BLACK),
                "no black piece bears on the white king's zone");
    }

    @Test
    void rookBearingOnTheZone_countsThreeUnitsOnce() {
        assertEquals(WeightingFunction.ATTACK_UNIT_ROOK,
                KingAttackUnits.of(Fen.importFEN(ROOK_ON_THE_ZONE), GameStatus.TURN_WHITE),
                "the rook attacks several zone squares but is one attacker");
    }

    @Test
    void queenBearingOnTheZone_countsFiveUnitsOnce() {
        assertEquals(WeightingFunction.ATTACK_UNIT_QUEEN,
                KingAttackUnits.of(Fen.importFEN(QUEEN_ON_THE_ZONE), GameStatus.TURN_WHITE),
                "the queen reaches the zone on file and diagonal, and still counts once");
    }

    @Test
    void twoAttackers_sumTheirUnits() {
        assertEquals(WeightingFunction.ATTACK_UNIT_QUEEN + WeightingFunction.ATTACK_UNIT_ROOK,
                KingAttackUnits.of(Fen.importFEN(QUEEN_AND_ROOK), GameStatus.TURN_WHITE),
                "queen and rook each count once, and the weights add");
    }

    @Test
    void knightBearingOnTheZone_countsTwoUnits() {
        assertEquals(WeightingFunction.ATTACK_UNIT_KNIGHT,
                KingAttackUnits.of(Fen.importFEN(KNIGHT_NEAR), GameStatus.TURN_WHITE),
                "a knight on f6 attacks e8 and d7");
    }

    @Test
    void pawnBearingOnTheZone_countsOneUnit() {
        assertEquals(WeightingFunction.ATTACK_UNIT_PAWN,
                KingAttackUnits.of(Fen.importFEN(PAWN_NEAR), GameStatus.TURN_WHITE),
                "a white pawn on d6 attacks e7, a zone square");
    }

    /**
     * The rear piece of a battery is screened. Guards the one place where this implementation
     * deliberately differs from the Audax fork, which continues the ray through friendly
     * sliders — a widening that belongs to its style goal, not to the quantity being fitted.
     */
    @Test
    void batteryRearPiece_isScreenedOff() {
        assertEquals(WeightingFunction.ATTACK_UNIT_ROOK,
                KingAttackUnits.of(Fen.importFEN(BATTERY_SCREENED), GameStatus.TURN_WHITE),
                "only the front rook on e4 bears on the zone; the one on e1 is behind it");
    }

    /**
     * Guards the tests above from passing vacuously: the same rook, moved off the e-file, must
     * stop counting. Without this a bug that returned a constant would satisfy every case that
     * expects a non-zero total.
     */
    @Test
    void rookOffTheZone_scoresZero() {
        assertEquals(0,
                KingAttackUnits.of(Fen.importFEN("4k3/8/8/8/8/8/R7/4K3 w - - 0 1"),
                        GameStatus.TURN_WHITE),
                "a rook on a2 reaches neither the black king nor its zone");
    }

    /** The king itself is worth nothing, so two bare kings never produce units either way. */
    @Test
    void adjacentKings_contributeNothing() {
        Board board = Fen.importFEN("8/8/8/3k4/8/3K4/8/8 w - - 0 1");

        assertEquals(0, KingAttackUnits.of(board, GameStatus.TURN_WHITE), "the king scores 0 units");
        assertEquals(0, KingAttackUnits.of(board, GameStatus.TURN_BLACK), "the king scores 0 units");
    }

    /**
     * Color symmetry: the mirrored position must give the mirrored answer. Catches a whole
     * class of sign and direction errors — particularly in the pawn offsets, which are the one
     * asymmetric rule here.
     */
    @Test
    void mirroredPosition_givesMirroredUnits() {
        int white = KingAttackUnits.of(Fen.importFEN(PAWN_NEAR), GameStatus.TURN_WHITE);
        int black = KingAttackUnits.of(Fen.importFEN("4k3/8/8/8/8/3p4/8/4K3 w - - 0 1"),
                GameStatus.TURN_BLACK);

        assertEquals(white, black, "a black pawn on d3 attacks e2 exactly as a white pawn on d6 attacks e7");
        assertTrue(white > 0, "the mirror check is worthless if both sides read zero");
    }

    // ------------------------------------------------------------------------------------
    // attackersOf / ofZone / placeboCenter — added 2026-08-30 for
    // tools/king-attack-vs-stockfish.py. The attacker count is what the production gate reads
    // and is NOT derivable from the unit sum (five units is one queen, or a rook and a knight,
    // and the gate treats those differently); the zone variants carry its placebo control.
    // ------------------------------------------------------------------------------------

    /**
     * Kings adjacent to the enemy king's zone must not count as attackers.
     *
     * <p>The one case where a naive implementation goes wrong, and it goes wrong in the
     * direction that matters: the dedup mask marks every piece it walked over, including the
     * king, whose weight is zero. Counting mask entries would report one attacker too many, and
     * the production gate's threshold is <em>two</em> — so a lone rook beside a lone king would
     * pass a gate it must not pass. Verified against {@code WeightingFunction}'s own counter
     * over the 39 619-position calibration corpus: zero divergences.
     */
    @Test
    void attackersOfExcludesTheKing() {
        // White king e6 and white rook a7 both bear on black's e8 zone; only the rook counts.
        var board = Fen.importFEN("4k3/R7/4K3/8/8/8/8/8 w - - 0 1");

        assertEquals(1, KingAttackUnits.attackersOf(board, GameStatus.TURN_WHITE),
                "the attacking king bears on the zone but carries zero units, so it is not an attacker");
        assertEquals(WeightingFunction.ATTACK_UNIT_ROOK, KingAttackUnits.of(board, GameStatus.TURN_WHITE),
                "and the unit sum is the rook's alone");
    }

    @Test
    void attackersOfCountsEachPieceOnceNotEachAttackedSquare() {
        var board = Fen.importFEN(QUEEN_AND_ROOK);

        assertEquals(2, KingAttackUnits.attackersOf(board, GameStatus.TURN_WHITE),
                "queen and rook are two attackers however many zone squares each of them hits");
        assertEquals(0, KingAttackUnits.attackersOf(board, GameStatus.TURN_BLACK),
                "black has nothing bearing on the white king");
    }

    @Test
    void attackersOfIsZeroInAQuietPosition() {
        assertEquals(0, KingAttackUnits.attackersOf(Fen.importFEN(QUIET), GameStatus.TURN_WHITE),
                "bare kings far apart: no attackers");
    }

    /**
     * The placebo centre sits on the king's rank, four files away, and wraps within the board.
     *
     * <p>Four files is what keeps the two 3×3 zones from ever overlapping — at three they would
     * touch, and the control would partly measure the thing it controls for.
     */
    @Test
    void placeboCenterIsFourFilesFromTheKingOnTheSameRank() {
        // Black king e8; the placebo centre is a8 (e -> a), same rank.
        int center = KingAttackUnits.placeboCenter(Fen.importFEN(QUIET), GameStatus.TURN_WHITE);

        assertEquals(Board.a8, center,
                "king on e8 (file 5) shifts by four files to a8, wrapping inside the board");

        // White king e1 seen from black: the placebo centre is a1.
        assertEquals(Board.a1, KingAttackUnits.placeboCenter(Fen.importFEN(QUIET), GameStatus.TURN_BLACK),
                "the same shift applies to the other king");
    }

    /**
     * The placebo zone is a different <em>place</em>, not a guarantee of a different answer.
     *
     * <p>Written first with a rook on e7 and the expectation of zero, which failed: a rook on
     * the seventh rakes it, so it reaches a7/b7 in the a8 placebo zone as surely as it reaches
     * the king's. That is not a defect in the control — it is what the control is for. Pieces
     * placed to attack a king often sweep the rest of the rank too, and a regression cannot tell
     * "bears on the king" from "is active deep in enemy territory" without measuring both. The
     * placebo column earns whatever the second explanation earns; the difference between the two
     * columns is the part that is about the king. Recording the failed expectation because the
     * intuition behind it — "the control zone is somewhere else, so it should score zero" — is
     * the one that would make someone quietly delete the control.
     */
    @Test
    void placeboZoneIsADifferentPlaceNotADifferentAnswer() {
        var raking = Fen.importFEN(ROOK_ON_THE_ZONE);
        int rakingPlacebo = KingAttackUnits.placeboCenter(raking, GameStatus.TURN_WHITE);

        assertEquals(WeightingFunction.ATTACK_UNIT_ROOK,
                KingAttackUnits.ofZone(raking, GameStatus.TURN_WHITE, rakingPlacebo),
                "a rook on the seventh reaches a7/b7 and therefore the a8 placebo zone as well");

        // A knight is local, so here the two zones genuinely separate.
        var local = Fen.importFEN(KNIGHT_NEAR);
        int localPlacebo = KingAttackUnits.placeboCenter(local, GameStatus.TURN_WHITE);

        assertEquals(WeightingFunction.ATTACK_UNIT_KNIGHT, KingAttackUnits.of(local, GameStatus.TURN_WHITE),
                "the knight on f6 bears on the king zone");
        assertEquals(0, KingAttackUnits.ofZone(local, GameStatus.TURN_WHITE, localPlacebo),
                "and reaches nothing in the placebo zone four files away");
    }

    @Test
    void placeboZoneUsesTheSameRulesAsTheKingZone() {
        // A rook on a7 bears on a8/b7 and on the a8 zone; asked about that zone directly it
        // must score exactly as it would if a king stood there.
        var board = Fen.importFEN("4k3/R7/8/8/8/8/8/4K3 w - - 0 1");

        assertEquals(WeightingFunction.ATTACK_UNIT_ROOK,
                KingAttackUnits.ofZone(board, GameStatus.TURN_WHITE, Board.a8),
                "the zone computation does not depend on a king being at its centre");
    }
}
