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
 * {@link WeightingFunction}. Both sets currently reference the same underlying
 * tables — the phase-agnostic null-test configuration — so the evaluation is
 * unchanged until the endgame tables are later tuned to diverge.
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
