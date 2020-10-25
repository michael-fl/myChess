package org.michaelfl.mychess;

import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class CommandHandler {

    @SuppressWarnings("InnerClassMayBeStatic")
    private abstract class Command {
        abstract boolean canHandle(String commandLine);
        abstract void handle(String commandLine) throws Exception;
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
        void handle(String commandLine) throws InterruptedException, ExecutionException, TimeoutException {
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

        private void makeComputerMove() throws InterruptedException, ExecutionException, TimeoutException {
            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.HOURS);
            if (move == MoveAndWeight.NO_MOVE) {
                System.err.println("No move possible!?");
                return;
            }

            game.makeMove(move);
            game.calculateAndSetGameResult();
            game.print();
            System.out.println("Move #" + game.getMoveCount() + ": " + ChessUtil.moveToString(move.move));
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
                String notation = "[[b1-c3 c7-c5 e2-e4 b8-c6 f1-c4 e7-e6 d2-d3 g8-f6 c1-g5 f8-e7 g1-f3 e8-g8 d1-d2 a7-a6 e1-g1 b7-b5 c4-b3 h7-h6 g5-f6 e7-f6 d2-f4 e6-e5 f4-e3 c6-d4 b3-d5 a8-b8 a1-b1 c8-b7 d5-b7 b8-b7 b2-b4 c5-b4 c3-d5 d4-c2 e3-d2 c2-d4 d2-b4 d4-e2 g1-h1 e2-f4 b4-d6 f4-d5 f3-e5 f6-e5 d6-d5 f8-e8 f2-f4 e5-c7]]";
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
        void handle(String commandLine) throws InterruptedException, ExecutionException, TimeoutException {
            if (game.getResult() != GameResult.ONGOING) {
                System.err.println("Game is already over");
                return;
            }
            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.HOURS);
            if (move == MoveAndWeight.NO_MOVE)
                System.out.println("Illegal position. No move possible.");
            else
                System.out.println(ChessUtil.moveToString(move.move));
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
        void handle(String commandLine) throws InterruptedException, ExecutionException, TimeoutException {
            if (game.getResult() != GameResult.ONGOING) {
                System.err.println("Game is already over");
                return;
            }

            ChessEngine engine = game.getEngine();
            MoveAndWeight move = engine.nextMoveAsync().getResult(1, TimeUnit.HOURS);
            if (move == MoveAndWeight.NO_MOVE)
                System.out.println("Illegal position. No move possible.");
            else
                System.out.println(ChessUtil.moveToString(move.move));
        }
    }

    private final class WeightCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "weight".equals(commandLine) || "w".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            MoveGenerator gen = new MoveGenerator(new MoveSorterImpl(game.getRandom()));
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

            MyChessEngine engine = new MyChessEngine(new EngineConfig(6), game);
            int[] moveOut = new int[1];
            int depth = engine.findCheckmate(game.getTurn(), moveOut);
            if (depth < 0)
                depth = engine.findCheckmate(game.getOppositeColor(), moveOut);
            if (depth < 0) {
                System.out.println("No checkmate found");
            } else {
                game.getBoard().print();
                System.out.println(ChessUtil.moveToString(moveOut[0]) + " ==> Checkmate in " + depth + " moves");
            }
        }
    }

    private final class GoCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "go".equals(commandLine) || "g".equals(commandLine);
        }

        @Override
        void handle(String commandLine) throws InterruptedException, ExecutionException, TimeoutException {
            if (game.getResult() != GameResult.ONGOING) {
                System.err.println("Game is already over");
                return;
            }

            long t1 = System.currentTimeMillis();
            MoveAndWeight move = game.getEngine().nextMoveAsync().getResult(1, TimeUnit.HOURS);
            long t2 = System.currentTimeMillis();
            if (move == MoveAndWeight.NO_MOVE) {
                System.err.println("No move possible!?");
                return;
            }

            computerColor = game.getTurn();
            game.makeMove(move);
            game.calculateAndSetGameResult();
            game.print();
            System.out.println("Move #" + game.getMoveCount() + ": " + ChessUtil.moveToString(move.move) + ", " + (t2 - t1) + "ms");
        }
    }

    private final class SetVariantsCommand extends Command {

        private static final String PREFIX = "config variants ";

        @Override
        boolean canHandle(String commandLine) {
            return commandLine.startsWith(PREFIX);
        }

        @Override
        void handle(String commandLine) {
            String variantsStr = commandLine.substring(PREFIX.length()).trim();
            int variants = Integer.parseInt(variantsStr);
            //game.setConfig(game.getConfig().setNVariants(variants));
            System.out.println("not implemented");
        }
    }

    private final class SetDepthCommand extends Command {

        private static final String PREFIX = "config depth ";

        @Override
        boolean canHandle(String commandLine) {
            return commandLine.startsWith(PREFIX);
        }

        @Override
        void handle(String commandLine) {
            String variantsStr = commandLine.substring(PREFIX.length()).trim();
            int depth = Integer.parseInt(variantsStr);
            //game.setConfig(game.getConfig().setMaxDepth(depth));
            System.out.println("not implemented");
        }
    }

    private final class SetIterationDepthCommand extends Command {

        private static final String PREFIX = "config iteration-depth ";

        @Override
        boolean canHandle(String commandLine) {
            return commandLine.startsWith(PREFIX);
        }

        @Override
        void handle(String commandLine) {
            String variantsStr = commandLine.substring(PREFIX.length()).trim();
            int depth = Integer.parseInt(variantsStr);
            //game.setConfig(game.getConfig().setIterationDepth(depth));
            System.out.println("not implemented");
        }
    }

    private final class PossibleMovesCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "moves".equals(commandLine);
        }

        @Override
        void handle(String commandLine) throws InterruptedException, ExecutionException, TimeoutException {
            if (game.getResult() != GameResult.ONGOING) {
                System.err.println("Game is already over");
                return;
            }
            Moves moves = game.getEngine().getPossibleMoves();
            moves.print();
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
            new CheckmateSearchCommand(),
            new SetVariantsCommand(),
            new SetDepthCommand(),
            new SetIterationDepthCommand(),
            new PossibleMovesCommand()
    );

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    private Game game;
    private final WeightingFunction weightingFunction = new WeightingFunction();
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
