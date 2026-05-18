package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
