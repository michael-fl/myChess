package org.michaelfl.mychess;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares the <em>playing style</em> of the two engines in a cutechess match,
 * from the match PGNs alone. A measuring instrument, not a test: it asserts
 * nothing and is never run by the suite.
 *
 * <h2>The question</h2>
 *
 * <p>Does Audax actually play more boldly than its parent? The obvious answer —
 * average its own king-attack bonus over the games — is circular: that bonus is
 * the quantity Audax's search maximises, so it comes out higher by construction,
 * whether or not anything about the games is genuinely sharper.
 *
 * <h2>Telling a sacrifice from a blunder</h2>
 *
 * <p>The metric that carries the weight here is what the report calls
 * <b>conviction</b>: plies in which a side is at least {@link #SACRIFICE_CP}
 * behind in material <em>while its own evaluation is not negative</em>. In
 * words: it is down a piece and thinks it is fine.
 *
 * <p>That distinguishes intent from accident, which is the hard part. Cutechess
 * records each engine's own score per move ({@code {+0.20/8 1.6s}}), so:
 *
 * <ul>
 *   <li>A deliberate sacrifice leaves the sacrificing side's own score healthy —
 *       it saw the material loss and accepted it.</li>
 *   <li>A blunder or a loss to a combination shows up as a score that
 *       <em>collapses</em> once the opponent replies — it did not see it
 *       coming, and afterwards it knows it is worse.</li>
 * </ul>
 *
 * <p>Measuring a state ("down material, still confident") rather than an event
 * ("this move was the sacrifice") also sidesteps having to decide which move
 * caused an imbalance, which is genuinely awkward once exchanges, recaptures and
 * forced sequences are involved.
 *
 * <p>Note what is deliberately <b>not</b> a criterion: the result of the game. A
 * blunder correlates with losing, but so does an unsound sacrifice — and this
 * fork exists to produce those. The goal is to measure boldness, not soundness,
 * so a metric that demanded the sacrifice work out would measure the wrong
 * thing.
 *
 * <h2>The other figures</h2>
 *
 * <p><b>Draw rate</b> and <b>game length</b> are independent of any evaluation
 * and fall out of the match anyway; sharper play should draw less. <b>Checks</b>
 * are a crude but cheap activity signal. <b>Mean own score</b> shows whether one
 * engine is systematically more optimistic, which is worth knowing on its own.
 * The <b>king-attack index</b> is reported last and as a secondary figure only:
 * not as evidence of aggression, but as a check that the curve in
 * {@code WeightingFunction.KING_ATTACK_BONUS} is being asked for the indices it
 * was calibrated for. Deliberately not a {@code @link}: neither that constant nor
 * our {@code KING_ATTACK_PENALTY} exists on master — both live on the branch this
 * tool was ported to serve.
 *
 * <p>Both engines are measured <b>within the same games</b>, so the comparison
 * is paired: same openings, same opponent, same conditions. That removes a lot
 * of variance compared with comparing two separate matches.
 *
 * <h2>A note on the match setup</h2>
 *
 * <p>Run the match at <b>fixed depth</b> ({@code -each tc=inf depth=8}), not
 * fixed time, when the question is style. Audax's raised material-only threshold
 * buys a more accurate evaluation and pays for it in speed, so under a clock it
 * would also be searching shallower, and the measurement would mix "plays
 * differently" with "sees less". Fixed time is the honest setting for an Elo
 * number, because there the speed cost is part of the truth — but that is a
 * different question and a separate run.
 *
 * <p>Fixed <em>nodes</em> would be the more precise instrument, but
 * {@code UciHandler} does not parse {@code go nodes}, so depth is what is
 * available. Depth 8 was chosen by measurement: it is the depth Audax reaches
 * within the 60 s per 40 moves of a {@code tc=40/60} budget.
 *
 * <p>The speed cost is larger than the 27% NPS figure documented on
 * {@code PositionSearch.EVALUATE_MATERIAL_ONLY_THRESHOLD}: measured over 100
 * games at depth 8, Audax needs <b>1.80× the time</b> of the parent for the same
 * depth. Two effects stack — the more expensive evaluation, and roughly 13% more
 * nodes for the same depth, because the changed evaluation worsens move ordering
 * and fewer cutoffs fire. See {@code docs/style-measurement.md}.
 *
 * <p>Run it with:
 * <pre>{@code
 * mvn -q test-compile
 * java -cp target/classes:target/test-classes \
 *      org.michaelfl.mychess.MatchStyleAnalysis <pgn-file-or-directory>
 * }</pre>
 *
 * @author Michael Fleischhauer
 */
public final class MatchStyleAnalysis {

    /** Material deficit from which being behind counts as a possible sacrifice. */
    private static final int SACRIFICE_CP = 300;

    /** Score at or above which a side is taken to believe it is doing fine. */
    private static final int CONFIDENT_CP = 0;

    /**
     * Upper bound on that score. Above it the engine is not believing in unclear
     * compensation, it is simply winning — the final combination of a decided
     * game, where throwing material in costs nothing. Counting those would
     * inflate the pay-off rate below and say nothing about boldness.
     *
     * <p>This is also why the last plies of a game are not cut off wholesale:
     * what makes an ending uninteresting here is that it is decided, and the
     * score says that directly, whereas a ply count has to guess at it and gets
     * game length wrong in both directions.
     */
    private static final int UNCLEAR_MAX_CP = 300;

    /**
     * Consecutive own plies the deficit has to persist before it counts as an
     * investment rather than an exchange.
     *
     * <p>Without this the metric mostly found mid-exchange states: material is
     * measured after the engine's own move, so giving a piece with an immediate
     * recapture coming leaves it "behind" for a single ply while being entirely
     * right to feel fine. Those episodes lasted about 1.5 plies and were won and
     * drawn far more often than average — the giveaway that the material was
     * simply coming straight back. A real sacrifice stays unpaid for a while.
     */
    private static final int MIN_PERSISTENCE_PLIES = 3;

    /** Centipawn stand-in for a mate score in a comment such as {@code +M5}. */
    private static final int MATE_CP = 10_000;

    private static final int WHITE = 0;
    private static final int BLACK = 1;

    /** Comment, or any non-space run outside a comment. */
    private static final Pattern TOKEN = Pattern.compile("\\{([^}]*)}|([^\\s{}]+)");

    /** {@code +0.20/8 1.6s}, {@code -1.50/9}, {@code +M5/12} — score is group 1. */
    private static final Pattern SCORE = Pattern.compile("^([-+]?(?:M?\\d+(?:\\.\\d+)?))/");

    private MatchStyleAnalysis() {
        throw new IllegalStateException();
    }

    /** Everything counted for one engine across the whole match. */
    private static final class Tally {

        long games;
        long wins;
        long draws;
        long losses;
        long plies;
        long scoredPlies;
        long convictionPlies;
        long convictionGames;
        long convictionWins;
        long convictionDraws;
        long convictionLosses;
        long matePlies;
        long checks;
        long scoreSum;
        int deepestDeficitHeld;
        final List<Integer> attackIndices = new ArrayList<>();

        double per(long value) {
            return games == 0 ? 0 : (double) value / games;
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: MatchStyleAnalysis <pgn-file-or-directory>");
            return;
        }

        var tallies = new LinkedHashMap<String, Tally>();
        int skipped = 0;

        for (File file : pgnFiles(new File(args[0]))) {
            for (Pgn pgn : (Iterable<Pgn>) Pgn.parse(file, true)::iterator) {
                if (!analyse(pgn, tallies)) {
                    skipped++;
                }
            }
        }

        report(tallies, skipped);
    }

    private static List<File> pgnFiles(File path) {
        if (path.isFile()) {
            return List.of(path);
        }

        var files = path.listFiles((dir, name) -> name.endsWith(".pgn"));

        return files == null ? List.of() : Arrays.stream(files).sorted().toList();
    }

    /**
     * Replays one game and folds it into both engines' tallies.
     *
     * @return {@code false} if the game was unusable (no result, unknown
     *         players, Chess960 without a start FEN, or an unreplayable move)
     */
    private static boolean analyse(Pgn pgn, Map<String, Tally> tallies) {
        String[] players = {pgn.getTag("White"), pgn.getTag("Black")};
        if (players[WHITE] == null || players[BLACK] == null
                || pgn.result == Pgn.Result.ONGOING || pgn.result == Pgn.Result.UNKNOWN) {
            return false;
        }

        Board board = startBoard(pgn);
        if (board == null) {
            return false;
        }

        var tally = new Tally[] {tallies.computeIfAbsent(players[WHITE], _ -> new Tally()),
                                 tallies.computeIfAbsent(players[BLACK], _ -> new Tally())};
        List<String> comments = comments(pgn.toString());
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        var weightingFunction = new WeightingFunction();
        var conviction = new boolean[2];
        var behindRun = new int[2];
        var peakIndex = new int[] {-1, -1};

        for (int ply = 0; ply < pgn.moves.size(); ply++) {
            int mover = ply % 2 == 0 ? WHITE : BLACK;
            MoveDescription move = pgn.moves.get(ply);

            try {
                board.makeMove(board.moveDescriptionToMove(board.resolveMoveDescription(move, moveGenerator)).move());
            } catch (RuntimeException _) {
                break; // unreplayable from here on; keep what we have
            }

            tally[mover].plies++;
            if (move.isCheck() || move.isCheckmate()) {
                tally[mover].checks++;
            }

            Integer scoreCp = ply < comments.size() ? scoreOf(comments.get(ply)) : null;
            if (scoreCp == null) {
                continue; // book move or missing comment — nothing to judge
            }

            // Material from the mover's point of view, after its own move.
            int material = WeightingFunction.calculateMaterialWeight(board) * (mover == WHITE ? 1 : -1);
            int deficit = -material;

            tally[mover].scoredPlies++;
            tally[mover].scoreSum += scoreCp;

            if (deficit < SACRIFICE_CP || scoreCp < CONFIDENT_CP) {
                behindRun[mover] = 0;
            } else if (scoreCp > UNCLEAR_MAX_CP) {
                // Down material but winning outright — a decided position, not a
                // belief in compensation. Mate scores are the extreme of that and
                // are counted separately so the exclusion stays visible.
                if (scoreCp >= MATE_CP) {
                    tally[mover].matePlies++;
                }
                behindRun[mover] = 0;
            } else if (++behindRun[mover] >= MIN_PERSISTENCE_PLIES) {
                tally[mover].convictionPlies++;
                conviction[mover] = true;
                tally[mover].deepestDeficitHeld = Math.max(tally[mover].deepestDeficitHeld, deficit);
            }

            // The peak king-attack index per game is dropped in this port: it needs
            // getAttackUnit / getKingAttackerCount / getDefendUnit, which exist only on
            // branch attack-units. Restore it there together with the term — it is five
            // lines, and the upstream original in ../myChess-Audax has them.
        }

        for (int color : new int[] {WHITE, BLACK}) {
            tally[color].games++;
            if (conviction[color]) {
                tally[color].convictionGames++;
            }
            if (peakIndex[color] >= 0) {
                tally[color].attackIndices.add(peakIndex[color]);
            }
        }

        recordResult(pgn.result, tally, conviction);

        return true;
    }

    private static void recordResult(Pgn.Result result, Tally[] tally, boolean[] conviction) {
        int winner = switch (result) {
            case WHITE_WINS -> WHITE;
            case BLACK_WINS -> BLACK;
            default -> -1;
        };

        for (int color : new int[] {WHITE, BLACK}) {
            boolean won = color == winner;
            boolean drawn = winner < 0;

            if (won) {
                tally[color].wins++;
            } else if (drawn) {
                tally[color].draws++;
            } else {
                tally[color].losses++;
            }

            if (!conviction[color]) {
                continue;
            }
            if (won) {
                tally[color].convictionWins++;
            } else if (drawn) {
                tally[color].convictionDraws++;
            } else {
                tally[color].convictionLosses++;
            }
        }
    }

    private static Board startBoard(Pgn pgn) {
        String fen = pgn.getStartFen();

        try {
            if (fen != null) {
                return pgn.isChess960() ? Fen.importChess960FEN(fen) : Fen.importFEN(fen);
            }

            return pgn.isChess960() ? null : Board.createNewGame();
        } catch (RuntimeException _) {
            return null; // unusable start position
        }
    }

    /**
     * The per-ply comments of a game, in order. Move numbers, the result token
     * and annotations are skipped; a comment is attached to the move it follows,
     * and plies without one get {@code null}, so the list stays index-aligned
     * with {@code pgn.moves} even if some moves carry no comment.
     */
    private static List<String> comments(String notation) {
        var perPly = new ArrayList<String>();
        Matcher matcher = TOKEN.matcher(notation.substring(lastTagEnd(notation)));

        while (matcher.find()) {
            if (matcher.group(1) != null) {
                if (!perPly.isEmpty()) {
                    perPly.set(perPly.size() - 1, matcher.group(1));
                }
                continue;
            }

            String token = matcher.group(2).replaceFirst("^\\d+\\.+", "");
            if (token.isEmpty() || isResult(token) || token.startsWith("$")) {
                continue;
            }
            perPly.add(null);
        }

        return perPly;
    }

    private static int lastTagEnd(String notation) {
        int end = notation.lastIndexOf("]\n");

        return end < 0 ? 0 : end + 2;
    }

    private static boolean isResult(String token) {
        return token.equals("1-0") || token.equals("0-1") || token.equals("1/2-1/2") || token.equals("*");
    }

    /**
     * The mover's own score in centipawns, or {@code null} for {@code {book}}
     * and anything else without a score. Mate scores are mapped to
     * ±{@link #MATE_CP}.
     */
    private static Integer scoreOf(String comment) {
        if (comment == null) {
            return null;
        }

        Matcher matcher = SCORE.matcher(comment.trim());
        if (!matcher.find()) {
            return null;
        }

        String score = matcher.group(1);
        int sign = score.startsWith("-") ? -1 : 1;
        String magnitude = score.replaceFirst("^[-+]", "");

        if (magnitude.startsWith("M")) {
            return sign * MATE_CP;
        }

        return (int) Math.round(sign * Double.parseDouble(magnitude) * 100);
    }

    private static void report(Map<String, Tally> tallies, int skipped) {
        System.out.printf("games skipped (no result / unreplayable): %,d%n%n", skipped);

        if (tallies.isEmpty()) {
            System.out.println("no usable games found");
            return;
        }

        System.out.printf("%-28s %8s %7s %7s %7s %8s %9s %9s %8s %9s%n",
                "engine", "games", "win%", "draw%", "plies", "checks", "convict%", "conv/game", "maxDef", "meanScore");
        System.out.println("-".repeat(115));

        for (var entry : tallies.entrySet()) {
            Tally t = entry.getValue();
            System.out.printf("%-28s %8d %6.1f%% %6.1f%% %7.1f %8.1f %8.1f%% %9.2f %8d %9.0f%n",
                    truncate(entry.getKey()), t.games,
                    100.0 * t.wins / Math.max(1, t.games),
                    100.0 * t.draws / Math.max(1, t.games),
                    t.per(t.plies), t.per(t.checks),
                    100.0 * t.convictionGames / Math.max(1, t.games),
                    t.per(t.convictionPlies),
                    t.deepestDeficitHeld,
                    t.scoredPlies == 0 ? 0 : (double) t.scoreSum / t.scoredPlies);
        }

        System.out.println("\nconvict%   = games in which the engine was >= " + SACRIFICE_CP
                + " cp down while its own score stayed between " + CONFIDENT_CP + " and " + UNCLEAR_MAX_CP
                + " cp,");
        System.out.println("             for at least " + MIN_PERSISTENCE_PLIES + " of its own plies in a row");
        System.out.println("conv/game  = such plies per game, counted from the " + MIN_PERSISTENCE_PLIES
                + "rd onward, so a long episode weighs more");
        System.out.println("maxDef     = deepest material deficit held with a non-negative score, in cp");
        System.out.println("meanScore  = mean of the engine's own score over all its scored plies, in cp");

        System.out.println("\n--- did the conviction pay off? (soundness, as opposed to boldness above) ---");
        System.out.printf("  %-28s %9s %8s %8s %8s %12s %10s%n",
                "engine", "conv.games", "win%", "draw%", "loss%", "vs overall", "mate plies");
        for (var entry : tallies.entrySet()) {
            Tally t = entry.getValue();
            long convGames = t.convictionWins + t.convictionDraws + t.convictionLosses;
            if (convGames == 0) {
                continue;
            }

            double convWinRate = 100.0 * t.convictionWins / convGames;
            double overallWinRate = 100.0 * t.wins / Math.max(1, t.games);
            System.out.printf("  %-28s %9d %7.1f%% %7.1f%% %7.1f%% %+11.1f%% %10d%n",
                    truncate(entry.getKey()), convGames, convWinRate,
                    100.0 * t.convictionDraws / convGames,
                    100.0 * t.convictionLosses / convGames,
                    convWinRate - overallWinRate,
                    t.matePlies);
        }
        System.out.println("  vs overall = conviction win rate minus this engine's overall win rate;");
        System.out.println("               a negative value means the material investment did not come back.");
        System.out.println("  mate plies = plies down material with a MATE score — excluded from conviction,");
        System.out.println("               since the position is decided rather than unclear.");
        System.out.println("  Caveat: conviction games are not a random sample — being down material often");
        System.out.println("  just means losing. Compare the two engines with each other, not against 50%.");

        System.out.println("\n--- secondary: peak king-attack index per game (calibration check, not evidence) ---");
        for (var entry : tallies.entrySet()) {
            List<Integer> indices = entry.getValue().attackIndices;
            if (indices.isEmpty()) {
                continue;
            }

            var sorted = new ArrayList<>(indices);
            Collections.sort(sorted);
            System.out.printf("  %-28s n=%,7d  median=%2d  p95=%2d  max=%2d%n",
                    truncate(entry.getKey()), sorted.size(),
                    percentile(sorted, 50), percentile(sorted, 95), sorted.getLast());
        }
    }

    private static String truncate(String name) {
        return name.length() <= 28 ? name : name.substring(0, 27) + "…";
    }

    private static int percentile(List<Integer> sorted, int p) {
        int index = Math.clamp((long) Math.ceil(p / 100.0 * sorted.size()) - 1, 0, sorted.size() - 1);

        return sorted.get(index);
    }
}
