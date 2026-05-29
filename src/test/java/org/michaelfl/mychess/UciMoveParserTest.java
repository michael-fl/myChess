package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class UciMoveParserTest {

    // ---- parse: normal moves ----

    @Test
    void parse_normalPawnMoveFromStart_returnsNonPromotionMove() {
        var board = new Game().getBoard();

        var md = UciMoveParser.parse("e2e4", board);

        assertNotNull(md, "parser must return a MoveDescription for e2e4");
        assertTrue(md.pawnPromotionPiece() <= 0, "no promotion expected on normal pawn move");
        assertFalse(md.isCastlingKingSide(), "not a kingside castling");
        assertFalse(md.isCastlingQueenSide(), "not a queenside castling");
    }

    @Test
    void parse_normalKnightMove_returnsNonPromotionMove() {
        var board = new Game().getBoard();

        var md = UciMoveParser.parse("b1c3", board);

        assertNotNull(md, "parser must return a MoveDescription for b1c3");
        assertTrue(md.pawnPromotionPiece() <= 0, "no promotion expected on knight move");
    }

    // ---- parse: castling ----

    @Test
    void parse_e1g1_whenKingOnE1_returnsWhiteKingsideCastling() {
        var game = GameImporter.importerFor("1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6").importGame();

        var md = UciMoveParser.parse("e1g1", game.getBoard());
        game.makeMove(md);

        assertEquals(Board.whiteKing, game.getBoard().get(Board.g1), "King ends on g1");
        assertEquals(Board.whiteRook, game.getBoard().get(Board.f1), "Rook ends on f1");
    }

    @Test
    void parse_e1c1_whenKingOnE1_returnsWhiteQueensideCastling() {
        var board = Fen.importFEN("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1");
        var game = new Game(Game.standardConfig(), board);

        var md = UciMoveParser.parse("e1c1", game.getBoard());
        game.makeMove(md);

        assertEquals(Board.whiteKing, game.getBoard().get(Board.c1), "King ends on c1");
        assertEquals(Board.whiteRook, game.getBoard().get(Board.d1), "Rook ends on d1");
    }

    @Test
    void parse_e8g8_whenKingOnE8_returnsBlackKingsideCastling() {
        var board = Fen.importFEN("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R b KQkq - 0 1");
        var game = new Game(Game.standardConfig(), board);

        var md = UciMoveParser.parse("e8g8", game.getBoard());
        game.makeMove(md);

        assertEquals(Board.blackKing, game.getBoard().get(Board.g8), "Black king ends on g8");
        assertEquals(Board.blackRook, game.getBoard().get(Board.f8), "Black rook ends on f8");
    }

    @Test
    void parse_e8c8_whenKingOnE8_returnsBlackQueensideCastling() {
        var board = Fen.importFEN("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R b KQkq - 0 1");
        var game = new Game(Game.standardConfig(), board);

        var md = UciMoveParser.parse("e8c8", game.getBoard());
        game.makeMove(md);

        assertEquals(Board.blackKing, game.getBoard().get(Board.c8), "Black king ends on c8");
        assertEquals(Board.blackRook, game.getBoard().get(Board.d8), "Black rook ends on d8");
    }

    @Test
    void parse_twoSquareMoveOfNonKing_isNotFlaggedAsCastling() {
        // Queen on c1 moving two squares to a1 is NOT castling — only kings
        // can castle. The parser must not flag this as castling.
        var board = Fen.importFEN("4k3/8/8/8/8/8/8/2Q1K2R w K - 0 1");

        var md = UciMoveParser.parse("c1a1", board);

        assertNotNull(md, "non-king move should still parse");
        assertFalse(md.isCastlingKingSide(),
                "should not be flagged as kingside castling");
        assertFalse(md.isCastlingQueenSide(),
                "should not be flagged as queenside castling");
    }

    // ---- parse: promotion ----

    @ParameterizedTest
    @CsvSource({
            "d7d8q,whiteQueen",
            "d7d8r,whiteRook",
            "d7d8b,whiteBishop",
            "d7d8n,whiteKnight"
    })
    void parse_whitePromotion_returnsCorrectPromotionPiece(String uciMove, String pieceName) {
        var board = Fen.importFEN("4k3/3P4/8/8/8/8/8/4K3 w - - 0 1");

        var md = UciMoveParser.parse(uciMove, board);

        byte expected = switch (pieceName) {
            case "whiteQueen" -> Board.whiteQueen;
            case "whiteRook" -> Board.whiteRook;
            case "whiteBishop" -> Board.whiteBishop;
            case "whiteKnight" -> Board.whiteKnight;
            default -> throw new IllegalStateException();
        };
        assertEquals(expected, md.pawnPromotionPiece(), "promotion piece must match suffix '" + uciMove + "'");
    }

    @ParameterizedTest
    @CsvSource({
            "d2d1q,blackQueen",
            "d2d1r,blackRook",
            "d2d1b,blackBishop",
            "d2d1n,blackKnight"
    })
    void parse_blackPromotion_returnsCorrectPromotionPiece(String uciMove, String pieceName) {
        var board = Fen.importFEN("4k3/8/8/8/8/8/3p4/4K3 b - - 0 1");

        var md = UciMoveParser.parse(uciMove, board);

        byte expected = switch (pieceName) {
            case "blackQueen" -> Board.blackQueen;
            case "blackRook" -> Board.blackRook;
            case "blackBishop" -> Board.blackBishop;
            case "blackKnight" -> Board.blackKnight;
            default -> throw new IllegalStateException();
        };
        assertEquals(expected, md.pawnPromotionPiece(), "promotion piece must match suffix '" + uciMove + "'");
    }

    // ---- parse: invalid input ----

    @ParameterizedTest
    @ValueSource(strings = {"e2e", "e2", "e2e4e4", ""})
    void parse_inputWithInvalidLength_throws(String bad) {
        var board = new Game().getBoard();

        assertThrows(IllegalArgumentException.class, () -> UciMoveParser.parse(bad, board),
                "input of length " + bad.length() + " must throw");
    }

    @Test
    void parse_nullInput_throws() {
        var board = new Game().getBoard();

        assertThrows(IllegalArgumentException.class, () -> UciMoveParser.parse(null, board),
                "null input must throw");
    }

    @Test
    void parse_invalidSquareNotation_throws() {
        var board = new Game().getBoard();

        assertThrows(IllegalArgumentException.class, () -> UciMoveParser.parse("z9e4", board),
                "out-of-range squares must throw");
    }

    // ---- toUci: packed-int → UCI string ----

    @Test
    void toUci_normalMove_returnsPlainFourCharString() {
        var board = new Game().getBoard();
        int packed = Move.create((byte) Board.e2, (byte) Board.e4, Board.empty, Move.typeNormal);

        assertEquals("e2e4", UciMoveParser.toUci(packed, board));
    }

    @ParameterizedTest
    @CsvSource({
            "typePawnPromotionQueen, d7d8q",
            "typePawnPromotionRook, d7d8r",
            "typePawnPromotionBishop, d7d8b",
            "typePawnPromotionKnight, d7d8n"
    })
    void toUci_promotion_appendsCorrectSuffix(String typeName, String expectedUci) {
        var board = new Game().getBoard();
        byte type = switch (typeName) {
            case "typePawnPromotionQueen" -> Move.typePawnPromotionQueen;
            case "typePawnPromotionRook" -> Move.typePawnPromotionRook;
            case "typePawnPromotionBishop" -> Move.typePawnPromotionBishop;
            case "typePawnPromotionKnight" -> Move.typePawnPromotionKnight;
            default -> throw new IllegalStateException();
        };
        int packed = Move.create((byte) Board.d7, (byte) Board.d8, Board.empty, type);

        assertEquals(expectedUci, UciMoveParser.toUci(packed, board));
    }

    @Test
    void toUci_castling_emitsKingMoveWithoutSuffix() {
        // UCI convention: castling is written as the king's from/to fields, no
        // suffix. The Move type bits stay typeCastlingKingSide but don't show.
        var board = new Game().getBoard();
        int packedKing = Move.create((byte) Board.e1, (byte) Board.g1, Board.empty, Move.typeCastlingKingSide);
        int packedQueen = Move.create((byte) Board.e1, (byte) Board.c1, Board.empty, Move.typeCastlingQueenSide);

        assertEquals("e1g1", UciMoveParser.toUci(packedKing, board));
        assertEquals("e1c1", UciMoveParser.toUci(packedQueen, board));
    }

    @Test
    void toUci_enPassant_emitsDiagonalPawnMoveWithoutSuffix() {
        // UCI: en passant looks like an ordinary diagonal pawn move. No suffix.
        var board = new Game().getBoard();
        int packed = Move.create((byte) Board.e5, (byte) Board.d6, Board.blackPawn, Move.typeEnPassant);

        assertEquals("e5d6", UciMoveParser.toUci(packed, board));
    }

    // ---- round-trip: parse → board apply → toUci of packed ----

    @Test
    void roundTrip_normalMove_preservesUciNotation() {
        var game = new Game();
        var md = UciMoveParser.parse("e2e4", game.getBoard());

        game.makeMove(md);

        // Last move on the status stack should produce the same UCI string.
        int lastMove = game.getGameStatus().getLastMove();
        assertEquals("e2e4", UciMoveParser.toUci(lastMove, game.getBoard()),
                "round-trip parse → makeMove → toUci should reproduce the UCI string");
    }

}
