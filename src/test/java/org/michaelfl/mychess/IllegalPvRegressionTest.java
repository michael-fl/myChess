package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the illegal-PV bug observed throughout the cutechess
 * SPRT match against Stockfish on 2026-05-21/22 (129 warnings across 27 of
 * 40 games). Each test replays the moves of one concrete game up to the
 * position in which myChess emitted an illegal PV, runs the search at the
 * same maximum depth at which the bug was observed, and asserts that every
 * PV — both per-iteration (the {@code info pv ...} stream the UCI handler
 * sends out) and the final returned principal variation — is legal when
 * played out from the search's root position.
 *
 * <p>"Legal" here means strict chess legality: the move must be in the
 * pseudo-legal set and must not leave the own king capturable on the next
 * ply. See {@link MoveGenerator}'s JavaDoc for the pseudo-legal /
 * {@link Moves#isIllegal()} protocol.
 *
 * <p>Each test corresponds to one shape of the failing PV; together they
 * pin down distinct manifestations of what is suspected to be the same
 * underlying PV-table defect in {@link org.michaelfl.mychess.engines.PositionSearch}
 * and/or {@link QuiescenceSearch}.
 *
 * @author Michael Fleischhauer
 */
@Tag("slow")
class IllegalPvRegressionTest {

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    /**
     * cutechess game 2 (Round 1, SF-1600 vs myChess), Black to move after
     * White's 32. f4. Illegal PV reported by cutechess:
     * {@code Ra2+ Bf2 Rfxf2+ Kg3 Rf3+ Kh4 Rxf4 g4g5} — ply 7's {@code g4g5}
     * does not address the check delivered on ply 6 by {@code Rxf4}.
     * Comment for the played move was {@code {-19.00/9 50s}}, so depth 9.
     */
    private static final String GAME_R1_BLACK_AFTER_F4 = """
            1. e4 Nf6 2. e5 Nd5 3. d4 d6 4. Nf3 c6 5. c4 Nc7 6. exd6 exd6 7. d5 Be7 8. Nc3 O-O
            9. Be2 Bf6 10. h3 Bxc3+ 11. bxc3 Qf6 12. Qd4 cxd5 13. cxd5 Qxd4 14. Nxd4 Nxd5
            15. Bd2 Nc6 16. Nb5 Rd8 17. O-O Be6 18. c4 Nb6 19. Bg5 f6 20. Be3 Bxc4
            21. Rfe1 Bxe2 22. Nxa7 Nxa7 23. g3 Nc6 24. Bxb6 Re8 25. a3 d5 26. Bc5 Bd3
            27. f3 Rxe1+ 28. Kh2 Rxa1 29. g4 Rf1 30. Kg2 Ne5 31. a4 Rxa4 32. f4
            """;

    /**
     * cutechess game 4 (Round 2, SF-1600 vs myChess), Black to move after
     * White's 14. Bxh7+. Illegal PV reported by cutechess:
     * {@code Kxh7 Qc2+ b8c6} — ply 2's {@code b8c6} (Nb8–c6) does not
     * address the check from {@code Qc2+} along the c2–h7 diagonal.
     * Comment for the played move was {@code {-4.00/10 37s}}, so depth 10.
     */
    private static final String GAME_R2_BLACK_AFTER_BXH7 = """
            1. e4 e6 2. d4 d5 3. Nd2 c5 4. Ngf3 Nf6 5. exd5 Nxd5 6. Nb3 cxd4 7. Nbxd4 Be7
            8. g3 O-O 9. a3 Qa5+ 10. Bd2 Qb6 11. Bd3 Bc5 12. c3 Qxb2 13. Rc1 Qxa3 14. Bxh7+
            """;

    /**
     * cutechess game 5 (Round 3, myChess vs SF-1600), White to move after
     * Black's 43...Bf8 — note: myChess already played 20. Bh6 earlier in
     * the same game, but the illegal PV was emitted during the search for
     * move 44 (the second {@code Bh6}). Illegal PV reported by cutechess:
     * {@code Bh6 Nc5 Rxc5 f8d6}. Confirmed at runtime to be a
     * pinned-piece violation: in the position reached after
     * {@code Bh6 Nc5 Rxc5}, the Black bishop on f8 is pinned against the
     * Black king on e8 by the White rook on h8 (which arrived via
     * 43. Rh8+). Moving the bishop {@code Bf8–d6} exposes the king to
     * that rook's attack along the 8th rank. Comment for the played move
     * was {@code {+6.00/9 25s}}, so depth 9.
     */
    private static final String GAME_R3_WHITE_BEFORE_44BH6 = """
            1. d4 d5 2. c4 dxc4 3. e4 Nf6 4. e5 Nd5 5. Bxc4 Nb6 6. Bb3 Nc6 7. Nf3 Bf5 8. e6 Bxe6
            9. Bxe6 fxe6 10. Nc3 Qc8 11. Be3 Qd7 12. Qb3 g6 13. O-O-O Nd5 14. Nxd5 exd5
            15. Qxb7 Rb8 16. Qa6 Nb4 17. Qxa7 Nc6 18. Qc5 e6 19. Qc2 Bd6 20. Bh6 Ke7
            21. Kb1 Rb6 22. Rhe1 Rhb8 23. Re2 Rxb2+ 24. Qxb2 Rxb2+ 25. Kxb2 Nb4 26. Ka1 Qe8
            27. Ng5 Qb5 28. Rde1 e5 29. dxe5 Bc5 30. Nf3 Qc4 31. Kb1 Ke8 32. Rb2 c6
            33. Rc1 Qe4+ 34. Ka1 Nd3 35. Rb8+ Kd7 36. Rc3 Qe2 37. Rbb3 Nb4 38. Bd2 Na6
            39. Rb7+ Ke8 40. Rxh7 Qd1+ 41. Rc1 Qe2 42. Rh6 Ba3 43. Rh8+ Bf8
            """;

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void selfCheckEvasion_round1_blackToMove() throws Exception {
        runPvLegalityCheck(GAME_R1_BLACK_AFTER_F4, 9,
                "cutechess game 2 / Round 1 — Black ignores check from Rxf4 by playing g4-g5");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void selfCheckEvasion_round2_blackToMove() throws Exception {
        runPvLegalityCheck(GAME_R2_BLACK_AFTER_BXH7, 10,
                "cutechess game 4 / Round 2 — Black ignores check from Qc2+ by playing Nb8-c6");
    }

    @Test
    @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void pinnedPieceViolation_round3_whiteToMove() throws Exception {
        runPvLegalityCheck(GAME_R3_WHITE_BEFORE_44BH6, 9,
                "cutechess game 5 / Round 3 — PV moves a pinned Black bishop f8-d6, exposing king on e8 to rook on h8");
    }

    // ---- test02 cases ----
    // Captured directly via the in-engine validatePv hook in the test02
    // cutechess run (2026-05-22). Each FEN is the search root at the moment
    // the engine emitted the illegal PV; depth is the iteration at which the
    // hook fired, taken from test02-mychess-stderr.log.
    //
    // Two failure classes show up:
    //
    //   - "not pseudo-legal" — the int-packed move at this PV slot does not
    //     match any move the MoveGenerator produces for the replayed
    //     position. Note that the from/to part of the move *would* be
    //     pseudo-legal on its own; the mismatch is in the encoded
    //     capturedPiece byte (or moveType), which only differs if the slot
    //     was written by an earlier sibling exploration whose board state
    //     differed at the target square. I.e., a stale pvTable entry that
    //     was never overwritten in the current branch.
    //
    //   - "leaves own king in check" — the move is pseudo-legal in its slot,
    //     but applying it leaves the moving side's king capturable. In every
    //     test02 case I traced, the immediately preceding PV move was a
    //     forced check (often forced mate) under which all of the moving
    //     side's responses were illegal. The search reaches
    //     PositionSearch.checkmateOrStalemate, which returns without calling
    //     copyUpPV — so the parent node's pvTable row retains whatever a
    //     previous sibling exploration had written into the same slots.
    //
    // Both classes share the same underlying defect: pvTable slots beyond
    // pvIndex(d) are not cleared between sibling iterations at depth d,
    // and a terminal (mate/stalemate) sub-tree at depth d+1 does not
    // overwrite them.

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void notPseudoLegal_h7g6_test02() throws Exception {
        runPvLegalityCheckFromFen(
                "5N1k/1qN2Qpp/p3p3/1b6/2p5/2PP4/PP3PPP/R4RK1 w - - 1 23",
                8,
                "test02 — PV ply 7 (h7-g6): int-packed move not in MoveGenerator's pseudo-legal list "
                        + "for the replayed position; from/to is plausible, mismatch is in the "
                        + "capturedPiece / moveType byte, indicating a stale pvTable slot");
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void selfCheckEvasion_d3d2_test02() throws Exception {
        runPvLegalityCheckFromFen(
                "7k/2N2Qpp/p3N3/1b6/2p5/2PP4/PP3PqP/R4RK1 w - - 0 24",
                6,
                "test02 — PV ply 5 (d3-d2): pseudo-legal in itself but leaves Black king in check. "
                        + "The PV's ply 4 (Qf7-g7+) is in fact checkmate for Black "
                        + "(Kxg7? attacked by Ne6, Kg8? attacked by Qg7, no block or capture), "
                        + "so depth-5 search hits checkmateOrStalemate without calling copyUpPV "
                        + "and the d3-d2 in slot 5 comes from a previous sibling exploration");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void notPseudoLegal_d8c7_test02() throws Exception {
        runPvLegalityCheckFromFen(
                "3k4/1R3p2/2rN4/1P4pp/8/4P3/P4rPP/4n1RK w - - 0 32",
                4,
                "test02 — PV ply 3 (d8-c7): Black king on d8 moving to empty c7 is pseudo-legal as a "
                        + "from/to pair; the int-mismatch is in the encoded capturedPiece byte. "
                        + "MoveGenerator produces d8-c7 with capturedPiece=0 (c7 empty in the replayed "
                        + "position); the engine's pvTable slot stores d8-c7 with a non-zero "
                        + "capturedPiece from a sibling exploration where c7 held a White piece");
    }

    @Test
    @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void selfCheckEvasion_f6g5_test02() throws Exception {
        runPvLegalityCheckFromFen(
                "2r2r2/p1P2p1k/p7/3R2p1/7p/3BQ2P/8/6K1 b - - 0 54",
                9,
                "test02 — PV ply 8 (f6-g5): pseudo-legal but leaves Black king on h8 attacked by Qh7. "
                        + "Same illegal end position is reached by four consecutive PVs from search "
                        + "roots at Black moves 54, 55, 56, 57 — the engine keeps re-emitting it as "
                        + "the position drifts closer to the suspected stale pvTable cell");
    }

    // ---- test14 case (myChess vs DoctorB, 2026-05-30/31) ----
    // Captured from the e5f6 illegal-move forfeit (Round 79, game UUID
    // c53ccf90 in mychess-stderr.log). The bug manifests as a runaway
    // iterative-deepening loop: each depth iteration completes in ~2 ms
    // visiting only ~21 nodes, then emits the same stale PV (e5f6) until
    // the watcher's budget+1s grace fires and the cancellation fallback
    // ships the lastIterationFirstMove (= e5f6) as bestmove — which is
    // pseudo-legal but exposes Black's king on e4 to White's rook on the
    // 5th rank after the bishop moves off e5.
    //
    // Hypothesis: the 50-moves-rule shortcut in PositionSearch.subSearch
    // (line 538) fires BEFORE the canCaptureOpposingKing check that
    // detects "previous move was a self-check". When halfMoveClock >= 100
    // is reached one ply after a self-check move, the recursive call
    // returns DRAW=0 instead of ILLEGAL_WEIGHT_POS, so the parent
    // happily accepts the self-check-leaving move. Triggered here
    // because the KRBK endgame has dragged halfMoveClock up to 99 at
    // the root, so any non-pawn, non-capture move pushes it to 100 at
    // the next ply.
    //
    // The actual game's depth-1 iteration already emits the illegal PV
    // (nodes=21, eval=+0.00, pv=e5f6 — see test14-mychess-stderr.log at
    // 00:04:15.376), so the regression test runs at depth 1.
    private static final String GAME_TEST14_BLACK_KRBK_ENDGAME_50MOVE_BUG = """
            1. c4 f5 2. h3 c6 3. d4 d5 4. e3 Nf6 5. Bd3 e5 6. dxe5 dxc4 7. Bxc4 Qxd1+
            8. Kxd1 Ne4 9. Ke1 Nd7 10. Nf3 b5 11. Bd3 Ndc5 12. Bc2 Bb7 13. Nd4 g6
            14. Rg1 O-O-O 15. Nd2 Rd5 16. N2f3 Kb8 17. Rb1 Be7 18. Bd2 Nd7 19. Bb3 Nxd2
            20. Kxd2 Rc5 21. Ne6 Nxe5 22. Nxc5 Nxf3+ 23. gxf3 Bxc5 24. Kc1 Be7 25. e4 Bh4
            26. Rf1 fxe4 27. fxe4 Rf8 28. e5 c5 29. e6 Be4 30. Ra1 c4 31. Bc2 Bc6 32. Re1 Kc7
            33. Re2 Kd6 34. Be4 Bxe4 35. Rxe4 Bg5+ 36. Kd1 Rxf2 37. Ke1 Rf3 38. Rd1+ Rd3
            39. Rxd3+ cxd3 40. h4 Bf6 41. Kd2 g5 42. hxg5 Bxg5+ 43. Kxd3 Bf6 44. Kc2 a5
            45. Kb1 h5 46. Kc2 h4 47. Re1 a4 48. a3 Bg5 49. Kb1 h3 50. Rh1 Kxe6 51. Rxh3 Bf4
            52. Rh5 Be5 53. Rh7 Kd5 54. Rh5 Ke6 55. Rg5 Kd6 56. Rf5 Ke6 57. Rh5 Kd5 58. Rf5 Ke6
            59. Rh5 Kd5 60. Kc2 Kd6 61. Rg5 Kd5 62. Kb1 Kd6 63. Ka2 Ke6 64. Rh5 Kd6 65. Rf5 Ke6
            66. Rh5 Kd6 67. b4 Kd5 68. Kb1 Kd4 69. Rh6 Kd3 70. Rc6 Bd4 71. Rc8 Be3 72. Ka2 Bd4
            73. Kb1 Be3 74. Rc7 Bf2 75. Rc2 Bb6 76. Rc6 Bd4 77. Rc7 Bf2 78. Ka2 Be3 79. Rc8 Bf2
            80. Rc5 Bg3 81. Rxb5 Bc7 82. Rb7 Be5 83. Rb5 Bc7 84. Rb7 Be5 85. Ra7 Ke4 86. Rxa4 Kd4
            87. Ra7 Kc4 88. Rb7 Bd6 89. b5 Bc5 90. Kb2 Bd4+ 91. Kc2 Bc5 92. Kd1 Bxa3 93. Kc2 Bc5
            94. Rb8 Ba7 95. Rb7 Bc5 96. Rb8 Ba7 97. Ra8 Bc5 98. Ra5 Kb4 99. Ra6 Kxb5 100. Rh6 Kc4
            101. Rh7 Bd6 102. Rh5 Be7 103. Kb1 Bf6 104. Ra5 Kd3 105. Rf5 Bd4 106. Rf7 Be5
            107. Kc1 Kd4 108. Kb1 Kd5 109. Kc2 Kd4 110. Kb1 Kd5 111. Ka2 Kd6 112. Rh7 Kd5
            113. Kb1 Ke6 114. Kc2 Kd5 115. Kb1 Ke6 116. Ra7 Bd4 117. Rc7 Be5 118. Rh7 Kf5
            119. Rh6 Ke4 120. Re6 Kd5 121. Re8 Bf6 122. Kc2 Kc4 123. Re6 Bg7 124. Re7 Bf6
            125. Re8 Bg7 126. Re7 Bf6 127. Rf7 Be5 128. Rd7 Bf4 129. Kb1 Be5 130. Re7 Kd5
            131. Re8 Bf6 132. Kc1 Be5 133. Re7 Kd6 134. Rf7 Ke6 135. Rh7 Kd5 136. Re7 Kd6
            137. Rh7 Ke6 138. Kb1 Bd4 139. Rb7 Be5 140. Ra7 Bd4 141. Rb7 Be5 142. Kc1 Bd4
            143. Kd1 Be5 144. Kc2 Bf6 145. Rh7 Kd5 146. Kb1 Be5 147. Re7 Kd4 148. Re8 Ke4
            149. Re7
            """;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void selfCheckEvasion_test14_blackKrbkEndgame50MoveBug() throws Exception {
        runPvLegalityCheck(GAME_TEST14_BLACK_KRBK_ENDGAME_50MOVE_BUG, 1,
                "test14 / Round 79 — KRBK endgame, halfMoveClock approaching 50-move limit. "
                        + "Black bishop on e5 cannot move to f6 because doing so exposes "
                        + "Black king on e4 to White rook on the 5th rank. Search emits "
                        + "e5f6 anyway because the 50-moves-rule shortcut returns DRAW from "
                        + "the recursive call before the self-check detection can fire.");
    }

    // ---- Nf3 Nc6 Ne5 case (REPL `imp [[g1-f3 b8-c6 f3-e5]]` + `dw`, 2026-08-01) ----
    // A distinct manifestation of the same PV-table defect: instead of an
    // illegal *emitted* PV, the search crashes mid-flight. At depth 14 the
    // bestKnownPath's pv move f1-d3 for an internal node (depth=9,
    // hash c54da06457b7f42e) is not producible by the MoveGenerator there, so
    // MoveSorterImpl skips it and the first move searched becomes c1-e3 — which
    // trips the "First move must be the best known move" invariant in
    // PositionSearch.alphaBetaSearchMain. The corrupt PV is visible in the
    // completed depth-13 line, where f1-d3 appears twice — a hallmark of a
    // stale pvTable slot carried over between sibling iterations.
    @Test
    @Timeout(value = 240, unit = TimeUnit.SECONDS)
    void staleBestKnownMove_nf3nc6ne5_depth14() throws Exception {
        runPvLegalityCheckFromFen(
                "r1bqkbnr/pppppppp/2n5/4N3/8/8/PPPPPPPP/RNBQKB1R b KQkq - 3 2",
                14,
                "REPL `imp [[g1-f3 b8-c6 f3-e5]]` + `dw` — at depth 14 the bestKnownPath pv move "
                        + "f1-d3 is not producible at an internal node (depth 9), so the first move "
                        + "searched is c1-e3, tripping the \"First move must be the best known move\" "
                        + "invariant; f1-d3 appears twice in the depth-13 PV (stale pvTable slot)");
    }

    /** Replay {@code gameMoves} via {@link GameImporter}. */
    private void runPvLegalityCheck(String gameMoves, int maxDepth, String label)
            throws Exception {
        var config = new EngineConfig.Builder()
                .maxDepth(maxDepth)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
        var game = GameImporter.importerFor(gameMoves).importGame(
                new GameConfig(MyChessEngine.class, config));

        runPvLegalityCheckOnGame(game, label);
    }

    /** Import a FEN directly — used for cases where the regression source is
     * an in-engine validatePv log entry rather than a game move list. */
    private void runPvLegalityCheckFromFen(String fen, int maxDepth, String label)
            throws Exception {
        var config = new EngineConfig.Builder()
                .maxDepth(maxDepth)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
        var board = Fen.importFEN(fen);
        var game = new Game(new GameConfig(MyChessEngine.class, config), board);

        runPvLegalityCheckOnGame(game, label);
    }

    /** Capture every iteration's PV and the final PV, and assert each is
     * legal in the search-root position. Used by both setup paths. */
    private static void runPvLegalityCheckOnGame(Game game, String label)
            throws Exception {
        var rootBoard = game.getBoard().copy();

        List<int[]> iterationPvs = new ArrayList<>();
        var moveAndWeight = game.getEngine()
                .nextMoveAsync(null, info -> iterationPvs.add(info.pv().clone()))
                .getResult(200, TimeUnit.SECONDS);

        assertFalse(iterationPvs.isEmpty(), label + ": search must report at least one iteration");

        for (int iter = 0; iter < iterationPvs.size(); iter++) {
            assertPvIsLegal(rootBoard.copy(), iterationPvs.get(iter),
                    label + " — iteration #" + (iter + 1) + " PV via info-listener");
        }

        assertPvIsLegal(rootBoard.copy(), moveAndWeight.path(),
                label + " — final principal variation returned from search");
    }

    /**
     * Replay {@code pv} on {@code board} (modifies it). Fails the test the
     * moment a PV move is illegal: either not in the pseudo-legal move list
     * for the current position, or pseudo-legal but leaving the own king
     * capturable (detected on the next ply via the {@link Moves#isIllegal()}
     * sentinel — see {@link MoveGenerator}'s class JavaDoc).
     */
    private static void assertPvIsLegal(Board board, int[] pv, String context) {
        var moveGen = new MoveGenerator(MoveSorter.defaultImplementation());
        Moves pseudoLegal = moveGen.calculateMoves(board);

        int lastAppliedMove = 0;
        int lastAppliedPly = -1;

        for (int i = 0; i < pv.length; i++) {
            int packed = pv[i];
            if (packed == 0) {
                break;
            }

            if (pseudoLegal.isIllegal()) {
                fail(context + ": ply " + lastAppliedPly + " move " + new Move(lastAppliedMove)
                        + " leaves own king in check — full PV: " + formatPv(pv) + "\n"
                        + board);
            }

            if (!pseudoLegal.contains(packed)) {
                fail(context + ": ply " + i + " move " + new Move(packed)
                        + " is not pseudo-legal in position " + Fen.exportFEN(board)
                        + " — full PV: " + formatPv(pv) + "\n"
                        + board);
            }

            board.makeMove(packed);
            pseudoLegal = moveGen.calculateMoves(board);
            lastAppliedMove = packed;
            lastAppliedPly = i;
        }

        if (pseudoLegal.isIllegal()) {
            fail(context + ": ply " + lastAppliedPly + " move " + new Move(lastAppliedMove)
                    + " leaves own king in check — full PV: " + formatPv(pv) + "\n"
                    + board);
        }
    }

    private static String formatPv(int[] pv) {
        var sb = new StringBuilder();

        for (int packed : pv) {
            if (packed == 0) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }

            sb.append(new Move(packed));
        }

        return sb.toString();
    }
}
