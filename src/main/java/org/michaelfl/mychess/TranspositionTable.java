package org.michaelfl.mychess;

import java.util.Arrays;

/**
 * Fixed-size open-addressed transposition table caching per-position search
 * results keyed by Zobrist hash. Positions reached through different move
 * orders are evaluated only once: subsequent visits read the cached score
 * (when the stored depth is at least as deep as the new search) or use the
 * stored best move as a move-ordering hint.
 *
 * <h2>Lookup &amp; collision handling</h2>
 *
 * <p>The hash function masks the low {@code log2(size)} bits of the
 * 64-bit Zobrist hash, mapping every key to one of {@code size} buckets.
 * On a hash-bucket collision (two positions land on the same slot but
 * have different Zobrist keys), {@link #get(long)} returns {@code null}
 * because of an explicit identity check on the full {@code long} hashKey;
 * the caller cannot read another position's entry by accident. True
 * 64-bit Zobrist collisions are astronomically rare (~1 in 10^19 per
 * pair) and treated as ignorable.
 *
 * <h2>Replacement strategy</h2>
 *
 * <p>{@link #put(long, int, int, Bound, int)} keeps an existing
 * {@link Bound#EXACT} entry if and only if its stored depth is strictly
 * greater than the new entry's depth. Anything else (different hashKey,
 * same key with shallower or equal depth, non-EXACT bound) is overwritten.
 * The depth-preferred-EXACT policy avoids losing a deeply searched score
 * to a shallow re-visit while keeping the eviction logic single-line.
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

    private static final int DEFAULT_SIZE = 1 << 20;

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
    private final TTEntry[] table;

    /**
     * Allocates a table with {@code size} entries. The size must be a
     * power of two so {@link #hash(long)} can mask with {@code size - 1}
     * to compute the bucket index. All entries are initialised to the
     * empty-slot sentinel state ({@code hashKey == 0}).
     *
     * @param size number of entries (rows). Must be a power of two; an
     *             {@link IllegalArgumentException} is thrown otherwise.
     */
    public TranspositionTable(int size) {
        if (!isPowerOfTwo(size)) {
            throw new IllegalArgumentException("size must be power of two");
        }

        this.size = size;
        this.table = new TTEntry[size];

        for (int i = 0; i < size; i++) {
            table[i] = new TTEntry();
        }
    }

    private static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    /**
     * Lazy-initialised process-wide singleton with {@code DEFAULT_SIZE = 2^20}
     * entries (~50 MB). Used by {@link EngineConfig.Builder#build()} when no
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

    private int hash(final long hashKey) {
        return (int) hashKey & (size - 1);
    }

    /**
     * Looks up the entry for the given Zobrist key. Returns the
     * {@link TTEntry} only if its stored {@code hashKey} matches the
     * argument exactly (full 64-bit identity); on a hash-bucket
     * collision with a different key, returns {@code null}. The
     * returned object is the live slot — callers must not retain it
     * across {@link #put(long, int, int, Bound, int)} calls.
     */
    public TTEntry get(final long hashKey) {
        var entry = table[hash(hashKey)];

        return hashKey == entry.hashKey ? entry : null;
    }

    /**
     * Inserts or overwrites the entry for {@code hashKey}. The existing
     * slot is kept only if it represents the same position with a
     * strictly deeper {@link Bound#EXACT} cached score — that is the
     * one case where overwriting would lose strictly more information
     * than it gains. Every other case (different hashKey, shallower
     * stored depth, non-EXACT bound) is overwritten unconditionally.
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
        var entry = table[hash(hashKey)];

        if (entry.hashKey == hashKey && entry.depth > depth && entry.bound == Bound.EXACT) {
            return;  // keep deeper exact entry
        }

        entry.hashKey = hashKey;
        entry.depth = depth;
        entry.score = score;
        entry.bound = bound;
        entry.bestMove = bestMove;
    }
}
