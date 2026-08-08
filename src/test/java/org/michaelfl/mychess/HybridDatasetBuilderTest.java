package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link HybridDatasetBuilder#combineAndShuffle}: it must preserve
 * every input line exactly once and be deterministic for a fixed seed.
 *
 * @author Michael Fleischhauer
 */
class HybridDatasetBuilderTest {

    private static final long SEED = 99L;

    private static final List<String> BASE = List.of("b1", "b2", "b3", "b4", "b5");
    private static final List<String> SELFPLAY = List.of("s1", "s2", "s3");

    @Test
    void combineKeepsEveryLineExactlyOnce() {
        List<String> combined = HybridDatasetBuilder.combineAndShuffle(BASE, SELFPLAY, SEED);

        assertEquals(BASE.size() + SELFPLAY.size(), combined.size(), "combined size");
        assertEquals(BASE.size() + SELFPLAY.size(), combined.stream().distinct().count(),
                "every line must appear exactly once");
        assertEquals(1L, combined.stream().filter("s2"::equals).count(), "a self-play line must be present once");
        assertEquals(1L, combined.stream().filter("b3"::equals).count(), "a base line must be present once");
    }

    @Test
    void combineIsDeterministicForAFixedSeed() {
        List<String> first = HybridDatasetBuilder.combineAndShuffle(BASE, SELFPLAY, SEED);
        List<String> second = HybridDatasetBuilder.combineAndShuffle(BASE, SELFPLAY, SEED);

        assertEquals(first, second, "same seed must yield the same shuffled order");
    }
}
