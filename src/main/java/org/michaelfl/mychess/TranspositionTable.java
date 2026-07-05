package org.michaelfl.mychess;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Fixed-size bucketed transposition table caching per-position search
 * results keyed by Zobrist hash. Positions reached through different move
 * orders are evaluated only once: subsequent visits read the cached score
 * (when the stored depth is at least as deep as the new search) or use the
 * stored best move as a move-ordering hint.
 *
 * <p>The table stores entries in a contiguous off-heap {@link MemorySegment}
 * allocated from an {@link Arena}. Each slot is a fixed 24-byte record:
 * one {@code long} hash key followed by four {@code int} values for depth,
 * score, bound ordinal, and best move. {@link TTEntryView} is a lightweight
 * view positioned on one record of that segment; the table reuses a single
 * view instance across all {@link #get(long)} and {@link #put} calls to
 * avoid allocation during search. Callers must therefore read the values
 * they need before the next table access repositions the view.
 *
 * <h2>Bucket layout &amp; lookup</h2>
 *
 * <p>Slots are grouped into fixed-size <em>buckets</em> of
 * {@value #BUCKET_SIZE} slots each. The hash function masks the low
 * {@code log2(size / BUCKET_SIZE)} bits of the 64-bit Zobrist key to
 * pick a bucket. Then {@link #get(long)} scans all {@value #BUCKET_SIZE}
 * slots of that bucket linearly, repositioning the reused view on each,
 * and returns the view whose stored {@code hashKey} matches the argument
 * exactly (full 64-bit identity check). Up to {@value #BUCKET_SIZE}
 * distinct keys that hash to the same bucket therefore coexist without
 * evicting each other; only a bucket-full-of-different-keys forces a
 * replacement decision. On no match, {@link #get(long)} returns
 * {@code null}. True 64-bit Zobrist collisions between distinct
 * positions are astronomically rare (~1 in 10^19 per pair) and treated
 * as ignorable.
 *
 * <h2>Replacement strategy</h2>
 *
 * <p>{@link #put(long, int, int, Bound, int)} scans the target bucket
 * looking for a slot that already holds the given {@code hashKey}:
 * <ul>
 *   <li><b>Same key already present.</b> If the incumbent is a strictly
 *       deeper {@link Bound#EXACT} entry, the put is a no-op (do not lose
 *       depth to a shallow re-visit). Otherwise, the incumbent slot is
 *       overwritten in place.</li>
 *   <li><b>Key not in bucket.</b> Evict the entry with the <em>lowest</em>
 *       stored {@code depth}. Ties are broken against {@link Bound#EXACT}
 *       — a non-EXACT candidate is picked over an EXACT incumbent of
 *       equal depth, so EXACT scores enjoy a small extra survival margin.
 *       The new entry then takes that slot.</li>
 * </ul>
 *
 * <p>This depth-preferred-EXACT policy is the simplest replacement rule
 * that consistently preserves information-dense entries. More elaborate
 * policies (age, hit-count, two-tier lanes, admission control) were
 * measured in the {@code tt-bucket-*} branches during v4.0.x development
 * and did not measurably outperform this rule at TC 40/60 — see
 * roadmap § 12.1 follow-up for the full comparison.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Every explicitly created instance owns native memory and must be
 * closed when it is no longer needed, preferably by wrapping it in
 * try-with-resources or by calling {@link #close()} from test or
 * application teardown code. The process-wide
 * {@link #getDefaultInstance()} is intended to live for the JVM lifetime
 * and is normally not closed manually. Tests use isolated instances
 * obtained from {@code TestSupport.createTestTT()} to avoid cross-test
 * pollution. {@link #clear()} resets all entries to the empty-sentinel
 * state (hashKey 0, depth 0, score 0, bound EXACT, bestMove 0) and should
 * be called whenever the engine starts a new game (UCI
 * {@code ucinewgame}) so that scores from the previous game do not
 * influence the new one.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Not thread-safe. {@link #put(long, int, int, Bound, int)} writes
 * five fields of a {@link TTEntryView} non-atomically; a concurrent
 * {@link #get(long)} could observe a half-updated entry. Additionally,
 * the single reused {@link TTEntryView} view means concurrent
 * {@code get} / {@code put} would race on the view's current position.
 * The engine runs a single-threaded search executor so this is not an
 * issue today; a future lazy-SMP search would need atomic packed-int
 * entries and a per-thread view (or none at all).
 *
 * @author Michael Fleischhauer
 */
public final class TranspositionTable implements AutoCloseable {

    /** Number of slots in the process-wide default table. */
    private static final int DEFAULT_SIZE = 1 << 22;

    /** Number of slots per bucket. */
    private static final int BUCKET_SIZE = 4;

    /** Size in bytes of one serialized table entry in {@link #memory}. */
    private static final long ENTRY_SIZE = 24;

    /** Offset of the full 64-bit Zobrist key within an entry record. */
    private static final long HASH_KEY_OFFSET  = 0;

    /** Offset of the remaining search depth within an entry record. */
    private static final long DEPTH_OFFSET     = 8;

    /** Offset of the cached score within an entry record. */
    private static final long SCORE_OFFSET     = 12;

    /** Offset of the {@link Bound#ordinal()} value within an entry record. */
    private static final long BOUND_OFFSET     = 16;

    /** Offset of the packed best move within an entry record. */
    private static final long BEST_MOVE_OFFSET = 20;

    /** Fast ordinal-to-enum lookup for bound values stored in {@link #memory}. */
    private static final Bound[] BOUNDS = {
            Bound.EXACT,
            Bound.LOWER,
            Bound.UPPER
    };

    /** Lazy process-wide table used when an engine does not receive an explicit table. */
    private static TranspositionTable INSTANCE;

    /**
     * Score-bound classification stored alongside each cached score, used
     * by the alpha-beta lookup in {@code PositionSearch.alphaBetaSearchPre}
     * to decide whether the cached score is usable directly or only as a
     * window-tightening hint.
     */
    public enum Bound {
        /** Score is the position's exact value: alpha &lt; score &lt; beta at store time. */
        EXACT,
        /** Score is a lower bound on the true value: a beta-cutoff fired at store time, so the true value is at least {@code score}. */
        LOWER,
        /** Score is an upper bound on the true value: every legal move failed low, so the true value is at most {@code score}. */
        UPPER
    }

    /**
     * View on one slot in the table. The object itself only stores the byte
     * offset of the current record inside the parent table's {@link #memory}
     * segment; getters read the fields from that segment on demand.
     *
     * <p>{@link TranspositionTable#get(long)} and
     * {@link TranspositionTable#put(long, int, int, Bound, int)} reuse a
     * single {@code TTEntryView} to avoid allocation during search. Callers
     * must read the needed values before the next table access and must
     * not keep the returned entry as a stable snapshot.
     *
     * <p>Fields:
     * <ul>
     *   <li>{@code hashKey} — full 64-bit Zobrist key of the stored
     *       position. Doubles as occupancy marker: a freshly cleared
     *       slot has {@code hashKey == 0}.</li>
     *   <li>{@code depth} — {@code remainingDepth} at which this entry
     *       was searched (= {@code maxDepth - currentDepth}, NOT the
     *       distance-from-root). A lookup uses this entry's score only
     *       if the entry's {@code depth} is at least as large as the
     *       caller's {@code remainingDepth}.</li>
     *   <li>{@code score} — cached score in centi-pawns. Mate scores
     *       are stored relative to the cached position (mate-in-N
     *       plies from here), not relative to the search root; callers
     *       must adjust on read/write via
     *       {@link WeightingFunction#scoreToTT(int, int)} /
     *       {@link WeightingFunction#scoreFromTT(int, int)}.</li>
     *   <li>{@code bound} — see {@link Bound}.</li>
     *   <li>{@code bestMove} — packed-int move (see {@link Move}) that
     *       produced the cached score. Used as a move-ordering hint on
     *       lookup even when the entry's depth is too shallow for the
     *       score to be returned directly.</li>
     * </ul>
     */
    public final class TTEntryView {

        /** Byte offset of this view's current entry record in {@link #memory}. */
        private long memoryOffset;

        /**
         * Positions this view on the entry record for the given slot index.
         *
         * @param index slot index in the table (bucket start + within-bucket offset)
         */
        void position(int index) {
            memoryOffset = index * ENTRY_SIZE;
        }

        /** Full 64-bit Zobrist key of the stored position. */
        public long getHashKey() {
            return memory.get(ValueLayout.JAVA_LONG, memoryOffset + HASH_KEY_OFFSET);
        }

        /** {@code remainingDepth} at which this entry was searched. */
        public int getDepth() {
            return memory.get(ValueLayout.JAVA_INT, memoryOffset + DEPTH_OFFSET);
        }

        /** Cached score in centi-pawns, relative to the cached position. */
        public int getScore() {
            return memory.get(ValueLayout.JAVA_INT, memoryOffset + SCORE_OFFSET);
        }

        /** Score-bound classification — see {@link Bound}. */
        public Bound getBound() {
            int ordinal = memory.get(ValueLayout.JAVA_INT, memoryOffset + BOUND_OFFSET);
            return BOUNDS[ordinal];
        }

        /** Packed-int best move from the cached search. */
        public int getBestMove() {
            return memory.get(ValueLayout.JAVA_INT, memoryOffset + BEST_MOVE_OFFSET);
        }

        /**
         * Writes all entry fields to this view's current record.
         *
         * @param hashKey  full 64-bit Zobrist key of the position
         * @param depth    remaining search depth at which {@code score} was obtained
         * @param score    centi-pawn score relative to the stored position
         * @param bound    cached score bound type
         * @param bestMove packed-int move that produced {@code score}, or 0 if none is meaningful
         */
        void write(final long hashKey, final int depth, final int score, final Bound bound, final int bestMove) {
            memory.set(ValueLayout.JAVA_LONG, memoryOffset + HASH_KEY_OFFSET, hashKey);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + DEPTH_OFFSET, depth);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + SCORE_OFFSET, score);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + BOUND_OFFSET, bound.ordinal());
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + BEST_MOVE_OFFSET, bestMove);
        }
    }

    /** Arena owning the off-heap memory for this table. Closed by {@link #close()}. */
    private final Arena arena = Arena.ofShared();

    /** Number of buckets in the table ({@code size / BUCKET_SIZE}). Also, a power of two. */
    private final int hashSize;

    /** Contiguous off-heap storage for all serialized table entries ({@code size * ENTRY_SIZE} bytes). */
    private final MemorySegment memory;

    /** Reused view object positioned on the currently accessed table entry. */
    private final TTEntryView currentEntryView = new TTEntryView();

    /**
     * Allocates a table with {@code size} total slots, laid out as
     * {@code size / BUCKET_SIZE} buckets of {@value #BUCKET_SIZE} slots
     * each. The size must be a power of two so that
     * {@code hashSize = size / BUCKET_SIZE} is also a power of two and
     * {@link #hash(long)} can pick a bucket by masking with
     * {@code hashSize - 1}. All slots are initialized to the empty-slot
     * sentinel state ({@code hashKey == 0}).
     *
     * @param size total number of slots. Must be a power of two (and,
     *             implicitly, at least {@value #BUCKET_SIZE}); an
     *             {@link IllegalArgumentException} is thrown otherwise.
     */
    public TranspositionTable(int size) {
        if (!isPowerOfTwo(size)) {
            throw new IllegalArgumentException("size must be power of two");
        }

        this.hashSize = size / BUCKET_SIZE;
        this.memory = arena.allocate(size * ENTRY_SIZE, 8); // all bytes 0-initialized
    }

    /**
     * Checks whether the given value is a positive power of two.
     */
    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    /**
     * Releases the native memory owned by this table. After closing, the
     * instance must no longer be used.
     */
    @Override
    public void close() {
        arena.close();
    }

    /**
     * Lazy-initialized process-wide singleton with {@code DEFAULT_SIZE = 2^22}
     * slots (~96 MiB of off-heap TT record storage). Used by
     * {@link EngineConfig.Builder#build()} when no explicit
     * {@link TranspositionTable} is set, and by the UCI / REPL code paths
     * that want one shared cache per JVM. Production engines normally
     * pick this one up; tests must create their own via
     * {@code TestSupport.createTestTT()} to stay isolated. The returned
     * singleton is intended to live for the lifetime of the JVM and should
     * not normally be closed by callers.
     */
    public static synchronized TranspositionTable getDefaultInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TranspositionTable(DEFAULT_SIZE);
        }

        return INSTANCE;
    }

    /**
     * Resets every slot to the empty-sentinel state ({@code hashKey == 0},
     * everything else default). Required between games (UCI
     * {@code ucinewgame}, REPL {@code new}) so cached scores from a prior
     * game cannot leak into the next; also useful in tests that want a
     * fresh start without throwing away the TT object itself.
     */
    public void clear() {
        memory.fill((byte) 0);
    }

    /**
     * Maps a Zobrist key to the start slot index of its bucket. Takes the
     * low {@code log2(hashSize)} bits of the key to pick a bucket ordinal,
     * then multiplies by {@value #BUCKET_SIZE} to get the flat slot index
     * of the bucket's first slot. The bucket then spans indices
     * {@code [return, return + BUCKET_SIZE)}.
     */
    private int hash(final long hashKey) {
        return ((int) hashKey & (hashSize - 1)) * BUCKET_SIZE;
    }

    /**
     * Looks up the entry for the given Zobrist key. Scans all
     * {@value #BUCKET_SIZE} slots of the target bucket linearly,
     * repositioning the reused {@link TTEntryView} on each slot, and
     * returns the view when its stored {@code hashKey} matches the
     * argument exactly (full 64-bit identity). If none of the
     * {@value #BUCKET_SIZE} slots holds this key, returns {@code null}.
     * The returned view is the live positioned view — callers must not
     * retain it across a subsequent {@code get} or
     * {@link #put(long, int, int, Bound, int)} call, because either will
     * reposition it.
     */
    public TTEntryView get(final long hashKey) {
        final int bucketStart = hash(hashKey);
        final int bucketEnd = bucketStart + BUCKET_SIZE;

        for (int index = bucketStart; index < bucketEnd; index++) {
            currentEntryView.position(index);
            if (currentEntryView.getHashKey() == hashKey) {
                return currentEntryView;
            }
        }

        return null;
    }

    /**
     * Inserts an entry into the bucket for {@code hashKey}. Behavior
     * depends on whether the bucket already contains a slot for this
     * key (see the class-level "Replacement strategy" section for the
     * full rationale):
     * <ul>
     *   <li><b>Same key present.</b> If the incumbent is a strictly
     *       deeper {@link Bound#EXACT} entry, this call is a no-op —
     *       the deeper cached result would be lost to a shallower
     *       re-visit. Otherwise, the incumbent slot is overwritten in
     *       place with the new fields.</li>
     *   <li><b>Key not in bucket.</b> The loop tracks an eviction
     *       candidate as it scans: the slot with the lowest {@code depth},
     *       breaking ties by preferring to evict a non-EXACT slot over an
     *       EXACT one of equal depth. That slot is then overwritten with
     *       the new fields.</li>
     * </ul>
     *
     * <p>Because the table shares a single reusable {@link TTEntryView},
     * the scan captures each slot's {@code depth} and {@code bound} in
     * local primitives rather than holding a second live view. After the
     * scan, the view is repositioned once on the chosen slot for write.
     *
     * <p>Mate-score depth adjustment is the caller's responsibility:
     * pass the score already converted to "mate-in-N from this position"
     * (see {@link WeightingFunction#scoreToTT(int, int)}). Likewise, the
     * {@code depth} argument is the {@code remainingDepth} at which the
     * score was searched, not the distance from the root.
     *
     * @param hashKey  full 64-bit Zobrist key of the position
     * @param depth    remaining search depth at which {@code score} was obtained
     * @param score    centi-pawn score relative to the stored position
     * @param bound    {@link Bound#EXACT} / {@link Bound#LOWER} / {@link Bound#UPPER}
     * @param bestMove packed-int move that produced {@code score}, or 0 if none is meaningful (terminal nodes)
     */
    public void put(final long hashKey, final int depth, final int score, final Bound bound, final int bestMove) {
        final int bucketStart = hash(hashKey);
        final int bucketEnd = bucketStart + BUCKET_SIZE;

        // Track the eviction candidate. Sentinel initial values (MAX depth,
        // EXACT bound) ensure the first real slot in the loop always takes
        // over as the initial candidate, then subsequent slots compete
        // against the currently-tracked candidate's captured depth/bound.
        int replaceIndex = bucketStart;
        int candidateDepth = Integer.MAX_VALUE;
        Bound candidateBound = Bound.EXACT;

        for (int index = bucketStart; index < bucketEnd; index++) {
            currentEntryView.position(index);
            final long entryHashKey = currentEntryView.getHashKey();
            final int entryDepth = currentEntryView.getDepth();
            final Bound entryBound = currentEntryView.getBound();

            if (entryHashKey == hashKey) {
                if (entryDepth > depth && entryBound == Bound.EXACT) {
                    return;  // keep deeper exact entry
                }
                replaceIndex = index;   // same-key hit → overwrite in place
                break;
            }
            // Eviction candidate: lowest depth, break ties by preferring
            // to evict a non-EXACT slot over an EXACT one of equal depth.
            if (entryDepth < candidateDepth
                    || (candidateBound == Bound.EXACT
                        && candidateDepth == entryDepth
                        && entryBound != Bound.EXACT)) {
                replaceIndex = index;
                candidateDepth = entryDepth;
                candidateBound = entryBound;
            }
        }

        currentEntryView.position(replaceIndex);
        currentEntryView.write(hashKey, depth, score, bound, bestMove);
    }
}
