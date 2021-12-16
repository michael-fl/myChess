package org.michaelfl.mychess;

import org.michaelfl.mychess.MoveDescription.Builder;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.NextMoveTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final ChessEngine statusEngine;

    private final Board board = Board.createNewGame();
    private GameResult result = GameResult.ONGOING;

    static GameConfig standardConfig() {
        return new GameConfig(
                MyChessEngine.class, new EngineConfig.Builder().maxDepth(8).build()
                //MyChessEngine1.class, new EngineConfig.Builder().maxDepth(14).iterationDepth(6).variants(4).build()
        );
    }

    Game() {
        this(standardConfig());
    }

    Game(GameConfig config) {
        engineWhite = config.createEngineWhite(this);
        engineBlack = config.createEngineBlack(this);
        statusEngine = new MyChessEngine(
                new EngineConfig.Builder()
                        .maxDepth(2)
                        .checkmateCheck(false)
                        .useHandicap(false)
                        .silent(true)
                        .build(), this);
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
        MoveAndWeight move = statusEngine.calculateNextMove(new NextMoveTask());
        if (move.path.length > 0 && move.path[0] != 0) {
            // at least one move still possible ==> ongoing
            setResult(GameResult.ONGOING);
        } else {
            setResult(move.result);
        }
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

    int getOppositeColor() {
        return getGameStatus().getOppositeColor();
    }

    void makeMove(MoveDescription moveDescr) {
        var moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());

        if (getResult() != GameResult.ONGOING) {
            System.err.println("Game is already over");
            return;
        }

        moveDescr = resolveMoveDescription(moveDescr, moveGenerator);
        makeMoveResolved(moveDescr, moveGenerator);
    }

    private MoveDescription resolveMoveDescription(MoveDescription moveDescr, MoveGenerator moveGenerator) {
        var builder = new Builder(moveDescr);
        int toField = moveDescr.getToField();

        if (builder.piece <= 0) {
            builder.piece = board.get(moveDescr.getFromField());
        }

        if (builder.fromCol < 0 || builder.fromRow < 0) {
            // Must resolve source field
            var possibleMoves = getPossiblePieceMoves(builder.piece, toField, moveGenerator);
            if (builder.fromCol >= 0) {
                possibleMoves.removeIf(move -> Move.getFromCol(move) != builder.fromCol);
            }
            if (builder.fromRow >= 0) {
                possibleMoves.removeIf(move -> Move.getFromRow(move) != builder.fromRow);
            }
            if (builder.pawnPromotionPiece > 0) {
                possibleMoves.removeIf(move -> Move.getMoveType(move) != Move.typePawnPromotionQueen);
            }
            if (possibleMoves.isEmpty()) {
                throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Impossible move.");
            }
            if (possibleMoves.size() != 1) {
                throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Move is not unique.");
            }

            int move = possibleMoves.iterator().next();
            builder.fromCol = Move.getFromCol(move);
            builder.fromRow = Move.getFromRow(move);
        }

        // Verify isCapture
        int capturedPiece = board.get(moveDescr.getToField());
        builder.isCapture = capturedPiece != Board.empty;
        if (moveDescr.isCapture != null && moveDescr.isCapture && !builder.isCapture) {
            throw new IllegalMoveException("Wrong move notation: " + moveDescr + ". Move does not capture any piece.");
        }

        return builder.build();
    }

    private Set<Integer> getPossiblePieceMoves(byte piece, int toField, MoveGenerator moveGenerator) {
        var result = new HashSet<Integer>();

        int[] possibleMoves = moveGenerator.calculateMoves(board).getMoves();

        for (int move : possibleMoves) {
            if (toField == Move.getToField(move) && board.get(Move.getFromField(move)) == piece) {
                result.add(move);
            }
        }

        return result;
    }

    private void makeMoveResolved(MoveDescription moveDescr, MoveGenerator moveGenerator) {
        int fromField = moveDescr.getFromField();
        int toField = moveDescr.getToField();
        byte piece = board.get(fromField);
        byte capturedPiece = board.get(toField);
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

        Move move = new Move(Move.create((byte) fromField, (byte) toField, capturedPiece, moveType));

        // Validate the move
        Moves validMoves = moveGenerator.calculateMoves(board);
        Move moveToValidate = move;
        if (moveType == Move.typePawnPromotionBishop || moveType == Move.typePawnPromotionRook) {
            moveToValidate = new Move(Move.create((byte) fromField, (byte) toField, capturedPiece, Move.typePawnPromotionQueen));
        }
        if (!validMoves.contains(moveToValidate.getMove())) {
            throw new IllegalMoveException("Illegal move: " + moveDescr);
        }

        makeMove(move.getMove());

        try {
            calculateAndSetGameResult();
            verifyMove(moveDescr, moveGenerator);

        } catch (IllegalMoveException e) { // move was illegal
            revertMove();
            throw e;
        }
    }

    private void verifyMove(MoveDescription moveDescr, MoveGenerator moveGenerator) {
        // Verify isCheck
        if (moveDescr.isCheck != null && moveDescr.isCheck && !testIsKingChecked(board, moveGenerator)) {
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
                        || moveDescr.toRow != 1) {
                    throw new IllegalMoveException("Wrong move notation: " + moveDescr + "+. Not a pawn promotion.");
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

    public static boolean testIsKingChecked(Board board, MoveGenerator moveGenerator) {
        // TODO MF: Optimize method testIsKingChecked
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
            if (move.move == 0) {
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
        System.out.println("Handicap: white=" + gameStatus.getHandicapWhite() + ", black=" + gameStatus.getHandicapBlack());
    }
}
