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

    // ---- exportFEN / exportShredderFEN: caller-chosen castling notation ----
    //
    // After the heuristic was removed there are two explicit paths:
    //   - exportFEN(Board)         → classical KQkq letters (board-agnostic).
    //   - exportShredderFEN(Board) → Shredder file letters derived from
    //                                Board.castlingRookFiles.
    // Callers pick whichever the consumer (GUI, persistence layer, ...) wants.

    @Test
    void exportFen_classical_standardChessStartPosition_emitsKQkq() {
        var board = Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
                Fen.exportFEN(board),
                "exportFEN must always emit classical KQkq letters on a standard-chess board");
    }

    @Test
    void exportFen_classical_chess960Position_stillEmitsKQkqLetters() {
        // exportFEN ignores rook files entirely — it only looks at the four
        // castling-right bits in GameStatus and emits K/Q/k/q accordingly.
        // For a 960 board with all four rights alive that still yields KQkq.
        // Acceptable X-FEN output for non-960-aware consumers; full
        // disambiguation requires exportShredderFEN.
        var board = Fen.importFEN("rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1");

        assertEquals("rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w KQkq - 0 1",
                Fen.exportFEN(board),
                "exportFEN on a 960 board still emits classical KQkq letters");
    }

    @Test
    void exportShredderFen_standardChessStartPosition_emitsHAha() {
        // Standard chess has rook files {0,7} → Shredder letters 'A' and 'H'.
        // Even though KQkq would be more idiomatic, exportShredderFEN always
        // emits the file-letter form — that is the contract of the method name.
        var board = Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1",
                Fen.exportShredderFEN(board),
                "exportShredderFEN must emit 'HAha' on standard chess (rook files a/h)");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            // Scharnagl ID 0 — BBQNNRKR, king g1, rooks f1/h1 → Shredder 'HFhf'.
            "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1",
            // Scharnagl ID 404 — RBBQNNKR, king g1, rooks a1/h1 → 'HAha'.
            // The exact case that defeated the old heuristic-based exporter
            // (rook files match standard-chess defaults but the king is NOT
            // on e1 — the old code decided "looks like standard chess, emit
            // KQkq" and lost the disambiguation).
            "rbbqnnkr/pppppppp/8/8/8/8/PPPPPPPP/RBBQNNKR w HAha - 0 1",
            // Scharnagl ID 959 — RKRNNQBB, king b1, rooks a1/c1 → 'CAca'.
            "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1",
            // Cutechess sample 960 position — RKBBNRNQ, king b1, rooks a1/f1 → 'FAfa'.
            "rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1"
    })
    void exportShredderFen_startPositionRoundTrips(String shredderFen) {
        var board = Fen.importFEN(shredderFen);

        assertEquals(shredderFen, Fen.exportShredderFEN(board),
                "Shredder-form starting FEN must round-trip through import + export");
    }

    @Test
    void exportShredderFen_allChess960StartPositions_roundTrip() {
        // Bulk round-trip: each of the 960 Scharnagl positions, imported and
        // re-exported through the Shredder writer, must reproduce the
        // original Shredder FEN byte-for-byte. This includes the previously
        // problematic cases like ID 404 where rook files match defaults
        // but the king is on a non-e file.
        for (int id = 0; id < Chess960StartPositions.COUNT; id++) {
            String fen = Chess960StartPositions.fenById(id);
            var board = Fen.importFEN(fen);

            assertEquals(fen, Fen.exportShredderFEN(board),
                    "Scharnagl ID " + id + " must round-trip Shredder-clean");
        }
    }

    @Test
    void castlingStateShredder_partialRights_emitsOnlyAliveSlots() {
        // Drop white queenside by clearing that single bit on a 960 board.
        // The resulting Shredder field should be three letters (HFhf minus 'F').
        String fullRightsFen = "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1";
        var board = Fen.importFEN(fullRightsFen);
        var original = board.getGameStatus();

        int reducedState = original.getCastlingState() & ~GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE;
        var reducedStatus = new GameStatus(
                original.getPlyCount(),
                original.getTurn(),
                original.getLastMove(),
                original.getHalfMoveClock(),
                reducedState,
                original.getEnPassantField(),
                original.getPositionHash(),
                new int[2]);

        assertEquals("Hhf", Fen.castlingStateShredder(reducedStatus, board),
                "missing white-queenside slot must drop 'F' from 'HFhf'");
    }

    @Test
    void castlingStateShredder_noRights_emitsDash() {
        // Same 960 back rank but castling rights manually set to -.
        // Verifies the dash fallback fires through the Shredder writer too.
        var board = Fen.importFEN("bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w - - 0 1");

        assertEquals("-", Fen.castlingStateShredder(board.getGameStatus(), board),
                "no castling rights on a 960 board must emit '-' from the Shredder writer");
    }

    @Test
    void castlingState_classical_unchangedByShredderAddition() {
        // The single-argument castlingState(GameStatus) overload is unchanged
        // and remains the classical KQkq emitter; the two paths are now
        // sibling APIs, not heuristically dispatched.
        var board = Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        assertEquals("KQkq", Fen.castlingState(board.getGameStatus()),
                "single-arg castlingState must still emit the classical letters");
    }

    // -------------------------------------------------------------------
    // Fen.requireStartFen — strict "is this a game-starting position?"
    // validator. Accepts exactly the 960 canonical starting FENs from
    // Chess960StartPositions (standard chess is position 518, the 959
    // Chess960 setups make up the rest); rejects everything else.
    // -------------------------------------------------------------------

    private static final String CHESS960_START_FEN =
            "bnrqkrnb/pppppppp/8/8/8/8/PPPPPPPP/BNRQKRNB w KQkq - 0 1";

    @Test
    void requireStartFen_acceptsStandardStart() {
        assertDoesNotThrow(() -> Fen.requireStartFen(START_POSITION_FEN),
                "canonical standard-chess starting FEN must be accepted");
    }

    @Test
    void requireStartFen_acceptsChess960Setup() {
        assertDoesNotThrow(() -> Fen.requireStartFen(CHESS960_START_FEN),
                "corner-bishop Chess960 starting FEN must be accepted");
    }

    @Test
    void requireStartFen_acceptsAllChess960StartPositions() {
        // Every canonical FEN produced by Chess960StartPositions must
        // pass the validator — the two are backed by the same underlying
        // set, so this is a smoke test for wiring.
        for (int id = 0; id < Chess960StartPositions.COUNT; id++) {
            var fen = Chess960StartPositions.fenById(id);
            assertDoesNotThrow(() -> Fen.requireStartFen(fen),
                    "canonical FEN #" + id + " must be accepted: " + fen);
        }
    }

    @Test
    void requireStartFen_acceptsShredderCastlingRights() {
        // Position 0 has back rank BBQNNRKR: kingside rook on h1, queenside
        // rook on f1. Its Shredder-form starting FEN uses HFhf for castling
        // rights (uppercase = White files, lowercase = Black files). Verify
        // the validator accepts this form alongside the classical KQkq
        // shorthand.
        var shredderFen = "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1";

        assertDoesNotThrow(() -> Fen.requireStartFen(shredderFen),
                "Shredder-FEN castling-rights spelling must be accepted");
    }

    @Test
    void requireStartFen_rejectsNull() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> Fen.requireStartFen(null));

        assertTrue(ex.getMessage().contains("must not be null"), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Mid-game standard chess (position after 1. e4).
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
            // Starting layout but Black to move.
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1",
            // Starting layout but partial castling rights.
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w Kk - 0 1",
            // Starting layout but with an en-passant square set.
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq e3 0 1",
            // Starting layout but non-zero half-move clock.
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 5 1",
            // Starting layout but full-move number > 1.
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 5",
            // Asymmetric back ranks (RNBQKBNR vs. RBNQKBNR).
            "rbnqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            // Malformed field count (5 fields instead of 6).
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq",
            // Complete garbage.
            "not a fen at all"
    })
    void requireStartFen_rejectsNonStartingFens(String fen) {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> Fen.requireStartFen(fen));

        assertTrue(ex.getMessage().contains("not a valid game starting position"),
                "error message should identify the strict-startFen contract, got: " + ex.getMessage());
    }
}
