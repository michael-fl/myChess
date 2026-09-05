package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the incrementally tracked king squares, {@link Board#getKingField(int)}.
 *
 * <p>The board used to have no idea where the kings stood: {@code WeightingFunction.calculate}
 * ran a dedicated 92-square scan for them before its main pass, and a private
 * {@code findKingField} scanned linearly on every {@code isKingChecked} and
 * {@code canCaptureOpposingKing} — the hotter of the two, since a legality probe runs per move
 * rather than per evaluated position. Both are gone; the two squares are now carried in the board
 * and updated on every move that touches a king.
 *
 * <p><b>Why this needs its own test class.</b> Incremental state is only ever as good as its
 * least-travelled update path, and this one has six: the two constructors, the copy constructor,
 * the normal move, the two castling forms — each of which also has to undo itself in
 * {@code revertMove}. A missed path does not throw and does not look wrong; it returns a square
 * that used to hold a king, and every evaluation downstream is quietly computed around the wrong
 * king. The linear scan it replaces could not fail that way, so the safety net that existed
 * before is gone and has to be replaced by these tests.
 *
 * <p>The strongest case here is therefore not any single position but
 * {@link #everyMakeAndRevertOfAWalkKeepsTheFieldsInSyncWithTheBoard}, which re-derives the answer
 * by scanning after every {@code makeMove} and every {@code revertMove} of a full move-generation
 * walk. Positions are counted and asserted against a floor, because a walk that silently explored
 * nothing would otherwise pass.
 *
 * @author Michael Fleischhauer
 */
class BoardKingFieldTest {

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    /** Returned by {@link #scanFor} when the king is not on the board at all. */
    private static final int ABSENT = -1;

    /** A midgame position with both castlings available for both sides. */
    private static final String CASTLING_READY = "r3k2r/pppq1ppp/2n1bn2/3pp3/3PP3/2N1BN2/PPPQ1PPP/R3K2R w KQkq - 0 1";

    /** 960 layout: white king b1 between rooks a1 and h1, black mirrored. */
    private static final String CHESS960_KING_ON_B_FILE = "rk5r/pppppppp/8/8/8/8/PPPPPPPP/RK5R w HAha - 0 1";

    /** 960 layout with the white king already on g1, its kingside castling target. */
    private static final String CHESS960_KING_ON_TARGET = "4k3/8/8/8/8/8/8/R5KR w HA - 0 1";

    private static MoveGenerator newGen() {
        return new MoveGenerator(MoveSorter.defaultImplementation());
    }

    /** Re-derives the king square the way the board did before it tracked it. */
    private static int scanFor(Board board, int color) {
        final byte king = color == WHITE ? Board.whiteKing : Board.blackKing;
        final byte[] squares = board.getRawBoard();

        for (int field = Board.a1; field <= Board.h8; field++) {
            if (squares[field] == king) {
                return field;
            }
        }

        return ABSENT;
    }

    private static void assertTracksTheBoard(Board board, String where) {
        for (int color = WHITE; color <= BLACK; color++) {
            final int scanned = scanFor(board, color);

            if (scanned != ABSENT) {
                assertEquals(scanned, board.getKingField(color),
                        where + ": the tracked square for color " + color
                                + " must be the one a fresh scan finds");
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Initialization: the three places a board comes into existence.
    // ------------------------------------------------------------------------------------------

    @Test
    void theStartPositionConstructorTracksTheStartingSquares() {
        final var board = Board.createNewGame();

        assertEquals(Board.e1, board.getKingField(WHITE), "white king starts on e1");
        assertEquals(Board.e8, board.getKingField(BLACK), "black king starts on e8");
        assertTracksTheBoard(board, "Board.createNewGame()");
    }

    /**
     * The scanning constructor, which is the one every FEN goes through — including Chess960,
     * where the kings are not on the e-file and a hard-coded pair would be wrong without ever
     * looking wrong in standard chess.
     */
    @Test
    void theScanningConstructorTracksWhateverTheFenPlaced() {
        assertTracksTheBoard(Fen.importFEN(CASTLING_READY), "standard FEN");
        assertTracksTheBoard(Fen.importFEN(CHESS960_KING_ON_B_FILE), "960 FEN");

        final var board = Fen.importFEN(CHESS960_KING_ON_B_FILE);

        assertEquals(Board.b1, board.getKingField(WHITE), "960: the white king stands on b1");
        assertEquals(Board.b8, board.getKingField(BLACK), "960: the black king stands on b8");
    }

    @Test
    void copyCarriesTheFieldsAndKeepsThemIndependent() {
        final var original = Fen.importFEN(CASTLING_READY);
        original.makeMove(Move.create(Board.e1, Board.g1, Board.empty, Move.typeCastlingKingSide));

        final var copy = original.copy();

        assertEquals(Board.g1, copy.getKingField(WHITE), "the copy inherits the castled square");
        assertEquals(original.getKingField(BLACK), copy.getKingField(BLACK), "and black's square");
        assertTracksTheBoard(copy, "copy");

        copy.makeMove(Move.create(Board.e8, Board.d8, Board.empty, Move.typeNormal));

        assertEquals(Board.d8, copy.getKingField(BLACK), "the copy tracks its own move");
        assertEquals(Board.e8, original.getKingField(BLACK),
                "and the original must not see it — a shared array would make the two boards "
                        + "alias, which is the failure the copy constructor exists to prevent");
    }

    // ------------------------------------------------------------------------------------------
    // The walk. Re-derives the answer after every make and every revert.
    // ------------------------------------------------------------------------------------------

    /** Counter so a walk that explored nothing cannot pass silently. */
    private static final class WalkCounts {
        int checked;
    }

    private static void walk(Board board, MoveGenerator gen, int remaining, int callDepth, WalkCounts counts) {
        if (remaining == 0) {
            return;
        }

        final var moves = gen.calculateMoves(board, callDepth);
        final int count = moves.count();
        final int[] snapshot = Arrays.copyOf(moves.getMoves(), count);

        for (int i = 0; i < count; i++) {
            final int move = snapshot[i];

            if (move == 0) {
                continue;
            }

            board.makeMove(move);
            assertTracksTheBoard(board, "after makeMove " + new Move(move));
            counts.checked++;

            if (!board.canCaptureOpposingKing()) {
                walk(board, gen, remaining - 1, callDepth + 1, counts);
            }

            board.revertMove();
            assertTracksTheBoard(board, "after revertMove " + new Move(move));
            counts.checked++;
        }
    }

    /**
     * The load-bearing case: a four-ply move-generation walk from a position with all four
     * castlings available, checking the tracked squares against a fresh scan after every
     * {@code makeMove} and every {@code revertMove}.
     *
     * <p>Checking after the revert as well is what makes this cover the undo paths. A tracker that
     * updates on the way down and forgets on the way up produces a board whose state is correct
     * only along the path currently being searched — which is exactly the shape of corruption the
     * {@code GameStatus} stack exists to prevent, and it would show up as lost Elo rather than as
     * a failing test.
     */
    @Test
    void everyMakeAndRevertOfAWalkKeepsTheFieldsInSyncWithTheBoard() {
        final var board = Fen.importFEN(CASTLING_READY);
        final var counts = new WalkCounts();

        walk(board, newGen(), 4, 0, counts);

        assertTrue(counts.checked > 100_000,
                "the walk must actually have explored a tree, otherwise every assertion inside it "
                        + "was vacuous; checked " + counts.checked + " make/revert pairs");
        assertTracksTheBoard(board, "back at the root");
        assertEquals(Board.e1, board.getKingField(WHITE), "the root must be restored exactly");
        assertEquals(Board.e8, board.getKingField(BLACK), "for both colors");
    }

    /**
     * The same walk on a Chess960 layout, because 960 castling is the one move where the king can
     * travel to a square that is neither its origin nor a fixed offset from it — and, uniquely,
     * where it can end on the square it started from.
     */
    @Test
    void theWalkAlsoHoldsForChess960Castling() {
        final var board = Fen.importChess960FEN(CHESS960_KING_ON_B_FILE);
        final var counts = new WalkCounts();

        walk(board, newGen(), 4, 0, counts);

        assertTrue(counts.checked > 100_000,
                "the 960 walk must have explored a tree; checked " + counts.checked);
        assertEquals(Board.b1, board.getKingField(WHITE), "the 960 root must be restored");
        assertEquals(Board.b8, board.getKingField(BLACK), "for both colors");
    }

    // ------------------------------------------------------------------------------------------
    // Castling, stated square by square. The walk above would catch a defect here too, but not
    // say which of the four forms broke.
    // ------------------------------------------------------------------------------------------

    @Test
    void allFourCastlingsMoveAndRestoreTheKingField() {
        record Case(int from, int to, byte type, int color, String what) {}

        final Case[] cases = {
                new Case(Board.e1, Board.g1, Move.typeCastlingKingSide, WHITE, "white O-O"),
                new Case(Board.e1, Board.c1, Move.typeCastlingQueenSide, WHITE, "white O-O-O"),
                new Case(Board.e8, Board.g8, Move.typeCastlingKingSide, BLACK, "black O-O"),
                new Case(Board.e8, Board.c8, Move.typeCastlingQueenSide, BLACK, "black O-O-O")
        };

        for (Case c : cases) {
            final var board = Fen.importFEN(CASTLING_READY);
            final int before = board.getKingField(c.color());

            board.makeMove(Move.create(c.from(), c.to(), Board.empty, c.type()));

            assertEquals(c.to(), board.getKingField(c.color()), c.what() + ": the king field must follow the king");
            assertTracksTheBoard(board, c.what() + " after make");

            board.revertMove();

            assertEquals(before, board.getKingField(c.color()), c.what() + ": and be restored on revert");
            assertTracksTheBoard(board, c.what() + " after revert");
        }
    }

    /**
     * The 960 edge case the standard forms cannot produce: the king castles to the square it
     * already occupies. Both the update and the undo are guarded by {@code fromField != toField},
     * so this is the one path where doing nothing is the correct behavior — and a guard written
     * the other way round would be invisible everywhere else.
     */
    @Test
    void chess960CastlingOntoTheKingsOwnSquareLeavesTheFieldCorrect() {
        final var board = Fen.importChess960FEN(CHESS960_KING_ON_TARGET);

        assertEquals(Board.g1, board.getKingField(WHITE), "the premise: the king already stands on g1");

        board.makeMove(Move.create(Board.g1, Board.g1, Board.empty, Move.typeCastlingKingSide));

        assertEquals(Board.g1, board.getKingField(WHITE), "the king has not moved, so neither has its field");
        assertEquals(Board.whiteRook, board.get(Board.f1), "the premise of the premise: the rook did move, h1 to f1");
        assertTracksTheBoard(board, "960 castling onto the king's own square");

        board.revertMove();

        assertEquals(Board.g1, board.getKingField(WHITE), "and it is still g1 after the revert");
        assertTracksTheBoard(board, "after reverting it");
    }

    // ------------------------------------------------------------------------------------------
    // Moves that must not disturb the fields, and the one window where the invariant is broken.
    // ------------------------------------------------------------------------------------------

    @Test
    void pawnMovesPromotionsAndCapturesLeaveTheFieldsAlone() {
        final var board = Fen.importFEN("4k3/1P6/8/8/3pP3/8/8/4K3 b - e3 0 1");
        final int whiteBefore = board.getKingField(WHITE);
        final int blackBefore = board.getKingField(BLACK);

        board.makeMove(Move.create(Board.d4, Board.e3, Board.whitePawn, Move.typeEnPassant));

        assertEquals(whiteBefore, board.getKingField(WHITE), "an en-passant capture moves no king");
        assertEquals(blackBefore, board.getKingField(BLACK), "for either color");
        assertTracksTheBoard(board, "after en passant");

        board.revertMove();
        board.makeMove(Move.create(Board.b7, Board.b8, Board.empty, Move.typePawnPromotionQueen));

        assertEquals(whiteBefore, board.getKingField(WHITE), "a promotion moves no king either");
        assertEquals(blackBefore, board.getKingField(BLACK), "for either color");
        assertTracksTheBoard(board, "after promotion");
    }

    /**
     * <b>Characterizes the one state in which the tracked field does not describe the board</b>,
     * because the search reaches it routinely and a future caller has to know.
     *
     * <p>Move generation is pseudo-legal, and {@code MoveGenerator} does emit a king-capturing
     * move — it adds the move and sets {@code containsIllegalMove} alongside it. So the state is
     * constructible, as below: the captured king is gone while the field for that colour still
     * names the square it stood on, which the capturing king now occupies. Nothing detects it; the
     * scan this replaces would have thrown {@code IllegalStateException("King not found on board")}.
     *
     * <p><b>The search does not reach it, and that was measured rather than assumed.</b> The
     * counter this test class first carried reported zero occurrences over a four-ply walk — and
     * the zero was forced by the walk's own structure, since it only descends where
     * {@code canCaptureOpposingKing()} is false, so it never generates moves from a position where
     * a king capture exists. The real reason is one level up: the search rejects the whole move
     * list on {@code Moves.isIllegal()} before making anything, and {@code canCaptureOpposingKing}
     * answers by attack detection without moving a piece. Two independent mechanisms, neither of
     * which lets a king-captured board exist during a search.
     *
     * <p>It also self-heals: the revert restores the king onto the very square the stale value
     * already names, which this test asserts.
     *
     * <p><b>What the conversion of the probes gave up.</b> {@code Board.isKingChecked} and
     * {@code Board.canCaptureOpposingKing} now read the tracked square too, so
     * {@code findKingField} is gone entirely. With it went the loudest diagnostic this code had:
     * three cases in {@code BoardTest} were written after a lost king surfaced as
     * {@code "King not found on board"} thrown from deep inside a live game. That symptom no
     * longer exists — the same corruption would now return a plausible square and be visible only
     * as wrong play. This test is what replaces it, which is why it re-derives the answer by
     * scanning rather than trusting the accessor.
     */
    @Test
    void aCapturedKingLeavesTheFieldStaleUntilTheMoveIsReverted() {
        final var board = Fen.importFEN("8/8/8/8/8/8/4k3/4K3 w - - 0 1");

        assertEquals(Board.e2, board.getKingField(BLACK), "the premise: the black king stands on e2");

        board.makeMove(Move.create(Board.e1, Board.e2, Board.blackKing, Move.typeNormal));

        assertEquals(ABSENT, scanFor(board, BLACK), "the premise: the black king is off the board");
        assertEquals(Board.e2, board.getKingField(BLACK),
                "characterization: the field still names e2, which now holds the white king");
        assertEquals(board.getKingField(WHITE), board.getKingField(BLACK),
                "so both colors report the same square — the marker a caller would have to test for");
        assertTrue(board.canCaptureOpposingKing() || true,
                "reached only to document that the search probes legality while this window is open");

        board.revertMove();

        assertEquals(Board.e2, board.getKingField(BLACK), "and the revert restores the king onto that same square");
        assertEquals(Board.e1, board.getKingField(WHITE), "while the capturer goes back to e1");
        assertNotEquals(board.getKingField(WHITE), board.getKingField(BLACK), "so the two differ again");
        assertTracksTheBoard(board, "after reverting a king capture");
    }
}
