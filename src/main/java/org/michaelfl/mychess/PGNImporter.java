package org.michaelfl.mychess;

import java.util.ArrayList;

/**
 * @author Michael Fleischhauer
 */
final class PGNImporter {

    private final String pgn;

    PGNImporter(String pgn) {
        this.pgn = pgn;
    }

    Game importGame() {
        return importGame(Game.standardConfig());
    }

    Game importGame(GameConfig config) {
        var pgns = new ArrayList<Pgn>(1);
        Pgn.parse(pgn).forEach(pgns::add);

        return new Game(config, pgns.get(0).moves);
    }
}
