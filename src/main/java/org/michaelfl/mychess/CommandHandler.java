package org.michaelfl.mychess;

import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.engines.MyChessEngine;
import org.michaelfl.mychess.openingdb.OpeningDB.MoveInfo;
import org.michaelfl.mychess.openingdb.OpeningDB.PositionInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

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
            System.out.println("Will exit MyChess...");
            game.shutdown();
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
            playAutoGame();
        }

        private void playAutoGame() {
            try {
                if (game.getResult() == GameResult.ONGOING) {
                    playAutoGameInternal();
                }

                game.getBoard().print();
                int turn = game.getTurn();
                System.out.println("Moves: " + game.exportMoves());
                System.out.println("Turn: " + (turn == GameStatus.TURN_WHITE ? "white" : "black"));
                if (game.getResult() == GameResult.CHECKMATE || game.getResult() == GameResult.STALEMATE)
                    System.out.println("Result: " + (turn == GameStatus.TURN_WHITE ? "white" : "black") + " " + game.getResult());
                else
                    System.out.println("Result: " + game.getResult());

            } catch (Throwable e) {
                e.printStackTrace();

                game.getBoard().print();
                System.out.println("Turn: " + (game.getTurn() == GameStatus.TURN_WHITE ? "white" : "black"));
                System.out.println("Moves: " + game.exportMoves());
                MoveGenerator moveGenerator = new MoveGenerator(MoveSorter.defaultImplementation());
                Moves possibleMoves = moveGenerator.calculateMoves(game.getBoard());
                System.out.println("Possible moves: " + possibleMoves);
                System.out.flush();
                System.err.println("ERROR: " + e);
            }
        }

        private void playAutoGameInternal() throws InterruptedException, ExecutionException, TimeoutException {
            game.getBoard().print();

            for (int i = 0; i < 1000 && game.getResult() == GameResult.ONGOING; i++) {
                MoveAndWeight move = game.getEngine().nextMoveAsync(env).getResult(1, TimeUnit.HOURS);
                if (move.move == 0) {
                    // No valid move possible ==> checkmate or stalemate
                    break;
                }
                game.makeMove(move);
                game.getBoard().print();
                System.out.println("Move #" + ((game.getGameStatus().getPlyCount() + 1) / 2) + ": " + ChessUtil.moveToString(move.move));
                System.out.println("FEN: " + game.exportFEN());

                if (move.path.length <= 1 || move.path[1] == 0) {
                    game.calculateAndSetGameResult();
                }
            }

            GameResult gameResult = game.getResult();
            if (gameResult == GameResult.ONGOING) {
                gameResult = GameResult.DRAW;
            }
            game.setResult(gameResult);
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
            return parseMove(commandLine) != null;
        }

        private MoveDescription parseMove(String s) {
            if (s.length() >= 2) {
                try {
                    return MoveDescription.fromString(s, game.getTurn());
                } catch (Exception e) {
                    // fall through
                }
                if ("nbrqk".indexOf(s.charAt(0)) >= 0) {
                    s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
                    try {
                        return MoveDescription.fromString(s, game.getTurn());
                    } catch (Exception e) {
                        // fall through
                    }
                }
            }

            return null;
        }

        @Override
        void handle(String commandLine) throws InterruptedException, ExecutionException, TimeoutException {
            MoveDescription move = parseMove(commandLine);

            try {
                game.makeMove(move);
                game.print();

                if (computerColor != null && computerColor == game.getTurn() && game.getResult() == GameResult.ONGOING) {
                    makeComputerMove();
                }
            } catch (IllegalStateException e) {
                System.out.println("Illegal move");
            }
        }
    }

    private void makeComputerMove() throws InterruptedException, ExecutionException, TimeoutException {
        long t1 = System.currentTimeMillis();
        MoveAndWeight move = game.getEngine().nextMoveAsync(env).getResult(1, TimeUnit.HOURS);
        long t2 = System.currentTimeMillis();
        if (move.move == 0) {
            System.err.println("No move possible!?");
            return;
        }

        var moveDescr = game.moveToShortNotation(new Move(move.move));
        game.makeMove(move);
        game.calculateAndSetGameResult();
        game.print();
        System.out.println("Move #" + game.getMoveCount()
                + ": " + moveDescr
                + ", weight " + ChessUtil.weightToString(move.weight)
                + ", " + (t2 - t1) + "ms");
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
                var gameNotation = commandLine.substring(commandLine.indexOf(' ') + 1);
                GameImporter importer;
                if (gameNotation.startsWith("[[")) {
                    importer = new SimpleNotationImporter(gameNotation);
                } else {
                    var pgn = Pgn.parse(gameNotation).findFirst();
                    importer = new PGNImporter(pgn.orElseThrow(() -> new IllegalArgumentException("No PGN given")));
                }
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
                //String notation = "[[b1-c3 c7-c5 e2-e4 b8-c6 f1-c4 e7-e6 d2-d3 g8-f6 c1-g5 f8-e7 g1-f3 e8-g8 d1-d2 a7-a6 e1-g1 b7-b5 c4-b3 h7-h6 g5-f6 e7-f6 d2-f4 e6-e5 f4-e3 c6-d4 b3-d5 a8-b8 a1-b1 c8-b7 d5-b7 b8-b7 b2-b4 c5-b4 c3-d5 d4-c2 e3-d2 c2-d4 d2-b4 d4-e2 g1-h1 e2-f4 b4-d6 f4-d5 f3-e5 f6-e5 d6-d5 f8-e8 f2-f4 e5-c7]]";
                //String notation = "[[b1-c3 e7-e6 e2-e3 b8-c6 f1-b5 d8-g5 b5-c6 d7-c6 d1-f3 g5-f5 e3-e4 f5-e5]]";
                String notation = "[[g1-f3 g8-f6 e2-e3 d7-d6 b1-c3 c8-g4 h2-h3 g4-d7 f1-c4 e7-e6 e1-g1 d6-d5 c4-d3 f8-d6 b2-b3 b8-c6 c3-b5 c6-b4 b5-d6 c7-d6 c1-a3 b4-d3 c2-d3 d8-c7 a1-c1 c7-b6 a3-b2 e6-e5]]";
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
            MoveAndWeight move = game.getEngine().nextMoveAsync(env).getResult(1, TimeUnit.HOURS);
            if (move.move == 0)
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
            System.out.println(new Move(game.getGameStatus().getLastMove()));
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
            MoveAndWeight move = engine.nextMoveAsync(null).getResult(1, TimeUnit.HOURS);
            if (move.move == 0)
                System.out.println("Illegal position. No move possible.");
            else
                System.out.println(((game.getGameStatus().getPlyCount() + 1) / 2) + ". " + ChessUtil.moveToString(move.move));
        }
    }

    private final class WeightCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "weight".equals(commandLine) || "w".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            weightingFunction.calculate(game.getBoard());
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

            MyChessEngine engine = new MyChessEngine(new EngineConfig.Builder().useHandicap(false).build(), game);
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
            MoveAndWeight move = game.getEngine().nextMoveAsync(env).getResult(1, TimeUnit.HOURS);
            long t2 = System.currentTimeMillis();
            if (move.move == 0) {
                System.err.println("No move possible!?");
                return;
            }

            var moveDescr = game.moveToShortNotation(new Move(move.move));
            computerColor = game.getTurn();
            game.makeMove(move);
            game.calculateAndSetGameResult();
            game.print();
            System.out.println("Move #" + game.getMoveCount()
                    + ": " + moveDescr
                    + ", weight " + ChessUtil.weightToString(move.weight)
                    + ", " + (t2 - t1) + "ms");
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
            //String variantsStr = commandLine.substring(PREFIX.length()).trim();
            //int variants = Integer.parseInt(variantsStr);
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
            //String variantsStr = commandLine.substring(PREFIX.length()).trim();
            //int depth = Integer.parseInt(variantsStr);
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
            //String variantsStr = commandLine.substring(PREFIX.length()).trim();
            //int depth = Integer.parseInt(variantsStr);
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
        void handle(String commandLine) {
            if (game.getResult() != GameResult.ONGOING) {
                System.err.println("Game is already over");
                return;
            }
            Moves moves = game.getEngine().getPossibleMoves();
            moves.print();
        }
    }

    private final class FenCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "fen".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            System.out.println(game.exportFEN());
        }
    }

    private final class HashCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "hash".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            System.out.println("hash: " + game.getBoard().getGameStatus().getPositionHash());
        }
    }

    private final class OpeningCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "o".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            var key = game.getBoard().calculatePositionKey();
            var positionInfo = env.getOpeningDB().lookupPosition(key);

            if (positionInfo == null) {
                System.out.println("No position found in opening DB.");
            } else {
                printOpeningVariants(positionInfo);
            }
        }

        private void printOpeningVariants(PositionInfo positionInfo) {
            var buf = new StringBuilder();

            buf.append("#Positions: ").append(positionInfo.count).append('\n');

            var moveNo = new AtomicInteger();
            positionInfo.moves
                    .stream()
                    .sorted(Comparator.comparingInt(MoveInfo::getTotalCount).reversed())
                    .forEach(moveInfo -> {
                        var moveDescr = game.moveToShortNotation(moveInfo.move);
                        buf.append(String.format("%-3s", moveNo.incrementAndGet() + ".")).append(' ')
                                .append(String.format("%-6s", moveDescr)).append(' ')
                                .append("#").append(String.format("%7d", moveInfo.getTotalCount())).append('\t')
                                .append(String.format("%3d", moveInfo.getWinPercentage())).append("% win, ")
                                .append(String.format("%3d", moveInfo.getDrawPercentage())).append("% draw, ")
                                .append(String.format("%3d", moveInfo.getLossPercentage())).append("% loss")
                                .append('\n');
                    });

            System.out.println(buf);
        }
    }

    private final class OpeningMoveCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return commandLine.length() > 1
                    && commandLine.startsWith("o")
                    && Character.isDigit(commandLine.charAt(1));
        }

        @Override
        void handle(String commandLine) throws ExecutionException, InterruptedException, TimeoutException {
            var index = Integer.parseInt(commandLine.substring(1));
            var key = game.getBoard().calculatePositionKey();
            var positionInfo = env.getOpeningDB().lookupPosition(key);

            if (index < 1) {
                System.out.println("Move number must be > 0.");
            } else if (positionInfo == null) {
                System.out.println("No position found in opening DB.");
            } else if (index > positionInfo.moves.size()) {
                System.out.println("No such move in opening DB.");
            } else {
                var moveInfo = positionInfo.moves
                        .stream()
                        .sorted(Comparator.comparingInt(MoveInfo::getTotalCount).reversed())
                        .skip(index - 1)
                        .findFirst()
                        .orElseThrow();

                try {
                    game.makeMove(moveInfo.move);
                    game.print();

                    if (computerColor != null && computerColor == game.getTurn() && game.getResult() == GameResult.ONGOING) {
                        makeComputerMove();
                    }
                } catch (IllegalStateException e) {
                    System.out.println("Illegal move");
                }
            }
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
            new PossibleMovesCommand(),
            new FenCommand(),
            new HashCommand(),
            new OpeningCommand(),
            new OpeningMoveCommand()
    );

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    private final MyChessEnv env;
    private Game game;
    private final WeightingFunction weightingFunction = new WeightingFunction();
    private Integer computerColor;

    CommandHandler(MyChessEnv env, Game game) {
        this.env = env;
        this.game = game;
    }

    boolean nextCommand() {
        try {
            do {
                String line = in.readLine().trim();
                if (!line.isEmpty()) {
                    for (Command command : commands) {
                        if (command.canHandle(line)) {
                            command.handle(line);
                            return !(command instanceof QuitCommand);
                        }
                    }

                    System.err.println("Unknown command");
                }
            } while (true);
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }
    }

}
