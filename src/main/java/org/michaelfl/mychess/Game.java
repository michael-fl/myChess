package org.michaelfl.mychess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

    private final Random rand = new Random();
    private Board previousBoard;
    private Board board = new Board();
    private List<Move> moves = new ArrayList<>();
    private List<GameStatus> statusStack = new ArrayList<>();
    private GameResult result = GameResult.ONGOING;

    Game() {
        statusStack.add(GameStatus.newGame());
    }

    Game(List<MoveDescription> moves) {
        statusStack.add(GameStatus.newGame());

        for (MoveDescription move : moves) {
            makeMove(move);
        }

        // Check if game is already over
        MoveGenerator moveGenerator = new MoveGenerator();
        GameResult gameResult = checkGameResult(moveGenerator);
        setResult(gameResult);
    }

    GameStatus getGameStatus() {
        return statusStack.get(statusStack.size() - 1);
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

    int getTurn() {
        return getGameStatus().getTurn();
    }

    int getOppositeColor() {
        return getGameStatus().getOppositeColor();
    }

    Random getRandom() {
        return rand;
    }

    void makeMove(MoveDescription moveDescr) {
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
            if (game.getResult() == GameResult.ONGOING)
                playAutoGameInternal(game);

            game.getBoard().print();
            int turn = game.getTurn();
            System.out.println("Moves: " + game.exportMoves());
            System.out.println("Turn: " + (turn == GameStatus.TURN_WHITE ? "white" : "black"));
            if (game.getResult() == GameResult.CHECKMATE || game.getResult() == GameResult.STALEMATE)
                System.out.println("Result: " + (turn == GameStatus.TURN_WHITE ? "white" : "black") + " " + game.getResult());
            else
                System.out.println("Result: " + game.getResult());

        } catch (Throwable e) {
            e.printStackTrace();

            Board prevBoard = game.getPreviousBoard();
            if (prevBoard != null)
                prevBoard.print();
            game.getBoard().print();

            System.out.println("Turn: " + (game.getTurn() == GameStatus.TURN_WHITE ? "white" : "black"));
            System.out.println("Moves: " + game.exportMoves());
            System.out.println("Status: " + game.getGameStatus());
            MoveGenerator moveGenerator = new MoveGenerator();
            Moves possibleMoves = moveGenerator.calculateMoves(game.getGameStatus(), game.getBoard());
            System.out.println("Possible moves: " + possibleMoves);
            System.out.flush();
            System.err.println("ERROR: " + e);
        }
    }

    @SuppressWarnings("Duplicates")
    private static void playAutoGameInternal(Game game) {
        //BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        ChessEngine engine = new RandomMoveEngine(game);

        game.getBoard().print();

        int maxMoves = 0;
        int totalMovesCount = 0;
        int countGreater40 = 0;
        int countGreater50 = 0;
        int moveNo = game.getMoveCount() + 1;

        int i = 0;
        for (; i <=1000; i++, moveNo++) {
            int move = engine.nextMove();
            if (move == 0) {
                // No valid move possible ==> checkmate or stalemate
                System.out.println("NO MOVE POSSIBLE");
                break;
            }
            game.makeMove(move);
            System.out.println("Move #" + moveNo + ": " + ChessUtil.moveToString(move));

            if (game.getBoard().isDrawByMaterial())
                break;

            int countPossibleMoves = engine.getCountPossibleMoves();
            maxMoves = Math.max(maxMoves, countPossibleMoves);
            totalMovesCount += countPossibleMoves;
            if (countPossibleMoves > 40)
                countGreater40++;
            if (countPossibleMoves > 50)
                countGreater50++;
        }

        GameResult gameResult = game.checkGameResult(new MoveGenerator());
        game.setResult(gameResult);

        int avgMoves = i > 0 ? totalMovesCount / i : 0;
        System.out.println("Statistics: max moves: " + maxMoves + ", avg moves: " + avgMoves
                + ", # >40: " + countGreater40 + ", # >50: " + countGreater50);
    }

    private static void playAutoGameInternalOld(Game game) {
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
            int move = moves.getMove(moveIndex);
            System.out.println("Move #" + moveNo + ": " + ChessUtil.moveToString(move));
            game.makeMove(move);

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

        int avgMoves = totalMovesCount / moveNo;
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
        System.out.println("Moves: " + exportMoves());
        System.out.println("Turn: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black"));
        if (getResult() == GameResult.CHECKMATE || getResult() == GameResult.STALEMATE)
            System.out.println("Result: " + (getTurn() == GameStatus.TURN_WHITE ? "white" : "black") + " " + getResult());
        else
            System.out.println("Result: " + getResult());
    }
}
