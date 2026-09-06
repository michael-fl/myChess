package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one invariant of {@code GameStatus.castlingState}: a color's <b>has castled</b> bit is never
 * set together with a castling right of that same color.
 *
 * <p>It is what licenses {@link GameStatus#isWhiteCastlingPossible()} and
 * {@link GameStatus#isBlackCastlingPossible()} to be a plain OR of the two right bits. Both used
 * to guard with {@code !hasCastled() &&}, which was redundant — {@code Board.calculateNewCastlingState}
 * is the only writer of the high bits and clears that color's rights in the same three lines that
 * set one. Remove the invariant and those two methods become wrong, silently and in the direction
 * that is hardest to notice: they would report "may still castle" for a king that has castled.
 *
 * <p><b>Not covered by {@code BoardTest}.</b> That class asserts {@code hasWhiteCastled()} /
 * {@code hasBlackCastled()} after each of the four castling moves, which is the easy half. It
 * never asserts that the rights went away, so a variant that set the bit and left a right standing
 * would pass it.
 *
 * <p>Three levels of coverage, deliberately separate:
 * <ul>
 *   <li>the four castling moves, by hand, standard and Chess960 — the states the invariant is
 *       created in;</li>
 *   <li>an exhaustive walk to depth 4 from positions where castling is available, asserting the
 *       invariant at every node — the states it has to survive;</li>
 *   <li>the two position readers, {@link Fen} and {@code PositionEncoding}, which cannot know
 *       whether a king castled and must therefore report that it did not.</li>
 * </ul>
 *
 * @author Michael Fleischhauer
 */
class GameStatusCastlingStateTest {

    /** Plies of the exhaustive walk. Four is ~200k nodes per start position and runs in seconds. */
    private static final int WALK_DEPTH = 4;

    /** A back rank cleared between king and rooks, so all four castling moves are legal at once. */
    private static final String CASTLING_READY = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1";

    /**
     * A Chess960 position with the king on b1/b8 and rooks on a and h. Both castling moves are
     * legal at once ({@code b1-g1} and {@code b1-c1}), and the king travels to the right in
     * <em>both</em> of them — the case a hard-coded target square or a direction derived from
     * standard chess gets wrong.
     *
     * <p>Deliberately not a Chess960 <em>start</em> position: those have a full back rank, so no
     * castling move is legal in them and such a fixture would silently test nothing. The first
     * version of this test used one and failed with "no castling move of type 1".
     */
    private static final String CHESS960_READY = "rk5r/pppppppp/8/8/8/8/PPPPPPPP/RK5R w KQkq - 0 1";

    private static void assertInvariant(GameStatus status, String where) {
        if (status.hasWhiteCastled()) {
            assertFalse(status.isWhiteCastlingKingSidePossible(),
                    where + ": white has castled, so the kingside right must be gone — state="
                            + status.getCastlingState());
            assertFalse(status.isWhiteCastlingQueenSidePossible(),
                    where + ": white has castled, so the queenside right must be gone — state="
                            + status.getCastlingState());
        }

        if (status.hasBlackCastled()) {
            assertFalse(status.isBlackCastlingKingSidePossible(),
                    where + ": black has castled, so the kingside right must be gone — state="
                            + status.getCastlingState());
            assertFalse(status.isBlackCastlingQueenSidePossible(),
                    where + ": black has castled, so the queenside right must be gone — state="
                            + status.getCastlingState());
        }
    }

    /**
     * The simplified accessors must agree with the guarded form they replaced, at every node of
     * the walk. Asserting the invariant alone would leave the refactoring itself unpinned.
     */
    private static void assertAccessorsAgreeWithTheGuardedForm(GameStatus status, String where) {
        boolean whiteGuarded = !status.hasWhiteCastled()
                && (status.isWhiteCastlingKingSidePossible() || status.isWhiteCastlingQueenSidePossible());
        boolean blackGuarded = !status.hasBlackCastled()
                && (status.isBlackCastlingKingSidePossible() || status.isBlackCastlingQueenSidePossible());

        assertEquals(whiteGuarded, status.isWhiteCastlingPossible(),
                where + ": isWhiteCastlingPossible() must equal the !hasWhiteCastled()-guarded form"
                        + " — state=" + status.getCastlingState());
        assertEquals(blackGuarded, status.isBlackCastlingPossible(),
                where + ": isBlackCastlingPossible() must equal the !hasBlackCastled()-guarded form"
                        + " — state=" + status.getCastlingState());
    }

    /** Plays the first castling move of the requested type found in the position, or fails. */
    private static Board afterCastling(String fen, boolean chess960, byte moveType) {
        Board board = chess960 ? Fen.importChess960FEN(fen) : Fen.importFEN(fen);
        var generator = new MoveGenerator(MoveSorter.defaultImplementation());
        Moves moves = generator.calculateMoves(board);

        for (int move : Arrays.copyOf(moves.getMoves(), moves.count())) {
            if (move != 0 && Move.getMoveType(move) == moveType) {
                board.makeMove(move);

                return board;
            }
        }

        throw new IllegalStateException("no castling move of type " + moveType + " in " + fen);
    }

    private static long walk(Board board, MoveGenerator generator, int remaining, int callDepth) {
        if (remaining == 0) {
            return 0;
        }

        Moves moves = generator.calculateMoves(board, callDepth);
        int count = moves.count();
        int[] snapshot = Arrays.copyOf(moves.getMoves(), count);
        long visited = 0;

        for (int move : snapshot) {
            if (move == 0) {
                continue;
            }

            board.makeMove(move);
            visited++;

            GameStatus status = board.getGameStatus();
            String where = "after " + new Move(move) + " at ply " + callDepth;

            assertInvariant(status, where);
            assertAccessorsAgreeWithTheGuardedForm(status, where);

            if (!board.canCaptureOpposingKing()) {
                visited += walk(board, generator, remaining - 1, callDepth + 1);
            }

            board.revertMove();
        }

        return visited;
    }

    // ------------------------------------------------------------------------------------------
    // The four castling moves — the states the invariant is created in
    // ------------------------------------------------------------------------------------------

    @Test
    void castlingSetsTheFlagAndClearsBothRightsOfThatColor() {
        record Case(String label, boolean chess960, byte moveType) {}

        final Case[] cases = {
                new Case("white kingside", false, Move.typeCastlingKingSide),
                new Case("white queenside", false, Move.typeCastlingQueenSide),
                new Case("Chess960 white kingside", true, Move.typeCastlingKingSide),
                new Case("Chess960 white queenside", true, Move.typeCastlingQueenSide)
        };

        for (Case c : cases) {
            GameStatus status = afterCastling(c.chess960() ? CHESS960_READY : CASTLING_READY,
                    c.chess960(), c.moveType()).getGameStatus();

            assertTrue(status.hasWhiteCastled(), c.label() + ": the has-castled flag must be set");
            assertFalse(status.isWhiteCastlingKingSidePossible(),
                    c.label() + ": the kingside right must be cleared in the same step");
            assertFalse(status.isWhiteCastlingQueenSidePossible(),
                    c.label() + ": the queenside right must be cleared in the same step, not only "
                            + "the side that was used");
            assertFalse(status.isWhiteCastlingPossible(),
                    c.label() + ": and the summary accessor must therefore report no castling");
        }
    }

    @Test
    void blackCastlingIsUnaffectedByWhitesRightsAndViceVersa() {
        // Black castles; white's untouched rights must survive, and black's must not.
        Board board = Fen.importFEN(CASTLING_READY);
        var generator = new MoveGenerator(MoveSorter.defaultImplementation());
        Moves whiteMoves = generator.calculateMoves(board);

        for (int move : Arrays.copyOf(whiteMoves.getMoves(), whiteMoves.count())) {
            if (move != 0 && Move.getMoveType(move) == Move.typeCastlingKingSide) {
                board.makeMove(move);
                break;
            }
        }

        Moves blackMoves = generator.calculateMoves(board);

        for (int move : Arrays.copyOf(blackMoves.getMoves(), blackMoves.count())) {
            if (move != 0 && Move.getMoveType(move) == Move.typeCastlingQueenSide) {
                board.makeMove(move);
                break;
            }
        }

        GameStatus status = board.getGameStatus();

        assertTrue(status.hasWhiteCastled(), "white castled first and the flag must persist");
        assertTrue(status.hasBlackCastled(), "black castled second");
        assertFalse(status.isWhiteCastlingPossible(), "white can no longer castle");
        assertFalse(status.isBlackCastlingPossible(), "black can no longer castle");
        assertEquals(GameStatus.BIT_WHITE_HAS_CASTLED | GameStatus.BIT_BLACK_HAS_CASTLED,
                status.getCastlingState(),
                "with both sides castled the state must be exactly the two high bits — any right "
                        + "still standing would be the invariant broken");
    }

    // ------------------------------------------------------------------------------------------
    // The exhaustive walk — the states the invariant has to survive
    // ------------------------------------------------------------------------------------------

    /**
     * Every position reachable in {@link #WALK_DEPTH} plies from a back rank where all four
     * castling moves are available. This is where a defect that only shows up in combination
     * would appear — a rook captured on its home square, a king stepping out and back, a rook
     * moving away and another arriving on the same square.
     */
    @Test
    void theInvariantHoldsAcrossAnExhaustiveWalk() {
        var generator = new MoveGenerator(MoveSorter.defaultImplementation());
        long standard = walk(Fen.importFEN(CASTLING_READY), generator, WALK_DEPTH, 0);
        long chess960 = walk(Fen.importChess960FEN(CHESS960_READY), generator, WALK_DEPTH, 0);

        assertTrue(standard > 100_000,
                "the standard walk must actually visit a large number of positions, was " + standard);
        assertTrue(chess960 > 10_000,
                "and the Chess960 walk too, was " + chess960);
    }

    // ------------------------------------------------------------------------------------------
    // The position readers — neither can know whether a king castled
    // ------------------------------------------------------------------------------------------

    /**
     * A FEN carries no has-castled field, so a position read from one reports "has not castled"
     * for both sides — even when the king sits on g1 with no rights left and every human would
     * say it castled. Pinned because the king-line term on branch {@code king-line-v2} gates on
     * exactly this distinction, which makes a FEN-loaded position behave differently from the same
     * position reached by playing the moves.
     */
    @Test
    void aFenNeverReportsThatAKingHasCastled() {
        GameStatus status = Fen.importFEN("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R4RK1 w kq - 0 20")
                .getGameStatus();

        assertFalse(status.hasWhiteCastled(),
                "white's king stands on g1 with no rights left, but a FEN cannot say whether it "
                        + "castled or walked there, so the flag must stay clear");
        assertFalse(status.isWhiteCastlingPossible(),
                "white still has no castling rights — that part a FEN does carry");
        assertTrue(status.isBlackCastlingPossible(), "black's kq rights must survive the import");
        assertInvariant(status, "FEN import");
    }

    /**
     * The same for {@code PositionEncoding}, which is a position encoding as well and therefore
     * has the same blind spot. Its earlier version derived the flag from "this color holds no
     * rights", which conflated losing the rights with castling and could produce a has-castled
     * bit next to a right of the other color.
     */
    @Test
    void aDecodedPositionNeverReportsThatAKingHasCastled() {
        Board original = Fen.importFEN("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R4RK1 w kq - 0 20");
        GameStatus originalStatus = original.getGameStatus();
        Board decoded = PositionEncoding.decode(PositionEncoding.encode(original),
                originalStatus.getPlyCount(), originalStatus.getLastMove(),
                originalStatus.getHalfMoveClock());
        GameStatus status = decoded.getGameStatus();

        assertFalse(status.hasWhiteCastled(),
                "a decoded position cannot know that white castled, so the flag must stay clear — "
                        + "deriving it from 'white holds no rights' would report a king that "
                        + "walked to g1 as castled");
        assertFalse(status.hasBlackCastled(), "and the same for black");
        assertInvariant(status, "PositionEncoding round trip");
        assertEquals(original.exportFEN(), decoded.exportFEN(),
                "the round trip must still reproduce the position itself");
    }
}
