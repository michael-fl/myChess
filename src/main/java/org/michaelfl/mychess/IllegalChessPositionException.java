package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public class IllegalChessPositionException extends IllegalStateException {

    private final Board board;

    public IllegalChessPositionException(Board board) {
        super("Illegal chess position.");

        this.board = board;
    }

    public Board getBoard() {
        return board;
    }
}
