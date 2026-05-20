package org.michaelfl.mychess;

import org.michaelfl.mychess.openingdb.OpeningDB;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Entry point: opens the {@link OpeningDB} and dispatches to either the
 * REPL ({@link CommandHandler}) or the UCI handler ({@link UciHandler})
 * depending on whether {@code "uci"} was given as a CLI argument.
 *
 * @author Michael Fleischhauer
 */
public final class MyChessMain {

    static void main(String[] args) {
        if (args.length > 0 && "uci".equalsIgnoreCase(args[0])) {
            runUci();
        } else {
            runRepl();
        }
    }

    private static void runUci() {
        Log.setMode(Log.Mode.UCI);
        try (OpeningDB openingDB = OpeningDB.open();
             BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            var env = new MyChessEnv(openingDB);
            new UciHandler(env, in).run();
        } catch (java.io.IOException e) {
            Log.error("UCI stdin/stdout failed", e);
        }
    }

    private static void runRepl() {
        try (OpeningDB openingDB = OpeningDB.open()) {
            var env = new MyChessEnv(openingDB);
            var game = new Game();
            CommandHandler scanner = new CommandHandler(env, game);

            game.print();

            do {
                System.out.print(">");
                System.out.flush();
            } while (scanner.nextCommand());

            System.out.println("Closing DB...");
        }
        System.out.println("DB closed");
    }
}
