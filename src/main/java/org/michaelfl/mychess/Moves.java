package org.michaelfl.mychess;

import java.util.Arrays;

final class Moves {

    final static Moves ILLEGAL = new Moves(0);

    private final static int INITIAL_MOVE_CAPACITY = 30;
    private final static int CAPACITY_INCREMENT = 10;

    private byte[] moves;
    private int size;

    Moves() {
        this(INITIAL_MOVE_CAPACITY);
    }

    Moves(int capacity) {
        moves = new byte[capacity * 2];
    }

    boolean isIllegal() {
        return this == ILLEGAL;
    }

    void addMove(int from, int to) {
        if (size == moves.length)
            moves = Arrays.copyOf(moves, size + CAPACITY_INCREMENT * 2);
        moves[size++] = (byte) from;
        moves[size++] = (byte) to;
    }

    void revertMove() {
        size -= 2;
    }

    int count() {
        return size / 2;
    }

    byte[] getMoves() {
        return moves;
    }

    int getFrom(int moveIndex) {
        return moves[moveIndex * 2];
    }

    int getTo(int moveIndex) {
        return moves[moveIndex * 2 + 1];
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(count()).append('#');

        for (int i = 0; i < size; i += 2) {
            if (i > 0)
                buf.append(", ");
            fieldToString(buf, moves[i]);
            buf.append('-');
            fieldToString(buf, moves[i+1]);
        }

        return buf.toString();
    }

    private static void fieldToString(StringBuilder buf, int field) {
        int row = ChessUtil.getRowOfField(field);
        int col = ChessUtil.getColOfField(field);
        buf.append((char) ('a' + col)).append(row + 1);
    }

    String moveToString(int moveIndex) {
        StringBuilder buf = new StringBuilder();
        fieldToString(buf, getFrom(moveIndex));
        buf.append('-');
        fieldToString(buf, getTo(moveIndex));
        return buf.toString();
    }

    void print() {
        System.out.println(this);
    }

    public static void main(String[] args) {
    }

}
