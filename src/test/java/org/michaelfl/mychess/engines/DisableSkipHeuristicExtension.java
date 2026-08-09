package org.michaelfl.mychess.engines;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Switches the skip-hopeless-iteration heuristic off for the entire test run.
 *
 * <p>The heuristic lets {@link PositionSearch} abandon a deepening iteration
 * whose estimated cost exceeds the remaining time budget. That estimate comes
 * from {@link IterationTimings}, whose state is <b>process-static</b>, and it is
 * compared against the wall clock. A search-based test therefore depends on two
 * things it must not depend on: which tests ran before it in the same JVM, and
 * how fast the machine is. The symptom is a test that passes when run alone and
 * fails when run as part of its class — or passes on a fast laptop and fails on
 * a loaded CI box.
 *
 * <p>This is registered by <em>auto-detection</em> rather than by
 * {@code @ExtendWith} on the individual tests, for two reasons: it must cover
 * every test without anyone remembering to opt in, and it must work no matter
 * who launches the tests. A Surefire {@code systemPropertyVariables} entry would
 * not do — IntelliJ runs tests with its own JUnit runner and never reads the
 * Surefire configuration, so the heuristic would stay on in exactly the
 * environment used most.
 *
 * <p>Auto-detection is enabled in {@code src/test/resources/junit-platform.properties};
 * the extension is discovered via {@code META-INF/services}. It lives in the
 * {@code engines} package because the switch is package-private.
 *
 * @author Michael Fleischhauer
 */
public final class DisableSkipHeuristicExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        IterationTimings.setSkipHeuristicEnabled(false);
    }
}
