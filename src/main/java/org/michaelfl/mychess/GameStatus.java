package org.michaelfl.mychess;

public final class GameStatus {

    public final static int TURN_WHITE = 8;
    @SuppressWarnings("WeakerAccess")
    public final static int TURN_BLACK = 16;

    private final int plyCount;
    private final int turn;
    private final int lastMove;
    private final int halfMoveClock;
    private boolean whiteHasCastled = false;
    private boolean blackHasCastled = false;
    private boolean whiteCastlingKingSidePossible = true;
    private boolean whiteCastlingQueenSidePossible = true;
    private boolean blackCastlingKingSidePossible = true;
    private boolean blackCastlingQueenSidePossible = true;

    private GameStatus(int plyCount, int turn, int lastMove) {
        this.plyCount = plyCount;
        this.turn = turn;
        this.lastMove = lastMove;
        this.halfMoveClock = 0;
    }

    private GameStatus(int plyCount, int turn, int lastMove, int halfMoveClock, GameStatus previousStatus) {
        this.plyCount = plyCount;
        this.turn = turn;
        this.lastMove = lastMove;
        this.halfMoveClock = halfMoveClock;

        this.whiteHasCastled = previousStatus.whiteHasCastled;
        this.blackHasCastled = previousStatus.blackHasCastled;
        this.whiteCastlingKingSidePossible = previousStatus.whiteCastlingKingSidePossible;
        this.whiteCastlingQueenSidePossible = previousStatus.whiteCastlingQueenSidePossible;
        this.blackCastlingKingSidePossible = previousStatus.blackCastlingKingSidePossible;
        this.blackCastlingQueenSidePossible = previousStatus.blackCastlingQueenSidePossible;
    }

    static GameStatus newGame() {
        return new GameStatus(0, TURN_WHITE, 0);
    }

    public int getPlyCount() {
        return plyCount;
    }

    public int getTurn() {
        return turn;
    }

    public int getHalfMoveClock() {
        return halfMoveClock;
    }

    public boolean isEndGame() {
        return plyCount > 60; // TODO: Optimize end game detection
    }

    public boolean isWhiteTurn() {
        return turn == GameStatus.TURN_WHITE;
    }

    public boolean isBlackTurn() {
        return turn == GameStatus.TURN_BLACK;
    }

    public int getOppositeColor() {
        return turn == GameStatus.TURN_WHITE ? GameStatus.TURN_BLACK : GameStatus.TURN_WHITE;
    }

    public int getLastMove() {
        return lastMove;
    }

    public final boolean hasWhiteCastled() {
        return whiteHasCastled;
    }

    public final boolean hasBlackCastled() {
        return blackHasCastled;
    }

    public final boolean isCastlingPossible() {
        return (turn == TURN_WHITE && (whiteCastlingKingSidePossible || whiteCastlingQueenSidePossible))
                || (turn == TURN_BLACK && (blackCastlingKingSidePossible || blackCastlingQueenSidePossible));
    }

    public final boolean isWhiteCastlingKingSidePossible() {
        return whiteCastlingKingSidePossible;
    }

    public final boolean isWhiteCastlingQueenSidePossible() {
        return whiteCastlingQueenSidePossible;
    }

    public final boolean isBlackCastlingKingSidePossible() {
        return blackCastlingKingSidePossible;
    }

    public final boolean isBlackCastlingQueenSidePossible() {
        return blackCastlingQueenSidePossible;
    }

    public GameStatus switchTurn() {
        return new GameStatus(0, getOppositeColor(), 0, halfMoveClock, this);
    }

    public GameStatus makeMove(final Board workingBoard, final int move) {
        // Reset halfMoveClock if a pawn was moved or a piece was captured
        int newHalfMoveClock = 0;
        if (Move.getCapturedPiece(move) == 0 && !Board.isPawn(workingBoard.get(Move.getFromField(move)))) {
            newHalfMoveClock = halfMoveClock + 1;
        }

        final GameStatus newStatus = new GameStatus(plyCount + 1, getOppositeColor(), move, newHalfMoveClock, this);
        newStatus.updateCastlingState(turn, move);

        // Make the move on the board
        workingBoard.makeMove(move);

        // Return new game status
        return newStatus;
    }

    private void updateCastlingState(final int turn, final int move) {
        final int fromField = Move.getFromField(move);
        final int toField = Move.getToField(move);

        if (turn == GameStatus.TURN_WHITE) {
            if (isWhiteCastlingKingSidePossible() || isWhiteCastlingQueenSidePossible()) {
                if (fromField == Board.e1) { // king moved
                    if (toField == Board.g1 || toField == Board.c1)
                        whiteHasCastled = true;
                    whiteCastlingKingSidePossible = false;
                    whiteCastlingQueenSidePossible = false;
                } else if (fromField == Board.h1) { // rook moved
                    whiteCastlingKingSidePossible = false;
                } else if (fromField == Board.a1) { // rook moved
                    whiteCastlingQueenSidePossible = false;
                }
            }
            if (blackCastlingQueenSidePossible && toField == Board.a8)
                blackCastlingQueenSidePossible = false;
            else if (blackCastlingKingSidePossible && toField == Board.h8)
                blackCastlingKingSidePossible = false;
        } else {
            if (isBlackCastlingKingSidePossible() || isBlackCastlingQueenSidePossible()) {
                if (fromField == Board.e8) {
                    if (toField == Board.g8 || toField == Board.c8)
                        blackHasCastled = true;
                    blackCastlingKingSidePossible = false;
                    blackCastlingQueenSidePossible = false;
                } else if (fromField == Board.h8) {
                    blackCastlingKingSidePossible = false;
                } else if (fromField == Board.a8) {
                    blackCastlingQueenSidePossible = false;
                }
            }
            if (whiteCastlingQueenSidePossible && toField == Board.a1)
                whiteCastlingQueenSidePossible = false;
            else if (whiteCastlingKingSidePossible && toField == Board.h1)
                whiteCastlingKingSidePossible = false;
        }
    }

    @Override
    public String toString() {
        return "turn=" + (turn == GameStatus.TURN_WHITE ? "white" : "black")
                + ", lastMove=" + (lastMove != 0 ? ChessUtil.moveToString(lastMove) : "none")
                + ", whiteCastling=" + (whiteCastlingKingSidePossible ? "O-O" : "") + " " + (whiteCastlingQueenSidePossible ? "O-O-O" : "")
                + ", blackCastling=" + (blackCastlingKingSidePossible ? "O-O" : "") + " " + (blackCastlingQueenSidePossible ? "O-O-O" : "");
    }
}
