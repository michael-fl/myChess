package org.michaelfl.mychess;

/**
 * Optional attributes a parsed {@link MoveDescription} can carry beyond
 * the bare from/to/piece information. The notation may explicitly mark a
 * move as capture, check, checkmate, en-passant or castling; the
 * exporter in {@link Board#moveToShortNotation(Move)} sets the same
 * flags based on the actual board state.
 *
 * <p>Stored in a single {@code EnumSet<MoveFlag>} on the move description
 * so the type signature stays narrow and the parser/validator code
 * reads the flags via {@code contains(...)} or the convenience accessors
 * on {@link MoveDescription}.
 *
 * @author Michael Fleischhauer
 */
public enum MoveFlag {

    /** The notation contains an {@code x} separator, or the move actually captures a piece. */
    CAPTURE,

    /** The notation ends in {@code +} (or {@code ++}), or the move actually checks the opposing king. */
    CHECK,

    /** The notation ends in {@code #}, or the move actually delivers checkmate. */
    CHECKMATE,

    /** The notation carries the {@code e.p.} marker, or the move is actually an en-passant capture. */
    EN_PASSANT,

    /** The notation is {@code O-O} / {@code 0-0}, or the move is an actual kingside castling. */
    CASTLING_KING_SIDE,

    /** The notation is {@code O-O-O} / {@code 0-0-0}, or the move is an actual queenside castling. */
    CASTLING_QUEEN_SIDE
}
