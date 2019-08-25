package org.michaelfl.mychess;

final class MovesArray extends IntArray {

    @Override
    public String toString() {
        final int size = this.size;
        StringBuilder buf = new StringBuilder();
        buf.append(size).append('#');

        for (int i = 0; i < size; i ++) {
            if (i > 0)
                buf.append(" ");
            buf.append(ChessUtil.moveToString(array[i]));
        }

        return buf.toString();
    }
}
