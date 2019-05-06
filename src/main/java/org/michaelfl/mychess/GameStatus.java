package org.michaelfl.mychess;

final class GameStatus {

    final static int TURN_WHITE = 8;
    @SuppressWarnings("WeakerAccess")
    final static int TURN_BLACK = 16;

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
        this.whiteCastlingQueenSidePossible = previousStatus.blackCastlingQueenSidePossible;
    }

    static GameStatus newGame() {
        return new GameStatus(TURN_WHITE, -1);
    }

    int getTurn() {
        return turn;
    }

    int getOppositeColor() {
        return turn == GameStatus.TURN_WHITE ? GameStatus.TURN_BLACK : GameStatus.TURN_WHITE;
    }

    int getLastMove() {
        return lastMove;
    }

    boolean isCastlingPossible() {
        return (turn == TURN_WHITE && (whiteCastlingKingSidePossible || whiteCastlingQueenSidePossible))
                || (turn == TURN_BLACK && (blackCastlingKingSidePossible || blackCastlingQueenSidePossible));
    }

    boolean isWhiteCastlingKingSidePossible() {
        return whiteCastlingKingSidePossible;
    }

    void setWhiteCastlingKingSidePossible(boolean whiteCastlingKingSidePossible) {
        this.whiteCastlingKingSidePossible = whiteCastlingKingSidePossible;
    }

    boolean isWhiteCastlingQueenSidePossible() {
        return whiteCastlingQueenSidePossible;
    }

    void setWhiteCastlingQueenSidePossible(boolean whiteCastlingQueenSidePossible) {
        this.whiteCastlingQueenSidePossible = whiteCastlingQueenSidePossible;
    }

    boolean isBlackCastlingKingSidePossible() {
        return blackCastlingKingSidePossible;
    }

    void setBlackCastlingKingSidePossible(boolean blackCastlingKingSidePossible) {
        this.blackCastlingKingSidePossible = blackCastlingKingSidePossible;
    }

    boolean isBlackCastlingQueenSidePossible() {
        return blackCastlingQueenSidePossible;
    }

    void setBlackCastlingQueenSidePossible(boolean blackCastlingQueenSidePossible) {
        this.blackCastlingQueenSidePossible = blackCastlingQueenSidePossible;
    }

    GameStatus switchTurn() {
        return new GameStatus(getOppositeColor(), -1, this);
    }

    GameStatus makeMove(int move) {
        GameStatus newStatus = new GameStatus(getOppositeColor(), move, this);
        newStatus.updateCastlingState(turn, move);

        return newStatus;
    }

    private void updateCastlingState(final int turn, final int move) {
        final int fromField = Move.getFromField(move);

        if (turn == GameStatus.TURN_WHITE) {
            if (isWhiteCastlingKingSidePossible() || isWhiteCastlingQueenSidePossible()) {
                if (fromField == Board.e1) { // king moved
                    setWhiteCastlingKingSidePossible(false);
                    setWhiteCastlingQueenSidePossible(false);
                } else if (fromField == Board.h1) { // rook moved
                    setWhiteCastlingKingSidePossible(false);
                } else if (fromField == Board.a1) { // rook moved
                    setWhiteCastlingQueenSidePossible(false);
                }
            }
        } else {
            if (isBlackCastlingKingSidePossible() || isBlackCastlingQueenSidePossible()) {
                if (fromField == Board.e8) {
                    setBlackCastlingKingSidePossible(false);
                    setBlackCastlingQueenSidePossible(false);
                } else if (fromField == Board.h8) {
                    setWhiteCastlingKingSidePossible(false);
                } else if (fromField == Board.a8) {
                    setWhiteCastlingQueenSidePossible(false);
                }
            }
        }
    }
}
