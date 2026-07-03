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
 * bucket coexistence and isolation, hash-bucket collision handling on
 * {@code get()}, the depth-preferred-EXACT replacement policy with
 * EXACT tie-break, and {@code clear()}.
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
    void get_returnsNull_forUnstoredKeyEvenWhenBucketHoldsOtherKeys() {
        // With SMALL_SIZE=16 and BUCKET_SIZE=4, hashSize=4, so the bucket
        // is picked by the low 2 bits of the key. Two keys with identical
        // low bits (0x100 and 0x200 — both bucket 0) land in the same
        // bucket, but get() checks the full 64-bit hashKey identity on
        // every scanned slot — it must return null for a key that was
        // never stored, even when another key occupies a slot in the
        // same bucket.
        var tt = new TranspositionTable(SMALL_SIZE);
        long storedKey = 0x100L;
        long unstoredKeyInSameBucket = 0x200L;
        tt.put(storedKey, 3, 50, Bound.EXACT, 1);

        assertNotNull(tt.get(storedKey), "stored key must be found");
        assertNull(tt.get(unstoredKeyInSameBucket),
                "unstored key in same bucket must not accidentally return a neighbor's entry");
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

    // -------------------------------------------------------------------
    // Bucket-specific tests (4-slot buckets, depth-preferred-EXACT eviction
    // with EXACT tie-break — see class-level Javadoc of TranspositionTable).
    //
    // Bucket-picking keys: with SMALL_SIZE=16 and BUCKET_SIZE=4 the bucket
    // index comes from the low 2 bits of the hashKey. Keys with low bits
    // 00 → bucket 0 (0x100, 0x200, 0x300, 0x400, 0x500). Keys with low
    // bits 01 → bucket 1 (0x101, 0x201, ...). We use those to control
    // bucket assignment deterministically without exposing internals.
    // -------------------------------------------------------------------

    @Test
    void bucket_fillsWithFourDifferentKeys_allRetrievable() {
        // Fill bucket 0 with four different keys (bucket is exactly full,
        // no eviction yet). Every stored key must be retrievable via get().
        var tt = new TranspositionTable(SMALL_SIZE);
        long[] bucket0Keys = { 0x100L, 0x200L, 0x300L, 0x400L };
        for (int i = 0; i < bucket0Keys.length; i++) {
            tt.put(bucket0Keys[i], 3, 100 + i, Bound.EXACT, i + 1);
        }

        for (int i = 0; i < bucket0Keys.length; i++) {
            TTEntry entry = tt.get(bucket0Keys[i]);
            assertNotNull(entry, "key #" + i + " (0x" + Long.toHexString(bucket0Keys[i]) + ") must be retrievable");
            assertEquals(bucket0Keys[i], entry.getHashKey(), "hashKey for key #" + i);
            assertEquals(100 + i, entry.getScore(), "score for key #" + i);
            assertEquals(i + 1, entry.getBestMove(), "bestMove for key #" + i);
        }
    }

    @Test
    void bucket_full_evictsLowestDepthEntry() {
        // Fill bucket 0 with 4 keys at depths 5, 4, 3, 2 (all EXACT).
        // A 5th put in the same bucket must evict the depth-2 entry
        // (lowest depth); the depth-5, 4, 3 entries must survive.
        var tt = new TranspositionTable(SMALL_SIZE);
        long keyD5 = 0x100L, keyD4 = 0x200L, keyD3 = 0x300L, keyD2 = 0x400L;
        long newKey = 0x500L;
        tt.put(keyD5, 5, 100, Bound.EXACT, 1);
        tt.put(keyD4, 4, 100, Bound.EXACT, 2);
        tt.put(keyD3, 3, 100, Bound.EXACT, 3);
        tt.put(keyD2, 2, 100, Bound.EXACT, 4);

        tt.put(newKey, 6, 200, Bound.EXACT, 5);

        assertNotNull(tt.get(keyD5), "depth-5 must survive");
        assertNotNull(tt.get(keyD4), "depth-4 must survive");
        assertNotNull(tt.get(keyD3), "depth-3 must survive");
        assertNull(tt.get(keyD2), "depth-2 must be evicted (lowest depth)");
        assertNotNull(tt.get(newKey), "newly inserted key must land in the freed slot");
    }

    @Test
    void bucket_full_evictionTieBreak_prefersNonExactOverExact() {
        // Fill bucket 0 so that two slots share the eviction-candidate
        // depth (=3): one EXACT and one LOWER. The other two slots are
        // at higher depth (5 and 4) — safely above the eviction floor.
        // The 5th put must evict the depth-3 LOWER entry, keeping the
        // depth-3 EXACT (EXACT wins the tie-break).
        var tt = new TranspositionTable(SMALL_SIZE);
        long keyD5 = 0x100L, keyD4 = 0x200L, keyD3Exact = 0x300L, keyD3Lower = 0x400L;
        long newKey = 0x500L;
        tt.put(keyD5, 5, 100, Bound.EXACT, 1);
        tt.put(keyD4, 4, 100, Bound.EXACT, 2);
        tt.put(keyD3Exact, 3, 100, Bound.EXACT, 3);
        tt.put(keyD3Lower, 3, 100, Bound.LOWER, 4);

        tt.put(newKey, 6, 200, Bound.EXACT, 5);

        assertNotNull(tt.get(keyD5), "depth-5 EXACT must survive");
        assertNotNull(tt.get(keyD4), "depth-4 EXACT must survive");
        assertNotNull(tt.get(keyD3Exact),
                "depth-3 EXACT must survive — EXACT wins tie-break over equal-depth non-EXACT");
        assertNull(tt.get(keyD3Lower),
                "depth-3 LOWER must be evicted — non-EXACT loses tie-break at equal depth");
        assertNotNull(tt.get(newKey));
    }

    @Test
    void bucket_notFull_freeSlotUsedBeforeEviction() {
        // Bucket 0 has 2 keys at depth 5. Insert a 3rd key with LOWER
        // depth (=1). Even though the new key has lower depth than the
        // incumbents, the bucket is not full — the 3rd put must take an
        // empty slot instead of evicting either incumbent. All three
        // keys must be retrievable afterward.
        var tt = new TranspositionTable(SMALL_SIZE);
        long key1 = 0x100L, key2 = 0x200L, key3 = 0x300L;
        tt.put(key1, 5, 100, Bound.EXACT, 1);
        tt.put(key2, 5, 100, Bound.EXACT, 2);

        tt.put(key3, 1, 100, Bound.EXACT, 3);

        assertNotNull(tt.get(key1), "existing high-depth key1 must survive");
        assertNotNull(tt.get(key2), "existing high-depth key2 must survive");
        assertNotNull(tt.get(key3), "newly inserted low-depth key3 must occupy a free slot");
    }

    @Test
    void hash_differentBucketsDoNotInterfere() {
        // Bucket isolation: fill bucket 0 to capacity (4 keys), then
        // insert into bucket 1. Bucket 0's four entries must all survive
        // — put() into an unrelated bucket must not touch them.
        var tt = new TranspositionTable(SMALL_SIZE);
        long[] bucket0Keys = { 0x100L, 0x200L, 0x300L, 0x400L };
        long bucket1Key = 0x101L;   // low 2 bits = 01, distinct bucket
        for (int i = 0; i < bucket0Keys.length; i++) {
            tt.put(bucket0Keys[i], 3, 100 + i, Bound.EXACT, i + 1);
        }

        tt.put(bucket1Key, 1, 999, Bound.LOWER, 99);

        for (int i = 0; i < bucket0Keys.length; i++) {
            assertNotNull(tt.get(bucket0Keys[i]),
                    "bucket-0 entry #" + i + " must survive an unrelated bucket-1 insert");
        }
        assertNotNull(tt.get(bucket1Key), "bucket-1 entry must be present");
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
