package org.michaelfl.mychess;

import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Symbolic, position-independent description of a move parsed from a notation
 * string (algebraic, long-algebraic or castling). Resolved to a concrete
 * packed move against the current position via
 * {@link Board#resolveMoveDescription(MoveDescription, MoveGenerator)}.
 *
 * <p>Optional attributes (capture / check / checkmate / en-passant / castling)
 * are stored in a single {@link Set} of {@link MoveFlag}. A flag's presence
 * means "the notation said so (or the board exporter set it because the move
 * actually has that property)"; absence means "not specified".
 *
 * <p>{@code flags} is defensively copied into an immutable set by the compact
 * constructor; callers cannot mutate the internal state via the auto-generated
 * accessor.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings({"java:S5843", "java:3776"})
public record MoveDescription(
        int turn,
        byte piece,
        int fromCol,
        int fromRow,
        int toCol,
        int toRow,
        byte pawnPromotionPiece,
        Set<MoveFlag> flags) {

    private static final Pattern MOVE_PATTERN = Pattern.compile("^([PNBRQK])?([a-h])?([1-8])?([-x])?([a-h])([1-8])(=?[NBRQ])?(\\+|#|\\+\\+)?( ?e\\.p\\.)?(!|!!|!\\?|\\?!|\\?|\\?\\?)?$");
    private static final Pattern CASTLING_PATTERN = Pattern.compile("^(0-0|O-O|0-0-0|O-O-O)(\\+|#|\\+\\+)?(!|!!|!\\?|\\?!|\\?|\\?\\?)?$");
    private static final int GROUP_PIECE = 1;
    private static final int GROUP_SRC_COL = 2;
    private static final int GROUP_SRC_ROW = 3;
    private static final int GROUP_SEPARATOR = 4;
    private static final int GROUP_TARGET_COL = 5;
    private static final int GROUP_TARGET_ROW = 6;
    private static final int GROUP_PROMOTION = 7;
    private static final int GROUP_CHESS = 8;
    private static final int GROUP_EN_PASSANT = 9;
    private static final int GROUP_CASTLING = 1;
    private static final int GROUP_CASTLING_CHESS = 2;

    public MoveDescription {
        if (turn <= 0) {
            throw new IllegalArgumentException("turn not set");
        }
        if (toCol < 0) {
            throw new IllegalArgumentException("toCol not set");
        }
        if (toRow < 0) {
            throw new IllegalArgumentException("toRow not set");
        }
        if (piece <= 0 && (fromCol < 0 || fromRow < 0)) {
            throw new IllegalArgumentException("Source field must be set if no piece is defined");
        }

        // Defensive immutable copy. Set.copyOf is a no-op when flags is already
        // an immutable set (e.g. Set.of() from the alternative constructor).
        flags = Set.copyOf(flags);
    }

    /**
     * Convenience constructor for callers that only know the basic
     * from/to coordinates and an optional promotion symbol — used by
     * {@link SimpleNotationImporter} for plain long-algebraic input.
     */
    MoveDescription(int turn, int fromCol, int fromRow, int toCol, int toRow, char pawnPromotionSymbol) {
        this(turn, (byte) 0, fromCol, fromRow, toCol, toRow,
                pawnPromotionSymbol == 0 ? (byte) 0 : ChessUtil.symbolToPiece(pawnPromotionSymbol, turn),
                Set.of());
    }

    public int getFromField() {
        if (fromCol < 0 || fromRow < 0) {
            throw new IllegalStateException("from field not defined");
        }

        return ChessUtil.colAndRowToField(fromCol, fromRow);
    }

    public int getToField() {
        return ChessUtil.colAndRowToField(toCol, toRow);
    }

    public boolean has(MoveFlag flag) {
        return flags.contains(flag);
    }

    public boolean isCapture() {
        return flags.contains(MoveFlag.CAPTURE);
    }

    public boolean isCheck() {
        return flags.contains(MoveFlag.CHECK);
    }

    public boolean isCheckmate() {
        return flags.contains(MoveFlag.CHECKMATE);
    }

    public boolean isEnPassant() {
        return flags.contains(MoveFlag.EN_PASSANT);
    }

    public boolean isCastlingKingSide() {
        return flags.contains(MoveFlag.CASTLING_KING_SIDE);
    }

    public boolean isCastlingQueenSide() {
        return flags.contains(MoveFlag.CASTLING_QUEEN_SIDE);
    }

    @SuppressWarnings({"java:S6541", "java:S3776"})
    public static MoveDescription fromString(String moveString, int turn) {
        if (moveString.isEmpty()) {
            throw new IllegalArgumentException("Empty move notation");
        }

        boolean isWhiteTurn = turn == GameStatus.TURN_WHITE;
        Builder builder = new Builder(turn);

        if (moveString.charAt(0) == 'O' || moveString.charAt(0) == '0') {
            var matcher = CASTLING_PATTERN.matcher(moveString);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Wrong move notation: " + moveString);
            }

            var castling = matcher.group(GROUP_CASTLING);
            if ("0-0".equals(castling) || "O-O".equals(castling)) {
                builder.flags.add(MoveFlag.CASTLING_KING_SIDE);
                if (isWhiteTurn) {
                    builder.piece = Board.whiteKing;
                    builder.fromCol = 4;
                    builder.fromRow = 0;
                    builder.toCol = 6;
                    builder.toRow = 0;
                } else {
                    builder.piece = Board.blackKing;
                    builder.fromCol = 4;
                    builder.fromRow = 7;
                    builder.toCol = 6;
                    builder.toRow = 7;
                }
            } else { // O-O-O
                builder.flags.add(MoveFlag.CASTLING_QUEEN_SIDE);
                if (isWhiteTurn) {
                    builder.piece = Board.whiteKing;
                    builder.fromCol = 4;
                    builder.fromRow = 0;
                    builder.toCol = 2;
                    builder.toRow = 0;
                } else {
                    builder.piece = Board.blackKing;
                    builder.fromCol = 4;
                    builder.fromRow = 7;
                    builder.toCol = 2;
                    builder.toRow = 7;
                }
            }

            // Chess/checkmate symbol
            parseChessOrCheckmateSymbol(GROUP_CASTLING_CHESS, matcher, builder);

        } else {
            var matcher = MOVE_PATTERN.matcher(moveString);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Wrong move notation: " + moveString);
            }

            // Symbol
            var symbol = matcher.group(GROUP_PIECE);
            if (symbol != null) {
                builder.piece = ChessUtil.symbolToPiece(symbol.charAt(0), turn);
            } else {
                builder.piece = isWhiteTurn ? Board.whitePawn : Board.blackPawn;
            }

            // Source field
            var srcCol = matcher.group(GROUP_SRC_COL);
            if (srcCol != null) {
                builder.fromCol = srcCol.charAt(0) - 'a';
            }
            var srcRow = matcher.group(GROUP_SRC_ROW);
            if (srcRow != null) {
                builder.fromRow = Integer.parseInt(srcRow) - 1;
            }

            // Separator
            var sep = matcher.group(GROUP_SEPARATOR);
            if (sep != null && sep.charAt(0) == 'x') {
                builder.flags.add(MoveFlag.CAPTURE);
            }

            // Target field
            var targetCol = matcher.group(GROUP_TARGET_COL);
            if (targetCol != null) {
                builder.toCol = targetCol.charAt(0) - 'a';
            }
            var targetRow = matcher.group(GROUP_TARGET_ROW);
            if (targetRow != null) {
                builder.toRow = Integer.parseInt(targetRow) - 1;
            }

            // Pawn promotion symbol
            symbol = matcher.group(GROUP_PROMOTION);
            if (symbol != null) {
                if (symbol.startsWith("=")) {
                    symbol = symbol.substring(1);
                }
                builder.pawnPromotionPiece = ChessUtil.symbolToPiece(symbol.charAt(0), turn);
            }

            // Chess/checkmate symbol
            parseChessOrCheckmateSymbol(GROUP_CHESS, matcher, builder);

            // En passant
            var enPassant = matcher.group(GROUP_EN_PASSANT);
            if (enPassant != null) {
                builder.flags.add(MoveFlag.EN_PASSANT);
            }
        }

        return builder.build();
    }

    private static void parseChessOrCheckmateSymbol(int group, Matcher matcher, Builder builder) {
        var symbol = matcher.group(group);
        if (symbol != null) {
            if ("+".equals(symbol) || "++".equals(symbol)) {
                builder.flags.add(MoveFlag.CHECK);
            } else if ("#".equals(symbol)) {
                builder.flags.add(MoveFlag.CHECKMATE);
            }
        }
    }

    @Override
    @SuppressWarnings("java:S3358")
    public @NonNull String toString() {
        if (isCastlingKingSide()) {
            return "0-0";
        }

        if (isCastlingQueenSide()) {
            return "0-0-0";
        }

        return (piece != Board.whitePawn && piece != Board.blackPawn ? ChessUtil.pieceToString(piece) : "")
                + (fromCol >= 0 ? (char) ('a' + fromCol) : "")
                + (fromRow >= 0 ? fromRow + 1 : "")
                + (isCapture() ? "x" : "")
                + ChessUtil.fieldToString(getToField())
                + (pawnPromotionPiece > 0 ? ChessUtil.pieceToString(pawnPromotionPiece) : "")
                + (isCheckmate() ? "#" : (isCheck() ? "+" : ""));
    }

    static final class Builder {
        final int turn;
        byte piece = -1;
        int fromCol = -1;
        int fromRow = -1;
        int toCol = -1;
        int toRow = -1;
        byte pawnPromotionPiece = -1;
        final EnumSet<MoveFlag> flags = EnumSet.noneOf(MoveFlag.class);

        Builder(int turn) {
            this.turn = turn;
        }

        Builder(MoveDescription moveDescr) {
            this.turn = moveDescr.turn;
            this.piece = moveDescr.piece;
            this.fromCol = moveDescr.fromCol;
            this.fromRow = moveDescr.fromRow;
            this.toCol = moveDescr.toCol;
            this.toRow = moveDescr.toRow;
            this.pawnPromotionPiece = moveDescr.pawnPromotionPiece;
            this.flags.addAll(moveDescr.flags);
        }

        /** Add {@code flag} if {@code present} is true; otherwise remove it. */
        void setFlag(MoveFlag flag, boolean present) {
            if (present) {
                flags.add(flag);
            } else {
                flags.remove(flag);
            }
        }

        boolean hasFlag(MoveFlag flag) {
            return flags.contains(flag);
        }

        MoveDescription build() {
            return new MoveDescription(turn, piece, fromCol, fromRow, toCol, toRow, pawnPromotionPiece, flags);
        }
    }
}
