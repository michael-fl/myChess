package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link EngineConfig.Builder}: the values set on the builder must arrive unchanged
 * in the built {@link EngineConfig}, and unset values must keep their documented defaults.
 *
 * <p>Covers the time-management inputs {@code previousOwnScoreCenti} and {@code remainingClockMillis},
 * whose "absent" values ({@code null} and {@link Integer#MAX_VALUE}) carry meaning for the search.
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
    void previousOwnScore_notSet_isNull() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt).build();

        assertNull(config.getPreviousOwnScoreCenti(),
                "without setPreviousOwnScoreCenti the config must report no previous score");
    }

    @Test
    void previousOwnScore_positive_arrivesUnchanged() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt).setPreviousOwnScoreCenti(35).build();

        assertEquals(35, config.getPreviousOwnScoreCenti(), "a positive previous score must arrive unchanged");
    }

    @Test
    void previousOwnScore_negative_keepsItsSign() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt).setPreviousOwnScoreCenti(-120).build();

        assertEquals(-120, config.getPreviousOwnScoreCenti(), "a negative previous score must keep its sign");
    }

    @Test
    void previousOwnScore_explicitNull_isNull() {
        var config = new EngineConfig.Builder().setTranspositionTable(tt).setPreviousOwnScoreCenti(null).build();

        assertNull(config.getPreviousOwnScoreCenti(), "an explicit null must stay null, not become 0");
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
}
