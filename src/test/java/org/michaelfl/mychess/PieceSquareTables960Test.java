package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Chess960 piece-square tables of {@link PieceSquareTables#getCombinedPST(boolean)}:
 * the first rank of the knight, bishop and king midgame tables is changed, everything else is
 * the standard table, black is the vertical mirror of white, and move ordering still sees the
 * standard values.
 *
 * @author Michael Fleischhauer
 */
class PieceSquareTables960Test {

    private static final byte[] WHITE_PIECES = {
            Board.whitePawn, Board.whiteKnight, Board.whiteBishop, Board.whiteRook, Board.whiteQueen, Board.whiteKing
    };

    private static final int FIRST_RANK = 0;

    private static final int FILES = 8;

    private static final int A_FILE = 0;

    private static final int H_FILE = 7;

    private static final int[][] STANDARD = PieceSquareTables.getCombinedPST(false);

    private static final int[][] CHESS960 = PieceSquareTables.getCombinedPST(true);

    private static short midgame(int packed) {
        return (short) packed;
    }

    private static short endgame(int packed) {
        return (short) ((packed + 0x8000) >> 16);
    }

    private static int field(int col, int row) {
        return ChessUtil.getFieldFromColAndRow(col, row);
    }

    private static byte black(byte white) {
        return (byte) (white + Board.blackPawn - Board.whitePawn);
    }

    @Test
    void knightFirstRankIsFlatInChess960() {
        for (int col = 0; col < FILES; col++) {
            assertEquals(PieceSquareTables.CHESS960_KNIGHT_BASE_ROW_WEIGHT, midgame(CHESS960[Board.whiteKnight][field(col, FIRST_RANK)]),
                    "Chess960 knight midgame value on the first rank, file " + col);
        }
    }

    @Test
    void bishopFirstRankIsFlatInChess960() {
        for (int col = 0; col < FILES; col++) {
            assertEquals(PieceSquareTables.CHESS960_BISHOP_BASE_ROW_WEIGHT, midgame(CHESS960[Board.whiteBishop][field(col, FIRST_RANK)]),
                    "Chess960 bishop midgame value on the first rank, file " + col);
        }
    }

    @Test
    void kingStartSquaresAreFlatAndCornersKeepTheirStandardValue() {
        for (int col = A_FILE + 1; col < H_FILE; col++) {
            assertEquals(PieceSquareTables.CHESS960_KING_BASE_ROW_WEIGHT, midgame(CHESS960[Board.whiteKing][field(col, FIRST_RANK)]),
                    "Chess960 king midgame value on b1-g1, file " + col);
        }

        assertEquals(midgame(STANDARD[Board.whiteKing][Board.a1]), midgame(CHESS960[Board.whiteKing][Board.a1]), "Chess960 king keeps the standard a1 value");
        assertEquals(midgame(STANDARD[Board.whiteKing][Board.h1]), midgame(CHESS960[Board.whiteKing][Board.h1]), "Chess960 king keeps the standard h1 value");
    }

    @Test
    void theKingFirstRankIsNotBelowAnySecondRankSquare() {
        for (int col = A_FILE + 1; col < H_FILE; col++) {
            for (int col2 = 0; col2 < FILES; col2++) {
                short start = midgame(CHESS960[Board.whiteKing][field(col, FIRST_RANK)]);
                short second = midgame(CHESS960[Board.whiteKing][field(col2, FIRST_RANK + 1)]);

                assertTrue(start >= second, "a step from file " + col + " of the first rank to file " + col2 + " of the second rank gains nothing");
            }
        }
    }

    @Test
    void everythingOutsideTheChangedFirstRanksIsTheStandardTable() {
        for (byte white : WHITE_PIECES) {
            boolean firstRankChanged = white == Board.whiteKnight || white == Board.whiteBishop || white == Board.whiteKing;

            for (int col = 0; col < FILES; col++) {
                for (int row = 0; row < FILES; row++) {
                    int field = field(col, row);

                    assertEquals(endgame(STANDARD[white][field]), endgame(CHESS960[white][field]),
                            "endgame value unchanged, piece " + white + ", file " + col + ", rank " + row);

                    boolean changed = firstRankChanged && row == FIRST_RANK
                            && !(white == Board.whiteKing && (col == A_FILE || col == H_FILE));

                    if (!changed) {
                        assertEquals(midgame(STANDARD[white][field]), midgame(CHESS960[white][field]),
                                "midgame value unchanged, piece " + white + ", file " + col + ", rank " + row);
                    }
                }
            }
        }
    }

    @Test
    void pawnRookAndQueenTablesAreIdentical() {
        for (byte white : new byte[] {Board.whitePawn, Board.whiteRook, Board.whiteQueen}) {
            assertArrayEquals(STANDARD[white], CHESS960[white], "Chess960 table equals the standard table, white piece " + white);
            assertArrayEquals(STANDARD[black(white)], CHESS960[black(white)], "Chess960 table equals the standard table, black piece " + black(white));
        }
    }

    @Test
    void blackChess960TablesAreTheMirrorOfWhite() {
        for (byte white : WHITE_PIECES) {
            for (int col = 0; col < FILES; col++) {
                for (int row = 0; row < FILES; row++) {
                    assertEquals(CHESS960[white][field(col, row)], CHESS960[black(white)][field(col, FILES - 1 - row)],
                            "black mirrors white, piece " + white + ", file " + col + ", rank " + row);
                }
            }
        }
    }

    @Test
    void moveOrderingKeepsTheStandardValues() {
        assertEquals(midgame(STANDARD[Board.whiteKnight][Board.a1]), PieceSquareTables.getMidGameWeight(Board.whiteKnight, Board.a1),
                "getMidGameWeight reads the standard knight table");
        assertEquals(midgame(STANDARD[Board.whiteKing][Board.e1]), PieceSquareTables.getMidGameWeight(Board.whiteKing, Board.e1),
                "getMidGameWeight reads the standard king table");
    }

    @Test
    void theStandardFirstRanksAreReallyChanged() {
        // guards against a test that cannot fail: the changed squares do differ from the standard tables
        assertNotEquals(PieceSquareTables.CHESS960_KNIGHT_BASE_ROW_WEIGHT, midgame(STANDARD[Board.whiteKnight][Board.a1]), "standard knight a1 differs");
        assertNotEquals(PieceSquareTables.CHESS960_BISHOP_BASE_ROW_WEIGHT, midgame(STANDARD[Board.whiteBishop][Board.a1]), "standard bishop a1 differs");
        assertNotEquals(PieceSquareTables.CHESS960_KING_BASE_ROW_WEIGHT, midgame(STANDARD[Board.whiteKing][Board.e1]), "standard king e1 differs");
    }
}
