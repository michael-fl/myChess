package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for the castling-rook-file storage introduced on
 * {@link Board} as part of Chess960 Phase 1:
 * {@link Board#defaultCastlingRookFiles()},
 * {@link Board#getCastlingRookFile(CastlingSlot)}, and the wiring of those
 * files through both the {@link Board#Board(byte[], GameStatus, byte[])}
 * constructor and {@link Board#copy()}.
 *
 * <p>FEN-driven coverage of the same surface lives in
 * {@link FenChess960ImportTest}; this class exercises the storage layer
 * in isolation from the FEN parser.
 *
 * @author Michael Fleischhauer
 */
class BoardCastlingRookFilesTest {

    @Test
    void defaultCastlingRookFiles_returnsStandardLayout() {
        assertArrayEquals(new byte[] { 0, 7 }, Board.defaultCastlingRookFiles(),
                "standard-chess defaults a-file (queenside, idx 0) / h-file (kingside, idx 1), "
                        + "symmetric across both colors per the Chess960 starting-position invariant");
    }

    @Test
    void defaultCastlingRookFiles_returnsFreshArrayPerCall() {
        byte[] first = Board.defaultCastlingRookFiles();
        byte[] second = Board.defaultCastlingRookFiles();
        assertNotSame(first, second, "each call must return a distinct array");

        first[0] = (byte) 99;
        assertEquals(0, second[0],
                "mutating the first array must not affect the second");
    }

    @Test
    void createNewGame_exposesDefaultRookFilesViaAccessor() {
        Board board = Board.createNewGame();
        assertEquals(0, board.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE), "WQ default");
        assertEquals(7, board.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE),  "WK default");
        assertEquals(0, board.getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE), "BQ default");
        assertEquals(7, board.getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE),  "BK default");
    }

    @Test
    void threeArgConstructor_storesGivenRookFiles() {
        // The 2-entry array stores one rook file per side
        // (queenside / kingside) — both colors share the same values
        // because Chess960 mirrors Black's back rank from White's. Both
        // the white-* and black-* slot lookups must therefore return
        // the same per-side file.
        byte[] custom = new byte[] { 1, 5 };
        Board board = new Board(Board.createNewGame().getRawBoard(),
                GameStatus.newGame(), custom, false);

        assertEquals(1, board.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE), "WQ custom");
        assertEquals(5, board.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE),  "WK custom");
        assertEquals(1, board.getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE),
                "BQ must mirror WQ — single per-side storage");
        assertEquals(5, board.getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE),
                "BK must mirror WK — single per-side storage");
    }

    @Test
    void twoArgConstructor_appliesDefaultRookFiles() {
        Board board = new Board(Board.createNewGame().getRawBoard(), GameStatus.newGame());

        assertEquals(0, board.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE), "WQ via 2-arg ctor");
        assertEquals(7, board.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE),  "WK via 2-arg ctor");
        assertEquals(0, board.getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE), "BQ via 2-arg ctor");
        assertEquals(7, board.getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE),  "BK via 2-arg ctor");
    }

    @Test
    void copy_carriesRookFilesIntoTheCopy() {
        byte[] custom = new byte[] { 2, 4 };
        Board original = new Board(Board.createNewGame().getRawBoard(),
                GameStatus.newGame(), custom, false);

        Board copy = original.copy();
        assertEquals(2, copy.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE), "WQ on copy");
        assertEquals(4, copy.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE),  "WK on copy");
        assertEquals(2, copy.getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE), "BQ on copy");
        assertEquals(4, copy.getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE),  "BK on copy");
    }

    @Test
    void copy_rookFilesAreDeepCopied_noSharedMutation() {
        byte[] custom = new byte[] { 2, 4 };
        Board original = new Board(Board.createNewGame().getRawBoard(),
                GameStatus.newGame(), custom, false);

        Board copy = original.copy();

        // Mutate the byte array still held externally; the original keeps a
        // reference to it but the copy should not.
        custom[0] = (byte) 99;
        assertEquals(2, copy.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE),
                "copy must hold its own array independent of the original's source array");
    }

    /**
     * Companion to the move-based clearing tests: a rook can also disappear
     * by being <em>captured on its starting square</em>, in which case the
     * matching castling right must be cleared even though the opponent's
     * piece (not the rook itself) is the one that moved.
     *
     * <p>Standard chess variant — uses the classical {@code h1} square so
     * this test is independent of any 960-specific machinery.
     */
    @Test
    void capturedKingsideRook_clearsWhiteKingsideCastlingRight() {
        // White king e1, white kingside rook h1 (only kingside right alive).
        // Black bishop a8 has a clear long diagonal to h1 — captures on its
        // first move and the white rook is gone.
        Board board = Fen.importFEN("b3k3/8/8/8/8/8/8/4K2R b K - 0 1");

        assertTrue(board.getGameStatus().isWhiteCastlingKingSidePossible(),
                "WK alive before capture (rook on h1, file 7)");

        var capture = MoveDescription.fromString("Bxh1", GameStatus.TURN_BLACK);
        var resolved = board.moveDescriptionToMove(
                board.resolveMoveDescription(capture,
                        new MoveGenerator(MoveSorter.defaultImplementation())));
        board.makeMove(resolved.move());

        assertFalse(board.getGameStatus().isWhiteCastlingKingSidePossible(),
                "WK must be cleared after the kingside rook is captured on h1");
    }

    /**
     * Chess 960 variant of {@link #capturedKingsideRook_clearsWhiteKingsideCastlingRight}:
     * the white kingside rook sits on a non-{@code h} file (here {@code f1})
     * and is captured by an enemy piece. The matching castling-right bit must
     * be cleared, and — equally important — the unaffected queenside right
     * must stay alive.
     */
    @Test
    void chess960_capturedKingsideRook_clearsOnlyMatchingRight() {
        // White king b1, white rooks a1 (queenside) and f1 (kingside).
        // Black king b8, black bishop a6 with a clear diagonal a6→f1.
        Board board = Fen.importFEN("1k6/8/b7/8/8/8/8/RK3R2 b KQ - 0 1");

        GameStatus before = board.getGameStatus();
        assertAll("both white rights alive before capture",
                () -> assertTrue(before.isWhiteCastlingKingSidePossible(),
                        "WK alive (rook on f1, file 5)"),
                () -> assertTrue(before.isWhiteCastlingQueenSidePossible(),
                        "WQ alive (rook on a1, file 0)")
        );

        var capture = MoveDescription.fromString("Bxf1", GameStatus.TURN_BLACK);
        var resolved = board.moveDescriptionToMove(
                board.resolveMoveDescription(capture,
                        new MoveGenerator(MoveSorter.defaultImplementation())));
        board.makeMove(resolved.move());

        GameStatus after = board.getGameStatus();
        assertAll("only the kingside right must drop",
                () -> assertFalse(after.isWhiteCastlingKingSidePossible(),
                        "WK must be cleared: kingside rook captured on f1"),
                () -> assertTrue(after.isWhiteCastlingQueenSidePossible(),
                        "WQ must survive: queenside rook on a1 unchanged")
        );
    }

    /**
     * Regression for the Chess960 "rights collapse after first move" defect
     * spotted via {@code new 960} in the REPL: {@link Board#makeMove} updates
     * the castling-state bits through
     * {@code Board.calculateNewCastlingState}, which re-validates each right
     * against the standard-chess king and rook squares
     * ({@code e1}/{@code h1}/{@code a1} and the black mirrors). Any 960 game
     * where king or rooks start elsewhere fails those checks, and all four
     * castling-right bits get cleared after the very first move — even if
     * that move is a plain pawn push that touches no castling-relevant
     * piece.
     */
    @Test
    void chess960_firstPawnMove_preservesAllFourCastlingRights() {
        // cutechess sample 960 position: white king on b1, rooks on a1 and
        // f1, mirror for black. Castling field FAfa, all four rights alive
        // straight out of Fen.importFEN.
        Board board = Fen.importFEN(
                "rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1");

        GameStatus before = board.getGameStatus();
        assertAll("all four castling rights must be alive immediately after import",
                () -> assertTrue(before.isWhiteCastlingKingSidePossible(),  "WK before move"),
                () -> assertTrue(before.isWhiteCastlingQueenSidePossible(), "WQ before move"),
                () -> assertTrue(before.isBlackCastlingKingSidePossible(),  "BK before move"),
                () -> assertTrue(before.isBlackCastlingQueenSidePossible(), "BQ before move")
        );

        // Plain pawn push e2-e4 — touches no king and no rook on either side.
        // The 960 setup happens to have a knight on e1 (not a king), so the
        // bug's e1/whiteKing check fails and every right is invalidated.
        var pawnMove = MoveDescription.fromString("e2-e4", GameStatus.TURN_WHITE);
        var resolved = board.moveDescriptionToMove(
                board.resolveMoveDescription(pawnMove,
                        new MoveGenerator(MoveSorter.defaultImplementation())));
        board.makeMove(resolved.move());

        GameStatus after = board.getGameStatus();
        assertAll("all four castling rights must survive a pawn-only first move",
                () -> assertTrue(after.isWhiteCastlingKingSidePossible(),  "WK must survive e2-e4"),
                () -> assertTrue(after.isWhiteCastlingQueenSidePossible(), "WQ must survive e2-e4"),
                () -> assertTrue(after.isBlackCastlingKingSidePossible(),  "BK must survive e2-e4"),
                () -> assertTrue(after.isBlackCastlingQueenSidePossible(), "BQ must survive e2-e4")
        );
    }
}
