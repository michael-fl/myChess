package org.michaelfl.mychess;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Reads one FEN per line and prints the numbers {@code tools/king-attack-vs-stockfish.py} fits on.
 *
 * <p>Output per line, semicolon-separated:
 * {@code staticEval;phase;unitsWhite;attackersWhite;unitsBlack;attackersBlack;placeboWhite;placeboBlack},
 * or the literal {@code skip} for a line the FEN importer rejects or whose evaluation hits the
 * illegal-position sentinel. The caller relies on one output line per input line to stay aligned
 * with its own copy of the corpus.
 *
 * <p><b>Why a batch driver rather than one process per position.</b> The calibration corpus is
 * 39 619 positions; a JVM start each would dominate the measurement and put it out of reach for
 * routine re-fitting, which is the whole point of having the tool in the tree.
 *
 * <p><b>Why it reads {@link KingAttackUnits} and not the production counters.</b> The evaluation
 * exposes {@code getAttackUnit()} only on branches that carry the king-attack term, and the fit
 * has to be runnable on master — that is where a re-fit would start. {@code KingAttackCurveTest}
 * asserts the two agree wherever both exist, so using the reference implementation here costs
 * nothing.
 *
 * <p>FENs may be classical or Shredder-castling Chess960; {@link Fen#importChess960FEN(String)}
 * accepts both.
 *
 * @author Michael Fleischhauer
 */
public final class KingAttackProbe {

    /** EPD lines carry four FEN fields; the importer wants six. */
    private static final String COUNTER_SUFFIX = " 0 1";

    private static final String SKIP = "skip";

    private KingAttackProbe() {
        // measurement driver
    }

    public static void main(String[] args) throws Exception {
        var evaluator = new WeightingFunction();
        var out = new StringBuilder();

        try (var in = new BufferedReader(new InputStreamReader(System.in))) {
            String line;

            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                System.out.println(describe(line.trim(), evaluator, out));
            }
        }
    }

    private static String describe(String fen, WeightingFunction evaluator, StringBuilder out) {
        Board board;

        try {
            board = Fen.importChess960FEN(fen.split(" ").length < 6 ? fen + COUNTER_SUFFIX : fen);
        } catch (RuntimeException ignore) {
            // A line the importer rejects contributes nothing; the caller counts the skips.
            return SKIP;
        }

        int weight = evaluator.calculate(board);

        if (WeightingFunction.isIllegalWeight(weight)) {
            return SKIP;
        }

        out.setLength(0);
        out.append(weight).append(';').append(evaluator.getPhase());
        append(out, board, GameStatus.TURN_WHITE);
        append(out, board, GameStatus.TURN_BLACK);
        out.append(';').append(KingAttackUnits.ofZone(board, GameStatus.TURN_WHITE,
                        KingAttackUnits.placeboCenter(board, GameStatus.TURN_WHITE)))
           .append(';').append(KingAttackUnits.ofZone(board, GameStatus.TURN_BLACK,
                        KingAttackUnits.placeboCenter(board, GameStatus.TURN_BLACK)));

        return out.toString();
    }

    private static void append(StringBuilder out, Board board, int color) {
        out.append(';').append(KingAttackUnits.of(board, color))
           .append(';').append(KingAttackUnits.attackersOf(board, color));
    }
}
