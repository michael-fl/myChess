package org.michaelfl.mychess;

/**
 * Small static-utility class for shared test-side helpers. Currently holds
 * the {@link TranspositionTable} factory used by tests that need an
 * isolated TT instance instead of the global default singleton.
 *
 * @author Michael Fleischhauer
 */
final class TestSupport {

    /**
     * Size of {@link TranspositionTable} instances created by
     * {@link #createTestTT()}. Power of two (required by the TT). 16K
     * entries are more than enough for any unit-level position visited at
     * the depths the test suite searches, and the per-test construction
     * cost is negligible.
     */
    private static final int TEST_TT_SIZE = 1 << 14;

    private TestSupport() {
        // utility class — no instances
    }

    /**
     * Fresh, isolated {@link TranspositionTable} for one test. Release the
     * returned table after the test, either with try-with-resources or with
     * {@code @AfterEach} calling {@link TranspositionTable#close()}. Tests
     * should not share a TT instance: lookup state from a prior test would
     * otherwise leak into the search of the next one (move-ordering hints
     * from stale entries, occasional surprise cutoffs on hash matches
     * across positions).
     */
    static TranspositionTable createTestTT() {
        return new TranspositionTable(TEST_TT_SIZE);
    }
}
