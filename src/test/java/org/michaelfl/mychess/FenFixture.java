package org.michaelfl.mychess;

/**
 * Gives tests outside this package a board from a FEN. {@link Fen#importFEN(String)} is package-private.
 *
 * @author Michael Fleischhauer
 */
public final class FenFixture {

    private FenFixture() {
    }

    public static Board board(String fen) {
        return Fen.importFEN(fen);
    }
}
