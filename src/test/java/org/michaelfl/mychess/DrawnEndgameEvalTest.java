package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for material advantages that <b>cannot be converted</b>:
 * endgames a tablebase proves to be drawn, which myChess nevertheless scores as a
 * decisive advantage.
 *
 * <p>This is the mirror image of the defect
 * {@code BlunderTest.kd4_atMove49_characterizesNotSeeingTheWonKnightEndgame}
 * pins. There, material is level and the evaluation misses a positional win. Here
 * the evaluation sees material it cannot use: rook and knight against rook is
 * <em>drawn</em> with correct defense, and myChess reads it as nearly a full piece.
 *
 * <h2>Why the reference is unusually strong</h2>
 *
 * <p>Every other eval characterization in this suite compares against Stockfish,
 * i.e. against a very good estimate. These four positions are compared against the
 * <b>Syzygy tablebase</b> (queried through {@code tablebase.lichess.ovh}), which for
 * five pieces is exhaustively solved. The expected value is not an estimate but a
 * proof: the positions are drawn, full stop. There is no reference uncertainty to
 * argue about.
 *
 * <h2>What it costs in practice</h2>
 *
 * <p>Rated rapid game <a href="https://lichess.org/OcR3sqSx">OcR3sqSx</a>
 * (rust-in-pieces 2118 vs myChessJava 2114, ½-½), the first fixture below. The
 * last capture fell on ply 134; from that moment the tablebase says draw at every
 * point of the remaining game, verified at twelve sample plies. myChess then spent
 * <b>100 plies</b> — the full fifty-move budget — trying to win a proven draw,
 * because a knight up reads as +3.6. The opponent defended correctly throughout and
 * the game was drawn by the fifty-move rule on move 118.
 *
 * <h2>Why a hard-coded "drawn material" table would be wrong</h2>
 *
 * <p>Tempting fix, and a trap: rook and knight against rook is <em>not</em> always
 * drawn. Sampling 70 random legal positions of that material against the tablebase
 * gives <b>36 % wins</b> (rook and bishop against rook: 43 %). Those wins are real —
 * mostly positions where the defense has already collapsed, but real. Returning a
 * flat 0.00 for the material signature would throw them away. The correct shape is a
 * <b>scaling factor</b> that pulls the score toward zero without erasing it, so the
 * engine keeps looking for the third of positions that are winnable. Two of the
 * probes written for this file underline the point: hand-built "rook against knight"
 * and "rook against bishop" positions came back from the tablebase as <em>wins</em>,
 * not draws.
 *
 * @author Michael Fleischhauer
 */
class DrawnEndgameEvalTest {

    /**
     * The fixtures: four positions the Syzygy tablebase proves drawn (verdicts read from
     * {@code tablebase.lichess.ovh} on 2026-08-17), each with one side holding an extra
     * piece. The first is from a real game; the other three are textbook shapes — defender
     * centralized, defender cornered, defender on the edge — chosen so the result cannot be
     * blamed on one accidental position.
     *
     * <p>The {@code whiteIsUpMaterial} flag exists so the assertion can be made on the
     * <em>signed</em> score. Testing {@code Math.abs(weight) > floor} would be shorter and
     * strictly weaker: it would also pass if the evaluation returned the right magnitude
     * with the wrong sign, which is a live failure mode in this codebase — see
     * {@code MirrorEvalTest} and the mate-score sign bug behind
     * {@code ScoreTTAdjustmentTest}. The advantaged side differs across the fixtures (in
     * the game position it is black), so the sign has to be carried per position rather
     * than assumed.
     */
    static Stream<Arguments> provenDrawnEndgames() {
        return Stream.of(
                Arguments.of("OcR3sqSx after ply 134 — R+N vs R, black a knight up",
                        "8/8/8/3r1nk1/R7/3K4/8/8 w - - 0 68", false),
                Arguments.of("R+N vs R, defender centralized with an active rook",
                        "4r3/8/4k3/8/8/2K2N2/8/3R4 w - - 0 1", true),
                Arguments.of("R+N vs R, defending king in the corner",
                        "6rk/8/8/8/8/4KN2/8/6R1 w - - 0 1", true),
                Arguments.of("R+B vs R, defending king on the edge",
                        "7k/7r/8/8/8/5B2/4K3/3R4 w - - 0 1", true));
    }

    /**
     * Floor on what myChess must still read in a proven draw for the defect to count as
     * present. Measured on v4.4.2 the four positions score <b>3.53, 3.66, 3.67 and 3.71
     * pawns</b> at depth 8, and 3.61 to 3.69 at depth 12 — so 2.0 sits far below every
     * reading and far above the ~0 a scaled evaluation would produce.
     */
    private static final float PROVEN_DRAW_FLOOR = 2.0f;

    /**
     * Search depth for the probes. Eight rather than twelve purely for runtime: the
     * measured scores move by at most 0.15 pawns between the two, so the extra plies
     * buy nothing. <b>That flatness is itself the finding</b> — this is not a horizon
     * problem the search could grow out of.
     */
    private static final int PROBE_DEPTH = 8;

    private static final int PROBE_BUDGET_MS = 60_000;
    private static final int PROBE_TIMEOUT_S = 120;

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    private MoveAndWeight searchAtProbeDepth(String fen) throws Exception {
        var engineConfig = new EngineConfig.Builder()
                .maxDepth(PROBE_DEPTH)
                .millisPerMove(PROBE_BUDGET_MS)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
        var game = new Game(new GameConfig(MyChessEngine.class, engineConfig), Fen.importFEN(fen));

        return game.getEngine().nextMoveAsync().getResult(PROBE_TIMEOUT_S - 20, TimeUnit.SECONDS);
    }

    /**
     * Each proven draw is scored as a decisive advantage.
     *
     * <p><b>Parameterized rather than a loop, deliberately.</b> A loop would abort on the
     * first fixture that stops matching, which is exactly the wrong behavior for a
     * characterization: when endgame scaling lands, the interesting question is whether it
     * fixed <em>all four</em> shapes or only some, and four independent reports answer that
     * while one aborted loop does not.
     *
     * <p>The assertion is on the <em>signed</em> score — see
     * {@link #provenDrawnEndgames()} for why the absolute value would be weaker.
     *
     * <p><b>Characterization, not a goal.</b> It passes because the defect is present.
     * When endgame scaling lands, these scores collapse toward zero, this test starts
     * failing, and that is the signal to rewrite it: require the scores to stay
     * <em>below</em> a small bound instead, and keep the same four fixtures as the
     * regression guard.
     *
     * <p><b>Test family:</b> drawn-endgame-overvaluation (defect)
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("provenDrawnEndgames")
    @Timeout(value = PROBE_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void provenDrawnEndgame_isScoredAsNearlyAFullPieceAhead(String label, String fen,
                                                            boolean whiteIsUpMaterial) throws Exception {
        MoveAndWeight result = searchAtProbeDepth(fen);
        float expectedFloor = whiteIsUpMaterial ? PROVEN_DRAW_FLOOR : -PROVEN_DRAW_FLOOR;
        boolean readAsDecisive = whiteIsUpMaterial
                ? result.weight() > expectedFloor
                : result.weight() < expectedFloor;

        assertTrue(readAsDecisive,
                "characterization: the tablebase proves this position drawn, and myChess must still read "
                        + "it as decisive for " + (whiteIsUpMaterial ? "white" : "black")
                        + ", because no term scales an unconvertible material advantage toward zero. "
                        + "A score between the two floors means endgame scaling has landed — invert this "
                        + "test to require a small score instead. A score past the floor on the WRONG side "
                        + "is a different bug entirely: the evaluation would have the sign inverted. "
                        + "Position: " + label + " | FEN " + fen
                        + " | white-POV eval " + result.weight() + ", expected past " + expectedFloor);
    }
}
