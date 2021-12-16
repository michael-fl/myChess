package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public class IllegalMoveException extends IllegalStateException {

    IllegalMoveException(String message) {
        super(message);
    }
}
