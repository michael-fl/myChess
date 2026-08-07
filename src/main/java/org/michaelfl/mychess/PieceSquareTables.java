package org.michaelfl.mychess;

import static org.michaelfl.mychess.Board.*;

/**
 * Per-piece, per-square positional bonuses. All twelve tables (six piece kinds
 * &times; midgame/endgame) were tuned offline with a full-joint Texel run on a
 * Zurichess {@code quiet-labeled} + myChess Chess960 self-play hybrid; see
 * {@code JointMgEgPstTaperedTexelData} / {@code TexelJointMgEgTuner}. The black
 * tables are derived from the white tables via vertical {@link #invert(short[])}
 * (rank 1 &harr; 8 etc.), which preserves the evaluation's color antisymmetry
 * asserted by {@code MirrorEvalTest}.
 *
 * <p>The tables feed a <em>tapered</em> evaluation: each piece kind has a
 * separate midgame and endgame table, retrieved through {@link #getMidGameWeight} /
 * {@link #getEndGameWeight} and interpolated by game phase in
 * {@link WeightingFunction}. Material and every non-PST term were held fixed
 * during the tune, and each table was re-centered to its pre-tune mean so the
 * tune adjusts only placement, not per-piece material.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("java:S115")
public final class PieceSquareTables {

    private PieceSquareTables() {
        throw new IllegalStateException("Utility class");
    }

    /** White pawn <b>midgame</b> table (full-joint Texel tune; see the class comment). */
    private static final String pawnTableWhiteString = """
               0,   0,   0,   0,   0,   0,   0,   0,
              53,   1,  84, 151, 151,  84,   1,  53,
             -39, -23,  48,  26,  26,  48, -23, -39,
             -30,  18,   7,  39,  39,   7,  18, -30,
             -49,  -1,  -2,  27,  27,  -2,  -1, -49,
             -22,  18,   5,  11,  11,   5,  18, -22,
             -22,  31,   9, -13, -13,   9,  31, -22,
               0,   0,   0,   0,   0,   0,   0,   0
            """;
    private static final short[] pawnTableWhite = createBoard(pawnTableWhiteString);
    private static final short[] pawnTableBlack = invert(pawnTableWhite);

    /**
     * White pawn <b>endgame</b> table (full-joint Texel tune; see the class
     * comment). Strongly rewards advanced (passed) pawns — the classic endgame
     * shape — where the midgame table is nearly flat past rank 5.
     */
    private static final String pawnEndgameTableWhiteString = """
              0,   0,   0,   0,   0,   0,   0,   0,
            358, 331, 339, 236, 236, 339, 331, 358,
            190, 213, 136, 130, 130, 136, 213, 190,
             52,  31,  14, -17, -17,  14,  31,  52,
              2,  -4, -31, -29, -29, -31,  -4,   2,
             -9,  -5, -22, -12, -12, -22,  -5,  -9,
             -7,   7,   1,  -8,  -8,   1,   7,  -7,
              0,   0,   0,   0,   0,   0,   0,   0
            """;
    private static final short[] pawnEndgameTableWhite = createBoard(pawnEndgameTableWhiteString);
    private static final short[] pawnEndgameTableBlack = invert(pawnEndgameTableWhite);

    /* Knight (midgame) */
    private static final String knightTableWhiteString = """
            -164,-154,-144, -78, -78,-144,-154,-164,
            -121, -89,  45,  45,  45,  45, -89,-121,
             -47,  37,  38,  60,  60,  38,  37, -47,
              15,   6,  40,  43,  43,  40,   6,  15,
             -31,   5,  33,  14,  14,  33,   5, -31,
             -26,  27,  31,  32,  32,  31,  27, -26,
               5,  25, -11,  32,  32, -11,  25,   5,
              -5, -17, -20, -21, -21, -20, -17,  -5
            """;
    private static final short[] knightTableWhite = createBoard(knightTableWhiteString);
    private static final short[] knightTableBlack = invert(knightTableWhite);

    /* Knight (endgame) */
    private static final String knightEndgameTableWhiteString = """
             -74, -64, -54,  -6,  -6, -54, -64, -74,
             -64, -44,  -5, -24, -24,  -5, -44, -64,
             -43, -24, -14,  -9,  -9, -14, -24, -43,
             -10,  33,  27,  13,  13,  27,  33, -10,
              33, -24,   7,  42,  42,   7, -24,  33,
             -27, -17, -14,  21,  21, -14, -17, -27,
              40, -12, -14, -19, -19, -14, -12,  40,
             -53, -49,  16,  14,  14,  16, -49, -53
            """;
    private static final short[] knightEndgameTableWhite = createBoard(knightEndgameTableWhiteString);
    private static final short[] knightEndgameTableBlack = invert(knightEndgameTableWhite);

    /* Bishop (midgame) */
    private static final String bishopTableWhiteString = """
             -94, -43, -13,-110,-110, -13, -43, -94,
             -31, -25, -24, -51, -51, -24, -25, -31,
              21,  43,  48,  49,  49,  48,  43,  21,
              -4, -20,   0,  33,  33,   0, -20,  -4,
               2,  -1,   0,  31,  31,   0,  -1,   2,
              33,  11,  27,  10,  10,  27,  11,  33,
               5,  48,   6,  13,  13,   6,  48,   5,
             -17, -35,  -5,   8,   8,  -5, -35, -17
            """;
    private static final short[] bishopTableWhite = createBoard(bishopTableWhiteString);
    private static final short[] bishopTableBlack = invert(bishopTableWhite);

    /* Bishop (endgame) */
    private static final String bishopEndgameTableWhiteString = """
              15, -48,   1,  16,  16,   1, -48,  15,
             -47,   1,  -5, -37, -37,  -5,   1, -47,
               1,   4,  15,  -8,  -8,  15,   4,   1,
               6,   6,  26,  13,  13,  26,   6,   6,
             -19,   0,  14,  25,  25,  14,   0, -19,
             -44,   6,  24,  41,  41,  24,   6, -44,
             -33,  29,  18,  19,  19,  18,  29, -33,
             -39, -49,   6, -15, -15,   6, -49, -39
            """;
    private static final short[] bishopEndgameTableWhite = createBoard(bishopEndgameTableWhiteString);
    private static final short[] bishopEndgameTableBlack = invert(bishopEndgameTableWhite);

    /* Rook (midgame) */
    private static final String rookTableWhiteString = """
              28,  83,  83,  16,  16,  83,  83,  28,
             -30,   4,  93,  20,  20,  93,   4, -30,
              -6, -17,  58,  -8,  -8,  58, -17,  -6,
              21, -10,   7,  17,  17,   7, -10,  21,
             -19, -76,  -7,   3,   3,  -7, -76, -19,
             -78, -39, -36,   0,   0, -36, -39, -78,
             -31, -19, -18, -22, -22, -18, -19, -31,
             -49, -12,  20,  30,  30,  20, -12, -49
            """;
    private static final short[] rookTableWhite = createBoard(rookTableWhiteString);
    private static final short[] rookTableBlack = invert(rookTableWhite);

    /* Rook (endgame) */
    private static final String rookEndgameTableWhiteString = """
              39,   9,   9,  39,  39,   9,   9,  39,
              18,  19,   0,  17,  17,   0,  19,  18,
               3,   1,  -8,  15,  15,  -8,   1,   3,
             -27,   3,  19, -13, -13,  19,   3, -27,
              -1,  29,  -6,  -5,  -5,  -6,  29,  -1,
              -5, -23,  -8, -12, -12,  -8, -23,  -5,
             -16, -40,   2,  -3,  -3,   2, -40, -16,
              -5,  -5, -15, -14, -14, -15,  -5,  -5
            """;
    private static final short[] rookEndgameTableWhite = createBoard(rookEndgameTableWhiteString);
    private static final short[] rookEndgameTableBlack = invert(rookEndgameTableWhite);

    /* Queen (midgame) */
    private static final String queenTableWhiteString = """
            -119,-109, -58,  55,  55, -58,-109,-119,
             -32, -99,  52, -12, -12,  52, -99, -32,
              50,  60, -73,  -6,  -6, -73,  60,  50,
              43,  -7, -16, -33, -33, -16,  -7,  43,
             -17,   8,   6,  -6,  -6,   6,   8, -17,
             -39,  25,  17,  39,  39,  17,  25, -39,
             -13,  47,  40,  49,  49,  40,  47, -13,
              10, -23,  17,  42,  42,  17, -23,  10
            """;
    private static final short[] queenTableWhite = createBoard(queenTableWhiteString);
    private static final short[] queenTableBlack = invert(queenTableWhite);

    /* Queen (endgame) */
    private static final String queenEndgameTableWhiteString = """
             -10, -22,  63,  56,  56,  63, -22, -10,
             -10, -46,  59,  78,  78,  59, -46, -10,
             -65, -31,  83,  61,  61,  83, -31, -65,
             -65, -15,  83,  38,  38,  83, -15, -65,
              -4,  20,   9,  66,  66,   9,  20,  -4,
              13, -44,   8, -48, -48,   8, -44,  13,
              19, -37, -38, -51, -51, -38, -37,  19,
             -83, -45, -65, -82, -82, -65, -45, -83
            """;
    private static final short[] queenEndgameTableWhite = createBoard(queenEndgameTableWhiteString);
    private static final short[] queenEndgameTableBlack = invert(queenEndgameTableWhite);

    /* King (midgame) */
    private static final String kingTableWhiteString = """
              32,  22,  22,  12,  12,  22,  22,  32,
              32,  22,  22,  12,  12,  22,  22,  32,
            -127,  22,  22, -14, -14,  22,  22,-127,
             -61,-129,  -8, -29, -29,  -8,-129, -61,
             -60,-124, -82, -93, -93, -82,-124, -60,
            -100,  21, -45,-117,-117, -45,  21,-100,
              66,  42, -61, -76, -76, -61,  42,  66,
              63,  91,   0, -97, -97,   0,  91,  63
            """;
    private static final short[] kingTableWhite = createBoard(kingTableWhiteString);
    private static final short[] kingTableBlack = invert(kingTableWhite);

    /**
     * White king <b>endgame</b> table (full-joint Texel tune; see the class
     * comment). Inverts the midgame "stay safe" shape into classic endgame
     * centralization: the center is strongly rewarded and the back rank
     * penalized, so the king marches up and inward once material comes off.
     */
    private static final String kingEndgameTableWhiteString = """
             -83,  -5,  -5,  -1,  -1,  -5,  -5, -83,
              32,  62,  88,  58,  58,  88,  62,  32,
              37,  69,  99,  69,  69,  99,  69,  37,
              31,  94,  72,  66,  66,  72,  94,  31,
              22,  66,  82, 110, 110,  82,  66,  22,
              46,  40,  81, 104, 104,  81,  40,  46,
             -18,  46,  74,  76,  76,  74,  46, -18,
             -74, -17,   7, -58, -58,   7, -17, -74
            """;
    private static final short[] kingEndgameTableWhite = createBoard(kingEndgameTableWhiteString);
    private static final short[] kingEndgameTableBlack = invert(kingEndgameTableWhite);

    /** Combined piece-square tables (mid and end game) per piece constant, indexed as {@code table[piece][field]}. */
    private static final int[][] piece2CombinedPST = new int[blackKing + 1][];
    static {
        piece2CombinedPST[whitePawn] = createCombinedTable(whitePawn);
        piece2CombinedPST[whiteBishop] = createCombinedTable(whiteBishop);
        piece2CombinedPST[whiteKnight] = createCombinedTable(whiteKnight);
        piece2CombinedPST[whiteRook] = createCombinedTable(whiteRook);
        piece2CombinedPST[whiteQueen] = createCombinedTable(whiteQueen);
        piece2CombinedPST[whiteKing] = createCombinedTable(whiteKing);
        piece2CombinedPST[blackPawn] = createCombinedTable(blackPawn);
        piece2CombinedPST[blackBishop] = createCombinedTable(blackBishop);
        piece2CombinedPST[blackKnight] = createCombinedTable(blackKnight);
        piece2CombinedPST[blackRook] = createCombinedTable(blackRook);
        piece2CombinedPST[blackQueen] = createCombinedTable(blackQueen);
        piece2CombinedPST[blackKing] = createCombinedTable(blackKing);
    }

    private static int[] createCombinedTable(byte forPiece) {
        int[] table = new int[LENGTH * LENGTH];
        short[] mgTable = midGameTable(forPiece);
        short[] egTable = endGameTable(forPiece);

        for (int field = a1; field <= h8; field++) {
            short mgWeight = mgTable[field];
            short egWeight = egTable[field];
            if (mgWeight != 0 || egWeight != 0) {
                table[field] = pack(mgWeight, egWeight);
            }
        }

        return table;
    }

    private static int pack(short mgWeight, short egWeight) {
        return (egWeight << 16) + mgWeight;
    }

    private static short[] midGameTable(byte forPiece) {
        return switch (forPiece) {
            case whitePawn -> pawnTableWhite;
            case blackPawn -> pawnTableBlack;
            case whiteKnight -> knightTableWhite;
            case blackKnight -> knightTableBlack;
            case whiteBishop -> bishopTableWhite;
            case blackBishop -> bishopTableBlack;
            case whiteRook -> rookTableWhite;
            case blackRook -> rookTableBlack;
            case whiteQueen -> queenTableWhite;
            case blackQueen -> queenTableBlack;
            case whiteKing -> kingTableWhite;
            case blackKing -> kingTableBlack;
            default -> throw new IllegalStateException("Unknown piece: " + forPiece);
        };
    }

    private static short[] endGameTable(byte forPiece) {
        return switch (forPiece) {
            case whitePawn -> pawnEndgameTableWhite;
            case blackPawn -> pawnEndgameTableBlack;
            case whiteKnight -> knightEndgameTableWhite;
            case blackKnight -> knightEndgameTableBlack;
            case whiteBishop -> bishopEndgameTableWhite;
            case blackBishop -> bishopEndgameTableBlack;
            case whiteRook -> rookEndgameTableWhite;
            case blackRook -> rookEndgameTableBlack;
            case whiteQueen -> queenEndgameTableWhite;
            case blackQueen -> queenEndgameTableBlack;
            case whiteKing -> kingEndgameTableWhite;
            case blackKing -> kingEndgameTableBlack;
            default -> throw new IllegalStateException("Unknown piece: " + forPiece);
        };
    }

    private static short[] createBoard(final String tableString) {
        final short[] table = new short[LENGTH * LENGTH];

        int col = 0;
        int row = 7;
        for (String s : tableString.split(",")) {
            short weight = Short.parseShort(s.trim());
            int field = ChessUtil.getFieldFromColAndRow(col, row);
            table[field] = weight;

            col = (col + 1) % 8;
            if (col == 0) {
                row--;
            }
        }

        return table;
    }

    private static short[] invert(final short[] table) {
        short[] resultTable = new short[LENGTH * LENGTH];

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int srcField = ChessUtil.getFieldFromColAndRow(col, row);
                int destField = ChessUtil.getFieldFromColAndRow(col, 7 - row);
                resultTable[destField] = table[srcField];
            }
        }

        return resultTable;
    }

    public static int getCombinedWeight(final byte piece, final int field) {
        return piece2CombinedPST[piece][field];
    }

    /**
     * The whole midgame table for {@code piece} (indexed by field).
     * Inefficient implementation - creates the whole table on each call! Used only by tests.
     */
    static short[] getMidGameTable(final byte piece) {
        int[] combiPST = piece2CombinedPST[piece];

        short[] result = new short[combiPST.length];

        for (int f = a1; f <= h8; f++) {
            int packed = combiPST[f];
            result[f] = (short) packed;
        }

        return result;
    }

    /**
     * Midgame piece-square bonus for {@code piece} on {@code field}, in
     * centipawns before scaling. Interpolated with {@link #getEndGameWeight} by
     * game phase in {@link WeightingFunction}.
     *
     * @param piece piece constant ({@code whitePawn} .. {@code blackKing})
     * @param field board field index
     * @return the midgame table value on that square
     */
    public static int getMidGameWeight(final byte piece, final int field) {
        final int[] combiPST = piece2CombinedPST[piece];
        return (short) combiPST[field];
    }

    /**
     * The whole endgame table for {@code piece} (indexed by field).
     * Inefficient implementation - creates the whole table on each call! Used only by tests.
     */
    static short[] getEndGameTable(final byte piece) {
        int[] combiPST = piece2CombinedPST[piece];

        short[] result = new short[combiPST.length];

        for (int f = a1; f <= h8; f++) {
            int packed = combiPST[f];
            result[f] = (short) ((packed + 0x8000) >> 16);
        }

        return result;
    }

    /**
     * Endgame piece-square bonus for {@code piece} on {@code field}, in
     * centipawns before scaling. Interpolated with {@link #getMidGameWeight} by
     * game phase in {@link WeightingFunction}.
     *
     * @param piece piece constant ({@code whitePawn} .. {@code blackKing})
     * @param field board field index
     * @return the endgame table value on that square
     */
    public static int getEndGameWeight(final byte piece, final int field) {
        final int[] combiPST = piece2CombinedPST[piece];
        return (short) ((combiPST[field] + 0x8000) >> 16);
    }
}
