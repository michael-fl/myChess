package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.MyChessEngine;

/**
 * @author Michael Fleischhauer
 */
final class PGNImporter implements GameImporter {

    private final Pgn pgn;

    PGNImporter(Pgn pgn) {
        this.pgn = pgn;
    }

    @Override
    public Game importGame() {
        var config = new GameConfig(
                MyChessEngine.class,
                new EngineConfig.Builder().enableThreefoldRepetition(false).enableFiftyMovesRule(false).build());

        return importGame(config);
    }

    @Override
    public Game importGame(GameConfig config) {
        return new Game(config, pgn.moves);
    }
}
