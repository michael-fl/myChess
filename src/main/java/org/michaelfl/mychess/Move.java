package org.michaelfl.mychess;

@SuppressWarnings({"WeakerAccess", "unused"})
final class Move {

    final static byte typeNormal = 0;
    final static byte typeCastlingKingSide    = 1;
    final static byte typeCastlingQueenSide   = 2;
    final static byte typePawnPromotionQueen  = 3;
    final static byte typePawnPromotionRook   = 4;
    final static byte typePawnPromotionKnight = 5;
    final static byte typePawnPromotionBishop = 6;

    private final int move;

    Move(int move) {
        this.move = move;
    }

    static int create(byte fromField, byte toField, byte capturedPiece, byte moveType) {
        return BitOps.createWord(fromField, toField, capturedPiece, moveType);
    }

    int getMove() {
        return move;
    }

    static byte getFromField(int move) {
        return BitOps.getByte0(move);
    }

    static byte getToField(int move) {
        return BitOps.getByte1(move);
    }

    static int getFromCol(int move) {
        int field = getFromField(move);
        return ChessUtil.getColOfField(field);
    }

    static int getToCol(int move) {
        int field = getToField(move);
        return ChessUtil.getColOfField(field);
    }

    static int getFromRow(int move) {
        int field = getFromField(move);
        return ChessUtil.getRowOfField(field);
    }

    static int getToRow(int move) {
        int field = getToField(move);
        return ChessUtil.getRowOfField(field);
    }

    static byte getCapturedPiece(int move) {
        return BitOps.getByte2(move);
    }

    static byte getMoveType(int move) {
        return BitOps.getByte3(move);
    }

    byte getFromField() {
        return getFromField(move);
    }

    byte getToField() {
        return getToField(move);
    }

    int getFromCol() {
        return getFromCol(move);
    }

    int getToCol() {
        return getToCol(move);
    }

    int getFromRow() {
        return getFromRow(move);
    }

    int getToRow() {
        return getToRow(move);
    }

    byte getCapturedPiece() {
        return getCapturedPiece(move);
    }

    byte getMoveType() {
        return getMoveType(move);
    }

    @Override
    public String toString() {
        return ChessUtil.moveToString(move);
    }
}
