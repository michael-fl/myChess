package org.michaelfl.mychess;

import java.util.Arrays;

@SuppressWarnings({"WeakerAccess", "unused", "PointlessArithmeticExpression"})
public final class Board {

    @FunctionalInterface
    private interface IMove {
        void move(byte[] board, int move);
    }

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
    final static int a2 = 3 * LENGTH + 2 + 0;
    final static int b2 = 3 * LENGTH + 2 + 1;
    final static int c2 = 3 * LENGTH + 2 + 2;
    final static int d2 = 3 * LENGTH + 2 + 3;
    final static int e2 = 3 * LENGTH + 2 + 4;
    final static int f2 = 3 * LENGTH + 2 + 5;
    final static int g2 = 3 * LENGTH + 2 + 6;
    final static int h2 = 3 * LENGTH + 2 + 7;
    final static int a3 = 4 * LENGTH + 2 + 0;
    final static int b3 = 4 * LENGTH + 2 + 1;
    final static int c3 = 4 * LENGTH + 2 + 2;
    final static int d3 = 4 * LENGTH + 2 + 3;
    final static int e3 = 4 * LENGTH + 2 + 4;
    final static int f3 = 4 * LENGTH + 2 + 5;
    final static int g3 = 4 * LENGTH + 2 + 6;
    final static int h3 = 4 * LENGTH + 2 + 7;
    final static int a4 = 5 * LENGTH + 2 + 0;
    final static int b4 = 5 * LENGTH + 2 + 1;
    final static int c4 = 5 * LENGTH + 2 + 2;
    final static int d4 = 5 * LENGTH + 2 + 3;
    final static int e4 = 5 * LENGTH + 2 + 4;
    final static int f4 = 5 * LENGTH + 2 + 5;
    final static int g4 = 5 * LENGTH + 2 + 6;
    final static int h4 = 5 * LENGTH + 2 + 7;
    final static int a5 = 6 * LENGTH + 2 + 0;
    final static int b5 = 6 * LENGTH + 2 + 1;
    final static int c5 = 6 * LENGTH + 2 + 2;
    final static int d5 = 6 * LENGTH + 2 + 3;
    final static int e5 = 6 * LENGTH + 2 + 4;
    final static int f5 = 6 * LENGTH + 2 + 5;
    final static int g5 = 6 * LENGTH + 2 + 6;
    final static int h5 = 6 * LENGTH + 2 + 7;
    final static int a6 = 7 * LENGTH + 2 + 0;
    final static int b6 = 7 * LENGTH + 2 + 1;
    final static int c6 = 7 * LENGTH + 2 + 2;
    final static int d6 = 7 * LENGTH + 2 + 3;
    final static int e6 = 7 * LENGTH + 2 + 4;
    final static int f6 = 7 * LENGTH + 2 + 5;
    final static int g6 = 7 * LENGTH + 2 + 6;
    final static int h6 = 7 * LENGTH + 2 + 7;
    final static int a7 = 8 * LENGTH + 2 + 0;
    final static int b7 = 8 * LENGTH + 2 + 1;
    final static int c7 = 8 * LENGTH + 2 + 2;
    final static int d7 = 8 * LENGTH + 2 + 3;
    final static int e7 = 8 * LENGTH + 2 + 4;
    final static int f7 = 8 * LENGTH + 2 + 5;
    final static int g7 = 8 * LENGTH + 2 + 6;
    final static int h7 = 8 * LENGTH + 2 + 7;
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

    private final static IMove[] MOVE_FUNCTIONS = new IMove[Move.typePawnPromotionBishop + 1];
    static {
        MOVE_FUNCTIONS[Move.typeNormal]              = Board::makeNormalMove;
        MOVE_FUNCTIONS[Move.typeCastlingKingSide]    = Board::makeCastlingKingSideMove;
        MOVE_FUNCTIONS[Move.typeCastlingQueenSide]   = Board::makeCastlingQueenSideMove;
        MOVE_FUNCTIONS[Move.typePawnPromotionQueen]  = Board::makePawnPromotionMoveQueen;
        MOVE_FUNCTIONS[Move.typePawnPromotionKnight] = Board::makePawnPromotionMoveKnight;
        MOVE_FUNCTIONS[Move.typePawnPromotionRook]   = Board::makePawnPromotionMoveRook;
        MOVE_FUNCTIONS[Move.typePawnPromotionBishop] = Board::makePawnPromotionMoveBishop;
    }

    private final static IMove[] MOVE_REVERT_FUNCTIONS = new IMove[Move.typePawnPromotionBishop + 1];
    static {
        MOVE_REVERT_FUNCTIONS[Move.typeNormal]              = Board::revertNormalMove;
        MOVE_REVERT_FUNCTIONS[Move.typeCastlingKingSide]    = Board::revertCastlingKingSideMove;
        MOVE_REVERT_FUNCTIONS[Move.typeCastlingQueenSide]   = Board::revertCastlingQueenSideMove;
        MOVE_REVERT_FUNCTIONS[Move.typePawnPromotionQueen]  = Board::revertPawnPromotionMove;
        MOVE_REVERT_FUNCTIONS[Move.typePawnPromotionKnight] = Board::revertPawnPromotionMove;
        MOVE_REVERT_FUNCTIONS[Move.typePawnPromotionRook]   = Board::revertPawnPromotionMove;
        MOVE_REVERT_FUNCTIONS[Move.typePawnPromotionBishop] = Board::revertPawnPromotionMove;
    }

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
    Board() {
        board = new byte[LENGTH*LENGTH];
        Arrays.fill(board, illegal);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                board[ChessUtil.getFieldFromColAndRow(col, row)] = empty;
            }
        }

        board[a1] = whiteRook;
        board[b1] = whiteKnight;
        board[c1] = whiteBishop;
        board[d1] = whiteQueen;
        board[e1] = whiteKing;
        board[f1] = whiteBishop;
        board[g1] = whiteKnight;
        board[h1] = whiteRook;

        board[a2] = whitePawn;
        board[b2] = whitePawn;
        board[c2] = whitePawn;
        board[d2] = whitePawn;
        board[e2] = whitePawn;
        board[f2] = whitePawn;
        board[g2] = whitePawn;
        board[h2] = whitePawn;

        board[a7] = blackPawn;
        board[b7] = blackPawn;
        board[c7] = blackPawn;
        board[d7] = blackPawn;
        board[e7] = blackPawn;
        board[f7] = blackPawn;
        board[g7] = blackPawn;
        board[h7] = blackPawn;

        board[a8] = blackRook;
        board[b8] = blackKnight;
        board[c8] = blackBishop;
        board[d8] = blackQueen;
        board[e8] = blackKing;
        board[f8] = blackBishop;
        board[g8] = blackKnight;
        board[h8] = blackRook;
    }

    Board(byte[] board) {
        this.board = board;
    }

    static Board createEmptyBoard() {
        return new Board(new byte[LENGTH*LENGTH]);
    }

    public Board copy() {
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

    public void print() {
        System.out.println(this);
    }

    private char toPrintSymbol(byte piece) {
        return printSymbols[piece];
    }

    byte get(int field) {
        return board[field];
    }

    void validateMove(int move) {
        byte fromField = Move.getFromField(move);
        byte toField = Move.getToField(move);
        byte piece = board[fromField];
        if (piece == empty || piece == illegal)
            throw new IllegalStateException("Illegal move: " + ChessUtil.moveToString(fromField, toField));
        byte targetField = board[toField];
        if (targetField == illegal)
            throw new IllegalStateException("Illegal move: " + ChessUtil.moveToString(fromField, toField));
    }

    static boolean isKnight(byte piece) {
        return piece == whiteKnight || piece == blackKnight;
    }

    static boolean isQueen(byte piece) {
        return piece == whiteQueen || piece == blackQueen;
    }

    static boolean isKing(byte piece) {
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

    public void makeMove(int move) {
        makeMove(board, move);
    }

    static void makeMove(byte[] board, int move) {
        MOVE_FUNCTIONS[Move.getMoveType(move)].move(board, move);
    }

    public void revertMove(int move) {
        revertMove(board, move);
    }

    static void revertMove(byte[] board, int move) {
        MOVE_REVERT_FUNCTIONS[Move.getMoveType(move)].move(board, move);
    }

    private static void makeNormalMove(byte[] board, int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);

        board[toField] = board[fromField];
        board[fromField] = empty;
    }

    private static void makePawnPromotionMoveQueen(byte[] board, int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        board[fromField] = empty;
        board[toField] = toField >= a8 ? Board.whiteQueen : Board.blackQueen;
    }

    private static void makePawnPromotionMoveKnight(byte[] board, int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        board[fromField] = empty;
        board[toField] = toField >= a8 ? Board.whiteKnight : Board.blackKnight;
    }

    private static void makePawnPromotionMoveRook(byte[] board, int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        board[fromField] = empty;
        board[toField] = toField >= a8 ? Board.whiteRook : Board.blackRook;
    }

    private static void makePawnPromotionMoveBishop(byte[] board, int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        board[fromField] = empty;
        board[toField] = toField >= a8 ? Board.whiteBishop : Board.blackBishop;
    }

    @SuppressWarnings("Duplicates")
    private static void makeCastlingKingSideMove(byte[] board, int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);

        board[toField] =  board[fromField];
        board[fromField] = empty;

        if (fromField == e1) {
            board[h1] = empty;
            board[f1] = whiteRook;
        } else {
            board[h8] = empty;
            board[f8] = blackRook;
        }
    }

    @SuppressWarnings("Duplicates")
    private static void makeCastlingQueenSideMove(byte[] board, int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);

        board[toField] =  board[fromField];
        board[fromField] = empty;

        if (fromField == e1) {
            board[a1] = empty;
            board[d1] = whiteRook;
        } else {
            board[a8] = empty;
            board[d8] = blackRook;
        }
    }

    private static void revertNormalMove(byte[] board, int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);

        board[fromField] = board[toField];
        board[toField] = Move.getCapturedPiece(move);
    }

    private static void revertPawnPromotionMove(byte[] board, int move) {
        byte fromField = Move.getFromField(move);
        byte toField = Move.getToField(move);

        board[fromField] = toField > fromField ? Board.whitePawn : Board.blackPawn;
        board[toField] = Move.getCapturedPiece(move);
    }

    private static void revertCastlingKingSideMove(byte[] board, int move) {
        byte fromField = Move.getFromField(move);
        byte toField = Move.getToField(move);

        board[fromField] = board[toField];
        board[toField] = Board.empty;

        if (fromField == Board.e1) {
            board[Board.f1] = Board.empty;
            board[Board.h1] = Board.whiteRook;
        } else {
            board[Board.f8] = Board.empty;
            board[Board.h8] = Board.blackRook;
        }
    }

    private static void revertCastlingQueenSideMove(byte[] board, int move) {
        byte fromField = Move.getFromField(move);
        byte toField = Move.getToField(move);

        board[fromField] = board[toField];
        board[toField] = Board.empty;

        if (fromField == Board.e1) {
            board[Board.d1] = Board.empty;
            board[Board.a1] = Board.whiteRook;
        } else {
            board[Board.d8] = Board.empty;
            board[Board.a8] = Board.blackRook;
        }
    }

    public static void main(String[] args) {
        Board board = new Board();
        board.print();
    }

}
