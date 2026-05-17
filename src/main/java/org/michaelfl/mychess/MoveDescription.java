package org.michaelfl.mychess;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"java:S5843"})
public final class MoveDescription {

    private static final Pattern MOVE_PATTERN = Pattern.compile("^([PNBRQK])?([a-h])?([1-8])?([-x])?([a-h])([1-8])(=?[NBRQ])?(\\+|#|\\+\\+)?( ?e\\.p\\.)?(!|!!|!\\?|\\?!|\\?|\\?\\?)?$");
    private static final Pattern CASTLING_PATTERN = Pattern.compile("^(0-0|O-O|0-0-0|O-O-O)(\\+|#|\\+\\+)?(!|!!|!\\?|\\?!|\\?|\\?\\?)?$");
    private static final int GROUP_PIECE = 1;
    private static final int GROUP_SRC_COL = 2;
    private static final int GROUP_SRC_ROW = 3;
    private static final int GROUP_SEPARATOR = 4;
    private static final int GROUP_TARGET_COL = 5;
    private static final int GROUP_TARGET_ROW = 6;
    private static final int GROUP_PROMOTION = 7;
    private static final int GROUP_CHESS = 8;
    private static final int GROUP_EN_PASSSANT = 9;
    private static final int GROUP_CASTLING = 1;
    private static final int GROUP_CASTLING_CHESS = 2;

    public final int turn;
    public final byte piece;
    public final int fromCol;
    public final int fromRow;
    public final int toCol;
    public final int toRow;
    public final byte pawnPromotionPiece;
    public final Boolean isCapture;
    public final Boolean isCheck;
    public final Boolean isCheckmate;
    public final Boolean isEnPassant;
    public final Boolean isCastlingKingSide;
    public final Boolean isCastlingQueenSide;


    MoveDescription(int turn, int fromCol, int fromRow, int toCol, int toRow, char pawnPromotionSymbol) {
        this.turn = turn;
        this.fromCol = fromCol;
        this.fromRow = fromRow;
        this.toCol = toCol;
        this.toRow = toRow;
        this.pawnPromotionPiece = pawnPromotionSymbol == 0 ? 0 : ChessUtil.symbolToPiece(pawnPromotionSymbol, turn);
        this.piece = 0;
        this.isCapture = null;
        this.isCheck = null;
        this.isCheckmate = null;
        this.isEnPassant = null;
        this.isCastlingKingSide = null;
        this.isCastlingQueenSide = null;
    }

    MoveDescription(
        int turn,
        byte piece,
        int fromCol,
        int fromRow,
        int toCol,
        int toRow,
        byte pawnPromotionPiece,
        Boolean isCapture,
        Boolean isCheck,
        Boolean isCheckmate,
        Boolean isEnPassant,
        Boolean isCastlingKingSide,
        Boolean isCastlingQueenSide
    ) {
        if (turn <= 0) {
            throw new IllegalArgumentException("turn not set");
        }
        if (toCol < 0) {
            throw new IllegalArgumentException("toCol not set");
        }
        if (toRow < 0) {
            throw new IllegalArgumentException("toRow not set");
        }
        if (piece <= 0 && (fromCol < 0 || fromRow < 0)) {
            throw new IllegalArgumentException("Source field must be set if no piece is defined");
        }

        this.turn = turn;
        this.piece = piece;
        this.fromCol = fromCol;
        this.fromRow = fromRow;
        this.toCol = toCol;
        this.toRow = toRow;
        this.pawnPromotionPiece = pawnPromotionPiece;
        this.isCapture = isCapture;
        this.isCheck = isCheck;
        this.isCheckmate = isCheckmate;
        this.isEnPassant = isEnPassant;
        this.isCastlingKingSide = isCastlingKingSide;
        this.isCastlingQueenSide = isCastlingQueenSide;
    }

    public int getFromField() {
        if (fromCol < 0 || fromRow < 0) {
            throw new IllegalStateException("from field not defined");
        }

        return ChessUtil.colAndRowToField(fromCol, fromRow);
    }

    public int getToField() {
        return ChessUtil.colAndRowToField(toCol, toRow);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MoveDescription that = (MoveDescription) o;
        return turn == that.turn && piece == that.piece
                && fromCol == that.fromCol && fromRow == that.fromRow
                && toCol == that.toCol && toRow == that.toRow
                && pawnPromotionPiece == that.pawnPromotionPiece
                && Objects.equals(isCapture, that.isCapture)
                && Objects.equals(isCheck, that.isCheck)
                && Objects.equals(isCheckmate, that.isCheckmate)
                && Objects.equals(isEnPassant, that.isEnPassant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(turn, piece, fromCol, fromRow, toCol, toRow);
    }

    public static MoveDescription fromString(String moveString, int turn) {
        if (moveString.isEmpty()) {
            throw new IllegalArgumentException("Empty move notation");
        }

        boolean isWhiteTurn = turn == GameStatus.TURN_WHITE;
        Builder builder = new Builder(turn);

        if (moveString.charAt(0) == 'O' || moveString.charAt(0) == '0') {
            var matcher = CASTLING_PATTERN.matcher(moveString);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Wrong move notation: " + moveString);
            }

            var castling = matcher.group(GROUP_CASTLING);
            if ("0-0".equals(castling) || "O-O".equals(castling)) {
                builder.isCastlingKingSide = true;
                if (isWhiteTurn) {
                    builder.piece = Board.whiteKing;
                    builder.fromCol = 4;
                    builder.fromRow = 0;
                    builder.toCol = 6;
                    builder.toRow = 0;
                } else {
                    builder.piece = Board.blackKing;
                    builder.fromCol = 4;
                    builder.fromRow = 7;
                    builder.toCol = 6;
                    builder.toRow = 7;
                }
            } else { // O-O-O
                builder.isCastlingQueenSide = true;
                if (isWhiteTurn) {
                    builder.piece = Board.whiteKing;
                    builder.fromCol = 4;
                    builder.fromRow = 0;
                    builder.toCol = 2;
                    builder.toRow = 0;
                } else {
                    builder.piece = Board.blackKing;
                    builder.fromCol = 4;
                    builder.fromRow = 7;
                    builder.toCol = 2;
                    builder.toRow = 7;
                }
            }

            // Chess/checkmate symbol
            parseChessOrCheckmateSymbol(GROUP_CASTLING_CHESS, matcher, builder);

        } else {
            var matcher = MOVE_PATTERN.matcher(moveString);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Wrong move notation: " + moveString);
            }

            // Symbol
            var symbol = matcher.group(GROUP_PIECE);
            if (symbol != null) {
                builder.piece = ChessUtil.symbolToPiece(symbol.charAt(0), turn);
            } else {
                builder.piece = isWhiteTurn ? Board.whitePawn : Board.blackPawn;
            }

            // Source field
            var srcCol = matcher.group(GROUP_SRC_COL);
            if (srcCol != null) {
                builder.fromCol = srcCol.charAt(0) - 'a';
            }
            var srcRow = matcher.group(GROUP_SRC_ROW);
            if (srcRow != null) {
                builder.fromRow = Integer.parseInt(srcRow) - 1;
            }

            // Separator
            var sep = matcher.group(GROUP_SEPARATOR);
            if (sep != null && sep.charAt(0) == 'x') {
                builder.isCapture = true;
            }

            // Target field
            var targetCol = matcher.group(GROUP_TARGET_COL);
            if (targetCol != null) {
                builder.toCol = targetCol.charAt(0) - 'a';
            }
            var targetRow = matcher.group(GROUP_TARGET_ROW);
            if (targetRow != null) {
                builder.toRow = Integer.parseInt(targetRow) - 1;
            }

            // Pawn promotion symbol
            symbol = matcher.group(GROUP_PROMOTION);
            if (symbol != null) {
                if (symbol.startsWith("=")) {
                    symbol = symbol.substring(1);
                }
                builder.pawnPromotionPiece = ChessUtil.symbolToPiece(symbol.charAt(0), turn);
            }

            // Chess/checkmate symbol
            parseChessOrCheckmateSymbol(GROUP_CHESS, matcher, builder);

            // En passant
            var enPassant = matcher.group(GROUP_EN_PASSSANT);
            if (enPassant != null) {
                builder.isEnPassant = true;
            }
        }

        return builder.build();
    }

    private static void parseChessOrCheckmateSymbol(int group, Matcher matcher, Builder builder) {
        var symbol = matcher.group(group);
        if (symbol != null) {
            if ("+".equals(symbol) || "++".equals(symbol)) {
                builder.isCheck = true;
            } else if ("#".equals(symbol)) {
                builder.isCheckmate = true;
            }
        }
    }

    @Override
    public String toString() {
        if (isCastlingKingSide != null && isCastlingKingSide) {
            return "0-0";
        }

        if (isCastlingQueenSide != null && isCastlingQueenSide) {
            return "0-0-0";
        }

        return (piece != Board.whitePawn && piece != Board.blackPawn ? ChessUtil.pieceToString(piece) : "")
                + (fromCol >= 0 ? (char) ('a' + fromCol) : "")
                + (fromRow >= 0 ? fromRow + 1 : "")
                + (isCapture != null && isCapture ? "x" : "")
                + ChessUtil.fieldToString(getToField())
                + (pawnPromotionPiece > 0 ? ChessUtil.pieceToString(pawnPromotionPiece) : "")
                + (isCheckmate != null && isCheckmate ? "#" : (isCheck != null && isCheck ? "+" : ""));
    }

    static final class Builder {
        final int turn;
        byte piece = -1;
        int fromCol = -1;
        int fromRow = -1;
        int toCol = -1;
        int toRow = -1;
        byte pawnPromotionPiece = -1;
        Boolean isCapture;
        Boolean isCheck;
        Boolean isCheckmate;
        Boolean isEnPassant;
        Boolean isCastlingKingSide;
        Boolean isCastlingQueenSide;

        Builder(int turn) {
            this.turn = turn;
        }

        Builder(MoveDescription moveDescr) {
            this.turn = moveDescr.turn;
            this.piece = moveDescr.piece;
            this.fromCol = moveDescr.fromCol;
            this.fromRow = moveDescr.fromRow;
            this.toCol = moveDescr.toCol;
            this.toRow = moveDescr.toRow;
            this.pawnPromotionPiece = moveDescr.pawnPromotionPiece;
            this.isCapture = moveDescr.isCapture;
            this.isCheck = moveDescr.isCheck;
            this.isCheckmate = moveDescr.isCheckmate;
            this.isEnPassant = moveDescr.isEnPassant;
            this.isCastlingKingSide = moveDescr.isCastlingKingSide;
            this.isCastlingQueenSide = moveDescr.isCastlingQueenSide;
        }

        MoveDescription build() {
            return new MoveDescription(turn, piece, fromCol, fromRow, toCol, toRow, pawnPromotionPiece, isCapture, isCheck, isCheckmate, isEnPassant, isCastlingKingSide, isCastlingQueenSide);
        }
    }
}
