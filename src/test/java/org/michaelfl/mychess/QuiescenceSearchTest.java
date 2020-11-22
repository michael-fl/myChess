package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.engines.MoveSorterImpl;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class QuiescenceSearchTest {

    @Test
    void testPositionAfterCapture() {
        SimpleNotationImporter importer = new SimpleNotationImporter("[[e2-e4 e7-e5 g1-f3 b8-c6 b1-c3 g8-f6 d2-d3 f8-b4 c1-d2 d7-d6 c3-d5 a7-a5 f1-e2 a8-a6 e1-g1 a6-b6 d1-e1 h7-h6 d5-b4]]");
        var game = importer.importGame();
        var moveGenerator = new MoveGenerator(new MoveSorterImpl());
        var statistics = new Statistics();
        var weightingFunction = new WeightingFunction();

        var quiescenceSearch = new QuiescenceSearch(game, moveGenerator, weightingFunction, statistics, game.getEngine().getConfig().getMaxQuiescenceDepth());
        var workingBoard = game.getBoard().copy();
        var capturedOnField = Board.b4;
        var weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        var materialWeight = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);

        assertEquals(-3.0f, materialWeight, "test setup error");
        assertEquals(Board.blackBishop, Move.getCapturedPiece(game.getGameStatus().getLastMove()), "test setup error");

        var weight = quiescenceSearch.quiescenceMaxSearch(workingBoard, capturedOnField, 0, materialWeight, 0f);

        assertTrue(Math.abs(weight) < 0.5f, "Unexpected weight: " + weight);
        assertTrue(statistics.getMaximumReachedDepth() >= 5, "Maximum search depth too low: " + statistics.getMaximumReachedDepth());
    }
}
