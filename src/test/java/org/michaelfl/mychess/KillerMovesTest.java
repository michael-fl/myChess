package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

class KillerMovesTest {

    @Test
    void testSortDescending() {
        int n = 100;
        short[] moves = new short[n];
        int[] counts = new int[n];
        KillerMoves.sortDescending(moves, counts);
    }

    @Test
    void testFindTopMoves() {
        int n = 10;
        int[] moveCounts = new int[Short.MAX_VALUE];
        KillerMoves.findTopMoves(n, moveCounts);
    }

}
