package org.michaelfl.mychess;

import java.util.Arrays;

public final class Board {

    final static byte illegal = 64;
    final static byte empty = 0;
    final static byte whitePawn = 8;
    final static byte whiteKnight = 9;
    final static byte whiteBishop = 10;
    final static byte whiteRook = 11;
    final static byte whiteQueen = 12;
    final static byte whiteKing = 13;
    final static byte blackPawn = 16;
    final static byte blackKnight = 17;
    final static byte blackBishop = 18;
    final static byte blackRook = 19;
    final static byte blackQueen = 20;
    final static byte blackKing = 21;

    final static int LENGTH = 12;

    final static int a1 = 2 * LENGTH + 2 + 0;
    final static int b1 = 2 * LENGTH + 2 + 1;
    final static int c1 = 2 * LENGTH + 2 + 2;
    final static int d1 = 2 * LENGTH + 2 + 3;
    final static int e1 = 2 * LENGTH + 2 + 4;
    final static int f1 = 2 * LENGTH + 2 + 5;
    final static int g1 = 2 * LENGTH + 2 + 6;
    final static int h1 = 2 * LENGTH + 2 + 7;
    final static int a8 = 9 * LENGTH + 2 + 0;
    final static int b8 = 9 * LENGTH + 2 + 1;
    final static int c8 = 9 * LENGTH + 2 + 2;
    final static int d8 = 9 * LENGTH + 2 + 3;
    final static int e8 = 9 * LENGTH + 2 + 4;
    final static int f8 = 9 * LENGTH + 2 + 5;
    final static int g8 = 9 * LENGTH + 2 + 6;
    final static int h8 = 9 * LENGTH + 2 + 7;

    private final static char[] printSymbols = new char[22];
    static {
        Arrays.fill(printSymbols, '.');
        printSymbols[whitePawn] = '\u2659';
        printSymbols[whiteKnight] = '\u2658';
        printSymbols[whiteBishop] = '\u2657';
        printSymbols[whiteRook] = '\u2656';
        printSymbols[whiteQueen] = '\u2655';
        printSymbols[whiteKing] = '\u2654';
        printSymbols[blackPawn] = '\u265F';
        printSymbols[blackKnight] = '\u265E';
        printSymbols[blackBishop] = '\u265D';
        printSymbols[blackRook] = '\u265C';
        printSymbols[blackQueen] = '\u265B';
        printSymbols[blackKing] = '\u265A';
    }

    private final static int ROW1 = 26;
    private final static int ROW2 = 38;
    private final static int ROW7 = 98;
    private final static int ROW8 = 110;

    private final byte[] board;

    //    132           ...             143
    //    120           ...             131
    //    84    110(a8) ... 117(h8) 118 119
    //           98(a7) ... 105(h7)
    //           86(a6) ...  93(h6)
    //           74(a5) ...  81(h5)
    //           62(a4) ...  69(h4)
    //           50(a3) ...  57(h3)
    //           38(a2) ...  45(h2)  46  47
    //    24 25  26(a1) ...  33(h1)  34  35
    //    12 13         ...          22  23
    //    00 01         ...          10  11
    @SuppressWarnings("PointlessArithmeticExpression")
    Board() {
        board = new byte[LENGTH*LENGTH];
        Arrays.fill(board, illegal);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                board[ChessUtil.getFieldFromColAndRow(col, row)] = empty;
            }
        }

        board[ROW1 + 0] = whiteRook;
        board[ROW1 + 1] = whiteKnight;
        board[ROW1 + 2] = whiteBishop;
        board[ROW1 + 3] = whiteQueen;
        board[ROW1 + 4] = whiteKing;
        board[ROW1 + 5] = whiteBishop;
        board[ROW1 + 6] = whiteKnight;
        board[ROW1 + 7] = whiteRook;

        board[ROW2 + 0] = whitePawn;
        board[ROW2 + 1] = whitePawn;
        board[ROW2 + 2] = whitePawn;
        board[ROW2 + 3] = whitePawn;
        board[ROW2 + 4] = whitePawn;
        board[ROW2 + 5] = whitePawn;
        board[ROW2 + 6] = whitePawn;
        board[ROW2 + 7] = whitePawn;

        board[ROW7 + 0] = blackPawn;
        board[ROW7 + 1] = blackPawn;
        board[ROW7 + 2] = blackPawn;
        board[ROW7 + 3] = blackPawn;
        board[ROW7 + 4] = blackPawn;
        board[ROW7 + 5] = blackPawn;
        board[ROW7 + 6] = blackPawn;
        board[ROW7 + 7] = blackPawn;

        board[ROW8 + 0] = blackRook;
        board[ROW8 + 1] = blackKnight;
        board[ROW8 + 2] = blackBishop;
        board[ROW8 + 3] = blackQueen;
        board[ROW8 + 4] = blackKing;
        board[ROW8 + 5] = blackBishop;
        board[ROW8 + 6] = blackKnight;
        board[ROW8 + 7] = blackRook;
    }

    Board(byte[] board) {
        this.board = board;
    }

    static Board createEmptyBoard() {
        return new Board(new byte[LENGTH*LENGTH]);
    }

    Board copy() {
        return new Board(Arrays.copyOf(board, board.length));
    }

    byte[] getRawBoard() {
        return board;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();

        for (int row = 7; row >= 0; row--) {
            buf.append(row + 1).append("|");

            for (int col = 0; col <= 7; col++) {
                int index = ChessUtil.getFieldFromColAndRow(col, row);
                byte field = board[index];
                buf.append(toPrintSymbol(field));
                buf.append(' ');
            }
            buf.append('\n');
        }

        buf.append("  ---------------\n");
        buf.append("  a b c d e f g h");

        return buf.toString();
    }

    void print() {
        System.out.println(this);
    }

    private char toPrintSymbol(byte piece) {
        return printSymbols[piece];
    }

    byte get(int field) {
        return board[field];
    }

    void makeMove(int fromField, int toField) {
        byte piece = board[fromField];
        board[fromField] = empty;
        board[toField] = piece;

        // Check castling
        if (isKing(piece))
            checkCastlingMove(fromField, toField, piece);
    }

    private void checkCastlingMove(int fromField, int toField, byte piece) {
        if (piece == whiteKing && fromField == e1 && toField == g1) {
            board[h1] = empty;
            board[f1] = whiteRook;
        } else if (piece == whiteKing && fromField == e1 && toField == c1) {
            board[a1] = empty;
            board[d1] = whiteRook;
        } else if (piece == blackKing && fromField == e8 && toField == g8) {
            board[h8] = empty;
            board[f8] = blackRook;
        } else if (piece == blackKing && fromField == e8 && toField == c8) {
            board[a8] = empty;
            board[d8] = blackRook;
        }
    }

    void makePawnPromotionMove(int fromField, int toField, byte promoteToPiece) {
        board[fromField] = empty;
        board[toField] = promoteToPiece;
    }

    void validateMove(int fromField, int toField) {
        byte piece = board[fromField];
        if (piece == empty || piece == illegal)
            throw new IllegalStateException("Illegal move: " + ChessUtil.moveToString(fromField, toField));
        byte targetField = board[toField];
        if (targetField == illegal)
            throw new IllegalStateException("Illegal move: " + ChessUtil.moveToString(fromField, toField));
    }

    private static boolean isKing(byte piece) {
        return piece == whiteKing || piece == blackKing;
    }

    boolean isDrawByMaterial() {
        int countPieces = 0;
        for (byte field : board) {
            if (field != empty && field != illegal)
                countPieces++;
            if (countPieces > 6)
                return false;
        }

        return checkDrawByMaterial();
    }

    private boolean checkDrawByMaterial() {
        byte[] piecesCount = new byte[blackKing + 1];

        for (byte field : board) {
            if (field != empty && field != illegal)
                piecesCount[field]++;
        }

        return piecesCount[whitePawn] == 0
                && piecesCount[blackPawn] == 0
                && piecesCount[whiteRook] == 0
                && piecesCount[blackRook] == 0
                && piecesCount[whiteQueen] == 0
                && piecesCount[blackQueen] == 0
                && piecesCount[whiteBishop] < 2
                && piecesCount[blackBishop] < 2
                && piecesCount[whiteKnight] < 3
                && piecesCount[blackKnight] < 3
                && (piecesCount[whiteKnight] == 0 || piecesCount[whiteBishop] == 0)
                && (piecesCount[blackKnight] == 0 || piecesCount[blackBishop] == 0);
    }

    public static void main(String[] args) {
        Board board = new Board();
        board.print();
    }

}
