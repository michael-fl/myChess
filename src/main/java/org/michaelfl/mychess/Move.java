package org.michaelfl.mychess;

import org.jspecify.annotations.NonNull;

@SuppressWarnings({"WeakerAccess", "unused", "java:S115"})
public record Move(int move) {

    public static final byte typeNormal = 0;
    public static final byte typeCastlingKingSide = 1;
    public static final byte typeCastlingQueenSide = 2;
    public static final byte typePawnPromotionQueen = 3;
    public static final byte typePawnPromotionRook = 4;
    public static final byte typePawnPromotionKnight = 5;
    public static final byte typePawnPromotionBishop = 6;
    public static final byte typeEnPassant = 7;

    static int create(byte fromField, byte toField, byte capturedPiece, byte moveType) {
        return BitOps.createWord(fromField, toField, capturedPiece, moveType);
    }

    static int create(int fromField, int toField, byte capturedPiece, byte moveType) {
        return BitOps.createWord((byte) fromField, (byte) toField, capturedPiece, moveType);
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

    public byte getPawnPromotionPiece() {
        return switch (getMoveType()) {
            case Move.typePawnPromotionQueen -> getToRow() == 7 ? Board.whiteQueen : Board.blackQueen;
            case Move.typePawnPromotionRook -> getToRow() == 7 ? Board.whiteRook : Board.blackRook;
            case Move.typePawnPromotionKnight -> getToRow() == 7 ? Board.whiteKnight : Board.blackKnight;
            case Move.typePawnPromotionBishop -> getToRow() == 7 ? Board.whiteBishop : Board.blackBishop;
            default -> 0;
        };

    }

    @Override
    public @NonNull String toString() {
        return ChessUtil.moveToString(move);
    }
}
