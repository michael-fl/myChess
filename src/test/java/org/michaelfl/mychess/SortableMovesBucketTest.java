package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Michael Fleischhauer
 */
class SortableMovesBucketTest {

    @Test
    void testSortEmptyArray() {
        var bucket = new SortableMovesBucket();
        bucket.sort();
        assertEquals(0, bucket.getMoves().size());
    }

    @Test
    void testSortSingleElementArray() {
        int move = 1;
        var bucket = new SortableMovesBucket();
        bucket.add(move, 1);
        bucket.sort();
        assertEquals(1, bucket.getMoves().size());
        assertEquals(move, bucket.getMove(0));
    }

    @Test
    void testSortTwoElementsArray() {
        int move1 = 1;
        int move2 = 2;
        var bucket = new SortableMovesBucket();
        bucket.add(move1, 3);
        bucket.add(move2, 10);
        bucket.sort();
        assertEquals(2, bucket.getMoves().size());
        assertEquals(move2, bucket.getMove(0));
        assertEquals(move1, bucket.getMove(1));
    }

    private record MoveAndWeight(int move, int weight) {
    }

    @Test
    void testSort() {
        var moves = List.of(
                new MoveAndWeight(1, 10),
                new MoveAndWeight(2, 0),
                new MoveAndWeight(3, -5),
                new MoveAndWeight(4, 100),
                new MoveAndWeight(5, 100),
                new MoveAndWeight(6, 80),
                new MoveAndWeight(7, 85),
                new MoveAndWeight(8, 3));
        var expectedMoves = new ArrayList<>(moves);
        expectedMoves.sort((m1, m2) -> m2.weight - m1.weight);

        var bucket = new SortableMovesBucket();
        for (var m : moves) {
            bucket.add(m.move, m.weight);
        }

        bucket.sort();

        for (int i = 0; i < moves.size(); i++) {
            assertEquals(expectedMoves.get(i).move, bucket.getMove(i));
        }
    }
}
