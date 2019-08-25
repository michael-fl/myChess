package org.michaelfl.mychess;

import java.util.ArrayList;
import java.util.Arrays;

public final class KillerMoves {

    private final static int CAPACITY_INCREMENT = 10;

    public final static class MoveSet {
        private int[] moves = new int[16];
        private int[] counts = new int[16];;
        private int size;

        public final void add(final int move) {
            // Remove information about captured piece and move type from the move
            final int moveToAdd = Move.create(Move.getFromField(move), Move.getToField(move), (byte) 0, (byte) 0);

            // Check if move is already in set
            for (int i = size - 1; i >= 0; i--) {
                if (moveToAdd == moves[i]) {
                    counts[i]++;
                    return;
                }
            }

            if (size == moves.length) {
                moves = Arrays.copyOf(moves, size + CAPACITY_INCREMENT);
                counts = Arrays.copyOf(counts, size + CAPACITY_INCREMENT);
            }
            counts[size] = 1;
            moves[size++] = moveToAdd;
        }

        public final void clear() {
            size = 0;
        }

        public final boolean contains(final int move) {
            // Remove information about captured piece and move type from the move
            final int moveToSearch = Move.create(Move.getFromField(move), Move.getToField(move), (byte) 0, (byte) 0);
            final int len = size;

            for (int i = 0; i < len; i++) {
                if (moveToSearch == moves[i])
                    return true;
            }

            return false;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append(size).append('#');

            for (int i = 0; i < size; i ++) {
                if (i > 0)
                    buf.append(" ");
                buf.append(ChessUtil.moveToString(moves[i]));
                buf.append('(').append(counts[i]).append(')');
            }

            return buf.toString();
        }
    }

    private final ArrayList<MoveSet> killerMovesPerDepth = new ArrayList<>(50);

    public final void clear() {
        for (MoveSet moveSet : killerMovesPerDepth) {
            moveSet.clear();
        }
    }

    public final void addMove(final int move, final int depth) {
        getMovesOnDepth(depth).add(move);
    }

    public final MoveSet getMovesOnDepth(int depth) {
        if (killerMovesPerDepth.size() <= depth) {
            final int n = depth - killerMovesPerDepth.size() + 1;
            for (int i = n; i > 0; i--) {
                killerMovesPerDepth.add(new MoveSet());
            }
        }

        return killerMovesPerDepth.get(depth);
    }
}
