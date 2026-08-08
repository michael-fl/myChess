package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Driver for the full-joint midgame+endgame piece-square-table Texel tune
 * (see {@link JointMgEgPstTaperedTexelData}). Loads a {@code c9}-labeled EPD
 * dataset (typically the Zurichess + self-play hybrid), runs coordinate descent
 * over all {@value JointMgEgPstTaperedTexelData#PARAM_COUNT} table parameters,
 * re-centers each block to strip the material-leak component, and writes the
 * tuned tables in the paste-ready {@code PieceSquareTables} grid format.
 *
 * <p>Usage: {@code TexelJointMgEgTuner [datasetEpd] [sampleLimit] [outputFile]}.
 *
 * @author Michael Fleischhauer
 */
public final class TexelJointMgEgTuner {

    private static final Path DEFAULT_DATASET = Path.of("tuning-data", "hybrid.epd");
    private static final int DEFAULT_LIMIT = 150_000;
    private static final Path DEFAULT_OUTPUT = Path.of("tuning-data", "joint-mg-eg-tables.txt");

    private TexelJointMgEgTuner() {
        // entry point
    }

    public static void main(String[] args) {
        Path dataset = args.length > 0 ? Path.of(args[0]) : DEFAULT_DATASET;
        int limit = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_LIMIT;
        Path output = args.length > 2 ? Path.of(args[2]) : DEFAULT_OUTPUT;

        System.out.printf("Loading up to %,d samples from %s ...%n", limit, dataset);
        List<Sample> data = JointMgEgPstTaperedTexelData.load(dataset, limit);
        System.out.printf("Loaded %,d samples; tuning %,d parameters.%n",
                data.size(), JointMgEgPstTaperedTexelData.PARAM_COUNT);

        double[] current = JointMgEgPstTaperedTexelData.currentTableValues();
        double initialK = TexelTuner.calibrateK(data, current);
        double initialMse = TexelTuner.meanSquaredError(data, current, initialK);
        System.out.printf("Initial: K=%.6f  MSE=%.8f%n", initialK, initialMse);

        double[] tuned = TexelTuner.tune(data, current, TexelTuner.Config.defaults(),
                (step, error, k) -> System.out.printf("  step=%.3f  MSE=%.8f  K=%.6f%n", step, error, k));

        double tunedK = TexelTuner.calibrateK(data, tuned);
        double tunedMse = TexelTuner.meanSquaredError(data, tuned, tunedK);

        double[] recentered = JointMgEgPstTaperedTexelData.recenterToCurrentMeans(tuned);
        double recenteredK = TexelTuner.calibrateK(data, recentered);
        double recenteredMse = TexelTuner.meanSquaredError(data, recentered, recenteredK);

        System.out.printf("%nTuned (raw):        MSE=%.8f  (improvement %.8f)%n", tunedMse, initialMse - tunedMse);
        System.out.printf("Tuned (recentered): MSE=%.8f  (improvement %.8f)%n", recenteredMse, initialMse - recenteredMse);

        String report = buildReport(current, tuned, recentered);
        System.out.println();
        System.out.println(materialSignalReport(current, tuned));

        writeReport(output, report);
        System.out.printf("%nTuned tables (recentered) written to %s%n", output);
    }

    /**
     * Per-block material signal = the uniform offset the tuner wanted, i.e.
     * {@code mean(tuned) - mean(current)} in centipawns of table value (scaled by
     * positionFactor 0.5 it is the eval offset at that phase). A large value is a
     * material re-rating the recentering strips out — flag it for review.
     */
    private static String materialSignalReport(double[] current, double[] tuned) {
        var report = new StringBuilder("Material-leak signal (tuned block mean - current block mean, table cp):\n");

        for (int piece = 0; piece < JointMgEgPstTaperedTexelData.PIECE_COUNT; piece++) {
            for (int phase = 0; phase < JointMgEgPstTaperedTexelData.PHASE_COUNT; phase++) {
                int base = JointMgEgPstTaperedTexelData.blockBase(piece, phase);
                double shift = blockMean(tuned, base) - blockMean(current, base);

                report.append("  %-6s %-7s : %+7.1f%n".formatted(
                        JointMgEgPstTaperedTexelData.pieceName(piece),
                        JointMgEgPstTaperedTexelData.phaseName(phase),
                        shift));
            }
        }

        return report.toString();
    }

    private static double blockMean(double[] values, int base) {
        double sum = 0.0;

        for (int slot = 0; slot < JointMgEgPstTaperedTexelData.SLOTS_PER_BLOCK; slot++) {
            sum += values[base + slot];
        }

        return sum / JointMgEgPstTaperedTexelData.SLOTS_PER_BLOCK;
    }

    private static String buildReport(double[] current, double[] tuned, double[] recentered) {
        var report = new StringBuilder();

        report.append("# Full-joint MG+EG PST tune — recentered tables (paste into PieceSquareTables).\n");
        report.append("# Each block is a white table, rank 8 first, in centipawns.\n\n");

        for (int piece = 0; piece < JointMgEgPstTaperedTexelData.PIECE_COUNT; piece++) {
            for (int phase = 0; phase < JointMgEgPstTaperedTexelData.PHASE_COUNT; phase++) {
                report.append("## %s %s%n".formatted(
                        JointMgEgPstTaperedTexelData.pieceName(piece),
                        JointMgEgPstTaperedTexelData.phaseName(phase)));
                report.append(JointMgEgPstTaperedTexelData.formatTable(recentered, piece, phase));
                report.append('\n');
            }
        }

        return report.toString();
    }

    private static void writeReport(Path output, String report) {
        try {
            Path parent = output.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(output, report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write " + output, e);
        }
    }
}
