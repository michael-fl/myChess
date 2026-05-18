package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class SimpleNotationImporterTest {

    @Test
    void importsSimpleOpening() {
        var game = new SimpleNotationImporter("[[e2-e4 e7-e5]]").importGame();
        assertEquals(2, game.getGameStatus().getPlyCount(),
                "2 plies must be applied");
        assertEquals(GameResult.ONGOING, game.getResult());
    }

    @Test
    void importsCastling() {
        var game = new SimpleNotationImporter(
                "[[e2-e4 e7-e5 g1-f3 b8-c6 f1-c4 g8-f6 e1-g1]]")
                .importGame();
        var status = game.getGameStatus();
        assertNotEquals(0, status.getCastlingState() & GameStatus.BIT_WHITE_HAS_CASTLED,
                "White has-castled bit must be set after O-O");
    }

    @Test
    void importsPromotion() {
        var game = new SimpleNotationImporter(
                "[[a2-a4 h7-h5 a4-a5 h5-h4 a5-a6 h4-h3 a6-b7 h3-g2 b7-a8Q]]")
                .importGame();
        assertEquals(Board.whiteQueen, game.getBoard().getPieceAt(0, 7),
                "a8 must hold a white queen after promotion");
    }

    @Test
    void toleratesLeadingAndTrailingWhitespace() {
        var game = new SimpleNotationImporter("   [[e2-e4 e7-e5]]   \n").importGame();
        assertEquals(2, game.getGameStatus().getPlyCount());
    }

    @Test
    void rejectsMalformedMove() {
        var importer = new SimpleNotationImporter("[[zzzz]]");
        assertThrows(IllegalArgumentException.class, importer::importGame,
                "Malformed move notation must be rejected");
    }
}
