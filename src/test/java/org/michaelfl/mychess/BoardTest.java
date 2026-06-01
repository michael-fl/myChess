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

    @Test
    void resolveMoveDescription_chess960_shortAlgebraicKingStep_resolvesToNormalKingMove() {
        // 960 position with white king on f1, queenside rook on a1,
        // kingside rook on h1. Black king parked on e8 (no attacks
        // reach white's back rank). White to move.
        //
        // In this position the MoveGenerator legally emits TWO king
        // moves to g1:
        //   (a) the normal one-square king step f1 → g1, and
        //   (b) the kingside castle, which in this 960 setup also
        //       lands the king on g1 with the same from-square
        //       (king stays on g1, rook h1 → f1).
        //
        // Convention: any explicit "king-to-target" notation —
        // short-algebraic "Kg1", long-algebraic "f1-g1" — resolves
        // to the normal king move. Castle has dedicated notations
        // (SAN "O-O", UCI 960 "f1h1") that carriers of castle-intent
        // are expected to use. resolveMoveDescription must therefore
        // pick the typeNormal interpretation, not throw an
        // ambiguity exception.
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/R4K1R w HA - 0 1");
        var moveDescr = MoveDescription.fromString("Kg1", GameStatus.TURN_WHITE);

        var resolved = board.resolveMoveDescription(moveDescr, newGen());
        var move = board.moveDescriptionToMove(resolved);

        assertEquals(Move.typeNormal, Move.getMoveType(move.move()),
                "short-algebraic Kg1 in a 960 position where the same from/to "
                        + "could also describe the castle must resolve to a normal "
                        + "king move: " + ChessUtil.moveToString(move.move()));
    }

    @Test
    void resolveMoveDescription_chess960_longAlgebraicKingStep_resolvesToNormalKingMove() {
        // Same 960 position. The long-algebraic notation "f1-g1"
        // — fromCol/fromRow explicit — also resolves to the normal
        // one-square king step. Same convention as the short
        // notation: explicit king-to-target = normal king move, even
        // in the 960 corner case where the geometry could in
        // principle also encode the kingside castle.
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/R4K1R w HA - 0 1");
        var moveDescr = MoveDescription.fromString("f1-g1", GameStatus.TURN_WHITE);

        var resolved = board.resolveMoveDescription(moveDescr, newGen());
        var move = board.moveDescriptionToMove(resolved);

        assertEquals(Move.typeNormal, Move.getMoveType(move.move()),
                "long-algebraic f1-g1 in a 960 position where the same from/to "
                        + "could also describe the castle must resolve to a normal "
                        + "king move: " + ChessUtil.moveToString(move.move()));
    }

    @Test
    void makeMove_chess960LongAlgebraicKingMove_recognisedAsCastle() {
        // 960 position: white king on b1, queenside rook on a1,
        // kingside rook on h1, lone black king on e8. White to
        // move. In this position b1-g1 is *unambiguous* — a normal
        // king move can only travel one square, so the only legal
        // interpretation of king-from-b1-to-g1 is the kingside
        // castle (king to g1, kingside rook from h1 to f1).
        //
        // The engine must therefore accept "b1-g1" as the long-
        // algebraic form of the castle and execute it correctly:
        // king ends on g1, kingside rook ends on f1, both source
        // squares empty.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/RK5R w HA - 0 1"));
        var moveDescr = MoveDescription.fromString("b1-g1", game.getTurn());

        game.makeMove(moveDescr);

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.g1), "king must land on g1");
        assertEquals(Board.whiteRook, board.get(Board.f1), "kingside rook must land on f1");
        assertEquals(Board.empty, board.get(Board.b1), "king's source square b1 must be empty");
        assertEquals(Board.empty, board.get(Board.h1), "kingside rook source h1 must be empty");
        assertEquals(Board.whiteRook, board.get(Board.a1), "queenside rook a1 stays put");
    }

    @Test
    void makeMove_chess960LongAlgebraicQueenSideKingMove_recognisedAsCastle() {
        // Queenside mirror of the previous test: white king on g1,
        // queenside rook on a1, kingside rook on h1. The only legal
        // king move from g1 to c1 is the queenside castle (a normal
        // king step can travel one square at most, so g1-c1 cannot
        // be anything else). The engine must accept "g1-c1" as the
        // long-algebraic form of the castle and execute it
        // correctly: king ends on c1, queenside rook ends on d1.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/R5KR w HA - 0 1"));
        var moveDescr = MoveDescription.fromString("g1-c1", game.getTurn());

        game.makeMove(moveDescr);

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.c1), "king must land on c1");
        assertEquals(Board.whiteRook, board.get(Board.d1), "queenside rook must land on d1");
        assertEquals(Board.empty, board.get(Board.g1), "king's source square g1 must be empty");
        assertEquals(Board.empty, board.get(Board.a1), "queenside rook source a1 must be empty");
        assertEquals(Board.whiteRook, board.get(Board.h1), "kingside rook h1 stays put");
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
    //      on f/d file, source squares empty)
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

    @Test
    void makeCastlingQueenSideMove_black_chess960_kingStaysWhenTargetEqualsSource() {
        // Degenerate 960 case: the king already sits on its castle
        // landing square. With the king on the c-file, a queenside
        // castle has king target == king source (c8 → c8); only the
        // rook actually moves (b8 → d8). This is the geometry of the
        // Scharnagl start "qrkrnbbn" (king on c, rooks on b / d) that
        // crashed a live cutechess 960 game: deep in the search the
        // black king was lost off the board, and a later
        // canCaptureOpposingKing → findKingField threw
        // "King not found on board: 21".
        var board = Fen.importFEN("1rk5/8/8/8/8/8/8/4K3 b b - 0 1");
        int castleMove = Move.create(Board.c8, Board.c8, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);

        assertEquals(Board.blackKing, board.get(Board.c8),
                "king must remain on c8 — its castle target equals its source square, so it must not vanish");
        assertEquals(Board.empty, board.get(Board.b8), "queenside rook source b8 must be empty");
        assertEquals(Board.blackRook, board.get(Board.d8), "queenside rook must be on d8");
        assertEquals(Board.whiteKing, board.get(Board.e1), "white king e1 untouched");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored, "incremental Zobrist update must match a fresh recomputation");
    }

    @Test
    void makeCastlingKingSideMove_white_chess960_kingStaysWhenTargetEqualsSource() {
        // Kingside mirror of the degenerate "king target == king source"
        // case: with the king on the g-file, a kingside castle has king
        // target == king source (g1 → g1); only the kingside rook moves
        // (h1 → f1). Same from == to geometry as the queenside / c-file
        // case above — covered here in the other color and on the other
        // side so a fix (or a regression) on either path is caught.
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/6KR w H - 0 1");
        int castleMove = Move.create(Board.g1, Board.g1, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);

        assertEquals(Board.whiteKing, board.get(Board.g1),
                "king must remain on g1 — its castle target equals its source square, so it must not vanish");
        assertEquals(Board.empty, board.get(Board.h1), "kingside rook source h1 must be empty");
        assertEquals(Board.whiteRook, board.get(Board.f1), "kingside rook must be on f1");
        assertEquals(Board.blackKing, board.get(Board.e8), "black king e8 untouched");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored, "incremental Zobrist update must match a fresh recomputation");
    }

    @Test
    void makeCastlingQueenSideMove_white_chess960_kingStaysWhenTargetEqualsSource() {
        // White queenside variant of the degenerate "king target ==
        // king source" case: king on the c-file, queenside castle keeps
        // it on c1 (c1 → c1) and only the rook moves (b1 → d1).
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/1RK5 w B - 0 1");
        int castleMove = Move.create(Board.c1, Board.c1, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);

        assertEquals(Board.whiteKing, board.get(Board.c1),
                "king must remain on c1 — its castle target equals its source square, so it must not vanish");
        assertEquals(Board.empty, board.get(Board.b1), "queenside rook source b1 must be empty");
        assertEquals(Board.whiteRook, board.get(Board.d1), "queenside rook must be on d1");
        assertEquals(Board.blackKing, board.get(Board.e8), "black king e8 untouched");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored, "incremental Zobrist update must match a fresh recomputation");
    }

    @Test
    void makeCastlingKingSideMove_black_chess960_kingStaysWhenTargetEqualsSource() {
        // Black kingside variant of the degenerate "king target ==
        // king source" case: king on the g-file, kingside castle keeps
        // it on g8 (g8 → g8) and only the rook moves (h8 → f8).
        var board = Fen.importFEN("6kr/8/8/8/8/8/8/4K3 b h - 0 1");
        int castleMove = Move.create(Board.g8, Board.g8, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);

        assertEquals(Board.blackKing, board.get(Board.g8),
                "king must remain on g8 — its castle target equals its source square, so it must not vanish");
        assertEquals(Board.empty, board.get(Board.h8), "kingside rook source h8 must be empty");
        assertEquals(Board.blackRook, board.get(Board.f8), "kingside rook must be on f8");
        assertEquals(Board.whiteKing, board.get(Board.e1), "white king e1 untouched");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored, "incremental Zobrist update must match a fresh recomputation");
    }


    // ---------- notation → Move pipeline (resolveMoveDescription + moveDescriptionToMove) ----------
    //
    // In production every call to board.moveDescriptionToMove is
    // preceded by board.resolveMoveDescription (see Game.makeMove and
    // OpeningDBImporter — the two only callers). The realistic
    // input → output chain is therefore:
    //
    //   MoveDescription.fromString(notation, turn)
    //     → board.resolveMoveDescription(moveDescr, moveGenerator)
    //       → board.moveDescriptionToMove(resolved)
    //         → Move
    //
    // These tests exercise that full chain for every castle-notation
    // flavour the engine understands, in lieu of testing
    // moveDescriptionToMove in isolation (which would test a path no
    // real caller takes):
    //
    //   • SAN castle              : "O-O" / "O-O-O" — no square hint
    //   • UCI 960 king-to-rook    : "b1h1" / "g1a1" — king moves to
    //                                                 own rook
    //   • Long-algebraic king-to-target multi-square:
    //                               "b1-g1" / "g1-c1" — king takes a
    //                                                   multi-square
    //                                                   step to the
    //                                                   castle landing
    //
    // For each, the test asserts on moveType, from-field and to-field
    // of the resulting Move. The from/to assertions specifically
    // expose bugs where any stage of the chain hardcodes a square
    // that doesn't match the board state (e.g. fromString's
    // hardcoded e1/e8 source for SAN castles).

    private static void assertNotationProducesMove(String fen, String notation,
            byte expectedMoveType, int expectedFromField, int expectedToField) {
        var board = Fen.importFEN(fen);
        var moveDescr = MoveDescription.fromString(notation, board.getGameStatus().getTurn());
        var resolved = board.resolveMoveDescription(moveDescr, newGen());
        var move = board.moveDescriptionToMove(resolved);

        String diag = " (notation '" + notation + "', resolved to " + ChessUtil.moveToString(move.move()) + ")";
        assertEquals(expectedMoveType, Move.getMoveType(move.move()), "moveType" + diag);
        assertEquals(expectedFromField, Move.getFromField(move.move()), "from-field" + diag);
        assertEquals(expectedToField, Move.getToField(move.move()), "to-field" + diag);
    }

    // -- SAN castle, standard chess (smoke) -------------------------

    @Test
    void notationToMove_OO_white_standardChess_isKingsideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1",
                "O-O", Move.typeCastlingKingSide, Board.e1, Board.g1);
    }

    @Test
    void notationToMove_OOO_white_standardChess_isQueensideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1",
                "O-O-O", Move.typeCastlingQueenSide, Board.e1, Board.c1);
    }

    @Test
    void notationToMove_OO_black_standardChess_isKingsideCastle() {
        assertNotationProducesMove("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1",
                "O-O", Move.typeCastlingKingSide, Board.e8, Board.g8);
    }

    @Test
    void notationToMove_OOO_black_standardChess_isQueensideCastle() {
        assertNotationProducesMove("r3k2r/8/8/8/8/8/8/4K3 b kq - 0 1",
                "O-O-O", Move.typeCastlingQueenSide, Board.e8, Board.c8);
    }

    // -- SAN castle, chess960 (adjacent and distant variants) -------

    // King on g1, kingside rook on h1 — king stays on g1 after castle.
    @Test
    void notationToMove_OO_white_chess960KingAdjacentRook_isKingsideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/R5KR w HA - 0 1",
                "O-O", Move.typeCastlingKingSide, Board.g1, Board.g1);
    }

    // King on b1, kingside rook on h1.
    @Test
    void notationToMove_OO_white_chess960KingDistantRook_isKingsideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/RK5R w HA - 0 1",
                "O-O", Move.typeCastlingKingSide, Board.b1, Board.g1);
    }

    // King on b1, queenside rook on a1.
    @Test
    void notationToMove_OOO_white_chess960KingAdjacentRook_isQueensideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/RK5R w HA - 0 1",
                "O-O-O", Move.typeCastlingQueenSide, Board.b1, Board.c1);
    }

    // King on g1, queenside rook on a1.
    @Test
    void notationToMove_OOO_white_chess960KingDistantRook_isQueensideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/R5KR w HA - 0 1",
                "O-O-O", Move.typeCastlingQueenSide, Board.g1, Board.c1);
    }

    @Test
    void notationToMove_OO_black_chess960KingAdjacentRook_isKingsideCastle() {
        assertNotationProducesMove("r5kr/8/8/8/8/8/8/4K3 b ha - 0 1",
                "O-O", Move.typeCastlingKingSide, Board.g8, Board.g8);
    }

    @Test
    void notationToMove_OO_black_chess960KingDistantRook_isKingsideCastle() {
        assertNotationProducesMove("rk5r/8/8/8/8/8/8/4K3 b ha - 0 1",
                "O-O", Move.typeCastlingKingSide, Board.b8, Board.g8);
    }

    @Test
    void notationToMove_OOO_black_chess960KingAdjacentRook_isQueensideCastle() {
        assertNotationProducesMove("rk5r/8/8/8/8/8/8/4K3 b ha - 0 1",
                "O-O-O", Move.typeCastlingQueenSide, Board.b8, Board.c8);
    }

    @Test
    void notationToMove_OOO_black_chess960KingDistantRook_isQueensideCastle() {
        assertNotationProducesMove("r5kr/8/8/8/8/8/8/4K3 b ha - 0 1",
                "O-O-O", Move.typeCastlingQueenSide, Board.g8, Board.c8);
    }

    // -- UCI 960 king-to-rook (4 tests) -----------------------------
    //
    // Input notation puts the rook's square in the to-position
    // ("b1h1" = king on b1 → own kingside rook on h1). The resulting
    // Move's toField must be the king's actual destination (g1 for
    // kingside, c1 for queenside) — NOT the rook's square — so that
    // castle moves from this path are encoded identically to those
    // emitted by the MoveGenerator (which uses g1/c1 by construction).

    @Test
    void notationToMove_kingToKingsideRook_white_chess960_isKingsideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/RK5R w HA - 0 1",
                "b1h1", Move.typeCastlingKingSide, Board.b1, Board.g1);
    }

    @Test
    void notationToMove_kingToQueensideRook_white_chess960_isQueensideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/R5KR w HA - 0 1",
                "g1a1", Move.typeCastlingQueenSide, Board.g1, Board.c1);
    }

    @Test
    void notationToMove_kingToKingsideRook_black_chess960_isKingsideCastle() {
        assertNotationProducesMove("rk5r/8/8/8/8/8/8/4K3 b ha - 0 1",
                "b8h8", Move.typeCastlingKingSide, Board.b8, Board.g8);
    }

    @Test
    void notationToMove_kingToQueensideRook_black_chess960_isQueensideCastle() {
        assertNotationProducesMove("r5kr/8/8/8/8/8/8/4K3 b ha - 0 1",
                "g8a8", Move.typeCastlingQueenSide, Board.g8, Board.c8);
    }

    // -- Long-algebraic king-target multi-square 960 (4 tests) ------

    @Test
    void notationToMove_longAlgebraicMultiSquare_white_kingside_chess960_isKingsideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/RK5R w HA - 0 1",
                "b1-g1", Move.typeCastlingKingSide, Board.b1, Board.g1);
    }

    @Test
    void notationToMove_longAlgebraicMultiSquare_white_queenside_chess960_isQueensideCastle() {
        assertNotationProducesMove("4k3/8/8/8/8/8/8/R5KR w HA - 0 1",
                "g1-c1", Move.typeCastlingQueenSide, Board.g1, Board.c1);
    }

    @Test
    void notationToMove_longAlgebraicMultiSquare_black_kingside_chess960_isKingsideCastle() {
        assertNotationProducesMove("rk5r/8/8/8/8/8/8/4K3 b ha - 0 1",
                "b8-g8", Move.typeCastlingKingSide, Board.b8, Board.g8);
    }

    @Test
    void notationToMove_longAlgebraicMultiSquare_black_queenside_chess960_isQueensideCastle() {
        assertNotationProducesMove("r5kr/8/8/8/8/8/8/4K3 b ha - 0 1",
                "g8-c8", Move.typeCastlingQueenSide, Board.g8, Board.c8);
    }

    // ---------- Board.revertMove for Chess960 castling ----------
    //
    // _revertCastlingKingSideMove and _revertCastlingQueenSideMove
    // (Board.java:870-904) dispatch on `fromField == Board.e1` to tell
    // white from black, and they hardcode the rook restoration squares
    // (h1/f1, a1/d1, h8/f8, a8/d8). In a 960 game with a non-e king,
    // the dispatch sends the move into the wrong colour branch and the
    // rook restoration touches squares that have nothing to do with the
    // actual castle.
    //
    // Each test performs a round-trip: snapshot the raw board + position
    // hash, makeMove, revertMove, then assert the board (full raw byte
    // array) and hash are byte-identical to the snapshot. With the
    // current buggy code make and revert do NOT mirror each other on
    // 960 boards, so the round-trip leaves phantom pieces on the
    // unrelated back rank and / or a hash that disagrees with the
    // resulting raw board.

    @Test
    void revertCastlingKingSideMove_white_chess960_isRoundTripIdentity() {
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/RK5R w HA - 0 1");
        byte[] originalBoard = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);
        long originalHash = board.getGameStatus().getPositionHash();
        int castleMove = Move.create(Board.b1, Board.g1, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);
        board.revertMove();

        assertArrayEquals(originalBoard, board.getRawBoard(),
                "makeMove + revertMove must restore the raw board byte-for-byte");
        assertEquals(originalHash, board.getGameStatus().getPositionHash(),
                "makeMove + revertMove must restore the position hash");
    }

    @Test
    void revertCastlingQueenSideMove_white_chess960_isRoundTripIdentity() {
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/RK5R w HA - 0 1");
        byte[] originalBoard = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);
        long originalHash = board.getGameStatus().getPositionHash();
        int castleMove = Move.create(Board.b1, Board.c1, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);
        board.revertMove();

        assertArrayEquals(originalBoard, board.getRawBoard(),
                "makeMove + revertMove must restore the raw board byte-for-byte");
        assertEquals(originalHash, board.getGameStatus().getPositionHash(),
                "makeMove + revertMove must restore the position hash");
    }

    @Test
    void revertCastlingKingSideMove_black_chess960_isRoundTripIdentity() {
        var board = Fen.importFEN("rk5r/8/8/8/8/8/8/4K3 b ha - 0 1");
        byte[] originalBoard = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);
        long originalHash = board.getGameStatus().getPositionHash();
        int castleMove = Move.create(Board.b8, Board.g8, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);
        board.revertMove();

        assertArrayEquals(originalBoard, board.getRawBoard(),
                "makeMove + revertMove must restore the raw board byte-for-byte");
        assertEquals(originalHash, board.getGameStatus().getPositionHash(),
                "makeMove + revertMove must restore the position hash");
    }

    @Test
    void revertCastlingQueenSideMove_black_chess960_isRoundTripIdentity() {
        var board = Fen.importFEN("rk5r/8/8/8/8/8/8/4K3 b ha - 0 1");
        byte[] originalBoard = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);
        long originalHash = board.getGameStatus().getPositionHash();
        int castleMove = Move.create(Board.b8, Board.c8, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);
        board.revertMove();

        assertArrayEquals(originalBoard, board.getRawBoard(),
                "makeMove + revertMove must restore the raw board byte-for-byte");
        assertEquals(originalHash, board.getGameStatus().getPositionHash(),
                "makeMove + revertMove must restore the position hash");
    }

    // ---------- revertCastling: rook destination == king source ----------
    //
    // Second-generation 960 castle-overlap defect (after the bededba
    // "king target == king source" fix, which covered the c-file king /
    // g-file king degenerate cases). Here the king moves normally but
    // the rook ends up on the king's STARTING square. Concretely:
    //   * Kingside with king on f-file, kingside rook anywhere right
    //     of f: king f → g, rook ? → f. f is both the king's source
    //     and the rook's destination.
    //   * Queenside symmetric: king on d-file, queenside rook anywhere
    //     left of d: king d → c, rook ? → d. d overlaps.
    //
    // The make path is fine: it moves the king (clearing the source)
    // before placing the rook on its destination. The revert path
    // restores the king first (writing the king on f1/d1/f8/d8) and
    // then unconditionally clears the rook's destination (f1/d1/f8/d8)
    // — wiping out the king that step 1 just restored. The next
    // search-internal query of canCaptureOpposingKing → findKingField
    // throws "King not found on board: 13" (= the whiteKing piece
    // byte). Discovered as the cause of the "engine plays instantly
    // after a few moves" symptom in a live cutechess GUI game on the
    // RBNNQKBR Scharnagl starting position.
    //
    // Each test does the same round-trip the bededba family did
    // (snapshot raw board + hash, makeMove, revertMove, assertEquals).
    // With the buggy revert the post-revert byte array is missing the
    // king on the f-file (kingside tests) or d-file (queenside tests).

    @Test
    void revertCastlingKingSideMove_white_chess960_rookDestEqualsKingSource() {
        // 960 setup: king on f1, kingside rook on h1. Kingside castle
        // sends king f1 → g1 and rook h1 → f1, so the rook's
        // destination (f1) overlaps with the king's source (f1).
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/5K1R w H - 0 1");
        byte[] originalBoard = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);
        long originalHash = board.getGameStatus().getPositionHash();
        int castleMove = Move.create(Board.f1, Board.g1, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);
        board.revertMove();

        assertArrayEquals(originalBoard, board.getRawBoard(),
                "makeMove + revertMove must restore the raw board byte-for-byte — "
                        + "the rook-destination clearance in revert must not wipe out the king "
                        + "that was just restored to the same square");
        assertEquals(originalHash, board.getGameStatus().getPositionHash(),
                "makeMove + revertMove must restore the position hash");
    }

    @Test
    void revertCastlingQueenSideMove_white_chess960_rookDestEqualsKingSource() {
        // 960 setup: king on d1, queenside rook on a1. Queenside castle
        // sends king d1 → c1 and rook a1 → d1, so the rook's
        // destination (d1) overlaps with the king's source (d1).
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/R2K4 w A - 0 1");
        byte[] originalBoard = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);
        long originalHash = board.getGameStatus().getPositionHash();
        int castleMove = Move.create(Board.d1, Board.c1, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);
        board.revertMove();

        assertArrayEquals(originalBoard, board.getRawBoard(),
                "makeMove + revertMove must restore the raw board byte-for-byte — "
                        + "the rook-destination clearance in revert must not wipe out the king "
                        + "that was just restored to the same square");
        assertEquals(originalHash, board.getGameStatus().getPositionHash(),
                "makeMove + revertMove must restore the position hash");
    }

    @Test
    void revertCastlingKingSideMove_black_chess960_rookDestEqualsKingSource() {
        // Black mirror: king on f8, kingside rook on h8.
        var board = Fen.importFEN("5k1r/8/8/8/8/8/8/4K3 b h - 0 1");
        byte[] originalBoard = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);
        long originalHash = board.getGameStatus().getPositionHash();
        int castleMove = Move.create(Board.f8, Board.g8, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);
        board.revertMove();

        assertArrayEquals(originalBoard, board.getRawBoard(),
                "makeMove + revertMove must restore the raw board byte-for-byte — "
                        + "the rook-destination clearance in revert must not wipe out the king "
                        + "that was just restored to the same square");
        assertEquals(originalHash, board.getGameStatus().getPositionHash(),
                "makeMove + revertMove must restore the position hash");
    }

    @Test
    void revertCastlingQueenSideMove_black_chess960_rookDestEqualsKingSource() {
        // Black mirror: king on d8, queenside rook on a8.
        var board = Fen.importFEN("r2k4/8/8/8/8/8/8/4K3 b a - 0 1");
        byte[] originalBoard = Arrays.copyOf(board.getRawBoard(), board.getRawBoard().length);
        long originalHash = board.getGameStatus().getPositionHash();
        int castleMove = Move.create(Board.d8, Board.c8, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);
        board.revertMove();

        assertArrayEquals(originalBoard, board.getRawBoard(),
                "makeMove + revertMove must restore the raw board byte-for-byte — "
                        + "the rook-destination clearance in revert must not wipe out the king "
                        + "that was just restored to the same square");
        assertEquals(originalHash, board.getGameStatus().getPositionHash(),
                "makeMove + revertMove must restore the position hash");
    }

    // ---------- makeCastling: rook source == king destination ----------
    //
    // Symmetric companion to the "rook destination == king source"
    // revert defect: the make path has the analogous overlap problem
    // when the rook's STARTING square equals the king's DESTINATION
    // square. In 960 setups with the castling rook directly next to
    // the king (e.g., RBBNNKRQ: king on f1, kingside rook on g1, Scharnagl-legal —
    // queenside-rook left of king, both bishops on opposite-color
    // squares, knights and queen filling out the remaining squares),
    // a kingside castle is the swap K f→g, R g→f. Both squares
    // involved are simultaneously a source AND a destination.
    //
    // The current make-path order is: king-move first
    // (board[toField] = board[fromField]; board[fromField] = empty),
    // then rook-handling (board[rookSource] = empty; board[rookDest]
    // = whiteRook). The first step writes the king onto g1 — which
    // is the rook's source — overwriting the rook. The rook-source
    // clear then sets g1 = empty, wiping out the king that was just
    // placed there. The final state is f1 = R, g1 = empty: the king
    // has disappeared from the board.
    //
    // The bededba "king-target-equals-source" fix (degenerate cases:
    // king on c/g file, rook on a/h) does not cover this geometry,
    // and the rook-destination-equals-king-source fix from the prior
    // commit only adjusted the revert path. Same diagnosis pattern
    // (canCaptureOpposingKing → findKingField → "King not found on
    // board: <pieceByte>") would surface this if it fires deep in a
    // search.
    //
    // Each test sets up the overlap geometry, executes the castle,
    // and asserts post-make that BOTH pieces land on their intended
    // squares (king on g1/c1/g8/c8, rook on f1/d1/f8/d8). Additionally
    // verifies the Zobrist hash via a fresh-recomputation cross-check.

    @Test
    void makeCastlingKingSideMove_white_chess960_rookSourceEqualsKingDest() {
        // 960 geometry: king on f1, kingside rook on g1 — directly
        // adjacent. Kingside castle is the swap K f1 → g1 / R g1 → f1.
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/5KR1 w G - 0 1");
        int castleMove = Move.create(Board.f1, Board.g1, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);

        assertEquals(Board.whiteKing, board.get(Board.g1),
                "king must land on g1 — the rook-source clearance must not wipe out "
                        + "the king that just moved there");
        assertEquals(Board.whiteRook, board.get(Board.f1),
                "kingside rook must land on f1 (the king's former square)");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored,
                "incremental Zobrist update must match a fresh recomputation — the hash "
                        + "must not encode a king on f1 or empty g1");
    }

    @Test
    void makeCastlingQueenSideMove_white_chess960_rookSourceEqualsKingDest() {
        // 960 geometry: king on d1, queenside rook on c1. Queenside
        // castle is the swap K d1 → c1 / R c1 → d1.
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/2RK4 w C - 0 1");
        int castleMove = Move.create(Board.d1, Board.c1, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);

        assertEquals(Board.whiteKing, board.get(Board.c1),
                "king must land on c1 — the rook-source clearance must not wipe out "
                        + "the king that just moved there");
        assertEquals(Board.whiteRook, board.get(Board.d1),
                "queenside rook must land on d1 (the king's former square)");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored,
                "incremental Zobrist update must match a fresh recomputation");
    }

    @Test
    void makeCastlingKingSideMove_black_chess960_rookSourceEqualsKingDest() {
        // Black mirror: king on f8, kingside rook on g8.
        var board = Fen.importFEN("5kr1/8/8/8/8/8/8/4K3 b g - 0 1");
        int castleMove = Move.create(Board.f8, Board.g8, Board.empty, Move.typeCastlingKingSide);

        board.makeMove(castleMove);

        assertEquals(Board.blackKing, board.get(Board.g8),
                "king must land on g8 — the rook-source clearance must not wipe out "
                        + "the king that just moved there");
        assertEquals(Board.blackRook, board.get(Board.f8),
                "kingside rook must land on f8 (the king's former square)");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored,
                "incremental Zobrist update must match a fresh recomputation");
    }

    @Test
    void makeCastlingQueenSideMove_black_chess960_rookSourceEqualsKingDest() {
        // Black mirror: king on d8, queenside rook on c8.
        var board = Fen.importFEN("2rk4/8/8/8/8/8/8/4K3 b c - 0 1");
        int castleMove = Move.create(Board.d8, Board.c8, Board.empty, Move.typeCastlingQueenSide);

        board.makeMove(castleMove);

        assertEquals(Board.blackKing, board.get(Board.c8),
                "king must land on c8 — the rook-source clearance must not wipe out "
                        + "the king that just moved there");
        assertEquals(Board.blackRook, board.get(Board.d8),
                "queenside rook must land on d8 (the king's former square)");

        long stored = board.getGameStatus().getPositionHash();
        long fresh = Board.calculatePositionHash(board.getRawBoard(), board.getGameStatus());
        assertEquals(fresh, stored,
                "incremental Zobrist update must match a fresh recomputation");
    }

    // ---------- SAN castling notation (O-O / O-O-O) ----------
    //
    // SAN castling is intent-only — "O-O" / "O-O-O" don't carry any
    // square information, so the same notation must work in standard
    // chess AND in any 960 starting configuration. Both pairs of
    // tests below use the same notation; only the board setup
    // changes between standard and 960.

    @Test
    void makeMove_standardChess_shortCastleNotation_executesKingsideCastle() {
        // Standard chess: king on e1, rooks on a1/h1. "O-O" must
        // produce king on g1, kingside rook on f1.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"));
        assertFalse(game.getBoard().isChess960(),
                "precondition: this FEN must be detected as standard chess");

        game.makeMove(MoveDescription.fromString("O-O", game.getTurn()));

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.g1), "king must land on g1");
        assertEquals(Board.whiteRook, board.get(Board.f1), "kingside rook must land on f1");
        assertEquals(Board.empty, board.get(Board.e1), "king's source square e1 must be empty");
        assertEquals(Board.empty, board.get(Board.h1), "kingside rook source h1 must be empty");
    }

    @Test
    void makeMove_chess960_shortCastleNotation_executesKingsideCastle() {
        // 960: king on b1, rooks on a1/h1. "O-O" must still produce
        // king on g1 and kingside rook on f1 — same SAN notation,
        // different king source.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/RK5R w HA - 0 1"));
        assertTrue(game.getBoard().isChess960(),
                "precondition: this FEN must be detected as a 960 position");

        game.makeMove(MoveDescription.fromString("O-O", game.getTurn()));

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.g1), "king must land on g1");
        assertEquals(Board.whiteRook, board.get(Board.f1), "kingside rook must land on f1");
        assertEquals(Board.empty, board.get(Board.b1), "king's source square b1 must be empty");
        assertEquals(Board.empty, board.get(Board.h1), "kingside rook source h1 must be empty");
    }

    @Test
    void makeMove_standardChess_longCastleNotation_executesQueensideCastle() {
        // Standard chess: king on e1, rooks on a1/h1. "O-O-O" must
        // produce king on c1, queenside rook on d1.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"));
        assertFalse(game.getBoard().isChess960(),
                "precondition: this FEN must be detected as standard chess");

        game.makeMove(MoveDescription.fromString("O-O-O", game.getTurn()));

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.c1), "king must land on c1");
        assertEquals(Board.whiteRook, board.get(Board.d1), "queenside rook must land on d1");
        assertEquals(Board.empty, board.get(Board.e1), "king's source square e1 must be empty");
        assertEquals(Board.empty, board.get(Board.a1), "queenside rook source a1 must be empty");
    }

    @Test
    void makeMove_chess960_longCastleNotation_executesQueensideCastle() {
        // 960: king on b1, rooks on a1/h1. "O-O-O" must produce
        // king on c1 (one square right of b1), queenside rook on d1.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/RK5R w HA - 0 1"));
        assertTrue(game.getBoard().isChess960(),
                "precondition: this FEN must be detected as a 960 position");

        game.makeMove(MoveDescription.fromString("O-O-O", game.getTurn()));

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.c1), "king must land on c1");
        assertEquals(Board.whiteRook, board.get(Board.d1), "queenside rook must land on d1");
        assertEquals(Board.empty, board.get(Board.b1), "king's source square b1 must be empty");
        assertEquals(Board.empty, board.get(Board.a1), "queenside rook source a1 must be empty");
    }

    // ---------- UCI Chess960 king-to-rook castling notation ----------
    //
    // UCI's Chess960 convention encodes a castle as king-source →
    // own-rook-source (the rook the castle is paired with). The
    // destination is the rook's *starting* square, not its
    // post-castle square, so the notation is unambiguous: a king
    // cannot legally capture its own rook, so any king-move that
    // lands on an own rook is necessarily a castle, and the side
    // (king/queenside) is determined by which rook the move targets.

    @Test
    void makeMove_chess960_kingToKingsideRookNotation_executesKingsideCastle() {
        // 960: king on b1, queenside rook on a1, kingside rook on
        // h1. "b1h1" = king moves to its own kingside rook = castle
        // kingside. Resulting position: king on g1, kingside rook on
        // f1.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/RK5R w HA - 0 1"));
        var moveDescr = MoveDescription.fromString("b1h1", game.getTurn());

        game.makeMove(moveDescr);

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.g1), "king must land on g1");
        assertEquals(Board.whiteRook, board.get(Board.f1), "kingside rook must land on f1");
        assertEquals(Board.empty, board.get(Board.b1), "king's source square b1 must be empty");
        assertEquals(Board.empty, board.get(Board.h1), "kingside rook source h1 must be empty");
        assertEquals(Board.whiteRook, board.get(Board.a1), "queenside rook a1 stays put");
    }

    @Test
    void makeMove_chess960_kingToQueensideRookNotation_executesQueensideCastle() {
        // 960: same setup. "b1a1" = king moves to its own queenside
        // rook = castle queenside. Resulting position: king on c1,
        // queenside rook on d1.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/RK5R w HA - 0 1"));
        var moveDescr = MoveDescription.fromString("b1a1", game.getTurn());

        game.makeMove(moveDescr);

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.c1), "king must land on c1");
        assertEquals(Board.whiteRook, board.get(Board.d1), "queenside rook must land on d1");
        assertEquals(Board.empty, board.get(Board.b1), "king's source square b1 must be empty");
        assertEquals(Board.empty, board.get(Board.a1), "queenside rook source a1 must be empty");
        assertEquals(Board.whiteRook, board.get(Board.h1), "kingside rook h1 stays put");
    }

    // ---------- Degenerate king-to-rook castle: king target == king source ----------
    //
    // When the king starts on the castle's destination file (c-file
    // for queenside, g-file for kingside), the king does not move at
    // all during the castle — only the rook does. With UCI_Chess960
    // active, cutechess sends back the engine's own outbound castle
    // ("c8a8" = king c8 → own queenside rook a8) on the next
    // `position fen ... moves ...` command, and that string must
    // replay cleanly against the engine's own board.
    //
    // The four tests below cover all four quadrants (W/B × K/Q) of
    // the degenerate case. They drive `Game.makeMove(MoveDescription)`
    // — not Board.makeMove(int) directly — so they exercise the
    // resolveMoveDescription → moveDescriptionToMove → validate-against-
    // MoveGenerator pipeline that the live UCI replay also takes.
    //
    // Regression context: a live cutechess Chess960 game on the
    // RNKBQNBR starting position (king on c-file) lost on move 9
    // because Black's queenside castle "c8a8", emitted by the
    // engine itself, was rejected by Game.makeMoveResolved on the
    // next position-command replay. moveDescriptionToMove packed the
    // castle move with capturedPiece = get(toField) = blackKing
    // (non-zero), while MoveGenerator emits the same castle with
    // capturedPiece = 0 — Moves.contains is an exact-int compare,
    // so the validation rejected the move.

    @Test
    void makeMove_chess960_kingToRookNotation_degenerateBlackQueenside_replaysWithoutException() {
        // Black king starts on c8 — its own queenside castle target.
        // "c8a8" (king → own queenside rook) is the canonical UCI
        // Chess960 form. This is the exact ply-16 from the live
        // cutechess game that the engine then failed to replay on
        // ply 17, causing a 1-0 loss on illegal-move forfeit.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("r1k5/8/8/8/8/8/8/4K3 b a - 0 1"));
        assertTrue(game.getBoard().isChess960(),
                "precondition: imported position must be detected as a 960 game");

        game.makeMove(UciMoveParser.parse("c8a8", game.getBoard()));

        var board = game.getBoard();
        assertEquals(Board.blackKing, board.get(Board.c8),
                "king stays on c8 — its queenside-castle target equals its source square");
        assertEquals(Board.blackRook, board.get(Board.d8),
                "queenside rook lands on d8");
        assertEquals(Board.empty, board.get(Board.a8),
                "queenside rook source a8 is empty");
        assertFalse(board.getGameStatus().isBlackCastlingQueenSidePossible(),
                "black queenside castle right is revoked after castling");
        assertFalse(board.getGameStatus().isBlackCastlingKingSidePossible(),
                "black kingside castle right is revoked after castling");
        assertTrue(board.getGameStatus().hasBlackCastled(),
                "hasBlackCastled flag is set after castling");
    }

    @Test
    void makeMove_chess960_kingToRookNotation_degenerateWhiteQueenside_replaysWithoutException() {
        // White mirror: king starts on c1, queenside rook on a1.
        // "c1a1" is the king-to-rook castle; king target c1 equals
        // king source c1, only the rook moves a1 → d1.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/R1K5 w A - 0 1"));
        assertTrue(game.getBoard().isChess960(),
                "precondition: imported position must be detected as a 960 game");

        game.makeMove(UciMoveParser.parse("c1a1", game.getBoard()));

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.c1),
                "king stays on c1 — its queenside-castle target equals its source square");
        assertEquals(Board.whiteRook, board.get(Board.d1),
                "queenside rook lands on d1");
        assertEquals(Board.empty, board.get(Board.a1),
                "queenside rook source a1 is empty");
        assertFalse(board.getGameStatus().isWhiteCastlingQueenSidePossible(),
                "white queenside castle right is revoked after castling");
        assertTrue(board.getGameStatus().hasWhiteCastled(),
                "hasWhiteCastled flag is set after castling");
    }

    @Test
    void makeMove_chess960_kingToRookNotation_degenerateBlackKingside_replaysWithoutException() {
        // Black kingside variant: king starts on g8, kingside rook
        // on h8. "g8h8" is the king-to-rook castle; king target g8
        // equals king source g8, only the rook moves h8 → f8.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("6kr/8/8/8/8/8/8/4K3 b h - 0 1"));
        assertTrue(game.getBoard().isChess960(),
                "precondition: imported position must be detected as a 960 game");

        game.makeMove(UciMoveParser.parse("g8h8", game.getBoard()));

        var board = game.getBoard();
        assertEquals(Board.blackKing, board.get(Board.g8),
                "king stays on g8 — its kingside-castle target equals its source square");
        assertEquals(Board.blackRook, board.get(Board.f8),
                "kingside rook lands on f8");
        assertEquals(Board.empty, board.get(Board.h8),
                "kingside rook source h8 is empty");
        assertFalse(board.getGameStatus().isBlackCastlingKingSidePossible(),
                "black kingside castle right is revoked after castling");
        assertTrue(board.getGameStatus().hasBlackCastled(),
                "hasBlackCastled flag is set after castling");
    }

    @Test
    void makeMove_chess960_kingToRookNotation_degenerateWhiteKingside_replaysWithoutException() {
        // White kingside variant: king starts on g1, kingside rook
        // on h1. "g1h1" is the king-to-rook castle; king target g1
        // equals king source g1, only the rook moves h1 → f1.
        var game = new Game(Game.standardConfig(),
                Fen.importFEN("4k3/8/8/8/8/8/8/6KR w H - 0 1"));
        assertTrue(game.getBoard().isChess960(),
                "precondition: imported position must be detected as a 960 game");

        game.makeMove(UciMoveParser.parse("g1h1", game.getBoard()));

        var board = game.getBoard();
        assertEquals(Board.whiteKing, board.get(Board.g1),
                "king stays on g1 — its kingside-castle target equals its source square");
        assertEquals(Board.whiteRook, board.get(Board.f1),
                "kingside rook lands on f1");
        assertEquals(Board.empty, board.get(Board.h1),
                "kingside rook source h1 is empty");
        assertFalse(board.getGameStatus().isWhiteCastlingKingSidePossible(),
                "white kingside castle right is revoked after castling");
        assertTrue(board.getGameStatus().hasWhiteCastled(),
                "hasWhiteCastled flag is set after castling");
    }

    // ---------- End-to-end FEN round-trip for Chess960 ----------
    //
    // Closes the loop on Phase 1 (FEN export) and Phase 2 (960
    // makeMove): take a 960 position, play a move, export the
    // resulting state as FEN, re-import that FEN into a fresh
    // Board, and assert the re-imported board's Zobrist hash
    // matches the in-memory post-move hash.
    //
    // Any FEN-level information loss (Shredder castling letters,
    // en-passant file, half-move clock, …) would surface here as a
    // hash mismatch.

    @Test
    void fenRoundTrip_chess960_kingsideCastle_preservesHash() {
        // 960 setup: white king on b1, queenside rook on a1, kingside
        // rook on h1, lone black king on e8. White to move.
        String initialFen = "4k3/8/8/8/8/8/8/RK5R w HA - 0 1";
        var board = Fen.importFEN(initialFen);

        assertTrue(board.isChess960(),
                "precondition: imported position must be detected as a 960 game");

        // Play the 960 kingside castle: king b1 → g1, kingside rook h1 → f1.
        int castleMove = Move.create(Board.b1, Board.g1, Board.empty, Move.typeCastlingKingSide);
        board.makeMove(castleMove);
        long postMoveHash = board.getGameStatus().getPositionHash();

        // Round-trip the post-move position via FEN.
        String exportedFen = board.exportFEN();
        long reimportedHash = Fen.importFEN(exportedFen).getGameStatus().getPositionHash();

        assertEquals(postMoveHash, reimportedHash,
                "FEN export + re-import must preserve the post-castle Zobrist hash. "
                        + "Exported FEN: '" + exportedFen + "'");
    }

    @Test
    void fenRoundTrip_chess960_nonCastleMove_preservesHash() {
        // 960 starting position (cutechess sample) — exercises the
        // round-trip for a non-castle move so that the Shredder export
        // path runs with the full set of back-rank castling rights
        // (FAfa) still alive after the move.
        String initialFen = "rkbbnrnq/pppppppp/8/8/8/8/PPPPPPPP/RKBBNRNQ w FAfa - 0 1";
        var board = Fen.importFEN(initialFen);

        assertTrue(board.isChess960(),
                "precondition: imported position must be detected as a 960 game");

        // Play 1. Ng1-f3 — no captures, no castling-rights changes,
        // no en-passant square set.
        int knightMove = Move.create(Board.g1, Board.f3, Board.empty, Move.typeNormal);
        board.makeMove(knightMove);
        long postMoveHash = board.getGameStatus().getPositionHash();

        String exportedFen = board.exportFEN();
        long reimportedHash = Fen.importFEN(exportedFen).getGameStatus().getPositionHash();

        assertEquals(postMoveHash, reimportedHash,
                "FEN export + re-import must preserve the Zobrist hash. "
                        + "Exported FEN: '" + exportedFen + "'");
    }
}
