package org.michaelfl.mychess;

public final class GameStatus {

    private final static float HANDICAP = 0.6f;

    public final static int BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE = 1;
    public final static int BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE = 2;
    public final static int BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE = 4;
    public final static int BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE = 8;
    public final static int BIT_WHITE_HAS_CASTLED = 16;
    public final static int BIT_BLACK_HAS_CASTLED = 32;

    private final static byte INITIAL_CASTLING_STATE = 15;
    private final static long INITIAL_POSITION_HASH = -8376097377325274526L;

    public final static int TURN_WHITE = 8;
    @SuppressWarnings("WeakerAccess")
    public final static int TURN_BLACK = 16;

    private final int plyCount;
    private final int turn;
    private final int lastMove;
    private final int halfMoveClock;
    private final int castlingState;
    private final long positionHash;
    private final byte enPassantField;
    private final float handicapWhite;
    private final float handicapBlack;

    private GameStatus() {
        this(0, TURN_WHITE, 0, 0, INITIAL_CASTLING_STATE, (byte) 0, INITIAL_POSITION_HASH, HANDICAP, HANDICAP);
    }

    GameStatus(int plyCount, int turn, int lastMove, int halfMoveClock, int castlingState, byte enPassantField, long positionHash, float handicapWhite, float handicapBlack) {
        this.plyCount = plyCount;
        this.turn = turn;
        this.lastMove = lastMove;
        this.halfMoveClock = halfMoveClock;
        this.castlingState = castlingState;
        this.positionHash = positionHash;
        this.enPassantField = enPassantField;
        this.handicapWhite = handicapWhite;
        this.handicapBlack = handicapBlack;
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

    public float getHandicapWhite() {
        return handicapWhite;
    }

    public float getHandicapBlack() {
        return handicapBlack;
    }

    public float getHandicap() {
        return turn == GameStatus.TURN_WHITE ? handicapWhite : handicapBlack;
    }

    public int getCastlingState() {
        return castlingState;
    }

    public final boolean isWhiteCastlingPossible() {
        return !hasWhiteCastled() && (isWhiteCastlingKingSidePossible() || isWhiteCastlingQueenSidePossible());
    }

    public final boolean isBlackCastlingPossible() {
        return !hasBlackCastled() && (isBlackCastlingKingSidePossible() || isBlackCastlingQueenSidePossible());
    }

    public final boolean hasWhiteCastled() {
        return (castlingState & BIT_WHITE_HAS_CASTLED) == BIT_WHITE_HAS_CASTLED;
    }

    public final boolean hasBlackCastled() {
        return (castlingState & BIT_BLACK_HAS_CASTLED) == BIT_BLACK_HAS_CASTLED;
    }

    public final boolean isCastlingPossible() {
        return (turn == TURN_WHITE && (isWhiteCastlingKingSidePossible() || isWhiteCastlingQueenSidePossible()))
                || (turn == TURN_BLACK && (isBlackCastlingKingSidePossible() || isBlackCastlingQueenSidePossible()));
    }

    public final boolean isWhiteCastlingKingSidePossible() {
        return (castlingState & BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE) == BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE;
    }

    public final boolean isWhiteCastlingQueenSidePossible() {
        return (castlingState & BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE) == BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE;
    }

    public final boolean isBlackCastlingKingSidePossible() {
        return (castlingState & BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE) == BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE;
    }

    public final boolean isBlackCastlingQueenSidePossible() {
        return (castlingState & BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE) == BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE;
    }

    public GameStatus switchTurn() {
        return new GameStatus(plyCount, getOppositeColor(), 0, halfMoveClock, castlingState, (byte) 0, positionHash, handicapWhite, handicapBlack);
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
