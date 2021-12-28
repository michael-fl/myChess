package org.michaelfl.mychess.engines;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Game.GameResult;
import org.michaelfl.mychess.GameStatus;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.WeightingFunction;
import org.michaelfl.mychess.openingdb.OpeningDB;
import org.michaelfl.mychess.openingdb.OpeningDB.MoveInfo;

import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public final class MyChessEngine extends ChessEngine {

    public MyChessEngine(EngineConfig config, Game game) {
        super(config, game);
    }

    @Override
    public Moves getPossibleMoves() {
        return PositionSearch.getPossibleMoves(this, game);
    }

    @Override
    public MoveAndWeight calculateNextMove(NextMoveTask task) {
        MoveAndWeight move = MoveAndWeight.NO_MOVE;
        var openingDB = task.getEnv().getOpeningDB();

        // First check if this game is already finished
        if (game.getResult() != GameResult.ONGOING) {
            if (game.getResult() == GameResult.CHECKMATE) {
                move = new MoveAndWeight(0, -WeightingFunction.CHECKMATE_WEIGHT_HIGH, GameResult.CHECKMATE, new int[0]);
            } else {
                move = new MoveAndWeight(0, 0f, game.getResult(), new int[0]);
            }
        } else if ((getConfig().isEnableFiftyMovesRule() && game.getGameStatus().getHalfMoveClock() >= 100) || isThreefoldRepetition()) {
            move = new MoveAndWeight(0, 0f, GameResult.DRAW, new int[0]);
        } else if (openingDB != null) {
            var m = getMoveFromOpeningDB(openingDB);
            if (m != null) {
                move = new MoveAndWeight(m.getMove(), 0f, GameResult.ONGOING, new int[] { move.move });
            }
        }

        if (move == MoveAndWeight.NO_MOVE) {
            // Phase 1: Checkmate search
            if (getConfig().isCheckmateCheck()) {
                long t1 = System.currentTimeMillis();
                move = CheckmateSearch.findCheckmateMove(this, game); // TODO: return path from checkmate search
                long t2 = System.currentTimeMillis();
                log("Checkmate check took " + (t2 - t1) + "ms");
            }

            // Phase 2: Position search
            if (move.move == 0) {
                long t1 = System.currentTimeMillis();
                move = PositionSearch.calculateNextMove(this, task, game);
                long t2 = System.currentTimeMillis();
                log("Position search took " + (t2 - t1) + "ms");
            }
        }

        float weightFactor = game.getTurn() == GameStatus.TURN_WHITE ? 1 : -1;

        return move.weightFactor(weightFactor);
    }

    private Move getMoveFromOpeningDB(OpeningDB openingDB) {
        var key = game.getBoard().calculatePositionKey();
        var positionInfo = openingDB.lookupPosition(key);
        if (positionInfo == null) {
            return null;
        }

        var candidates = positionInfo.moves
                .stream()
                .filter(
                    m -> m.getTotalCount() >= 100
                        && m.getWinPercentage() >= 20
                        && m.getLossPercentage() < 45)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return null;
        }

        int sum = candidates.stream().mapToInt(MoveInfo::getTotalCount).sum();
        int n = getRandom().nextInt(sum);

        int i = 0;
        for (var m : candidates) {
            i += m.getTotalCount();
            if (n < i) {
                return m.move;
            }
        }

        throw new IllegalStateException();
    }

    public int findCheckmate(int forColor, int[] moveOut) {
        return CheckmateSearch.findCheckmate(this, game, forColor, moveOut);
    }
}
