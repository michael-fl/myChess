package org.michaelfl.mychess;

final class MoveDescription {

    final static MoveDescription whiteCastlingKingSide = new MoveDescription(4, 0, 6, 0);
    final static MoveDescription whiteCastlingQueenSide = new MoveDescription(4, 0, 2, 0);
    final static MoveDescription blackCastlingKingSide = new MoveDescription(4, 7, 6, 7);
    final static MoveDescription blackCastlingQueenSide = new MoveDescription(4, 7, 2, 7);

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
