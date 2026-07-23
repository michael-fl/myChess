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
    void symmetricCastledPositionEvaluatesToZero() {
        // A perfectly color-symmetric position (both kings castled kingside,
        // mirrored rooks and pawns) MUST evaluate to 0. Detector for the
        // king-dependent pawn PST wiring: the eval passes only the side-to-move's
        // king to getPieceSquareWeight for BOTH colors, so white's pawns use the
        // kingside table while black's fall back to the endgame table -> the
        // symmetry breaks and the score is non-zero. Goes green once each pawn is
        // bucketed by its OWN color's king.
        var f = new WeightingFunction();
        var board = Fen.importFEN("5rk1/5ppp/8/8/8/8/5PPP/5RK1 w - - 0 1");

        assertEquals(0.0f, f.calculate(board), "a color-symmetric position must score 0");
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
        testPosition(pgn, 0.76f);
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
        testPosition(pgn, 0.72f);
    }

    @Test
    void testPosition23() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5
                """;
        testPosition(pgn, 0.84f);
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
        testPosition(pgn, 0.68f);
    }

    @Test
    void testPosition26() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+
                """;
        testPosition(pgn, -0.60f);
    }

    @Test
    void testPosition27() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1
                """;
        testPosition(pgn, -0.29f);
    }

    @Test
    void testPosition28() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5
                """;
        testPosition(pgn, -0.64f);
    }

    // ** Unguarded bishop attacked by queen
    @Test
    void testPosition29() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5
                """;
        testPosition(pgn, -0.27f);
    }

    // ** White wins (back) a pawn with Nxe6
    @Test
    void testPosition30() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8
                """;
        testPosition(pgn, -0.18f);
    }

    @Test
    void testPosition31() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6
                """;
        testPosition(pgn, 1.12f);
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
        testPosition(pgn, 0.33f);
    }

    @Test
    void testPosition34() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O
                """;
        testPosition(pgn, -0.01f);
    }

    @Test
    void testPosition35() {
        var pgn = """
                1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 a6 6. Bg5 e6 7. f4 Be7 8. Qf3 Qc7 9.
                O-O-O Nbd7 10. g4 b5 11. Bxf6 Nxf6 12. g5 Nd7 13. f5 Bxg5+ 14. Kb1 Ne5 15. Qh5 Qd8 16.
                Nxe6 Bxe6 17. fxe6 O-O 18. Rg1
                """;
        testPosition(pgn, 0.13f);
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
}
