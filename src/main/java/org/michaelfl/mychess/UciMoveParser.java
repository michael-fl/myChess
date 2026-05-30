package org.michaelfl.mychess;

/**
 * Conversion between UCI long-algebraic move notation and the project's
 * {@link MoveDescription} / packed-int {@link Move} representations.
 *
 * <p>UCI notation is a fixed 4- or 5-character string: from-square ({@code "e2"}),
 * to-square ({@code "e4"}), and an optional promotion piece letter
 * ({@code "q"}/{@code "r"}/{@code "b"}/{@code "n"}). Castling is encoded as a
 * king move spanning two files ({@code "e1g1"}, {@code "e8c8"}). En-passant
 * captures look like ordinary diagonal pawn moves and are resolved against the
 * board state during {@link Board#resolveMoveDescription} downstream.
 *
 * @author Michael Fleischhauer
 */
final class UciMoveParser {

    private UciMoveParser() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Parse a UCI move string against the given board (used to disambiguate
     * castling from a normal king move). Returns a {@link MoveDescription} that
     * {@link Game#makeMove(MoveDescription)} can consume.
     */
    static MoveDescription parse(String uci, Board board) {
        if (uci == null || uci.length() < 4 || uci.length() > 5) {
            throw new IllegalArgumentException("UCI move must be 4 or 5 characters: '" + uci + "'");
        }

        var fromStr = uci.substring(0, 2);
        var toStr = uci.substring(2, 4);
        Character promotion = uci.length() == 5 ? uci.charAt(4) : null;

        int fromField = fieldFromString(fromStr);
        byte piece = board.get(fromField);

        int turn = isWhitePiece(piece) ? GameStatus.TURN_WHITE : GameStatus.TURN_BLACK;

        if (isKing(piece) && isCastlingMove(fromStr, toStr, board, turn)) {
            boolean kingSide = toStr.charAt(0) > fromStr.charAt(0);
            return MoveDescription.fromString(kingSide ? "O-O" : "O-O-O", turn);
        }

        // Otherwise treat as a long-algebraic move: insert dash, append promotion if any.
        var builder = new StringBuilder(fromStr).append('-').append(toStr);
        if (promotion != null) {
            builder.append('=').append(Character.toUpperCase(promotion));
        }

        return MoveDescription.fromString(builder.toString(), turn);
    }

    /** Convert a packed-int move to UCI notation. */
    static String toUci(int packedMove, Board board) {
        byte fromField = Move.getFromField(packedMove);
        byte toField = Move.getToField(packedMove);
        byte moveType = Move.getMoveType(packedMove);

        var sb = new StringBuilder();
        sb.append(ChessUtil.fieldToString(fromField));

        // In chess960 castling in UCI notation must be written as kingField-rookField
        if (board.isChess960() && moveType == Move.typeCastlingKingSide) {
            int rookFile = board.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE);
            int row = ChessUtil.getRowOfField(fromField);
            sb.append(String.valueOf((char) ('a' + rookFile)) + (row + 1));
        } else if (board.isChess960() && moveType == Move.typeCastlingQueenSide) {
            int rookFile = board.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE);
            int row = ChessUtil.getRowOfField(fromField);
            sb.append(String.valueOf((char) ('a' + rookFile)) + (row + 1));
        } else {
            sb.append(ChessUtil.fieldToString(toField));
        }

        switch (moveType) {
            case Move.typePawnPromotionQueen -> sb.append('q');
            case Move.typePawnPromotionRook -> sb.append('r');
            case Move.typePawnPromotionBishop -> sb.append('b');
            case Move.typePawnPromotionKnight -> sb.append('n');
            default -> { /* nothing to append */ }
        }

        return sb.toString();
    }

    private static boolean isKing(byte piece) {
        return piece == Board.whiteKing || piece == Board.blackKing;
    }

    private static boolean isWhitePiece(byte piece) {
        return piece >= Board.whitePawn && piece <= Board.whiteKing;
    }

    /**
     * Detect whether a king-from/to pair encodes a castle.
     *
     * <p>Accepts two forms:
     * <ul>
     *   <li><b>Standard chess</b> — king moves exactly two files on the
     *       same row ({@code e1g1}, {@code e1c1}, …).</li>
     *   <li><b>Chess960 king-to-rook</b> — king moves to an own rook on
     *       the same row ({@code c8a8}, {@code g8h8}, {@code b1h1}, …).
     *       Any column distance from 1 to 7 is valid; the rook may be
     *       on either side of the king. Selected by 960-aware GUIs
     *       when {@code UCI_Chess960} is set.</li>
     * </ul>
     *
     * <p>The board context is needed for the 960 form so the parser can
     * tell a king-captures-own-rook castle apart from an illegal king
     * step onto an empty / enemy / non-rook square.
     */
    private static boolean isCastlingMove(String fromStr, String toStr, Board board, int turn) {
        if (fromStr.charAt(1) != toStr.charAt(1)) {
            return false;
        }

        int colDiff = Math.abs(toStr.charAt(0) - fromStr.charAt(0));
        if (colDiff == 2) {
            return true;
        }

        if (colDiff < 1) {
            return false;
        }

        byte targetPiece = board.get(fieldFromString(toStr));
        return isOwnRook(targetPiece, turn);
    }

    private static boolean isOwnRook(byte piece, int turn) {
        return turn == GameStatus.TURN_WHITE ? piece == Board.whiteRook : piece == Board.blackRook;
    }

    private static int fieldFromString(String s) {
        int[] colRow = ChessUtil.getColAndRowFromString(s);
        return ChessUtil.getFieldFromColAndRow(colRow[0], colRow[1]);
    }
}
