package org.michaelfl.mychess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the king-attack term of {@link WeightingFunction}: the 3x3 king
 * zone, the attack-unit accumulation for pieces bearing on the enemy king zone,
 * and the gated, progressive penalty table.
 *
 * <p>{@code calculate(Board)} populates the internal per-color arrays
 * (index 0 = white, 1 = black); the tests read them through the package-private
 * accessors. Positions are built with {@link Fen#importFEN(String)}.
 *
 * @author Michael Fleischhauer
 */
class WeightingFunctionAttackUnitTest {

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    private static WeightingFunction evalFor(String fen) {
        var weightingFunction = new WeightingFunction();
        weightingFunction.calculate(Fen.importFEN(fen));

        return weightingFunction;
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
            // with 7 attack units (Q=5 + N=2) -> KING_ATTACK_PENALTY[7]. The per-square
            // bug instead reports 6 attackers / 27 units (the queen alone hits
            // f6/g6/h6/f7/g8), which clamps to the table maximum and hugely overvalues
            // the position.
            var wf = evalFor("8/6kp/p2pQ3/1p1N2p1/5b2/2P5/PP1r3P/4K3 b - - 2 41");

            assertEquals(2, wf.getKingAttackerCount()[WHITE], "only the queen and the knight attack black's king zone");
            assertEquals(7, wf.getAttackUnit()[WHITE], "queen (5) + knight (2), each counted once");
            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[7], (int) wf.calcKingAttackPenalty(WHITE),
                    "two attackers, 7 units -> penalty table entry 7, not the clamped maximum");
        }

        @Test
        void distinctPiecesOfTheSameTypeAreEachCountedOnce() {
            // Two white queens (c8, g5) and two white knights (f5, h7) all bear on black's
            // f7 king zone. Each PIECE counts once, but the dedup must be per piece, NOT
            // per piece type: four attackers with 14 units (2x5 + 2x2). The per-square bug
            // over-counts; a naive per-type dedup would under-count (2 / 7).
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
