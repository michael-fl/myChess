package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

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
        assertArrayEquals(new byte[] { 0, 7, 0, 7 }, Board.defaultCastlingRookFiles(),
                "standard-chess defaults a-file (queenside) / h-file (kingside) for both colors");
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
        byte[] custom = new byte[] { 1, 5, 2, 6 };
        Board board = new Board(Board.createEmptyRawBoard(),
                GameStatus.newGame(), custom);

        assertEquals(1, board.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE), "WQ custom");
        assertEquals(5, board.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE),  "WK custom");
        assertEquals(2, board.getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE), "BQ custom");
        assertEquals(6, board.getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE),  "BK custom");
    }

    @Test
    void twoArgConstructor_appliesDefaultRookFiles() {
        Board board = new Board(Board.createEmptyRawBoard(), GameStatus.newGame());

        assertEquals(0, board.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE), "WQ via 2-arg ctor");
        assertEquals(7, board.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE),  "WK via 2-arg ctor");
        assertEquals(0, board.getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE), "BQ via 2-arg ctor");
        assertEquals(7, board.getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE),  "BK via 2-arg ctor");
    }

    @Test
    void copy_carriesRookFilesIntoTheCopy() {
        byte[] custom = new byte[] { 2, 4, 2, 4 };
        Board original = new Board(Board.createEmptyRawBoard(),
                GameStatus.newGame(), custom);

        Board copy = original.copy();
        assertEquals(2, copy.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE), "WQ on copy");
        assertEquals(4, copy.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE),  "WK on copy");
        assertEquals(2, copy.getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE), "BQ on copy");
        assertEquals(4, copy.getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE),  "BK on copy");
    }

    @Test
    void copy_rookFilesAreDeepCopied_noSharedMutation() {
        byte[] custom = new byte[] { 2, 4, 2, 4 };
        Board original = new Board(Board.createEmptyRawBoard(),
                GameStatus.newGame(), custom);

        Board copy = original.copy();

        // Mutate the byte array still held externally; the original keeps a
        // reference to it but the copy should not.
        custom[0] = (byte) 99;
        assertEquals(2, copy.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE),
                "copy must hold its own array independent of the original's source array");
    }
}
