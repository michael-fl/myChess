package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.MyChessEngine;

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

        assertThrows(IllegalMoveException.class, () -> game.makeMove(MoveDescription.fromString(move, game.getTurn())));
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

        assertThrows(IllegalMoveException.class, () -> game.makeMove(MoveDescription.fromString(move, game.getTurn())));
    }

    @Test
    void testWrongCapture() {
        var game = new Game();

        assertThrows(IllegalMoveException.class, () -> game.makeMove(MoveDescription.fromString("Nb1xc3", game.getTurn())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"b1-c3", "b1c3", "Nb1-c3", "Nb1c3", "Nbc3", "N1c3", "e2-c3", "e2c3", "Ne2-c3", "Ne2c3", "Nec3", "N2c3"})
    void testKnightMoves2(String move) {
        var importer = new SimpleNotationImporter("[[e2-e4 e7-e5 g1-e2 d7-d6]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.c3);
        assertEquals(Board.whiteKnight, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nc3"})
    void testWrongKnightMoves2(String move) {
        var importer = new SimpleNotationImporter("[[e2-e4 e7-e5 g1-e2 d7-d6]]");
        var game = importer.importGame();
        assertThrows(IllegalMoveException.class, () -> game.makeMove(MoveDescription.fromString(move, game.getTurn())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nb1c3", "Nbc3", "Nd1c3", "Ndc3"})
    void testKnightMoves3(String move) {
        var importer = new SimpleNotationImporter("[[f2-f3 e7-e6 g1-h3 d7-d6 h3-f2 c8-d7 e2-e3 f8-e7 d1-e2 g8-f6 f2-d1 e8-g8]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.c3);
        assertEquals(Board.whiteKnight, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nc3", "N1c3"})
    void testWrongKnightMoves3(String move) {
        var importer = new SimpleNotationImporter("[[f2-f3 e7-e6 g1-h3 d7-d6 h3-f2 c8-d7 e2-e3 f8-e7 d1-e2 g8-f6 f2-d1 e8-g8]]");
        var game = importer.importGame();
        assertThrows(IllegalMoveException.class, () -> game.makeMove(MoveDescription.fromString(move, game.getTurn())));
    }
    
    @Test
    void testCheckMove() {
        var importer = new SimpleNotationImporter("[[e2-e4 e7-e5 d2-d3]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString("Bb4+", game.getTurn()));
    }

    @Test
    void testWrongCheckMove() {
        var importer = new SimpleNotationImporter("[[e2-e4 e7-e5 d2-d3]]");
        var game = importer.importGame();
        assertThrows(IllegalMoveException.class, () -> game.makeMove(MoveDescription.fromString("Bc5+", game.getTurn())));
    }

    @Test
    void testCheckmateMove() {
        var importer = new SimpleNotationImporter("[[f2-f3 e7-e6 g2-g4]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString("Qh4#", game.getTurn()));
        assertEquals(GameResult.CHECKMATE, game.getResult());
    }

    @Test
    void testWrongCheckmateMove() {
        var importer = new SimpleNotationImporter("[[f2-f3 e7-e6 g2-g4]]");
        var game = importer.importGame();
        assertThrows(IllegalMoveException.class, () -> game.makeMove(MoveDescription.fromString("Qg5#", game.getTurn())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"d8Q", "d8=Q", "d7d8Q", "d7-d8=Q!"})
    void testPawnPromotionMoveQueen(String move) {
        var importer = new SimpleNotationImporter("[[c2-c4 d7-d5 c4-d5 d8-d7 d5-d6 f7-f6 d2-d3 d7-c6 d6-d7 e8-f7]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.d8);
        assertEquals(Board.whiteQueen, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"d8R", "d8=R", "d7d8R", "d7-d8=R!"})
    void testPawnPromotionMoveRook(String move) {
        var importer = new SimpleNotationImporter("[[c2-c4 d7-d5 c4-d5 d8-d7 d5-d6 f7-f6 d2-d3 d7-c6 d6-d7 e8-f7]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.d8);
        assertEquals(Board.whiteRook, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"d8N", "d8=N", "d7d8N", "d7-d8=N!"})
    void testPawnPromotionMoveKnight(String move) {
        var importer = new SimpleNotationImporter("[[c2-c4 d7-d5 c4-d5 d8-d7 d5-d6 f7-f6 d2-d3 d7-c6 d6-d7 e8-f7]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.d8);
        assertEquals(Board.whiteKnight, piece, "wrong piece");
    }

    @ParameterizedTest
    @ValueSource(strings = {"d8B", "d8=B", "d7d8B", "d7-d8=B!"})
    void testPawnPromotionMoveBishop(String move) {
        var importer = new SimpleNotationImporter("[[c2-c4 d7-d5 c4-d5 d8-d7 d5-d6 f7-f6 d2-d3 d7-c6 d6-d7 e8-f7]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString(move, game.getTurn()));
        var piece = game.getBoard().get(Board.d8);
        assertEquals(Board.whiteBishop, piece, "wrong piece");
    }

    @Test
    void testWrongPawnPromotionMove() {
        var game = new Game();
        assertThrows(IllegalMoveException.class, () -> game.makeMove(MoveDescription.fromString("e4=Q", game.getTurn())));
    }

    @Test
    void testBlackCastlingKingSide() {
        var importer = new SimpleNotationImporter("[[e2-e4 c7-c5 f1-e2 b8-c6 f2-f4 e7-e6 g1-f3 b7-b6 e1-g1 c8-b7 d2-d3 d8-c7 c2-c3 g8-f6 a2-a4 d7-d5 e4-e5 f6-d7 b1-a3 a7-a6 d1-e1 c6-e7 a3-c2 e7-f5 g2-g4 f5-e7 e1-g3 h7-h5 h2-h3 d5-d4 c3-c4 e7-c6 c1-d2 g7-g6 f3-g5 f8-e7 g5-e4]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString("O-O", game.getTurn()));
        var piece = game.getBoard().get(Board.g8);
        assertEquals(Board.blackKing, piece, "wrong piece");
        piece = game.getBoard().get(Board.f8);
        assertEquals(Board.blackRook, piece, "wrong piece");
    }

    @Test
    void testBlackCastlingQueenSide() {
        var importer = new SimpleNotationImporter("[[e2-e4 c7-c5 f1-e2 b8-c6 f2-f4 e7-e6 g1-f3 b7-b6 e1-g1 c8-b7 d2-d3 d8-c7 c2-c3 g8-f6 a2-a4 d7-d5 e4-e5 f6-d7 b1-a3 a7-a6 d1-e1 c6-e7 a3-c2 e7-f5 g2-g4 f5-e7 e1-g3 h7-h5 h2-h3 d5-d4 c3-c4 e7-c6 c1-d2 g7-g6 f3-g5 f8-e7 g5-e4]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString("O-O-O", game.getTurn()));
        var piece = game.getBoard().get(Board.c8);
        assertEquals(Board.blackKing, piece, "wrong piece");
        piece = game.getBoard().get(Board.d8);
        assertEquals(Board.blackRook, piece, "wrong piece");
    }

    @Test
    void testPinMakesMoveUnique() {
        var importer = new SimpleNotationImporter("[[e2-e4 c7-c5 g1-f3 b8-c6 d2-d4 c5-d4 f3-d4 d7-d6 f1-b5 c8-d7 e1-g1 g7-g6 b2-b3 f8-g7 c1-b2 g8-f6 b1-c3 e8-g8 f1-e1 a8-c8 d4-c6 b7-c6 b5-a6 c8-b8 e4-e5 f6-g4 e5-d6 e7-d6 a6-e2 d8-a5 c3-a4 g7-b2 a4-b2 a5-e5 e2-g4 d7-g4 d1-g4 e5-b2 g4-c4 c6-c5 a1-d1 b8-b4 c4-d5 b2-a2 d5-d6 a2-c2 d1-a1 b4-b3 a1-a7 b3-b1]]");
        var game = importer.importGame();
        // two rook moves possible, but one is pinned
        game.makeMove(MoveDescription.fromString("Re7", game.getTurn()));
        var piece = game.getBoard().get(Board.e7);
        assertEquals(Board.whiteRook, piece, "wrong piece");
    }

    @Test
    void testEnPassantMove() {
        var importer = new SimpleNotationImporter("[[e2-e4 c7-c5 c2-c3 g8-f6 e4-e5 f6-d5 g1-f3 b8-c6 d2-d4 c5-d4 f1-c4 d5-b6 c4-b3 d7-d5]]");
        var game = importer.importGame();
        game.makeMove(MoveDescription.fromString("exd6", game.getTurn())); // en passant
        var piece = game.getBoard().get(Board.d6);
        assertEquals(Board.whitePawn, piece, "wrong piece");
    }

}
