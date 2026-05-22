package org.michaelfl.mychess;

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
class IllegalPvRegressionTest {

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

    /**
     * Replay {@code gameMoves} via {@link GameImporter}, configure myChess at
     * {@code maxDepth}, capture every iteration's PV and the final PV, and
     * assert each is legal in the search-root position. Identical structure
     * across all regression cases.
     */
    private static void runPvLegalityCheck(String gameMoves, int maxDepth, String label)
            throws Exception {
        var config = new EngineConfig.Builder()
                .maxDepth(maxDepth)
                .silent(true)
                .build();
        var game = GameImporter.importerFor(gameMoves).importGame(
                new GameConfig(MyChessEngine.class, config));

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
                        + " leaves own king in check — full PV: " + formatPv(pv));
            }

            if (!pseudoLegal.contains(packed)) {
                fail(context + ": ply " + i + " move " + new Move(packed)
                        + " is not pseudo-legal in position " + Fen.exportFEN(board)
                        + " — full PV: " + formatPv(pv));
            }

            board.makeMove(packed);
            pseudoLegal = moveGen.calculateMoves(board);
            lastAppliedMove = packed;
            lastAppliedPly = i;
        }

        if (pseudoLegal.isIllegal()) {
            fail(context + ": ply " + lastAppliedPly + " move " + new Move(lastAppliedMove)
                    + " leaves own king in check — full PV: " + formatPv(pv));
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
