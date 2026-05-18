package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.MoveSorterImpl;

/**
 * Sink for generated moves that returns them in search-friendly order. The
 * default implementation is {@link MoveSorterImpl}; instances are obtained
 * via {@link #defaultImplementation()}.
 *
 * @author Michael Fleischhauer
 */
public interface MoveSorter {
    void reset(GameStatus gameStatus, Board board, int depth, int knownBestMove);
    void addMove(int move, int fromField, int toField, byte movingPiece, byte capturedPiece);
    Moves getSortedMoves();

    static MoveSorter defaultImplementation() {
        return new MoveSorterImpl();
    }
}
