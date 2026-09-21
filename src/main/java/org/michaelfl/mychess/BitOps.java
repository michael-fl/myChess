package org.michaelfl.mychess;

/**
 * Bit-level helpers. Packs four bytes into one {@code int} and extracts them back — used by
 * {@link Move} for the from/to/captured/type fields and by
 * {@link org.michaelfl.mychess.openingdb.DBValue} for serialized integer fields — and
 * addresses individual bits of a bit set spread over a {@code long} array.
 *
 * @author Michael Fleischhauer
 */
public final class BitOps {

    private BitOps() {
        // cannot be instantiated
    }

    public static int createWord(byte b0, byte b1, byte b2, byte b3) {
        return (b0 & 0xFF) +
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

    /**
     * Sets one bit of a bit set held in a {@code long} array.
     *
     * <p>Bit {@code n} lives in word {@code n / 64} at offset {@code n % 64}, so the array
     * addresses {@code 64 * bitSet.length} bits in all. Both halves of that are done by the
     * shift: {@code bit >>> 6} is the division, and the {@code %} needs no code at all
     * because Java masks a {@code long} shift distance to its low six bits — {@code 1L << 70}
     * is {@code 1L << 6}. Writing {@code 1L << (bit & 63)} would be the same instruction.
     *
     * <p>The {@code L} is load-bearing. {@code 1 << bit} is an <em>int</em> shift, whose
     * distance is masked to five bits, so every bit from 32 upward would land in the wrong
     * place — bit 64 would set bit 0 of word 1, and bit 32 would set bit 0 of word 0 again.
     *
     * @param bitSet the words holding the set, assumed large enough for {@code bit}
     * @param bit    the bit to set, counted from 0 across the whole array
     */
    public static void setBit(long[] bitSet, int bit) {
        bitSet[bit >>> 6] |= 1L << bit;
    }

    /**
     * Reads one bit of a bit set held in a {@code long} array.
     *
     * <p>Addressed exactly as {@link #setBit}; see there for why the shift needs no mask and
     * why the literal must be {@code 1L}.
     *
     * @param bitSet the words holding the set, assumed large enough for {@code bit}
     * @param bit    the bit to read, counted from 0 across the whole array
     * @return {@code true} when the bit is set
     */
    public static boolean getBit(long[] bitSet, int bit) {
        return (bitSet[bit >>> 6] & (1L << bit)) != 0;
    }
}
