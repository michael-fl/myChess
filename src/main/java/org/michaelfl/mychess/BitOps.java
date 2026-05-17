package org.michaelfl.mychess;

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

}
