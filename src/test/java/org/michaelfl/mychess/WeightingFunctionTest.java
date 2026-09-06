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
        testPosition(pgn, 0.53f); // was 0.4; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    @Test
    void testPosition02() {
        var pgn = """
                1. e4 c5
                """;
        testPosition(pgn, 0.54f); // was 0.45; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    @Test
    void testPosition03() {
        var pgn = """
                1. e4 c5 2. Nf3
                """;
        testPosition(pgn, 0.96f); // was 0.76; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    @Test
    void testPosition04() {
        var pgn = """
                1. e4 c5 2. Nf3 d6
                """;
        testPosition(pgn, 0.62f); // was 0.48; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    @Test
    void testPosition05() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4
                """;
        testPosition(pgn, 1.14f); // was 0.82; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    @Test
    void testPosition06() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4
                """;
        testPosition(pgn, -0.31f); // was -0.17; shifted by the PeSTO piece-square tables (v4.4.0), then 0.11; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition07() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4
                """;
        testPosition(pgn, 0.95f); // was 0.71; shifted by the PeSTO piece-square tables (v4.4.0), then 1.16; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition08() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6
                """;
        testPosition(pgn, 0.33f); // was 0.19; shifted by the PeSTO piece-square tables (v4.4.0), then 0.54; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition09() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3
                """;
        testPosition(pgn, 0.85f); // was 0.61; shifted by the PeSTO piece-square tables (v4.4.0), then 1.06; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition10() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6
                """;
        testPosition(pgn, 0.8f); // was 0.66; shifted by the PeSTO piece-square tables (v4.4.0), then 1.01; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition11() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5
                """;
        testPosition(pgn, 1.13f); // was 0.72; shifted by the PeSTO piece-square tables (v4.4.0), then 1.34; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition12() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6
                """;
        testPosition(pgn, 1.09f); // was 0.71; shifted by the PeSTO piece-square tables (v4.4.0), then 1.3; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition13() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4
                """;
        testPosition(pgn, 0.95f); // was 0.53; shifted by the PeSTO piece-square tables (v4.4.0), then 1.16; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition14() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7
                """;
        testPosition(pgn, 0.78f); // was 0.44; shifted by the PeSTO piece-square tables (v4.4.0), then 0.99; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition15() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3
                """;
        testPosition(pgn, 0.77f); // was 0.33; shifted by the PeSTO piece-square tables (v4.4.0), then 0.98; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition16() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7
                """;
        testPosition(pgn, 0.51f); // was 0.23; shifted by the PeSTO piece-square tables (v4.4.0), then 0.72; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition17() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O
                """;
        testPosition(pgn, 1.28f); // was 1.34; shifted by the PeSTO piece-square tables (v4.4.0), then 1.49; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition18() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7
                """;
        testPosition(pgn, 1.04f); // was 1.04; shifted by the PeSTO piece-square tables (v4.4.0), then 1.25; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition19() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4
                """;
        testPosition(pgn, 0.92f); // was 0.9; shifted by the PeSTO piece-square tables (v4.4.0), then 1.13; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition20() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5
                """;
        testPosition(pgn, 1.05f); // was 1.04; shifted by the PeSTO piece-square tables (v4.4.0), then 1.26; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition21() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6
                """;
        testPosition(pgn, 4.59f);
    }

    @Test
    void testPosition22() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6
                """;
        testPosition(pgn, 0.56f); // was 0.85; shifted by the PeSTO piece-square tables (v4.4.0), then 0.75; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition23() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5
                """;
        testPosition(pgn, 0.77f); // v4.3.3 bishop-pair (black holds the pair), then 1.05; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition24() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7
                """;
        testPosition(pgn, 0.82f); // v4.3.3 bishop-pair (black holds the pair), then 0.94; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition25() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5
                """;
        testPosition(pgn, 0.78f); // was 0.87; shifted by the PeSTO piece-square tables (v4.4.0), then 0.97; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition26() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+
                """;
        testPosition(pgn, -0.73f); // was -0.44; shifted by the PeSTO piece-square tables (v4.4.0), then -0.54; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition27() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1
                """;
        testPosition(pgn, 0.02f); // was 0.19; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    @Test
    void testPosition28() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5
                """;
        testPosition(pgn, -0.48f); // was -0.03; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    // ** Unguarded bishop attacked by queen
    @Test
    void testPosition29() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5
                """;
        testPosition(pgn, -0.11f); // was 0.5; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    // ** White wins (back) a pawn with Nxe6
    @Test
    void testPosition30() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8
                """;
        testPosition(pgn, 0.04f); // was 0.52; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    @Test
    void testPosition31() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6
                """;
        testPosition(pgn, 2.02f); // was 2.1; shifted by the PeSTO piece-square tables (v4.4.0), then 1.83; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition32() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6
                """;
        testPosition(pgn, -2.85f); // was -2.01; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    @Test
    void testPosition33() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6
                """;
        testPosition(pgn, 1.36f); // was 1.51; shifted by the PeSTO piece-square tables (v4.4.0), then 1.01; shifted by the king-line danger term (4.6.0-king-line)
    }

    @Test
    void testPosition34() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O
                """;
        testPosition(pgn, 0.11f); // was 0.16; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    @Test
    void testPosition35() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O 18. Rg1
                """;
        testPosition(pgn, 0.26f); // was 0.46; shifted by the PeSTO piece-square tables (v4.4.0)
    }

    // Terrible position for black (no mobility, pieces on bad, narrow positions)
    @Test
    void testPosition36() {
        var pgn = """
                1. Nc3 e5 2. Nf3 Nc6 3. d4 d6 4. d5 Nb4 5. a3 Na6 6. e4 Nc5 7. Be3 b6 8. b4 Nb7 9. Bb5+
                Bd7 10. Qd3 Nf6 11. Bxd7+ Qxd7 12. Qa6 Qc8 13. Qc4 Be7 14. Qc6+ Nd7 15. Nb5
                """;
        testPosition(pgn, 2.04f); // was 0.19; shifted by the PeSTO piece-square tables (v4.4.0)
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

    // ---- Regression: en-passant capture path in _calculateForWhitePawn / _calculateForBlackPawn ----

    private static float scoreOf(String pgn) {
        var game = GameImporter.importerFor(pgn).importGame();
        return new WeightingFunction().calculate(game.getBoard()) / 100f;
    }

    /**
     * White has a pawn on e5 but black's last move was not a double-step
     * adjacent to it. The en-passant code path in
     * {@code _calculateForWhitePawn} must NOT register a phantom capture.
     */
    @Test
    void whitePawnOnRank5_noEnPassantWhenBlackDidNotJustDoubleStep() {
        // Note: the two PGNs reach positions that differ ONLY in the side-to-move
        // and in whether en-passant is available — both have a white pawn on e5
        // and black pawns on identical other files. The (a) variant has the
        // en-passant target square live, the (b) variant does not.
        float withEp    = scoreOf("1. e4 a6 2. e5 f5");      // black just played f7-f5 -> exf6 legal
        float withoutEp = scoreOf("1. e4 a6 2. e5 f5 3. a3 a5"); // two more quiet plies -> en-passant target cleared

        assertTrue(withEp > withoutEp,
                "Position with a real en-passant target should score better for white than the same shape without it. " +
                        "withEp=" + withEp + ", withoutEp=" + withoutEp);
    }

    /**
     * Mirror-image check for black: black pawn on d4, white plays a double step
     * next to it -> en-passant target live; without the double step -> no target.
     */
    @Test
    void blackPawnOnRank4_noEnPassantWhenWhiteDidNotJustDoubleStep() {
        float withEp    = scoreOf("1. a3 e5 2. a4 e4 3. d4");        // white d2-d4 -> exd3 legal
        float withoutEp = scoreOf("1. a3 e5 2. a4 e4 3. d4 a6 4. h3"); // en-passant target gone

        assertTrue(withEp < withoutEp,
                "Position with a real en-passant target should score worse for white (black gets the capture). " +
                        "withEp=" + withEp + ", withoutEp=" + withoutEp);
    }

    /**
     * Direct regression for the spurious-credit bug: a white pawn on rank 5 with
     * NO adjacent black pawn must not be granted any en-passant threat bonus.
     * Without the fix, this position scored ~0.02-0.04 pawns higher.
     */
    @Test
    void whitePawnOnRank5_noEnPassantTargetMeansNoExtraThreat() {
        // White pawn on e5, the only black pawns on the queen side, prior move was h2-h3
        // (white) and then it's black's turn — but black has nothing on d/f files so
        // the en-passant code path must NOT activate.
        float scoreWithoutEp = scoreOf("1. e4 a6 2. e5 a5 3. h3");
        // The current eval at this position is known-good; we anchor it within a
        // ±0.05 band around the value we measured after the bug fix. Re-baselined
        // 0.54 -> 0.62 for the PeSTO piece-square tables (v4.4.0).
        assertEquals(0.62f, scoreWithoutEp, 0.05f,
                "Score without en-passant target must be stable around the fixed baseline");
    }

    // ---------- getMaterialWeightOfMove ----------
    // Centipawn material delta of a single packed move, sign-positive
    // (returns |gain|). Covers every branch of the switch: sentinel 0,
    // normal quiet move, normal capture, en-passant (previously missing —
    // regression anchor), each of the four promotion targets, promotion
    // with capture, and castling.

    private static final int PAWN_CP = 100;

    @Test
    void materialWeightOfMove_sentinelZero_returnsZero() {
        assertEquals(0, WeightingFunction.getMaterialWeightOfMove(0),
                "the null-move sentinel has no material delta");
    }

    @Test
    void materialWeightOfMove_normalMoveWithoutCapture_returnsZero() {
        int move = Move.create(Board.e2, Board.e4, Board.empty, Move.typeNormal);

        assertEquals(0, WeightingFunction.getMaterialWeightOfMove(move),
                "quiet moves have no material delta");
    }

    @Test
    void materialWeightOfMove_normalCapture_returnsCapturedWeight() {
        int move = Move.create(Board.f3, Board.e5, Board.blackKnight, Move.typeNormal);

        assertEquals(300, WeightingFunction.getMaterialWeightOfMove(move),
                "a normal capture reports the captured piece's centipawn value");
    }

    @Test
    void materialWeightOfMove_enPassantCapture_returnsPawnWeight() {
        // En-passant captures a pawn — the fix locks this in. Before the fix
        // this branch fell through to the switch default and returned 0,
        // silently under-weighting en-passant in delta-pruning and the
        // material-only shortcut in PositionSearch.
        int move = Move.create(Board.d5, Board.e6, Board.blackPawn, Move.typeEnPassant);

        assertEquals(PAWN_CP, WeightingFunction.getMaterialWeightOfMove(move),
                "en-passant captures a pawn — the returned value must reflect that");
    }

    @Test
    void materialWeightOfMove_promotionToQueen_noCapture_returnsQueenMinusPawn() {
        int move = Move.create(Board.a7, Board.a8, Board.empty, Move.typePawnPromotionQueen);

        assertEquals(1000 - PAWN_CP, WeightingFunction.getMaterialWeightOfMove(move),
                "queen promotion adds queen weight, subtracts the consumed pawn's weight");
    }

    @Test
    void materialWeightOfMove_promotionToKnight_noCapture_returnsKnightMinusPawn() {
        int move = Move.create(Board.a7, Board.a8, Board.empty, Move.typePawnPromotionKnight);

        assertEquals(300 - PAWN_CP, WeightingFunction.getMaterialWeightOfMove(move));
    }

    @Test
    void materialWeightOfMove_promotionToRook_noCapture_returnsRookMinusPawn() {
        int move = Move.create(Board.a7, Board.a8, Board.empty, Move.typePawnPromotionRook);

        assertEquals(500 - PAWN_CP, WeightingFunction.getMaterialWeightOfMove(move));
    }

    @Test
    void materialWeightOfMove_promotionToBishop_noCapture_returnsBishopMinusPawn() {
        int move = Move.create(Board.a7, Board.a8, Board.empty, Move.typePawnPromotionBishop);

        assertEquals(300 - PAWN_CP, WeightingFunction.getMaterialWeightOfMove(move));
    }

    @Test
    void materialWeightOfMove_promotionWithCapture_returnsPromotedMinusPawnPlusCaptured() {
        int move = Move.create(Board.b7, Board.a8, Board.blackKnight, Move.typePawnPromotionQueen);

        // Gain: promoted queen (+1000) - consumed pawn (-100) + captured knight (+300) = 1200
        assertEquals(1000 - PAWN_CP + 300, WeightingFunction.getMaterialWeightOfMove(move),
                "promotion-with-capture aggregates promotion delta and captured-piece value");
    }

    @Test
    void materialWeightOfMove_castlingKingSide_returnsZero() {
        int move = Move.create(Board.e1, Board.g1, Board.empty, Move.typeCastlingKingSide);

        assertEquals(0, WeightingFunction.getMaterialWeightOfMove(move),
                "castling produces no material delta");
    }

    @Test
    void materialWeightOfMove_castlingQueenSide_returnsZero() {
        int move = Move.create(Board.e1, Board.c1, Board.empty, Move.typeCastlingQueenSide);

        assertEquals(0, WeightingFunction.getMaterialWeightOfMove(move),
                "castling produces no material delta");
    }

    // --- the containsIllegalMove sentinel -------------------------------------------------
    //
    // A position in which the side to move can capture the enemy king is unreachable by legal
    // play: it means the previous move left its own king en prise. The evaluation detects this
    // itself and answers ILLEGAL_WEIGHT instead of a score.
    //
    // Inside the search that detection is redundant — MoveGenerator returns Moves.ILLEGAL and
    // QuiescenceSearch probes canCaptureOpposingKing(). It is NOT redundant for the Texel corpus
    // builders, which call analyzeFactors directly, with no move generator and no quiescence
    // around it, and drop a position on isIllegalWeight. Nothing covered that until these tests,
    // so removing the sentinel would have left the bench signature bit-identical and every test
    // green while silently admitting illegal positions into every tuning corpus.

    /** White rook on e8 attacks the black king on e5 while it is white's turn. */
    private static final String ILLEGAL_WHITE_TO_MOVE = "4R3/8/8/4k3/8/8/8/4K3 w - - 0 1";

    /** The mirror image: black rook on e1 attacks the white king on e4 while it is black's turn. */
    private static final String ILLEGAL_BLACK_TO_MOVE = "4k3/8/8/8/4K3/8/8/4r3 b - - 0 1";

    /** Same material as {@link #ILLEGAL_WHITE_TO_MOVE}, but the rook does not attack the king. */
    private static final String LEGAL_SAME_MATERIAL = "7R/8/8/4k3/8/8/8/4K3 w - - 0 1";

    @Test
    void illegalPosition_whiteToMove_evaluatesToIllegalWeightPos() {
        var evaluator = new WeightingFunction();

        int weight = evaluator.calculate(Fen.importFEN(ILLEGAL_WHITE_TO_MOVE));

        assertEquals(WeightingFunction.ILLEGAL_WEIGHT_POS, weight,
                "white to move and able to capture the black king must evaluate to ILLEGAL_WEIGHT_POS");
    }

    @Test
    void illegalPosition_blackToMove_evaluatesToIllegalWeightNeg() {
        var evaluator = new WeightingFunction();

        int weight = evaluator.calculate(Fen.importFEN(ILLEGAL_BLACK_TO_MOVE));

        assertEquals(WeightingFunction.ILLEGAL_WEIGHT_NEG, weight,
                "the evaluation is white-positive, so the same condition with black to move must be "
                        + "ILLEGAL_WEIGHT_NEG; QuiescenceSearch multiplies by weightFactor and both "
                        + "branches arrive at +ILLEGAL_WEIGHT_POS from the node's own perspective");
    }

    @Test
    void illegalPosition_isRecognizedByIsIllegalWeight() {
        var evaluator = new WeightingFunction();

        assertTrue(WeightingFunction.isIllegalWeight(evaluator.calculate(Fen.importFEN(ILLEGAL_WHITE_TO_MOVE))),
                "isIllegalWeight is the predicate every caller outside the search uses");
        assertTrue(WeightingFunction.isIllegalWeight(evaluator.calculate(Fen.importFEN(ILLEGAL_BLACK_TO_MOVE))),
                "isIllegalWeight must recognize the negative branch as well");
    }

    /**
     * The path the Texel corpus builders take: {@code analyzeFactors} rather than
     * {@code calculate}. They drop a position when its eval is an illegal weight, so this is the
     * one consumer that has no other detector behind it.
     */
    @Test
    void illegalPosition_isReportedByAnalyzeFactors() {
        var evaluator = new WeightingFunction();

        var breakdown = evaluator.analyzeFactors(Fen.importFEN(ILLEGAL_WHITE_TO_MOVE));

        assertTrue(WeightingFunction.isIllegalWeight(breakdown.eval()),
                "analyzeFactors must surface the sentinel, or every Texel corpus silently keeps "
                        + "illegal positions with an ordinary-looking score");
    }

    /**
     * Guards the four tests above from passing vacuously: with the same material on the board but
     * the rook off the king's file, the sentinel must stay silent and an ordinary score come back.
     */
    @Test
    void legalPosition_withTheSameMaterial_doesNotFireTheSentinel() {
        var evaluator = new WeightingFunction();

        int weight = evaluator.calculate(Fen.importFEN(LEGAL_SAME_MATERIAL));

        assertFalse(WeightingFunction.isIllegalWeight(weight),
                "a rook that does not attack the enemy king must produce an ordinary score, got " + weight);
    }
}
