package org.michaelfl.mychess;

/**
 * Identifies one of the four castling slots in a position. Used as a
 * compact handle for the {@link Board#getCastlingRookFile(CastlingSlot)}
 * lookup and as the bridge to the matching castling-right bit in
 * {@link GameStatus}.
 *
 * <p>The rook-file lookup ignores the color half of the slot per the
 * Chess960 starting-position symmetry (see
 * {@link Board#getCastlingRookFile} for details): the kingside-rook file
 * is the same for both colors, and likewise the queenside-rook file.
 * The four enum values still exist so {@link #bitMask()} can map each
 * slot to its own {@link GameStatus} castling-right bit and so
 * {@link #slotFor(boolean, boolean)} can return a color-aware handle.
 *
 * @author Michael Fleischhauer
 */
public enum CastlingSlot {

    WHITE_QUEENSIDE(GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE, 0),
    WHITE_KINGSIDE(GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE, 1),
    BLACK_QUEENSIDE(GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE, 0),
    BLACK_KINGSIDE(GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE, 1);

    private final int bitMask;
    /** 1 = kingside, 0 = queenside. */
    private final int kingSide;

    CastlingSlot(int bitMask, int kingSide) {
        this.bitMask = bitMask;
        this.kingSide = kingSide;
    }

    /** The {@code GameStatus.BIT_*} mask flagging this slot's castling right. */
    public int bitMask() {
        return bitMask;
    }

    /**
     * The index into the symmetry-collapsed {@code Board.castlingRookFiles}
     * array that this slot addresses: {@code 1} for the two kingside
     * variants ({@code WHITE_KINGSIDE}, {@code BLACK_KINGSIDE}) — i.e.
     * the rook toward the h-file — and {@code 0} for the two queenside
     * variants — the rook toward the a-file. Used by
     * {@link Board#getCastlingRookFile(CastlingSlot)} to fold the color
     * half of the slot away: the Chess960 starting-position symmetry
     * guarantees Black's back rank mirrors White's, so the kingside and
     * queenside rook files are stored once per side, not per color.
     */
    public int getKingQueenSideIndex() {
        return kingSide;
    }

    static CastlingSlot slotFor(boolean white, boolean kingside) {
        if (white) {
            return kingside ? CastlingSlot.WHITE_KINGSIDE : CastlingSlot.WHITE_QUEENSIDE;
        }

        return kingside ? CastlingSlot.BLACK_KINGSIDE : CastlingSlot.BLACK_QUEENSIDE;
    }
}
