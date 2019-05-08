package org.michaelfl.mychess;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

final class CommandHandler {

    private abstract class Command {
        abstract boolean canHandle(String commandLine);
        abstract void handle(String commandLine);
    }

    private final class QuitCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "quit".equals(commandLine) || "exit".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            System.exit(0);
        }
    }

    private final class AutoGameCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "auto".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            game.playAutoGame();
        }
    }

    private final class NewGameCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "new".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            game = new Game();
            game.print();
        }
    }

    private final class MoveCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            // "a2a3", "h7h8Q", "O-O", "0-0", "O-O-O", "0-0-0"
            if (commandLine.length() < 3 || commandLine.length() > 5)
                return false;

            if (commandLine.charAt(0) == '0') {
                return "0-0".equals(commandLine) || "0-0-0".equals(commandLine);
            }
            if (commandLine.charAt(0) == 'O') {
                return "O-O".equals(commandLine) || "O-O-O".equals(commandLine);
            }

            if (!(commandLine.charAt(0) >= 'a' && commandLine.charAt(0) <= 'h'
                && commandLine.charAt(1) >= '1' && commandLine.charAt(1) <= '8'
                && commandLine.charAt(2) >= 'a' && commandLine.charAt(2) <= 'h'
                && commandLine.charAt(3) >= '1' && commandLine.charAt(3) <= '8'))
                return false;

            if (commandLine.length() == 4)
                return true;

            char piece = Character.toUpperCase(commandLine.charAt(4));
            return piece == 'Q' || piece == 'N' || piece == 'R' || piece == 'B';
        }

        @Override
        void handle(String commandLine) {
            boolean isWhiteTurn = game.getTurn() == GameStatus.TURN_WHITE;
            MoveDescription move = null;

            if ("O-O".equals(commandLine) || "0-0".equals(commandLine))
                move = isWhiteTurn ? MoveDescription.whiteCastlingKingSide : MoveDescription.blackCastlingKingSide;
            if ("O-O-O".equals(commandLine) || "0-0-0".equals(commandLine))
                move = isWhiteTurn ? MoveDescription.whiteCastlingQueenSide : MoveDescription.blackCastlingQueenSide;

            try {
                game.makeMove(move);
            } catch (IllegalStateException e) {
                System.err.println("Illegal move");
            }
        }
    }

    private final List<Command> commands = List.of(
            new QuitCommand(),
            new AutoGameCommand(),
            new NewGameCommand(),
            new MoveCommand()
    );

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    private Game game;

    CommandHandler(Game game) {
        this.game = game;
    }

    void nextCommand() {
        try {
            do {
                String line = in.readLine().trim();
                if (!line.isEmpty()) {
                    for (Command command : commands) {
                        if (command.canHandle(line)) {
                            command.handle(line);
                            return;
                        }
                    }

                    System.err.println("Unknown command");
                }
            } while (true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
