package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.michaelfl.mychess.BlunderTest.DEPTH_BOUND_TIMEOUT_S;
import static org.michaelfl.mychess.BlunderTest.assertEngineStillPlays;
import static org.michaelfl.mychess.BlunderTest.gameFromFenAtDepth;
import static org.michaelfl.mychess.BlunderTest.searchCurrentPositionDeep;

/**
 * Characterization tests for defects the Strategic Test Suite surfaced.
 *
 * <p>Separate from {@link BlunderTest} because the provenance differs and that matters
 * when weighing the evidence: {@code BlunderTest}'s cases are hand-picked from games
 * myChess lost, so they are a biased sample of whatever the opponents happened to
 * punish. These come from a curated suite of 1188 positions that nobody selected with
 * myChess in mind. The search harness is shared with {@code BlunderTest} rather than
 * copied ({@code docs/testing.md} § 11.1, cross-test sharing).
 *
 * <h2>How these cases were found, and why the pipeline has three stages</h2>
 *
 * <p>The v4.4.2 run at depth 8 scored zero on 87 of the 1188 positions
 * ({@code test-results/sts-4.4.2-d8.txt}). A zero means the played move is not among the
 * ten candidates the suite annotates — it does <b>not</b> mean the move is bad. The
 * suite's point values are a <em>ranking, rescaled per position</em>: the best move is
 * worth 100 whether it leads the second by a tenth of a pawn or by three. So the misses
 * list cannot be turned into tests directly.
 *
 * <ol>
 *   <li>{@code tools/scan-sts-misses.py} asked Stockfish for the real centipawn loss of
 *       each candidate. Of the 191 positions scoring 0–20 points, <b>91 lose less than a
 *       pawn</b> — those are preferences, and a low score there is ranking noise.</li>
 *   <li>Each survivor was then asked at depths 8 to 11: does myChess keep the move, or
 *       abandon it? A move it keeps at every depth is a hole in the evaluation; a move it
 *       abandons for a materially better one was plies out of reach, i.e. a horizon
 *       effect. Abandoning it for something no better is neither — the evaluation is
 *       simply misranking, and that counts as an evaluation defect too.</li>
 *   <li>Only then were cases chosen — by classification first and loss second.</li>
 * </ol>
 *
 * <p><b>Both filters are needed, and in that order.</b> Point values are a ranking
 * <em>rescaled per position</em>: the best move is worth 100 whether it leads the second by
 * a tenth of a pawn or by three. Selecting on points alone put {@code King Activity.100}
 * forward as a defect when Stockfish 18 scores myChess's 2-point move and the suite's
 * 100-point move at exactly 0.00 — the gap there is Stockfish 15 disagreeing with
 * Stockfish 18, not a flaw in myChess. And the cheap filter has to run first, because the
 * depth sweep costs minutes per position while the loss measurement costs seconds.
 *
 * <h2>The finding that shaped this class</h2>
 *
 * <p><b>Ranking the zero-scoring positions by centipawn loss selects horizon effects
 * systematically</b> — 25 of 25 classified, with the abandonment depths clustering at
 * <b>depth 9</b>, a single ply beyond the measurement depth. The reason is structural: a
 * large loss usually means something concrete and tactical, and tactics are exactly what
 * extra depth resolves. So the STS misses are material for the search roadmap
 * (§§ 12.1–12.6: LMR, PVS, history ordering), not a source of evaluation defects — which
 * is the opposite of what the suite is usually reached for.
 *
 * <p>Evaluation defects live in the other band: moves that <em>are</em> among Stockfish's
 * ten candidates but worth a fifth of the best or less. Those carry no refutation a ply
 * deeper would reveal, so when myChess keeps choosing them at every depth it is a
 * preference — the evaluation ranking them wrongly.
 *
 * <p>The two halves below are separated accordingly, and every case names the depth
 * behavior that put it there. That is what makes each label a measurement rather than an
 * assertion, and what will explain a future flip.
 *
 * <p><b>These tests are more fragile than an evaluation characterization, on purpose.</b>
 * An evaluation case flips when its defect is fixed, which is the point. A horizon case
 * flips as soon as <em>any</em> search improvement makes more visible at the pinned depth,
 * including work with nothing to do with this position. A flip here is therefore not
 * automatically a bug: check whether the engine now finds the better move, and if so,
 * convert the case to {@code assertEngineAvoids} exactly as
 * {@code docs/testing.md} § 11.3 prescribes.
 *
 * <p>Every case is pinned at <b>depth 8</b>, the depth the suite is measured at, so the
 * test and the reported STS score talk about the same engine. Every evaluation quoted
 * below is Stockfish 18 at depth 24 from the side to move.
 *
 * @author Michael Fleischhauer
 */
@Tag("slow")
class StsDefectTest {

    /**
     * The STS measurement depth, and therefore the pin depth for every case here.
     *
     * <p>Deliberately not the depth at which each case happens to be sharpest: a case
     * pinned at the measurement depth is directly comparable to the score in
     * {@code docs/sts-history.md}, and every case below reproduces here.
     */
    private static final int PIN_DEPTH = Sts.DEFAULT_DEPTH;

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    // -----------------------------------------------------------------------
    // Horizon effects — the move is abandoned two to four plies deeper, so the
    // evidence is for the search work, not for the evaluation.
    // -----------------------------------------------------------------------

    /**
     * STS {@code Square Vacancy.099} — the single worst position of the whole run:
     * myChess is winning and plays into a forced mate.
     *
     * <p>{@code 3r4/k1pr4/npR1p3/p3Pp1p/P1QP2pN/q5P1/6PP/1R5K b - - 0 1}, black to move
     * and better by 3.66. myChess plays {@code 1...Rxd4}, grabbing the pawn with the rook
     * that was the only thing guarding c7 — and is <b>mated in five</b>:
     * {@code 2.Rxc7+ Nxc7 3.Qxc7+ Ka8 4.Qc6+ Kb8 5.Qxb6+ Kc8#}. The suite's move
     * {@code 1...Qe3} keeps black at +3.88; Stockfish reads +4.38 after it.
     *
     * <p>Measured loss <b>13.66 pawns</b>, the largest in the run, and myChess reports
     * +1.84 for itself while being mated — a gap that looks like a textbook evaluation
     * blind spot. It is not. The move survives depths 6 through 10 and is abandoned at
     * <b>depth 11</b>, where {@code Qe3} is found: the mate is simply out of reach at 8
     * plies. This case is the reason this class classifies before it selects.
     *
     * <p><b>Test family:</b> search-horizon (defect)
     *
     * <p>TODO: once the search reaches this at depth 8 (LMR/PVS would), invert to
     * {@code assertEngineAvoids} and keep the position — a forced mate five moves out is
     * worth a permanent guard.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void squareVacancy099_atDepth8_removesTheOnlyDefenderOfC7AndIsMatedInFive() throws Exception {
        var game = gameFromFenAtDepth("3r4/k1pr4/npR1p3/p3Pp1p/P1QP2pN/q5P1/6PP/1R5K b - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.d7, Board.d4, "Rxd4",
                "which is mate in five after 2.Rxc7+ (Stockfish 18 depth 24; the suite's Qe3 "
                        + "holds +3.88). Abandoned at depth 11, so this is reach, not evaluation");
    }

    /**
     * STS {@code Square Vacancy.079} — the deepest correction of the five, and the one
     * closest to being an evaluation defect without being one.
     *
     * <p>{@code r1b2rk1/p6p/1p1P1pp1/q1p5/2P1PQ2/P7/1R2B1PP/5R1K b - - 0 1}, black to
     * move at +0.70. myChess takes the a3 pawn with {@code 1...Qxa3} and lands at
     * <b>−3.91</b>: the queen has no way back, and {@code 2.Qd2} followed by {@code 3.Ra2}
     * hunts it down the a-file. The suite's {@code 1...Qc3} keeps the game level (+0.27).
     *
     * <p>Measured loss <b>4.05 pawns</b>. myChess holds {@code Qxa3} through depth 11 and
     * only drops it at <b>depth 12</b> — four plies beyond the measurement depth, the
     * deepest of the five and therefore the most demanding target for the search work.
     *
     * <p><b>Test family:</b> search-horizon (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once the trap is visible at depth 8.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void squareVacancy079_atDepth8_grabsA3AndStrandsItsQueen() throws Exception {
        var game = gameFromFenAtDepth("r1b2rk1/p6p/1p1P1pp1/q1p5/2P1PQ2/P7/1R2B1PP/5R1K b - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.a5, Board.a3, "Qxa3",
                "after which 2.Qd2 and 3.Ra2 trap the queen for −3.91, where the suite's Qc3 "
                        + "holds +0.27. Abandoned at depth 12, the deepest of this class");
    }

    /**
     * STS {@code Center Control.071} — myChess plays the h-pawn nudge that the king-safety
     * family is full of, and it is a wasted tempo that loses the game.
     *
     * <p>{@code b4rk1/8/4pr1p/2q5/P4p2/2PB4/6PP/R3QR1K w - - 0 1}, white to move at +1.36.
     * myChess plays {@code 1.h3} and drops to <b>−2.46</b>, because {@code 1...f3!} breaks
     * through: {@code 2.Rxf3 Rxf3 3.Qxe6+ R3f7 4.Bg6}. The suite's {@code 1.Be4} holds
     * +1.54 by taking the a8 bishop off the long diagonal first.
     *
     * <p>Measured loss <b>3.35 pawns</b>. Kept through depth 9, abandoned at
     * <b>depth 10</b>.
     *
     * <p>Worth noting for the family tally rather than for this test: {@code h3} is the
     * literal move shape of several open {@code king-safety} cases (`20.h3`, `33.f3`). Here
     * it is measurably a horizon effect, not an evaluation hole — one data point against
     * reading every flank-pawn nudge as the same defect.
     *
     * <p><b>Test family:</b> search-horizon (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once {@code 1...f3} is seen at depth 8.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void centerControl071_atDepth8_playsH3AndAllowsTheF3Break() throws Exception {
        var game = gameFromFenAtDepth("b4rk1/8/4pr1p/2q5/P4p2/2PB4/6PP/R3QR1K w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.h2, Board.h3, "h3",
                "after which 1...f3! breaks through for −2.46, where the suite's Be4 holds "
                        + "+1.54. Abandoned at depth 10");
    }

    /**
     * STS {@code Undermine.098} — the missed h-pawn thrust, and the clearest instance of
     * an attack whose value matures beyond the horizon.
     *
     * <p>{@code rn3rk1/p1p1qp2/1pbppn1p/6p1/P1PP4/2PBP1B1/3N1P1P/R2QK1R1 w Q - 0 1}, white
     * to move at +2.10 with black's king behind a loosened g5/h6 pawn pair. The suite's
     * move is {@code 1.h4!}, undermining that pair: {@code 1...g4 2.Bf4 Kg7 3.Rxg4+ Kh8
     * 4.Bxh6} for +2.13. myChess plays {@code 1.Qb3} instead and the advantage evaporates
     * to <b>−0.18</b>.
     *
     * <p>Measured loss <b>2.45 pawns</b>. Kept through depth 10, abandoned at
     * <b>depth 11</b> — so the attack is real and myChess can see it, three plies later
     * than it needs to.
     *
     * <p><b>Test family:</b> search-horizon (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once {@code 1.h4} is found at depth 8.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void undermine098_atDepth8_declinesTheH4ThrustAgainstTheLoosenedKingside() throws Exception {
        var game = gameFromFenAtDepth(
                "rn3rk1/p1p1qp2/1pbppn1p/6p1/P1PP4/2PBP1B1/3N1P1P/R2QK1R1 w Q - 0 1", PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.d1, Board.b3, "Qb3",
                "which lets +2.10 become −0.18, where the suite's h4! keeps +2.13 by "
                        + "undermining g5/h6. Abandoned at depth 11");
    }

    /**
     * STS {@code King Activity.035} — a pawn pushed for its own sake in an endgame, handing
     * back a won position.
     *
     * <p>{@code 3r2k1/1p4p1/p2P3p/1pPN4/1K4b1/8/2R4P/8 w - - 0 1}, white to move at +2.21.
     * myChess plays {@code 1.c6}, which simply loses the pawn: {@code 1...bxc6 2.Rxc6 a5+
     * 3.Kxa5 Bf3} and the game is level at <b>−0.04</b>. The suite's {@code 1.Rf2} keeps
     * +3.27 by activating the rook first.
     *
     * <p>Measured loss <b>2.28 pawns</b>. Kept through depth 10, abandoned at
     * <b>depth 11</b>.
     *
     * <p><b>Test family:</b> search-horizon (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once {@code 1...bxc6} is seen at
     * depth 8.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void kingActivity035_atDepth8_pushesC6AndGivesThePawnAway() throws Exception {
        var game = gameFromFenAtDepth("3r2k1/1p4p1/p2P3p/1pPN4/1K4b1/8/2R4P/8 w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.c5, Board.c6, "c6",
                "after which 1...bxc6 levels the game at −0.04, where the suite's Rf2 keeps "
                        + "+3.27. Abandoned at depth 11");
    }

    // -----------------------------------------------------------------------
    // Evaluation defects — the move is kept at every depth from 8 to 11 and the
    // replacement, where there is one, is no better. Extra search does not help,
    // so the evidence is for the evaluation.
    //
    // These five were selected by "aggregate backing" -- their themes were among the
    // weakest in the v4.4.2 depth-8 run -- and a later depth-10 run over the same
    // 1188 positions showed that criterion to be largely wrong. A low theme score
    // does NOT mean the theme is evaluation-limited: six of the fifteen gain more
    // than a fifth of their remaining headroom from two extra plies, so their low
    // score was reach, not knowledge (docs/sts-history.md).
    //
    // Of these five, three sit on themes that are evaluation-limited after all
    // (Square Vacancy 11 %, Offer of Simplification 10 %, AT 18 % of headroom
    // captured) and two do not (King Activity 24 %, AKPC 21 % -- both depth-limited).
    // The two are marked in place. Every case below stands regardless, because each
    // is pinned by its own measurement: a verified centipawn loss and a point value
    // that does not move between depth 8 and depth 11. It is the borrowed argument
    // that failed, not the finding.
    // -----------------------------------------------------------------------

    /**
     * STS {@code Square Vacancy.062} — the largest verified evaluation loss found: myChess
     * gives a check that trades the queens off and loses to the passed c-pawn.
     *
     * <p>{@code 5rk1/3n4/3Rp1p1/2P2q1p/1p1P2n1/6P1/1B1NQ2P/6K1 b - - 0 1}, black to move at
     * +1.34. myChess plays {@code 1...Qf2+}, which looks forcing and is losing:
     * {@code 2.Qxf2 Rxf2 3.c6 Nf8 4.c7 Rxd2 5.c8=Q} and the pawn queens. Stockfish reads
     * <b>−3.41</b> after it, against +1.49 for the suite's {@code 1...Qc2}.
     *
     * <p>Measured loss <b>4.27 pawns</b>. Kept at depths 8, 9, 10 and 11 — the point value
     * is 14 of 100 at every one of them, so the choice is a preference and not a matter of
     * reach. The mechanism is a pawn two squares from promotion valued as an ordinary pawn:
     * the piece-square tables express where a pawn stands, not that it is about to become a
     * queen. Same root as the two passed-pawn failures already noted under
     * {@code endgame-technique} in {@code docs/testing.md}.
     *
     * <p><b>Test family:</b> passed-pawn (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once a passed-pawn term prices the
     * c-pawn race.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void squareVacancy062_tradesTheQueensOffAndLosesToThePassedCPawn() throws Exception {
        var game = gameFromFenAtDepth("5rk1/3n4/3Rp1p1/2P2q1p/1p1P2n1/6P1/1B1NQ2P/6K1 b - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.f5, Board.f2, "Qf2+",
                "which loses to the c-pawn after 2.Qxf2 Rxf2 3.c6 (−3.41 against +1.49 for "
                        + "the suite's Qc2). Worth 14 of 100 points at every depth from 8 to 11");
    }

    /**
     * STS {@code King Activity.085} — a flank pawn pushed instead of the king activated, in
     * the theme that speaks most directly to the open king work.
     *
     * <p>{@code 8/4q1k1/5pp1/pp2b3/2p1P3/P1P1Q1Pp/7P/3R2K1 w - - 0 1}, white to move at
     * +2.13. The suite's move is {@code 1.Kf1}, walking the king toward safety and the
     * center for +2.65. myChess plays {@code 1.g4} instead and the advantage is gone:
     * <b>0.00</b> after {@code 1...b4 2.g5 bxc3 3.Qxh3 c2}.
     *
     * <p>Measured loss <b>1.93 pawns</b>, and the choice is worth <b>1 of 100 points at
     * every depth from 8 to 11</b> — one of the flattest trajectories in the whole scan.
     *
     * <p>The engine prefers the flank pawn *to* the king move, which is the same
     * mis-weighting the open {@code king-safety} cases show from the other side
     * (`20.h3`, `33.f3`) — but that is a resemblance between positions, not shared evidence.
     *
     * <p><b>Two things written here first were wrong and are corrected.</b> The case was
     * presented as joining the theme table's findings; it does not. Theme 11 captures
     * <b>24 % of its remaining headroom</b> at depth 10 and is depth-limited, so its 62.6 %
     * says myChess cannot calculate these endings out — this position, flat at 1 point across
     * four depths, is an exception within it. And theme 11 is <em>King Activity</em>, the
     * king used as an active piece; the STS has no theme for <em>king safety</em>, the king
     * under attack, so nothing here is evidence for [roadmap § 12.21] in either direction.
     * That case rests on the 19 game-derived cases and the keBKOXd1 guard, untouched by this
     * run. See docs/sts-history.md.
     *
     * <p><b>Test family:</b> king-activity (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once king activity is priced. Not tracked
     * with § 12.21 — that section is about king *safety*, which is a different defect.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void kingActivity085_pushesTheGPawnInsteadOfWalkingTheKingIn() throws Exception {
        var game = gameFromFenAtDepth("8/4q1k1/5pp1/pp2b3/2p1P3/P1P1Q1Pp/7P/3R2K1 w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.g3, Board.g4, "g4",
                "which throws away +2.13 for 0.00, where the suite's Kf1 keeps +2.65. Worth "
                        + "1 of 100 points at every depth from 8 to 11");
    }

    /**
     * STS {@code AKPC.036} — from the weakest theme of the entire run: the kingside pawn
     * advance is not seen as a plan at all.
     *
     * <p>{@code 3r2k1/2q1rp2/4p1p1/2p1P2p/1p3Q2/1P1bP1PP/P4RBK/2R5 w - - 0 1}, white to
     * move at +1.14. The suite's {@code 1.Qf6} prepares exactly the advance the theme is
     * named for — Stockfish continues {@code 1...Ba6 2.g4 h4 3.g5} — and holds +1.05.
     * myChess plays the neutral {@code 1.Rd2} and the edge evaporates to <b>−0.09</b>.
     *
     * <p>Measured loss <b>1.23 pawns</b>, worth <b>5 of 100 points at every depth from 8 to
     * 11</b>. Note what the position does *not* contain: no tactic, no capture, nothing a
     * deeper search would stumble over. What is missing is the idea that a pawn advance on
     * the side where the opponent's king sits is worth something, and myChess has no term
     * that could express it.
     *
     * <p><b>Its theme does not back this case, contrary to what was written here first.</b>
     * Theme 8 is the weakest of the fifteen at depth 8 (59.5 %), which is why the case was
     * picked — but the depth-10 run showed the theme captures <b>21 % of its remaining
     * headroom</b> from two extra plies and is therefore depth-limited, not
     * evaluation-limited (docs/sts-history.md). The theme's low score says myChess cannot
     * calculate these positions out, not that it lacks the concept. This position is the
     * exception within it: flat at 5 points across four depths, so here the concept really
     * is missing. Do not cite theme 8 as evidence for a flank-advance term.
     *
     * <p><b>Test family:</b> flank-pawn-advance (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once a flank-advance or space term
     * exists.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void akpc036_playsANeutralRookMoveInsteadOfPreparingTheKingsidePawnAdvance() throws Exception {
        var game = gameFromFenAtDepth("3r2k1/2q1rp2/4p1p1/2p1P2p/1p3Q2/1P1bP1PP/P4RBK/2R5 w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.f2, Board.d2, "Rd2",
                "which gives up +1.14 for −0.09, where the suite's Qf6 prepares g4-g5 and "
                        + "holds +1.05. Worth 5 of 100 points at every depth from 8 to 11");
    }

    /**
     * STS {@code AT.098} — the queens traded off for nothing, in the theme where myChess
     * finds the best move least often of all fifteen.
     *
     * <p>{@code 1k1rqb1r/ppp5/2n4n/3p2p1/3P3p/2PB4/PP2Q1PP/RN2BRK1 b - - 0 1}, black to move
     * at +0.84. myChess plays {@code 1...Qxe2}, and after {@code 2.Bxe2} the position is
     * <b>−0.98</b> — a full swing of nearly two pawns from trading the one piece that was
     * doing the work. The suite's {@code 1...Qd7} keeps +0.82 and the queen on the board.
     *
     * <p>Measured loss <b>1.72 pawns</b>, worth <b>18 of 100 points at every depth from 8 to
     * 11</b>. Theme 15, *Avoid Pointless Exchange*, produced only <b>19 best moves out of
     * 73</b> in the v4.4.2 run — the lowest best-move count of any theme — and the depth-10
     * run confirms the theme is <b>evaluation-limited</b>: two extra plies capture only 18 %
     * of its remaining headroom. So the aggregate does back this case, which is not true of
     * every case in this section (see the note above). An engine whose quiescence search is
     * driven by captures and whose evaluation has no term for keeping tension is biased
     * toward exactly this move.
     *
     * <p><b>Test family:</b> pointless-exchange (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once exchanges are valued by what they
     * leave behind rather than by material alone.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void at098_tradesTheQueensOffAndTurnsAnAdvantageIntoADeficit() throws Exception {
        var game = gameFromFenAtDepth("1k1rqb1r/ppp5/2n4n/3p2p1/3P3p/2PB4/PP2Q1PP/RN2BRK1 b - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.e8, Board.e2, "Qxe2",
                "which turns +0.84 into −0.98, where the suite's Qd7 holds +0.82. Worth 18 "
                        + "of 100 points at every depth from 8 to 11");
    }

    /**
     * STS {@code Offer of Simplification.090} — the second case of the same family, and the
     * first evaluation defect the pipeline confirmed: the bishop handed over for a knight.
     *
     * <p>{@code r2r4/5kpp/p2q1n2/1p4B1/3b4/2Np1Q2/PP3PPP/3R1RK1 w - - 0 1}, white to move at
     * +2.12. myChess plays {@code 1.Bxf6}, giving up the bishop for the f6 knight, and after
     * {@code 1...Qxf6 2.Rxd3 Qxf3} the position is <b>+0.22</b>. The suite's {@code 1.Ne4}
     * keeps +2.26 — and Stockfish's own continuation takes on f6 two moves later
     * ({@code 1...Qd5 2.Rfe1 Re8 3.Bxf6 gxf6}), so the capture is not wrong, only the
     * timing: played at once it lets the queen off the hook.
     *
     * <p>Measured loss <b>1.87 pawns</b>, worth <b>20 of 100 points at every depth from 8 to
     * 11</b>, with myChess's own score *rising* from +47 to +66 cp across those depths — it
     * grows more confident in the move the longer it looks.
     *
     * <p>Two cases in this family rather than one is the point: a defect seen twice in
     * unrelated positions is a property of the evaluation, not a quirk of a position.
     *
     * <p><b>Test family:</b> pointless-exchange (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} together with {@code at098} — one fix
     * should move both.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void offerOfSimplification090_givesUpTheBishopForTheKnightAtOnce() throws Exception {
        var game = gameFromFenAtDepth("r2r4/5kpp/p2q1n2/1p4B1/3b4/2Np1Q2/PP3PPP/3R1RK1 w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.g5, Board.f6, "Bxf6",
                "which drops +2.12 to +0.22, where the suite's Ne4 keeps +2.26 and takes on "
                        + "f6 two moves later. Worth 20 of 100 points at every depth from 8 to 11");
    }

    // -----------------------------------------------------------------------
    // Rook activation — the family the second pass produced, and the deepest
    // evaluation family in the suite with four cases.
    //
    // In each one the better move activates a rook (to the seventh, or onto an
    // open file or rank) and myChess plays a quiet piece move or a trade instead.
    // Mobility rewards squares a rook can see and the piece-square tables reward
    // centralization, but neither prices a rook ON the seventh or the tempo of
    // seizing a file, because those pay off later than the search horizon.
    // -----------------------------------------------------------------------

    /**
     * STS {@code 7th Rank.078} — castling short instead of taking the seventh rank, and the
     * whole advantage goes with it.
     *
     * <p>{@code r1b2rk1/1p2q1b1/1n4p1/pP1p4/3Pp3/6BN/P2QBPP1/2R1K2R w K - 0 1}, white to move
     * at +1.58. The suite plays {@code 1.Rc7} and holds +1.65. myChess plays {@code 1.0-0},
     * a move that is never *wrong* and here costs everything: <b>0.00</b> after
     * {@code 1...Bxh3 2.gxh3 Rac8 3.Rxc8 Nxc8 4.Qxa5 Bxd4}, because black gets the c-file
     * first.
     *
     * <p>Measured loss <b>1.51 pawns</b>, worth 10 of 100 points at every depth from 8 to 11.
     * Note which move myChess prefers: the safe developing one. Castling is cheap to like —
     * king safety and rook connection both score — while {@code Rc7} pays off only once the
     * rook is there.
     *
     * <p><b>Test family:</b> rook-activation (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once a rook-on-the-seventh or
     * open-file-tempo term exists.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void seventhRank078_castlesInsteadOfTakingTheSeventhRank() throws Exception {
        var game = gameFromFenAtDepth("r1b2rk1/1p2q1b1/1n4p1/pP1p4/3Pp3/6BN/P2QBPP1/2R1K2R w K - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.e1, Board.g1, "0-0",
                "which turns +1.58 into 0.00 because black takes the c-file first, where the "
                        + "suite's Rc7 holds +1.65. Worth 10 of 100 points at every depth from 8 to 11");
    }

    /**
     * STS {@code 7th Rank.090} — the rooks traded off instead of one planted on the seventh.
     *
     * <p>{@code r4rk1/pp2n2p/4P1p1/2Qp2q1/1P6/5R2/P5PP/4RBK1 w - - 0 1}, white to move at
     * +1.05. {@code 1.Rf7} keeps +1.09. myChess plays {@code 1.Rxf8+} — a check, so it looks
     * like progress — and after {@code 1...Rxf8 2.Qxa7 Qd2} the advantage is <b>+0.05</b>.
     *
     * <p>Measured loss <b>1.05 pawns</b>, worth 12 of 100 points at all four depths. This is
     * the family's overlap with {@code pointless-exchange}: the move is both a trade and a
     * refusal to activate, and it is filed here because what is lost is the seventh rank
     * rather than a piece's quality.
     *
     * <p><b>Test family:</b> rook-activation (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids}; one term should move this and
     * {@code seventhRank078} together.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void seventhRank090_tradesTheRooksInsteadOfPlantingOneOnTheSeventh() throws Exception {
        var game = gameFromFenAtDepth("r4rk1/pp2n2p/4P1p1/2Qp2q1/1P6/5R2/P5PP/4RBK1 w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.f3, Board.f8, "Rxf8+",
                "a check that trades the advantage away, +1.05 down to +0.05, where the "
                        + "suite's Rf7 keeps +1.09. Worth 12 of 100 points at every depth from 8 to 11");
    }

    /**
     * STS {@code Open Files and Diagonals.041} — a pawn nudged instead of the rook swung to
     * the open h-file, and the clearest instance of an unstable trajectory.
     *
     * <p>{@code 3r4/3pkpp1/4p3/2p3q1/6r1/1P6/P5B1/2R1RQK1 b - - 0 1}, black to move at +2.61.
     * {@code 1...Rh8} keeps +2.74 by taking the file the white king sits on. myChess plays
     * {@code 1...d6} and the game is level, <b>0.00</b>, after {@code 2.Rc4 Rxc4 3.Qxc4}.
     *
     * <p>Measured loss <b>2.13 pawns</b>. The point trajectory is <b>1 → 12 → 0 → 22</b>
     * across depths 8 to 11: the choice churns without ever becoming good, and at depth 10 it
     * is *worse* than at depth 8. That rules out a horizon effect by definition — extra
     * search cannot make a position worse if the earlier choice was merely short-sighted.
     *
     * <p><b>Test family:</b> rook-activation (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once seizing an open file toward the enemy
     * king is priced.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void openFiles041_nudgesAPawnInsteadOfSwingingTheRookToTheOpenFile() throws Exception {
        var game = gameFromFenAtDepth("3r4/3pkpp1/4p3/2p3q1/6r1/1P6/P5B1/2R1RQK1 b - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.d7, Board.d6, "d6",
                "which levels a +2.61 position at 0.00, where the suite's Rh8 keeps +2.74. "
                        + "Point value churns 1/12/0/22 over depths 8 to 11 without ever getting good");
    }

    /**
     * STS {@code Center Control.001} — a knight sortie that walks into a rook capture, where
     * the rook belonged in the center.
     *
     * <p>{@code 1k1r4/4bp2/p1q1pnr1/6B1/NppP3P/6P1/1P3P2/2RQR1K1 w - - 0 1}, white to move at
     * +1.31. {@code 1.Re5} keeps +1.34. myChess plays {@code 1.Nc5} and after
     * {@code 1...Rxg5! 2.Nxa6+ Qxa6 3.hxg5} it is <b>−0.48</b> — the knight leaves the g5
     * bishop unsupported.
     *
     * <p>Measured loss <b>1.47 pawns</b>, worth <b>1 of 100 points at every depth from 8 to
     * 11</b> — one of the flattest trajectories in the scan, and the reason it is filed here
     * rather than under a tactical family: four depths of search never reconsider it.
     *
     * <p><b>Test family:</b> rook-activation (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids}.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void centerControl001_sortiesTheKnightWhereTheRookBelongedInTheCenter() throws Exception {
        var game = gameFromFenAtDepth("1k1r4/4bp2/p1q1pnr1/6B1/NppP3P/6P1/1P3P2/2RQR1K1 w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.a4, Board.c5, "Nc5",
                "which loses the g5 bishop to 1...Rxg5 and turns +1.31 into −0.48, where the "
                        + "suite's Re5 holds +1.34. Worth 1 of 100 points at every depth from 8 to 11");
    }

    // -----------------------------------------------------------------------
    // Pawn thrusts, king activity, an exchange, and a defensive resource.
    // -----------------------------------------------------------------------

    /**
     * STS {@code Undermine.023} — the queenside thrust declined for a quiet queen move.
     *
     * <p>{@code 2kr2r1/1bpnqp2/1p1ppn2/p5pp/P1PP4/4PP2/1P1NBBPP/R2Q1RK1 w - - 0 1}, white to
     * move at +1.31. {@code 1.b4!} undermines the a5/b6 pawn pair in front of the black king
     * and holds +1.39. myChess plays {@code 1.Qc2} and black consolidates with {@code 1...c5}
     * to <b>0.00</b>.
     *
     * <p>Measured loss <b>1.26 pawns</b>, trajectory <b>13 → 9 → 13 → 13</b>: at depth 9 it
     * briefly prefers something *worse*.
     *
     * <p><b>Test family:</b> pawn-thrust (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once pawn thrusts against a castled or
     * queenside king are priced — the same term as {@code akpc036} above, from the other wing.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void undermine023_declinesTheB4ThrustForAQuietQueenMove() throws Exception {
        var game = gameFromFenAtDepth("2kr2r1/1bpnqp2/1p1ppn2/p5pp/P1PP4/4PP2/1P1NBBPP/R2Q1RK1 w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.d1, Board.c2, "Qc2",
                "after which 1...c5 levels the game at 0.00, where the suite's b4! undermines "
                        + "a5/b6 for +1.39. Worth 13 points at depth 8, and only 9 at depth 9");
    }

    /**
     * STS {@code Undermine.073} — a winning position halved by moving a knight instead of
     * pushing the pawn that breaks the chain.
     *
     * <p>{@code k1qbr1n1/1p4p1/p1p1p1Np/2P2p1P/3P4/R7/PP2Q1P1/1K1R4 w - - 0 1}, white to move
     * and <b>winning at +3.65</b>. {@code 1.d5!} cracks the c6/e6 pawn pair open and keeps
     * +3.75. myChess plays {@code 1.Ne5} and drops to <b>+1.35</b> — still better, but a
     * winning position turned into a mere edge.
     *
     * <p>Measured loss <b>1.24 pawns</b>, worth 12 of 100 points at all four depths.
     *
     * <p><b>Test family:</b> pawn-thrust (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} together with {@code undermine023}.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void undermine073_movesTheKnightInsteadOfBreakingThePawnChainWithD5() throws Exception {
        var game = gameFromFenAtDepth("k1qbr1n1/1p4p1/p1p1p1Np/2P2p1P/3P4/R7/PP2Q1P1/1K1R4 w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.g6, Board.e5, "Ne5",
                "which lets a won +3.65 shrink to +1.35, where the suite's d5! keeps +3.75 by "
                        + "breaking the c6/e6 chain. Worth 12 of 100 points at every depth from 8 to 11");
    }

    /**
     * STS {@code King Activity.005} — the second case of that family: a knight sortie that
     * simply loses a piece, where the king wanted to step up.
     *
     * <p>{@code 1r2r3/1p1b3k/2p2n2/p1Pp4/P2N1PpP/1R2p3/1P2P1BP/3R2K1 b - - 0 1}, black to
     * move in a balanced position (0.00). {@code 1...Kg6} holds it. myChess plays
     * {@code 1...Ne4} and after {@code 2.Bxe4+ Rxe4 3.Kg2 Rxf4 4.Kg3} it is <b>−1.08</b>.
     *
     * <p>Measured loss <b>1.37 pawns</b>, worth <b>1 of 100 points at every depth from 8 to
     * 11</b>. Together with {@code kingActivity085} the family now has two cases in unrelated
     * positions, so the gap is a property of the evaluation rather than a quirk of one
     * position — which is what the family was missing.
     *
     * <p><b>Test family:</b> king-activity (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} once king activity is priced; tracked with
     * [roadmap § 12.21].
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void kingActivity005_sortiesTheKnightIntoBxe4WhereTheKingWantedToStepUp() throws Exception {
        var game = gameFromFenAtDepth("1r2r3/1p1b3k/2p2n2/p1Pp4/P2N1PpP/1R2p3/1P2P1BP/3R2K1 b - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.f6, Board.e4, "Ne4",
                "which loses a piece to 2.Bxe4+ and turns a level game into −1.08, where the "
                        + "suite's Kg6 holds 0.00. Worth 1 of 100 points at every depth from 8 to 11");
    }

    /**
     * STS {@code AT.063} — the third case of {@code pointless-exchange}: the queen given away
     * on f3 for nothing.
     *
     * <p>{@code 3rrbk1/5ppp/b1q5/p1p2P2/p1P5/4BQN1/PP1R2PP/2R3K1 b - - 0 1}, black to move at
     * +1.10. {@code 1...Qf6} keeps +1.23. myChess plays {@code 1...Qxf3}, and after
     * {@code 2.gxf3 Rxd2 3.Bxd2} the game is level at <b>0.00</b>: the trade repairs white's
     * structure and removes black's best piece.
     *
     * <p>Measured loss <b>1.13 pawns</b>, worth <b>1 of 100 points at every depth from 8 to
     * 11</b>. Three cases now share this family, all of them a queen or bishop given up for
     * an equal-material trade that costs the position — the pattern behind theme 15's
     * 19-best-moves-of-73, the worst best-move count in the run.
     *
     * <p><b>Test family:</b> pointless-exchange (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids} together with {@code at098} and
     * {@code offerOfSimplification090} — one term should move all three.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void at063_givesTheQueenAwayOnF3ForAnEqualMaterialTrade() throws Exception {
        var game = gameFromFenAtDepth("3rrbk1/5ppp/b1q5/p1p2P2/p1P5/4BQN1/PP1R2PP/2R3K1 b - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.c6, Board.f3, "Qxf3",
                "which repairs white's structure and levels a +1.10 position at 0.00, where "
                        + "the suite's Qf6 keeps +1.23. Worth 1 of 100 points at all four depths");
    }

    /**
     * STS {@code Square Vacancy.019} — the only line that holds is a forcing one, and myChess
     * plays a quiet rook move instead.
     *
     * <p>{@code 1Q6/1p2p2k/1r4pp/p1p2P2/q2nr1P1/7R/1P1R4/5NK1 w - - 0 1}, white to move in a
     * position Stockfish reads as <b>exactly balanced</b> — but only because {@code 1.Qf8}
     * keeps checking; black's Nd4, Re4, Rb6 and Qa4 are otherwise overwhelming. myChess plays
     * {@code 1.Rg2} and after {@code 1...Kg7 2.Ne3 g5} it is <b>−4.44</b>.
     *
     * <p>Measured loss <b>3.88 pawns</b>, the second largest verified evaluation loss in the
     * scan. Trajectory <b>19 → 0 → 19 → 19</b>: depth 9 picks something worse still. The
     * defect is not that myChess misses a tactic — it is that a position needing a forcing
     * continuation is scored as if a quiet move were available, so nothing pushes the search
     * toward the checks.
     *
     * <p><b>Test family:</b> defensive-resource (defect)
     *
     * <p>TODO: invert to {@code assertEngineAvoids}. Single case so far — treat the family as
     * provisional until a second one turns up.
     */
    @Test
    @Timeout(value = DEPTH_BOUND_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void squareVacancy019_playsAQuietRookMoveWhereOnlyPerpetualChecksHold() throws Exception {
        var game = gameFromFenAtDepth("1Q6/1p2p2k/1r4pp/p1p2P2/q2nr1P1/7R/1P1R4/5NK1 w - - 0 1",
                PIN_DEPTH, tt);
        var result = searchCurrentPositionDeep(game);

        assertEngineStillPlays(result, Board.d2, Board.g2, "Rg2",
                "which collapses a balanced position to −4.44, where only the suite's Qf8 and "
                        + "its checks hold 0.00. Worth 19 points at depth 8 and 0 at depth 9");
    }
}
