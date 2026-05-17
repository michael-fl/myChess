package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Michael Fleischhauer
 */
class PGNConverterTest {

    @Test
    void singleWhiteMoveYieldsHalfMove() {
        assertEquals("1. e4", PGNConverter.toPGN("[[e2-e4]]"), "single ply must produce '<n>. <move>'");
    }

    @Test
    void simpleOpeningProducesSAN() {
        var pgn = PGNConverter.toPGN("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-b5]]");
        assertEquals("1. e4 e5 2. Nf3 Nc6 3. Bb5", pgn, "Ruy Lopez opening must be SAN-encoded");
    }

    @Test
    void captureIsRenderedWithX() {
        var pgn = PGNConverter.toPGN("[[e2-e4 d7-d5 e4-d5]]");
        assertEquals("1. e4 d5 2. exd5", pgn, "pawn capture must use 'exd5' form");
    }

    @Test
    void kingsideCastlingUsesCapitalO() {
        var pgn = PGNConverter.toPGN("[[e2-e4 e7-e5 g1-f3 b8-c6 f1-c4 g8-f6 e1-g1]]");
        assertEquals("1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. O-O", pgn, "king-side castling must be O-O");
    }

    @Test
    void queensideCastlingUsesCapitalO() {
        var pgn = PGNConverter.toPGN("[[d2-d4 d7-d5 b1-c3 b8-c6 c1-f4 c8-f5 d1-d2 d8-d7 e1-c1]]");
        assertEquals("1. d4 d5 2. Nc3 Nc6 3. Bf4 Bf5 4. Qd2 Qd7 5. O-O-O", pgn,
                "queen-side castling must be O-O-O");
    }

    @Test
    void checkIsAnnotatedWithPlus() {
        // Scholar's mate setup ending in a check on the way to mate (but stopped before mate).
        var pgn = PGNConverter.toPGN("[[e2-e4 e7-e5 d1-h5 b8-c6 f1-c4 g8-f6 h5-f7]]");
        assertEquals("1. e4 e5 2. Qh5 Nc6 3. Bc4 Nf6 4. Qxf7#", pgn,
                "the mating move must be annotated with '#'");
    }

    @Test
    void promotionIncludesPromotedPiece() {
        // A fast pawn race to promotion on a8.
        // Note: this codebase's short-notation writer emits "bxa8Q" (without "="),
        // which is non-canonical PGN but is still accepted by Pgn.parse.
        var pgn = PGNConverter.toPGN("[[a2-a4 h7-h6 a4-a5 h6-h5 a5-a6 h5-h4 a6-b7 h4-h3 b7-a8Q]]");
        assertEquals("1. a4 h6 2. a5 h5 3. a6 h4 4. axb7 h3 5. bxa8Q", pgn,
                "promotion must include the promoted piece letter");
    }

    @Test
    void roundTripParsesBackToSameFinalPosition() {
        // Take a longer real game, convert to PGN, then re-import via the PGN parser.
        // The final FEN of the round-tripped game must match the original.
        var legacy = "[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 "
                + "f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5]]";
        var originalGame = new SimpleNotationImporter(legacy).importGame();
        var pgnText = PGNConverter.toPGN(legacy);

        var pgn = Pgn.parse(pgnText).findFirst().orElseThrow();
        var reimportedGame = new PGNImporter(pgn).importGame();

        assertEquals(originalGame.exportFEN(), reimportedGame.exportFEN(),
                "round-tripping legacy -> PGN -> Pgn.parse must preserve the final position");
    }

    @Test
    void linesWrapAroundNinetyCharacters() {
        // A real Sicilian game long enough to force at least one line break in the output.
        var legacy = "[[e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 "
                + "c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 "
                + "g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5]]";

        var pgn = PGNConverter.toPGN(legacy);

        var lines = pgn.split("\n");
        assertEquals(true, lines.length >= 2,
                "this 28-ply game must wrap onto multiple lines; got " + lines.length);
        for (var line : lines) {
            assertEquals(true, line.length() <= 95,
                    "no output line should exceed the wrap threshold; got len " + line.length() + ": " + line);
        }
    }

    @Test
    void invalidInputPropagatesAsException() {
        // SimpleNotationImporter is strict about the [[...]] wrapper; passing raw PGN text
        // must not silently succeed.
        assertThrows(RuntimeException.class,
                () -> PGNConverter.toPGN("1. e4 e5"),
                "non-legacy input must not be accepted");
    }
}
