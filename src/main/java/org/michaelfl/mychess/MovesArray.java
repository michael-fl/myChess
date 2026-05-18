package org.michaelfl.mychess;

/**
 * {@link IntArray} specialization whose {@code toString()} formats the stored
 * values as move notation (via {@link ChessUtil#movesToString}).
 *
 * @author Michael Fleischhauer
 */
public final class MovesArray extends IntArray {

    public MovesArray() {
    }

    public MovesArray(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public String toString() {
        return ChessUtil.movesToString(array, size);
    }
}
