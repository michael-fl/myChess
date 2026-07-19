package org.michaelfl.mychess;

import java.util.ArrayList;
import java.util.List;

import static org.michaelfl.mychess.Assert.__assert;

public final class StaticExchangeEvaluation {

    private static final int COLOR_WHITE = 0;
    private static final int COLOR_BLACK = 1;

    static final byte NO_FIELD = -1;

    private static final int[] SEE_PIECE_WEIGHT = new int[Board.blackKing + 1];
    static {
        SEE_PIECE_WEIGHT[Board.whitePawn] = WeightingFunction.weightOfPiece[Board.whitePawn];
        SEE_PIECE_WEIGHT[Board.whiteKnight] = WeightingFunction.weightOfPiece[Board.whiteKnight];
        SEE_PIECE_WEIGHT[Board.whiteBishop] = WeightingFunction.weightOfPiece[Board.whiteBishop];
        SEE_PIECE_WEIGHT[Board.whiteRook] = WeightingFunction.weightOfPiece[Board.whiteRook];
        SEE_PIECE_WEIGHT[Board.whiteQueen] = WeightingFunction.weightOfPiece[Board.whiteQueen];
        SEE_PIECE_WEIGHT[Board.whiteKing] = 10000; // Ensures that king moves are played last (normally king's weight is 0)
        SEE_PIECE_WEIGHT[Board.blackPawn] = WeightingFunction.weightOfPiece[Board.blackPawn];
        SEE_PIECE_WEIGHT[Board.blackKnight] = WeightingFunction.weightOfPiece[Board.blackKnight];
        SEE_PIECE_WEIGHT[Board.blackBishop] = WeightingFunction.weightOfPiece[Board.blackBishop];
        SEE_PIECE_WEIGHT[Board.blackRook] = WeightingFunction.weightOfPiece[Board.blackRook];
        SEE_PIECE_WEIGHT[Board.blackQueen] = WeightingFunction.weightOfPiece[Board.blackQueen];
        SEE_PIECE_WEIGHT[Board.blackKing] = SEE_PIECE_WEIGHT[Board.whiteKing];
    }

    private static final byte[] KNIGHT_PIECE = new byte[] { Board.whiteKnight, Board.blackKnight };
    private static final byte[] BISHOP_PIECE = new byte[] { Board.whiteBishop, Board.blackBishop };
    private static final byte[] ROOK_PIECE = new byte[] { Board.whiteRook, Board.blackRook };
    private static final byte[] QUEEN_PIECE = new byte[] { Board.whiteQueen, Board.blackQueen };
    private static final byte[] KING_PIECE = new byte[] { Board.whiteKing, Board.blackKing };

    private static final int BOARD_SIZE = Board.LENGTH * Board.LENGTH;
    private static final byte[] DIRECTIONS = initDirections();
    private static final boolean[] ARE_ORTHOGONAL = initOrthogonality();

    @SuppressWarnings("SameParameterValue")
    private static byte[] initDirections() {
        final byte[] directions = new byte[BOARD_SIZE * BOARD_SIZE];

        for (int fromField = Board.a1; fromField <= Board.h8; fromField++) {
            for (int toField = Board.a1; toField <= Board.h8; toField++) {
                if (fromField != toField) {
                    directions[fromField * BOARD_SIZE + toField] = (byte) calcDirection(
                            ChessUtil.getRowOfField(fromField), ChessUtil.getColOfField(fromField),
                            ChessUtil.getRowOfField(toField), ChessUtil.getColOfField(toField)
                    );
                }
            }

        }

        return directions;
    }

    private static int calcDirection(final int fromRow, final int fromCol, final int toRow, final int toCol) {
        // orthogonal
        if (fromRow == toRow) {
            return fromCol < toCol ? 1 : -1;
        }
        if (fromCol == toCol) {
            return fromRow < toRow ? Board.LENGTH : -Board.LENGTH;
        }

        // diagonal
        if (fromRow < toRow) {
            return fromCol < toCol ? Board.LENGTH + 1 : Board.LENGTH - 1;
        }
        return fromCol < toCol ? -Board.LENGTH + 1 : -Board.LENGTH - 1;
    }

    @SuppressWarnings("SameParameterValue")
    private static boolean[] initOrthogonality() {
        final boolean[] result = new boolean[BOARD_SIZE * BOARD_SIZE];

        for (int fromField = Board.a1; fromField <= Board.h8; fromField++) {
            for (int toField = Board.a1; toField <= Board.h8; toField++) {
                if (fromField != toField) {
                    result[fromField * BOARD_SIZE + toField] = calcOrthogonality(
                            ChessUtil.getRowOfField(fromField), ChessUtil.getColOfField(fromField),
                            ChessUtil.getRowOfField(toField), ChessUtil.getColOfField(toField)
                    );
                }
            }
        }

        return result;
    }

    private static boolean calcOrthogonality(final int fromRow, final int fromCol, final int toRow, final int toCol) {
        return fromCol == toCol || fromRow == toRow;
    }

    final class SEEMovesContainer {
        private final byte[] fromFields = new byte[16];
        private final int[] weights = new int[16];
        private int size;
        private int firstIndex;

        public int getSize() {
            return size;
        }

        public void clear() {
            size = 0;
            firstIndex = 0;
        }

        // for tests
        public byte[] getFromFields() {
            return fromFields;
        }

        public void addField(final byte field) {
            final int weight = SEE_PIECE_WEIGHT[board[field]];

            // Fields are sorted by piece weight ascending
            for (int i = firstIndex; i < size; i++) {
                if (weight < weights[i]) {
                    System.arraycopy(fromFields, i, fromFields, i + 1, size - i);
                    System.arraycopy(weights, i, weights, i + 1, size - i);
                    fromFields[i] = field;
                    weights[i] = weight;
                    size++;
                    return;
                }
            }

            weights[size] = weight;
            fromFields[size++] = field;
        }

        public boolean hasPiecesLeft() {
            return firstIndex < size;
        }

        public byte take() {
            return fromFields[firstIndex++];
        }

        public void moveFieldToFront(byte field) {
            for (int i = 0; i < size; i++) {
                if (field == fromFields[i]) {
                    if (i > 0) {
                        System.arraycopy(fromFields, 0, fromFields, 1, i);
                        fromFields[0] = field;
                        weights[0] = weights[i];
                    }
                    return;
                }
            }

            throw new IllegalStateException("Field not contained in container: " + ChessUtil.fieldToString(field));
        }

        @Override
        public String toString() {
            List<String> list = new ArrayList<>(fromFields.length);

            for (int i = 0; i < size; i++) {
                list.add(ChessUtil.fieldToString(fromFields[i]));
            }

            return list.toString();
        }
    }

    private final SEEMovesContainer[] movesContainers = new SEEMovesContainer[] {new SEEMovesContainer(), new SEEMovesContainer()};
    private byte[] board;

    public StaticExchangeEvaluation() {
    }

    public StaticExchangeEvaluation(Board board) {
        this.board = board.getRawBoard();
    }

    public void init(Board board) {
        this.board = board.getRawBoard();
    }

    // for tests
    SEEMovesContainer getSEEMovesContainer(int color) {
        return movesContainers[color];
    }

    // for tests
    byte[] getRawBoard() {
        return board;
    }

    public int see(int move) {
        final byte toField = Move.getToField(move);
        final byte fromField = Move.getFromField(move);
        final byte capturedPiece = Move.getCapturedPiece(move);
        final byte movedPiece = board[fromField];
        // TODO remove
        __assert(() -> capturedPiece != 0, () -> String.format("SEE move must be a capture: %s", ChessUtil.moveToString(move)));
        if (Move.getMoveType(move) != Move.typeEnPassant) {
            __assert(() -> capturedPiece == board[toField], () -> String.format("Captured piece mismatch in SEE move: %s", ChessUtil.moveToString(move)));
        }
        __assert(() -> movedPiece != Board.empty && movedPiece != Board.illegal, () -> String.format("No moved piece in SEE move: %s", ChessUtil.moveToString(move)));

        final int color = ChessUtil.isWhitePiece(movedPiece) ? COLOR_WHITE : COLOR_BLACK;

        movesContainers[0].clear();
        movesContainers[1].clear();

        collectCaptureMovesInto(movesContainers[0], toField, 0);
        collectCaptureMovesInto(movesContainers[1], toField, 1);

        // The given move must be played first, regardless of piece weight.
        // All other captures are played in ascending order according to piece weight.
        movesContainers[color].moveFieldToFront(fromField);

        return calcSEE(toField, color, capturedPiece);
    }

    int calcSEE(final byte toField, final int color, final byte pieceToCapture) {
        return calcSEE(0, toField, color, pieceToCapture);
    }

    private int calcSEE(final int depth, final byte toField, final int color, final byte pieceToCapture) {
        final var container = movesContainers[color];

        if (container.hasPiecesLeft()) {
            final byte fromField = container.take();
            final byte piece = board[fromField];

            if (!ChessUtil.isKnight(piece)) {
                final byte revealedField = findRevealedAttackerOrDefenderField(fromField, toField);
                if (revealedField != NO_FIELD) {
                    final int colIndex = ChessUtil.isWhitePiece(board[revealedField]) ? COLOR_WHITE : COLOR_BLACK;
                    movesContainers[colIndex].addField(revealedField);
                }
            }

            int weight = SEE_PIECE_WEIGHT[pieceToCapture] - calcSEE(depth +1 , toField, swapColor(color), piece);
            return depth > 0 ? Math.max(0, weight) : weight;
        } else {
            return 0;
        }
    }

    byte findRevealedAttackerOrDefenderField(byte fromField, byte toField) {
        final boolean isOrthogonal = areOrthogonal(fromField, toField);
        final int dir = -getDirection(fromField, toField);

        final int to = findFirstNonEmptyField(fromField, dir);
        final byte revealedPiece = board[to];
        if (revealedPiece != Board.illegal) {
            if (ChessUtil.isQueen(revealedPiece)
                    || (isOrthogonal && ChessUtil.isRook(revealedPiece))
                    || (!isOrthogonal && ChessUtil.isBishop(revealedPiece))) {
                return (byte) to;
            }
        }

        return NO_FIELD;
    }

    static int getDirection(final byte fromField, final byte toField) {
        return DIRECTIONS[fromField * BOARD_SIZE + toField];
    }

    static boolean areOrthogonal(final byte fromField, final byte toField) {
        return ARE_ORTHOGONAL[fromField * BOARD_SIZE + toField];
    }

    private static int swapColor(int color) {
        return color ^ 1;
    }

    @SuppressWarnings("DuplicatedCode")
    void collectCaptureMovesInto(final StaticExchangeEvaluation.SEEMovesContainer container, final byte field, final int color) {
        final byte attackerKing = KING_PIECE[color];
        final byte attackerKnight = KNIGHT_PIECE[color];
        final byte attackerBishop = BISHOP_PIECE[color];
        final byte attackerRook = ROOK_PIECE[color];
        final byte attackerQueen = QUEEN_PIECE[color];

        if (color == COLOR_WHITE) {
            if (board[field - Board.LENGTH - 1] == Board.whitePawn) {
                container.addField((byte) (field - Board.LENGTH - 1));
            }
            if (board[field - Board.LENGTH + 1] == Board.whitePawn) {
                container.addField((byte) (field - Board.LENGTH + 1));
            }
        } else {
            if (board[field + Board.LENGTH - 1] == Board.blackPawn) {
                container.addField((byte) (field + Board.LENGTH - 1));
            }
            if (board[field + Board.LENGTH + 1] == Board.blackPawn) {
                container.addField((byte) (field + Board.LENGTH + 1));
            }
        }

        for (int off : Board.KNIGHT_OFFSETS) {
            if (board[field + off] == attackerKnight) {
                container.addField((byte) (field + off));
            }
        }

        for (int off : Board.KING_ADJACENCY_OFFSETS) {
            if (board[field + off] == attackerKing) {
                container.addField((byte) (field + off));
                break;
            }
        }

        for (int dir : Board.DIAGONAL_RAY_DIRS) {
            final int to = findFirstNonEmptyField(field, dir);
            final byte piece = board[to];
            if (piece == attackerBishop || piece == attackerQueen) {
                container.addField((byte) to);
            }
        }

        for (int dir : Board.ORTHOGONAL_RAY_DIRS) {
            final int to = findFirstNonEmptyField(field, dir);
            final byte piece = board[to];
            if (piece == attackerRook || piece == attackerQueen) {
                container.addField((byte) to);
            }
        }
    }

    private int findFirstNonEmptyField(byte fromField, int dir) {
        int to = fromField + dir;
        while (board[to] == Board.empty) {
            to += dir;
        }

        return to;
    }
}
