package org.michaelfl.mychess.engines;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.BitOps;
import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.FenFixture;
import org.michaelfl.mychess.Move;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void blockingRule_namesTheRule() {
        var filter = white(move(Board.c3, Board.c5), move(Board.a3, Board.c3), move(Board.a1, Board.a3));

        assertEquals("return", filter.blockingRule(move(Board.c5, Board.c3)), "the rook back to c3");
        assertEquals("backrank", filter.blockingRule(move(Board.d4, Board.d1)), "a retreat onto rank 1");
        assertEquals("samepiece", filter.blockingRule(move(Board.c5, Board.e5)), "a fourth rook move in a row");
        assertNull(filter.blockingRule(move(Board.h2, Board.h3)), "an ordinary move breaks no rule");
    }

    // ---- mobility rule ------------------------------------------------------------------------

    /** The rook on a5 has 12 squares. On a3, between its own pawns a2 and b3, it has 5. */
    private static final String ROOK_INTO_A_CORNER = "4k3/8/8/R7/8/1P6/P7/4K3 w - - 0 1";

    /** The knight on a2 has 3 squares. On c1 it has 2, because d3 and e2 hold own pawns. */
    private static final String KNIGHT_LOSES_ONE = "4k3/8/8/8/8/3P4/N3P3/7K w - - 0 1";

    /** As {@link #KNIGHT_LOSES_ONE}, with an own pawn on b3. On c1 the knight has 1 square left. */
    private static final String KNIGHT_LOSES_TWO = "4k3/8/8/8/8/1P1P4/N3P3/7K w - - 0 1";

    private static final int DROP = 30;

    @Test
    void mobility_countsEmptyAndEnemySquares() {
        assertEquals(12, FarMoveFilter.mobility(FenFixture.board(ROOK_INTO_A_CORNER), Board.a5), "rook on a5");
        assertEquals(3, FarMoveFilter.mobility(FenFixture.board(KNIGHT_LOSES_ONE), Board.a2), "knight on a2");
        assertEquals(11, FarMoveFilter.mobility(FenFixture.board("4k3/8/8/R7/n7/1P6/P7/4K3 w - - 0 1"), Board.a5),
                "the enemy knight on a4 counts, the square a3 behind it does not");
    }

    @Test
    void mobilityRule_rookIntoACorner_isBlocked() {
        var filter = mobilityFilter(ROOK_INTO_A_CORNER, DROP);

        assertTrue(filter.losesMobility(move(Board.a5, Board.a3)), "12 to 5 squares must be blocked");
        assertFalse(filter.losesMobility(move(Board.a5, Board.d5)), "12 to 12 squares must pass");
    }

    @Test
    void mobilityRule_dropOfOneSquare_isAllowed() {
        var filter = mobilityFilter(KNIGHT_LOSES_ONE, DROP);

        assertFalse(filter.losesMobility(move(Board.a2, Board.c1)),
                "3 to 2 squares is 33 % but only one square. That must pass.");
    }

    @Test
    void mobilityRule_dropOfTwoSquares_isBlocked() {
        var filter = mobilityFilter(KNIGHT_LOSES_TWO, DROP);

        assertTrue(filter.losesMobility(move(Board.a2, Board.c1)), "3 to 1 squares must be blocked");
    }

    @Test
    void mobilityRule_dropAtTheLimit_isAllowed() {
        var filter = mobilityFilter(ROOK_INTO_A_CORNER, 59);

        assertFalse(filter.losesMobility(move(Board.a5, Board.a3)), "7 of 12 squares is 58 %. That is below 59 %.");
    }

    @Test
    void mobilityRule_capture_isExempt() {
        var filter = mobilityFilter("4k3/8/8/R7/8/nP6/P7/4K3 w - - 0 1", DROP);

        assertFalse(filter.losesMobility(move(Board.a5, Board.a3)), "a capture must pass");
    }

    @Test
    void mobilityRule_check_isExempt() {
        // the knight on b3 has 6 squares. On a1 it has 2.
        var withCheck = mobilityFilter("7K/8/8/8/8/1N6/2k5/8 w - - 0 1", DROP);
        var withoutCheck = mobilityFilter("7K/8/8/8/8/1N6/8/7k w - - 0 1", DROP);

        assertFalse(withCheck.losesMobility(move(Board.b3, Board.a1)), "Nb3-a1 checks the king on c2. It must pass.");
        assertTrue(withoutCheck.losesMobility(move(Board.b3, Board.a1)), "the same move without check must be blocked");
    }

    @Test
    void mobilityRule_pawnAndKing_areExempt() {
        var filter = mobilityFilter(ROOK_INTO_A_CORNER, DROP);

        assertFalse(filter.losesMobility(move(Board.a2, Board.a3)), "a pawn move must pass");
        assertFalse(filter.losesMobility(move(Board.e1, Board.d1)), "a king move must pass");
    }

    @Test
    void mobilityRule_zeroDisablesTheRule() {
        var filter = mobilityFilter(ROOK_INTO_A_CORNER, 0);

        assertFalse(filter.losesMobility(move(Board.a5, Board.a3)), "a limit of 0 must disable the rule");
    }

    @Test
    void mobilityRule_namesTheRuleAndLeavesTheBoardUnchanged() {
        var board = FenFixture.board(ROOK_INTO_A_CORNER);
        var filter = new FarMoveFilter(board, N, M, DROP);

        assertEquals("mobility", filter.blockingRule(move(Board.a5, Board.a3)), "the rook into the corner");
        assertEquals("mobility", filter.blockingRule(move(Board.a5, Board.a3)), "a second call must give the same answer");
        assertNull(filter.blockingRule(move(Board.a5, Board.d5)), "a move along rank 5 breaks no rule");
        assertEquals(ROOK_INTO_A_CORNER, board.exportFEN(), "the filter must not change the game's board");
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

    private static FarMoveFilter mobilityFilter(String fen, int dropPercent) {
        return new FarMoveFilter(FenFixture.board(fen), N, M, dropPercent);
    }
}
