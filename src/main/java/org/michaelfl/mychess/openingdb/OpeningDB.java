package org.michaelfl.mychess.openingdb;

import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.michaelfl.mychess.Move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Michael Fleischhauer
 */
public final class OpeningDB implements AutoCloseable {

    public static final class MoveInfo {
        public final Move move;
        public final int winCount;
        public final int drawCount;
        public final int lossCount;

        MoveInfo(DBValue dbValue, int moveIndex) {
            this.move = dbValue.getMoveByIndex(moveIndex);
            this.winCount = dbValue.getWinCountByIndex(moveIndex);
            this.drawCount = dbValue.getDrawCountByIndex(moveIndex);
            this.lossCount = dbValue.getLossCountByIndex(moveIndex);
        }

        public int getTotalCount() {
            return winCount + drawCount + lossCount;
        }

        public int getWinPercentage() {
            return Math.round((float) winCount / getTotalCount() * 100.0f);
        }

        public int getDrawPercentage() {
            return Math.round((float) drawCount / getTotalCount() * 100.0f);
        }

        public int getLossPercentage() {
            return Math.round((float) lossCount / getTotalCount() * 100.0f);
        }
    }

    public static final class PositionInfo {
        public final int count;
        public final List<MoveInfo> moves;

        PositionInfo(DBValue dbValue) {
            final int nMoves = dbValue.getNumberOfMoves();
            this.count = dbValue.getPositionCount();
            var internalMoveList = new ArrayList<MoveInfo>(count);

            for (int i = 0; i < nMoves; i++) {
                internalMoveList.add(new MoveInfo(dbValue, i));
            }

            this.moves = Collections.unmodifiableList(internalMoveList);
        }
    }

    private final DB db;
    private final BTreeMap<String, byte[]> dbMap;

    private OpeningDB() {
        db = DBMaker
                .fileDB("db/openings.db")
                .transactionEnable()
                .closeOnJvmShutdown()
                .make();

        dbMap = db.treeMap("openingsMap")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.BYTE_ARRAY)
                .valuesOutsideNodesEnable()
                .createOrOpen();
    }

    public static OpeningDB open() {
        return new OpeningDB();
    }

    public PositionInfo lookupPosition(String key) {
        var dbValue = get(key);
        if (dbValue == null) {
            return null;
        }

        return new PositionInfo(dbValue);
    }

    public void put(String key, byte[] value) {
        dbMap.put(key, value);
    }

    public DBValue get(String key) {
        var value = dbMap.get(key);
        return value != null ? new DBValue(value) : null;
    }

    public void commit() {
        db.commit();
    }

    public void rollback() {
        db.rollback();
    }

    @Override
    public void close() {
        dbMap.close();
        db.close();
    }
}
