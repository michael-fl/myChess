package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

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

    @Test
    void testIsKingChecked() {
        var pgn = """
                1. c4 e5 2. Nc3 Nf6 3. a3 Nc6 4. e4 Nd4 5. Nf3 d6 6. h3 Be7 7. Be2 O-O 8. O-O
                h6 9. d3 a6 10. Be3 c5 11. Nd5 Be6 12. Nxd4 cxd4 13. Bd2 Nxd5 14. cxd5 Bd7 15. Rc1
                Rc8 16. Bg4 f5 17. Bxf5 Bxf5 18. exf5 Rxf5 19. Qg4 Rxc1 20. Rxc1 Qf8 21. Bxh6
                Kh7 22. Bxg7 Qf7 23. Rc8 Qg6
                """;
        var game = GameImporter.importerFor(pgn).importGame();

        var moveGenerator = newGen();
        var board = game.getBoard();
        assertFalse(board.isKingChecked(moveGenerator), "King should not yet be checked");

        game.makeMove(MoveDescription.fromString("Rh8+", board.getGameStatus().getTurn()));
        assertTrue(board.isKingChecked(moveGenerator), "King should now be checked");

        game.makeMove(MoveDescription.fromString("Kxg7", board.getGameStatus().getTurn()));
        assertFalse(board.isKingChecked(moveGenerator), "King should no longer be checked");
    }

    @Test
    void testIsCheckmate_scholarsMate() {
        var pgn = """
                1. e4 e5
                2. Qh5 Nc6
                3. Bc4 Nf6??
                """;
        var game = GameImporter.importerFor(pgn).importGame();
        var moveGenerator = newGen();
        var board = game.getBoard();

        assertFalse(board.isCheckmate(moveGenerator), "Should not yet be checkmate");
        game.makeMove(MoveDescription.fromString("Qxf7", board.getGameStatus().getTurn()));
        assertTrue(board.isCheckmate(moveGenerator), "Should be checkmate now");
    }

    @Test
    void testIsCheckmate_foolsMate() {
        var pgn = """
                1. f3 e6
                2. g4
                """;
        var game = GameImporter.importerFor(pgn).importGame();
        var moveGenerator = newGen();
        var board = game.getBoard();

        assertFalse(board.isCheckmate(moveGenerator), "Should not yet be checkmate");
        game.makeMove(MoveDescription.fromString("Qh4", board.getGameStatus().getTurn()));
        assertTrue(board.isCheckmate(moveGenerator), "Should be checkmate now");
    }

    @Test
    void testIsCheckmate_smotheredMate() {
        var pgn = """
                1. e4 c6 2. d4 d5
                3. Nc3 dxe4
                4. Nxe4 Nd7
                5. Qe2 Ngf6
                """;
        var game = GameImporter.importerFor(pgn).importGame();
        var moveGenerator = newGen();
        var board = game.getBoard();

        assertFalse(board.isCheckmate(moveGenerator), "Should not yet be checkmate");
        game.makeMove(MoveDescription.fromString("Nd6#", board.getGameStatus().getTurn()));
        assertTrue(board.isCheckmate(moveGenerator), "Should be checkmate now");
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
