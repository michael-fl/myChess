package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.MyChessEngine;

/**
 * @author Michael Fleischhauer
 */
final class PGNImporter {

    private final Pgn pgn;

    PGNImporter(Pgn pgn) {
        this.pgn = pgn;
    }

    Game importGame() {
        var config = new GameConfig(
                MyChessEngine.class,
                new EngineConfig.Builder().enableThreefoldRepetition(false).enableFiftyMovesRule(false).build());

        return importGame(config);
    }

    Game importGame(GameConfig config) {
        return new Game(config, pgn.moves);
    }
}
