package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.TranspositionTable.Bound;
import org.michaelfl.mychess.TranspositionTable.TTEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link TranspositionTable}: constructor invariants,
 * round-tripping of {@link TTEntry} fields via {@code put} / {@code get},
 * hash-bucket collision handling, depth-preferred-EXACT replacement
 * policy, and {@code clear()}.
 *
 * @author Michael Fleischhauer
 */
class TranspositionTableTest {

    private static final int SMALL_SIZE = 16;
    private static final long KEY = 0xCAFEBABEDEADBEEFL;

    @Test
    void constructor_rejectsNonPowerOfTwo() {
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(3),
                "size 3 is not a power of two");
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(1000),
                "size 1000 is not a power of two");
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(0),
                "size 0 is not a power of two");
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(1),
                "size 1 cannot hold a full bucket");
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(2),
                "size 2 cannot hold a full bucket");
    }

    @Test
    void constructor_acceptsPowerOfTwo() {
        var tt = new TranspositionTable(SMALL_SIZE);
        assertNull(tt.get(KEY), "fresh table must return null for any lookup");
    }

    @Test
    void putAndGet_roundTripsAllFields() {
        var tt = new TranspositionTable(SMALL_SIZE);
        tt.put(KEY, 5, 137, Bound.EXACT, 0x42);

        TTEntry entry = tt.get(KEY);
        assertNotNull(entry, "stored key must be retrievable");
        assertEquals(KEY, entry.getHashKey(), "hashKey");
        assertEquals(5, entry.getDepth(), "depth");
        assertEquals(137, entry.getScore(), "score");
        assertEquals(Bound.EXACT, entry.getBound(), "bound");
        assertEquals(0x42, entry.getBestMove(), "bestMove");
    }

    @Test
    void hashBucketCollision_getReturnsNullForDifferentKey() {
        // size = 16 with BUCKET_SIZE = 4 means the low 2 bits select the
        // bucket. These keys land in the same bucket, but get() must reject
        // the second one via its full hashKey identity check.
        var tt = new TranspositionTable(SMALL_SIZE);
        long key1 = 0x100L;             // bucket 0
        long key2 = 0x200L;             // bucket 0, different high bits
        tt.put(key1, 3, 50, Bound.EXACT, 1);

        assertNotNull(tt.get(key1), "stored key must be found");
        assertNull(tt.get(key2),
                "different key landing in the same bucket must return null");
    }

    @Test
    void put_overwritesShallowerEntry() {
        var tt = new TranspositionTable(SMALL_SIZE);
        tt.put(KEY, 3, 100, Bound.EXACT, 1);
        tt.put(KEY, 5, 200, Bound.EXACT, 2);

        TTEntry entry = tt.get(KEY);
        assertNotNull(entry, "stored key must be retrievable");
        assertEquals(5, entry.getDepth(),
                "deeper entry must overwrite the shallower one");
        assertEquals(200, entry.getScore());
        assertEquals(2, entry.getBestMove());
    }

    @Test
    void put_keepsDeeperExactEntry() {
        var tt = new TranspositionTable(SMALL_SIZE);
        tt.put(KEY, 5, 100, Bound.EXACT, 1);
        tt.put(KEY, 3, 200, Bound.EXACT, 2);   // shallower → must be ignored

        TTEntry entry = tt.get(KEY);
        assertNotNull(entry, "stored key must be retrievable");
        assertEquals(5, entry.getDepth(),
                "deeper EXACT entry must be preserved against a shallower put");
        assertEquals(100, entry.getScore());
        assertEquals(1, entry.getBestMove());
    }

    @Test
    void put_overwritesDeeperEntryIfBoundIsNotExact() {
        // Depth-preferred-EXACT rule only protects EXACT entries.
        // A deeper LOWER (or UPPER) entry is replaceable.
        var tt = new TranspositionTable(SMALL_SIZE);
        tt.put(KEY, 5, 100, Bound.LOWER, 1);
        tt.put(KEY, 3, 200, Bound.EXACT, 2);   // shallower but not against EXACT

        TTEntry entry = tt.get(KEY);
        assertNotNull(entry, "stored key must be retrievable");
        assertEquals(3, entry.getDepth(),
                "a deeper non-EXACT entry must be replaceable");
        assertEquals(200, entry.getScore());
        assertEquals(Bound.EXACT, entry.getBound());
        assertEquals(2, entry.getBestMove());
    }

    @Test
    void put_keepsRecentEntrySeparateFromProtectedEntries() {
        var tt = new TranspositionTable(SMALL_SIZE);

        long protectedKey1 = 0x100L;
        long protectedKey2 = 0x200L;
        long protectedKey3 = 0x300L;
        long recentKey = 0x400L;
        long newRecentKey = 0x500L;

        tt.put(protectedKey1, 5, 100, Bound.EXACT, 1);
        tt.put(protectedKey2, 4, 200, Bound.EXACT, 2);
        tt.put(protectedKey3, 3, 300, Bound.EXACT, 3);
        tt.put(recentKey, 1, 400, Bound.LOWER, 4);

        assertNotNull(tt.get(protectedKey1), "protected key 1 must be stored");
        assertNotNull(tt.get(protectedKey2), "protected key 2 must be stored");
        assertNotNull(tt.get(protectedKey3), "protected key 3 must be stored");
        assertNotNull(tt.get(recentKey), "recent key must be stored");

        tt.put(newRecentKey, 1, 500, Bound.UPPER, 5);

        assertNotNull(tt.get(protectedKey1), "recent lane must not evict protected key 1");
        assertNotNull(tt.get(protectedKey2), "recent lane must not evict protected key 2");
        assertNotNull(tt.get(protectedKey3), "recent lane must not evict protected key 3");
        assertNull(tt.get(recentKey), "new recent key must replace the old recent entry");
        assertNotNull(tt.get(newRecentKey), "new recent key must be retrievable");
    }

    @Test
    void put_replacesWeakestProtectedEntryWithoutTouchingRecentLane() {
        var tt = new TranspositionTable(SMALL_SIZE);

        long protectedKey1 = 0x100L;
        long protectedKey2 = 0x200L;
        long weakestProtectedKey = 0x300L;
        long recentKey = 0x400L;
        long newProtectedKey = 0x500L;

        tt.put(protectedKey1, 5, 100, Bound.EXACT, 1);
        tt.put(protectedKey2, 4, 200, Bound.EXACT, 2);
        tt.put(weakestProtectedKey, 3, 300, Bound.EXACT, 3);
        tt.put(recentKey, 1, 400, Bound.LOWER, 4);

        tt.put(newProtectedKey, 6, 500, Bound.EXACT, 5);

        assertNotNull(tt.get(protectedKey1), "strong protected key 1 must stay");
        assertNotNull(tt.get(protectedKey2), "strong protected key 2 must stay");
        assertNull(tt.get(weakestProtectedKey), "weakest protected key must be evicted");
        assertNotNull(tt.get(recentKey), "protected replacement must not touch recent lane");
        TTEntry entry = tt.get(newProtectedKey);
        assertNotNull(entry, "new protected key must be retrievable");
        assertEquals(6, entry.getDepth());
    }

    @Test
    void put_promotesSameKeyFromRecentLaneToProtectedLane() {
        var tt = new TranspositionTable(SMALL_SIZE);

        long promotedKey = 0x100L;
        long protectedKey1 = 0x200L;
        long protectedKey2 = 0x300L;
        long weakestProtectedKey = 0x400L;

        tt.put(promotedKey, 1, 100, Bound.LOWER, 1);
        tt.put(protectedKey1, 5, 200, Bound.EXACT, 2);
        tt.put(protectedKey2, 4, 300, Bound.EXACT, 3);
        tt.put(weakestProtectedKey, 3, 400, Bound.EXACT, 4);

        tt.put(promotedKey, 6, 500, Bound.EXACT, 5);

        TTEntry entry = tt.get(promotedKey);
        assertNotNull(entry, "promoted key must stay retrievable");
        assertEquals(6, entry.getDepth(), "promoted key must hold the newer protected value");
        assertEquals(Bound.EXACT, entry.getBound());
        assertNull(tt.get(weakestProtectedKey), "promotion must use the protected replacement lane");
    }

    @Test
    void clear_resetsAllEntries() {
        var tt = new TranspositionTable(SMALL_SIZE);
        tt.put(0xAAL, 5, 100, Bound.EXACT, 1);
        tt.put(0xBBL, 3, 200, Bound.LOWER, 2);

        tt.clear();

        assertNull(tt.get(0xAAL),
                "clear() must drop previously stored EXACT entry");
        assertNull(tt.get(0xBBL),
                "clear() must drop previously stored LOWER entry");
    }
}
