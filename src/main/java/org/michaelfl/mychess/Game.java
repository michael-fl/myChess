package org.michaelfl.mychess;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class Game {

    enum GameResult {
        CHECKMATE,
        STALEMATE,
        DRAW,
        ONGOING
    }

    final static int TURN_WHITE = 8;
    final static int TURN_BLACK = 16;

    private final Random rand = new Random();
    private Board board = new Board();
    private int turn = TURN_WHITE; // 8 = white, 16 = black
    private int oppositeColor = TURN_BLACK; // 8 = white, 16 = black
    private List<Board> boardStack = new ArrayList<>();
    private List<Move> moves = new ArrayList<>();
    private GameResult result = GameResult.ONGOING;

    Game() {

    }

    Game(List<Move> moves) {
        for (Move move : moves) {
            makeMove(move.getFromField(), move.getToField());
        }

        // Check if game is already over
        MoveGenerator moveGenerator = new MoveGenerator();
        GameResult gameResult = checkGameResult(moveGenerator);
        setResult(gameResult);
    }

    GameStatus getGameStatus() {
        if (moves.isEmpty())
            return new GameStatus(turn, 0, 0);
        else {
            Move lastMove = moves.get(moves.size() - 1);
            return new GameStatus(turn, lastMove.getFromField(), lastMove.getToField());
        }
    }

    GameResult getResult() {
        return result;
    }

    void setResult(GameResult result) {
        this.result = result;
    }

    Board getBoard() {
        return board;
    }

    Board getPreviousBoard() {
        return boardStack.isEmpty() ? null : boardStack.get(boardStack.size() - 1);
    }

    List<Move> getMoves() {
        return moves;
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

    int getTurn() {
        return turn;
    }

    int getOppositeColor() {
        return oppositeColor;
    }

    Random getRandom() {
        return rand;
    }

    void makeMove(int fromField, int toField) {
        // TODO deactivate in "production"
        board.validateMove(fromField, toField);

        boardStack.add(board.copy());
        moves.add(new Move(fromField, toField));

        final int toRow = ChessUtil.getRowOfField(toField);
        final byte piece = board.get(fromField);
        if ((piece == Board.whitePawn && toRow == 7) || (piece == Board.blackPawn && toRow == 0))
            board.makePawnPromotionMove(fromField, toField, turn == TURN_WHITE ? Board.whiteQueen : Board.blackQueen);
        else
            board.makeMove(fromField, toField);

        int nextTurn = oppositeColor;
        oppositeColor = turn;
        turn = nextTurn;
    }

    void revertMove() {
        if (boardStack.isEmpty())
            throw new IllegalStateException("No move to revert");

        board = boardStack.remove(boardStack.size() - 1);
        moves.remove(moves.size() - 1);

        int prevTurn = oppositeColor;
        oppositeColor = turn;
        turn = prevTurn;
    }

    private static GameStatus makeMove(GameStatus gameStatus, Board board, int fromField, int toField) {
        final int toRow = ChessUtil.getRowOfField(toField);
        final byte piece = board.get(fromField);
        if ((piece == Board.whitePawn && toRow == 7) || (piece == Board.blackPawn && toRow == 0))
            board.makePawnPromotionMove(fromField, toField, gameStatus.getTurn() == TURN_WHITE ? Board.whiteQueen : Board.blackQueen);
        else
            board.makeMove(fromField, toField);

        return new GameStatus(gameStatus.getOppositeColor(), fromField, toField);
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
            GameStatus nextGameStatus = Game.makeMove(gameStatus, workingBoard, nextMoves.getFrom(i), nextMoves.getTo(i));
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

    private static boolean checkIsKingUnderChess(GameStatus gameStatus, Board board, MoveGenerator moveGenerator) {
        // Switch turn
        gameStatus = gameStatus.switchTurn();

        // Check the next theoretically possible moves. If those contain an illegal move (king can be captured),
        // the king was under chess.
        Moves nextMoves = moveGenerator.calculateMoves(gameStatus, board);
        return nextMoves.isIllegal();
    }

    public static void main(String[] args) {
        playAutoGame(new Game());
    }

    static void playAutoGame(Game game) {
        try {
            if (game.getResult() == GameResult.ONGOING) {
                playAutoGameInternal(game);
            } else {
                game.getBoard().print();
                int turn = game.getTurn();
                System.out.println("Moves: " + game.exportMoves());
                System.out.println("Turn: " + (turn == Game.TURN_WHITE ? "white" : "black"));
                if (game.getResult() == GameResult.CHECKMATE || game.getResult() == GameResult.STALEMATE)
                    System.out.println("Result: " + (turn == Game.TURN_WHITE ? "white" : "black") + " " + game.getResult());
                else
                    System.out.println("Result: " + game.getResult());
            }
        } catch (Throwable e) {
            e.printStackTrace();

            Board prevBoard = game.getPreviousBoard();
            if (prevBoard != null)
                prevBoard.print();
            game.getBoard().print();

            System.out.println("Turn: " + (game.getTurn() == Game.TURN_WHITE ? "white" : "black"));
            System.out.println("Moves: " + game.getMoves());
            System.out.flush();
            System.err.println("ERROR: " + e);
        }
    }

    private static void playAutoGameInternal(Game game) {
        MoveGenerator moveGenerator = new MoveGenerator();

        Moves moves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());
        game.getBoard().print();
        moves.print();

        int maxMoves = 0;
        int totalMovesCount = 0;
        int countGreater40 = 0;
        int countGreater50 = 0;
        int moveNo = 1;

        for (; moveNo <= 1000; moveNo++) {
            int moveIndex = game.getRandom().nextInt(moves.count());
            int fromField = moves.getFrom(moveIndex);
            int toField = moves.getTo(moveIndex);
            System.out.println("Move #" + moveNo + ": " + moves.moveToString(moveIndex));
            game.makeMove(fromField, toField);

            Moves nextMoves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());
            if (nextMoves.isIllegal()) {
                System.out.println("ILLEGAL");
                game.getBoard().print();
                game.revertMove();
                game.getBoard().print();
                nextMoves = findValidMove(game, moves, moveGenerator, moveNo);
            }
            if (nextMoves.isIllegal() || nextMoves.count() == 0)
                break;
            if (game.getBoard().isDrawByMaterial())
                break;

            moves = nextMoves;
            maxMoves = Math.max(maxMoves, moves.count());
            totalMovesCount += moves.count();
            if (moves.count() > 40)
                countGreater40++;
            if (moves.count() > 50)
                countGreater50++;
        }

        GameResult gameResult = game.checkGameResult(moveGenerator);
        game.setResult(gameResult);

        game.getBoard().print();
        int turn = game.getTurn();
        int avgMoves = totalMovesCount / moveNo;
        System.out.println("Moves: " + game.exportMoves());
        System.out.println("Turn: " + (turn == Game.TURN_WHITE ? "white" : "black"));
        System.out.println("Statistics: max moves: " + maxMoves + ", avg moves: " + avgMoves
                + ", # >40: " + countGreater40 + ", # >50: " + countGreater50);
        if (game.getResult() == GameResult.CHECKMATE || game.getResult() == GameResult.STALEMATE)
            System.out.println("Result: " + (turn == Game.TURN_WHITE ? "white" : "black") + " " + game.getResult());
        else
            System.out.println("Result: " + game.getResult());
    }

    private static Moves findValidMove(Game game, Moves moves, MoveGenerator moveGenerator, int moveNo) {
        Moves nextMoves = null;

        int nPossibleMoves = moves.count();
        for (int moveIndex = 0; moveIndex < nPossibleMoves; moveIndex++) {
            int fromField = moves.getFrom(moveIndex);
            int toField = moves.getTo(moveIndex);
            System.out.println("Move #" + moveNo + ": " + moves.moveToString(moveIndex));
            game.makeMove(fromField, toField);
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
        System.out.println("Moves: " + exportMoves());
        System.out.println("Turn: " + (turn == Game.TURN_WHITE ? "white" : "black"));
        if (getResult() == GameResult.CHECKMATE || getResult() == GameResult.STALEMATE)
            System.out.println("Result: " + (turn == Game.TURN_WHITE ? "white" : "black") + " " + getResult());
        else
            System.out.println("Result: " + getResult());
    }
}
