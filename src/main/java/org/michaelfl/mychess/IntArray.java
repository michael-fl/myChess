package org.michaelfl.mychess;

import java.util.Arrays;
import java.util.Random;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class IntArray {

    private final static int INITIAL_CAPACITY = 30;
    private final static int CAPACITY_INCREMENT = 10;

    int[] array;
    private int size;

    public IntArray() {
        this(INITIAL_CAPACITY);
    }

    public IntArray(int initialCapacity) {
        array = new int[initialCapacity];
    }

    public final void add(int element) {
        if (size == array.length)
            array = Arrays.copyOf(array, size + CAPACITY_INCREMENT);
        array[size++] = element;
    }

    public final int pop() {
        return array[--size];
    }

    public final int[] getArray() {
        return array;
    }

    public final int size() {
        return size;
    }

    public final boolean contains(int element) {
        for (int i = size - 1; i >= 0; i--) {
            if (array[i] == element)
                return true;
        }

        return false;
    }

    public final void shuffle(final Random random) {
        // Implementing Fisher–Yates shuffle
        for (int i = size - 1; i > 0; i--) {
            final int index = random.nextInt(i + 1);
            final int tmp = array[index];
            array[index] = array[i];
            array[i] = tmp;
        }
    }

    @Override
    public String toString() {
        return Arrays.toString(Arrays.copyOf(array, size));
    }
}
