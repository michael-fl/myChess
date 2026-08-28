package org.michaelfl.mychess;

import java.util.Locale;

/**
 * Counts how often a <em>sound</em> lazy-evaluation cutoff would fire, without acting on it.
 *
 * <p>The last open number before deciding whether lazy evaluation is worth an SPRT. Timing
 * established that a firing cutoff saves 64.4 % of the evaluation at that node; the total saving is
 * that times the firing rate, and nothing so far measures the rate.
 *
 * <p>Counted alongside is the rate of <b>today's</b> stand-pat cutoff, because that is the honest
 * comparison. Today's test is {@code material >= beta} with <b>no margin at all</b> — unsound as a
 * bound, and therefore firing more often than {@code cheapEval - MARGIN >= beta} ever can. If the
 * sound version fires much less, myChess already has the aggressive form of this optimization and
 * the sound one is a downgrade rather than an improvement.
 *
 * <p><b>Gated off at compile time</b> by {@code QuiescenceSearch.COUNT_LAZY_CUTOFFS}, a
 * {@code private static final boolean}, so javac removes every increment and the probing
 * {@code cheapPass} call along with them. Verify with {@code javap -c -p QuiescenceSearch}: with the
 * gate off, no reference to this class may remain. A counting build is deliberately slower — it
 * evaluates the cheap pass a second time purely to ask the question — which does not matter, because
 * the output is a ratio and the search decisions are untouched. The bench signature must stay
 * 1,300,002,835 either way.
 *
 * <p>Not thread-safe, like {@code EvalWorkCounters}: plain {@code static long} for a single-threaded
 * search. This becomes wrong the day a parallel search exists.
 *
 * @author Michael Fleischhauer
 */
final class LazyCutoffCounters {

    /** Quiescence nodes reached — the denominator for every rate below. */
    static long nodes;

    /** Nodes where today's margin-less stand-pat test cuts off: {@code standPat >= beta}. */
    static long standPatCutoffs;

    /** Nodes where a sound cutoff at 172 cp would fire on the fail-high side. */
    static long soundHigh172;

    /** Nodes where a sound cutoff at 172 cp would fire on the fail-low side. */
    static long soundLow172;

    /** The same at 129 cp, the margin the optional row-C refactor would unlock. */
    static long soundHigh129;
    static long soundLow129;

    private LazyCutoffCounters() {
        // counters only
    }

    static void reset() {
        nodes = 0;
        standPatCutoffs = 0;
        soundHigh172 = 0;
        soundLow172 = 0;
        soundHigh129 = 0;
        soundLow129 = 0;
    }

    /**
     * The measured rates, or a note that counting was compiled out.
     *
     * @return a multi-line report, ready to print
     */
    static String report() {
        if (nodes == 0) {
            return "no quiescence nodes counted — is QuiescenceSearch.COUNT_LAZY_CUTOFFS still false?";
        }

        double n = nodes;
        double savedPerFiring = 0.644;

        return String.format(Locale.ROOT, """
                        quiescence nodes            : %,d
                        ----
                        today's stand-pat cutoff    : %,12d  = %5.1f %% of nodes  (no margin, unsound)
                        ----
                        sound lazy, MARGIN = 172 cp
                          fail-high                 : %,12d  = %5.1f %%
                          fail-low                  : %,12d  = %5.1f %%
                          either                    : %,12d  = %5.1f %%  -> saves %4.1f %% of evaluation time
                        ----
                        sound lazy, MARGIN = 129 cp (needs the row-C refactor)
                          fail-high                 : %,12d  = %5.1f %%
                          fail-low                  : %,12d  = %5.1f %%
                          either                    : %,12d  = %5.1f %%  -> saves %4.1f %% of evaluation time""",
                nodes,
                standPatCutoffs, 100 * standPatCutoffs / n,
                soundHigh172, 100 * soundHigh172 / n,
                soundLow172, 100 * soundLow172 / n,
                soundHigh172 + soundLow172, 100 * (soundHigh172 + soundLow172) / n,
                100 * savedPerFiring * (soundHigh172 + soundLow172) / n,
                soundHigh129, 100 * soundHigh129 / n,
                soundLow129, 100 * soundLow129 / n,
                soundHigh129 + soundLow129, 100 * (soundHigh129 + soundLow129) / n,
                100 * savedPerFiring * (soundHigh129 + soundLow129) / n);
    }
}
