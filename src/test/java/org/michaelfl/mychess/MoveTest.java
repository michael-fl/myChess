package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.michaelfl.mychess.Game.GameResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class MoveTest {

    @ParameterizedTest
    @ValueSource(strings = {"Pe2-e4", "e2-e4", "e2e4", "ee4", "2e4", "e4"})
    void testPawnMoves1(String move) {
        var game = new Game();

        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.e4);
        assertEquals(Board.whitePawn, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"e2-e5", "e2-f3", "e2xd3", "de4", "1e4", "e5"})
    void testWrongPawnMoves(String move) {
        var game = new Game();
        var moveDescr = MoveDescription.fromString(move, game.getTurn());

        assertThrows(IllegalMoveException.class, () -> game.makeMove(moveDescr));
    }

    @ParameterizedTest
    @ValueSource(strings = {"b1-c3", "b1c3", "Nb1-c3", "Nb1-c3!", "Nb1c3", "Nbc3", "N1c3", "Nc3", "Nc3!?"})
    void testKnightMoves1(String move) {
        var game = new Game();

        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.c3);
        assertEquals(Board.whiteKnight, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bc3", "1c3", "Nc1c2", "Nb1"})
    void testWrongKnightMoves1(String move) {
        var game = new Game();
        var moveDescr = MoveDescription.fromString(move, game.getTurn());

        assertThrows(IllegalMoveException.class, () -> game.makeMove(moveDescr));

        assertEquals(Board.whiteKnight, game.getBoard().get(Board.b1),
                "knight must stay on b1 after a rejected knight move");
        assertEquals(Board.empty, game.getBoard().get(Board.c3),
                "c3 must remain empty after a rejected knight move");
    }

    @Test
    void testWrongCapture() {
        var game = new Game();
        var moveDescr = MoveDescription.fromString("Nb1xc3", game.getTurn());

        assertThrows(IllegalMoveException.class, () -> game.makeMove(moveDescr));
    }

    @ParameterizedTest
    @ValueSource(strings = {"b1-c3", "b1c3", "Nb1-c3", "Nb1c3", "Nbc3", "N1c3", "e2-c3", "e2c3", "Ne2-c3", "Ne2c3", "Nec3", "N2c3"})
    void testKnightMoves2(String move) {
        var importer = GameImporter.importerFor("""
                1. e4 e5 2. Ne2 d6
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.c3);
        assertEquals(Board.whiteKnight, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nc3"})
    void testWrongKnightMoves2(String move) {
        var importer = GameImporter.importerFor("""
                1. e4 e5 2. Ne2 d6
                """);
        var game = importer.importGame();
        var moveDescr = MoveDescription.fromString(move, game.getTurn());
        assertThrows(IllegalMoveException.class, () -> game.makeMove(moveDescr));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nb1c3", "Nbc3", "Nd1c3", "Ndc3"})
    void testKnightMoves3(String move) {
        var importer = GameImporter.importerFor("""
                1. f3 e6 2. Nh3 d6 3. Nf2 Bd7 4. e3 Be7 5. Qe2 Nf6 6. Nd1 O-O
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.c3);
        assertEquals(Board.whiteKnight, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nc3", "N1c3"})
    void testWrongKnightMoves3(String move) {
        var importer = GameImporter.importerFor("""
                1. f3 e6 2. Nh3 d6 3. Nf2 Bd7 4. e3 Be7 5. Qe2 Nf6 6. Nd1 O-O
                """);
        var game = importer.importGame();
        var moveDescr = MoveDescription.fromString(move, game.getTurn());
        assertThrows(IllegalMoveException.class, () -> game.makeMove(moveDescr));
    }
    
    @Test
    void testCheckMove() {
        var importer = GameImporter.importerFor("""
                1. e4 e5 2. d3
                """);
        var game = importer.importGame();

        game.makeMove(MoveDescription.fromString("Bb4+", game.getTurn()));

        assertEquals(Board.blackBishop, game.getBoard().get(Board.b4),
                "bishop should be on b4 after Bb4+");
        assertTrue(game.getBoard().isKingChecked(),
                "white king must be in check after Bb4+");
    }

    @Test
    void testWrongCheckMove() {
        var importer = GameImporter.importerFor("""
                1. e4 e5 2. d3
                """);
        var game = importer.importGame();
        var moveDescr = MoveDescription.fromString("Bc5+", game.getTurn());
        assertThrows(IllegalMoveException.class, () -> game.makeMove(moveDescr));
    }

    @Test
    void testCheckmateMove() {
        var importer = GameImporter.importerFor("""
                1. f3 e6 2. g4
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString("Qh4#", game.getTurn()));
        assertEquals(GameResult.CHECKMATE, game.getResult());
    }

    @Test
    void testWrongCheckmateMove() {
        var importer = GameImporter.importerFor("""
                1. f3 e6 2. g4
                """);
        var game = importer.importGame();
        var moveDescr = MoveDescription.fromString("Qg5#", game.getTurn());
        assertThrows(IllegalMoveException.class, () -> game.makeMove(moveDescr));
    }

    @ParameterizedTest
    @ValueSource(strings = {"d8Q", "d8=Q", "d7d8Q", "d7-d8=Q!"})
    void testPawnPromotionMoveQueen(String move) {
        var importer = GameImporter.importerFor("""
                1. c4 d5 2. cxd5 Qd7 3. d6 f6 4. d3 Qc6 5. d7+ Kf7
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.d8);
        assertEquals(Board.whiteQueen, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"d8R", "d8=R", "d7d8R", "d7-d8=R!"})
    void testPawnPromotionMoveRook(String move) {
        var importer = GameImporter.importerFor("""
                1. c4 d5 2. cxd5 Qd7 3. d6 f6 4. d3 Qc6 5. d7+ Kf7
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.d8);
        assertEquals(Board.whiteRook, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"d8N", "d8=N", "d7d8N", "d7-d8=N!"})
    void testPawnPromotionMoveKnight(String move) {
        var importer = GameImporter.importerFor("""
                1. c4 d5 2. cxd5 Qd7 3. d6 f6 4. d3 Qc6 5. d7+ Kf7
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.d8);
        assertEquals(Board.whiteKnight, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"d8B", "d8=B", "d7d8B", "d7-d8=B!"})
    void testPawnPromotionMoveBishop(String move) {
        var importer = GameImporter.importerFor("""
                1. c4 d5 2. cxd5 Qd7 3. d6 f6 4. d3 Qc6 5. d7+ Kf7
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.d8);
        assertEquals(Board.whiteBishop, piece, "wrong piece");
    }

    @Test
    void testWrongPawnPromotionMove() {
        var game = new Game();
        var moveDescr = MoveDescription.fromString("e4=Q", game.getTurn());
        assertThrows(IllegalMoveException.class, () -> game.makeMove(moveDescr));
    }

    @Test
    void testBlackCastlingKingSide() {
        var importer = GameImporter.importerFor("""
                1. e4 c5 2. Be2 Nc6 3. f4 e6 4. Nf3 b6 5. O-O Bb7 6. d3 Qc7 7. c3 Nf6 8. a4 d5 9. e5 Nd7
                10. Na3 a6 11. Qe1 Ne7 12. Nc2 Nf5 13. g4 Ne7 14. Qg3 h5 15. h3 d4 16. c4 Nc6 17. Bd2 g6
                18. Ng5 Be7 19. Ne4
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString("O-O", game.getTurn()));
        var piece = game.getBoard().get(Board.g8);
        assertEquals(Board.blackKing, piece, "wrong piece");
        piece = game.getBoard().get(Board.f8);
        assertEquals(Board.blackRook, piece, "wrong piece");
    }

    @Test
    void testBlackCastlingQueenSide() {
        var importer = GameImporter.importerFor("""
                1. e4 c5 2. Be2 Nc6 3. f4 e6 4. Nf3 b6 5. O-O Bb7 6. d3 Qc7 7. c3 Nf6 8. a4 d5 9. e5 Nd7
                10. Na3 a6 11. Qe1 Ne7 12. Nc2 Nf5 13. g4 Ne7 14. Qg3 h5 15. h3 d4 16. c4 Nc6 17. Bd2 g6
                18. Ng5 Be7 19. Ne4
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString("O-O-O", game.getTurn()));
        var piece = game.getBoard().get(Board.c8);
        assertEquals(Board.blackKing, piece, "wrong piece");
        piece = game.getBoard().get(Board.d8);
        assertEquals(Board.blackRook, piece, "wrong piece");
    }

    @Test
    void testPinMakesMoveUnique() {
        var importer = GameImporter.importerFor("""
                1. e4 c5 2. Nf3 Nc6 3. d4 cxd4 4. Nxd4 d6 5. Bb5 Bd7 6. O-O g6 7. b3 Bg7 8. Bb2 Nf6 9. Nc3
                O-O 10. Re1 Rc8 11. Nxc6 bxc6 12. Ba6 Rb8 13. e5 Ng4 14. exd6 exd6 15. Be2 Qa5 16. Na4
                Bxb2 17. Nxb2 Qe5 18. Bxg4 Bxg4 19. Qxg4 Qxb2 20. Qc4 c5 21. Rad1 Rb4 22. Qd5 Qxa2 23.
                Qxd6 Qxc2 24. Ra1 Rxb3 25. Rxa7 Rb1
                """);
        var game = importer.importGame();
        // two rook moves possible, but one is pinned
        game.makeMove(MoveDescription.fromString("Re7", game.getTurn()));
        var piece = game.getBoard().get(Board.e7);
        assertEquals(Board.whiteRook, piece, "wrong piece");
    }

    @Test
    void testEnPassantMove() {
        var importer = GameImporter.importerFor("""
                1. e4 c5 2. c3 Nf6 3. e5 Nd5 4. Nf3 Nc6 5. d4 cxd4 6. Bc4 Nb6 7. Bb3 d5
                """);
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString("exd6", game.getTurn())); // en passant
        var piece = game.getBoard().get(Board.d6);
        assertEquals(Board.whitePawn, piece, "wrong piece");
    }

    @Test
    void testIllegalMoveIsReverted() {
        var importer = GameImporter.importerFor("""
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Be3 e5 7. Nf3 Be7 8. Bc4 O-O 9. O-O
                Nc6 10. Qe2 Bg4 11. h3 Bxf3 12. Qxf3 Qc7 13. Bb3 Qd7 14. Rad1 Bd8 15. Nd5 Nxd5 16. Bxd5
                Nb4 17. Bb3 Rc8 18. c3 Nc6 19. Bd5 Re8 20. b4 Bf6 21. a3 Ne7 22. c4 Nxd5 23. cxd5 Rc3 24.
                Ra1 Bg5 25. Rfc1 Rec8 26. Re1 R8c4 27. Kh2 Bh6 28. h4 Bxe3 29. Rxe3 Rxe3 30. Qxe3 f5 31.
                f3 fxe4 32. fxe4 Qg4 33. Qg5 Qxg5 34. hxg5 Rxe4 35. Rc1 Rg4 36. Rc8+ Kf7 37. Rc7+ Kg6 38.
                Rxb7 Rd4 39. Ra7 Rxd5 40. Rxa6 Kxg5 41. a4 Rd4 42. b5 Rh4+
                """);
        var game = importer.importGame();
        var moveDescr = MoveDescription.fromString("g3", game.getTurn());

        // make invalid move
        assertThrows(IllegalMoveException.class, () -> game.makeMove(moveDescr));

        // assure that move was not executed
        var piece = game.getBoard().get(Board.g2);
        assertEquals(Board.whitePawn, piece, "Wrong piece. Move not reverted.");
        piece = game.getBoard().get(Board.g3);
        assertEquals(Board.empty, piece, "Field should be empty. Move not reverted.");
    }
}
