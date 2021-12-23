package org.michaelfl.mychess.openingdb;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Pgn.Result;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Michael Fleischhauer
 */
class DBValueTest {

    @Test
    void test() {
        var dbValue = new DBValue();

        var winMoves = List.of(1, 1, 3, 5, 2, 1, 3, 7, 8, 7, 6, 5, 7, 7, 7, 9, 10, 1, 1, 10);
        var lossMoves = List.of(5, 6, 2, 4, 4, 2, 2, 2, 7, 8, 4, 6, 8, 8, 1, 8, 10);
        var drawMoves = List.of(5, 9, 2, 3, 5, 8, 5, 9, 5, 6, 5, 5, 6, 10, 2, 1);

        for (var m : winMoves) {
            dbValue.addMove(m, GameStatus.TURN_WHITE, Result.WHITE_WINS);
        }
        for (var m : lossMoves) {
            dbValue.addMove(m, GameStatus.TURN_WHITE, Result.BLACK_WINS);
        }
        for (var m : drawMoves) {
            dbValue.addMove(m, GameStatus.TURN_WHITE, Result.DRAW);
        }

        assertEquals(4 + 10 * 16, dbValue.getBuffer().length, "wrong buffer size");

        assertEquals(winMoves.size() + lossMoves.size() + drawMoves.size(), dbValue.getPositionCount(), "wrong position count");

        int index = dbValue.getIndexOfMove(1);
        assertEquals(7, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(5, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(1, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(1, dbValue.getDrawCountByIndex(index), "wrong draw count");

        index = dbValue.getIndexOfMove(2);
        assertEquals(7, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(1, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(4, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(2, dbValue.getDrawCountByIndex(index), "wrong draw count");

        index = dbValue.getIndexOfMove(3);
        assertEquals(3, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(2, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(0, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(1, dbValue.getDrawCountByIndex(index), "wrong draw count");

        index = dbValue.getIndexOfMove(4);
        assertEquals(3, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(0, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(3, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(0, dbValue.getDrawCountByIndex(index), "wrong draw count");

        index = dbValue.getIndexOfMove(5);
        assertEquals(9, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(2, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(1, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(6, dbValue.getDrawCountByIndex(index), "wrong draw count");

        index = dbValue.getIndexOfMove(6);
        assertEquals(5, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(1, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(2, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(2, dbValue.getDrawCountByIndex(index), "wrong draw count");

        index = dbValue.getIndexOfMove(7);
        assertEquals(6, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(5, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(1, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(0, dbValue.getDrawCountByIndex(index), "wrong draw count");

        index = dbValue.getIndexOfMove(8);
        assertEquals(6, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(1, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(4, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(1, dbValue.getDrawCountByIndex(index), "wrong draw count");

        index = dbValue.getIndexOfMove(9);
        assertEquals(3, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(1, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(0, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(2, dbValue.getDrawCountByIndex(index), "wrong draw count");

        index = dbValue.getIndexOfMove(10);
        assertEquals(4, dbValue.getCountByIndex(index), "wrong move count");
        assertEquals(2, dbValue.getWinCountByIndex(index), "wrong win count");
        assertEquals(1, dbValue.getLossCountByIndex(index), "wrong loss count");
        assertEquals(1, dbValue.getDrawCountByIndex(index), "wrong draw count");
    }
}
