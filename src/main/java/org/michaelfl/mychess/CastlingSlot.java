package org.michaelfl.mychess;

/**
 * Identifies one of the four castling slots in a position. Used as a
 * compact handle for the {@link Board#getCastlingRookFile(CastlingSlot)}
 * lookup and as the bridge to the matching castling-right bit in
 * {@link GameStatus}.
 *
 * <p>Ordinal order is {@code white queenside, white kingside, black
 * queenside, black kingside} — chosen to match the
 * {@code Board.castlingRookFiles} array layout, which in turn follows the
 * {@code a}-file → {@code h}-file reading order from White's perspective.
 *
 * @author Michael Fleischhauer
 */
public enum CastlingSlot {

    WHITE_QUEENSIDE(GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE),
    WHITE_KINGSIDE(GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE),
    BLACK_QUEENSIDE(GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE),
    BLACK_KINGSIDE(GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE);

    private final int bitMask;

    CastlingSlot(int bitMask) {
        this.bitMask = bitMask;
    }

    /** The {@code GameStatus.BIT_*} mask flagging this slot's castling right. */
    public int bitMask() {
        return bitMask;
    }

    static CastlingSlot slotFor(boolean white, boolean kingside) {
        if (white) {
            return kingside ? CastlingSlot.WHITE_KINGSIDE : CastlingSlot.WHITE_QUEENSIDE;
        }

        return kingside ? CastlingSlot.BLACK_KINGSIDE : CastlingSlot.BLACK_QUEENSIDE;
    }
}
