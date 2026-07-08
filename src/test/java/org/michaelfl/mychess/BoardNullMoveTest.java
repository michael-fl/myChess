package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Board#makeNullMove()} and
 * {@link Board#revertNullMove()} — the null-move plumbing used by
 * null-move pruning in the search. A null move flips the side to move
 * without touching a piece, clears the en-passant target square (a
 * pawn can no longer be captured en passant after the opponent has
 * "passed"), leaves castling rights and piece positions untouched, and
 * remains reversible via {@code revertNullMove} so the search can
 * descend and unwind normally.
 *
 * @author Michael Fleischhauer
 */
class BoardNullMoveTest {

    private static Board freshBoard() {
        return new Game().getBoard();
    }

    private static Board boardAfter(String pgn) {
        return GameImporter.importerFor(pgn).importGame().getBoard();
    }

    // ---------- makeNullMove: single-step state changes ----------

    @Test
    void makeNullMove_switchesTurn() {
        var board = freshBoard();
        int turnBefore = board.getGameStatus().getTurn();

        board.makeNullMove();

        assertNotEquals(turnBefore, board.getGameStatus().getTurn(),
                "turn must switch after a null move");
    }

    @Test
    void makeNullMove_incrementsPlyCount() {
        var board = freshBoard();
        int plyBefore = board.getGameStatus().getPlyCount();

        board.makeNullMove();

        assertEquals(plyBefore + 1, board.getGameStatus().getPlyCount(),
                "ply count must advance by one");
    }

    @Test
    void makeNullMove_preservesPiecePositions() {
        var board = freshBoard();
        var rawBefore = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);

        board.makeNullMove();

        assertArrayEquals(rawBefore, board.getRawBoard(),
                "no piece moves during a null move");
    }

    @Test
    void makeNullMove_preservesCastlingRights() {
        var board = freshBoard();
        int castlingBefore = board.getGameStatus().getCastlingState();

        board.makeNullMove();

        assertEquals(castlingBefore, board.getGameStatus().getCastlingState(),
                "castling rights are untouched by a null move");
    }

    @Test
    void makeNullMove_clearsEnPassantField() {
        // After 1. e4, Black-to-move has an en-passant target on e3.
        var board = boardAfter("1. e4");
        assertNotEquals(0, board.getGameStatus().getEnPassantField(),
                "test setup: en-passant target should be set after 1. e4");

        board.makeNullMove();

        assertEquals(0, board.getGameStatus().getEnPassantField(),
                "en-passant target must be cleared by a null move");
    }

    @Test
    void makeNullMove_resetsHalfMoveClockToZero() {
        // Play a few knight moves so the half-move clock accumulates
        // (no pawn moves, no captures → clock advances). After the
        // null move the clock must read 0.
        //
        // This is a deliberate design decision, not the chess-rule-strict
        // behavior: preserving the counter would be rule-correct (null
        // moves are neither pawn moves nor captures), but resetting it
        // suppresses the 50-move draw detector inside the null-move
        // subtree — a null move is never part of the real game, so the
        // search should not fabricate a draw scored down that branch.
        var board = boardAfter("1. e4 e5 2. Nf3 Nc6");
        int clockBefore = board.getGameStatus().getHalfMoveClock();
        assertTrue(clockBefore > 0, "test setup: half-move clock should be non-zero");

        board.makeNullMove();

        assertEquals(0, board.getGameStatus().getHalfMoveClock(),
                "half-move clock must be reset to 0 by a null move — deliberate "
                        + "search behavior, not chess-rule-strict (see method Javadoc)");
    }

    @Test
    void makeNullMove_setsLastMoveToSentinelZero() {
        var board = freshBoard();

        board.makeNullMove();

        assertEquals(0, board.getGameStatus().getLastMove(),
                "null move has no packed-move representation; "
                        + "getLastMove must be the sentinel 0 so revertNullMove can recognize it");
    }

    @Test
    void makeNullMove_positionHash_matchesFreshRecomputation_noEnPassant() {
        var board = freshBoard();

        board.makeNullMove();

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored,
                "incremental hash update on null move (no ep) must equal a fresh recomputation");
    }

    @Test
    void makeNullMove_positionHash_matchesFreshRecomputation_withEnPassant() {
        // Exercises the ep-XOR-out path in makeNullMove.
        var board = boardAfter("1. e4");

        board.makeNullMove();

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored,
                "incremental hash update on null move (ep cleared) must equal a fresh recomputation");
    }

    // ---------- revertNullMove: round-trip and guards ----------

    @Test
    void makeThenRevertNullMove_restoresState_fromStartPosition() {
        assertNullMoveRoundTrip("");
    }

    @Test
    void makeThenRevertNullMove_restoresState_withEnPassantSet() {
        // Setup ends with 1. e4, so Black-to-move has an ep target on e3.
        assertNullMoveRoundTrip("1. e4");
    }

    @Test
    void makeThenRevertNullMove_restoresState_withCastlingRightsPartial() {
        // 2. Ke2 removes both white castling rights and leaves black's intact.
        // Extra plies also drive the half-move clock and ply count off zero.
        assertNullMoveRoundTrip("1. e4 e5 2. Ke2 Nf6");
    }

    @Test
    void revertNullMove_onFreshBoard_throws() {
        var board = freshBoard();

        var ex = assertThrows(IllegalStateException.class, board::revertNullMove);
        assertTrue(ex.getMessage().contains("No move to revert"),
                "expected 'No move to revert' guard, got: " + ex.getMessage());
    }

    @Test
    void revertNullMove_afterNormalMove_throws() {
        var board = boardAfter("1. e4");

        var ex = assertThrows(IllegalStateException.class, board::revertNullMove);
        assertTrue(ex.getMessage().contains("null move"),
                "expected 'not a null move' guard, got: " + ex.getMessage());
    }

    // ---------- extras ----------

    @Test
    void twoConsecutiveNullMoves_cancelOutOnTurnHashBit_whenNoEnPassantSet() {
        // A double null flips the turn twice → the Zobrist turn contribution
        // XORs itself out; castling / pieces / (absent) ep contribute nothing
        // new, so the incrementally-updated hash returns to the pre-null
        // value. The ply count and half-move clock advance in ways the hash
        // does not track, so those two positions are formally different but
        // Zobrist-equivalent — that's a legitimate hash "collision" and
        // this test locks it in.
        var board = freshBoard();
        long hashBefore = board.getGameStatus().getPositionHash();

        board.makeNullMove();
        board.makeNullMove();

        assertEquals(hashBefore, board.getGameStatus().getPositionHash(),
                "two consecutive null moves with no ep field must cancel out on the Zobrist turn key");
    }

    // ---------- helper ----------

    private static void assertNullMoveRoundTrip(String setupPgn) {
        var board = setupPgn.isEmpty() ? freshBoard() : boardAfter(setupPgn);

        var statusBefore = board.getGameStatus();
        long hashBefore = statusBefore.getPositionHash();
        int turnBefore = statusBefore.getTurn();
        int plyBefore = statusBefore.getPlyCount();
        int castlingBefore = statusBefore.getCastlingState();
        int halfMoveBefore = statusBefore.getHalfMoveClock();
        int enPassantBefore = statusBefore.getEnPassantField();
        int lastMoveBefore = statusBefore.getLastMove();
        var rawBefore = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);

        board.makeNullMove();
        board.revertNullMove();

        var statusAfter = board.getGameStatus();
        assertEquals(hashBefore, statusAfter.getPositionHash(), "position hash must be restored");
        assertEquals(turnBefore, statusAfter.getTurn(), "turn must be restored");
        assertEquals(plyBefore, statusAfter.getPlyCount(), "ply count must be restored");
        assertEquals(castlingBefore, statusAfter.getCastlingState(), "castling state must be restored");
        assertEquals(halfMoveBefore, statusAfter.getHalfMoveClock(), "half-move clock must be restored");
        assertEquals(enPassantBefore, statusAfter.getEnPassantField(), "en-passant field must be restored");
        assertEquals(lastMoveBefore, statusAfter.getLastMove(), "last-move field must be restored");
        assertArrayEquals(rawBefore, board.getRawBoard(), "raw board must be identical");
    }
}
