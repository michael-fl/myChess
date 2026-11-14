package org.michaelfl.mychess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pawn shield of {@link WeightingFunction}: own pawns in the own king zone,
 * and how they lower the king-attack penalty the opponent can charge.
 */
class WeightingFunctionPawnShieldTest {

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    private static final String POSITION_RESOURCE = "/sts/STS1-STS15_LAN_v6.epd";
    private static final String BEST_MOVE_TAG = " bm ";
    private static final String COUNTER_SUFFIX = " 0 1";

    private static WeightingFunction evalFor(String fen) {
        var weightingFunction = new WeightingFunction();
        weightingFunction.calculate(Fen.importFEN(fen));

        return weightingFunction;
    }

    /** The penalty the evaluation should charge, built from its own counters. */
    private static int expectedPenalty(WeightingFunction wf, int attacker, int phase) {
        if (wf.getKingAttackerCount()[attacker] < 2) {
            return 0;
        }

        int[] shieldUnits = WeightingFunction.PAWN_SHIELD_DEFENSE_UNITS;
        int shield = Math.min(wf.getPawnShieldCount()[attacker ^ 1], shieldUnits.length - 1);
        int index = Math.max(wf.getAttackUnit()[attacker] - shieldUnits[shield], 0);
        int[] table = WeightingFunction.KING_ATTACK_PENALTY;

        return WeightingFunction.blend(table[Math.min(index, table.length - 1)], 0, phase);
    }

    @Nested
    class Counting {

        @Test
        void castledKingBehindThreePawns_hasAShieldOfThree() {
            var wf = evalFor("6k1/8/8/8/8/8/5PPP/6K1 w - - 0 1");

            assertEquals(3, wf.getPawnShieldCount()[WHITE]);
        }

        @Test
        void advancedPawn_leavesTheZone() {
            var wf = evalFor("6k1/8/8/8/8/6P1/5P1P/6K1 w - - 0 1"); // g3 is outside the g1 zone

            assertEquals(2, wf.getPawnShieldCount()[WHITE]);
        }

        @Test
        void cornerKing_countsThePawnsOfTheShiftedZone() {
            var wf = evalFor("6k1/8/8/8/8/8/5PPP/7K w - - 0 1"); // h1 reads the zone around g1, so f2 counts

            assertEquals(3, wf.getPawnShieldCount()[WHITE]);
        }

        @Test
        void queensideCornerKing_countsThePawnsOfTheShiftedZone() {
            var wf = evalFor("6k1/8/8/8/8/8/PPP5/K7 w - - 0 1"); // a1 reads the zone around b1, so c2 counts

            assertEquals(3, wf.getPawnShieldCount()[WHITE]);
        }

        @Test
        void kingOnTheSecondRank_countsPawnsBesideAndInFront() {
            var wf = evalFor("6k1/8/8/8/8/5PPP/5PKP/8 w - - 0 1"); // Kg2: f2, h2 beside, f3, g3, h3 in front

            assertEquals(5, wf.getPawnShieldCount()[WHITE]);
        }

        @Test
        void enemyPawnInTheZone_isNoShield() {
            var wf = evalFor("6k1/8/8/8/8/8/6p1/6K1 w - - 0 1");

            assertEquals(0, wf.getPawnShieldCount()[WHITE]);
        }

        @Test
        void blackShield_countsForBlack() {
            var wf = evalFor("6k1/5ppp/8/8/8/8/8/6K1 b - - 0 1");

            assertEquals(3, wf.getPawnShieldCount()[BLACK]);
            assertEquals(0, wf.getPawnShieldCount()[WHITE]);
        }

        @Test
        void missingKing_hasNoShield() {
            var wf = evalFor("6k1/8/8/8/8/8/5PPP/8 w - - 0 1");

            assertEquals(0, wf.getPawnShieldCount()[WHITE]);
        }

        @Test
        void secondEvaluation_startsFromZero() {
            var wf = new WeightingFunction();
            wf.calculate(Fen.importFEN("6k1/8/8/8/8/8/5PPP/6K1 w - - 0 1"));
            wf.calculate(Fen.importFEN("6k1/8/8/8/8/8/8/6K1 w - - 0 1"));

            assertEquals(0, wf.getPawnShieldCount()[WHITE], "the shield of the previous position must not carry over");
        }

        /** A shield pawn is an own pawn on a square of the own king zone, as {@code isInKingZone} defines it. */
        @Test
        void count_agreesWithTheKingZoneOverTheStsPositions() {
            var divergences = new ArrayList<String>();

            for (String fen : positions()) {
                Board board = Fen.importFEN(fen);
                var wf = new WeightingFunction();
                wf.calculate(board);

                for (int color = WHITE; color <= BLACK; color++) {
                    byte pawn = color == WHITE ? Board.whitePawn : Board.blackPawn;
                    int expected = 0;

                    for (int field = Board.a1; field <= Board.h8; field++) {
                        if (board.getRawBoard()[field] == pawn && wf.isInKingZone(color, field)) {
                            expected++;
                        }
                    }

                    if (expected != wf.getPawnShieldCount()[color]) {
                        divergences.add("%s color %d: counted %d, zone holds %d"
                                .formatted(fen, color, wf.getPawnShieldCount()[color], expected));
                    }
                }
            }

            assertTrue(divergences.isEmpty(), divergences.size() + " counts differ. First few: "
                    + divergences.subList(0, Math.min(5, divergences.size())));
        }
    }

    @Nested
    class Penalty {

        private static WeightingFunction whiteAttacks(int attackers, int units) {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = attackers;
            wf.getAttackUnit()[WHITE] = units;

            return wf;
        }

        @Test
        void fullShieldOfTheAttackedKing_lowersTheIndexBySix() {
            var wf = whiteAttacks(2, 8);
            wf.getPawnShieldCount()[BLACK] = 3;

            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[2],
                    wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "8 attack units against a black king behind three pawns read entry 2");
        }

        @Test
        void shieldOfTheAttacker_doesNotLowerThePenalty() {
            var wf = whiteAttacks(2, 8);
            wf.getPawnShieldCount()[WHITE] = 3;

            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[8],
                    wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "the black king has no shield, so the white shield must not matter");
        }

        @Test
        void shieldUnits_followTheTable() {
            int[] expectedIndex = {8, 7, 5, 2, 2}; // 0, 1, 2, 3 and 4 shield pawns against 8 units

            for (int pawns = 0; pawns < expectedIndex.length; pawns++) {
                var wf = whiteAttacks(2, 8);
                wf.getPawnShieldCount()[BLACK] = pawns;

                assertEquals(WeightingFunction.KING_ATTACK_PENALTY[expectedIndex[pawns]],
                        wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                        pawns + " shield pawns of the attacked king");
            }
        }

        @Test
        void shieldAtLeastAsLargeAsTheAttack_costsNothing() {
            var wf = whiteAttacks(3, 5);
            wf.getPawnShieldCount()[BLACK] = 3;

            assertEquals(0, wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE));
        }

        @Test
        void gate_staysOnTheAttackers() {
            var wf = whiteAttacks(1, 8);

            assertEquals(0, wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "one attacker stays below the gate whatever the shield");
        }

        @Test
        void blackAttacking_usesTheWhiteShield() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[BLACK] = 2;
            wf.getAttackUnit()[BLACK] = 8;
            wf.getPawnShieldCount()[WHITE] = 2;

            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[5],
                    wf.calcKingAttackPenalty(BLACK, WeightingFunction.MAX_PHASE));
        }

        /** The evaluation must charge what its own counters say, for both sides, in every position. */
        @Test
        void penalty_matchesTheCountersOverTheStsPositions() {
            var divergences = new ArrayList<String>();

            for (String fen : positions()) {
                var wf = evalFor(fen);
                int phase = wf.getPhase();

                for (int attacker = WHITE; attacker <= BLACK; attacker++) {
                    int expected = expectedPenalty(wf, attacker, phase);
                    int actual = wf.calcKingAttackPenalty(attacker, phase);

                    if (expected != actual) {
                        divergences.add("%s attacker %d: penalty %d, expected %d".formatted(fen, attacker, actual, expected));
                    }
                }
            }

            assertTrue(divergences.isEmpty(), divergences.size() + " penalties differ. First few: "
                    + divergences.subList(0, Math.min(5, divergences.size())));
        }
    }

    @Nested
    class Tables {

        @Test
        void shieldUnits_startAtZeroAndRise() {
            int[] units = WeightingFunction.PAWN_SHIELD_DEFENSE_UNITS;

            assertEquals(0, units[0], "no shield pawn must not lower the attack");

            for (int i = 1; i < units.length; i++) {
                assertTrue(units[i] >= units[i - 1], "a further shield pawn must not raise the danger");
            }
        }
    }

    private static List<String> positions() {
        var fens = new ArrayList<String>();

        try (var stream = WeightingFunctionPawnShieldTest.class.getResourceAsStream(POSITION_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("missing test resource " + POSITION_RESOURCE);
            }

            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    int tag = line.indexOf(BEST_MOVE_TAG);

                    if (tag > 0) {
                        fens.add(line.substring(0, tag).trim() + COUNTER_SUFFIX);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return fens;
    }
}
