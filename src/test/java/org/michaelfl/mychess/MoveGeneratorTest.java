package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class MoveGeneratorTest {

    @Test
    void testWhiteEnPassantMove() {
        String movesStr = "[[e2-e4 h7-h6 e4-e5 f7-f5]]";
        var importer = new SimpleNotationImporter(movesStr);
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
        String movesStr = "[[a2-a3 e7-e5 a3-a4 e5-e4 d2-d4]]";
        var importer = new SimpleNotationImporter(movesStr);
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
    void testCastlingPossible1() {
        testCastlingPossible("[[d2-d3 c7-c5 g2-g3 g8-f6 f1-g2 e7-e6 g1-f3 d8-b6]]");
    }

    @Test
    void testCastlingNotPossible1() {
        testCastlingNotPossible("[[e2-e4 c7-c6 f2-f3 d8-b6 f1-e2 g8-f6 g1-h3 e7-e6]]");
    }

    @Test
    void testCastlingNotPossible2() {
        testCastlingNotPossible("[[g2-g3 b7-b6 f1-g2 c8-a6 e2-e4 e7-e5 g1-f3 g8-f6]]");
    }

    @Test
    void testCastlingNotPossible3() {
        testCastlingNotPossible("[[d2-d3 c7-c5 g2-g3 g8-f6 f1-g2 e7-e6 g1-f3 d8-a5]]");
    }

    private void testCastlingPossible(String movesStr) {
        var importer = new SimpleNotationImporter(movesStr);
        var game = importer.importGame();

        assertMovePossible("e1-g1", game);

        game.makeMove(MoveDescription.fromString("e1-g1", game.getTurn()));
    }

    private void testCastlingNotPossible(String movesStr) {
        var importer = new SimpleNotationImporter(movesStr);
        var game = importer.importGame();

        assertMoveNotPossible("e1-g1", game);

        var ex = assertThrows(IllegalStateException.class, () -> game.makeMove(MoveDescription.fromString("e1-g1", game.getTurn())));
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
}
