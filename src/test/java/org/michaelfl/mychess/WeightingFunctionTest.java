package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class WeightingFunctionTest {

    @Test
    void testCheckmateInFunctions() {
        assertEquals(WeightingFunction.CHECKMATE_WEIGHT_HIGH / 100f, WeightingFunction.checkmateIn(0), "wrong checkmate weight");
        assertEquals((WeightingFunction.CHECKMATE_WEIGHT_HIGH - 100) / 100f, WeightingFunction.checkmateIn(1),"wrong checkmate weight");
        assertEquals((WeightingFunction.CHECKMATE_WEIGHT_HIGH - 50 * 100) / 100f, WeightingFunction.checkmateIn(50), "wrong checkmate weight");

        assertEquals(WeightingFunction.CHECKMATE_WEIGHT_HIGH, WeightingFunction.checkmateInCenti(0), "wrong checkmate weight");
        assertEquals(WeightingFunction.CHECKMATE_WEIGHT_HIGH - 100, WeightingFunction.checkmateInCenti(1), "wrong checkmate weight");
        assertEquals(WeightingFunction.CHECKMATE_WEIGHT_HIGH - 50 * 100, WeightingFunction.checkmateInCenti(50), "wrong checkmate weight");
    }

    @Test
    void testCheckmateWeightToPliesFunctions() {
        var w1 = WeightingFunction.checkmateIn(0);
        assertEquals(0, WeightingFunction.checkmateWeightToPlies(w1), "wrong number of plies");
        w1 = WeightingFunction.checkmateIn(1);
        assertEquals(1, WeightingFunction.checkmateWeightToPlies(w1), "wrong number of plies");
        w1 = WeightingFunction.checkmateIn(50);
        assertEquals(50, WeightingFunction.checkmateWeightToPlies(w1), "wrong number of plies");

        var w2 = WeightingFunction.checkmateInCenti(0);
        assertEquals(0, WeightingFunction.checkmateWeightToPlies(w2), "wrong number of plies");
        w2 = WeightingFunction.checkmateInCenti(1);
        assertEquals(1, WeightingFunction.checkmateWeightToPlies(w2), "wrong number of plies");
        w2 = WeightingFunction.checkmateInCenti(50);
        assertEquals(50, WeightingFunction.checkmateWeightToPlies(w2), "wrong number of plies");
    }

    @Test
    void testStartPosition() {
        var f = new WeightingFunction();
        var board = Board.createNewGame();

        var weight = f.calculate(board);
        assertEquals(0.0f, weight, "Wrong weight");
    }

    @Test
    void testPosition01() {
        var pgn = """
                1. e4
                """;
        testPosition(pgn, 0.5f);
    }

    @Test
    void testPosition02() {
        var pgn = """
                1. e4 c5
                """;
        testPosition(pgn, 0.46f);
    }

    @Test
    void testPosition03() {
        var pgn = """
                1. e4 c5 2. Nf3
                """;
        testPosition(pgn, 0.86f);
    }

    @Test
    void testPosition04() {
        var pgn = """
                1. e4 c5 2. Nf3 d6
                """;
        testPosition(pgn, 0.48f);
    }

    @Test
    void testPosition05() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4
                """;
        testPosition(pgn, 0.97f);
    }

    @Test
    void testPosition06() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4
                """;
        testPosition(pgn, -0.18f);
    }

    @Test
    void testPosition07() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4
                """;
        testPosition(pgn, 1.07f);
    }

    @Test
    void testPosition08() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6
                """;
        testPosition(pgn, 0.53f);
    }

    @Test
    void testPosition09() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3
                """;
        testPosition(pgn, 0.95f);
    }

    @Test
    void testPosition10() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6
                """;
        testPosition(pgn, 1.03f);
    }

    @Test
    void testPosition11() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5
                """;
        testPosition(pgn, 1.37f);
    }

    @Test
    void testPosition12() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6
                """;
        testPosition(pgn, 1.39f);
    }

    @Test
    void testPosition13() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4
                """;
        testPosition(pgn, 1.2f);
    }

    @Test
    void testPosition14() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7
                """;
        testPosition(pgn, 1.04f);
    }

    @Test
    void testPosition15() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3
                """;
        testPosition(pgn, 1.11f);
    }

    @Test
    void testPosition16() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7
                """;
        testPosition(pgn, 0.95f);
    }

    @Test
    void testPosition17() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O
                """;
        testPosition(pgn, 1.32f);
    }

    @Test
    void testPosition18() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7
                """;
        testPosition(pgn, 0.92f);
    }

    @Test
    void testPosition19() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4
                """;
        testPosition(pgn, 0.91f);
    }

    @Test
    void testPosition20() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5
                """;
        testPosition(pgn, 0.9f);
    }

    @Test
    void testPosition21() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6
                """;
        testPosition(pgn, 4.05f);
    }

    @Test
    void testPosition22() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6
                """;
        testPosition(pgn, 0.95f);
    }

    @Test
    void testPosition23() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5
                """;
        testPosition(pgn, 1.1f);
    }

    @Test
    void testPosition24() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7
                """;
        testPosition(pgn, 0.9f);
    }

    @Test
    void testPosition25() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5
                """;
        testPosition(pgn, 0.93f);
    }

    @Test
    void testPosition26() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+
                """;
        testPosition(pgn, -0.50f);
    }

    @Test
    void testPosition27() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1
                """;
        testPosition(pgn, -0.19f);
    }

    @Test
    void testPosition28() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5
                """;
        testPosition(pgn, -0.54f);
    }

    // ** Unguarded bishop attacked by queen
    @Test
    void testPosition29() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5
                """;
        testPosition(pgn, -0.24f);
    }

    // ** White wins (back) a pawn with Nxe6
    @Test
    void testPosition30() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8
                """;
        testPosition(pgn, -0.07f);
    }

    @Test
    void testPosition31() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6
                """;
        testPosition(pgn, 1.11f);
    }

    @Test
    void testPosition32() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6
                """;
        testPosition(pgn, -2.73f);
    }

    @Test
    void testPosition33() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6
                """;
        testPosition(pgn, 0.57f);
    }

    @Test
    void testPosition34() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O
                """;
        testPosition(pgn, 0.12f);
    }

    @Test
    void testPosition35() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O 18. Rg1
                """;
        testPosition(pgn, 0.22f);
    }

    // Terrible position for black (no mobility, pieces on bad, narrow positions)
    @Test
    void testPosition36() {
        var pgn = """
                1. Nc3 e5 2. Nf3 Nc6 3. d4 d6 4. d5 Nb4 5. a3 Na6 6. e4 Nc5 7. Be3 b6 8. b4 Nb7 9. Bb5+
                Bd7 10. Qd3 Nf6 11. Bxd7+ Qxd7 12. Qa6 Qc8 13. Qc4 Be7 14. Qc6+ Nd7 15. Nb5
                """;
        testPosition(pgn, 1.04f);
    }

    private void testPosition(String gameNotation, float expectedWeight) {
        float w1 = Math.round(expectedWeight * 0.9f * 100f) / 100f;
        float w2 = Math.round(expectedWeight * 1.1f * 100f) / 100f;
        float expectedMinWeight = expectedWeight >= 0 ? w1 : w2;
        float expectedMaxWeight = expectedWeight >= 0 ? w2 : w1;

        GameImporter importer = GameImporter.importerFor(gameNotation);
        var game = importer.importGame();
        var f = new WeightingFunction();
        var weight = f.calculate(game.getBoard()) / 100f;
        assertTrue(weight >= expectedMinWeight, "Wrong weight: " + weight + ". Expected minimum of " + expectedMinWeight);
        assertTrue(weight <= expectedMaxWeight, "Wrong weight: " + weight + ". Expected maximum of " + expectedMaxWeight);
    }

}
