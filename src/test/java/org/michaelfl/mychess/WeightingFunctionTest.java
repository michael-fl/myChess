package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class WeightingFunctionTest {

    @Test
    void testStartPosition() {
        var f = new WeightingFunction();
        var gameStatus = GameStatus.newGame();
        var board = Board.createEmptyBoard();

        var weight = f.calculate(gameStatus, board);
        assertEquals(0.0f, weight, "Wrong weight");
    }

    @Test
    void testPosition01() {
        testPosition("[[e2-e4]]", 0.5f);
    }

    @Test
    void testPosition02() {
        testPosition("[[e2-e4 c7-c5]]", 0.46f);
    }

    @Test
    void testPosition03() {
        testPosition("[[e2-e4 c7-c5 g1-f3]]", 0.86f);
    }

    @Test
    void testPosition04() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6]]", 0.48f);
    }

    @Test
    void testPosition05() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4]]", 0.97f);
    }

    @Test
    void testPosition06() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4]]", -0.18f);
    }

    @Test
    void testPosition07() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4]]", 1.07f);
    }

    @Test
    void testPosition08() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6]]", 0.53f);
    }

    @Test
    void testPosition09() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3]]", 0.95f);
    }

    @Test
    void testPosition10() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6]]", 1.03f);
    }

    @Test
    void testPosition11() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5]]", 1.37f);
    }

    @Test
    void testPosition12() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6]]", 1.39f);
    }

    @Test
    void testPosition13() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4]]", 1.2f);
    }

    @Test
    void testPosition14() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7]]", 1.04f);
    }

    @Test
    void testPosition15() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3]]", 1.11f);
    }

    @Test
    void testPosition16() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7]]", 0.95f);
    }

    @Test
    void testPosition17() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1]]", 1.32f);
    }

    @Test
    void testPosition18() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7]]", 0.92f);
    }

    @Test
    void testPosition19() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4]]", 0.91f);
    }

    @Test
    void testPosition20() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5]]", 0.9f);
    }

    @Test
    void testPosition21() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6]]", 4.05f);
    }

    @Test
    void testPosition22() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6]]", 0.95f);
    }

    @Test
    void testPosition23() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5]]", 1.1f);
    }

    @Test
    void testPosition24() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7]]", 0.9f);
    }

    @Test
    void testPosition25() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5]]", 0.93f);
    }

    @Test
    void testPosition26() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5]]", -0.58f);
    }

    @Test
    void testPosition27() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1]]", -0.19f);
    }

    @Test
    void testPosition28() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5]]", -0.54f);
    }

    // ** Unguarded bishop attacked by queen
    @Test
    void testPosition29() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5]]", -0.24f);
    }

    // ** White wins (back) a pawn with Nxe6
    @Test
    void testPosition30() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8]]", -0.07f);
    }

    @Test
    void testPosition31() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6]]", 1.11f);
    }

    @Test
    void testPosition32() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6]]", -2.73f);
    }

    @Test
    void testPosition33() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6]]", 0.57f);
    }

    @Test
    void testPosition34() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8]]", 0.12f);
    }

    @Test
    void testPosition35() {
        testPosition("[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1]]", 0.22f);
    }

    private void testPosition(String gameNotation, float expectedWeight) {
        float w1 = Math.round(expectedWeight * 0.9f * 100f) / 100f;
        float w2 = Math.round(expectedWeight * 1.1f * 100f) / 100f;
        float expectedMinWeight = expectedWeight >= 0 ? w1 : w2;
        float expectedMaxWeight = expectedWeight >= 0 ? w2 : w1;

        SimpleNotationImporter importer = new SimpleNotationImporter(gameNotation);
        var game = importer.importGame();
        var f = new WeightingFunction();
        var weight = f.calculate(game.getGameStatus(), game.getBoard());
        assertTrue(weight >= expectedMinWeight, "Wrong weight: " + weight + ". Expected minimum of " + expectedMinWeight);
        assertTrue(weight <= expectedMaxWeight, "Wrong weight: " + weight + ". Expected maximum of " + expectedMaxWeight);
    }

}
