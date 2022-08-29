package org.michaelfl.mychess;

import java.util.function.Supplier;

/**
 * @author Michael Fleischhauer
 */
public final class Assert {

    private final static boolean ENABLED = true;

    public static void __assert(Supplier<Boolean> conditionFunction) {
        if (ENABLED && !conditionFunction.get()) {
            throw new AssertionError();
        }
    }

    public static void __assert(Supplier<Boolean> conditionFunction, String message) {
        if (ENABLED && !conditionFunction.get()) {
            throw new AssertionError(message);
        }
    }

    public static void __assert(Supplier<Boolean> conditionFunction, Supplier<String> messageSupplier) {
        if (ENABLED && !conditionFunction.get()) {
            throw new AssertionError(messageSupplier.get());
        }
    }
}
