package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.v1.MyChessEngine1;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Game {

    public enum GameResult {
        CHECKMATE,
        STALEMATE,
        DRAW,
        ONGOING
    }

    private final Random rand = new Random();
    private final ChessEngine engineWhite;
    private final ChessEngine engineBlack;
    private Board previousBoard;
    private final Board board = Board.createNewGame();
    private final List<Move> moves = new ArrayList<>();
    private final List<GameStatus> statusStack = new ArrayList<>();
    private GameResult result = GameResult.ONGOING;
    private Float weight;

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

        statusStack.add(GameStatus.newGame());
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

    GameResult calculateAndSetGameResult() {
        MoveGenerator moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
        GameResult gameResult = checkGameResult(moveGenerator);
        setResult(gameResult);
        return gameResult;
    }

    public Float getWeight() {
        return weight;
    }

    private void setWeight(float weight) {
        this.weight = weight;
    }

    public GameStatus getGameStatus() {
        return statusStack.get(statusStack.size() - 1);
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

    Board getPreviousBoard() {
        return previousBoard;
    }

    List<Move> getMoves() {
        return moves;
    }

    int getMoveCount() {
        return moves.size();
    }

    String exportMoves() {
        StringBuilder buf = new StringBuilder("[[");

        for (Move move : moves) {
            if (buf.length() > 2)
                buf.append(' ');
            buf.append(move.toString());
        }

        buf.append("]]");
        return buf.toString();
    }

    public String exportFEN() {
        return Fen.exportFEN(this);
    }

    public int getTurn() {
        return getGameStatus().getTurn();
    }

    int getOppositeColor() {
        return getGameStatus().getOppositeColor();
    }

    Random getRandom() {
        return rand;
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
        Moves validMoves = moveGenerator.calculateMoves(getGameStatus(), board);
        if (!validMoves.contains(move.getMove())) {
            print();
            System.out.println("Valid moves: " + validMoves);
            throw new IllegalStateException("Illegal move: " + move);
        }

        makeMove(move.getMove());

        calculateAndSetGameResult();
    }

    void makeMove(MoveAndWeight move) {
        final int factor = getGameStatus().isWhiteTurn() ? 1 : -1;
        setWeight(move.weight * factor); // Remember weight of current position
        makeMove(move.move);
    }

    private void makeMove(int move) {
        board.validateMove(move);

        previousBoard = board.copy();

        board.makeMove(move);
        moves.add(new Move(move));

        GameStatus newStatus = getGameStatus().makeMove(move);
        statusStack.add(newStatus);
    }

    void revertMove() {
        if (moves.isEmpty())
            throw new IllegalStateException("No move to revert");

        Move lastMove = moves.remove(moves.size() - 1);
        board.revertMove(lastMove.getMove());
        previousBoard = board.copy();
        if (!moves.isEmpty())
            previousBoard.revertMove(moves.get(moves.size() - 1).getMove());

        statusStack.remove(statusStack.size() - 1);
        result = GameResult.ONGOING;
    }

    private GameResult checkGameResult(MoveGenerator moveGenerator) {
        GameResult gameResult = checkCheckMateOrStaleMate(getGameStatus(), getBoard(), moveGenerator);
        if (gameResult == GameResult.ONGOING && getBoard().isDrawByMaterial())
            gameResult = GameResult.DRAW;

        return gameResult;
    }

    private static GameResult checkCheckMateOrStaleMate(GameStatus gameStatus, Board board, MoveGenerator moveGenerator) {
        final byte[] rawBoard = board.getRawBoard();

        // Check the next theoretically possible moves
        Moves nextMoves = moveGenerator.calculateMoves(gameStatus, board);
        if (nextMoves.isIllegal())
            throw new IllegalArgumentException("Illegal chess position");

        // Test each of those moves and try to find a valid one
        boolean haveValidMove = false;
        final int nPossibleMoves = nextMoves.count();
        final Board workingBoard = Board.createEmptyBoard();
        final byte[] rawWorkingBoard = workingBoard.getRawBoard();

        for (int i = 0; i < nPossibleMoves; i++) {
            System.arraycopy(rawBoard, 0, rawWorkingBoard, 0, rawBoard.length);
            final int nextMove = nextMoves.getMove(i);
            workingBoard.makeMove(nextMove);
            GameStatus nextGameStatus = gameStatus.makeMove(nextMove);

            Moves nextNextMoves = moveGenerator.calculateMoves(nextGameStatus, workingBoard);
            if (!nextNextMoves.isIllegal()) {
                haveValidMove = true;
                break;
            }
        }

        if (haveValidMove)
            return GameResult.ONGOING;

        // No valid move possible ==> Check if it is checkmate or stalemate
        return checkIsKingUnderChess(gameStatus, board, moveGenerator) ? GameResult.CHECKMATE : GameResult.STALEMATE;
    }

    public static boolean checkIsKingUnderChess(GameStatus gameStatus, Board board, MoveGenerator moveGenerator) {
        // TODO MF: Optimize method checkIsKingUnderChess
        // Switch turn
        gameStatus = gameStatus.switchTurn();

        // Check the next theoretically possible moves. If those contain an illegal move (king can be captured),
        // the king was under chess.
        // TODO MF: Calculate moves without sorting
        Moves nextMoves = moveGenerator.calculateMoves(gameStatus, board);
        return nextMoves.isIllegal();
    }

    void playAutoGame() {
        try {
            if (getResult() == GameResult.ONGOING)
                playAutoGameInternal();

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

            Board prevBoard = getPreviousBoard();
            if (prevBoard != null)
                prevBoard.print();
            getBoard().print();

            System.out.println("Turn: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black"));
            System.out.println("Moves: " + exportMoves());
            System.out.println("Status: " + getGameStatus());
            MoveGenerator moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
            Moves possibleMoves = moveGenerator.calculateMoves(getGameStatus(), getBoard());
            System.out.println("Possible moves: " + possibleMoves);
            System.out.flush();
            System.err.println("ERROR: " + e);
        }
    }

    @SuppressWarnings("Duplicates")
    private void playAutoGameInternal() throws InterruptedException, ExecutionException, TimeoutException {
        getBoard().print();

        int moveNo = getMoveCount() + 1;

        int i = 0;
        for (; i <=1000; i++, moveNo++) {
            MoveAndWeight move = getEngine().nextMoveAsync().getResult(1, TimeUnit.HOURS);
            if (move == MoveAndWeight.NO_MOVE) {
                // No valid move possible ==> checkmate or stalemate
                break;
            }
            makeMove(move);
            getBoard().print();
            System.out.println("Move #" + moveNo + ": " + ChessUtil.moveToString(move.move));

            if (getBoard().isDrawByMaterial())
                break;

            Thread.sleep(20000);
        }

        GameResult gameResult = checkGameResult(new MoveGenerator(MoveSorter.defaultImplementation()));
        setResult(gameResult);
    }

    void print() {
        getBoard().print();
        if (getResult() == GameResult.CHECKMATE || getResult() == GameResult.STALEMATE)
            System.out.println("Result: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black") + " " + getResult());
        else if (getResult() == GameResult.DRAW)
            System.out.println("Result: DRAW");
        else
            System.out.println("Turn: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black"));
    }
}
