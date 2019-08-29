package org.michaelfl.mychess;

final class MovesArray extends IntArray {

    MovesArray() {
    }

    MovesArray(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public String toString() {
        return ChessUtil.movesToString(array, size);
    }
}
