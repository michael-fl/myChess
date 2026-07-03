package org.michaelfl.mychess;

/**
 * Fixed-size open-addressed transposition table caching per-position search
 * results keyed by Zobrist hash. Positions reached through different move
 * orders are evaluated only once: subsequent visits read the cached score
 * (when the stored depth is at least as deep as the new search) or use the
 * stored best move as a move-ordering hint.
 *
 * <h2>Bucket layout &amp; lookup</h2>
 *
 * <p>Entries are grouped into fixed-size <em>buckets</em> of
 * {@value #BUCKET_SIZE} slots each. The hash function masks the low
 * {@code log2(size / BUCKET_SIZE)} bits of the 64-bit Zobrist key to
 * pick a bucket, then {@link #get(long)} scans all {@value #BUCKET_SIZE}
 * slots of that bucket linearly and returns the entry whose full 64-bit
 * {@code hashKey} matches exactly. Up to {@value #BUCKET_SIZE} distinct
 * keys that hash to the same bucket therefore coexist without evicting
 * each other; only a bucket-full-of-different-keys forces a replacement
 * decision. On no match, {@link #get(long)} returns {@code null}. True
 * 64-bit Zobrist collisions between distinct positions are astronomically
 * rare (~1 in 10^19 per pair) and treated as ignorable.
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
 * <p>One instance per engine in production (wired via
 * {@link EngineConfig#getTranspositionTable()}). Tests use isolated
 * instances obtained from {@code TestSupport.createTestTT()} to avoid
 * cross-test pollution. {@link #clear()} resets all entries to the
 * empty sentinel (hashKey 0, depth 0, score 0, bound EXACT, bestMove
 * 0) and should be called whenever the engine starts a new game
 * (UCI {@code ucinewgame}) so that scores from the previous game do
 * not influence the new one.
 *
 * <h2>Thread safety</h2>
 *
 * <p>Not thread-safe. {@link #put(long, int, int, Bound, int)} writes
 * five fields of a {@link TTEntry} non-atomically; a concurrent
 * {@link #get(long)} could observe a half-updated entry. The engine
 * runs a single-threaded search executor so this is not an issue
 * today; a future lazy-SMP search would need atomic packed-int
 * entries.
 *
 * @author Michael Fleischhauer
 */
public final class TranspositionTable {

    private static final int DEFAULT_SIZE = 1 << 22;
    private static final int BUCKET_SIZE = 4;

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
     * One slot in the table. Mutable to avoid garbage during the search;
     * {@link TranspositionTable#put} overwrites the five fields in place
     * instead of allocating a fresh object.
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
    public static final class TTEntry {
        private long hashKey;
        private int depth;
        private int score;
        private Bound bound;
        private int bestMove;

        /** Full 64-bit Zobrist key of the stored position. */
        public long getHashKey() {
            return hashKey;
        }

        /** {@code remainingDepth} at which this entry was searched. */
        public int getDepth() {
            return depth;
        }

        /** Cached score in centi-pawns, relative to the cached position. */
        public int getScore() {
            return score;
        }

        /** Score-bound classification — see {@link Bound}. */
        public Bound getBound() {
            return bound;
        }

        /** Packed-int best move from the cached search. */
        public int getBestMove() {
            return bestMove;
        }
    }

    private final int size;
    private final int hashSize;
    private final TTEntry[] table;

    /**
     * Allocates a table with {@code size} total slots, laid out as
     * {@code size / BUCKET_SIZE} buckets of {@value #BUCKET_SIZE} slots
     * each. The size must be a power of two so that {@code hashSize =
     * size / BUCKET_SIZE} is also a power of two and {@link #hash(long)}
     * can pick a bucket by masking with {@code hashSize - 1}. All entries
     * are initialized to the empty-slot sentinel state
     * ({@code hashKey == 0}).
     *
     * @param size total number of slots. Must be a power of two (and,
     *             implicitly, at least {@value #BUCKET_SIZE}); an
     *             {@link IllegalArgumentException} is thrown otherwise.
     */
    public TranspositionTable(int size) {
        if (!isPowerOfTwo(size)) {
            throw new IllegalArgumentException("size must be power of two");
        }

        this.size = size;
        this.hashSize = size / BUCKET_SIZE;
        this.table = new TTEntry[size];

        for (int i = 0; i < size; i++) {
            table[i] = new TTEntry();
        }
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    /**
     * Lazy-initialized process-wide singleton with {@code DEFAULT_SIZE = 2^22}
     * entries (~200 MB). Used by {@link EngineConfig.Builder#build()} when no
     * explicit {@link TranspositionTable} is set, and by the UCI / REPL
     * code paths that want one shared cache per JVM. Production engines
     * normally pick this one up; tests must create their own via
     * {@code TestSupport.createTestTT()} to stay isolated.
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
        for (int i = 0; i < size; i++) {
            var entry = table[i];
            entry.hashKey = 0;
            entry.depth = 0;
            entry.score = 0;
            entry.bound = Bound.EXACT;
            entry.bestMove = 0;
        }
    }

    /**
     * Maps a Zobrist key to the start index of its bucket in
     * {@link #table}: takes the low {@code log2(hashSize)} bits of the
     * key to pick a bucket ordinal, then multiplies by
     * {@value #BUCKET_SIZE} to get the flat-array offset of the bucket's
     * first slot. The bucket then spans indices {@code [return,
     * return + BUCKET_SIZE)}.
     */
    private int hash(final long hashKey) {
        return ((int) hashKey & (hashSize - 1)) * BUCKET_SIZE;
    }

    /**
     * Looks up the entry for the given Zobrist key. Scans all
     * {@value #BUCKET_SIZE} slots of the target bucket linearly and
     * returns the first slot whose stored {@code hashKey} matches the
     * argument exactly (full 64-bit identity). If none of the
     * {@value #BUCKET_SIZE} slots holds this key, returns {@code null}.
     * The returned object is the live slot — callers must not retain it
     * across {@link #put(long, int, int, Bound, int)} calls, because a
     * subsequent put may overwrite its fields.
     */
    public TTEntry get(final long hashKey) {
        final int startIndex = hash(hashKey);
        final int endIndex = startIndex + BUCKET_SIZE;

        for (int i = startIndex; i < endIndex; i++ ) {
            if (table[i].hashKey == hashKey) {
                return table[i];
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
     *   <li><b>Key not in bucket.</b> The bucket is scanned during the
     *       same loop to track the eviction candidate: the slot with
     *       the lowest {@code depth}, breaking ties by preferring to
     *       evict a non-EXACT slot over an EXACT one of equal depth.
     *       That slot is then overwritten with the new fields.</li>
     * </ul>
     *
     * <p>Mate-score depth adjustment is the caller's responsibility:
     * pass the score already converted to "mate-in-N from this position"
     * (see {@link WeightingFunction#scoreToTT(int, int)}). Likewise, the
     * {@code depth} argument is the {@code remainingDepth} at which the
     * score was searched, not the distance from the root.
     *
     * @param hashKey  full 64-bit Zobrist key of the position
     * @param depth    remaining search depth at which {@code score} was
     *                 obtained
     * @param score    centi-pawn score relative to the stored position
     * @param bound    {@link Bound#EXACT} / {@link Bound#LOWER} / {@link Bound#UPPER}
     * @param bestMove packed-int move that produced {@code score}, or 0
     *                 if none is meaningful (terminal nodes)
     */
    public void put(final long hashKey, final int depth, final int score, final Bound bound, final int bestMove) {
        int index = hash(hashKey);
        final int endIndex = index + BUCKET_SIZE;
        int replaceIndex = index;

        for (; index < endIndex; index++ ) {
            final TTEntry entry = table[index];

            if (entry.hashKey == hashKey) {
                if (entry.depth > depth && entry.bound == Bound.EXACT) {
                    return;  // keep deeper exact entry
                }
                replaceIndex = index;
                break;
            }
            if (entry.depth < table[replaceIndex].depth
                    || (table[replaceIndex].bound == Bound.EXACT
                        && table[replaceIndex].depth == entry.depth
                        && entry.bound != Bound.EXACT)) {
                replaceIndex = index;
            }
        }

        TTEntry entry = table[replaceIndex];
        entry.hashKey = hashKey;
        entry.depth = depth;
        entry.score = score;
        entry.bound = bound;
        entry.bestMove = bestMove;
    }
}
