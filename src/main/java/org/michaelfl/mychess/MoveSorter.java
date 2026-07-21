package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.MoveSorterImpl;

/**
 * Sink for generated moves that returns them in search-friendly order. The
 * default implementation is {@link MoveSorterImpl}; instances are obtained
 * via {@link #defaultImplementation()}.
 *
 * @author Michael Fleischhauer
 */
public interface MoveSorter {
    /**
     * Prepares the sorter for a new sort pass. Clears all internal
     * buckets and remembers the two move-ordering hints:
     * <ul>
     *   <li>{@code pvMove} — packed-int move from the previous
     *       iteration's principal variation at this depth, or {@code 0}
     *       if none. Placed first in the sorted output (highest
     *       priority) provided the move generator actually produces it
     *       at this position.</li>
     *   <li>{@code ttMove} — packed-int best move from a transposition-
     *       table lookup at this position, or {@code 0} if none. Placed
     *       second (after pvMove) provided the move generator produces
     *       it. Different from pvMove because the TT entry can be from
     *       a transposed subtree, not necessarily on the iteration's
     *       PV path.</li>
     * </ul>
     *
     * <p>Implementations must protect against either hint being a move
     * the generator does not produce at this position (stale PV, true
     * Zobrist collision in the TT). The reference implementation
     * tracks per-hint "seen" flags inside {@link #addMove} and skips
     * the unseen hint at output time.
     */
    void reset(GameStatus gameStatus, Board board, int depth, int pvMove, int ttMove);
    void addMove(int move, int fromField, int toField, byte movingPiece, byte capturedPiece);
    Moves getSortedMoves();

    /**
     * Returns a fresh sorter for the full (main) search: a {@link MoveSorterImpl}
     * with its own empty killer-move table and no static exchange evaluation.
     * Captures are ordered by the material delta ({@code captured − mover}). For
     * the quiescence-search variant (SEE ordering and losing-capture pruning) use
     * {@link MoveSorterImpl#forQuiescenceSearch()}.
     */
    static MoveSorter defaultImplementation() {
        return new MoveSorterImpl();
    }
}
