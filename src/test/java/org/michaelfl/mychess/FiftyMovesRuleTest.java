package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Michael Fleischhauer
 */
class FiftyMovesRuleTest {

    @Test
    void testFiftyMovesRule() {
        SimpleNotationImporter importer = new SimpleNotationImporter("[[e2-e4 e7-e5 d2-d3 d7-d6 f1-e2 g8-f6 g1-f3 d8-d7 b1-c3 e8-d8 e1-g1 h8-g8 a1-b1 g8-h8 c1-e3 d7-e8 b1-c1 b8-c6 f3-e1 c8-e6 d1-d2 d8-c8 c1-a1 c8-b8 a1-d1 f6-g8 d1-a1 g8-h6 e1-f3 h6-f5 f3-h4 f5-e7 h4-f3 e8-c8 f1-b1 c6-d4 c3-d1 e7-c6 f3-e1 f8-e7 g1-h1 h8-d8 h1-g1 d8-e8 g1-f1 e8-f8 f1-g1 f8-g8 g1-h1 c8-f8 e3-f4 e7-h4 e2-f3 e6-c4 d2-e3 d4-e6 d1-c3 c6-e7 f3-g4 e7-g6 e1-f3 g6-h8 f3-g1 e6-d8 e3-c1 c4-e6 g4-d1 e6-c8 f4-d2 h4-e7 d2-e1 f8-e8 c3-e2 e7-f8 e2-g3 e8-d7 g3-f1 d7-e8 f1-e3 e8-e6 e3-c4 e6-e8 c4-a5 e8-e6 a5-b3 e6-e8 b3-c5 e8-e7 c5-e6 e7-e8 e6-g5 e8-e7 g5-h3 e7-e6 h3-f4 e6-e8 f4-e2 e8-e6 e2-c3 e6-e8 c3-d5 d8-c6 g1-f3]]");
        var game = importer.importGame();

        var halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(99, halfMoveClock, "Wrong half move clock");

        assertEquals(GameResult.ONGOING, game.getResult(), "Game should not yet be finished - one move left");

        game.makeMove(MoveDescription.fromString("c8-d7", game.getTurn()));
        halfMoveClock = game.getGameStatus().getHalfMoveClock();
        assertEquals(100, halfMoveClock, "Wrong half move clock");
        assertEquals(GameResult.DRAW, game.getResult(), "Game should be draw due to 50 moves rule");

        String expectedFEN = "rk2qbrn/pppb1ppp/2np4/3Np3/4P3/3P1N2/PPP2PPP/RRQBB2K w - - 100 53";
        assertEquals(expectedFEN, game.exportFEN(), "wrong FEN");
    }
}
