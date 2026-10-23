package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Chess960Variant}: the arm names, the table values each arm writes, and that
 * no arm touches the standard tables or the black/white antisymmetry.
 *
 * @author Michael Fleischhauer
 */
class Chess960VariantTest {

    private static final List<String> ALL_ARMS = List.of(Chess960Variant.BASE, "N", "L", "A", "B", "C", "D", "R", "N+L+B+R");

    @Test
    void baseAndBlankChangeNothing() {
        assertEquals(Chess960Variant.NONE, Chess960Variant.parse(Chess960Variant.BASE), "base is the empty variant");
        assertEquals(Chess960Variant.NONE, Chess960Variant.parse(""), "a blank arm list is the empty variant");
    }

    @Test
    void parsesACombination() {
        Chess960Variant variant = Chess960Variant.parse("N+L+B+R");

        assertTrue(variant.flatKnightRank(), "N flattens the knight rank");
        assertTrue(variant.flatBishopRank(), "L flattens the bishop rank");
        assertEquals(new Chess960Variant.KingArm((short) 9, (short) 9, (short) 9), variant.king(), "B is the 9/9/9 king arm");
        assertTrue(variant.noCastlingTerm(), "R switches the castling term off");
        assertEquals(0f, variant.castlingScale(), "castling scale of arm R");
    }

    @Test
    void parsesEveryKingArm() {
        assertEquals(new Chess960Variant.KingArm((short) 16, (short) 16, (short) 9), Chess960Variant.parse("A").king(), "arm A");
        assertEquals(new Chess960Variant.KingArm((short) 9, (short) 9, (short) 9), Chess960Variant.parse("B").king(), "arm B");
        assertEquals(new Chess960Variant.KingArm((short) 0, (short) 0, (short) 0), Chess960Variant.parse("C").king(), "arm C");
        assertEquals(new Chess960Variant.KingArm((short) 50, (short) 16, (short) 9), Chess960Variant.parse("D").king(), "arm D");
        assertNull(Chess960Variant.parse("N").king(), "no king arm unless one is named");
        assertEquals(1f, Chess960Variant.parse("N").castlingScale(), "castling term stays on without R");
    }

    @Test
    void rejectsUnknownArmsAndTwoKingArms() {
        assertThrows(Chess960Variant.UnknownChess960VariantException.class, () -> Chess960Variant.parse("X"), "unknown arm");
        assertThrows(Chess960Variant.UnknownChess960VariantException.class, () -> Chess960Variant.parse("A+B"), "two king arms");
    }

    @Test
    void noArmChangesTheStandardTables() {
        int[][] standard = PieceSquareTables.combinedTables(false);
        int[][] none = PieceSquareTables.buildCombinedTables(Chess960Variant.NONE);

        for (byte piece = Board.whitePawn; piece <= Board.blackKing; piece++) {
            if (standard[piece] == null) {
                continue;
            }

            assertArrayEquals(standard[piece], none[piece], "tables built without a variant equal the standard tables, piece " + piece);
        }

        for (String arm : ALL_ARMS) {
            int[][] variant = PieceSquareTables.buildCombinedTables(Chess960Variant.parse(arm));

            for (byte piece : new byte[] {Board.whitePawn, Board.whiteRook, Board.whiteQueen, Board.blackPawn, Board.blackRook, Board.blackQueen}) {
                assertArrayEquals(standard[piece], variant[piece], "arm " + arm + " leaves piece " + piece + " unchanged");
            }
        }
    }

    @Test
    void knightArmFlattensOnlyTheFirstRankOfTheMidgameTable() {
        assertFirstRank(Chess960Variant.parse("N"), Board.whiteKnight, Chess960Variant.KNIGHT_RANK_VALUE);
        assertOnlyMidgameFirstRankChanged(Chess960Variant.parse("N"), Board.whiteKnight);
    }

    @Test
    void bishopArmFlattensOnlyTheFirstRankOfTheMidgameTable() {
        assertFirstRank(Chess960Variant.parse("L"), Board.whiteBishop, Chess960Variant.BISHOP_RANK_VALUE);
        assertOnlyMidgameFirstRankChanged(Chess960Variant.parse("L"), Board.whiteBishop);
    }

    @Test
    void kingArmSetsTheFirstRankAndTheWingSquaresOfTheSecondRank() {
        Chess960Variant variant = Chess960Variant.parse("C");
        int[][] tables = PieceSquareTables.buildCombinedTables(variant);

        assertFirstRank(variant, Board.whiteKing, (short) 0);
        assertEquals(0, (short) tables[Board.whiteKing][Board.b2], "arm C: b2");
        assertEquals(0, (short) tables[Board.whiteKing][Board.g2], "arm C: g2");
        assertEquals(0, (short) tables[Board.whiteKing][Board.a2], "arm C: a2");
        assertEquals(0, (short) tables[Board.whiteKing][Board.h2], "arm C: h2");

        int[][] standard = PieceSquareTables.combinedTables(false);

        assertEquals((short) standard[Board.whiteKing][Board.c2], (short) tables[Board.whiteKing][Board.c2], "arm C leaves c2");
    }

    @Test
    void noKingArmCreatesAPullToTheSecondRank() {
        for (String arm : List.of("A", "B", "C", "D")) {
            int[][] tables = PieceSquareTables.buildCombinedTables(Chess960Variant.parse(arm));
            short firstRank = (short) tables[Board.whiteKing][Board.e1];

            for (int col = 0; col < 8; col++) {
                int field = ChessUtil.getFieldFromColAndRow(col, 1);

                assertTrue((short) tables[Board.whiteKing][field] <= firstRank, "arm " + arm + ": second-rank square " + col + " not above the first rank");
            }
        }
    }

    @Test
    void blackTablesStayTheMirrorOfWhite() {
        for (String arm : ALL_ARMS) {
            int[][] tables = PieceSquareTables.buildCombinedTables(Chess960Variant.parse(arm));

            for (byte white = Board.whitePawn; white <= Board.whiteKing; white++) {
                byte black = (byte) (white + Board.blackPawn - Board.whitePawn);

                for (int col = 0; col < 8; col++) {
                    for (int row = 0; row < 8; row++) {
                        int field = ChessUtil.getFieldFromColAndRow(col, row);
                        int mirrored = ChessUtil.getFieldFromColAndRow(col, 7 - row);

                        assertEquals(tables[white][field], tables[black][mirrored], "arm " + arm + ", piece " + white + ", col " + col + ", row " + row);
                    }
                }
            }
        }
    }

    @Test
    void theJvmWithoutThePropertyUsesTheStandardTablesInChess960() {
        String property = System.getProperty(Chess960Variant.PROPERTY);

        assertTrue(property == null || Chess960Variant.BASE.equals(property), "the test JVM runs without a variant");

        int[][] standard = PieceSquareTables.combinedTables(false);
        int[][] chess960 = PieceSquareTables.combinedTables(true);

        for (byte piece = Board.whitePawn; piece <= Board.blackKing; piece++) {
            if (standard[piece] != null) {
                assertArrayEquals(standard[piece], chess960[piece], "Chess960 tables without a variant, piece " + piece);
            }
        }
    }

    private static void assertFirstRank(Chess960Variant variant, byte whitePiece, short expected) {
        int[][] tables = PieceSquareTables.buildCombinedTables(variant);

        for (int col = 0; col < 8; col++) {
            int field = ChessUtil.getFieldFromColAndRow(col, 0);

            assertEquals(expected, (short) tables[whitePiece][field], "first rank, col " + col);
        }
    }

    private static void assertOnlyMidgameFirstRankChanged(Chess960Variant variant, byte whitePiece) {
        int[][] standard = PieceSquareTables.combinedTables(false);
        int[][] tables = PieceSquareTables.buildCombinedTables(variant);

        for (int col = 0; col < 8; col++) {
            for (int row = 0; row < 8; row++) {
                int field = ChessUtil.getFieldFromColAndRow(col, row);
                short egStandard = (short) ((standard[whitePiece][field] + 0x8000) >> 16);
                short egVariant = (short) ((tables[whitePiece][field] + 0x8000) >> 16);

                assertEquals(egStandard, egVariant, "endgame value unchanged, col " + col + ", row " + row);

                if (row > 0) {
                    assertEquals((short) standard[whitePiece][field], (short) tables[whitePiece][field], "midgame value above rank 1 unchanged, col " + col + ", row " + row);
                }
            }
        }
    }
}
