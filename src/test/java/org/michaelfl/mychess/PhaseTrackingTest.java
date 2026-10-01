package org.michaelfl.mychess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the incrementally tracked game phase: {@link Fen#calculateUnclampedPhase(byte[])} at a
 * position's import, {@link GameStatus#calcNewPhaseForMove(int, int)} on every move, and the
 * invariant that ties them together. The phase tracked through a game must equal the phase
 * counted afresh from the board it arrives at, clamped to {@link WeightingFunction#MAX_PHASE}.
 * Two paths to the same position may not disagree, or the same Zobrist key carries two
 * evaluations.
 *
 * @author Michael Fleischhauer
 */
class PhaseTrackingTest {

    private static final int MAX_PHASE = WeightingFunction.MAX_PHASE;

    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** Full material plus a promoted white queen: the raw phase sum is 28. */
    private static final String EXTRA_QUEEN_FEN = "rnbqkbnr/pppppppp/8/8/8/5Q2/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** A white pawn on b7 that can take the a8 rook and promote, with every other piece still on the board. */
    private static final String PROMOTION_CAPTURE_FEN = "rnbqkbnr/pPpppppp/8/8/8/8/P1PPPPPP/RNBQKBNR w KQkq - 0 1";

    private static int recountedPhase(Board board) {
        return Math.min(Fen.calculateUnclampedPhase(board.getRawBoard()), MAX_PHASE);
    }

    @Nested
    class CalculatePhase {

        @Test
        void startPosition_isTheMaximum() {
            assertEquals(MAX_PHASE, Fen.calculateUnclampedPhase(Fen.importFEN(START_FEN).getRawBoard()),
                    "full starting material sums to MAX_PHASE");
        }

        @Test
        void bareKings_areZero() {
            assertEquals(0, Fen.calculateUnclampedPhase(Fen.importFEN("8/5k2/8/8/3K4/8/8/8 w - - 0 60").getRawBoard()),
                    "kings and pawns carry no phase weight");
        }

        @Test
        void importedPosition_neverExceedsTheMaximum() {
            assertEquals(MAX_PHASE, Fen.importFEN(EXTRA_QUEEN_FEN).getGameStatus().getPhase(),
                    "an extra queen pushes the raw sum to 28; the phase a position starts with must be clamped, "
                            + "since every table indexed by it has MAX_PHASE + 1 entries");
        }

        @Test
        void importedPosition_canBeEvaluated() {
            var weightingFunction = new WeightingFunction();

            weightingFunction.calculate(Fen.importFEN(EXTRA_QUEEN_FEN));

            assertEquals(MAX_PHASE, weightingFunction.getPhase(),
                    "the evaluation of a position with promoted material must not index past its phase tables");
        }
    }

    @Nested
    class CalcNewPhaseForMove {

        @Test
        void quietMove_leavesThePhaseUnchanged() {
            int move = Move.create(Board.g1, Board.f3, Board.empty, Move.typeNormal);

            assertEquals(17, GameStatus.calcNewPhaseForMove(move, 17), "a move without capture or promotion");
        }

        @Test
        void capture_subtractsThePhaseWeightOfTheCapturedPiece() {
            assertEquals(13, GameStatus.calcNewPhaseForMove(Move.create(Board.d1, Board.d8, Board.blackQueen, Move.typeNormal), 17),
                    "a queen weighs 4");
            assertEquals(15, GameStatus.calcNewPhaseForMove(Move.create(Board.d1, Board.a8, Board.blackRook, Move.typeNormal), 17),
                    "a rook weighs 2");
            assertEquals(16, GameStatus.calcNewPhaseForMove(Move.create(Board.d1, Board.c8, Board.blackBishop, Move.typeNormal), 17),
                    "a bishop weighs 1");
            assertEquals(16, GameStatus.calcNewPhaseForMove(Move.create(Board.d1, Board.b8, Board.blackKnight, Move.typeNormal), 17),
                    "a knight weighs 1");
            assertEquals(17, GameStatus.calcNewPhaseForMove(Move.create(Board.d1, Board.d7, Board.blackPawn, Move.typeNormal), 17),
                    "a pawn weighs 0");
        }

        @Test
        void promotion_addsThePhaseWeightOfTheNewPiece() {
            assertEquals(14, GameStatus.calcNewPhaseForMove(Move.create(Board.b7, Board.b8, Board.empty, Move.typePawnPromotionQueen), 10),
                    "a new queen adds 4");
            assertEquals(12, GameStatus.calcNewPhaseForMove(Move.create(Board.b7, Board.b8, Board.empty, Move.typePawnPromotionRook), 10),
                    "a new rook adds 2");
            assertEquals(11, GameStatus.calcNewPhaseForMove(Move.create(Board.b7, Board.b8, Board.empty, Move.typePawnPromotionBishop), 10),
                    "a new bishop adds 1");
            assertEquals(11, GameStatus.calcNewPhaseForMove(Move.create(Board.b7, Board.b8, Board.empty, Move.typePawnPromotionKnight), 10),
                    "a new knight adds 1");
        }

        @Test
        void promotionWithCapture_combinesBoth() {
            int move = Move.create(Board.b7, Board.a8, Board.blackRook, Move.typePawnPromotionQueen);

            assertEquals(12, GameStatus.calcNewPhaseForMove(move, 10), "minus a rook (2), plus a queen (4)");
        }

        @Test
        void clampedPromotion_keepsWhatItClampedAway() {
            // b7xa8=Q captures a rook (-2) and adds a queen (+4): 24 becomes 26 raw.
            int promotion = Move.create(Board.b7, Board.a8, Board.blackRook, Move.typePawnPromotionQueen);
            int queenCapture = Move.create(Board.d8, Board.d1, Board.whiteQueen, Move.typeNormal);

            int afterPromotion = GameStatus.calcNewPhaseForMove(promotion, MAX_PHASE);
            int afterCapture = GameStatus.calcNewPhaseForMove(queenCapture, afterPromotion);

            // Then a queen comes off (-4): 26 - 4 = 22, below the clamp, so 22 is the true phase.
            int rook = WeightingFunction.phaseWeightOfPiece[Board.blackRook];
            int queen = WeightingFunction.phaseWeightOfPiece[Board.whiteQueen];
            int expected = MAX_PHASE - rook + queen - queen;

            assertEquals(expected, afterCapture,
                    "24 - 2 + 4 = 26 raw, then a queen comes off: 22 raw, which is below the clamp. Clamping the "
                            + "stored value at 24 loses the 2 points above it, and the chain arrives at 20");
        }
    }

    @Nested
    class TrackedEqualsRecounted {

        @Test
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void throughoutAPlayedGame_andAfterEveryRevert() {
            var game = GameImporter.importerFor("""
                    1. e4 c5 2. Nc3 Nc6 3. Nf3 e5 4. Bc4 d6 5. O-O Be7 6. d3 Nf6 7. Be3 O-O 8. a3 h5 9. Nh4 Bg4
                    10. f3 Be6 11. Bxe6 fxe6 12. Ng6 Re8 13. f4 Ng4 14. Qd2 Bf6 15. f5 exf5 16. exf5 e4 17. Rae1 Nxe3
                    18. Rxe3 Bd4 19. dxe4 d5 20. f6 gxf6 21. Nf4 Ne5 22. Kh1 Bxe3 23. Qxe3 Ng4 24. Qg3 Qd6
                    25. Ncxd5 Rxe4 26. h3 f5 27. hxg4 hxg4 28. Qb3 Qa6 29. Kg1 c4 30. Qc3 Qc6 31. Nf6+ Kf7
                    32. Nxe4 Qxe4 33. Nh5 Rg8 34. Nf6 Qe6 35. Nxg8 Kxg8 36. Re1 Qc8 37. Qf6 Qd7 38. Re7 Qd1+
                    39. Kf2 g3+ 40. Ke3 Qc1+ 41. Kf3 Qd1+ 42. Kf4 Qg4+ 43. Ke3 Qe4+ 44. Rxe4 fxe4 45. Kxe4 Kh7
                    46. Qg7+ Kxg7
                    """).importGame();
            var board = game.getBoard();
            int plies = board.getGameStatus().getPlyCount();

            // The game ends with 46.Qg7+ Kxg7 appended, so only kings and pawns remain.
            assertEquals(0, board.getGameStatus().getPhase(), "kings and pawns only at the end (" + board.exportFEN() + ")");

            for (int ply = plies; ply > 0; ply--) {
                assertEquals(recountedPhase(board), board.getGameStatus().getPhase(),
                        "after ply " + ply + " (" + board.exportFEN() + ")");
                board.revertMove();
            }

            assertEquals(MAX_PHASE, board.getGameStatus().getPhase(), "back at the start position");
        }

        @Test
        void afterAPromotionWithCapture_atFullMaterial() {
            var board = Fen.importFEN(PROMOTION_CAPTURE_FEN);

            board.makeMove(Move.create(Board.b7, Board.a8, Board.blackRook, Move.typePawnPromotionQueen));

            assertEquals(recountedPhase(board), board.getGameStatus().getPhase(),
                    "tracked phase after b7xa8=Q must match the recount (" + board.exportFEN() + ")");
        }

        @Test
        void afterAnUnderpromotion() {
            var board = Fen.importFEN("4k3/1P6/8/8/8/8/8/4K3 w - - 0 1");

            board.makeMove(Move.create(Board.b7, Board.b8, Board.empty, Move.typePawnPromotionKnight));

            assertEquals(1, board.getGameStatus().getPhase(), "the new knight adds 1 to a bare-kings phase of 0");
            assertEquals(recountedPhase(board), board.getGameStatus().getPhase(), "and agrees with the recount");
        }

        @Test
        void nullMove_leavesThePhaseUnchanged() {
            var board = Fen.importFEN("r3k3/8/8/8/8/8/8/4K2R w - - 0 1");
            int before = board.getGameStatus().getPhase();

            board.makeNullMove();

            assertEquals(before, board.getGameStatus().getPhase(), "a null move removes and adds nothing");
        }
    }

    @Nested
    class MaterialOnlyShortcut {

        @Test
        void termInCentipawns_matchesTheTermInTheFullEvaluation() {
            // Four pawns ahead (excess 300 over the one-pawn threshold), fully exchanged: 10 % of 300 is
            // 30, scaled by materialExchangeFactor into pawns; 100 turns pawns into centipawns.
            float termInPawns = WeightingFunction.calcMaterialExchangeTerm(0, 400);

            assertEquals(Math.round(termInPawns * 100f), WeightingFunction.calcMaterialExchangeTermCp(0, 400),
                    "the shortcut must add the same centipawns the full evaluation adds, not round to whole pawns first");
        }

        @Test
        void termInCentipawns_matchesTheFullEvaluationAtEveryPhase() {
            // The full evaluation adds calcMaterialExchangeTerm (pawns) before its final * 100; the
            // shortcut adds calcMaterialExchangeTermCp. Both apply materialExchangeFactor inside,
            // so they agree for any factor - this pins that they keep doing so.
            for (int phase = 0; phase <= MAX_PHASE; phase++) {
                for (int delta : new int[] {150, 333, 650, 1400, -800}) {
                    float inCentipawns = WeightingFunction.calcMaterialExchangeTerm(phase, delta) * 100f;

                    // 0.501, not 0.5: a term of exactly x.5 rounds away from zero, and the float
                    // product above can land a few millionths short of the .5 it stands for.
                    assertEquals(inCentipawns, WeightingFunction.calcMaterialExchangeTermCp(phase, delta), 0.501f,
                            "the shortcut's term must match the full evaluation's to within rounding: phase "
                                    + phase + ", delta " + delta);
                }
            }
        }

        @Test
        void termInCentipawns_staysOddSymmetric() {
            for (int phase = 0; phase <= MAX_PHASE; phase++) {
                for (int delta : new int[] {150, 333, 650, 1400}) {
                    assertEquals(-WeightingFunction.calcMaterialExchangeTermCp(phase, delta),
                            WeightingFunction.calcMaterialExchangeTermCp(phase, -delta),
                            "the shortcut is fed the side-to-move material, so the term must be odd: phase "
                                    + phase + ", delta " + delta);
                }
            }
        }
    }
}
