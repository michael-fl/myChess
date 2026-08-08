package org.michaelfl.mychess;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.michaelfl.mychess.Chess960OpeningBookGenerator.MAX_ABS_EVAL_CENTIPAWNS;
import static org.michaelfl.mychess.Chess960OpeningBookGenerator.RANDOM_PLIES;

/**
 * Tests for {@link Chess960OpeningBookGenerator}. Tagged {@code slow} because
 * the balance filter runs a real depth-{@value Chess960OpeningBookGenerator#FILTER_DEPTH}
 * search per candidate.
 *
 * @author Michael Fleischhauer
 */
@Tag("slow")
class Chess960OpeningBookGeneratorTest {

    private static final int SMALL_COUNT = 8;
    private static final long SEED = 4242L;

    /** Standard start position — balanced, must pass the filter. */
    private static final String STANDARD_START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** Standard start minus White's queen — White is ~9 pawns down, must fail the filter. */
    private static final String WHITE_DOWN_QUEEN_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1";

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void generatesRequestedCountOfDistinctBalanced960Openings() {
        var result = Chess960OpeningBookGenerator.generate(SMALL_COUNT, SEED);
        List<String> fens = result.fens();

        assertEquals(SMALL_COUNT, fens.size(), "number of generated openings");
        assertEquals(SMALL_COUNT, fens.stream().distinct().count(), "openings must be distinct");

        for (String fen : fens) {
            var board = Fen.importChess960FEN(fen);

            assertEquals(RANDOM_PLIES, board.getGameStatus().getPlyCount(),
                    "each opening must have exactly " + RANDOM_PLIES + " random plies applied: " + fen);
            assertTrue(board.isChess960(),
                    "each opening must be flagged as Chess960: " + fen);
        }
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void isReproducibleForAFixedSeed() {
        var first = Chess960OpeningBookGenerator.generate(SMALL_COUNT, SEED);
        var second = Chess960OpeningBookGenerator.generate(SMALL_COUNT, SEED);

        assertEquals(first.fens(), second.fens(), "same seed must yield the same book");
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void balanceFilterRejectsBlundersAndAcceptsEqualPositions() {
        var tt = TestSupport.createTestTT();
        var engineConfig = new EngineConfig.Builder()
                .maxDepth(Chess960OpeningBookGenerator.FILTER_DEPTH)
                .setTranspositionTable(tt)
                .silent(true)
                .build();
        var gameConfig = new GameConfig(MyChessEngine.class, engineConfig);

        try {
            float lostEval = Chess960OpeningBookGenerator.shallowEvalCentipawns(WHITE_DOWN_QUEEN_FEN, gameConfig);
            float balancedEval = Chess960OpeningBookGenerator.shallowEvalCentipawns(STANDARD_START_FEN, gameConfig);

            assertTrue(Math.abs(lostEval) > MAX_ABS_EVAL_CENTIPAWNS,
                    "a position a queen down must exceed the balance threshold, was " + lostEval + " cp");
            assertTrue(Math.abs(balancedEval) <= MAX_ABS_EVAL_CENTIPAWNS,
                    "the start position must pass the balance threshold, was " + balancedEval + " cp");
        } finally {
            tt.close();
        }
    }
}
