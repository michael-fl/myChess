package org.michaelfl.mychess;

import static org.michaelfl.mychess.Board.*;

/**
 * Tables taken from https://www.chessprogramming.org/Simplified_Evaluation_Function.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("java:S115")
public final class PieceSquareTables {

    private PieceSquareTables() {
        throw new IllegalStateException("Utility class");
    }

    /* Pawn */
    private static final String pawnTableWhiteString = """
             0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
             5,  5, 10, 25, 25, 10,  5,  5,
             0,  0,  0, 20, 20,  0,  0,  0,
             5, -5,-10,  0,  0,-10, -5,  5,
             5, 10, 10,-20,-20, 10, 10,  5,
             0,  0,  0,  0,  0,  0,  0,  0
            """;
    private static final byte[] pawnTableWhite = createBoard(pawnTableWhiteString);
    private static final byte[] pawnTableBlack = invert(pawnTableWhite);

    /* Knight */
    private static final String knightTableWhiteString = """
            -50,-40,-30,-30,-30,-30,-40,-50,
            -40,-20,  0,  0,  0,  0,-20,-40,
            -30,  0, 10, 15, 15, 10,  0,-30,
            -30,  5, 15, 20, 20, 15,  5,-30,
            -30,  0, 15, 20, 20, 15,  0,-30,
            -30,  5, 10, 15, 15, 10,  5,-30,
            -40,-20,  0,  5,  5,  0,-20,-40,
            -50,-40,-30,-30,-30,-30,-40,-50
            """;
    private static final byte[] knightTableWhite = createBoard(knightTableWhiteString);
    private static final byte[] knightTableBlack = invert(knightTableWhite);

    /* Bishop */
    private static final String bishopTableWhiteString = """
            -20,-10,-10,-10,-10,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5, 10, 10,  5,  0,-10,
            -10,  5,  5, 10, 10,  5,  5,-10,
            -10,  0, 10, 10, 10, 10,  0,-10,
            -10, 10, 10, 10, 10, 10, 10,-10,
            -10,  5,  0,  0,  0,  0,  5,-10,
            -20,-10,-10,-10,-10,-10,-10,-20
            """;
    private static final byte[] bishopTableWhite = createBoard(bishopTableWhiteString);
    private static final byte[] bishopTableBlack = invert(bishopTableWhite);

    /* Rook */
    private static final String rookTableWhiteString = """
              0,  0,  0,  0,  0,  0,  0,  0,
              5, 10, 10, 10, 10, 10, 10,  5,
             -5,  0,  0,  0,  0,  0,  0, -5,
             -5,  0,  0,  0,  0,  0,  0, -5,
             -5,  0,  0,  0,  0,  0,  0, -5,
             -5,  0,  0,  0,  0,  0,  0, -5,
             -5,  0,  0,  0,  0,  0,  0, -5,
              0,  0,  0,  5,  5,  0,  0,  0
            """;
    private static final byte[] rookTableWhite = createBoard(rookTableWhiteString);
    private static final byte[] rookTableBlack = invert(rookTableWhite);

    /* Queen */
    private static final String queenTableWhiteString = """
            -20,-10,-10, -5, -5,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5,  5,  5,  5,  0,-10,
             -5,  0,  5,  5,  5,  5,  0, -5,
              0,  0,  5,  5,  5,  5,  0, -5,
            -10,  5,  5,  5,  5,  5,  0,-10,
            -10,  0,  5,  0,  0,  0,  0,-10,
            -20,-10,-10, -5, -5,-10,-10,-20
            """;
    private static final byte[] queenTableWhite = createBoard(queenTableWhiteString);
    private static final byte[] queenTableBlack = invert(queenTableWhite);

    /* King */
    private static final String kingTableWhiteString = """
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -20,-30,-30,-40,-40,-30,-30,-20,
            -10,-20,-20,-20,-20,-20,-20,-10,
             20, 20,  0,  0,  0,  0, 20, 20,
             20, 30, 10,  0,  0, 10, 30, 20
            """;
    private static final byte[] kingTableWhite = createBoard(kingTableWhiteString);
    private static final byte[] kingTableBlack = invert(kingTableWhite);

    private static final byte[][] piece2table = new byte[blackKing + 1][];
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
        final byte[] table = Board.createEmptyRawBoard();

        int col = 0;
        int row = 7;
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
        byte[] resultTable = Board.createEmptyRawBoard();

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

    public static int getPieceSquareWeight(final byte piece, final int field) {
        return piece2table[piece][field];
    }
}
