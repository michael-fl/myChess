package org.michaelfl.mychess;

import static org.michaelfl.mychess.Board.*;

/**
 * Per-piece, per-square positional bonuses. Tables are adapted from the
 * <a href="https://www.chessprogramming.org/Simplified_Evaluation_Function">chessprogramming.org
 * <em>Simplified Evaluation Function</em></a>, with one local modification in the white
 * pawn table — see {@link #pawnTableWhiteString} for details. The black tables are
 * derived from the white tables via vertical {@link #invert(byte[])} (rank 1 &harr; 8 etc.),
 * which preserves the evaluation's color antisymmetry asserted by {@code MirrorEvalTest}.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("java:S115")
public final class PieceSquareTables {

    private PieceSquareTables() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * White pawn table. Deviates from the Simplified Evaluation Function on five squares:
     * <ul>
     *   <li>{@code b2}, {@code c2}, {@code g2}: {@code +10} &rarr; {@code 0}</li>
     *   <li>{@code b3}, {@code g3}: {@code -5} &rarr; {@code 0}</li>
     * </ul>
     * The original Simplified values rewarded those pawns for staying on their starting
     * squares and penalized the first step to {@code b3}/{@code g3}, which discouraged
     * queenside expansion and fianchetto preparation. The retired
     * {@code WeightingFunction.calculateOpeningState} heuristic compensated for this with
     * an opposite-signed bonus on the same squares; once that heuristic was removed
     * (commit on branch {@code version-3.2-no-opening-weight}), the conflicting Simplified
     * values had to go too. SPRT-confirmed Elo-neutral against the pre-refactor version
     * over 800 games (+5.6 &plusmn; 21.2, LOS 69.9%). See roadmap &sect; 12.7 for the
     * planned full migration to PeSTO.
     */
    private static final String pawnTableWhiteString = """
             0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
             5,  5, 10, 25, 25, 10,  5,  5,
             0,  0,  0, 20, 20,  0,  0,  0,
             5,  0,-10,  0,  0,-10,  0,  5,
             5,  0,  0,-20,-20, 10,  0,  5,
             0,  0,  0,  0,  0,  0,  0,  0
            """;
    private static final byte[] pawnTableWhite = createBoard(pawnTableWhiteString);
    private static final byte[] pawnTableBlack = invert(pawnTableWhite);

    /* Knight */
    private static final String knightTableWhiteString = """
            -147, -75, -95, -14, -14, -95, -75,-147,
             -79, -34,  27,   2,   2,  27, -34, -79,
              -7,  26,  39,  51,  51,  39,  26,  -7,
              23,  25,  35,  52,  52,  35,  25,  23,
              -2,  12,  44,  52,  52,  44,  12,  -2,
              -6,  27,  44,  50,  50,  44,  27,  -6,
              20,  -3,  13,  38,  38,  13,  -3,  20,
            -108,   0, -10,   2,   2, -10,   0,-108
            """;
    private static final byte[] knightTableWhite = createBoard(knightTableWhiteString);
    private static final byte[] knightTableBlack = invert(knightTableWhite);

    /* Bishop */
    private static final String bishopTableWhiteString = """
             -15, -38, -50, -44, -44, -50, -38, -15,
             -41,   3, -22, -23, -23, -22,   3, -41,
              15,  19,  21,  20,  20,  21,  19,  15,
              -8,   2,  -9,  25,  25,  -9,   2,  -8,
             -23,  -1,  14,  31,  31,  14,  -1, -23,
              12,  30,  30,  28,  28,  30,  30,  12,
               3,  52,  19,  25,  25,  19,  52,   3,
             -30, -47,   1,   2,   2,   1, -47, -30
            """;
    private static final byte[] bishopTableWhite = createBoard(bishopTableWhiteString);
    private static final byte[] bishopTableBlack = invert(bishopTableWhite);

    /* Rook */
    private static final String rookTableWhiteString = """
              38,  13,  17,  27,  27,  17,  13,  38,
              23,  36,  32,  27,  27,  32,  36,  23,
               1,  13,   8,  14,  14,   8,  13,   1,
              -8, -12,   5,  10,  10,   5, -12,  -8,
             -16, -19, -17,  -7,  -7, -17, -19, -16,
             -30, -17, -16,  -4,  -4, -16, -17, -30,
             -50, -16,  -2, -12, -12,  -2, -16, -50,
             -74, -29,  43,  27,  27,  43, -29, -74
            """;
    private static final byte[] rookTableWhite = createBoard(rookTableWhiteString);
    private static final byte[] rookTableBlack = invert(rookTableWhite);

    /* Queen */
    private static final String queenTableWhiteString = """
             -29, -22,  18,  27,  27,  18, -22, -29,
             -39, -47,  17, -11, -11,  17, -47, -39,
               5,   4,  30,  41,  41,  30,   4,   5,
              -5, -20,   4, -13, -13,   4, -20,  -5,
             -20,  11,  -2,   3,   3,  -2,  11, -20,
             -32,   8,  26,  11,  11,  26,   8, -32,
             -10,  12,  12,  22,  22,  12,  12, -10,
               1, -27,   0,  20,  20,   0, -27,   1
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
