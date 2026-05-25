package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
}
