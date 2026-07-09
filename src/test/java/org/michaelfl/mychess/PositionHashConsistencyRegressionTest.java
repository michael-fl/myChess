package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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
     * Exhaustive perft-style traversal to 8 plies. At every visited node —
     * every legal 1-, 2-, 3-, ... 8-ply sequence from the standard start,
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
                    "after makeMove(" + ChessUtil.moveToString(move) + ") at depth " + depth
                    + ", captured piece=" + Move.getCapturedPiece(move) + ", move type=" + Move.getMoveType(move));

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

    // -------------------------------------------------------------------
    // Focused en-passant hash-consistency checks. Both reproduce the
    // failure surfaced by the depth-8 perft-style traversal above,
    // but in a single make / revert pair — so a broken en-passant
    // incremental hash update fires here without needing to walk to
    // depth 7 first.
    // -------------------------------------------------------------------

    @Test
    void makeAndRevertEnPassant_blackCapturesWhitePawn_preservesHashConsistency() {
        // Black to move; ep target d3; Black pawn e4 ready to capture
        // White's just-double-moved d-pawn on d4. The only en-passant
        // move at this position is e4-d3 (typeEnPassant, capturedPiece =
        // whitePawn), matching the failure signature seen in the depth-7
        // perft node.
        var board = Fen.importFEN("4k3/8/8/8/3Pp3/8/8/4K3 b - d3 0 1");

        assertEnPassantRoundTripPreservesHash(board, "e4-d3");
    }

    @Test
    void makeAndRevertEnPassant_whiteCapturesBlackPawn_preservesHashConsistency() {
        // Mirror of the black-side setup: White to move; ep target d6;
        // White pawn on e5 ready to capture Black's just-double-moved
        // d-pawn on d5. The only en-passant move here is e5-d6.
        var board = Fen.importFEN("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");

        assertEnPassantRoundTripPreservesHash(board, "e5-d6");
    }

    /**
     * Shared body of the two en-passant hash-consistency tests: assert
     * the invariant at the pre-move position, apply the named en-passant
     * move, re-assert, revert, and re-assert once more. Failure at any
     * of the three assertion points identifies which side of the
     * make / revert cycle is broken:
     * <ul>
     *   <li>Pre-move failure &rarr; the loaded FEN's hash is wrong
     *       (Fen.importFEN bug, unrelated to en-passant).</li>
     *   <li>After-make failure &rarr; the en-passant path in
     *       {@code Board.makeMove} does not correctly maintain the
     *       incremental hash — the specific case seen in the perft
     *       failure.</li>
     *   <li>After-revert failure &rarr; the en-passant path in
     *       {@code Board.revertMove} does not correctly undo the
     *       hash change.</li>
     * </ul>
     */
    private static void assertEnPassantRoundTripPreservesHash(Board board, String enPassantMoveNotation) {
        assertHashConsistent(board, "pre-move position with ep target set");

        int enPassantMove = findMoveByNotation(board, enPassantMoveNotation);
        board.makeMove(enPassantMove);
        assertHashConsistent(board, "after makeMove(" + enPassantMoveNotation + ") en-passant");

        board.revertMove();
        assertHashConsistent(board, "after revertMove of " + enPassantMoveNotation);
    }

    // -------------------------------------------------------------------
    // Deep-line probe branching over all 20 legal White opening moves
    // from the standard start. For each branch, plies 2..N follow the
    // first-legal-move-from-MoveGenerator heuristic down until no
    // legal move remains (mate / stalemate) or the ply-cap fires.
    //
    // Complementary to the shallow depth-4 breadth-first traversal:
    // that test covers every reachable position within four plies
    // (broad, shallow); this one covers 20 deep lines that reach
    // positions unreachable within four plies — deep endgames, post-
    // promotion material configurations, long chains of transitions
    // between move types.
    // -------------------------------------------------------------------

    /**
     * Safety cap for
     * {@link #allWhiteOpeningMoves_firstLegalMoveDescent_preservesHashConsistency}.
     * Without a 50-move / threefold-repetition detector each descent
     * line could in principle loop forever in insufficient-material
     * positions; 1000 plies is far beyond any real game length yet
     * well below the {@code Board.statusStack} capacity of 2000.
     */
    private static final int MAX_PLIES_IN_DESCENT = 500;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void allWhiteOpeningMoves_firstLegalMoveDescent_preservesHashConsistency() {
        var board = Board.createNewGame();
        var gen = new MoveGenerator(MoveSorter.defaultImplementation());

        assertHashConsistent(board, "root position before any branch");

        // Snapshot the root move list — the MoveGenerator's internal
        // buffer is reused by later calculateMoves calls inside the
        // descent loop, so we must copy out before iterating.
        var rootMoves = gen.calculateMoves(board);
        final int rootCount = rootMoves.count();
        final int[] whiteOpeningMoves = Arrays.copyOf(rootMoves.getMoves(), rootCount);

        for (int i = 0; i < rootCount; i++) {
            int whiteOpening = whiteOpeningMoves[i];
            if (whiteOpening == 0) {
                continue;
            }

            // Every one of White's 20 pseudo-legal opening moves is
            // also strictly legal from the start position — but keep
            // the legality guard so a future MoveGenerator that emits
            // extra pseudo-legal candidates at the root does not
            // slip through undetected.
            board.makeMove(whiteOpening);
            if (board.canCaptureOpposingKing()) {
                board.revertMove();
                continue;
            }

            final String openingStr = ChessUtil.moveToString(whiteOpening);
            assertHashConsistent(board, "after opening " + openingStr + " (ply 1)");

            // Descend via first legal move at each subsequent ply.
            int pliesMade = 1;
            while (pliesMade < MAX_PLIES_IN_DESCENT) {
                int nextMove = findFirstLegalMove(board, gen);
                if (nextMove == 0) {
                    // Line reached a terminal position — mate / stalemate.
                    break;
                }

                String moveStr = ChessUtil.moveToString(nextMove);
                assertHashConsistent(board,
                        "opening " + openingStr + ", before makeMove at ply "
                                + (pliesMade + 1) + " (" + moveStr + ")");
                board.makeMove(nextMove);
                pliesMade++;
                assertHashConsistent(board,
                        "opening " + openingStr + ", after makeMove at ply "
                                + pliesMade + " (" + moveStr + ")");
            }

            // Unwind the whole branch — including the opening move —
            // so the next iteration of the outer loop starts from the
            // clean root state.
            for (int r = 0; r < pliesMade; r++) {
                board.revertMove();
            }
            assertHashConsistent(board, "back at root after unwinding branch " + openingStr);
        }
    }

    // -------------------------------------------------------------------
    // Random-play probe from a random Chess960 starting position. Each
    // ply picks uniformly among the legal moves; the game continues
    // until no legal move remains (mate / stalemate) or the ply-cap
    // fires. Hash consistency is asserted before and after every move.
    //
    // On failure — any AssertionError or RuntimeException from anywhere
    // in the loop — the enclosing catch replaces the exception with one
    // whose message carries the Chess960 position id, the starting FEN
    // and the full move sequence in myChess long-algebraic notation, so
    // the exact game can be replayed by hand.
    // -------------------------------------------------------------------

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void randomChess960Game_preservesHashConsistency_throughoutTheGame() {
        // Non-deterministic seed by design — the value gets logged in the
        // reproduction message on failure. Full-promotion mode so the
        // random play can pick every legal move type, including bishop
        // under-promotions the production MoveGenerator would skip.
        Random random = new Random();

        for (int gameNo = 0; gameNo < 500; gameNo++) {
            int positionId = random.nextInt(Chess960StartPositions.COUNT);
            String startFen = Chess960StartPositions.fenById(positionId);
            Board board = Fen.importChess960FEN(startFen);
            MoveGenerator gen = new MoveGenerator(MoveSorter.defaultImplementation(), true);
            List<String> playedMoves = new ArrayList<>();

            try {
                assertHashConsistent(board, "root (Chess960 id=" + positionId + ")");

                int ply = 0;
                while (ply < MAX_PLIES_IN_DESCENT) {
                    int[] legalMoves = collectLegalMoves(board, gen);
                    if (legalMoves.length == 0) {
                        // Mate or stalemate — game over.
                        break;
                    }

                    int pickedMove = legalMoves[random.nextInt(legalMoves.length)];
                    String moveStr = ChessUtil.moveToString(pickedMove);

                    assertHashConsistent(board, "before makeMove at ply " + (ply + 1) + " (" + moveStr + ")");
                    board.makeMove(pickedMove);
                    ply++;
                    playedMoves.add(moveStr);
                    assertHashConsistent(board, "after makeMove at ply " + ply + " (" + moveStr + ")");
                }
            } catch (AssertionError | RuntimeException failure) {
                throw new AssertionError(reproductionMessage(positionId, startFen, playedMoves, failure), failure);
            }
        }
    }

    /**
     * Collect all strictly legal moves for the side to move at
     * {@code board} — the moves produced by {@link MoveGenerator},
     * minus any that leave the mover's own king capturable
     * (self-check). Uses transient {@link Board#makeMove(int)} /
     * {@link Board#revertMove()} cycles per candidate to test legality;
     * those cycles must themselves preserve the hash-consistency
     * invariant, and the outer per-move assertion catches any drift.
     */
    private static int[] collectLegalMoves(Board board, MoveGenerator gen) {
        var moves = gen.calculateMoves(board);
        int count = moves.count();
        int[] arr = moves.getMoves();

        int[] temp = new int[count];
        int legalCount = 0;

        for (int i = 0; i < count; i++) {
            int move = arr[i];
            if (move == 0) {
                continue;
            }

            board.makeMove(move);
            if (!board.canCaptureOpposingKing()) {
                temp[legalCount++] = move;
            }
            board.revertMove();
        }

        return Arrays.copyOf(temp, legalCount);
    }

    /**
     * Build an error message that carries enough information to replay
     * the failing random game by hand: the Chess960 position id (via
     * {@link Chess960StartPositions#fenById(int)}), the raw starting FEN
     * (Shredder form as emitted by that factory), and the full sequence
     * of played moves in myChess long-algebraic notation.
     */
    private static String reproductionMessage(int positionId, String startFen,
                                              List<String> playedMoves, Throwable failure) {
        var sb = new StringBuilder();
        sb.append("Randomized Chess960 hash-consistency test failed after ")
                .append(playedMoves.size()).append(" plies.\n\n")
                .append("Reproduction:\n")
                .append("  Chess960 position id: ").append(positionId)
                .append("   (Chess960StartPositions.fenById(").append(positionId).append("))\n")
                .append("  Start FEN:            ").append(startFen).append("\n")
                .append("  Load with:            Fen.importChess960FEN(<Start FEN>)\n")
                .append("  Moves played (").append(playedMoves.size()).append(" plies):");

        if (playedMoves.isEmpty()) {
            sb.append(" (none — failure before any move)");
        } else {
            for (int i = 0; i < playedMoves.size(); i++) {
                if (i % 2 == 0) {
                    sb.append("\n    ").append((i / 2) + 1).append(". ");
                }
                sb.append(playedMoves.get(i));
                if (i % 2 == 0) {
                    sb.append(' ');
                }
            }
        }

        sb.append("\n\nOriginal failure: ").append(failure.getClass().getSimpleName())
                .append(": ").append(failure.getMessage());

        return sb.toString();
    }

    /**
     * Return the first move in the sorted output of {@link MoveGenerator}
     * that is legal in {@code board} (does not leave the mover's own
     * king capturable), or {@code 0} when no such move exists (mate /
     * stalemate). The legality probe uses a transient
     * {@link Board#makeMove(int)} / {@link Board#revertMove()} pair per
     * candidate — those transient pairs must themselves preserve the
     * hash-consistency invariant (and if they don't, the OUTER loop's
     * assertion catches it on the next iteration).
     */
    private static int findFirstLegalMove(Board board, MoveGenerator gen) {
        var moves = gen.calculateMoves(board);
        int count = moves.count();
        int[] arr = moves.getMoves();

        for (int i = 0; i < count; i++) {
            int move = arr[i];
            if (move == 0) {
                continue;
            }

            board.makeMove(move);
            boolean legal = !board.canCaptureOpposingKing();
            board.revertMove();

            if (legal) {
                return move;
            }
        }

        return 0;
    }

    /**
     * Search the pseudo-legal move list produced by a fresh
     * {@link MoveGenerator} for the move that stringifies to
     * {@code notation} (myChess long-algebraic form,
     * {@code <from>-<to>[<promotionLetter>]}). Fails the test loudly
     * if the target move is not among the generated moves — that
     * would mean the FEN setup is wrong or the MoveGenerator has
     * lost the en-passant path itself.
     */
    private static int findMoveByNotation(Board board, String notation) {
        var gen = new MoveGenerator(MoveSorter.defaultImplementation());
        var moves = gen.calculateMoves(board);
        int count = moves.count();
        int[] arr = moves.getMoves();

        for (int i = 0; i < count; i++) {
            int move = arr[i];
            if (move != 0 && notation.equals(ChessUtil.moveToString(move))) {
                return move;
            }
        }

        throw new AssertionError("move '" + notation + "' not among the "
                + count + " pseudo-legal moves at this position — FEN setup issue "
                + "or MoveGenerator missing the en-passant branch entirely");
    }

    private static void assertHashConsistent(Board board, String context) {
        long stored = board.getGameStatus().getPositionHash();
        long recomputed = board.calculatePositionHash();

        if (stored != recomputed) {
            board.print();
        }

        assertEquals(stored, recomputed,
                "position hash inconsistency (" + context + "): "
                        + "GameStatus.getPositionHash()=0x" + Long.toHexString(stored)
                        + " vs from-scratch Board.calculatePositionHash()=0x" + Long.toHexString(recomputed));
    }
}
