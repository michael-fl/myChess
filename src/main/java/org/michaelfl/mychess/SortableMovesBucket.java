package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public final class SortableMovesBucket {
    private final MovesArray moves;
    private final IntArray weights;

    public SortableMovesBucket() {
        this(IntArray.INITIAL_CAPACITY);
    }

    public SortableMovesBucket(int initialCapacity) {
        moves = new MovesArray(initialCapacity);
        weights = new IntArray(initialCapacity);
    }

    public final void clear() {
        moves.clear();
        weights.clear();
    }

    public final void add(int move, int weight) {
        moves.add(move);
        weights.add(weight);
    }

    public final void sort() {
        final int[] movesArr = moves.getArray();
        final int[] weightArr = weights.getArray();
        final int n = moves.size();

        if (n < 2) {
            return;
        }
        if (n == 2) {
            if (weightArr[1] > weightArr[0]) {
                final int tmpMove = movesArr[0];
                final int tmpWeight = weightArr[0];
                movesArr[0] = movesArr[1];
                movesArr[1] = tmpMove;
                weightArr[0] = weightArr[1];
                weightArr[1] = tmpWeight;
            }
            return;
        }

        // insertion sort
        int j;
        for (int i = 1; i < n; i++) {
            final int tmpMove = movesArr[i];
            final int tmpWeight = weightArr[i];

            for (j = i; j >= 1 && tmpWeight > weightArr[j-1]; j--) {
                movesArr[j] = movesArr[j - 1];
                weightArr[j] = weightArr[j - 1];
            }

            movesArr[j] = tmpMove;
            weightArr[j] = tmpWeight;
        }
    }

    public MovesArray getMoves() {
        return moves;
    }

    public int getMove(int index) {
        return moves.getArray()[index];
    }
}
