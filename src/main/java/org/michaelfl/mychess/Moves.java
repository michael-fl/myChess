package org.michaelfl.mychess;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class Moves {

    static final Moves ILLEGAL = new Moves(0);

    private static final int INITIAL_MOVE_CAPACITY = 30;
    private static final int CAPACITY_INCREMENT = 10;

    public final IntArray moves;

    public Moves() {
        this(INITIAL_MOVE_CAPACITY);
    }

    Moves(int capacity) {
        moves = new IntArray(capacity);
    }

    public boolean isIllegal() {
        return this == ILLEGAL;
    }

    public void addMove(int move) {
        moves.add(move);
    }

    public int count() {
        return moves.size();
    }

    public int[] getMoves() {
        return moves.getArray();
    }

    int getMove(int moveIndex) {
        return moves.array[moveIndex];
    }

    boolean contains(int move) {
        return moves.contains(move);
    }

    @Override
    public String toString() {
        return ChessUtil.movesToString(moves.array, moves.size());
    }

    void print() {
        System.out.println(this);
    }
}
