package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.v1.MyChessEngine1;

/**
 * @author Michael Fleischhauer
 */
public class GameConfig {

    private final Class<? extends ChessEngine> engineWhite;
    private final Class<? extends ChessEngine> engineBlack;
    private final EngineConfig engineWhiteConfig;
    private final EngineConfig engineBlackConfig;

    public GameConfig(Class<? extends ChessEngine> engine, EngineConfig engineConfig) {
        this(engine, engineConfig, engine, engineConfig);
    }

    public GameConfig(Class<? extends ChessEngine> engineWhite, EngineConfig engineWhiteConfig, Class<? extends ChessEngine> engineBlack, EngineConfig engineBlackConfig) {
        this.engineWhite = engineWhite;
        this.engineBlack = engineBlack;
        this.engineWhiteConfig = engineWhiteConfig;
        this.engineBlackConfig = engineBlackConfig;
    }

    ChessEngine createEngineWhite(Game game) {
        return createEngine(game, engineWhite, engineWhiteConfig);
    }

    ChessEngine createEngineBlack(Game game) {
        return createEngine(game, engineBlack, engineBlackConfig);
    }

    private ChessEngine createEngine(Game game, Class<? extends ChessEngine> engineClass, EngineConfig engineConfig) {
        if (engineClass == MyChessEngine.class) {
            return new MyChessEngine(engineConfig, game);
        }
        if (engineClass == MyChessEngine1.class) {
            return new MyChessEngine1(engineConfig, game);
        }
        throw new IllegalArgumentException("Unknown engine: " + engineClass);
    }
}
