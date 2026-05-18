package org.michaelfl.mychess;

/**
 * Killer-move heuristic table: stores up to two non-capturing moves per
 * search depth that previously caused a beta cutoff. Queried by
 * {@link org.michaelfl.mychess.engines.MoveSorterImpl} to elevate them
 * ahead of other quiet moves.
 *
 * @author Michael Fleischhauer
 */
public final class KillerMoves {

    private final int[][] moves = new int[50][2];

    public boolean isKillerMove(int move, int depth) {
        final var m = moves[depth];
        return m[0] == move || m[1] == move;
    }

    public void addMove(int move, int depth) {
        final var m = moves[depth];
        if (m[0] != move) {
            m[1] = m[0];
            m[0] = move;
        }
    }
}
