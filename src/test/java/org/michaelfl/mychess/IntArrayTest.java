package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class IntArrayTest {

    @Test
    void defaultCapacityIsThirty() {
        var a = new IntArray();
        assertEquals(0, a.size(), "freshly created array is empty");
        // Internal capacity is exposed indirectly via getArray().length
        assertEquals(IntArray.INITIAL_CAPACITY, a.getArray().length,
                "initial backing array has INITIAL_CAPACITY length");
    }

    @Test
    void addAndPopRestoreSize() {
        var a = new IntArray();
        a.add(7);
        a.add(11);
        assertEquals(2, a.size());
        assertEquals(11, a.pop(), "pop returns last-pushed value");
        assertEquals(7, a.pop());
        assertEquals(0, a.size());
    }

    @Test
    void addBeyondInitialCapacityGrowsBackingArray() {
        var a = new IntArray(2);
        a.add(1);
        a.add(2);
        // Now backing array is full at capacity 2; the next add must trigger growth.
        a.add(3);
        assertEquals(3, a.size(), "size after overflow add");
        assertTrue(a.getArray().length >= 3, "backing array must have grown");
        assertEquals(3, a.pop());
        assertEquals(2, a.pop());
        assertEquals(1, a.pop());
    }

    @Test
    void clearResetsSizeButKeepsCapacity() {
        var a = new IntArray();
        int initialCapacity = a.getArray().length;
        a.add(1);
        a.add(2);
        a.add(3);
        a.clear();
        assertEquals(0, a.size(), "clear() resets size");
        assertEquals(initialCapacity, a.getArray().length,
                "clear() does not shrink the backing array");
    }

    @Test
    void containsScansBackwards() {
        var a = new IntArray();
        a.add(10);
        a.add(20);
        a.add(30);
        assertTrue(a.contains(20), "contains must find an element present");
        assertTrue(a.contains(30), "contains must find the last element");
        assertFalse(a.contains(99), "contains must return false for missing element");
    }

    @Test
    void addAllAppendsOtherArrayInOrder() {
        var a = new IntArray();
        a.add(1);
        var b = new IntArray();
        b.add(10);
        b.add(20);
        b.add(30);
        a.addAll(b);
        assertEquals(4, a.size(), "size after addAll");
        // Pop in reverse to verify order
        assertEquals(30, a.pop());
        assertEquals(20, a.pop());
        assertEquals(10, a.pop());
        assertEquals(1, a.pop());
    }

    @Test
    void addAllOfEmptyIsNoOp() {
        var a = new IntArray();
        a.add(42);
        a.addAll(new IntArray());
        assertEquals(1, a.size(), "addAll of empty must not change size");
    }
}
