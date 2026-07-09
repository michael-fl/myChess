package org.michaelfl.mychess;

/**
 * Immutable per-ply state snapshot owned by {@link Board}: side to move,
 * castling-rights bitmask, en-passant target square, half-move clock, last
 * move and Zobrist position hash. Pushed before every {@code makeMove} and
 * popped on {@code revertMove}.
 *
 * @author Michael Fleischhauer
 */
public final class GameStatus {

    public static final int BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE = 1;
    public static final int BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE = 2;
    public static final int BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE = 4;
    public static final int BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE = 8;
    public static final int BIT_WHITE_HAS_CASTLED = 16;
    public static final int BIT_BLACK_HAS_CASTLED = 32;

    private static final byte INITIAL_CASTLING_STATE = 15;
    private static final long INITIAL_POSITION_HASH = -8376097377325274526L;

    public static final int TURN_WHITE = 8;
    @SuppressWarnings("WeakerAccess")
    public static final int TURN_BLACK = 16;

    private final int plyCount;
    private final int turn;
    private final int lastMove;
    private final int halfMoveClock;

    /**
     * Six-bit castling-state bitmask. Each bit is one of the
     * {@code BIT_*} constants defined on this class; the field is the
     * bitwise OR of all bits that are currently set. Raw integer range
     * therefore is {@code [0, 63]}; a typical middle-game value is any
     * subset of the six bits below.
     *
     * <table>
     *   <caption>Bit layout of {@code castlingState}</caption>
     *   <tr><th>Bit value</th><th>Constant</th><th>Meaning when set</th></tr>
     *   <tr><td>{@code 0x01} (=&nbsp;1)</td>
     *       <td>{@link #BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE}</td>
     *       <td>White may still castle kingside (K + kingside rook
     *           both unmoved, path unblocked / unattacked can be
     *           checked separately).</td></tr>
     *   <tr><td>{@code 0x02} (=&nbsp;2)</td>
     *       <td>{@link #BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE}</td>
     *       <td>White may still castle queenside.</td></tr>
     *   <tr><td>{@code 0x04} (=&nbsp;4)</td>
     *       <td>{@link #BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE}</td>
     *       <td>Black may still castle kingside.</td></tr>
     *   <tr><td>{@code 0x08} (=&nbsp;8)</td>
     *       <td>{@link #BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE}</td>
     *       <td>Black may still castle queenside.</td></tr>
     *   <tr><td>{@code 0x10} (=&nbsp;16)</td>
     *       <td>{@link #BIT_WHITE_HAS_CASTLED}</td>
     *       <td>White has already castled at some earlier ply.
     *           Cleared bits 0x01 / 0x02 implicit once set.</td></tr>
     *   <tr><td>{@code 0x20} (=&nbsp;32)</td>
     *       <td>{@link #BIT_BLACK_HAS_CASTLED}</td>
     *       <td>Black has already castled at some earlier ply.
     *           Cleared bits 0x04 / 0x08 implicit once set.</td></tr>
     * </table>
     *
     * <p>Well-known values:
     * <ul>
     *   <li>{@code 15} ({@code 0x0F}) — initial position: all four
     *       castling rights set, neither side has castled yet.</li>
     *   <li>{@code 0} — neither side may castle and neither has
     *       castled (both kings and / or both rooks have moved).</li>
     *   <li>{@code 47} ({@code 0x2F}) — a game position where White
     *       still holds both castling rights but Black has already
     *       castled ({@code 0x0F | 0x20 = 0x2F}).</li>
     * </ul>
     *
     * <p>Do not read individual bits with hand-rolled AND masks —
     * prefer the semantic accessors {@link #isWhiteCastlingKingSidePossible()},
     * {@link #isWhiteCastlingQueenSidePossible()},
     * {@link #isBlackCastlingKingSidePossible()},
     * {@link #isBlackCastlingQueenSidePossible()},
     * {@link #hasWhiteCastled()} and {@link #hasBlackCastled()}.
     */
    private final int castlingState;
    private final long positionHash;

    /**
     * En-passant target square for this ply, encoded as a
     * {@link Board}-indexed field byte, or {@code 0} when no en-passant
     * capture is currently available.
     *
     * <p>Semantics: when the immediately preceding move was a pawn
     * double-move, this field points to the square the pawn
     * <em>skipped over</em> (not the square the pawn moved to). Any
     * opposing pawn on the same rank and adjacent file may capture
     * en-passant by moving to this square on its next ply. The field
     * is reset to {@code 0} by the very next move, since en-passant
     * rights last exactly one half-move.
     *
     * <p>Possible non-zero values are exactly the sixteen squares
     * where an en-passant target can legally sit:
     * <ul>
     *   <li>{@link Board#a3}={@value Board#a3} through
     *       {@link Board#h3}={@value Board#h3} — targets left behind
     *       by a White pawn's a2/…/h2 → a4/…/h4 double-move.</li>
     *   <li>{@link Board#a6}={@value Board#a6} through
     *       {@link Board#h6}={@value Board#h6} — targets left behind
     *       by a Black pawn's a7/…/h7 → a5/…/h5 double-move.</li>
     * </ul>
     *
     * <p>Note that the byte value stores the myChess 12x12 padded-board
     * field index, not the classical 0..63 square index. To recover the
     * file (0=a … 7=h) use {@code enPassantField % Board.LENGTH - 2};
     * to recover the rank (0=1 … 7=8) use
     * {@code enPassantField / Board.LENGTH - 2}. That file arithmetic
     * is what the Zobrist-hash update reads to look up the correct
     * en-passant-file random number.
     */
    private final byte enPassantField;

    private GameStatus() {
        this(0, TURN_WHITE, 0, 0, INITIAL_CASTLING_STATE, (byte) 0, INITIAL_POSITION_HASH);
    }

    GameStatus(int plyCount, int turn, int lastMove, int halfMoveClock, int castlingState, byte enPassantField, long positionHash) {
        this.plyCount = plyCount;
        this.turn = turn;
        this.lastMove = lastMove;
        this.halfMoveClock = halfMoveClock;
        this.castlingState = castlingState;
        this.positionHash = positionHash;
        this.enPassantField = enPassantField;
    }

    static GameStatus newGame() {
        return new GameStatus();
    }

    public int getPlyCount() {
        return plyCount;
    }

    public int getTurn() {
        return turn;
    }

    public int getHalfMoveClock() {
        return halfMoveClock;
    }

    public boolean isEndGame() {
        return plyCount > 60; // TODO: Optimize end game detection
    }

    public boolean isWhiteTurn() {
        return turn == GameStatus.TURN_WHITE;
    }

    public boolean isBlackTurn() {
        return turn == GameStatus.TURN_BLACK;
    }

    public int getOppositeColor() {
        return turn == GameStatus.TURN_WHITE ? GameStatus.TURN_BLACK : GameStatus.TURN_WHITE;
    }

    public int getLastMove() {
        return lastMove;
    }

    /**
     * The en-passant target square for the ply this snapshot represents,
     * as a {@link Board}-indexed field byte — see the
     * {@link #enPassantField} field documentation for the encoding and
     * the list of possible values. Returns {@code 0} when no en-passant
     * capture is currently available (the common case: only the
     * half-move immediately after a pawn double-move exposes this
     * value non-zero).
     *
     * @return {@code 0} or one of the sixteen legal en-passant target
     *         squares on rank 3 (White double-move aftermath) or rank
     *         6 (Black)
     */
    public byte getEnPassantField() {
        return enPassantField;
    }

    public long getPositionHash() {
        return positionHash;
    }

    /**
     * The full six-bit castling-state bitmask for this ply — see the
     * {@link #castlingState} field documentation for the bit layout and
     * for the well-known integer values (initial position = 15, no
     * rights = 0, etc.). Prefer the semantic accessors
     * ({@link #isWhiteCastlingKingSidePossible()} and friends) unless
     * you need the raw value, e.g. to XOR into a Zobrist-hash update
     * or to compare two positions for castling-rights equivalence.
     *
     * @return an integer in {@code [0, 63]} where each set bit
     *         corresponds to one of the {@code BIT_*} constants
     */
    public int getCastlingState() {
        return castlingState;
    }

    public boolean isWhiteCastlingPossible() {
        return !hasWhiteCastled() && (isWhiteCastlingKingSidePossible() || isWhiteCastlingQueenSidePossible());
    }

    public boolean isBlackCastlingPossible() {
        return !hasBlackCastled() && (isBlackCastlingKingSidePossible() || isBlackCastlingQueenSidePossible());
    }

    public boolean hasWhiteCastled() {
        return (castlingState & BIT_WHITE_HAS_CASTLED) == BIT_WHITE_HAS_CASTLED;
    }

    public boolean hasBlackCastled() {
        return (castlingState & BIT_BLACK_HAS_CASTLED) == BIT_BLACK_HAS_CASTLED;
    }

    public boolean isCastlingPossible() {
        return (turn == TURN_WHITE && (isWhiteCastlingKingSidePossible() || isWhiteCastlingQueenSidePossible()))
                || (turn == TURN_BLACK && (isBlackCastlingKingSidePossible() || isBlackCastlingQueenSidePossible()));
    }

    public boolean isWhiteCastlingKingSidePossible() {
        return (castlingState & BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE) == BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE;
    }

    public boolean isWhiteCastlingQueenSidePossible() {
        return (castlingState & BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE) == BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE;
    }

    public boolean isBlackCastlingKingSidePossible() {
        return (castlingState & BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE) == BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE;
    }

    public boolean isBlackCastlingQueenSidePossible() {
        return (castlingState & BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE) == BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE;
    }

    public GameStatus switchTurn() {
        return new GameStatus(plyCount, getOppositeColor(), 0, halfMoveClock, castlingState, (byte) 0, positionHash);
    }

    @Override
    public String toString() {
        return "turn=" + (turn == GameStatus.TURN_WHITE ? "white" : "black")
                + ", plyCount=" + plyCount
                + ", halfMoveClock=" + halfMoveClock
                + ", lastMove=" + (lastMove != 0 ? ChessUtil.moveToString(lastMove) : "none")
                + ", castlingState=" + Fen.castlingState(this)
                + ", enPassantField=" + (enPassantField != 0 ? ChessUtil.fieldToString(enPassantField) : "")
                + ", positionHash=" + positionHash;
    }
}
