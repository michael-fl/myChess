package org.michaelfl.mychess;

import org.michaelfl.mychess.openingdb.OpeningDB;

public final class MyChessMain {

    public static void main(String[] args) {
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
