package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage for the non-pawn material tracking on {@link GameStatus}: the
 * process for computing {@link Board#calculateNonPawnMaterialWeights(byte[])}
 * from a raw board, the initial value carried by {@code newGame()}, and the
 * incremental updates driven by {@link Board#makeMove(int)} and
 * {@link Board#makeNullMove()} through {@code GameStatus.switchTurn}.
 *
 * <p>The invariant this class defends is: at every point in a game, the
 * incrementally-tracked {@code getWhiteNonPawnMaterialWeight} /
 * {@code getBlackNonPawnMaterialWeight} must equal the from-scratch
 * recomputation from the current raw board via
 * {@code calculateNonPawnMaterialWeights}. Every move type — normal, capture,
 * pawn-capture, en-passant, castling, promotion (all four target pieces),
 * promotion-with-capture — is exercised as its own test so a break in one
 * branch points directly at the responsible switch case.
 *
 * @author Michael Fleischhauer
 */
class NonPawnMaterialWeightTest {

    /** Standard chess starting non-pawn material: 2R + 2N + 2B + 1Q = 3100cp. */
    private static final int INITIAL_NON_PAWN_MATERIAL = 2 * 500 + 2 * 300 + 2 * 300 + 900;

    private static final int KNIGHT = 300;
    private static final int BISHOP = 300;
    private static final int ROOK   = 500;
    private static final int QUEEN  = 900;

    // ---------- Board.calculateNonPawnMaterialWeights (static, from scratch) ----------

    @Test
    void calculate_emptyBoard_returnsZero() {
        byte[] emptyBoard = Board.createEmptyRawBoard();

        int[] weights = Board.calculateNonPawnMaterialWeights(emptyBoard);

        assertArrayEquals(new int[] { 0, 0 }, weights,
                "empty board has no non-pawn material for either side");
    }

    @Test
    void calculate_standardStartPosition_matchesExpectedPerSide() {
        byte[] startBoard = Board.createNewGame().getRawBoard();

        int[] weights = Board.calculateNonPawnMaterialWeights(startBoard);

        assertEquals(INITIAL_NON_PAWN_MATERIAL, weights[0], "white non-pawn material at start");
        assertEquals(INITIAL_NON_PAWN_MATERIAL, weights[1], "black non-pawn material at start");
    }

    @Test
    void calculate_pawnsAndKingsOnly_returnsZero() {
        // 8/8/8/4k3/4K3/8/PPPPPPPP/8 with pawns and kings only — no non-pawn material.
        var board = Fen.importFEN("8/pppppppp/8/4k3/4K3/8/PPPPPPPP/8 w - - 0 1");

        int[] weights = Board.calculateNonPawnMaterialWeights(board.getRawBoard());

        assertArrayEquals(new int[] { 0, 0 }, weights,
                "kings weigh 0 and pawns are excluded, so pawn-only endgames report 0");
    }

    @Test
    void calculate_asymmetricPosition_reportsDifferentValuesPerSide() {
        // White has 2 rooks + queen (1900), black has only a rook (500).
        var board = Fen.importFEN("4k2r/8/8/8/8/8/8/R3K2R w KQ - 0 1");

        int[] weights = Board.calculateNonPawnMaterialWeights(board.getRawBoard());

        assertEquals(2 * ROOK, weights[0], "white 2 rooks");
        assertEquals(ROOK, weights[1], "black 1 rook");
    }

    @Test
    void calculate_ignoresPawns_evenWhenBoardIsFull() {
        // White has 2 knights + 8 pawns; black has 1 bishop + 8 pawns. Verify
        // that pawn contributions do NOT bleed into the non-pawn total.
        var board = Fen.importFEN("4kb2/pppppppp/8/8/8/8/PPPPPPPP/1N2K1N1 w - - 0 1");

        int[] weights = Board.calculateNonPawnMaterialWeights(board.getRawBoard());

        assertEquals(2 * KNIGHT, weights[0], "only white knights count; pawns excluded");
        assertEquals(BISHOP, weights[1], "only black bishop counts; pawns excluded");
    }

    @Test
    void calculate_matchesAcrossAllChess960StartPositions() {
        // Every Chess960 starting FEN has the same set of non-pawn pieces per
        // side (2R + 2N + 2B + 1Q + K), just permuted on the back rank.
        // The totals must therefore be the same as standard chess for every
        // position ID.
        for (int id = 0; id < Chess960StartPositions.COUNT; id++) {
            var fen = Chess960StartPositions.fenById(id);
            var board = Fen.importChess960FEN(fen);

            int[] weights = Board.calculateNonPawnMaterialWeights(board.getRawBoard());
            assertEquals(INITIAL_NON_PAWN_MATERIAL, weights[0],
                    "white non-pawn material for Chess960 position #" + id);
            assertEquals(INITIAL_NON_PAWN_MATERIAL, weights[1],
                    "black non-pawn material for Chess960 position #" + id);
        }
    }

    // ---------- GameStatus initial values ----------

    @Test
    void newGame_bothSidesHaveEqualStartingMaterial() {
        var status = new Game().getBoard().getGameStatus();

        assertEquals(status.getWhiteNonPawnMaterialWeight(), status.getBlackNonPawnMaterialWeight(),
                "standard chess starts symmetrically");
    }

    @Test
    void newGame_startingMaterialEquals3100Centipawns() {
        var status = new Game().getBoard().getGameStatus();

        assertEquals(INITIAL_NON_PAWN_MATERIAL, status.getWhiteNonPawnMaterialWeight(),
                "2R + 2N + 2B + 1Q = 3100cp");
    }

    // ---------- Incremental updates: no-change move types ----------

    @Test
    void makeMove_normalPieceMove_leavesBothMaterialsUnchanged() {
        assertMaterialUnchanged("1. Nf3");
    }

    @Test
    void makeMove_normalPawnMove_leavesBothMaterialsUnchanged() {
        assertMaterialUnchanged("1. e4");
    }

    @Test
    void makeMove_pawnCapturesPawn_leavesBothMaterialsUnchanged() {
        // 1. e4 d5 2. exd5 — a pawn takes a pawn, neither counts as non-pawn.
        assertMaterialUnchanged("1. e4 d5 2. exd5");
    }

    @Test
    void makeMove_enPassantCapture_leavesBothMaterialsUnchanged() {
        // Setup an en-passant capture: 1.e4 a6 2.e5 d5 3.exd6 (ep).
        assertMaterialUnchanged("1. e4 a6 2. e5 d5 3. exd6");
    }

    @Test
    void makeMove_castlingKingSide_leavesBothMaterialsUnchanged() {
        assertMaterialUnchanged("1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. Nc3 Nf6 5. O-O");
    }

    @Test
    void makeMove_castlingQueenSide_leavesBothMaterialsUnchanged() {
        assertMaterialUnchanged("1. d4 d5 2. Nc3 Nc6 3. Bf4 Bf5 4. Qd2 Qd7 5. O-O-O");
    }

    // ---------- Incremental updates: normal captures of non-pawn pieces ----------

    @Test
    void makeMove_whiteCapturesBlackKnight_blackLosesKnightWeight() {
        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6 — white bishop captures black knight.
        var board = boardAfter("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6");
        var status = board.getGameStatus();

        assertEquals(INITIAL_NON_PAWN_MATERIAL, status.getWhiteNonPawnMaterialWeight(),
                "white keeps all its non-pawn material — capturer's material does not shift");
        assertEquals(INITIAL_NON_PAWN_MATERIAL - KNIGHT, status.getBlackNonPawnMaterialWeight(),
                "black lost a knight");
    }

    @Test
    void makeMove_blackCapturesWhiteBishop_whiteLosesBishopWeight() {
        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6 bxc6 — black pawn recaptures the bishop.
        // Wait: bxc6 is black pawn takes white bishop → white loses a bishop.
        var board = boardAfter("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6 bxc6");
        var status = board.getGameStatus();

        assertEquals(INITIAL_NON_PAWN_MATERIAL - BISHOP, status.getWhiteNonPawnMaterialWeight(),
                "white lost the light-squared bishop on c6");
        assertEquals(INITIAL_NON_PAWN_MATERIAL - KNIGHT, status.getBlackNonPawnMaterialWeight(),
                "black still down a knight from move 4");
    }

    @Test
    void makeMove_queenCapturesRook_capturedSideLosesRookCapturerUnchanged() {
        // Construct a position via FEN and play Qxa8 (white queen takes black
        // rook). Simplest to just import a position where it's white to
        // move and Qxa8 is legal, then run the move.
        var board = Fen.importFEN("r3k3/8/8/8/8/8/8/Q3K3 w - - 0 1");
        int[] before = { board.getGameStatus().getWhiteNonPawnMaterialWeight(),
                         board.getGameStatus().getBlackNonPawnMaterialWeight() };

        int move = Move.create(Board.a1, Board.a8, Board.blackRook, Move.typeNormal);
        board.makeMove(move);

        assertEquals(before[0], board.getGameStatus().getWhiteNonPawnMaterialWeight(),
                "capturer keeps its material");
        assertEquals(before[1] - ROOK, board.getGameStatus().getBlackNonPawnMaterialWeight(),
                "captured side loses the rook");
    }

    // ---------- Incremental updates: promotions ----------

    @Test
    void makeMove_promotionToQueen_noCapture_promoterGainsQueenWeight() {
        assertPromotionGainForWhite(Move.typePawnPromotionQueen, QUEEN);
    }

    @Test
    void makeMove_promotionToKnight_noCapture_promoterGainsKnightWeight() {
        assertPromotionGainForWhite(Move.typePawnPromotionKnight, KNIGHT);
    }

    @Test
    void makeMove_promotionToRook_noCapture_promoterGainsRookWeight() {
        assertPromotionGainForWhite(Move.typePawnPromotionRook, ROOK);
    }

    @Test
    void makeMove_promotionToBishop_noCapture_promoterGainsBishopWeight() {
        assertPromotionGainForWhite(Move.typePawnPromotionBishop, BISHOP);
    }

    @Test
    void makeMove_promotionCaptureKnight_promoterGainsQueen_opponentLosesKnight() {
        // Position: white pawn on b7, black knight on a8. Move: b7xa8=Q.
        var board = Fen.importFEN("n3k3/1P6/8/8/8/8/8/4K3 w - - 0 1");
        int[] before = { board.getGameStatus().getWhiteNonPawnMaterialWeight(),
                         board.getGameStatus().getBlackNonPawnMaterialWeight() };

        int move = Move.create(Board.b7, Board.a8, Board.blackKnight, Move.typePawnPromotionQueen);
        board.makeMove(move);

        assertEquals(before[0] + QUEEN, board.getGameStatus().getWhiteNonPawnMaterialWeight(),
                "promoter gains the promoted-piece weight (the vanished pawn was not counted anyway)");
        assertEquals(before[1] - KNIGHT, board.getGameStatus().getBlackNonPawnMaterialWeight(),
                "opponent loses the captured knight");
    }

    // ---------- Roundtrip / consistency ----------

    @Test
    void makeMove_thenRevertMove_restoresBothMaterialWeights() {
        var board = boardAfter("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6");
        int whiteBefore = board.getGameStatus().getWhiteNonPawnMaterialWeight();
        int blackBefore = board.getGameStatus().getBlackNonPawnMaterialWeight();

        // Play Bxc6 (a capture), then revert.
        int move = Move.create(Board.b5, Board.c6, Board.blackKnight, Move.typeNormal);
        board.makeMove(move);
        board.revertMove();

        assertEquals(whiteBefore, board.getGameStatus().getWhiteNonPawnMaterialWeight(),
                "material restored to pre-move value for white after revert");
        assertEquals(blackBefore, board.getGameStatus().getBlackNonPawnMaterialWeight(),
                "material restored to pre-move value for black after revert");
    }

    @Test
    void makeNullMove_leavesBothMaterialsUnchanged() {
        var board = boardAfter("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6");
        var status = board.getGameStatus();
        int whiteBefore = status.getWhiteNonPawnMaterialWeight();
        int blackBefore = status.getBlackNonPawnMaterialWeight();

        board.makeNullMove();

        var after = board.getGameStatus();
        assertEquals(whiteBefore, after.getWhiteNonPawnMaterialWeight(),
                "null move must not touch white's material");
        assertEquals(blackBefore, after.getBlackNonPawnMaterialWeight(),
                "null move must not touch black's material");
    }

    @Test
    void makeNullMove_thenRevertNullMove_restoresBothMaterialWeights() {
        var board = boardAfter("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6");
        int whiteBefore = board.getGameStatus().getWhiteNonPawnMaterialWeight();
        int blackBefore = board.getGameStatus().getBlackNonPawnMaterialWeight();

        board.makeNullMove();
        board.revertNullMove();

        assertEquals(whiteBefore, board.getGameStatus().getWhiteNonPawnMaterialWeight(),
                "null-move round-trip preserves white material");
        assertEquals(blackBefore, board.getGameStatus().getBlackNonPawnMaterialWeight(),
                "null-move round-trip preserves black material");
    }

    @Test
    void sequenceOfMoves_incrementalTracking_matchesFullRecomputationAfterEachPly() {
        // Play a game with several tactical exchanges: bishop trades, pawn
        // takes pawn, knight capture. After every ply, the incrementally-
        // maintained materials on GameStatus must match a fresh recomputation
        // over the raw board — the strongest invariant this feature guards.
        var pgn = """
                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Bxc6 bxc6 5. Nc3 Nf6
                6. d4 exd4 7. Nxd4 c5 8. Nf3 c4 9. Bg5 Be7 10. Bxf6 Bxf6
                """;
        // Play each half-move step by step, verifying the invariant every time.
        var moves = Pgn.parse(pgn).findFirst().orElseThrow().moves;
        var game = new Game();
        for (var m : moves) {
            game.makeMove(m);

            var status = game.getBoard().getGameStatus();
            int[] fresh = Board.calculateNonPawnMaterialWeights(game.getBoard().getRawBoard());
            assertEquals(fresh[0], status.getWhiteNonPawnMaterialWeight(),
                    "white incremental material must match fresh recomputation after " + m);
            assertEquals(fresh[1], status.getBlackNonPawnMaterialWeight(),
                    "black incremental material must match fresh recomputation after " + m);
        }
    }

    // ---------- helpers ----------

    private static Board boardAfter(String pgn) {
        return GameImporter.importerFor(pgn).importGame().getBoard();
    }

    private static void assertMaterialUnchanged(String pgn) {
        var status = boardAfter(pgn).getGameStatus();

        assertEquals(INITIAL_NON_PAWN_MATERIAL, status.getWhiteNonPawnMaterialWeight(),
                "white non-pawn material unchanged for setup: " + pgn);
        assertEquals(INITIAL_NON_PAWN_MATERIAL, status.getBlackNonPawnMaterialWeight(),
                "black non-pawn material unchanged for setup: " + pgn);
    }

    /**
     * White promotes a pawn on the a-file (a7-a8) to the given target piece
     * type with no capture. Verifies that white's non-pawn material gains
     * exactly the promoted-piece weight and black's material is unchanged.
     */
    private static void assertPromotionGainForWhite(byte promotionType, int expectedGainWeight) {
        var board = Fen.importFEN("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        int[] before = { board.getGameStatus().getWhiteNonPawnMaterialWeight(),
                         board.getGameStatus().getBlackNonPawnMaterialWeight() };

        int move = Move.create(Board.a7, Board.a8, Board.empty, promotionType);
        board.makeMove(move);

        assertEquals(before[0] + expectedGainWeight, board.getGameStatus().getWhiteNonPawnMaterialWeight(),
                "white non-pawn material gains the promoted-piece weight");
        assertEquals(before[1], board.getGameStatus().getBlackNonPawnMaterialWeight(),
                "black material unchanged by a non-capturing promotion");
    }
}
