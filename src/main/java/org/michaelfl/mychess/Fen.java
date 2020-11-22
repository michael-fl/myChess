package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
final class Fen {

    private Fen() {
        throw new IllegalStateException("Utility class");
    }

    static String exportFEN(Board board) {
        GameStatus gameStatus = board.getGameStatus();
        StringBuilder buf = new StringBuilder();

        writePosition(board, buf);
        buf.append(' ');
        buf.append(gameStatus.getTurn() == GameStatus.TURN_WHITE ? 'w' : 'b');
        buf.append(' ');
        writeCastlingState(gameStatus, buf);
        buf.append(' ');
        writeEnPassant(board, buf);
        buf.append(' ');
        buf.append(gameStatus.getHalfMoveClock());
        buf.append(' ');
        buf.append((gameStatus.getPlyCount() / 2) + 1);

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

    public static String castlingState(GameStatus gameStatus) {
        StringBuilder buf = new StringBuilder();
        writeCastlingState(gameStatus, buf);
        return buf.toString();
    }

    private static void writeCastlingState(GameStatus gameStatus, StringBuilder buf) {
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

    private static void writeEnPassant(Board board, StringBuilder buf) {
        var gameStatus = board.getGameStatus();
        var move = gameStatus.getLastMove();
        if (move != 0) {
            var fromField = Move.getFromField(move);
            var toField = Move.getToField(move);
            var piece = board.get(Move.getToField(move));
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
}
