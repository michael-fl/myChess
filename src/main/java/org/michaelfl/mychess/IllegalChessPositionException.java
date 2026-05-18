package org.michaelfl.mychess;

/**
 * Thrown when a {@link Board} state cannot legally arise in a real game
 * (e.g. side-to-move could capture the opponent's king).
 *
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
