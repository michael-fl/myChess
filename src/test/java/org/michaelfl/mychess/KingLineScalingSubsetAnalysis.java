package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Config;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gives the opponent-material scaling a fair hearing, where a corpus-wide mean could not.
 *
 * <h2>Why the first comparison was not a fair test</h2>
 *
 * <p>Tuning each scaling over all ~1.5 M positions returned gains of 0.00053 (phase), 0.00052
 * (opponent non-pawn) and 0.00051 (opponent heavy) — differences of about 4 % of the gain, which
 * is not a result. But the scalings only disagree where the material they read disagrees: the
 * standard phase counts both sides' non-pawn material, the opponent variants count one side's. In
 * a position where both sides have similar material they are the same number, contribute the same
 * error, and dilute the comparison. A large effect on a small subset is invisible in that average.
 *
 * <h2>What this measures instead</h2>
 *
 * <p>Positions are bucketed by <b>divergence</b> — how far the phase scale is from the
 * opponent-heavy scale for the more affected king, in 0..1 — and the comparison is repeated inside
 * each bucket. If the opponent scalings are right, their advantage has to appear where the
 * scalings actually differ. If it does not appear even there, the argument is refuted rather than
 * merely undetected.
 *
 * <p>Two readings per bucket, because they answer different things:
 *
 * <ul>
 *   <li><b>with the globally tuned tables</b> — how the shippable candidates behave here, using
 *       tables fitted over the whole corpus;</li>
 *   <li><b>tuned inside the bucket</b> — the best each scaling could do if this were the only
 *       thing that mattered. Reported with the bucket's size, because a table fitted to a small
 *       subset overfits it and its error is optimistic.</li>
 * </ul>
 *
 * <p>A proxy objective, not Elo, and a subset of a proxy at that. It decides which scaling belongs
 * in a match candidate; the match prices it.
 *
 * <pre>
 * java -Xmx8g -cp target/classes:target/test-classes:target/dependency/* \
 *      org.michaelfl.mychess.KingLineScalingSubsetAnalysis tuning-data/hybrid.epd
 * </pre>
 *
 * @author Michael Fleischhauer
 */
public final class KingLineScalingSubsetAnalysis {

    private static final double[] BUCKET_EDGES = {0.0, 0.05, 0.15, 0.30, 0.50, 1.01};
    private static final int MIN_FOR_LOCAL_TUNE = 20_000;
    private static final Config SCHEDULE = new Config(32.0, 0.5, 24);

    private KingLineScalingSubsetAnalysis() {
        // measurement driver
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : "tuning-data/hybrid.epd");
        int maxSamples = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.printf(Locale.ROOT, "loading %s ...%n", epd);
        List<KingLineTableTexelData.Row> rows = KingLineTableTexelData.loadAll(epd, maxSamples);
        System.out.printf(Locale.ROOT, "%,d usable positions%n%n", rows.size());

        var scalings = KingLineTableTexelData.Scaling.values();
        double[] shipped = KingLineTableTexelData.currentParameters();

        // Tables fitted over the whole corpus, one per scaling — the shippable candidates.
        var global = new double[scalings.length][];

        for (var scaling : scalings) {
            List<Sample> all = samples(rows, scaling.ordinal());
            global[scaling.ordinal()] = TexelTuner.tune(all,
                    new double[KingLineTableTexelData.PARAMETERS], SCHEDULE, (a, b, c) -> { });
        }

        System.out.println("divergence = |phase scale - opponent-heavy scale| for the more"
                + " affected king\n");

        for (int b = 0; b + 1 < BUCKET_EDGES.length; b++) {
            final double low = BUCKET_EDGES[b];
            final double high = BUCKET_EDGES[b + 1];
            List<KingLineTableTexelData.Row> bucket = new ArrayList<>();

            for (var row : rows) {
                if (row.divergence() >= low && row.divergence() < high) {
                    bucket.add(row);
                }
            }

            System.out.printf(Locale.ROOT, "=== divergence %.2f - %.2f === %,d positions (%.1f %%)%n",
                    low, high, bucket.size(), 100.0 * bucket.size() / rows.size());

            if (bucket.isEmpty()) {
                System.out.println();
                continue;
            }

            System.out.printf(Locale.ROOT, "%-22s%16s%18s%18s%n",
                    "scaling", "no term", "global table", "tuned in bucket");
            System.out.println("-".repeat(74));

            for (var scaling : scalings) {
                List<Sample> here = samples(bucket, scaling.ordinal());
                double[] zero = new double[KingLineTableTexelData.PARAMETERS];
                double k = TexelTuner.calibrateK(here, zero);
                double termless = TexelTuner.meanSquaredError(here, zero, k);
                double globalMse = TexelTuner.meanSquaredError(here, global[scaling.ordinal()],
                        TexelTuner.calibrateK(here, global[scaling.ordinal()]));
                String local = "  (too few)";

                if (bucket.size() >= MIN_FOR_LOCAL_TUNE) {
                    double[] tuned = TexelTuner.tune(here, zero, SCHEDULE, (a, c, d) -> { });
                    local = String.format(Locale.ROOT, "%18.8f",
                            TexelTuner.meanSquaredError(here, tuned, TexelTuner.calibrateK(here, tuned)));
                }

                System.out.printf(Locale.ROOT, "%-22s%16.8f%18.8f%s%n",
                        scaling, termless, globalMse, local);
            }

            System.out.printf(Locale.ROOT, "  shipped table under PHASE: %.8f%n%n",
                    TexelTuner.meanSquaredError(samples(bucket, 0), shipped,
                            TexelTuner.calibrateK(samples(bucket, 0), shipped)));
        }

        System.out.println("""
                How to read it. Compare each scaling's 'global table' column against its own
                'no term' column in the SAME bucket — that difference is what the term buys there.
                Comparing across scalings without that anchor is meaningless, because the three
                have different termless baselines inside a bucket even though they share one over
                the whole corpus.

                If the opponent scalings only win in the high-divergence buckets, the phase scaling
                is firing the term where nothing can exploit an open file, exactly as suspected —
                and the corpus-wide mean was blind to it because those positions are a minority.
                If they do not win even there, the modelling argument is refuted.

                'tuned in bucket' overfits by construction and is the optimistic bound, not the
                candidate.""");
    }

    private static List<Sample> samples(List<KingLineTableTexelData.Row> rows, int scaling) {
        var out = new ArrayList<Sample>(rows.size());

        for (var row : rows) {
            out.add(new Sample(row.baseEval(), row.featuresByScaling()[scaling], row.result()));
        }

        return out;
    }
}
