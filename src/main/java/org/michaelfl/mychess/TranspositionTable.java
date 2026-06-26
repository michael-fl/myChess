package org.michaelfl.mychess;

/**
 * Fixed-size bucketed transposition table caching per-position search results
 * keyed by Zobrist hash. Positions reached through different move orders are
 * evaluated only once: subsequent visits read the cached score (when the
 * stored depth is at least as deep as the new search) or use the stored best
 * move as a move-ordering hint.
 *
 * <h2>Lookup &amp; collision handling</h2>
 *
 * <p>The table contains {@code size} slots grouped into fixed
 * {@value #BUCKET_SIZE}-entry buckets. The hash function masks the low
 * {@code log2(size / BUCKET_SIZE)} bits of the 64-bit Zobrist hash and maps
 * every key to the start of one bucket. {@link #get(long)} scans only that
 * bucket and returns an entry only when the stored full {@code long hashKey}
 * matches the requested key exactly; the caller cannot read another
 * position's entry by accident. True 64-bit Zobrist collisions are
 * astronomically rare (~1 in 10^19 per pair) and treated as ignorable.
 *
 * <pre>
 * table
 * +----------+----------+----------+----------+----------+-----+
 * | bucket 0 | bucket 1 | bucket 2 | bucket 3 | bucket 4 | ... |
 * +----------+----------+----------+----------+----------+-----+
 *
 * one bucket (4 slots)
 * +--------+-------------+-------------+-------------+
 * | slot 0 | slot 1      | slot 2      | slot 3      |
 * | recent | protected   | protected   | protected   |
 * +--------+-------------+-------------+-------------+
 *      ^          ^
 *      |          +-- exact or deeper entries; admitted only if at least as
 *      |              valuable as the weakest protected slot
 *      +------------- shallow non-EXACT entries; always replace recent slot
 * </pre>
 *
 * <h2>Replacement strategy</h2>
 *
 * <p>Each bucket is split into a recent lane (slot 0) and a protected lane
 * (slots 1-3). Shallow non-EXACT entries go to the recent lane so the current
 * search front always leaves a move-ordering hint without displacing deeper
 * cached work. Exact or deeper entries go to the protected lane, where
 * replacement prefers evicting lower-depth entries and preserves
 * {@link Bound#EXACT} entries on equal depth. A candidate enters the protected
 * lane only if it is at least as valuable as the weakest protected entry;
 * otherwise it falls back to the recent lane.
 *
 * <p>{@link #put(long, int, int, Bound, int)} first searches the target bucket
 * for the same key. For a same-key update it keeps an existing
 * {@link Bound#EXACT} entry if and only if its stored depth is strictly
 * greater than the new entry's depth. A same-key entry stored in the recent
 * lane is promoted to the protected lane once the new value qualifies for
 * protection and passes the same admission check.
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
 * <p>Not thread-safe. {@link #put(long, int, int, Bound, int)} writes the
 * mutable fields of a {@link TTEntry} non-atomically; a concurrent
 * {@link #get(long)} could observe a half-updated entry. The engine runs a
 * single-threaded search executor so this is not an issue today; a future
 * lazy-SMP search would need atomic packed-int entries.
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
     * {@link TranspositionTable#put} overwrites the fields in place instead of
     * allocating a fresh object.
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
     *       {@code PositionSearch.scoreFromTT/scoreToTT}.</li>
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
     * Allocates a table with {@code size} slots. The size must be a power of
     * two and at least one full bucket so {@link #hash(long)} can use a low-bit
     * mask to compute the target bucket. All entries are initialized to the
     * empty-slot sentinel state ({@code hashKey == 0}).
     *
     * @param size number of slots. Must be a power of two and at least
     *             {@value #BUCKET_SIZE}; an
     *             {@link IllegalArgumentException} is thrown otherwise.
     */
    public TranspositionTable(int size) {
        if (!isPowerOfTwo(size) || size < BUCKET_SIZE) {
            throw new IllegalArgumentException("size must be power of two and at least " + BUCKET_SIZE);
        }

        this.size = size;
        this.hashSize = size / BUCKET_SIZE;
        this.table = new TTEntry[size];

        for (int i = 0; i < size; i++) {
            table[i] = new TTEntry();
            clearEntry(table[i]);
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
            clearEntry(table[i]);
        }
    }

    private int hash(final long hashKey) {
        return ((int) hashKey & (hashSize - 1)) * BUCKET_SIZE;
    }

    /**
     * Looks up the entry for the given Zobrist key. Scans the key's
     * {@value #BUCKET_SIZE}-entry bucket and returns a {@link TTEntry} only if
     * its stored {@code hashKey} matches the argument exactly (full 64-bit
     * identity). The returned object is the live slot — callers must not retain
     * it across {@link #put(long, int, int, Bound, int)} calls.
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
     * Inserts or overwrites the entry for {@code hashKey}. The key maps to one
     * fixed-size bucket; only slots in that bucket are inspected or replaced.
     *
     * <p>If the bucket already contains {@code hashKey}, the existing slot is
     * kept only when it is a strictly deeper {@link Bound#EXACT} entry. If the
     * new value qualifies for the protected lane and the old value is in the
     * recent lane, the entry is promoted only if it is at least as valuable as
     * the weakest protected entry; otherwise the recent slot is overwritten
     * with the new data. Conversely, a protected entry updated with a shallow
     * non-EXACT value is moved back to the recent lane.
     *
     * <p>If the bucket does not contain {@code hashKey}, shallow non-EXACT
     * entries replace the recent-lane slot. Exact or deeper entries replace the
     * weakest protected-lane slot only when they are at least as valuable;
     * otherwise they also replace the recent-lane slot.
     *
     * <p>Mate-score depth adjustment is the caller's responsibility:
     * pass the score already converted to "mate-in-N from this position"
     * (see {@code PositionSearch.scoreToTT}). Likewise the {@code depth}
     * argument is the {@code remainingDepth} at which the score was
     * searched, not the distance from the root.
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
        final int bucketStartIndex = hash(hashKey);

        if (updateExistingEntryIfPresent(bucketStartIndex, hashKey, depth, score, bound, bestMove)) {
            return;
        }

        final int replaceIndex = findReplacementIndex(bucketStartIndex, depth, bound);
        writeEntry(table[replaceIndex], hashKey, depth, score, bound, bestMove);
    }

    private boolean updateExistingEntryIfPresent(final int bucketStartIndex, final long hashKey, final int depth,
                                                 final int score, final Bound bound, final int bestMove) {
        final int endIndex = bucketStartIndex + BUCKET_SIZE;

        for (int index = bucketStartIndex; index < endIndex; index++ ) {
            final TTEntry entry = table[index];

            if (entry.hashKey == hashKey) {
                if (entry.depth > depth && entry.bound == Bound.EXACT) {
                    return true;  // keep deeper exact entry
                }
                final boolean useProtectedLane = qualifiesForProtectedLane(depth, bound);
                if (index == bucketStartIndex && useProtectedLane) {
                    final int replacementIndex = findReplacementIndex(bucketStartIndex, depth, bound);
                    if (replacementIndex != bucketStartIndex) {
                        writeEntry(table[replacementIndex], hashKey, depth, score, bound, bestMove);
                        clearEntry(entry);
                    } else {
                        writeEntry(entry, hashKey, depth, score, bound, bestMove);
                    }
                } else if (index != bucketStartIndex && !useProtectedLane) {
                    writeEntry(table[bucketStartIndex], hashKey, depth, score, bound, bestMove);
                    clearEntry(entry);
                } else {
                    writeEntry(entry, hashKey, depth, score, bound, bestMove);
                }
                return true;
            }
        }

        return false;
    }

    private static boolean qualifiesForProtectedLane(final int depth, final Bound bound) {
        return depth > 1 || bound == Bound.EXACT;
    }

    private int findReplacementIndex(final int bucketStartIndex, final int depth, final Bound bound) {
        if (!qualifiesForProtectedLane(depth, bound)) {
            return bucketStartIndex;
        }

        final int protectedIndex = findProtectedReplacementIndex(bucketStartIndex);
        return isAtLeastAsValuable(depth, bound, table[protectedIndex])
                ? protectedIndex
                : bucketStartIndex;
    }

    private int findProtectedReplacementIndex(final int bucketStartIndex) {
        int replaceIndex = bucketStartIndex + 1;
        final int endIndex = bucketStartIndex + BUCKET_SIZE;

        for (int index = replaceIndex + 1; index < endIndex; index++) {
            if (isBetterReplacementCandidate(table[index], table[replaceIndex])) {
                replaceIndex = index;
            }
        }

        return replaceIndex;
    }

    private static boolean isBetterReplacementCandidate(final TTEntry candidate, final TTEntry current) {
        return candidate.depth < current.depth
                || (current.bound == Bound.EXACT
                    && current.depth == candidate.depth
                    && candidate.bound != Bound.EXACT);
    }

    private static boolean isAtLeastAsValuable(final int depth, final Bound bound, final TTEntry current) {
        return depth > current.depth
                || (depth == current.depth
                    && (bound == Bound.EXACT || current.bound != Bound.EXACT));
    }

    private static void writeEntry(final TTEntry entry, final long hashKey, final int depth, final int score,
                                   final Bound bound, final int bestMove) {
        entry.hashKey = hashKey;
        entry.depth = depth;
        entry.score = score;
        entry.bound = bound;
        entry.bestMove = bestMove;
    }

    private static void clearEntry(final TTEntry entry) {
        entry.hashKey = 0;
        entry.depth = 0;
        entry.score = 0;
        entry.bound = Bound.EXACT;
        entry.bestMove = 0;
    }
}
