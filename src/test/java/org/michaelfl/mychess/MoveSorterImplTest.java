package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.engines.MoveSorterImpl;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class MoveSorterImplTest {

    /** Helper to construct a packed-int move with type {@link Move#typeNormal}. */
    private static int normalMove(int from, int to, byte capturedPiece) {
        return Move.create(from, to, capturedPiece, Move.typeNormal);
    }

    /** Fresh game status whose last move is "opponent moved Q to d5" (so its toField == d5). */
    private static GameStatus statusWithLastMoveTo(int toField) {
        int lastMove = Move.create(Board.d4, toField, Board.empty, Move.typeNormal);
        int initialCastling = 15; // all four castling rights granted
        return new GameStatus(1, GameStatus.TURN_WHITE, lastMove, 0,
                initialCastling, (byte) 0, 0L, new int[2]);
    }

    @Test
    void knownBestMoveAppearsFirst() {
        var sorter = new MoveSorterImpl();
        var board = Board.createNewGame();
        int bestMove = normalMove(Board.e2, Board.e4, Board.empty);

        sorter.reset(board.getGameStatus(), board, 0, bestMove, 0);

        // Add a couple of unrelated quiet moves so the bucket isn't empty.
        sorter.addMove(normalMove(Board.d2, Board.d4, Board.empty),
                Board.d2, Board.d4, Board.whitePawn, Board.empty);
        sorter.addMove(normalMove(Board.g1, Board.f3, Board.empty),
                Board.g1, Board.f3, Board.whiteKnight, Board.empty);
        // Add the best move
        sorter.addMove(bestMove, Board.e2, Board.e4, Board.whitePawn, Board.empty);
        sorter.addMove(normalMove(Board.a1, Board.a4, Board.empty),
                Board.a1, Board.a4, Board.whitePawn, Board.empty);

        var moves = sorter.getSortedMoves();
        assertEquals(bestMove, moves.getMoves()[0],
                "knownBestMove must appear as the very first entry");
    }

    @Test
    void recaptureOfLastMovedPieceComesBeforeOtherCaptures() {
        // Last opposite move ended on d5; a capture targeting d5 must outrank
        // an equal-delta capture targeting another square.
        var sorter = new MoveSorterImpl();
        var board = Board.createNewGame();
        var status = statusWithLastMoveTo(Board.d5);

        sorter.reset(status, board, 0, 0, 0);

        int recaptureD5 = normalMove(Board.e4, Board.d5, Board.blackQueen);
        int otherCapture = normalMove(Board.f4, Board.e5, Board.blackQueen);

        // Order added does not matter.
        sorter.addMove(otherCapture, Board.f4, Board.e5, Board.whitePawn, Board.blackQueen);
        sorter.addMove(recaptureD5, Board.e4, Board.d5, Board.whitePawn, Board.blackQueen);

        var moves = sorter.getSortedMoves().getMoves();
        assertEquals(recaptureD5, moves[0],
                "Recapture of the last-moved opposite piece must come first");
    }

    @Test
    void winningCapturesPrecedeLosingCaptures() {
        var sorter = new MoveSorterImpl();
        var board = Board.createNewGame();
        var status = statusWithLastMoveTo(Board.a1); // irrelevant for this test

        sorter.reset(status, board, 0, 0, 0);

        // Pawn captures queen (winning: +800)
        int winningCapture = normalMove(Board.e4, Board.d5, Board.blackQueen);
        // Queen captures pawn (losing: -800)
        int losingCapture = normalMove(Board.d1, Board.d7, Board.blackPawn);

        sorter.addMove(losingCapture, Board.d1, Board.d7, Board.whiteQueen, Board.blackPawn);
        sorter.addMove(winningCapture, Board.e4, Board.d5, Board.whitePawn, Board.blackQueen);

        var moves = sorter.getSortedMoves().getMoves();
        // Find the indices
        int winningIdx = -1;
        int losingIdx = -1;
        for (int i = 0; i < moves.length && moves[i] != 0; i++) {
            if (moves[i] == winningCapture) winningIdx = i;
            if (moves[i] == losingCapture)  losingIdx = i;
        }
        assertTrue(winningIdx >= 0 && losingIdx >= 0,
                "Both moves must be present in the output");
        assertTrue(winningIdx < losingIdx,
                "Winning capture (delta > 0) must appear before losing capture (delta < 0)");
    }

    @Test
    void killerMovesPlacedBetweenWinningAndOtherCaptures() {
        var killers = new KillerMoves();
        int killer = normalMove(Board.g1, Board.f3, Board.empty);
        killers.addMove(killer, 0);

        var sorter = new MoveSorterImpl(killers);
        var board = Board.createNewGame();
        sorter.reset(board.getGameStatus(), board, 0, 0, 0);

        int winningCapture = normalMove(Board.e4, Board.d5, Board.blackQueen);
        int losingCapture  = normalMove(Board.d1, Board.d7, Board.blackPawn);

        sorter.addMove(winningCapture, Board.e4, Board.d5, Board.whitePawn, Board.blackQueen);
        sorter.addMove(killer,         Board.g1, Board.f3, Board.whiteKnight, Board.empty);
        sorter.addMove(losingCapture,  Board.d1, Board.d7, Board.whiteQueen, Board.blackPawn);

        var moves = sorter.getSortedMoves().getMoves();
        int winningIdx = -1;
        int killerIdx  = -1;
        int losingIdx  = -1;
        for (int i = 0; i < moves.length && moves[i] != 0; i++) {
            if (moves[i] == winningCapture) winningIdx = i;
            if (moves[i] == killer)         killerIdx = i;
            if (moves[i] == losingCapture)  losingIdx = i;
        }
        assertTrue(winningIdx >= 0 && killerIdx >= 0 && losingIdx >= 0,
                "All three moves must be present in the output");
        assertTrue(winningIdx < killerIdx,
                "Killer must come after winning captures");
        assertTrue(killerIdx < losingIdx,
                "Killer must come before other captures");
    }

    @Test
    void kingMovesLandLast() {
        var sorter = new MoveSorterImpl();
        var board = Board.createNewGame();
        sorter.reset(board.getGameStatus(), board, 0, 0, 0);

        int kingMove = normalMove(Board.e1, Board.e2, Board.empty);
        int quietMove = normalMove(Board.d2, Board.d4, Board.empty);

        sorter.addMove(kingMove,  Board.e1, Board.e2, Board.whiteKing, Board.empty);
        sorter.addMove(quietMove, Board.d2, Board.d4, Board.whitePawn, Board.empty);

        var moves = sorter.getSortedMoves().getMoves();
        int kingIdx = -1;
        int quietIdx = -1;
        for (int i = 0; i < moves.length && moves[i] != 0; i++) {
            if (moves[i] == kingMove)  kingIdx = i;
            if (moves[i] == quietMove) quietIdx = i;
        }
        assertTrue(quietIdx < kingIdx,
                "King move must appear after quiet non-king moves");
    }

    /**
     * Regression test for the seen-flag reset bug: {@code MoveSorterImpl}
     * is reused across all search nodes (one sorter per engine instance,
     * reset() called at every node). The {@code pvMoveSeen}/{@code ttMoveSeen}
     * flags must be cleared inside reset(), otherwise a "seen" flag set
     * by the previous node's addMove() loop sticks across reset() and
     * the next node blindly adds its (possibly illegal) pv/tt move to
     * the sorted output.
     *
     * <p>Production symptom: the search reaches makeMove() with a move
     * whose from-field is empty, and Board throws IllegalStateException.
     */
    @Test
    void ttMoveSeenFlag_isResetBetweenInvocations() {
        var sorter = new MoveSorterImpl();
        var board = Board.createNewGame();

        // First invocation: ttMove = M1, and the move generator produces M1.
        // addMove() sets ttMoveSeen = true.
        int m1 = normalMove(Board.e2, Board.e4, Board.empty);
        sorter.reset(board.getGameStatus(), board, 0, 0, m1);
        sorter.addMove(m1, Board.e2, Board.e4, Board.whitePawn, Board.empty);
        sorter.getSortedMoves();   // drains the bucket; M1 is in the output

        // Second invocation: ttMove = M2, but the move generator does NOT
        // produce M2 (simulating an inconsistent TT-bestMove for this position).
        // Without the seen-flag reset, ttMoveSeen stays true from the first
        // invocation and M2 would be blindly added to the output.
        int m2 = normalMove(Board.d2, Board.d4, Board.empty);
        sorter.reset(board.getGameStatus(), board, 0, 0, m2);
        sorter.addMove(normalMove(Board.g1, Board.f3, Board.empty),
                Board.g1, Board.f3, Board.whiteKnight, Board.empty);

        var moves = sorter.getSortedMoves();
        int[] out = moves.getMoves();
        int count = moves.count();
        for (int i = 0; i < count; i++) {
            assertNotEquals(m2, out[i],
                    "ttMove that was never reported by the move generator must not appear "
                            + "in sorted output — sticky ttMoveSeen flag would otherwise leak from "
                            + "the previous reset() call");
        }
    }

    /** Symmetric regression test for {@code pvMoveSeen}: same shape as the
     *  ttMoveSeen test, but with the pv-move slot. */
    @Test
    void pvMoveSeenFlag_isResetBetweenInvocations() {
        var sorter = new MoveSorterImpl();
        var board = Board.createNewGame();

        int m1 = normalMove(Board.e2, Board.e4, Board.empty);
        sorter.reset(board.getGameStatus(), board, 0, m1, 0);
        sorter.addMove(m1, Board.e2, Board.e4, Board.whitePawn, Board.empty);
        sorter.getSortedMoves();

        int m2 = normalMove(Board.d2, Board.d4, Board.empty);
        sorter.reset(board.getGameStatus(), board, 0, m2, 0);
        sorter.addMove(normalMove(Board.g1, Board.f3, Board.empty),
                Board.g1, Board.f3, Board.whiteKnight, Board.empty);

        var moves = sorter.getSortedMoves();
        int[] out = moves.getMoves();
        int count = moves.count();
        for (int i = 0; i < count; i++) {
            assertNotEquals(m2, out[i],
                    "pvMove that was never reported by the move generator must not appear "
                            + "in sorted output — sticky pvMoveSeen flag would otherwise leak from "
                            + "the previous reset() call");
        }
    }

    @Test
    void knownBestMoveIsNotDuplicatedWhenAlsoAdded() {
        var sorter = new MoveSorterImpl();
        var board = Board.createNewGame();
        int bestMove = normalMove(Board.e2, Board.e4, Board.empty);

        sorter.reset(board.getGameStatus(), board, 0, bestMove, 0);

        sorter.addMove(bestMove, Board.e2, Board.e4, Board.whitePawn, Board.empty);
        sorter.addMove(normalMove(Board.d2, Board.d4, Board.empty),
                Board.d2, Board.d4, Board.whitePawn, Board.empty);

        var moves = sorter.getSortedMoves();
        int count = 0;
        for (int i = 0; i < moves.getMoves().length && moves.getMoves()[i] != 0; i++) {
            if (moves.getMoves()[i] == bestMove) count++;
        }
        assertEquals(1, count, "knownBestMove must appear exactly once even if also added via addMove()");
    }

    // --- Quiescence mode: SEE-based capture ordering and pruning of losing captures. ---
    // These use real boards (Fen.importFEN), because in quiescence mode the sorter
    // scores captures via a static exchange evaluation that reads the actual position.

    private static boolean contains(Moves moves, int move) {
        return indexOf(moves, move) >= 0;
    }

    private static int indexOf(Moves moves, int move) {
        for (int i = 0; i < moves.count(); i++) {
            if (moves.getMoves()[i] == move) {
                return i;
            }
        }

        return -1;
    }

    @Test
    void quiescenceModePrunesLosingCapturesThatMainSearchKeeps() {
        // bxc5 wins an undefended knight (SEE +300); Qxh7 grabs a king-defended pawn
        // (SEE -800). The main search keeps both (ordered by delta); quiescence prunes
        // the SEE-negative Qxh7.
        var board = Fen.importFEN("6k1/7p/7Q/2n5/1P6/8/8/K7 w - - 0 1");
        int winning = normalMove(Board.b4, Board.c5, Board.blackKnight);
        int losing = normalMove(Board.h6, Board.h7, Board.blackPawn);

        var mainSorter = new MoveSorterImpl(new KillerMoves());
        mainSorter.reset(board.getGameStatus(), board, 0, 0, 0);
        mainSorter.addMove(winning, Board.b4, Board.c5, Board.whitePawn, Board.blackKnight);
        mainSorter.addMove(losing, Board.h6, Board.h7, Board.whiteQueen, Board.blackPawn);

        var mainMoves = mainSorter.getSortedMoves();
        assertTrue(contains(mainMoves, winning), "main search keeps the winning capture");
        assertTrue(contains(mainMoves, losing), "main search keeps the losing capture (no SEE pruning)");

        var quiescenceSorter = MoveSorterImpl.forQuiescenceSearch();
        quiescenceSorter.reset(board.getGameStatus(), board, 0, 0, 0);
        quiescenceSorter.addMove(winning, Board.b4, Board.c5, Board.whitePawn, Board.blackKnight);
        quiescenceSorter.addMove(losing, Board.h6, Board.h7, Board.whiteQueen, Board.blackPawn);

        var quiescenceMoves = quiescenceSorter.getSortedMoves();
        assertTrue(contains(quiescenceMoves, winning), "quiescence keeps the SEE-positive capture");
        assertFalse(contains(quiescenceMoves, losing), "quiescence prunes the SEE-negative capture");
    }

    @Test
    void quiescenceModeOrdersWinningCapturesBySee() {
        // Two winning captures: bxc5 wins a knight (SEE +300), fxe5 wins a pawn (SEE +100).
        var board = Fen.importFEN("6k1/8/8/2n1p3/1P3P2/8/8/K7 w - - 0 1");
        int captureKnight = normalMove(Board.b4, Board.c5, Board.blackKnight);
        int capturePawn = normalMove(Board.f4, Board.e5, Board.blackPawn);

        var sorter = MoveSorterImpl.forQuiescenceSearch();
        sorter.reset(board.getGameStatus(), board, 0, 0, 0);
        // Add the lower-SEE capture first to prove the ordering is by value, not insertion.
        sorter.addMove(capturePawn, Board.f4, Board.e5, Board.whitePawn, Board.blackPawn);
        sorter.addMove(captureKnight, Board.b4, Board.c5, Board.whitePawn, Board.blackKnight);

        var moves = sorter.getSortedMoves();
        assertTrue(indexOf(moves, captureKnight) < indexOf(moves, capturePawn),
                "the higher-SEE capture (knight, +300) must be ordered before the lower-SEE capture (pawn, +100)");
    }

    @Test
    void quiescenceModeKeepsEvenCaptures() {
        // dxe5: a pawn takes a pawn defended by the f6 pawn -> SEE 0. The >= 0 gate keeps it.
        var board = Fen.importFEN("4k3/8/5p2/4p3/3P4/8/8/K7 w - - 0 1");
        int evenCapture = normalMove(Board.d4, Board.e5, Board.blackPawn);

        var sorter = MoveSorterImpl.forQuiescenceSearch();
        sorter.reset(board.getGameStatus(), board, 0, 0, 0);
        sorter.addMove(evenCapture, Board.d4, Board.e5, Board.whitePawn, Board.blackPawn);

        assertTrue(contains(sorter.getSortedMoves(), evenCapture),
                "an even capture (SEE == 0) must not be pruned in quiescence");
    }

    @Test
    void quiescenceModeDiscardsLosingCaptures() {
        var board = Fen.importFEN("4k3/3n3q/8/2p1P3/3N1B2/7P/7R/K7 b - - 0 1");
        int losingCapture1 = normalMove(Board.d7, Board.e5, Board.whitePawn);
        int losingCapture2 = normalMove(Board.h7, Board.h3, Board.whitePawn);
        int winningCapture = normalMove(Board.c5, Board.d4, Board.whiteKnight);

        var sorter = MoveSorterImpl.forQuiescenceSearch();
        sorter.reset(board.getGameStatus(), board, 0, 0, 0);
        sorter.addMove(losingCapture1, Board.d7, Board.e5, Board.blackKnight, Board.whitePawn);
        sorter.addMove(winningCapture, Board.c5, Board.d4, Board.blackPawn, Board.whitePawn);
        sorter.addMove(losingCapture2, Board.h7, Board.h3, Board.blackQueen, Board.whitePawn);

        var moves = sorter.getSortedMoves();
        assertFalse(contains(moves, losingCapture1),
                "an losing capture must be pruned in quiescence");
        assertFalse(contains(moves, losingCapture2),
                "an losing capture must be pruned in quiescence");
        assertTrue(contains(moves, winningCapture),
                "an winning capture must not be pruned in quiescence");
    }
}
