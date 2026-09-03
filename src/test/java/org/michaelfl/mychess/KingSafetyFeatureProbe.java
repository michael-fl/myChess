package org.michaelfl.mychess;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Reads one FEN per line and prints
 * {@code eval;phase;stormWhite;stormBlack;mobilityWhite;mobilityBlack;placeboWhite;placeboBlack;denseStormWhite;denseStormBlack;fileDangerWhite;fileDangerBlack;splitWhite;splitBlack;placeboFileWhite;placeboFileBlack}.
 *
 * <p>The evaluation and phase come first because the screen needs them for every position anyway:
 * its target is Stockfish's static evaluation minus this one. They used to come from a second
 * probe, which tied the whole screen to a class that exists only on the {@code attack-units}
 * branch — so on {@code master}, where the screen is meant to run, it could not start at all.
 * One probe also means one JVM launch instead of two.
 *
 * <p>Each value is the danger to <em>that color's own</em> king, which is the natural reading
 * for both quantities and the opposite of {@link KingAttackUnits#of}, where the argument names
 * the attacker. The caller flips the sign accordingly.
 *
 * <p>Prints {@code skip} for a line the importer rejects, one output line per input line, so the
 * caller stays aligned with its own copy of the corpus.
 *
 * @author Michael Fleischhauer
 */
public final class KingSafetyFeatureProbe {

    /** EPD lines carry four FEN fields; the importer wants six. */
    private static final String COUNTER_SUFFIX = " 0 1";

    private KingSafetyFeatureProbe() {
        // measurement driver
    }

    public static void main(String[] args) throws Exception {
        try (var in = new BufferedReader(new InputStreamReader(System.in))) {
            String line;

            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                System.out.println(describe(line.trim()));
            }
        }
    }

    private static String describe(String fen) {
        Board board;

        try {
            board = Fen.importChess960FEN(fen.split(" ").length < 6 ? fen + COUNTER_SUFFIX : fen);
        } catch (RuntimeException ignore) {
            // A line the importer rejects contributes nothing; the caller counts the skips.
            return "skip";
        }

        final var evaluator = new WeightingFunction();
        final int weight = evaluator.calculate(board);

        return weight + ";" + evaluator.getPhase()
                + ";" + KingSafetyFeatures.stormAgainst(board, GameStatus.TURN_WHITE)
                + ";" + KingSafetyFeatures.stormAgainst(board, GameStatus.TURN_BLACK)
                + ";" + KingSafetyFeatures.virtualQueenMobility(board, GameStatus.TURN_WHITE)
                + ";" + KingSafetyFeatures.virtualQueenMobility(board, GameStatus.TURN_BLACK)
                + ";" + KingSafetyFeatures.placeboQueenMobility(board, GameStatus.TURN_WHITE)
                + ";" + KingSafetyFeatures.placeboQueenMobility(board, GameStatus.TURN_BLACK)
                + ";" + KingSafetyFeatures.denseStormAgainst(board, GameStatus.TURN_WHITE)
                + ";" + KingSafetyFeatures.denseStormAgainst(board, GameStatus.TURN_BLACK)
                + ";" + KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_WHITE)
                + ";" + KingSafetyFeatures.fileDangerAround(board, GameStatus.TURN_BLACK)
                + ";" + KingSafetyFeatures.fileDangerSplitAround(board, GameStatus.TURN_WHITE)
                + ";" + KingSafetyFeatures.fileDangerSplitAround(board, GameStatus.TURN_BLACK)
                + ";" + KingSafetyFeatures.placeboFileDanger(board, GameStatus.TURN_WHITE)
                + ";" + KingSafetyFeatures.placeboFileDanger(board, GameStatus.TURN_BLACK);
    }
}
