package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link PositionEncoding}.
 *
 * @author Michael Fleischhauer
 */
public class PositionEncodingTest {
    @Test
    void testStartPosition() {
        var game = new Game();
        var originalBoard = game.getBoard();
        var gameStatus = originalBoard.getGameStatus();

        var encodedBoard = PositionEncoding.encode(originalBoard);

        assertNotNull(encodedBoard, "No array returned");
        assertEquals(3, encodedBoard.length, "Unexpected length of array");

        var bitSet = BitSet.valueOf(encodedBoard);
        assertEquals(191, bitSet.length(), "Wrong number of bits");

        assertEquals(-281474976645121L, encodedBoard[0], "Wrong encoding");
        assertEquals(3838454862L, encodedBoard[1], "Wrong encoding");
        assertEquals(8492448966494982417L, encodedBoard[2], "Wrong encoding");

        var decodedBoard = PositionEncoding.decode(encodedBoard, gameStatus.getPlyCount(), gameStatus.getLastMove(), gameStatus.getHalfMoveClock());
        assertNotNull(decodedBoard, "No board returned");

        var expectedFEN = originalBoard.exportFEN();
        var actualFEN = decodedBoard.exportFEN();

        assertEquals(expectedFEN, actualFEN, "Board not correctly decoded");
    }

    @Test
    void testMultiplePositions() {
        var game = new Game();
        String[] moves = "b1-c3 d7-d6 e2-e4 e7-e5 g1-f3 g8-f6 d2-d4 d8-e7 c1-g5 b8-d7 f1-d3 d7-b6 g5-f6 e7-f6 c3-b5 f6-e7 d4-e5 d6-e5 e1-g1 a7-a6 b5-c3 e7-f6 c3-d5 b6-d5 e4-d5 c8-g4 d1-e2 g4-f3 g2-f3 e8-c8 e2-e4 c8-b8 a1-e1 f6-h6 g1-h1 f8-d6 f1-g1 h6-f6 e4-f5 f6-f5 d3-f5 g7-g6 f5-g4 h8-e8 e1-e2 d6-c5 c2-c4 c5-d4 g1-d1 d4-c5 d1-e1 f7-f5 g4-h3 c5-b4 e1-d1 e8-e7 a2-a3 b4-d6 b2-b4 e7-e8 c4-c5 d6-f8 e2-c2 f8-g7 h3-f1 e5-e4 f3-e4 f5-e4 b4-b5 a6-a5 f1-c4 g7-f6 d5-d6 c7-d6 d1-d6 f6-e5 d6-d5 e5-d4 c5-c6 b7-c6 b5-c6 b8-c7 d5-a5 e8-f8 a5-b5 f8-f2 c2-f2 d4-f2 c4-d5 e4-e3 b5-b7 c7-c8 d5-e6 d8-d7 b7-d7 f2-g3 h2-g3 h7-h5 d7-g7 c8-b8 c6-c7 b8-b7 c7-c8Q b7-b6 g7-b7 b6-a5 c8-a8".split(" ");

        for (String moveString : moves) {
            game.makeMove(MoveDescription.fromString(moveString, game.getTurn()));
            testPosition(game);
        }
    }

    private void testPosition(Game game) {
        var originalBoard = game.getBoard();
        var gameStatus = originalBoard.getGameStatus();

        var encodedBoard = PositionEncoding.encode(originalBoard);
        assertNotNull(encodedBoard, "No array returned");

        var decodedBoard = PositionEncoding.decode(encodedBoard, gameStatus.getPlyCount(), gameStatus.getLastMove(), gameStatus.getHalfMoveClock());
        assertNotNull(decodedBoard, "No board returned");

        var expectedFEN = originalBoard.exportFEN();
        var actualFEN = decodedBoard.exportFEN();

        assertEquals(expectedFEN, actualFEN, "Board not correctly decoded:\n" + originalBoard + "\n" + decodedBoard);
    }

}
