package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class LogTest {

    private static final String SAMPLE_MESSAGE = "hello, log";

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
    private final ByteArrayOutputStream capturedErr = new ByteArrayOutputStream();

    @BeforeEach
    void redirectStreams() {
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
        Log.setMode(Log.Mode.REPL);
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        Log.setMode(Log.Mode.REPL);
    }

    @Test
    void infoInReplModeWritesToStdout() {
        Log.setMode(Log.Mode.REPL);

        Log.info(SAMPLE_MESSAGE);

        assertTrue(capturedOut.toString().contains(SAMPLE_MESSAGE),
                "REPL-mode info should appear on stdout, got: " + capturedOut);
        assertFalse(capturedErr.toString().contains(SAMPLE_MESSAGE),
                "REPL-mode info must not leak to stderr");
    }

    @Test
    void infoInUciModeWritesToStderr() {
        Log.setMode(Log.Mode.UCI);

        Log.info(SAMPLE_MESSAGE);

        assertTrue(capturedErr.toString().contains(SAMPLE_MESSAGE),
                "UCI-mode info should appear on stderr, got: " + capturedErr);
        assertFalse(capturedOut.toString().contains(SAMPLE_MESSAGE),
                "UCI-mode info must NEVER pollute stdout (would break UCI protocol)");
    }

    @Test
    void errorAlwaysWritesToStderr() {
        Log.setMode(Log.Mode.REPL);
        Log.error("oops repl");

        Log.setMode(Log.Mode.UCI);
        Log.error("oops uci");

        assertTrue(capturedErr.toString().contains("oops repl"), "REPL-mode error on stderr");
        assertTrue(capturedErr.toString().contains("oops uci"), "UCI-mode error on stderr");
        assertFalse(capturedOut.toString().contains("oops"),
                "error must never go to stdout in any mode");
    }

    @Test
    void errorWithThrowableIncludesStackTrace() {
        var cause = new IllegalStateException("boom");

        Log.error("failure occurred", cause);

        var err = capturedErr.toString();
        assertTrue(err.contains("failure occurred"), "stderr must contain the message");
        assertTrue(err.contains("IllegalStateException"), "stderr must contain the exception class");
        assertTrue(err.contains("boom"), "stderr must contain the exception message");
        assertTrue(err.contains("at "), "stderr must contain at least one stack frame");
    }

    @Test
    void modeToggleRoutesCorrectly() {
        Log.setMode(Log.Mode.UCI);
        Log.info("uci-msg");
        Log.setMode(Log.Mode.REPL);
        Log.info("repl-msg");

        assertTrue(capturedErr.toString().contains("uci-msg"), "first message on stderr");
        assertFalse(capturedOut.toString().contains("uci-msg"), "first message not on stdout");
        assertTrue(capturedOut.toString().contains("repl-msg"), "second message on stdout");
        assertFalse(capturedErr.toString().contains("repl-msg"), "second message not on stderr");
    }

    @Test
    void getModeReturnsCurrentMode() {
        Log.setMode(Log.Mode.REPL);
        assertEquals(Log.Mode.REPL, Log.getMode(), "getMode after setMode(REPL)");

        Log.setMode(Log.Mode.UCI);
        assertEquals(Log.Mode.UCI, Log.getMode(), "getMode after setMode(UCI)");
    }
}
