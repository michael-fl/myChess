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
}
