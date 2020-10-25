package org.michaelfl.mychess;

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
