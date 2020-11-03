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

    static MoveDescription fromString(String moveString, int turn) {
        boolean isWhiteTurn = turn == GameStatus.TURN_WHITE;
        MoveDescription move;

        if ("O-O".equals(moveString) || "0-0".equals(moveString) || "OO".equals(moveString) || "00".equals(moveString)) {
            move = isWhiteTurn ? MoveDescription.whiteCastlingKingSide : MoveDescription.blackCastlingKingSide;
        } else if ("O-O-O".equals(moveString) || "0-0-0".equals(moveString) || "OOO".equals(moveString) || "000".equals(moveString)) {
            move = isWhiteTurn ? MoveDescription.whiteCastlingQueenSide : MoveDescription.blackCastlingQueenSide;
        } else {
            int[] from = ChessUtil.getColAndRowFromString(moveString.substring(0, 2));
            int offset = moveString.charAt(2) == '-' ? 1 : 0;
            int[] to = ChessUtil.getColAndRowFromString(moveString.substring(2 + offset, 4 + offset));

            char pawnPromotionSymbol = moveString.length() > 4 + offset ? Character.toUpperCase(moveString.charAt(4 + offset)) : 0;

            move = new MoveDescription(from[0], from[1], to[0], to[1], pawnPromotionSymbol);
        }

        return move;
    }
}
