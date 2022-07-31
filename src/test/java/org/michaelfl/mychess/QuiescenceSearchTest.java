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
        var gameNotation = "[[e2-e4 e7-e5 g1-f3 b8-c6 b1-c3 g8-f6 d2-d3 f8-b4 c1-d2 d7-d6 c3-d5 a7-a5 f1-e2 a8-a6 e1-g1 a6-b6 d1-e1 h7-h6 d5-b4]]";
        quiescenceTest(gameNotation, Board.blackBishop, 3.0f, 0, 0.5f, 5);
    }

    // A good quiescence search should detect that the white knight is not protected
    // and can be captured as compensation for the captured black knight.
    @Test
    void testPositionWithUnguardedNight1() {
        var gameNotation = "1.c3 e6 2.Nf3 d6 3.a3 Nc6 4.Nh4 Nb4 5.d3 c5 6.axb4";
        // TODO: 0 < expectedWeight < 0.5 !
        quiescenceTest(gameNotation, Board.blackKnight, 3.0f, 2.7f, 2.9f, 2);
    }

    // A good quiescence search should detect that the white knight is not protected
    // and is a much more valuable target for the black queen than the pawn on d5.
    @Test
    void testPositionWithUnguardedNight2() {
        var gameNotation = "1.Nf3 e6 2.Nh4 d5 3.c3 a6 4.c4 b6 5.cxd5";
        // TODO: -2.0 < expectedWeight < 3.0 !
        quiescenceTest(gameNotation, Board.blackPawn, 1.0f, -0.6f, -0.5f, 1);
    }

    void quiescenceTest(String gameNotation, byte capturedPiece, float expectedMaterialWeight, float expectedWeightMin, float expectedWeightMax, int expectedMaximumReachedDepthMin) {
        var importer = GameImporter.importerFor(gameNotation);
        var game = importer.importGame();
        var moveGenerator = new MoveGenerator(new MoveSorterImpl());
        var statistics = new Statistics();
        var weightingFunction = new WeightingFunction();

        var quiescenceSearch = new QuiescenceSearch(game, moveGenerator, weightingFunction, statistics, game.getEngine().getConfig().getMaxQuiescenceDepth());
        var workingBoard = game.getBoard().copy();
        var weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        var materialWeight = WeightingFunction.calculateMaterialWeight(workingBoard);
        assertEquals(expectedMaterialWeight, materialWeight, "test setup error");

        var f = new WeightingFunction();
        var weight = f.calculate(game.getBoard());
        System.out.println("Position weight: " + weight);

        assertEquals(capturedPiece, Move.getCapturedPiece(game.getGameStatus().getLastMove()), "test setup error");

        var capturedOnField = Move.getToField(game.getGameStatus().getLastMove());
        var alpha = Float.NEGATIVE_INFINITY;
        var beta = Float.POSITIVE_INFINITY;
        weight = weightFactor * quiescenceSearch.quiescenceSearch(workingBoard, capturedOnField, 0, weightFactor, alpha, beta, weightFactor * materialWeight, 0f);
        System.out.println("Quiescence weight: " + weight);

        assertTrue(weight >= expectedWeightMin, "Unexpected weight: " + weight);
        assertTrue(weight <= expectedWeightMax, "Unexpected weight: " + weight);
        assertTrue(statistics.getMaximumReachedDepth() >= expectedMaximumReachedDepthMin, "Maximum search depth too low: " + statistics.getMaximumReachedDepth());
    }

}
