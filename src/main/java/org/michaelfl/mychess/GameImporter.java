package org.michaelfl.mychess;

/**
 * Builds a {@link Game} from a notation string. {@link #importerFor(String)}
 * dispatches to {@link SimpleNotationImporter} for the {@code [[...]]} form
 * or {@link PGNImporter} for standard PGN.
 *
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
