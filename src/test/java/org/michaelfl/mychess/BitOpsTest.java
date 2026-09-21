package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author Michael Fleischhauer
 */
class BitOpsTest {

    @Test
    void createWordRoundTrip() {
        byte b0 = 0x12;
        byte b1 = 0x34;
        byte b2 = 0x56;
        byte b3 = 0x78;

        int word = BitOps.createWord(b0, b1, b2, b3);

        assertEquals(b0, BitOps.getByte0(word), "byte 0 round-trip");
        assertEquals(b1, BitOps.getByte1(word), "byte 1 round-trip");
        assertEquals(b2, BitOps.getByte2(word), "byte 2 round-trip");
        assertEquals(b3, BitOps.getByte3(word), "byte 3 round-trip");
    }

    @Test
    void createWordPreservesSignedHighByte() {
        // The high byte can be sign-extended on shift; verify the helper handles this.
        byte b0 = 0;
        byte b1 = 0;
        byte b2 = 0;
        byte b3 = (byte) 0xFF;

        int word = BitOps.createWord(b0, b1, b2, b3);
        assertEquals((byte) 0xFF, BitOps.getByte3(word),
                "high byte 0xFF must round-trip through createWord/getByte3");
    }

    @Test
    void createWordZerosYieldZero() {
        assertEquals(0, BitOps.createWord((byte) 0, (byte) 0, (byte) 0, (byte) 0));
    }

    @Test
    void setBitIsReadBackByGetBit() {
        var bits = new long[4];

        for (int bit : new int[] {0, 1, 63, 64, 127, 128, 255}) {
            BitOps.setBit(bits, bit);
        }

        for (int bit = 0; bit < 256; bit++) {
            boolean expected = bit == 0 || bit == 1 || bit == 63 || bit == 64
                    || bit == 127 || bit == 128 || bit == 255;

            assertEquals(expected, BitOps.getBit(bits, bit), "bit " + bit);
        }
    }

    @Test
    void setBitLandsInTheRightWord() {
        var bits = new long[4];

        BitOps.setBit(bits, 0);
        BitOps.setBit(bits, 64);
        BitOps.setBit(bits, 129);
        BitOps.setBit(bits, 255);

        assertEquals(1L, bits[0], "bit 0 is word 0 offset 0");
        assertEquals(1L, bits[1], "bit 64 is word 1 offset 0");
        assertEquals(2L, bits[2], "bit 129 is word 2 offset 1");
        assertEquals(Long.MIN_VALUE, bits[3], "bit 255 is word 3 offset 63, the sign bit");
    }

    /**
     * Pins the trap the implementation exists to avoid: {@code 1 << bit} masks its shift
     * distance to five bits rather than six, so bit 32 would fold back onto bit 0 of the
     * same word and bit 64 onto bit 0 of word 1 by the wrong route. With {@code 1L} the
     * words stay independent, which is what these assertions check.
     */
    @Test
    void bitsAbove31DoNotFoldBackOntoLowBits() {
        var bits = new long[2];

        BitOps.setBit(bits, 32);

        assertEquals(1L << 32, bits[0], "bit 32 must set bit 32, not bit 0");
        assertEquals(0L, bits[1], "bit 32 must not touch word 1");

        BitOps.setBit(bits, 96);

        assertEquals(1L << 32, bits[0], "setting bit 96 must leave word 0 alone");
        assertEquals(1L << 32, bits[1], "bit 96 is word 1 offset 32");
    }

    @Test
    void getBitIsFalseOnAnEmptySet() {
        var bits = new long[2];

        for (int bit = 0; bit < 128; bit++) {
            assertFalse(BitOps.getBit(bits, bit), "bit " + bit + " of an all-zero set");
        }
    }

    @Test
    void setBitIsIdempotent() {
        var bits = new long[1];

        BitOps.setBit(bits, 7);
        long once = bits[0];

        BitOps.setBit(bits, 7);

        assertEquals(once, bits[0], "setting the same bit twice must not change the word");
    }
}
