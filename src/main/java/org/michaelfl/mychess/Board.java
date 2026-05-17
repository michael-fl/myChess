package org.michaelfl.mychess;

import org.michaelfl.mychess.MoveDescription.Builder;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.michaelfl.mychess.ChessUtil.*;
import static org.michaelfl.mychess.RandomNumbers.RANDOM_NUMBERS;

@SuppressWarnings({"WeakerAccess", "unused", "PointlessArithmeticExpression", "DuplicatedCode"})
public final class Board {

    @FunctionalInterface
    private interface IMove {
        long move(Board board, int move);
    }

    @FunctionalInterface
    private interface IRevertMove {
        void revert(Board board, int move);
    }

    public final static byte illegal = 64;
    public final static byte empty = 0;
    public final static byte whitePawn = 8;
    public final static byte whiteKnight = 9;
    public final static byte whiteBishop = 10;
    public final static byte whiteRook = 11;
    public final static byte whiteQueen = 12;
    public final static byte whiteKing = 13;
    public final static byte blackPawn = 16;
    public final static byte blackKnight = 17;
    public final static byte blackBishop = 18;
    public final static byte blackRook = 19;
    public final static byte blackQueen = 20;
    public final static byte blackKing = 21;

    public final static int LENGTH = 12;

    public final static int a1 = 2 * LENGTH + 2 + 0;
    public final static int b1 = 2 * LENGTH + 2 + 1;
    public final static int c1 = 2 * LENGTH + 2 + 2;
    public final static int d1 = 2 * LENGTH + 2 + 3;
    public final static int e1 = 2 * LENGTH + 2 + 4;
    public final static int f1 = 2 * LENGTH + 2 + 5;
    public final static int g1 = 2 * LENGTH + 2 + 6;
    public final static int h1 = 2 * LENGTH + 2 + 7;
    public final static int a2 = 3 * LENGTH + 2 + 0;
    public final static int b2 = 3 * LENGTH + 2 + 1;
    public final static int c2 = 3 * LENGTH + 2 + 2;
    public final static int d2 = 3 * LENGTH + 2 + 3;
    public final static int e2 = 3 * LENGTH + 2 + 4;
    public final static int f2 = 3 * LENGTH + 2 + 5;
    public final static int g2 = 3 * LENGTH + 2 + 6;
    public final static int h2 = 3 * LENGTH + 2 + 7;
    public final static int a3 = 4 * LENGTH + 2 + 0;
    public final static int b3 = 4 * LENGTH + 2 + 1;
    public final static int c3 = 4 * LENGTH + 2 + 2;
    public final static int d3 = 4 * LENGTH + 2 + 3;
    public final static int e3 = 4 * LENGTH + 2 + 4;
    public final static int f3 = 4 * LENGTH + 2 + 5;
    public final static int g3 = 4 * LENGTH + 2 + 6;
    public final static int h3 = 4 * LENGTH + 2 + 7;
    public final static int a4 = 5 * LENGTH + 2 + 0;
    public final static int b4 = 5 * LENGTH + 2 + 1;
    public final static int c4 = 5 * LENGTH + 2 + 2;
    public final static int d4 = 5 * LENGTH + 2 + 3;
    public final static int e4 = 5 * LENGTH + 2 + 4;
    public final static int f4 = 5 * LENGTH + 2 + 5;
    public final static int g4 = 5 * LENGTH + 2 + 6;
    public final static int h4 = 5 * LENGTH + 2 + 7;
    public final static int a5 = 6 * LENGTH + 2 + 0;
    public final static int b5 = 6 * LENGTH + 2 + 1;
    public final static int c5 = 6 * LENGTH + 2 + 2;
    public final static int d5 = 6 * LENGTH + 2 + 3;
    public final static int e5 = 6 * LENGTH + 2 + 4;
    public final static int f5 = 6 * LENGTH + 2 + 5;
    public final static int g5 = 6 * LENGTH + 2 + 6;
    public final static int h5 = 6 * LENGTH + 2 + 7;
    public final static int a6 = 7 * LENGTH + 2 + 0;
    public final static int b6 = 7 * LENGTH + 2 + 1;
    public final static int c6 = 7 * LENGTH + 2 + 2;
    public final static int d6 = 7 * LENGTH + 2 + 3;
    public final static int e6 = 7 * LENGTH + 2 + 4;
    public final static int f6 = 7 * LENGTH + 2 + 5;
    public final static int g6 = 7 * LENGTH + 2 + 6;
    public final static int h6 = 7 * LENGTH + 2 + 7;
    public final static int a7 = 8 * LENGTH + 2 + 0;
    public final static int b7 = 8 * LENGTH + 2 + 1;
    public final static int c7 = 8 * LENGTH + 2 + 2;
    public final static int d7 = 8 * LENGTH + 2 + 3;
    public final static int e7 = 8 * LENGTH + 2 + 4;
    public final static int f7 = 8 * LENGTH + 2 + 5;
    public final static int g7 = 8 * LENGTH + 2 + 6;
    public final static int h7 = 8 * LENGTH + 2 + 7;
    public final static int a8 = 9 * LENGTH + 2 + 0;
    public final static int b8 = 9 * LENGTH + 2 + 1;
    public final static int c8 = 9 * LENGTH + 2 + 2;
    public final static int d8 = 9 * LENGTH + 2 + 3;
    public final static int e8 = 9 * LENGTH + 2 + 4;
    public final static int f8 = 9 * LENGTH + 2 + 5;
    public final static int g8 = 9 * LENGTH + 2 + 6;
    public final static int h8 = 9 * LENGTH + 2 + 7;

    private final static int TURN_INDEX = 12 * 64; // length = 1
    private final static int CASTLING_RIGHTS_INDEX = 12 * 64 + 1; // length = 16
    private final static int EN_PASSANT_INDEX = 12 * 64 + 17; // length = 8

    private final static char[] printSymbols = new char[22];
    static {
        Arrays.fill(printSymbols, '.');
        printSymbols[whitePawn] = '\u2659';
        printSymbols[whiteKnight] = '\u2658';
        printSymbols[whiteBishop] = '\u2657';
        printSymbols[whiteRook] = '\u2656';
        printSymbols[whiteQueen] = '\u2655';
        printSymbols[whiteKing] = '\u2654';
        printSymbols[blackPawn] = '\u265F';
        printSymbols[blackKnight] = '\u265E';
        printSymbols[blackBishop] = '\u265D';
        printSymbols[blackRook] = '\u265C';
        printSymbols[blackQueen] = '\u265B';
        printSymbols[blackKing] = '\u265A';
    }

    public final static char[] fenSymbols = new char[22];
    static {
        Arrays.fill(fenSymbols, '?');
        fenSymbols[whitePawn] = 'P';
        fenSymbols[whiteKnight] = 'N';
        fenSymbols[whiteBishop] = 'B';
        fenSymbols[whiteRook] = 'R';
        fenSymbols[whiteQueen] = 'Q';
        fenSymbols[whiteKing] = 'K';
        fenSymbols[blackPawn] = 'p';
        fenSymbols[blackKnight] = 'n';
        fenSymbols[blackBishop] = 'b';
        fenSymbols[blackRook] = 'r';
        fenSymbols[blackQueen] = 'q';
        fenSymbols[blackKing] = 'k';
    }

    private final static IMove[] MOVE_FUNCTIONS = new IMove[Move.typeEnPassant + 1];
    static {
        MOVE_FUNCTIONS[Move.typeNormal]              = Board::makeNormalMove;
        MOVE_FUNCTIONS[Move.typeCastlingKingSide]    = Board::makeCastlingKingSideMove;
        MOVE_FUNCTIONS[Move.typeCastlingQueenSide]   = Board::makeCastlingQueenSideMove;
        MOVE_FUNCTIONS[Move.typePawnPromotionQueen]  = Board::makePawnPromotionMoveQueen;
        MOVE_FUNCTIONS[Move.typePawnPromotionKnight] = Board::makePawnPromotionMoveKnight;
        MOVE_FUNCTIONS[Move.typePawnPromotionRook]   = Board::makePawnPromotionMoveRook;
        MOVE_FUNCTIONS[Move.typePawnPromotionBishop] = Board::makePawnPromotionMoveBishop;
        MOVE_FUNCTIONS[Move.typeEnPassant]           = Board::makeEnPassantMove;
    }

    private final static IRevertMove[] MOVE_REVERT_FUNCTIONS = new IRevertMove[Move.typeEnPassant + 1];
    static {
        MOVE_REVERT_FUNCTIONS[Move.typeNormal]              = Board::revertNormalMove;
        MOVE_REVERT_FUNCTIONS[Move.typeCastlingKingSide]    = Board::revertCastlingKingSideMove;
        MOVE_REVERT_FUNCTIONS[Move.typeCastlingQueenSide]   = Board::revertCastlingQueenSideMove;
        MOVE_REVERT_FUNCTIONS[Move.typePawnPromotionQueen]  = Board::revertPawnPromotionMove;
        MOVE_REVERT_FUNCTIONS[Move.typePawnPromotionKnight] = Board::revertPawnPromotionMove;
        MOVE_REVERT_FUNCTIONS[Move.typePawnPromotionRook]   = Board::revertPawnPromotionMove;
        MOVE_REVERT_FUNCTIONS[Move.typePawnPromotionBishop] = Board::revertPawnPromotionMove;
        MOVE_REVERT_FUNCTIONS[Move.typeEnPassant]           = Board::revertEnPassantMove;
    }

    private final byte[] board;
    private final GameStatus[] statusStack;
    private int stackSize;

    public Board(byte[] rawBoard, GameStatus gameStatus) {
        board = rawBoard;
        statusStack = new GameStatus[2000];
        push(gameStatus);
    }

    //    132           ...             143
    //    120           ...             131
    //    84    110(a8) ... 117(h8) 118 119
    //           98(a7) ... 105(h7)
    //           86(a6) ...  93(h6)
    //           74(a5) ...  81(h5)
    //           62(a4) ...  69(h4)
    //           50(a3) ...  57(h3)
    //           38(a2) ...  45(h2)  46  47
    //    24 25  26(a1) ...  33(h1)  34  35
    //    12 13         ...          22  23
    //    00 01         ...          10  11
    private Board() {
        board = createEmptyRawBoard();
        statusStack = new GameStatus[2000];

        board[a1] = whiteRook;
        board[b1] = whiteKnight;
        board[c1] = whiteBishop;
        board[d1] = whiteQueen;
        board[e1] = whiteKing;
        board[f1] = whiteBishop;
        board[g1] = whiteKnight;
        board[h1] = whiteRook;

        board[a2] = whitePawn;
        board[b2] = whitePawn;
        board[c2] = whitePawn;
        board[d2] = whitePawn;
        board[e2] = whitePawn;
        board[f2] = whitePawn;
        board[g2] = whitePawn;
        board[h2] = whitePawn;

        board[a7] = blackPawn;
        board[b7] = blackPawn;
        board[c7] = blackPawn;
        board[d7] = blackPawn;
        board[e7] = blackPawn;
        board[f7] = blackPawn;
        board[g7] = blackPawn;
        board[h7] = blackPawn;

        board[a8] = blackRook;
        board[b8] = blackKnight;
        board[c8] = blackBishop;
        board[d8] = blackQueen;
        board[e8] = blackKing;
        board[f8] = blackBishop;
        board[g8] = blackKnight;
        board[h8] = blackRook;

        push(GameStatus.newGame());
    }

    private Board(Board other) {
        this.board = Arrays.copyOf(other.board, other.board.length);
        this.statusStack = Arrays.copyOf(other.statusStack, other.statusStack.length);
        this.stackSize = other.stackSize;
    }

    public static Board createNewGame() {
        return new Board();
    }

    static byte[] createEmptyRawBoard() {
        final var board = new byte[LENGTH*LENGTH];
        Arrays.fill(board, 0, 2 * LENGTH, illegal);
        board[a1 - 2] = illegal;
        board[a1 - 1] = illegal;
        board[h1 + 1] = illegal;
        board[h1 + 2] = illegal;
        board[a2 - 2] = illegal;
        board[a2 - 1] = illegal;
        board[h2 + 1] = illegal;
        board[h2 + 2] = illegal;
        board[a3 - 2] = illegal;
        board[a3 - 1] = illegal;
        board[h3 + 1] = illegal;
        board[h3 + 2] = illegal;
        board[a4 - 2] = illegal;
        board[a4 - 1] = illegal;
        board[h4 + 1] = illegal;
        board[h4 + 2] = illegal;
        board[a5 - 2] = illegal;
        board[a5 - 1] = illegal;
        board[h5 + 1] = illegal;
        board[h5 + 2] = illegal;
        board[a6 - 2] = illegal;
        board[a6 - 1] = illegal;
        board[h6 + 1] = illegal;
        board[h6 + 2] = illegal;
        board[a7 - 2] = illegal;
        board[a7 - 1] = illegal;
        board[h7 + 1] = illegal;
        board[h7 + 2] = illegal;
        board[a8 - 2] = illegal;
        board[a8 - 1] = illegal;
        board[h8 + 1] = illegal;
        board[h8 + 2] = illegal;
        Arrays.fill(board, h8 + 2, h8 + 2 + 2 * LENGTH, illegal);

        return board;
    }

    public Board copy() {
        return new Board(this);
    }

    public byte[] getRawBoard() {
        return board;
    }

    public byte getPieceAt(int col, int row) {
        int index = ChessUtil.getFieldFromColAndRow(col, row);
        return board[index];
    }

    private void push(GameStatus gameStatus) {
        statusStack[stackSize++] = gameStatus;
    }

    private void pop() {
        statusStack[--stackSize] = null;
    }

    public GameStatus getGameStatus() {
        return statusStack[stackSize - 1];
    }

    public List<GameStatus> getGameStatusStackCopy() {
        return new ArrayList<>(Arrays.asList(statusStack).subList(0, stackSize));
    }

    public String exportFEN() {
        return Fen.exportFEN(this);
    }

    public String calculatePositionKey() {
        var fen = exportFEN();
        int i1 = fen.lastIndexOf(' ', fen.lastIndexOf(' ', fen.lastIndexOf(' ') - 1) - 1);
        return fen.substring(0, i1);
    }

    public void makeMove(final MoveAndWeight move) {
        makeMove(move.move);
    }

    public void makeMove(final int move) {
        final GameStatus gameStatus = getGameStatus();
        final byte movedPiece = get(Move.getFromField(move));
        final byte capturedPiece = Move.getCapturedPiece(move);

        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        final int movingPieceNo = ChessUtil.getPieceNumber12(board[fromField]);
        final int toFieldNo = ChessUtil.getFieldNumber64(toField);

        // Make the move on the board
        long newPositionHash = MOVE_FUNCTIONS[Move.getMoveType(move)].move(this, move);

        // Reset halfMoveClock if a pawn was moved or a piece was captured
        int newHalfMoveClock = capturedPiece != 0 || Board.isPawn(movedPiece) ? 0 : gameStatus.getHalfMoveClock() + 1;

        // Calculate new castling state
        int newCastlingState = calculateNewCastlingState(gameStatus, move);
        newPositionHash ^= RANDOM_NUMBERS[CASTLING_RIGHTS_INDEX + (gameStatus.getCastlingState() % 16)];
        newPositionHash ^= RANDOM_NUMBERS[CASTLING_RIGHTS_INDEX + (newCastlingState % 16)];

        // Switch turn
        newPositionHash ^= RANDOM_NUMBERS[TURN_INDEX];

        // En passant right
        byte enPassantField = getEnPassantField(movedPiece, fromField, toField);

        // New game status
        push(new GameStatus(gameStatus.getPlyCount() + 1, gameStatus.getOppositeColor(), move, newHalfMoveClock, newCastlingState, enPassantField, newPositionHash));
    }

    static byte getEnPassantField(byte movedPiece, byte fromField, byte toField) {
        // Check theoretical en passant right. The en-passant field is the "skipped" filed of a pawn double move.
        if (movedPiece == Board.whitePawn && toField == fromField + 2 * Board.LENGTH) {
            return (byte) (fromField + Board.LENGTH);
        }
        if (movedPiece == Board.blackPawn && toField == fromField - 2 * Board.LENGTH) {
            return (byte) (fromField - Board.LENGTH);
        }

        return 0;
    }

    private int calculateNewCastlingState(GameStatus gameStatus, int move) {
        int bitSet = gameStatus.getCastlingState();

        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        final byte moveType = Move.getMoveType(move);

        if (gameStatus.isWhiteCastlingPossible()) {
            bitSet = setBit(bitSet, GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE,
                    gameStatus.isWhiteCastlingKingSidePossible()
                            && get(e1) == Board.whiteKing
                            && get(h1) == Board.whiteRook);
            bitSet = setBit(bitSet, GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE,
                    gameStatus.isWhiteCastlingQueenSidePossible()
                            && get(e1) == Board.whiteKing
                            && get(a1) == Board.whiteRook);

            if (gameStatus.getTurn() == GameStatus.TURN_WHITE && (moveType == Move.typeCastlingKingSide || moveType == Move.typeCastlingQueenSide)) {
                bitSet = setBit(bitSet, GameStatus.BIT_WHITE_HAS_CASTLED);
            }
        }
        if (gameStatus.isBlackCastlingPossible()) {
            bitSet = setBit(bitSet, GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE,
                    gameStatus.isBlackCastlingKingSidePossible()
                            && get(e8) == Board.blackKing
                            && get(h8) == Board.blackRook);
            bitSet = setBit(bitSet, GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE,
                    gameStatus.isBlackCastlingQueenSidePossible()
                            && get(e8) == Board.blackKing
                            && get(a8) == Board.blackRook);

            if (gameStatus.getTurn() == GameStatus.TURN_BLACK && (moveType == Move.typeCastlingKingSide || moveType == Move.typeCastlingQueenSide)) {
                bitSet = setBit(bitSet, GameStatus.BIT_BLACK_HAS_CASTLED);
            }
        }

        return bitSet;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();

        for (int row = 7; row >= 0; row--) {
            buf.append(row + 1).append("|");

            for (int col = 0; col <= 7; col++) {
                int index = ChessUtil.getFieldFromColAndRow(col, row);
                byte field = board[index];
                buf.append(toPrintSymbol(field));
                buf.append(' ');
            }
            buf.append('\n');
        }

        buf.append("  ---------------\n");
        buf.append("  a b c d e f g h");

        return buf.toString();
    }

    public void print() {
        System.out.println(this);
    }

    public static char toPrintSymbol(byte piece) {
        return printSymbols[piece];
    }

    byte get(int field) {
        return board[field];
    }

    void validateMove(int move) {
        byte fromField = Move.getFromField(move);
        byte toField = Move.getToField(move);
        if (fromField < 0 || fromField >= board.length)
            throw new IllegalStateException("Illegal move: " + move);
        if (toField < 0 || toField >= board.length)
            throw new IllegalStateException("Illegal move: " + move);
        byte piece = board[fromField];
        if (piece == empty || piece == illegal)
            throw new IllegalStateException("Illegal move: " + ChessUtil.moveToString(fromField, toField));
        byte targetField = board[toField];
        if (targetField == illegal)
            throw new IllegalStateException("Illegal move: " + ChessUtil.moveToString(fromField, toField));
    }

    static boolean isPawn(byte piece) {
        return piece == whitePawn || piece == blackPawn;
    }

    public static boolean isKing(byte piece) {
        return piece == whiteKing || piece == blackKing;
    }

    private static long makeNormalMove(Board board, int move) {
        return board._makeNormalMove(move);
    }

    private long _makeNormalMove(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);

        // Update position hash
        final int movingPieceNo = ChessUtil.getPieceNumber12(board[fromField]);
        final int toFieldNo = ChessUtil.getFieldNumber64(toField);
        long newPositionHash = getGameStatus().getPositionHash();

        // Remove moving piece from source field
        newPositionHash ^= RANDOM_NUMBERS[movingPieceNo * 64 + ChessUtil.getFieldNumber64(fromField)];
        // Remove captured piece from target field
        if (board[toField] != Board.empty) {
            final int capturedPieceNo = ChessUtil.getPieceNumber12(board[toField]);
            newPositionHash ^= RANDOM_NUMBERS[capturedPieceNo * 64 + toFieldNo];
        }
        // Add moving piece to target field
        newPositionHash ^= RANDOM_NUMBERS[movingPieceNo * 64 + toFieldNo];

        // Update en passant field
        int enPassantField = getGameStatus().getEnPassantField();
        if (enPassantField != 0) {
            newPositionHash ^= RANDOM_NUMBERS[EN_PASSANT_INDEX + enPassantField % Board.LENGTH - 2];
        }
        enPassantField = Board.getEnPassantField(board[fromField], fromField, toField);
        if (enPassantField != 0) {
            newPositionHash ^= RANDOM_NUMBERS[EN_PASSANT_INDEX + enPassantField % Board.LENGTH - 2];
        }

        board[toField] = board[fromField];
        board[fromField] = empty;

        return newPositionHash;
    }

    private static long makeEnPassantMove(Board board, int move) {
        return board._makeEnPassantMove(move);
    }

    private long _makeEnPassantMove(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);

        // Update position hash
        final int movingPieceNo = ChessUtil.getPieceNumber12(board[fromField]);
        final int toFieldNo = ChessUtil.getFieldNumber64(toField);
        long newPositionHash = getGameStatus().getPositionHash();

        // Remove pawn from source field
        newPositionHash ^= RANDOM_NUMBERS[movingPieceNo * 64 + ChessUtil.getFieldNumber64(fromField)];
        // Add pawn to target field
        newPositionHash ^= RANDOM_NUMBERS[movingPieceNo * 64 + toFieldNo];

        board[toField] = board[fromField];
        board[fromField] = empty;
        if (toField > fromField) { // white move
            board[toField - Board.LENGTH] = empty;
            // Remove captured pawn
            newPositionHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(blackPawn)  * 64 + ChessUtil.getFieldNumber64(toField - Board.LENGTH)];
        } else { // black move
            board[toField + Board.LENGTH] = empty;
            // Remove captured pawn
            newPositionHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(whitePawn) * 64 + ChessUtil.getFieldNumber64(toField + Board.LENGTH)];
        }

        return newPositionHash;
    }

    private static long makePawnPromotionMoveQueen(Board board, int move) {
        return board._makePawnPromotionMoveQueen(move);
    }

    private long _makePawnPromotionMoveQueen(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        final byte targetPiece = toField >= a8 ? Board.whiteQueen : Board.blackQueen;

        // Update position hash
        long newPositionHash = calcPromotionPositionHash(fromField, toField, targetPiece);

        board[fromField] = empty;
        board[toField] = targetPiece;

        return newPositionHash;
    }

    private long calcPromotionPositionHash(byte fromField, byte toField, byte targetPiece) {
        final int toFieldNo = ChessUtil.getFieldNumber64(toField);
        long newPositionHash = getGameStatus().getPositionHash();

        // Remove pawn from source field
        final int movingPieceNo = ChessUtil.getPieceNumber12(board[fromField]);
        newPositionHash ^= RANDOM_NUMBERS[movingPieceNo * 64 + ChessUtil.getFieldNumber64(fromField)];
        // Remove captured piece from target field
        if (board[toField] != Board.empty) {
            newPositionHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(board[toField]) * 64 + toFieldNo];
        }
        // Add queen to target field
        newPositionHash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(targetPiece) * 64 + toFieldNo];

        return newPositionHash;
    }

    private static long makePawnPromotionMoveKnight(Board board, int move) {
        return board._makePawnPromotionMoveKnight(move);
    }

    private long _makePawnPromotionMoveKnight(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        final byte targetPiece = toField >= a8 ? Board.whiteKnight : Board.blackKnight;

        // Update position hash
        long newPositionHash = calcPromotionPositionHash(fromField, toField, targetPiece);

        board[fromField] = empty;
        board[toField] = targetPiece;

        return newPositionHash;
    }

    private static long makePawnPromotionMoveRook(Board board, int move) {
        return board._makePawnPromotionMoveRook(move);
    }

    private long _makePawnPromotionMoveRook(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        final byte targetPiece = toField >= a8 ? Board.whiteRook : Board.blackRook;

        // Update position hash
        long newPositionHash = calcPromotionPositionHash(fromField, toField, targetPiece);

        board[fromField] = empty;
        board[toField] = targetPiece;

        return newPositionHash;
    }

    private static long makePawnPromotionMoveBishop(Board board, int move) {
        return board._makePawnPromotionMoveBishop(move);
    }

    private long _makePawnPromotionMoveBishop(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        final byte targetPiece = toField >= a8 ? Board.whiteBishop : Board.blackBishop;

        // Update position hash
        long newPositionHash = calcPromotionPositionHash(fromField, toField, targetPiece);

        board[fromField] = empty;
        board[toField] = targetPiece;

        return newPositionHash;
    }

    private static long makeCastlingKingSideMove(Board board, int move) {
        return board._makeCastlingKingSideMove(move);
    }

    @SuppressWarnings("Duplicates")
    private long _makeCastlingKingSideMove(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        long newPositionHash = getGameStatus().getPositionHash();

        board[toField] =  board[fromField];
        board[fromField] = empty;

        if (fromField == e1) {
            board[h1] = empty;
            board[f1] = whiteRook;

            // Update hash
            newPositionHash ^= RANDOM_NUMBERS[5 * 64 + ChessUtil.getFieldNumber64(e1)]; // white king
            newPositionHash ^= RANDOM_NUMBERS[5 * 64 + ChessUtil.getFieldNumber64(g1)];
            newPositionHash ^= RANDOM_NUMBERS[3 * 64 + ChessUtil.getFieldNumber64(h1)]; // white rook
            newPositionHash ^= RANDOM_NUMBERS[3 * 64 + ChessUtil.getFieldNumber64(f1)];
        } else {
            board[h8] = empty;
            board[f8] = blackRook;

            // Update hash
            newPositionHash ^= RANDOM_NUMBERS[11 * 64 + ChessUtil.getFieldNumber64(e8)]; // black king
            newPositionHash ^= RANDOM_NUMBERS[11 * 64 + ChessUtil.getFieldNumber64(g8)];
            newPositionHash ^= RANDOM_NUMBERS[9 * 64 + ChessUtil.getFieldNumber64(h8)]; // black rook
            newPositionHash ^= RANDOM_NUMBERS[9 * 64 + ChessUtil.getFieldNumber64(f8)];
        }

        return newPositionHash;
    }

    private static long makeCastlingQueenSideMove(Board board, int move) {
        return board._makeCastlingQueenSideMove(move);
    }

    @SuppressWarnings("Duplicates")
    private long _makeCastlingQueenSideMove(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        long newPositionHash = getGameStatus().getPositionHash();

        board[toField] =  board[fromField];
        board[fromField] = empty;

        if (fromField == e1) {
            board[a1] = empty;
            board[d1] = whiteRook;

            // Update hash
            newPositionHash ^= RANDOM_NUMBERS[5 * 64 + ChessUtil.getFieldNumber64(e1)]; // white king
            newPositionHash ^= RANDOM_NUMBERS[5 * 64 + ChessUtil.getFieldNumber64(c1)];
            newPositionHash ^= RANDOM_NUMBERS[3 * 64 + ChessUtil.getFieldNumber64(a1)]; // white rook
            newPositionHash ^= RANDOM_NUMBERS[3 * 64 + ChessUtil.getFieldNumber64(d1)];
        } else {
            board[a8] = empty;
            board[d8] = blackRook;

            // Update hash
            newPositionHash ^= RANDOM_NUMBERS[11 * 64 + ChessUtil.getFieldNumber64(e8)]; // black king
            newPositionHash ^= RANDOM_NUMBERS[11 * 64 + ChessUtil.getFieldNumber64(c8)];
            newPositionHash ^= RANDOM_NUMBERS[9 * 64 + ChessUtil.getFieldNumber64(a8)]; // black rook
            newPositionHash ^= RANDOM_NUMBERS[9 * 64 + ChessUtil.getFieldNumber64(d8)];
        }

        return newPositionHash;
    }

    public void revertMove() {
        if (stackSize <= 1) {
            throw new IllegalStateException("No move to revert");
        }

        int move = getGameStatus().getLastMove();
        MOVE_REVERT_FUNCTIONS[Move.getMoveType(move)].revert(this, move);
        pop();
    }

    private static void revertNormalMove(Board board, int move) {
        board._revertNormalMove(move);
    }

    private void _revertNormalMove(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);

        board[fromField] = board[toField];
        board[toField] = Move.getCapturedPiece(move);
    }

    private static void revertEnPassantMove(Board board, int move) {
        board._revertEnPassantMove(move);
    }

    private void _revertEnPassantMove(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);
        final byte capturedPiece = Move.getCapturedPiece(move);

        board[fromField] = board[toField];
        board[toField] = empty;
        if (toField > fromField) {
            board[toField - Board.LENGTH] = capturedPiece;
        } else {
            board[toField + Board.LENGTH] = capturedPiece;
        }
    }

    private static void revertPawnPromotionMove(Board board, int move) {
        board._revertPawnPromotionMove(move);
    }

    private void _revertPawnPromotionMove(int move) {
        byte fromField = Move.getFromField(move);
        byte toField = Move.getToField(move);

        board[fromField] = toField > fromField ? Board.whitePawn : Board.blackPawn;
        board[toField] = Move.getCapturedPiece(move);
    }

    private static void revertCastlingKingSideMove(Board board, int move) {
        board._revertCastlingKingSideMove(move);
    }

    private void _revertCastlingKingSideMove(int move) {
        byte fromField = Move.getFromField(move);
        byte toField = Move.getToField(move);

        board[fromField] = board[toField];
        board[toField] = Board.empty;

        if (fromField == Board.e1) {
            board[Board.f1] = Board.empty;
            board[Board.h1] = Board.whiteRook;
        } else {
            board[Board.f8] = Board.empty;
            board[Board.h8] = Board.blackRook;
        }
    }

    private static void revertCastlingQueenSideMove(Board board, int move) {
        board._revertCastlingQueenSideMove(move);
    }

    private void _revertCastlingQueenSideMove(int move) {
        byte fromField = Move.getFromField(move);
        byte toField = Move.getToField(move);

        board[fromField] = board[toField];
        board[toField] = Board.empty;

        if (fromField == Board.e1) {
            board[Board.d1] = Board.empty;
            board[Board.a1] = Board.whiteRook;
        } else {
            board[Board.d8] = Board.empty;
            board[Board.a8] = Board.blackRook;
        }
    }

    long calculatePositionHash() {
        return calculatePositionHash(board, getGameStatus());
    }

    static long calculatePositionHash(final byte[] board, final GameStatus gameStatus) {
        var hash = 0L;

        for (int field = a1; field <= h8; field++) {
            final byte piece = board[field];
            if (piece != Board.empty && piece != Board.illegal) {
                hash ^= RANDOM_NUMBERS[ChessUtil.getPieceNumber12(piece) * 64 + ChessUtil.getFieldNumber64(field)];
            }
        }

        // Castling rights
        hash ^= RANDOM_NUMBERS[CASTLING_RIGHTS_INDEX + (gameStatus.getCastlingState() % 16)];

        // Turn
        if (gameStatus.isBlackTurn()) {
            hash ^= RANDOM_NUMBERS[TURN_INDEX];
        }

        // En passant file
        int enPassantField = gameStatus.getEnPassantField();
        if (enPassantField != 0) {
            hash ^= RANDOM_NUMBERS[EN_PASSANT_INDEX + enPassantField % Board.LENGTH - 2];
        }

        return hash;
    }

    public static void main(String[] args) {
        Board board = new Board();
        board.print();
    }

    public boolean isThreefoldRepetition() {
        final GameStatus gameStatus = getGameStatus();
        final int halfMoveClock = gameStatus.getHalfMoveClock();
        if (halfMoveClock < 4 || stackSize < 4) {
            return false;
        }
        final long hash = gameStatus.getPositionHash();
        final int lowerLimit = Math.max(stackSize - 1 - halfMoveClock, 0);
        int count = 0;

        for (int i = stackSize - 3; i >= lowerLimit; i -= 2) {
            if (hash == statusStack[i].getPositionHash()) {
                count++;
            }
            if (count == 2) {
                return true;
            }
        }

        return false;
    }

    public boolean isKingChecked(MoveGenerator moveGenerator) {
        // TODO MF: Optimize method testIsKingChecked
        // Switch turn
        GameStatus gameStatus = getGameStatus().switchTurn();

        // Check the next theoretically possible moves. If those contain an illegal move (king can be captured),
        // the king was under check.
        // TODO MF: Calculate moves without sorting
        Moves nextMoves = moveGenerator.calculateMoves(gameStatus, this, 0, 0);
        return nextMoves.isIllegal();
    }

    public boolean isCheckmate(MoveGenerator moveGenerator) {
        if (!isKingChecked(moveGenerator)) {
            return false;
        }

        Moves nextMoves = moveGenerator.calculateMoves(getGameStatus(), this, 0, 0);
        if (nextMoves.isIllegal()) {
            return false; // illegal chess position
        }

        // Try to find a move to escape the check
        final int[] plainMoves = nextMoves.getMoves();
        final int countMoves = nextMoves.count();

        for (int i = 0; i < countMoves; i++) {
            var move = plainMoves[i];
            makeMove(move);

            // Check the next theoretically possible moves. If those contain an illegal move (king can be captured),
            // the king is still under check.
            Moves nextMoves2 = moveGenerator.calculateMoves(getGameStatus(), this, 0, 0);
            if (!nextMoves2.isIllegal()) {
                revertMove();
                return false;
            }

            revertMove();
        }

        return true;
    }

    public MoveDescription resolveMoveDescription(MoveDescription moveDescr, MoveGenerator moveGenerator) {
        var builder = new Builder(moveDescr);
        int toField = moveDescr.getToField();

        if (builder.piece <= 0) {
            builder.piece = get(moveDescr.getFromField());
        }

        if (builder.fromCol < 0 || builder.fromRow < 0) {
            // Must resolve source field
            var possibleMoves = getPossiblePieceMoves(builder.piece, toField, this, moveGenerator);
            if (builder.fromCol >= 0) {
                possibleMoves.removeIf(move -> Move.getFromCol(move) != builder.fromCol);
            }
            if (builder.fromRow >= 0) {
                possibleMoves.removeIf(move -> Move.getFromRow(move) != builder.fromRow);
            }
            if (builder.pawnPromotionPiece > 0) {
                possibleMoves.removeIf(move -> Move.getMoveType(move) != Move.typePawnPromotionQueen);
            }
            if (possibleMoves.size() > 1) {
                // Remove illegal moves
                var workingBoard = copy();
                possibleMoves.removeIf(move -> {
                    workingBoard.makeMove(move);
                    Moves nextMoves = moveGenerator.calculateMoves(workingBoard.getGameStatus(), workingBoard, 0, 0);
                    workingBoard.revertMove();
                    return nextMoves.isIllegal();
                });
            }
            if (possibleMoves.isEmpty()) {
                throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Impossible move.");
            }
            if (possibleMoves.size() > 1) {
                throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Move is not unique.");
            }

            int move = possibleMoves.iterator().next();
            builder.fromCol = Move.getFromCol(move);
            builder.fromRow = Move.getFromRow(move);
        }

        return builder.build();
    }

    private static Set<Integer> getPossiblePieceMoves(byte piece, int toField, Board board, MoveGenerator moveGenerator) {
        var result = new HashSet<Integer>();

        int[] possibleMoves = moveGenerator.calculateMoves(board).getMoves();

        for (int move : possibleMoves) {
            if (toField == Move.getToField(move) && board.get(Move.getFromField(move)) == piece) {
                result.add(move);
            }
        }

        return result;
    }

    public Move moveDescriptionToMove(MoveDescription moveDescr) {
        int fromField = moveDescr.getFromField();
        int toField = moveDescr.getToField();
        byte piece = get(fromField);
        byte capturedPiece = get(toField);
        byte moveType = Move.typeNormal;
        byte promotionPiece = moveDescr.pawnPromotionPiece;

        if (Board.whiteQueen == promotionPiece || Board.blackQueen == promotionPiece)
            moveType = Move.typePawnPromotionQueen;
        else if (Board.whiteKnight == promotionPiece || Board.blackKnight == promotionPiece)
            moveType = Move.typePawnPromotionKnight;
        else if (Board.whiteRook == promotionPiece || Board.blackRook == promotionPiece)
            moveType = Move.typePawnPromotionRook;
        else if (Board.whiteBishop == promotionPiece || Board.blackBishop == promotionPiece)
            moveType = Move.typePawnPromotionBishop;
        else if (piece == Board.whiteKing && fromField == Board.e1 && toField == Board.g1)
            moveType = Move.typeCastlingKingSide;
        else if (piece == Board.whiteKing && fromField == Board.e1 && toField == Board.c1)
            moveType = Move.typeCastlingQueenSide;
        else if (piece == Board.blackKing && fromField == Board.e8 && toField == Board.g8)
            moveType = Move.typeCastlingKingSide;
        else if (piece == Board.blackKing && fromField == Board.e8 && toField == Board.c8)
            moveType = Move.typeCastlingQueenSide;
        else if ((piece == Board.whitePawn && ChessUtil.getRowOfField(toField) == 7)
                || (piece == Board.blackPawn && ChessUtil.getRowOfField(toField) == 0)) {
            // Sanity check: Pawn promotion symbol is missing ==> assume queen
            moveType = Move.typePawnPromotionQueen;
        } else if ((piece == Board.whitePawn || piece == Board.blackPawn)
                && ChessUtil.getColOfField(fromField) != ChessUtil.getColOfField(toField)
                && capturedPiece == 0) {
            moveType = Move.typeEnPassant;
            capturedPiece = piece == Board.whitePawn ? Board.blackPawn : Board.whitePawn;
        }

        return new Move(Move.create((byte) fromField, (byte) toField, capturedPiece, moveType));
    }

    public MoveDescription moveToShortNotation(Move move) {
        var builder = new MoveDescription.Builder(getGameStatus().getTurn());
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());

        makeMove(move.getMove());
        builder.isCheckmate = isCheckmate(moveGenerator);
        builder.isCheck = isKingChecked(moveGenerator);
        revertMove();

        builder.piece = get(move.getFromField());
        builder.toCol = move.getToCol();
        builder.toRow = move.getToRow();
        if (move.getCapturedPiece() != Board.empty) {
            builder.isCapture = true;
            if (Board.isPawn(builder.piece)) {
                builder.fromCol = move.getFromCol();
            }
        }

        var pawnPromotionPiece = move.getPawnPromotionPiece();
        if (pawnPromotionPiece > 0) {
            builder.pawnPromotionPiece = pawnPromotionPiece;
        }

        var moveType = Move.getMoveType(move.getMove());
        builder.isCastlingKingSide = moveType == Move.typeCastlingKingSide;
        builder.isCastlingQueenSide = moveType == Move.typeCastlingQueenSide;

        try {
            var moveDescr = builder.build();
            resolveMoveDescription(moveDescr, moveGenerator);
            return moveDescr;
        } catch (RuntimeException e) {
            // fall through
        }

        if (builder.fromCol == -1) {
            builder.fromCol = move.getFromCol();
            try {
                var moveDescr = builder.build();
                resolveMoveDescription(moveDescr, moveGenerator);
                return moveDescr;
            } catch (RuntimeException e) {
                // fall through
            }
            builder.fromCol = -1;
            builder.fromRow = move.getFromRow();
            try {
                var moveDescr = builder.build();
                resolveMoveDescription(moveDescr, moveGenerator);
                return moveDescr;
            } catch (RuntimeException e) {
                // fall through
            }
        }

        builder.fromCol = move.getFromCol();
        builder.fromRow = move.getFromRow();
        var moveDescr = builder.build();
        resolveMoveDescription(moveDescr, moveGenerator);
        return moveDescr;
    }

}
