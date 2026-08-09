package org.michaelfl.mychess.engines;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link DisableSkipHeuristicExtension} is wired up and has
 * actually run.
 *
 * <p>The extension is what keeps every search-based test in this suite
 * deterministic (see its class documentation). It is registered by
 * auto-detection, which means nothing references it from code — so if a
 * resource file is lost in a refactoring or a merge, it simply stops applying
 * and the only symptom is tests that occasionally fail depending on the order
 * they run in. That failure mode is expensive to diagnose and easy to blame on
 * the engine instead of the setup.
 *
 * <p>The three tests below separate the failure modes on purpose:
 * {@link #autoDetectionIsEnabled} and {@link #extensionIsRegisteredAsAService}
 * check the two halves of the wiring, so a red test names the missing file
 * directly; {@link #extensionHasRun} checks the effect and would also catch an
 * extension that is registered but no longer does its job.
 *
 * @author Michael Fleischhauer
 */
class DisableSkipHeuristicExtensionTest {

    private static final String PROPERTIES_RESOURCE = "junit-platform.properties";
    private static final String AUTODETECTION_KEY = "junit.jupiter.extensions.autodetection.enabled";
    private static final String SERVICE_RESOURCE = "META-INF/services/org.junit.jupiter.api.extension.Extension";

    @Test
    void autoDetectionIsEnabled() {
        var properties = new Properties();

        try (InputStream in = classLoader().getResourceAsStream(PROPERTIES_RESOURCE)) {
            assertNotNull(in, PROPERTIES_RESOURCE + " must be on the test classpath; without it JUnit never looks "
                    + "for auto-detected extensions and DisableSkipHeuristicExtension is silently ignored");
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + PROPERTIES_RESOURCE, e);
        }

        assertEquals("true", properties.getProperty(AUTODETECTION_KEY),
                AUTODETECTION_KEY + " must be true in " + PROPERTIES_RESOURCE);
    }

    @Test
    void extensionIsRegisteredAsAService() {
        List<String> registered = readServiceEntries();

        assertTrue(registered.contains(DisableSkipHeuristicExtension.class.getName()),
                DisableSkipHeuristicExtension.class.getName() + " must be listed in " + SERVICE_RESOURCE
                        + "; found instead: " + registered);
    }

    /**
     * The effect, and the assertion that matters most: nothing else in the
     * codebase switches the heuristic off, so this can only hold if the
     * extension ran before this class.
     */
    @Test
    void extensionHasRun() {
        assertFalse(IterationTimings.isSkipHeuristicEnabled(),
                "the whole test suite must run with the skip-hopeless-iteration heuristic off, otherwise a "
                        + "search-based test depends on which tests ran before it in this JVM and on how fast "
                        + "this machine is");
    }

    private static List<String> readServiceEntries() {
        var entries = new ArrayList<String>();

        try {
            for (URL url : Collections.list(classLoader().getResources(SERVICE_RESOURCE))) {
                try (var reader = new InputStreamReader(url.openStream(), StandardCharsets.UTF_8);
                     var buffered = new BufferedReader(reader)) {
                    buffered.lines()
                            .map(String::trim)
                            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                            .forEach(entries::add);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + SERVICE_RESOURCE, e);
        }

        return entries;
    }

    private static ClassLoader classLoader() {
        return DisableSkipHeuristicExtensionTest.class.getClassLoader();
    }
}
