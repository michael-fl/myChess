package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.Board;
import org.michaelfl.mychess.TranspositionTable;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.Arrays;

/**
 * Per-node state for one invocation of
 * {@code PositionSearch#alphaBetaSearch}: current
 * search depth, alpha-beta window, side-to-move sign, running material
 * counters, the working board, and a reference to the shared
 * principal-variation (PV) table. Non-PV fields are straightforward;
 * the PV table has a triangular layout that this class encapsulates.
 *
 * <h2>PV table layout</h2>
 *
 * <p>{@code pvTable} is a single flat {@code int[]} of length
 * {@code pvMaxLength * pvMaxLength}, where {@code pvMaxLength =
 * maxDepth + 1}. It is logically a square matrix indexed by depth:
 * row {@code d} starts at index {@code d * pvMaxLength} and holds the
 * PV that a node at depth {@code d} is building. Only the upper
 * triangle is ever filled with moves — row {@code d}'s diagonal slot
 * is at column {@code d}, and slots left of the diagonal stay empty.
 *
 * <p>The example below shows a fully populated table for
 * {@code maxDepth = 4} (so {@code pvMaxLength = 5}, five columns,
 * five rows). Moves {@code M0 .. M3} are the four PV plies. The
 * rightmost column (column 4) is never written by anyone and so
 * always reads as {@code 0}; that final {@code 0} serves as a
 * zero-terminator for PV-consuming code that walks the PV until
 * it hits a {@code 0}. Row 4 corresponds to the leaf (depth
 * {@code == maxDepth}) — the leaf takes the static-eval shortcut
 * and never writes its own diagonal, so row 4 is empty too.
 *
 * <pre>{@code
 *   col          0    1    2    3    4
 *             +----+----+----+----+----+
 *   row 0     | M0 | M1 | M2 | M3 |  0 |   <- root PV: 4 moves + 0-terminator
 *             +----+----+----+----+----+
 *   row 1     |    | M1 | M2 | M3 |  0 |
 *             +----+----+----+----+----+
 *   row 2     |    |    | M2 | M3 |  0 |
 *             +----+----+----+----+----+
 *   row 3     |    |    |    | M3 |  0 |   <- only this depth's diagonal move
 *             +----+----+----+----+----+
 *   row 4     |    |    |    |    |  0 |   <- leaf row: never written
 *             +----+----+----+----+----+
 * }</pre>
 *
 * <p>The diagonal slot of row {@code d} — column {@code d}, addressed
 * by {@link #pvIndex()} — is the move currently being considered at
 * depth {@code d}. It is rewritten at the top of every move-loop
 * iteration in {@code alphaBetaSearchMain}. The slots
 * {@code d+1 .. maxDepth-1} to the right of the diagonal hold the
 * sub-PV continuing from that candidate; deeper nodes fill them via
 * {@link #copyUpPV()}. Slots to the left of the diagonal are written
 * by shallower nodes (or, for row 0, by the root's direct
 * {@code pvTable[0] = move}).
 *
 * <h2>PV propagation</h2>
 *
 * <p>Two operations move data between rows:
 *
 * <ul>
 *   <li>{@link #copyUpPV()} — "I found a good continuation": copies
 *       this depth's row slots {@code d .. maxDepth} (the diagonal
 *       move plus its sub-PV) into the parent's row slots
 *       {@code d .. maxDepth}. Called whenever a recursive child
 *       result improves the parent's best so far.</li>
 *   <li>{@link #truncateParentPv()} — "I have no continuation":
 *       writes zeros into the same parent-row range. Called at
 *       terminal returns (mate / stalemate / draw / leaf static
 *       eval). Without this, the parent's later {@code copyUpPV}
 *       would carry forward stale slots from an earlier sibling's
 *       deeper exploration.</li>
 * </ul>
 *
 * <p>By induction up the chain, row 0 ends up containing the full
 * principal variation {@code [M0, M1, ..., M_{maxDepth-1}]} when the
 * search returns to the root.
 *
 * <h2>Why the diagonal layout?</h2>
 *
 * <p>Storing each row from column 0 instead of column {@code d}
 * would also work. The diagonal layout chosen here has a useful
 * property: the source range of {@code copyUpPV} (row {@code d},
 * columns {@code d..maxDepth}) sits adjacent in memory to its
 * destination (row {@code d-1}, columns {@code d..maxDepth}) —
 * same column range, stride {@code pvMaxLength}. {@code copyUpPV}
 * is therefore a single {@link System#arraycopy} with no per-element
 * offset arithmetic.
 *
 * <h2>Non-PV fields</h2>
 *
 * <ul>
 *   <li>{@code depth} — current ply, starting at 1 for the first
 *       recursion below the root. The root's own loop in
 *       {@code PositionSearch#calculateNextMove}
 *       does not create a {@code SearchNodeContext}; it writes
 *       {@code pvTable[0]} directly.</li>
 *   <li>{@code maxDepth} — the iterative-deepening iteration's target
 *       depth. The leaf shortcut in {@code alphaBetaSearchMain} fires
 *       at {@code depth == maxDepth}.</li>
 *   <li>{@code bestKnownPath} — the previous iteration's PV, threaded
 *       through so each depth can take its best-known move from
 *       {@code bestKnownPath.path()[depth]} and pass it to
 *       {@code MoveGenerator} as the head of the move-ordering list.</li>
 *   <li>{@code weightFactor} — {@code +1} for white-to-move,
 *       {@code -1} for black; negated at each child recursion so the
 *       search runs in pure negamax form.</li>
 *   <li>{@code materialWeight} — cumulative material from the
 *       side-to-move's perspective. Used by the leaf shortcut when
 *       {@code materialDelta} exceeds
 *       {@link PositionSearch#EVALUATE_MATERIAL_ONLY_THRESHOLD}.</li>
 *   <li>{@code materialDelta} — running material change since the
 *       search root; gates the material-only leaf shortcut.</li>
 *   <li>{@code workingBoard} — shared mutable board. One per
 *       search; every recursion calls {@code makeMove} /
 *       {@code revertMove} on it.</li>
 *   <li>{@code pvTable} — see above. Shared across every
 *       {@code SearchNodeContext} within one deepening iteration.</li>
 *   <li>{@code lastMoveWasNull} — set to {@code true} only in the
 *       recursive descent right after a null-move push, so the
 *       null-move-pruning gate at the child node can refuse to fire
 *       twice in a row. All other paths default to {@code false} via
 *       the convenience constructor.</li>
 * </ul>
 *
 * <p>The alpha-beta window ({@code alphaWeight} / {@code betaWeight})
 * and the TT-supplied move-ordering hint ({@code ttMove}) used to be
 * record fields in earlier iterations. They have been promoted to
 * plain method parameters on {@code alphaBetaSearch} /
 * {@code alphaBetaSearchMain} so that the per-node alpha/beta narrowing
 * does not require allocating a new record at every recursion — the
 * record state ({@code workingBoard}, {@code pvTable}, etc.) is shared,
 * the window is per-call.
 */
@SuppressWarnings("java:S6218")
public record SearchNodeContext(int depth, int maxDepth, MoveAndWeight bestKnownPath,
                                int weightFactor,
                                int materialWeight, int materialDelta,
                                Board workingBoard, int[] pvTable, int pvMaxLength,
                                boolean lastMoveWasNull) {

    /**
     * Convenience constructor that defaults {@code lastMoveWasNull} to
     * {@code false}. Every path that reaches this constructor is a
     * real (non-null) move context; only the null-move descent uses
     * the primary constructor with {@code lastMoveWasNull = true} to
     * prevent consecutive null moves.
     */
    public SearchNodeContext(int depth, int maxDepth, MoveAndWeight bestKnownPath,
                      int weightFactor,
                      int materialWeight, int materialDelta,
                      Board workingBoard, int[] pvTable, int pvMaxLength) {
        this(depth, maxDepth, bestKnownPath, weightFactor, materialWeight, materialDelta, workingBoard, pvTable, pvMaxLength, false);
    }

    /**
     * Plies still to search below this node ({@code maxDepth - depth}).
     * Used as the TT lookup-depth comparison: an entry stored at
     * remaining-depth {@code r_stored} is only usable for a score
     * read when {@code r_stored >= this.remainingDepth()}.
     */
    public int remainingDepth() {
        return maxDepth - depth;
    }

    // pvMaxLength is the PV-table's row/column stride (= the root's maxDepth + 1,
    // with one extra slot past the last move as a zero-terminator). It is carried
    // as its own record component rather than derived from maxDepth: the null-move
    // descent reduces maxDepth, and if the stride followed maxDepth the reduced
    // sub-tree would compute pvIndex()/copyUpPV() with a smaller stride and write
    // PV slots at the wrong offsets in the shared pvTable — a stale-slot /
    // illegal-PV defect (see IllegalPvRegressionTest). Keeping it independent of
    // maxDepth is the fix. The record's generated pvMaxLength() accessor returns it.

    /**
     * Flat index of this depth's diagonal slot: column {@code d} of
     * row {@code d}, where {@code d == this.depth}. The move
     * currently being tried at this depth is stored here.
     */
    public int pvIndex() {
        return depth * pvMaxLength() + depth;
    }

    /**
     * Flat index of the slot in the parent's row that
     * {@link #copyUpPV()} and {@link #truncateParentPv()} write
     * into: column {@code d} of row {@code d-1}, i.e. the slot
     * directly to the right of the parent's own diagonal entry.
     */
    public int pvParentIndex() {
        return (depth - 1) * pvMaxLength() + depth;
    }

    /**
     * Propagate "my move plus its sub-PV" one level up. Copies
     * row {@code d} slots {@code d .. maxDepth} into row
     * {@code d-1} at the same column range, so that the parent's
     * PV reads {@code [parent's diagonal move, my diagonal move,
     * my sub-PV, ...]}.
     *
     * <p>Example, {@code copyUpPV()} called at depth 2 of a depth-4
     * search (so {@code maxDepth=4}, {@code pvMaxLength=5}). The
     * trailing column 4 stays at the {@code 0} terminator throughout:
     *
     * <pre>{@code
     *   col           0    1    2    3    4
     *               +----+----+----+----+----+
     *   row 1 before |    | M1 | ?? | ?? |  0 |   <- parent's M1 in slot 1; slots 2..3 not
     *               +----+----+----+----+----+      yet set in this iteration
     *   row 2       |    |    | M2 | M3 |  0 |   <- depth 2's M2 in slot 2; M3 already
     *               +----+----+----+----+----+      filled in by an earlier copyUpPV from
     *                                              depth 3
     *
     *                       || copy row 2, slots 2..4 --> row 1, slots 2..4
     *                       v
     *
     *               +----+----+----+----+----+
     *   row 1 after |    | M1 | M2 | M3 |  0 |   <- row 1 now reads as the PV
     *               +----+----+----+----+----+      [M1, M2, M3] from depth 1's perspective
     * }</pre>
     *
     * <p>Called from {@code alphaBetaSearchMain}'s move loop whenever
     * the current child move improves the parent's best result so
     * far (and on a β-cutoff before the early return).
     */
    public void copyUpPV() {
        System.arraycopy(pvTable, pvIndex(), pvTable, pvParentIndex(), pvMaxLength() - depth);
    }

    /**
     * Dual of {@link #copyUpPV()}: tell the parent "from my depth
     * onwards there is no PV continuation" by writing zeros into the
     * same parent-row range that {@code copyUpPV} would write into.
     *
     * <p>Required at every return path that produces a parent-
     * acceptable (non-ILLEGAL) weight without further moves to
     * propagate: terminal mate / stalemate at the move-loop exit,
     * draw by 50-move / threefold-repetition, and the leaf
     * static-eval return at {@code depth == maxDepth}. Without it,
     * the parent's subsequent {@code copyUpPV} carries forward
     * stale slots written by an earlier sibling's deeper
     * exploration — the defect reproduced by
     * {@code IllegalPvRegressionTest}'s test02 cases.
     *
     * <p>Example, {@code truncateParentPv()} called at depth 2 of a
     * depth-4 search (so {@code maxDepth=4}, {@code pvMaxLength=5}).
     * The trailing column 4 stays at the {@code 0} terminator
     * throughout:
     *
     * <pre>{@code
     *   col           0    1    2    3    4
     *               +----+----+----+----+----+
     *   row 1 before |    | M1 | M2'| M3'|  0 |   <- parent has stale slots 2..3
     *               +----+----+----+----+----+      from an earlier sibling's
     *                                              copyUpPV at this depth
     *   row 2       |    |    | ?? | ?? |  ? |   <- depth 2 returns terminal
     *               +----+----+----+----+----+      (no move to propagate)
     *
     *                       || zero row 1, slots 2..4
     *                       v
     *
     *               +----+----+----+----+----+
     *   row 1 after |    | M1 |  0 |  0 |  0 |   <- parent's row reads as the PV
     *               +----+----+----+----+----+      [M1] only; no stale tail
     * }</pre>
     */
    public void truncateParentPv() {
        final int fromIndex = pvParentIndex();
        final int toIndexExclusive = depth * pvMaxLength();
        Arrays.fill(pvTable, fromIndex, toIndexExclusive, 0);
    }

    /**
     * PV-table maintenance for the transposition-table early-return
     * paths in {@code alphaBetaSearchPre}. When a TT lookup serves the
     * result for this node (EXACT bound, or LOWER/UPPER cutoff with
     * {@code alpha >= beta}), the recursive descent that would
     * normally fill row {@code d}'s slots {@code d..maxDepth} via
     * {@link #copyUpPV()} from the child never happens. Without this
     * helper, the parent's subsequent {@link #copyUpPV()} would
     * carry the row's stale slots — left over from an earlier
     * sibling's exploration — up into its own row, and the iteration
     * would emit a principal variation that includes moves which are
     * not legal at the positions the PV claims to reach.
     *
     * <p>This is the same hazard {@link #truncateParentPv()} addresses
     * for terminal returns (mate/stalemate/draw/leaf eval), specialized
     * for the TT case where we DO have a non-zero move to propagate
     * (the TT entry's stored best move). Concretely:
     *
     * <ol>
     *   <li>Write {@code ttMove} into this depth's diagonal slot
     *       (row {@code d}, column {@code d}), matching what an
     *       in-tree search would have written via
     *       {@code pvTable[pvIndex] = move} in the move loop.</li>
     *   <li>Zero this depth's row past the diagonal (columns
     *       {@code d+1..maxDepth}) — no further continuation is
     *       known beyond the TT-stored best move.</li>
     *   <li>Propagate the result one level up via {@link #copyUpPV()},
     *       so the parent's row reads
     *       {@code [..., parent's move, ttMove, 0, 0, ...]} after the
     *       parent picks this child as its best.</li>
     * </ol>
     *
     * <p>Without this propagation, the next iterative-deepening pass
     * picks up the stale PV as its {@code bestKnownPath}, hands a
     * not-legal move to {@code MoveGenerator} as the move-ordering
     * hint, and (depending on the MoveSorter's tolerance) either
     * trips the sorter's "pvMove not produced" diagnostic skip or
     * crashes inside {@code Board.makeMove}.
     *
     * <p>Example, {@code writeTTCachedPv(TT)} called at depth 2 of a
     * depth-4 search (so {@code maxDepth=4}, {@code pvMaxLength=5})
     * after a TT lookup serves the result for this node:
     *
     * <pre>{@code
     *   col           0    1    2    3    4
     *               +----+----+----+----+----+
     *   row 1 before |    | M1 | M2'| M3'|  0 |   <- parent's stale slots
     *               +----+----+----+----+----+
     *   row 2 before |    |    | M2'| M3'|  0 |   <- own row also stale
     *               +----+----+----+----+----+      (from a previous visit)
     *
     *                       || step 1: write TT-bestMove to own
     *                       ||         diagonal (row 2, col 2)
     *                       v
     *
     *               +----+----+----+----+----+
     *   row 2 step1 |    |    | TT | M3'|  0 |   <- diagonal correct, tail stale
     *               +----+----+----+----+----+
     *
     *                       || step 2: zero own row past the diagonal
     *                       ||         (row 2, cols 3..4)
     *                       v
     *
     *               +----+----+----+----+----+
     *   row 2 step2 |    |    | TT |  0 |  0 |
     *               +----+----+----+----+----+
     *
     *                       || step 3: copyUpPV — row 2, slots 2..4
     *                       ||         -> row 1, slots 2..4
     *                       v
     *
     *               +----+----+----+----+----+
     *   row 1 after |    | M1 | TT |  0 |  0 |   <- parent's row now reads
     *               +----+----+----+----+----+      [M1, TT] with no tail
     * }</pre>
     *
     * @param ttMove the {@link TranspositionTable.TTEntryView#getBestMove()}
     *               of the entry being returned. Must be non-zero; the
     *               TT only stores the best move alongside a real score.
     */
    public void writeTTCachedPv(int ttMove) {
        pvTable[pvIndex()] = ttMove;
        Arrays.fill(pvTable, pvIndex() + 1, (depth + 1) * pvMaxLength(), 0);
        copyUpPV();
    }

}
