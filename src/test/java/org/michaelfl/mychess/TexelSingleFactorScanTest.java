package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
class TexelSingleFactorScanTest {

    private static final double K = 0.01;

    @Test
    void project_foldsTheFixedFactorsIntoTheBaseEvaluation() {
        var sample = new Sample(10, new double[] {2, 3, 5}, 1.0);

        List<Sample> projected = TexelSingleFactorScan.project(List.of(sample), 1, new double[] {7, 100, 11});

        assertEquals(10 + 2 * 7 + 5 * 11, projected.getFirst().baseEval(), 1e-9,
                "base = old base plus the contributions of the two held factors");
        assertArrayEquals(new double[] {3}, projected.getFirst().features(), 1e-9,
                "only the free factor's feature is left");
        assertEquals(1.0, projected.getFirst().result(), 1e-9, "the result is carried over");
    }

    @Test
    void project_leavesTheEvaluationUnchangedAtTheHeldValue() {
        var sample = new Sample(-40, new double[] {4, -6, 9}, 0.5);
        double[] factors = {0.5, 2.0, 0.25};

        Sample projected = TexelSingleFactorScan.project(List.of(sample), 2, factors).getFirst();

        assertEquals(TexelTuner.evaluate(sample, factors), TexelTuner.evaluate(projected, new double[] {factors[2]}), 1e-9,
                "the projection must not change the evaluation at the factors' own values");
    }

    @Test
    void minimize_findsTheFactorTheResultsWereGeneratedWith() {
        double trueFactor = 0.0235;
        var data = new ArrayList<Sample>();

        for (int i = -50; i <= 50; i++) {
            double feature = i * 40.0;
            double base = (i % 7) * 15.0;
            double expected = TexelTuner.sigmoid(K * (base + feature * trueFactor));

            data.add(new Sample(base, new double[] {feature}, expected));
        }

        double found = TexelSingleFactorScan.minimize(data, K, 0.0, 0.1);

        assertEquals(trueFactor, found, 1e-6, "the error is minimal exactly at the generating factor");
    }

    @Test
    void minimize_staysInsideTheBracketWhenTheOptimumLiesBeyondIt() {
        var data = List.of(new Sample(0, new double[] {100}, 1.0), new Sample(0, new double[] {-100}, 0.0));

        double found = TexelSingleFactorScan.minimize(data, K, 0.0, 0.5);

        assertEquals(0.5, found, 1e-6, "a monotone error ends at the bracket's edge, not outside it");
    }

    @Test
    void bootstrap_scattersAroundTheGeneratingFactor() {
        double trueFactor = 0.02;
        var random = new Random(7);
        var data = new ArrayList<Sample>();

        for (int i = 0; i < 4000; i++) {
            double feature = random.nextGaussian() * 60;
            double base = random.nextGaussian() * 80;
            double p = TexelTuner.sigmoid(K * (base + feature * trueFactor));

            data.add(new Sample(base, new double[] {feature}, random.nextDouble() < p ? 1.0 : 0.0));
        }

        double[] optima = TexelSingleFactorScan.bootstrap(data, K, 0.1, 30, 20, new Random(1));

        assertEquals(30, optima.length, "one optimum per replicate");
        assertTrue(optima[0] <= optima[optima.length - 1], "optima come back sorted");
        assertTrue(TexelSingleFactorScan.percentile(optima, 0.025) < trueFactor
                && trueFactor < TexelSingleFactorScan.percentile(optima, 0.975),
                "the 95 % range of the replicates covers the generating factor");
    }

    @Test
    void percentile_usesTheNearestRank() {
        double[] sorted = {1, 2, 3, 4};

        assertEquals(1, TexelSingleFactorScan.percentile(sorted, 0.0), 1e-9, "the lowest rank at 0");
        assertEquals(2, TexelSingleFactorScan.percentile(sorted, 0.5), 1e-9, "the median rank of four values");
        assertEquals(4, TexelSingleFactorScan.percentile(sorted, 1.0), 1e-9, "the highest rank at 1");
    }
}
