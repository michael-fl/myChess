package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link EngineConfig.Builder}: the values set on the builder must arrive unchanged
 * in the built {@link EngineConfig}, and unset values must keep their documented defaults.
 *
 * <p>Covers the time-management input {@code remainingClockMillis}, whose "absent" value
 * ({@link Integer#MAX_VALUE}) carries meaning for the search.
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
}
