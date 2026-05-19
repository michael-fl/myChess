package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class FenTest {

    private static final String START_POSITION_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    void testStartPosition() {
        var game = new Game();

        assertEquals(START_POSITION_FEN, game.exportFEN(), "wrong FEN");
    }

    @Test
    void testPawnDoubleMove() {
        var game = new Game();

        game.makeMove(MoveDescription.fromString("e2-e4", game.getTurn()));

        String expectedFEN = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        assertEquals(expectedFEN, game.exportFEN(), "wrong FEN");
    }

    @Test
    void testPosition1() {
        GameImporter importer = GameImporter.importerFor("""
                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 b5 5. Bb3 Nf6 6. O-O a5 7. d4 a4 8. Bxf7+ Kxf7 9.
                dxe5 Ng8 10. Ng5+ Ke8 11. Nc3 b4 12. Qd5 Nh6 13. Nb5 a3 14. Rd1 Bb7
                """);
        var game = importer.importGame();

        String expectedFEN = "r2qkb1r/1bpp2pp/2n4n/1N1QP1N1/1p2P3/p7/PPP2PPP/R1BR2K1 w - - 2 15";
        assertEquals(expectedFEN, game.exportFEN(), "wrong FEN");
    }

    // ---- importFEN: round-trip and field-level coverage ----

    @ParameterizedTest
    @ValueSource(strings = {
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",                         // start
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",                      // after e2-e4
            "r2qkb1r/1bpp2pp/2n4n/1N1QP1N1/1p2P3/p7/PPP2PPP/R1BR2K1 w - - 2 15",                // mid-game, no rights
            "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",                   // both with rights, no ep
            "8/8/8/4k3/4K3/8/8/8 w - - 0 1",                                                    // bare kings endgame
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1",                                             // both can still castle, no minor pieces
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 b - - 99 50"                                       // edge: high halfmove clock, near 50-move
    })
    void testImportExportRoundTrip(String fen) {
        var board = Fen.importFEN(fen);

        assertEquals(fen, Fen.exportFEN(board),
                "FEN round-trip must be byte-equal for: " + fen);
    }

    @Test
    void testImportStartPositionMatchesNewGameHash() {
        var imported = Fen.importFEN(START_POSITION_FEN);
        var fresh = new Game();

        assertEquals(fresh.getBoard().getGameStatus().getPositionHash(),
                imported.getGameStatus().getPositionHash(),
                "Zobrist hash of FEN-imported start position must match the freshly constructed start position");
    }

    @Test
    void testImportEnPassantField() {
        var board = Fen.importFEN("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");

        assertEquals(Board.e3, board.getGameStatus().getEnPassantField(),
                "en-passant target square should be e3");
    }

    @Test
    void testImportNoEnPassant() {
        var board = Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        assertEquals(0, board.getGameStatus().getEnPassantField(),
                "en-passant field must be 0 when FEN has '-'");
    }

    @Test
    void testImportCastlingRightsFull() {
        var status = Fen.importFEN(START_POSITION_FEN).getGameStatus();

        assertTrue(status.isWhiteCastlingKingSidePossible(), "white K-side");
        assertTrue(status.isWhiteCastlingQueenSidePossible(), "white Q-side");
        assertTrue(status.isBlackCastlingKingSidePossible(), "black K-side");
        assertTrue(status.isBlackCastlingQueenSidePossible(), "black Q-side");
    }

    @Test
    void testImportCastlingRightsEmpty() {
        var status = Fen.importFEN("8/8/8/4k3/4K3/8/8/8 w - - 0 1").getGameStatus();

        assertFalse(status.isWhiteCastlingKingSidePossible(), "white K-side gone");
        assertFalse(status.isWhiteCastlingQueenSidePossible(), "white Q-side gone");
        assertFalse(status.isBlackCastlingKingSidePossible(), "black K-side gone");
        assertFalse(status.isBlackCastlingQueenSidePossible(), "black Q-side gone");
    }

    @Test
    void testImportCastlingRightsPartial() {
        var status = Fen.importFEN("r3k2r/8/8/8/8/8/8/R3K2R w Kq - 0 1").getGameStatus();

        assertTrue(status.isWhiteCastlingKingSidePossible(), "white K-side present");
        assertFalse(status.isWhiteCastlingQueenSidePossible(), "white Q-side absent");
        assertFalse(status.isBlackCastlingKingSidePossible(), "black K-side absent");
        assertTrue(status.isBlackCastlingQueenSidePossible(), "black Q-side present");
    }

    @Test
    void testImportSideToMoveBlack() {
        var status = Fen.importFEN("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1").getGameStatus();

        assertEquals(GameStatus.TURN_BLACK, status.getTurn(), "side to move should be black");
    }

    @Test
    void testImportPlyCountFromFullmoveAndTurn() {
        // 1 fullmove + white-to-move = plyCount 0
        assertEquals(0, Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
                .getGameStatus().getPlyCount());

        // 1 fullmove + black-to-move = plyCount 1
        assertEquals(1, Fen.importFEN("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
                .getGameStatus().getPlyCount());

        // 15 fullmoves + white-to-move = plyCount 28
        assertEquals(28, Fen.importFEN("r2qkb1r/1bpp2pp/2n4n/1N1QP1N1/1p2P3/p7/PPP2PPP/R1BR2K1 w - - 2 15")
                .getGameStatus().getPlyCount());
    }

    @Test
    void testImportHalfMoveClock() {
        var status = Fen.importFEN("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 b - - 99 50").getGameStatus();

        assertEquals(99, status.getHalfMoveClock(), "half-move clock should be 99");
    }

    // ---- importFEN: piece placement ----

    @Test
    void testImportPlacesPiecesAtCorrectSquares() {
        var board = Fen.importFEN("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");

        assertEquals(Board.whitePawn, board.getPieceAt(4, 3), "white pawn on e4");
        assertEquals(Board.empty, board.getPieceAt(4, 1), "e2 should be empty");
        assertEquals(Board.whiteRook, board.getPieceAt(0, 0), "white rook on a1");
        assertEquals(Board.blackKing, board.getPieceAt(4, 7), "black king on e8");
    }

    // ---- importFEN: negative cases ----

    @Test
    void testImportRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Fen.importFEN(null),
                "null FEN must throw");
    }

    @Test
    void testImportRejectsTooFewFields() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -"),
                "FEN with 5 fields must throw");
    }

    @Test
    void testImportRejectsTooManyFields() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 extra"),
                "FEN with 7 fields must throw");
    }

    @Test
    void testImportRejectsTooFewRows() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1"),
                "FEN with 7 rows must throw");
    }

    @Test
    void testImportRejectsRowTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPP/RNBQKBNR w KQkq - 0 1"),
                "row 'PPPPPPP' (7 cols) must throw");
    }

    @Test
    void testImportRejectsRowTooLong() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPPP/RNBQKBNR w KQkq - 0 1"),
                "row 'PPPPPPPPP' (9 cols) must throw");
    }

    @Test
    void testImportRejectsInvalidPieceChar() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbXr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
                "invalid piece 'X' must throw");
    }

    @Test
    void testImportRejectsInvalidSideToMove() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1"),
                "side-to-move 'x' must throw");
    }

    @Test
    void testImportRejectsInvalidCastlingChar() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KZkq - 0 1"),
                "invalid castling char 'Z' must throw");
    }

    @Test
    void testImportRejectsBadEnPassantField() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq z9 0 1"),
                "en-passant field 'z9' must throw");
    }

    @Test
    void testImportRejectsNonIntegerHalfMoveClock() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - x 1"),
                "non-integer half-move clock must throw");
    }

    @Test
    void testImportRejectsFullMoveBelow1() {
        assertThrows(IllegalArgumentException.class,
                () -> Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0"),
                "full-move number 0 must throw");
    }
}
