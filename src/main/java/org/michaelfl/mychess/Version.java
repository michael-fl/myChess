package org.michaelfl.mychess;

import java.io.IOException;
import java.util.Properties;

/**
 * The build-time version of myChess, read once from {@code version.properties}.
 *
 * <p>That resource carries {@code version=${project.version}} and is filled in by
 * Maven resource filtering, so the value always matches {@code <version>} in
 * {@code pom.xml} without anyone having to keep a constant in sync.
 *
 * <p>Lives in its own class rather than in {@code MyChessMain} because both the
 * entry point and {@link UciHandler} need it, and the front-ends sit at the top of
 * the dependency chain — nothing else may depend on them (see the layering note in
 * {@code README.md} § 2.2).
 *
 * <p>Usage:
 * <pre>{@code
 * writeLine("id name myChess " + Version.get());   // -> id name myChess 4.3.4
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class Version {

    /**
     * Returned when {@code version.properties} is absent or unreadable — which in
     * practice only happens when classes are run from outside a Maven build.
     */
    static final String UNKNOWN = "dev";

    private static final String VERSION = load();

    private Version() {
        // static utility
    }

    /** The build-time version, e.g. {@code "4.3.4"}, or {@code "dev"} if unavailable. */
    public static String get() {
        return VERSION;
    }

    private static String load() {
        try (var in = Version.class.getResourceAsStream("/version.properties")) {
            if (in == null) {
                return UNKNOWN;
            }

            var props = new Properties();
            props.load(in);

            return props.getProperty("version", UNKNOWN);
        } catch (IOException e) {
            // The version is cosmetic — a missing or broken resource must never
            // stop the engine from starting.
            return UNKNOWN;
        }
    }
}
