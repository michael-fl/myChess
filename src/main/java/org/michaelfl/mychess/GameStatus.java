package org.michaelfl.mychess;

public final class GameStatus {

    public final static int TURN_WHITE = 8;
    @SuppressWarnings("WeakerAccess")
    public final static int TURN_BLACK = 16;

    private int turn;
    private int lastMove;
    private boolean whiteCastlingKingSidePossible = true;
    private boolean whiteCastlingQueenSidePossible = true;
    private boolean blackCastlingKingSidePossible = true;
    private boolean blackCastlingQueenSidePossible = true;

    private GameStatus(int turn, int lastMove) {
        this.turn = turn;
        this.lastMove = lastMove;
    }

    private GameStatus(int turn, int lastMove, GameStatus previousStatus) {
        this.turn = turn;
        this.lastMove = lastMove;

        this.whiteCastlingKingSidePossible = previousStatus.whiteCastlingKingSidePossible;
        this.whiteCastlingQueenSidePossible = previousStatus.whiteCastlingQueenSidePossible;
        this.blackCastlingKingSidePossible = previousStatus.blackCastlingKingSidePossible;
        this.blackCastlingQueenSidePossible = previousStatus.blackCastlingQueenSidePossible;
    }

    static GameStatus newGame() {
        return new GameStatus(TURN_WHITE, 0);
    }

    public int getTurn() {
        return turn;
    }

    public int getOppositeColor() {
        return turn == GameStatus.TURN_WHITE ? GameStatus.TURN_BLACK : GameStatus.TURN_WHITE;
    }

    public int getLastMove() {
        return lastMove;
    }

    public boolean isCastlingPossible() {
        return (turn == TURN_WHITE && (whiteCastlingKingSidePossible || whiteCastlingQueenSidePossible))
                || (turn == TURN_BLACK && (blackCastlingKingSidePossible || blackCastlingQueenSidePossible));
    }

    public boolean isWhiteCastlingKingSidePossible() {
        return whiteCastlingKingSidePossible;
    }

    public boolean isWhiteCastlingQueenSidePossible() {
        return whiteCastlingQueenSidePossible;
    }

    public boolean isBlackCastlingKingSidePossible() {
        return blackCastlingKingSidePossible;
    }

    public boolean isBlackCastlingQueenSidePossible() {
        return blackCastlingQueenSidePossible;
    }

    public GameStatus switchTurn() {
        return new GameStatus(getOppositeColor(), 0, this);
    }

    public GameStatus makeMove(int move) {
        GameStatus newStatus = new GameStatus(getOppositeColor(), move, this);
        newStatus.updateCastlingState(turn, move);

        return newStatus;
    }

    private void updateCastlingState(final int turn, final int move) {
        final int fromField = Move.getFromField(move);

        if (turn == GameStatus.TURN_WHITE) {
            if (isWhiteCastlingKingSidePossible() || isWhiteCastlingQueenSidePossible()) {
                if (fromField == Board.e1) { // king moved
                    whiteCastlingKingSidePossible = false;
                    whiteCastlingQueenSidePossible = false;
                } else if (fromField == Board.h1) { // rook moved
                    whiteCastlingKingSidePossible = false;
                } else if (fromField == Board.a1) { // rook moved
                    whiteCastlingQueenSidePossible = false;
                }
            }
        } else {
            if (isBlackCastlingKingSidePossible() || isBlackCastlingQueenSidePossible()) {
                if (fromField == Board.e8) {
                    blackCastlingKingSidePossible = false;
                    blackCastlingQueenSidePossible = false;
                } else if (fromField == Board.h8) {
                    blackCastlingKingSidePossible = false;
                } else if (fromField == Board.a8) {
                    blackCastlingQueenSidePossible = false;
                }
            }
        }
    }

    public boolean isBetterWeight(float w1, float w2) {
        return turn == TURN_WHITE ? w1 > w2 : w2 > w1;
    }

    @Override
    public String toString() {
        return "turn=" + (turn == GameStatus.TURN_WHITE ? "white" : "black")
                + ", lastMove=" + (lastMove != 0 ? ChessUtil.moveToString(lastMove) : "none")
                + ", whiteCastling=" + (whiteCastlingKingSidePossible ? "O-O" : "") + " " + (whiteCastlingQueenSidePossible ? "O-O-O" : "")
                + ", blackCastling=" + (blackCastlingKingSidePossible ? "O-O" : "") + " " + (blackCastlingQueenSidePossible ? "O-O-O" : "");
    }
}
