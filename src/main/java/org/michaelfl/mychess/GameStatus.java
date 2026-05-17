package org.michaelfl.mychess;

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
    private final int castlingState;
    private final long positionHash;
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

    public byte getEnPassantField() {
        return enPassantField;
    }

    public long getPositionHash() {
        return positionHash;
    }

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
