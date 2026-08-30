package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.engines.PositionSearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shape and the magnitude of {@link WeightingFunction#KING_ATTACK_PENALTY}.
 *
 * <p>The table is fitted rather than chosen — see `docs/king-safety.md` § 4.6 — and the two
 * properties that make it safe to ship are not properties a fit enforces. Both are checked here
 * because both have a history.
 *
 * <p><b>The cap.</b> The material-only shortcut switches the positional evaluation off once the
 * material swing from the root passes {@link PositionSearch#EVALUATE_MATERIAL_ONLY_THRESHOLD}.
 * A minor piece is 300, so every piece sacrifice is past it. If the attack term can grow large
 * enough to make such a sacrifice look profitable on its own, the engine plays it and then goes
 * blind to the compensation it played for — the term is part of the evaluation the shortcut
 * discards, so it falls silent exactly in the positions it steered into. That combination
 * measured **−67.1 Elo** in the Audax fork, worse than having no attack term at all
 * (§ 4.4).
 *
 * <p>This is green today with a good margin — the table tops out at 85 against a limit of 100 —
 * and it is not decoration: the anchor-corpus calibration reaches 191 cp, entirely above the cap,
 * so the next re-fit can breach it. It also fails if someone *lowers* the threshold, which is the
 * other way the two constants can collide.
 *
 * <p><b>Monotonicity.</b> More attackers must never score as less danger. This is not a
 * hypothetical: the curve § 4.5 recommended fell from 49 to 40 between six and seven attack
 * units and would have failed this test, and the unconstrained fit behind § 4.6 fell from 34.3
 * to 15.2 across three to five units. Both descents were noise — constraining the fit to be
 * monotone costs 0.077 % of residual — but neither was visible without checking. The shipped
 * table is monotone by construction because the constraint went into the fit; this assertion
 * guards the hand-editing of a single entry afterwards.
 *
 * @author Michael Fleischhauer
 */
class KingAttackCurveTest {

    /**
     * Headroom demanded between the loudest the term can be and the point where the shortcut
     * blinds the search. A factor of two: the cap argued for in § 4.4 is 150 against a threshold
     * of 200, and the shipped curve tops out at 85.
     */
    private static final int SAFETY_DIVISOR = 2;

    @Test
    void curveCannotOnItsOwnMakeASacrificeLookProfitable() {
        int loudest = Arrays.stream(WeightingFunction.KING_ATTACK_PENALTY).max().orElseThrow();
        int limit = PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD / SAFETY_DIVISOR;

        assertTrue(loudest <= limit,
                "the king-attack term tops out at " + loudest + " cp against a limit of " + limit
                        + " (EVALUATE_MATERIAL_ONLY_THRESHOLD / " + SAFETY_DIVISOR + "). Above it the "
                        + "term can pay for a material investment whose compensation the "
                        + "material-only shortcut then hides from the search — the combination that "
                        + "measured −67.1 Elo in the Audax fork. Either re-calibrate the curve or "
                        + "revisit the threshold, but do not simply raise this bound");
    }

    @Test
    void curveNeverRewardsFewerAttackers() {
        int[] curve = WeightingFunction.KING_ATTACK_PENALTY;

        for (int units = 1; units < curve.length; units++) {
            assertTrue(curve[units] >= curve[units - 1],
                    "entry " + units + " (" + curve[units] + ") is below entry " + (units - 1)
                            + " (" + curve[units - 1] + "), so more attack units would score as "
                            + "less danger there");
        }
    }

    /**
     * Index 0 has to stay at zero. Only the difference of the two sides' entries reaches the
     * score, so a constant added to every entry cancels — which also means a non-zero entry 0
     * silently shifts nothing and merely misleads whoever reads the table.
     */
    @Test
    void curveStartsAtZero() {
        assertEquals(0, WeightingFunction.KING_ATTACK_PENALTY[0],
                "index 0 is the pinned reference point; only differences reach the score");
    }

    /**
     * The table is fitted over indices 0–8, which carry 99.7 % of king samples; everything above
     * is clamped onto the last entry by {@code calcKingAttackPenalty}. A longer table would
     * therefore contain entries the fit never placed.
     */
    @Test
    void curveCoversExactlyTheFittedRange() {
        assertEquals(9, WeightingFunction.KING_ATTACK_PENALTY.length,
                "the curve was fitted for 0..8 attack units; entries beyond that are not measured");
    }

    /** The STS suite, reused here purely as 1 188 tracked middlegame positions. */
    private static final String POSITION_RESOURCE = "/sts/STS1-STS15_LAN_v6.epd";

    /** STS lines carry four FEN fields; {@link Fen#importFEN(String)} wants six. */
    private static final String COUNTER_SUFFIX = " 0 1";

    /** Separator that ends the FEN on an STS line. */
    private static final String BEST_MOVE_TAG = " bm ";

    /**
     * The production scan and the implementation the curve was fitted over must agree exactly.
     *
     * <p><b>This is the assertion that makes the calibration mean anything.</b>
     * {@link WeightingFunction#KING_ATTACK_PENALTY} is a lookup indexed by an attack-unit count,
     * and it was fitted over {@link KingAttackUnits} — a second implementation living in test
     * sources, written because master had no attacker-set API at the time. The shipped table is
     * therefore only correct while the number the evaluation computes is the number the fit saw.
     * Nothing else in the suite relates the two: {@link KingAttackUnitsTest} pins the reference
     * implementation, {@code WeightingFunctionAttackUnitTest} pins the production one, and both
     * stay green if the two drift apart.
     *
     * <p>Four rules have to hold together for the counts to match: the per-piece weights, the
     * 3×3 zone, deduplication by origin square, and rays that stop at the first piece rather than
     * continuing through batteries and x-rays the way the Audax fork's do. Only the weights are
     * shared by construction — {@link KingAttackUnits} reads them from {@link WeightingFunction}
     * — so the other three are what this actually guards, and it guards them by comparing
     * results rather than representations.
     *
     * <p>Measured over the 39 619-position calibration corpus at the time it was written: zero
     * divergences. The 1 188 positions used here are the STS suite, borrowed as tracked
     * middlegame material rather than for anything it was designed to test — the corpus itself
     * is not in the repository.
     */
    @Test
    void theProductionScanCountsWhatTheCurveWasFittedOn() {
        var evaluator = new WeightingFunction();
        var divergences = new ArrayList<String>();

        for (String fen : positions()) {
            Board board = Fen.importFEN(fen);
            evaluator.calculate(board);

            int productionWhite = evaluator.getAttackUnit()[0];
            int productionBlack = evaluator.getAttackUnit()[1];
            int referenceWhite = KingAttackUnits.of(board, GameStatus.TURN_WHITE);
            int referenceBlack = KingAttackUnits.of(board, GameStatus.TURN_BLACK);

            if (productionWhite != referenceWhite || productionBlack != referenceBlack) {
                divergences.add("%s: production w=%d b=%d, reference w=%d b=%d"
                        .formatted(fen, productionWhite, productionBlack, referenceWhite, referenceBlack));
            }
        }

        assertTrue(divergences.isEmpty(),
                divergences.size() + " of the positions count different attack units in the "
                        + "evaluation than in KingAttackUnits, which is what KING_ATTACK_PENALTY "
                        + "was fitted against — so the table is being indexed by a quantity "
                        + "nobody calibrated. Either restore the agreement or refit the curve; do "
                        + "not relax this test. First few: "
                        + divergences.subList(0, Math.min(5, divergences.size())));
    }

    /** The FEN of every line in {@link #POSITION_RESOURCE}, with the missing counters appended. */
    private static List<String> positions() {
        var fens = new ArrayList<String>();

        try (var stream = KingAttackCurveTest.class.getResourceAsStream(POSITION_RESOURCE)) {
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
            throw new UncheckedIOException("failed to read " + POSITION_RESOURCE, e);
        }

        return fens;
    }
}
