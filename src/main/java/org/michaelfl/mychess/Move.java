package org.michaelfl.mychess;

final class Move {
    private final byte fromField;
    private final byte toField;

    Move(int fromField, int toField) {
        this.fromField = (byte) fromField;
        this.toField = (byte) toField;
    }

    Move(byte fromField, byte toField) {
        this.fromField = fromField;
        this.toField = toField;
    }

    byte getFromField() {
        return fromField;
    }

    byte getToField() {
        return toField;
    }

    @Override
    public String toString() {
        return ChessUtil.moveToString(fromField, toField);
    }
}
