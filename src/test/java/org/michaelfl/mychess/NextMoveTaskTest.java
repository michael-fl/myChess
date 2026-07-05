package org.michaelfl.mychess;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.engines.NextMoveTask;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Michael Fleischhauer
 */
class NextMoveTaskTest {

    private TranspositionTable tt;

    @BeforeEach
    void setup() {
        tt = TestSupport.createTestTT();
    }

    @AfterEach
    void tearDown() {
        tt.close();
    }

    private static EngineConfig slowConfig(TranspositionTable tt) {
        return new EngineConfig.Builder()
                .maxDepth(20)
                .millisPerMove(60_000)
                .silent(true)
                .setTranspositionTable(tt)
                .build();
    }

    @Test
    void noArgConstructorYieldsNonNullEnv() {
        var task = new NextMoveTask();
        assertNotNull(task.getEnv(), "Even without an env argument the task must carry an env");
        assertNull(task.getEnv().openingDB(),
                "The synthetic default env has no opening DB");
    }

    @Test
    void getEnvReturnsProvidedEnv() {
        var env = new MyChessEnv();
        var task = new NextMoveTask(env);
        assertSame(env, task.getEnv(),
                "getEnv() must return the exact env passed to the constructor");
    }

    @Test
    void isCanceledIsFalseByDefault() {
        assertFalse(new NextMoveTask().isCanceled(),
                "A fresh task is not yet cancelled");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void cancelSetsTheFlag() {
        // Submit a long-running search via the engine so the future is wired up.
        var game = GameImporter.importerFor("1. e4 e5").importGame(
                new GameConfig(MyChessEngine.class, slowConfig(tt)));
        var task = game.getEngine().nextMoveAsync();
        try {
            task.cancel();
            assertTrue(task.isCanceled(), "After cancel(), isCanceled() must be true");
        } finally {
            game.shutdown();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getResultTimesOutWhenSearchIsLongerThanBudget() {
        // 60-second budget per move with a deep search — getResult with a very small
        // timeout must throw TimeoutException.
        var game = GameImporter.importerFor("1. e4 e5").importGame(
                new GameConfig(MyChessEngine.class, slowConfig(tt)));
        var task = game.getEngine().nextMoveAsync();

        try {
            assertThrows(TimeoutException.class,
                    () -> task.getResult(50, TimeUnit.MILLISECONDS),
                    "getResult with a 50ms timeout must throw on a long-running search");
        } finally {
            task.cancel();
            game.shutdown();
        }
    }
}
