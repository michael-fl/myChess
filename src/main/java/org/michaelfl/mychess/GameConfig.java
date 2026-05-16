package org.michaelfl.mychess;

import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.MyChessEngine;

/**
 * @author Michael Fleischhauer
 */
public final class GameConfig {

    private final Class<? extends ChessEngine> engineWhite;
    private final Class<? extends ChessEngine> engineBlack;
    private final EngineConfig engineWhiteConfig;
    private final EngineConfig engineBlackConfig;

    public GameConfig(EngineConfig engineConfig) {
        this(MyChessEngine.class, engineConfig, MyChessEngine.class, engineConfig);
    }

    public GameConfig(Class<? extends ChessEngine> engine, EngineConfig engineConfig) {
        this(engine, engineConfig, engine, engineConfig);
    }

    public Class<? extends ChessEngine> getEngineWhite() {
        return engineWhite;
    }

    public Class<? extends ChessEngine> getEngineBlack() {
        return engineBlack;
    }

    public EngineConfig getEngineWhiteConfig() {
        return engineWhiteConfig;
    }

    public EngineConfig getEngineBlackConfig() {
        return engineBlackConfig;
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
        throw new IllegalArgumentException("Unknown engine: " + engineClass);
    }
}
