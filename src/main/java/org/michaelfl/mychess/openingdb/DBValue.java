package org.michaelfl.mychess.openingdb;

import org.michaelfl.mychess.BitOps;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.Pgn.Result;

import java.util.Arrays;

/**
 *   4byte    4byte    4byte    4byte    4byte    4byte    4byte    4byte    4byte
 * |position| move1  | count  | count  | count  | move2  | count  | count  | count  | ... | moveN  | count  | count  | count  |
 * | count  |        | total  | win    | loss   |        | total  | win    | loss   | ... |        | total  | win    | loss   |
 *
 * @author Michael Fleischhauer
 */
public final class DBValue {

    private static final int MOVE_ENTRY_SIZE = 16;
    private static final int OFFSET_POSITION_COUNT = 0;
    private static final int OFFSET_MOVES = 4;
    private static final int OFFSET_MOVE_COUNT_TOTAL = 4;
    private static final int OFFSET_MOVE_COUNT_WIN = 8;
    private static final int OFFSET_MOVE_COUNT_LOSS = 12;

    private byte[] buf;

    DBValue() {
        this.buf = new byte[4];
    }

    DBValue(byte[] buf) {
        this.buf = buf != null ? buf : new byte[4];
    }

    public void addMove(int move, int turn, Result result) {
        final byte[] value = buf;

        final int positionCount = readInt(value, OFFSET_POSITION_COUNT);
        writeInt(value, OFFSET_POSITION_COUNT, positionCount + 1);

        final int offset = findMoveOffset(move);
        if (offset < 0) {
            // Move not yet contained ==> increase space and add move
            increaseSpaceAndInsertNewMove(move, turn, result);
        } else {
            boolean isWinMove = (result == Result.WHITE_WINS && turn == GameStatus.TURN_WHITE)
                    || (result == Result.BLACK_WINS && turn == GameStatus.TURN_BLACK);
            boolean isLossMove = !isWinMove && (result == Result.WHITE_WINS || result == Result.BLACK_WINS);

            int totalCount = readInt(value, offset + OFFSET_MOVE_COUNT_TOTAL);
            writeInt(value, offset + OFFSET_MOVE_COUNT_TOTAL, totalCount + 1);
            if (isWinMove) {
                int count = readInt(value, offset + OFFSET_MOVE_COUNT_WIN);
                writeInt(value, offset + OFFSET_MOVE_COUNT_WIN, count + 1);
            } else if (isLossMove) {
                int count = readInt(value, offset + OFFSET_MOVE_COUNT_LOSS);
                writeInt(value, offset + OFFSET_MOVE_COUNT_LOSS, count + 1);
            }
        }
    }

    public byte[] getBuffer() {
        return buf;
    }

    public int getNumberOfMoves() {
        return (buf.length - 4) / MOVE_ENTRY_SIZE;
    }

    public Move getMoveByIndex(int index) {
        if (index >= getNumberOfMoves()) {
            throw new IndexOutOfBoundsException(index + ">=" + getNumberOfMoves());
        }
        final int offset = indexToOffset(index);
        return new Move(readInt(buf, offset));
    }

    public int getPositionCount() {
        return readInt(buf, OFFSET_POSITION_COUNT);
    }

    public int getIndexOfMove(int move) {
        final byte[] value = buf;
        final int length = value.length;
        int i = 0;

        for (int offset = OFFSET_MOVES; offset < length; offset += MOVE_ENTRY_SIZE, i++) {
            if (readInt(value, offset) == move) {
                return i;
            }
        }

        return -1;
    }

    public int getCountByIndex(int moveIndex) {
        final int offset = indexToOffset(moveIndex);
        return readInt(buf, offset + OFFSET_MOVE_COUNT_TOTAL);
    }

    public int getWinCountByIndex(int moveIndex) {
        final int offset = indexToOffset(moveIndex);
        return readInt(buf, offset + OFFSET_MOVE_COUNT_WIN);
    }

    public int getLossCountByIndex(int moveIndex) {
        final int offset = indexToOffset(moveIndex);
        return readInt(buf, offset + OFFSET_MOVE_COUNT_LOSS);
    }

    public int getDrawCountByIndex(int moveIndex) {
        final int offset = indexToOffset(moveIndex);
        return readInt(buf, offset + OFFSET_MOVE_COUNT_TOTAL)
                - readInt(buf, offset + OFFSET_MOVE_COUNT_WIN)
                - readInt(buf, offset + OFFSET_MOVE_COUNT_LOSS);
    }

    private static int indexToOffset(int moveIndex) {
        return 4 + moveIndex * MOVE_ENTRY_SIZE;
    }

    public void increaseSpaceAndInsertNewMove(int move, int turn, Result result) {
        boolean isWinMove = (result == Result.WHITE_WINS && turn == GameStatus.TURN_WHITE)
                || (result == Result.BLACK_WINS && turn == GameStatus.TURN_BLACK);
        boolean isLossMove = !isWinMove && (result == Result.WHITE_WINS || result == Result.BLACK_WINS);

        // Move not yet contained ==> increase space and add move
        int offset = increaseSpace();
        final byte[] value = buf;

        writeInt(value, offset, move);
        writeInt(value, offset + OFFSET_MOVE_COUNT_TOTAL, 1);
        writeInt(value, offset + OFFSET_MOVE_COUNT_WIN, isWinMove ? 1 : 0);
        writeInt(value, offset + OFFSET_MOVE_COUNT_LOSS, isLossMove ? 1 : 0);
    }

    private int increaseSpace() {
        buf = Arrays.copyOf(buf, buf.length + MOVE_ENTRY_SIZE);
        return buf.length - MOVE_ENTRY_SIZE;
    }

    private int findMoveOffset(int move) {
        final byte[] value = buf;
        final int length = value.length;

        for (int offset = OFFSET_MOVES; offset < length; offset += MOVE_ENTRY_SIZE) {
            if (readInt(value, offset) == move) {
                return offset;
            }
        }

        return -1;
    }

    private static void writeInt(final byte[] buf, final int offset, final int value) {
        buf[offset] = BitOps.getByte0(value);
        buf[offset + 1] = BitOps.getByte1(value);
        buf[offset + 2] = BitOps.getByte2(value);
        buf[offset + 3] = BitOps.getByte3(value);
    }

    private static int readInt(final byte[] buf, final int offset) {
        return BitOps.createWord(buf[offset], buf[offset + 1], buf[offset + 2], buf[offset + 3]);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DBValue dbValue = (DBValue) o;
        return Arrays.equals(buf, dbValue.buf);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(buf);
    }
}
