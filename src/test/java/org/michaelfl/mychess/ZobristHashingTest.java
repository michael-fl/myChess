package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.michaelfl.mychess.Board.*;
import static org.michaelfl.mychess.RandomNumbers.RANDOM_NUMBERS;

/**
 * @author Michael Fleischhauer
 */
class ZobristHashingTest {

    private static final int CASTLING_RIGHTS_INDEX = 12 * 64 + 1; // length = 16

    @Test
    void testHashOfStartPosition() {
        var game = new Game();

        var expectedHash = 0L;

        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whiteRook) * 64];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whiteKnight) * 64 + 1];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whiteBishop) * 64 + 2];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whiteQueen) * 64 + 3];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whiteKing) * 64 + 4];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whiteBishop) * 64 + 5];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whiteKnight) * 64 + 6];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whiteRook) * 64 + 7];

        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whitePawn) * 64 + 8];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whitePawn) * 64 + 8 + 1];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whitePawn) * 64 + 8 + 2];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whitePawn) * 64 + 8 + 3];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whitePawn) * 64 + 8 + 4];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whitePawn) * 64 + 8 + 5];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whitePawn) * 64 + 8 + 6];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whitePawn) * 64 + 8 + 7];

        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackPawn) * 64 + 6 * 8];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackPawn) * 64 + 6 * 8 + 1];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackPawn) * 64 + 6 * 8 + 2];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackPawn) * 64 + 6 * 8 + 3];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackPawn) * 64 + 6 * 8 + 4];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackPawn) * 64 + 6 * 8 + 5];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackPawn) * 64 + 6 * 8 + 6];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackPawn) * 64 + 6 * 8 + 7];

        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackRook) * 64 + 7 * 8];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackKnight) * 64 + 7 * 8 + 1];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackBishop) * 64 + 7 * 8 + 2];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackQueen) * 64 + 7 * 8 + 3];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackKing) * 64 + 7 * 8 + 4];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackBishop) * 64 + 7 * 8 + 5];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackKnight) * 64 + 7 * 8 + 6];
        expectedHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackRook) * 64 + 7 * 8 + 7];

        expectedHash ^= RANDOM_NUMBERS[CASTLING_RIGHTS_INDEX + game.getGameStatus().getCastlingState()];

        var hash = game.getBoard().calculatePositionHash();
        assertEquals(expectedHash, hash, "wrong hash");
        hash = game.getBoard().getGameStatus().getPositionHash();
        assertEquals(expectedHash, hash, "wrong hash");
    }

    @Test
    void testIncrementalUpdate() {
        var game = new Game();
        String[] moves = "b1-c3 d7-d6 e2-e4 e7-e5 g1-f3 g8-f6 d2-d4 d8-e7 c1-g5 b8-d7 f1-d3 d7-b6 g5-f6 e7-f6 c3-b5 f6-e7 d4-e5 d6-e5 e1-g1 a7-a6 b5-c3 e7-f6 c3-d5 b6-d5 e4-d5 c8-g4 d1-e2 g4-f3 g2-f3 e8-c8 e2-e4 c8-b8 a1-e1 f6-h6 g1-h1 f8-d6 f1-g1 h6-f6 e4-f5 f6-f5 d3-f5 g7-g6 f5-g4 h8-e8 e1-e2 d6-c5 c2-c4 c5-d4 g1-d1 d4-c5 d1-e1 f7-f5 g4-h3 c5-b4 e1-d1 e8-e7 a2-a3 b4-d6 b2-b4 e7-e8 c4-c5 d6-f8 e2-c2 f8-g7 h3-f1 e5-e4 f3-e4 f5-e4 b4-b5 a6-a5 f1-c4 g7-f6 d5-d6 c7-d6 d1-d6 f6-e5 d6-d5 e5-d4 c5-c6 b7-c6 b5-c6 b8-c7 d5-a5 e8-f8 a5-b5 f8-f2 c2-f2 d4-f2 c4-d5 e4-e3 b5-b7 c7-c8 d5-e6 d8-d7 b7-d7 f2-g3 h2-g3 h7-h5 d7-g7 c8-b8 c6-c7 b8-b7 c7-c8Q b7-b6 g7-b7 b6-a5 c8-a8".split(" ");

        for (String moveString : moves) {
            game.makeMove(MoveDescription.fromString(moveString, game.getTurn()));
            assertEquals(game.getBoard().calculatePositionHash(), game.getBoard().getGameStatus().getPositionHash(), "Wrong hash for position " + game.exportMoves());
        }
    }

    @Test
    void testIncrementalUpdateWithRevert() {
        var game = new Game();
        String[] moves = "b1-c3 d7-d6 e2-e4 e7-e5 g1-f3 g8-f6 d2-d4 d8-e7 c1-g5 b8-d7 f1-d3 d7-b6 g5-f6 e7-f6 c3-b5 f6-e7 d4-e5 d6-e5 e1-g1 a7-a6 b5-c3 e7-f6 c3-d5 b6-d5 e4-d5 c8-g4 d1-e2 g4-f3 g2-f3 e8-c8 e2-e4 c8-b8 a1-e1 f6-h6 g1-h1 f8-d6 f1-g1 h6-f6 e4-f5 f6-f5 d3-f5 g7-g6 f5-g4 h8-e8 e1-e2 d6-c5 c2-c4 c5-d4 g1-d1 d4-c5 d1-e1 f7-f5 g4-h3 c5-b4 e1-d1 e8-e7 a2-a3 b4-d6 b2-b4 e7-e8 c4-c5 d6-f8 e2-c2 f8-g7 h3-f1 e5-e4 f3-e4 f5-e4 b4-b5 a6-a5 f1-c4 g7-f6 d5-d6 c7-d6 d1-d6 f6-e5 d6-d5 e5-d4 c5-c6 b7-c6 b5-c6 b8-c7 d5-a5 e8-f8 a5-b5 f8-f2 c2-f2 d4-f2 c4-d5 e4-e3 b5-b7 c7-c8 d5-e6 d8-d7 b7-d7 f2-g3 h2-g3 h7-h5 d7-g7 c8-b8 c6-c7 b8-b7 c7-c8Q b7-b6 g7-b7 b6-a5 c8-a8".split(" ");
        long previousHash = game.getGameStatus().getPositionHash();

        for (String moveString : moves) {
            MoveDescription move = MoveDescription.fromString(moveString, game.getTurn());
            game.makeMove(move);
            long expectedHash = game.getBoard().calculatePositionHash();

            // revert
            game.revertMove();
            assertEquals(previousHash, game.getBoard().getGameStatus().getPositionHash(), "Wrong hash for position " + game.exportMoves());

            // redo move
            game.makeMove(move);
            assertEquals(expectedHash, game.getBoard().getGameStatus().getPositionHash(), "Wrong hash for position " + game.exportMoves());

            previousHash = expectedHash;
        }
    }

    @Test
    void testWhiteEnPassantField() {
        var game = new Game();
        game.makeMove(MoveDescription.fromString("e2-e4", game.getTurn()));

        assertEquals(game.getBoard().calculatePositionHash(), game.getBoard().getGameStatus().getPositionHash(), "Wrong position hash");
    }

    @Test
    void testBlackEnPassantField() {
        var game = new Game();
        game.makeMove(MoveDescription.fromString("e2-e4", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e7-e5", game.getTurn()));

        assertEquals(game.getBoard().calculatePositionHash(), game.getBoard().getGameStatus().getPositionHash(), "Wrong position hash");
    }

    @Test
    void testDifferentHashForDifferentPositions() {
        var game = new Game();
        game.makeMove(MoveDescription.fromString("e2-e3", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e7-e6", game.getTurn()));
        game.makeMove(MoveDescription.fromString("d2-d3", game.getTurn()));
        game.makeMove(MoveDescription.fromString("g8-f6", game.getTurn()));
        var hash1 = game.getBoard().calculatePositionHash();
        assertEquals(hash1, game.getBoard().getGameStatus().getPositionHash(), "Wrong position hash");

        game = new Game();
        game.makeMove(MoveDescription.fromString("d2-d3", game.getTurn()));
        game.makeMove(MoveDescription.fromString("g8-h6", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e2-e3", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e7-e6", game.getTurn()));
        var hash2 = game.getBoard().calculatePositionHash();
        assertEquals(hash2, game.getBoard().getGameStatus().getPositionHash(), "Wrong position hash");
        assertNotEquals(hash1, hash2, "Hash should be different for different positions");
    }

    @Test
    void testSameHashForSamePosition() {
        var game = new Game();
        game.makeMove(MoveDescription.fromString("e2-e3", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e7-e6", game.getTurn()));
        game.makeMove(MoveDescription.fromString("d2-d3", game.getTurn()));
        game.makeMove(MoveDescription.fromString("g8-f6", game.getTurn()));
        var hash1 = game.getBoard().calculatePositionHash();
        assertEquals(hash1, game.getBoard().getGameStatus().getPositionHash(), "Wrong position hash");

        game = new Game();
        game.makeMove(MoveDescription.fromString("d2-d3", game.getTurn()));
        game.makeMove(MoveDescription.fromString("g8-f6", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e2-e3", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e7-e6", game.getTurn()));
        var hash2 = game.getBoard().calculatePositionHash();
        assertEquals(hash2, game.getBoard().getGameStatus().getPositionHash(), "Wrong position hash");
        assertEquals(hash1, hash2, "Hash should be same for same positions without en passant field");
    }

    @Test
    void testEnPassantMakesDifferenceForSamePosition() {
        var game = new Game();
        game.makeMove(MoveDescription.fromString("e2-e4", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e7-e5", game.getTurn()));
        game.makeMove(MoveDescription.fromString("d2-d4", game.getTurn()));
        var hash1 = game.getBoard().calculatePositionHash();
        assertEquals(hash1, game.getBoard().getGameStatus().getPositionHash(), "Wrong position hash");

        game = new Game();
        game.makeMove(MoveDescription.fromString("d2-d4", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e7-e5", game.getTurn()));
        game.makeMove(MoveDescription.fromString("e2-e4", game.getTurn()));
        var hash2 = game.getBoard().calculatePositionHash();
        assertEquals(hash2, game.getBoard().getGameStatus().getPositionHash(), "Wrong position hash");
        assertNotEquals(hash1, hash2, "Hash positions should be different due to different en passant fields");
    }
}
