package org.michaelfl.mychess;

@SuppressWarnings("unused")
final class BitOps {

    private BitOps() {
        // cannot be instantiated
    }

    static int createWord(byte b0, byte b1, byte b2, byte b3) {
        return ((b0 & 0xFF)      ) +
                ((b1 & 0xFF) <<  8) +
                ((b2 & 0xFF) << 16) +
                ((b3       ) << 24);
    }

    static int setByte0(int word, byte b) {
        return ((b                    & 0xFF)      ) +
                (((byte) (word >>>  8) & 0xFF) <<  8) +
                (((byte) (word >>> 16) & 0xFF) << 16) +
                (((byte) (word >>> 24)       ) << 24);
    }

    static int setByte1(int word, byte b) {
        return (((byte) (word       ) & 0xFF)      ) +
                ((b                    & 0xFF) <<  8) +
                (((byte) (word >>> 16) & 0xFF) << 16) +
                (((byte) (word >>> 24)       ) << 24);
    }

    static int setByte2(int word, byte b) {
        return (((byte) (word       ) & 0xFF)      ) +
                (((byte) (word >>>  8) & 0xFF) <<  8) +
                ((b                    & 0xFF) << 16) +
                (((byte) (word >>> 24)       ) << 24);
    }

    static int setByte3(int word, byte b) {
        return (((byte) (word       ) & 0xFF)      ) +
                (((byte) (word >>>  8) & 0xFF) <<  8) +
                (((byte) (word >>> 16) & 0xFF) << 16) +
                ((b                          ) << 24);
    }

    static byte getByte0(int word) {
        return (byte) word;
    }

    static byte getByte1(int word) {
        return (byte) (word >>> 8);
    }

    static byte getByte2(int word) {
        return (byte) (word >>> 16);
    }

    static byte getByte3(int word) {
        return (byte) (word >>> 24);
    }

    static short createShort(byte b0, byte b1) {
        return (short) (((b0 & 0xFF)      ) +
                ((b1 & 0xFF) <<  8));
    }

    static byte getByte0(short word) {
        return (byte) word;
    }

    static byte getByte1(short word) {
        return (byte) (word >>> 8);
    }

    public static void main(String[] args) {
        short[] moves = new short[Short.MAX_VALUE];
        short s1 = (short) createWord((byte) Board.a1, (byte) Board.h8, (byte) 255, (byte) 255);
        moves[s1]++;
        byte b0 = getByte0(s1);
        byte b1 = getByte1(s1);
        System.out.println(s1 + " = [" + ChessUtil.fieldToString(b0) + "-" + ChessUtil.fieldToString(b1) + "]");
    }
}
