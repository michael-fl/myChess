package org.michaelfl.mychess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the king-safety terms of {@link WeightingFunction}: the pawn
 * shield in front of the king, the 3x3 king zone, the attack-unit accumulation
 * for pieces bearing on the enemy king zone, and the gated penalty table.
 *
 * <p>{@code calculate(Board)} populates the internal per-color arrays
 * (index 0 = white, 1 = black); the tests read them through the package-private
 * accessors. Positions are built with {@link Fen#importFEN(String)}.
 *
 * @author Michael Fleischhauer
 */
class WeightingFunctionKingSafetyTest {

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    private static WeightingFunction evalFor(String fen) {
        var weightingFunction = new WeightingFunction();
        weightingFunction.calculate(Fen.importFEN(fen));

        return weightingFunction;
    }

    /**
     * Reflects a board-index offset across the horizontal axis: the rank
     * component (multiples of {@link Board#LENGTH}) is negated while the file
     * component (the small remainder in [-2, 2]) is kept. This is the exact
     * transform the black pawn-shield offsets are expected to be relative to
     * the white ones.
     */
    private static int verticalMirror(int offset) {
        int rankDelta = Math.round(offset / (float) Board.LENGTH);
        int fileDelta = offset - rankDelta * Board.LENGTH;

        return -rankDelta * Board.LENGTH + fileDelta;
    }

    @Nested
    class PawnShield {

        @Test
        void fullSecondRankShieldScores45() {
            // White king g1 with an intact f2/g2/h2 shield (each 15 on the rank ahead).
            var wf = evalFor("4k3/8/8/8/8/8/5PPP/6K1 w - - 0 1");

            assertEquals(45, wf.getPawnShieldWeight()[WHITE], "f2+g2+h2 shield = 3 x 15");
        }

        @Test
        void aPawnTwoRanksAheadAddsTen() {
            // Same shield plus a g3 pawn (two ranks ahead, center file = 10).
            var wf = evalFor("4k3/8/8/8/8/6P1/5PPP/6K1 w - - 0 1");

            assertEquals(55, wf.getPawnShieldWeight()[WHITE], "45 + g3 (two ranks ahead) = 55");
        }

        @Test
        void exposedKingHasNoShield() {
            var wf = evalFor("4k3/8/8/8/8/8/8/6K1 w - - 0 1");

            assertEquals(0, wf.getPawnShieldWeight()[WHITE], "no friendly pawns near the king");
        }

        @Test
        void shieldIsMirroredForBlack() {
            // Black king g8 with an intact f7/g7/h7 shield.
            var wf = evalFor("6k1/5ppp/8/8/8/8/8/4K3 w - - 0 1");

            assertEquals(45, wf.getPawnShieldWeight()[BLACK], "f7+g7+h7 shield = 3 x 15");
        }

        @Test
        void whiteAndBlackShieldsAreComputedSymmetrically() {
            // White Kg1 with f2 + g3 and black Kg8 with the vertically mirrored
            // f7 + g6. The white and black offset tables must mirror each other,
            // so both kings score the identical shield: f-pawn (15, one rank
            // ahead) + center pawn two ranks ahead (10) = 25.
            var wf = evalFor("6k1/5p2/6p1/8/8/6P1/5P2/6K1 w - - 0 1");

            assertEquals(25, wf.getPawnShieldWeight()[WHITE], "f2 (15) + g3 (10) = 25");
            assertEquals(wf.getPawnShieldWeight()[WHITE], wf.getPawnShieldWeight()[BLACK],
                    "white and black shields must be identical for a mirrored position");
        }

        @Test
        void aBroadShieldIsSymmetricForBothColors() {
            // Both kings on g1/g8 with a broad pawn cover across the two ranks
            // in front (e2,f2,g2,h2 + f3,g3,h3, mirrored for black): each side
            // reaches 5+15+15+15 + 10+10+10 = 80, and the two must be equal.
            var wf = evalFor("6k1/4pppp/5ppp/8/8/5PPP/4PPPP/6K1 w - - 0 1");

            assertEquals(80, wf.getPawnShieldWeight()[WHITE], "e2(5)+f2,g2,h2(45) + f3,g3,h3(30) = 80");
            assertEquals(wf.getPawnShieldWeight()[WHITE], wf.getPawnShieldWeight()[BLACK],
                    "white and black broad-zone shields must be identical");
        }

        @Test
        void aPawnTwoFilesToTheSideStillCountsInTheWiderZone() {
            // King g1, a lone pawn on e2 — two files to the left on the rank
            // ahead. The widened zone reaches it at the edge weight of 5.
            var wf = evalFor("4k3/8/8/8/8/8/4P3/6K1 w - - 0 1");

            assertEquals(5, wf.getPawnShieldWeight()[WHITE], "e2 is the left edge of the rank-ahead row = 5");
        }

        @Test
        void theCornerOfTheTwoRanksAheadRowScoresFive() {
            // King g1, a lone pawn on e3 — two files to the left, two ranks
            // ahead: the corner of the widened zone, weight 5.
            var wf = evalFor("4k3/8/8/8/8/4P3/8/6K1 w - - 0 1");

            assertEquals(5, wf.getPawnShieldWeight()[WHITE], "e3 is the corner of the two-ranks-ahead row = 5");
        }

        @Test
        void pawnsBesideAnAdvancedKingOnItsOwnRankAreCounted() {
            // A centralized king on g4 with pawns immediately left and right
            // (f4, h4) on its own rank. Those flank squares belong to the
            // widened zone at weight 5 each.
            var wf = evalFor("4k3/8/8/8/5PKP/8/8/8 w - - 0 1");

            assertEquals(10, wf.getPawnShieldWeight()[WHITE], "f4 + h4 flank squares = 2 x 5");
        }

        @Test
        void blackOffsetsAreTheVerticalMirrorOfWhiteOffsets() {
            // Structural guard on the offset table itself: every black shield
            // offset must be the vertical mirror of the white one at the same
            // index (same file, negated rank). This is what makes the shield
            // symmetric for both colors independent of any position.
            int[] white = WeightingFunction.getPawnShieldOffsets()[WHITE];
            int[] black = WeightingFunction.getPawnShieldOffsets()[BLACK];

            assertEquals(white.length, black.length, "white and black shield-offset rows must have equal length");

            for (int i = 0; i < white.length; i++) {
                assertEquals(verticalMirror(white[i]), black[i],
                        "black offset[" + i + "] must be the vertical mirror of white offset[" + i + "]");
            }
        }
    }

    @Nested
    class KingZone {

        @Test
        void coversTheThreeByThreeAroundTheKing() {
            var wf = evalFor("4k3/8/8/8/8/8/8/6K1 w - - 0 1"); // white king g1

            for (int field : new int[] {Board.f1, Board.g1, Board.h1, Board.f2, Board.g2, Board.h2}) {
                assertTrue(wf.isInKingZone(WHITE, field), "field " + ChessUtil.fieldToString(field) + " is in the g1 king zone");
            }

            for (int field : new int[] {Board.e1, Board.e2, Board.f3}) {
                assertFalse(wf.isInKingZone(WHITE, field), "field " + ChessUtil.fieldToString(field) + " is outside the g1 king zone");
            }
        }

        @Test
        void inTheCornerStaysInBoundsAndMarksTheReachableSquares() {
            // Regression guard: a corner king pushes zone offsets onto the border;
            // it must not throw and must still mark the in-board zone squares.
            var wf = evalFor("4k3/8/8/8/8/8/8/K7 w - - 0 1"); // white king a1

            for (int field : new int[] {Board.a1, Board.b1, Board.a2, Board.b2}) {
                assertTrue(wf.isInKingZone(WHITE, field), "field " + ChessUtil.fieldToString(field) + " is in the a1 king zone");
            }
            assertFalse(wf.isInKingZone(WHITE, Board.c1), "c1 is outside the a1 king zone");
        }
    }

    @Nested
    class AttackUnits {

        @Test
        void countsASingleThreateningPieceOnce() {
            // White knight e6 bears on TWO squares of black's g8 king zone (f8 and g7),
            // but a piece that threatens one or more king-zone squares must be counted
            // exactly once: the attacker count is 1 and its unit is added a single time.
            var wf = evalFor("6k1/8/4N3/8/8/8/8/4K3 w - - 0 1");

            assertEquals(1, wf.getKingAttackerCount()[WHITE], "one knight = one attacker, no matter how many zone squares it hits");
            assertEquals(2, wf.getAttackUnit()[WHITE], "knight unit counted once, not once per attacked square");
            assertEquals(0, wf.getKingAttackerCount()[BLACK], "black's lone king does not reach white's king zone");
        }

        @Test
        void countsEachThreateningPieceOnceNotPerSquare() {
            // Two white knights each threaten two of black's king-zone squares
            // (Ne6 -> f8/g7, Ng5 -> f7/h7; neither gives check on g8). The count must be
            // 2 (two pieces), not 4 (four attacked squares).
            var wf = evalFor("6k1/8/4N3/6N1/8/8/8/4K3 w - - 0 1");

            assertEquals(2, wf.getKingAttackerCount()[WHITE], "two knights = two attackers");
            assertEquals(4, wf.getAttackUnit()[WHITE], "two knight units (2 + 2), each piece counted once");
        }

        @Test
        void realGameQueenPlusKnightAreTwoAttackersNotSixSquares() {
            // Real position (EngineSmokeTest.testPosition1 after 41.Nd5): white's queen
            // e6 and knight d5 attack the exposed black king on g7. That is TWO attackers
            // with 7 attack units (Q=5 + N=2) -> KING_ATTACK_PENALTY[7] = 30cp. The
            // per-square bug instead reports 6 attackers / 27 units (the queen alone hits
            // f6/g6/h6/f7/g8), which clamps to the table maximum and hugely overvalues
            // the position (king-attack term ~ +6.7 instead of +0.30).
            var wf = evalFor("8/6kp/p2pQ3/1p1N2p1/5b2/2P5/PP1r3P/4K3 b - - 2 41");

            assertEquals(2, wf.getKingAttackerCount()[WHITE], "only the queen and the knight attack black's king zone");
            assertEquals(7, wf.getAttackUnit()[WHITE], "queen (5) + knight (2), each counted once");
            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[7], (int) wf.calcKingAttackPenalty(WHITE),
                    "two attackers, 7 units -> penalty table entry 7 (30cp), not the clamped maximum");
        }

        @Test
        void distinctPiecesOfTheSameTypeAreEachCountedOnce() {
            // Two white queens (c8, g5) and two white knights (f5, h7) all bear on black's
            // f7 king zone. Each PIECE counts once, but the dedup must be per piece, NOT
            // per piece type: four attackers with 14 units (2x5 + 2x2). The per-square bug
            // over-counts (13 / 53); a naive per-type dedup would under-count (2 / 7).
            var wf = evalFor("2Q5/p4k1N/8/5NQ1/8/8/8/K7 b - - 0 1");

            assertEquals(4, wf.getKingAttackerCount()[WHITE], "two queens + two knights = four distinct attackers");
            assertEquals(14, wf.getAttackUnit()[WHITE], "2 x queen(5) + 2 x knight(2) = 14, each piece once");
            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[14], (int) wf.calcKingAttackPenalty(WHITE),
                    "four attackers, 14 units -> penalty table entry 14, not the clamped maximum");
        }

        @Test
        void noEnemyPressureLeavesTheCountAtZero() {
            var wf = evalFor("4k3/8/8/8/8/8/8/4K3 w - - 0 1"); // bare kings, far apart

            assertEquals(0, wf.getKingAttackerCount()[WHITE], "no attackers on black's king zone");
            assertEquals(0, wf.getAttackUnit()[WHITE], "no attack units");
        }

        @Test
        void theKingItselfIsNotCountedAsAnAttacker() {
            // Kings two squares apart (legal): the white Ke5 "controls" d6/e6/f6, which
            // lie in black's e7 king zone, and vice versa. The king must never be counted
            // as a king-zone attacker, so both sides stay at zero.
            var wf = evalFor("8/4k3/8/4K3/8/8/8/8 w - - 0 1");

            assertEquals(0, wf.getKingAttackerCount()[WHITE], "the white king is not an attacker of black's king zone");
            assertEquals(0, wf.getAttackUnit()[WHITE], "no attack units contributed by the king");
            assertEquals(0, wf.getKingAttackerCount()[BLACK], "the black king is not an attacker of white's king zone");
        }

        @Test
        void kingPlusQueenCountAsOneAttackerSoNoPenalty() {
            // Both the white king e7 (bearing on f7/f8) and the white queen a1 (bearing
            // on g7 along the long diagonal, then blocked) attack black's g8 king zone.
            // The king is not counted, so only the queen remains: a single attacker,
            // below the >= 2 gate, so the king-attack penalty is zero even though the
            // queen does contribute attack units.
            var wf = evalFor("6k1/4K1p1/8/8/8/8/8/Q7 w - - 0 1");

            assertEquals(1, wf.getKingAttackerCount()[WHITE], "only the queen counts; the king is excluded");
            assertEquals(5, wf.getAttackUnit()[WHITE], "queen unit (5) for its single zone square, king contributes nothing");
            assertEquals(0, (int) wf.calcKingAttackPenalty(WHITE), "one attacker is below the gate => no penalty");
        }
    }

    @Nested
    class KingAttackPenalty {

        @Test
        void oneAttackerIsBelowTheGateAndCostsNothing() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = 1;
            wf.getAttackUnit()[WHITE] = 5;

            assertEquals(0, (int) wf.calcKingAttackPenalty(WHITE), "fewer than two attackers => no penalty");
        }

        @Test
        void twoAttackersLookUpThePenaltyTableByAttackUnit() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = 2;
            wf.getAttackUnit()[WHITE] = 8;

            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[8], (int) wf.calcKingAttackPenalty(WHITE),
                    "two attackers, 8 attack units => penalty table entry 8");
        }

        @Test
        void attackUnitBeyondTheTableIsClampedToTheMaximum() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = 2;
            wf.getAttackUnit()[WHITE] = 100; // far beyond the table

            int max = WeightingFunction.KING_ATTACK_PENALTY[WeightingFunction.KING_ATTACK_PENALTY.length - 1];
            assertEquals(max, (int) wf.calcKingAttackPenalty(WHITE), "attack unit is clamped to the last table entry");
        }
    }
}
