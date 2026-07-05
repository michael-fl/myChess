package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.TranspositionTable.Bound;
import org.michaelfl.mychess.TranspositionTable.TTEntryView;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the two-tier admission-hitcount variant of
 * {@link TranspositionTable}: constructor invariants, {@code TTEntryView}
 * field round-tripping, replacement-score semantics (depth + EXACT bonus
 * − generation age), the recent/protected lane split, the hitcount-based
 * admission gate that promotes proven entries into the protected lane,
 * and {@code clear()}.
 *
 * @author Michael Fleischhauer
 */
class TranspositionTableTest {

    private static final int SMALL_SIZE = 16;
    private static final long KEY = 0xCAFEBABEDEADBEEFL;

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = new TranspositionTable(SMALL_SIZE);
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    // ---------- constructor invariants ----------

    // The constructors below throw before returning; no TranspositionTable
    // instance is ever created, so the IDE's "AutoCloseable used outside
    // try-with-resources" warning is a false positive here.
    @Test
    @SuppressWarnings("resource")
    void constructor_rejectsNonPowerOfTwo() {
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(3),
                "size 3 is not a power of two");
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(1000),
                "size 1000 is not a power of two");
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(0),
                "size 0 is not a power of two");
    }

    @Test
    @SuppressWarnings("resource")
    void constructor_rejectsSizeSmallerThanOneBucket() {
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(1),
                "size 1 cannot hold a full BUCKET_SIZE=8 bucket");
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(2),
                "size 2 cannot hold a full BUCKET_SIZE=8 bucket");
        assertThrows(IllegalArgumentException.class, () -> new TranspositionTable(4),
                "size 4 cannot hold a full BUCKET_SIZE=8 bucket");
    }

    @Test
    void constructor_acceptsPowerOfTwo() {
        assertNull(tt.get(KEY), "fresh table must return null for any lookup");
    }

    // ---------- round-trip ----------

    @Test
    void putAndGet_roundTripsAllFields() {
        tt.put(KEY, 5, 137, Bound.EXACT, 0x42);

        TTEntryView entry = tt.get(KEY);
        assertNotNull(entry, "stored key must be retrievable");
        assertEquals(KEY, entry.getHashKey(), "hashKey");
        assertEquals(5, entry.getDepth(), "depth");
        assertEquals(137, entry.getScore(), "score");
        assertEquals(Bound.EXACT, entry.getBound(), "bound");
        assertEquals(0x42, entry.getBestMove(), "bestMove");
    }

    @Test
    void get_returnsNull_forUnstoredKeyEvenWhenBucketHoldsOtherKeys() {
        // With SMALL_SIZE=16 and BUCKET_SIZE=8, hashSize=2 and the bucket
        // is picked by the low 1 bit of the key. Two keys with matching
        // low bit (0x100 and 0x200 — both bit 0 clear) land in the same
        // bucket, but get() checks the full 64-bit hashKey identity on
        // every scanned slot — it must return null for a key that was
        // never stored, even when another key occupies a slot in the
        // same bucket.
        long storedKey = 0x100L;
        long unstoredKeyInSameBucket = 0x200L;
        tt.put(storedKey, 3, 50, Bound.EXACT, 1);

        assertNotNull(tt.get(storedKey), "stored key must be found");
        assertNull(tt.get(unstoredKeyInSameBucket),
                "unstored key in same bucket must not accidentally return a neighbor's entry");
    }

    // ---------- same-key updates: depth-preferred-EXACT + replacement-score ----------

    @Test
    void put_overwritesShallowerEntry() {
        tt.put(KEY, 3, 100, Bound.EXACT, 1);
        tt.put(KEY, 5, 200, Bound.EXACT, 2);

        TTEntryView entry = tt.get(KEY);
        assertNotNull(entry, "stored key must be retrievable");
        assertEquals(5, entry.getDepth(),
                "deeper entry must overwrite the shallower one");
        assertEquals(200, entry.getScore());
        assertEquals(2, entry.getBestMove());
    }

    @Test
    void put_keepsDeeperExactEntry() {
        tt.put(KEY, 5, 100, Bound.EXACT, 1);
        tt.put(KEY, 3, 200, Bound.EXACT, 2);   // shallower → must be ignored

        TTEntryView entry = tt.get(KEY);
        assertNotNull(entry, "stored key must be retrievable");
        assertEquals(5, entry.getDepth(),
                "deeper EXACT entry must be preserved against a shallower put");
        assertEquals(100, entry.getScore());
        assertEquals(1, entry.getBestMove());
    }

    @Test
    void put_keepsDeeperEntryIfReplacementScoreIsHigherEvenWhenBoundIsNotExact() {
        // Depth-5 LOWER: replacementScore = 4*5 + 0 = 20
        // Depth-3 EXACT: replacementScore = 4*3 + 2 = 14
        // 14 < 20 → the second put is a no-op, the LOWER entry is kept.
        tt.put(KEY, 5, 100, Bound.LOWER, 1);
        tt.put(KEY, 3, 200, Bound.EXACT, 2);

        TTEntryView entry = tt.get(KEY);
        assertNotNull(entry, "stored key must be retrievable");
        assertEquals(5, entry.getDepth(),
                "a deeper non-EXACT entry with higher replacement score must be kept");
        assertEquals(100, entry.getScore());
        assertEquals(Bound.LOWER, entry.getBound());
        assertEquals(1, entry.getBestMove());
    }

    @Test
    void put_replacesDeeperNonExactEntryWhenNewExactHasHigherReplacementScore() {
        // Depth-3 LOWER: replacementScore = 4*3 + 0 = 12
        // Depth-4 EXACT: replacementScore = 4*4 + 2 = 18
        // 18 > 12 → the second put overwrites in place.
        tt.put(KEY, 3, 100, Bound.LOWER, 1);
        tt.put(KEY, 4, 200, Bound.EXACT, 2);

        TTEntryView entry = tt.get(KEY);
        assertNotNull(entry, "stored key must be retrievable");
        assertEquals(4, entry.getDepth());
        assertEquals(200, entry.getScore());
        assertEquals(Bound.EXACT, entry.getBound());
        assertEquals(2, entry.getBestMove());
    }

    // ---------- recent / protected lane separation ----------

    @Test
    void put_keepsRecentEntrySeparateFromProtectedEntries() {
        // Fill the protected lane (4 slots) with strong entries via
        // promotion, then verify that a shallow recent-lane put does
        // not disturb them.
        long protectedKey1 = 0x100L, protectedKey2 = 0x200L, protectedKey3 = 0x300L;
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

        assertNotNull(tt.get(protectedKey1), "recent-lane traffic must not evict protected key 1");
        assertNotNull(tt.get(protectedKey2), "recent-lane traffic must not evict protected key 2");
        assertNotNull(tt.get(protectedKey3), "recent-lane traffic must not evict protected key 3");
        assertNotNull(tt.get(newRecentKey), "new recent key must be retrievable");
    }

    @Test
    void put_newUnprovenEntryReplacesOnlyRecentLane() {
        // Fill both lanes: 4 protected + 4 recent slots. Then insert
        // yet another new key. It must evict a recent-lane slot, never
        // any of the protected entries — because it has no hits yet
        // (hitcount = 0 fails the admission gate).
        long protectedKey1 = 0x100L, protectedKey2 = 0x200L, protectedKey3 = 0x300L, protectedKey4 = 0x400L;
        long recentKey1 = 0x500L, recentKey2 = 0x600L, recentKey3 = 0x700L, recentKey4 = 0x800L;
        long newRecentKey = 0x900L;

        promoteToProtected(protectedKey1, 6, 100, Bound.EXACT, 1);
        promoteToProtected(protectedKey2, 5, 200, Bound.EXACT, 2);
        promoteToProtected(protectedKey3, 4, 300, Bound.EXACT, 3);
        promoteToProtected(protectedKey4, 3, 400, Bound.EXACT, 4);

        tt.put(recentKey1, 1, 501, Bound.LOWER, 5);
        tt.put(recentKey2, 1, 502, Bound.LOWER, 6);
        tt.put(recentKey3, 1, 503, Bound.LOWER, 7);
        tt.put(recentKey4, 1, 504, Bound.LOWER, 8);

        tt.put(newRecentKey, 1, 900, Bound.LOWER, 9);

        assertNotNull(tt.get(protectedKey1), "protected key 1 must be untouched");
        assertNotNull(tt.get(protectedKey2), "protected key 2 must be untouched");
        assertNotNull(tt.get(protectedKey3), "protected key 3 must be untouched");
        assertNotNull(tt.get(protectedKey4), "protected key 4 must be untouched");
        assertNotNull(tt.get(newRecentKey), "new recent key must be retrievable");
    }

    // ---------- admission gate: hit-count promotion ----------

    @Test
    void put_promotesProvenRecentEntryAndEvictsWeakestProtectedEntry() {
        // Fill protected lane. Then insert a stronger key; touch it via
        // gets so its hitcount clears the admission gate; the next put
        // qualifies for promotion and evicts the weakest protected entry.
        long protectedKey1 = 0x100L, protectedKey2 = 0x200L, protectedKey3 = 0x300L;
        long weakestProtectedKey = 0x400L;
        long promotedKey = 0x500L;

        promoteToProtected(protectedKey1, 6, 100, Bound.EXACT, 1);
        promoteToProtected(protectedKey2, 5, 200, Bound.EXACT, 2);
        promoteToProtected(protectedKey3, 4, 300, Bound.EXACT, 3);
        promoteToProtected(weakestProtectedKey, 3, 400, Bound.EXACT, 4);

        // First put lands in recent lane. Two hits raise hitcount to 2.
        tt.put(promotedKey, 7, 500, Bound.EXACT, 5);
        assertNotNull(tt.get(promotedKey));
        assertNotNull(tt.get(promotedKey));

        // Second put: same key, hitcount=2 clears the gate, promotion
        // fires — the weakest protected slot (depth 3) is evicted.
        tt.put(promotedKey, 7, 500, Bound.EXACT, 5);

        assertNotNull(tt.get(protectedKey1), "strong protected key 1 must stay");
        assertNotNull(tt.get(protectedKey2), "strong protected key 2 must stay");
        assertNotNull(tt.get(protectedKey3), "strong protected key 3 must stay");
        assertNull(tt.get(weakestProtectedKey), "weakest protected key must be evicted");

        TTEntryView entry = tt.get(promotedKey);
        assertNotNull(entry, "promoted key must be retrievable");
        assertEquals(7, entry.getDepth());
    }

    @Test
    void put_doesNotPromoteUntouchedRecentEntryEvenIfStrong() {
        // Even a very deep candidate must not enter the protected lane
        // without having been visited at least twice via get(). The
        // hitcount gate is the whole point of the admission policy.
        long protectedKey1 = 0x100L, protectedKey2 = 0x200L, protectedKey3 = 0x300L;
        long weakestProtectedKey = 0x400L;
        long strongButUntouchedKey = 0x500L;

        promoteToProtected(protectedKey1, 6, 100, Bound.EXACT, 1);
        promoteToProtected(protectedKey2, 5, 200, Bound.EXACT, 2);
        promoteToProtected(protectedKey3, 4, 300, Bound.EXACT, 3);
        promoteToProtected(weakestProtectedKey, 3, 400, Bound.EXACT, 4);

        // Two puts, but NO get() between them → hitcount stays 0.
        tt.put(strongButUntouchedKey, 9, 900, Bound.EXACT, 9);
        tt.put(strongButUntouchedKey, 9, 901, Bound.EXACT, 10);

        assertNotNull(tt.get(weakestProtectedKey),
                "the weakest protected entry must survive — the newcomer never proved itself");
        assertNotNull(tt.get(strongButUntouchedKey),
                "the strong-but-untouched newcomer stays in the recent lane");
    }

    @Test
    void put_doesNotPromoteIfProtectedReplacementScoreIsNotHigher() {
        // Candidate has hitcount ≥ 2 (admission gate cleared), but its
        // replacement score is not strictly higher than the weakest
        // protected entry — the promotion must be denied.
        long protectedKey1 = 0x100L, protectedKey2 = 0x200L, protectedKey3 = 0x300L;
        long weakestProtectedKey = 0x400L;
        long candidateKey = 0x500L;

        promoteToProtected(protectedKey1, 6, 100, Bound.EXACT, 1);
        promoteToProtected(protectedKey2, 5, 200, Bound.EXACT, 2);
        promoteToProtected(protectedKey3, 4, 300, Bound.EXACT, 3);
        promoteToProtected(weakestProtectedKey, 3, 400, Bound.EXACT, 4);

        // depth=3 LOWER: replacementScore = 4*3 = 12
        // weakest protected: depth=3 EXACT, replacementScore = 14
        // 12 !> 14 → no promotion, candidate stays in recent lane.
        tt.put(candidateKey, 3, 500, Bound.LOWER, 5);
        touch(candidateKey, 2);
        tt.put(candidateKey, 3, 501, Bound.LOWER, 6);

        assertNotNull(tt.get(weakestProtectedKey),
                "candidate with equal/lower replacement score must not enter protected lane");
        assertNotNull(tt.get(candidateKey),
                "candidate should still remain in recent lane");
    }

    @Test
    void put_promotesSameKeyFromRecentLaneToProtectedLane() {
        long promotedKey = 0x100L;
        long protectedKey1 = 0x200L, protectedKey2 = 0x300L, protectedKey3 = 0x400L;
        long weakestProtectedKey = 0x500L;

        promoteToProtected(protectedKey1, 6, 200, Bound.EXACT, 2);
        promoteToProtected(protectedKey2, 5, 300, Bound.EXACT, 3);
        promoteToProtected(protectedKey3, 4, 400, Bound.EXACT, 4);
        promoteToProtected(weakestProtectedKey, 3, 500, Bound.EXACT, 5);

        // Enter recent lane, gather hits, then re-put with a much
        // stronger value: the entry is promoted, and the weakest
        // protected entry is evicted.
        tt.put(promotedKey, 1, 100, Bound.LOWER, 1);
        assertNotNull(tt.get(promotedKey));
        assertNotNull(tt.get(promotedKey));

        tt.put(promotedKey, 7, 700, Bound.EXACT, 7);
        TTEntryView entry = tt.get(promotedKey);

        assertNotNull(entry, "promoted key must stay retrievable");
        assertEquals(7, entry.getDepth(), "promoted key must hold the newer protected value");
        assertEquals(Bound.EXACT, entry.getBound());
        assertNull(tt.get(weakestProtectedKey), "promotion must evict weakest protected entry");
    }

    // ---------- generation aging ----------

    @Test
    void put_generationAgingMakesOldProtectedEntryReplaceable() {
        long oldProtectedKey = 0x100L;
        long protectedKey2 = 0x200L, protectedKey3 = 0x300L, protectedKey4 = 0x400L;
        long newCandidateKey = 0x500L;

        promoteToProtected(oldProtectedKey, 3, 100, Bound.EXACT, 1);
        promoteToProtected(protectedKey2, 6, 200, Bound.EXACT, 2);
        promoteToProtected(protectedKey3, 5, 300, Bound.EXACT, 3);
        promoteToProtected(protectedKey4, 4, 400, Bound.EXACT, 4);

        // Two generation increments → all four protected entries have
        // an age penalty of 2 relative to the new generation.
        // oldProtectedKey (depth=3 EXACT): 4*3 + 2 − 2 = 12
        // The other three still have replacement scores 24, 20, 16.
        // A new candidate at depth=3 EXACT with fresh generation:
        // 4*3 + 2 − 0 = 14 > 12 → wins over oldProtectedKey.
        tt.nextGeneration();
        tt.nextGeneration();

        tt.put(newCandidateKey, 3, 500, Bound.EXACT, 5);
        touch(newCandidateKey, 2);
        tt.put(newCandidateKey, 3, 501, Bound.EXACT, 6);

        assertNull(tt.get(oldProtectedKey),
                "old protected entry with same depth must become replaceable through generation aging");

        TTEntryView entry = tt.get(newCandidateKey);
        assertNotNull(entry, "new candidate must be promoted into protected lane");
        assertEquals(3, entry.getDepth());
        assertEquals(Bound.EXACT, entry.getBound());

        assertNotNull(tt.get(protectedKey2));
        assertNotNull(tt.get(protectedKey3));
        assertNotNull(tt.get(protectedKey4));
    }

    // ---------- hit-count observability ----------

    @Test
    void get_incrementsHitcountOnHit() {
        // A put() stores hitcount=0 on a fresh slot. Each subsequent
        // get() increments the slot's hitcount BEFORE returning the
        // view, so the very first get() sees hitcount=1, the second
        // sees hitcount=2, etc. Same-key put() then carries the
        // accumulated hitcount into the rewritten record — see the
        // promotion tests above.
        tt.put(KEY, 5, 100, Bound.EXACT, 1);

        assertEquals(1, Objects.requireNonNull(tt.get(KEY)).getHitcount(), "first get must increment hitcount to 1");
        assertEquals(2, Objects.requireNonNull(tt.get(KEY)).getHitcount(), "second get must increment hitcount to 2");
        assertEquals(3, Objects.requireNonNull(tt.get(KEY)).getHitcount(), "third get must increment hitcount to 3");
    }

    // ---------- clear ----------

    @Test
    void clear_resetsAllEntries() {
        tt.put(0xAAL, 5, 100, Bound.EXACT, 1);
        tt.put(0xBBL, 3, 200, Bound.LOWER, 2);

        tt.clear();

        assertNull(tt.get(0xAAL), "clear() must drop previously stored EXACT entry");
        assertNull(tt.get(0xBBL), "clear() must drop previously stored LOWER entry");
    }

    // ---------- test helpers ----------

    /**
     * Puts an entry, hits it twice to raise its hitcount past the admission
     * gate, then puts again — which triggers promotion into the protected
     * lane. Used by tests that need to prepare the protected lane with
     * strong entries before exercising the eviction/promotion logic.
     */
    @SuppressWarnings("SameParameterValue")   // {@code bound} is always EXACT in current callers, but the helper is written for arbitrary bounds
    private void promoteToProtected(long key, int depth, int score, Bound bound, int bestMove) {
        tt.put(key, depth, score, bound, bestMove);
        assertNotNull(tt.get(key));
        assertNotNull(tt.get(key));
        tt.put(key, depth, score, bound, bestMove);
    }

    /**
     * Hits {@code key} {@code count} times via {@link TranspositionTable#get}
     * to raise its hitcount without changing its stored value.
     */
    @SuppressWarnings("SameParameterValue")   // {@code count} is always 2 in current callers, but the helper generalizes trivially
    private void touch(long key, int count) {
        for (int i = 0; i < count; i++) {
            assertNotNull(tt.get(key));
        }
    }
}
