package org.michaelfl.mychess;

import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.FixDepthEngine;

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
            return "quit".equals(commandLine) || "exit".equals(commandLine) || "q".equals(commandLine);
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
            computerColor = null;
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
            computerColor = null;
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
            MoveDescription move;

            if ("O-O".equals(commandLine) || "0-0".equals(commandLine)) {
                move = isWhiteTurn ? MoveDescription.whiteCastlingKingSide : MoveDescription.blackCastlingKingSide;
            } else if ("O-O-O".equals(commandLine) || "0-0-0".equals(commandLine)) {
                move = isWhiteTurn ? MoveDescription.whiteCastlingQueenSide : MoveDescription.blackCastlingQueenSide;
            } else {
                int[] from = ChessUtil.getColAndRowFromString(commandLine.substring(0, 2));
                int[] to = ChessUtil.getColAndRowFromString(commandLine.substring(2, 4));

                char pawnPromotionSymbol = commandLine.length() > 4 ? Character.toUpperCase(commandLine.charAt(4)) : 0;

                move = new MoveDescription(from[0], from[1], to[0], to[1], pawnPromotionSymbol);
            }

            try {
                game.makeMove(move);
                game.print();
            } catch (IllegalStateException e) {
                System.err.println("Illegal move");
            }

            if (computerColor != null && computerColor == game.getTurn() && game.getResult() == GameResult.ONGOING)
                makeComputerMove();
        }

        private void makeComputerMove() {
            int move = game.getEngine().nextMove();
            if (move == 0) {
                System.err.println("No move possible!?");
                return;
            }

            game.makeMove(move);
            System.out.println("Move #" + game.getMoveCount() + ": " + ChessUtil.moveToString(move));
            game.calculateAndSetGameResult();
            game.print();
        }
    }

    private final class ImportCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return commandLine.startsWith("import ") || commandLine.startsWith("imp ");
        }

        @Override
        void handle(String commandLine) {
            try {
                computerColor = null;
                SimpleNotationImporter importer = new SimpleNotationImporter(commandLine.substring(commandLine.indexOf(' ') + 1));
                game = importer.importGame();
                game.print();
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private final class LoadCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return commandLine.equals("l");
        }

        @Override
        void handle(String commandLine) {
            try {
                String notation = "[[e2-e4 d7-d5 e4-d5 d8-d5 b1-c3 d5-e5 f1-e2 b8-c6 g1-f3 e5-f4 d2-d4 f4-d6 e1-g1 c6-b4 c3-b5 d6-c6 c1-f4 b4-d5 f4-g3 a7-a6 a2-a4 c8-f5 c2-c4 c6-h6 b5-c7 d5-c7 g3-c7 h6-c6 c7-g3 e8-c8 f3-e5 c6-e6 e2-h5 g7-g6 h5-f3 f8-h6 f1-e1 b7-b6 f3-d5 d8-d5 c4-d5 e6-d5 a1-a3 g8-f6 a3-c3 c8-b7 e5-f3 h8-e8 c3-c7 b7-a8 e1-e7 e8-e7 c7-e7 h6-f8 d1-e2]]";
                //[[e2-e4 d7-d5 e4-d5 d8-d5 b1-c3 d5-e5 f1-e2 b8-c6 g1-f3 e5-f4 d2-d4 f4-d6 e1-g1 c6-b4 c3-b5 d6-c6 c1-f4 b4-d5 f4-g3 a7-a6 a2-a4 c8-f5 c2-c4 c6-h6 b5-c7 d5-c7 g3-c7 h6-c6 c7-g3 e8-c8 f3-e5 c6-e6 e2-h5 g7-g6 h5-f3 f8-h6 f1-e1 b7-b6]]";
                computerColor = null;
                SimpleNotationImporter importer = new SimpleNotationImporter(notation);
                game = importer.importGame();
                game.print();
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private final class PrintCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "print".equals(commandLine) || "p".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            game.print();
        }
    }

    private final class BoardCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "board".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            game.getBoard().print();
        }
    }

    private final class ExportCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "export".equals(commandLine) || "exp".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            System.out.println(game.exportMoves());
        }
    }

    private final class RevertCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "revert".equals(commandLine) || "r".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            try {
                computerColor = null;
                game.revertMove();
                game.print();
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private final class TipCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "tip".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            if (game.getResult() != GameResult.ONGOING) {
                System.err.println("Game is already over");
                return;
            }
            int move = game.getEngine().nextMove();
            System.out.println(ChessUtil.moveToString(move));
        }
    }

    private final class LastCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "last".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            if (game.getMoveCount() == 0) {
                System.err.println("No previous move");
                return;
            }
            System.out.println(game.getMoves().get(game.getMoveCount() - 1));
        }
    }

    private final class DeepWeightCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "dw".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            if (game.getResult() != GameResult.ONGOING) {
                System.err.println("Game is already over");
                return;
            }

            Float oldWeight = game.getWeight();
            FixDepthEngine engine = new FixDepthEngine(game);
            int move = engine.nextMove();
            if (move != 0)
                System.out.println("weight: " + game.getWeight());
            else
                System.out.println("Illegal position. No move possible.");
            game.setWeight(oldWeight != null ? oldWeight : 0);
        }
    }

    private final class WeightCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "weight".equals(commandLine) || "w".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            MoveGenerator gen = new MoveGenerator();
            Moves moves = gen.calculateMoves(game.getGameStatus(), game.getBoard());
            System.out.println("Possible moves: " + moves);
            weightingFunction.calculate(game.getGameStatus(), game.getBoard());
            weightingFunction.print();
        }
    }

    private final class CheckmateSearchCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "cm".equals(commandLine) || "checkmate".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            if (game.getResult() != GameResult.ONGOING) {
                System.err.println("Game is already over");
                return;
            }

            int[] moveOut = new int[1];
            int depth = game.getEngine().findCheckmate(game.getTurn(), game.getGameStatus(), game.getBoard().copy(), moveOut);
            if (depth < 0)
                depth = game.getEngine().findCheckmate(game.getOppositeColor(), game.getGameStatus(), game.getBoard().copy(), moveOut);
            if (depth < 0) {
                System.out.println("No checkmate found");
            } else {
                int nextMove = game.getEngine().nextMove();
                game.getBoard().print();
                System.out.println(ChessUtil.moveToString(moveOut[0]) + " ==> Checkmate in " + depth + " moves. Next calculated move: " + ChessUtil.moveToString(nextMove));
            }
        }
    }

    private final class GoCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "go".equals(commandLine) || "g".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            if (game.getResult() != GameResult.ONGOING) {
                System.err.println("Game is already over");
                return;
            }

            long t1 = System.currentTimeMillis();
            int move = game.getEngine().nextMove();
            long t2 = System.currentTimeMillis();
            if (move == 0) {
                System.err.println("No move possible!?");
                return;
            }

            computerColor = game.getTurn();
            game.makeMove(move);
            System.out.println("Move #" + game.getMoveCount() + ": " + ChessUtil.moveToString(move) + ", " + (t2 - t1) + "ms");
            game.calculateAndSetGameResult();
            game.print();
        }
    }

    private final List<Command> commands = List.of(
            new QuitCommand(),
            new AutoGameCommand(),
            new NewGameCommand(),
            new MoveCommand(),
            new ImportCommand(),
            new PrintCommand(),
            new BoardCommand(),
            new ExportCommand(),
            new RevertCommand(),
            new TipCommand(),
            new LastCommand(),
            new GoCommand(),
            new WeightCommand(),
            new DeepWeightCommand(),
            new LoadCommand(),
            new CheckmateSearchCommand()
    );

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    private Game game;
    private WeightingFunction weightingFunction = new WeightingFunction();
    private Integer computerColor;

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
