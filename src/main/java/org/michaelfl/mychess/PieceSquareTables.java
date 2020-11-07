package org.michaelfl.mychess;

import static org.michaelfl.mychess.Board.*;

/**
 * Tables taken from https://www.chessprogramming.org/Simplified_Evaluation_Function.
 *
 * @author Michael Fleischhauer
 */
final class PieceSquareTables {

    private PieceSquareTables() {
        throw new IllegalStateException("Utility class");
    }

    /* Pawn */
    private final static String pawnTableWhiteString =
            " 0,  0,  0,  0,  0,  0,  0,  0,\n" +
            "50, 50, 50, 50, 50, 50, 50, 50,\n" +
            "10, 10, 20, 30, 30, 20, 10, 10,\n" +
            " 5,  5, 10, 25, 25, 10,  5,  5,\n" +
            " 0,  0,  0, 20, 20,  0,  0,  0,\n" +
            " 5, -5,-10,  0,  0,-10, -5,  5,\n" +
            " 5, 10, 10,-20,-20, 10, 10,  5,\n" +
            " 0,  0,  0,  0,  0,  0,  0,  0\n";
    private final static byte[] pawnTableWhite = createBoard(pawnTableWhiteString);
    private final static byte[] pawnTableBlack = invert(pawnTableWhite);

    /* Knight */
    private final static String knightTableWhiteString =
            "-50,-40,-30,-30,-30,-30,-40,-50,\n" +
            "-40,-20,  0,  0,  0,  0,-20,-40,\n" +
            "-30,  0, 10, 15, 15, 10,  0,-30,\n" +
            "-30,  5, 15, 20, 20, 15,  5,-30,\n" +
            "-30,  0, 15, 20, 20, 15,  0,-30,\n" +
            "-30,  5, 10, 15, 15, 10,  5,-30,\n" +
            "-40,-20,  0,  5,  5,  0,-20,-40,\n" +
            "-50,-40,-30,-30,-30,-30,-40,-50\n";
    private final static byte[] knightTableWhite = createBoard(knightTableWhiteString);
    private final static byte[] knightTableBlack = invert(knightTableWhite);

    /* Bishop */
    private final static String bishopTableWhiteString =
            "-20,-10,-10,-10,-10,-10,-10,-20,\n" +
            "-10,  0,  0,  0,  0,  0,  0,-10,\n" +
            "-10,  0,  5, 10, 10,  5,  0,-10,\n" +
            "-10,  5,  5, 10, 10,  5,  5,-10,\n" +
            "-10,  0, 10, 10, 10, 10,  0,-10,\n" +
            "-10, 10, 10, 10, 10, 10, 10,-10,\n" +
            "-10,  5,  0,  0,  0,  0,  5,-10,\n" +
            "-20,-10,-10,-10,-10,-10,-10,-20";
    private final static byte[] bishopTableWhite = createBoard(bishopTableWhiteString);
    private final static byte[] bishopTableBlack = invert(bishopTableWhite);

    /* Rook */
    private final static String rookTableWhiteString =
            "  0,  0,  0,  0,  0,  0,  0,  0,\n" +
            "  5, 10, 10, 10, 10, 10, 10,  5,\n" +
            " -5,  0,  0,  0,  0,  0,  0, -5,\n" +
            " -5,  0,  0,  0,  0,  0,  0, -5,\n" +
            " -5,  0,  0,  0,  0,  0,  0, -5,\n" +
            " -5,  0,  0,  0,  0,  0,  0, -5,\n" +
            " -5,  0,  0,  0,  0,  0,  0, -5,\n" +
            "  0,  0,  0,  5,  5,  0,  0,  0";
    private final static byte[] rookTableWhite = createBoard(rookTableWhiteString);
    private final static byte[] rookTableBlack = invert(rookTableWhite);

    /* Queen */
    private final static String queenTableWhiteString =
            "-20,-10,-10, -5, -5,-10,-10,-20,\n" +
            "-10,  0,  0,  0,  0,  0,  0,-10,\n" +
            "-10,  0,  5,  5,  5,  5,  0,-10,\n" +
            " -5,  0,  5,  5,  5,  5,  0, -5,\n" +
            "  0,  0,  5,  5,  5,  5,  0, -5,\n" +
            "-10,  5,  5,  5,  5,  5,  0,-10,\n" +
            "-10,  0,  5,  0,  0,  0,  0,-10,\n" +
            "-20,-10,-10, -5, -5,-10,-10,-20";
    private final static byte[] queenTableWhite = createBoard(queenTableWhiteString);
    private final static byte[] queenTableBlack = invert(queenTableWhite);

    /* King */
    private final static String kingTableWhiteString =
            "-30,-40,-40,-50,-50,-40,-40,-30,\n" +
            "-30,-40,-40,-50,-50,-40,-40,-30,\n" +
            "-30,-40,-40,-50,-50,-40,-40,-30,\n" +
            "-30,-40,-40,-50,-50,-40,-40,-30,\n" +
            "-20,-30,-30,-40,-40,-30,-30,-20,\n" +
            "-10,-20,-20,-20,-20,-20,-20,-10,\n" +
            " 20, 20,  0,  0,  0,  0, 20, 20,\n" +
            " 20, 30, 10,  0,  0, 10, 30, 20";
    private final static byte[] kingTableWhite = createBoard(kingTableWhiteString);
    private final static byte[] kingTableBlack = invert(kingTableWhite);

    private final static byte[][] piece2table = new byte[blackKing + 1][];
    static {
        piece2table[whitePawn] = pawnTableWhite;
        piece2table[whiteBishop] = bishopTableWhite;
        piece2table[whiteKnight] = knightTableWhite;
        piece2table[whiteRook] = rookTableWhite;
        piece2table[whiteQueen] = queenTableWhite;
        piece2table[whiteKing] = kingTableWhite;
        piece2table[blackPawn] = pawnTableBlack;
        piece2table[blackBishop] = bishopTableBlack;
        piece2table[blackKnight] = knightTableBlack;
        piece2table[blackRook] = rookTableBlack;
        piece2table[blackQueen] = queenTableBlack;
        piece2table[blackKing] = kingTableBlack;
    }

    private static byte[] createBoard(final String tableString) {
        final byte[] table = createEmptyBoard().getRawBoard();

        int col = 0, row = 7;
        for (String s : tableString.split(",")) {
            byte weight = (byte) Integer.parseInt(s.trim());
            int field = ChessUtil.getFieldFromColAndRow(col, row);
            table[field] = weight;

            col = (col + 1) % 8;
            if (col == 0) {
                row--;
            }
        }

        return table;
    }

    private static byte[] invert(byte[] table) {
        byte[] resultTable = Board.createEmptyBoard().getRawBoard();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int srcField = ChessUtil.getFieldFromColAndRow(col, row);
                int destField = ChessUtil.getFieldFromColAndRow(col, 7 - row);
                resultTable[destField] = table[srcField];
            }
        }

        return resultTable;
    }

    static byte[] getPieceSquareTable(final byte piece) {
        return piece2table[piece];
    }

    static int getPieceSquareWeight(final byte piece, final int field) {
        return piece2table[piece][field];
    }
}
