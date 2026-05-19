package org.michaelfl.mychess;

/**
 * Forsyth–Edwards Notation (FEN) import and export for {@link Board}.
 *
 * @author Michael Fleischhauer
 */
final class Fen {

    private Fen() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Parse a FEN string into a fresh {@link Board}. Inverse to
     * {@link #exportFEN(Board)}. Accepts the six standard FEN fields
     * (position, side, castling rights, en-passant target, half-move clock,
     * full-move number) separated by whitespace.
     *
     * @throws IllegalArgumentException if the input is not a syntactically
     *         valid FEN string. No semantic legality check (e.g. side-to-move
     *         could not have legally reached this position) is performed.
     */
    static Board importFEN(String fen) {
        if (fen == null) {
            throw new IllegalArgumentException("FEN must not be null");
        }
        var fields = fen.trim().split("\\s+");
        if (fields.length != 6) {
            throw new IllegalArgumentException("FEN must have 6 fields, got " + fields.length + ": " + fen);
        }

        byte[] rawBoard = Board.createEmptyRawBoard();
        readPosition(fields[0], rawBoard);

        int turn = parseTurn(fields[1]);
        int castlingState = parseCastlingState(fields[2]);
        byte enPassantField = parseEnPassantField(fields[3]);
        int halfMoveClock = parseNonNegativeInt(fields[4], "half-move clock");
        int fullMoveNumber = parseNonNegativeInt(fields[5], "full-move number");
        if (fullMoveNumber < 1) {
            throw new IllegalArgumentException("Full-move number must be >= 1, got " + fullMoveNumber);
        }

        int plyCount = (fullMoveNumber - 1) * 2 + (turn == GameStatus.TURN_BLACK ? 1 : 0);

        // Build a draft status (hash=0) just to feed into Board.calculatePositionHash,
        // which only reads castling/turn/enPassant from the GameStatus, not the hash itself.
        var draftStatus = new GameStatus(plyCount, turn, 0, halfMoveClock, castlingState, enPassantField, 0L);
        long positionHash = Board.calculatePositionHash(rawBoard, draftStatus);
        var gameStatus = new GameStatus(plyCount, turn, 0, halfMoveClock, castlingState, enPassantField, positionHash);

        return new Board(rawBoard, gameStatus);
    }

    private static void readPosition(String positionField, byte[] rawBoard) {
        var rows = positionField.split("/");
        if (rows.length != 8) {
            throw new IllegalArgumentException("FEN position must have 8 rows, got " + rows.length);
        }

        for (int rowIdx = 0; rowIdx < 8; rowIdx++) {
            readRow(rows[rowIdx], rowIdx, rawBoard);
        }
    }

    private static void readRow(String rowStr, int rowIdx, byte[] rawBoard) {
        int row = 7 - rowIdx;
        int col = 0;

        for (int i = 0; i < rowStr.length(); i++) {
            char ch = rowStr.charAt(i);
            if (ch >= '1' && ch <= '8') {
                col += ch - '0';
                continue;
            }
            if (col >= 8) {
                throw new IllegalArgumentException("FEN row " + (rowIdx + 1) + " is too long: " + rowStr);
            }
            rawBoard[ChessUtil.getFieldFromColAndRow(col, row)] = parsePieceSymbol(ch, rowIdx);
            col++;
        }

        if (col != 8) {
            throw new IllegalArgumentException("FEN row " + (rowIdx + 1) + " describes " + col + " columns, expected 8: " + rowStr);
        }
    }

    private static byte parsePieceSymbol(char ch, int rowIdx) {
        int turn = Character.isUpperCase(ch) ? GameStatus.TURN_WHITE : GameStatus.TURN_BLACK;
        try {
            return ChessUtil.symbolToPiece(Character.toUpperCase(ch), turn);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid piece symbol '" + ch + "' in FEN row " + (rowIdx + 1));
        }
    }

    private static int parseTurn(String turnField) {
        return switch (turnField) {
            case "w" -> GameStatus.TURN_WHITE;
            case "b" -> GameStatus.TURN_BLACK;
            default -> throw new IllegalArgumentException("FEN side-to-move must be 'w' or 'b', got '" + turnField + "'");
        };
    }

    private static int parseCastlingState(String castlingField) {
        if ("-".equals(castlingField)) {
            return 0;
        }
        int state = 0;

        for (int i = 0; i < castlingField.length(); i++) {
            switch (castlingField.charAt(i)) {
                case 'K' -> state |= GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE;
                case 'Q' -> state |= GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE;
                case 'k' -> state |= GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE;
                case 'q' -> state |= GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE;
                default -> throw new IllegalArgumentException("Invalid castling-rights char '" + castlingField.charAt(i) + "' in FEN");
            }
        }

        return state;
    }

    private static byte parseEnPassantField(String enPassantField) {
        if ("-".equals(enPassantField)) {
            return 0;
        }
        if (enPassantField.length() != 2) {
            throw new IllegalArgumentException("FEN en-passant field must be '-' or 'a3'-style, got '" + enPassantField + "'");
        }
        int[] colAndRow;
        try {
            colAndRow = ChessUtil.getColAndRowFromString(enPassantField);
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Invalid FEN en-passant field '" + enPassantField + "'");
        }
        return (byte) ChessUtil.getFieldFromColAndRow(colAndRow[0], colAndRow[1]);
    }

    private static int parseNonNegativeInt(String s, String name) {
        try {
            int value = Integer.parseInt(s);
            if (value < 0) {
                throw new IllegalArgumentException("FEN " + name + " must be >= 0, got " + value);
            }
            return value;
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("FEN " + name + " is not a valid integer: '" + s + "'");
        }
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
        var enPassantField = board.getGameStatus().getEnPassantField();

        if (enPassantField == 0) {
            buf.append('-');
        } else {
            buf.append(ChessUtil.fieldToString(enPassantField));
        }
    }
}
