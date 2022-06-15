package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public interface GameImporter {
    Game importGame();
    Game importGame(GameConfig config);
}
