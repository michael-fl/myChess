package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link EngineConfig.Builder}: the values set on the builder must arrive unchanged
 * in the built {@link EngineConfig}, and unset values must keep their documented defaults.
 *
 * <p>Covers {@code remainingClockMillis} and the strength options.
 *
 * @author Michael Fleischhauer
 */
class EngineConfigTest {

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    @Test
    void remainingClock_notSet_isMaxValue() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt).build();

        assertEquals(Integer.MAX_VALUE, config.getRemainingClockMillis(),
                "without remainingClockMillis the config must report \"no clock\" (Integer.MAX_VALUE)");
    }

    @Test
    void remainingClock_set_arrivesUnchanged() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt).remainingClockMillis(58_443).build();

        assertEquals(58_443, config.getRemainingClockMillis(), "a remaining clock must arrive unchanged");
    }

    @Test
    void strengthOptions_notSet_areZero() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt).build();

        assertEquals(0, config.getStrengthMaxDepth(), "without strengthMaxDepth there must be no depth limit");
        assertEquals(0, config.getMoveToleranceCenti(), "without moveToleranceCenti the best move must always be played");
        assertEquals(EngineConfig.DEFAULT_MOVE_TOLERANCE_NEAR_CENTI, config.getMoveToleranceNearCenti(),
                "without moveToleranceNearCenti the near bound must be its default");
    }

    @Test
    void filterOptions_notSet_areTheirDefaults() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt).build();

        assertTrue(config.isMoveToleranceFilter(), "the far-group filter must be on by default");
        assertEquals(EngineConfig.DEFAULT_MOVE_TOLERANCE_RETURN_PLIES, config.getMoveToleranceReturnPlies());
        assertEquals(EngineConfig.DEFAULT_MOVE_TOLERANCE_SAME_PIECE, config.getMoveToleranceSamePiece());
    }

    @Test
    void filterOptions_set_arriveUnchanged() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt)
                .moveToleranceFilter(false)
                .moveToleranceReturnPlies(5)
                .moveToleranceSamePiece(2)
                .build();

        assertFalse(config.isMoveToleranceFilter(), "the filter switch must arrive unchanged");
        assertEquals(5, config.getMoveToleranceReturnPlies(), "N must arrive unchanged");
        assertEquals(2, config.getMoveToleranceSamePiece(), "M must arrive unchanged");
    }

    @Test
    void strengthOptions_set_arriveUnchanged() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt)
                .strengthMaxDepth(6)
                .moveToleranceCenti(40)
                .moveToleranceNearCenti(15)
                .build();

        assertEquals(6, config.getStrengthMaxDepth(), "the depth limit must arrive unchanged");
        assertEquals(40, config.getMoveToleranceCenti(), "the tolerance must arrive unchanged");
        assertEquals(15, config.getMoveToleranceNearCenti(), "the near bound must arrive unchanged");
    }
}
