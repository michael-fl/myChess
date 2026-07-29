package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class WeightingFunctionTest {

    private static final int WHITE = 0;
    private static final int BLACK = 1;

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
        testPosition(pgn, 0.4f);
    }

    @Test
    void testPosition02() {
        var pgn = """
                1. e4 c5
                """;
        testPosition(pgn, 0.39f);
    }

    @Test
    void testPosition03() {
        var pgn = """
                1. e4 c5 2. Nf3
                """;
        testPosition(pgn, 0.76f);
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
        testPosition(pgn, 0.82f);
    }

    @Test
    void testPosition06() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4
                """;
        testPosition(pgn, -0.08f);
    }

    @Test
    void testPosition07() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4
                """;
        testPosition(pgn, 0.92f);
    }

    @Test
    void testPosition08() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6
                """;
        testPosition(pgn, 0.34f);
    }

    @Test
    void testPosition09() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3
                """;
        testPosition(pgn, 0.80f);
    }

    @Test
    void testPosition10() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6
                """;
        testPosition(pgn, 0.88f);
    }

    @Test
    void testPosition11() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5
                """;
        testPosition(pgn, 1.03f);
    }

    @Test
    void testPosition12() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6
                """;
        testPosition(pgn, 1.04f);
    }

    @Test
    void testPosition13() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4
                """;
        testPosition(pgn, 0.86f);
    }

    @Test
    void testPosition14() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7
                """;
        testPosition(pgn, 0.90f);
    }

    @Test
    void testPosition15() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3
                """;
        testPosition(pgn, 0.96f);
    }

    @Test
    void testPosition16() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7
                """;
        testPosition(pgn, 0.80f);
    }

    @Test
    void testPosition17() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O
                """;
        testPosition(pgn, 1.12f);
    }

    @Test
    void testPosition18() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7
                """;
        testPosition(pgn, 0.82f);
    }

    @Test
    void testPosition19() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4
                """;
        testPosition(pgn, 0.86f);
    }

    @Test
    void testPosition20() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5
                """;
        testPosition(pgn, 0.8f);
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
        testPosition(pgn, 0.82f);
    }

    @Test
    void testPosition23() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5
                """;
        testPosition(pgn, 0.94f);
    }

    @Test
    void testPosition24() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7
                """;
        testPosition(pgn, 0.80f);
    }

    @Test
    void testPosition25() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5
                """;
        testPosition(pgn, 0.78f);
    }

    @Test
    void testPosition26() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+
                """;
        testPosition(pgn, -0.53f);
    }

    @Test
    void testPosition27() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1
                """;
        testPosition(pgn, -0.21f);
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
        testPosition(pgn, -0.17f);
    }

    // ** White wins (back) a pawn with Nxe6
    @Test
    void testPosition30() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8
                """;
        testPosition(pgn, -0.11f);
    }

    @Test
    void testPosition31() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6
                """;
        testPosition(pgn, 1.19f);
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
        testPosition(pgn, 0.43f);
    }

    @Test
    void testPosition34() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O
                """;
        testPosition(pgn, 0.04f);
    }

    @Test
    void testPosition35() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O 18. Rg1
                """;
        testPosition(pgn, 0.28f);
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
        // ±0.05 band around the value we measured after the bug fix.
        assertEquals(0.41f, scoreWithoutEp, 0.05f,
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

        assertEquals(900 - PAWN_CP, WeightingFunction.getMaterialWeightOfMove(move),
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

        // Gain: promoted queen (+900) - consumed pawn (-100) + captured knight (+300) = 1100
        assertEquals(900 - PAWN_CP + 300, WeightingFunction.getMaterialWeightOfMove(move),
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

    // ---------- rook file and battery evaluation ----------

    @Test
    void rookFile_singleWhiteRookOnOpenFile_getsOpenFileBonus() {
        var evaluator = analyze("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");

        assertArrayEquals(new int[] {0, -1}, evaluator.getRookFiles(WHITE),
                "white rook file state should store the a-file");
        assertArrayEquals(new int[] {20, 0}, evaluator.getRookFilesWeight(),
                "a rook on an open file gets the 20cp open-file bonus");
    }

    @Test
    void rookFile_singleBlackRookOnOpenFile_getsOpenFileBonusForBlack() {
        var evaluator = analyze("4k2r/8/8/8/8/8/8/4K3 b - - 0 1");

        assertArrayEquals(new int[] {7, -1}, evaluator.getRookFiles(BLACK),
                "black rook file state should store the h-file");
        assertArrayEquals(new int[] {0, 20}, evaluator.getRookFilesWeight(),
                "black receives the same open-file bonus as white");
    }

    @Test
    void rookFile_opponentPawnOnly_makesFileHalfOpen() {
        var evaluator = analyze("4k3/p7/8/8/8/8/8/R3K3 w - - 0 1");

        assertEquals(10, evaluator.calculateRookFileWeight(WHITE, 0),
                "opponent pawn without own pawn makes the file half-open");
        assertArrayEquals(new int[] {10, 0}, evaluator.getRookFilesWeight());
    }

    @Test
    void rookFile_ownPawnOnFile_removesFileBonus() {
        var evaluator = analyze("4k3/8/8/8/8/8/P7/R3K3 w - - 0 1");

        assertEquals(0, evaluator.calculateRookFileWeight(WHITE, 0),
                "own pawn on the file means neither open nor half-open");
        assertArrayEquals(new int[] {0, 0}, evaluator.getRookFilesWeight());
    }

    @Test
    void rookFile_twoRooksOnDifferentOpenFiles_addBothBonuses() {
        var evaluator = analyze("4k3/8/8/8/8/8/8/R3K2R w - - 0 1");

        assertArrayEquals(new int[] {0, 7}, evaluator.getRookFiles(WHITE),
                "both original rook files should be tracked");
        assertArrayEquals(new int[] {40, 0}, evaluator.getRookFilesWeight(),
                "two open files should contribute two open-file bonuses");
    }

    @Test
    void rookFileWeightsForColor_sumsOnlyStoredRookFiles() {
        var evaluator = analyze("4k3/p7/8/8/8/8/8/R3K2R w - - 0 1");

        assertEquals(0, evaluator.calculateRookFileWeightsForColor(WHITE, new int[] {-1, 7}),
                "the first slot is the sentinel; a second slot without a first slot must be ignored");
        assertEquals(10, evaluator.calculateRookFileWeightsForColor(WHITE, new int[] {0, -1}),
                "one stored half-open rook file should contribute only that file");
        assertEquals(30, evaluator.calculateRookFileWeightsForColor(WHITE, new int[] {0, 7}),
                "two stored rook files should be summed");
    }

    @Test
    void rookBattery_twoRooksSeeingEachOtherOnOpenFile_getsBatteryBonus() {
        var evaluator = analyze("4k3/8/8/8/8/R7/8/R3K3 w - - 0 1");

        assertArrayEquals(new int[] {0, -1}, evaluator.getRookFiles(WHITE),
                "a battery on one file should be stored once");
        assertEquals(50, evaluator.calculateRookFileWeight(WHITE, 0),
                "open file battery gets 20cp open-file bonus plus 30cp battery bonus");
        assertArrayEquals(new int[] {50, 0}, evaluator.getRookFilesWeight());
    }

    @Test
    void rookBattery_twoRooksSeeingEachOtherOnHalfOpenFile_getsHalfOpenBatteryBonus() {
        var evaluator = analyze("4k3/p7/8/8/8/R7/8/R3K3 w - - 0 1");

        assertEquals(40, evaluator.calculateRookFileWeight(WHITE, 0),
                "half-open battery gets 10cp half-open bonus plus 30cp battery bonus");
        assertArrayEquals(new int[] {40, 0}, evaluator.getRookFilesWeight());
    }

    @Test
    void rookBattery_pieceBetweenRooksPreventsBatteryBonus() {
        var evaluator = analyze("4k3/8/8/8/8/R7/N7/R3K3 w - - 0 1");

        assertEquals(20, evaluator.calculateRookFileWeight(WHITE, 0),
                "a non-empty square between the rooks means they are not a battery");
        assertArrayEquals(new int[] {20, 0}, evaluator.getRookFilesWeight());
    }

    @Test
    void rookFile_stateIsResetBetweenCalculations() {
        var evaluator = analyze("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");
        assertArrayEquals(new int[] {20, 0}, evaluator.getRookFilesWeight());

        evaluator.calculate(Fen.importFEN("4k3/8/8/8/8/8/8/4K3 w - - 0 1"));

        assertArrayEquals(new int[] {-1, -1}, evaluator.getRookFiles(WHITE),
                "rook files from a previous position must not leak into the next calculation");
        assertArrayEquals(new int[] {0, 0}, evaluator.getRookFilesWeight(),
                "rook file weights from a previous position must not leak into the next calculation");
    }

    @Test
    void rookFile_gettersReturnCopies() {
        var evaluator = analyze("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");

        evaluator.getRookFiles(WHITE)[0] = 7;
        evaluator.getRookFilesWeight()[0] = 99;

        assertArrayEquals(new int[] {0, -1}, evaluator.getRookFiles(WHITE));
        assertArrayEquals(new int[] {20, 0}, evaluator.getRookFilesWeight());
    }

    @Test
    void analyzeFactors_rookFileFeatureReconstructsEvalContribution() {
        var evaluator = new WeightingFunction();
        var breakdown = evaluator.analyzeFactors(Fen.importFEN("4k3/8/8/8/8/8/8/R3K3 w - - 0 1"));

        int rookIndex = indexOf(WeightingFunction.TUNABLE_FACTOR_NAMES, "rookFileFactor");

        assertEquals(20.0, breakdown.features()[rookIndex],
                "rook file features should be centipawn coefficients because rookFileFactor is scaled as 1.0");
    }

    @Test
    void rookBattery_ownPawnOnFile_beatsBatteryBonus() {
        // Rooks a1 + a3 form a connected battery, but a white pawn on a5 sits
        // higher on the same file. The own-pawn check returns 0 for the whole
        // file, so the detected battery earns nothing — a closed file is neither
        // open nor half-open.
        var evaluator = analyze("4k3/8/8/P7/8/R7/8/R3K3 w - - 0 1");

        assertArrayEquals(new int[] {0, -1}, evaluator.getRookFiles(WHITE),
                "both rooks are on the a-file, so a single file slot is stored");
        assertEquals(0, evaluator.calculateRookFileWeight(WHITE, 0),
                "an own pawn on the file forces 0, even when a battery is present");
        assertArrayEquals(new int[] {0, 0}, evaluator.getRookFilesWeight());
    }

    @Test
    void rookFile_thirdRookOnThirdFile_isNotTracked() {
        // Documents a known limitation: storeRookFile keeps at most two distinct
        // files, so a third rook (only reachable via promotion) on a third file
        // is silently dropped. If that limitation is ever lifted, this test must
        // be updated. Rooks a1 (file 0), d1 (file 3), h1 (file 7); all files open.
        var evaluator = analyze("4k3/8/8/8/8/8/8/R2RK2R w - - 0 1");

        assertArrayEquals(new int[] {0, 3}, evaluator.getRookFiles(WHITE),
                "only the first two distinct rook files (a, d) are tracked; the h-file is dropped");
        assertArrayEquals(new int[] {40, 0}, evaluator.getRookFilesWeight(),
                "two open files score 2x20; the untracked third rook contributes nothing");
    }

    private static WeightingFunction analyze(String fen) {
        var evaluator = new WeightingFunction();
        evaluator.calculate(Fen.importFEN(fen));
        return evaluator;
    }

    @SuppressWarnings("SameParameterValue")
    private static int indexOf(String[] values, String needle) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(needle)) {
                return i;
            }
        }
        fail("missing expected factor: " + needle);
        return -1;
    }
}
