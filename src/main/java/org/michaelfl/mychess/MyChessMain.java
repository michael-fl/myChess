package org.michaelfl.mychess;

import org.michaelfl.mychess.openingdb.OpeningDB;

/**
 * Entry point: opens the {@link OpeningDB} and runs the REPL loop in
 * {@link CommandHandler#nextCommand()} until the user exits.
 *
 * @author Michael Fleischhauer
 */
public final class MyChessMain {

    static void main() {
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
