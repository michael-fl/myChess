package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for the position-hash-consistency invariant that the
 * temporary debug check at the tail of
 * {@code PositionSearch.alphaBetaSearchPre} enforces: after every
 * make / revert cycle in the search, the incrementally-tracked
 * Zobrist hash on the top-of-stack {@link GameStatus} must equal a
 * from-scratch recomputation via {@link Board#calculatePositionHash()}.
 *
 * <p>The debug check runs per-node in a live search — too expensive to
 * leave in production, but a strong indicator that something in the
 * make / revert chain is drifting when it fires. This test approximates
 * the same coverage cheaply: it does a perft-style exhaustive
 * traversal from the standard starting position up to a shallow depth,
 * asserting the invariant at every visited node — both after each
 * {@link Board#makeMove(int)} and after the matching
 * {@link Board#revertMove()}. If any specific move type at any
 * specific position has a broken incremental hash update, this test
 * fires with a message that identifies the offending move.
 *
 * <p>Coverage the perft approach does <em>not</em> cover:
 * <ul>
 *   <li>Bugs that only manifest through interaction with TT lookups,
 *       killer moves, or iterative-deepening restarts — the traversal
 *       here talks directly to {@link MoveGenerator} and does not run
 *       the alpha-beta search.</li>
 *   <li>Bugs specific to {@link Board#makeNullMove()} /
 *       {@link Board#revertNullMove()} — perft only iterates real
 *       legal moves. Null-move hash consistency is covered separately
 *       by {@code BoardNullMoveTest}.</li>
 * </ul>
 *
 * <p>Not marked slow — perft-4 from the standard start is ~197k legal
 * nodes plus ~150k pseudo-legal-but-illegal that we visit for the
 * make / revert check anyway; the whole traversal runs in a few
 * seconds.
 *
 * @author Michael Fleischhauer
 */
class PositionHashConsistencyRegressionTest {

    @Test
    void startPosition_incrementalHashEqualsRecomputation() {
        var board = Board.createNewGame();

        assertHashConsistent(board, "initial position (no moves played)");
    }

    /**
     * Exhaustive perft-style traversal to 4 plies. At every visited node —
     * every legal 1-, 2-, 3-, and 4-ply sequence from the standard start,
     * plus every pseudo-legal make / revert cycle even for moves that
     * self-check the mover — the incremental hash on the top-of-stack
     * {@link GameStatus} must equal {@link Board#calculatePositionHash()}.
     *
     * <p>If it doesn't, the failure message identifies the exact move
     * (in the myChess long-algebraic form) and the depth at which the
     * inconsistency was observed — useful signal for bisecting which
     * move type's incremental update is broken.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void makeAndRevertMove_preservesHashConsistency_acrossAllLegalMovesToDepth4() {
        var board = Board.createNewGame();
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());

        assertHashConsistent(board, "before traversal (root)");
        traverse(board, moveGenerator, 0, 4);
        assertHashConsistent(board, "back at root after full traversal");
    }

    private static void traverse(Board board, MoveGenerator gen, int depth, int maxDepth) {
        if (depth >= maxDepth) {
            return;
        }

        var moves = gen.calculateMoves(board, depth, 0, 0);
        if (moves.isIllegal()) {
            return;
        }

        // Snapshot the move array — the shared internal buffer may be
        // reused by the recursive call.
        final int count = moves.count();
        final int[] snapshot = Arrays.copyOf(moves.getMoves(), count);

        for (int i = 0; i < count; i++) {
            final int move = snapshot[i];
            if (move == 0) {
                continue;
            }

            board.makeMove(move);
            assertHashConsistent(board,
                    "after makeMove(" + ChessUtil.moveToString(move) + ") at depth " + depth);

            // Only recurse into legal positions (moves that don't leave
            // the mover's own king capturable). Illegal-self-check
            // positions still have their make / revert pair verified,
            // but we don't descend into them.
            if (!board.canCaptureOpposingKing()) {
                traverse(board, gen, depth + 1, maxDepth);
            }

            board.revertMove();
            assertHashConsistent(board,
                    "after revertMove of " + ChessUtil.moveToString(move) + " at depth " + depth);
        }
    }

    private static void assertHashConsistent(Board board, String context) {
        long stored = board.getGameStatus().getPositionHash();
        long recomputed = board.calculatePositionHash();

        assertEquals(stored, recomputed,
                "position hash inconsistency (" + context + "): "
                        + "GameStatus.getPositionHash()=0x" + Long.toHexString(stored)
                        + " vs from-scratch Board.calculatePositionHash()=0x" + Long.toHexString(recomputed));
    }
}
