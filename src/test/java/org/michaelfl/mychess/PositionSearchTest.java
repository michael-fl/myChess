package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.PositionSearch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct tests for {@link PositionSearch}. Exercised through the public
 * {@code calculateNextMove(...)} static entry point: each test builds a game
 * via {@link GameImporter}, configures a {@link MyChessEngine}, then runs the
 * async API end-to-end.
 *
 * @author Michael Fleischhauer
 */
class PositionSearchTest {

    /**
     * Black to move and in check from the knight on f7, with exactly one legal reply:
     * {@code Bxf7}. The king is boxed in by its own bishop and pawns.
     *
     * <p>The move generator works pseudo-legally, so it also offers moves that leave the
     * king in check, among them {@code Qxa5}, which would win white's queen. That makes
     * the position a probe for <em>where</em> the search drops illegal moves: if they
     * survived the first iteration, a material-greedy depth-1 search would pick the queen
     * capture.
     */
    private static final String SINGLE_LEGAL_REPLY_FEN = "6bk/5Npp/8/Q7/8/8/3q4/6K1 b - - 0 1";

    /** The only legal move in {@link #SINGLE_LEGAL_REPLY_FEN}. */
    private static final String ONLY_LEGAL_MOVE = ChessUtil.moveToString(Board.g8, Board.f7);

    /** The tempting illegal move in {@link #SINGLE_LEGAL_REPLY_FEN}: it wins a queen but ignores the check. */
    private static final String TEMPTING_ILLEGAL_MOVE = ChessUtil.moveToString(Board.d2, Board.a5);

    /**
     * White to move and in check from the rook on h1 along the first rank, with exactly one
     * legal reply: the king move {@code Ka2}. Nothing can block on c1 to g1 or capture the rook.
     *
     * <p>The counterpart to {@link #SINGLE_LEGAL_REPLY_FEN}, where the only reply is a capture
     * by a piece: here it is a quiet king move. The pseudo-legal generator again offers captures
     * that ignore the check, among them {@code Qxa7+}, which takes a pawn next to the black king
     * with check.
     */
    private static final String SINGLE_LEGAL_KING_MOVE_FEN = "1kq5/pp4b1/2p5/Q7/1NR1p3/8/2PP1PP1/1K5r w - - 0 1";

    /** The only legal move in {@link #SINGLE_LEGAL_KING_MOVE_FEN}. */
    private static final String ONLY_LEGAL_KING_MOVE = ChessUtil.moveToString(Board.b1, Board.a2);

    /** A tempting illegal move in {@link #SINGLE_LEGAL_KING_MOVE_FEN}: a capture with check that ignores the own check. */
    private static final String TEMPTING_ILLEGAL_CAPTURE = ChessUtil.moveToString(Board.a5, Board.a7);

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    private static EngineConfig deepConfig(int maxDepth, TranspositionTable tt) {
        return new EngineConfig.Builder()
                .maxDepth(maxDepth)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void findsMateInOne() throws Exception {
        // White to move: Qxf7# (Scholar's mate completion).
        var setup = """
                1. e4 e5
                2. Qh5 Nc6
                3. Bc4 Nf6??
                """;
        var game = GameImporter.importerFor(setup).importGame(
                new GameConfig(MyChessEngine.class, deepConfig(4, tt)));

        var move = game.getEngine().nextMoveAsync().getResult(20, TimeUnit.SECONDS);

        var san = game.getBoard().moveToShortNotation(new Move(move.move())).toString();
        assertEquals("Qxf7#", san, "Search must find the mate-in-1 move Qxf7#");
        assertTrue(WeightingFunction.isCheckmateWeight(move.weight()),
                "Returned weight must be in the checkmate range, was " + move.weight());
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void principalVariationLengthAtMostMaxDepth() throws Exception {
        // Use a quiet opening so the engine does not bail with a mate-shortened path.
        var game = GameImporter.importerFor("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6").importGame(
                new GameConfig(MyChessEngine.class, deepConfig(4, tt)));

        var move = game.getEngine().nextMoveAsync().getResult(30, TimeUnit.SECONDS);

        int len = 0;
        for (int i = 0; i < move.path().length && move.path()[i] != 0; i++) {
            len++;
        }
        assertTrue(len <= 4, "Principal variation must not exceed maxDepth, got " + len);
        assertTrue(len > 0, "Principal variation must be non-empty");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void terminatesOnAlreadyOverGame() throws Exception {
        // Set up a checkmate position so the search recognizes the game is over.
        var setup = """
                1. e4 e5
                2. Qh5 Nc6
                3. Bc4 Nf6
                4. Qxf7#
                """;
        var game = GameImporter.importerFor(setup).importGame(
                new GameConfig(MyChessEngine.class, deepConfig(8, tt)));

        var move = game.getEngine().nextMoveAsync().getResult(10, TimeUnit.SECONDS);

        assertEquals(0, move.move(), "No move can be played when the game is already mated");
        assertEquals(Game.GameResult.CHECKMATE, move.result(),
                "Result must be CHECKMATE when the game is already over");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getPossibleMovesAtStartReturnsTwentyMoves() {
        var game = new Game(new GameConfig(MyChessEngine.class, deepConfig(1, tt)));
        Moves moves = PositionSearch.getPossibleMoves(game.getEngine(), game);
        assertEquals(20, moves.count(),
                "From the start position there are exactly 20 legal moves for white");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void searchHonorsMillisPerMoveTimeout() throws Exception {
        // Configure a 1-second budget with a very deep maxDepth — the timeout must trigger.
        var config = new EngineConfig.Builder()
                .maxDepth(20)
                .millisPerMove(1_000)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
        var game = GameImporter.importerFor("1. e4 e5 2. Nf3 Nc6").importGame(
                new GameConfig(MyChessEngine.class, config));

        long t0 = System.currentTimeMillis();
        var move = game.getEngine().nextMoveAsync().getResult(15, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - t0;

        assertNotNull(move, "Search must return a move from the previous completed depth");
        assertNotEquals(0, move.move(), "Returned move must be a real move");
        assertTrue(elapsed < 10_000,
                "Search must abort within a few seconds when millisPerMove=1000, actual: " + elapsed + "ms");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void cancellationFlagIsObservableAfterSubmission() {
        // Drive cancel() via the engine's real async submission (which sets the
        // future on the task), then verify the flag is observable.
        var config = new EngineConfig.Builder()
                .maxDepth(20)
                .millisPerMove(60_000)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
        var game = GameImporter.importerFor("1. e4 e5 2. Nf3 Nc6").importGame(
                new GameConfig(MyChessEngine.class, config));

        var task = game.getEngine().nextMoveAsync();
        try {
            task.cancel();
            assertTrue(task.isCanceled(), "After cancel(), isCanceled() must report true");
        } finally {
            game.shutdown();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void moveGeneratorOffersIllegalRepliesWhenInCheck() {
        assertGeneratorOffersIllegalReply(SINGLE_LEGAL_REPLY_FEN, ONLY_LEGAL_MOVE, TEMPTING_ILLEGAL_MOVE);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void depthOneSearchAlreadyDiscardsIllegalRootMoves() throws Exception {
        assertDepthOneSearchReturnsOnlyLegalMove(SINGLE_LEGAL_REPLY_FEN, ONLY_LEGAL_MOVE, TEMPTING_ILLEGAL_MOVE);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void moveGeneratorOffersIllegalRepliesWhenOnlyTheKingCanMove() {
        assertGeneratorOffersIllegalReply(SINGLE_LEGAL_KING_MOVE_FEN, ONLY_LEGAL_KING_MOVE, TEMPTING_ILLEGAL_CAPTURE);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void depthOneSearchFindsTheOnlyLegalKingMove() throws Exception {
        assertDepthOneSearchReturnsOnlyLegalMove(SINGLE_LEGAL_KING_MOVE_FEN, ONLY_LEGAL_KING_MOVE, TEMPTING_ILLEGAL_CAPTURE);
    }

    /**
     * Premise of {@link #assertDepthOneSearchReturnsOnlyLegalMove}: the pseudo-legal generator must
     * offer the illegal move as well as the legal one. Without that, a depth-1 result could not
     * show that the search filters illegal root moves.
     */
    private void assertGeneratorOffersIllegalReply(String fen, String onlyLegalMove, String illegalMove) {
        var game = new Game(new GameConfig(MyChessEngine.class, deepConfig(1, tt)), Fen.importFEN(fen));
        Moves moves = PositionSearch.getPossibleMoves(game.getEngine(), game);
        var offered = new HashSet<String>();

        for (int move : Arrays.copyOf(moves.getMoves(), moves.count())) {
            offered.add(ChessUtil.moveToString(move));
        }

        assertTrue(moves.count() > 1,
                "the generator is pseudo-legal and must offer more than the single legal reply, got " + offered);
        assertTrue(offered.contains(illegalMove),
                "the generator must offer the illegal move " + illegalMove + ", got " + offered);
        assertTrue(offered.contains(onlyLegalMove),
                "the generator must offer the legal reply " + onlyLegalMove + ", got " + offered);
    }

    /**
     * Runs a depth-1 search and asserts it returns the only legal move.
     *
     * <p>The first iteration plays each root move and searches the reply. A move that leaves the
     * king attackable is answered by the king capture and scored as illegal. So after depth 1 only
     * the legal reply carries a valid result, which is what a "single legal move, play it at once"
     * shortcut could read off the first iteration.
     */
    private void assertDepthOneSearchReturnsOnlyLegalMove(String fen, String onlyLegalMove, String illegalMove)
            throws Exception {
        var game = new Game(new GameConfig(MyChessEngine.class, deepConfig(1, tt)), Fen.importFEN(fen));

        var move = game.getEngine().nextMoveAsync().getResult(20, TimeUnit.SECONDS);

        assertEquals(onlyLegalMove, ChessUtil.moveToString(move.move()),
                "a depth-1 search must already discard the illegal root moves and return the only legal reply "
                        + onlyLegalMove + ", not " + illegalMove);
        assertEquals(Game.GameResult.ONGOING, move.result(),
                "the position is a check with one escape, not a mate, so the result must be ONGOING");
        assertFalse(WeightingFunction.isCheckmateWeight(move.weight()),
                "the score of the only legal reply must be an ordinary evaluation, was " + move.weight());
    }
}
