package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public class IllegalMoveException extends IllegalStateException {

    IllegalMoveException(MoveDescription move) {
        this("Illegal move: " + move);
    }

    IllegalMoveException(String message) {
        super(message);
    }
}
