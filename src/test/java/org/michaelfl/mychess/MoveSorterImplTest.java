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
                initialCastling, (byte) 0, 0L);
    }

    @Test
    void knownBestMoveAppearsFirst() {
        var sorter = new MoveSorterImpl();
        var board = Board.createNewGame();
        int bestMove = normalMove(Board.e2, Board.e4, Board.empty);

        sorter.reset(board.getGameStatus(), board, 0, bestMove);

        // Add a couple of unrelated quiet moves so the bucket isn't empty.
        sorter.addMove(normalMove(Board.d2, Board.d4, Board.empty),
                Board.d2, Board.d4, Board.whitePawn, Board.empty);
        sorter.addMove(normalMove(Board.g1, Board.f3, Board.empty),
                Board.g1, Board.f3, Board.whiteKnight, Board.empty);

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

        sorter.reset(status, board, 0, 0);

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

        sorter.reset(status, board, 0, 0);

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
        sorter.reset(board.getGameStatus(), board, 0, 0);

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
        sorter.reset(board.getGameStatus(), board, 0, 0);

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

    @Test
    void knownBestMoveIsNotDuplicatedWhenAlsoAdded() {
        var sorter = new MoveSorterImpl();
        var board = Board.createNewGame();
        int bestMove = normalMove(Board.e2, Board.e4, Board.empty);

        sorter.reset(board.getGameStatus(), board, 0, bestMove);

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
}
