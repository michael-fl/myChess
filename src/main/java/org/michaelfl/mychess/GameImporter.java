package org.michaelfl.mychess;

/**
 * @author Michael Fleischhauer
 */
public interface GameImporter {
    Game importGame();
    Game importGame(GameConfig config);

    static GameImporter importerFor(String gameNotation) {
        if (gameNotation.startsWith("[[")) {
            return new SimpleNotationImporter(gameNotation);
        } else {
            var pgn = Pgn.parse(gameNotation).findFirst();
            return new PGNImporter(pgn.orElseThrow(() -> new IllegalArgumentException("No PGN given")));
        }
    }
}
