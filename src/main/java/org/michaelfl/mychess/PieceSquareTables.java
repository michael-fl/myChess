package org.michaelfl.mychess;

import java.util.Arrays;

import static org.michaelfl.mychess.Board.*;

/**
 * Per-piece, per-square positional bonuses. Tables are adapted from the
 * <a href="https://www.chessprogramming.org/Simplified_Evaluation_Function">chessprogramming.org
 * <em>Simplified Evaluation Function</em></a>, with one local modification in the white
 * pawn table — see {@link #pawnTableWhiteCenterKing} for details. The black tables are
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
    private static final String pawnTableWhiteCenterKingString = """
             0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
             5,  5, 10, 25, 25, 10,  5,  5,
             0,  0,  0, 20, 20,  0,  0,  0,
             5,  0,-10,  0,  0,-10,  0,  5,
             5,  0,  0,-20,-20, 10,  0,  5,
             0,  0,  0,  0,  0,  0,  0,  0
            """;
    private static final byte[] pawnTableWhiteCenterKing = createBoard(pawnTableWhiteCenterKingString);
    private static final byte[] pawnTableBlackCenterKing = invert(pawnTableWhiteCenterKing);

    private static final String pawnTableWhiteKingsideKingString = """
             0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
             5,  5, 10, 25, 25, 10,  5,  5,
             0,  0,  0, 20, 20,  0,  0,  0,
             0,  0,  0,  0,  0,-10,  0,  5,
             0,  0,  0,-20,-20, 10,  0,  5,
             0,  0,  0,  0,  0,  0,  0,  0
            """;
    private static final byte[] pawnTableWhiteKingsideKing = createBoard(pawnTableWhiteKingsideKingString);
    private static final byte[] pawnTableBlackKingsideKing = invert(pawnTableWhiteKingsideKing);

    private static final String pawnTableWhiteQueensideKingString = """
             0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
             5,  5, 10, 25, 25, 10,  5,  5,
             0,  0,  0, 20, 20,  0,  0,  0,
             5,  0,-10,  0,  0,  0,  0,  0,
             5,  0, 10,-20,-20,  0,  0,  0,
             0,  0,  0,  0,  0,  0,  0,  0
            """;
    private static final byte[] pawnTableWhiteQueensideKing = createBoard(pawnTableWhiteQueensideKingString);
    private static final byte[] pawnTableBlackQueensideKing = invert(pawnTableWhiteQueensideKing);

    private static final String pawnTableWhiteEndgameString = """
             0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
             5,  5, 10, 25, 25, 10,  5,  5,
             5,  5,  5, 20, 20,  5,  5,  5,
             0,  0,  0,  0,  0,  0,  0,  0,
             0,  0,  0,-20,-20,  0,  0,  0,
             0,  0,  0,  0,  0,  0,  0,  0
            """;
    private static final byte[] pawnTableWhiteEndgame = createBoard(pawnTableWhiteEndgameString);
    private static final byte[] pawnTableBlackEndgame = invert(pawnTableWhiteEndgame);

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
        piece2table[whiteBishop] = bishopTableWhite;
        piece2table[whiteKnight] = knightTableWhite;
        piece2table[whiteRook] = rookTableWhite;
        piece2table[whiteQueen] = queenTableWhite;
        piece2table[whiteKing] = kingTableWhite;
        piece2table[blackBishop] = bishopTableBlack;
        piece2table[blackKnight] = knightTableBlack;
        piece2table[blackRook] = rookTableBlack;
        piece2table[blackQueen] = queenTableBlack;
        piece2table[blackKing] = kingTableBlack;
    }

    private static final int QUEENSIDE = 0;
    private static final int CENTER = 1;
    private static final int KINGSIDE = 2;
    private static final int ENDGAME = 3;

    private static final int[][] FIELD_2_KING_POS = new int[2][LENGTH * LENGTH];
    static {
        Arrays.fill(FIELD_2_KING_POS[0], ENDGAME);
        Arrays.fill(FIELD_2_KING_POS[1], ENDGAME);

        // white
        FIELD_2_KING_POS[0][a1] = QUEENSIDE;
        FIELD_2_KING_POS[0][b1] = QUEENSIDE;
        FIELD_2_KING_POS[0][c1] = QUEENSIDE;
        FIELD_2_KING_POS[0][d1] = CENTER;
        FIELD_2_KING_POS[0][e1] = CENTER;
        FIELD_2_KING_POS[0][f1] = KINGSIDE;
        FIELD_2_KING_POS[0][g1] = KINGSIDE;
        FIELD_2_KING_POS[0][h1] = KINGSIDE;
        FIELD_2_KING_POS[0][a2] = QUEENSIDE;
        FIELD_2_KING_POS[0][b2] = QUEENSIDE;
        FIELD_2_KING_POS[0][c2] = QUEENSIDE;
        FIELD_2_KING_POS[0][d2] = CENTER;
        FIELD_2_KING_POS[0][e2] = CENTER;
        FIELD_2_KING_POS[0][f2] = KINGSIDE;
        FIELD_2_KING_POS[0][g2] = KINGSIDE;
        FIELD_2_KING_POS[0][h2] = KINGSIDE;

        // black
        FIELD_2_KING_POS[1][a8] = QUEENSIDE;
        FIELD_2_KING_POS[1][b8] = QUEENSIDE;
        FIELD_2_KING_POS[1][c8] = QUEENSIDE;
        FIELD_2_KING_POS[1][d8] = CENTER;
        FIELD_2_KING_POS[1][e8] = CENTER;
        FIELD_2_KING_POS[1][f8] = KINGSIDE;
        FIELD_2_KING_POS[1][g8] = KINGSIDE;
        FIELD_2_KING_POS[1][h8] = KINGSIDE;
        FIELD_2_KING_POS[1][a7] = QUEENSIDE;
        FIELD_2_KING_POS[1][b7] = QUEENSIDE;
        FIELD_2_KING_POS[1][c7] = QUEENSIDE;
        FIELD_2_KING_POS[1][d7] = CENTER;
        FIELD_2_KING_POS[1][e7] = CENTER;
        FIELD_2_KING_POS[1][f7] = KINGSIDE;
        FIELD_2_KING_POS[1][g7] = KINGSIDE;
        FIELD_2_KING_POS[1][h7] = KINGSIDE;
    }

    private static final byte[][][] pawnTables = new byte[][][]{
            {
                    pawnTableWhiteQueensideKing,
                    pawnTableWhiteCenterKing,
                    pawnTableWhiteKingsideKing,
                    pawnTableWhiteEndgame
            },
            {
                    pawnTableBlackQueensideKing,
                    pawnTableBlackCenterKing,
                    pawnTableBlackKingsideKing,
                    pawnTableBlackEndgame
            }
    };

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

    public static int getPieceSquareWeight(final byte piece, final int field, final int kingField) {
        return getPSTForPiece(piece, kingField)[field];
    }

    private static byte[] getPSTForPiece(final byte piece, final int kingField) {
        if (ChessUtil.isPawn(piece)) {
            final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;
            return pawnTables[color][FIELD_2_KING_POS[color][kingField]];
        }

        return piece2table[piece];
    }
}
