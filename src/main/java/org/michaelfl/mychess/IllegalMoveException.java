package org.michaelfl.mychess;

/**
 * Thrown when a {@link MoveDescription} cannot be played in the current
 * position (illegal target, wrong side to move, bogus check/checkmate
 * annotation, etc.). {@link Game#makeMove} reverts the board before throwing.
 *
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
