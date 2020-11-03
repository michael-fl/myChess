package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
final class Fen {

    private Fen() {
        throw new IllegalStateException("Utility class");
    }

    static String exportFEN(Game game) {
        Board board = game.getBoard();

        StringBuilder buf = new StringBuilder();

        writePosition(board, buf);
        buf.append(' ');
        buf.append(game.getTurn() == GameStatus.TURN_WHITE ? 'w' : 'b');
        buf.append(' ');
        writeCastlingState(game, buf);
        buf.append(' ');
        writeEnPassant(game, buf);
        buf.append(' ');
        write50PliesRule(game, buf);
        buf.append(' ');
        buf.append((game.getGameStatus().getPlyCount() / 2) + 1);

        return buf.toString();
    }

    private static void writePosition(Board board, StringBuilder buf) {
        for (int row = 7; row >= 0; row--) {
            int emptyCount = 0;

            for (int col = 0; col <= 7; col++) {
                byte piece = board.getPieceAt(col, row);
                if (piece == Board.empty) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        buf.append(emptyCount);
                        emptyCount = 0;
                    }
                    buf.append(Board.fenSymbols[piece]);
                }
            }
            if (emptyCount > 0) {
                buf.append(emptyCount);
            }
            if (row > 0) {
                buf.append('/');
            }
        }
    }

    private static void writeCastlingState(Game game, StringBuilder buf) {
        var gameStatus = game.getGameStatus();
        var orgLen = buf.length();
        if (gameStatus.isWhiteCastlingKingSidePossible()) {
            buf.append('K');
        }
        if (gameStatus.isWhiteCastlingQueenSidePossible()) {
            buf.append('Q');
        }
        if (gameStatus.isBlackCastlingKingSidePossible()) {
            buf.append('k');
        }
        if (gameStatus.isBlackCastlingQueenSidePossible()) {
            buf.append('q');
        }
        if (buf.length() == orgLen) {
            buf.append('-');
        }
    }

    private static void writeEnPassant(Game game, StringBuilder buf) {
        var move = game.getGameStatus().getLastMove();
        if (move != 0) {
            var fromField = Move.getFromField(move);
            var toField = Move.getToField(move);
            var piece = game.getBoard().get(Move.getToField(move));
            if (piece == Board.whitePawn && ChessUtil.getRowOfField(fromField) == 1 && ChessUtil.getRowOfField(toField) == 3) {
                buf.append(ChessUtil.fieldToString(fromField + Board.LENGTH));
                return;
            } else if (piece == Board.blackPawn && ChessUtil.getRowOfField(fromField) == 6 && ChessUtil.getRowOfField(toField) == 4) {
                buf.append(ChessUtil.fieldToString(fromField - Board.LENGTH));
                return;
            }
        }

        buf.append('-');
    }

    private static void write50PliesRule(Game game, StringBuilder buf) {
        buf.append(0); // TODO write50PliesRule
    }
}
