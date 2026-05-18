package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.MyChessEngine;

/**
 * {@link GameImporter} that replays a parsed {@link Pgn} into a {@link Game}.
 * The no-arg variant disables threefold-repetition and the 50-move rule so
 * that draw-by-rule does not interrupt importing finished games.
 *
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
