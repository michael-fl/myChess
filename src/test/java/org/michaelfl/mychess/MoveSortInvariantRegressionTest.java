package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression test for the "ttMove / pvMove not produced by MoveGenerator"
 * invariant violation warning emitted by
 * {@link org.michaelfl.mychess.engines.MoveSorterImpl#getSortedMoves()}.
 *
 * <p>Observed 2026-07-08: starting a fresh Standard-chess game and asking
 * the engine to compute the first move from the initial position (White
 * to move) produces multiple such warnings in the log at typical search
 * depths. Both the {@code ttMove} and the {@code pvMove} variants share
 * the same "not produced by MoveGenerator" fallback path in the sorter —
 * this test guards against either firing.
 *
 * <p>The invariant that must hold: any move handed to the sorter as
 * {@code ttMove} or {@code pvMove} for the current position must be
 * legal in that position, i.e. produced by the {@link MoveGenerator}
 * for the same board state. If it is not, the sorter cannot integrate
 * it into the ordering (the "seen" flag never fires) and the move is
 * silently dropped — the search then behaves as if no hint were
 * available. Not a search-correctness bug in the strict sense (the
 * move is skipped, not played), but a symptom of a broken invariant
 * somewhere upstream — likely a Zobrist collision, a stale TT entry, a
 * corrupted PV path, or a piece / castling / en-passant field mixup
 * between the position that produced the move and the position where
 * it is being applied.
 *
 * <p>Test strategy: redirect {@code System.out}, run the engine on the
 * initial position at the default search depth, and assert that the
 * captured stdout does not contain the invariant marker. The test
 * fails while the bug is present and passes once it is fixed.
 *
 * @author Michael Fleischhauer
 */
class MoveSortInvariantRegressionTest extends EngineTestBase {

    /**
     * Substring that appears in every {@code MoveSorterImpl.getSortedMoves()}
     * invariant-violation log line — for both the {@code ttMove} and the
     * {@code pvMove} variants. Any occurrence in the captured stdout is a
     * bug reproduction.
     */
    private static final String INVARIANT_MARKER = "not produced by MoveGenerator";

    private final PrintStream originalOut = System.out;
    private final ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
    private Log.Mode originalMode;

    @BeforeEach
    void captureStdout() {
        System.setOut(new PrintStream(capturedOut));
        originalMode = Log.getMode();
        // REPL mode routes Log.info to stdout — where our redirect can catch it.
        Log.setMode(Log.Mode.REPL);
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
        Log.setMode(originalMode);
    }

    @Test
    void firstMoveFromStandardStart_doesNotEmitMoveSortInvariantViolation()
            throws InterruptedException, ExecutionException, TimeoutException {
        var config = new GameConfig(ENGINE, engineConfig());
        try {
            var game = new Game(config);
            game.getEngine().nextMoveAsync().getResult(5, TimeUnit.MINUTES);

            var output = capturedOut.toString();
            assertFalse(output.contains(INVARIANT_MARKER),
                    "engine's first-move search from the standard start position must not emit any "
                            + "'ttMove/pvMove not produced by MoveGenerator' warning; "
                            + "captured stdout was:\n" + output);
        } finally {
            config.getEngineWhiteConfig().getTranspositionTable().close();
        }
    }
}
