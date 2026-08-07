package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PgnQuietEpdExtractor}: the quiet-position filter, the FEN
 * trimming and even-subsampling helpers, and end-to-end game extraction into the
 * Zurichess-style {@code c9} EPD format.
 *
 * @author Michael Fleischhauer
 */
class PgnQuietEpdExtractorTest {

    private static final String EPD_LINE_PATTERN = "\\S+ [wb] \\S+ \\S+ c9 \"(1-0|0-1|1/2-1/2)\";";

    private static MoveGenerator newMoveGenerator() {
        return new MoveGenerator(MoveSorter.defaultImplementation());
    }

    @Test
    void isQuiet_quietMiddlegamePosition_true() {
        // After 1. e4 e5: no capture is available and nobody is in check.
        Board board = Fen.importFEN("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2");

        assertTrue(PgnQuietEpdExtractor.isQuiet(board, newMoveGenerator(), new StaticExchangeEvaluation()),
                "a position with no captures and no check must be quiet");
    }

    @Test
    void isQuiet_freeWinningCaptureAvailable_false() {
        // White's e4 pawn can play exd5, winning the undefended queen (SEE >> 0).
        Board board = Fen.importFEN("4k3/8/8/3q4/4P3/8/8/4K3 w - - 0 1");

        assertFalse(PgnQuietEpdExtractor.isQuiet(board, newMoveGenerator(), new StaticExchangeEvaluation()),
                "a position with a capture of SEE >= 0 must not be quiet");
    }

    @Test
    void isQuiet_winningCaptureExistsOnlyWhenTheCheaperPieceTakesFirst_false() {
        // Both the e4 pawn and the d1 queen attack the black knight on d5, which
        // is defended by the c6 and e6 pawns. Qxd5 loses (SEE < 0: ...cxd5 wins
        // the queen), but exd5 wins a knight for a pawn (SEE > 0). The position
        // must be non-quiet: testing every capture independently catches the
        // pawn-first winning capture regardless of iteration order.
        Board board = Fen.importFEN("4k3/8/2p1p3/3n4/4P3/8/8/3QK3 w - - 0 1");

        assertFalse(PgnQuietEpdExtractor.isQuiet(board, newMoveGenerator(), new StaticExchangeEvaluation()),
                "a position must be non-quiet if ANY capture has SEE > 0, even when only one attacker's capture order wins");
    }

    @Test
    void isQuiet_sideToMoveInCheck_false() {
        // Black rook on e2 checks the white king on e1.
        Board board = Fen.importFEN("4k3/8/8/8/8/8/4r3/4K3 w - - 0 1");

        assertFalse(PgnQuietEpdExtractor.isQuiet(board, newMoveGenerator(), new StaticExchangeEvaluation()),
                "a position where the side to move is in check must not be quiet");
    }

    @Test
    void isQuiet_knownLimitation_blindToUnavoidableOpponentThreat_true() {
        // DOCUMENTED BLIND SPOT of the L0 filter (see PgnQuietEpdExtractor JavaDoc).
        // White is not in check and has no capture of its own with SEE > 0, so the
        // filter reports "quiet" — yet the a5 knight is trapped: Black plays ...b6
        // next and wins it no matter what White does (b3 is covered by the a4 pawn,
        // c4 is White's own pawn, c6 by the d7 pawn, and Nxb7 loses to the c8
        // bishop). The filter only inspects the side-to-move's own captures and is
        // blind to the opponent's unavoidable threat. If a future search-delta
        // filter closes this gap, this assertion should be flipped.
        Board board = Fen.importFEN("2b3k1/1ppp2pp/8/N7/p1P5/P7/6PP/6K1 w - - 0 1");

        assertTrue(PgnQuietEpdExtractor.isQuiet(board, newMoveGenerator(), new StaticExchangeEvaluation()),
                "the L0 filter is (by design) blind to the opponent's unavoidable material threat");
    }

    @Test
    void firstFourFenFields_dropsTheTwoMoveCounters() {
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -",
                PgnQuietEpdExtractor.firstFourFenFields("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"),
                "the halfmove clock and fullmove number must be dropped");
    }

    @Test
    void evenSubsample_spreadsAcrossTheWholeList() {
        List<Integer> input = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        List<Integer> chosen = PgnQuietEpdExtractor.evenSubsample(input, 3);

        assertEquals(List.of(0, 3, 6), chosen, "subsample must pick evenly spread indices across the list");
        assertEquals(input, PgnQuietEpdExtractor.evenSubsample(input, 20),
                "subsampling to more than the size must return the input unchanged");
    }

    @Test
    void extractGame_producesWellFormedResultLabeledEpd() {
        // A quiet Queen's Gambit Declined; result decisive so every sample is labeled 1-0.
        String pgn = """
                [Event "test"]
                [Result "1-0"]

                1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 5. e3 O-O 6. Nf3 Nbd7 7. Rc1 c6
                8. Bd3 h6 9. Bh4 b6 10. O-O Bb7 1-0
                """;
        Pgn parsed = Pgn.parse(pgn).findFirst().orElseThrow();

        var config = new PgnQuietEpdExtractor.Config(4, 4, 2, 3, true);
        List<String> lines = PgnQuietEpdExtractor.extractGame(parsed, config, new HashSet<>());

        assertFalse(lines.isEmpty(), "a long quiet game must yield at least one sampled position");
        assertTrue(lines.size() <= config.maxPerGame(), "must not exceed maxPerGame samples: " + lines);

        for (String line : lines) {
            assertTrue(line.matches(EPD_LINE_PATTERN), "malformed EPD line: " + line);
            assertTrue(line.endsWith("c9 \"1-0\";"), "result label must match the game result: " + line);
        }
    }

    @Test
    void extractGame_gameShorterThanTheSkipWindow_yieldsNothing() {
        String pgn = """
                [Event "test"]
                [Result "0-1"]

                1. e4 e5 2. Nf3 Nc6 0-1
                """;
        Pgn parsed = Pgn.parse(pgn).findFirst().orElseThrow();

        // Skip 8 opening and 8 ending plies from a 4-ply game -> no eligible plies.
        List<String> lines = PgnQuietEpdExtractor.extractGame(parsed, PgnQuietEpdExtractor.Config.defaults(), new HashSet<>());

        assertTrue(lines.isEmpty(), "a game shorter than the skip window must yield no samples: " + lines);
    }

    @Test
    void extractGame_dedupAcrossRun_neverEmitsTheSamePositionTwice() {
        String pgn = """
                [Event "test"]
                [Result "1/2-1/2"]

                1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 5. e3 O-O 6. Nf3 Nbd7 7. Rc1 c6
                8. Bd3 h6 9. Bh4 b6 10. O-O Bb7 1/2-1/2
                """;
        Pgn parsed = Pgn.parse(pgn).findFirst().orElseThrow();
        var config = new PgnQuietEpdExtractor.Config(4, 4, 2, 3, true);

        // Share the seen-set across both passes: whatever the first pass emits
        // must never be emitted again (the cross-run no-duplicate guarantee).
        Set<String> seen = new HashSet<>();
        List<String> first = PgnQuietEpdExtractor.extractGame(parsed, config, seen);
        List<String> second = PgnQuietEpdExtractor.extractGame(parsed, config, seen);

        assertFalse(first.isEmpty(), "first extraction must yield samples");

        Set<String> firstFens = new HashSet<>();
        for (String line : first) {
            firstFens.add(fenOf(line));
        }

        for (String line : second) {
            assertFalse(firstFens.contains(fenOf(line)),
                    "dedup must never emit the same position twice across the run: " + line);
        }
    }

    private static String fenOf(String epdLine) {
        return epdLine.substring(0, epdLine.indexOf(" c9 "));
    }
}
