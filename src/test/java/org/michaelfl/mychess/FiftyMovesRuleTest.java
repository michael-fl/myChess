package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.michaelfl.mychess.EngineTestBase.engineConfig;

/**
 * @author Michael Fleischhauer
 */
class FiftyMovesRuleTest {

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    @Test
    void testFiftyMovesRule() {
        GameImporter importer = GameImporter.importerFor("""
                1. e4 e5 2. d3 d6 3. Be2 Nf6 4. Nf3 Qd7 5. Nc3 Kd8 6. O-O Rg8 7. Rb1 Rh8 8. Be3 Qe8 9. Rc1
                Nc6 10. Ne1 Be6 11. Qd2 Kc8 12. Ra1 Kb8 13. Rd1 Ng8 14. Ra1 Nh6 15. Nf3 Nf5 16. Nh4 Nfe7
                17. Nf3 Qc8 18. Rfb1 Nd4 19. Nd1 Nec6 20. Ne1 Be7 21. Kh1 Rd8 22. Kg1 Re8 23. Kf1 Rf8 24.
                Kg1 Rg8 25. Kh1 Qf8 26. Bf4 Bh4 27. Bf3 Bc4 28. Qe3 Ne6 29. Nc3 Ne7 30. Bg4 Ng6 31. Nf3
                Nh8 32. Ng1 Nd8 33. Qc1 Be6 34. Bd1 Bc8 35. Bd2 Be7 36. Be1 Qe8 37. Nce2 Bf8 38. Ng3 Qd7
                39. Nf1 Qe8 40. Ne3 Qe6 41. Nc4 Qe8 42. Na5 Qe6 43. Nb3 Qe8 44. Nc5 Qe7 45. Ne6 Qe8 46.
                Ng5 Qe7 47. N5h3 Qe6 48. Nf4 Qe8 49. Nfe2 Qe6 50. Nc3 Qe8 51. Nd5 Nc6 52. Nf3
                """);
        var game = importer.importGame(Game.standardConfig());

        var halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(99, halfMoveClock, "Wrong half move clock");

        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not yet be finished - one move left");

        game.makeMove(MoveDescription.fromString("c8-d7", game.getTurn()));
        halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(100, halfMoveClock, "Wrong half move clock");
        assertEquals(GameResult.DRAW, game.getResult(), "Game should be draw due to 50 moves rule");

        String expectedFEN = "rk2qbrn/pppb1ppp/2np4/3Np3/4P3/3P1N2/PPP2PPP/RRQBB2K w - - 100 53";
        assertEquals(expectedFEN, game.exportFEN(), "wrong FEN");
    }

    @Test
    void testDisableFiftyMovesRule() {
        GameImporter importer = GameImporter.importerFor("""
                1. e4 e5 2. d3 d6 3. Be2 Nf6 4. Nf3 Qd7 5. Nc3 Kd8 6. O-O Rg8 7. Rb1 Rh8 8. Be3 Qe8 9. Rc1
                Nc6 10. Ne1 Be6 11. Qd2 Kc8 12. Ra1 Kb8 13. Rd1 Ng8 14. Ra1 Nh6 15. Nf3 Nf5 16. Nh4 Nfe7
                17. Nf3 Qc8 18. Rfb1 Nd4 19. Nd1 Nec6 20. Ne1 Be7 21. Kh1 Rd8 22. Kg1 Re8 23. Kf1 Rf8 24.
                Kg1 Rg8 25. Kh1 Qf8 26. Bf4 Bh4 27. Bf3 Bc4 28. Qe3 Ne6 29. Nc3 Ne7 30. Bg4 Ng6 31. Nf3
                Nh8 32. Ng1 Nd8 33. Qc1 Be6 34. Bd1 Bc8 35. Bd2 Be7 36. Be1 Qe8 37. Nce2 Bf8 38. Ng3 Qd7
                39. Nf1 Qe8 40. Ne3 Qe6 41. Nc4 Qe8 42. Na5 Qe6 43. Nb3 Qe8 44. Nc5 Qe7 45. Ne6 Qe8 46.
                Ng5 Qe7 47. N5h3 Qe6 48. Nf4 Qe8 49. Nfe2 Qe6 50. Nc3 Qe8 51. Nd5 Nc6 52. Nf3
                """);
        var config = new GameConfig(
                MyChessEngine.class,
                new EngineConfig.Builder().enableFiftyMovesRule(false)
                        .setTranspositionTable(tt).build()
        );
        var game = importer.importGame(config);

        var halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(99, halfMoveClock, "Wrong half move clock");

        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not be finished");

        game.makeMove(MoveDescription.fromString("c8-d7", game.getTurn()));
        halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(100, halfMoveClock, "Wrong half move clock");
        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not be finished");
    }

    /**
     * Filipowicz&ndash;Smederevac, Polanica Zdrój 1966 &mdash; the textbook fifty-move game,
     * and the counterpart to {@code ThreefoldRepetitionTest.testFindDrawMove}: the engine must
     * take a draw the rules offer it.
     *
     * <p>The last irreversible move was <b>20...h5</b>. Fifty moves of manoeuvring later the
     * position after {@code 70.Qg2} carries a half-move clock of <b>99</b>, black to move.
     *
     * <p><b>That 99 is the whole point, and it is easy to misread this test without it.</b>
     * The root short-circuit in {@code ChessEngine.calculateNextMove} fires at
     * {@code halfMoveClock >= 100}, so it does <em>not</em> trigger here. The draw has to be
     * found one ply deeper, inside {@code PositionSearch.alphaBetaSearchPre}. This therefore
     * exercises the search, not the rule check at the root — which is what makes it worth
     * having next to {@link #testFiftyMovesRule()}, where the clock has already run out.
     * The premise is asserted below so that an edit to the move list cannot silently turn
     * this into a test of something else.
     *
     * <p><b>Why no assertion on the move.</b> Black has 38 legal moves, and <b>33</b> of them
     * reach the fifty-move draw; only {@code Bxe5}, {@code Bxa4}, {@code Nxf4}, {@code b5} and
     * {@code c4} reset the clock and play on. There is no unique drawing move to pin, so the
     * assertions are on the score and the result. myChess picks {@code Nc3} and reports 0.00
     * from depth 1 upward.
     *
     * <p>The choice is not a throwaway: three of the five continuations are captures. That the
     * engine prefers 0.00 to grabbing a pawn is correct here — Stockfish rates the position
     * with the clock artificially cleared at only +0.24 for black, so the draw costs
     * essentially nothing, and with the real clock its own top three moves all evaluate to 0.
     *
     * <p>Deliberately carries no {@code Test family:} marker, for the same reason
     * {@code testFindDrawMove} does not: it guards correct behavior in a theme that has no
     * open defect, and a family counting only guards would add nothing to the tally that is
     * evidence of anything.
     */
    @Test
    void testFilipowiczSmederevac1966() throws ExecutionException, InterruptedException, TimeoutException {
        var importer = GameImporter.importerFor("""
                1. e4 e6 2. d3 Ne7 3. g3 c5 4. Bg2 Nbc6 5. Be3 b6 6. Ne2 d5 7. O-O d4 8. Bc1 g6 9. Nd2 Bg7 10. f4 f5
                11. a3 O-O 12. e5 a5 13. a4 Ba6 14. b3 Rb8 15. Nc4 Qc7 16. Kh1 Nd5 17. Bd2 Rfd8 18. Ng1 Bf8 19. Nf3 Be7
                20. h4 h5 21. Qe2 Ncb4 22. Rfc1 Bb7 23. Kh2 Bc6 24. Na3 Ra8 25. Qe1 Rdb8 26. Qg1 Qb7 27. Qf1 Kg7
                28. Qh1 Qd7 29. Ne1 Ra7 30. Nf3 Rba8 31. Ne1 Bd8 32. Nf3 Rb8 33. Ne1 Bc7 34. Nf3 Rh8 35. Ng5 Bd8
                36. Nf3 Be7 37. Qg1 Bb7 38. Nb5 Raa8 39. Na3 Ba6 40. Qf1 Rab8 41. Nc4 Bd8 42. Qd1 Ne7 43. Nd6 Bc7
                44. Qe2 Ng8 45. Ng5 Nh6 46. Bf3 Bd8 47. Nh3 Ng4+ 48. Kg1 Be7 49. Nc4 Nd5 50. Nf2 Bb7 51. Nh3 Bc6
                52. Qg2 Rhc8 53. Re1 Rc7 54. Re2 Ra7 55. Ree1 Ra6 56. Re2 Rba8 57. Ree1 R8a7 58. Na3 Ra8 59. Nc4 Nh6
                60. Na3 Nf7 61. Nf2 Rd8 62. Nc4 Rb8 63. Nh3 Bd8 64. Na3 Ra7 65. Qh1 Bc7 66. Qg2 Rd8 67. Qh1 Nh6
                68. Ng5 Qe8 69. Kh2 Rd7 70. Qg2
                """);
        var config = new GameConfig(MyChessEngine.class, engineConfig(tt));
        var game = importer.importGame(config);

        // 99, not 100: one ply below what the rule needs, so the draw has to come from the
        // search rather than from the root check in ChessEngine.
        assertEquals(99, game.getGameStatus().getHalfMoveClock(),
                "premise of this test: the clock must be one ply short of the rule, so the draw has to be "
                        + "found by the search rather than by the root check in ChessEngine");

        MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
        assertEquals(0f, move.weight(), "Weight must be 0 (draw)");
        assertEquals(GameResult.DRAW, move.result(), "game must be draw due to the fifty-move rule");
    }

    /**
     * Position before {@code 183.Nce3}, white (myChess) to move and winning — with the half-move
     * clock at <b>91</b>, i.e. nine plies from the draw.
     */
    private static final String OUTRUN_THE_CLOCK_FEN =
            "4r1k1/2br2p1/5p1p/p1p1pN1P/PpNpP1P1/1P1R1PK1/2P5/4R3 w - - 91 183";

    /** The identical board with the clock cleared — the control. Only the half-move field differs. */
    private static final String OUTRUN_THE_CLOCK_CONTROL_FEN =
            "4r1k1/2br2p1/5p1p/p1p1pN1P/PpNpP1P1/1P1R1PK1/2P5/4R3 w - - 0 183";

    /** Depth at which the fifty-move boundary is still out of reach: 91 + 8 = 99. */
    private static final int BELOW_THE_HORIZON_DEPTH = 8;

    /** Depth at which a quiet line first reaches the boundary: 91 + 9 = 100, so 10 is safely past it. */
    private static final int PAST_THE_HORIZON_DEPTH = 10;

    /** Budget for the probes below; high enough that depth, not the clock, ends the search. */
    private static final int CLOCK_PROBE_BUDGET_MS = 120_000;

    /** JUnit safety timeout for the probes; above {@link #CLOCK_PROBE_BUDGET_MS}. */
    private static final int CLOCK_PROBE_TIMEOUT_S = 150;

    /**
     * Searches {@code fen} bounded by {@code depth} rather than by the clock, <b>on a private
     * transposition table</b>.
     *
     * <p>The private table is the point, not housekeeping. The half-move clock is not part of the
     * Zobrist key, so an entry written during the clock-91 search matches the identical position
     * in the clock-0 search — and would carry the fifty-move influence across into the control,
     * collapsing exactly the difference this test measures. Sharing the class-level table here
     * would make the test pass or fail for reasons that have nothing to do with the engine.
     */
    private static MoveAndWeight searchAtDepth(String fen, int depth) throws Exception {
        try (var privateTt = TestSupport.createTestTT()) {
            var engineConfig = new EngineConfig.Builder()
                    .maxDepth(depth)
                    .millisPerMove(CLOCK_PROBE_BUDGET_MS)
                    .silent(true)
                    .setTranspositionTable(privateTt)
                    .build();
            var game = new Game(new GameConfig(MyChessEngine.class, engineConfig), Fen.importFEN(fen));

            return game.getEngine().nextMoveAsync().getResult(CLOCK_PROBE_TIMEOUT_S - 10, TimeUnit.SECONDS);
        }
    }

    /**
     * The counterpart to {@link #testFilipowiczSmederevac1966}: not "take the draw the rules offer
     * you", but <b>"outrun the draw while you are winning"</b>. Same rule, opposite side of it,
     * and the only test that shows the engine <em>pricing</em> the fifty-move rule rather than
     * obeying it.
     *
     * <p>Both searches run on the same board. The <em>only</em> difference between
     * {@link #OUTRUN_THE_CLOCK_FEN} and {@link #OUTRUN_THE_CLOCK_CONTROL_FEN} is the half-move
     * field: 91 against 0. That makes the experiment self-contained — any behavioral difference
     * can come from nothing but the fifty-move check in
     * {@code PositionSearch.alphaBetaSearchPre}.
     *
     * <p><b>The two depths are computed, not chosen.</b> From a clock of 91 a quiet line first
     * touches the rule after nine plies, because 91 + 9 = 100. So at
     * {@link #BELOW_THE_HORIZON_DEPTH} the boundary is unreachable and the two searches must be
     * <em>identical</em>; at {@link #PAST_THE_HORIZON_DEPTH} it is reachable and they must
     * differ. Quiescence cannot smuggle the boundary inside the shallow search either: it
     * follows captures only, and a capture resets the clock. Neither can null-move pruning,
     * which zeroes the clock in {@code Board.makeNullMove}.
     *
     * <p>Measured on v4.4.2: depth 8 gives {@code +3.10} for both, bit-identical down to the node
     * count (654 842). Depth 9 diverges — {@code +2.74} against {@code +3.19}, 7.36 M nodes
     * against 2.05 M. At depth 10 it is {@code +2.74} with {@code Nfe3} against {@code +3.09}
     * with {@code Nce3}: the approaching draw costs about a third of a pawn and changes which
     * knight goes to e3.
     *
     * <p><b>Why the assertion is on the direction and not on the moves.</b> That the clock-91
     * score cannot exceed the clock-0 score is structural — scoring some leaves as a draw can
     * only lower the winning side's result, never raise it — so the inequality survives changes
     * to the evaluation, while the specific knight would not.
     *
     * <p>This also guards the placement of the check, in the same way § 12.23 guards the
     * repetition check. The clock is absent from the Zobrist key, so if the fifty-move test were
     * ever moved <em>below</em> the transposition-table lookup, or if its result were stored,
     * entries written without clock awareness would answer clock-91 probes and the two columns
     * would collapse into one. The test fails in exactly that case.
     *
     * <p>Provenance: lichess game <a href="https://lichess.org/RWR1Tf6P">RWR1Tf6P</a>, myChess as
     * white, 1-0 in 215 moves. Neither side ever repeated a position three times — of 429
     * positions exactly one occurred twice — so the fifty-move rule was the only clock that ever
     * ran, and it ran twice. Black's {@code 136...Bxe4} is the first instance: at a clock of 98
     * it gave up a bishop purely to reset the counter, in a position Stockfish rates
     * {@code -0.85} for it, i.e. it paid a piece to avoid a draw it should have wanted. White's
     * {@code 184.Nd5} is the second, six plies from the boundary, and that one was sound:
     * {@code 184...Rxd5 185.Ne7+ Kf7 186.Nxd5 Rxd5 187.exd5} trades two knights for two rooks.
     *
     * <pre>{@code
     * 1. e4 c5 2. Nc3 Nc6 3. Nf3 d6 4. Bb5 e5 5. d3 Be7 6. O-O Nf6 7. Be3 O-O 8. Bxc6 bxc6
     * 9. Qd2 Bb7 10. a4 d5 11. Bg5 d4 12. Ne2 h6 13. Bxf6 Bxf6 14. Ng3 Qc7 15. Rad1 a6
     * 16. Rfe1 Rfe8 17. b3 Rad8 18. Nf5 Qb6 19. h4 Qb4 20. g4 Rd7 21. Kg2 Qa3 22. Ra1 Qb4
     * 23. Qxb4 cxb4 24. Rad1 Kh7 25. h5 c5 26. Rh1 Bd8 27. Nd2 Bc7 28. Nc4 a5 29. Rde1 Kg8
     * 30. Rc1 Kh7 31. Rcd1 Rdd8 32. Rde1 Kg8 33. Rb1 Bc6 34. Rbc1 Rd7 35. Rcd1 Bb7 36. Rb1 Kh7
     * 37. Kf3 Kg8 38. Rbg1 f6 39. Rd1 Rdd8 40. Rhf1 Bc6 41. Kg3 Bb7 42. Rfe1 Bc6 43. Rb1 Rd7
     * 44. Rbc1 Rdd8 45. Rcd1 Rd7 46. Rb1 Rf8 47. Kh3 Re8 48. Kg2 Bb7 49. Rbd1 Rf8 50. Kh2 Re8
     * 51. Kg1 Rdd8 52. Rf1 Bc8 53. Kh2 Bb7 54. Kg2 Ba6 55. Rfe1 Bc8 56. Kg3 Be6 57. Kh3 Bc8
     * 58. Kg2 Ba6 59. Rc1 Bb7 60. Red1 Bc6 61. Rf1 Rd7 62. Rcd1 Rdd8 63. Rfe1 Kf8 64. Rb1 Kg8
     * 65. Kg1 Bb7 66. Rbd1 Bc6 67. Kh1 Kf7 68. Kh2 Bb7 69. Kg2 Rd7 70. Rc1 Kg8 71. Kg3 Bc8
     * 72. Rcd1 Rdd8 73. Kf3 Be6 74. Rb1 Kf7 75. Rbc1 Kg8 76. Rcd1 Bd7 77. Ke2 Bc6 78. Kd2 Kh7
     * 79. Rg1 Kh8 80. Ke2 Kh7 81. Kf3 Kg8 82. Rd2 Rd7 83. Re1 Kh7 84. Rdd1 Kg8 85. Kg2 Rf8
     * 86. Rb1 Kf7 87. f3 Re8 88. Rbd1 Rdd8 89. Rd2 Kg8 90. Kh1 Bb7 91. Kg1 Rf8 92. Rc1 Rfe8
     * 93. Rdd1 Rd7 94. Re1 Rdd8 95. Rcd1 Bc6 96. Kh2 Ba8 97. Kg2 Bc6 98. Ra1 Kh7 99. Kh2 Kg8
     * 100. Rac1 Ba8 101. Rf1 Rd7 102. Rcd1 Bc6 103. Kh3 Rf8 104. Rfe1 Re8 105. Rd2 Rf8
     * 106. Kh2 Rfd8 107. Rdd1 Rc8 108. Kg2 Kh7 109. Rd2 Kg8 110. Rde2 Rcd8 111. Rd1 Kh7
     * 112. Kg1 Re8 113. Red2 Kg8 114. Kg2 Bb7 115. Re2 Rdd8 116. Kf2 Bc6 117. Ree1 Rd7
     * 118. Kg3 Rdd8 119. Rd2 Ba8 120. Kh3 Bc6 121. Rde2 Bb7 122. Rd1 Bc6 123. Kg2 Kf8
     * 124. Rf2 Kg8 125. Rff1 Rd7 126. Kg3 Rc8 127. Rfe1 Re8 128. Kh3 Rdd8 129. Rc1 Ba8
     * 130. Red1 Bc6 131. Kg2 Rd7 132. Kf2 Rdd8 133. Re1 Bb7 134. Rcd1 Ba8 135. Rf1 Bc6
     * 136. Kg1 Bxe4 137. dxe4 Kf7 138. Rfe1 Kg8 139. Kg2 Kh7 140. Kg3 Kg8 141. Kf2 Rd7
     * 142. Kg2 Rdd8 143. Rd3 Kh7 144. Kh2 Kg8 145. Rdd1 Kh7 146. Kh3 Kg8 147. Kg3 Kh7
     * 148. Rd3 Kg8 149. Kf2 Kh7 150. Rdd1 Kg8 151. Kg1 Kh7 152. Rd3 Kg8 153. Rc1 Kh7
     * 154. Ra1 Kh8 155. Rad1 Kh7 156. Rc1 Kh8 157. Ra1 Kg8 158. Re1 Kh8 159. Rf1 Kg8
     * 160. Kg2 Kf8 161. Re1 Kf7 162. Rd2 Kg8 163. Kg1 Kh7 164. Kh2 Kg8 165. Rd3 Kh7
     * 166. Rf1 Kg8 167. Rdd1 Kh7 168. Rfe1 Kg8 169. Rd2 Kf8 170. Kg1 Kg8 171. Kg2 Kh7
     * 172. Rd3 Kh8 173. Rf1 Kg8 174. Rfd1 Kh7 175. Rf1 Kg8 176. Rc1 Kh7 177. Rd2 Kg8
     * 178. Kf1 Kh7 179. Kf2 Kg8 180. Re1 Rd7 181. Rd3 Kh7 182. Kg3 Kg8 183. Nce3 Red8
     * 184. Nd5 Rxd5 185. Ne7+ Kf7 186. Nxd5 Rxd5 187. exd5 e4+ 188. f4 exd3 189. cxd3 Kf8
     * 190. Re6 Kf7 191. Rc6 Bd8 192. Rxc5 Ke7 193. Kf3 Kd6 194. Rc8 Be7 195. Ke4 f5+
     * 196. gxf5 Kd7 197. Rg8 Bf6 198. Ra8 Kc7 199. Rxa5 Kb6 200. Rb5+ Ka7 201. d6 Bd8
     * 202. d7 Bc7 203. Rxb4 Bd8 204. Rxd4 Kb7 205. Rc4 Be7 206. Rc8 Kb6 207. b4 Bf6
     * 208. Kd5 Bh4 209. Kd6 Kb7 210. d8=Q Bxd8 211. Rxd8 g5 212. fxg6 Ka6 213. Kc6 Ka7
     * 214. Re8 Ka6 215. Ra8# 1-0
     * }</pre>
     */
    @Test
    @Timeout(value = CLOCK_PROBE_TIMEOUT_S, unit = TimeUnit.SECONDS)
    void clockAt91_theApproachingDrawIsPricedIntoTheSearch() throws Exception {
        assertEquals(91, Fen.importFEN(OUTRUN_THE_CLOCK_FEN).getGameStatus().getHalfMoveClock(),
                "premise: the real position must carry a half-move clock of 91, nine plies short of the rule");
        assertEquals(0, Fen.importFEN(OUTRUN_THE_CLOCK_CONTROL_FEN).getGameStatus().getHalfMoveClock(),
                "premise: the control must differ from the real position in the half-move clock and nothing else");

        var realBelow = searchAtDepth(OUTRUN_THE_CLOCK_FEN, BELOW_THE_HORIZON_DEPTH);
        var controlBelow = searchAtDepth(OUTRUN_THE_CLOCK_CONTROL_FEN, BELOW_THE_HORIZON_DEPTH);

        assertEquals(controlBelow.weight(), realBelow.weight(),
                "at depth " + BELOW_THE_HORIZON_DEPTH + " the fifty-move boundary is out of reach (91 + "
                        + BELOW_THE_HORIZON_DEPTH + " = 99), so the clock must make no difference at all — "
                        + "a difference here means something other than the rule is diverging the two searches");

        var realPast = searchAtDepth(OUTRUN_THE_CLOCK_FEN, PAST_THE_HORIZON_DEPTH);
        var controlPast = searchAtDepth(OUTRUN_THE_CLOCK_CONTROL_FEN, PAST_THE_HORIZON_DEPTH);

        assertTrue(realPast.weight() < controlPast.weight(),
                "at depth " + PAST_THE_HORIZON_DEPTH + " quiet lines reach the fifty-move draw, so white's "
                        + "winning score must come out lower with the clock at 91 than with it cleared. Equal "
                        + "scores mean the search no longer prices the rule — check that the fifty-move test in "
                        + "PositionSearch still sits above the transposition-table lookup and still does not "
                        + "store its result; clock-91 " + realPast.weight() + ", clock-0 " + controlPast.weight());
    }

}
