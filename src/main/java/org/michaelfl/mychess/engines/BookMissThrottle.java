package org.michaelfl.mychess.engines;

/**
 * Throttles {@code [book] miss} log emissions. Without throttling, every
 * move once the game leaves the opening book would produce a fresh miss
 * line — quickly dominating the diagnostic stream. With throttling, at
 * most {@link #MAX_LOGGED_MISSES} consecutive misses are logged; further
 * misses are silenced until a book hit resets the streak.
 *
 * <p>State is process-static on purpose: in UCI mode every {@code go}
 * command creates a fresh {@code ChessEngine} instance, so instance state
 * would reset the throttle every move and defeat the whole point. The
 * static design mirrors {@link IterationTimings}.
 *
 * <p>All public methods are {@code synchronized}. Contention is
 * irrelevant — book lookups happen at most once per {@code go}.
 *
 * @author Michael Fleischhauer
 */
final class BookMissThrottle {

    /**
     * Maximum number of consecutive {@code [book] miss} lines emitted
     * before further misses are silenced. Reset to zero on any book hit.
     */
    static final int MAX_LOGGED_MISSES = 5;

    private static int consecutiveMisses = 0;

    private BookMissThrottle() {
        throw new IllegalStateException();
    }

    /**
     * Record a book miss.
     *
     * @return {@code true} if this miss should be logged (still within the
     *         allowed streak), {@code false} if it should be silenced.
     */
    static synchronized boolean recordMissAndShouldLog() {
        boolean shouldLog = consecutiveMisses < MAX_LOGGED_MISSES;
        consecutiveMisses++;

        return shouldLog;
    }

    /** Record a book hit — resets the miss streak. */
    static synchronized void recordHit() {
        consecutiveMisses = 0;
    }
}
