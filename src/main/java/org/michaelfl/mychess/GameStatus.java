package org.michaelfl.mychess;

final class GameStatus {

    private final int turn;
    private final int lastMove;

    GameStatus(int turn, int lastMove) {
        this.turn = turn;
        this.lastMove = lastMove;
    }

    int getTurn() {
        return turn;
    }

    int getOppositeColor() {
        return turn == Game.TURN_WHITE ? Game.TURN_BLACK : Game.TURN_WHITE;
    }

    int getLastMove() {
        return lastMove;
    }

    GameStatus switchTurn() {
        return new GameStatus(getOppositeColor(), -1);
    }
}
