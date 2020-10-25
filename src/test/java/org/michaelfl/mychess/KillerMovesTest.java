package org.michaelfl.mychess;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.MovesCounter.MoveSet;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillerMovesTest {

    private static Random rand;

    @BeforeAll
    static void beforeAll() {
        rand = new Random();
    }

    private final static class MoveAndCount {
        private short move;
        private int count;

        MoveAndCount(short move, int count) {
            this.move = move;
            this.count = count;
        }

        @Override
        public String toString() {
            return move + "(" + count + ")";
        }
    }

    @Test
    void testSortDescending() {
        final int n = 1000;

        ArrayList<MoveAndCount> expected = new ArrayList<>(n);

        for (short i = 0; i < n; i++) {
            expected.add(new MoveAndCount(i, rand.nextInt(200)));
        }

        Collections.shuffle(expected, rand);

        short[] moves = new short[n];
        int[] counts = new int[n];

        for (short i = 0; i < n; i++) {
            MoveAndCount m = expected.get(i);
            moves[i] = m.move;
            counts[i] = m.count;
        }

        MovesCounter.sortDescending(moves, counts);

        expected.sort((m1, m2) -> m2.count - m1.count);

        for (short i = 0; i < n; i++) {
            MoveAndCount m = expected.get(i);
            assertEquals(m.count, counts[i], "Counts not correctly ordered");
            assertEquals(m.move, moves[i], "Moves not correctly ordered");
        }
    }

    @Test
    void testFindTopMovesOf1000() {
        testFindTopMoves(1000);
    }

    @Test
    void testFindTopMovesOf10000() {
        testFindTopMoves(10000);
    }

    @Test
    void testFindTopMovesOf30000() {
        testFindTopMoves(30000);
    }

    private void testFindTopMoves(int n) {
        ArrayList<MoveAndCount> expected = prepareExpectedMoves(n);
        int[] moveCounts = new int[n];

        for (short i = 0; i < n; i++) {
            moveCounts[i] = expected.get(i).count;
        }

        short[] topMoves = MovesCounter.findTopMoves(10, moveCounts);

        expected.sort((m1, m2) -> m2.count - m1.count);

        assertEquals(10, topMoves.length, "10 moves expected");

        for (int i = 0; i < topMoves.length; i++) {
            MoveAndCount m = expected.get(i);
            assertEquals(m.move, topMoves[i], "Wrong move at index " + i
                    + "\nexpected: " + expected.subList(0, topMoves.length)
                    + "\nactual: " + Arrays.toString(topMoves));
        }
    }

    ArrayList<MoveAndCount> prepareExpectedMoves(int n) {
        ArrayList<MoveAndCount> result = new ArrayList<>(n);
        List<Integer> weights = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            weights.add(i);
        }

        Collections.shuffle(weights, rand);

        for (short i = 0; i < n; i++) {
            result.add(new MoveAndCount(i, weights.get(i)));
        }

        return result;
    }

    @Test
    void testFindTopMovesWithIdenticalWeights() {
        final int n = 1000;
        ArrayList<MoveAndCount> expected = new ArrayList<>(n);
        int[] moveCounts = new int[n];

        for (short i = 0; i < n; i++) {
            MoveAndCount mc = new MoveAndCount(i, i % 2 == 0 ? 10 : 20);
            expected.add(mc);
            moveCounts[i] = mc.count;
        }

        short[] topMoves = MovesCounter.findTopMoves(10, moveCounts);

        expected.sort((m1, m2) -> m2.count - m1.count);

        assertEquals(10, topMoves.length, "10 moves expected");

        Set<Short> expectedTopMoves = new HashSet<>();
        Set<Short> actualTopMoves = new HashSet<>();

        for (int i = 0; i < topMoves.length; i++) {
            expectedTopMoves.add(expected.get(i).move);
            actualTopMoves.add(topMoves[i]);
        }

        assertEquals(expectedTopMoves, actualTopMoves, "Wrong top moves");
    }

//    @Test
    void testPerformance() throws InterruptedException {
        final int n = Short.MAX_VALUE;
        int[] moveCounts = new int[n];

        for (short i = 0; i < n; i++) {
            moveCounts[i] = rand.nextInt(20000);
        }

        long dummy = 0;
        for (int i = 0; i < 100000; i++) {
            short[] topMoves = MovesCounter.findTopMoves(2, moveCounts);
            dummy += topMoves[0] + topMoves[topMoves.length - 1];
        }
        System.out.println(dummy);

        dummy = 0;
        for (int i = 0; i < 100; i++) {
            long t1 = System.nanoTime();
            short[] topMoves = MovesCounter.findTopMoves(2, moveCounts);
            long t2 = System.nanoTime();
            dummy += topMoves[0] + topMoves[topMoves.length - 1];
            System.out.println("findTopMoves took " + (t2 - t1) / 1000 + "µs");
            Thread.sleep(100);
        }
        System.out.println(dummy);
    }

    @Test
    void testMoveSet() {
        final int n = Short.MAX_VALUE;
        ArrayList<MoveAndCount> expected = prepareExpectedMoves(n);

        MovesCounter killerMoves = new MovesCounter(10);
        MoveSet moveSet = killerMoves.getMovesOnDepth(0);

        for (MoveAndCount mc : expected) {
            for (int i = 0; i < mc.count; i++) {
                moveSet.add(mc.move);
            }
        }

        moveSet.findAndStoreTopMoves();
        short[] topMoves = moveSet.getTopMoves();

        expected.sort((m1, m2) -> m2.count - m1.count);

        assertEquals(10, topMoves.length, "10 moves expected");

        for (int i = 0; i < topMoves.length; i++) {
            MoveAndCount m = expected.get(i);
            assertEquals(m.move, topMoves[i], "Wrong move at index " + i
                    + "\nexpected: " + expected.subList(0, topMoves.length)
                    + "\nactual: " + Arrays.toString(topMoves));
        }
    }
}
