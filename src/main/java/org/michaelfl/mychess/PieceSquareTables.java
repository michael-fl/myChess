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

    /** Midgame piece-square table per piece constant, indexed as {@code table[piece][field]}. */
    private static final byte[][] piece2midGameTable = new byte[blackKing + 1][];
    static {
        piece2midGameTable[whitePawn] = pawnTableWhite;
        piece2midGameTable[whiteBishop] = bishopTableWhite;
        piece2midGameTable[whiteKnight] = knightTableWhite;
        piece2midGameTable[whiteRook] = rookTableWhite;
        piece2midGameTable[whiteQueen] = queenTableWhite;
        piece2midGameTable[whiteKing] = kingTableWhite;
        piece2midGameTable[blackPawn] = pawnTableBlack;
        piece2midGameTable[blackBishop] = bishopTableBlack;
        piece2midGameTable[blackKnight] = knightTableBlack;
        piece2midGameTable[blackRook] = rookTableBlack;
        piece2midGameTable[blackQueen] = queenTableBlack;
        piece2midGameTable[blackKing] = kingTableBlack;
    }

    /**
     * Endgame piece-square table per piece constant. Currently populated with
     * the same tables as {@link #piece2midGameTable} (the null-test
     * configuration); tuning will later give the endgame phase its own values.
     */
    private static final byte[][] piece2endGameTable = new byte[blackKing + 1][];
    static {
        piece2endGameTable[whitePawn] = pawnTableWhite;
        piece2endGameTable[whiteBishop] = bishopTableWhite;
        piece2endGameTable[whiteKnight] = knightTableWhite;
        piece2endGameTable[whiteRook] = rookTableWhite;
        piece2endGameTable[whiteQueen] = queenTableWhite;
        piece2endGameTable[whiteKing] = kingTableWhite;
        piece2endGameTable[blackPawn] = pawnTableBlack;
        piece2endGameTable[blackBishop] = bishopTableBlack;
        piece2endGameTable[blackKnight] = knightTableBlack;
        piece2endGameTable[blackRook] = rookTableBlack;
        piece2endGameTable[blackQueen] = queenTableBlack;
        piece2endGameTable[blackKing] = kingTableBlack;
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

    /** The whole midgame table for {@code piece} (indexed by field). */
    static byte[] getMidGameTable(final byte piece) {
        return piece2midGameTable[piece];
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
        return piece2midGameTable[piece][field];
    }

    /** The whole endgame table for {@code piece} (indexed by field). */
    static byte[] getEndGameTable(final byte piece) {
        return piece2endGameTable[piece];
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
        return piece2endGameTable[piece][field];
    }
}
