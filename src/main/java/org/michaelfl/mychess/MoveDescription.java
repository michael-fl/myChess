package org.michaelfl.mychess;

final class MoveDescription {
    private final int fromCol;
    private final int fromRow;
    private final int toCol;
    private final int toRow;
    private final char pawnPromotionSymbol;


    MoveDescription(int fromCol, int fromRow, int toCol, int toRow) {
        this(fromCol, fromRow, toCol, toRow, (char) 0);
    }

    MoveDescription(int fromCol, int fromRow, int toCol, int toRow, char pawnPromotionSymbol) {
        this.fromCol = fromCol;
        this.fromRow = fromRow;
        this.toCol = toCol;
        this.toRow = toRow;
        this.pawnPromotionSymbol = pawnPromotionSymbol;
    }

    int getFromField() {
        return ChessUtil.colAndRowToField(fromCol, fromRow);
    }

    int getToField() {
        return ChessUtil.colAndRowToField(toCol, toRow);
    }

    char getPawnPromotionSymbol() {
        return pawnPromotionSymbol;
    }
}
