package org.michaelfl.mychess;

final class GameStatus {

    private final int turn;
    private final int lastMoveFrom;
    private final int lastMoveTo;

    GameStatus(int turn, int lastMoveFrom, int lastMoveTo) {
        this.turn = turn;
        this.lastMoveFrom = lastMoveFrom;
        this.lastMoveTo = lastMoveTo;
    }

    int getTurn() {
        return turn;
    }

    int getOppositeColor() {
        return turn == Game.TURN_WHITE ? Game.TURN_BLACK : Game.TURN_WHITE;
    }

    int getLastMoveFrom() {
        return lastMoveFrom;
    }

    int getLastMoveTo() {
        return lastMoveTo;
    }

    GameStatus switchTurn() {
        return new GameStatus(getOppositeColor(), 0, 0);
    }
}
