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

        fail("Move " + moveStr + " expected");
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
