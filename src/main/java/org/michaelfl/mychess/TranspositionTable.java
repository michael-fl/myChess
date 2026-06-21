package org.michaelfl.mychess;

import java.util.Arrays;

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
 * <h2>Replacement strategy</h2>
 *
 * <p>{@link #put(long, int, int, Bound, int)} first searches the target
 * bucket for the same key. For a same-key update it keeps an existing
 * {@link Bound#EXACT} entry if and only if its stored depth is strictly
 * greater than the new entry's depth, refreshing the entry's generation so a
 * useful deep hit is not aged out immediately. Otherwise the same-key slot is
 * overwritten.
 *
 * <p>For a new key, {@code put} replaces the slot with the lowest replacement
 * score: deeper entries are favored, {@link Bound#EXACT} entries get a small
 * bonus, and older entries are penalized by generation age. The goal is to
 * keep the bucket's most useful recent search information while still making
 * stale or shallow entries easy to evict.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>One instance per engine in production (wired via
 * {@link EngineConfig#getTranspositionTable()}). Tests use isolated
 * instances obtained from {@code TestSupport.createTestTT()} to avoid
 * cross-test pollution. {@link #clear()} resets all entries to the
 * empty sentinel (hashKey 0, depth 0, score 0, bound EXACT, bestMove
 * 0, generation 0) and should be called whenever the engine starts a new game
 * (UCI {@code ucinewgame}) so that scores from the previous game do not
 * influence the new one.
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
     *   <li>{@code generation} — root-search generation in which the entry was
     *       last written or refreshed. Used only by the replacement heuristic
     *       to age out stale entries inside a bucket.</li>
     * </ul>
     */
    public static final class TTEntry {
        private long hashKey;
        private int depth;
        private int score;
        private Bound bound;
        private int bestMove;
        private int generation;

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
    private int generation;

    /**
     * Allocates a table with {@code size} slots. The size must be a power of
     * two so {@link #hash(long)} can use a low-bit mask to compute the target
     * bucket. All entries are initialized to the empty-slot sentinel state
     * ({@code hashKey == 0}).
     *
     * @param size number of slots. Must be a power of two; an
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
            entry.generation = 0;
        }

        generation = 0;
    }

    /**
     * Advances the table generation used by the replacement heuristic. Called
     * once per root search so older entries gradually lose priority inside
     * their bucket.
     */
    public void nextGeneration() {
        generation++;
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
     * kept only when it is a strictly deeper {@link Bound#EXACT} entry. In that
     * case only its generation is refreshed. Otherwise the same slot is
     * overwritten with the new data.
     *
     * <p>If the bucket does not contain {@code hashKey}, the replacement slot
     * is the entry with the lowest replacement score. The score favors greater
     * search depth, gives a small bonus to {@link Bound#EXACT} entries, and
     * penalizes entries from older generations.
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
        int index = hash(hashKey);
        final int endIndex = index + BUCKET_SIZE;
        int replaceIndex = index;

        for (; index < endIndex; index++ ) {
            final TTEntry entry = table[index];

            if (entry.hashKey == hashKey) {
                if (entry.depth > depth && entry.bound == Bound.EXACT) {
                    entry.generation = generation;
                    return;  // keep deeper exact entry
                }
                replaceIndex = index;
                break;
            }

            if (replacementScore(entry) < replacementScore(table[replaceIndex])) {
                replaceIndex = index;
            }
        }

        TTEntry entry = table[replaceIndex];
        entry.hashKey = hashKey;
        entry.depth = depth;
        entry.score = score;
        entry.bound = bound;
        entry.bestMove = bestMove;
        entry.generation = generation;
    }

    private int replacementScore(final TTEntry entry) {
        final int exactBonus = entry.bound == Bound.EXACT ? 2 : 0;
        final int agePenalty = generation - entry.generation;

        return 4 * entry.depth + exactBonus - agePenalty;
    }
}
