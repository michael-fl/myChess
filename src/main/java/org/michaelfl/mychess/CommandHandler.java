package org.michaelfl.mychess;

import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.ChessEngine.MoveAndWeight;
import org.michaelfl.mychess.openingdb.OpeningDB.MoveInfo;
import org.michaelfl.mychess.openingdb.OpeningDB.PositionInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * REPL dispatcher for {@link MyChessMain}. Reads one input line via
 * {@code java.lang.IO.readln()}, finds the first {@code Command} subclass whose
 * {@code canHandle} matches, and runs its {@code handle}. Anything that is not
 * a known command is parsed as an algebraic move.
 *
 * @author Michael Fleischhauer
 */
@SuppressWarnings("CallToPrintStackTrace")
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
                if (move.move() == 0) {
                    // No valid move possible ==> checkmate or stalemate
                    break;
                }
                game.makeMove(move);
                game.getBoard().print();
                System.out.println("Move #" + ((game.getGameStatus().getPlyCount() + 1) / 2) + ": " + ChessUtil.moveToString(move.move()));
                System.out.println("FEN: " + game.exportFEN());

                if (move.path().length <= 1 || move.path()[1] == 0) {
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
            return "new".equals(commandLine) || commandLine.startsWith("new ");
        }

        @Override
        void handle(String commandLine) {
            computerColor = null;

            var args = commandLine.split(" ");
            if (args.length == 1) {
                game = new Game(); // start standard chess game
                game.print();
            } else if (args.length == 2 && args[1].equals("960")) {
                game = Game.new960(); // start chess960 game
                game.print();
            } else {
                System.err.println("Invalid command line. Only \"new\" or \"new 960\" are supported.");
            }
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
                } catch (Exception _) {
                    // fall through
                }
                if ("nbrqk".indexOf(s.charAt(0)) >= 0) {
                    s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
                    try {
                        return MoveDescription.fromString(s, game.getTurn());
                    } catch (Exception _) {
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
            } catch (IllegalStateException _) {
                System.out.println("Illegal move");
            }
        }
    }

    private void makeComputerMove() throws InterruptedException, ExecutionException, TimeoutException {
        long t1 = System.currentTimeMillis();
        MoveAndWeight move = game.getEngine().nextMoveAsync(env).getResult(1, TimeUnit.HOURS);
        long t2 = System.currentTimeMillis();
        if (move.move() == 0) {
            System.err.println("No move possible!?");
            return;
        }

        var moveDescr = game.getBoard().moveToShortNotation(new Move(move.move()));
        game.makeMove(move);
        game.calculateAndSetGameResult();
        game.print();
        System.out.println("Move #" + game.getMoveCount()
                + ": " + moveDescr
                + ", weight " + ChessUtil.weightToString(move.weight())
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
                GameImporter importer = GameImporter.importerFor(gameNotation);
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

    private final class PgnCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return "pgn".equals(commandLine);
        }

        @Override
        void handle(String commandLine) {
            if (game.getGameStatus().getPlyCount() == 0) {
                System.out.println();
                return;
            }
            System.out.println(PGNConverter.toPGN(game.exportMoves()));
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
            if (move.move() == 0)
                System.out.println("Illegal position. No move possible.");
            else
                System.out.println(ChessUtil.moveToString(move.move()));
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
            if (move.move() == 0)
                System.out.println("Illegal position. No move possible.");
            else
                System.out.println(((game.getGameStatus().getPlyCount() + 1) / 2) + ". " + ChessUtil.moveToString(move.move()));
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
            if (move.move() == 0) {
                System.err.println("No move possible!?");
                return;
            }

            var moveDescr = game.getBoard().moveToShortNotation(new Move(move.move()));
            computerColor = game.getTurn();
            game.makeMove(move);
            game.calculateAndSetGameResult();
            game.print();
            System.out.println("Move #" + game.getMoveCount()
                    + ": " + moveDescr
                    + ", weight " + ChessUtil.weightToString(move.weight())
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
            System.out.println("not implemented");
        }
    }

    private final class SetDepthCommand extends CommandHandler.Command {

        private static final String PREFIX = "config depth ";

        @Override
        boolean canHandle(String commandLine) {
            return commandLine.startsWith(PREFIX);
        }

        @Override
        void handle(String commandLine) {
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

    private final class LoadFenCommand extends Command {

        @Override
        boolean canHandle(String commandLine) {
            return commandLine.startsWith("fen ");
        }

        @Override
        void handle(String commandLine) {
            String fen = commandLine.substring("fen ".length()).trim();
            try {
                var board = Fen.importFEN(fen);
                computerColor = null;
                game = new Game(Game.standardConfig(), board);
                game.print();
            } catch (IllegalArgumentException e) {
                System.err.println("Could not load FEN: " + e.getMessage());
            }
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
            var positionInfo = env.openingDB() != null ? env.openingDB().lookupPosition(key) : null;

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
                        var moveDescr = game.getBoard().moveToShortNotation(moveInfo.move);
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
            var positionInfo = env.openingDB() != null ? env.openingDB().lookupPosition(key) : null;

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
                        .skip(index - 1L)
                        .findFirst()
                        .orElseThrow();

                try {
                    game.makeMove(moveInfo.move);
                    game.print();

                    if (computerColor != null && computerColor == game.getTurn() && game.getResult() == GameResult.ONGOING) {
                        makeComputerMove();
                    }
                } catch (IllegalStateException _) {
                    System.out.println("Illegal move");
                }
            }
        }
    }

    private final class BenchCommand extends Command {

        private static final String PREFIX = "bench";

        @Override
        boolean canHandle(String commandLine) {
            return PREFIX.equals(commandLine) || commandLine.startsWith(PREFIX + " ");
        }

        @Override
        void handle(String commandLine) {
            String args = commandLine.substring(PREFIX.length()).trim();
            boolean all = false;
            boolean chess960 = false;
            int depth = Bench.DEFAULT_DEPTH;

            if (!args.isEmpty()) {
                for (String token : args.split("\\s+")) {
                    if ("all".equals(token)) {
                        all = true;
                    } else if ("960".equals(token)) {
                        chess960 = true;
                    } else {
                        try {
                            depth = Integer.parseInt(token);
                        } catch (NumberFormatException _) {
                            System.err.println("Usage: bench [all | 960] [depth]");
                            return;
                        }
                    }
                }
            }

            if (all) {
                printAll(depth);
            } else {
                printResult(Bench.run(depth, chess960, progressPrinter(depth, chess960)));
            }
        }

        /**
         * Prints one line per position as the suite progresses, so a run that takes minutes at
         * depth 8 and tens of minutes at depth 9 is distinguishable from a hung one.
         *
         * <p><b>The line format is fixed by the archive, not chosen here.</b> These lines are
         * what gets redirected into {@code test-results/bench/<version>-d<depth>.txt}, whose
         * whole purpose is that one version's line diffs against another's to localize a
         * changed signature to a position. Every archive since 3.5.2 uses
         * {@code n/total  nodes N  time T ms  fen}, so that is what is printed — a running
         * total or an extra column would be more informative on screen and would misalign 14
         * existing files.
         *
         * <p>No estimate of remaining time either: the suite's positions span three orders of
         * magnitude in size, so extrapolating from the count completed would be badly wrong
         * early on. The position index against the total is what is actually known.
         */
        private Consumer<Bench.PositionResult> progressPrinter(int depth, boolean chess960) {
            int total = Bench.suiteSize(chess960);

            System.out.printf(Locale.ROOT, "bench suite=%s depth=%d positions=%d%n",
                    chess960 ? "chess960" : "standard", depth, total);

            var done = new int[1];

            return position -> {
                done[0]++;

                System.out.printf(Locale.ROOT, "%3d/%d  nodes %,12d  time %6d ms   %s%n",
                        done[0], total, position.nodes(), position.timeMs(), shortFen(position.fen()));
            };
        }

        private void printResult(Bench.BenchResult result) {
            System.out.println("===========================================================");
            System.out.printf(Locale.ROOT, "Total time     : %,d ms%n", result.totalTimeMs());
            System.out.printf(Locale.ROOT, "Nodes searched : %,d%n", result.totalNodes());
            System.out.printf(Locale.ROOT, "NPS            : %,d%n", result.nps());

            // How the total is composed, not just how large it is — in this suite one position
            // has reached 87 % of the signature, which the sum alone hides. Appended below the
            // established three lines so the archive's existing footer stays byte-comparable.
            var largest = result.largestPosition();

            System.out.printf(Locale.ROOT, "Largest pos.   : %,d nodes (%.1f %% of total, %,d ms)   %s%n",
                    largest.nodes(), result.largestPositionShare(), largest.timeMs(),
                    shortFen(largest.fen()));
            System.out.printf(Locale.ROOT, "Without it     : %,d nodes, %,d ms, NPS %,d%n",
                    result.nodesWithoutLargestPosition(),
                    result.totalTimeMs() - largest.timeMs(),
                    result.npsWithoutLargestPosition());
        }

        private void printAll(int depth) {
            // Both suites get the progress printer too — this path is the longest of all, since
            // it runs the standard suite and then the Chess960 one.
            var standard = Bench.run(depth, false, progressPrinter(depth, false));
            var chess960 = Bench.run(depth, true, progressPrinter(depth, true));

            printResult(standard);
            System.out.println();
            printResult(chess960);
            System.out.println();

            // Combined grand total across all suites, in the same summary format
            // as a single-suite footer (no per-position lines to merge).
            long totalNodes = standard.totalNodes() + chess960.totalNodes();
            long totalTimeMs = standard.totalTimeMs() + chess960.totalTimeMs();
            int totalPositions = standard.positions().size() + chess960.positions().size();
            long totalNps = totalTimeMs == 0 ? 0 : totalNodes * 1_000L / totalTimeMs;

            System.out.printf(Locale.ROOT, "bench suite=all depth=%d positions=%d%n", depth, totalPositions);
            System.out.println("===========================================================");
            System.out.printf(Locale.ROOT, "Total time     : %,d ms%n", totalTimeMs);
            System.out.printf(Locale.ROOT, "Nodes searched : %,d%n", totalNodes);
            System.out.printf(Locale.ROOT, "NPS            : %,d%n", totalNps);
        }

        private String shortFen(String fen) {
            int space = fen.indexOf(' ');

            return space < 0 ? fen : fen.substring(0, space);
        }
    }

    private final List<Command> commands = List.of(
            new CommandHandler.QuitCommand(),
            new CommandHandler.AutoGameCommand(),
            new CommandHandler.NewGameCommand(),
            new BenchCommand(),
            new CommandHandler.MoveCommand(),
            new CommandHandler.ImportCommand(),
            new CommandHandler.PrintCommand(),
            new CommandHandler.BoardCommand(),
            new CommandHandler.ExportCommand(),
            new CommandHandler.PgnCommand(),
            new CommandHandler.RevertCommand(),
            new CommandHandler.TipCommand(),
            new CommandHandler.LastCommand(),
            new CommandHandler.GoCommand(),
            new CommandHandler.WeightCommand(),
            new CommandHandler.DeepWeightCommand(),
            new CommandHandler.LoadCommand(),
            new SetVariantsCommand(),
            new SetDepthCommand(),
            new SetIterationDepthCommand(),
            new PossibleMovesCommand(),
            new LoadFenCommand(),
            new FenCommand(),
            new HashCommand(),
            new OpeningCommand(),
            new OpeningMoveCommand()
    );

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
                String line = IO.readln().trim();
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
