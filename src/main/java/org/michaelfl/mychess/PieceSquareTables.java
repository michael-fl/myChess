package org.michaelfl.mychess;

import static org.michaelfl.mychess.Board.*;

/**
 * Per-piece, per-square positional bonuses. Tables are adapted from the
 * <a href="https://www.chessprogramming.org/Simplified_Evaluation_Function">chessprogramming.org
 * <em>Simplified Evaluation Function</em></a>, with one local modification in the white
 * pawn table — see {@link #pawnTableWhiteString} for details. The black tables are
 * derived from the white tables via vertical {@link #invert(short[])} (rank 1 &harr; 8 etc.),
 * which preserves the evaluation's color antisymmetry asserted by {@code MirrorEvalTest}.
 *
 * <p>The tables feed a <em>tapered</em> evaluation: each piece kind has a
 * separate midgame and endgame table, retrieved through {@link #getMidGameWeight} /
 * {@link #getEndGameWeight} and interpolated by game phase in
 * {@link WeightingFunction}. Every piece kind now has a distinct, offline-tuned
 * endgame table that diverges from its midgame table: the pawn
 * ({@link #pawnEndgameTableWhiteString}, v4.3.0) and king
 * ({@link #kingEndgameTableWhiteString}, v4.3.1) first, then knight, bishop, rook
 * and queen jointly ({@link #knightEndgameTableWhiteString} etc., v4.3.2).
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
    private static final short[] pawnTableWhite = createBoard(pawnTableWhiteString);
    private static final short[] pawnTableBlack = invert(pawnTableWhite);

    /**
     * White pawn <b>endgame</b> table (tapered evaluation). Tuned offline on the
     * Zurichess {@code quiet-labeled} dataset (1.43 M positions) via the
     * {@code PawnPstTaperedTexelData} / {@code TexelPawnTaperedTuner} tooling,
     * holding the midgame table above fixed. It strongly rewards advanced
     * (passed) pawns — the classic endgame shape — where the midgame table is
     * nearly flat past rank 5. File-symmetric by construction; the black table is
     * the vertical mirror. A Texel-MSE candidate, to be confirmed by a cutechess
     * match against the pre-change baseline before it is trusted.
     */
    private static final String pawnEndgameTableWhiteString = """
              0,   0,   0,   0,   0,   0,   0,   0,
            290, 290, 290, 290, 290, 290, 290, 290,
            199, 207, 182, 159, 159, 182, 207, 199,
             39,  50,  27,   6,   6,  27,  50,  39,
            -24,  -5, -26, -27, -27, -26,  -5, -24,
            -28,   2,  -9, -16, -16,  -9,   2, -28,
            -12,   9,  11,  -5,  -5,  11,   9, -12,
              0,   0,   0,   0,   0,   0,   0,   0
            """;
    private static final short[] pawnEndgameTableWhite = createBoard(pawnEndgameTableWhiteString);
    private static final short[] pawnEndgameTableBlack = invert(pawnEndgameTableWhite);

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
    private static final short[] knightTableWhite = createBoard(knightTableWhiteString);
    private static final short[] knightTableBlack = invert(knightTableWhite);

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
    private static final short[] bishopTableWhite = createBoard(bishopTableWhiteString);
    private static final short[] bishopTableBlack = invert(bishopTableWhite);

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
    private static final short[] rookTableWhite = createBoard(rookTableWhiteString);
    private static final short[] rookTableBlack = invert(rookTableWhite);

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
    private static final short[] queenTableWhite = createBoard(queenTableWhiteString);
    private static final short[] queenTableBlack = invert(queenTableWhite);

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
    private static final short[] kingTableWhite = createBoard(kingTableWhiteString);
    private static final short[] kingTableBlack = invert(kingTableWhite);

    /**
     * White king <b>endgame</b> table (tapered evaluation). Tuned offline on the
     * Zurichess {@code quiet-labeled} set (1.43 M positions) via the
     * {@code KingPstTaperedTexelData} / {@code TexelKingTaperedTuner} tooling,
     * holding the midgame table above fixed. It inverts the midgame "stay safe"
     * shape into classic endgame centralization: the center is strongly rewarded
     * and the back-rank center penalized, so the king marches up and inward once
     * material comes off. Paired with removing the crude {@code isEndGame()}
     * king-PST skip in {@link WeightingFunction}, this is the tapered king term
     * (king-safety-lite in the midgame + endgame centralization). A Texel-MSE
     * candidate, to be confirmed by a cutechess match before it is trusted.
     */
    private static final String kingEndgameTableWhiteString = """
            -45, 24, 26, 24, 24, 26, 24,-45,
             38, 79, 75, 55, 55, 75, 79, 38,
             49, 96, 86, 72, 72, 86, 96, 49,
             32, 67, 80, 72, 72, 80, 67, 32,
              2, 50, 70, 79, 79, 70, 50,  2,
              6, 51, 70, 72, 72, 70, 51,  6,
             14, 44, 60, 56, 56, 60, 44, 14,
            -52, 57,  6,-135,-135,  6, 57,-52
            """;
    private static final short[] kingEndgameTableWhite = createBoard(kingEndgameTableWhiteString);
    private static final short[] kingEndgameTableBlack = invert(kingEndgameTableWhite);

    /*
     * Joint Texel-tuned knight/bishop/rook/queen ENDGAME tables (v4.3.2). Tuned
     * together in one 128-parameter run on the Zurichess quiet-labeled set with
     * the midgame tables held fixed. Each table's uniform offset was then removed
     * — a material leak: with material fixed, the tuner expressed "this piece is
     * worth more/less in the endgame" as a flat shift of the whole table (e.g. the
     * raw queen table sat ~+50 cp above its midgame level, the raw knight ~-36 cp
     * below). That flat part re-counts material the fixed piece values already
     * hold, so each endgame table is anchored to its midgame table's mean, leaving
     * only the positional shape: knight and rook centralization / activity, milder
     * bishop and queen patterns. A Texel-MSE candidate, to be confirmed by a
     * cutechess match against v4.3.1 before it is trusted.
     */
    private static final String knightEndgameTableWhiteString = """
            -106, -90, -81, -34, -34, -81, -90,-106,
             -88, -40, -11, -14, -14, -11, -40, -88,
             -26, -16,  21,  29,  29,  21, -16, -26,
              20,  19,  26,  45,  45,  26,  19,  20,
               6,   5,  29,  38,  38,  29,   5,   6,
             -34, -11,  -1,  32,  32,  -1, -11, -34,
              20, -20, -20,   9,   9, -20, -20,  20,
             -94, -22,  -4,  -6,  -6,  -4, -22, -94
            """;
    private static final short[] knightEndgameTableWhite = createBoard(knightEndgameTableWhiteString);
    private static final short[] knightEndgameTableBlack = invert(knightEndgameTableWhite);

    private static final String bishopEndgameTableWhiteString = """
             -38, -53, -45, -29, -29, -45, -53, -38,
             -65, -13, -23, -19, -19, -23, -13, -65,
              15,   8,  14,   9,   9,  14,   8,  15,
              -6,   7, -10,  17,  17, -10,   7,  -6,
             -25,  -3,  25,  15,  15,  25,  -3, -25,
               3,  13,  29,  39,  39,  29,  13,   3,
             -15,  74,   1,  45,  45,   1,  74, -15,
             -33, -33,  11,   3,   3,  11, -33, -33
            """;
    private static final short[] bishopEndgameTableWhite = createBoard(bishopEndgameTableWhiteString);
    private static final short[] bishopEndgameTableBlack = invert(bishopEndgameTableWhite);

    private static final String rookEndgameTableWhiteString = """
              38,  25,  31,  29,  29,  31,  25,  38,
              10,  19,  27,  31,  31,  27,  19,  10,
              12,  13,   7,   9,   9,   7,  13,  12,
               4,   1,   9,   5,   5,   9,   1,   4,
             -20, -13,  -7,  -3,  -3,  -7, -13, -20,
             -32, -27, -21, -10, -10, -21, -27, -32,
             -36, -15,  -7, -11, -11,  -7, -15, -36,
             -99, -15,  37,  18,  18,  37, -15, -99
            """;
    private static final short[] rookEndgameTableWhite = createBoard(rookEndgameTableWhiteString);
    private static final short[] rookEndgameTableBlack = invert(rookEndgameTableWhite);

    private static final String queenEndgameTableWhiteString = """
               3,   5,  17,  22,  22,  17,   5,   3,
             -73, -73,  27,  27,  27,  27, -73, -73,
               1,  10,  32,  32,  32,  32,  10,   1,
             -22,  -1,  32,  32,  32,  32,  -1, -22,
             -56,  27,  -2,  32,  32,  -2,  27, -56,
             -71, -19,  32,  16,  16,  32, -19, -71,
             -39, -21,  13,  27,  27,  13, -21, -39,
               7, -79, -39,  22,  22, -39, -79,   7
            """;
    private static final short[] queenEndgameTableWhite = createBoard(queenEndgameTableWhiteString);
    private static final short[] queenEndgameTableBlack = invert(queenEndgameTableWhite);

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
