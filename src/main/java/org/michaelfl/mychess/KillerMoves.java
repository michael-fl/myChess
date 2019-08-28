package org.michaelfl.mychess;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("WeakerAccess")
public final class KillerMoves {

    @SuppressWarnings("WeakerAccess")
    public final static class MoveSet {
        private final int[] moveCounts = new int[Short.MAX_VALUE];

        public final void add(final int move) {
            // Remove information about captured piece and move type from the move
            final short shortMove = (short) move;
            moveCounts[shortMove]++;
        }

        public final void clear() {
            Arrays.fill(moveCounts, 0);
        }

        public final boolean contains(final int move) {
            // Remove information about captured piece and move type from the move
            final short shortMove = (short) move;
            return moveCounts[shortMove] > 0;
        }

        /** Get the best moves. */
        public short[] findTopMoves() {
            return KillerMoves.findTopMoves(moveCounts);
        }

        private int size() {
            int size = 0;
            for (int cnt : moveCounts) {
                if (cnt > 0)
                    size++;
            }
            return size;
        }

        private Short[] sortMoves() {
            final Short[] moves = new Short[moveCounts.length];

            for (short i = 0; i < moves.length; i++) {
                moves[i] = i;
            }

            Arrays.sort(moves, (m1, m2) -> moveCounts[m2] - moveCounts[m1]);

            return moves;
        }

        @Override
        public String toString() {
            final Short[] moves = sortMoves();

            StringBuilder buf = new StringBuilder();
            buf.append(size()).append('#');

            for (short move : moves) {
                if (moveCounts[move] > 0) {
                    buf.append(" ");
                    buf.append(ChessUtil.moveToString(move));
                    buf.append('(').append(moveCounts[move]).append(')');
                }
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

    /** Traditional (without sentinel) insertion sort, descending. */
    static void sortDescending(final short[] moves, final int[] counts) {
        final int n = moves.length - 1;

        for (int i = 0, j = 0; i < n; j = ++i) {
            final int cnt = counts[i + 1];
            final short move = moves[i + 1];

            while (cnt > counts[j]) {
                counts[j + 1] = counts[j];
                moves[j + 1] = moves[j];
                if (j-- == 0)
                    break;
            }

            counts[j + 1] = cnt;
            moves[j + 1] = move;
        }
    }

    private final static int TOP_N = 10;

    /** Get the 10 best moves. */
    static short[] findTopMoves(final int[] moveCounts) {
        final short moveCount = (short) moveCounts.length;
        final short[] bestMoves = new short[TOP_N];
        final int[] bestCounts = new int[TOP_N];

        initTopMoves(moveCounts, bestMoves, bestCounts);

        int minIndex = minIndex(bestCounts);
        int min = bestCounts[minIndex];

        for (short i = (short) TOP_N; i < moveCount; i++) {
            if (moveCounts[i] > min) {
                bestCounts[minIndex] = moveCounts[i];
                bestMoves[minIndex] = i;
                minIndex = minIndex(bestCounts);
                min = bestCounts[minIndex];
            }
        }

        sortDescending(bestMoves, bestCounts);

        return Arrays.copyOf(bestMoves, bestMoves.length);
    }

    private static int minIndex(final int[] counts) {
        final int len = counts.length;
        int min = counts[0];
        int minIndex = 0;

        for (int i = 1; i < len; i++) {
            if (counts[i] < min) {
                minIndex = i;
                min = counts[i];
            }
        }

        return minIndex;
    }

    private static void initTopMoves(final int[] moveCounts, final short[] bestMoves, final int[] bestCounts) {
        final int n = bestMoves.length;

        for (short i = 0; i < n; i++) {
            bestCounts[i] = moveCounts[i];
            bestMoves[i] = i;
        }
    }

}
