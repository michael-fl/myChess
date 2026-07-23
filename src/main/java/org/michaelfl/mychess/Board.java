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

/**
 * Mutable chess board with a 12x12 byte mailbox layout (illegal-square border)
 * and a stack of {@link GameStatus} snapshots that makes every {@code makeMove}
 * undoable in O(1) via {@code revertMove}. Owns Zobrist hashing and the
 * resolution of symbolic moves ({@link MoveDescription}) into packed
 * integer moves.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings({"WeakerAccess", "unused", "PointlessArithmeticExpression", "DuplicatedCode", "java:S115"})
public final class Board {

    @FunctionalInterface
    private interface IMove {
        long move(Board board, int move);
    }

    @FunctionalInterface
    private interface IRevertMove {
        void revert(Board board, int move);
    }

    public static final byte illegal = 64;
    public static final byte empty = 0;
    public static final byte whitePawn = 8;
    public static final byte whiteKnight = 9;
    public static final byte whiteBishop = 10;
    public static final byte whiteRook = 11;
    public static final byte whiteQueen = 12;
    public static final byte whiteKing = 13;
    public static final byte blackPawn = 16;
    public static final byte blackKnight = 17;
    public static final byte blackBishop = 18;
    public static final byte blackRook = 19;
    public static final byte blackQueen = 20;
    public static final byte blackKing = 21;

    public static final int LENGTH = 12;

    public static final int a1 = 2 * LENGTH + 2 + 0;
    public static final int b1 = 2 * LENGTH + 2 + 1;
    public static final int c1 = 2 * LENGTH + 2 + 2;
    public static final int d1 = 2 * LENGTH + 2 + 3;
    public static final int e1 = 2 * LENGTH + 2 + 4;
    public static final int f1 = 2 * LENGTH + 2 + 5;
    public static final int g1 = 2 * LENGTH + 2 + 6;
    public static final int h1 = 2 * LENGTH + 2 + 7;
    public static final int a2 = 3 * LENGTH + 2 + 0;
    public static final int b2 = 3 * LENGTH + 2 + 1;
    public static final int c2 = 3 * LENGTH + 2 + 2;
    public static final int d2 = 3 * LENGTH + 2 + 3;
    public static final int e2 = 3 * LENGTH + 2 + 4;
    public static final int f2 = 3 * LENGTH + 2 + 5;
    public static final int g2 = 3 * LENGTH + 2 + 6;
    public static final int h2 = 3 * LENGTH + 2 + 7;
    public static final int a3 = 4 * LENGTH + 2 + 0;
    public static final int b3 = 4 * LENGTH + 2 + 1;
    public static final int c3 = 4 * LENGTH + 2 + 2;
    public static final int d3 = 4 * LENGTH + 2 + 3;
    public static final int e3 = 4 * LENGTH + 2 + 4;
    public static final int f3 = 4 * LENGTH + 2 + 5;
    public static final int g3 = 4 * LENGTH + 2 + 6;
    public static final int h3 = 4 * LENGTH + 2 + 7;
    public static final int a4 = 5 * LENGTH + 2 + 0;
    public static final int b4 = 5 * LENGTH + 2 + 1;
    public static final int c4 = 5 * LENGTH + 2 + 2;
    public static final int d4 = 5 * LENGTH + 2 + 3;
    public static final int e4 = 5 * LENGTH + 2 + 4;
    public static final int f4 = 5 * LENGTH + 2 + 5;
    public static final int g4 = 5 * LENGTH + 2 + 6;
    public static final int h4 = 5 * LENGTH + 2 + 7;
    public static final int a5 = 6 * LENGTH + 2 + 0;
    public static final int b5 = 6 * LENGTH + 2 + 1;
    public static final int c5 = 6 * LENGTH + 2 + 2;
    public static final int d5 = 6 * LENGTH + 2 + 3;
    public static final int e5 = 6 * LENGTH + 2 + 4;
    public static final int f5 = 6 * LENGTH + 2 + 5;
    public static final int g5 = 6 * LENGTH + 2 + 6;
    public static final int h5 = 6 * LENGTH + 2 + 7;
    public static final int a6 = 7 * LENGTH + 2 + 0;
    public static final int b6 = 7 * LENGTH + 2 + 1;
    public static final int c6 = 7 * LENGTH + 2 + 2;
    public static final int d6 = 7 * LENGTH + 2 + 3;
    public static final int e6 = 7 * LENGTH + 2 + 4;
    public static final int f6 = 7 * LENGTH + 2 + 5;
    public static final int g6 = 7 * LENGTH + 2 + 6;
    public static final int h6 = 7 * LENGTH + 2 + 7;
    public static final int a7 = 8 * LENGTH + 2 + 0;
    public static final int b7 = 8 * LENGTH + 2 + 1;
    public static final int c7 = 8 * LENGTH + 2 + 2;
    public static final int d7 = 8 * LENGTH + 2 + 3;
    public static final int e7 = 8 * LENGTH + 2 + 4;
    public static final int f7 = 8 * LENGTH + 2 + 5;
    public static final int g7 = 8 * LENGTH + 2 + 6;
    public static final int h7 = 8 * LENGTH + 2 + 7;
    public static final int a8 = 9 * LENGTH + 2 + 0;
    public static final int b8 = 9 * LENGTH + 2 + 1;
    public static final int c8 = 9 * LENGTH + 2 + 2;
    public static final int d8 = 9 * LENGTH + 2 + 3;
    public static final int e8 = 9 * LENGTH + 2 + 4;
    public static final int f8 = 9 * LENGTH + 2 + 5;
    public static final int g8 = 9 * LENGTH + 2 + 6;
    public static final int h8 = 9 * LENGTH + 2 + 7;

    /**
     * Base offset of the 1-entry side-to-move sub-table in
     * {@link RandomNumbers#RANDOM_NUMBERS}. A single random number
     * suffices for the two possible turn values: it is XOR-ed exactly
     * when Black is on move (see {@link #calculatePositionHash(byte[],
     * GameStatus)}) or, equivalently, toggled once per turn switch in
     * the incremental update — two XORs of the same value cancel, so
     * one slot cleanly separates the White-to-move and Black-to-move
     * hashes.
     *
     * <p>Sub-table length: 1. Range in {@code RANDOM_NUMBERS}:
     * {@code [768, 769)}. See {@code docs/data-types.md §3.8} for the
     * full table layout.
     */
    private static final int TURN_INDEX = 12 * 64;

    /**
     * Base offset of the 16-entry castling-rights sub-table in
     * {@link RandomNumbers#RANDOM_NUMBERS}. One random per possible
     * combination of the four rights bits. The castling contribution
     * to the hash is:
     *
     * <pre>
     *   hash ^= RANDOM_NUMBERS[CASTLING_RIGHTS_INDEX + (castlingState % 16)];
     * </pre>
     *
     * <p>Only the low four bits of {@link GameStatus#getCastlingState()}
     * are read — the four castling-rights bits ({@code BIT_WHITE_
     * CASTLING_KING_SIDE_POSSIBLE}, {@code ..._QUEEN_SIDE_POSSIBLE},
     * and the two black counterparts). The two higher {@code HAS_CASTLED}
     * bits are intentionally <em>not</em> hashed: once a side has
     * castled, both of its rights bits are already cleared, so the
     * "has castled" state is fully encoded by the low-four-bits pattern
     * being zero for that side. Adding the {@code HAS_CASTLED} bits to
     * the hash would introduce redundant information without
     * distinguishing any additional position.
     *
     * <p>Sub-table length: 16. Range in {@code RANDOM_NUMBERS}:
     * {@code [769, 785)}. See {@code docs/data-types.md §3.8} for the
     * full table layout.
     */
    private static final int CASTLING_RIGHTS_INDEX = 12 * 64 + 1;

    /**
     * Base offset of the 8-entry en-passant sub-table in
     * {@link RandomNumbers#RANDOM_NUMBERS}. One random per file (a..h).
     * The en-passant contribution to the hash is:
     *
     * <pre>
     *   int file = enPassantField % Board.LENGTH - 2;    // 0..7 (a..h)
     *   hash    ^= RANDOM_NUMBERS[EN_PASSANT_INDEX + file];
     * </pre>
     *
     * <p>Only the file is hashed, not the rank: an en-passant target
     * always sits on rank 3 (a white pawn's double-move aftermath) or
     * rank 6 (a black pawn's), and which of the two is determined by
     * whose turn it is — so the rank carries no additional distinguishing
     * information beyond what {@link #TURN_INDEX} already contributes.
     *
     * <p>The {@code - 2} accounts for the two-column left border of the
     * 12x12 mailbox board: {@link #a3}={@value Board#a3} lives at
     * mailbox column 2, so {@code (enPassantField % 12) - 2} maps a
     * legal en-passant square index back to a 0-based file. The formula
     * only makes sense for a non-zero {@code enPassantField}; the two
     * hash sites — {@link #calculatePositionHash(byte[], GameStatus)}
     * and the incremental update in {@link #_makeNormalMove(int)} —
     * both guard on {@code enPassantField != 0} before reading the
     * random.
     *
     * <p>Sub-table length: 8. Range in {@code RANDOM_NUMBERS}:
     * {@code [785, 793)}. See {@code docs/data-types.md §3.8} for the
     * full table layout.
     */
    private static final int EN_PASSANT_INDEX = 12 * 64 + 17;

    // Piece-number constants for direct indexing into RANDOM_NUMBERS.
    // Equivalent to ChessUtil.getPieceNumber12(<piece byte>), surfaced as
    // compile-time constants so the castling-hash code can compose the
    // RANDOM_NUMBERS index inline without going through the helper.
    private static final int WHITE_ROOK_NO = 3;
    private static final int WHITE_KING_NO = 5;
    private static final int BLACK_ROOK_NO = 9;
    private static final int BLACK_KING_NO = 11;

    private static final char[] printSymbols = new char[22];
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

    static final char[] fenSymbols = new char[22];
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

    private static final IMove[] MOVE_FUNCTIONS = new IMove[Move.typeEnPassant + 1];
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

    private static final IRevertMove[] MOVE_REVERT_FUNCTIONS = new IRevertMove[Move.typeEnPassant + 1];
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

    static final byte[] FIELD_12_TO_8 = new byte[144];
    static {
        for (int field = 0; field < 144; field++) {
            int row = field / Board.LENGTH - 2;
            int col = field % Board.LENGTH - 2;

            FIELD_12_TO_8[field] = (byte) (row * 8 + col);
        }
    }

    private final byte[] board;
    private final GameStatus[] statusStack;
    private final boolean is960;

    private int stackSize;

    /**
     * Starting files of the castling rooks, indexed by
     * {@code 0 = queenside, 1 = kingside}.
     *
     * <p>Chess960 guarantees the starting position is symmetric across
     * the colors — Black's back rank is by definition the mirror of
     * White's, so the queenside rook of both colors starts on the same
     * file, and similarly for the kingside rook. Storing one file per
     * side (rather than one per side × color) makes that invariant
     * explicit in the data layout; the FEN parser enforces it on input.
     *
     * <p>A file value is in {@code [0, 7]}. Set once at construction
     * time — game-constant, not reverted on {@code revertMove}.
     */
    private final byte[] castlingRookFiles;

    public Board(byte[] rawBoard, GameStatus gameStatus) {
        this(rawBoard, gameStatus, defaultCastlingRookFiles(), false);
    }

    public Board(byte[] rawBoard, GameStatus gameStatus, byte[] castlingRookFiles, boolean is960) {
        this.board = rawBoard;
        this.statusStack = new GameStatus[2000];
        this.castlingRookFiles = castlingRookFiles;
        push(gameStatus);
        this.is960 = is960 || isChess960Position();
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
        is960 = false;
        castlingRookFiles = defaultCastlingRookFiles();

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
        this.is960 = other.is960;
        this.stackSize = other.stackSize;
        this.castlingRookFiles = Arrays.copyOf(other.castlingRookFiles, other.castlingRookFiles.length);
    }

    public static Board createNewGame() {
        return new Board();
    }

    /**
     * Returns a fresh 2-entry array {@code { 0, 7 }} encoding the
     * standard chess castling-rook files (a-file queenside, h-file
     * kingside). Indexed by {@code 0 = queenside, 1 = kingside}, mirrored
     * across both colors per the Chess960 symmetry invariant. A new
     * instance is returned per call so the caller can safely mutate it.
     */
    public static byte[] defaultCastlingRookFiles() {
        return new byte[] { 0, 7 };
    }

    /**
     * Returns the back-rank file (0..7) of the rook that backs the given
     * castling slot. The value is meaningful only while the matching
     * castling-right bit in {@link GameStatus#getCastlingState()} is set.
     *
     * <p>The color half of {@code slot} is ignored: the value for
     * {@link CastlingSlot#WHITE_KINGSIDE} and
     * {@link CastlingSlot#BLACK_KINGSIDE} is the same (the kingside-rook
     * file), and likewise the two queenside variants return the same
     * value. This follows from the Chess960 starting-position symmetry —
     * Black's back rank mirrors White's, so the rook files are paired by
     * side, not by color. The four-valued {@link CastlingSlot} enum
     * carries other per-color information (e.g. {@code CastlingSlot#bitMask})
     * that this lookup ignores.
     */
    public int getCastlingRookFile(CastlingSlot slot) {
        return castlingRookFiles[slot.getKingQueenSideIndex()];
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

    public boolean isStandardChess() {
        return !isChess960();
    }

    public boolean isChess960() {
        return is960;
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

    public String exportShredderFEN() {
        return Fen.exportShredderFEN(this);
    }

    public String calculatePositionKey() {
        var fen = exportFEN();
        int i1 = fen.lastIndexOf(' ', fen.lastIndexOf(' ', fen.lastIndexOf(' ') - 1) - 1);
        return fen.substring(0, i1);
    }

    public void makeMove(final MoveAndWeight move) {
        makeMove(move.move());
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

        int materialWeightOfMove = WeightingFunction.getMaterialWeightOfMove(move);

        // New game status
        push(getGameStatus().switchTurn(move, newHalfMoveClock, newCastlingState, enPassantField, newPositionHash));
    }

    public void makeNullMove() {
        final GameStatus gameStatus = getGameStatus();
        long newPositionHash = gameStatus.getPositionHash();

        // Clear en-passant field
        newPositionHash = clearEnPassantHashContribution(newPositionHash, false);

        // Switch turn
        newPositionHash ^= RANDOM_NUMBERS[TURN_INDEX];

        // New game status
        push(getGameStatus().switchTurn(
                0, // no actual move
                0, // reset half move clock
                gameStatus.getCastlingState(),
                (byte) 0, // reset en-passant field
                newPositionHash));
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
        if (gameStatus.hasWhiteCastled() && gameStatus.hasBlackCastled()) {
            return gameStatus.getCastlingState();
        }

        int bitSet = gameStatus.getCastlingState();

        final byte toField = Move.getToField(move);
        final byte moveType = Move.getMoveType(move);

        if (gameStatus.isWhiteCastlingPossible()) {
            if (gameStatus.getTurn() == GameStatus.TURN_WHITE && (moveType == Move.typeCastlingKingSide || moveType == Move.typeCastlingQueenSide)) {
                bitSet = setBit(bitSet, GameStatus.BIT_WHITE_HAS_CASTLED);
                bitSet = setBit(bitSet, GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE, false);
                bitSet = setBit(bitSet, GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE, false);
            } else {
                byte piece = get(toField);
                if (piece == Board.whiteKing) {
                    bitSet = setBit(bitSet, GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE, false);
                    bitSet = setBit(bitSet, GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE, false);
                } else {
                    int kFile = getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE);
                    int qFile = getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE);
                    bitSet = setBit(bitSet, GameStatus.BIT_WHITE_CASTLING_KING_SIDE_POSSIBLE,
                            gameStatus.isWhiteCastlingKingSidePossible() && get(ChessUtil.getFieldFromColAndRow(kFile, 0)) == Board.whiteRook);
                    bitSet = setBit(bitSet, GameStatus.BIT_WHITE_CASTLING_QUEEN_SIDE_POSSIBLE,
                            gameStatus.isWhiteCastlingQueenSidePossible() && get(ChessUtil.getFieldFromColAndRow(qFile, 0)) == Board.whiteRook);
                }
            }
        }

        if (gameStatus.isBlackCastlingPossible()) {
            if (gameStatus.getTurn() == GameStatus.TURN_BLACK && (moveType == Move.typeCastlingKingSide || moveType == Move.typeCastlingQueenSide)) {
                bitSet = setBit(bitSet, GameStatus.BIT_BLACK_HAS_CASTLED);
                bitSet = setBit(bitSet, GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE, false);
                bitSet = setBit(bitSet, GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE, false);
            } else {
                byte piece = get(toField);
                if (piece == Board.blackKing) {
                    bitSet = setBit(bitSet, GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE, false);
                    bitSet = setBit(bitSet, GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE, false);
                } else {
                    int kFile = getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE);
                    int qFile = getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE);
                    bitSet = setBit(bitSet, GameStatus.BIT_BLACK_CASTLING_KING_SIDE_POSSIBLE,
                            gameStatus.isBlackCastlingKingSidePossible() && get(ChessUtil.getFieldFromColAndRow(kFile, 7)) == Board.blackRook);
                    bitSet = setBit(bitSet, GameStatus.BIT_BLACK_CASTLING_QUEEN_SIDE_POSSIBLE,
                            gameStatus.isBlackCastlingQueenSidePossible() && get(ChessUtil.getFieldFromColAndRow(qFile, 7)) == Board.blackRook);
                }
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

    public byte get(int field) {
        return board[field];
    }

    /**
     * Defensive pre-check for {@link #makeMove(int)}: verifies that the
     * move's {@code fromField} / {@code toField} indices are inside the
     * 12×12 board array, that the source square actually holds a piece
     * (not empty, not the illegal-border sentinel), and that the target
     * square is on the playable board. Throws
     * {@link IllegalStateException} with a human-readable move string on
     * any violation.
     *
     * <p>Added during the transposition-table work to make the symptom
     * of a bad TT- or PV-supplied move ordering hint actionable: a stale
     * move encoded with an empty source square used to fail deep inside
     * {@link #_makeNormalMove}'s Zobrist update with a cryptic
     * {@link ArrayIndexOutOfBoundsException}; this helper surfaces the
     * problem at the move's entry point with the actual squares named.
     */
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

        if (movingPieceNo < 0 || movingPieceNo >= 12) {
            throw new IllegalStateException("Wrong source piece: " + board[fromField]);
        }

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
        newPositionHash = clearEnPassantHashContribution(newPositionHash, false);
        byte enPassantField = Board.getEnPassantField(board[fromField], fromField, toField);
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

        return clearEnPassantHashContribution(newPositionHash, true);
    }

    private long clearEnPassantHashContribution(final long positionHash, boolean isEnPassantMove) {
        int enPassantField = getGameStatus().getEnPassantField();
        if (enPassantField != 0) {
            return positionHash ^ RANDOM_NUMBERS[EN_PASSANT_INDEX + enPassantField % Board.LENGTH - 2];
        } else if (isEnPassantMove) {
            throw new IllegalStateException("En-passant field must be set in GameStatus on en-passant move");
        }

        return positionHash;
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

        return clearEnPassantHashContribution(newPositionHash, false);
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

        if (fromField != toField) {
            board[toField] = board[fromField];
            board[fromField] = empty;
        }

        if (ChessUtil.getRowOfField(fromField) == 0) { // White
            final int rookFile = getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE);
            final int rookField = ChessUtil.getFieldFromColAndRow(rookFile, 0);
            if (toField != rookField) {
                board[rookField] = empty;
            }
            board[f1] = whiteRook;

            // Update hash
            newPositionHash ^= RANDOM_NUMBERS[WHITE_KING_NO * 64 + ChessUtil.getFieldNumber64(fromField)]; // remove king
            newPositionHash ^= RANDOM_NUMBERS[WHITE_KING_NO * 64 + ChessUtil.getFieldNumber64(g1)];        // add king
            newPositionHash ^= RANDOM_NUMBERS[WHITE_ROOK_NO * 64 + ChessUtil.getFieldNumber64(rookField)]; // remove rook
            newPositionHash ^= RANDOM_NUMBERS[WHITE_ROOK_NO * 64 + ChessUtil.getFieldNumber64(f1)];        // add rook
        } else { // Black
            final int rookFile = getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE);
            final int rookField = ChessUtil.getFieldFromColAndRow(rookFile, 7);
            if (toField != rookField) {
                board[rookField] = empty;
            }
            board[f8] = blackRook;

            // Update hash
            newPositionHash ^= RANDOM_NUMBERS[BLACK_KING_NO * 64 + ChessUtil.getFieldNumber64(fromField)]; // remove king
            newPositionHash ^= RANDOM_NUMBERS[BLACK_KING_NO * 64 + ChessUtil.getFieldNumber64(g8)];        // add king
            newPositionHash ^= RANDOM_NUMBERS[BLACK_ROOK_NO * 64 + ChessUtil.getFieldNumber64(rookField)]; // remove rook
            newPositionHash ^= RANDOM_NUMBERS[BLACK_ROOK_NO * 64 + ChessUtil.getFieldNumber64(f8)];        // add rook
        }

        return clearEnPassantHashContribution(newPositionHash, false);
    }

    private static long makeCastlingQueenSideMove(Board board, int move) {
        return board._makeCastlingQueenSideMove(move);
    }

    @SuppressWarnings("Duplicates")
    private long _makeCastlingQueenSideMove(int move) {
        final byte fromField = Move.getFromField(move);
        final byte toField = Move.getToField(move);

        long newPositionHash = getGameStatus().getPositionHash();

        if (fromField != toField) {
            board[toField] = board[fromField];
            board[fromField] = empty;
        }

        if (ChessUtil.getRowOfField(fromField) == 0) { // White
            final int rookFile = getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE);
            final int rookField = ChessUtil.getFieldFromColAndRow(rookFile, 0);
            if (toField != rookField) {
                board[rookField] = empty;
            }
            board[d1] = whiteRook;

            // Update hash
            newPositionHash ^= RANDOM_NUMBERS[WHITE_KING_NO * 64 + ChessUtil.getFieldNumber64(fromField)]; // remove king
            newPositionHash ^= RANDOM_NUMBERS[WHITE_KING_NO * 64 + ChessUtil.getFieldNumber64(c1)];        // add king
            newPositionHash ^= RANDOM_NUMBERS[WHITE_ROOK_NO * 64 + ChessUtil.getFieldNumber64(rookField)]; // remove rook
            newPositionHash ^= RANDOM_NUMBERS[WHITE_ROOK_NO * 64 + ChessUtil.getFieldNumber64(d1)];        // add rook
        } else { // Black
            final int rookFile = getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE);
            final int rookField = ChessUtil.getFieldFromColAndRow(rookFile, 7);
            if (toField != rookField) {
                board[rookField] = empty;
            }
            board[d8] = blackRook;

            // Update hash
            newPositionHash ^= RANDOM_NUMBERS[BLACK_KING_NO * 64 + ChessUtil.getFieldNumber64(fromField)]; // remove king
            newPositionHash ^= RANDOM_NUMBERS[BLACK_KING_NO * 64 + ChessUtil.getFieldNumber64(c8)];        // add king
            newPositionHash ^= RANDOM_NUMBERS[BLACK_ROOK_NO * 64 + ChessUtil.getFieldNumber64(rookField)]; // remove rook
            newPositionHash ^= RANDOM_NUMBERS[BLACK_ROOK_NO * 64 + ChessUtil.getFieldNumber64(d8)];        // add rook
        }

        return clearEnPassantHashContribution(newPositionHash, false);
    }

    public void revertMove() {
        if (stackSize <= 1) {
            throw new IllegalStateException("No move to revert");
        }

        int move = getGameStatus().getLastMove();
        MOVE_REVERT_FUNCTIONS[Move.getMoveType(move)].revert(this, move);
        pop();
    }

    public void revertNullMove() {
        if (stackSize <= 1) {
            throw new IllegalStateException("No move to revert");
        }
        if (getGameStatus().getLastMove() != 0) {
            throw new IllegalStateException("Previous move wasn't a null move");
        }

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

        if (fromField != toField) {
            board[fromField] = board[toField];
            board[toField] = Board.empty;
        }

        if (toField == Board.g1) {
            if (Board.f1 != fromField) {
                board[Board.f1] = Board.empty;
            }
            board[ChessUtil.getFieldFromColAndRow(getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE), 0)] = Board.whiteRook;
        } else {
            if (Board.f8 != fromField) {
                board[Board.f8] = Board.empty;
            }
            board[ChessUtil.getFieldFromColAndRow(getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE), 7)] = Board.blackRook;
        }
    }

    private static void revertCastlingQueenSideMove(Board board, int move) {
        board._revertCastlingQueenSideMove(move);
    }

    private void _revertCastlingQueenSideMove(int move) {
        byte fromField = Move.getFromField(move);
        byte toField = Move.getToField(move);

        if (fromField != toField) {
            board[fromField] = board[toField];
            board[toField] = Board.empty;
        }

        if (toField == Board.c1) {
            if (Board.d1 != fromField) {
                board[Board.d1] = Board.empty;
            }
            board[ChessUtil.getFieldFromColAndRow(getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE), 0)] = Board.whiteRook;
        } else {
            if (Board.d8 != fromField) {
                board[Board.d8] = Board.empty;
            }
            board[ChessUtil.getFieldFromColAndRow(getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE), 7)] = Board.blackRook;
        }
    }

    public long calculatePositionHash() {
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

    public static void main() {
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

    // ---- Attack detection ----
    //
    // These helpers answer "is square X currently attacked by side Y?" without
    // enumerating or sorting moves. They walk outward from the target square
    // in each geometric direction that could deliver an attack and short-circuit
    // on the first attacker found — typically returning false after probing
    // just a handful of squares. Significantly cheaper than running the full
    // pseudo-legal move generator and inspecting Moves.isIllegal(), which is
    // what an earlier implementation of isKingChecked used to do.

    public static final int[] KNIGHT_OFFSETS = {
            2 * LENGTH + 1, 2 * LENGTH - 1, -2 * LENGTH + 1, -2 * LENGTH - 1,
            LENGTH + 2, LENGTH - 2, -LENGTH + 2, -LENGTH - 2
    };

    public static final int[] KING_ADJACENCY_OFFSETS = {
            LENGTH, LENGTH + 1, 1, -LENGTH + 1, -LENGTH, -LENGTH - 1, -1, LENGTH - 1
    };

    public static final int[] DIAGONAL_RAY_DIRS = {
            LENGTH + 1, LENGTH - 1, -LENGTH + 1, -LENGTH - 1
    };

    public static final int[] ORTHOGONAL_RAY_DIRS = {
            LENGTH, -LENGTH, 1, -1
    };

    /**
     * Returns true if the given board field is currently attacked by any
     * piece of {@code attackerColor}. The field need not contain a piece;
     * this is a pure square-attack query and short-circuits on the first
     * attacker found.
     *
     * @param field         field index in the 12×12 raw board layout
     * @param attackerColor either {@link GameStatus#TURN_WHITE} or
     *                      {@link GameStatus#TURN_BLACK}
     */
    public boolean isFieldAttackedBy(int field, int attackerColor) {
        final boolean attackerIsWhite = attackerColor == GameStatus.TURN_WHITE;

        // Pawn attacks. A white pawn attacks up-diagonally, so attackers of
        // 'field' sit one rank below 'field' on either diagonal; conversely
        // for black. Off-board source squares land on the illegal-border ring,
        // which never equals whitePawn/blackPawn — no bounds check needed.
        if (attackerIsWhite) {
            if (board[field - LENGTH - 1] == whitePawn || board[field - LENGTH + 1] == whitePawn) {
                return true;
            }
        } else if (board[field + LENGTH - 1] == blackPawn || board[field + LENGTH + 1] == blackPawn) {
            return true;
        }

        final byte attackerKnight = attackerIsWhite ? whiteKnight : blackKnight;
        for (int off : KNIGHT_OFFSETS) {
            if (board[field + off] == attackerKnight) {
                return true;
            }
        }

        // King adjacency. Two kings can never be legally adjacent, but during
        // pseudo-legal search a king move into the opposing king's reach is a
        // self-check that must be detected.
        final byte attackerKing = attackerIsWhite ? whiteKing : blackKing;
        for (int off : KING_ADJACENCY_OFFSETS) {
            if (board[field + off] == attackerKing) {
                return true;
            }
        }

        final byte attackerBishop = attackerIsWhite ? whiteBishop : blackBishop;
        final byte attackerQueen = attackerIsWhite ? whiteQueen : blackQueen;
        for (int dir : DIAGONAL_RAY_DIRS) {
            int to = field + dir;
            while (board[to] == empty) {
                to += dir;
            }
            // First non-empty square along the ray: either an attacker, a
            // blocker (any other piece), or the illegal-border byte. Only an
            // attacking bishop / queen of the right color counts as a hit.
            final byte piece = board[to];
            if (piece == attackerBishop || piece == attackerQueen) {
                return true;
            }
        }

        final byte attackerRook = attackerIsWhite ? whiteRook : blackRook;
        for (int dir : ORTHOGONAL_RAY_DIRS) {
            int to = field + dir;
            while (board[to] == empty) {
                to += dir;
            }
            final byte piece = board[to];
            if (piece == attackerRook || piece == attackerQueen) {
                return true;
            }
        }

        return false;
    }

    /** Returns true if the side to move's king is currently in check. */
    public boolean isKingChecked() {
        final int myColor = getGameStatus().getTurn();
        final byte myKing = (myColor == GameStatus.TURN_WHITE) ? whiteKing : blackKing;
        final int enemyColor = getGameStatus().getOppositeColor();
        return isFieldAttackedBy(findKingField(myKing), enemyColor);
    }

    /**
     * Returns true if the side to move could legally capture the opposing
     * king on its next move — equivalently, if the previous move was an
     * illegal self-check. Intended as a fast legality probe at search leaves,
     * where no full move list is otherwise needed.
     */
    public boolean canCaptureOpposingKing() {
        final int myColor = getGameStatus().getTurn();
        final int enemyColor = getGameStatus().getOppositeColor();
        final byte enemyKing = (enemyColor == GameStatus.TURN_WHITE) ? whiteKing : blackKing;
        return isFieldAttackedBy(findKingField(enemyKing), myColor);
    }

    public int findKingField(byte king) {
        for (int field = a1; field <= h8; field++) {
            if (board[field] == king) {
                return field;
            }
        }
        throw new IllegalStateException("King not found on board: " + king);
    }

    public boolean isCheckmate(MoveGenerator moveGenerator) {
        if (!isKingChecked()) {
            return false;
        }

        Moves nextMoves = moveGenerator.calculateMoves(this);
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
            Moves nextMoves2 = moveGenerator.calculateMoves(this);
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

        if (moveDescr.isCastlingKingSide()) {
            builder.setFlag(MoveFlag.CASTLING_KING_SIDE, true);
        }
        if (moveDescr.isCastlingQueenSide()) {
            builder.setFlag(MoveFlag.CASTLING_QUEEN_SIDE, true);
        }

        if (builder.fromCol < 0 || builder.fromRow < 0) {
            // Must resolve source field
            if (builder.piece <= 0) {
                throw new IllegalMoveException("Cannot resolve move without piece information");
            }
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
                    Moves nextMoves = moveGenerator.calculateMoves(workingBoard);
                    workingBoard.revertMove();
                    return nextMoves.isIllegal();
                });
            }
            if (possibleMoves.isEmpty()) {
                throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Impossible move.");
            }
            if (possibleMoves.size() > 1) {
                // If multiple legal moves share the same from/to and differ only in moveType, prefer the typeNormal interpretation.
                // Castle has dedicated notations (O-O / king-to-rook); any explicit king-to-target notation means the plain king step.
                possibleMoves.removeIf(move ->
                        Move.getMoveType(move) == Move.typeCastlingKingSide || Move.getMoveType(move) == Move.typeCastlingQueenSide);
                if (possibleMoves.size() != 1) {
                    throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Move is not unique.");
                }
            }

            int move = possibleMoves.iterator().next();
            builder.fromCol = Move.getFromCol(move);
            builder.fromRow = Move.getFromRow(move);
        }

        builder.piece = get(ChessUtil.colAndRowToField(builder.fromCol, builder.fromRow)); // set or overwrite piece info (it may be set with pawn as default)

        if (isChess960()) {
            resolve960MoveDescription(builder);
        }

        return builder.build();
    }

    @SuppressWarnings("java:S1066")
    private void resolve960MoveDescription(Builder builder) {
        final int fromField = ChessUtil.colAndRowToField(builder.fromCol, builder.fromRow);
        final int toField = ChessUtil.colAndRowToField(builder.toCol, builder.toRow);
        final byte piece = get(fromField);
        final byte capturedPiece = get(toField);
        final boolean isWhiteTurn = builder.turn == GameStatus.TURN_WHITE;

        // Check for Chess960 castling moves
        if ((builder.hasFlag(MoveFlag.CASTLING_KING_SIDE) || builder.hasFlag(MoveFlag.CASTLING_QUEEN_SIDE)) && !isKing(piece)) {
            if (isWhiteTurn) {
                builder.fromCol = ChessUtil.findColOfPieceOnRow(board, Board.whiteKing, 0);
            } else {
                builder.fromCol = ChessUtil.findColOfPieceOnRow(board, Board.blackKing, 7);
            }
        }
        if (piece == Board.whiteKing && ChessUtil.getRowOfField(fromField) == 0) {
            if (capturedPiece == Board.whiteRook && ChessUtil.getColOfField(toField) == getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE)) {
                builder.setFlag(MoveFlag.CASTLING_KING_SIDE, true);
                builder.toCol = 6;
            } else if (capturedPiece == Board.whiteRook && ChessUtil.getColOfField(toField) == getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE)) {
                builder.setFlag(MoveFlag.CASTLING_QUEEN_SIDE, true);
                builder.toCol = 2;
            } else if (toField == Board.g1) {
                if (fromField != Board.f1 && fromField != Board.h1) {
                    builder.setFlag(MoveFlag.CASTLING_KING_SIDE, true);
                }
            } else if (toField == Board.c1) {
                if (fromField != Board.b1 && fromField != Board.d1) {
                    builder.setFlag(MoveFlag.CASTLING_QUEEN_SIDE, true);
                }
            }
        } else if (piece == Board.blackKing && ChessUtil.getRowOfField(fromField) == 7) {
            if (capturedPiece == Board.blackRook && ChessUtil.getColOfField(toField) == getCastlingRookFile(CastlingSlot.BLACK_KINGSIDE)) {
                builder.setFlag(MoveFlag.CASTLING_KING_SIDE, true);
                builder.toCol = 6;
            } else if (capturedPiece == Board.blackRook && ChessUtil.getColOfField(toField) == getCastlingRookFile(CastlingSlot.BLACK_QUEENSIDE)) {
                builder.setFlag(MoveFlag.CASTLING_QUEEN_SIDE, true);
                builder.toCol = 2;
            } else if (toField == Board.g8) {
                if (fromField != Board.f8 && fromField != Board.h8) {
                    builder.setFlag(MoveFlag.CASTLING_KING_SIDE, true);
                }
            } else if (toField == Board.c8) {
                if (fromField != Board.b8 && fromField != Board.d8) {
                    builder.setFlag(MoveFlag.CASTLING_QUEEN_SIDE, true);
                }
            }
        }
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
        byte moveType = -1;
        byte promotionPiece = moveDescr.pawnPromotionPiece();

        if (moveDescr.isCastlingKingSide()) {
            moveType = Move.typeCastlingKingSide;
        }
        if (moveDescr.isCastlingQueenSide()) {
            moveType = Move.typeCastlingQueenSide;
        }

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

        if (moveType < 0) {
            moveType = Move.typeNormal;
        }

        if (moveType == Move.typeCastlingKingSide || moveType == Move.typeCastlingQueenSide) {
            capturedPiece = 0;
        }

        return new Move(Move.create((byte) fromField, (byte) toField, capturedPiece, moveType));
    }

    public MoveDescription moveToShortNotation(Move move) {
        var builder = new MoveDescription.Builder(getGameStatus().getTurn());
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());

        makeMove(move.move());
        builder.setFlag(MoveFlag.CHECKMATE, isCheckmate(moveGenerator));
        builder.setFlag(MoveFlag.CHECK, isKingChecked());
        revertMove();

        builder.piece = get(move.getFromField());
        builder.toCol = move.getToCol();
        builder.toRow = move.getToRow();
        if (move.getCapturedPiece() != Board.empty) {
            builder.flags.add(MoveFlag.CAPTURE);
            if (Board.isPawn(builder.piece)) {
                builder.fromCol = move.getFromCol();
            }
        }

        var pawnPromotionPiece = move.getPawnPromotionPiece();
        if (pawnPromotionPiece > 0) {
            builder.pawnPromotionPiece = pawnPromotionPiece;
        }

        var moveType = Move.getMoveType(move.move());
        builder.setFlag(MoveFlag.CASTLING_KING_SIDE, moveType == Move.typeCastlingKingSide);
        builder.setFlag(MoveFlag.CASTLING_QUEEN_SIDE, moveType == Move.typeCastlingQueenSide);

        try {
            var moveDescr = builder.build();
            resolveMoveDescription(moveDescr, moveGenerator);
            return moveDescr;
        } catch (RuntimeException _) {
            // fall through
        }

        if (builder.fromCol == -1) {
            builder.fromCol = move.getFromCol();
            try {
                var moveDescr = builder.build();
                resolveMoveDescription(moveDescr, moveGenerator);
                return moveDescr;
            } catch (RuntimeException _) {
                // fall through
            }
            builder.fromCol = -1;
            builder.fromRow = move.getFromRow();
            try {
                var moveDescr = builder.build();
                resolveMoveDescription(moveDescr, moveGenerator);
                return moveDescr;
            } catch (RuntimeException _) {
                // fall through
            }
        }

        builder.fromCol = move.getFromCol();
        builder.fromRow = move.getFromRow();
        var moveDescr = builder.build();
        resolveMoveDescription(moveDescr, moveGenerator);
        return moveDescr;
    }

    /**
     * Returns {@code true} if the given board represents a Chess960
     * (Fischer Random) position, {@code false} for standard chess.
     *
     * <p>The detector works in three stages, in order of decreasing
     * cheapness and decreasing decisiveness:
     *
     * <ol>
     *   <li><b>Rook-file check.</b> If either of the white castling-rook
     *       starting files in {@link Board#getCastlingRookFile} deviates
     *       from the standard-chess defaults ({@code a} for queenside,
     *       {@code h} for kingside), the position is 960. Catches the
     *       vast majority of Scharnagl positions immediately.</li>
     *   <li><b>King-file check.</b> If a side still has a castling right
     *       alive but its king does not sit on the {@code e}-file, the
     *       position is 960. Catches the remainder of Scharnagl positions
     *       whose rook files happen to match standard chess but whose
     *       king sits elsewhere.</li>
     *   <li><b>Structural fallback.</b> If both fast checks fail and the
     *       board still looks like a starting position (pawns on the
     *       second/seventh rank, non-pawns on the back ranks, all other
     *       squares empty), the position is 960 iff its back-rank
     *       arrangement differs from standard chess. Catches the small
     *       set of Scharnagl positions (e.g. ID 414, {@code RQNNKBBR})
     *       with both rook files at {@code a}/{@code h} and king on
     *       {@code e}.</li>
     * </ol>
     *
     * <p>Once any of the three stages returns a verdict the result is
     * cached on the {@link Game} for the rest of its life-cycle — a
     * game's variant identity does not change mid-play.
     *
     * <p><b>Known limitation, intentional.</b> A 960 game with rook files
     * {@code {0, 7}} and king on {@code e1}/{@code e8} that has already
     * left the starting position (pawns advanced, pieces developed) will
     * be classified as standard chess by this detector. This is not a
     * defect: such a position is rules-equivalent to standard chess in
     * every relevant aspect (castling targets {@code g1}/{@code c1}
     * resp. {@code g8}/{@code c8}, identical path squares, the same
     * X-FEN castling-bit semantics, the same UCI move encodings), so
     * playing it as standard chess produces correct moves and a
     * correct FEN. The detector intentionally does not carry around
     * the original starting-board metadata that would be needed to
     * preserve the 960-flag past the opening.
     */
    @SuppressWarnings("java:S1066")
    boolean isChess960Position() {
        var gameStatus = getGameStatus();

        // If the rook's start fields are non-standard, it's obviously a chess960 position
        if (getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE) != 0
                || getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE) != 7) {
            return true; // Rooks start on non-standard fields ==> 960
        }

        // If either side can still castle, but the king's position is non-standard, it's obviously a chess960 position
        if (gameStatus.isWhiteCastlingQueenSidePossible() || gameStatus.isWhiteCastlingKingSidePossible()) {
            if (ChessUtil.findColOfPieceOnRow(getRawBoard(), Board.whiteKing, 0) != 4) {
                return true; // White king starts on non-standard field ==> 960
            }
        }

        if (gameStatus.isBlackCastlingQueenSidePossible() || gameStatus.isBlackCastlingKingSidePossible()) {
            if (ChessUtil.findColOfPieceOnRow(getRawBoard(), Board.blackKing, 7) != 4) {
                return true; // Black king starts on non-standard field ==> 960
            }
        }

        // Now we can only guess...

        if (!seemsToBeStartPosition()) {
            return false;
        }

        return !isStandardStartPosition();
    }

    private boolean seemsToBeStartPosition() {
        // All pawns still on second row?
        if (!(allPawnsOnStartPos(Board.whitePawn, 1) && allPawnsOnStartPos(Board.blackPawn, 6))) {
            return false;
        }

        // Other pieces still on backrow?
        if (!(allNonPawnsOnStartPos(GameStatus.TURN_WHITE, 0) && allNonPawnsOnStartPos(GameStatus.TURN_BLACK, 7))) {
            return false;
        }

        // Remaining fields must all be empty
        return allNonStartFieldsEmpty();
    }

    private boolean allNonPawnsOnStartPos(int color, int row) {
        for (int col = 0; col < 8; col++) {
            byte piece = getPieceAt(col, row);
            if (Board.isPawn(piece)) {
                return false;
            }
            if ((piece & color) != color) {
                return false;
            }
        }

        return true;
    }

    private boolean allPawnsOnStartPos(byte pawnPiece, int row) {
        for (int col = 0; col < 8; col++) {
            if (getPieceAt(col, row) != pawnPiece) {
                return false;
            }
        }

        return true;
    }

    private boolean allNonStartFieldsEmpty() {
        final byte[] rawBoard = getRawBoard();

        for (int i = Board.a3; i <= Board.h6; i++) {
            if (rawBoard[i] != Board.illegal && rawBoard[i] != Board.empty) {
                return false;
            }
        }

        return true;
    }

    boolean isStandardStartPosition() {
        final byte[] rawBoard = getRawBoard();
        final byte[] standardRawBoard = Board.createNewGame().getRawBoard();

        for (int i = 0; i < rawBoard.length; i++) {
            if (rawBoard[i] != standardRawBoard[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * From-scratch sum of non-pawn material per side, in centipawns.
     * Returns a two-element array where index 0 is White and index 1
     * is Black. Pawns are excluded from the count; kings weigh 0 by
     * convention, so they do not affect the totals either. Empty and
     * off-board squares are skipped.
     *
     * <p>Used at boundary points where the incremental counter on
     * {@link GameStatus} needs to be seeded or verified — FEN import,
     * position encoding, the sanity check in the {@code Board}
     * constructor. Not called on the hot search path; the search
     * consults {@link GameStatus#getWhiteNonPawnMaterialWeight()} /
     * {@link GameStatus#getBlackNonPawnMaterialWeight()} instead,
     * which are maintained incrementally by
     * {@link GameStatus#switchTurn(int, int, int, byte, long)}.
     *
     * <p>The invariant every test in {@code NonPawnMaterialWeightTest}
     * defends: at every point in a game, this recomputation from the
     * current raw board must equal the incrementally-tracked pair on
     * the current {@link GameStatus}.
     */
    public static int[] calculateNonPawnMaterialWeights(byte[] rawBoard) {
        final int[] materialWeights = new int[2];
        final int stopField = Board.h8 + 1;

        for (int field = Board.a1; field < stopField; field++) {
            final byte piece = rawBoard[field];
            if (piece != Board.empty && piece != Board.illegal && !Board.isPawn(piece)) {
                final int color = (piece & GameStatus.TURN_WHITE) == GameStatus.TURN_WHITE ? 0 : 1;
                materialWeights[color] += WeightingFunction.weightOfPiece[piece];
            }
        }

        return materialWeights;
    }
}
