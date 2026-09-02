package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the king-line danger term: the three files at and beside the king, each classified on
 * an ordered five-level scale and summed into {@code KING_LINE_PENALTY}.
 *
 * <p><b>Why every level is pinned for <em>both</em> colors.</b> The scale is ordinal and the
 * computation is mirrored, which is the combination that hides defects best. An ordinal scale
 * absorbs an off-by-one without ever looking wrong — the numbers still rise, the fit still comes
 * out monotone. A mirrored computation absorbs a sign or direction error in one color while the
 * other stays correct, and half the evaluation being silently constant does not crash, does not
 * warn, and shows up only as lost Elo. So each level appears twice here, once per color, and the
 * black cases additionally assert equality against their white mirror: a bare number tells you a
 * level is wrong, the mirror pair tells you the walk is going the wrong way.
 *
 * <p>The term is read through {@link WeightingFunction#calculateKingLineDanger}, which classifies
 * one file given the square the king stands on (or the neighboring file's square on the king's
 * rank, which is how the production caller invokes it). {@link WeightingFunction#calculate} has to
 * run first: the classifier reads the evaluator's board reference, which that call installs.
 *
 * @author Michael Fleischhauer
 */
class WeightingFunctionKingLineTest {

    /**
     * Color indices as {@code WeightingFunction} uses them internally: 0 and 1, indices into its
     * per-color arrays. Deliberately not {@code GameStatus.TURN_WHITE} / {@code TURN_BLACK}, which
     * are 8 and 16 — those are turn markers sharing the piece encoding's high bits, and passing
     * one where an array index is wanted throws
     * {@code ArrayIndexOutOfBoundsException: Index 8 out of bounds for length 2}.
     */
    private static final int WHITE = 0;
    private static final int BLACK = 1;

    /** White king g1 with the g-pawn still home, nothing else on the board. */
    private static final String WHITE_CLOSED = "4k3/8/8/8/8/8/6P1/6K1 w - - 0 1";
    /** Black king g8 with the g-pawn still home — the mirror of {@link #WHITE_CLOSED}. */
    private static final String BLACK_CLOSED = "6k1/6p1/8/8/8/8/8/4K3 w - - 0 1";

    /** White king g1, black pawn on g7: half-open, and the pawn is still on its own half. */
    private static final String WHITE_HALF_OPEN = "4k3/6p1/8/8/8/8/8/6K1 w - - 0 1";
    private static final String BLACK_HALF_OPEN = "6k1/8/8/8/8/8/6P1/4K3 w - - 0 1";

    /** White king g1, black pawn on g3: half-open with the pawn past the middle. */
    private static final String WHITE_STORMED = "4k3/8/8/8/8/6p1/8/6K1 w - - 0 1";
    private static final String BLACK_STORMED = "6k1/8/6P1/8/8/8/8/4K3 w - - 0 1";

    /** White king g1, the g-file empty from g2 up. */
    private static final String WHITE_OPEN = "4k3/8/8/8/8/8/8/6K1 w - - 0 1";
    private static final String BLACK_OPEN = "6k1/8/8/8/8/8/8/4K3 w - - 0 1";

    /** White king g1, the g-file empty and a black rook on it. */
    private static final String WHITE_OPEN_ROOK = "4k1r1/8/8/8/8/8/8/6K1 w - - 0 1";
    private static final String BLACK_OPEN_ROOK = "6k1/8/8/8/8/8/8/4K1R1 w - - 0 1";

    private static int danger(String fen, int color, int startField) {
        var evaluator = new WeightingFunction();
        evaluator.calculate(Fen.importFEN(fen));

        return evaluator.calculateKingLineDanger(color, startField);
    }

    private static int eval(String fen) {
        return new WeightingFunction().calculate(Fen.importFEN(fen));
    }

    // ------------------------------------------------------------------------------------------
    // The five levels, white to defend. These document the intended scale.
    // ------------------------------------------------------------------------------------------

    @Test
    void ownPawnOnTheFileIsClosed() {
        assertEquals(0, danger(WHITE_CLOSED, WHITE, Board.g1),
                "a white pawn on g2 shelters the white king on g1");
    }

    @Test
    void anEnemyPawnOnItsOwnHalfIsHalfOpen() {
        assertEquals(WeightingFunction.KING_DANGER_HALF_OPEN,
                danger(WHITE_HALF_OPEN, WHITE, Board.g1),
                "own g-pawn gone, black g-pawn still on g7");
    }

    @Test
    void anEnemyPawnPastTheMiddleIsWorseThanHalfOpen() {
        assertEquals(WeightingFunction.KING_DANGER_HALF_OPEN_ADVANCED_OPPONENT_PAWN,
                danger(WHITE_STORMED, WHITE, Board.g1),
                "the black g-pawn stands on g3, on white's half of the board");
    }

    @Test
    void anEmptyFileIsOpen() {
        assertEquals(WeightingFunction.KING_DANGER_OPEN,
                danger(WHITE_OPEN, WHITE, Board.g1),
                "nothing at all on the g-file in front of the king");
    }

    @Test
    void anOpenFileWithAnEnemyRookIsTheTopLevel() {
        assertEquals(WeightingFunction.KING_DANGER_OPEN_OPPONENT_MAJOR_PIECE,
                danger(WHITE_OPEN_ROOK, WHITE, Board.g1),
                "the black rook on g8 bears down the open g-file");
    }

    // ------------------------------------------------------------------------------------------
    // The same five levels, black to defend, each against its white mirror.
    // ------------------------------------------------------------------------------------------

    @Test
    void blackReadsAClosedFileTheSameWayWhiteDoes() {
        assertEquals(danger(WHITE_CLOSED, WHITE, Board.g1),
                danger(BLACK_CLOSED, BLACK, Board.g8),
                "mirrored position, mirrored king: a black pawn on g7 shelters the king on g8 "
                        + "exactly as a white pawn on g2 shelters the king on g1");
    }

    @Test
    void blackReadsAHalfOpenFileTheSameWayWhiteDoes() {
        assertEquals(danger(WHITE_HALF_OPEN, WHITE, Board.g1),
                danger(BLACK_HALF_OPEN, BLACK, Board.g8),
                "white pawn on g2 against the black king on g8 is the mirror of black's pawn on "
                        + "g7 against the white king on g1");
    }

    @Test
    void blackReadsAnAdvancedEnemyPawnTheSameWayWhiteDoes() {
        assertEquals(danger(WHITE_STORMED, WHITE, Board.g1),
                danger(BLACK_STORMED, BLACK, Board.g8),
                "a white pawn on g6 has crossed onto black's half, mirroring black's pawn on g3");
    }

    /**
     * Recorded with a warning: this one is the case in which a wrong answer and the right answer
     * coincide, so its passing is not evidence about the black side. It is kept because it would
     * fail if black ever stopped reporting an open file as open.
     */
    @Test
    void blackReadsAnOpenFileTheSameWayWhiteDoes() {
        assertEquals(danger(WHITE_OPEN, WHITE, Board.g1),
                danger(BLACK_OPEN, BLACK, Board.g8),
                "an empty file is an empty file from either side");
    }

    @Test
    void blackReadsAnEnemyRookOnAnOpenFileTheSameWayWhiteDoes() {
        assertEquals(danger(WHITE_OPEN_ROOK, WHITE, Board.g1),
                danger(BLACK_OPEN_ROOK, BLACK, Board.g8),
                "a white rook on g1 against the black king on g8 mirrors a black rook on g8 "
                        + "against the white king on g1");
    }

    // ------------------------------------------------------------------------------------------
    // Boundaries: the board edge in both directions, and the file window at the board's edge.
    // ------------------------------------------------------------------------------------------

    /**
     * A king on the a- or h-file has one of its three windows off the board, and the production
     * caller passes that off-board square in rather than filtering it out.
     */
    @Test
    void anOffBoardFileContributesNothing() {
        final String kingOnA1 = "4k3/8/8/8/8/8/8/K7 w - - 0 1";

        assertEquals(0, danger(kingOnA1, WHITE, Board.a1 - 1),
                "the square left of a1 is border, so that window scores nothing");
    }

    /**
     * A king that has walked all the way to the far rank has no squares left in front of it. White
     * on rank 8 and black on rank 1 are the same situation mirrored, so they must agree — and
     * neither may leave the board while looking.
     */
    @Test
    void aKingOnTheFarRankHasNothingLeftToLookAtAndMustNotWalkOffTheBoard() {
        final String whiteKingOnG8 = "6K1/8/8/8/8/8/8/4k3 w - - 0 1";
        final String blackKingOnG1 = "4K3/8/8/8/8/8/8/6k1 w - - 0 1";

        final int white = assertDoesNotThrow(
                () -> danger(whiteKingOnG8, WHITE, Board.g8),
                "looking forward from the far rank must terminate");
        final int black = assertDoesNotThrow(
                () -> danger(blackKingOnG1, BLACK, Board.g1),
                "and the same from black's far rank");

        assertEquals(white, black, "the two are the same position mirrored");
    }

    /**
     * One rank short of the far rank: exactly one square left to examine. This is the narrowest
     * case in which the walk still enters its loop, so it is where a termination condition that
     * only holds for one direction comes apart.
     */
    @Test
    void aKingOneRankFromTheFarRankLooksAtExactlyOneSquare() {
        final String whiteKingOnG7 = "4k3/6K1/8/8/8/8/8/8 w - - 0 1";
        final String blackKingOnG2 = "4K3/8/8/8/8/8/6k1/8 w - - 0 1";

        final int white = assertDoesNotThrow(
                () -> danger(whiteKingOnG7, WHITE, Board.g7),
                "one square ahead, then the walk must stop");
        final int black = assertDoesNotThrow(
                () -> danger(blackKingOnG2, BLACK, Board.g2),
                "and mirrored, one square ahead of a black king on g2");

        assertEquals(white, black, "the two are the same position mirrored");
    }

    // ------------------------------------------------------------------------------------------
    // The whole term, at evaluation level.
    // ------------------------------------------------------------------------------------------

    /**
     * The single strongest assertion available: in a position that is its own mirror the term must
     * cancel exactly. Any per-color asymmetry in the walk shows up here as a non-zero score for a
     * position in which neither side is better by construction.
     */
    @Test
    void theStartingPositionStaysBalanced() {
        assertEquals(0, eval("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
                "both kings sit behind intact shelters, so the king-line term must cancel");
    }

    /**
     * A mirrored pair must produce exactly opposite evaluations.
     *
     * <p>Both positions carry a rook per side deliberately. The term is scaled by the game phase,
     * and a kings-and-pawns position has phase 0 — there the term contributes nothing at all and
     * the assertion would hold no matter how the classification behaves. An earlier draft of this
     * test used bare kings and passed for exactly that reason.
     */
    @Test
    void aMirroredPairEvaluatesToOppositeScores() {
        // White king sheltered by f2/g2/h2, black king bare; a rook each side so the phase is not
        // zero. blackSafe is the exact color mirror.
        final String whiteSafe = "r5k1/8/8/8/8/8/5PPP/R5K1 w - - 0 1";
        final String blackSafe = "r5k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1";

        assertEquals(eval(whiteSafe), -eval(blackSafe),
                "the same structure with the colors exchanged must give the negated score");
    }

    /**
     * The same property at a higher phase, where the term carries more weight. A defect that only
     * shows up once the term is scaled up would slip past the rook-only pair above.
     */
    @Test
    void aMirroredPairIsStillOppositeWithMoreMaterialOnTheBoard() {
        final String whiteSafe = "r2q2k1/8/8/8/8/8/5PPP/R2Q2K1 w - - 0 1";
        final String blackSafe = "r2q2k1/5ppp/8/8/8/8/8/R2Q2K1 w - - 0 1";

        assertEquals(eval(whiteSafe), -eval(blackSafe),
                "queens on the board raise the phase, so the king-line term counts for more — "
                        + "and the mirror property must still hold exactly");
    }

    // ------------------------------------------------------------------------------------------
    // The table. Guards a calibration rather than behavior, like KingAttackCurveTest.
    // ------------------------------------------------------------------------------------------

    /**
     * Three files at the top level sum to 12, so the table needs an entry there. An index one
     * short would not be a wrong number, it would be an
     * {@link ArrayIndexOutOfBoundsException} in the middle of a search.
     */
    @Test
    void theTableCoversEveryReachableSum() {
        assertEquals(13, WeightingFunction.KING_LINE_PENALTY.length,
                "three files of at most " + WeightingFunction.KING_DANGER_OPEN_OPPONENT_MAJOR_PIECE
                        + " sum to 12, and index 12 must exist");

        final String allThreeFilesOpenWithMajors = "k4qrr/8/8/8/8/8/8/6K1 w - - 0 1";

        for (int offset = -1; offset <= 1; offset++) {
            assertEquals(WeightingFunction.KING_DANGER_OPEN_OPPONENT_MAJOR_PIECE,
                    danger(allThreeFilesOpenWithMajors, WHITE, Board.g1 + offset),
                    "f, g and h are each open with a black major piece on them, offset " + offset);
        }

        assertDoesNotThrow(() -> eval(allThreeFilesOpenWithMajors),
                "the maximum reachable danger must not index past the table");
    }

    @Test
    void theTableRisesWithDanger() {
        final int[] table = WeightingFunction.KING_LINE_PENALTY;

        assertEquals(0, table[0], "no danger, no penalty");

        for (int i = 1; i < table.length; i++) {
            assertTrue(table[i] >= table[i - 1],
                    "the fitted table must never fall: index " + i + " is " + table[i]
                            + " against " + table[i - 1] + " before it");
        }
    }

    // ------------------------------------------------------------------------------------------
    // Phase scaling. The term must count fully in the midgame and not at all in the endgame.
    // ------------------------------------------------------------------------------------------

    /**
     * Kings and pawns only, so the phase is 0 — the endgame end of the blend. White's king is the
     * exposed one: without a non-zero danger on at least one side these tests would hold whatever
     * the blend does.
     */
    private static final String PAWN_ENDGAME = "6k1/5ppp/8/8/8/8/8/6K1 w - - 0 1";
    /** The same pawn structure with a queen and a rook per side: a midgame phase. */
    private static final String WITH_HEAVY_PIECES = "3qr1k1/5ppp/8/8/8/8/8/3QR1K1 w - - 0 1";

    private static WeightingFunction evaluated(String fen) {
        var evaluator = new WeightingFunction();
        evaluator.calculate(Fen.importFEN(fen));

        return evaluator;
    }

    @Test
    void thePawnEndgameReallyHasPhaseZero() {
        assertEquals(0, evaluated(PAWN_ENDGAME).getPhase(),
                "the premise of the endgame test below: no knights, bishops, rooks or queens");
    }

    /**
     * The whole point of the blend. An asymmetric king-line danger that still moves the score in a
     * pawn endgame means the term never fades — and the earlier attempt at a king-safety term
     * measured its worst results precisely by running at full strength where the sign of king
     * exposure is the opposite one.
     */
    @Test
    void theTermContributesNothingInAPawnEndgame() {
        final var evaluator = evaluated(PAWN_ENDGAME);
        final int[] danger = evaluator.getKingLineDanger();

        assertTrue(danger[WHITE] != danger[BLACK],
                "the premise: this position must be asymmetric, otherwise the term would cancel "
                        + "and the assertion below would hold for the wrong reason — was "
                        + danger[WHITE] + " against " + danger[BLACK]);
        assertEquals(0, evaluator.calculateKingLinePenalty(WHITE),
                "at phase 0 the term must be gone for white");
        assertEquals(0, evaluator.calculateKingLinePenalty(BLACK),
                "and for black");
    }

    /**
     * The identity the term is built from, asserted directly: the contribution is the fitted table
     * entry for the measured danger, blended toward zero by the phase, and negated. Checking it
     * catches a swapped blend argument, a missing blend, and an off-by-one table index — none of
     * which a plausible-looking evaluation would reveal.
     */
    @Test
    void theContributionIsTheTableEntryBlendedByPhase() {
        for (String fen : new String[] {WITH_HEAVY_PIECES, WHITE_OPEN_ROOK, PAWN_ENDGAME,
                "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"}) {
            final var evaluator = evaluated(fen);
            final int[] danger = evaluator.getKingLineDanger();

            for (int color = WHITE; color <= BLACK; color++) {
                // The value is the positive penalty; that more danger is worse is expressed by
                // kingLinePenaltyFactor being negative, matching doublePawnFactor and
                // undefendedPiecesFactor. Read the two together or the sign looks wrong.
                final int expected = WeightingFunction.blend(
                        WeightingFunction.KING_LINE_PENALTY[danger[color]], 0, evaluator.getPhase());

                assertEquals(expected, evaluator.calculateKingLinePenalty(color),
                        "color " + color + " in " + fen + ": danger " + danger[color]
                                + " at phase " + evaluator.getPhase());
            }
        }
    }

    /** More material on the board means a higher phase, so the same danger must weigh more. */
    @Test
    void theSameDangerWeighsMoreInTheMidgameThanInTheEndgame() {
        final var endgame = evaluated(PAWN_ENDGAME);
        final var midgame = evaluated(WITH_HEAVY_PIECES);

        assertEquals(endgame.getKingLineDanger()[WHITE], midgame.getKingLineDanger()[WHITE],
                "the premise: the two positions share their pawn structure, so white's danger "
                        + "must be identical and only the phase differs");
        assertTrue(midgame.getPhase() > endgame.getPhase(),
                "the premise: a queen and a rook per side raise the phase");
        assertTrue(endgame.getKingLineDanger()[WHITE] > 0,
                "the premise: white must actually be in danger, or both sides of the comparison "
                        + "below are zero and it holds for the wrong reason");
        assertTrue(Math.abs(midgame.calculateKingLinePenalty(WHITE))
                        > Math.abs(endgame.calculateKingLinePenalty(WHITE)),
                "the same danger must weigh strictly more in the midgame — midgame "
                        + midgame.calculateKingLinePenalty(WHITE) + ", endgame "
                        + endgame.calculateKingLinePenalty(WHITE));
    }

    /** The danger sums a position actually produces, so the three-file window is pinned end to end. */
    @Test
    void bothKingsGetTheirThreeFilesCounted() {
        final int[] danger = evaluated("6k1/5ppp/8/8/8/8/5PPP/6K1 w - - 0 1").getKingLineDanger();

        assertEquals(0, danger[WHITE],
                "f2, g2 and h2 shelter the white king on g1, so all three files are closed");
        assertEquals(0, danger[BLACK],
                "and f7, g7, h7 do the same for the black king on g8");
    }
}
