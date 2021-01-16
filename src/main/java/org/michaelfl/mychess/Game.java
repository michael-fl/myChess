package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.v1.MyChessEngine1;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private final Board board = Board.createNewGame();
    private GameResult result = GameResult.ONGOING;

    static GameConfig standardConfig() {
        return new GameConfig(
                MyChessEngine.class, new EngineConfig.Builder().maxDepth(8).build(),
                MyChessEngine1.class, new EngineConfig.Builder().maxDepth(14).iterationDepth(6).variants(4).build());
    }

    Game() {
        this(standardConfig());
    }

    Game(GameConfig config) {
        engineWhite = config.createEngineWhite(this);
        engineBlack = config.createEngineBlack(this);
    }

    Game(GameConfig config, List<MoveDescription> moves) {
        this(config);

        for (MoveDescription move : moves) {
            makeMove(move);
        }

        // Check if game is over
        calculateAndSetGameResult();
    }

    ChessEngine getEngine() {
        return getTurn() == GameStatus.TURN_WHITE ? engineWhite : engineBlack;
    }

    public void calculateAndSetGameResult() {
        MoveGenerator moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        GameResult gameResult = checkGameResult(moveGenerator);
        setResult(gameResult);
    }

    public GameStatus getGameStatus() {
        return getBoard().getGameStatus();
    }

    public GameResult getResult() {
        return result;
    }

    private void setResult(GameResult result) {
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
            buf.append(new Move(gameStatus.getLastMove()).toString());
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

    int getOppositeColor() {
        return getGameStatus().getOppositeColor();
    }

    void makeMove(MoveDescription moveDescr) {
        if (getResult() != GameResult.ONGOING) {
            System.err.println("Game is already over");
            return;
        }

        int fromField = moveDescr.getFromField();
        int toField = moveDescr.getToField();
        byte piece = board.get(fromField);
        byte capturedPiece = board.get(toField);
        byte moveType = Move.typeNormal;
        char promotionSymbol = moveDescr.getPawnPromotionSymbol();

        if ('Q' == promotionSymbol)
            moveType = Move.typePawnPromotionQueen;
        else if ('N' == promotionSymbol)
            moveType = Move.typePawnPromotionKnight;
        else if ('R' == promotionSymbol)
            moveType = Move.typePawnPromotionRook;
        else if ('B' == promotionSymbol)
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

        Move move = new Move(Move.create((byte) fromField, (byte) toField, capturedPiece, moveType));

        // Validate the move
        MoveGenerator moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        Moves validMoves = moveGenerator.calculateMoves(board);
        if (!validMoves.contains(move.getMove())) {
            print();
            System.out.println("Valid moves: " + validMoves);
            throw new IllegalStateException("Illegal move: " + move);
        }

        makeMove(move.getMove());

        calculateAndSetGameResult();
    }

    void makeMove(MoveAndWeight move) {
        makeMove(move.move);
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

    private GameResult checkGameResult(MoveGenerator moveGenerator) {
        GameResult gameResult = checkCheckMateOrStaleMate(getBoard(), moveGenerator);
        if (gameResult == GameResult.ONGOING) {
            if (getGameStatus().getHalfMoveClock() >= 100 || getBoard().isDrawByMaterial()) {
                gameResult = GameResult.DRAW;
            }
        }

        return gameResult;
    }

    private static GameResult checkCheckMateOrStaleMate(Board board, MoveGenerator moveGenerator) {
        // Check the next theoretically possible moves
        Moves nextMoves = moveGenerator.calculateMoves(board);
        if (nextMoves.isIllegal())
            throw new IllegalArgumentException("Illegal chess position");

        // Test each of those moves and try to find a valid one
        boolean haveValidMove = false;
        final int nPossibleMoves = nextMoves.count();
        final Board workingBoard = board.copy();

        for (int i = 0; i < nPossibleMoves; i++) {
            final int nextMove = nextMoves.getMove(i);
            workingBoard.makeMove(nextMove);
            Moves nextNextMoves = moveGenerator.calculateMoves(workingBoard);
            if (!nextNextMoves.isIllegal()) {
                haveValidMove = true;
                break;
            }
            workingBoard.revertMove();
        }

        if (haveValidMove)
            return GameResult.ONGOING;

        // No valid move possible ==> Check if it is checkmate or stalemate
        return checkIsKingUnderChess(board, moveGenerator) ? GameResult.CHECKMATE : GameResult.STALEMATE;
    }

    public static boolean checkIsKingUnderChess(Board board, MoveGenerator moveGenerator) {
        // TODO MF: Optimize method checkIsKingUnderChess
        // Switch turn
        GameStatus gameStatus = board.getGameStatus().switchTurn();

        // Check the next theoretically possible moves. If those contain an illegal move (king can be captured),
        // the king was under chess.
        // TODO MF: Calculate moves without sorting
        Moves nextMoves = moveGenerator.calculateMoves(gameStatus, board, 0, 0);
        return nextMoves.isIllegal();
    }

    void playAutoGame() {
        try {
            if (getResult() == GameResult.ONGOING) {
                playAutoGameInternal();
            }

            getBoard().print();
            int turn = getTurn();
            System.out.println("Moves: " + exportMoves());
            System.out.println("Turn: " + (turn == GameStatus.TURN_WHITE ? "white" : "black"));
            if (getResult() == GameResult.CHECKMATE || getResult() == GameResult.STALEMATE)
                System.out.println("Result: " + (turn == GameStatus.TURN_WHITE ? "white" : "black") + " " + getResult());
            else
                System.out.println("Result: " + getResult());

        } catch (Throwable e) {
            e.printStackTrace();

            getBoard().print();
            System.out.println("Turn: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black"));
            System.out.println("Moves: " + exportMoves());
            MoveGenerator moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
            Moves possibleMoves = moveGenerator.calculateMoves(getBoard());
            System.out.println("Possible moves: " + possibleMoves);
            System.out.flush();
            System.err.println("ERROR: " + e);
        }
    }

    @SuppressWarnings("Duplicates")
    private void playAutoGameInternal() throws InterruptedException, ExecutionException, TimeoutException {
        getBoard().print();

        for (int i = 0; i < 1000 && getResult() == GameResult.ONGOING; i++) {
            MoveAndWeight move = getEngine().nextMoveAsync().getResult(1, TimeUnit.HOURS);
            if (move == MoveAndWeight.NO_MOVE) {
                // No valid move possible ==> checkmate or stalemate
                break;
            }
            makeMove(move);
            getBoard().print();
            System.out.println("Move #" + ((getGameStatus().getPlyCount() + 1) / 2) + ": " + ChessUtil.moveToString(move.move));
            System.out.println("FEN: " + exportFEN());

            if (move.path.length <= 1 || move.path[1] == 0) {
                calculateAndSetGameResult();
            }
        }

        GameResult gameResult = getResult();
        if (gameResult == GameResult.ONGOING) {
            gameResult = GameResult.DRAW;
        }
        setResult(gameResult);
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
