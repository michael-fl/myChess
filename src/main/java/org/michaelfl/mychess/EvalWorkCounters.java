package org.michaelfl.mychess;

import java.util.Locale;

/**
 * Counters for the work {@link WeightingFunction} does, so the cost of a cheap evaluation pass can
 * be quantified as a <b>count</b> rather than as a wall-clock time.
 *
 * <p>A count is reproducible, machine-independent and immune to CPU contention — the same reason
 * {@code docs/bench-history.md} rests on node counts and forbids comparing times. It makes the
 * question "how much work would lazy evaluation skip?" answerable while a time-controlled SPRT is
 * running, which a timing measurement cannot be.
 *
 * <p><b>Gated off at compile time.</b> Every increment sits behind
 * {@code WeightingFunction.COUNT_EVAL_WORK}, a {@code private static final boolean} — a
 * compile-time constant, so javac removes the guarded code outright rather than leaving a field
 * read and a branch in the hot path. Verify with {@code javap -c -p WeightingFunction}: with the
 * gate off, the bytecode must contain no reference to this class. Flip the constant and rebuild for
 * a measurement build, the same procedure the threshold experiments use.
 *
 * <p><b>Not thread-safe, deliberately.</b> Plain {@code static long} without synchronization,
 * because myChess's search is single-threaded (see {@code CLAUDE.md}) and so is every measurement
 * driver. This becomes wrong the day a parallel search exists — at which point these must either
 * become per-thread or the counting must move into the search's own {@code Statistics}.
 *
 * <p>Static rather than per-instance because {@code WeightingFunction} is constructed per search
 * and per driver, while the useful figure is an average over many positions.
 *
 * @author Michael Fleischhauer
 */
final class EvalWorkCounters {

    /** Invocations of {@code WeightingFunction.calculate} — the denominator for every average. */
    static long evalCalls;

    /** Invocations of the per-piece dispatch, i.e. pieces found on the board. */
    static long pieceWalks;

    /**
     * Board squares inspected by the walk-dependent work: the sliding/stepping generator for
     * non-pawn pieces plus every square a pawn routine looks at. The dominant expensive unit, and
     * the one a cheap pass skips entirely.
     */
    static long squareProbes;

    /**
     * Copies of the board into {@code tempBoard}. 144 bytes per evaluation, needed only by the
     * undefended-pieces machinery, so a cheap pass skips it too.
     */
    static long tempBoardCopies;

    /** Fields scanned by {@code calculateUndefendedPiecesCount}. */
    static long undefendedScanFields;

    /**
     * Fields scanned by the doubled-pawn file walk. Counted separately because doubled pawns are a
     * <i>cheap</i> term that nevertheless lives inside the pawn routine — the entanglement that
     * makes row C of the margin analysis unreachable without a small refactor.
     */
    static long doubledPawnScanFields;

    private EvalWorkCounters() {
        // counters only
    }

    static void reset() {
        evalCalls = 0;
        pieceWalks = 0;
        squareProbes = 0;
        tempBoardCopies = 0;
        undefendedScanFields = 0;
        doubledPawnScanFields = 0;
    }

    /**
     * Per-evaluation averages, or a note that counting was compiled out.
     *
     * @return a multi-line report, ready to print
     */
    static String report() {
        if (evalCalls == 0) {
            return "no evaluations counted — is WeightingFunction.COUNT_EVAL_WORK still false?";
        }

        double calls = evalCalls;

        return String.format(Locale.ROOT, """
                        evaluations counted    : %,d
                        pieces walked          : %,12d  = %7.2f per evaluation
                        square probes          : %,12d  = %7.2f per evaluation, %5.2f per piece
                        tempBoard copies       : %,12d  = %7.2f per evaluation
                        undefended scan fields : %,12d  = %7.2f per evaluation
                        doubled-pawn scan      : %,12d  = %7.2f per evaluation
                        ----
                        skippable by a cheap pass (probes + copies + both scans):
                                                 %,12d  = %7.2f per evaluation
                        still paid by a cheap pass (the piece loop itself):
                                                 %,12d  = %7.2f per evaluation""",
                evalCalls,
                pieceWalks, pieceWalks / calls,
                squareProbes, squareProbes / calls, pieceWalks == 0 ? 0 : (double) squareProbes / pieceWalks,
                tempBoardCopies, tempBoardCopies / calls,
                undefendedScanFields, undefendedScanFields / calls,
                doubledPawnScanFields, doubledPawnScanFields / calls,
                skippable(), skippable() / calls,
                pieceWalks, pieceWalks / calls);
    }

    private static long skippable() {
        return squareProbes + tempBoardCopies + undefendedScanFields + doubledPawnScanFields;
    }
}
