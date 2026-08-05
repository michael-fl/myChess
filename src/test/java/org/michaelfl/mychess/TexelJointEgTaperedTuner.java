package org.michaelfl.mychess;

import org.michaelfl.mychess.tuning.TexelTuner;
import org.michaelfl.mychess.tuning.TexelTuner.Sample;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line runner for the offline joint Texel tuning of the <b>endgame</b>
 * piece-square tables of knight, bishop, rook and queen (midgame tables held
 * fixed) via the phase-aware {@link JointEndgamePstTaperedTexelData} adapter.
 * Counterpart of {@link TexelPawnTaperedTuner} / {@link TexelKingTaperedTuner},
 * bundling the four remaining pieces into one 128-parameter tune.
 *
 * <p>Usage: {@code TexelJointEgTaperedTuner [epdFile] [maxSamples]} — defaults to
 * {@code tuning-data/quiet-labeled.epd} and all samples. Loads the labeled
 * positions, splits them 90/10 into a training and a validation set, tunes the
 * four endgame tables on the training set, reports the mean squared error
 * before/after on both sets, and prints each tuned table ready to paste into
 * {@link PieceSquareTables} (that piece's endgame table) alongside a
 * human-readable view of its actual eval contribution (table value ×
 * positionFactor 0.5).
 *
 * <p>The tuning objective (MSE against game outcomes) is only a proxy: the tuned
 * tables MUST be confirmed with a real cutechess match against the current
 * baseline (v4.3.1) before shipping.
 *
 * @author Michael Fleischhauer
 */
public final class TexelJointEgTaperedTuner {

    private static final int VALIDATION_EVERY = 10;

    private TexelJointEgTaperedTuner() {
        // entry point only
    }

    public static void main(String[] args) {
        Path epd = Path.of(args.length > 0 ? args[0] : "tuning-data/quiet-labeled.epd");
        int maxSamples = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.printf("Loading up to %,d positions from %s ...%n", maxSamples, epd);
        List<Sample> all = JointEndgamePstTaperedTexelData.load(epd, maxSamples);
        System.out.printf("Loaded %,d samples%n", all.size());

        var training = new ArrayList<Sample>();
        var validation = new ArrayList<Sample>();

        for (int i = 0; i < all.size(); i++) {
            (i % VALIDATION_EVERY == 0 ? validation : training).add(all.get(i));
        }

        System.out.printf("train=%,d  validation=%,d%n%n", training.size(), validation.size());

        double[] start = JointEndgamePstTaperedTexelData.currentTableValues();
        double kStart = TexelTuner.calibrateK(training, start);
        System.out.printf("start:  K=%.6f  trainMSE=%.6f  valMSE=%.6f%n",
                kStart,
                TexelTuner.meanSquaredError(training, start, kStart),
                TexelTuner.meanSquaredError(validation, start, kStart));

        var config = new TexelTuner.Config(8.0, 0.5, 30);
        long startedMs = System.currentTimeMillis();

        double[] tuned = TexelTuner.tune(training, start, config,
                (step, error, k) -> System.out.printf("  step=%.2f  trainMSE=%.6f  K=%.6f%n", step, error, k));

        double seconds = (System.currentTimeMillis() - startedMs) / 1000.0;
        double kTuned = TexelTuner.calibrateK(training, tuned);

        System.out.printf("%ntuned:  K=%.6f  trainMSE=%.6f  valMSE=%.6f  (%.1fs)%n",
                kTuned,
                TexelTuner.meanSquaredError(training, tuned, kTuned),
                TexelTuner.meanSquaredError(validation, tuned, kTuned),
                seconds);

        for (int pieceIndex = 0; pieceIndex < JointEndgamePstTaperedTexelData.PIECE_COUNT; pieceIndex++) {
            String name = JointEndgamePstTaperedTexelData.pieceName(pieceIndex);

            System.out.printf("%n=== tuned white %s ENDGAME table (paste into PieceSquareTables, %s endgame table) ===%n",
                    name, name);
            System.out.print(JointEndgamePstTaperedTexelData.formatEndgameTable(tuned, pieceIndex));

            System.out.printf("%n--- %s: readable eval contribution per square (cp, ranks 8..1), value x positionFactor 0.5 ---%n", name);
            System.out.println("start (current):");
            System.out.print(JointEndgamePstTaperedTexelData.formatEndgameContributions(start, pieceIndex));
            System.out.println("tuned:");
            System.out.print(JointEndgamePstTaperedTexelData.formatEndgameContributions(tuned, pieceIndex));
        }
    }
}
