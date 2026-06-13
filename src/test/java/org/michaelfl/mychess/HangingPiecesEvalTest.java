package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Focused unit tests for the hanging-pieces eval term added in roadmap
 * § 12.19. A piece counts as "hanging" when it is simultaneously attacked
 * by an opposing piece AND has no own-color defender (kings excluded).
 *
 * <p>These tests probe {@link WeightingFunction#getHangingPiecesCount(int)}
 * directly instead of going through the full-eval snapshot in
 * {@link WeightingFunctionTest}, so each scenario asserts exactly the
 * hanging-pieces semantics — and only those — for a single FEN-built
 * position.
 *
 * @author Michael Fleischhauer
 */
class HangingPiecesEvalTest {

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    private static WeightingFunction evalOf(Board board) {
        var f = new WeightingFunction();
        f.calculate(board);
        return f;
    }

    @Test
    void startingPosition_noHangingPieces() {
        var f = evalOf(Board.createNewGame());

        assertEquals(0, f.getHangingPiecesCount(WHITE), "white hanging in starting position");
        assertEquals(0, f.getHangingPiecesCount(BLACK), "black hanging in starting position");
    }

    /**
     * Baseline "attacked + undefended" case: white queen on a1 attacks
     * black knight on a8 along the a-file. The knight has no own-side
     * defender, so it counts as hanging.
     */
    @Test
    void blackKnightAttackedByQueen_isHanging() {
        var board = Fen.importFEN("n3k3/8/8/8/8/8/8/Q3K3 w - - 0 1");

        var f = evalOf(board);

        assertEquals(0, f.getHangingPiecesCount(WHITE), "white side");
        assertEquals(1, f.getHangingPiecesCount(BLACK), "black knight should count as hanging");
    }

    /**
     * Same attacker / target, but a black bishop on c6 defends a8 via the
     * c6-b7-a8 diagonal. defend() wipes the marker that capture() set on
     * the knight's square, so the knight no longer counts as hanging.
     */
    @Test
    void blackKnightDefendedByBishop_notHanging() {
        var board = Fen.importFEN("n3k3/8/2b5/8/8/8/8/Q3K3 w - - 0 1");

        var f = evalOf(board);

        assertEquals(0, f.getHangingPiecesCount(WHITE), "white side");
        assertEquals(0, f.getHangingPiecesCount(BLACK), "knight is defended -> not hanging");
    }

    /**
     * White queen on d1 attacks both a black knight (d-file, d8) and a
     * black bishop (d1-h5 diagonal, g4). Neither has an own-side defender,
     * so both count.
     */
    @Test
    void twoBlackPiecesAttackedAndUndefended_bothCounted() {
        var board = Fen.importFEN("k2n4/8/8/8/6b1/8/8/3QK3 w - - 0 1");

        var f = evalOf(board);

        assertEquals(0, f.getHangingPiecesCount(WHITE), "white side");
        assertEquals(2, f.getHangingPiecesCount(BLACK), "both black pieces should count as hanging");
    }

    /**
     * Black king on e8 is in check from a white queen on e1 (open e-file).
     * A black knight on a7 is independently attacked by a white rook on a1
     * with no own-side defender. Verifies that the attacked king is
     * excluded from the count while the knight is correctly counted.
     */
    @Test
    void attackedKing_excludedFromHangingCount() {
        var board = Fen.importFEN("4k3/n7/8/8/8/8/8/R3Q3 b - - 0 1");

        var f = evalOf(board);

        assertEquals(0, f.getHangingPiecesCount(WHITE), "white side");
        assertEquals(1, f.getHangingPiecesCount(BLACK), "knight counted, king excluded despite being attacked");
    }

    /**
     * Black knight on b6 is attacked by a white rook on b1. A black pawn
     * on a7 defends b6 via its diagonal capture squares (pawns defend
     * diagonally toward rank 1 for black). The pawn-defense path is in
     * {@code captureOrDefendWithPawn}, not in the generic {@code move()},
     * so this case exercises a different code branch than test
     * {@code blackKnightDefendedByBishop_notHanging}.
     */
    @Test
    void pieceDefendedByPawnDiagonally_notHanging() {
        var board = Fen.importFEN("4k3/p7/1n6/8/8/8/8/1R2K3 w - - 0 1");

        var f = evalOf(board);

        assertEquals(0, f.getHangingPiecesCount(BLACK), "knight defended by pawn -> not hanging");
    }

    /**
     * Black knight on a4 attacked by a white rook on a1. An own-color
     * pawn behind the knight on the same file (a5) does NOT defend it —
     * pawns capture only diagonally, not straight back. Sanity check for
     * the {@code captureOrDefendWithPawn} target-square arithmetic.
     */
    @Test
    void ownPawnBehindOnSameFile_doesNotDefend_pieceIsHanging() {
        var board = Fen.importFEN("4k3/8/8/p7/n7/8/8/R3K3 w - - 0 1");

        var f = evalOf(board);

        assertEquals(1, f.getHangingPiecesCount(BLACK), "knight is hanging despite own pawn on a5");
    }

    /**
     * Sliding-piece ray hits its own piece first and registers it as
     * defended. Black queen on a8 attacks the white a2 pawn along the
     * a-file; the white rook on a1 sits behind that pawn, and its
     * upward-ray's first encounter is the own pawn — so {@code move()}'s
     * "own color" branch calls defend(a2).
     */
    @Test
    void slidingPieceDefendsOwnBlockingPiece() {
        var board = Fen.importFEN("q3k3/8/8/8/8/8/P7/R3K3 w - - 0 1");

        var f = evalOf(board);

        assertEquals(0, f.getHangingPiecesCount(WHITE), "pawn defended by rook behind it -> not hanging");
    }

    /**
     * A piece with no own-side defender does NOT count as hanging when it
     * is also not under attack. Hanging requires BOTH attacked AND
     * undefended, not just undefended.
     */
    @Test
    void pieceUndefendedButNotAttacked_doesNotCount() {
        var board = Fen.importFEN("4k3/8/8/4b3/8/8/8/4K3 b - - 0 1");

        var f = evalOf(board);

        assertEquals(0, f.getHangingPiecesCount(WHITE), "white side");
        assertEquals(0, f.getHangingPiecesCount(BLACK), "unattacked bishop is not hanging");
    }

    /**
     * Color-mirror of {@code blackKnightAttackedByQueen_isHanging}:
     * black queen on a8 attacks an undefended white knight on a1. Verifies
     * the white-side counting path symmetrically.
     */
    @Test
    void whiteKnightAttackedByQueen_isHanging() {
        var board = Fen.importFEN("q3k3/8/8/8/8/8/8/N3K3 b - - 0 1");

        var f = evalOf(board);

        assertEquals(1, f.getHangingPiecesCount(WHITE), "white knight should count as hanging");
        assertEquals(0, f.getHangingPiecesCount(BLACK), "black side");
    }

    /**
     * Regression guard for the en-passant attack-mark field index. The
     * en-passant code in {@code calculateForWhitePawn} /
     * {@code calculateForBlackPawn} must mark the captured pawn's square
     * (not the diagonal destination square) with ATTACK_MARK_BIT. All
     * four code branches are exercised:
     *
     * <ul>
     *   <li>White-pawn EP, {@code field - 1} (captured pawn left of the
     *       capturing pawn) — {@code dxc6} after
     *       {@code 1. d4 Nf6 2. Nc3 g6 3. d5 c5}.</li>
     *   <li>White-pawn EP, {@code field + 1} (captured pawn right of the
     *       capturing pawn) — {@code exf6} after
     *       {@code 1. e4 Nc6 2. Nf3 b6 3. e5 f5}.</li>
     *   <li>Black-pawn EP, {@code field + 1} — {@code exf3} after
     *       {@code 1. Nc3 e5 2. b3 Nf6 3. Bb2 e4 4. f4}.</li>
     *   <li>Black-pawn EP, {@code field - 1} — {@code dxc3} after
     *       {@code 1. Nf3 d5 2. g3 d4 3. c4}.</li>
     * </ul>
     *
     * <p>In every PGN the just-double-stepped pawn lands on a square that
     * natural starting-position geometry leaves undefended (queen on its
     * own back rank blocked along the file by an own pawn, bishops blocked
     * by own pawns, knights do not reach the target square in one move),
     * so if the en-passant marker fires on the correct square the pawn is
     * counted as hanging.
     *
     * <p>Uses PGN import rather than FEN because
     * {@link Fen#importFEN(String)} does not populate
     * {@code GameStatus.lastMove}, which the en-passant trigger condition
     * reads.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("enPassantTargetCases")
    void enPassantTarget_isCorrectlyMarkedAsAttacked(String label, String pgn, int hangingSide) {
        var game = GameImporter.importerFor(pgn).importGame();

        var f = evalOf(game.getBoard());

        int otherSide = 1 - hangingSide;
        assertEquals(0, f.getHangingPiecesCount(otherSide),
                label + " — non-capturing side should have no hanging pieces");
        assertEquals(1, f.getHangingPiecesCount(hangingSide),
                label + " — just-double-stepped pawn is attacked via en-passant and has no own-side defender");
    }

    static Stream<Arguments> enPassantTargetCases() {
        return Stream.of(
                Arguments.of("white dxc6 (calculateForWhitePawn field - 1 branch)",
                        "1. d4 Nf6 2. Nc3 g6 3. d5 c5", BLACK),
                Arguments.of("white exf6 (calculateForWhitePawn field + 1 branch)",
                        "1. e4 Nc6 2. Nf3 b6 3. e5 f5", BLACK),
                Arguments.of("black exf3 (calculateForBlackPawn field + 1 branch)",
                        "1. Nc3 e5 2. b3 Nf6 3. Bb2 e4 4. f4", WHITE),
                Arguments.of("black dxc3 (calculateForBlackPawn field - 1 branch)",
                        "1. Nf3 d5 2. g3 d4 3. c4", WHITE)
        );
    }
}
