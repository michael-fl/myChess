package org.michaelfl.mychess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Chess960 weakness, stage 2: which evaluation terms make myChess prefer a worse move?
 *
 * <p>Reads the CSV written by {@code myChess-Lab/scripts/c960-stage2-positions.py}: per position the
 * position after the move myChess played and after Stockfish's best move. For every row where the
 * two differ, both positions are evaluated statically with {@link WeightingFunction#analyzeFactors}
 * and split into the tunable factor terms (factor value times coefficient), material, and the
 * rest (every untuned term). Printed per term, from the mover's point of view: the mean
 * of (term after the played move − term after the best move), and how often the term favored the
 * played move. A term with a large positive mean is one that "talks the bad move up".
 *
 * <p>With {@code detail} as the second argument, one {@code DETAIL;game;move;color;played;best;piece;pstDiff}
 * line per row is printed as well.
 *
 * <p>Not a test: run it with
 * {@code mvn -q exec:java -Dexec.classpathScope=test -Dexec.mainClass=org.michaelfl.mychess.Chess960EvalTermProbe -Dexec.args=<csv>}.
 *
 * @author Michael Fleischhauer
 */
public final class Chess960EvalTermProbe {

    private Chess960EvalTermProbe() {
        // command-line probe
    }

    /**
     * Prints the per-term comparison for the CSV given as the first argument.
     *
     * @param args the path of the positions CSV
     * @throws IOException if the CSV cannot be read
     */
    public static void main(String[] args) throws IOException {
        List<String> lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        boolean detail = args.length > 1 && "detail".equals(args[1]);
        String[] names = WeightingFunction.TUNABLE_FACTOR_NAMES;
        double[] factors = WeightingFunction.tunableFactorValues();
        int terms = names.length + 2;
        var pstByPiece = new java.util.TreeMap<Character, double[]>();
        double[] sumDiff = new double[terms];
        int[] favorsPlayed = new int[terms];
        int rows = 0;
        int skipped = 0;
        var evaluator = new WeightingFunction();

        for (String line : lines) {
            String[] f = line.split(";");

            if (f.length < 8 || f[3].equals(f[4])) {
                continue;
            }

            double sign = "W".equals(f[2]) ? 1 : -1;
            double[] played;
            double[] best;

            try {
                played = terms(evaluator, f[6], factors);
                best = terms(evaluator, f[7], factors);
            } catch (IllegalArgumentException e) {
                skipped++;
                continue;
            }

            for (int i = 0; i < terms; i++) {
                double diff = sign * (played[i] - best[i]);
                sumDiff[i] += diff;

                if (diff > 0) {
                    favorsPlayed[i]++;
                }
            }

            // positionFactor difference, grouped by the piece the played move moved
            char piece = Character.toUpperCase(pieceOn(f[6], f[3].substring(2, 4)));
            double[] acc = pstByPiece.computeIfAbsent(piece, k -> new double[3]);
            double pstDiff = sign * (played[0] - best[0]);
            acc[0] += pstDiff;
            acc[1]++;
            acc[2] += pstDiff > 0 ? 1 : 0;

            if (detail) {
                System.out.printf(Locale.ROOT, "DETAIL;%s;%s;%s;%s;%s;%c;%.1f%n",
                        f[0], f[1], f[2], f[3], f[4], piece, pstDiff);
            }

            rows++;
        }

        System.out.printf(Locale.ROOT, "positions where the played move differs from Stockfish's best: %d (skipped %d)%n",
                rows, skipped);
        System.out.println("term                      mean(played - best), cp   favors the played move");

        for (int i = 0; i < terms; i++) {
            String name = i < names.length ? names[i] : i == names.length ? "material" : "untuned rest";
            System.out.printf(Locale.ROOT, "%-25s %+10.1f                  %5.1f %%%n",
                    name, sumDiff[i] / Math.max(rows, 1), 100.0 * favorsPlayed[i] / Math.max(rows, 1));
        }

        System.out.println();
        System.out.println("positionFactor term by the piece the played move moved:");
        System.out.println("piece   moves   mean(played - best), cp   favors the played move");

        for (var entry : pstByPiece.entrySet()) {
            double[] acc = entry.getValue();
            System.out.printf(Locale.ROOT, "%-7s %5.0f   %+10.1f                %5.1f %%%n",
                    entry.getKey(), acc[1], acc[0] / acc[1], 100.0 * acc[2] / acc[1]);
        }
    }

    /** The FEN piece letter on {@code square} (e.g. "e4") of {@code fen}, or '?' if empty. */
    private static char pieceOn(String fen, String square) {
        String[] ranks = fen.split(" ")[0].split("/");
        String rank = ranks[8 - (square.charAt(1) - '0')];
        int file = square.charAt(0) - 'a';
        int col = 0;

        for (char c : rank.toCharArray()) {
            if (Character.isDigit(c)) {
                col += c - '0';
            } else if (col++ == file) {
                return c;
            }
        }

        return '?';
    }

    /** Material balance from White's point of view, with {@link WeightingFunction#weightOfPiece}. */
    private static int material(String fen) {
        int sum = 0;

        for (char c : fen.split(" ")[0].toCharArray()) {
            int value = switch (Character.toLowerCase(c)) {
                case 'p' -> WeightingFunction.weightOfPiece[Board.whitePawn];
                case 'n' -> WeightingFunction.weightOfPiece[Board.whiteKnight];
                case 'b' -> WeightingFunction.weightOfPiece[Board.whiteBishop];
                case 'r' -> WeightingFunction.weightOfPiece[Board.whiteRook];
                case 'q' -> WeightingFunction.weightOfPiece[Board.whiteQueen];
                default -> 0;
            };

            sum += Character.isUpperCase(c) ? value : -value;
        }

        return sum;
    }

    /** The term contributions (White's point of view, cp) of the position, the rest last. */
    private static double[] terms(WeightingFunction evaluator, String fen, double[] factors) {
        var breakdown = evaluator.analyzeFactors(Fen.importChess960FEN(fen));
        double[] out = new double[factors.length + 2];
        double sum = 0;

        for (int i = 0; i < factors.length; i++) {
            out[i] = breakdown.features()[i] * factors[i];
            sum += out[i];
        }

        out[factors.length] = material(fen);
        out[factors.length + 1] = breakdown.eval() - sum - out[factors.length];

        return out;
    }
}
