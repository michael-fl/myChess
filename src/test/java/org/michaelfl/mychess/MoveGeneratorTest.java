package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class MoveGeneratorTest {

    @Test
    void testWhiteEnPassantMove() {
        String movesStr = """
                1. e4 h6 2. e5 f5
                """;
        var importer = GameImporter.importerFor(movesStr);
        var game = importer.importGame();

        var enPassantField = game.getGameStatus().getEnPassantField();
        assertEquals(Board.f6, enPassantField, "Wrong en passant field");

        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());

        var moves = moveGenerator.calculateMoves(game.getBoard());

        String expectedEnPassantMove = "e5-f6";
        boolean found = Arrays.stream(moves.getMoves()).anyMatch(move -> expectedEnPassantMove.equals(ChessUtil.moveToString(move)));
        assertTrue(found, "en passant move not found. All moves: " + ChessUtil.movesToString(moves.getMoves(), moves.count()));
    }

    @Test
    void testBlackEnPassantMove() {
        String movesStr = """
                1. a3 e5 2. a4 e4 3. d4
                """;
        var importer = GameImporter.importerFor(movesStr);
        var game = importer.importGame();

        var enPassantField = game.getGameStatus().getEnPassantField();
        assertEquals(Board.d3, enPassantField, "Wrong en passant field");

        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var moves = moveGenerator.calculateMoves(game.getBoard());

        String expectedEnPassantMove = "e4-d3";
        boolean found = Arrays.stream(moves.getMoves()).anyMatch(move -> expectedEnPassantMove.equals(ChessUtil.moveToString(move)));
        assertTrue(found, "en passant move not found. All moves: " + ChessUtil.movesToString(moves.getMoves(), moves.count()));
    }

    @Test
    void testWhiteCanCastleKingSide() {
        var pgn = """
                1. d3 c5 2. g3 Nf6 3. Bg2 e6 4. Nf3 Qb6
                """;
        assertCastlingPossible(pgn, "e1-g1");
    }

    @Test
    void testBlackCanCastleKingSide() {
        var pgn = """
                1. e4 e5 2. Nf3 Nf6 3. Bc4 Bc5 4. d3
                """;
        assertCastlingPossible(pgn, "e8-g8");
    }

    @Test
    void testWhiteCanCastleQueenSide() {
        var pgn = """
                1. d4 d5 2. Nc3 Nc6 3. Bf4 Bf5 4. Qd2 Qd7
                """;
        assertCastlingPossible(pgn, "e1-c1");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "1. e4 c6 2. f3 Qb6 3. Be2 Nf6 4. Nh3 e6",
            "1. g3 b6 2. Bg2 Ba6 3. e4 e5 4. Nf3 Nf6",
            "1. d3 c5 2. g3 Nf6 3. Bg2 e6 4. Nf3 Qa5+",
    })
    void testWhiteCannotCastleKingSide(String pgn) {
        assertCastlingNotPossible(pgn, "e1-g1");
    }

    @Test
    void testWhiteCannotCastleQueenSide_piecesInTheWay() {
        // After 4 plies, white's turn — Nb1, Bc1 and Qd1 still block the queen-side path.
        var pgn = """
                1. e4 e5 2. Nf3 Nf6
                """;
        assertCastlingNotPossible(pgn, "e1-c1");
    }

    @Test
    void testBlackCannotCastleKingSide_piecesInTheWay() {
        // After 1 ply, black's turn — Ng8 and Bf8 still on their starting squares.
        var pgn = "1. e4";
        assertCastlingNotPossible(pgn, "e8-g8");
    }

    @Test
    void testBlackCannotCastleQueenSide_piecesInTheWay() {
        // After 1 ply, black's turn — Nb8, Bc8 and Qd8 still on their starting squares.
        var pgn = "1. d4";
        assertCastlingNotPossible(pgn, "e8-c8");
    }

    // ---- Chess960 castling, direct MoveGenerator checks ----
    //
    // These exercise calculateMoves(board) against sparse 960 positions
    // built from FEN — only K + 2R on the back rank, lone enemy king (or
    // a deliberately placed attacker) on rank 8. The PGN/GameImporter
    // route can't reach 960 starting positions, so the setup goes through
    // Fen.importFEN + the Game constructor's auto-detection.
    //
    // Until the 960 castling rules land in MoveGenerator, every test
    // here is expected to fail — the current code emits castling moves
    // from the hard-coded e1/e8 squares regardless of where the king
    // actually stands, so the king-on-b1 / king-on-g1 setups below
    // simply don't produce the expected from/to-field pairs.

    @Test
    void testWhiteCanCastleKingSide_chess960() {
        // 960 layout: white king on b1, queenside rook on a1, kingside rook on h1.
        // Black king on e8 — off the a-/h-file so the open white-rook files
        // do not put it in check (would make the position illegal with white
        // to move). Kingside castle moves the king b1 → g1 (rook h1 → f1).
        var game = chess960Game("4k3/8/8/8/8/8/8/RK5R w HA - 0 1");

        assertCastlingMoveGenerated(game, Move.typeCastlingKingSide, Board.b1, Board.g1);
    }

    @Test
    void testWhiteCanCastleQueenSide_chess960() {
        // 960 layout: white king on g1, queenside rook on a1, kingside rook on h1.
        // Black king on e8 to keep the position legal (same reasoning as above).
        // Queenside castle moves the king g1 → c1 (rook a1 → d1).
        var game = chess960Game("4k3/8/8/8/8/8/8/R5KR w HA - 0 1");

        assertCastlingMoveGenerated(game, Move.typeCastlingQueenSide, Board.g1, Board.c1);
    }

    @Test
    void testBlackCanCastleKingSide_chess960() {
        // Mirror of the white kingside test: black king on b8, rooks on
        // a8 and h8. White king parked on d1 — not on either of the open
        // black-rook files and not adjacent to the black king.
        var game = chess960Game("rk5r/8/8/8/8/8/8/3K4 b ha - 0 1");

        assertCastlingMoveGenerated(game, Move.typeCastlingKingSide, Board.b8, Board.g8);
    }

    @Test
    void testCannotCastleWhenKingInCheck_chess960() {
        // White king on b1, queenside rook on a1, kingside rook on h1.
        // Black rook on b8 attacks the white king via the open b-file
        // (the intended check). Black king on e8 stays off both
        // white-rook files. Castling out of check is illegal — neither
        // side may castle.
        //
        // Note on assertion semantics: the queenside-castle move from
        // king-on-b1 is encoded as b1 → c1, which shares its from/to
        // pair with a perfectly legal ordinary king move (the king can
        // step out of check via Kc1 since c1 isn't attacked by the b8
        // rook). The earlier string-only "b1-c1"-match falsely flagged
        // the legal regular move as the forbidden castle. Disambiguate
        // by matching on Move.typeCastling* as well.
        var game = chess960Game("1r2k3/8/8/8/8/8/8/RK5R w HA - 0 1");

        assertCastlingMoveNotGenerated(game, Move.typeCastlingKingSide, Board.b1, Board.g1);
        assertCastlingMoveNotGenerated(game, Move.typeCastlingQueenSide, Board.b1, Board.c1);
    }

    @Test
    void testCannotCastleWhenKingPathSquareAttacked_chess960() {
        // White king on b1 wants to castle kingside (king path b1, c1,
        // d1, e1, f1, g1). Black rook on f8 attacks f1 via the open
        // f-file. The king would cross an attacked square → illegal.
        // Black king on e8, adjacent to its own f8 rook; off the
        // white-rook files.
        var game = chess960Game("4kr2/8/8/8/8/8/8/RK5R w HA - 0 1");

        assertCastlingMoveNotGenerated(game, Move.typeCastlingKingSide, Board.b1, Board.g1);
    }

    @Test
    void testCanCastleWhenRookOnlyPathSquareAttacked_chess960() {
        // White king on g1, queenside rook on a1, kingside rook on h1.
        // Queenside castle: king g1 → c1 (path g1, f1, e1, d1, c1)
        //                   rook a1 → d1 (path a1, b1, c1, d1).
        // The only square the rook crosses without the king is b1
        // (a-file is the rook's own source square).
        // Black rook on b8 attacks b1 via the open b-file — the king
        // never crosses b1, so Chess960 rules keep castling legal.
        // Black king on e8 again to keep the position legal.
        var game = chess960Game("1r2k3/8/8/8/8/8/8/R5KR w HA - 0 1");

        assertCastlingMoveGenerated(game, Move.typeCastlingQueenSide, Board.g1, Board.c1);
    }

    /**
     * Disambiguates castling from regular king moves that happen to share
     * the same from/to fields (e.g. king on b1 castling queenside to c1
     * vs. king on b1 stepping to c1 as a normal one-square move). Matches
     * on Move.typeCastling* in addition to from/to.
     */
    private static boolean isCastlingMoveGenerated(Game game, byte castlingType, int fromField, int toField) {
        var moves = new MoveGenerator(MoveSorter.defaultImplementation()).calculateMoves(game.getBoard());
        int[] moveArray = moves.getMoves();
        for (int i = 0; i < moves.count(); i++) {
            int move = moveArray[i];
            if (Move.getMoveType(move) == castlingType
                    && Move.getFromField(move) == fromField
                    && Move.getToField(move) == toField) {
                return true;
            }
        }

        return false;
    }

    private static void assertCastlingMoveGenerated(Game game, byte castlingType, int fromField, int toField) {
        assertTrue(isCastlingMoveGenerated(game, castlingType, fromField, toField),
                () -> "Expected castling move " + ChessUtil.moveToString(fromField, toField)
                        + " (type=" + castlingType + ") to be generated. Generated moves:\n"
                        + dumpGeneratedMoves(game));
    }

    private static void assertCastlingMoveNotGenerated(Game game, byte castlingType, int fromField, int toField) {
        assertFalse(isCastlingMoveGenerated(game, castlingType, fromField, toField),
                () -> "Castling move " + ChessUtil.moveToString(fromField, toField)
                        + " (type=" + castlingType + ") must not be generated. Generated moves:\n"
                        + dumpGeneratedMoves(game));
    }

    private static String dumpGeneratedMoves(Game game) {
        var moves = new MoveGenerator(MoveSorter.defaultImplementation()).calculateMoves(game.getBoard());
        int[] moveArray = moves.getMoves();
        var sb = new StringBuilder();
        for (int i = 0; i < moves.count(); i++) {
            int move = moveArray[i];
            sb.append("  [").append(i).append("] ")
                    .append(ChessUtil.moveToString(move))
                    .append(" type=").append(Move.getMoveType(move))
                    .append(" from=").append(Move.getFromField(move) & 0xFF)
                    .append(" to=").append(Move.getToField(move) & 0xFF)
                    .append('\n');
        }
        return sb.toString();
    }

    /** Build a {@link Game} from a 960 FEN. The 960 auto-detection in
     *  {@link Game#Game(GameConfig, Board)} picks up the variant from the
     *  imported board's castling-rook files / king position. */
    private static Game chess960Game(String fen) {
        return new Game(Game.standardConfig(), Fen.importFEN(fen));
    }

    private void assertCastlingPossible(String movesStr, String castlingMove) {
        var importer = GameImporter.importerFor(movesStr);
        var game = importer.importGame();

        assertMovePossible(castlingMove, game);

        game.makeMove(MoveDescription.fromString(castlingMove, game.getTurn()));
    }

    private void assertCastlingNotPossible(String movesStr, String castlingMove) {
        var importer = GameImporter.importerFor(movesStr);
        var game = importer.importGame();

        assertMoveNotPossible(castlingMove, game);

        var moveDescr = MoveDescription.fromString(castlingMove, game.getTurn());
        var ex = assertThrows(IllegalStateException.class, () -> game.makeMove(moveDescr));
        assertTrue(ex.getMessage().contains("Illegal move"), "Unexpected exception message: " + ex.getMessage());
    }

    private void assertMovePossible(String moveStr, Game game) {
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var moves = moveGenerator.calculateMoves(game.getBoard());

        for (var move : moves.getMoves()) {
            if (moveStr.equals(ChessUtil.moveToString(move))) {
                return;
            }
        }

        fail("Move " + moveStr + " expected; got " + moves);
    }

    private void assertMoveNotPossible(String moveStr, Game game) {
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var moves = moveGenerator.calculateMoves(game.getBoard());

        for (var move : moves.getMoves()) {
            if (moveStr.equals(ChessUtil.moveToString(move))) {
                fail("Move " + moveStr + " not expected");
            }
        }
    }

    private EngineConfig engineConfig() {
        return new EngineConfig.Builder()
                .maxDepth(8)
                .build();
    }

    @Test
    void testStartPosition() {
        var game = new Game(new GameConfig(engineConfig()));

        testMoves(game, "b1-c3 g1-f3 d2-d4 e2-e4 d2-d3 e2-e3 b1-a3 g1-h3 a2-a3 h2-h3 a2-a4 h2-h4 b2-b4 c2-c4 f2-f4 g2-g4 b2-b3 g2-g3 c2-c3 f2-f3");
    }

    @Test
    void testBlackFirstMove() {
        var game = new Game(new GameConfig(engineConfig()));
        game.makeMove(MoveDescription.fromString("e4", GameStatus.TURN_WHITE));

        testMoves(game, "b8-c6 g8-f6 d7-d5 e7-e5 d7-d6 e7-e6 b8-a6 g8-h6 a7-a6 h7-h6 a7-a5 h7-h5 b7-b5 c7-c5 f7-f5 g7-g5 b7-b6 g7-g6 c7-c6 f7-f6");
    }

    @Test
    void testMoves1() {
        var notation = """
                1. c4 e5 2. Nc3 Nf6 3. a3 Nc6 4. e4 Nd4 5. Nf3 d6 6. h3 Be7 7. Be2 O-O 8. O-O
                h6 9. d3 a6 10. Be3 c5 11. Nd5 Be6 12. Nxd4 cxd4 13. Bd2 Nxd5 14. cxd5 Bd7 15. Rc1
                Rc8 16. Bg4 f5 17. Bxf5 Bxf5 18. exf5 Rxf5 19. Qg4 Rxc1 20. Rxc1 Qf8 21. Bxh6
                Kh7 22. Bxg7 Qf7 23. Rc8
                """;
        testMoves(notation, "h7-g7 f5-f2 f7-g7 f7-d5 e7-f6 e5-e4 f7-f6 f7-e6 f5-f4 f5-f3 f5-g5 e7-g5 f7-g6 f5-f6 f5-h5 a6-a5 f7-h5 b7-b5 e7-h4 f7-e8 b7-b6 e7-f8 e7-d8 f7-f8 f7-g8 h7-h8 h7-h6 h7-g6 h7-g8");
    }

    @Test
    void testMoves2() {
        var notation = """
                1. Nc3 d6 2. e4 e5 3. Nf3 Nf6 4. d4 Qe7 5. Bg5 Nbd7 6. Bd3 Nb6 7. Bxf6 Qxf6 8. Nb5 Qe7 9.
                dxe5 dxe5 10. O-O a6 11. Nc3 Qf6 12. Nd5 Nxd5 13. exd5 Bg4 14. Qe2 Bxf3 15. gxf3 O-O-O 16.
                Qe4 Kb8 17. Rae1 Qh6 18. Kh1 Bd6 19. Rg1 Qf6 20. Qf5 Qxf5 21. Bxf5 g6 22. Bg4 Rhe8 23. Re2
                Bc5 24. c4 Bd4 25. Rd1 Bc5 26. Rde1 f5 27. Bh3 Bb4 28. Rd1 Re7 29. a3 Bd6 30. b4 Ree8 31.
                c5 Bf8 32. Rc2 Bg7 33. Bf1 e4 34. fxe4 fxe4 35. b5 a5 36. Bc4 Bf6 37. d6 cxd6 38. Rxd6 Be5
                39. Rd5 Bd4 40. c6 bxc6 41. bxc6 Kc7 42. Rxa5 Rf8 43. Rb5 Rxf2 44. Rxf2 Bxf2 45. Bd5 e3
                46. Rb7+ Kc8
                """;

        testMoves(notation, "b7-h7 c6-c7 h2-h3 d5-e6 b7-c7 b7-d7 b7-e7 b7-f7 b7-g7 h2-h4 a3-a4 d5-e4 d5-f3 d5-c4 d5-b3 b7-a7 d5-f7 d5-g2 b7-b8 b7-b6 b7-b5 b7-b4 b7-b3 b7-b2 b7-b1 d5-g8 d5-a2 h1-g1 h1-g2");
    }

    @Test
    void testKnownBestMoveComesFirst() {
        var game = new Game(new GameConfig(engineConfig()));

        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        int knowBestMove = game.getBoard().moveDescriptionToMove(MoveDescription.fromString("h2-h4", GameStatus.TURN_WHITE)).move();
        var moves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard(), 0, knowBestMove);

        testMoves(game, "b1-c3 g1-f3 d2-d4 e2-e4 d2-d3 e2-e3 b1-a3 g1-h3 a2-a3 h2-h3 a2-a4 h2-h4 b2-b4 c2-c4 f2-f4 g2-g4 b2-b3 g2-g3 c2-c3 f2-f3");

        assertEquals("h2-h4", ChessUtil.moveToString(moves.getMove(0)));
    }

    private void testMoves(String gameNotation, String expectedMovesStr) {
        GameImporter importer = GameImporter.importerFor(gameNotation);
        var config = new GameConfig(engineConfig());
        var game = importer.importGame(config);

        testMoves(game, expectedMovesStr);
    }

    private void testMoves(Game game, String expectedMovesStr) {
        game.print();
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var moves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard(), 0, 0);
        System.out.println(moves.toString());

        assertNotNull(moves, "No moves returned");
        assertTrue(moves.count() > 0, "No moves returned");

        List<Integer> actualMoves = Arrays.stream(moves.getMoves()).limit(moves.count()).boxed().toList();

        Set<Integer> expectedMoves = parseMoves(game, expectedMovesStr);
        assertEquals(expectedMoves.size(), moves.count(), "Unexpected number of moves");

        for (var move : expectedMoves) {
            assertTrue(actualMoves.contains(move), "Expected move: " + new Move(move));
        }

        for (int move : actualMoves) {
            assertTrue(expectedMoves.contains(move), "Unexpected move: " + new Move(move));
        }
    }

    private Set<Integer> parseMoves(Game game, String moves) {
        return Arrays.stream(moves.split(" "))
                .map(moveStr -> MoveDescription.fromString(moveStr, game.getTurn()))
                .map(moveDescription -> game.getBoard().moveDescriptionToMove(moveDescription))
                .map(Move::move)
                .collect(Collectors.toSet());
    }
}
