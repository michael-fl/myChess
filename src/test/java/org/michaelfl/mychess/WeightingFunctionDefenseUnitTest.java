package org.michaelfl.mychess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the defense units of {@link WeightingFunction}: own pieces bearing on their own
 * king zone, and how they lower the king-attack penalty of the opponent.
 *
 */
class WeightingFunctionDefenseUnitTest {

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    /** The production weights in the order of {@link KingDefenseUnits#PAWN} .. {@link KingDefenseUnits#KING}. */
    private static final int[] PRODUCTION_WEIGHTS = {
            WeightingFunction.DEFENSE_UNIT_PAWN,
            WeightingFunction.DEFENSE_UNIT_KNIGHT,
            WeightingFunction.DEFENSE_UNIT_BISHOP,
            WeightingFunction.DEFENSE_UNIT_ROOK,
            WeightingFunction.DEFENSE_UNIT_QUEEN,
            WeightingFunction.DEFENSE_UNIT_KING
    };

    private static final String POSITION_RESOURCE = "/sts/STS1-STS15_LAN_v6.epd";
    private static final String BEST_MOVE_TAG = " bm ";
    private static final String COUNTER_SUFFIX = " 0 1";

    private static WeightingFunction evalFor(String fen) {
        var weightingFunction = new WeightingFunction();
        weightingFunction.calculate(Fen.importFEN(fen));

        return weightingFunction;
    }

    @Nested
    class Pieces {

        @Test
        void knightNextToTheKingZone_countsAsAKnight() {
            var wf = evalFor("4k3/8/8/8/8/5N2/8/6K1 w - - 0 1"); // Nf3 bears on g1 and h2

            assertEquals(WeightingFunction.DEFENSE_UNIT_KNIGHT, wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void fianchettoBishop_countsAsABishop() {
            var wf = evalFor("4k3/8/8/8/8/8/6B1/6K1 w - - 0 1"); // Bg2 stands in the zone

            assertEquals(WeightingFunction.DEFENSE_UNIT_BISHOP, wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void rookOnTheBackRank_countsAsARook() {
            var wf = evalFor("4k3/8/8/8/8/8/8/R5K1 w - - 0 1"); // Ra1 sees along the empty first rank to f1

            assertEquals(WeightingFunction.DEFENSE_UNIT_ROOK, wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void queenFromAfar_countsAsAQueen() {
            var wf = evalFor("k7/8/8/8/8/8/8/Q5K1 w - - 0 1"); // Qa1 sees along the first rank

            assertEquals(WeightingFunction.DEFENSE_UNIT_QUEEN, wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void blockedRook_doesNotCount() {
            var wf = evalFor("4k3/8/8/8/8/8/8/RN4K1 w - - 0 1"); // Nb1 blocks Ra1; Nb1 itself is too far

            assertEquals(0, wf.getDefenseUnit()[WHITE], "a rook that cannot reach the zone must not count");
        }

        @Test
        void farKnight_doesNotCount() {
            var wf = evalFor("4k3/8/8/8/N7/8/8/6K1 w - - 0 1");

            assertEquals(0, wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void rookBearingOnSeveralZoneSquares_countsOnce() {
            var wf = evalFor("4k3/8/8/8/8/8/8/5RK1 w - - 0 1"); // Rf1 bears on f2 and g1

            assertEquals(WeightingFunction.DEFENSE_UNIT_ROOK, wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void severalDefenders_add() {
            var wf = evalFor("4k3/8/8/8/8/5N2/6B1/5RK1 w - - 0 1");

            assertEquals(WeightingFunction.DEFENSE_UNIT_KNIGHT + WeightingFunction.DEFENSE_UNIT_BISHOP
                    + WeightingFunction.DEFENSE_UNIT_ROOK, wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void enemyPieceInTheZone_isNoDefender() {
            var wf = evalFor("4k3/8/8/8/8/5n2/8/6K1 w - - 0 1");

            assertEquals(0, wf.getDefenseUnit()[WHITE], "an enemy knight next to the white king defends nothing");
        }

        @Test
        void blackDefenders_countForBlack() {
            var wf = evalFor("6k1/5rb1/5n2/8/8/8/8/4K3 b - - 0 1");

            assertEquals(WeightingFunction.DEFENSE_UNIT_KNIGHT + WeightingFunction.DEFENSE_UNIT_BISHOP
                    + WeightingFunction.DEFENSE_UNIT_ROOK, wf.getDefenseUnit()[BLACK]);
            assertEquals(0, wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void mirroredPosition_givesTheSameDefenseToTheOtherSide() {
            var white = evalFor("r3k3/pp6/8/8/8/5NP1/5PBP/5RK1 w - - 0 1");
            var black = evalFor("5rk1/5pbp/5np1/8/8/8/PP6/R3K3 b - - 0 1");

            assertEquals(white.getDefenseUnit()[WHITE], black.getDefenseUnit()[BLACK]);
        }

        @Test
        void cornerKing_usesTheShiftedZone() {
            var wf = evalFor("4k3/8/8/8/8/8/8/4R2K w - - 0 1"); // h1 king reads g1's zone, so Re1 reaches f1

            assertEquals(WeightingFunction.DEFENSE_UNIT_ROOK, wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void secondEvaluation_startsFromZero() {
            var wf = new WeightingFunction();
            wf.calculate(Fen.importFEN("4k3/8/8/8/8/5N2/6B1/5RK1 w - - 0 1"));
            wf.calculate(Fen.importFEN("4k3/8/8/8/8/8/8/6K1 w - - 0 1"));

            assertEquals(0, wf.getDefenseUnit()[WHITE], "defense units of the previous position must not carry over");
        }

        @Test
        void missingKing_givesNoDefense() {
            var wf = evalFor("4k3/8/8/8/8/5N2/6B1/5R2 w - - 0 1");

            assertEquals(0, wf.getDefenseUnit()[WHITE]);
        }
    }

    /**
     * Pawns and the king carry a weight of their own in the table. These tests pin that the weight
     * is applied. With today's weights of 0 they pass trivially. They fail once a weight above 0
     * does not reach the defense units.
     */
    @Nested
    class PawnsAndKing {

        @Test
        void pawnBearingOnTheZone_addsThePawnWeight() {
            var wf = evalFor("4k3/8/8/8/8/8/5PK1/8 w - - 0 1"); // Kg2, zone f1-h3; f2 attacks g3

            assertEquals(WeightingFunction.DEFENSE_UNIT_PAWN + WeightingFunction.DEFENSE_UNIT_KING,
                    wf.getDefenseUnit()[WHITE]);
        }

        @Test
        void king_addsTheKingWeight() {
            var wf = evalFor("4k3/8/8/8/8/8/8/6K1 w - - 0 1");

            assertEquals(WeightingFunction.DEFENSE_UNIT_KING, wf.getDefenseUnit()[WHITE],
                    "the king always stands in its own zone");
        }
    }

    @Nested
    class Penalty {

        private static WeightingFunction withAttack(int attackers, int units, int defenseOfTheAttackedKing) {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[WHITE] = attackers;
            wf.getAttackUnit()[WHITE] = units;
            wf.getDefenseUnit()[BLACK] = defenseOfTheAttackedKing;

            return wf;
        }

        @Test
        void defense_lowersTheTableIndex() {
            var wf = withAttack(2, 7, 2);

            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[5],
                    wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "7 attack units against 2 defense units read entry 5");
        }

        @Test
        void defenseAtLeastAsLargeAsTheAttack_costsNothing() {
            var wf = withAttack(3, 5, 6);

            assertEquals(0, wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE));
        }

        @Test
        void attackersOwnDefense_doesNotLowerThePenalty() {
            var wf = withAttack(2, 8, 0);
            wf.getDefenseUnit()[WHITE] = 5;

            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[8],
                    wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "only the defense of the attacked king counts");
        }

        @Test
        void gate_stillCountsTheAttackers() {
            var wf = withAttack(1, 8, 0);

            assertEquals(0, wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE),
                    "one attacker stays below the gate whatever the defense");
        }

        @Test
        void effectiveIndexBeyondTheTable_isClamped() {
            var wf = withAttack(4, 30, 1);
            int last = WeightingFunction.KING_ATTACK_PENALTY[WeightingFunction.KING_ATTACK_PENALTY.length - 1];

            assertEquals(last, wf.calcKingAttackPenalty(WHITE, WeightingFunction.MAX_PHASE));
        }

        @Test
        void blackAttacking_usesWhiteDefense() {
            var wf = new WeightingFunction();
            wf.getKingAttackerCount()[BLACK] = 2;
            wf.getAttackUnit()[BLACK] = 6;
            wf.getDefenseUnit()[WHITE] = 1;

            assertEquals(WeightingFunction.KING_ATTACK_PENALTY[5],
                    wf.calcKingAttackPenalty(BLACK, WeightingFunction.MAX_PHASE));
        }
    }

    /**
     * The scan must count what an independent implementation counts. {@link KingDefenseUnits}
     * looks from the zone squares back to the defenders. The evaluation walks from each piece
     * forward. Both use the production weights, so every position must agree.
     */
    @Test
    void productionScan_agreesWithTheReferenceOverTheStsPositions() {
        var evaluator = new WeightingFunction();
        var divergences = new ArrayList<String>();

        for (String fen : positions()) {
            Board board = Fen.importFEN(fen);
            evaluator.calculate(board);

            int productionWhite = evaluator.getDefenseUnit()[WHITE];
            int productionBlack = evaluator.getDefenseUnit()[BLACK];
            int referenceWhite = KingDefenseUnits.weighted(board, GameStatus.TURN_WHITE, PRODUCTION_WEIGHTS);
            int referenceBlack = KingDefenseUnits.weighted(board, GameStatus.TURN_BLACK, PRODUCTION_WEIGHTS);

            if (productionWhite != referenceWhite || productionBlack != referenceBlack) {
                divergences.add("%s: production w=%d b=%d, reference w=%d b=%d"
                        .formatted(fen, productionWhite, productionBlack, referenceWhite, referenceBlack));
            }
        }

        assertTrue(divergences.isEmpty(), divergences.size() + " positions differ. First few: "
                + divergences.subList(0, Math.min(5, divergences.size())));
    }

    private static List<String> positions() {
        var fens = new ArrayList<String>();

        try (var stream = WeightingFunctionDefenseUnitTest.class.getResourceAsStream(POSITION_RESOURCE)) {
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
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        return fens;
    }
}
