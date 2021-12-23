package org.michaelfl.mychess;

@SuppressWarnings("unused")
public final class BitOps {

    private BitOps() {
        // cannot be instantiated
    }

    public static int createWord(byte b0, byte b1, byte b2, byte b3) {
        return ((b0 & 0xFF)      ) +
                ((b1 & 0xFF) <<  8) +
                ((b2 & 0xFF) << 16) +
                ((b3       ) << 24);
    }

    public static int setByte0(int word, byte b) {
        return ((b                    & 0xFF)      ) +
                (((byte) (word >>>  8) & 0xFF) <<  8) +
                (((byte) (word >>> 16) & 0xFF) << 16) +
                (((byte) (word >>> 24)       ) << 24);
    }

    public static int setByte1(int word, byte b) {
        return (((byte) (word       ) & 0xFF)      ) +
                ((b                    & 0xFF) <<  8) +
                (((byte) (word >>> 16) & 0xFF) << 16) +
                (((byte) (word >>> 24)       ) << 24);
    }

    public static int setByte2(int word, byte b) {
        return (((byte) (word       ) & 0xFF)      ) +
                (((byte) (word >>>  8) & 0xFF) <<  8) +
                ((b                    & 0xFF) << 16) +
                (((byte) (word >>> 24)       ) << 24);
    }

    public static int setByte3(int word, byte b) {
        return (((byte) (word       ) & 0xFF)      ) +
                (((byte) (word >>>  8) & 0xFF) <<  8) +
                (((byte) (word >>> 16) & 0xFF) << 16) +
                ((b                          ) << 24);
    }

    public static byte getByte0(int word) {
        return (byte) word;
    }

    public static byte getByte1(int word) {
        return (byte) (word >>> 8);
    }

    public static byte getByte2(int word) {
        return (byte) (word >>> 16);
    }

    public static byte getByte3(int word) {
        return (byte) (word >>> 24);
    }

    public static short createShort(byte b0, byte b1) {
        return (short) (((b0 & 0xFF)      ) +
                ((b1 & 0xFF) <<  8));
    }

    public static byte getByte0(short word) {
        return (byte) word;
    }

    public static byte getByte1(short word) {
        return (byte) (word >>> 8);
    }

}
