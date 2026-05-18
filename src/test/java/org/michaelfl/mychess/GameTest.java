package org.michaelfl.mychess;

import org.junit.jupiter.api.Test;
import org.michaelfl.mychess.Game.GameResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class GameTest {

    @Test
    void testBlackCheckmate() {
        GameImporter importer = GameImporter.importerFor("""
                1. Nc3 d6 2. e4 e5 3. Nf3 Nf6 4. d4 Qe7 5. Bg5 Nbd7 6. Bd3 Nb6 7. Bxf6 Qxf6 8. Nb5 Qe7 9.
                dxe5 dxe5 10. O-O a6 11. Nc3 Qf6 12. Nd5 Nxd5 13. exd5 Bg4 14. Qe2 Bxf3 15. gxf3 O-O-O 16.
                Qe4 Kb8 17. Rae1 Qh6 18. Kh1 Bd6 19. Rg1 Qf6 20. Qf5 Qxf5 21. Bxf5 g6 22. Bg4 Rhe8 23. Re2
                Bc5 24. c4 Bd4 25. Rd1 Bc5 26. Rde1 f5 27. Bh3 Bb4 28. Rd1 Re7 29. a3 Bd6 30. b4 Ree8 31.
                c5 Bf8 32. Rc2 Bg7 33. Bf1 e4 34. fxe4 fxe4 35. b5 a5 36. Bc4 Bf6 37. d6 cxd6 38. Rxd6 Be5
                39. Rd5 Bd4 40. c6 bxc6 41. bxc6 Kc7 42. Rxa5 Rf8 43. Rb5 Rxf2 44. Rxf2 Bxf2 45. Bd5 e3
                46. Rb7+ Kc8 47. Be6+ Rd7 48. Rxd7 Bg3 49. hxg3 h5 50. Rg7+ Kb8 51. c7+ Kb7 52. c8Q+ Kb6
                53. Rb7+ Ka5
                """);
        var game = importer.importGame();

        assertEquals(GameResult.ONGOING, game.getResult(), "game should not be finished yet");

        game.makeMove(MoveDescription.fromString("c8-a8", GameStatus.TURN_WHITE));

        assertEquals(GameResult.CHECKMATE, game.getResult(), "game status should be black checkmate");
    }

    @Test
    void testToShortNotation() {
        var moves = "e2-e4 c7-c5 g1-f3 d7-d6 d2-d4 c5-d4 f3-d4 g8-f6 b1-c3 a7-a6 c1-g5 e7-e6 f2-f4 f8-e7 d1-f3 d8-c7 e1-c1 b8-d7 g2-g4 b7-b5 g5-f6 d7-f6 g4-g5 f6-d7 f4-f5 e7-g5 c1-b1 d7-e5 f3-h5 c7-d8 d4-e6 c8-e6 f5-e6 e8-g8 h1-g1 g5-f6 f1-h3 f8-e8 e6-f7 e5-f7 h3-f5 h7-h6 c3-d5 a6-a5 h5-g6 a5-a4 d5-f6 d8-f6 g6-f6 f7-g5 f6-g6 d6-d5 d1-d5 e8-e7 g1-g5 h6-g5 f5-e6 e7-e6 g6-e6 g8-h8 d5-g5 g7-g6 e6-g6 a8-b8 g6-g7".split(" ");
        var expectedShortMoves = "e4 c5 Nf3 d6 d4 cxd4 Nxd4 Nf6 Nc3 a6 Bg5 e6 f4 Be7 Qf3 Qc7 0-0-0 Nbd7 g4 b5 Bxf6 Nxf6 g5 Nd7 f5 Bxg5+ Kb1 Ne5 Qh5 Qd8 Nxe6 Bxe6 fxe6 0-0 Rg1 Bf6 Bh3 Re8 exf7+ Nxf7 Bf5 h6 Nd5 a5 Qg6 a4 Nxf6+ Qxf6 Qxf6 Ng5 Qg6 d5 Rxd5 Re7 Rxg5 hxg5 Be6+ Rxe6 Qxe6+ Kh8 Rxg5 g6 Qxg6 Rb8 Qg7#".split(" ");

        var game = new Game();
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());

        for (int i = 0; i < moves.length; i++) {
            if ("Bxg5+".equals(expectedShortMoves[i])) {
                System.out.println();
            }
            var moveDescr = MoveDescription.fromString(moves[i], game.getTurn());
            moveDescr = game.getBoard().resolveMoveDescription(moveDescr, moveGenerator);
            var move = game.getBoard().moveDescriptionToMove(moveDescr);
            var shortNotation = game.getBoard().moveToShortNotation(move);

            if (!expectedShortMoves[i].equals(shortNotation.toString())) {
                System.out.println();
            }
            assertEquals(expectedShortMoves[i], shortNotation.toString(), "Wrong short move notation");

            game.makeMove(moveDescr);
        }
    }

    // ---- Rollback and verification paths ----

    @Test
    void illegalMoveOnOngoingGameLeavesBoardUntouched() {
        var game = new Game();
        var fenBefore = game.exportFEN();
        var hashBefore = game.getGameStatus().getPositionHash();

        // e4 is legal, but "e7" by white at the start position is not a legal pawn move.
        var bad = MoveDescription.fromString("e7", game.getTurn());
        assertThrows(IllegalMoveException.class, () -> game.makeMove(bad),
                "An impossible move must throw IllegalMoveException");

        assertEquals(fenBefore, game.exportFEN(),
                "After a rejected move the FEN must be unchanged");
        assertEquals(hashBefore, game.getGameStatus().getPositionHash(),
                "After a rejected move the Zobrist hash must be unchanged");
        assertEquals(GameResult.ONGOING, game.getResult(),
                "Result must still be ONGOING after a rejected move");
    }

    @Test
    void verifyMoveRejectsBogusCheckAnnotation() {
        // 1. e4 is a legal first move but is not a check. The "+" suffix must be rejected.
        var game = new Game();
        var bogus = MoveDescription.fromString("e4+", GameStatus.TURN_WHITE);

        var ex = assertThrows(IllegalMoveException.class, () -> game.makeMove(bogus),
                "Move with bogus check annotation must throw");
        assertTrue(ex.getMessage().contains("does not give check"),
                "Exception message should pinpoint the bogus check annotation: " + ex.getMessage());
    }

    @Test
    void verifyMoveRejectsBogusCheckmateAnnotation() {
        var game = new Game();
        var bogus = MoveDescription.fromString("e4#", GameStatus.TURN_WHITE);

        var ex = assertThrows(IllegalMoveException.class, () -> game.makeMove(bogus),
                "Move with bogus checkmate annotation must throw");
        assertTrue(ex.getMessage().toLowerCase().contains("checkmate")
                        || ex.getMessage().toLowerCase().contains("check"),
                "Exception message should reference the bogus annotation: " + ex.getMessage());
    }

    @Test
    void makeMoveOnFinishedGameThrows() {
        // Set up a Scholar's mate to terminate the game.
        var game = GameImporter.importerFor("""
                1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7#
                """).importGame();
        assertEquals(GameResult.CHECKMATE, game.getResult(),
                "Game must be CHECKMATE after Scholar's mate");

        var anyMove = MoveDescription.fromString("Kxf7", game.getTurn());
        assertThrows(IllegalStateException.class, () -> game.makeMove(anyMove),
                "Making a move on a finished game must throw IllegalStateException");
    }

    @Test
    void revertOnEmptyStackThrows() {
        var game = new Game();
        assertThrows(IllegalStateException.class, game::revertMove,
                "Reverting before any move was made must throw IllegalStateException");
    }

    @Test
    void revertAfterMate_restoresOngoingState() {
        var game = GameImporter.importerFor("""
                1. f3 e6 2. g4 Qh4
                """).importGame();
        assertEquals(GameResult.CHECKMATE, game.getResult(), "Fool's mate must terminate the game");

        game.revertMove();

        assertEquals(GameResult.ONGOING, game.getResult(),
                "Result must transition back to ONGOING after revert");
    }
}
