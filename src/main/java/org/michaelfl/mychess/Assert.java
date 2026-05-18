package org.michaelfl.mychess;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Compile-time toggleable invariant checks. Conditions are passed as
 * {@link BooleanSupplier} and messages as {@link Supplier} so they are not
 * evaluated when assertions are disabled — safe to use in hot paths.
 *
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
