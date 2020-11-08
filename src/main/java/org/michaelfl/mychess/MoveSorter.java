package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.MoveSorterImpl;

/**
 * @author Michael Fleischhauer
 */
public interface MoveSorter {
    void reset(GameStatus gameStatus, Board board, int depth);
    void addMove(int move, int fromField, int toField, byte movingPiece, byte capturedPiece);
    Moves getSortedMoves();

    static MoveSorter defaultImplementation() {
        return new MoveSorterImpl();
    }
}
