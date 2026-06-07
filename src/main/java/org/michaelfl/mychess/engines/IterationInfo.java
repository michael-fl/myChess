package org.michaelfl.mychess.engines;

/**
 * Per-iteration snapshot fired by {@link PositionSearch} during iterative
 * deepening. Used by external observers (e.g. the UCI handler) to emit
 * {@code info depth N nodes M time T score cp X pv ...} lines as the search
 * makes progress.
 *
 * @param generation monotonically increasing id of the {@code Game} this
 *                   iteration belongs to (see {@link org.michaelfl.mychess.Game#getGeneration()}).
 *                   Observers compare this against the current game's generation
 *                   to drop events from an old search that survived past
 *                   {@code ucinewgame} or a new {@code go} — without this guard,
 *                   stale iterations could emit {@code info pv …} lines or
 *                   {@code bestmove} fallbacks against a board that the search
 *                   no longer corresponds to.
 * @param depth  search depth (in plies) that has just completed
 * @param nodes  cumulative position count since the search started
 * @param timeMs wall-clock milliseconds elapsed since the search started
 * @param weight raw evaluation in pawn units (positive = side-to-move advantage),
 *               can encode a checkmate score; see {@link org.michaelfl.mychess.WeightingFunction}
 * @param pv   principal variation as packed-int moves (length = depth typically)
 *
 * @author Michael Fleischhauer
 */
// equals/hashCode are intentionally left as record defaults (shallow on pv).
// IterationInfo instances are fire-and-forget: created by the search loop,
// passed to a Consumer, fields read for UCI info-line emission, then discarded.
// They are never compared, never used as a Map key, never put in a Set, so the
// generic Sonar rule java:S6218 does not apply here.
@SuppressWarnings("java:S6218")
public record IterationInfo(int generation, int depth, long nodes, long timeMs, float weight, int[] pv) {}
