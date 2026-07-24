package org.michaelfl.mychess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Import tests for PGNs carrying the common non-move annotations: brace and
 * rest-of-line comments, NAGs, move suffix annotations, recursive (nested)
 * variations, structured clock/eval command comments, black move-number
 * continuations, extra tag pairs, and the game-termination marker.
 *
 * <p>None of these carry move information; the importer is expected to simply
 * ignore them. Each annotated game must therefore import <em>without throwing</em>
 * and reach exactly the same position as the same game written without any
 * annotations (compared via the exported FEN).
 *
 * <p>Every fixture is the same reference game (Ruy Lopez, 10 plies) so that a
 * single clean import defines the expected position for all variants.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("TextBlockMigration")
class PgnAnnotationImportTest {

    /** Seven-tag-roster header prepended to each move-text fixture. */
    private static final String HEADER = """
            [Event "PGN annotation import test"]
            [Site "?"]
            [Date "2026.07.24"]
            [Round "1"]
            [White "A"]
            [Black "B"]
            [Result "1-0"]

            """;

    /** The reference game's moves (no annotations, no termination marker). */
    private static final String MOVES =
            "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7";

    /** The reference game with no annotations. */
    private static final String CLEAN_MOVES = MOVES + " 1-0";

    private static final int TIMEOUT_SECONDS = 30;

    private String cleanFen;

    @BeforeEach
    void importCleanReference() {
        cleanFen = finalFen(HEADER + CLEAN_MOVES);
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void cleanGameIsTheDeterministicBaseline() {
        assertEquals(cleanFen, finalFen(HEADER + CLEAN_MOVES),
                "the un-annotated reference game must import deterministically");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void braceComments() {
        assertImportsLikeClean(
                "1. e4 {King's pawn} e5 {Black mirrors} 2. Nf3 Nc6 3. Bb5 {the Spanish} a6 "
                        + "4. Ba4 Nf6 5. O-O {White castles} Be7 1-0",
                "brace comments { ... }");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void braceCommentContainingAnOpeningBrace() {
        // Per the PGN standard, brace comments do not nest and there is no
        // escape for '}': a comment runs from '{' to the FIRST '}'. An inner
        // '{' is literal text. The importer must skip to the first '}', not
        // try to balance braces (which would swallow the rest of the game
        // looking for a second '}' that never comes).
        assertImportsLikeClean(
                "1. e4 {a comment with an inner { brace} e5 2. Nf3 Nc6 3. Bb5 a6 "
                        + "4. Ba4 Nf6 5. O-O Be7 1-0",
                "a brace comment containing a literal '{' (must end at the first '}')");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void multiLineBraceComment() {
        // A { ... } comment may span several lines: newlines inside the braces
        // are part of the comment. The importer must scan across line breaks to
        // the first '}', not stop at the end of the line.
        assertImportsLikeClean(
                "1. e4 {this comment\nspans three\nlines} e5 2. Nf3 Nc6 3. Bb5 a6 "
                        + "4. Ba4 Nf6 5. O-O Be7 1-0",
                "a brace comment spanning multiple lines");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void braceCommentsWithoutSurroundingWhitespace() {
        // Whitespace around a { ... } comment is convention, not required: a
        // move symbol self-terminates at '{', and '}' self-terminates the
        // comment. The importer must not depend on the spaces.
        assertImportsLikeClean(
                "1.e4{King's pawn}e5{Black mirrors}2.Nf3 Nc6 3.Bb5{the Spanish}a6 "
                        + "4.Ba4 Nf6 5.O-O{castles}Be7 1-0",
                "brace comments with no surrounding whitespace (e4{...}e5)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void restOfLineComments() {
        assertImportsLikeClean(
                "1. e4 e5 ; the open game\n2. Nf3 Nc6 3. Bb5 a6 ; Morphy defence\n"
                        + "4. Ba4 Nf6 5. O-O Be7 1-0",
                "; rest-of-line comments");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void restOfLineCommentsWithoutBlank() {
        assertImportsLikeClean(
                "1. e4 e5;the open game\n2. Nf3 Nc6 3. Bb5 a6 ; Morphy defence\n"
                        + "4. Ba4 Nf6 5. O-O Be7 1-0",
                "; rest-of-line comments");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void escapeLine() {
        // A line whose FIRST character is '%' is ignored entirely by PGN
        // readers (out-of-band, non-PGN data). Only at the start of a line;
        // a '%' elsewhere is not special.
        assertImportsLikeClean(
                "1. e4 e5\n% engine: depth 30 reached\n2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "a %-escape line (the whole line is ignored)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void blackMoveNumberContinuationAfterComment() {
        assertImportsLikeClean(
                "1. e4 e5 2. Nf3 {a comment forcing a black move number} 2... Nc6 "
                        + "3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "black move-number continuation (2... after a comment)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void blackMoveNumberContinuationWrongMoveNumber() {
        // Move numbers are redundant and are ignored on import: an inconsistent
        // continuation number (here "3..." while Black is to move on move 2)
        // must be tolerated, and the game must still reach the same position.
        assertImportsLikeClean(
                "1. e4 e5 2. Nf3 {a comment forcing a black move number} 3... Nc6 "
                        + "3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "a wrong black move-number continuation (3...) that must be ignored");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void completelyScrambledMoveNumbers() {
        // Move numbers carry no information the position does not already have,
        // so even wildly wrong numbers on both White's moves and Black's
        // continuations must be ignored — only the move sequence matters.
        assertImportsLikeClean(
                "5. e4 e5 1. Nf3 {x} 9... Nc6 88. Bb5 {y} 2... a6 7. Ba4 Nf6 12. O-O Be7 1-0",
                "completely scrambled move numbers that must be ignored");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void blackMoveNumberContinuationWithoutSpace() {
        // The move-number indication self-terminates at the first letter, so no
        // space is required between "2..." and the move: "2...Nc6" is valid,
        // just like "1.d4" for White.
        assertImportsLikeClean(
                "1. e4 e5 2. Nf3 {comment} 2...Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "black move-number continuation with no space (2...Nc6)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void blackMoveNumberContinuationAfterNag() {
        // A NAG between White's move and Black's reply separates them, so the
        // black move is written with its number and ellipsis (2... Nc6).
        assertImportsLikeClean(
                "1. e4 e5 2. Nf3 $1 2... Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "black move-number continuation (2...) after a NAG");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void numericAnnotationGlyphs() {
        assertImportsLikeClean(
                "1. e4 $1 e5 $2 2. Nf3 $10 Nc6 $13 3. Bb5 a6 4. Ba4 Nf6 5. O-O $14 Be7 1-0",
                "numeric annotation glyphs ($1, $10, ...)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void nagWithoutSurroundingWhitespace() {
        // '$' is self-delimiting: a move symbol self-terminates at '$', and the
        // NAG's digit run self-terminates at the next non-digit, so whitespace
        // around a NAG is optional (e4$1, $10Nc6).
        assertImportsLikeClean(
                "1. e4$1 e5 2. Nf3 $10Nc6 3. Bb5$10a6$11 4. Ba4 Nf6 5. O-O Be7 1-0",
                "NAGs with no surrounding whitespace (e4$1, $10Nc6)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void nagInAnUnusualPosition() {
        // Canonically a NAG follows the move it annotates, but since NAGs are
        // ignored anyway, a lenient importer that skips '$n' wherever it appears
        // must also tolerate a misplaced one (here between move number and move).
        assertImportsLikeClean(
                "1. e4 e5 2. $1 Nf3 Nc6 3.$10Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "a NAG between the move number and the move (2. $1 Nf3)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void moveSuffixAnnotations() {
        assertImportsLikeClean(
                "1. e4! e5?! 2. Nf3!? Nc6?? 3. Bb5!! a6?! 4. Ba4 Nf6 5. O-O Be7 1-0",
                "move suffix annotations (!, ?, !!, ??, !?, ?!)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void recursiveVariations() {
        assertImportsLikeClean(
                "1. e4 e5 2. Nf3 (2. Nc3 Nc6 3. f4) 2... Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "recursive annotation variations ( ... )");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void variationWithoutSurroundingWhitespace() {
        // '(' and ')' are self-delimiting like braces, so whitespace around a
        // variation is optional: a move symbol self-terminates at '(', and the
        // next token starts right after ')'. Here there is no space before '('
        // nor after ')'.
        assertImportsLikeClean(
                "1. e4 e5 2. Nf3(2. Nc3 Nc6 3. f4)2... Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "a variation with no surrounding whitespace (Nf3(...)2...)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void nestedVariations() {
        assertImportsLikeClean(
                "1. e4 e5 2. Nf3 (2. Nc3 Nc6 (2... Nf6 3. f4 exf4) 3. Bb5) 2... Nc6 "
                        + "3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "nested variations ( ( ... ) )");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void clockAndEvalCommandComments() {
        assertImportsLikeClean(
                "1. e4 {[%clk 0:03:00]} e5 {[%eval 0.17]} 2. Nf3 {+0.20/8 1.6s} Nc6 {book} "
                        + "3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0",
                "structured clock/eval command comments ([%clk], [%eval], engine output)");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void extraTagPairsBeyondSevenTagRoster() {
        String pgn = """
                [Event "PGN annotation import test"]
                [Site "?"]
                [Date "2026.07.24"]
                [Round "1"]
                [White "A"]
                [Black "B"]
                [Result "1-0"]
                [WhiteElo "2604"]
                [BlackElo "2397"]
                [ECO "C78"]
                [Opening "Ruy Lopez"]
                [TimeControl "40/60"]
                [Annotator "Tester"]

                1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 1-0
                """;

        assertEquals(cleanFen, finalFen(pgn),
                "extra tag pairs beyond the seven-tag roster must be ignored");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void gameTerminationMarkers() {
        // Only "1-0" is exercised by the other fixtures; the remaining markers
        // must be accepted too and must not change the imported position.
        assertEquals(cleanFen, finalFen(HEADER + MOVES + " 0-1"),
                "termination marker 0-1 must be accepted");
        assertEquals(cleanFen, finalFen(HEADER + MOVES + " 1/2-1/2"),
                "termination marker 1/2-1/2 must be accepted");
        assertEquals(cleanFen, finalFen(HEADER + MOVES + " *"),
                "termination marker * (unfinished game) must be accepted");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void enPassantSuffix() {
        // The redundant "e.p." after an en-passant capture must be ignored;
        // the move itself (exd6) is what matters.
        String withSuffix = HEADER + "1. e4 e6 2. e5 d5 3. exd6 e.p. 1-0";
        String withoutSuffix = HEADER + "1. e4 e6 2. e5 d5 3. exd6 1-0";

        assertEquals(finalFen(withoutSuffix), finalFen(withSuffix),
                "a redundant 'e.p.' suffix after an en-passant capture must be ignored");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void allAnnotationsCombined() {
        assertImportsLikeClean(
                "1. e4! {King's pawn} $1 e5 {[%clk 0:03:00]} 2. Nf3 (2. Nc3 Nc6) 2... Nc6 $10 "
                        + "3. Bb5!? {the Spanish} a6 ; Morphy defence\n"
                        + "4. Ba4 Nf6 5. O-O {castles} Be7 1-0",
                "all annotation kinds combined");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void midMoveCommentIsRejected() {
        // A comment may NOT appear inside a move token: '{' terminates the SAN
        // symbol, so "N{Bla}f3" is not the move Nf3 but an invalid token "N",
        // a comment, and a separate token "f3". The importer must reject it,
        // never silently read it as Nf3 (or as a stray f3 pawn move). This
        // guard is green today and must stay green once comments are handled.
        assertThrows(RuntimeException.class,
                () -> GameImporter.importerFor(HEADER + "1. e4 e5 2. N{Bla}f3 Nc6 1-0").importGame(),
                "a comment inside a move (N{Bla}f3) must be rejected, not parsed as Nf3");
    }

    @Test
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void realWorldCutechessGameImports() {
        // A verbatim cutechess self-play game (from test-results/): a {book}
        // tag and an {eval/depth time} comment on every move, a result-bearing
        // comment, move text wrapped across many lines, disambiguated moves
        // (Nec6, Raxe7), and a 1/2-1/2 terminator. Exactly the kind of PGN that
        // previously had to have its comments stripped before import; it must
        // now parse without error, all 70 plies.
        String pgn = """
                [Event "?"]
                [Site "?"]
                [Date "2026.07.19"]
                [Round "1"]
                [White "4.2.1-qsearch-see"]
                [Black "4.2.0"]
                [Result "1/2-1/2"]
                [ECO "A40"]
                [Opening "Queen's pawn"]
                [PlyCount "70"]
                [TimeControl "40/60"]

                1. d4 {book} e6 {book} 2. a3 {book} d6 {book} 3. e4 {+0.20/8 1.6s}
                d5 {-0.20/7 1.5s} 4. e5 {+0.35/8 1.5s} Ne7 {-0.35/7 1.5s} 5. Nc3 {+0.39/8 1.5s}
                Nec6 {-0.39/7 1.5s} 6. Nf3 {+0.45/8 0.76s} Be7 {-0.53/8 1.5s}
                7. Bd3 {+0.47/8 1.0s} O-O {-0.55/8 1.5s} 8. O-O {+0.46/8 0.87s}
                Re8 {-0.46/7 1.5s} 9. Re1 {+0.48/8 1.1s} Nd7 {-0.48/7 0.33s}
                10. Bb5 {+0.48/8 0.83s} Ncb8 {-0.51/7 0.84s} 11. Qd3 {+0.48/8 1.6s}
                a6 {-0.55/8 0.99s} 12. Bxd7 {+0.46/8 1.6s} Nxd7 {0.00/10 1.6s}
                13. Bf4 {+0.55/7 1.6s} c5 {-0.39/7 1.4s} 14. dxc5 {+0.40/8 0.99s}
                Nxc5 {-0.44/8 1.4s} 15. Qd4 {+0.44/7 1.6s} Bd7 {-0.32/7 1.6s}
                16. Be3 {+0.41/7 1.2s} Rc8 {-0.41/6 1.6s} 17. h3 {+0.42/7 1.6s}
                b5 {-0.30/7 1.5s} 18. Rec1 {+0.41/7 1.3s} Na4 {-0.22/7 1.3s}
                19. Nxa4 {+0.15/8 0.81s} bxa4 {0.00/10 1.6s} 20. Qa7 {+0.20/7 1.7s}
                Bb5 {-0.11/8 1.1s} 21. c3 {+0.17/7 1.7s} Be2 {-0.01/7 1.1s}
                22. Bb6 {+0.27/9 1.7s} Ra8 {-0.14/9 1.7s} 23. Bxd8 {+0.17/10 1.7s}
                Rxa7 {0.00/11 1.7s} 24. Bxe7 {+0.15/10 1.7s} Raxe7 {0.00/12 1.7s}
                25. Nd4 {+0.14/11 1.7s} Bc4 {0.00/11 1.7s} 26. Rd1 {+0.14/10 1.6s}
                f6 {-0.14/9 1.7s} 27. f4 {+0.20/9 1.7s} Rb7 {-0.16/9 1.7s}
                28. Rd2 {+0.20/9 1.1s} g5 {-0.12/9 1.7s} 29. g3 {+0.23/9 1.5s}
                gxf4 {-0.11/9 1.7s} 30. gxf4 {+0.24/9 1.8s} Kf7 {-0.13/9 1.0s}
                31. Kh2 {+0.25/9 1.5s} Reb8 {-0.12/10 1.5s} 32. Rb1 {+0.17/9 1.9s}
                Ba2 {-0.12/10 1.3s} 33. Ra1 {+0.17/10 0.94s} Bc4 {-0.12/11 1.0s}
                34. Rb1 {+0.12/10 1.3s} Ba2 {0.00/11 1.1s} 35. Ra1 {+0.17/10 2.1s}
                Bc4 {0.00/13 2.1s, Draw by 3-fold repetition} 1/2-1/2
                """;

        Game game = assertDoesNotThrow(() -> GameImporter.importerFor(pgn).importGame(),
                "a real cutechess game with comments on every move must import without error");

        try {
            assertEquals(70, game.getBoard().getGameStatus().getPlyCount(),
                    "all 70 plies of the game must be imported");
        } finally {
            game.shutdown();
        }
    }

    private void assertImportsLikeClean(String annotatedMoves, String what) {
        assertEquals(cleanFen, finalFen(HEADER + annotatedMoves),
                "a PGN with " + what + " must import without error and reach the same position as the clean game");
    }

    /**
     * Imports a complete PGN and returns the exported FEN of the resulting
     * position. The game is shut down before returning so its engine executor
     * does not leak.
     *
     * @param pgn the complete PGN (tag pairs plus move text)
     * @return the FEN of the position after the last imported move
     */
    private static String finalFen(String pgn) {
        Game game = GameImporter.importerFor(pgn).importGame();

        try {
            return game.exportFEN();
        } finally {
            game.shutdown();
        }
    }
}
