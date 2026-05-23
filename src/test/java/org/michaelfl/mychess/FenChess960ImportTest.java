package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEN import coverage for Chess960 / Fischer Random — the Phase 1 deliverable
 * of {@code docs/Chess960-project.md}.
 *
 * <p>Verifies that {@link Fen#importFEN} accepts both classical
 * {@code KQkq} castling-rights and the Shredder-FEN form ({@code A}-{@code H}
 * / {@code a}-{@code h}) and that the resulting {@link Board} carries the
 * correct castling-rook files. Regression checks for standard chess live in
 * {@link FenTest}; this class adds the new 960 surface and the
 * default-rook-file assertion that touches the new code path on the standard
 * FEN.
 *
 * @author Michael Fleischhauer
 */
class FenChess960ImportTest {

    private static final String STANDARD_FEN_CLASSIC =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String STANDARD_FEN_SHREDDER =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1";

    // ---- Standard chess: both notations populate the same internal state ----

    @Test
    void standardFen_classicalKQkq_populatesAllRightsAndDefaultRookFiles() {
        Board board = Fen.importFEN(STANDARD_FEN_CLASSIC);
        assertAllFourRightsAlive(board);
        assertRookFiles(board, 0, 7, 0, 7);
    }

    @Test
    void standardFen_shredderHAha_populatesIdenticalStateAsClassical() {
        Board board = Fen.importFEN(STANDARD_FEN_SHREDDER);
        assertAllFourRightsAlive(board);
        assertRookFiles(board, 0, 7, 0, 7);
    }

    // ---- Concrete Chess960 positions ----

    @Test
    void chess960_cutechessSamplePosition_parsesFAfa() {
        // King on b-file, rooks on a- and f-files
        Board board = Fen.importFEN(
                "rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1");
        assertAllFourRightsAlive(board);
        assertRookFiles(board, /*WQ*/ 0, /*WK*/ 5, /*BQ*/ 0, /*BK*/ 5);
    }

    @Test
    void chess960_scharnaglId0_parsesHFhf() {
        // King on g-file, rooks on f- and h-files
        Board board = Fen.importFEN(
                "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1");
        assertAllFourRightsAlive(board);
        assertRookFiles(board, /*WQ*/ 5, /*WK*/ 7, /*BQ*/ 5, /*BK*/ 7);
    }

    @Test
    void chess960_classicalKQkqNotation_resolvesToRookFilesByKingProximity() {
        // Same Chess960 position as the cutechess sample (rooks on a- and
        // f-files, king on b-file) but written with classical KQkq letters
        // instead of Shredder FAfa. The parser must locate the rooks by
        // scanning outward from the king, not by assuming a-/h-file
        // defaults — which is what would happen if classical notation
        // were treated as a hard-coded standard-chess shortcut.
        Board board = Fen.importFEN(
                "rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w KQkq - 0 1");
        assertAllFourRightsAlive(board);
        assertRookFiles(board, /*WQ*/ 0, /*WK*/ 5, /*BQ*/ 0, /*BK*/ 5);
    }

    @Test
    void chess960_scharnaglId959_parsesCAca() {
        // King on b-file, rooks on a- and c-files
        Board board = Fen.importFEN(
                "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1");
        assertAllFourRightsAlive(board);
        assertRookFiles(board, /*WQ*/ 0, /*WK*/ 2, /*BQ*/ 0, /*BK*/ 2);
    }

    // ---- Bulk round-trip over all 960 positions ----

    @Test
    void allChess960StartPositions_importCleanlyWithFourLiveRights() {
        for (int id = 0; id < Chess960StartPositions.COUNT; id++) {
            String fen = Chess960StartPositions.fenById(id);
            Board board = Fen.importFEN(fen);
            assertAllFourRightsAlive(board, "Scharnagl ID " + id);
        }
    }

    // ---- Partial-rights coverage ----

    @Test
    void onlyKingsideRights_classical_setsKKBitsOnly() {
        // Standard chess board but only "Kk" rights
        Board board = Fen.importFEN(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w Kk - 0 1");
        GameStatus status = board.getGameStatus();
        assertAll(
                () -> assertTrue(status.isWhiteCastlingKingSidePossible(), "white kingside alive"),
                () -> assertFalse(status.isWhiteCastlingQueenSidePossible(), "white queenside dead"),
                () -> assertTrue(status.isBlackCastlingKingSidePossible(), "black kingside alive"),
                () -> assertFalse(status.isBlackCastlingQueenSidePossible(), "black queenside dead")
        );
        // Queenside rook files stay at default (a-file)
        assertRookFiles(board, 0, 7, 0, 7);
    }

    @Test
    void emptyRights_dashField_leavesAllBitsClearAndDefaultRookFiles() {
        Board board = Fen.importFEN(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1");
        GameStatus status = board.getGameStatus();
        assertEquals(0, status.getCastlingState() & 0xF,
                "no castling-right bits should be set");
        assertRookFiles(board, 0, 7, 0, 7);
    }

    // ---- Mixed notations on a single position ----

    @Test
    void mixedClassicalAndShredder_onStandardBoard_acceptsBothInOneField() {
        // K (= h1 rook) + a (= a8 rook) — different colors, different notations
        Board board = Fen.importFEN(
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w Ka - 0 1");
        GameStatus status = board.getGameStatus();
        assertTrue(status.isWhiteCastlingKingSidePossible(), "white kingside set via K");
        assertTrue(status.isBlackCastlingQueenSidePossible(), "black queenside set via a");
        assertFalse(status.isWhiteCastlingQueenSidePossible(), "white queenside untouched");
        assertFalse(status.isBlackCastlingKingSidePossible(), "black kingside untouched");
    }

    // ---- Negative cases ----

    @ParameterizedTest
    @ValueSource(strings = { "X", "1", "?", "@", "I", "i", "Z" })
    void invalidCastlingChar_throws(String invalid) {
        String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w " + invalid + " - 0 1";
        assertThrows(IllegalArgumentException.class, () -> Fen.importFEN(fen),
                "FEN with castling char '" + invalid + "' must throw");
    }

    @Test
    void shredderLetter_pointingAtKingsOwnFile_throws() {
        // King on e1, "E" would claim a rook on e1 → must throw
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w E - 0 1"),
                "Shredder letter targeting king's own file must throw");
    }

    @Test
    void shredderLetter_pointingAtEmptySquare_throws() {
        // 4-rook position would be needed for "B" to mean something; standard
        // chess has no rook on the b-file, so "B" is impossible.
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w B - 0 1"),
                "Shredder letter without matching rook must throw");
    }

    @Test
    void classicalRightOnPositionWithoutRook_throws() {
        // King on e1 but no rook on either side → "K" cannot resolve
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/4K3 w K - 0 1"),
                "classical 'K' on king-only back rank must throw");
    }

    // ---- helpers ----

    private static void assertAllFourRightsAlive(Board board) {
        assertAllFourRightsAlive(board, "");
    }

    private static void assertAllFourRightsAlive(Board board, String context) {
        GameStatus status = board.getGameStatus();
        String suffix = context.isEmpty() ? "" : " (" + context + ")";
        assertAll(
                () -> assertTrue(status.isWhiteCastlingKingSidePossible(),  "white kingside" + suffix),
                () -> assertTrue(status.isWhiteCastlingQueenSidePossible(), "white queenside" + suffix),
                () -> assertTrue(status.isBlackCastlingKingSidePossible(),  "black kingside" + suffix),
                () -> assertTrue(status.isBlackCastlingQueenSidePossible(), "black queenside" + suffix)
        );
    }

    private static void assertRookFiles(Board board, int whiteQueenside, int whiteKingside,
                                        int blackQueenside, int blackKingside) {
        assertAll(
                () -> assertEquals(whiteQueenside, board.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE),
                        "white queenside rook file"),
                () -> assertEquals(whiteKingside,  board.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE),
                        "white kingside rook file"),
                () -> assertEquals(blackQueenside, board.getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE),
                        "black queenside rook file"),
                () -> assertEquals(blackKingside,  board.getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE),
                        "black kingside rook file")
        );
    }
}
