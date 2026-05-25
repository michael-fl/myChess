package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.NextMoveTask;

import java.util.List;

/**
 * Top-level chess game: a {@link Board} plus the two side engines and a status
 * engine that detects checkmate/stalemate after every successful move. Provides
 * {@link #makeMove}/{@link #revertMove} with automatic rollback on validation
 * failures and exposes the asynchronous engine entry points via
 * {@link #getEngine()}.
 *
 * @author Michael Fleischhauer
 */
public final class Game {

    public enum GameResult {
        CHECKMATE,
        STALEMATE,
        DRAW,
        ONGOING;

        public boolean isDraw() {
            return this == DRAW || this == STALEMATE;
        }
    }

    private final ChessEngine engineWhite;
    private final ChessEngine engineBlack;
    private final ChessEngine statusEngine;

    private final Board board;
    private final boolean is960;

    private GameResult result = GameResult.ONGOING;

    static GameConfig standardConfig() {
        return new GameConfig(
                MyChessEngine.class, new EngineConfig.Builder().build()
        );
    }

    Game() {
        this(standardConfig());
    }

    Game(GameConfig config) {
        this(config, Board.createNewGame());
    }

    /**
     * Construct a game whose board is the given pre-built {@link Board}.
     * Used by {@link Fen#importFEN(String)} and the REPL/UCI {@code position fen}
     * commands to install an arbitrary starting position.
     */
    Game(GameConfig config, Board initialBoard) {
        this.board = initialBoard;
        engineWhite = config.createEngineWhite(this);
        engineBlack = config.createEngineBlack(this);
        statusEngine = new MyChessEngine(
                new EngineConfig.Builder()
                        .maxDepth(2)
                        .enableThreefoldRepetition(engineWhite.getConfig().isEnableThreefoldRepetition())
                        .enableFiftyMovesRule(engineWhite.getConfig().isEnableFiftyMovesRule())
                        .silent(true)
                        .build(), this);
        is960 = isChess960Position(initialBoard);
    }

    Game(GameConfig config, List<MoveDescription> moves) {
        this(config);

        try {
            for (MoveDescription move : moves) {
                makeMove(move);
            }

            // OPT MF: Expensive hotspot method!
            // Check if game is over
            calculateAndSetGameResult();

        } catch (RuntimeException e) {
            Log.error("Game construction failed during move replay\n" + board + "\n" + exportMoves(), e);
            throw e;
        }
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
    static boolean isChess960Position(Board board) {
        var gameStatus = board.getGameStatus();

        // If the rook's start fields are non-standard, it's obviously a chess960 position
        if (board.getCastlingRookFile(CastlingSlot.WHITE_QUEENSIDE) != 0
                || board.getCastlingRookFile(CastlingSlot.WHITE_KINGSIDE) != 7) {
            return true; // Rooks start on non-standard fields ==> 960
        }

        // If either side can still castle, but the king's position is non-standard, it's obviously a chess960 position
        if (gameStatus.isWhiteCastlingQueenSidePossible() || gameStatus.isWhiteCastlingKingSidePossible()) {
            if (ChessUtil.findColOfPieceOnRow(board, Board.whiteKing, 0) != 4) {
                return true; // White king starts on non-standard field ==> 960
            }
        }

        if (gameStatus.isBlackCastlingQueenSidePossible() || gameStatus.isBlackCastlingKingSidePossible()) {
            if (ChessUtil.findColOfPieceOnRow(board, Board.blackKing, 7) != 4) {
                return true; // Black king starts on non-standard field ==> 960
            }
        }

        // Now we can only guess...

        if (!seemsToBeStartPosition(board)) {
            return false;
        }

        return !isStandardStartPosition(board);
    }

    private static boolean seemsToBeStartPosition(Board board) {
        // All pawns still on second row?
        if (!(allPawnsOnStartPos(board, Board.whitePawn, 1) && allPawnsOnStartPos(board, Board.blackPawn, 6))) {
            return false;
        }

        // Other pieces still on backrow?
        if (!(allNonPawnsOnStartPos(board, GameStatus.TURN_WHITE, 0) && allNonPawnsOnStartPos(board, GameStatus.TURN_BLACK, 7))) {
            return false;
        }

        // Remaining fields must all be empty
        return allNonStartFieldsEmpty(board);
    }

    private static boolean allNonPawnsOnStartPos(Board board, int color, int row) {
        for (int col = 0; col < 8; col++) {
            byte piece = board.getPieceAt(col, row);
            if (Board.isPawn(piece)) {
                return false;
            }
            if ((piece & color) != color) {
                return false;
            }
        }

        return true;
    }

    private static boolean allPawnsOnStartPos(Board board, byte pawnPiece, int row) {
        for (int col = 0; col < 8; col++) {
            if (board.getPieceAt(col, row) != pawnPiece) {
                return false;
            }
        }

        return true;
    }

    private static boolean allNonStartFieldsEmpty(Board board) {
        final byte[] rawBoard = board.getRawBoard();
        final int startField = ChessUtil.getFieldFromColAndRow(0, 2);
        final int endField = ChessUtil.getFieldFromColAndRow(7, 5);

        for (int i = startField; i <= endField; i++) {
            if (rawBoard[i] != Board.illegal && rawBoard[i] != Board.empty) {
                return false;
            }
        }

        return true;
    }

    static boolean isStandardStartPosition(Board board) {
        final byte[] rawBoard = board.getRawBoard();
        final byte[] standardRawBoard = Board.createNewGame().getRawBoard();

        for (int i = 0; i < rawBoard.length; i++) {
            if (rawBoard[i] != standardRawBoard[i]) {
                return false;
            }
        }

        return true;
    }

    public static Game new960() {
        var board = Fen.importFEN(Chess960StartPositions.randomFen());

        return new Game(standardConfig(), board);
    }

    boolean is960() {
        return is960;
    }

    void shutdown() {
        engineBlack.shutdown();
        engineWhite.shutdown();
    }

    ChessEngine getEngine() {
        return getTurn() == GameStatus.TURN_WHITE ? engineWhite : engineBlack;
    }

    private GameResult calculateGameResult() {
        MoveAndWeight move = statusEngine.calculateNextMove(new NextMoveTask());
        if (move.path().length > 0 && move.path()[0] != 0) {
            // at least one move still possible ==> ongoing
            return GameResult.ONGOING;
        } else {
            return move.result();
        }
    }

    public void calculateAndSetGameResult() {
        setResult(calculateGameResult());
    }

    public GameStatus getGameStatus() {
        return getBoard().getGameStatus();
    }

    public GameResult getResult() {
        return result;
    }

    void setResult(GameResult result) {
        this.result = result;
    }

    public Board getBoard() {
        return board;
    }

    int getMoveCount() {
        return (getGameStatus().getPlyCount() + 1) / 2;
    }

    String exportMoves() {
        StringBuilder buf = new StringBuilder("[[");

        List<GameStatus> statusStack = board.getGameStatusStackCopy();
        for (GameStatus gameStatus : statusStack.subList(1, statusStack.size())) {
            if (buf.length() > 2)
                buf.append(' ');
            buf.append(new Move(gameStatus.getLastMove()));
        }

        buf.append("]]");
        return buf.toString();
    }

    public String exportFEN() {
        return is960() ? board.exportShredderFEN() : board.exportFEN();
    }

    public int getTurn() {
        return getGameStatus().getTurn();
    }

    void makeMove(MoveDescription moveDescr) {
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());

        if (getResult() != GameResult.ONGOING) {
            Log.error("Move on finished game: " + moveDescr
                    + " | result=" + getResult()
                    + "\n" + board
                    + "\nhistory=" + exportMoves());
            throw new IllegalStateException("Game is already over. State is " + getResult());
        }

        moveDescr = board.resolveMoveDescription(moveDescr, moveGenerator);
        makeMoveResolved(moveDescr, moveGenerator);
    }

    private void makeMoveResolved(MoveDescription moveDescr, MoveGenerator moveGenerator) {
        var move = board.moveDescriptionToMove(moveDescr);

        var moveType = move.getMoveType();
        var capturedPiece = move.getCapturedPiece();

        // Verify isCapture
        if (moveDescr.isCapture() && capturedPiece == Board.empty) {
            throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Move does not capture any piece.");
        }

        // Validate the move
        Moves validMoves = moveGenerator.calculateMoves(board);
        Move moveToValidate = move;
        if (moveType == Move.typePawnPromotionBishop || moveType == Move.typePawnPromotionRook) {
            moveToValidate = new Move(Move.create(move.getFromField(), move.getToField(), capturedPiece, Move.typePawnPromotionQueen));
        }
        if (!validMoves.contains(moveToValidate.move())) {
            throw new IllegalMoveException(moveDescr);
        }

        makeMove(move.move());

        try {
            calculateAndSetGameResult();
            verifyMove(moveDescr);

        } catch (IllegalMoveException e) { // move was illegal
            revertMove();
            throw e;
        } catch (IllegalChessPositionException _) { // move was illegal
            revertMove();
            throw new IllegalMoveException(moveDescr);
        }
    }

    void makeMove(Move move) {
        makeMove(move.move());
        calculateAndSetGameResult();
    }

    private void verifyMove(MoveDescription moveDescr) {
        // Verify isCheck
        if (moveDescr.isCheck() && !board.isKingChecked()) {
            throw new IllegalMoveException("Wrong move notation: " + moveDescr + "+. Move does not give check.");
        }

        // Verify isCheckmate
        if (moveDescr.isCheckmate() && getResult() != GameResult.CHECKMATE) {
            throw new IllegalMoveException("Wrong move notation: " + moveDescr + "+. Move does not set checkmate.");
        }

        // Verify pawn promotion
        if (moveDescr.pawnPromotionPiece() > 0) {
            if (getGameStatus().getTurn() == GameStatus.TURN_BLACK) {
                if (moveDescr.piece() != Board.whitePawn
                        || board.get(moveDescr.getToField()) != moveDescr.pawnPromotionPiece()
                        || moveDescr.toRow() != 7) {
                    throw new IllegalMoveException("Wrong move notation: " + moveDescr + "+. Not a pawn promotion.");
                }
            } else {
                if (moveDescr.piece() != Board.blackPawn
                        || board.get(moveDescr.getToField()) != moveDescr.pawnPromotionPiece()
                        || moveDescr.toRow() != 0) {
                    Log.error("Bogus promotion notation: " + moveDescr + "\n" + board);
                    throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Not a pawn promotion.");
                }
            }
        }

        // TODO: Verify isEnPassant
    }

    void makeMove(MoveAndWeight move) {
        board.validateMove(move.move());
        board.makeMove(move);
    }

    private void makeMove(int move) {
        board.validateMove(move);
        board.makeMove(move);
    }

    void revertMove() {
        if (getGameStatus().getPlyCount() == 0)
            throw new IllegalStateException("No move to revert");

        board.revertMove();
        result = GameResult.ONGOING;
    }

    void print() {
        getBoard().print();
        var gameStatus = getGameStatus();
        System.out.println("Moves: " + getMoveCount()
                + ", halfMoveClock: " + gameStatus.getHalfMoveClock()
                + ", castling: " + (is960() ?
                        Fen.castlingStateShredder(gameStatus, getBoard()) :
                        Fen.castlingState(gameStatus))
                + (is960() ? ", 960" : ", standard")
        );
        if (getResult() == GameResult.CHECKMATE || getResult() == GameResult.STALEMATE)
            System.out.println("Result: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black") + " " + getResult());
        else if (getResult() == GameResult.DRAW)
            System.out.println("Result: DRAW");
        else
            System.out.println("Turn: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black"));
    }
}
