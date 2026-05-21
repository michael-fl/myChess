package org.michaelfl.mychess;

/**
 * Minimal logging chokepoint for non-protocol output. Routes informational
 * messages to stdout in REPL mode and to stderr in UCI mode so that the UCI
 * protocol stream on stdout stays clean. Errors always go to stderr.
 *
 * <p>Single mutable mode flag, switched at process start by {@code MyChessMain}
 * depending on whether {@code uci} was passed as a CLI argument. No log levels,
 * no file backends; if more is ever needed it's a local refactor.
 *
 * @author Michael Fleischhauer
 */
public final class Log {

    /** Output routing mode. */
    public enum Mode { REPL, UCI }

    private static volatile Mode mode = Mode.REPL;

    private Log() {
        throw new IllegalStateException("Utility class");
    }

    /** Switch routing. UCI mode keeps stdout reserved for the UCI protocol. */
    public static void setMode(Mode m) {
        mode = m;
    }

    public static Mode getMode() {
        return mode;
    }

    /** Informational message. REPL mode: stdout. UCI mode: stderr. */
    public static void info(String msg) {
        var out = (mode == Mode.UCI) ? System.err : System.out;
        out.println(msg);
        out.flush();
    }

    /** Error or warning. Always stderr — irrelevant for the UCI protocol either way. */
    public static void error(String msg) {
        System.err.println(msg);
        System.err.flush();
    }

    /** Error or warning with cause; prints the stack trace to stderr. */
    public static void error(String msg, Throwable t) {
        System.err.println(msg);
        t.printStackTrace(System.err);
        System.err.flush();
    }
}
