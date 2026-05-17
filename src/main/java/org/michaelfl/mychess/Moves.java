package org.michaelfl.mychess;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class Moves {

    final static Moves ILLEGAL = new Moves(0);

    private final static int INITIAL_MOVE_CAPACITY = 30;
    private final static int CAPACITY_INCREMENT = 10;

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

    public final void addMove(int move) {
        moves.add(move);
    }

    public final int count() {
        return moves.size();
    }

    public final int[] getMoves() {
        return moves.getArray();
    }

    final int getMove(int moveIndex) {
        return moves.array[moveIndex];
    }

    final boolean contains(int move) {
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
