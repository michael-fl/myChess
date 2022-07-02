package org.michaelfl.mychess;

/**
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
