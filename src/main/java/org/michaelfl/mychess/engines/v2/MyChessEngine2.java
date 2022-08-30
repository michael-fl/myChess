package org.michaelfl.mychess.engines.v2;

import org.michaelfl.mychess.EngineConfig;
import org.michaelfl.mychess.Game;
import org.michaelfl.mychess.Move;
import org.michaelfl.mychess.Moves;
import org.michaelfl.mychess.engines.ChessEngine;
import org.michaelfl.mychess.engines.NextMoveTask;
import org.michaelfl.mychess.openingdb.OpeningDB;
import org.michaelfl.mychess.openingdb.OpeningDB.MoveInfo;

import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public final class MyChessEngine2 extends ChessEngine {

    public MyChessEngine2(EngineConfig config, Game game) {
        super(config, game);
    }

    @Override
    public Moves getPossibleMoves() {
        return PositionSearch2.getPossibleMoves(this, game);
    }

    @Override
    protected MoveAndWeight calculateNextMoveSub(NextMoveTask task) {

        long t1 = System.currentTimeMillis();
        var move = PositionSearch2.calculateNextMove(this, task, game);
        long t2 = System.currentTimeMillis();
        log("Position search took " + (t2 - t1) + "ms");

        return move;
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

}
