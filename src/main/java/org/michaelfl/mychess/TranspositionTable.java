package org.michaelfl.mychess;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Fixed-size bucketed transposition table with a two-tier eviction policy
 * (admission gate + hit-count promotion), backed by an off-heap
 * {@link MemorySegment}.
 *
 * <p>This is the {@code tt-bucket-two-tier-admission-hitcount} variant of
 * the TT: an experimental replacement policy that was explored during
 * v4.0.x development against the simpler v4.0.2 {@code tt-bucket-depth}
 * baseline. See [roadmap § 12.1 follow-up] for the empirical comparison;
 * at the time of writing this variant scored nominally +5.9 ± 9.8 Elo
 * against {@code tt-bucket-depth} at TC 40/60 (LOS 87.9 %), inconclusive
 * on its own but derived to be roughly a +9-Elo policy contribution once
 * the confounding bucket-size difference is factored out (2×2 factorial
 * disentanglement in the same follow-up). The variant is preserved on
 * this branch for post-NMP/LMR/QSearch re-evaluation, when the TT-access
 * profile shifts and a more elaborate replacement policy may become worth
 * the complexity.
 *
 * <h2>Bucket layout &amp; lookup</h2>
 *
 * <p>Slots are grouped into buckets of {@value #BUCKET_SIZE}. Each bucket
 * is split in the middle into a <em>recent lane</em> (slots 0..3 of the
 * bucket) and a <em>protected lane</em> (slots 4..7). Shallow non-EXACT
 * entries go to the recent lane so the current search front always leaves
 * a move-ordering hint without displacing deeper cached work. Exact or
 * deeper entries can be promoted to the protected lane once they have
 * proven useful (hit-count &gt; 1).
 *
 * <p>{@link #get(long)} scans the whole bucket linearly, repositioning
 * the reused {@link TTEntryView} on each slot, and returns the view when
 * its stored {@code hashKey} matches the argument exactly. Every hit also
 * increments the entry's {@code hitcount} — this is what feeds the
 * admission gate during subsequent {@link #put}s.
 *
 * <h2>Replacement strategy</h2>
 *
 * <p>{@link #put(long, int, int, Bound, int)} first scans the whole
 * bucket for an existing slot with the same {@code hashKey}, then
 * branches on where it landed:
 * <ul>
 *   <li><b>No matching key.</b> Evict the cheapest slot in the
 *       <em>recent</em> lane (lowest {@link #replacementScore}).</li>
 *   <li><b>Matching key found, but the new value is not better than the
 *       existing one.</b> Keep the existing entry — the new call is a
 *       no-op.</li>
 *   <li><b>Matching key in the protected lane.</b> Overwrite in place
 *       with the new fields.</li>
 *   <li><b>Matching key in the recent lane, qualifies for the protected
 *       lane.</b> ({@code hitcount > 1 && (depth > 1 || bound == EXACT)}.)
 *       Look for a protected-lane slot whose replacement score is lower
 *       than the incoming value. If one exists, clear the recent-lane
 *       slot and write into that protected slot — the entry gets
 *       "promoted". Otherwise overwrite the recent-lane slot in
 *       place.</li>
 *   <li><b>Matching key in the recent lane, does not qualify.</b>
 *       Overwrite in place.</li>
 * </ul>
 *
 * <p>The replacement score is
 * {@code 4·depth + (EXACT ? 2 : 0) − (currentGeneration − entryGeneration)}
 * — a linear combination that favors deep, EXACT, recent entries. Older
 * entries lose priority naturally as {@link #nextGeneration()} is called
 * once at the start of every root search.
 *
 * <h2>Storage</h2>
 *
 * <p>Slots are laid out as fixed 32-byte records in a single off-heap
 * {@link MemorySegment} allocated from an {@link Arena}: 8-byte
 * {@code hashKey}, then five {@code int}s ({@code depth}, {@code score},
 * {@code bound} ordinal, {@code bestMove}, {@code generation}) and one
 * more {@code int} ({@code hitcount}). {@link TTEntryView} is a
 * lightweight view positioned on the current record; the table reuses a
 * single view instance across all accesses to avoid allocation on the
 * hot path. Callers must read the fields they need before the next TT
 * access repositions the view.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>Every explicitly created instance owns native memory and must be
 * closed (try-with-resources or explicit {@link #close()} in test
 * teardown). The process-wide {@link #getDefaultInstance()} lives for
 * the JVM lifetime. {@link #clear()} zeroes all records — required on
 * UCI {@code ucinewgame}.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Not thread-safe (single reusable view, non-atomic writes).
 *
 * @author Michael Fleischhauer
 */
public final class TranspositionTable implements AutoCloseable {

    /** Number of slots in the process-wide default table. */
    private static final int DEFAULT_SIZE = 1 << 22;

    /** Number of slots per bucket (recent lane + protected lane). */
    private static final int BUCKET_SIZE = 8;

    /** Boundary between recent lane (lower half) and protected lane (upper half). */
    private static final int PROTECTED_LANE_OFFSET = BUCKET_SIZE / 2;

    /** Size in bytes of one serialized table entry. */
    private static final long ENTRY_SIZE = 32;

    private static final long HASH_KEY_OFFSET   = 0;
    private static final long DEPTH_OFFSET      = 8;
    private static final long SCORE_OFFSET      = 12;
    private static final long BOUND_OFFSET      = 16;
    private static final long BEST_MOVE_OFFSET  = 20;
    private static final long GENERATION_OFFSET = 24;
    private static final long HITCOUNT_OFFSET   = 28;

    private static final Bound[] BOUNDS = { Bound.LOWER, Bound.UPPER, Bound.EXACT };

    private static TranspositionTable INSTANCE;

    /**
     * Score-bound classification stored alongside each cached score, used
     * by the alpha-beta lookup in {@code PositionSearch.alphaBetaSearchPre}
     * to decide whether the cached score is usable directly or only as a
     * window-tightening hint.
     */
    public enum Bound {
        /** Score is a lower bound on the true value: a beta-cutoff fired at store time. */
        LOWER,
        /** Score is an upper bound on the true value: every legal move failed low. */
        UPPER,
        /** Score is the position's exact value: alpha &lt; score &lt; beta at store time. */
        EXACT
    }

    /**
     * View on one slot in the table. Only stores a byte offset; the
     * getters read the fields on demand from the parent's memory segment.
     * The table reuses one instance across all accesses; callers must
     * read the values they need before the next {@code get} / {@code put}
     * repositions the view.
     */
    public final class TTEntryView {

        private long memoryOffset;

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
            return BOUNDS[memory.get(ValueLayout.JAVA_INT, memoryOffset + BOUND_OFFSET)];
        }

        /** Packed-int best move from the cached search. */
        public int getBestMove() {
            return memory.get(ValueLayout.JAVA_INT, memoryOffset + BEST_MOVE_OFFSET);
        }

        /** Generation counter at time of last write (used by the replacement heuristic). */
        public int getGeneration() {
            return memory.get(ValueLayout.JAVA_INT, memoryOffset + GENERATION_OFFSET);
        }

        /** Cumulative hit count on this slot; the admission gate uses it to gate protected-lane promotion. */
        public int getHitcount() {
            return memory.get(ValueLayout.JAVA_INT, memoryOffset + HITCOUNT_OFFSET);
        }

        void incrementHitcount() {
            memory.set(ValueLayout.JAVA_INT, memoryOffset + HITCOUNT_OFFSET, getHitcount() + 1);
        }

        /** Writes all entry fields to the current record, tagging with the table's current generation. */
        void write(final long hashKey, final int depth, final int score, final Bound bound,
                   final int bestMove, final int hitcount) {
            memory.set(ValueLayout.JAVA_LONG, memoryOffset + HASH_KEY_OFFSET, hashKey);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + DEPTH_OFFSET, depth);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + SCORE_OFFSET, score);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + BOUND_OFFSET, bound.ordinal());
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + BEST_MOVE_OFFSET, bestMove);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + GENERATION_OFFSET, generation);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + HITCOUNT_OFFSET, hitcount);
        }

        /** Zeroes the record at the current position (empty-slot sentinel state). */
        void clear() {
            memory.set(ValueLayout.JAVA_LONG, memoryOffset + HASH_KEY_OFFSET, 0L);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + DEPTH_OFFSET, 0);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + SCORE_OFFSET, 0);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + BOUND_OFFSET, 0);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + BEST_MOVE_OFFSET, 0);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + GENERATION_OFFSET, 0);
            memory.set(ValueLayout.JAVA_INT,  memoryOffset + HITCOUNT_OFFSET, 0);
        }
    }

    private final Arena arena = Arena.ofShared();

    /** Number of buckets in the table ({@code size / BUCKET_SIZE}). Also, a power of two. */
    private final int hashSize;
    private final MemorySegment memory;
    private final TTEntryView currentEntryView = new TTEntryView();
    private int generation;

    /**
     * Allocates a table with {@code size} total slots, laid out as
     * {@code size / BUCKET_SIZE} buckets of {@value #BUCKET_SIZE} slots
     * each. All slots are initialized to the empty-slot sentinel state.
     *
     * @param size total number of slots. Must be a power of two and at
     *             least {@value #BUCKET_SIZE}.
     */
    public TranspositionTable(int size) {
        if (!isPowerOfTwo(size) || size < BUCKET_SIZE) {
            throw new IllegalArgumentException("size must be power of two and at least " + BUCKET_SIZE);
        }

        this.hashSize = size / BUCKET_SIZE;
        this.memory = arena.allocate(size * ENTRY_SIZE, 8); // all bytes 0-initialized
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    @Override
    public void close() {
        arena.close();
    }

    /**
     * Lazy-initialized process-wide singleton with {@code DEFAULT_SIZE = 2^22}
     * slots. With the 32-byte record layout of this variant, that's
     * ~128 MiB of off-heap TT record storage.
     */
    public static synchronized TranspositionTable getDefaultInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TranspositionTable(DEFAULT_SIZE);
        }

        return INSTANCE;
    }

    /**
     * Advances the table generation used by the replacement heuristic.
     * Called once per root search so older entries gradually lose
     * priority.
     */
    public void nextGeneration() {
        generation++;
    }

    /** Zeros all slots. Called between games via UCI {@code ucinewgame}. */
    public void clear() {
        memory.fill((byte) 0);
    }

    /** Maps a Zobrist key to the start slot index of its bucket. */
    private int hash(final long hashKey) {
        return ((int) hashKey & (hashSize - 1)) * BUCKET_SIZE;
    }

    /**
     * Looks up the entry for the given Zobrist key. Scans the whole
     * bucket; on a hit, increments the slot's hitcount (used by the
     * admission gate later) and returns the positioned view. Returns
     * {@code null} if no slot in the bucket matches.
     *
     * <p>The returned view is the live positioned view — callers must not
     * retain it across a subsequent {@code get} or
     * {@link #put(long, int, int, Bound, int)} call, because either will
     * reposition it.
     */
    public TTEntryView get(final long hashKey) {
        final int bucketStart = hash(hashKey);
        final int bucketEnd = bucketStart + BUCKET_SIZE;

        for (int i = bucketStart; i < bucketEnd; i++) {
            currentEntryView.position(i);
            if (currentEntryView.getHashKey() == hashKey) {
                currentEntryView.incrementHitcount();
                return currentEntryView;
            }
        }

        return null;
    }

    /**
     * Inserts an entry into the bucket for {@code hashKey}. See the
     * class-level "Replacement strategy" section for the full decision
     * tree.
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
        final int protectedStart = bucketStart + PROTECTED_LANE_OFFSET;
        final int bucketEnd = bucketStart + BUCKET_SIZE;
        final int newReplacementScore = replacementScore(depth, bound, generation);

        // Phase 1: locate an existing slot for this key, if any.
        int existingIndex = -1;
        int existingHitcount = 0;
        int existingReplacementScore = 0;
        for (int i = bucketStart; i < bucketEnd; i++) {
            currentEntryView.position(i);
            if (currentEntryView.getHashKey() == hashKey) {
                existingIndex = i;
                existingHitcount = currentEntryView.getHitcount();
                existingReplacementScore = replacementScore(
                        currentEntryView.getDepth(), currentEntryView.getBound(),
                        currentEntryView.getGeneration());
                break;
            }
        }

        int targetIndex;
        int carriedHitcount;

        if (existingIndex == -1) {
            // No match — evict the cheapest slot in the recent lane.
            targetIndex = findRecentLaneReplacementIndex(bucketStart);
            carriedHitcount = 0;
        } else if (newReplacementScore < existingReplacementScore) {
            // Existing entry is better (deeper, more recent, EXACT) —
            // keep it and drop the new write entirely.
            return;
        } else if (existingIndex >= protectedStart) {
            // Same key already in the protected lane — overwrite in place,
            // preserving the accumulated hitcount.
            targetIndex = existingIndex;
            carriedHitcount = existingHitcount;
        } else if (qualifiesForProtectedLane(depth, bound, existingHitcount)) {
            // Same key in recent lane and the new entry qualifies for
            // promotion. Try to find a protected-lane slot cheap enough
            // to be replaced by this entry.
            int promotedIndex = findProtectedLaneReplacementIndex(bucketStart, newReplacementScore);
            if (promotedIndex >= 0) {
                // Promote: clear the old recent-lane slot, write into
                // the found protected-lane slot.
                currentEntryView.position(existingIndex);
                currentEntryView.clear();
                targetIndex = promotedIndex;
            } else {
                // No protected slot cheap enough — overwrite recent slot in place.
                targetIndex = existingIndex;
            }
            carriedHitcount = existingHitcount;
        } else {
            // Same key in recent lane but does not qualify for promotion —
            // overwrite in place.
            targetIndex = existingIndex;
            carriedHitcount = existingHitcount;
        }

        currentEntryView.position(targetIndex);
        currentEntryView.write(hashKey, depth, score, bound, bestMove, carriedHitcount);
    }

    /**
     * Replacement score used by the eviction heuristic:
     * {@code 4·depth + (EXACT ? 2 : 0) − age}. Larger is better (more
     * likely to survive).
     */
    private int replacementScore(final int depth, final Bound bound, final int entryGeneration) {
        final int exactBonus = bound == Bound.EXACT ? 2 : 0;
        final int agePenalty = generation - entryGeneration;

        return 4 * depth + exactBonus - agePenalty;
    }

    /**
     * Only entries that have already been visited more than once and are
     * either deep enough or EXACT are eligible for promotion into the
     * protected lane. The hitcount gate is the crucial guard that
     * differentiates this policy from the simpler two-tier admission
     * variant (which admits every deeper entry) — that simpler variant
     * measured as a clear regression against plain two-tier bucketing.
     */
    private static boolean qualifiesForProtectedLane(final int depth, final Bound bound, final int hitcount) {
        return hitcount > 1 && (depth > 1 || bound == Bound.EXACT);
    }

    /** Cheapest slot in the recent lane (slots 0..3 of the bucket). */
    private int findRecentLaneReplacementIndex(final int bucketStart) {
        final int endIndex = bucketStart + PROTECTED_LANE_OFFSET;
        int minIndex = bucketStart;
        currentEntryView.position(bucketStart);
        int minScore = replacementScore(currentEntryView.getDepth(),
                currentEntryView.getBound(), currentEntryView.getGeneration());

        for (int i = bucketStart + 1; i < endIndex; i++) {
            currentEntryView.position(i);
            int score = replacementScore(currentEntryView.getDepth(),
                    currentEntryView.getBound(), currentEntryView.getGeneration());
            if (score < minScore) {
                minScore = score;
                minIndex = i;
            }
        }

        return minIndex;
    }

    /**
     * Cheapest slot in the protected lane (slots 4..7). Returns the slot
     * index only if its replacement score is strictly less than the
     * incoming entry's score — i.e. only if the promotion is a net
     * improvement. Returns {@code -1} when the whole protected lane is
     * already at least as strong as the incoming entry.
     */
    private int findProtectedLaneReplacementIndex(final int bucketStart, final int incomingScore) {
        final int startIndex = bucketStart + PROTECTED_LANE_OFFSET;
        final int endIndex = bucketStart + BUCKET_SIZE;
        int minIndex = startIndex;
        currentEntryView.position(startIndex);
        int minScore = replacementScore(currentEntryView.getDepth(),
                currentEntryView.getBound(), currentEntryView.getGeneration());

        for (int i = startIndex + 1; i < endIndex; i++) {
            currentEntryView.position(i);
            int score = replacementScore(currentEntryView.getDepth(),
                    currentEntryView.getBound(), currentEntryView.getGeneration());
            if (score < minScore) {
                minScore = score;
                minIndex = i;
            }
        }

        return incomingScore > minScore ? minIndex : -1;
    }
}
