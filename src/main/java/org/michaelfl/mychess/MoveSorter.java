package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public interface MoveSorter {
    void reset(GameStatus gameStatus, Board board, int depth);
    void addMove(int move, int fromField, int toField, byte movingPiece, byte capturedPiece);
    Moves getSortedMoves();
}
