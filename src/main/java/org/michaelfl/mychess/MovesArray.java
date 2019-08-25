package org.michaelfl.mychess;

final class MovesArray extends IntArray {

    @Override
    public String toString() {
        return ChessUtil.movesToString(array, size);
    }
}
