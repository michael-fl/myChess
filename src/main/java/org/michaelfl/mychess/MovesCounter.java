package org.michaelfl.mychess;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("WeakerAccess")
public final class MovesCounter {

    private final static short[] EMPTY_MOVE_ARR = new short[0];

    @SuppressWarnings("WeakerAccess")
    public final class MoveSet {
        private final int[] moveCounts = new int[Short.MAX_VALUE];
        private short[] topMoves = EMPTY_MOVE_ARR;

        public final void add(final int move) {
            // Remove information about captured piece and move type from the move
            final short shortMove = (short) move;
            moveCounts[shortMove]++;
        }

        public final void clear() {
            Arrays.fill(moveCounts, 0);
            topMoves = EMPTY_MOVE_ARR;
        }

        /**
         * Get the currently known best moves.
         * Unless findAndStoreTopMoves was not called at least once, the returned array will be empty.
         */
        public short[] getTopMoves() {
            return topMoves;
        }

        /** Find the best moves and store them internally. */
        public void findAndStoreTopMoves() {
            topMoves = findTopMoves(nTop, moveCounts);
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

            buf.append("Top:");
            for (short move : topMoves) {
                if (move > 0) {
                    buf.append(" ");
                    buf.append(ChessUtil.moveToString(move));
                }
            }

            buf.append(", total: #").append(size());

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

    private final int nTop;
    private final ArrayList<MoveSet> movesPerDepth = new ArrayList<>(50);

    public MovesCounter(int nTop) {
        this.nTop = nTop;
    }

    public final void clear() {
        for (MoveSet moveSet : movesPerDepth) {
            moveSet.clear();
        }
    }

    public final void addMove(final int move, final int depth) {
        getMovesOnDepth(depth).add(move);
    }

    /** Find and store current top moves. */
    public final void sample() {
        for (var moveSet : movesPerDepth) {
            moveSet.findAndStoreTopMoves();
        }
    }

    public final MoveSet getMovesOnDepth(int depth) {
        if (movesPerDepth.size() <= depth) {
            final int n = depth - movesPerDepth.size() + 1;
            for (int i = n; i > 0; i--) {
                movesPerDepth.add(new MoveSet());
            }
        }

        return movesPerDepth.get(depth);
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

    /** Get the n best moves. */
    static short[] findTopMoves(final int nTop, final int[] moveCounts) {
        final short moveCount = (short) moveCounts.length;
        final short[] bestMoves = new short[nTop];
        final int[] bestCounts = new int[nTop];

        initTopMoves(moveCounts, bestMoves, bestCounts);

        int minIndex = minIndex(bestCounts);
        int min = bestCounts[minIndex];

        for (short i = (short) nTop; i < moveCount; i++) {
            if (moveCounts[i] > min) {
                bestCounts[minIndex] = moveCounts[i];
                bestMoves[minIndex] = i;
                minIndex = minIndex(bestCounts);
                min = bestCounts[minIndex];
            }
        }

        sortDescending(bestMoves, bestCounts);

//        final int limit = 1000; // Math.max(bestCounts[0] * 2 / 3, 1000);
//        for (var i = 0; i < nTop; i++) {
//            if (bestCounts[i] < limit)
//                bestMoves[i] = 0;
//        }

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
            bestMoves[i] = moveCounts[i] > 0 ? i : 0;
        }
    }

}
