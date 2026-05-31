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
        return Fen.importFEN(fen, false);
    }

    static Board importChess960FEN(String fen) {
        return Fen.importFEN(fen, true);
    }

    private static Board importFEN(String fen, boolean is960) {
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
        CastlingState castling = parseCastlingState(fields[2], rawBoard);
        byte enPassantField = parseEnPassantField(fields[3]);
        int halfMoveClock = parseNonNegativeInt(fields[4], "half-move clock");
        int fullMoveNumber = parseNonNegativeInt(fields[5], "full-move number");
        if (fullMoveNumber < 1) {
            throw new IllegalArgumentException("Full-move number must be >= 1, got " + fullMoveNumber);
        }

        int plyCount = (fullMoveNumber - 1) * 2 + (turn == GameStatus.TURN_BLACK ? 1 : 0);

        // Build a draft status (hash=0) just to feed into Board.calculatePositionHash,
        // which only reads castling/turn/enPassant from the GameStatus, not the hash itself.
        var draftStatus = new GameStatus(plyCount, turn, 0, halfMoveClock, castling.bits(), enPassantField, 0L);
        long positionHash = Board.calculatePositionHash(rawBoard, draftStatus);
        var gameStatus = new GameStatus(plyCount, turn, 0, halfMoveClock, castling.bits(), enPassantField, positionHash);

        return new Board(rawBoard, gameStatus, castling.rookFiles(), is960);
    }

    /** Decoded castling field: the {@code GameStatus} bit mask plus the
     *  matching {@code Board.castlingRookFiles} array. */
    @SuppressWarnings("java:S6218")
    private record CastlingState(int bits, byte[] rookFiles) {}

    /** One castling-rights character resolved against the board. */
    private record SlotRook(CastlingSlot slot, int rookFile) {}

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

    /**
     * Parses the FEN castling-rights field for both notations supported by
     * the X-FEN / Shredder-FEN conventions:
     *
     * <ul>
     *   <li>{@code K}/{@code Q}/{@code k}/{@code q} — classical: identifies
     *       the rook by direction (outermost rook on the relevant side of
     *       the king), as used in standard chess and X-FEN.</li>
     *   <li>{@code A}-{@code H} / {@code a}-{@code h} — Shredder: identifies
     *       the rook by its file letter directly. This is what 960-aware
     *       GUIs like cutechess emit.</li>
     * </ul>
     *
     * <p>Returns both the {@link GameStatus} bit mask and the 2-entry
     * {@code Board.castlingRookFiles} array (queenside-rook file at
     * index 0, kingside-rook file at index 1, symmetric across both
     * colors). Unused sides stay at their defaults (a-/h-file).
     *
     * <p>The Chess960 starting-position symmetry — Black's back rank
     * mirrors White's — implies the kingside-rook file is the same for
     * both colors, and likewise the queenside-rook file. The parser
     * enforces this: if both an upper- and lower-case castling letter
     * resolve to the same side but to different files (e.g.
     * {@code "Hg"} declaring white's kingside rook on h but black's on
     * g), an {@code IllegalArgumentException} is thrown.
     *
     * @throws IllegalArgumentException for an unrecognized character, a
     *         missing king on the back rank, a missing matching rook, a
     *         letter that would collide with the king's own file, or an
     *         asymmetric kingside/queenside rook-file specification.
     */
    private static CastlingState parseCastlingState(String castlingField, byte[] rawBoard) {
        if ("-".equals(castlingField)) {
            return new CastlingState(0, Board.defaultCastlingRookFiles());
        }

        // -1 sentinel = "not yet set by any letter in the field". Filled
        // in with defaults at the end for sides not mentioned.
        byte[] rookFiles = { -1, -1 };
        int bits = 0;
        for (int i = 0; i < castlingField.length(); i++) {
            char ch = castlingField.charAt(i);
            SlotRook resolved = resolveCastlingChar(ch, rawBoard);
            bits |= resolved.slot().bitMask();

            int sideIdx = resolved.slot().getKingQueenSideIndex();
            byte newFile = (byte) resolved.rookFile();
            byte existing = rookFiles[sideIdx];
            if (existing != -1 && existing != newFile) {
                throw new IllegalArgumentException(
                        "FEN castling field '" + castlingField + "' specifies asymmetric "
                                + (sideIdx == 1 ? "kingside" : "queenside")
                                + " rook files across colors: file "
                                + (char) ('a' + existing) + " vs file " + (char) ('a' + newFile)
                                + ". Chess960 requires Black's back rank to mirror White's.");
            }
            rookFiles[sideIdx] = newFile;
        }

        if (rookFiles[0] == -1) {
            rookFiles[0] = 0;
        }
        if (rookFiles[1] == -1) {
            rookFiles[1] = 7;
        }

        return new CastlingState(bits, rookFiles);
    }

    private static SlotRook resolveCastlingChar(char ch, byte[] rawBoard) {
        char upper = Character.toUpperCase(ch);
        validateCastlingRightsChar(upper);

        boolean isWhite = Character.isUpperCase(ch);
        int backRow = isWhite ? 0 : 7;
        byte rookPiece = isWhite ? Board.whiteRook : Board.blackRook;
        byte kingPiece = isWhite ? Board.whiteKing : Board.blackKing;

        int kingFile = findKingFile(rawBoard, kingPiece, backRow);
        if (kingFile < 0) {
            throw new IllegalArgumentException("FEN castling-right '" + ch + "' but no king on back rank");
        }

        int rookFile = findRookFile(ch, rawBoard, upper, rookPiece, backRow, kingFile);
        boolean kingside = rookFile > kingFile;

        return new SlotRook(CastlingSlot.slotFor(isWhite, kingside), rookFile);
    }

    private static int findRookFile(char ch, byte[] rawBoard, char upper, byte rookPiece, int backRow, int kingFile) {
        int rookFile;
        if (upper == 'K' || upper == 'Q') {
            int direction = (upper == 'K') ? +1 : -1;
            rookFile = findCastlingRookFile(rawBoard, rookPiece, backRow, kingFile, direction);
            if (rookFile < 0) {
                throw new IllegalArgumentException("FEN castling-right '" + ch + "' but no matching rook on back rank");
            }
        } else {
            rookFile = upper - 'A';
            if (rookFile == kingFile) {
                throw new IllegalArgumentException(
                        "FEN castling-right '" + ch + "' targets the king's own file");
            }
            if (rawBoard[ChessUtil.getFieldFromColAndRow(rookFile, backRow)] != rookPiece) {
                throw new IllegalArgumentException(
                        "FEN castling-right '" + ch + "' but no rook on " + (char) ('a' + rookFile) + (backRow + 1));
            }
        }

        return rookFile;
    }

    private static void validateCastlingRightsChar(char ch) {
        if (ch != 'K' && ch != 'Q' && (ch < 'A' || ch > 'H')) {
            throw new IllegalArgumentException("Invalid castling-rights char '" + ch + "' in FEN");
        }
    }

    private static int findKingFile(byte[] rawBoard, byte kingPiece, int row) {
        for (int col = 0; col < 8; col++) {
            if (rawBoard[ChessUtil.getFieldFromColAndRow(col, row)] == kingPiece) {
                return col;
            }
        }

        return -1;
    }

    /**
     * Returns the file of the first rook found while scanning outward from
     * the king along the back rank in the given {@code direction}, or
     * {@code -1} if no such rook exists.
     *
     * <p>For every starting position (standard or 960) and every realistic
     * mid-game position this is equivalent to X-FEN's "outermost rook on
     * the kingside / queenside of the king" rule, because each side of the
     * king carries at most one rook. The only case where the two diverge
     * is a pawn-promotion artifact (an extra rook between king and the
     * original castling rook) — and that case is exactly the X-FEN
     * ambiguity that Shredder notation exists to resolve, so we lose
     * nothing by taking the simpler "first rook in this direction" route.
     */
    private static int findCastlingRookFile(byte[] rawBoard, byte rookPiece, int row, int kingFile, int direction) {
        for (int col = kingFile + direction; col >= 0 && col <= 7; col += direction) {
            if (rawBoard[ChessUtil.getFieldFromColAndRow(col, row)] == rookPiece) {
                return col;
            }
        }

        return -1;
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
        return exportFEN(board, false);
    }

    static String exportShredderFEN(Board board) {
        return exportFEN(board, true);
    }

    private static String exportFEN(Board board, boolean isShredder) {
        GameStatus gameStatus = board.getGameStatus();
        StringBuilder buf = new StringBuilder();

        writePosition(board, buf);
        buf.append(' ');
        buf.append(gameStatus.getTurn() == GameStatus.TURN_WHITE ? 'w' : 'b');
        buf.append(' ');
        if (isShredder) {
            writeCastlingStateShredder(gameStatus, board, buf);
        } else {
            writeCastlingStateStandard(gameStatus, buf);
        }
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
        writeCastlingStateStandard(gameStatus, buf);
        return buf.toString();
    }

    public static String castlingStateShredder(GameStatus gameStatus, Board board) {
        StringBuilder buf = new StringBuilder();
        writeCastlingStateShredder(gameStatus, board, buf);
        return buf.toString();
    }

    private static void writeCastlingStateStandard(GameStatus gameStatus, StringBuilder buf) {
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

    private static void writeCastlingStateShredder(GameStatus gameStatus, Board board, StringBuilder buf) {
        var orgLen = buf.length();

        if (gameStatus.isWhiteCastlingKingSidePossible()) {
            int file = board.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE);
            buf.append(getCastlingFileChar(file));
        }
        if (gameStatus.isWhiteCastlingQueenSidePossible()) {
            int file = board.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE);
            buf.append(getCastlingFileChar(file));
        }
        if (gameStatus.isBlackCastlingKingSidePossible()) {
            int file = board.getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE);
            buf.append(Character.toLowerCase(getCastlingFileChar(file)));
        }
        if (gameStatus.isBlackCastlingQueenSidePossible()) {
            int file = board.getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE);
            buf.append(Character.toLowerCase(getCastlingFileChar(file)));
        }
        if (buf.length() == orgLen) {
            buf.append('-');
        }
    }

    private static char getCastlingFileChar(int file) {
        return (char) ('A' + file);
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
