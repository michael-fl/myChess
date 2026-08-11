// ---------------------------------------------------------------------------
// PeSTO Piece-Square Tables by Ronald Friederich (RofChade).
// Source:  https://www.chessprogramming.org/PeSTO%27s_Evaluation_Function
// License: none stated for the table values themselves; the Chess Programming
//          Wiki page that publishes them is CC BY-SA 3.0 by its contributors.
// Adapted for myChess: mirror-symmetrized and scaled x2. Not our own tuning
// output -- see the class comment for the full attribution.
// ---------------------------------------------------------------------------

package org.michaelfl.mychess;

import static org.michaelfl.mychess.Board.*;

/**
 * Per-piece, per-square positional bonuses. All twelve tables (six piece kinds
 * &times; midgame/endgame) are <b>derived from PeSTO's evaluation function by
 * Ronald Friederich</b> &mdash; see the attribution section below. The black
 * tables are derived from the white tables via vertical {@link #invert(short[])}
 * (rank 1 &harr; 8 etc.), which preserves the evaluation's color antisymmetry
 * asserted by {@code MirrorEvalTest}.
 *
 * <p>The tables feed a <em>tapered</em> evaluation: each piece kind has a
 * separate midgame and endgame table, retrieved through {@link #getMidGameWeight} /
 * {@link #getEndGameWeight} and interpolated by game phase in
 * {@link WeightingFunction}.
 *
 * <h2>Attribution &mdash; third-party values</h2>
 *
 * The numbers below are <b>not</b> myChess's own tuning output. They come from
 * PeSTO's evaluation function, created by <b>Ronald Friederich</b> and first
 * tested in his engine RofChade:
 *
 * <ul>
 *   <li>Source: <a href="https://www.chessprogramming.org/PeSTO%27s_Evaluation_Function">
 *       PeSTO's Evaluation Function</a> on the Chess Programming Wiki, which
 *       publishes the tables. That wiki page is licensed
 *       <a href="https://creativecommons.org/licenses/by-sa/3.0/">CC BY-SA 3.0</a>
 *       by its contributors.</li>
 *   <li>Author's engine: <a href="https://www.chessprogramming.org/RofChade">RofChade</a>.</li>
 *   <li>Lineage: PeSTO replaces Tomasz Michniewski's
 *       <a href="https://www.chessprogramming.org/Simplified_Evaluation_Function">Simplified
 *       Evaluation Function</a>; the wiki's code form is based on Pawel Koziol's
 *       adaptation for TSCP.</li>
 *   <li><b>License of the values themselves: not stated.</b> No explicit license
 *       or terms of use accompany the tables at the source (checked 2026-08-11).
 *       They are credited here because attribution is owed regardless, and because
 *       a transformation does not make borrowed values original.</li>
 * </ul>
 *
 * <p><b>What was changed.</b> Two mechanical transformations, no re-tuning:
 *
 * <ol>
 *   <li><b>Mirror-symmetrized</b> &mdash; each square is averaged with its
 *       file-mirrored counterpart, so the tables became left/right symmetric.
 *       PeSTO's originals are not.</li>
 *   <li><b>Scaled &times;2</b> onto myChess's evaluation scale.</li>
 * </ol>
 *
 * Worked example, PeSTO {@code mg_pawn} rank 7 &mdash; original
 * {@code 98, 134, 61, 95, 68, 126, 34, -11}; mirror-averaged
 * {@code 43.5, 84, 93.5, 81.5, …}; doubled {@code 87, 168, 187, 163, …}, which is
 * the first non-zero row of {@link #pawnTableWhiteString} below.
 *
 * <p><b>Why these and not ours.</b> myChess previously carried its own
 * full-joint Texel tune ({@code JointMgEgPstTaperedTexelData} /
 * {@code TexelJointMgEgTuner}, still in the tree and still usable). Swapping in
 * these tables and keeping every other myChess evaluation term measured
 * <b>+32.6 &plusmn; 12.4 Elo</b> over v4.3.4 across 2 000 games. For the full
 * measurement, including why a pure-PeSTO evaluation measured ~0 while this
 * hybrid did not, see {@code docs/roadmap.md} &sect; 12.7.5.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("java:S115")
public final class PieceSquareTables {

    private PieceSquareTables() {
        throw new IllegalStateException("Utility class");
    }

    /** White pawn <b>midgame</b> table (PeSTO-derived; see the class comment). */
    private static final String pawnTableWhiteString = """
               0,   0,   0,   0,   0,   0,   0,   0,
              87, 168, 187, 163, 163, 187, 168,  87,
             -26,  32,  82,  96,  96,  82,  32, -26,
             -37,  30,  18,  44,  44,  18,  30, -37,
             -52,   8,   1,  29,  29,   1,   8, -52,
             -38,  29,  -1,  -7,  -7,  -1,  29, -38,
             -57,  37,   4, -38, -38,   4,  37, -57,
               0,   0,   0,   0,   0,   0,   0,   0
            """;
    private static final short[] pawnTableWhite = createBoard(pawnTableWhiteString);
    private static final short[] pawnTableBlack = invert(pawnTableWhite);

    /**
     * White pawn <b>endgame</b> table (PeSTO-derived; see the class
     * comment). Strongly rewards advanced (passed) pawns — the classic endgame
     * shape — where the midgame table is nearly flat past rank 5.
     */
    private static final String pawnEndgameTableWhiteString = """
               0,   0,   0,   0,   0,   0,   0,   0,
             365, 338, 290, 281, 281, 290, 338, 365,
             178, 182, 138, 123, 123, 138, 182, 178,
              49,  41,  17,   3,   3,  17,  41,  49,
              12,  12, -11, -14, -14, -11,  12,  12,
              -4,   6, -11,   1,   1, -11,   6,  -4,
               6,  10,   8,  23,  23,   8,  10,   6,
               0,   0,   0,   0,   0,   0,   0,   0
            """;
    private static final short[] pawnEndgameTableWhite = createBoard(pawnEndgameTableWhiteString);
    private static final short[] pawnEndgameTableBlack = invert(pawnEndgameTableWhite);

    /* Knight (midgame) */
    private static final String knightTableWhiteString = """
            -274,-104,-131,  12,  12,-131,-104,-274,
             -90, -34, 134,  59,  59, 134, -34, -90,
              -3, 133, 166, 149, 149, 166, 133,  -3,
              13,  35,  88,  90,  90,  88,  35,  13,
             -21,  25,  35,  41,  41,  35,  25, -21,
             -39,  16,  29,  29,  29,  29,  16, -39,
             -48, -67,   6,  -4,  -4,   6, -67, -48,
            -128, -40, -86, -50, -50, -86, -40,-128
            """;
    private static final short[] knightTableWhite = createBoard(knightTableWhiteString);
    private static final short[] knightTableBlack = invert(knightTableWhite);

    /* Knight (endgame) */
    private static final String knightEndgameTableWhiteString = """
            -157,-101, -40, -59, -59, -40,-101,-157,
             -77, -32, -50, -11, -11, -50, -32, -77,
             -65, -39,   1,   8,   8,   1, -39, -65,
             -35,  11,  33,  44,  44,  33,  11, -35,
             -36,  -2,  33,  41,  41,  33,  -2, -36,
             -45, -23,  -4,  25,  25,  -4, -23, -45,
             -86, -43, -30,  -7,  -7, -30, -43, -86,
             -93,-101, -41, -37, -37, -41,-101, -93
            """;
    private static final short[] knightEndgameTableWhite = createBoard(knightEndgameTableWhiteString);
    private static final short[] knightEndgameTableBlack = invert(knightEndgameTableWhite);

    /* Bishop (midgame) */
    private static final String bishopTableWhiteString = """
             -37,  11,-124, -62, -62,-124,  11, -37,
             -73,  34,  41,  17,  17,  41,  34, -73,
             -18,  74,  93,  75,  75,  93,  74, -18,
              -6,  12,  56,  87,  87,  56,  12,  -6,
              -2,  23,  25,  60,  60,  25,  23,  -2,
              10,  33,  42,  29,  29,  42,  33,  10,
               5,  48,  37,   7,   7,  37,  48,   5,
             -54, -42, -26, -34, -34, -26, -42, -54
            """;
    private static final short[] bishopTableWhite = createBoard(bishopTableWhiteString);
    private static final short[] bishopTableBlack = invert(bishopTableWhite);

    /* Bishop (endgame) */
    private static final String bishopEndgameTableWhiteString = """
             -38, -38, -20, -15, -15, -20, -38, -38,
             -22,  -8,  -6, -15, -15,  -6,  -8, -22,
               6,  -8,   6,  -3,  -3,   6,  -8,   6,
              -1,  12,  22,  23,  23,  22,  12,  -1,
             -15,   0,  23,  26,  26,  23,   0, -15,
             -27, -10,  11,  23,  23,  11, -10, -27,
             -41, -33, -16,   3,   3, -16, -33, -41,
             -40, -14, -39, -14, -14, -39, -14, -40
            """;
    private static final short[] bishopEndgameTableWhite = createBoard(bishopEndgameTableWhiteString);
    private static final short[] bishopEndgameTableBlack = invert(bishopEndgameTableWhite);

    /* Rook (midgame) */
    private static final String rookTableWhiteString = """
              75,  73,  41, 114, 114,  41,  73,  75,
              71,  58, 125, 142, 142, 125,  58,  71,
              11,  80,  71,  53,  53,  71,  80,  11,
             -44, -19,  42,  50,  50,  42, -19, -44,
             -59, -20, -19,   8,   8, -19, -20, -59,
             -78, -30, -16, -14, -14, -16, -30, -78,
            -115, -22,  -9, -10, -10,  -9, -22,-115,
             -45, -50,   8,  33,  33,   8, -50, -45
            """;
    private static final short[] rookTableWhite = createBoard(rookTableWhiteString);
    private static final short[] rookTableBlack = invert(rookTableWhite);

    /* Rook (endgame) */
    private static final String rookEndgameTableWhiteString = """
              18,  18,  30,  27,  27,  30,  18,  18,
              14,  21,  16,   8,   8,  16,  21,  14,
               4,   2,   4,   9,   9,   4,   2,   4,
               6,   2,  14,   3,   3,  14,   2,   6,
              -8,  -3,   2,  -1,  -1,   2,  -3,  -8,
             -20,  -8, -17,  -8,  -8, -17,  -8, -20,
              -9, -17,  -9,  -7,  -7,  -9, -17,  -9,
             -29,   6, -10,  -6,  -6, -10,   6, -29
            """;
    private static final short[] rookEndgameTableWhite = createBoard(rookEndgameTableWhiteString);
    private static final short[] rookEndgameTableBlack = invert(rookEndgameTableWhite);

    /* Queen (midgame) */
    private static final String queenTableWhiteString = """
              17,  43,  73,  71,  71,  73,  43,  17,
              30, -11,  52, -15, -15,  52, -11,  30,
              44,  30,  63,  37,  37,  63,  30,  44,
             -26, -29,   1, -17, -17,   1, -29, -26,
             -12, -23, -13, -12, -12, -13, -23, -12,
              -9,  16,  -9,  -7,  -7,  -9,  16,  -9,
             -34, -11,  26,  10,  10,  26, -11, -34,
             -51, -49, -34,  -5,  -5, -34, -49, -51
            """;
    private static final short[] queenTableWhite = createBoard(queenTableWhiteString);
    private static final short[] queenTableBlack = invert(queenTableWhite);

    /* Queen (endgame) */
    private static final String queenEndgameTableWhiteString = """
              11,  32,  41,  54,  54,  41,  32,  11,
             -17,  50,  57,  99,  99,  57,  50, -17,
             -11,  25,  44,  96,  96,  44,  25, -11,
              39,  79,  64, 102, 102,  64,  79,  39,
               5,  67,  53,  78,  78,  53,  67,   5,
             -11, -17,  32,  15,  15,  32, -17, -11,
             -54, -59, -53, -32, -32, -53, -59, -54,
             -74, -48, -54, -48, -48, -54, -48, -74
            """;
    private static final short[] queenEndgameTableWhite = createBoard(queenEndgameTableWhiteString);
    private static final short[] queenEndgameTableBlack = invert(queenEndgameTableWhite);

    /* King (midgame) */
    private static final String kingTableWhiteString = """
             -52,  25, -18, -71, -71, -18,  25, -52,
               0, -39, -24, -15, -15, -24, -39,   0,
             -31,  46,   8, -36, -36,   8,  46, -31,
             -53, -34, -37, -57, -57, -37, -34, -53,
            -100, -34, -71, -85, -85, -71, -34,-100,
             -41, -29, -52, -90, -90, -52, -29, -41,
               9,  16, -24,-107,-107, -24,  16,   9,
              -1,  60, -16, -46, -46, -16,  60,  -1
            """;
    private static final short[] kingTableWhite = createBoard(kingTableWhiteString);
    private static final short[] kingTableBlack = invert(kingTableWhite);

    /**
     * White king <b>endgame</b> table (PeSTO-derived; see the class
     * comment). Inverts the midgame "stay safe" shape into classic endgame
     * centralization: the center is strongly rewarded and the back rank
     * penalized, so the king marches up and inward once material comes off.
     */
    private static final String kingEndgameTableWhiteString = """
             -91, -31,  -3, -29, -29,  -3, -31, -91,
              -1,  40,  52,  34,  34,  52,  40,  -1,
              23,  61,  68,  35,  35,  68,  61,  23,
              -5,  48,  57,  53,  53,  57,  48,  -5,
             -29,   5,  44,  51,  51,  44,   5, -29,
             -28,   4,  27,  44,  44,  27,   4, -28,
             -44, -16,   8,  27,  27,   8, -16, -44,
             -96, -58, -35, -39, -39, -35, -58, -96
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

    @SuppressWarnings("DuplicatedCode")
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

    @SuppressWarnings("DuplicatedCode")
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
     * @param piece piece constant ({@code whitePawn} ... {@code blackKing})
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
     * @param piece piece constant ({@code whitePawn} ... {@code blackKing})
     * @param field board field index
     * @return the endgame table value on that square
     */
    public static int getEndGameWeight(final byte piece, final int field) {
        final int[] combiPST = piece2CombinedPST[piece];
        return (short) ((combiPST[field] + 0x8000) >> 16);
    }
}
