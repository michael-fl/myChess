package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@code Sts}, the Strategic Test Suite runner.
 *
 * <p>The class covers three different things, and they are worth keeping apart
 * because they carry very different weight:
 *
 * <ol>
 *   <li><b>Unit tests of our own parser and arithmetic</b> — {@code parseLine},
 *       {@code pointsFor}, {@code aggregate} on hand-written fixtures. Independent
 *       of the suite file.</li>
 *   <li><b>The notation contract</b> — every {@code c9} candidate must be a string
 *       {@code UciMoveParser.toUci} can actually produce for a legal move in that
 *       position. This is the one part that guards <em>myChess</em> code: it breaks
 *       when {@code toUci}, the castling branch, or move generation drifts, and
 *       such drift would otherwise corrupt every future measurement silently.</li>
 *   <li><b>Asset-swap detection</b> — the counts and shape of the frozen suite.
 *       Over a tracked, unchanging file these cannot fail on their own; their sole
 *       purpose is to go red if someone replaces the file (a newer STS release, or
 *       the bare-FEN {@code sts_google_sheet.epd} variant). They are honest
 *       bookkeeping, not a test of the engine.</li>
 * </ol>
 *
 * <p>None of the three starts the engine, so a fourth test does: a wiring proof
 * over three positions at depth 2, asserting structure only.
 *
 * @author Michael Fleischhauer
 */
class StsTest {

    /**
     * A valid, shortened STS line — three candidates instead of ten, and without
     * the {@code c0} and {@code Ae} operations the parser does not need.
     */
    private static final String VALID_LINE =
            "1kr5/3n4/q3p2p/p2n2p1/PppB1P2/5BP1/1P2Q2P/3R2K1 w - - bm f5; "
            + "id \"STS(v1.0) Undermine.001\"; "
            + "c7 \"f5 Bf2 Bg4\"; c8 \"100 46 23\"; c9 \"f4f5 d4f2 f3g4\";";

    private static final String UNDERMINE = "Undermine";
    private static final String KING_ACTIVITY = "King Activity";
    private static final String BEST_MOVE = "f4f5";
    private static final String SECOND_BEST_MOVE = "d4f2";

    /** Depth for the wiring proof — the shallowest search that still returns a move. */
    private static final int WIRING_DEPTH = 2;

    private static final int WIRING_POSITIONS = 3;

    /**
     * Expected positions per theme in the frozen LAN v6 suite.
     *
     * <p>Counted from the file itself. They sum to {@code Sts.SUITE_SIZE}: v6 keeps
     * only positions where the best move leads the second by at least 10 cp, which
     * is why this is 1188 rather than the 1500 of the original suite.
     */
    private static final Map<Integer, Integer> POSITIONS_PER_THEME = Map.ofEntries(
            Map.entry(1, 85), Map.entry(2, 80), Map.entry(3, 86), Map.entry(4, 89),
            Map.entry(5, 85), Map.entry(6, 80), Map.entry(7, 82), Map.entry(8, 80),
            Map.entry(9, 71), Map.entry(10, 79), Map.entry(11, 70), Map.entry(12, 74),
            Map.entry(13, 75), Map.entry(14, 79), Map.entry(15, 73));

    // ---------------------------------------------------------------------
    // 1. Unit tests of the parser and the scoring arithmetic
    // ---------------------------------------------------------------------

    @Test
    void parseLine_readsEveryOperationItNeeds() {
        var position = Sts.parseLine(VALID_LINE, 1);

        assertEquals("1kr5/3n4/q3p2p/p2n2p1/PppB1P2/5BP1/1P2Q2P/3R2K1 w - - 0 1", position.fen(),
                "FEN must be completed with the halfmove and fullmove counters EPD omits");
        assertEquals(1, position.theme(), "theme number comes from the version in the id");
        assertEquals(UNDERMINE, position.themeName(), "theme name comes from the id");
        assertEquals(1, position.number(), "position number is what follows the last dot");
        assertEquals(List.of(BEST_MOVE, SECOND_BEST_MOVE, "f3g4"), position.candidates(),
                "candidates come from c9 in file order");
        assertEquals(List.of(100, 46, 23), position.points(), "points come from c8 in file order");
        assertEquals(BEST_MOVE, position.bestMove(), "the best move is c9's first token");
        assertEquals("Undermine.001", position.label(), "label pads the position number to three digits");
    }

    @Test
    void parseLine_handlesTheIdFormsTheSuiteActuallyUses() {
        var minorVersion = Sts.parseLine(
                VALID_LINE.replace("STS(v1.0) Undermine.001", "STS(v2.2) Open Files and Diagonals.078"), 1);

        assertEquals(2, minorVersion.theme(), "theme 2 is spelled STS(v2.2), so the minor version must be ignored");
        assertEquals("Open Files and Diagonals", minorVersion.themeName(), "theme name must survive a minor version");
        assertEquals(78, minorVersion.number(), "position number must come from the last dot, not the version dot");

        var slashInName = Sts.parseLine(
                VALID_LINE.replace("STS(v1.0) Undermine.001", "STS(v9.0) Advancement of a/b/c pawns.001"), 1);

        assertEquals(9, slashInName.theme(), "theme 9 must parse despite the slashes in its name");
        assertEquals("Advancement of a/b/c pawns", slashInName.themeName(),
                "a theme name containing slashes must be taken verbatim");

        var twoDigitTheme = Sts.parseLine(
                VALID_LINE.replace("STS(v1.0) Undermine.001", "STS(v10.0) Simplification.079"), 1);

        assertEquals(10, twoDigitTheme.theme(), "a two-digit theme number must parse");
    }

    @Test
    void pointsFor_awardsTheCandidatesValueAndNothingElse() {
        var position = Sts.parseLine(VALID_LINE, 1);

        assertEquals(100, position.pointsFor(BEST_MOVE), "the best move must earn full credit");
        assertEquals(46, position.pointsFor(SECOND_BEST_MOVE), "a listed move must earn its own value");
        assertEquals(23, position.pointsFor("f3g4"), "the last listed move must earn its own value");
        assertEquals(0, position.pointsFor("g1h1"), "a move outside the list must earn nothing");
        assertEquals(100, position.maxPoints(), "the available credit is the best move's value");
    }

    @Test
    void parseLine_rejectsEveryStructuralDefect() {
        assertMalformed("missing bm", VALID_LINE.replace(" bm f5;", " f5;"));
        assertMalformed("missing c9", VALID_LINE.replace("c9 \"f4f5 d4f2 f3g4\";", ""));
        assertMalformed("missing c8", VALID_LINE.replace("c8 \"100 46 23\";", ""));
        assertMalformed("missing id", VALID_LINE.replace("id \"STS(v1.0) Undermine.001\";", ""));
        assertMalformed("c8 shorter than c9", VALID_LINE.replace("c8 \"100 46 23\"", "c8 \"100 46\""));
        assertMalformed("c8 not starting at 100", VALID_LINE.replace("c8 \"100 46 23\"", "c8 \"99 46 23\""));
        assertMalformed("c8 not descending", VALID_LINE.replace("c8 \"100 46 23\"", "c8 \"100 23 46\""));
        assertMalformed("non-numeric c8", VALID_LINE.replace("c8 \"100 46 23\"", "c8 \"100 x 23\""));
        assertMalformed("five-character candidate", VALID_LINE.replace("f3g4\"", "f3g4q\""));
        assertMalformed("bm disagreeing with c7", VALID_LINE.replace(" bm f5;", " bm Bf2;"));
        assertMalformed("theme number out of range", VALID_LINE.replace("STS(v1.0)", "STS(v16.0)"));
        assertMalformed("unparseable id", VALID_LINE.replace("STS(v1.0) Undermine.001", "Undermine"));
        assertMalformed("FEN with a broken board", VALID_LINE.replace("1kr5/", "9kr5/"));
    }

    @Test
    void aggregate_keysThemesByNumberSoTheSuite3TypoFoldsIntoOneRow() {
        // Theme 3 appears in the suite under two orderings of the same name. Keying
        // by name would split it in two; keying by number must not.
        var results = List.of(
                result(position(3, "Knight Outposts/Repositioning/Centralization", 1), BEST_MOVE),
                result(position(3, "Knight Outposts/Centralization/Repositioning", 2), SECOND_BEST_MOVE),
                result(position(11, KING_ACTIVITY, 1), "g1h1"));

        var aggregated = Sts.aggregate(Sts.DEFAULT_DEPTH, results, 4_000L, 2_000L);

        assertEquals(2, aggregated.themes().size(), "two distinct theme numbers must produce two rows");

        var knights = aggregated.themes().getFirst();
        assertEquals(3, knights.theme(), "themes must be ordered by number");
        assertEquals(2, knights.positions(), "both spellings must land in the same theme");
        assertEquals("Knight Outposts/Repositioning/Centralization", knights.themeName(),
                "the display name must come from the first position seen for that theme");
        assertEquals(146, knights.points(), "points must sum across the theme");
        assertEquals(200, knights.maxPoints(), "available credit must sum across the theme");
        assertEquals(1, knights.bestMoveHits(), "only the first result played the best move");
        assertEquals(0, knights.misses(), "neither result scored zero");
        assertEquals(73.0, knights.percent(), 0.001, "percentage is points over available credit");

        var kingActivity = aggregated.themes().get(1);
        assertEquals(1, kingActivity.misses(), "the unlisted move must count as a miss");

        assertEquals(146, aggregated.points(), "run total must sum every theme");
        assertEquals(300, aggregated.maxPoints(), "run maximum must sum every position");
        assertEquals(1, aggregated.bestMoveHits(), "run best-move count must sum every theme");
        assertEquals(1, aggregated.misses(), "run miss count must sum every theme");
        assertEquals(2_000L, aggregated.nps(), "nps is nodes per second: 4 000 nodes in 2 000 ms");
        assertEquals(1, aggregated.missedPositions().size(), "the misses list feeds new characterization tests");
    }

    @Test
    void weakestFirst_ranksTheThemesTheDiagnosisNeeds() {
        var results = List.of(
                result(position(1, UNDERMINE, 1), BEST_MOVE),
                result(position(11, KING_ACTIVITY, 1), "g1h1"));

        var ranked = Sts.aggregate(Sts.DEFAULT_DEPTH, results, 0L, 0L).weakestFirst();

        assertEquals(Sts.KING_ACTIVITY_THEME, ranked.getFirst().theme(),
                "the worst-scoring theme must come first — that is the whole point of the report");
    }

    // ---------------------------------------------------------------------
    // 2. The notation contract — the part that guards myChess code
    // ---------------------------------------------------------------------

    /**
     * Every annotated candidate must be reachable, i.e. a string the runner could
     * actually produce.
     *
     * <p>The search only ever chooses among generated moves, so the set of strings
     * a run can produce for a position is exactly
     * {@code { toUci(m, board) : m legal in board }}. A candidate outside that set
     * is <b>unreachable</b>: its points can never be awarded at any depth, and the
     * symptom is indistinguishable from myChess simply playing worse. No search is
     * needed to check this — move generation is enough, and it covers legality and
     * notation agreement (4 versus 5 characters on promotions, {@code e1g1} versus
     * {@code e1h1} on castling) in a single step.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void everyCandidateIsAMoveTheRunnerCouldActuallyProduce() {
        var unreachable = new ArrayList<String>();

        for (Sts.Position position : Sts.loadSuite()) {
            Board board = Fen.importFEN(position.fen());
            Set<String> reachable = legalMoveNotations(board);

            for (String candidate : position.candidates()) {
                if (!reachable.contains(candidate)) {
                    unreachable.add(position.label() + ": " + candidate);
                }
            }
        }

        assertTrue(unreachable.isEmpty(),
                "every c9 candidate must be in the toUci image of the legal moves, otherwise its "
                + "points are unreachable and the score silently understates the engine; unreachable: "
                + unreachable);
    }

    // ---------------------------------------------------------------------
    // 3. Asset-swap detection
    // ---------------------------------------------------------------------

    /**
     * Pins the shape of the frozen suite file.
     *
     * <p>These assertions cannot fail while the tracked file is unchanged — they
     * exist to go red when it is <em>replaced</em>, because a newer STS release or
     * the bare-FEN variant in the same upstream download would change the
     * denominator without changing anything visible.
     */
    @Test
    void theFrozenSuiteHasTheShapeTheScoringAssumes() {
        var suite = Sts.loadSuite();

        assertEquals(Sts.SUITE_SIZE, suite.size(), "position count of the frozen LAN v6 suite");

        var perTheme = HashMap.<Integer, Integer>newHashMap(Sts.THEME_COUNT);
        var namesPerTheme = HashMap.<Integer, Set<String>>newHashMap(Sts.THEME_COUNT);

        for (Sts.Position position : suite) {
            perTheme.merge(position.theme(), 1, Integer::sum);
            namesPerTheme.computeIfAbsent(position.theme(), theme -> new HashSet<>()).add(position.themeName());
        }

        assertEquals(POSITIONS_PER_THEME, perTheme, "positions per theme in the frozen suite");
        assertEquals(POSITIONS_PER_THEME.get(Sts.KING_ACTIVITY_THEME), perTheme.get(Sts.KING_ACTIVITY_THEME),
                "theme " + Sts.KING_ACTIVITY_THEME + " is King Activity, the one the king-safety family needs");

        for (var entry : namesPerTheme.entrySet()) {
            assertTrue(entry.getValue().size() <= 2,
                    "theme " + entry.getKey() + " must not carry more than the one known spelling variant, "
                    + "otherwise aggregation by theme number would be hiding two real themes: " + entry.getValue());
        }

        assertEquals(2, namesPerTheme.get(3).size(),
                "theme 3 is the known anomaly: two orderings of the same name, which is why aggregation "
                + "keys on the theme number");
    }

    @Test
    void candidateListsAreNotAlwaysTenLong() {
        var lengths = new HashSet<Integer>();

        for (Sts.Position position : Sts.loadSuite()) {
            lengths.add(position.candidates().size());
        }

        assertEquals(Set.of(6, 8, 10), lengths,
                "1186 positions list ten candidates, one lists eight and one lists six — the parser must "
                + "never assume ten");
    }

    @Test
    void filterByTheme_isolatesTheKingActivityPositions() {
        var kingActivity = Sts.filterByTheme(Sts.loadSuite(), Sts.KING_ACTIVITY_THEME);

        assertEquals(POSITIONS_PER_THEME.get(Sts.KING_ACTIVITY_THEME), kingActivity.size(),
                "King Activity position count");

        for (Sts.Position position : kingActivity) {
            assertEquals(KING_ACTIVITY, position.themeName(), "filtered positions must all be King Activity");
        }
    }

    // ---------------------------------------------------------------------
    // 4. Wiring proof — the only part that starts the engine
    // ---------------------------------------------------------------------

    /**
     * Runs the real loop over three positions at depth 2 and asserts structure
     * only — never which move or how many points.
     *
     * <p>Everything above this point leaves the engine untouched, so without this
     * test a wrong board handed to {@code toUci}, an un-cleared transposition
     * table, or an inverted theme filter would surface only in the hour-long
     * measurement run.
     */
    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void runSearchesEachPositionAndScoresWhatItPlayed() {
        var positions = Sts.loadSuite().subList(0, WIRING_POSITIONS);
        var reported = new ArrayList<Sts.PositionResult>();

        var result = Sts.run(WIRING_DEPTH, positions, reported::add);

        assertEquals(WIRING_POSITIONS, result.positions().size(), "one result per searched position");
        assertEquals(WIRING_POSITIONS, reported.size(), "the progress callback must fire once per position");
        assertEquals(WIRING_DEPTH, result.depth(), "the result must carry the depth it was run at");
        assertEquals(WIRING_POSITIONS * Sts.BEST_MOVE_POINTS, result.maxPoints(),
                "available credit is 100 per position");

        for (Sts.PositionResult positionResult : result.positions()) {
            assertNotEquals("", positionResult.chosenMove(), "every position must yield a chosen move");
            assertEquals(4, positionResult.chosenMove().length(),
                    "a chosen move in these positions is from-to notation: " + positionResult.chosenMove());
            assertTrue(positionResult.points() >= 0 && positionResult.points() <= Sts.BEST_MOVE_POINTS,
                    "credit must lie between 0 and 100, was " + positionResult.points());
            assertEquals(positionResult.position().pointsFor(positionResult.chosenMove()), positionResult.points(),
                    "the credit must be the value of the move actually played");
            assertTrue(positionResult.nodes() > 0, "a searched position must report visited nodes");
            assertEquals(1, positionResult.position().theme(), "the first suite positions are theme 1");
        }

        assertFalse(result.themes().isEmpty(), "a run must aggregate at least one theme");
        assertEquals(result.points(), result.themes().stream().mapToInt(Sts.ThemeScore::points).sum(),
                "the run total must equal the sum over its themes");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * @return every from-to string the engine could return for {@code board}, i.e.
     *         {@code toUci} applied to each legal move
     */
    private static Set<String> legalMoveNotations(Board board) {
        var generator = new MoveGenerator(MoveSorter.defaultImplementation());
        var moves = generator.calculateMoves(board, 0);
        final int count = moves.count();
        // Snapshot the buffer: the generator reuses its internal array.
        final int[] snapshot = Arrays.copyOf(moves.getMoves(), count);
        var notations = new HashSet<String>(count);

        for (int move : snapshot) {
            if (move == 0) {
                continue;
            }

            board.makeMove(move);
            boolean leavesOwnKingSafe = !board.canCaptureOpposingKing();
            board.revertMove();

            if (leavesOwnKingSafe) {
                notations.add(UciMoveParser.toUci(move, board));
            }
        }

        return notations;
    }

    private static Sts.Position position(int theme, String themeName, int number) {
        return new Sts.Position("1kr5/3n4/q3p2p/p2n2p1/PppB1P2/5BP1/1P2Q2P/3R2K1 w - - 0 1",
                "STS(v%d.0) %s.%03d".formatted(theme, themeName, number),
                theme, themeName, number,
                List.of(BEST_MOVE, SECOND_BEST_MOVE, "f3g4"), List.of(100, 46, 23));
    }

    private static Sts.PositionResult result(Sts.Position position, String chosenMove) {
        return new Sts.PositionResult(position, chosenMove, position.pointsFor(chosenMove), 1_000L, 500L);
    }

    private static void assertMalformed(String what, String epdLine) {
        assertThrows(Sts.StsException.class, () -> Sts.parseLine(epdLine, 1),
                "a line with " + what + " must be rejected loudly, because a silently skipped position "
                + "changes the denominator and makes two runs incomparable");
    }
}
