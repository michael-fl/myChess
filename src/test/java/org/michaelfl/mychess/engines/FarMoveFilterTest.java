package org.michaelfl.mychess.engines;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.BitOps;
import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.Move;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FarMoveFilter}. The own moves are handed in newest first.
 *
 * @author Michael Fleischhauer
 */
class FarMoveFilterTest {

    private static final int N = 3;
    private static final int M = 3;

    // ---- return rule --------------------------------------------------------------------------

    @Test
    void returnRule_directReturn_isBlocked() {
        // knight f3-g5, now g5-f3 back
        var filter = white(move(Board.f3, Board.g5));

        assertTrue(filter.returnsToRecentSquare(move(Board.g5, Board.f3)), "a direct return must be blocked");
    }

    @Test
    void returnRule_threeMoveRoundTrip_isBlocked() {
        // knight f6-e4-g5-e4, now e4-f6. It left f6 three moves ago.
        var filter = black(move(Board.g5, Board.e4), move(Board.e4, Board.g5), move(Board.f6, Board.e4));

        assertTrue(filter.returnsToRecentSquare(move(Board.e4, Board.f6)), "a return after three moves must be blocked at N = 3");
    }

    @Test
    void returnRule_kingTriangle_isBlocked() {
        // king triangle g1-h1-h2, now h2-g1
        var filter = white(move(Board.h1, Board.h2), move(Board.g1, Board.h1));

        assertTrue(filter.returnsToRecentSquare(move(Board.h2, Board.g1)), "a triangle back to the start square must be blocked");
    }

    @Test
    void returnRule_otherPiecesInBetween_doNotCount() {
        // rook d1-e1, then two pawn moves, now e1-d1
        var filter = white(move(Board.a2, Board.a3), move(Board.h2, Board.h3), move(Board.d1, Board.e1));

        assertTrue(filter.returnsToRecentSquare(move(Board.e1, Board.d1)), "moves of other pieces must not hide a return");
    }

    @Test
    void returnRule_beyondN_isAllowed() {
        // the knight left f3 four moves ago. That is beyond N = 3.
        var filter = white(move(Board.e5, Board.d3), move(Board.c4, Board.e5), move(Board.d2, Board.c4),
                move(Board.f3, Board.d2));

        assertFalse(filter.returnsToRecentSquare(move(Board.d3, Board.f3)), "a return beyond N piece moves must be allowed");
    }

    @Test
    void returnRule_beyondTheWindowOfTenOwnMoves_isAllowed() {
        // knight f3-g5 eleven own moves ago, ten other moves since
        int[] moves = new int[11];

        for (int i = 0; i < 10; i++) {
            moves[i] = move(Board.a1, Board.a2);   // some other piece, never on g5
        }

        moves[10] = move(Board.f3, Board.g5);
        var filter = new FarMoveFilter(moves, true, N, M);

        assertFalse(filter.returnsToRecentSquare(move(Board.g5, Board.f3)),
                "a return older than " + FarMoveFilter.RETURN_WINDOW + " own moves must be allowed");
    }

    @Test
    void returnRule_windowGrowsWithN() {
        // same history, but N = 12 > X = 10. The window grows to 12.
        int[] moves = new int[11];

        for (int i = 0; i < 10; i++) {
            moves[i] = move(Board.a1, Board.a2);
        }

        moves[10] = move(Board.f3, Board.g5);
        var filter = new FarMoveFilter(moves, true, 12, M);

        assertTrue(filter.returnsToRecentSquare(move(Board.g5, Board.f3)), "with N > X the window must be N own moves");
    }

    @Test
    void returnRule_newSquare_isAllowed() {
        var filter = white(move(Board.f3, Board.g5));

        assertFalse(filter.returnsToRecentSquare(move(Board.g5, Board.e6)), "a move to a new square must be allowed");
    }

    // ---- back-rank rule -----------------------------------------------------------------------

    @Test
    void backRank_retreatOntoOwnBackRank_isBlockedForEveryPiece() {
        var whiteFilter = white();
        var blackFilter = black();

        assertTrue(whiteFilter.retreatsToBackRank(move(Board.c3, Board.b1)), "white retreat to rank 1");
        assertTrue(whiteFilter.retreatsToBackRank(move(Board.d4, Board.d1)), "white rook or queen retreat to rank 1");
        assertTrue(blackFilter.retreatsToBackRank(move(Board.f6, Board.g8)), "black retreat to rank 8");
    }

    @Test
    void backRank_sidewaysAlongTheBackRank_isAllowed() {
        assertFalse(white().retreatsToBackRank(move(Board.d1, Board.e1)), "sideways along rank 1 must be allowed");
    }

    @Test
    void backRank_castling_isAllowed() {
        int castling = BitOps.createWord((byte) Board.e1, (byte) Board.g1, (byte) 0, Move.typeCastlingKingSide);

        assertFalse(white().retreatsToBackRank(castling), "castling is not a retreat");
    }

    @Test
    void backRank_opponentsBackRank_isNotOwn() {
        assertFalse(white().retreatsToBackRank(move(Board.d7, Board.d8)), "a white move onto rank 8 is forward");
        assertFalse(black().retreatsToBackRank(move(Board.d2, Board.d1)), "a black move onto rank 1 is forward");
    }

    // ---- same-piece rule ----------------------------------------------------------------------

    @Test
    void samePiece_fourthMoveInARow_isBlocked() {
        // rook a1-a3-c3-c5, now c5-e5
        var filter = white(move(Board.c3, Board.c5), move(Board.a3, Board.c3), move(Board.a1, Board.a3));

        assertTrue(filter.movesSamePieceAgain(move(Board.c5, Board.e5)), "a fourth move of the same piece in a row must be blocked at M = 3");
    }

    @Test
    void samePiece_interruptedByAnotherPiece_isAllowed() {
        var filter = white(move(Board.c3, Board.c5), move(Board.h2, Board.h3), move(Board.a1, Board.a3));

        assertFalse(filter.movesSamePieceAgain(move(Board.c5, Board.e5)), "another piece in between resets the count");
    }

    @Test
    void samePiece_zeroDisablesTheRule() {
        var filter = new FarMoveFilter(new int[] {move(Board.c3, Board.c5), move(Board.a3, Board.c3), move(Board.a1, Board.a3)},
                true, N, 0);

        assertFalse(filter.movesSamePieceAgain(move(Board.c5, Board.e5)), "M = 0 must disable the rule");
    }

    // ---- all rules together -------------------------------------------------------------------

    @Test
    void isBlocked_combinesTheRules() {
        var filter = white(move(Board.f3, Board.g5));

        assertTrue(filter.isBlocked(move(Board.g5, Board.f3)), "return");
        assertTrue(filter.isBlocked(move(Board.c3, Board.b1)), "back rank");
        assertFalse(filter.isBlocked(move(Board.g5, Board.e6)), "an ordinary move must pass");
    }

    // ---- own moves from the game ---------------------------------------------------------------

    @Test
    void ownMoves_areTheSideToMovesMovesNewestFirst() {
        var board = Board.createNewGame();
        board.makeMove(move(Board.e2, Board.e4));
        board.makeMove(move(Board.e7, Board.e5));
        board.makeMove(move(Board.g1, Board.f3));
        board.makeMove(move(Board.b8, Board.c6));

        int[] own = FarMoveFilter.ownMoves(board.getGameStatusStackCopy());

        assertEquals(2, own.length, "white to move has made two moves");
        assertEquals(Board.f3, Move.getToField(own[0]), "newest first: g1-f3");
        assertEquals(Board.e4, Move.getToField(own[1]), "then e2-e4");
    }

    @Test
    void gameConstructor_takesTheMovesAndTheSideToMoveFromTheStatusStack() {
        var board = Board.createNewGame();
        board.makeMove(move(Board.g1, Board.f3));
        board.makeMove(move(Board.g8, Board.f6));
        board.makeMove(move(Board.f3, Board.g5));
        board.makeMove(move(Board.f6, Board.g4));

        var filter = new FarMoveFilter(board.getGameStatusStackCopy(), N, M);

        assertTrue(filter.returnsToRecentSquare(move(Board.g5, Board.f3)), "white's knight returning to f3 must be blocked");
        assertTrue(filter.retreatsToBackRank(move(Board.g5, Board.e1)), "white to move: rank 1 is the own back rank");
        assertFalse(filter.retreatsToBackRank(move(Board.d7, Board.d8)), "white to move: rank 8 is not the own back rank");
    }

    private static int move(int from, int to) {
        return BitOps.createWord((byte) from, (byte) to, (byte) 0, Move.typeNormal);
    }

    private static FarMoveFilter white(int... ownMovesNewestFirst) {
        return new FarMoveFilter(ownMovesNewestFirst, true, N, M);
    }

    private static FarMoveFilter black(int... ownMovesNewestFirst) {
        return new FarMoveFilter(ownMovesNewestFirst, false, N, M);
    }
}
