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
            board.print();
            System.out.println(exportMoves());
            throw e;
        }
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
        if (move.path.length > 0 && move.path[0] != 0) {
            // at least one move still possible ==> ongoing
            return GameResult.ONGOING;
        } else {
            return move.result;
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
        return board.exportFEN();
    }

    public int getTurn() {
        return getGameStatus().getTurn();
    }

    void makeMove(MoveDescription moveDescr) {
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());

        if (getResult() != GameResult.ONGOING) {
            board.print();
            System.out.println(exportMoves());
            System.out.println("Current move: " + moveDescr);
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
        if (moveDescr.isCapture != null && moveDescr.isCapture && capturedPiece == Board.empty) {
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
            verifyMove(moveDescr, moveGenerator);

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

    private void verifyMove(MoveDescription moveDescr, MoveGenerator moveGenerator) {
        // Verify isCheck
        if (moveDescr.isCheck != null && moveDescr.isCheck && !board.isKingChecked(moveGenerator)) {
            throw new IllegalMoveException("Wrong move notation: " + moveDescr + "+. Move does not give check.");
        }

        // Verify isCheckmate
        if (moveDescr.isCheckmate != null && moveDescr.isCheckmate && getResult() != GameResult.CHECKMATE) {
            throw new IllegalMoveException("Wrong move notation: " + moveDescr + "+. Move does not set checkmate.");
        }

        // Verify pawn promotion
        if (moveDescr.pawnPromotionPiece > 0) {
            if (getGameStatus().getTurn() == GameStatus.TURN_BLACK) {
                if (moveDescr.piece != Board.whitePawn
                        || board.get(moveDescr.getToField()) != moveDescr.pawnPromotionPiece
                        || moveDescr.toRow != 7) {
                    throw new IllegalMoveException("Wrong move notation: " + moveDescr + "+. Not a pawn promotion.");
                }
            } else {
                if (moveDescr.piece != Board.blackPawn
                        || board.get(moveDescr.getToField()) != moveDescr.pawnPromotionPiece
                        || moveDescr.toRow != 0) {
                    board.print();
                    throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Not a pawn promotion.");
                }
            }
        }

        // TODO: Verify isEnPassant
    }

    void makeMove(MoveAndWeight move) {
        board.validateMove(move.move);
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
                + ", castling: " + Fen.castlingState(gameStatus));
        if (getResult() == GameResult.CHECKMATE || getResult() == GameResult.STALEMATE)
            System.out.println("Result: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black") + " " + getResult());
        else if (getResult() == GameResult.DRAW)
            System.out.println("Result: DRAW");
        else
            System.out.println("Turn: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black"));
    }
}
