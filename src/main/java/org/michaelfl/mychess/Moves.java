package org.michaelfl.mychess;

import java.util.Arrays;

public final class Moves {

    final static Moves ILLEGAL = new Moves(0);

    private final static int INITIAL_MOVE_CAPACITY = 30;
    private final static int CAPACITY_INCREMENT = 10;

    private int[] moves;
    private int size;

    public Moves() {
        this(INITIAL_MOVE_CAPACITY);
    }

    Moves(int capacity) {
        moves = new int[capacity];
    }

    public boolean isIllegal() {
        return this == ILLEGAL;
    }

    public void addMove(int move) {
        if (size == moves.length)
            moves = Arrays.copyOf(moves, size + CAPACITY_INCREMENT);
        moves[size++] = move;
    }

    public int popMove() {
        return moves[--size];
    }

    public void revertMove() {
        --size;
    }

    public int count() {
        return size;
    }

    public int[] getMoves() {
        return moves;
    }

    int getMove(int moveIndex) {
        return moves[moveIndex];
    }

    boolean contains(int move) {
        for (int i = size - 1; i >= 0; i--) {
            if (moves[i] == move)
                return true;
        }

        return false;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(count()).append('#');

        for (int i = 0; i < size; i ++) {
            if (i > 0)
                buf.append(" ");
            buf.append(ChessUtil.moveToString(moves[i]));
        }

        return buf.toString();
    }

    void print() {
        System.out.println(this);
    }

}
