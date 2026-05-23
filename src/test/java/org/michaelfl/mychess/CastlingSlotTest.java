package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Direct unit tests for the {@link CastlingSlot} enum: ordinal layout
 * (which doubles as the index into {@code Board.castlingRookFiles}),
 * the {@link GameStatus#getCastlingState()} bit-mask wiring, and the
 * {@link CastlingSlot#slotFor(boolean, boolean)} dispatch helper.
 *
 * @author Michael Fleischhauer
 */
class CastlingSlotTest {

    @Test
    void ordinals_followWQ_WK_BQ_BK_order() {
        assertEquals(0, CastlingSlot.WHITE_QUEENSIDE.ordinal(), "WQ ordinal");
        assertEquals(1, CastlingSlot.WHITE_KINGSIDE.ordinal(),  "WK ordinal");
        assertEquals(2, CastlingSlot.BLACK_QUEENSIDE.ordinal(), "BQ ordinal");
        assertEquals(3, CastlingSlot.BLACK_KINGSIDE.ordinal(),  "BK ordinal");
    }

    @Test
    void bitMask_eachSlotPointsAtItsOwnGameStatusBit() {
        assertEquals(GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE,
                CastlingSlot.WHITE_QUEENSIDE.bitMask(), "WQ bit");
        assertEquals(GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE,
                CastlingSlot.WHITE_KINGSIDE.bitMask(), "WK bit");
        assertEquals(GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE,
                CastlingSlot.BLACK_QUEENSIDE.bitMask(), "BQ bit");
        assertEquals(GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE,
                CastlingSlot.BLACK_KINGSIDE.bitMask(), "BK bit");
    }

    @Test
    void bitMasks_areAllDistinctAndCoverFourBits() {
        int union = CastlingSlot.WHITE_QUEENSIDE.bitMask()
                | CastlingSlot.WHITE_KINGSIDE.bitMask()
                | CastlingSlot.BLACK_QUEENSIDE.bitMask()
                | CastlingSlot.BLACK_KINGSIDE.bitMask();
        assertEquals(4, Integer.bitCount(union),
                "the four slot masks must occupy four distinct bits");
    }

    @Test
    void slotFor_white_kingside_returnsWhiteKingside() {
        assertEquals(CastlingSlot.WHITE_KINGSIDE,
                CastlingSlot.slotFor(true, true), "white + kingside");
    }

    @Test
    void slotFor_white_queenside_returnsWhiteQueenside() {
        assertEquals(CastlingSlot.WHITE_QUEENSIDE,
                CastlingSlot.slotFor(true, false), "white + queenside");
    }

    @Test
    void slotFor_black_kingside_returnsBlackKingside() {
        assertEquals(CastlingSlot.BLACK_KINGSIDE,
                CastlingSlot.slotFor(false, true), "black + kingside");
    }

    @Test
    void slotFor_black_queenside_returnsBlackQueenside() {
        assertEquals(CastlingSlot.BLACK_QUEENSIDE,
                CastlingSlot.slotFor(false, false), "black + queenside");
    }
}
