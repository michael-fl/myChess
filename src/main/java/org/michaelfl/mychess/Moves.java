package org.michaelfl.mychess;

import java.util.Arrays;

final class Moves {

    final static Moves ILLEGAL = new Moves(0);

    private final static int INITIAL_MOVE_CAPACITY = 30;
    private final static int CAPACITY_INCREMENT = 10;

    private int[] moves;
    private int size;

    Moves() {
        this(INITIAL_MOVE_CAPACITY);
    }

    Moves(int capacity) {
        moves = new int[capacity];
    }

    boolean isIllegal() {
        return this == ILLEGAL;
    }

    void addMove(int move) {
        if (size == moves.length)
            moves = Arrays.copyOf(moves, size + CAPACITY_INCREMENT);
        moves[size++] = move;
    }

    void revertMove() {
        --size;
    }

    int count() {
        return size;
    }

    int[] getMoves() {
        return moves;
    }

    int getMove(int moveIndex) {
        return moves[moveIndex];
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(count()).append('#');

        for (int i = 0; i < size; i ++) {
            if (i > 0)
                buf.append(", ");
            ChessUtil.moveToString(moves[i]);
        }

        return buf.toString();
    }

    void print() {
        System.out.println(this);
    }

    public static void main(String[] args) {
    }

}
