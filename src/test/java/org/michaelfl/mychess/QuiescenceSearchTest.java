package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.MoveSorterImpl;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class QuiescenceSearchTest {

    /** Upper bound for a capture-only quiescence on a no-capture position. It resolves
     *  to a single stand-pat evaluation (sub-millisecond); a full-width search would
     *  run for seconds. Generous enough to absorb JVM warmup / GC on a slow CI box. */
    private static final long MAX_QUIESCENCE_MILLIS = 500;

    @Test
    void testPositionAfterCapture() {
        // After 10.Nxb4 (knight takes bishop) black recaptures a5xb4 (SEE-positive).
        // White's only recapture Bd2xb4 is a losing capture — the b6 rook defends the
        // b4 pawn — so SEE pruning drops it and quiescence resolves at depth 1 with an
        // even score (the bishop-for-knight trade nets out). The old depth bound of 5
        // reflected a search that also followed that losing recapture (and beyond).
        var gameNotation = """
                1. e4 e5 2. Nf3 Nc6 3. Nc3 Nf6 4. d3 Bb4 5. Bd2 d6 6. Nd5 a5 7. Be2 Ra6 8. O-O Rb6 9. Qe1
                h6 10. Nxb4
                """;
        quiescenceTest(gameNotation, Board.blackBishop, 3.0f, 0, 0.5f, 1);
    }

    // A good quiescence search should detect that the white knight is not protected
    // and can be captured as compensation for the captured black knight.
    @Test
    void testPositionWithUnguardedKnight1() {
        var gameNotation = "1.c3 e6 2.Nf3 d6 3.a3 Nc6 4.Nh4 Nb4 5.d3 c5 6.axb4";
        quiescenceTest(gameNotation, Board.blackKnight, 3.0f, 0f, 0.5f, 2);
    }

    // A good quiescence search should detect that the white knight is not protected
    // and is a much more valuable target for the black queen than the pawn on d5.
    @Test
    void testPositionWithUnguardedKnight2() {
        var gameNotation = "1.Nf3 e6 2.Nh4 d5 3.c3 a6 4.c4 b6 5.cxd5";
        // TODO: 2.0 < expectedWeight < 3.0 !
        quiescenceTest(gameNotation, Board.blackPawn, 1.0f, -3.0f, -2.0f, 1);
    }

    /**
     * Fail-soft regression: a beta-cutoff must return the actual position
     * score, not the beta bound. Fail-hard would clamp the return to beta;
     * fail-soft returns the unclamped value so a future TT can store a
     * tighter lower bound (see roadmap § 12.13).
     */
    @Test
    void quiescenceFailSoft_betaCutoffReturnsUnclampedWeight() {
        // After 1.Nf3 e5 2.Nxe5, white is a pawn up and no black piece attacks
        // e5 — quiescence has no captures to follow, so stand-pat decides.
        var gameNotation = "1.Nf3 e5 2.Nxe5";
        var importer = GameImporter.importerFor(gameNotation);
        var game = importer.importGame();
        // Quiescence follows only captures — mirror how PositionSearch wires it:
        // a quiescence-mode sorter (SEE ordering/pruning) with an onlyCaptures generator.
        var moveGenerator = MoveGenerator.forQuiescenceSearch();
        var statistics = new Statistics();
        var weightingFunction = new WeightingFunction();
        var qsearch = new QuiescenceSearch(moveGenerator, weightingFunction, statistics, game.getEngine().getConfig().getMaxQuiescenceDepth(), System.currentTimeMillis() + 5 * 60_000);

        var workingBoard = game.getBoard().copy();
        int weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        int materialCenti = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);

        int wide = qsearch.quiescenceSearch(workingBoard, 0, weightFactor,
                WeightingFunction.MIN_ALPHA, WeightingFunction.MAX_BETA, materialCenti, 0);

        int tightBeta = wide - 100;
        int tight = qsearch.quiescenceSearch(workingBoard, 0, weightFactor,
                WeightingFunction.MIN_ALPHA, tightBeta, materialCenti, 0);

        assertEquals(wide, tight, "fail-soft must return the true stand-pat weight even when beta-cutoff fires; got " + tight + " with beta=" + tightBeta + ", true=" + wide);
        assertTrue(tight > tightBeta, "returned weight must exceed the tight beta bound (fail-hard would clamp to " + tightBeta + ")");
    }

    @Test
    void quiescenceTimeout_setsTimeoutFlag() {
        var gameNotation = """
                1. e4 e5 2. Nf3 Nc6 3. Nc3 Nf6 4. d3 Bb4 5. Bd2 d6 6. Nd5 a5 7. Be2 Ra6 8. O-O Rb6 9. Qe1
                h6 10. Nxb4
                """;
        var importer = GameImporter.importerFor(gameNotation);
        var game = importer.importGame();
        var moveGenerator = MoveGenerator.forQuiescenceSearch();
        var statistics = new Statistics();
        var weightingFunction = new WeightingFunction();
        for (int i = 0; i < 9_998; i++) {
            statistics.incrPositionCount();
        }
        var qsearch = new QuiescenceSearch(moveGenerator, weightingFunction, statistics,
                game.getEngine().getConfig().getMaxQuiescenceDepth(), System.currentTimeMillis() - 1);

        var workingBoard = game.getBoard().copy();
        int weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        int materialCenti = weightFactor * WeightingFunction.calculateMaterialWeight(workingBoard);

        int weight = qsearch.quiescenceSearch(workingBoard, 0, weightFactor,
                WeightingFunction.MIN_ALPHA, WeightingFunction.MAX_BETA, materialCenti, 0);

        assertTrue(qsearch.isTimeout(), "Quiescence search must expose a timeout that occurs inside its recursive capture search");
        assertEquals(0, weight, "Timeout returns a dummy score that callers must ignore when isTimeout() is true");
    }

    /**
     * Regression guard against a "full-width quiescence" explosion. Quiescence must
     * only follow captures; if it ever followed quiet moves (e.g. a non-onlyCaptures
     * generator plus the missing capture filter), a high-branching position with no
     * captures would blow up to {@code maxQuiescenceDepth} and never return. The
     * starting position has 20 legal moves and zero captures — a capture-only search
     * returns instantly, a full-width one cannot finish within the timeout.
     *
     * <p>The timeout runs in SEPARATE_THREAD mode so it aborts the run preemptively at
     * the limit. The default SAME_THREAD mode is NOT preemptive: it lets the test method
     * run to completion and only then reports the failure, so a runaway search would
     * keep burning wall-clock time up to its own internal budget. That internal budget
     * is therefore also kept short (3 s), so the abandoned CPU-bound worker — which does
     * not observe interrupts — cannot linger long after the preemptive abort.
     */
    @Test
    @Timeout(value = 15, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void quiescenceStaysFastWithManyMovesButNoCaptures() {
        // Production wiring: quiescence-mode sorter (SEE) with an onlyCaptures generator.
        // (Temporarily flip the sorter/generator to (…, false), false, false to see the
        //  guard trip: the search explodes and the timeout aborts it preemptively at ~2 s.)
        var moveGenerator = MoveGenerator.forQuiescenceSearch();
        int maxQuiescenceDepth = GameImporter.importerFor("1.e4").importGame()
                .getEngine().getConfig().getMaxQuiescenceDepth();
        var qsearch = new QuiescenceSearch(moveGenerator, new WeightingFunction(), new Statistics(),
                maxQuiescenceDepth, System.currentTimeMillis() + 10_000);

        var board = Board.createNewGame();
        int weightFactor = 1; // white to move
        int materialCenti = weightFactor * WeightingFunction.calculateMaterialWeight(board);

        // Measure the real wall-clock time — do not merely rely on isTimeout()/@Timeout,
        // which are far too loose: a full-width search could stay under the internal
        // budget yet still take seconds.
        long startNanos = System.nanoTime();
        int weight = qsearch.quiescenceSearch(board, 0, weightFactor,
                WeightingFunction.MIN_ALPHA, WeightingFunction.MAX_BETA, materialCenti, 0);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        assertTrue(elapsedMillis < MAX_QUIESCENCE_MILLIS,
                "capture-only quiescence must return near-instantly; took " + elapsedMillis
                        + " ms (a full-width search would run for seconds)");
        assertFalse(WeightingFunction.isIllegalWeight(weight), "stand-pat must yield a legal weight");
    }

    void quiescenceTest(String gameNotation, byte capturedPiece, float expectedMaterialWeight, float expectedWeightMin, float expectedWeightMax, int expectedMaximumReachedDepthMin) {
        var importer = GameImporter.importerFor(gameNotation);
        var game = importer.importGame();
        var moveGenerator = MoveGenerator.forQuiescenceSearch();
        var statistics = new Statistics();
        var weightingFunction = new WeightingFunction();

        var quiescenceSearch = new QuiescenceSearch(moveGenerator, weightingFunction, statistics, game.getEngine().getConfig().getMaxQuiescenceDepth(), System.currentTimeMillis() + 5 * 60_000);
        var workingBoard = game.getBoard().copy();
        var weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;
        var materialWeightCenti = WeightingFunction.calculateMaterialWeight(workingBoard);
        assertEquals(expectedMaterialWeight, materialWeightCenti / 100f, "test setup error");

        var f = new WeightingFunction();
        var weightCenti = f.calculate(game.getBoard());
        System.out.println("Position weight: " + weightCenti);

        assertEquals(capturedPiece, Move.getCapturedPiece(game.getGameStatus().getLastMove()), "test setup error");

        var alpha = WeightingFunction.MIN_ALPHA;
        var beta = WeightingFunction.MAX_BETA;
        weightCenti = weightFactor * quiescenceSearch.quiescenceSearch(workingBoard, 0, weightFactor, alpha, beta, weightFactor * materialWeightCenti, 0);
        float weight = weightCenti / 100f;
        System.out.println("Quiescence weight: " + weight);

        assertTrue(weight >= expectedWeightMin, "Unexpected weight: " + weight + ", expected >= " + expectedWeightMin);
        assertTrue(weight <= expectedWeightMax, "Unexpected weight: " + weight + ", expected <= " + expectedWeightMax);
        assertTrue(statistics.getMaximumReachedDepth() >= expectedMaximumReachedDepthMin, "Maximum search depth too low: " + statistics.getMaximumReachedDepth());
    }

}
