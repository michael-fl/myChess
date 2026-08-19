package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Version}, which also cover the Maven side of it: the value comes
 * from {@code version.properties} through resource filtering, so a broken filtering
 * setup would silently degrade every version report to {@code "dev"} — in the startup
 * banner and, since it feeds {@code id name}, in cutechess logs and PGN metadata too.
 *
 * @author Michael Fleischhauer
 */
class VersionTest {

    @Test
    void get_inAMavenBuild_returnsTheFilteredPomVersion() {
        String version = Version.get();

        assertNotEquals(Version.UNKNOWN, version,
                "version.properties must be on the classpath and filtered; getting \"" + Version.UNKNOWN
                        + "\" means Maven resource filtering is not doing its job");
        // The qualifier may itself contain hyphens and dots: Maven's own scheme has
        // 1.0-alpha-1 and 1.0-beta-2 as canonical examples, and branch builds such as
        // 4.5.0-complete-pv follow the same shape. A \\w+ qualifier would reject those.
        assertTrue(version.matches("\\d+(\\.\\d+)+(-[\\w.-]+)?"),
                "the version must look like a Maven version — digits separated by dots, optionally with a "
                        + "qualifier such as -SNAPSHOT or -alpha-1; got " + version);
    }

    @Test
    void get_isStable() {
        //noinspection EqualsWithItself
        assertEquals(Version.get(), Version.get(),
                "the version is read once and cached, so repeated calls must agree");
    }
}
