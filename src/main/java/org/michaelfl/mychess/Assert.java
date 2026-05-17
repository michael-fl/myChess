package org.michaelfl.mychess;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * @author Michael Fleischhauer
 */
public final class Assert {

    private static final boolean ENABLED = true;

    private Assert() {
        throw new IllegalStateException("Utility class");
    }

    public static void __assert(BooleanSupplier conditionFunction) {
        if (ENABLED && !conditionFunction.getAsBoolean()) {
            throw new AssertionError();
        }
    }

    public static void __assert(BooleanSupplier conditionFunction, String message) {
        if (ENABLED && !conditionFunction.getAsBoolean()) {
            throw new AssertionError(message);
        }
    }

    public static void __assert(BooleanSupplier conditionFunction, Supplier<String> messageSupplier) {
        if (ENABLED && !conditionFunction.getAsBoolean()) {
            throw new AssertionError(messageSupplier.get());
        }
    }
}
