package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class BoardTest {

    private static MoveGenerator newGen() {
        return new MoveGenerator(MoveSorter.defaultImplementation());
    }

    private static int playedMove(Game game, String notation) {
        var moveDescr = MoveDescription.fromString(notation, game.getTurn());
        var move = game.getBoard().moveDescriptionToMove(
                game.getBoard().resolveMoveDescription(moveDescr, newGen()));
        return move.move();
    }

    /**
     * Exhaustive directional coverage for the attack detector behind
     * {@link Board#isKingChecked()} and {@link Board#canCaptureOpposingKing()}.
     * For each piece type, every direction from which that piece can deliver
     * an attack is exercised by a dedicated FEN.
     *
     * <p>Most cases follow the pattern <em>black attacker → white king, white
     * to move</em>: that makes {@code isKingChecked} the truthy assertion
     * (white's king is under attack) and {@code canCaptureOpposingKing} the
     * falsy one (white's only piece is its king, which doesn't reach the
     * remote black king). King-adjacency cases trip both methods because
     * both kings then attack each other. A few sanity cases exercise the
     * other colour, the no-attack baseline, and a slider blocked by an
     * intervening piece.
     */
    static Stream<Arguments> attackDetectionCases() {
        return Stream.of(
                // ---- Pawn (direction-dependent on colour) ----
                Arguments.of("black pawn d5 attacks white king e4 (down-left)",
                        "k7/8/8/3p4/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black pawn f5 attacks white king e4 (down-right)",
                        "k7/8/8/5p2/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("white pawn d4 attacks black king e5 (up-right)",
                        "8/8/8/4k3/3P4/8/8/K7 b - - 0 1", true, false),
                Arguments.of("white pawn f4 attacks black king e5 (up-left)",
                        "8/8/8/4k3/5P2/8/8/K7 b - - 0 1", true, false),

                // ---- Knight (all 8 L-shapes around e4) ----
                Arguments.of("black knight d6 attacks white king e4",
                        "k7/8/3n4/8/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black knight f6 attacks white king e4",
                        "k7/8/5n2/8/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black knight g5 attacks white king e4",
                        "k7/8/8/6n1/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black knight g3 attacks white king e4",
                        "k7/8/8/8/4K3/6n1/8/8 w - - 0 1", true, false),
                Arguments.of("black knight f2 attacks white king e4",
                        "k7/8/8/8/4K3/8/5n2/8 w - - 0 1", true, false),
                Arguments.of("black knight d2 attacks white king e4",
                        "k7/8/8/8/4K3/8/3n4/8 w - - 0 1", true, false),
                Arguments.of("black knight c3 attacks white king e4",
                        "k7/8/8/8/4K3/2n5/8/8 w - - 0 1", true, false),
                Arguments.of("black knight c5 attacks white king e4",
                        "k7/8/8/2n5/4K3/8/8/8 w - - 0 1", true, false),

                // ---- Bishop (all 4 diagonals from e4) ----
                Arguments.of("black bishop a8 attacks white king e4 (up-left diagonal)",
                        "b6k/8/8/8/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black bishop h7 attacks white king e4 (up-right diagonal)",
                        "k7/7b/8/8/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black bishop h1 attacks white king e4 (down-right diagonal)",
                        "k7/8/8/8/4K3/8/8/7b w - - 0 1", true, false),
                Arguments.of("black bishop b1 attacks white king e4 (down-left diagonal)",
                        "k7/8/8/8/4K3/8/8/1b6 w - - 0 1", true, false),

                // ---- Rook (all 4 orthogonals from e4) ----
                Arguments.of("black rook e8 attacks white king e4 (up the file)",
                        "4r2k/8/8/8/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black rook e1 attacks white king e4 (down the file)",
                        "k7/8/8/8/4K3/8/8/4r3 w - - 0 1", true, false),
                Arguments.of("black rook h4 attacks white king e4 (rank, from the right)",
                        "k7/8/8/8/4K2r/8/8/8 w - - 0 1", true, false),
                Arguments.of("black rook a4 attacks white king e4 (rank, from the left)",
                        "7k/8/8/8/r3K3/8/8/8 w - - 0 1", true, false),

                // ---- Queen (all 8 directions from e4) ----
                Arguments.of("black queen a8 attacks white king e4 (up-left diagonal)",
                        "q6k/8/8/8/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black queen h7 attacks white king e4 (up-right diagonal)",
                        "k7/7q/8/8/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black queen h1 attacks white king e4 (down-right diagonal)",
                        "k7/8/8/8/4K3/8/8/7q w - - 0 1", true, false),
                Arguments.of("black queen b1 attacks white king e4 (down-left diagonal)",
                        "k7/8/8/8/4K3/8/8/1q6 w - - 0 1", true, false),
                Arguments.of("black queen e8 attacks white king e4 (up the file)",
                        "4q2k/8/8/8/4K3/8/8/8 w - - 0 1", true, false),
                Arguments.of("black queen e1 attacks white king e4 (down the file)",
                        "k7/8/8/8/4K3/8/8/4q3 w - - 0 1", true, false),
                Arguments.of("black queen h4 attacks white king e4 (rank, from the right)",
                        "k7/8/8/8/4K2q/8/8/8 w - - 0 1", true, false),
                Arguments.of("black queen a4 attacks white king e4 (rank, from the left)",
                        "7k/8/8/8/q3K3/8/8/8 w - - 0 1", true, false),

                // ---- King adjacency (all 8 squares around e4) ----
                Arguments.of("kings touching: black king d5 (up-left of e4)",
                        "8/8/8/3k4/4K3/8/8/8 w - - 0 1", true, true),
                Arguments.of("kings touching: black king e5 (up of e4)",
                        "8/8/8/4k3/4K3/8/8/8 w - - 0 1", true, true),
                Arguments.of("kings touching: black king f5 (up-right of e4)",
                        "8/8/8/5k2/4K3/8/8/8 w - - 0 1", true, true),
                Arguments.of("kings touching: black king d4 (left of e4)",
                        "8/8/8/8/3kK3/8/8/8 w - - 0 1", true, true),
                Arguments.of("kings touching: black king f4 (right of e4)",
                        "8/8/8/8/4Kk2/8/8/8 w - - 0 1", true, true),
                Arguments.of("kings touching: black king d3 (down-left of e4)",
                        "8/8/8/8/4K3/3k4/8/8 w - - 0 1", true, true),
                Arguments.of("kings touching: black king e3 (down of e4)",
                        "8/8/8/8/4K3/4k3/8/8 w - - 0 1", true, true),
                Arguments.of("kings touching: black king f3 (down-right of e4)",
                        "8/8/8/8/4K3/5k2/8/8 w - - 0 1", true, true),

                // ---- Colour sanity: white piece attacking black king ----
                Arguments.of("white queen h8 attacks black king e8 (rank, from the right)",
                        "4k2Q/8/8/8/8/8/8/4K3 b - - 0 1", true, false),
                Arguments.of("white knight d6 attacks black king e8",
                        "4k3/8/3N4/8/8/8/8/4K3 b - - 0 1", true, false),
                Arguments.of("white bishop a4 attacks black king e8 (up-right diagonal)",
                        "4k3/8/8/8/B7/8/8/4K3 b - - 0 1", true, false),

                // ---- canCaptureOpposingKing positive: side to move can capture opposing king ----
                Arguments.of("queen on e2: side-to-move (black) can capture white king e1",
                        "4k3/8/8/8/8/8/4q3/4K3 b - - 0 1", false, true),

                // ---- Negative / edge cases ----
                Arguments.of("bare kings, no attacks",
                        "4k3/8/8/8/8/8/8/4K3 w - - 0 1", false, false),
                Arguments.of("diagonal slider blocked by intervening piece",
                        "k7/8/8/b7/8/2P5/8/4K3 w - - 0 1", false, false),
                Arguments.of("file slider blocked by intervening piece",
                        "k3r3/8/8/8/4P3/8/8/4K3 w - - 0 1", false, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attackDetectionCases")
    void attackDetection(String name, String fen, boolean isCheck, boolean canCaptureKing) {
        var board = Fen.importFEN(fen);
        assertEquals(isCheck, board.isKingChecked(), name + " — isKingChecked");
        assertEquals(canCaptureKing, board.canCaptureOpposingKing(), name + " — canCaptureOpposingKing");
    }

    @Test
    void testIsKingChecked() {
        var pgn = """
                1. c4 e5 2. Nc3 Nf6 3. a3 Nc6 4. e4 Nd4 5. Nf3 d6 6. h3 Be7 7. Be2 O-O 8. O-O
                h6 9. d3 a6 10. Be3 c5 11. Nd5 Be6 12. Nxd4 cxd4 13. Bd2 Nxd5 14. cxd5 Bd7 15. Rc1
                Rc8 16. Bg4 f5 17. Bxf5 Bxf5 18. exf5 Rxf5 19. Qg4 Rxc1 20. Rxc1 Qf8 21. Bxh6
                Kh7 22. Bxg7 Qf7 23. Rc8 Qg6
                """;
        var game = GameImporter.importerFor(pgn).importGame();

        var board = game.getBoard();
        assertFalse(board.isKingChecked(), "King should not yet be checked");

        game.makeMove(MoveDescription.fromString("Rh8+", board.getGameStatus().getTurn()));
        assertTrue(board.isKingChecked(), "King should now be checked");

        game.makeMove(MoveDescription.fromString("Kxg7", board.getGameStatus().getTurn()));
        assertFalse(board.isKingChecked(), "King should no longer be checked");
    }

    static Stream<Arguments> checkmateCases() {
        return Stream.of(
                Arguments.of("Scholar's mate",
                        """
                        1. e4 e5
                        2. Qh5 Nc6
                        3. Bc4 Nf6??
                        """,
                        "Qxf7"),
                Arguments.of("Fool's mate",
                        """
                        1. f3 e6
                        2. g4
                        """,
                        "Qh4"),
                Arguments.of("Smothered mate",
                        """
                        1. e4 c6 2. d4 d5
                        3. Nc3 dxe4
                        4. Nxe4 Nd7
                        5. Qe2 Ngf6
                        """,
                        "Nd6#")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("checkmateCases")
    void testIsCheckmate(String name, String setupPgn, String matingMove) {
        var game = GameImporter.importerFor(setupPgn).importGame();
        var moveGenerator = newGen();
        var board = game.getBoard();

        assertFalse(board.isCheckmate(moveGenerator),
                name + ": should not yet be checkmate");
        game.makeMove(MoveDescription.fromString(matingMove, board.getGameStatus().getTurn()));
        assertTrue(board.isCheckmate(moveGenerator),
                name + ": should be checkmate after " + matingMove);
    }

    // ---------- make / revert symmetry per move type ----------

    private static void assertMakeRevertIsSymmetric(String setupPgn, String moveNotation) {
        var game = setupPgn.isEmpty()
                ? new Game()
                : GameImporter.importerFor(setupPgn).importGame();
        var board = game.getBoard();

        var fenBefore = board.exportFEN();
        var hashBefore = board.getGameStatus().getPositionHash();
        var rawBefore = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);
        var plyBefore = board.getGameStatus().getPlyCount();

        int packedMove = playedMove(game, moveNotation);
        board.makeMove(packedMove);
        assertNotEquals(hashBefore, board.getGameStatus().getPositionHash(),
                "hash must change after move " + moveNotation);
        assertEquals(plyBefore + 1, board.getGameStatus().getPlyCount(),
                "ply count must advance");

        board.revertMove();
        assertEquals(hashBefore, board.getGameStatus().getPositionHash(),
                "hash must return after revert of " + moveNotation);
        assertArrayEquals(rawBefore, board.getRawBoard(),
                "raw board must match after revert of " + moveNotation);
        assertEquals(fenBefore, board.exportFEN(),
                "FEN must match after revert of " + moveNotation);
        assertEquals(plyBefore, board.getGameStatus().getPlyCount(),
                "ply count must return after revert");
    }

    @Test
    void testMakeRevert_normalMove() {
        assertMakeRevertIsSymmetric("", "e2-e4");
    }

    @Test
    void testMakeRevert_capture() {
        assertMakeRevertIsSymmetric("1. e4 d5", "exd5");
    }

    @Test
    void testMakeRevert_castlingKingSide() {
        var setup = """
                1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. Nc3 Nf6
                """;
        assertMakeRevertIsSymmetric(setup, "O-O");
    }

    @Test
    void testMakeRevert_castlingQueenSide() {
        var setup = """
                1. d4 d5 2. Nc3 Nc6 3. Bf4 Bf5 4. Qd2 Qd7
                """;
        assertMakeRevertIsSymmetric(setup, "O-O-O");
    }

    @Test
    void testMakeRevert_promotionQueen() {
        var setup = "1. a4 h5 2. a5 h4 3. a6 h3 4. axb7 hxg2";
        assertMakeRevertIsSymmetric(setup, "bxa8Q");
    }

    @Test
    void testMakeRevert_promotionKnight() {
        var setup = "1. a4 h5 2. a5 h4 3. a6 h3 4. axb7 hxg2";
        assertMakeRevertIsSymmetric(setup, "bxa8N");
    }

    @Test
    void testMakeRevert_promotionRook() {
        var setup = "1. a4 h5 2. a5 h4 3. a6 h3 4. axb7 hxg2";
        assertMakeRevertIsSymmetric(setup, "bxa8R");
    }

    @Test
    void testMakeRevert_promotionBishop() {
        var setup = "1. a4 h5 2. a5 h4 3. a6 h3 4. axb7 hxg2";
        assertMakeRevertIsSymmetric(setup, "bxa8B");
    }

    @Test
    void testMakeRevert_enPassant() {
        var setup = "1. e4 h6 2. e5 f5";
        assertMakeRevertIsSymmetric(setup, "exf6");
    }

    // ---------- castling state transitions ----------

    @Test
    void castlingRightsClearedWhenKingMoves() {
        var game = GameImporter.importerFor("1. e4 e5 2. Ke2").importGame();
        var castlingState = game.getBoard().getGameStatus().getCastlingState();
        assertEquals(0,
                castlingState & GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE,
                "white king-side castling must be cleared after Ke2");
        assertEquals(0,
                castlingState & GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE,
                "white queen-side castling must be cleared after Ke2");
        assertNotEquals(0,
                castlingState & GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE,
                "black king-side castling must still be possible");
    }

    @Test
    void castlingRightsClearedWhenRookMoves() {
        var game = GameImporter.importerFor("1. a4 a5 2. Ra3").importGame();
        var castlingState = game.getBoard().getGameStatus().getCastlingState();
        assertEquals(0,
                castlingState & GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE,
                "white queen-side castling must be cleared after a-rook moved");
        assertNotEquals(0,
                castlingState & GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE,
                "white king-side castling must still be possible");
    }

    @Test
    void blackCastlingRightsClearedWhenKingMoves() {
        var game = GameImporter.importerFor("1. e4 e5 2. Nf3 Ke7").importGame();
        var castlingState = game.getBoard().getGameStatus().getCastlingState();
        assertEquals(0,
                castlingState & GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE,
                "black king-side castling must be cleared after Ke7");
        assertEquals(0,
                castlingState & GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE,
                "black queen-side castling must be cleared after Ke7");
        assertNotEquals(0,
                castlingState & GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE,
                "white king-side castling must still be possible");
    }

    @Test
    void blackCastlingRightsClearedWhenRookMoves() {
        var game = GameImporter.importerFor("1. a4 a5 2. Nf3 Ra6").importGame();
        var castlingState = game.getBoard().getGameStatus().getCastlingState();
        assertEquals(0,
                castlingState & GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE,
                "black queen-side castling must be cleared after a-rook moved");
        assertNotEquals(0,
                castlingState & GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE,
                "black king-side castling must still be possible");
    }

    @Test
    void castlingHasCastledFlagSetAfterCastling() {
        var game = GameImporter.importerFor("""
                1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. Nc3 Nf6 5. O-O
                """).importGame();
        var castlingState = game.getBoard().getGameStatus().getCastlingState();
        assertNotEquals(0,
                castlingState & GameStatus.BIT_WHITE_HAS_CASTLED,
                "white-has-castled bit must be set after O-O");
    }

    // ---------- moveToShortNotation disambiguation ----------

    @Test
    void shortNotationDisambiguatesByFileWhenTwoKnightsCanReach() {
        // Knights on b1 and f3 both reach d2.
        var game = GameImporter.importerFor("1. Nf3 a6 2. e4 a5 3. d4 a4").importGame();
        var board = game.getBoard();
        var resolved = board.resolveMoveDescription(
                MoveDescription.fromString("Nbd2", game.getTurn()), newGen());
        var move = board.moveDescriptionToMove(resolved);
        var san = board.moveToShortNotation(move).toString();
        assertEquals("Nbd2", san,
                "Notation must include the source file when two knights can reach the same square");
    }

    // ---------- ambiguity rejection ----------

    @Test
    void resolveMoveDescription_rejectsAmbiguousInput() {
        // Knights on b1 and f3 both reach d2 — plain "Nd2" must fail.
        var game = GameImporter.importerFor("1. Nf3 a6 2. e4 a5 3. d4 a4").importGame();
        var moveDescr = MoveDescription.fromString("Nd2", game.getTurn());
        var ex = assertThrows(IllegalMoveException.class,
                () -> game.getBoard().resolveMoveDescription(moveDescr, newGen()),
                "Nd2 must be ambiguous when both knights can reach d2");
        assertTrue(ex.getMessage().toLowerCase().contains("unique")
                        || ex.getMessage().toLowerCase().contains("ambig"),
                "Exception message should hint at ambiguity: " + ex.getMessage());
    }

    // ---------- status-stack snapshot ----------

    @Test
    void getGameStatusStackCopy_isIndependent() {
        var game = GameImporter.importerFor("1. e4 e5").importGame();
        List<GameStatus> stack = game.getBoard().getGameStatusStackCopy();
        var sizeBefore = stack.size();

        game.makeMove(MoveDescription.fromString("Nf3", game.getTurn()));

        assertEquals(sizeBefore, stack.size(),
                "Previously taken stack copy must be unaffected by subsequent moves");
    }

    // ---------- copy() ----------

    @Test
    void copy_producesIndependentBoard() {
        var game = GameImporter.importerFor("1. e4 e5 2. Nf3 Nc6").importGame();
        var original = game.getBoard();
        var copy = original.copy();

        assertEquals(original.exportFEN(), copy.exportFEN(), "Copy must start with the same FEN");
        assertEquals(original.getGameStatus().getPositionHash(),
                copy.getGameStatus().getPositionHash(),
                "Copy must start with the same hash");

        // Mutate the copy and verify the original is untouched.
        var moveDescr = MoveDescription.fromString("Bb5", copy.getGameStatus().getTurn());
        var resolved = copy.resolveMoveDescription(moveDescr, newGen());
        copy.makeMove(copy.moveDescriptionToMove(resolved).move());

        assertNotEquals(original.exportFEN(), copy.exportFEN(),
                "Mutating the copy must not affect the original FEN");
    }

    // ---------- isChess960Position ----------
    //
    // Direct unit tests for the detector that decides whether a given Board
    // represents a 960 game. Covers the three positive paths documented in
    // the implementation (non-default rook files, custom king file with live
    // castling rights, ambiguity-resolution via isStandardStartPosition) and
    // the standard-chess negative paths.

    @Test
    void isChess960Position_standardChessStartPosition_returnsFalse() {
        var board = Board.createNewGame();

        assertFalse(board.isChess960Position(),
                "the standard chess start position is not a 960 game");
    }

    @Test
    void isChess960Position_standardChessAfterE2E4_returnsFalse() {
        var game = GameImporter.importerFor("1. e4").importGame();

        assertFalse(game.getBoard().isChess960Position(),
                "standard chess mid-game (after 1. e4) is not a 960 game");
    }

    @Test
    void isChess960Position_standardChessAfterCastling_returnsFalse() {
        // After both sides have castled the castling-right hints are gone,
        // so the detector has to fall back on the structural heuristic.
        var game = GameImporter.importerFor("""
                1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. O-O Nf6 5. Nc3 O-O
                """).importGame();

        assertFalse(game.getBoard().isChess960Position(),
                "a standard-chess mid-game position with both sides castled is not a 960 game");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1",   // cutechess sample
            "bbqnnrkr/pppppppp/8/8/8/8/PPPPPPPP/BBQNNRKR w HFhf - 0 1",   // Scharnagl ID 0, rooks on f/h
            "rkrnnqbb/pppppppp/8/8/8/8/PPPPPPPP/RKRNNQBB w CAca - 0 1",   // Scharnagl ID 959, rooks on a/c
            "rbbqnnkr/pppppppp/8/8/8/8/PPPPPPPP/RBBQNNKR w HAha - 0 1"    // Scharnagl ID 404: rook files
                                                                          // happen to be {0, 7}, king on g
    })
    void isChess960Position_chess960StartPositions_returnsTrue(String fen) {
        var board = Fen.importFEN(fen);

        assertTrue(board.isChess960Position(),
                "FEN must be detected as 960: " + fen);
    }

    @Test
    void isChess960Position_allChess960StartPositions_returnsTrueExceptId518() {
        // Walks every Scharnagl-numbered start position. Standard chess
        // (ID 518) is the single exception that must return false; the
        // remaining 959 must all return true.
        for (int id = 0; id < Chess960StartPositions.COUNT; id++) {
            String fen = Chess960StartPositions.fenById(id);
            var board = Fen.importFEN(fen);

            boolean expected = id != Chess960StartPositions.STANDARD_CHESS_ID;
            assertEquals(expected, board.isChess960Position(),
                    "Scharnagl ID " + id + " (" + fen + ")");
        }
    }

    // ---------- isStandardStartPosition ----------
    //
    // Byte-for-byte equality check against Board.createNewGame(). Covers the
    // obvious positive and negative paths, plus a sanity check that every
    // 960 starting position with rooks at the standard a-/h-files is still
    // rejected (because of the rest of the back rank differing).

    @Test
    void isStandardStartPosition_freshStandardGame_returnsTrue() {
        var board = Board.createNewGame();

        assertTrue(board.isStandardStartPosition(),
                "Board.createNewGame() must satisfy isStandardStartPosition");
    }

    @Test
    void isStandardStartPosition_importedStandardStartFen_returnsTrue() {
        var board = Fen.importFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        assertTrue(board.isStandardStartPosition(),
                "FEN-imported standard chess start position must match");
    }

    @Test
    void isStandardStartPosition_afterFirstMove_returnsFalse() {
        var game = GameImporter.importerFor("1. e4").importGame();

        assertFalse(game.getBoard().isStandardStartPosition(),
                "any move played ⇒ no longer the start position");
    }

    @Test
    void isStandardStartPosition_chess960StartPosition_returnsFalse() {
        var board = Fen.importFEN("rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1");

        assertFalse(board.isStandardStartPosition(),
                "a 960 start position has a different back rank ⇒ not the standard start");
    }

    @Test
    void isStandardStartPosition_allChess960StartPositions_returnsFalseExceptId518() {
        // Every Scharnagl position EXCEPT the standard chess one (ID 518)
        // must be rejected by isStandardStartPosition. ID 518 itself must
        // be accepted, since it IS standard chess.
        for (int id = 0; id < Chess960StartPositions.COUNT; id++) {
            String fen = Chess960StartPositions.fenById(id);
            var board = Fen.importFEN(fen);

            boolean expected = id == Chess960StartPositions.STANDARD_CHESS_ID;
            assertEquals(expected, board.isStandardStartPosition(),
                    "Scharnagl ID " + id + " (" + fen + ")");
        }
    }

    // ---------- is960 cached flag ----------

    @Test
    void is960_isCarriedByCopyConstructor() {
        // The is960 flag is computed once in Board's main constructor and
        // stored as a final field. The copy constructor must carry it over
        // verbatim — without that, every Board.copy() (used by the engine's
        // root-of-search snapshot) would silently drop the variant signal
        // and route castling through the standard-chess code path, corrupting
        // move generation on a 960 game.
        var original = Fen.importFEN("rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1");
        assertTrue(original.isChess960(), "precondition: imported 960 FEN must be detected as 960");

        var copy = original.copy();

        assertTrue(copy.isChess960(), "Board.copy() must preserve the is960 flag");
    }

    // ---------- Board.makeMove for Chess960 castling ----------
    //
    // The four _makeCastlingXxxSideMove implementations in Board today
    // dispatch on `fromField == e1` to tell white from black, and they
    // hard-code the rook squares as h1/f1 / a1/d1 (white) and h8/f8 /
    // a8/d8 (black). In a 960 game where the king starts on a non-e
    // file, the dispatch sends the white move into the black branch
    // (and vice versa); even when the rook squares happen to coincide
    // with the standard chess defaults, the incremental Zobrist update
    // still XORs in/out keys for e1/e8/h1/h8 instead of the actual
    // king and rook source squares — so the stored position hash
    // diverges from a fresh recomputation off the resulting board.
    //
    // Each test below asserts both:
    //   1) the expected post-move board state (king on g/c file, rook
    //      on f/d file, source squares empty);
    //   2) hash consistency — getPositionHash() must equal a fresh
    //      calculatePositionHash() off the same raw board + game status.
    //
    // For the two black tests the buggy code happens to leave a
    // board-correct result (the hard-coded h8/f8 and a8/d8 squares
    // coincide with the test setup's rook files), so assertion (2)
    // is the failure mode there. Assertion (1) still pins the
    // expected board for documentation and for the case where a
    // future bug breaks the board too.

    @Test
    void makeCastlingKingSideMove_white_chess960_movesKingAndKingsideRook() {
        // 960 layout: white king on b1, queenside rook on a1, kingside
        // rook on h1. After kingside castling, king must sit on g1
        // and the kingside rook on f1; the queenside rook stays.
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/RK5R w HA - 0 1");
        int castleMove = Move.create(Board.b1, Board.g1, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);

        assertEquals(Board.empty, board.get(Board.b1), "king's source square b1 must be empty");
        assertEquals(Board.whiteKing, board.get(Board.g1), "king must be on g1");
        assertEquals(Board.empty, board.get(Board.h1), "kingside rook source h1 must be empty");
        assertEquals(Board.whiteRook, board.get(Board.f1), "kingside rook must be on f1");
        assertEquals(Board.whiteRook, board.get(Board.a1), "queenside rook a1 stays");
        assertEquals(Board.blackKing, board.get(Board.e8), "black king e8 untouched");
        assertEquals(Board.empty, board.get(Board.f8), "f8 must remain empty — no rook should be conjured on black's back rank");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored, "incremental Zobrist update must match a fresh recomputation");
    }

    @Test
    void makeCastlingQueenSideMove_white_chess960_movesKingAndQueensideRook() {
        // Same 960 layout, but castle queenside. King b1 → c1, queenside
        // rook a1 → d1, kingside rook h1 stays.
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/RK5R w HA - 0 1");
        int castleMove = Move.create(Board.b1, Board.c1, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);

        assertEquals(Board.empty, board.get(Board.b1), "king's source square b1 must be empty");
        assertEquals(Board.whiteKing, board.get(Board.c1), "king must be on c1");
        assertEquals(Board.empty, board.get(Board.a1), "queenside rook source a1 must be empty");
        assertEquals(Board.whiteRook, board.get(Board.d1), "queenside rook must be on d1");
        assertEquals(Board.whiteRook, board.get(Board.h1), "kingside rook h1 stays");
        assertEquals(Board.blackKing, board.get(Board.e8), "black king e8 untouched");
        assertEquals(Board.empty, board.get(Board.d8), "d8 must remain empty — no rook should be conjured on black's back rank");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored, "incremental Zobrist update must match a fresh recomputation");
    }

    @Test
    void makeCastlingKingSideMove_black_chess960_movesKingAndKingsideRook() {
        // Mirror layout for black: king on b8, rooks on a8 / h8.
        // Black kingside castle puts king on g8, kingside rook on f8.
        var board = Fen.importFEN("rk5r/8/8/8/8/8/8/4K3 b ha - 0 1");
        int castleMove = Move.create(Board.b8, Board.g8, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);

        assertEquals(Board.empty, board.get(Board.b8), "king's source square b8 must be empty");
        assertEquals(Board.blackKing, board.get(Board.g8), "king must be on g8");
        assertEquals(Board.empty, board.get(Board.h8), "kingside rook source h8 must be empty");
        assertEquals(Board.blackRook, board.get(Board.f8), "kingside rook must be on f8");
        assertEquals(Board.blackRook, board.get(Board.a8), "queenside rook a8 stays");
        assertEquals(Board.whiteKing, board.get(Board.e1), "white king e1 untouched");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored, "incremental Zobrist update must match a fresh recomputation — "
                + "with king on b8 the update must use b8 (not the hard-coded e8) as the king's source square");
    }

    @Test
    void makeCastlingQueenSideMove_black_chess960_movesKingAndQueensideRook() {
        // Same black 960 layout, castle queenside. King b8 → c8,
        // queenside rook a8 → d8, kingside rook h8 stays.
        var board = Fen.importFEN("rk5r/8/8/8/8/8/8/4K3 b ha - 0 1");
        int castleMove = Move.create(Board.b8, Board.c8, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);

        assertEquals(Board.empty, board.get(Board.b8), "king's source square b8 must be empty");
        assertEquals(Board.blackKing, board.get(Board.c8), "king must be on c8");
        assertEquals(Board.empty, board.get(Board.a8), "queenside rook source a8 must be empty");
        assertEquals(Board.blackRook, board.get(Board.d8), "queenside rook must be on d8");
        assertEquals(Board.blackRook, board.get(Board.h8), "kingside rook h8 stays");
        assertEquals(Board.whiteKing, board.get(Board.e1), "white king e1 untouched");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored, "incremental Zobrist update must match a fresh recomputation — "
                + "with king on b8 the update must use b8 (not the hard-coded e8) as the king's source square");
    }
}
