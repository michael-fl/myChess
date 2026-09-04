package org.michaelfl.mychess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the king-attack term of {@link WeightingFunction}: the 3x3 king
 * zone, the attack-unit accumulation for pieces bearing on the enemy king zone,
 * the gated penalty table, and the game-phase scaling that multiplies it.
 *
 * <p>{@code calculate(Board)} populates the internal per-color arrays
 * (index 0 = white, 1 = black); the tests read them through the package-private
 * accessors. Positions are built with {@link Fen#importFEN(String)}.
 *
 * <p><b>Every assertion on the penalty passes an explicit phase</b>, usually
 * {@link WeightingFunction#MAX_PHASE}. The fixtures in {@link KingAttackPenalty}
 * construct a {@link WeightingFunction} and set the arrays by hand without calling
 * {@code calculate}, so their phase field is 0 — reading it back would scale every
 * expectation to zero and the assertions would pass while testing nothing.
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

        /**
         * <b>Replaces {@code inTheCornerStaysInBoundsAndMarksTheReachableSquares}.</b> That test
         * asserted the a1 zone was {@code a1, b1, a2, b2} with c1 outside — the unshifted 3x3
         * clipped at the border, four squares against six for b1 and nine for b2. It described
         * itself as a regression guard for "must not throw", which it was; what it also did was
         * pin the discount below.
         *
         * <p><b>The defect.</b> The zone is the 3x3 block centred on the king, clipped to the
         * board, so it holds 9 squares inside, 6 on an edge and 4 in a corner. An attacker is
         * counted when it bears on any zone square, so <em>fewer squares means fewer attackers
         * qualify</em> — and the engine can shrink its own measured danger by walking the king
         * toward the corner, which chess theory says is usually less safe rather than more. The
         * worst single move loses 5 of 9 squares (g2-h1). Measured on the 152 complete pairs of
         * <code>sprt-attack-units.pgn</code> the candidate already sat on smaller zones than the
         * baseline, −0.1008 squares (95 % −0.179...−0.023, t = −2.52) with 13.5 % more corner
         * positions — under-powered and from the slow first build, so corroboration rather than
         * proof. The proof is the arithmetic.
         *
         * <p><b>The repair: clamp the king's <em>file</em> to b...g, leave the rank alone.</b> So
         * an a1 king reads the zone of a b1 king and a h1 king that of a g1 king. Clamping the
         * rank as well would make every zone 9 squares and remove the last gradient, and it is
         * deliberately <em>not</em> done — see
         * {@link #theZoneKeepsSixSquaresOnTheBackRankAndNineAboveIt}.
         */
        @Test
        void inTheCornerTheZoneShiftsInwardInsteadOfShrinking() {
            var wf = evalFor("4k3/8/8/8/8/8/8/K7 w - - 0 1"); // white king a1

            for (int field : new int[] {Board.a1, Board.b1, Board.c1, Board.a2, Board.b2, Board.c2}) {
                assertTrue(wf.isInKingZone(WHITE, field),
                        "field " + ChessUtil.fieldToString(field) + " must be in the a1 king zone: "
                                + "the window shifts onto the b-file rather than losing a third of itself");
            }

            for (int field : new int[] {Board.d1, Board.d2, Board.a3, Board.b3, Board.c3}) {
                assertFalse(wf.isInKingZone(WHITE, field),
                        "field " + ChessUtil.fieldToString(field) + " must stay outside: shifting the "
                                + "window is not widening it, and the rank is not clamped");
            }
        }

        /**
         * The property the repair exists for, asserted as an equality so it does not restate the
         * zone's shape a third time: a king on the a- or h-file must cover exactly what a king one
         * file further in covers. Checked on both back ranks, where a castled king actually lives,
         * and on a middle rank, where the zone is nine squares rather than six.
         */
        @Test
        void aKingOnTheEdgeFileCoversTheSameZoneAsOneFileInward() {
            record Pair(String edge, String inward, String what) {}

            final Pair[] pairs = {
                    new Pair("4k3/8/8/8/8/8/8/7K w - - 0 1", "4k3/8/8/8/8/8/8/6K1 w - - 0 1", "h1 against g1"),
                    new Pair("4k3/8/8/8/8/8/8/K7 w - - 0 1", "4k3/8/8/8/8/8/8/1K6 w - - 0 1", "a1 against b1"),
                    new Pair("4k3/8/8/8/7K/8/8/8 w - - 0 1", "4k3/8/8/8/6K1/8/8/8 w - - 0 1", "h4 against g4"),
                    new Pair("4k3/8/8/8/K7/8/8/8 w - - 0 1", "4k3/8/8/8/1K6/8/8/8 w - - 0 1", "a4 against b4")
            };

            for (Pair p : pairs) {
                final var edge = evalFor(p.edge());
                final var inward = evalFor(p.inward());

                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        final int field = ChessUtil.getFieldFromColAndRow(col, row);

                        assertEquals(inward.isInKingZone(WHITE, field), edge.isInKingZone(WHITE, field),
                                p.what() + ": " + ChessUtil.fieldToString(field)
                                        + " must belong to both zones or to neither");
                    }
                }
            }
        }

        /**
         * <b>The design decision, pinned so it cannot be tidied away.</b> This case is green
         * before the repair as well, and it is the one that fails if someone clamps the rank too.
         *
         * <p>Clamping both axes would make every zone nine squares and leave no gradient at all,
         * which looks strictly better and is not. A king on g1 would then read the zone of a king
         * on g2 — the two become <em>indistinguishable</em>, and the term loses the one thing it
         * still expresses about shelter: whether the king sits behind its own pawns or has stepped
         * out in front of them. Stockfish can afford to clamp both because its king ring sits
         * beside a shelter and storm term that carries that distinction separately; myChess has no
         * such companion, so the zone has to carry it.
         *
         * <p>So the surviving gradient — six squares on the back rank against nine above it — is
         * kept on purpose, because its sign is right: stepping off the back rank in front of one's
         * own pawns really is more dangerous. The corner gradient was removed because its sign was
         * wrong.
         */
        @Test
        void theZoneKeepsSixSquaresOnTheBackRankAndNineAboveIt() {
            assertEquals(6, zoneSize(evalFor("4k3/8/8/8/8/8/8/6K1 w - - 0 1")),
                    "a king on g1 has no rank below it, so its zone is 3 files by 2 ranks");
            assertEquals(9, zoneSize(evalFor("4k3/8/8/8/8/8/6K1/8 w - - 0 1")),
                    "a king on g2 has, and its zone is the full 3 by 3 — the term must be able to "
                            + "tell the two apart, so the rank must not be clamped");
            assertEquals(6, zoneSize(evalFor("4k3/8/8/8/8/8/8/7K w - - 0 1")),
                    "and after the repair a king on h1 reads the same six as one on g1");
        }

        private static int zoneSize(WeightingFunction wf) {
            int n = 0;

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if (wf.isInKingZone(WHITE, ChessUtil.getFieldFromColAndRow(col, row))) {
                        n++;
                    }
                }
            }

            return n;
        }
    }

    /**
     * The consequence the repair is actually for: attackers that bear only on the squares a
     * corner king's zone drops. Separate from {@link KingZone} because these read the accumulated
     * units rather than the zone membership, and the two accessors use <b>opposite colour
     * conventions</b> — {@code isInKingZone(colour, …)} is indexed by the king's owner, while
     * {@code getAttackUnit()[colour]} is indexed by the <em>attacker</em>. Mixing them up produced
     * a silent false negative once already (§ 4.11: an inverted feature fits to a flat zero and
     * reads as "carries nothing").
     */
    @Nested
    class CornerDiscount {

        /**
         * White king on g1 or h1, otherwise identical: a black rook on the open f-file bearing on
         * f1 and f2, and a black knight on d3 bearing on f2. Neither touches g1, g2, h1 nor h2.
         *
         * <p>Both squares they reach — f1 and f2 — are in a g1 king's zone and neither is in a
         * h1 king's, so before the repair <code>Kg1-h1</code> takes the reading from two attackers
         * and five units to <b>zero of each</b>, which also drops it below the two-attacker gate.
         * With a queen and a rook in place of these two the same move would take the penalty from
         * the table's maximum to nothing.
         */
        @Test
        void steppingIntoTheCornerKeepsTheAttackersCounted() {
            final var onG1 = evalFor("4kr2/8/8/8/8/3n4/8/6K1 w - - 0 1");
            final var onH1 = evalFor("4kr2/8/8/8/8/3n4/8/7K w - - 0 1");

            assertEquals(2, onG1.getKingAttackerCount()[BLACK],
                    "the premise: against the king on g1 the rook on f8 and the knight on d3 both "
                            + "bear on the zone (f1/f2 and f2)");
            assertEquals(5, onG1.getAttackUnit()[BLACK],
                    "the premise: rook 3 plus knight 2, each counted once");

            assertEquals(onG1.getKingAttackerCount()[BLACK], onH1.getKingAttackerCount()[BLACK],
                    "stepping into the corner must not lose attackers — g1 counted "
                            + onG1.getKingAttackerCount()[BLACK] + ", h1 counted "
                            + onH1.getKingAttackerCount()[BLACK]);
            assertEquals(onG1.getAttackUnit()[BLACK], onH1.getAttackUnit()[BLACK],
                    "and must not lose units — g1 read " + onG1.getAttackUnit()[BLACK]
                            + ", h1 read " + onH1.getAttackUnit()[BLACK]);
        }

        /** The same on the queenside, where the dropped squares are c1 and c2. */
        @Test
        void theQueensideCornerBehavesTheSameWay() {
            final var onB1 = evalFor("4kr2/8/8/8/8/4n3/8/1K6 w - - 0 1");
            final var onA1 = evalFor("4kr2/8/8/8/8/4n3/8/K7 w - - 0 1");

            assertTrue(onB1.getAttackUnit()[BLACK] > 0,
                    "the premise: black must actually reach the b1 king's zone, or both sides of "
                            + "the comparison are zero and it holds for the wrong reason");
            assertEquals(onB1.getKingAttackerCount()[BLACK], onA1.getKingAttackerCount()[BLACK],
                    "b1 counted " + onB1.getKingAttackerCount()[BLACK] + ", a1 counted "
                            + onA1.getKingAttackerCount()[BLACK]);
            assertEquals(onB1.getAttackUnit()[BLACK], onA1.getAttackUnit()[BLACK],
                    "b1 read " + onB1.getAttackUnit()[BLACK] + ", a1 read "
                            + onA1.getAttackUnit()[BLACK]);
        }

        /**
         * The mirror. A repair applied to one colour only does not crash and does not warn; it
         * leaves half the evaluation wrong, and this term is computed through the same mirrored
         * walk that made that failure mode worth guarding against for the king-line term.
         */
        @Test
        void blackReadsItsOwnCornerTheSameWay() {
            final var onG8 = evalFor("6k1/8/3N4/8/8/8/8/4KR2 w - - 0 1");
            final var onH8 = evalFor("7k/8/3N4/8/8/8/8/4KR2 w - - 0 1");

            assertEquals(2, onG8.getKingAttackerCount()[WHITE],
                    "the premise, mirrored: the rook on f1 and the knight on d6 both bear on the "
                            + "g8 king's zone");
            assertEquals(onG8.getKingAttackerCount()[WHITE], onH8.getKingAttackerCount()[WHITE],
                    "g8 counted " + onG8.getKingAttackerCount()[WHITE] + ", h8 counted "
                            + onH8.getKingAttackerCount()[WHITE]);
            assertEquals(onG8.getAttackUnit()[WHITE], onH8.getAttackUnit()[WHITE],
                    "g8 read " + onG8.getAttackUnit()[WHITE] + ", h8 read "
                            + onH8.getAttackUnit()[WHITE]);
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
            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[7],
                    wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "two attackers, 7 units -> penalty table entry 7 undiluted, not the clamped maximum");
            assertEquals(WeightingFunction.blend(WeightingFunction.KING_ATTACK_PENALTY[7], 0, wf.getPhase()),
                    wf.calcKingAttackPenalty(WHITE, wf.getPhase()),
                    "at this position's own phase the same entry is scaled down: it is an endgame "
                            + "(phase " + wf.getPhase() + " of " + WeightingFunction.MAX_PHASE
                            + "), where the measured king-attack effect is weak or reversed");
        }

        @Test
        void distinctPiecesOfTheSameTypeAreEachCountedOnce() {
            // Two white queens (c8, g5) and two white knights (f5, h7) all bear on black's
            // f7 king zone. Each PIECE counts once, but the dedup must be per piece, NOT
            // per piece type: four attackers with 14 units (2x5 + 2x2). The per-square bug
            // over-counts; a naive per-type dedup would under-count (2 / 7).
            //
            // 14 units is past the end of the fitted table, which stops at 8 (docs/king-safety.md
            // 4.6: indices above that hold 0.3 % of samples and cannot be fitted from the data).
            // Clamping is therefore the designed behavior, not a failure mode — this assertion
            // read KING_ATTACK_PENALTY[14] while the table was 21 entries long and would now
            // throw ArrayIndexOutOfBoundsException.
            var wf = evalFor("2Q5/p4k1N/8/5NQ1/8/8/8/K7 b - - 0 1");

            assertEquals(4, wf.getKingAttackerCount()[WHITE], "two queens + two knights = four distinct attackers");
            assertEquals(14, wf.getAttackUnit()[WHITE], "2 x queen(5) + 2 x knight(2) = 14, each piece once");
            int lastEntry = WeightingFunction.KING_ATTACK_PENALTY.length - 1;

            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[lastEntry],
                    wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "four attackers, 14 units -> clamped onto the last fitted entry (index "
                            + lastEntry + ")");
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
            assertEquals(0, wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "one attacker is below the gate => no penalty, and asserted at full midgame so "
                            + "it is the gate rather than the phase scaling that produces the zero");
        }
    }

    @Nested
    class KingAttackPenalty {

        @Test
        void oneAttackerIsBelowTheGateAndCostsNothing() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = 1;
            wf.getAttackUnit()[WHITE] = 5;

            assertEquals(0, wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "fewer than two attackers => no penalty, even at full midgame");
        }

        @Test
        void twoAttackersLookUpThePenaltyTableByAttackUnit() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = 2;
            wf.getAttackUnit()[WHITE] = 8;

            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[8],
                    wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "two attackers, 8 attack units => penalty table entry 8");
        }

        @Test
        void attackUnitBeyondTheTableIsClampedToTheMaximum() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = 2;
            wf.getAttackUnit()[WHITE] = 100; // far beyond the table

            int max = WeightingFunction.KING_ATTACK_PENALTY[WeightingFunction.KING_ATTACK_PENALTY.length - 1];

            assertEquals(max, wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "attack unit is clamped to the last table entry");
        }

        /**
         * The penalty is multiplied by the game phase, and that is the whole point of the port.
         *
         * <p>Branch {@code attack-units} carried the term for weeks with {@code kingAttackFactor}
         * fixed at 0.01 and no reference to the phase, so it ran at full strength in the endgame.
         * That is not merely wasteful, it is the wrong sign: measured over the corpus, the
         * king-attack effect is about −34 cp per attacker in the midgame and **+12 in the
         * endgame** (`docs/king-safety.md` § 4.2, finding F1). An unscaled term therefore pays a
         * penalty where the data say there is a small bonus.
         *
         * <p>Three properties, and none of them was covered before: the term vanishes at
         * {@code phase == 0}, reaches the table entry undiluted at {@link WeightingFunction#MAX_PHASE},
         * and never decreases in between. The last one is what a handwritten scaling formula
         * gets wrong — an off-by-one in the rounding shows up as a dip, not as a wrong endpoint.
         */
        @Test
        void thePenaltyScalesWithTheGamePhaseAndVanishesInTheEndgame() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = 2;
            wf.getAttackUnit()[WHITE] = 8;

            int midgame = WeightingFunction.KING_ATTACK_PENALTY[8];

            assertEquals(0, wf.calcKingAttackPenalty(WHITE, 0),
                    "at phase 0 — bare kings — the king-attack penalty is switched off entirely");
            assertEquals(midgame, wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "at full midgame material the table entry is applied undiluted");

            int previous = -1;

            for (int phase = 0; phase <= WeightingFunction.MAX_PHASE; phase++) {
                int penalty = wf.calcKingAttackPenalty(WHITE, phase);

                assertTrue(penalty >= previous,
                        "the penalty must not fall as material grows: phase " + phase + " gives "
                                + penalty + " against " + previous + " at phase " + (phase - 1));
                previous = penalty;
            }
        }

        /**
         * The gate outranks the phase: below two attackers there is nothing to scale.
         *
         * <p>Worth its own case because the two guards multiply rather than compose. A
         * refactoring that folded the phase into the table lookup — plausible, and cheaper —
         * would still return zero here and look correct, while a refactoring that dropped the
         * gate would produce a phase-scaled penalty for a lone queen. The corpus says that is
         * 33.5 % of all king samples (`docs/king-safety.md` § 4.6), so the mistake would be
         * large and silent.
         */
        @Test
        void theGateAppliesAtEveryPhase() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = 1;
            wf.getAttackUnit()[WHITE] = 5;

            for (int phase = 0; phase <= WeightingFunction.MAX_PHASE; phase++) {
                assertEquals(0, wf.calcKingAttackPenalty(WHITE, phase),
                        "a single attacker is gated out at phase " + phase);
            }
        }
    }
}
