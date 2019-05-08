package org.michaelfl.mychess;

public final class MyChessMain {

    public static void main(String[] args) {
        Game game = new Game();
        CommandHandler scanner = new CommandHandler(game);

        game.print();

        //noinspection InfiniteLoopStatement
        while (true) {
            System.out.print(">");
            System.out.flush();
            scanner.nextCommand();
        }
    }
}
