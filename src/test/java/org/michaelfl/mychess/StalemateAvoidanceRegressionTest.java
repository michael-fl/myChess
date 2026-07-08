package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test that pins down the practical cost of the current
 * MoveGenerator's Q+N-only under-promotion set. In the position below
 * the winning move is an under-promotion to a rook or bishop —
 * promoting to a queen produces immediate stalemate. If the
 * MoveGenerator does not offer rook or bishop promotion, the engine
 * has to pick between queen (immediate draw by stalemate) and knight
 * (forces a king-and-pawn endgame that is a draw), and the position
 * that was strictly winning becomes at best a draw.
 *
 * <p>FEN: {@code 8/k1P5/p7/P7/8/8/8/4K3 w - - 0 1}
 *
 * <pre>
 *   . . . . . . . .   8
 *   k . P . . . . .   7
 *   p . . . . . . .   6
 *   P . . . . . . .   5
 *   . . . . . . . .   4
 *   . . . . . . . .   3
 *   . . . . . . . .   2
 *   . . . . K . . .   1
 *   a b c d e f g h
 * </pre>
 *
 * <p>Analysis of the four promotion targets from c7:
 * <ul>
 *   <li>{@code c8=Q}: the queen controls a8, b8, and b7; the white
 *       pawn on a5 controls b6. Black's king on a7 has no legal
 *       square left, and it is not in check
 *       &nbsp;&rArr; <b>stalemate, immediate draw</b>.</li>
 *   <li>{@code c8=R}: the rook controls a8 and b8 along the eighth
 *       rank but does <em>not</em> control b7. Black plays
 *       {@code Kb7} and the game continues into a winning K+R+P
 *       endgame for White.</li>
 *   <li>{@code c8=B}: the bishop controls b7 diagonally but not a8
 *       or b8. Black plays {@code Ka8} or {@code Kb8}, game
 *       continues; light-square bishop supports the a-corner
 *       promotion square, winning K+B+P endgame.</li>
 *   <li>{@code c8=N+}: gives check. Black plays {@code Kxc8} (only
 *       piece attacking c8), leaving pure K+P vs K+P with the pawns
 *       blocked on the a-file &nbsp;&rArr; drawn fortress.</li>
 * </ul>
 *
 * <p>The MoveGenerator currently produces only Q and N promotions
 * (bishop and rook under-promotions are skipped as "redundant to
 * queen"). In this position that skip removes both winning options
 * from consideration; the engine can only pick between Q (stalemate,
 * caught by the stalemate-aware search) and N (which the search
 * mistakenly picks — Kxc8 gives a fortress draw). The test asserts
 * the tight property that the engine's chosen move is c7-c8R or
 * c7-c8B — the only two winning moves. Currently, fails; will pass
 * once the MoveGenerator generates all four under-promotion targets
 * (see the memory-flagged Option D plan).
 *
 * @author Michael Fleischhauer
 */
class StalemateAvoidanceRegressionTest extends EngineTestBase {

    private static final String STALEMATE_TRAP_FEN =
            "8/k1P5/p7/P7/8/8/8/4K3 w - - 0 1";

    /**
     * The two winning under-promotions from c7. Notation:
     * {@code c7-c8R} = rook, {@code c7-c8B} = bishop. Compare against
     * {@link ChessUtil#moveToString(int)} output — the format is
     * {@code <fromSquare>-<toSquare><promotionLetter>}.
     */
    private static final Set<String> WINNING_UNDERPROMOTIONS = Set.of("c7-c8R", "c7-c8B");

    @Test
    void engineFirstMove_choosesRookOrBishopUnderpromotion_theOnlyWinningMoves()
            throws InterruptedException, ExecutionException, TimeoutException {
        var board = Fen.importFEN(STALEMATE_TRAP_FEN);
        var config = new GameConfig(ENGINE, engineConfig());
        var game = new Game(config, board);
        try {
            MoveAndWeight result = game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);
            String moveStr = ChessUtil.moveToString(result.move());

            assertTrue(WINNING_UNDERPROMOTIONS.contains(moveStr),
                    "engine chose " + moveStr + " — the only winning moves in this position "
                            + "are c7-c8R and c7-c8B (queen stalemates, knight fortress-draws "
                            + "after Kxc8). If the MoveGenerator does not generate rook and "
                            + "bishop under-promotions, this move is not even in the search's "
                            + "candidate set — see the planned Option D refactor.");
        } finally {
            config.getEngineWhiteConfig().getTranspositionTable().close();
        }
    }
}
