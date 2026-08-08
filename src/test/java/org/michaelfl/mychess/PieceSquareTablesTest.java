package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural and spot-check tests for {@link PieceSquareTables}.
 *
 * <p>This test no longer duplicates the full table values (they are offline-tuned
 * and change with every tune — see {@code JointMgEgPstTaperedTexelData}). Instead
 * it pins the <em>invariants</em> that must hold whatever the tuned values are —
 * color antisymmetry (black = vertical mirror of white), board length, the
 * carry-corrected endgame unpacking, and the tapered midgame/endgame split — plus
 * a handful of anchor values that guard against an accidental table swap or
 * corruption. The exact playing values are validated by SPRT/fixed-N, not here.
 *
 * @author Michael Fleischhauer
 */
class PieceSquareTablesTest {

    private static final byte[][] WHITE_BLACK_PAIRS = {
            {Board.whitePawn, Board.blackPawn},
            {Board.whiteKnight, Board.blackKnight},
            {Board.whiteBishop, Board.blackBishop},
            {Board.whiteRook, Board.blackRook},
            {Board.whiteQueen, Board.blackQueen},
            {Board.whiteKing, Board.blackKing},
    };

    /**
     * The black table must be the exact vertical mirror (rank 1 &harr; 8) of the
     * white table, in both phases — this is the color antisymmetry the whole eval
     * relies on ({@code MirrorEvalTest}). Computed independently of
     * {@link PieceSquareTables}'s own {@code invert()} so it actually checks it.
     */
    @Test
    void blackTablesAreTheVerticalMirrorOfWhiteInBothPhases() {
        for (byte[] pair : WHITE_BLACK_PAIRS) {
            assertMirror(pair[0], pair[1], false);
            assertMirror(pair[0], pair[1], true);
        }
    }

    private static void assertMirror(byte white, byte black, boolean endgame) {
        short[] w = endgame ? PieceSquareTables.getEndGameTable(white) : PieceSquareTables.getMidGameTable(white);
        short[] b = endgame ? PieceSquareTables.getEndGameTable(black) : PieceSquareTables.getMidGameTable(black);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int whiteField = ChessUtil.getFieldFromColAndRow(col, row);
                int blackField = ChessUtil.getFieldFromColAndRow(col, 7 - row);

                assertEquals(w[whiteField], b[blackField],
                        "black " + (endgame ? "endgame" : "midgame") + " table must mirror white for piece pair "
                                + white + "/" + black + " at col " + col + " row " + row);
            }
        }
    }

    @Test
    void allTablesHaveBoardLength() {
        int expectedLength = Board.createEmptyRawBoard().length;

        for (byte[] pair : WHITE_BLACK_PAIRS) {
            for (byte piece : pair) {
                assertEquals(expectedLength, PieceSquareTables.getMidGameTable(piece).length,
                        "wrong midgame-table length for piece " + piece);
                assertEquals(expectedLength, PieceSquareTables.getEndGameTable(piece).length,
                        "wrong endgame-table length for piece " + piece);
            }
        }
    }

    /**
     * The endgame half is packed as {@code (eg << 16) + mg} and unpacked with the
     * make-score carry correction {@code (short) ((packed + 0x8000) >> 16)}. The
     * whole-table accessor ({@link PieceSquareTables#getEndGameTable}) and the
     * per-field accessor ({@link PieceSquareTables#getEndGameWeight}) must agree on
     * every square — a broken correction shows up as an off-by-one on negative
     * squares. Also asserts that negative endgame squares actually occur, so the
     * borrow case is exercised.
     */
    @Test
    void endgameUnpackingIsCarryCorrectedAndConsistent() {
        for (byte[] pair : WHITE_BLACK_PAIRS) {
            for (byte piece : pair) {
                short[] table = PieceSquareTables.getEndGameTable(piece);
                assertNotNull(table, "no endgame table for piece " + piece);

                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        int field = ChessUtil.getFieldFromColAndRow(col, row);

                        assertEquals(table[field], (short) PieceSquareTables.getEndGameWeight(piece, field),
                                "endgame-table vs per-field accessor disagree for piece " + piece
                                        + " at col " + col + " row " + row);
                    }
                }
            }
        }

        // Exercise the negative (borrow) case: the knight corners are strongly negative.
        assertTrue(PieceSquareTables.getEndGameWeight(Board.whiteKnight, Board.a8) < 0,
                "expected a negative knight endgame corner to exercise the carry-corrected unpacking");
    }

    /**
     * Since the full-joint tune, all six piece kinds have a genuine midgame/endgame
     * split — their endgame tables must differ from their midgame tables.
     */
    @Test
    void allPieceKindsHaveDivergingMidgameAndEndgameTables() {
        for (byte[] pair : WHITE_BLACK_PAIRS) {
            for (byte piece : pair) {
                assertFalse(
                        Arrays.equals(PieceSquareTables.getMidGameTable(piece), PieceSquareTables.getEndGameTable(piece)),
                        "midgame and endgame tables must differ (tapered) for piece " + piece);
            }
        }
    }

    /**
     * Anchor spot-checks: pin a few known loaded values so an accidental table swap
     * or corruption is caught. These are the current (v4.3.4 full-joint) values;
     * update them when the tables are re-tuned.
     */
    @Test
    void anchorValuesMatchTheLoadedTables() {
        assertEquals(-164, PieceSquareTables.getMidGameWeight(Board.whiteKnight, Board.a8), "knight MG a8");
        assertEquals(-5, PieceSquareTables.getMidGameWeight(Board.whiteKnight, Board.a1), "knight MG a1");
        assertEquals(-97, PieceSquareTables.getMidGameWeight(Board.whiteKing, Board.e1), "king MG e1");
        assertEquals(83, PieceSquareTables.getMidGameWeight(Board.whiteRook, Board.c8), "rook MG c8");
        assertEquals(236, PieceSquareTables.getEndGameWeight(Board.whitePawn, Board.d7), "pawn EG d7");
        assertEquals(110, PieceSquareTables.getEndGameWeight(Board.whiteKing, Board.e4), "king EG e4");
    }
}
