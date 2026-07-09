package org.michaelfl.mychess;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Move-generator correctness tests using the perft (performance test)
 * methodology: exhaustively enumerate all strictly legal move sequences
 * to a fixed depth from six canonical positions and compare the leaf
 * count against the mathematically known values published on the
 * <a href="https://www.chessprogramming.org/Perft_Results">Chess
 * Programming Wiki</a>.
 *
 * <p>These are the gold-standard move-generator tests every chess engine
 * ships. Together the six positions cover the interactions that a linear
 * hand-crafted test suite tends to miss: high capture / castling density
 * (Kiwipete), en-passant chains in pawn endgames, promotion capture
 * combinations, pinned pieces revealing check, and rook castling paths
 * through attacked squares. If any of these counts is off by even one,
 * the move generator has a bug — either a missed legal move or a
 * spuriously generated illegal one.
 *
 * <p>Split into a default set (each of the six positions at two depths,
 * about 2 seconds total) and a slow set ({@code @Tag("slow")}, one depth
 * deeper per position, about 40 seconds total on a modern machine —
 * ~600 million total nodes traversed). The default set already catches
 * every known move-generator bug family in the six-position catalog;
 * the slow set adds ~40x more nodes per position for extra confidence
 * after larger refactors.
 *
 * @author Michael Fleischhauer
 */
class PerftTest {

    /**
     * Per-invocation timeout for slow-set cases, in minutes. Well above
     * the ~40-second wall-clock total observed on a modern machine;
     * only kicks in on much slower CI hardware or if the move generator
     * accidentally goes exponential.
     */
    private static final int SLOW_CASE_TIMEOUT_MIN = 10;

    /** CPW position 1 — the standard chess initial position. */
    private static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** CPW position 2 — "Kiwipete" (Peter McKenzie). Dense middle-game with
     *  captures, checks, castling in both directions, en-passant chances. */
    private static final String KIWIPETE_FEN =
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";

    /** CPW position 3 — pawn endgame with en-passant, race positions. */
    private static final String ENDGAME_FEN =
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";

    /** CPW position 4 — promotions on capture, pinned pieces, exposed checks. */
    private static final String PROMOTIONS_FEN =
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";

    /** CPW position 5 (Steven Edwards) — mid-game with promotion and pinned pieces. */
    private static final String CPW_5_FEN =
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8";

    /** CPW position 6 — mirrored middle-game (Talkchess). No castling rights,
     *  many pieces active, checks from both sides. */
    private static final String CPW_6_FEN =
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10";

    /** Default-set perft cases — six positions at two depths each, a few seconds total. */
    static Stream<Arguments> perftCases() {
        return Stream.of(
                Arguments.of("start / depth 4",      START_FEN,      4,      197_281L),
                Arguments.of("start / depth 5",      START_FEN,      5,    4_865_609L),
                Arguments.of("kiwipete / depth 3",   KIWIPETE_FEN,   3,       97_862L),
                Arguments.of("kiwipete / depth 4",   KIWIPETE_FEN,   4,    4_085_603L),
                Arguments.of("endgame / depth 4",    ENDGAME_FEN,    4,       43_238L),
                Arguments.of("endgame / depth 5",    ENDGAME_FEN,    5,      674_624L),
                Arguments.of("promotions / depth 3", PROMOTIONS_FEN, 3,        9_467L),
                Arguments.of("promotions / depth 4", PROMOTIONS_FEN, 4,      422_333L),
                Arguments.of("cpw-5 / depth 3",      CPW_5_FEN,      3,       62_379L),
                Arguments.of("cpw-5 / depth 4",      CPW_5_FEN,      4,    2_103_487L),
                Arguments.of("cpw-6 / depth 3",      CPW_6_FEN,      3,       89_890L),
                Arguments.of("cpw-6 / depth 4",      CPW_6_FEN,      4,    3_894_594L)
        );
    }

    /**
     * Slow-set perft cases — one depth deeper per position, node totals
     * ~40x larger than the default set (roughly 600 million total nodes
     * traversed). All six cases together run in ~40 seconds on a modern
     * machine; the individual timeout of {@value #SLOW_CASE_TIMEOUT_MIN}
     * minutes per case is a safety net for slow CI hardware, not a
     * realistic expectation.
     */
    static Stream<Arguments> slowPerftCases() {
        return Stream.of(
                Arguments.of("start / depth 6",      START_FEN,      6,   119_060_324L),
                Arguments.of("kiwipete / depth 5",   KIWIPETE_FEN,   5,   193_690_690L),
                Arguments.of("endgame / depth 6",    ENDGAME_FEN,    6,    11_030_083L),
                Arguments.of("promotions / depth 5", PROMOTIONS_FEN, 5,    15_833_292L),
                Arguments.of("cpw-5 / depth 5",      CPW_5_FEN,      5,    89_941_194L),
                Arguments.of("cpw-6 / depth 5",      CPW_6_FEN,      5,   164_075_551L)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("perftCases")
    void perft_matchesCanonicalCount(String label, String fen, int depth, long expected) {
        assertEquals(expected, perft(Fen.importFEN(fen), depth),
                label + " — leaf count must match the CPW reference value");
    }

    @Tag("slow")
    @ParameterizedTest(name = "{0}")
    @MethodSource("slowPerftCases")
    @Timeout(value = SLOW_CASE_TIMEOUT_MIN, unit = TimeUnit.MINUTES)
    void perftDeep_matchesCanonicalCount(String label, String fen, int depth, long expected) {
        assertEquals(expected, perft(Fen.importFEN(fen), depth),
                label + " — leaf count must match the CPW reference value");
    }

    /**
     * Count all strictly legal move sequences of length {@code depth}
     * reachable from {@code board}. Uses a fresh {@link MoveGenerator}
     * per call, so no state leaks between test invocations.
     *
     * <p>Constructed in the full under-promotion mode
     * ({@code allPromotions = true}) so that all four promotion targets
     * are generated — the CPW reference values count every strictly
     * legal move, including bishop promotions that the production
     * MoveGenerator intentionally skips.
     */
    private static long perft(Board board, int depth) {
        var gen = new MoveGenerator(MoveSorter.defaultImplementation(), true);
        return perftRecursive(board, gen, depth, 0);
    }

    private static long perftRecursive(Board board, MoveGenerator gen, int remaining, int callDepth) {
        if (remaining == 0) {
            return 1;
        }

        var moves = gen.calculateMoves(board, callDepth);
        final int count = moves.count();
        // Snapshot the move buffer — the recursive call reuses the
        // MoveGenerator and may overwrite the shared internal array.
        final int[] snapshot = Arrays.copyOf(moves.getMoves(), count);

        long nodes = 0;
        for (int i = 0; i < count; i++) {
            final int move = snapshot[i];
            if (move == 0) {
                continue;
            }

            board.makeMove(move);
            if (!board.canCaptureOpposingKing()) {
                nodes += perftRecursive(board, gen, remaining - 1, callDepth + 1);
            }
            board.revertMove();
        }

        return nodes;
    }
}
