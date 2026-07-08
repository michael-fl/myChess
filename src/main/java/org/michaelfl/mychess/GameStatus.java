package org.michaelfl.mychess;

/**
 * Immutable per-ply state snapshot owned by {@link Board}: side to move,
 * castling-rights bitmask, en-passant target square, half-move clock, last
 * move, Zobrist position hash, and cumulative non-pawn material per side.
 * Pushed before every {@code makeMove} and popped on {@code revertMove}.
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

    /**
     * Zero-filled sentinel {@code [0, 0]} used by callers that build a
     * throwaway {@code GameStatus} solely to feed
     * {@link Board#calculatePositionHash(byte[], GameStatus)} — the hash
     * calculator does not read the material fields, so seeding them with
     * the correct values would be wasted work. The two live users are
     * {@link Fen} and {@link PositionEncoding}, both of which discard the
     * temporary status and build a second one with the actual material
     * (via {@link Board#calculateNonPawnMaterialWeights(byte[])}) before
     * constructing the {@link Board}.
     *
     * <p>Safe to share across many temporary statuses: the
     * {@link #GameStatus GameStatus} constructor copies element-wise into
     * its own array, so no caller can mutate this static through a
     * constructed status.
     */
    static final int[] EMPTY_NON_PAWN_MATERIAL_WEIGHT = new int[2];

    /**
     * Per-side non-pawn material at the start of a fresh game:
     * {@code 2R + 2N + 2B + 1Q = 3100} centipawns. Identical across
     * standard chess and every Chess960 setup, since all 960 starting
     * positions share the same piece set (just permuted on the back
     * rank).
     */
    private static final int INITIAL_NON_PAWN_MATERIAL_WEIGHT =
            WeightingFunction.weightOfPiece[Board.whiteRook] * 2
                    + WeightingFunction.weightOfPiece[Board.whiteKnight] * 2
                    + WeightingFunction.weightOfPiece[Board.whiteBishop] * 2
                    + WeightingFunction.weightOfPiece[Board.whiteQueen];

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

    /**
     * Cumulative non-pawn material per side in centipawns.
     * {@code nonPawnMaterialWeight[0]} is White, {@code [1]} is Black.
     * Maintained incrementally by
     * {@link #switchTurn(int, int, int, byte, long)}: non-pawn captures
     * subtract from the loser's slot, pawn promotions add the promoted
     * piece's weight to the promoter's slot. Kings weigh 0 and pawns
     * are excluded, so the initial value is 3100cp per side
     * ({@code 2R + 2N + 2B + 1Q}) for both standard chess and every
     * Chess960 setup.
     *
     * <p>Used for allocation-free non-pawn-material lookups on the hot
     * search path (Q-search filtering, endgame detection, the
     * zugzwang guard on null-move pruning). For a from-scratch
     * recomputation over the raw board see
     * {@link Board#calculateNonPawnMaterialWeights(byte[])}.
     */
    private final int[] nonPawnMaterialWeight = new int[2];

    private GameStatus() {
        this(0, TURN_WHITE, 0, 0, INITIAL_CASTLING_STATE, (byte) 0,
                INITIAL_POSITION_HASH, new int[] { INITIAL_NON_PAWN_MATERIAL_WEIGHT, INITIAL_NON_PAWN_MATERIAL_WEIGHT });
    }

    GameStatus(int plyCount, int turn, int lastMove, int halfMoveClock, int castlingState, byte enPassantField,
               long positionHash, int[] nonPawnMaterialWeight) {
        this.plyCount = plyCount;
        this.turn = turn;
        this.lastMove = lastMove;
        this.halfMoveClock = halfMoveClock;
        this.castlingState = castlingState;
        this.positionHash = positionHash;
        this.enPassantField = enPassantField;
        this.nonPawnMaterialWeight[0] = nonPawnMaterialWeight[0];
        this.nonPawnMaterialWeight[1] = nonPawnMaterialWeight[1];
    }

    static GameStatus newGame() {
        return new GameStatus();
    }

    /**
     * Build the {@code GameStatus} that follows this one after a move.
     * Advances {@code plyCount} by one and flips the side to move; sets
     * {@code lastMove}, {@code halfMoveClock}, {@code castlingState},
     * {@code enPassantField} and {@code positionHash} to the values the
     * caller has already computed. The reason this method exists rather
     * than a raw constructor is the incremental update of
     * {@link #nonPawnMaterialWeight} from the packed move:
     * <ul>
     *   <li>Non-pawn capture: subtract the captured piece's centipawn
     *       weight from the loser's slot.</li>
     *   <li>Pawn promotion (with or without capture): add the promoted
     *       piece's centipawn weight to the promoter's slot. The
     *       consumed pawn was never counted, so this simple add is
     *       correct.</li>
     *   <li>Everything else — quiet move, pawn move, pawn capture,
     *       en-passant, castling — leaves both sides untouched. The new
     *       status shares the parent's {@code nonPawnMaterialWeight}
     *       array reference; the constructor copies element-wise into
     *       its own array, so the aliasing is safe.</li>
     * </ul>
     *
     * <p>Null-move handling: when {@code lastMove == 0} the whole
     * material-update branch is skipped and the parent material is
     * carried over unchanged. Callers (see {@link Board#makeNullMove()})
     * are still responsible for passing the null-move-appropriate
     * {@code halfMoveClock} (typically 0) and {@code enPassantField}
     * (typically 0) themselves.
     *
     * @param lastMove       packed move that just completed, or {@code 0}
     *                       for a null move
     * @param halfMoveClock  new half-move clock (caller-computed)
     * @param castlingState  new castling-rights bitmask
     * @param enPassantField new en-passant target square, or {@code 0} if none
     * @param positionHash   Zobrist hash of the resulting position
     * @return the new immutable {@code GameStatus} to push onto the
     *         Board's status stack
     */
    GameStatus switchTurn(int lastMove, int halfMoveClock, int castlingState, byte enPassantField, long positionHash) {
        int[] newNonPawnMaterialWeight = this.nonPawnMaterialWeight;
        if (lastMove != 0) {
            final byte capturedPiece = Move.getCapturedPiece(lastMove);
            final boolean pawnCaptured = Board.isPawn(capturedPiece);
            final int nonPawnCaptureWeight = capturedPiece == 0 || pawnCaptured ? 0 : WeightingFunction.weightOfPiece[capturedPiece];
            final int myNonPawnMaterialGain = getNonPawnMaterialGain(lastMove);

            if (nonPawnCaptureWeight > 0 || myNonPawnMaterialGain > 0) {
                if (turn == GameStatus.TURN_WHITE) {
                    newNonPawnMaterialWeight = new int[]{nonPawnMaterialWeight[0] + myNonPawnMaterialGain, nonPawnMaterialWeight[1] - nonPawnCaptureWeight};
                } else {
                    newNonPawnMaterialWeight = new int[]{nonPawnMaterialWeight[0] - nonPawnCaptureWeight, nonPawnMaterialWeight[1] + myNonPawnMaterialGain};
                }
            }
        }

        return new GameStatus(getPlyCount() + 1, getOppositeColor(), lastMove,
                halfMoveClock, castlingState, enPassantField, positionHash, newNonPawnMaterialWeight);
    }

    /**
     * Centipawn non-pawn material gain from {@code move} for the side
     * that just moved. Non-zero only for promotions — the promoted
     * piece's weight, since the consumed pawn was never in the non-pawn
     * total. Non-promotion moves return 0: captures do not shift
     * material to the capturer's side (the captured piece is accounted
     * for by subtracting from the loser in
     * {@link #switchTurn(int, int, int, byte, long)}).
     */
    private static int getNonPawnMaterialGain(int move) {
        if (move == 0) {
            return 0;
        }

        final byte moveType = Move.getMoveType(move);
        return switch (moveType) {
            case Move.typeNormal -> //noinspection DuplicateBranchesInSwitch
                    0; // opt for most likely case
            case Move.typePawnPromotionQueen -> WeightingFunction.weightOfPiece[Board.whiteQueen];
            case Move.typePawnPromotionKnight -> WeightingFunction.weightOfPiece[Board.whiteKnight];
            case Move.typePawnPromotionRook -> WeightingFunction.weightOfPiece[Board.whiteRook];
            case Move.typePawnPromotionBishop -> WeightingFunction.weightOfPiece[Board.whiteBishop];
            default -> 0;
        };
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
     * White's cumulative non-pawn material in centipawns
     * ({@code 2R + 2N + 2B + 1Q = 3100} at game start, minus what
     * White has lost, plus what White has gained by promotion). See
     * {@link #nonPawnMaterialWeight} for the tracking semantics.
     */
    public int getWhiteNonPawnMaterialWeight() {
        return nonPawnMaterialWeight[0];
    }

    /**
     * Black's cumulative non-pawn material in centipawns. See
     * {@link #getWhiteNonPawnMaterialWeight()} for the semantics.
     */
    public int getBlackNonPawnMaterialWeight() {
        return nonPawnMaterialWeight[1];
    }

    /** Has the side to move at least 1 non-pawn piece (king excluded)? */
    public boolean hasNonPawnMaterial() {
        final int index = getTurn() == TURN_WHITE ? 0 : 1;
        return nonPawnMaterialWeight[index] > 0;
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
