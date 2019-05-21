package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.FixDepthEngine;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.RandomMoveEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Game {

    public enum GameResult {
        CHECKMATE,
        STALEMATE,
        DRAW,
        ONGOING
    }

    private final Random rand = new Random();
    private final ChessEngine engineWhite = new FixDepthEngine(this);
    private final ChessEngine engineBlack = new FixDepthEngine(this);
    private Board previousBoard;
    private Board board = new Board();
    private List<Move> moves = new ArrayList<>();
    private List<GameStatus> statusStack = new ArrayList<>();
    private GameResult result = GameResult.ONGOING;
    private Float weight;

    Game() {
        statusStack.add(GameStatus.newGame());
    }

    Game(List<MoveDescription> moves) {
        statusStack.add(GameStatus.newGame());

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
        MoveGenerator moveGenerator = new MoveGenerator();
        GameResult gameResult = checkGameResult(moveGenerator);
        setResult(gameResult);
        return gameResult;
    }

    public Float getWeight() {
        return weight;
    }

    public void setWeight(float weight) {
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
        }

        Move move = new Move(Move.create((byte) fromField, (byte) toField, capturedPiece, moveType));

        // Validate the move
        MoveGenerator moveGenerator = new MoveGenerator();
        Moves validMoves = moveGenerator.calculateMoves(getGameStatus(), board);
        if (!validMoves.contains(move.getMove())) {
            print();
            System.out.println("Valid moves: " + validMoves);
            throw new IllegalStateException("Illegal move: " + move);
        }

        makeMove(move.getMove());

        calculateAndSetGameResult();
    }

    void makeMove(int move) {
        // TODO deactivate in "production"
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
    }

    private GameResult checkGameResult(MoveGenerator moveGenerator) {
        GameResult gameResult = checkCheckMateOrStaleMate(getGameStatus(), getBoard(), moveGenerator);
        if (gameResult == GameResult.ONGOING && getBoard().isDrawByMaterial())
            gameResult = GameResult.DRAW;

        return gameResult;
    }

    static GameResult checkCheckMateOrStaleMate(GameStatus gameStatus, Board board, MoveGenerator moveGenerator) {
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
        Moves nextMoves = moveGenerator.calculateMoves(gameStatus, board);
        return nextMoves.isIllegal();
    }

    public static void main(String[] args) {
        new Game().playAutoGame();
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
            MoveGenerator moveGenerator = new MoveGenerator();
            Moves possibleMoves = moveGenerator.calculateMoves(getGameStatus(), getBoard());
            System.out.println("Possible moves: " + possibleMoves);
            System.out.flush();
            System.err.println("ERROR: " + e);
        }
    }

    @SuppressWarnings("Duplicates")
    private void playAutoGameInternal() {
        getBoard().print();

        int maxMoves = 0;
        int totalMovesCount = 0;
        int countGreater40 = 0;
        int countGreater50 = 0;
        int moveNo = getMoveCount() + 1;

        int i = 0;
        for (; i <=1000; i++, moveNo++) {
            int move = getEngine().nextMove();
            if (move == 0) {
                // No valid move possible ==> checkmate or stalemate
                break;
            }
            makeMove(move);
            System.out.println("Move #" + moveNo + ": " + ChessUtil.moveToString(move));

            if (getBoard().isDrawByMaterial())
                break;

            int countPossibleMoves = getEngine().getCountPossibleMoves();
            maxMoves = Math.max(maxMoves, countPossibleMoves);
            totalMovesCount += countPossibleMoves;
            if (countPossibleMoves > 40)
                countGreater40++;
            if (countPossibleMoves > 50)
                countGreater50++;
        }

        GameResult gameResult = checkGameResult(new MoveGenerator());
        setResult(gameResult);

        int avgMoves = i > 0 ? totalMovesCount / i : 0;
        System.out.println("Statistics: max moves: " + maxMoves + ", avg moves: " + avgMoves
                + ", # >40: " + countGreater40 + ", # >50: " + countGreater50);
    }

    private static Moves findValidMove(Game game, Moves moves, MoveGenerator moveGenerator, int moveNo) {
        Moves nextMoves = null;

        int nPossibleMoves = moves.count();
        for (int moveIndex = 0; moveIndex < nPossibleMoves; moveIndex++) {
            int move = moves.getMove(moveIndex);
            System.out.println("Move #" + moveNo + ": " + ChessUtil.moveToString(move));
            game.makeMove(move);
            nextMoves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());
            if (!nextMoves.isIllegal())
                break;

            System.out.println("ILLEGAL");
            game.revertMove();
        }

        return nextMoves;
    }

    private static String readLineFromStdin(BufferedReader in) throws IOException {
        System.out.print(">");
        System.out.flush();
        return in.readLine();
    }

    private static boolean isQuit(String line) {
        return "quit".equals(line) || "exit".equals(line);
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
