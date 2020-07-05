package org.michaelfl.mychess;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class Move {

    final static byte typeNormal = 0;
    final static byte typeCastlingKingSide    = 1;
    final static byte typeCastlingQueenSide   = 2;
    final static byte typePawnPromotionQueen  = 3;
    final static byte typePawnPromotionRook   = 4;
    final static byte typePawnPromotionKnight = 5;
    final static byte typePawnPromotionBishop = 6;
    final static byte typeEnPassant = 7;

    private final int move;

    public Move(int move) {
        this.move = move;
    }

    static int create(byte fromField, byte toField, byte capturedPiece, byte moveType) {
        return BitOps.createWord(fromField, toField, capturedPiece, moveType);
    }

    static int create(int fromField, int toField, byte capturedPiece, byte moveType) {
        return BitOps.createWord((byte) fromField, (byte) toField, capturedPiece, moveType);
    }

    public int getMove() {
        return move;
    }

    public static byte getFromField(int move) {
        return BitOps.getByte0(move);
    }

    public static byte getToField(int move) {
        return BitOps.getByte1(move);
    }

    public static int getFromCol(int move) {
        int field = getFromField(move);
        return ChessUtil.getColOfField(field);
    }

    public static int getToCol(int move) {
        int field = getToField(move);
        return ChessUtil.getColOfField(field);
    }

    public static int getFromRow(int move) {
        int field = getFromField(move);
        return ChessUtil.getRowOfField(field);
    }

    public static int getToRow(int move) {
        int field = getToField(move);
        return ChessUtil.getRowOfField(field);
    }

    public static byte getCapturedPiece(int move) {
        return BitOps.getByte2(move);
    }

    public static byte getMoveType(int move) {
        return BitOps.getByte3(move);
    }

    public byte getFromField() {
        return getFromField(move);
    }

    public byte getToField() {
        return getToField(move);
    }

    public int getFromCol() {
        return getFromCol(move);
    }

    public int getToCol() {
        return getToCol(move);
    }

    public int getFromRow() {
        return getFromRow(move);
    }

    public int getToRow() {
        return getToRow(move);
    }

    public byte getCapturedPiece() {
        return getCapturedPiece(move);
    }

    public byte getMoveType() {
        return getMoveType(move);
    }

    @Override
    public String toString() {
        return ChessUtil.moveToString(move);
    }
}
