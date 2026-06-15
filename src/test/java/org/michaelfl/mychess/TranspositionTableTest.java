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
        // size = 16 means the low 4 bits select the bucket. Two keys with
        // identical low 4 bits but different high bits land in the same
        // slot; get() must reject the second one via its full hashKey
        // identity check.
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
    void put_overwritesDifferentKeyInSameBucket() {
        // Different keys landing in the same bucket: the new key wins
        // unconditionally (the keep-deeper-EXACT guard requires hashKey
        // identity, so it does not apply to cross-key bucket collisions).
        var tt = new TranspositionTable(SMALL_SIZE);
        long oldKey = 0x100L;
        long newKey = 0x200L;
        tt.put(oldKey, 5, 100, Bound.EXACT, 1);
        tt.put(newKey, 3, 200, Bound.EXACT, 2);

        assertNull(tt.get(oldKey),
                "old key must be evicted by a same-bucket put");
        TTEntry entry = tt.get(newKey);
        assertNotNull(entry, "new key must be retrievable");
        assertEquals(3, entry.getDepth());
        assertEquals(200, entry.getScore());
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
